package infrastructure.performance.verify

import core.seed.Seed
import infrastructure.jit.JITAnalyzer
import infrastructure.jvm.JvmExecutor
import infrastructure.performance.PerformanceAnalyzer
import infrastructure.performance.PerformanceMeasurer
import infrastructure.performance.anomaly.AnomalyGroupType
import infrastructure.performance.anomaly.AnomalyRepository
import infrastructure.performance.anomaly.PerformanceAnomalyGroup
import infrastructure.performance.entity.PerformanceMetrics
import infrastructure.performance.entity.SignificanceLevel
import java.io.File

/**
 * Реализует верификацию аномалий через детальные измерения производительности.
 * Подтверждает аномалии, обнаруженные в процессе фаззинга, путем проведения
 * более точных и продолжительных измерений.
 */
class DetailedMeasurementAnomalyVerifier(
    private val performanceMeasurer: PerformanceMeasurer,
    private val performanceAnalyzer: PerformanceAnalyzer,
    private val anomalyRepository: AnomalyRepository,
    private val jitAnalyzer: JITAnalyzer? = null,
    private val periodBetweenDetailedChecks: Int = 100,
    private val minSeedsForDetailedCheck: Int = 10,
    private val countOfMostInterestedSeedsToVerify: Int = 3
) : AnomalyVerifier {

    companion object {
        private const val INTEREST_REDUCTION_FACTOR = 2.0
        private val MUTATIONS_DIR = File("mutations")
    }

    private val pendingSeeds = mutableSetOf<Seed>()
    private var iterationsSinceLastCheck: Int = 0

    override fun addSeedForVerification(seed: Seed) {
        if (!seed.verified) {
            pendingSeeds.add(seed)
        }
    }

    override fun shouldPerformDetailedCheck(): Boolean =
        iterationsSinceLastCheck >= periodBetweenDetailedChecks ||
                pendingSeeds.size >= minSeedsForDetailedCheck

    override fun onNewIteration() {
        iterationsSinceLastCheck++
    }

    override fun getPendingSeedsCount(): Int = pendingSeeds.size

    override fun performDetailedCheck(
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>
    ): Int {
        if (pendingSeeds.isEmpty()) return 0

        val topSeeds = selectTopSeeds()
        logDetailedCheckStart(topSeeds.size)

        val confirmedAnomaliesCount = topSeeds
            .filter { it.anomalies.isNotEmpty() }
            .sumOf { seed ->
                confirmAnomaliesAndUpdateSeed(
                    seed, jvmExecutors, jvmOptions, VerificationPurpose.SEED_VERIFICATION
                )
            }

        resetVerificationState()
        println("Детальная проверка завершена: подтверждено $confirmedAnomaliesCount аномалий.")

        return confirmedAnomaliesCount
    }

    override fun confirmAnomaliesAndUpdateSeed(
        seed: Seed,
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>,
        purpose: VerificationPurpose
    ): Int {
        println("Выполняем детальную проверку сида для ${purpose.name}...")

        val detailedMetrics = measureSeedPerformance(seed, jvmExecutors, jvmOptions)
            .takeIf { it.isNotEmpty() } ?: return 0

        val analysisResult = performanceAnalyzer.analyzeWithJIT(detailedMetrics)
        val previousInterestingness = seed.interestingness

        val targetLevel = getTargetSignificanceLevel(purpose)
        val meetsSignificanceLevel = isSignificanceLevelSufficient(
            analysisResult.significanceLevel, targetLevel
        )

        val newInterestingness = if (meetsSignificanceLevel) {
            analysisResult.interestingnessScore
        } else {
            calculateReducedInterestingness(seed.anomalies, previousInterestingness)
        }

        seed.updateWithVerificationResults(
            analysisResult.significanceLevel,
            if (meetsSignificanceLevel) analysisResult.anomalies else emptyList(),
            newInterestingness
        )

        return if (meetsSignificanceLevel && analysisResult.anomalies.isNotEmpty()) {
            handleConfirmedAnomalies(seed, analysisResult.anomalies, purpose, newInterestingness)
        } else {
            logNoConfirmedAnomalies(seed, purpose, previousInterestingness, newInterestingness)
            0
        }
    }

    private fun selectTopSeeds(): List<Seed> =
        pendingSeeds
            .sortedByDescending { it.interestingness }
            .take(countOfMostInterestedSeedsToVerify)

    private fun resetVerificationState() {
        pendingSeeds.clear()
        iterationsSinceLastCheck = 0
    }

    private fun getTargetSignificanceLevel(purpose: VerificationPurpose): SignificanceLevel =
        when (purpose) {
            VerificationPurpose.SEED_VERIFICATION -> SignificanceLevel.SEED_EVOLUTION
            VerificationPurpose.REPORTING -> SignificanceLevel.REPORTING
        }

    private fun isSignificanceLevelSufficient(
        actualLevel: SignificanceLevel,
        targetLevel: SignificanceLevel
    ): Boolean = when (targetLevel) {
        SignificanceLevel.REPORTING ->
            actualLevel == SignificanceLevel.REPORTING

        SignificanceLevel.SEED_EVOLUTION ->
            actualLevel == SignificanceLevel.REPORTING ||
                    actualLevel == SignificanceLevel.SEED_EVOLUTION

        else -> false
    }

    private fun measureSeedPerformance(
        seed: Seed,
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>
    ): List<Pair<JvmExecutor, PerformanceMetrics>> {
        val bytecodeEntry = seed.bytecodeEntry
        val outputFile = getBytecodeOutputFile(bytecodeEntry.packageName, bytecodeEntry.className)

        try {
            outputFile.parentFile?.mkdirs()
            outputFile.writeBytes(bytecodeEntry.bytecode)
        } catch (e: Exception) {
            println("Ошибка при записи байткода: ${e.message}")
            return emptyList()
        }

        return performanceMeasurer.measureAll(
            executors = jvmExecutors,
            classpath = MUTATIONS_DIR,
            packageName = bytecodeEntry.packageName,
            className = bytecodeEntry.className,
            quickMeasurement = false,
            jvmOptionsProvider = { executor ->
                jitAnalyzer?.let {
                    jvmOptions + it.jitOptionsProvider.getJITLoggingOptions(executor)
                } ?: jvmOptions
            }
        )
    }

    private fun getBytecodeOutputFile(packageName: String, className: String): File =
        File(File(MUTATIONS_DIR, packageName), "$className.class")

    private fun calculateReducedInterestingness(
        originalAnomalies: List<PerformanceAnomalyGroup>,
        previousInterestingness: Double
    ): Double {
        // Специальный случай для ложных срабатываний на память
        if (originalAnomalies.any { it.anomalyType == AnomalyGroupType.MEMORY }) {
            return 0.01
        }

        // Прогрессивное снижение интересности
        val reductionFactor = when {
            previousInterestingness > 1_000_000 -> 10_000.0
            previousInterestingness > 100_000 -> 5_000.0
            previousInterestingness > 10_000 -> 200.0
            previousInterestingness > 1_000 -> 10.0
            else -> INTEREST_REDUCTION_FACTOR
        }

        return previousInterestingness / reductionFactor
    }

    private fun handleConfirmedAnomalies(
        seed: Seed,
        confirmedAnomalies: List<PerformanceAnomalyGroup>,
        purpose: VerificationPurpose,
        newInterestingness: Double
    ): Int {
        logConfirmedAnomalies(seed, confirmedAnomalies, purpose, newInterestingness)

        if (purpose == VerificationPurpose.REPORTING) {
            anomalyRepository.saveSeedAnomalies(seed)
        }

        return confirmedAnomalies.size
    }

    private fun logDetailedCheckStart(topSeedsCount: Int) {
        println("Выполняем детальную проверку $topSeedsCount лучших сидов из ${pendingSeeds.size}...")
    }

    private fun logConfirmedAnomalies(
        seed: Seed,
        confirmedAnomalies: List<PerformanceAnomalyGroup>,
        purpose: VerificationPurpose,
        newInterestingness: Double
    ) {
        val anomalyCounts = formatAnomalyStatistics(confirmedAnomalies)
        println(
            "Аномалии подтверждены при точном измерении для ${purpose.name} " +
                    "(всего: ${confirmedAnomalies.size}, $anomalyCounts). " +
                    "Интересность обновлена до $newInterestingness. " +
                    "Энергия сида обновлена до ${seed.energy}."
        )
    }

    private fun logNoConfirmedAnomalies(
        seed: Seed,
        purpose: VerificationPurpose,
        previousInterestingness: Double,
        newInterestingness: Double
    ) {
        println(
            "Аномалии не подтвердились при точном измерении для ${purpose.name}. " +
                    "Интересность уменьшена с $previousInterestingness до $newInterestingness. " +
                    "Энергия сида уменьшена до ${seed.energy}."
        )
    }

    private fun formatAnomalyStatistics(anomalies: List<PerformanceAnomalyGroup>): String =
        AnomalyGroupType.entries
            .map { type -> type to anomalies.count { it.anomalyType == type } }
            .filter { (_, count) -> count > 0 }
            .joinToString(", ") { (type, count) ->
                "${type.toDisplayName()}: $count"
            }

    private fun AnomalyGroupType.toDisplayName(): String = when (this) {
        AnomalyGroupType.TIME -> "время"
        AnomalyGroupType.MEMORY -> "память"
        AnomalyGroupType.TIMEOUT -> "таймауты"
        AnomalyGroupType.ERROR -> "ошибки"
        AnomalyGroupType.JIT -> "JIT"
    }
}

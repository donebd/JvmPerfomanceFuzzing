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
import java.lang.Exception

/**
 * Верификатор аномалий производительности, выполняющий детальные измерения
 * для подтверждения обнаруженных аномалий.
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

    /**
     * Добавляет сид в очередь на верификацию
     *
     * @param seed сид для верификации
     */
    override fun addSeedForVerification(seed: Seed) {
        if (!seed.verified) {
            pendingSeeds.add(seed)
        }
    }

    /**
     * Проверяет, нужно ли выполнить детальную проверку аномалий
     *
     * @return true, если нужно выполнить проверку
     */
    override fun shouldPerformDetailedCheck(): Boolean {
        return iterationsSinceLastCheck >= periodBetweenDetailedChecks ||
                pendingSeeds.size >= minSeedsForDetailedCheck
    }

    /**
     * Обработчик новой итерации фаззинга
     */
    override fun onNewIteration() {
        iterationsSinceLastCheck++
    }

    /**
     * Возвращает количество сидов, ожидающих верификации
     *
     * @return количество ожидающих сидов
     */
    override fun getPendingSeedsCount(): Int {
        return pendingSeeds.size
    }

    /**
     * Выполняет детальную проверку сидов с наивысшей интересностью
     *
     * @param jvmExecutors список исполнителей JVM для тестирования
     * @param jvmOptions опции JVM для запуска
     * @return количество подтвержденных аномалий
     */
    override fun performDetailedCheck(
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>
    ): Int {
        if (pendingSeeds.isEmpty()) return 0

        val topSeeds = selectTopSeedsForVerification()
        logStartDetailedCheck(topSeeds.size)

        var confirmedAnomaliesCount = 0

        for (seed in topSeeds) {
            if (seed.anomalies.isNotEmpty()) {
                val confirmedCount = confirmAnomaliesAndUpdateSeed(
                    seed, jvmExecutors, jvmOptions, VerificationPurpose.SEED_VERIFICATION
                )
                confirmedAnomaliesCount += confirmedCount
            }
        }

        resetVerificationState()
        logVerificationComplete(confirmedAnomaliesCount)

        return confirmedAnomaliesCount
    }

    /**
     * Подтверждает аномалии для сида с использованием точных измерений
     *
     * @param seed сид для верификации
     * @param jvmExecutors список исполнителей JVM
     * @param jvmOptions опции JVM
     * @param purpose цель верификации
     * @return количество подтвержденных аномалий
     */
    override fun confirmAnomaliesAndUpdateSeed(
        seed: Seed,
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>,
        purpose: VerificationPurpose
    ): Int {
        logStartSeedVerification(purpose)

        val detailedMetrics = performDetailedMeasurements(seed, jvmExecutors, jvmOptions)
        val metricsMap = detailedMetrics.toMap()

        val significanceLevel = getSignificanceLevelForPurpose(purpose)
        val confirmedAnomalies = analyzeAndEnrichAnomalies(
            jvmExecutors, detailedMetrics, metricsMap, significanceLevel
        )

        val previousInterestingness = seed.interestingness
        val newInterestingness = calculateNewInterestingness(seed.anomalies, confirmedAnomalies, previousInterestingness)

        seed.updateWithVerificationResults(confirmedAnomalies, newInterestingness)

        return processVerificationResults(
            seed, confirmedAnomalies, purpose, previousInterestingness, newInterestingness
        )
    }

    // --- Вспомогательные методы для управления верификацией ---

    private fun selectTopSeedsForVerification(): List<Seed> {
        return pendingSeeds
            .sortedByDescending { it.interestingness }
            .take(countOfMostInterestedSeedsToVerify)
    }

    private fun resetVerificationState() {
        pendingSeeds.clear()
        iterationsSinceLastCheck = 0
    }

    private fun getSignificanceLevelForPurpose(purpose: VerificationPurpose): SignificanceLevel {
        return when (purpose) {
            VerificationPurpose.SEED_VERIFICATION -> SignificanceLevel.SEED_EVOLUTION
            VerificationPurpose.REPORTING -> SignificanceLevel.REPORTING
        }
    }

    // --- Методы работы с измерениями и анализом ---

    private fun performDetailedMeasurements(
        seed: Seed,
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>
    ): List<Pair<JvmExecutor, PerformanceMetrics>> {
        val bytecodeEntry = seed.bytecodeEntry
        val outputClassFile = prepareOutputClassFile(bytecodeEntry.packageName, bytecodeEntry.className)

        try {
            writeMutatedBytecode(bytecodeEntry.bytecode, outputClassFile)
        } catch (e: Exception) {
            println("Ошибка при записи байткода: ${e.message}")
            return emptyList()
        }

        return jvmExecutors.map { executor ->
            val jvmSpecificOptions = if (jitAnalyzer != null) {
                val jvmType = executor::class.simpleName ?: ""
                jvmOptions + jitAnalyzer.jitOptionsProvider.getJITLoggingOptions(jvmType)
            } else {
                jvmOptions
            }

            val metrics = performanceMeasurer.measure(
                executor,
                MUTATIONS_DIR,
                bytecodeEntry.packageName,
                bytecodeEntry.className,
                false,
                jvmSpecificOptions
            )
            executor to metrics
        }
    }

    private fun prepareOutputClassFile(packageName: String, className: String): File {
        val packageDir = File(MUTATIONS_DIR, packageName)
        return File(packageDir, "$className.class")
    }

    private fun writeMutatedBytecode(bytecode: ByteArray, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(bytecode)
    }

    private fun analyzeAndEnrichAnomalies(
        jvmExecutors: List<JvmExecutor>,
        detailedMetrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        metricsMap: Map<JvmExecutor, PerformanceMetrics>,
        significanceLevel: SignificanceLevel
    ): List<PerformanceAnomalyGroup> {
        val baseAnomalies = performanceAnalyzer.analyze(detailedMetrics, significanceLevel)

        return if (jitAnalyzer != null) {
            performanceAnalyzer.enrichAnomaliesWithJITFromLogs(
                baseAnomalies, jvmExecutors, metricsMap, jitAnalyzer
            )
        } else {
            baseAnomalies
        }
    }

    private fun calculateNewInterestingness(
        unconfirmedAnomalies: List<PerformanceAnomalyGroup>,
        confirmedAnomalies: List<PerformanceAnomalyGroup>,
        previousInterestingness: Double
    ): Double {
        return if (confirmedAnomalies.isNotEmpty() &&
            performanceAnalyzer.areAnomaliesInteresting(confirmedAnomalies)
        ) {
            performanceAnalyzer.calculateOverallInterestingness(confirmedAnomalies)
        } else {
            if (unconfirmedAnomalies.map { it.anomalyType }.contains(AnomalyGroupType.MEMORY) ) {
                return 0.01 // False cause of OS IO operations
            }
            val reductionFactor = when {
                previousInterestingness > 1000000 -> 10000.0
                previousInterestingness > 100000 -> 5000.0
                previousInterestingness > 10000 -> 200.0
                previousInterestingness > 1000 -> 10.0
                else -> INTEREST_REDUCTION_FACTOR
            }
            previousInterestingness / reductionFactor
        }
    }

    private fun processVerificationResults(
        seed: Seed,
        confirmedAnomalies: List<PerformanceAnomalyGroup>,
        purpose: VerificationPurpose,
        previousInterestingness: Double,
        newInterestingness: Double
    ): Int {
        if (confirmedAnomalies.isNotEmpty()) {
            logSuccessfulVerification(seed, confirmedAnomalies, purpose, newInterestingness)

            if (purpose == VerificationPurpose.REPORTING) {
                anomalyRepository.saveSeedAnomalies(seed)
            }

            return confirmedAnomalies.size
        } else {
            logFailedVerification(seed, purpose, previousInterestingness, newInterestingness)
            return 0
        }
    }

    // --- Методы логирования ---

    private fun logStartDetailedCheck(topSeedsCount: Int) {
        println("Выполняем детальную проверку $topSeedsCount лучших сидов из ${pendingSeeds.size}...")
    }

    private fun logVerificationComplete(confirmedCount: Int) {
        println("Детальная проверка завершена: подтверждено $confirmedCount аномалий.")
    }

    private fun logStartSeedVerification(purpose: VerificationPurpose) {
        println("Выполняем детальную проверку сида для ${purpose.name}...")
    }

    private fun logSuccessfulVerification(
        seed: Seed,
        confirmedAnomalies: List<PerformanceAnomalyGroup>,
        purpose: VerificationPurpose,
        newInterestingness: Double
    ) {
        val anomalyCounts = buildAnomalyCounts(confirmedAnomalies)

        println(
            """
            Аномалии подтверждены при точном измерении для ${purpose.name} 
            (всего: ${confirmedAnomalies.size}, ${anomalyCounts}). 
            Интересность обновлена до $newInterestingness.
            Энергия сида обновлена до ${seed.energy}.
            """.trimIndent().replace("\n", " ")
        )
    }

    private fun logFailedVerification(
        seed: Seed,
        purpose: VerificationPurpose,
        previousInterestingness: Double,
        newInterestingness: Double
    ) {
        println(
            """
            Аномалии не подтвердились при точном измерении для ${purpose.name}.
            Интересность уменьшена с $previousInterestingness до $newInterestingness.
            Энергия сида уменьшена до ${seed.energy}.
            """.trimIndent().replace("\n", " ")
        )
    }

    private fun buildAnomalyCounts(confirmedAnomalies: List<PerformanceAnomalyGroup>): String {
        val timeAnomalies = confirmedAnomalies.count { it.anomalyType == AnomalyGroupType.TIME }
        val memoryAnomalies = confirmedAnomalies.count { it.anomalyType == AnomalyGroupType.MEMORY }
        val timeoutAnomalies = confirmedAnomalies.count { it.anomalyType == AnomalyGroupType.TIMEOUT }
        val errorAnomalies = confirmedAnomalies.count { it.anomalyType == AnomalyGroupType.ERROR }
        val jitAnomalies = confirmedAnomalies.count { it.anomalyType == AnomalyGroupType.JIT }

        val parts = mutableListOf<String>()
        if (timeAnomalies > 0) parts.add("время: $timeAnomalies")
        if (memoryAnomalies > 0) parts.add("память: $memoryAnomalies")
        if (timeoutAnomalies > 0) parts.add("таймауты: $timeoutAnomalies")
        if (errorAnomalies > 0) parts.add("ошибки: $errorAnomalies")
        if (jitAnomalies > 0) parts.add("JIT: $jitAnomalies")

        return parts.joinToString(", ")
    }
}

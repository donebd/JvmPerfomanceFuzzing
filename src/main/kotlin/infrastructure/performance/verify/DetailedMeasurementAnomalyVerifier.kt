package infrastructure.performance.verify

import core.seed.Seed
import infrastructure.jvm.JvmExecutor
import infrastructure.performance.AnomalyType
import infrastructure.performance.PerformanceAnalyzer
import infrastructure.performance.PerformanceMeasurer
import infrastructure.performance.anomaly.AnomalyGroupType
import infrastructure.performance.anomaly.AnomalyRepository
import infrastructure.performance.anomaly.PerformanceAnomalyGroup
import infrastructure.performance.entity.PerformanceMetrics
import java.io.File

/**
 * Верификатор аномалий, использующий точные измерения для подтверждения
 */
class DetailedMeasurementAnomalyVerifier(
    private val performanceMeasurer: PerformanceMeasurer,
    private val performanceAnalyzer: PerformanceAnalyzer,
    private val anomalyRepository: AnomalyRepository,
    private val periodBetweenDetailedChecks: Int = 100,
    private val minSeedsForDetailedCheck: Int = 10,
    private val countOfMostInterestedSeedsToVerify: Int = 3
) : AnomalyVerifier {

    private val pendingSeedsForDetailedCheck = mutableSetOf<Seed>()
    private var iterationsSinceLastDetailedCheck: Int = 0

    override fun addSeedForVerification(seed: Seed) {
        if (!seed.verified) {
            pendingSeedsForDetailedCheck.add(seed)
        }
    }

    override fun shouldPerformDetailedCheck(): Boolean {
        return iterationsSinceLastDetailedCheck >= periodBetweenDetailedChecks ||
                pendingSeedsForDetailedCheck.size >= minSeedsForDetailedCheck
    }

    override fun onNewIteration() {
        iterationsSinceLastDetailedCheck++
    }

    override fun getPendingSeedsCount(): Int {
        return pendingSeedsForDetailedCheck.size
    }

    override fun performDetailedCheck(
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>
    ): Int {
        if (pendingSeedsForDetailedCheck.isEmpty()) {
            return 0
        }

        val topSeeds = pendingSeedsForDetailedCheck
            .sortedByDescending { it.interestingness }
            .take(countOfMostInterestedSeedsToVerify)

        println("Выполняем детальную проверку ${topSeeds.size} лучших сидов из ${pendingSeedsForDetailedCheck.size}...")

        var confirmedAnomaliesCount = 0

        for (seed in topSeeds) {
            // Проверяем существующие аномалии
            if (seed.anomalies.isNotEmpty()) {
                val confirmedCount = confirmAnomaliesAndUpdateSeed(
                    seed,
                    jvmExecutors,
                    jvmOptions,
                    VerificationPurpose.SEED_VERIFICATION
                )

                confirmedAnomaliesCount += confirmedCount
            }
        }

        // Полностью очищаем очередь сидов на верификацию
        pendingSeedsForDetailedCheck.clear()
        iterationsSinceLastDetailedCheck = 0

        println("Детальная проверка завершена: подтверждено $confirmedAnomaliesCount аномалий.")
        return confirmedAnomaliesCount
    }

    override fun confirmAnomaliesAndUpdateSeed(
        seed: Seed,
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>,
        purpose: VerificationPurpose
    ): Int {
        println("Выполняем детальную проверку сида для ${purpose.name} ...")

        // Выполняем измерения
        val detailedMetrics = performDetailedMeasurements(seed, jvmExecutors, jvmOptions)

        // Анализируем результаты с подходящим порогом для цели верификации
        val anomalyType = when (purpose) {
            VerificationPurpose.SEED_VERIFICATION -> AnomalyType.POTENTIAL
            VerificationPurpose.REPORTING -> AnomalyType.SIGNIFICANT
        }
        val confirmedAnomalies = performanceAnalyzer.analyze(detailedMetrics, anomalyType)

        val previousInterestingness = seed.interestingness

        // Определяем новую интересность
        val newInterestingness = calculateNewInterestingness(confirmedAnomalies, previousInterestingness)

        // Обновляем сид на основе результатов анализа
        seed.updateWithVerificationResults(
            confirmedAnomalies,
            newInterestingness
        )

        // Выводим результаты и при необходимости сохраняем
        return processVerificationResults(
            seed, confirmedAnomalies, purpose,
            previousInterestingness, newInterestingness
        )
    }

    private fun performDetailedMeasurements(
        seed: Seed,
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>
    ): List<Pair<JvmExecutor, PerformanceMetrics>> {
        val bytecodeEntry = seed.bytecodeEntry
        val classpath = File("sootOutput/mutated")
        val packageDir = File(classpath, bytecodeEntry.packageName)
        val outputClassFile = File(packageDir, "${bytecodeEntry.className}.class")

        writeMutatedBytecode(bytecodeEntry.bytecode, outputClassFile)

        return jvmExecutors.map { executor ->
            val metrics = performanceMeasurer.measure(
                executor, classpath, bytecodeEntry.packageName,
                bytecodeEntry.className, false, jvmOptions
            )
            executor to metrics
        }
    }

    private fun calculateNewInterestingness(
        confirmedAnomalies: List<PerformanceAnomalyGroup>,
        previousInterestingness: Double
    ): Double {
        return if (confirmedAnomalies.isNotEmpty() &&
            performanceAnalyzer.areAnomaliesInteresting(confirmedAnomalies)) {
            performanceAnalyzer.calculateOverallInterestingness(confirmedAnomalies)
        } else {
            // Уменьшаем интересность вдвое при неподтверждении
            previousInterestingness / 2.0
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
            logSuccessfulVerification(confirmedAnomalies, purpose, newInterestingness)

            if (purpose == VerificationPurpose.REPORTING) {
                anomalyRepository.saveSeedAnomalies(seed)
            }
            return confirmedAnomalies.size
        } else {
            println("Аномалии не подтвердились при точном измерении для ${purpose.name}. " +
                    "Интересность уменьшена с $previousInterestingness до $newInterestingness. ")
            return 0
        }
    }

    private fun logSuccessfulVerification(
        confirmedAnomalies: List<PerformanceAnomalyGroup>,
        purpose: VerificationPurpose,
        newInterestingness: Double
    ) {
        val timeAnomalies = confirmedAnomalies.count { it.anomalyType == AnomalyGroupType.TIME }
        val memoryAnomalies = confirmedAnomalies.count { it.anomalyType == AnomalyGroupType.MEMORY }
        val timeoutAnomalies = confirmedAnomalies.count { it.anomalyType == AnomalyGroupType.TIMEOUT }
        val errorAnomalies = confirmedAnomalies.count { it.anomalyType == AnomalyGroupType.ERROR }

        println("Аномалии подтверждены при точном измерении для ${purpose.name} " +
                "(всего: ${confirmedAnomalies.size}, время: $timeAnomalies, память: $memoryAnomalies, " +
                "таймауты: $timeoutAnomalies, ошибки: $errorAnomalies). " +
                "Интересность обновлена до $newInterestingness")
    }

    private fun writeMutatedBytecode(bytecode: ByteArray, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(bytecode)
    }
}

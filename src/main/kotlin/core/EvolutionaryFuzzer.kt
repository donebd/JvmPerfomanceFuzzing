package core

import core.mutation.AdaptiveMutator
import core.mutation.Mutator
import core.seed.BytecodeEntry
import core.seed.Seed
import core.seed.SeedManager
import infrastructure.jvm.JvmExecutor
import infrastructure.jvm.JITLoggingOptionsProvider
import infrastructure.performance.PerformanceAnalyzer
import infrastructure.performance.PerformanceMeasurer
import infrastructure.performance.verify.AnomalyVerifier
import infrastructure.performance.verify.VerificationPurpose
import infrastructure.performance.anomaly.AnomalyGroupType
import infrastructure.performance.entity.SignificanceLevel
import java.io.File
import kotlin.math.max

class EvolutionaryFuzzer(
    private val mutator: Mutator,
    private val performanceMeasurer: PerformanceMeasurer,
    private val performanceAnalyzer: PerformanceAnalyzer,
    private val seedManager: SeedManager,
    private val anomalyVerifier: AnomalyVerifier,
    private val jitOptionsProvider: JITLoggingOptionsProvider? = null,
    private val maxIterations: Int = 100_000,
    private val stagnationThreshold: Int = 1000,
    private val initialEnergy: Int = 10
) : Fuzzer {

    override fun fuzz(
        initialEntries: List<BytecodeEntry>, jvmExecutors: List<JvmExecutor>, jvmOptions: List<String>
    ) {
        // Инициализация начальных сидов
        val initialSeeds = initialEntries.mapIndexed { index, entry ->
            Seed(
                bytecodeEntry = entry,
                mutationHistory = mutableListOf(),
                energy = initialEnergy,
                description = entry.description.ifEmpty { "initial_$index" }
            )
        }

        val addedCount = seedManager.addInitialSeeds(initialSeeds)
        var iterations = 0
        var iterationsWithoutNewSeeds = 0
        var totalAnomaliesFound = 0

        println("Начинаем фаззинг с ${jvmExecutors.size} JVM и $addedCount начальными сидами...")

        while (iterations < maxIterations && iterationsWithoutNewSeeds < stagnationThreshold) {
            anomalyVerifier.onNewIteration()
            if (anomalyVerifier.shouldPerformDetailedCheck()) {
                val confirmedCount = anomalyVerifier.performDetailedCheck(
                    jvmExecutors, jvmOptions
                )

                totalAnomaliesFound += confirmedCount
            }

            val selectedSeed = seedManager.selectSeedForMutation()

            if (selectedSeed == null) {
                println("Пул сидов пуст, завершаем фаззинг")
                break
            }
            val bytecode = selectedSeed.bytecodeEntry.bytecode
            val className = selectedSeed.bytecodeEntry.className
            val packageName = selectedSeed.bytecodeEntry.packageName

            seedManager.decrementSeedEnergy(selectedSeed)

            println("Итерация #${iterations + 1} | Используем сид: ${selectedSeed.description} | Энергия: ${selectedSeed.energy} | Интересность: ${selectedSeed.interestingness}")

            val mutatedBytecode = mutator.mutate(bytecode, className, packageName)

            if (mutatedBytecode.contentEquals(bytecode)) {
                println("Мутация не произвела изменений, пропускаем")
                iterations++
                continue
            }

            val classpath = File("mutations")
            val packageDir = File(classpath, packageName)
            val outputClassFile = File(packageDir, "${className}.class")
            writeMutatedBytecode(mutatedBytecode, outputClassFile)

            val metrics = jvmExecutors.map { executor ->
                val jvmOptionsWithJIT = if (jitOptionsProvider != null) {
                    jvmOptions + jitOptionsProvider.getJITLoggingOptions(executor::class.simpleName ?: "")
                } else {
                    jvmOptions
                }
                val metrics =
                    performanceMeasurer.measure(executor, classpath, packageName, className, true, jvmOptionsWithJIT)
                executor to metrics
            }

            val (performanceAnomalies, jitResult) = performanceAnalyzer.analyzeWithJIT(
                metrics, SignificanceLevel.SEED_EVOLUTION
            )
            val significantAnomalies = performanceAnalyzer.analyze(metrics, SignificanceLevel.REPORTING)
            val jitInterestingness = jitResult.first

            val hasPerformanceAnomalies = performanceAnalyzer.areAnomaliesInteresting(performanceAnomalies)
            val hasJitAnomalies = jitInterestingness >= 15.0

            if (hasPerformanceAnomalies || hasJitAnomalies) {
                val overallInterestingness = if (hasPerformanceAnomalies) {
                    performanceAnalyzer.calculateOverallInterestingness(performanceAnomalies) + (jitInterestingness * 0.3)
                } else {
                    jitInterestingness
                }

                val newSeedEnergy = calculateEnergy(overallInterestingness)

                val mutationRecord = (mutator as? AdaptiveMutator)?.getLastMutationRecord()?.copy(
                    parentSeedDescription = selectedSeed.description
                )

                val newMutationHistory = if (mutationRecord != null) {
                    selectedSeed.mutationHistory + mutationRecord
                } else {
                    selectedSeed.mutationHistory
                }

                val newSeed = Seed(
                    bytecodeEntry = BytecodeEntry(mutatedBytecode, className, packageName),
                    mutationHistory = newMutationHistory,
                    energy = newSeedEnergy,
                    interestingness = overallInterestingness,
                    anomalies = performanceAnomalies,
                    iteration = iterations
                )

                val wasAdded = seedManager.addSeed(newSeed)

                if (wasAdded) {
                    // Создаем более информативное сообщение в логе
                    val jitAnomaliesCount = performanceAnomalies.count { it.anomalyType == AnomalyGroupType.JIT }
                    val perfInfo = if (hasPerformanceAnomalies) {
                        val timeAnomalies = performanceAnomalies.count { it.anomalyType == AnomalyGroupType.TIME }
                        val memoryAnomalies = performanceAnomalies.count { it.anomalyType == AnomalyGroupType.MEMORY }
                        val timeoutAnomalies = performanceAnomalies.count { it.anomalyType == AnomalyGroupType.TIMEOUT }
                        val errorAnomalies = performanceAnomalies.count { it.anomalyType == AnomalyGroupType.ERROR }

                        "производительность (всего: ${performanceAnomalies.size - jitAnomaliesCount}, " +
                                "время: $timeAnomalies, память: $memoryAnomalies, " +
                                "таймауты: $timeoutAnomalies, ошибки: $errorAnomalies)"
                    } else ""

                    val jitInfo = if (jitAnomaliesCount > 0) {
                        "JIT-аномалии: $jitAnomaliesCount"
                    } else ""

                    val separator = if (hasPerformanceAnomalies && hasJitAnomalies) ", " else ""

                    println(
                        "Найдены аномалии: $perfInfo$separator$jitInfo! " +
                                "Добавлен новый сид: ${newSeed.description} с энергией $newSeedEnergy и интересностью $overallInterestingness"
                    )

                    iterationsWithoutNewSeeds = 0
                    anomalyVerifier.addSeedForVerification(newSeed)

                    if (mutator is AdaptiveMutator) {
                        mutator.notifyNewSeedGenerated()
                    }

                    // Сохраняем только значимые аномалии в репозиторий
                    if (significantAnomalies.isNotEmpty() && performanceAnalyzer.areAnomaliesInteresting(
                            significantAnomalies
                        )
                    ) {
                        anomalyVerifier.confirmAnomaliesAndUpdateSeed(
                            newSeed, jvmExecutors, jvmOptions, VerificationPurpose.REPORTING
                        )
                    }
                } else {
                    println("Найдены аномалии, но сид не был добавлен (возможно дубликат)")
                    iterationsWithoutNewSeeds++

                    if (mutator is AdaptiveMutator) {
                        mutator.notifySeedRejected()
                    }
                }
            } else {
                println("Аномалий не обнаружено")
                iterationsWithoutNewSeeds++
            }

            iterations++
        }

        println("Фаззинг завершен. Всего итераций: $iterations, найдено аномалий: $totalAnomaliesFound")
        println("Финальный размер пула сидов: ${seedManager.getSeedCount()}")
    }

    /**
     * Определяет начальную энергию для нового сида на основе его интересности
     */
    private fun calculateEnergy(interestingness: Double): Int {
        return max(initialEnergy, (interestingness / 10.0).toInt())
    }

    private fun writeMutatedBytecode(bytecode: ByteArray, outputFile: File) {
        outputFile.parentFile?.mkdirs()
        outputFile.writeBytes(bytecode)
    }
}

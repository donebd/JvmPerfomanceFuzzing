package core

import core.mutation.AdaptiveMutator
import core.mutation.Mutator
import core.seed.BytecodeEntry
import core.seed.Seed
import core.seed.SeedManager
import infrastructure.jvm.JvmExecutor
import infrastructure.performance.AnomalyType
import infrastructure.performance.PerformanceAnalyzer
import infrastructure.performance.PerformanceMeasurer
import infrastructure.performance.verify.AnomalyVerifier
import infrastructure.performance.verify.VerificationPurpose
import infrastructure.performance.anomaly.AnomalyGroupType
import infrastructure.performance.anomaly.PerformanceAnomalyGroup
import java.io.File
import kotlin.math.max

class EvolutionaryFuzzer(
    private val mutator: Mutator,
    private val performanceMeasurer: PerformanceMeasurer,
    private val performanceAnalyzer: PerformanceAnalyzer,
    private val seedManager: SeedManager,
    private val anomalyVerifier: AnomalyVerifier,
    private val maxIterations: Int = 1000,
    private val stagnationThreshold: Int = 50,
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
                description = entry.description.ifEmpty { "initial_$index" })
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

            // Выбираем сид для мутации по стратегии
            val selectedSeed = seedManager.selectSeedForMutation()

            if (selectedSeed == null) {
                println("Пул сидов пуст, завершаем фаззинг")
                break
            }
            val bytecode = selectedSeed.bytecodeEntry.bytecode
            val className = selectedSeed.bytecodeEntry.className
            val packageName = selectedSeed.bytecodeEntry.packageName

            // Уменьшаем энергию выбранного сида
            seedManager.decrementSeedEnergy(selectedSeed)

            println("Итерация #${iterations + 1} | Используем сид: ${selectedSeed.description} | Энергия: ${selectedSeed.energy} | Интересность: ${selectedSeed.interestingness}")

            // Мутируем выбранный сид
            val mutatedBytecode = mutator.mutate(bytecode, className, packageName)

            // Если мутация не изменила байткод, пропускаем итерацию
            if (mutatedBytecode.contentEquals(bytecode)) {
                println("Мутация не произвела изменений, пропускаем")
                iterations++
                continue
            }

            // Сохраняем мутированный байткод
            val classpath = File("mutations")
            val packageDir = File(classpath, packageName)
            val outputClassFile = File(packageDir, "${className}.class")
            writeMutatedBytecode(mutatedBytecode, outputClassFile)

            // Запускаем и измеряем на всех JVM
            val metrics = jvmExecutors.map { executor ->
                val metrics = performanceMeasurer.measure(executor, classpath, packageName, className, true, jvmOptions)
                executor to metrics
            }

            // 1. Анализируем результаты на потенциальные аномалии (для выбора сидов)
            val potentialAnomalies = performanceAnalyzer.analyze(metrics, AnomalyType.POTENTIAL)

            // 2. Отдельно анализируем на значимые аномалии (для отчетности)
            val significantAnomalies = performanceAnalyzer.analyze(metrics, AnomalyType.SIGNIFICANT)

            // Проверяем интересность аномалий
            if (performanceAnalyzer.areAnomaliesInteresting(potentialAnomalies)) {
                // Получаем интересность для нового сида
                val newSeedInterestingness = performanceAnalyzer.calculateOverallInterestingness(potentialAnomalies)
                val newSeedEnergy = calculateEnergy(newSeedInterestingness)

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
                    interestingness = newSeedInterestingness,
                    anomalies = potentialAnomalies,
                    iteration = iterations
                )

                val wasAdded = seedManager.addSeed(newSeed)

                if (wasAdded) {
                    // Выводим более детальную информацию о найденных группах аномалий
                    val timeAnomalies =
                        potentialAnomalies.count { it.anomalyType == AnomalyGroupType.TIME }
                    val memoryAnomalies =
                        potentialAnomalies.count { it.anomalyType == AnomalyGroupType.MEMORY }
                    val timeoutAnomalies =
                        potentialAnomalies.count { it.anomalyType == AnomalyGroupType.TIMEOUT }
                    val errorAnomalies =
                        potentialAnomalies.count { it.anomalyType == AnomalyGroupType.ERROR }

                    println(
                        "Найдены потенциальные аномалии (всего: ${potentialAnomalies.size}, время: $timeAnomalies, память: $memoryAnomalies, " + "таймауты: $timeoutAnomalies, ошибки: $errorAnomalies)! " + "Добавлен новый сид: ${newSeed.description} с энергией $newSeedEnergy и интересностью $newSeedInterestingness"
                    )

                    iterationsWithoutNewSeeds = 0

                    anomalyVerifier.addSeedForVerification(newSeed)

                    if (mutator is AdaptiveMutator) {
                        mutator.notifyNewSeedGenerated(foundAnomaly = significantAnomalies.isNotEmpty())
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

package core

import core.mutation.Mutator
import core.seed.BytecodeEntry
import core.seed.Seed
import infrastructure.jvm.JvmExecutor
import infrastructure.performance.AnomalyType
import infrastructure.performance.PerformanceAnalyzer
import infrastructure.performance.PerformanceMeasurer
import infrastructure.performance.anomaly.AnomalyRepository
import java.io.File
import java.io.FileOutputStream

class SimpleFuzzer(
    private val mutator: Mutator,
    private val performanceMeasurer: PerformanceMeasurer,
    private val performanceAnalyzer: PerformanceAnalyzer,
)  {

    fun fuzz(
        bytecode: ByteArray,
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>,
        anomalyRepository: AnomalyRepository,
        className: String,
        packageName: String
    ) {
        var currentBytecode = bytecode
        var mutationCounter = 0

        while (mutationCounter < 1) {
            println("Мутация #${mutationCounter + 1}")

            val mutatedBytecode = mutator.mutate(currentBytecode, className, packageName)

            val classpath = File("mutations")
            val packageDir = File(classpath, packageName)
            val outputClassFile = File(packageDir, "${className}.class")
            writeMutatedBytecode(mutatedBytecode, outputClassFile)

            val metrics = jvmExecutors.map { executor ->
                val metrics = performanceMeasurer.measure(executor, classpath, packageName, className, true, jvmOptions)
                executor to metrics
            }

            // Используем SIGNIFICANT для простого фаззера, так как нас интересуют явные аномалии
            val anomalies = performanceAnalyzer.analyze(metrics, AnomalyType.SIGNIFICANT)

            if (anomalies.isNotEmpty() && performanceAnalyzer.areAnomaliesInteresting(anomalies)) {
                // Создаем сид с аномалиями для сохранения
                val interestingness = performanceAnalyzer.calculateOverallInterestingness(anomalies)
                val seed = Seed(
                    bytecodeEntry = BytecodeEntry(mutatedBytecode, className, packageName),
                    mutationHistory = listOf(),
                    energy = 10,
                    description = "simple_fuzzer_anomaly_$mutationCounter",
                    interestingness = interestingness,
                    anomalies = anomalies,
                    verified = true
                )

                // Сохраняем сид в репозиторий
                anomalyRepository.saveSeedAnomalies(seed)

                println("Найдены аномалии (${anomalies.size}), сохранены в репозитории.")
            } else {
                println("Аномалий не обнаружено.")
            }

            currentBytecode = mutatedBytecode
            mutationCounter++
        }

        println("Фаззинг завершен.")
    }

    private fun writeMutatedBytecode(bytecode: ByteArray, outputFile: File) {
        outputFile.parentFile?.mkdirs()

        FileOutputStream(outputFile).use { fos ->
            fos.write(bytecode)
            fos.flush()
            fos.fd.sync()
        }
    }
}

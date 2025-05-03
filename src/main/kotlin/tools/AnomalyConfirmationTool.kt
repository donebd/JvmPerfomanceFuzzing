package tools

import infrastructure.jvm.GraalVmExecutor
import infrastructure.jvm.HotSpotJvmExecutor
import infrastructure.jvm.JvmConfigReader
import infrastructure.jvm.OpenJ9JvmExecutor
import infrastructure.performance.PerformanceAnalyzerImpl
import infrastructure.performance.PerformanceMeasurerImpl
import infrastructure.performance.anomaly.AnomalyGroupType
import infrastructure.performance.anomaly.FileAnomalyRepository
import infrastructure.performance.entity.JmhOptions
import infrastructure.performance.entity.SignificanceLevel
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Утилита для автоматической верификации аномалий с высокой точностью измерений.
 * Сортирует аномалии по интересности, обрабатывает их последовательно и формирует отчет.
 */
class AnomalyConfirmationTool(
    private val potentialAnomaliesDir: String,
    private val confirmedAnomaliesDir: String,
    private val tempDir: String = "temp_verification",
    private val maxSeedsToVerify: Int = Int.MAX_VALUE,
    private val onlyTypes: List<AnomalyGroupType>? = null
) {
    private val configReader = JvmConfigReader()
    private val jvmExecutors = listOf(
        HotSpotJvmExecutor(configReader),
        GraalVmExecutor(configReader),
        OpenJ9JvmExecutor(configReader)
    )
    private val perfMeasurer = PerformanceMeasurerImpl()
    private val perfAnalyzer = PerformanceAnalyzerImpl()

    // Высокоточные настройки для верификации
    private val verificationJmhOptions = JmhOptions(
        2,
        20,
        5,
        40,
        3
    )

    private data class VerificationStats(
        var totalProcessed: Int = 0,
        var confirmed: Int = 0,
        var notConfirmed: Int = 0,
        var errors: Int = 0,
        var typeStats: MutableMap<AnomalyGroupType, TypeStat> = mutableMapOf(),
        var startTime: LocalDateTime = LocalDateTime.now(),
        var processingTimes: MutableList<Long> = mutableListOf()
    ) {
        data class TypeStat(
            var found: Int = 0,
            var confirmed: Int = 0
        )

        fun updateTypeStat(type: AnomalyGroupType, confirmed: Boolean) {
            val stat = typeStats.getOrPut(type) { TypeStat() }
            stat.found++
            if (confirmed) stat.confirmed++
        }

        fun addProcessingTime(timeMs: Long) {
            processingTimes.add(timeMs)
        }

        fun getAverageProcessingTimeSeconds(): Double {
            return if (processingTimes.isEmpty()) 0.0
            else processingTimes.average() / 1000.0
        }

        fun getRemainingTimeEstimate(remaining: Int): String {
            if (processingTimes.isEmpty() || remaining <= 0) return "неизвестно"

            val avgTimeMs = processingTimes.average()
            val totalRemainingMs = avgTimeMs * remaining
            val remainingMinutes = (totalRemainingMs / 60000).toInt()
            val remainingHours = remainingMinutes / 60

            return if (remainingHours > 0)
                "$remainingHours ч ${remainingMinutes % 60} мин"
            else
                "$remainingMinutes мин"
        }
    }

    fun run() {
        val stats = VerificationStats()
        val logFile = File("verification_log_${getCurrentTimestamp()}.txt")

        try {
            val potentialRepo = FileAnomalyRepository(potentialAnomaliesDir)
            val confirmedRepo = FileAnomalyRepository(confirmedAnomaliesDir)

            val potentialSeeds = potentialRepo.getAllSeeds()
                .filter { seed ->
                    onlyTypes == null || seed.anomalies.any { it.anomalyType in onlyTypes }
                }
                .sortedByDescending { it.interestingness }
                .take(maxSeedsToVerify)

            logMessage("Найдено ${potentialSeeds.size} потенциальных аномалий для верификации", logFile)
            logMessage("Настройки JMH: forks=${verificationJmhOptions.forks}, " +
                    "warmup=${verificationJmhOptions.warmupIterations}×${verificationJmhOptions.warmupTimeSeconds}s, " +
                    "measurement=${verificationJmhOptions.measurementIterations}×${verificationJmhOptions.measurementTimeSeconds}s",
                logFile)

            File(tempDir).apply {
                if (exists()) deleteRecursively()
                mkdirs()
            }

            potentialSeeds.forEach { seed ->
                val startTime = System.currentTimeMillis()
                stats.totalProcessed++

                logMessage("═════════════════════════════════════════════════════════════", logFile)
                logMessage("Верификация [${stats.totalProcessed}/${potentialSeeds.size}]: ${seed.description}", logFile)
                logMessage("Интересность: ${seed.interestingness}", logFile)
                logMessage("Типы аномалий: ${seed.anomalies.map { it.anomalyType }.distinct()}", logFile)

                try {
                    val packageDir = File(tempDir, seed.bytecodeEntry.packageName.replace('.', '/')).apply { mkdirs() }
                    val classFile = File(packageDir, "${seed.bytecodeEntry.className}.class")
                    classFile.writeBytes(seed.bytecodeEntry.bytecode)

                    val metrics = perfMeasurer.measureAll(
                        executors = jvmExecutors,
                        classpath = File(tempDir),
                        packageName = seed.bytecodeEntry.packageName,
                        className = seed.bytecodeEntry.className,
                        jmhOptions = verificationJmhOptions,
                        jvmOptionsProvider = { listOf() }
                    )

                    val analysisResult = perfAnalyzer.analyzeWithJIT(metrics)

                    seed.anomalies.forEach { anomaly ->
                        val confirmed = analysisResult.anomalies.any { it.anomalyType == anomaly.anomalyType }
                        stats.updateTypeStat(anomaly.anomalyType, confirmed)
                    }

                    if (analysisResult.isAnomaliesInteresting &&
                        analysisResult.significanceLevel == SignificanceLevel.REPORTING) {
                        val confirmedSeed = seed.copy(
                            anomalies = analysisResult.anomalies,
                            interestingness = analysisResult.interestingnessScore,
                            verified = true
                        )

                        confirmedRepo.saveSeedAnomalies(confirmedSeed)

                        logMessage("✅ ПОДТВЕРЖДЕНО с уровнем ${analysisResult.interestingnessScore}", logFile)
                        logMessage("Типы подтвержденных аномалий: ${analysisResult.anomalies.map { it.anomalyType }.distinct()}", logFile)

                        stats.confirmed++
                    } else {
                        logMessage("❌ НЕ ПОДТВЕРЖДЕНО (level: ${analysisResult.significanceLevel}, " +
                                "interesting: ${analysisResult.isAnomaliesInteresting})", logFile)
                        stats.notConfirmed++
                    }
                } catch (e: Exception) {
                    logMessage("❌ ОШИБКА: ${e.message}", logFile)
                    e.printStackTrace()
                    stats.errors++
                } finally {
                    File("$tempDir/${seed.bytecodeEntry.packageName.replace('.', '/')}").deleteRecursively()

                    val processingTime = System.currentTimeMillis() - startTime
                    stats.addProcessingTime(processingTime)

                    val remaining = potentialSeeds.size - stats.totalProcessed
                    logMessage("Прогресс: ${stats.totalProcessed}/${potentialSeeds.size}, " +
                            "Время: ${formatTime(processingTime)}, " +
                            "Осталось: ${stats.getRemainingTimeEstimate(remaining)}", logFile)

                    printInterimStats(stats, potentialSeeds.size, logFile)
                }
            }

            printFinalStats(stats, logFile)

        } catch (e: Exception) {
            logMessage("КРИТИЧЕСКАЯ ОШИБКА: ${e.message}", logFile)
            e.printStackTrace()
        } finally {
            File(tempDir).deleteRecursively()
        }
    }

    private fun printInterimStats(stats: VerificationStats, totalCount: Int, logFile: File) {
        val progress = (stats.totalProcessed * 100.0 / totalCount).toInt()
        val confirmedRate = if (stats.totalProcessed > 0)
            (stats.confirmed * 100.0 / stats.totalProcessed).toInt() else 0

        logMessage("Прогресс: $progress%, Подтверждено: $confirmedRate% (${stats.confirmed}/${stats.totalProcessed})", logFile)
    }

    private fun printFinalStats(stats: VerificationStats, logFile: File) {
        val duration = LocalDateTime.now().minute - stats.startTime.minute
        val confirmedRate = if (stats.totalProcessed > 0)
            (stats.confirmed * 100.0 / stats.totalProcessed).toInt() else 0

        logMessage("\n╔═══════════════════════════════════════════════════", logFile)
        logMessage("║ ИТОГОВЫЙ ОТЧЕТ ПО ВЕРИФИКАЦИИ АНОМАЛИЙ", logFile)
        logMessage("╠═══════════════════════════════════════════════════", logFile)
        logMessage("║ Всего обработано:      ${stats.totalProcessed}", logFile)
        logMessage("║ Подтверждено:          ${stats.confirmed} ($confirmedRate%)", logFile)
        logMessage("║ Не подтверждено:       ${stats.notConfirmed}", logFile)
        logMessage("║ Ошибки:                ${stats.errors}", logFile)
        logMessage("║ Среднее время проверки: ${String.format("%.2f", stats.getAverageProcessingTimeSeconds())} сек", logFile)
        logMessage("║ Общее время работы:     ${duration} мин", logFile)
        logMessage("╠═══════════════════════════════════════════════════", logFile)
        logMessage("║ СТАТИСТИКА ПО ТИПАМ АНОМАЛИЙ:", logFile)

        stats.typeStats.forEach { (type, stat) ->
            val confirmRate = if (stat.found > 0) (stat.confirmed * 100.0 / stat.found).toInt() else 0
            logMessage("║ ${type.name.padEnd(10)}: ${stat.confirmed}/${stat.found} подтверждено ($confirmRate%)", logFile)
        }

        logMessage("╚═══════════════════════════════════════════════════", logFile)
    }

    private fun logMessage(message: String, logFile: File) {
        println(message)
        logFile.appendText("${getCurrentTimestamp()} $message\n")
    }

    private fun getCurrentTimestamp(): String {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    }

    private fun formatTime(timeMs: Long): String {
        val seconds = timeMs / 1000
        return if (seconds < 60) "$seconds сек" else "${seconds / 60} мин ${seconds % 60} сек"
    }
}

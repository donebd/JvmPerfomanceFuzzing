package infrastructure.performance.anomaly

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import core.seed.Seed
import core.seed.Seed.Companion.clearSignificanceLevel
import infrastructure.jit.JITReportGenerator
import java.io.File
import java.nio.file.Files
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Хранилище аномалий производительности на файловой системе.
 * Сохраняет детальную информацию о сидах, их байткоде, обнаруженных аномалиях и JIT-данных.
 */
class FileAnomalyRepository(
    directoryPath: String,
    private val jitReportGenerator: JITReportGenerator = JITReportGenerator()
) : AnomalyRepository {
    private val objectMapper = jacksonObjectMapper()
    private val rootDir = File(directoryPath).apply { mkdirs() }
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override fun saveSeedAnomalies(seed: Seed) {
        if (seed.anomalies.isEmpty()) {
            println("Сид не содержит аномалий для сохранения")
            return
        }

        val timestamp = LocalDateTime.now().format(timestampFormatter)
        val seedDirName = createSeedDirectoryName(seed)
        val seedDir = File(rootDir, seedDirName).apply { mkdirs() }
        val directories = createDirectoryStructure(seedDir)

        saveSeedInfo(seed, directories.seedDir, timestamp)
        saveBytecode(seed, directories.bytecodeDir)
        saveAnomalies(seed, directories, timestamp)

        println("Сохранено ${seed.anomalies.size} аномалий для сида ${seed.description} в ${seedDir.absolutePath}")
    }

    override fun clear() {
        rootDir.listFiles()?.forEach { it.deleteRecursively() }
        println("Репозиторий аномалий очищен")
    }

    private data class DirectoryStructure(
        val seedDir: File,
        val anomaliesDir: File,
        val bytecodeDir: File,
        val jmhReportsDir: File
    )

    private fun createDirectoryStructure(seedDir: File): DirectoryStructure {
        val anomaliesDir = File(seedDir, "anomalies").apply { mkdirs() }
        val bytecodeDir = File(seedDir, "bytecode").apply { mkdirs() }
        val jmhReportsDir = File(seedDir, "jmh_reports").apply { mkdirs() }

        return DirectoryStructure(seedDir, anomaliesDir, bytecodeDir, jmhReportsDir)
    }

    private fun createSeedDirectoryName(seed: Seed): String {
        val bytecodeHash = seed.bytecodeEntry.bytecode.contentHashCode().toString(16)
        return "seed_${seed.description.replace(" ", "_").clearSignificanceLevel()}_${bytecodeHash}"
    }

    private fun saveSeedInfo(seed: Seed, seedDir: File, timestamp: String) {
        val seedInfo = mapOf(
            "description" to seed.description,
            "className" to seed.bytecodeEntry.className,
            "packageName" to seed.bytecodeEntry.packageName,
            "mutationHistory" to seed.mutationHistory,
            "interestingness" to seed.interestingness,
            "verified" to seed.verified,
            "timestamp" to timestamp,
            "anomaliesCount" to seed.anomalies.size
        )

        File(seedDir, "seed_info.json").writeText(
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(seedInfo)
        )
    }

    private fun saveBytecode(seed: Seed, bytecodeDir: File) {
        val bytecodeFile = File(bytecodeDir, "${seed.bytecodeEntry.className}.class")
        Files.write(bytecodeFile.toPath(), seed.bytecodeEntry.bytecode)
    }

    private fun saveAnomalies(seed: Seed, directories: DirectoryStructure, timestamp: String) {
        val timestampString = timestamp.replace(" ", "_").replace(":", "-")

        seed.anomalies.forEachIndexed { index, anomalyGroup ->
            val filePrefix = generateAnomalyFilePrefix(anomalyGroup, index)
            val anomalyFile = File(directories.anomaliesDir, "anomaly_${filePrefix}_$timestampString.json")

            val anomalyJson = if (anomalyGroup.anomalyType == AnomalyGroupType.JIT) {
                objectMapper.writeValueAsString(anomalyGroup)
            } else {
                objectMapper.writeValueAsString(anomalyGroup.copy(jitData = null))
            }

            anomalyFile.writeText(anomalyJson)
        }

        val reportsCopied: Int = copyJmhReports(seed, directories.jmhReportsDir)
        if (reportsCopied > 0) {
            println("Сохранено $reportsCopied JMH отчетов для сида ${seed.description}")
        }

        generateJitReports(seed, directories.anomaliesDir)
    }

    private fun copyJmhReports(seed: Seed, jmhReportsDir: File): Int {
        val jmhReports = mutableSetOf<String>()

        seed.anomalies.forEach { anomalyGroup ->
            val allJvms = anomalyGroup.fasterJvms + anomalyGroup.slowerJvms

            allJvms.forEach { jvmResult ->
                jvmResult.metrics.jmhReportPath?.let { reportPath ->
                    val sourceFile = File(reportPath)
                    if (sourceFile.exists()) {
                        val targetFileName =
                            "jmh_${anomalyGroup.anomalyType.name.lowercase()}_${jvmResult.jvmName}.json"
                        val targetFile = File(jmhReportsDir, targetFileName)
                        if (!targetFile.exists()) {
                            sourceFile.copyTo(targetFile, overwrite = true)
                            jmhReports.add(targetFileName)
                        }
                    }
                }
            }
        }

        return jmhReports.size
    }

    private fun generateJitReports(seed: Seed, anomaliesDir: File) {
        seed.anomalies
            .filter { it.jitData != null }
            .distinctBy { it.jitData.hashCode() }
            .forEachIndexed { index, anomalyGroup ->
                val jitDir = File(anomaliesDir, "jit_analysis").apply { mkdirs() }

                anomalyGroup.jitData?.let { jitData ->
                    val report = jitReportGenerator.generateMarkdownReport(jitData)
                    File(jitDir, "jit_report_$index.md").writeText(report)
                    File(jitDir, "jit_details_$index.json").writeText(
                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jitData)
                    )
                }
            }
    }

    private fun generateAnomalyFilePrefix(anomaly: PerformanceAnomalyGroup, index: Int): String {
        val anomalyType = anomaly.anomalyType.name.lowercase()

        val anomalyDescription = when (anomaly.anomalyType) {
            AnomalyGroupType.TIME, AnomalyGroupType.MEMORY -> {
                val deviation = "%.0f".format(anomaly.averageDeviation)
                "${anomalyType}_dev${deviation}pct"
            }

            AnomalyGroupType.TIMEOUT -> "timeout"

            AnomalyGroupType.ERROR -> {
                val exitCode = anomaly.exitCodes.values.firstOrNull() ?: "unknown"
                "error_code${exitCode}"
            }

            AnomalyGroupType.JIT -> {
                val probability = anomaly.jitData?.comparisons?.maxOfOrNull { it.jitRelatedProbability } ?: 0.0
                val probPercent = "%.0f".format(probability * 100)
                "jit_prob${probPercent}pct"
            }
        }

        return "${index}_${anomalyDescription}"
    }
}

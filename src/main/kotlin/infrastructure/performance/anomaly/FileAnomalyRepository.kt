package infrastructure.performance.anomaly

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import core.seed.Seed
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FileAnomalyRepository(private val directoryPath: String) : AnomalyRepository {
    private val objectMapper = jacksonObjectMapper()

    // Базовая директория
    private val rootDir get() = File(directoryPath)

    init {
        rootDir.mkdirs()
    }

    override fun saveSeedAnomalies(seed: Seed) {
        if (seed.anomalies.isEmpty()) {
            println("Сид не содержит аномалий для сохранения")
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val bytecode = seed.bytecodeEntry.bytecode

        // Создаем директорию для сида на основе его описания и хеша байткода
        val seedIdHash = bytecode.contentHashCode().toString(16)
        val seedDirName = "seed_${seed.description.replace(" ", "_")}_${seedIdHash}"
        val seedDir = File(rootDir, seedDirName)

        // Создаем подпапки для аномалий и байткода
        val anomaliesDir = File(seedDir, "anomalies")
        val bytecodeDir = File(seedDir, "bytecode")

        anomaliesDir.mkdirs()
        bytecodeDir.mkdirs()

        // Сохраняем информацию о сиде
        val seedInfoFile = File(seedDir, "seed_info.json")
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
        seedInfoFile.writeText(objectMapper.writeValueAsString(seedInfo))

        // Сохраняем байткод
        val bytecodeFile = File(bytecodeDir, "${seed.bytecodeEntry.className}.class")
        bytecodeFile.writeBytes(bytecode)

        // Сохраняем все аномалии сида
        seed.anomalies.forEachIndexed { index, anomalyGroup ->
            val filePrefix = generateFilePrefix(anomalyGroup, index, timestamp)
            val anomalyFile = File(anomaliesDir, "anomaly_${filePrefix}.json")
            anomalyFile.writeText(objectMapper.writeValueAsString(anomalyGroup))
        }

        println("Сохранено ${seed.anomalies.size} аномалий для сида ${seed.description} в ${seedDir.absolutePath}")
    }

    override fun clear() {
        rootDir.listFiles()?.forEach { it.deleteRecursively() }
        println("Репозиторий аномалий очищен")
    }

    private fun generateFilePrefix(anomaly: PerformanceAnomalyGroup, index: Int, timestamp: String): String {
        val anomalyType = anomaly.anomalyType.name.lowercase()

        // Формируем краткое описание аномалии в зависимости от типа
        val anomalyDescription = when (anomaly.anomalyType) {
            AnomalyGroupType.TIME, AnomalyGroupType.MEMORY -> {
                val deviation = String.format("%.0f", anomaly.averageDeviation)
                "${anomalyType}_dev${deviation}pct"
            }
            AnomalyGroupType.TIMEOUT -> "timeout"
            AnomalyGroupType.ERROR -> {
                val exitCode = anomaly.exitCodes.values.firstOrNull() ?: "unknown"
                "error_code${exitCode}"
            }
        }

        return "${index}_${anomalyDescription}_$timestamp"
    }
}

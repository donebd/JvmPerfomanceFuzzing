package core.seed

import core.mutation.MutationRecord
import infrastructure.performance.anomaly.AnomalyGroupType
import infrastructure.performance.anomaly.PerformanceAnomalyGroup

/**
 * Класс, представляющий сид для фаззинга
 */
data class Seed(
    val bytecodeEntry: BytecodeEntry,
    val mutationHistory: List<MutationRecord> = emptyList(),
    var energy: Int,
    val description: String,
    var interestingness: Double = 0.0,
    var anomalies: List<PerformanceAnomalyGroup> = emptyList(),
    val iteration: Int = 0,
    var verified: Boolean = false
) {

    constructor(
        bytecodeEntry: BytecodeEntry,
        mutationHistory: List<MutationRecord> = emptyList(),
        energy: Int,
        anomalies: List<PerformanceAnomalyGroup> = emptyList(),
        iteration: Int = 0,
        interestingness: Double = 0.0,
        verified: Boolean = false
    ) : this(
        bytecodeEntry,
        mutationHistory,
        energy,
        generateSeedDescription(anomalies, iteration),
        interestingness,
        anomalies,
        iteration,
        verified
    )

    fun updateWithVerificationResults(
        confirmedAnomalies: List<PerformanceAnomalyGroup>,
        newInterestingness: Double
    ) {
        this.anomalies = confirmedAnomalies
        this.interestingness = newInterestingness
        this.verified = confirmedAnomalies.isNotEmpty()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Seed

        return bytecodeEntry.bytecode.contentEquals(other.bytecodeEntry.bytecode)
    }

    override fun hashCode(): Int {
        return bytecodeEntry.bytecode.contentHashCode()
    }

    companion object {
        fun generateSeedDescription(
            anomalies: List<PerformanceAnomalyGroup>,
            iteration: Int
        ): String {
            // Формируем описание на основе типов аномалий и максимального отклонения
            val anomalyTypes = anomalies.map { it.anomalyType }.distinct().joinToString("_")

            // Находим максимальное отклонение среди всех аномалий
            val maxDeviation = anomalies
                .filter { it.anomalyType in listOf(AnomalyGroupType.TIME, AnomalyGroupType.MEMORY) }
                .maxOfOrNull { it.maxDeviation }
                ?.toInt() ?: 0

            // Добавляем информацию о таймаутах и ошибках, если они есть
            val hasTimeout = anomalies.any { it.anomalyType == AnomalyGroupType.TIMEOUT }
            val hasError = anomalies.any { it.anomalyType == AnomalyGroupType.ERROR }

            val deviationPart = if (maxDeviation > 0) "_dev${maxDeviation}pct" else ""
            val timeoutPart = if (hasTimeout) "_timeout" else ""
            val errorPart = if (hasError) "_error" else ""

            return "anomaly_${anomalyTypes}${deviationPart}${timeoutPart}${errorPart}_iter_${iteration}"
        }
    }
}

package core.seed

import core.mutation.MutationRecord
import infrastructure.performance.anomaly.AnomalyGroupType
import infrastructure.performance.anomaly.PerformanceAnomalyGroup
import kotlin.math.max

/**
 * Представляет единичный сид для фаззинга.
 * Хранит байткод, историю мутаций, метрики и подтвержденные аномалии.
 */
data class Seed(
    val bytecodeEntry: BytecodeEntry,
    val mutationHistory: List<MutationRecord> = emptyList(),
    var energy: Int,
    var description: String,
    var interestingness: Double = 0.0,
    var anomalies: List<PerformanceAnomalyGroup> = emptyList(),
    val iteration: Int = 0,
    var verified: Boolean = false
) {

    constructor(
        bytecodeEntry: BytecodeEntry,
        mutationHistory: List<MutationRecord> = emptyList(),
        anomalies: List<PerformanceAnomalyGroup> = emptyList(),
        iteration: Int = 0,
        interestingness: Double = 0.0,
        verified: Boolean = false
    ) : this(
        bytecodeEntry,
        mutationHistory,
        calculateEnergy(interestingness),
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
        anomalies = confirmedAnomalies
        interestingness = newInterestingness
        energy = calculateEnergy(newInterestingness)
        verified = confirmedAnomalies.isNotEmpty()
        description = generateSeedDescription(anomalies, iteration)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Seed) return false
        return bytecodeEntry.bytecode.contentEquals(other.bytecodeEntry.bytecode)
    }

    override fun hashCode(): Int {
        return bytecodeEntry.bytecode.contentHashCode()
    }

    companion object {
        private const val INITIAL_ENERGY = 10


        fun calculateEnergy(interestingness: Double): Int {
            return max(INITIAL_ENERGY, (interestingness / 10.0).toInt())
        }

        fun generateSeedDescription(
            anomalies: List<PerformanceAnomalyGroup>,
            iteration: Int
        ): String {
            val types = anomalies.map { it.anomalyType }.distinct().joinToString("_")

            val maxDeviation = anomalies
                .filter { it.anomalyType in listOf(AnomalyGroupType.TIME, AnomalyGroupType.MEMORY) }
                .maxOfOrNull { it.maxDeviation }
                ?.toInt() ?: 0

            val parts = buildList {
                if (maxDeviation > 0) add("_dev${maxDeviation}pct")
                if (anomalies.any { it.anomalyType == AnomalyGroupType.TIMEOUT }) add("_timeout")
                if (anomalies.any { it.anomalyType == AnomalyGroupType.ERROR }) add("_error")
                if (anomalies.any { it.anomalyType == AnomalyGroupType.JIT }) add("_jit")
            }

            return "anomaly_${types}${parts.joinToString("")}_iter_${iteration}"
        }
    }
}
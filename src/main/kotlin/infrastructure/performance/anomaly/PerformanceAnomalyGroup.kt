package infrastructure.performance.anomaly

import infrastructure.performance.entity.JvmPerformanceResult

/**
 * Представляет группу связанных аномалий производительности между различными JVM.
 * Содержит информацию о существенных различиях в производительности, их метриках и деталях.
 */
data class PerformanceAnomalyGroup(
    val anomalyType: AnomalyGroupType,

    val fasterJvms: List<JvmPerformanceResult>,
    val slowerJvms: List<JvmPerformanceResult>,

    // Метрики отклонений между группами в процентах
    val averageDeviation: Double,
    val maxDeviation: Double,
    val minDeviation: Double,
    /** Детализация отклонений для каждой пары JVM: [slower -> [faster -> deviation %]] */
    val pairwiseDeviations: Map<String, Map<String, Double>>,

    val description: String,
    val interestingnessScore: Double,

    val exitCodes: Map<String, Int> = emptyMap(),
    /** Данные об аномалиях JIT-компиляции, если применимо */
    val jitData: JITAnomalyData? = null
)

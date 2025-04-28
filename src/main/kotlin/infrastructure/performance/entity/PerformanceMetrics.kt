package infrastructure.performance.entity

import infrastructure.jvm.entity.JvmExecutionResult

/**
 * Метрики производительности, полученные при выполнении бенчмарка.
 * Содержит данные о времени выполнения, использовании памяти и результатах измерений.
 */
data class PerformanceMetrics(
    val avgMemoryUsageKb: Long,

    val avgTimeMs: Double,
    val minTimeMs: Double,
    val maxTimeMs: Double,
    val scoreError: Double,

    val successfullyParsed: Boolean,
    val jvmExecutionResult: JvmExecutionResult,
    val jmhReportPath: String? = null
)

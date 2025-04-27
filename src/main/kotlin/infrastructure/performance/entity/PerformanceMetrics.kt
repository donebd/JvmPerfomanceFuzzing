package infrastructure.performance.entity

import infrastructure.jvm.entity.JvmExecutionResult

data class PerformanceMetrics(
    val avgMemoryUsageKb: Long,
    val scoreError: Double,
    val minTimeMs: Double,
    val avgTimeMs: Double,
    val maxTimeMs: Double,
    val successfullyParsed: Boolean,
    val jvmExecutionResult: JvmExecutionResult,
    val jmhReportPath: String? = null
)
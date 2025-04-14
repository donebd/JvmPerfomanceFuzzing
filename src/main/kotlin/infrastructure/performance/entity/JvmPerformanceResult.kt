package infrastructure.performance.entity

data class JvmPerformanceResult(
    val jvmName: String,
    val metrics: PerformanceMetrics
)

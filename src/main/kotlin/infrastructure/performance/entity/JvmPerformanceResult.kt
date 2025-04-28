package infrastructure.performance.entity

/** Результаты измерения производительности для конкретной JVM */
data class JvmPerformanceResult(
    val jvmName: String,
    val metrics: PerformanceMetrics
)

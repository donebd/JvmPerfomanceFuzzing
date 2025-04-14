package infrastructure.performance

import infrastructure.jvm.JvmExecutor
import infrastructure.performance.anomaly.PerformanceAnomalyGroup
import infrastructure.performance.entity.PerformanceMetrics

interface PerformanceAnalyzer {
    /**
     * Сравнивает метрики производительности между различными JVM.
     *
     * @param metrics список метрик производительности для анализа.
     * @return список аномалий.
     */
    fun analyze(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        anomalyType: AnomalyType = AnomalyType.SIGNIFICANT
    ): List<PerformanceAnomalyGroup>

    fun areAnomaliesInteresting(anomalies: List<PerformanceAnomalyGroup>): Boolean

    fun calculateOverallInterestingness(anomalies: List<PerformanceAnomalyGroup>): Double
}

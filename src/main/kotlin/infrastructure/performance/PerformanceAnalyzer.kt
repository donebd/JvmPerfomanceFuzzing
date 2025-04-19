package infrastructure.performance

import infrastructure.jit.JITAnalyzer
import infrastructure.jvm.JvmExecutor
import infrastructure.performance.anomaly.JITAnomalyData
import infrastructure.performance.anomaly.PerformanceAnomalyGroup
import infrastructure.performance.entity.PerformanceMetrics
import infrastructure.performance.entity.SignificanceLevel

interface PerformanceAnalyzer {
    /**
     * Анализирует метрики производительности и выявляет аномалии.
     *
     * @param metrics список пар JvmExecutor и соответствующих метрик
     * @param significanceLevel уровень значимости для анализа
     * @return список групп аномалий
     */
    fun analyze(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        significanceLevel: SignificanceLevel
    ): List<PerformanceAnomalyGroup>

    /**
     * Определяет, представляют ли найденные аномалии интерес.
     */
    fun areAnomaliesInteresting(anomalies: List<PerformanceAnomalyGroup>): Boolean

    /**
     * Рассчитывает общую интересность набора аномалий.
     */
    fun calculateOverallInterestingness(anomalies: List<PerformanceAnomalyGroup>): Double

    /**
     * Анализирует JIT-логи и вычисляет JIT-интересность.
     *
     * @return пара (JIT-интересность, JIT-данные)
     */
    fun analyzeJIT(
        jvmExecutors: List<JvmExecutor>,
        metrics: Map<JvmExecutor, PerformanceMetrics>,
        significanceLevel: SignificanceLevel
    ): Pair<Double, JITAnomalyData?>

    /**
     * Выполняет комбинированный анализ производительности и JIT.
     *
     * @return пара (список аномалий, (JIT-интересность, JIT-данные))
     */
    fun analyzeWithJIT(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        significanceLevel: SignificanceLevel
    ): Pair<List<PerformanceAnomalyGroup>, Pair<Double, JITAnomalyData?>>

    /**
     * Обогащает существующие аномалии данными JIT-анализа.
     */
    fun enrichAnomaliesWithJIT(
        anomalies: List<PerformanceAnomalyGroup>,
        jitData: JITAnomalyData
    ): List<PerformanceAnomalyGroup>

    /**
     * Извлекает JIT-данные из логов и обогащает ими аномалии.
     */
    fun enrichAnomaliesWithJITFromLogs(
        anomalies: List<PerformanceAnomalyGroup>,
        jvmExecutors: List<JvmExecutor>,
        metrics: Map<JvmExecutor, PerformanceMetrics>,
        jitAnalyzer: JITAnalyzer
    ): List<PerformanceAnomalyGroup>
}

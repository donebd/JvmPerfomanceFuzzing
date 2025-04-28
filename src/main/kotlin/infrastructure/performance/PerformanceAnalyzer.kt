package infrastructure.performance

import infrastructure.jvm.JvmExecutor
import infrastructure.performance.anomaly.JITAnomalyData
import infrastructure.performance.entity.AnalysisResult
import infrastructure.performance.entity.ExtendedAnalysisResult
import infrastructure.performance.entity.PerformanceMetrics
import infrastructure.performance.entity.SignificanceLevel

/**
 * Анализатор производительности для выявления аномалий между JVM.
 */
interface PerformanceAnalyzer {
    /**
     * Анализирует метрики производительности с автоопределением уровня значимости.
     * Последовательно применяет уровни от REPORTING до SEED_EVOLUTION.
     *
     * @return структурированный результат с аномалиями и метаданными
     */
    fun analyze(metrics: List<Pair<JvmExecutor, PerformanceMetrics>>): AnalysisResult

    /**
     * Анализирует JIT-логи для оценки влияния JIT-компиляции на производительность.
     */
    fun analyzeJIT(
        jvmExecutors: List<JvmExecutor>,
        metrics: Map<JvmExecutor, PerformanceMetrics>,
        significanceLevel: SignificanceLevel
    ): Pair<Double, JITAnomalyData?>

    /**
     * Комбинированный анализ производительности и JIT-компиляции с автоопределением
     * значимости.
     */
    fun analyzeWithJIT(metrics: List<Pair<JvmExecutor, PerformanceMetrics>>): ExtendedAnalysisResult

}
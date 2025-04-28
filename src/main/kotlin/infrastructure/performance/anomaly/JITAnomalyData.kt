package infrastructure.performance.anomaly

import infrastructure.jit.model.JITComparisonResult
import infrastructure.jit.model.JITProfile

/**
 * Содержит данные об аномалиях в JIT-компиляции между различными JVM.
 * Включает как исходные профили компиляции, так и результаты их сравнения.
 */
data class JITAnomalyData(
    /** Профили JIT-компиляции для каждой JVM: [jvmName -> profile] */
    val profiles: Map<String, JITProfile>,

    /** Результаты сравнения профилей JIT-компиляции между парами JVM */
    val comparisons: List<JITComparisonResult>
)

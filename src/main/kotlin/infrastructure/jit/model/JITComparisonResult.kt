package infrastructure.jit.model

import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Результат сравнения JIT-компиляции между двумя JVM.
 * Содержит анализ различий в компиляции, влияющих на производительность.
 */
data class JITComparisonResult(
    @JsonIgnore val fasterJvmProfile: JITProfile,
    @JsonIgnore val slowerJvmProfile: JITProfile,

    val fasterJvmName: String,
    val slowerJvmName: String,

    /** Разница в эффективности компиляции между JVM (%) */
    val compilationEfficiencyDiff: Double,

    /** Оптимизации, выполненные только в быстрой JVM */
    val uniqueOptimizationsInFaster: List<String>,

    /** Оптимизации, выполненные только в медленной JVM */
    val uniqueOptimizationsInSlower: List<String>,

    /** Разница в уровне инлайнинга между JVM (%) */
    val inliningRateDiff: Double,

    /** Разница в скорости компиляции между JVM (%) */
    val compilationSpeedDiff: Double,

    /** Оценка вероятности что различие в производительности связано с JIT [0.0-1.0] */
    val jitRelatedProbability: Double,

    val analysisExplanation: String,
    val hotMethods: List<HotMethodAnalysis> = emptyList()
)

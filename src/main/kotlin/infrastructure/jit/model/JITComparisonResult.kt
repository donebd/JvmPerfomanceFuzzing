package infrastructure.jit.model

/**
 * Результат сравнения JIT-компиляции между двумя JVM.
 */
data class JITComparisonResult(
    val fasterJvmProfile: JITProfile,
    val slowerJvmProfile: JITProfile,
    val compilationEfficiencyDiff: Double,    // Разница в эффективности компиляции
    val uniqueOptimizationsInFaster: List<String>,
    val uniqueOptimizationsInSlower: List<String>,
    val inliningRateDiff: Double,             // Разница в проценте инлайнинга
    val compilationSpeedDiff: Double,         // Разница во времени компиляции
    val jitRelatedProbability: Double,        // Вероятность, что разница связана с JIT
    val analysisExplanation: String,           // Объяснение анализа
    val hotMethods: List<HotMethodAnalysis> = emptyList()
)

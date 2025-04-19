package infrastructure.jit.model

/**
 * Модель анализа "горячего" метода
 */
data class HotMethodAnalysis(
    val methodName: String,
    val fasterJvmCompileLevel: Int,
    val slowerJvmCompileLevel: Int,
    val fasterJvmDeoptimized: Boolean,
    val slowerJvmDeoptimized: Boolean,
    val fasterJvmInlinedCount: Int,
    val slowerJvmInlinedCount: Int,
    val hotnessScore: Double
)

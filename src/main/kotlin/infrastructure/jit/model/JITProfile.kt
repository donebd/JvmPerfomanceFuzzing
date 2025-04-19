package infrastructure.jit.model

/**
 * Собранный профиль JIT-компиляции для конкретной JVM.
 */
data class JITProfile(
    val jvmName: String,
    val compiledMethods: List<JITCompilationEvent>,
    val totalCompilations: Int,
    val totalCompilationTime: Long,
    val maxCompileLevel: Int,
    val inliningRate: Double,       // Отношение инлайнов к общему числу компиляций
    val deoptimizationCount: Int,
    val uniqueCompiledMethods: Int
)

package infrastructure.jit.model

/**
 * Представляет отдельное событие JIT-компиляции метода.
 */
data class JITCompilationEvent(
    val methodName: String,
    val signature: String?,
    val compileLevel: Int,           // Уровень компиляции (C1, C2, и т.д.)
    val timestamp: Long,             // Время компиляции
    val compileTime: Long,           // Затраченное на компиляцию время
    val bytecodeSize: Int = -1,      // Размер байткода
    val inlinedMethods: List<String> = emptyList(),
    val deoptimization: Boolean = false,
    val reason: String? = null       // Причина компиляции/деоптимизации
)

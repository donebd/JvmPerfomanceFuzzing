package infrastructure.jvm.entity

/**
 * Результат выполнения процесса JVM.
 */
data class JvmExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean
)

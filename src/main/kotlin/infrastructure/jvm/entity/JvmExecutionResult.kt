package infrastructure.jvm.entity

data class JvmExecutionResult(
    var exitCode: Int,      // Код возврата JVM
    val stdout: String,     // Вывод stdout
    val stderr: String,      // Вывод stderr
    val timedOut: Boolean
)

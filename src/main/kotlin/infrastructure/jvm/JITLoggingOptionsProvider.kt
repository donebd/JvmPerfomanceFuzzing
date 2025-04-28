package infrastructure.jvm

/**
 * Предоставляет набор опций для включения JIT-логирования в различных JVM.
 * Опции специфичны для каждой реализации JVM и могут зависеть от её версии.
 */
class JITLoggingOptionsProvider {

    fun getJITLoggingOptions(executor: JvmExecutor): List<String> {
        return when (executor) {
            is HotSpotJvmExecutor -> listOf(
                UNLOCK_DIAGNOSTIC_OPTIONS,
                "-XX:+PrintCompilation",
                "-XX:+PrintInlining"
                // Для сборок Development доступны дополнительные опции:
                // "-XX:+TraceDeoptimization"
                // Для расширенного анализа:
                // "-XX:+LogCompilation", "-XX:LogFile=jit-log.xml"
            )

            is OpenJ9JvmExecutor -> listOf(
                "-Xjit:verbose={compileStart|compileEnd|inlining|optimizer}"
            )

            is GraalVmExecutor -> listOf(
                UNLOCK_DIAGNOSTIC_OPTIONS,
                "-Dgraal.PrintCompilation=true",
                "-Dgraal.TraceInlining=true",
                "-Dgraal.TraceDeoptimization=true"
                // Не поддерживается в JVM 17:
                // "-Dgraal.DetailedMethodMetrics=true"
            )

            is AxiomJvmExecutor -> emptyList() // На данный момент опции не определены
            else -> emptyList()
        }
    }

    companion object {
        // Общие флаги
        const val UNLOCK_DIAGNOSTIC_OPTIONS = "-XX:+UnlockDiagnosticVMOptions"
    }
}

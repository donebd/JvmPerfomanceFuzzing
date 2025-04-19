package infrastructure.jvm

class JITLoggingOptionsProvider {
    fun getJITLoggingOptions(jvmType: String): List<String> {
        return when (jvmType) {
            "HotSpotJvmExecutor" -> listOf(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+PrintCompilation",
                "-XX:+PrintInlining",
                // "-XX:+TraceDeoptimization",
                // Опционально для еще более подробного анализа
                // "-XX:+LogCompilation", "-XX:LogFile=jit-log.xml"
            )

            "OpenJ9JvmExecutor" -> listOf(
                "-Xjit:verbose={compileStart|compileEnd|inlining|optimizer}"
            )

            "GraalVmExecutor" -> listOf(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+PrintCompilation"
            )

            "AxiomJvmExecutor" -> listOf(
                // Опции для Axiom JVM
            )

            else -> emptyList()
        }
    }
}

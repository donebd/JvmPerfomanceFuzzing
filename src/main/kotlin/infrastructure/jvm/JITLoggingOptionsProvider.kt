package infrastructure.jvm

class JITLoggingOptionsProvider {

    // Все флаги актуальные для 17 версии jvm и могут меняться в зависимости от неё
    fun getJITLoggingOptions(jvmType: String): List<String> {
        return when (jvmType) {
            "HotSpotJvmExecutor" -> listOf(
                "-XX:+UnlockDiagnosticVMOptions",
                "-XX:+PrintCompilation",
                "-XX:+PrintInlining",
//                "-XX:+TraceDeoptimization", // Only in develop build
                // Опционально для еще более подробного анализа
                // "-XX:+LogCompilation", "-XX:LogFile=jit-log.xml"
            )

            "OpenJ9JvmExecutor" -> listOf(
                "-Xjit:verbose={compileStart|compileEnd|inlining|optimizer}"
            )

            "GraalVmExecutor" -> listOf(
                "-XX:+UnlockDiagnosticVMOptions",
                "-Dgraal.PrintCompilation=true",
                "-Dgraal.TraceInlining=true",
                "-Dgraal.TraceDeoptimization=true",
//                "-Dgraal.DetailedMethodMetrics=true" // not supported in jvm 17
            )

            "AxiomJvmExecutor" -> listOf(
                // Опции для Axiom JVM
            )

            else -> emptyList()
        }
    }
}

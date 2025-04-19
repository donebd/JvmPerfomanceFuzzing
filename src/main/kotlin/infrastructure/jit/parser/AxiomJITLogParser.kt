package infrastructure.jit.parser

import infrastructure.jit.model.JITProfile

class AxiomJITLogParser : JITLogParser {
    override fun parseCompilationLogs(stdout: String, stderr: String): JITProfile {
        // Реализация парсера для Axiom
        // ...

        // Временная заглушка
        return JITProfile(
            jvmName = "Axiom",
            compiledMethods = emptyList(),
            totalCompilations = 0,
            totalCompilationTime = 0,
            maxCompileLevel = 0,
            inliningRate = 0.0,
            deoptimizationCount = 0,
            uniqueCompiledMethods = 0
        )
    }
}

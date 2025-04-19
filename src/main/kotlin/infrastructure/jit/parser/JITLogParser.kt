package infrastructure.jit.parser

import infrastructure.jit.model.JITProfile

interface JITLogParser {
    fun parseCompilationLogs(stdout: String, stderr: String): JITProfile
}

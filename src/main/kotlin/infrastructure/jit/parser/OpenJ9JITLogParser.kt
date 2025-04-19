package infrastructure.jit.parser

import infrastructure.jit.model.JITCompilationEvent
import infrastructure.jit.model.JITProfile

class OpenJ9JITLogParser : JITLogParser {

    companion object {
        private val COMPILATION_REGEX =
            """\+\s+\((cold|warm|hot)\)\s+([\w/$]+)\.([\w$]+)(\(.*?\)).*?@\s+([\dA-F]+)-([\dA-F]+)""".toRegex()
        private val DEOPT_REGEX =
            """!\s+\((cold|warm|hot)\)\s+([\w/$]+)\.([\w$]+)(\(.*?\)).*?decompiled.*?@\s+([\dA-F]+)-([\dA-F]+)""".toRegex()
        private val INLINING_REGEX =
            """<\s+\((cold|warm|hot)\)\s+inlining\s+([\w/$]+)\.([\w$]+)(\(.*?\))\s+into\s+([\w/$]+)\.([\w$]+)""".toRegex()
        private val TIME_REGEX = """(\d+)ms""".toRegex()
    }

    override fun parseCompilationLogs(stdout: String, stderr: String): JITProfile {
        val events = mutableListOf<JITCompilationEvent>()
        val inlinedMethodsMap = mutableMapOf<String, MutableList<String>>()
        var totalCompilationTime = 0L

        val lines = stdout.lines() + stderr.lines()

        for (line in lines) {
            parseCompilation(line, events)
            parseDeoptimization(line, events)
            parseInlining(line, inlinedMethodsMap)
            parseCompilationTime(line)?.let { totalCompilationTime += it }
        }

        val updatedEvents = addInliningInfoToEvents(events, inlinedMethodsMap)

        return createProfile(updatedEvents, totalCompilationTime, inlinedMethodsMap)
    }

    private fun parseCompilation(line: String, events: MutableList<JITCompilationEvent>) {
        val match = COMPILATION_REGEX.find(line) ?: return

        val warmth = match.groupValues[1]
        val className = match.groupValues[2].replace('/', '.')
        val methodName = match.groupValues[3]
        val signature = match.groupValues[4]
        val fullMethodName = "$className.$methodName"

        events.add(
            JITCompilationEvent(
                methodName = fullMethodName,
                signature = signature,
                compileLevel = warmthToLevel(warmth),
                timestamp = System.currentTimeMillis(),
                compileTime = 0,
                bytecodeSize = -1,
                deoptimization = false
            )
        )
    }

    private fun parseDeoptimization(line: String, events: MutableList<JITCompilationEvent>) {
        val match = DEOPT_REGEX.find(line) ?: return

        val className = match.groupValues[2].replace('/', '.')
        val methodName = match.groupValues[3]
        val signature = match.groupValues[4]
        val fullMethodName = "$className.$methodName"

        val existingIndex = events.indexOfFirst { it.methodName == fullMethodName }
        if (existingIndex >= 0) {
            events[existingIndex] = events[existingIndex].copy(deoptimization = true)
        } else {
            events.add(
                JITCompilationEvent(
                    methodName = fullMethodName,
                    signature = signature,
                    compileLevel = 0,
                    timestamp = System.currentTimeMillis(),
                    compileTime = 0,
                    bytecodeSize = -1,
                    deoptimization = true
                )
            )
        }
    }

    private fun parseInlining(line: String, inlinedMethodsMap: MutableMap<String, MutableList<String>>) {
        val match = INLINING_REGEX.find(line) ?: return

        val inlinedClass = match.groupValues[2].replace('/', '.')
        val inlinedMethod = match.groupValues[3]
        val targetClass = match.groupValues[5].replace('/', '.')
        val targetMethod = match.groupValues[6]

        val inlinedFullName = "$inlinedClass.$inlinedMethod"
        val targetFullName = "$targetClass.$targetMethod"

        inlinedMethodsMap.getOrPut(targetFullName) { mutableListOf() }.add(inlinedFullName)
    }

    private fun parseCompilationTime(line: String): Long? {
        if (!line.contains("compilation took") && !line.contains("compile time")) return null

        val match = TIME_REGEX.find(line) ?: return null
        return match.groupValues[1].toLongOrNull()
    }

    private fun warmthToLevel(warmth: String): Int {
        return when (warmth) {
            "cold" -> 1
            "warm" -> 2
            "hot" -> 3
            else -> 1
        }
    }

    private fun addInliningInfoToEvents(
        events: List<JITCompilationEvent>,
        inlinedMethodsMap: Map<String, List<String>>
    ): List<JITCompilationEvent> {
        return events.map { event ->
            event.copy(inlinedMethods = inlinedMethodsMap[event.methodName] ?: emptyList())
        }
    }

    private fun createProfile(
        events: List<JITCompilationEvent>,
        totalCompilationTime: Long,
        inlinedMethodsMap: Map<String, List<String>>
    ): JITProfile {
        val deoptCount = events.count { it.deoptimization }
        val totalInlines = inlinedMethodsMap.values.sumOf { it.size }
        val inliningRate = if (events.isNotEmpty()) totalInlines.toDouble() / events.size else 0.0

        return JITProfile(
            jvmName = "OpenJ9",
            compiledMethods = events,
            totalCompilations = events.size,
            totalCompilationTime = totalCompilationTime,
            maxCompileLevel = 3, // OpenJ9 использует уровни cold(1), warm(2), hot(3)
            inliningRate = inliningRate,
            deoptimizationCount = deoptCount,
            uniqueCompiledMethods = events.map { it.methodName }.distinct().size
        )
    }
}

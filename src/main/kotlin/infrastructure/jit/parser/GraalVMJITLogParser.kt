package infrastructure.jit.parser

import infrastructure.jit.model.JITCompilationEvent
import infrastructure.jit.model.JITProfile

class GraalVMJITLogParser : JITLogParser {

    companion object {
        private val COMPILATION_REGEX =
            """\[GraalCompiler-\d+\].*?Compiled method\s+([\w\.]+)::([\w<>]+).*?\((\d+) bytes\)""".toRegex()
        private val INLINING_REGEX =
            """\[GraalCompiler-\d+\].*?Inlining\s+([\w\.]+)::([\w<>]+)\s+into\s+([\w\.]+)::([\w<>]+)""".toRegex()
        private val DEOPT_REGEX = """\[GraalCompiler-\d+\].*?Deoptimizing\s+([\w\.]+)::([\w<>]+)""".toRegex()
        private val TIME_REGEX = """\[GraalCompiler-\d+\].*?Took (\d+)ms""".toRegex()

        // Уровень компиляции в GraalVM - считаем эквивалентом C2
        private const val GRAAL_COMPILE_LEVEL = 4
    }

    override fun parseCompilationLogs(stdout: String, stderr: String): JITProfile {
        val logLines = stdout.lines() + stderr.lines()

        // Извлекаем основные компоненты данных
        val (events, deoptCount) = parseCompilationEvents(logLines)
        val inlinedMethodsMap = parseInliningInfo(logLines)
        val compileTime = parseCompilationTime(logLines)

        // Обновляем события информацией об инлайнинге
        val updatedEvents = addInliningInfoToEvents(events, inlinedMethodsMap)

        return createJITProfile(updatedEvents, compileTime, deoptCount, inlinedMethodsMap)
    }

    private fun parseCompilationEvents(logLines: List<String>): Pair<List<JITCompilationEvent>, Int> {
        val events = mutableListOf<JITCompilationEvent>()
        var deoptCount = 0

        for (line in logLines) {
            val compileMatch = COMPILATION_REGEX.find(line)
            if (compileMatch != null) {
                val event = createCompilationEvent(compileMatch)
                events.add(event)
                continue
            }

            val deoptMatch = DEOPT_REGEX.find(line)
            if (deoptMatch != null) {
                deoptCount++
                markMethodAsDeoptimized(events, deoptMatch)
            }
        }

        return Pair(events, deoptCount)
    }

    private fun createCompilationEvent(match: MatchResult): JITCompilationEvent {
        val className = match.groupValues[1]
        val methodName = match.groupValues[2]
        val bytecodeSize = match.groupValues[3].toIntOrNull() ?: -1
        val fullMethodName = "$className.$methodName"

        return JITCompilationEvent(
            methodName = fullMethodName,
            signature = null, // GraalVM обычно не показывает сигнатуру
            compileLevel = GRAAL_COMPILE_LEVEL,
            timestamp = System.currentTimeMillis(),
            compileTime = 0,
            bytecodeSize = bytecodeSize,
            deoptimization = false
        )
    }

    private fun markMethodAsDeoptimized(events: MutableList<JITCompilationEvent>, match: MatchResult) {
        val className = match.groupValues[1]
        val methodName = match.groupValues[2]
        val fullMethodName = "$className.$methodName"

        val index = events.indexOfFirst { it.methodName == fullMethodName }
        if (index >= 0) {
            events[index] = events[index].copy(deoptimization = true)
        }
    }

    private fun parseInliningInfo(logLines: List<String>): Map<String, List<String>> {
        val inlinedMethodsMap = mutableMapOf<String, MutableList<String>>()

        for (line in logLines) {
            val inliningMatch = INLINING_REGEX.find(line) ?: continue

            val inlinedClass = inliningMatch.groupValues[1]
            val inlinedMethod = inliningMatch.groupValues[2]
            val targetClass = inliningMatch.groupValues[3]
            val targetMethod = inliningMatch.groupValues[4]

            val inlinedFullName = "$inlinedClass.$inlinedMethod"
            val targetFullName = "$targetClass.$targetMethod"

            inlinedMethodsMap.getOrPut(targetFullName) { mutableListOf() }.add(inlinedFullName)
        }

        return inlinedMethodsMap
    }

    private fun parseCompilationTime(logLines: List<String>): Long {
        var compileTime = 0L

        for (line in logLines) {
            val timeMatch = TIME_REGEX.find(line) ?: continue
            compileTime += timeMatch.groupValues[1].toLongOrNull() ?: 0
        }

        return compileTime
    }

    private fun addInliningInfoToEvents(
        events: List<JITCompilationEvent>,
        inlinedMethodsMap: Map<String, List<String>>
    ): List<JITCompilationEvent> {
        return events.map { event ->
            event.copy(inlinedMethods = inlinedMethodsMap[event.methodName] ?: emptyList())
        }
    }

    private fun createJITProfile(
        events: List<JITCompilationEvent>,
        compileTime: Long,
        deoptCount: Int,
        inlinedMethodsMap: Map<String, List<String>>
    ): JITProfile {
        val totalInlines = inlinedMethodsMap.values.sumOf { it.size }
        val inliningRate = if (events.isNotEmpty()) totalInlines.toDouble() / events.size else 0.0

        return JITProfile(
            jvmName = "GraalVM",
            compiledMethods = events,
            totalCompilations = events.size,
            totalCompilationTime = compileTime,
            maxCompileLevel = GRAAL_COMPILE_LEVEL,
            inliningRate = inliningRate,
            deoptimizationCount = deoptCount,
            uniqueCompiledMethods = events.map { it.methodName }.distinct().size
        )
    }
}

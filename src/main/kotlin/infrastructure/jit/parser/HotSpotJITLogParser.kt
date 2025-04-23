package infrastructure.jit.parser

import infrastructure.jit.model.JITCompilationEvent
import infrastructure.jit.model.JITProfile

class HotSpotJITLogParser : JITLogParser {

    companion object {
        private val COMPILATION_REGEX = """(\d+)\s+(\d+)([! ]?)\s+(\d+)\s+([\w\.$]+)::([\w<>]+).*?\((\d+) bytes\)""".toRegex()
        private val INLINING_REGEX = """^\s+\@\s+\d+\s+([\w\.$]+)::([\w<>]+).*?(?:inline|intrinsic)""".toRegex(RegexOption.MULTILINE)
        private val COMPILATION_TIME_REGEX = """compile time\s+(\d+)ms""".toRegex()
        private val DEOPT_NOT_ENTRANT_REGEX = """([\w\.$]+)::([\w<>]+).*made not entrant""".toRegex()
    }

    override fun parseCompilationLogs(stdout: String, stderr: String): JITProfile {
        val logLines = stdout.lines() + stderr.lines()

        // Первый проход - извлечение событий компиляции
        val compilationEvents = parseCompilationEvents(logLines)

        // Второй проход - извлечение инлайнинга и времени компиляции
        val inliningInfo = parseInliningInfo(logLines, compilationEvents)
        val totalCompilationTime = parseCompilationTime(logLines)

        // Обновление событий с данными об инлайнинге
        val updatedEvents = enrichEventsWithInlining(compilationEvents, inliningInfo)

        return createJITProfile(updatedEvents, totalCompilationTime, inliningInfo)
    }

    private fun parseCompilationEvents(logLines: List<String>): List<JITCompilationEvent> {
        val events = mutableListOf<JITCompilationEvent>()

        for (line in logLines) {
            val match = COMPILATION_REGEX.find(line) ?: continue

            val isDeopt = match.groupValues[2] == "!"

            val className = match.groupValues[5]
            val methodName = match.groupValues[6]
            val signature = null
            val compileLevel = match.groupValues[4].toIntOrNull() ?: 1
            val bytecodeSize = match.groupValues[7].toIntOrNull() ?: -1

            events.add(JITCompilationEvent(
                methodName = "$className.$methodName",
                signature = signature,
                compileLevel = compileLevel,
                timestamp = System.currentTimeMillis(),
                compileTime = 0,
                bytecodeSize = bytecodeSize,
                deoptimization = isDeopt
            ))
        }

        for (line in logLines) {
            val deoptMatch = DEOPT_NOT_ENTRANT_REGEX.find(line)
            if (deoptMatch != null) {
                val dClassName = deoptMatch.groupValues[1]
                val dMethodName = deoptMatch.groupValues[2]
                val methodKey = "$dClassName.$dMethodName"

                // Найти метод и отметить как деоптимизированный
                val index = events.indexOfFirst { it.methodName == methodKey }
                if (index >= 0) {
                    events[index] = events[index].copy(deoptimization = true)
                }
            }
        }


        return events
    }

    private fun parseInliningInfo(
        logLines: List<String>,
        events: List<JITCompilationEvent>
    ): Map<String, List<String>> {
        val inliningMap = mutableMapOf<String, MutableList<String>>()

        if (events.isEmpty()) return inliningMap

        var currentMethod = events.first().methodName

        for (line in logLines) {
            // Проверяем, не начался ли новый метод
            val compileMatch = COMPILATION_REGEX.find(line)
            if (compileMatch != null) {
                val className = compileMatch.groupValues[5]
                val methodName = compileMatch.groupValues[6]
                currentMethod = "$className.$methodName"
                continue
            }

            // Ищем информацию об инлайнинге
            val inliningMatch = INLINING_REGEX.find(line)
            if (inliningMatch != null) {
                val className = inliningMatch.groupValues[1]
                val methodName = inliningMatch.groupValues[2]
                val inlinedMethod = "$className.$methodName"

                // Добавляем инлайн к текущему методу
                inliningMap.getOrPut(currentMethod) { mutableListOf() }.add(inlinedMethod)
            }
        }

        return inliningMap
    }

    private fun parseCompilationTime(logLines: List<String>): Long {
        var totalTime = 0L

        for (line in logLines) {
            val match = COMPILATION_TIME_REGEX.find(line)
            if (match != null) {
                totalTime += match.groupValues[1].toLongOrNull() ?: 0
            }
        }

        return totalTime
    }

    private fun enrichEventsWithInlining(
        events: List<JITCompilationEvent>,
        inliningInfo: Map<String, List<String>>
    ): List<JITCompilationEvent> {
        return events.map { event ->
            event.copy(inlinedMethods = inliningInfo[event.methodName] ?: emptyList())
        }
    }

    private fun createJITProfile(
        events: List<JITCompilationEvent>,
        totalCompilationTime: Long,
        inliningInfo: Map<String, List<String>>
    ): JITProfile {
        val deoptCount = events.count { it.deoptimization }
        val totalInlines = inliningInfo.values.sumOf { it.size }
        val inliningRate = if (events.isNotEmpty()) totalInlines.toDouble() / events.size else 0.0

        return JITProfile(
            jvmName = "HotSpot",
            compiledMethods = events,
            totalCompilations = events.size,
            totalCompilationTime = totalCompilationTime,
            maxCompileLevel = events.maxOfOrNull { it.compileLevel } ?: 0,
            inliningRate = inliningRate,
            deoptimizationCount = deoptCount,
            uniqueCompiledMethods = events.map { it.methodName }.distinct().size
        )
    }
}

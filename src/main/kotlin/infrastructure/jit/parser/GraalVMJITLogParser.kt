package infrastructure.jit.parser

import infrastructure.jit.model.JITCompilationEvent
import infrastructure.jit.model.JITProfile

class GraalVMJITLogParser : JITLogParser {

    companion object {
        private val COMPILATION_REGEX =
            """HotSpotCompilation-(\d+)\s+L([\w/$]+);\s+([\w<>$]+)\s+((?:\(.*?\)))\s+\|\s+(\d+)us\s+(\d+)B bytecodes""".toRegex()

        private val COMPILATION_BLOCK_START_REGEX =
            """compilation of ([\w\.]+)\.([\w<>$]+)(?:\(.*?\))?:""".toRegex()

        private val INLINING_REGEX =
            """└──<PriorityInliningPhase> ([\w\.]+)\.([\w<>$]+)(?:\(.*?\))?.*?: yes""".toRegex()

        private val DEOPT_REGEX = """made not entrant""".toRegex()

        // Уровень компиляции в GraalVM - считаем эквивалентом C2
        private const val GRAAL_COMPILE_LEVEL = 4
    }

    override fun parseCompilationLogs(stdout: String, stderr: String): JITProfile {
        val combinedLog = stdout + stderr

        // Извлекаем основные компоненты данных
        val compilationEvents = parseCompilationEvents(combinedLog)
        val inlinedMethodsMap = parseInliningInfo(combinedLog)
        val compileTime = calculateTotalCompileTime(compilationEvents)

        // Обновляем события информацией об инлайнинге
        val updatedEvents = addInliningInfoToEvents(compilationEvents, inlinedMethodsMap)

        return createJITProfile(updatedEvents, compileTime, inlinedMethodsMap)
    }

    private fun parseCompilationEvents(log: String): List<JITCompilationEvent> {
        val events = mutableListOf<JITCompilationEvent>()

        val compileMatches = COMPILATION_REGEX.findAll(log)
        for (match in compileMatches) {
            val id = match.groupValues[1]
            val className = match.groupValues[2].replace('/', '.')
            val methodName = match.groupValues[3]
            val signature = match.groupValues[4]
            val compileTimeUs = match.groupValues[5].toLongOrNull() ?: 0
            val bytecodeSize = match.groupValues[6].toIntOrNull() ?: -1

            val fullMethodName = "$className.$methodName"
            val isDeoptimized = log.contains("$id.*$fullMethodName.*$DEOPT_REGEX")

            events.add(JITCompilationEvent(
                methodName = fullMethodName,
                signature = signature,
                compileLevel = GRAAL_COMPILE_LEVEL,
                timestamp = System.currentTimeMillis(),
                compileTime = compileTimeUs / 1000, // микросекунды в миллисекунды
                bytecodeSize = bytecodeSize,
                deoptimization = isDeoptimized
            ))
        }

        return events
    }

    private fun parseInliningInfo(log: String): Map<String, List<String>> {
        val inlinedMethodsMap = mutableMapOf<String, MutableList<String>>()

        val blockMatches = COMPILATION_BLOCK_START_REGEX.findAll(log)
        for (blockMatch in blockMatches) {
            val targetClass = blockMatch.groupValues[1]
            val targetMethod = blockMatch.groupValues[2]
            val targetFullName = "$targetClass.$targetMethod"

            // Находим конец блока (следующий блок или конец лога)
            val startPos = blockMatch.range.first
            val endPos = log.indexOf("compilation of", startPos + 1).let {
                if (it == -1) log.length else it
            }

            val block = log.substring(startPos, endPos)

            // Ищем все инлайны в этом блоке
            val inlineMatches = INLINING_REGEX.findAll(block)
            for (inlineMatch in inlineMatches) {
                val inlinedClass = inlineMatch.groupValues[1]
                val inlinedMethod = inlineMatch.groupValues[2]
                val inlinedFullName = "$inlinedClass.$inlinedMethod"

                inlinedMethodsMap.getOrPut(targetFullName) { mutableListOf() }.add(inlinedFullName)
            }
        }

        return inlinedMethodsMap
    }

    private fun calculateTotalCompileTime(events: List<JITCompilationEvent>): Long {
        return events.sumOf { it.compileTime }
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
        inlinedMethodsMap: Map<String, List<String>>
    ): JITProfile {
        val deoptCount = events.count { it.deoptimization }
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
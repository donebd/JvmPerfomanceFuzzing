package infrastructure.jit

import infrastructure.jit.model.HotMethodAnalysis
import infrastructure.jit.model.JITComparisonResult
import infrastructure.jit.model.JITCompilationEvent
import infrastructure.performance.anomaly.JITAnomalyData

/**
 * Генератор отчетов в формате Markdown для результатов JIT-анализа.
 */
class JITReportGenerator {

    companion object {
        private const val JIT_SIGNIFICANCE_THRESHOLD = 0.4
        private const val INLINING_DIFFERENCE_THRESHOLD = 0.1
        private const val MAX_METHODS_TO_LIST = 10
        private const val MAX_METHODS_FOR_DETAILED_ANALYSIS = 3
    }

    /**
     * Генерирует полный отчет о JIT-аномалии в формате Markdown.
     */
    fun generateMarkdownReport(jitData: JITAnomalyData): String = buildString {
        appendLine("# Анализ JIT-компиляции\n")

        appendProfilesSection(jitData)

        if (jitData.comparisons.isNotEmpty()) {
            val mostSignificantComparison = jitData.comparisons
                .maxByOrNull { it.jitRelatedProbability }
                ?: jitData.comparisons.first()

            appendComparisonSection(mostSignificantComparison)
            appendRecommendationsSection(mostSignificantComparison)
            appendMethodAnalysisSection(mostSignificantComparison)
        }
    }

    private fun StringBuilder.appendProfilesSection(jitData: JITAnomalyData) {
        appendLine("## Профили компиляции")
        appendLine("| JVM | Компиляций | Деоптимизаций | Макс. уровень | Инлайнинг | Уникальные методы |")
        appendLine("|-----|------------|---------------|---------------|-----------|------------------|")

        jitData.profiles.forEach { (jvm, profile) ->
            appendLine(
                "| $jvm | ${profile.totalCompilations} | ${profile.deoptimizationCount} | " +
                        "${profile.maxCompileLevel} | ${"%.2f".format(profile.inliningRate)} | " +
                        "${profile.uniqueCompiledMethods} |"
            )
        }

        appendLine()
    }

    private fun StringBuilder.appendComparisonSection(comp: JITComparisonResult) {
        appendLine("## Сравнительный анализ")
        appendLine("### ${comp.fasterJvmProfile.jvmName} vs ${comp.slowerJvmProfile.jvmName}\n")

        appendLine("Разница эффективности компиляции: ${"%.2f%%".format(comp.compilationEfficiencyDiff * 100)}")
        appendLine("Вероятность связи с JIT: ${"%.2f%%".format(comp.jitRelatedProbability * 100)}\n")
        appendLine("**Объяснение**: ${comp.analysisExplanation}\n")

        appendUniqueMethodsSection(comp)
    }

    private fun StringBuilder.appendUniqueMethodsSection(comp: JITComparisonResult) {
        if (comp.uniqueOptimizationsInFaster.isEmpty()) return

        appendLine("### Уникальные методы в быстрой JVM")

        val methodsToShow = comp.uniqueOptimizationsInFaster.take(MAX_METHODS_TO_LIST)
        methodsToShow.forEach { appendLine("- `$it`") }

        val remaining = comp.uniqueOptimizationsInFaster.size - methodsToShow.size
        if (remaining > 0) {
            appendLine("- ... и еще $remaining методов")
        }

        appendLine()
    }

    private fun StringBuilder.appendRecommendationsSection(comp: JITComparisonResult) {
        appendLine("## Рекомендации для проверки")

        if (comp.jitRelatedProbability <= JIT_SIGNIFICANCE_THRESHOLD) return

        val (faster, slower) = comp.fasterJvmProfile to comp.slowerJvmProfile

        if (faster.deoptimizationCount < slower.deoptimizationCount) {
            appendLine("- Исследовать причины деоптимизаций в ${slower.jvmName}")
            appendLine("  - `-XX:+TraceDeoptimization` для HotSpot")
        }

        if (comp.inliningRateDiff > INLINING_DIFFERENCE_THRESHOLD) {
            appendLine("- Проверить эффект инлайнинга:")
            appendLine("  - `-XX:-Inline` для HotSpot")
        }

        if (faster.maxCompileLevel != slower.maxCompileLevel) {
            appendLine("- Проверить влияние уровней компиляции:")
            appendLine("  - `-XX:TieredStopAtLevel=` (1-4) для HotSpot")
        }

        appendLine()
    }

    private fun StringBuilder.appendMethodAnalysisSection(comp: JITComparisonResult) {
        val allMethods = (comp.fasterJvmProfile.compiledMethods.map { it.methodName } +
                comp.slowerJvmProfile.compiledMethods.map { it.methodName }).distinct()

        if (allMethods.isEmpty()) return

        appendLine("## Детальный анализ компиляции методов\n")

        if (allMethods.size <= MAX_METHODS_FOR_DETAILED_ANALYSIS) {
            allMethods.forEach { methodName ->
                appendDetailedMethodComparison(methodName, comp)
            }
        } else {
            appendSummaryMethodTable(comp)
        }
    }

    private fun StringBuilder.appendDetailedMethodComparison(methodName: String, comp: JITComparisonResult) {
        val faster = comp.fasterJvmProfile.jvmName
        val slower = comp.slowerJvmProfile.jvmName
        val methodFaster = comp.fasterJvmProfile.compiledMethods.find { it.methodName == methodName }
        val methodSlower = comp.slowerJvmProfile.compiledMethods.find { it.methodName == methodName }

        appendLine("### Метод `$methodName`\n")

        if (methodFaster != null && methodSlower != null) {
            appendLine("| Параметр | $faster | $slower |")
            appendLine("|----------|--------|--------|")
            appendLine("| Уровень компиляции | ${methodFaster.compileLevel} | ${methodSlower.compileLevel} |")
            appendLine(
                "| Статус | ${if (methodFaster.deoptimization) "деоптимизирован" else "активен"} | " +
                        "${if (methodSlower.deoptimization) "деоптимизирован" else "активен"} |"
            )
            appendLine("| Инлайны | ${methodFaster.inlinedMethods.size} | ${methodSlower.inlinedMethods.size} |\n")

            appendInliningDifferences(faster, slower, methodFaster, methodSlower)
            appendMethodConclusions(faster, methodFaster, methodSlower)
        } else {
            if (methodFaster == null) {
                appendLine("Метод **не скомпилирован** в $faster, но скомпилирован в $slower.")
            } else {
                appendLine("Метод скомпилирован в $faster, но **не скомпилирован** в $slower.")
            }
        }

        appendLine()
    }

    private fun StringBuilder.appendInliningDifferences(
        fasterJvmName: String,
        slowerJvmName: String,
        fasterMethod: JITCompilationEvent,
        slowerMethod: JITCompilationEvent
    ) {
        val inFaster = fasterMethod.inlinedMethods.toSet()
        val inSlower = slowerMethod.inlinedMethods.toSet()
        val onlyInFaster = inFaster - inSlower
        val onlyInSlower = inSlower - inFaster

        if (onlyInFaster.isEmpty() && onlyInSlower.isEmpty()) return

        appendLine("#### Инлайнинг")

        if (onlyInFaster.isNotEmpty()) {
            appendLine("Инлайны только в $fasterJvmName:")
            onlyInFaster.forEach { appendLine("- `$it`") }
        }

        if (onlyInSlower.isNotEmpty()) {
            appendLine("Инлайны только в $slowerJvmName:")
            onlyInSlower.forEach { appendLine("- `$it`") }
        }

        appendLine()
    }

    private fun StringBuilder.appendMethodConclusions(
        fasterJvmName: String,
        fasterMethod: JITCompilationEvent,
        slowerMethod: JITCompilationEvent
    ) {
        appendLine("#### Анализ")

        if (fasterMethod.compileLevel > slowerMethod.compileLevel)
            appendLine("- **Выше уровень компиляции** в быстрой JVM может объяснять разницу")

        if (!fasterMethod.deoptimization && slowerMethod.deoptimization)
            appendLine("- **Деоптимизация** в медленной JVM может быть причиной снижения производительности")

        if (fasterMethod.inlinedMethods.size > slowerMethod.inlinedMethods.size)
            appendLine("- **Более агрессивный инлайнинг** в быстрой JVM может улучшать производительность")
    }

    private fun StringBuilder.appendSummaryMethodTable(comp: JITComparisonResult) {
        val faster = comp.fasterJvmProfile.jvmName
        val slower = comp.slowerJvmProfile.jvmName

        appendLine("| Метод | $faster | $slower | Примечания |")
        appendLine("|-------|--------|--------|------------|")

        comp.hotMethods.sortedByDescending { it.hotnessScore }.forEach { method ->
            val fasterInfo = formatCompilationStatus(method.fasterJvmCompileLevel, method.fasterJvmDeoptimized)
            val slowerInfo = formatCompilationStatus(method.slowerJvmCompileLevel, method.slowerJvmDeoptimized)
            val notes = generateMethodNotes(method)

            appendLine("| `${method.methodName}` | $fasterInfo | $slowerInfo | $notes |")
        }

        appendLine()
    }

    private fun formatCompilationStatus(compileLevel: Int, isDeoptimized: Boolean): String {
        return when {
            compileLevel < 0 -> "не скомпилирован"
            isDeoptimized -> "уровень $compileLevel (деопт)"
            else -> "уровень $compileLevel"
        }
    }

    private fun generateMethodNotes(method: HotMethodAnalysis): String {
        return when {
            method.fasterJvmCompileLevel < 0 && method.slowerJvmCompileLevel >= 0 ->
                "не скомпилирован в быстрой"

            method.slowerJvmCompileLevel < 0 && method.fasterJvmCompileLevel >= 0 ->
                "не скомпилирован в медленной"

            method.fasterJvmCompileLevel > method.slowerJvmCompileLevel ->
                "выше уровень в быстрой"

            method.slowerJvmDeoptimized && !method.fasterJvmDeoptimized ->
                "деоптимизирован в медленной"

            method.fasterJvmInlinedCount > method.slowerJvmInlinedCount ->
                "лучший инлайнинг в быстрой"

            else -> ""
        }
    }
}

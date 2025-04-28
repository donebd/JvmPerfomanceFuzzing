package infrastructure.jit

import infrastructure.jit.model.HotMethodAnalysis
import infrastructure.jit.model.JITComparisonResult
import infrastructure.jit.model.JITCompilationEvent
import infrastructure.jit.model.JITProfile
import infrastructure.jit.parser.*
import infrastructure.jvm.JITLoggingOptionsProvider
import infrastructure.jvm.JvmExecutor
import infrastructure.performance.entity.PerformanceMetrics
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Анализатор JIT-компиляции для сравнения и выявления различий
 * в оптимизациях между разными реализациями JVM.
 */
class JITAnalyzer(val jitOptionsProvider: JITLoggingOptionsProvider) {

    companion object {
        // Веса для расчета эффективности компиляции
        private const val WEIGHT_HIGH_TIER_COMPILATION = 0.5
        private const val WEIGHT_INLINING_RATE = 0.3
        private const val WEIGHT_DEOPT_PENALTY = 0.2

        // Веса для расчета вероятности связи с JIT
        private const val WEIGHT_EFFICIENCY_FACTOR = 0.4
        private const val WEIGHT_LEVEL_DIFF = 0.2
        private const val WEIGHT_DEOPT_DIFF = 0.3
        private const val WEIGHT_INLINING_DIFF = 0.1

        // Пороговые значения для анализа
        private const val HIGH_JIT_PROBABILITY = 0.7
        private const val MEDIUM_JIT_PROBABILITY = 0.4
        private const val SIGNIFICANT_EFFICIENCY_DIFF = 0.2
        private const val SIGNIFICANT_METHOD_COUNT_DIFF_RATIO = 0.2
        private const val SIGNIFICANT_DEOPT_RATIO = 2.0
        private const val SIGNIFICANT_INLINING_RATIO = 1.5

        // Коэффициенты для расчета факторов
        private const val EFFICIENCY_SCALE_FACTOR = 3.0
        private const val LEVEL_HIGHER_FACTOR = 0.6
        private const val LEVEL_LOWER_FACTOR = 0.2
        private const val LEVEL_EQUAL_FACTOR = 0.3
        private const val MAX_DEOPT_FACTOR = 0.8
        private const val DEOPT_SCALE_FACTOR = 5.0
        private const val MAX_INLINING_FACTOR = 0.7
        private const val INLINING_SCALE_FACTOR = 3.0

        // Веса для оценки "горячести" методов
        private const val HOT_METHOD_MAX_LEVEL_SCORE = 3.0
        private const val HOT_METHOD_INLINING_WEIGHT = 0.5
        private const val HOT_METHOD_ASYMMETRIC_COMPILE_SCORE = 2.0
        private const val HOT_METHOD_ASYMMETRIC_DEOPT_SCORE = 5.0
        private const val HOT_METHOD_LEVEL_DIFF_WEIGHT = 1.5
    }

    private val parserRegistry = mapOf(
        "HotSpotJvmExecutor" to HotSpotJITLogParser(),
        "OpenJ9JvmExecutor" to OpenJ9JITLogParser(),
        "GraalVmExecutor" to GraalVMJITLogParser(),
        "AxiomJvmExecutor" to AxiomJITLogParser()
    )

    /**
     * Анализирует логи JIT-компиляции и создает профиль для указанной JVM
     *
     * @param jvmExecutor исполнитель JVM
     * @param metrics метрики производительности с логами выполнения
     * @return профиль JIT-компиляции
     * @throws IllegalArgumentException если для данной JVM нет поддерживаемого парсера
     */
    fun analyzeJITLogs(jvmExecutor: JvmExecutor, metrics: PerformanceMetrics): JITProfile {
        val parser = getParserForJvm(jvmExecutor)
        return parser.parseCompilationLogs(
            metrics.jvmExecutionResult.stdout,
            metrics.jvmExecutionResult.stderr
        )
    }

    /**
     * Сравнивает профили JIT-компиляции двух JVM и выявляет различия
     *
     * @param fasterProfile профиль JVM с лучшей производительностью
     * @param slowerProfile профиль JVM с худшей производительностью
     * @return результат сравнения с анализом различий
     */
    fun compareJITProfiles(fasterProfile: JITProfile, slowerProfile: JITProfile): JITComparisonResult {
        val compilationEfficiencyDiff = calculateCompilationEfficiency(fasterProfile) -
                calculateCompilationEfficiency(slowerProfile)

        val uniqueMethods = findUniqueMethods(fasterProfile, slowerProfile)

        val inliningRateDiff = fasterProfile.inliningRate - slowerProfile.inliningRate
        val compilationSpeedDiff = calculateCompilationSpeedDiff(fasterProfile, slowerProfile)

        val jitRelatedProbability = calculateJITRelatedProbability(
            fasterProfile, slowerProfile, compilationEfficiencyDiff
        )

        val explanation = generateAnalysisExplanation(
            fasterProfile, slowerProfile, compilationEfficiencyDiff, jitRelatedProbability
        )

        val hotMethods = identifyHotMethods(fasterProfile, slowerProfile)

        return JITComparisonResult(
            fasterJvmProfile = fasterProfile,
            slowerJvmProfile = slowerProfile,
            fasterJvmName = fasterProfile.jvmName,
            slowerJvmName = slowerProfile.jvmName,
            compilationEfficiencyDiff = compilationEfficiencyDiff,
            uniqueOptimizationsInFaster = uniqueMethods.first,
            uniqueOptimizationsInSlower = uniqueMethods.second,
            inliningRateDiff = inliningRateDiff,
            compilationSpeedDiff = compilationSpeedDiff,
            jitRelatedProbability = jitRelatedProbability,
            analysisExplanation = explanation,
            hotMethods = hotMethods
        )
    }

    /**
     * Идентифицирует "горячие" методы, которые вероятно вносят наибольший вклад
     * в разницу производительности между JVM
     *
     * @param fasterProfile профиль быстрой JVM
     * @param slowerProfile профиль медленной JVM
     * @param maxMethodsToReturn максимальное количество методов для возврата
     * @return список методов с наивысшим показателем "горячести"
     */
    private fun identifyHotMethods(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile,
        maxMethodsToReturn: Int = 10
    ): List<HotMethodAnalysis> {
        val allMethods = collectMethodsFromProfiles(fasterProfile, slowerProfile)
        val hotMethodsAnalysis = mutableListOf<HotMethodAnalysis>()

        for (method in allMethods) {
            val fasterMethod = fasterProfile.compiledMethods.find { it.methodName == method }
            val slowerMethod = slowerProfile.compiledMethods.find { it.methodName == method }

            if (fasterMethod == null && slowerMethod == null) continue

            val hotnessScore = calculateHotnessScore(
                fasterMethod, slowerMethod, fasterProfile, slowerProfile
            )

            hotMethodsAnalysis.add(
                HotMethodAnalysis(
                    methodName = method,
                    fasterJvmCompileLevel = fasterMethod?.compileLevel ?: -1,
                    slowerJvmCompileLevel = slowerMethod?.compileLevel ?: -1,
                    fasterJvmDeoptimized = fasterMethod?.deoptimization ?: false,
                    slowerJvmDeoptimized = slowerMethod?.deoptimization ?: false,
                    fasterJvmInlinedCount = fasterMethod?.inlinedMethods?.size ?: 0,
                    slowerJvmInlinedCount = slowerMethod?.inlinedMethods?.size ?: 0,
                    hotnessScore = hotnessScore
                )
            )
        }

        return hotMethodsAnalysis.sortedByDescending { it.hotnessScore }.take(maxMethodsToReturn)
    }

    // --- Вспомогательные методы для анализа профилей ---

    private fun getParserForJvm(jvmExecutor: JvmExecutor): JITLogParser {
        val executorName = jvmExecutor::class.simpleName ?: "Unknown"
        return parserRegistry[executorName]
            ?: throw IllegalArgumentException("No JIT parser for $executorName")
    }

    private fun findUniqueMethods(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ): Pair<List<String>, List<String>> {
        val fasterMethods = fasterProfile.compiledMethods.map { it.methodName }.toSet()
        val slowerMethods = slowerProfile.compiledMethods.map { it.methodName }.toSet()

        return Pair(
            (fasterMethods - slowerMethods).toList(),
            (slowerMethods - fasterMethods).toList()
        )
    }

    private fun calculateCompilationSpeedDiff(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ): Double {
        if (fasterProfile.totalCompilationTime <= 0 || slowerProfile.totalCompilationTime <= 0) {
            return 0.0
        }

        return (slowerProfile.totalCompilationTime - fasterProfile.totalCompilationTime).toDouble() /
                slowerProfile.totalCompilationTime
    }

    private fun collectMethodsFromProfiles(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ): List<String> {
        return (fasterProfile.compiledMethods.map { it.methodName } +
                slowerProfile.compiledMethods.map { it.methodName }).distinct()
    }

    private fun calculateHotnessScore(
        fasterMethod: JITCompilationEvent?,
        slowerMethod: JITCompilationEvent?,
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ): Double {
        var score = 0.0

        score += calculateCompileLevelScore(fasterMethod, slowerMethod, fasterProfile, slowerProfile)

        score += calculateInliningScore(fasterMethod, slowerMethod)

        if ((fasterMethod != null && slowerMethod == null) ||
            (fasterMethod == null && slowerMethod != null)
        ) {
            score += HOT_METHOD_ASYMMETRIC_COMPILE_SCORE
        }

        if (fasterMethod != null && slowerMethod != null &&
            fasterMethod.deoptimization != slowerMethod.deoptimization
        ) {
            score += HOT_METHOD_ASYMMETRIC_DEOPT_SCORE
        }

        return score
    }

    /**
     * Рассчитывает вклад уровней компиляции в оценку "горячести"
     */
    private fun calculateCompileLevelScore(
        fasterMethod: JITCompilationEvent?,
        slowerMethod: JITCompilationEvent?,
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ): Double {
        var score = 0.0

        // Высший уровень компиляции
        if (fasterMethod != null && fasterMethod.compileLevel == fasterProfile.maxCompileLevel) {
            score += HOT_METHOD_MAX_LEVEL_SCORE
        }
        if (slowerMethod != null && slowerMethod.compileLevel == slowerProfile.maxCompileLevel) {
            score += HOT_METHOD_MAX_LEVEL_SCORE
        }

        // Разница в уровнях компиляции
        if (fasterMethod != null && slowerMethod != null &&
            fasterMethod.compileLevel != slowerMethod.compileLevel
        ) {
            score += abs(fasterMethod.compileLevel - slowerMethod.compileLevel) *
                    HOT_METHOD_LEVEL_DIFF_WEIGHT
        }

        return score
    }

    private fun calculateInliningScore(
        fasterMethod: JITCompilationEvent?,
        slowerMethod: JITCompilationEvent?
    ): Double {
        var score = 0.0

        // Больше инлайнинга = важнее метод
        if (fasterMethod != null) {
            score += fasterMethod.inlinedMethods.size * HOT_METHOD_INLINING_WEIGHT
        }
        if (slowerMethod != null) {
            score += slowerMethod.inlinedMethods.size * HOT_METHOD_INLINING_WEIGHT
        }

        return score
    }

    // --- Методы анализа эффективности компиляции ---

    private fun calculateCompilationEfficiency(profile: JITProfile): Double {
        if (profile.totalCompilations == 0) return 0.0

        val highTierPercentage = calculateHighTierPercentage(profile)
        val deoptPenalty = calculateDeoptimizationPenalty(profile)

        return (highTierPercentage * WEIGHT_HIGH_TIER_COMPILATION) +
                (profile.inliningRate * WEIGHT_INLINING_RATE) +
                ((1.0 - deoptPenalty) * WEIGHT_DEOPT_PENALTY)
    }

    private fun calculateHighTierPercentage(profile: JITProfile): Double {
        val highTierCompilations = profile.compiledMethods.count {
            it.compileLevel >= profile.maxCompileLevel
        }.toDouble()

        return highTierCompilations / profile.totalCompilations
    }

    private fun calculateDeoptimizationPenalty(profile: JITProfile): Double {
        return if (profile.totalCompilations > 0) {
            min(1.0, profile.deoptimizationCount.toDouble() / profile.totalCompilations)
        } else {
            0.0
        }
    }

    private fun calculateJITRelatedProbability(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile,
        compilationEfficiencyDiff: Double
    ): Double {
        val efficiencyFactor = min(1.0, compilationEfficiencyDiff * EFFICIENCY_SCALE_FACTOR)
        val levelDiff = calculateLevelDifferenceFactor(fasterProfile, slowerProfile)
        val deoptDiffFactor = calculateDeoptimizationDifferenceFactor(fasterProfile, slowerProfile)
        val inliningDiffFactor = calculateInliningDifferenceFactor(fasterProfile, slowerProfile)

        val rawProbability = (efficiencyFactor * WEIGHT_EFFICIENCY_FACTOR) +
                (levelDiff * WEIGHT_LEVEL_DIFF) +
                (deoptDiffFactor * WEIGHT_DEOPT_DIFF) +
                (inliningDiffFactor * WEIGHT_INLINING_DIFF)

        return min(1.0, rawProbability)
    }

    private fun calculateLevelDifferenceFactor(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ): Double {
        return when {
            fasterProfile.maxCompileLevel > slowerProfile.maxCompileLevel ->
                LEVEL_HIGHER_FACTOR

            fasterProfile.maxCompileLevel < slowerProfile.maxCompileLevel ->
                LEVEL_LOWER_FACTOR

            else ->
                LEVEL_EQUAL_FACTOR
        }
    }

    private fun calculateDeoptimizationDifferenceFactor(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ): Double {
        if (slowerProfile.deoptimizationCount <= fasterProfile.deoptimizationCount) {
            return 0.0
        }

        val deoptDiff = slowerProfile.deoptimizationCount - fasterProfile.deoptimizationCount
        val normalizedDiff = deoptDiff.toDouble() / max(1, slowerProfile.totalCompilations) *
                DEOPT_SCALE_FACTOR

        return min(MAX_DEOPT_FACTOR, normalizedDiff)
    }

    private fun calculateInliningDifferenceFactor(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ): Double {
        val inliningDiff = abs(fasterProfile.inliningRate - slowerProfile.inliningRate)
        return min(MAX_INLINING_FACTOR, inliningDiff * INLINING_SCALE_FACTOR)
    }

    // --- Методы генерации объяснений ---

    private fun generateAnalysisExplanation(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile,
        compilationEfficiencyDiff: Double,
        jitRelatedProbability: Double
    ): String {
        val explanation = StringBuilder()

        appendGeneralConclusion(explanation, jitRelatedProbability)
        appendCompileLevelAnalysis(explanation, fasterProfile, slowerProfile)
        appendCompilationCountAnalysis(explanation, fasterProfile, slowerProfile)
        appendDeoptimizationAnalysis(explanation, fasterProfile, slowerProfile)
        appendInliningAnalysis(explanation, fasterProfile, slowerProfile)
        appendEfficiencyAnalysis(explanation, fasterProfile, slowerProfile, compilationEfficiencyDiff)

        if (jitRelatedProbability > MEDIUM_JIT_PROBABILITY) {
            appendRecommendations(explanation, fasterProfile, slowerProfile)
        }

        return explanation.toString()
    }

    private fun appendGeneralConclusion(
        sb: StringBuilder,
        jitRelatedProbability: Double
    ) {
        when {
            jitRelatedProbability > HIGH_JIT_PROBABILITY ->
                sb.appendLine("JIT-компиляция с высокой вероятностью является причиной разницы в производительности.")

            jitRelatedProbability > MEDIUM_JIT_PROBABILITY ->
                sb.appendLine("JIT-компиляция, вероятно, частично влияет на разницу в производительности.")

            else ->
                sb.appendLine("JIT-компиляция, скорее всего, не является основной причиной разницы в производительности.")
        }
        sb.appendLine()
    }

    private fun appendCompileLevelAnalysis(
        sb: StringBuilder,
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ) {
        if (fasterProfile.maxCompileLevel > slowerProfile.maxCompileLevel) {
            sb.appendLine(
                "Быстрая JVM (${fasterProfile.jvmName}) использует более высокий уровень компиляции: " +
                        "${fasterProfile.maxCompileLevel} против ${slowerProfile.maxCompileLevel}."
            )
        } else if (fasterProfile.maxCompileLevel < slowerProfile.maxCompileLevel) {
            sb.appendLine(
                "Хотя более медленная JVM (${slowerProfile.jvmName}) имеет более высокий уровень компиляции " +
                        "(${slowerProfile.maxCompileLevel} против ${fasterProfile.maxCompileLevel}), " +
                        "это не привело к лучшей производительности."
            )
        }
    }

    private fun appendCompilationCountAnalysis(
        sb: StringBuilder,
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ) {
        val compilationDiff = fasterProfile.totalCompilations - slowerProfile.totalCompilations
        val minCompilations = min(fasterProfile.totalCompilations, slowerProfile.totalCompilations)

        if (abs(compilationDiff) > minCompilations * SIGNIFICANT_METHOD_COUNT_DIFF_RATIO) {
            if (compilationDiff > 0) {
                sb.appendLine(
                    "${fasterProfile.jvmName} скомпилировала значительно больше методов: " +
                            "${fasterProfile.totalCompilations} против ${slowerProfile.totalCompilations}."
                )
            } else {
                sb.appendLine(
                    "${slowerProfile.jvmName} скомпилировала больше методов " +
                            "(${slowerProfile.totalCompilations} против ${fasterProfile.totalCompilations}), " +
                            "но это не привело к лучшей производительности."
                )
            }
        }
    }

    private fun appendDeoptimizationAnalysis(
        sb: StringBuilder,
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ) {
        if (slowerProfile.deoptimizationCount > fasterProfile.deoptimizationCount * SIGNIFICANT_DEOPT_RATIO) {
            sb.appendLine(
                "${slowerProfile.jvmName} имеет существенно больше деоптимизаций: " +
                        "${slowerProfile.deoptimizationCount} против ${fasterProfile.deoptimizationCount}, " +
                        "что могло негативно повлиять на производительность."
            )
        }
    }

    private fun appendInliningAnalysis(
        sb: StringBuilder,
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ) {
        val inliningDiffRatio = calculateInliningRatio(fasterProfile, slowerProfile)

        if (inliningDiffRatio > SIGNIFICANT_INLINING_RATIO) {
            sb.appendLine(
                "${fasterProfile.jvmName} применила существенно больше инлайнинга: " +
                        "коэффициент ${String.format("%.2f", fasterProfile.inliningRate)} против " +
                        "${String.format("%.2f", slowerProfile.inliningRate)}."
            )
        }
    }

    private fun calculateInliningRatio(
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ): Double {
        return if (slowerProfile.inliningRate > 0) {
            fasterProfile.inliningRate / slowerProfile.inliningRate
        } else {
            if (fasterProfile.inliningRate > 0) 2.0 else 1.0
        }
    }

    private fun appendEfficiencyAnalysis(
        sb: StringBuilder,
        fasterProfile: JITProfile,
        slowerProfile: JITProfile,
        compilationEfficiencyDiff: Double
    ) {
        if (compilationEfficiencyDiff > SIGNIFICANT_EFFICIENCY_DIFF) {
            val fasterEfficiency = calculateCompilationEfficiency(fasterProfile)
            val slowerEfficiency = calculateCompilationEfficiency(slowerProfile)

            sb.appendLine(
                "Общая эффективность компиляции ${fasterProfile.jvmName} значительно выше: " +
                        "${String.format("%.2f", fasterEfficiency)} против " +
                        "${String.format("%.2f", slowerEfficiency)}."
            )
        }
    }

    private fun appendRecommendations(
        sb: StringBuilder,
        fasterProfile: JITProfile,
        slowerProfile: JITProfile
    ) {
        sb.appendLine()
        sb.appendLine("Рекомендации для проверки гипотезы JIT-влияния:")

        if (fasterProfile.maxCompileLevel != slowerProfile.maxCompileLevel) {
            sb.appendLine("- Попробуйте ограничить уровни компиляции (для HotSpot: `-XX:TieredStopAtLevel=`)")
        }

        if (slowerProfile.deoptimizationCount > fasterProfile.deoptimizationCount * SIGNIFICANT_DEOPT_RATIO) {
            sb.appendLine("- Исследуйте причины деоптимизаций в ${slowerProfile.jvmName}")
        }

        val inliningDiffRatio = calculateInliningRatio(fasterProfile, slowerProfile)
        if (inliningDiffRatio > SIGNIFICANT_INLINING_RATIO) {
            sb.appendLine("- Проверьте влияние инлайнинга, отключив его (для HotSpot: `-XX:-Inline`)")
        }
    }
}

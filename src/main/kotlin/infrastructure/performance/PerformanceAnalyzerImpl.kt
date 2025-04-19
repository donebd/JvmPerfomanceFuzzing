package infrastructure.performance

import infrastructure.jit.JITAnalyzer
import infrastructure.jit.model.JITComparisonResult
import infrastructure.jit.model.JITProfile
import infrastructure.jvm.JvmExecutor
import infrastructure.performance.anomaly.AnomalyGroupType
import infrastructure.performance.anomaly.JITAnomalyData
import infrastructure.performance.entity.JvmPerformanceResult
import infrastructure.performance.anomaly.PerformanceAnomalyGroup
import infrastructure.performance.entity.PerformanceMetrics
import infrastructure.performance.entity.SignificanceLevel
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Реализация анализатора производительности, обнаруживающего различные типы аномалий
 * между JVM, включая время выполнения, использование памяти, таймауты, ошибки и JIT-аномалии.
 */
class PerformanceAnalyzerImpl(
    private val jitAnalyzer: JITAnalyzer? = null,
    private val significantTimeThreshold: Double = 10.0,
    private val significantMemoryThreshold: Double = 10.0,
    private val potentialTimeThreshold: Double = 1.0,
    private val potentialMemoryThreshold: Double = 1.0
) : PerformanceAnalyzer {

    companion object {
        // Константы для анализа JIT-аномалий
        private const val JIT_ANOMALY_PROBABILITY_THRESHOLD = 0.3
        private const val JIT_RELATED_PROBABILITY_WEIGHT = 30.0
        private const val JIT_UNIQUE_METHODS_WEIGHT = 2.0
        private const val JIT_UNIQUE_METHODS_MAX = 20.0
        private const val JIT_DEOPT_DIFFERENCE_BONUS = 15.0
        private const val JIT_HOT_METHODS_BONUS = 20.0

        // Константы для анализа обычных аномалий
        private const val TIMEOUT_DEVIATION = 100.0
        private const val TIMEOUT_INTERESTINGNESS_WEIGHT = 50.0
        private const val ERROR_INTERESTINGNESS_WEIGHT = 30.0
    }

    /**
     * Анализирует метрики производительности и выявляет аномалии между различными JVM.
     *
     * @param metrics список пар JvmExecutor и метрик производительности
     * @param significanceLevel уровень значимости для определения порогов аномалий
     * @return список обнаруженных групп аномалий
     */
    override fun analyze(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        significanceLevel: SignificanceLevel
    ): List<PerformanceAnomalyGroup> {
        if (metrics.size < 2) return emptyList() // Для анализа нужны хотя бы две JVM

        val anomalyGroups = mutableListOf<PerformanceAnomalyGroup>()
        val thresholds = getThresholds(significanceLevel)

        val validMetrics = metrics.filter { (_, metric) ->
            metric.successfullyParsed && !metric.jvmExecutionResult.timedOut
        }

        anomalyGroups.addAll(handleSpecialCases(metrics))

        if (validMetrics.size >= 2) {
            findPerformanceAnomalies(
                validMetrics,
                AnomalyGroupType.TIME,
                thresholds.timeThreshold,
                significanceLevel,
                { it.avgTimeMs }
            ).also { anomalyGroups.addAll(it) }

            findPerformanceAnomalies(
                validMetrics,
                AnomalyGroupType.MEMORY,
                thresholds.memoryThreshold,
                significanceLevel,
                { it.avgMemoryUsageKb.toDouble() }
            ).also { anomalyGroups.addAll(it) }
        }

        return anomalyGroups
    }

    /**
     * Проверяет, представляют ли найденные аномалии интерес.
     *
     * @param anomalies список групп аномалий для проверки
     * @return true, если аномалии интересны
     */
    override fun areAnomaliesInteresting(anomalies: List<PerformanceAnomalyGroup>): Boolean {
        if (anomalies.isEmpty()) return false

        val timeoutAnomalies = anomalies.filter { it.anomalyType == AnomalyGroupType.TIMEOUT }
        if (timeoutAnomalies.isNotEmpty() && timeoutAnomalies.all { it.interestingnessScore <= 0 }) {
            return false
        }

        val errorAnomalies = anomalies.filter { it.anomalyType == AnomalyGroupType.ERROR }
        return !(errorAnomalies.isNotEmpty() && errorAnomalies.all { it.interestingnessScore <= 0 })
    }

    /**
     * Рассчитывает общую интересность набора аномалий.
     *
     * @param anomalies список групп аномалий
     * @return суммарная интересность в диапазоне [0, +∞)
     */
    override fun calculateOverallInterestingness(anomalies: List<PerformanceAnomalyGroup>): Double {
        if (!areAnomaliesInteresting(anomalies)) return 0.0

        return anomalies.sumOf { it.interestingnessScore } / anomalies.size.coerceAtLeast(1)
    }

    /**
     * Выполняет комбинированный анализ производительности и JIT.
     *
     * @param metrics список пар JvmExecutor и метрик
     * @param significanceLevel уровень значимости для определения порогов
     * @return пара (список аномалий, (JIT-интересность, JIT-данные))
     */
    override fun analyzeWithJIT(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        significanceLevel: SignificanceLevel
    ): Pair<List<PerformanceAnomalyGroup>, Pair<Double, JITAnomalyData?>> {
        val performanceAnomalies = analyze(metrics, significanceLevel)

        val jitResult = analyzeJIT(
            metrics.map { it.first },
            metrics.toMap(),
            significanceLevel
        )

        val enrichedAnomalies = jitResult.second?.let {
            enrichAnomaliesWithJIT(performanceAnomalies, it)
        } ?: performanceAnomalies

        return Pair(enrichedAnomalies, jitResult)
    }

    /**
     * Анализирует JIT-логи и вычисляет JIT-интересность.
     *
     * @param jvmExecutors список исполнителей JVM
     * @param metrics карта метрик производительности
     * @param significanceLevel уровень значимости
     * @return пара (JIT-интересность, JIT-данные)
     */
    override fun analyzeJIT(
        jvmExecutors: List<JvmExecutor>,
        metrics: Map<JvmExecutor, PerformanceMetrics>,
        significanceLevel: SignificanceLevel
    ): Pair<Double, JITAnomalyData?> {
        if (jitAnalyzer == null) return Pair(0.0, null)

        if (metrics.values.any { !it.successfullyParsed || it.jvmExecutionResult.timedOut }) {
            return Pair(0.0, null)
        }

        val jitProfiles = collectJitProfiles(jvmExecutors, metrics)
        if (jitProfiles.size < 2) return Pair(0.0, null)

        val comparisons = createJvmComparisons(jvmExecutors, metrics, jitProfiles)
        if (comparisons.isEmpty()) return Pair(0.0, null)

        val jitAnomalyData = JITAnomalyData(jitProfiles, comparisons)

        val interestScore = calculateJITInterestScore(comparisons, jitProfiles)

        val significanceMultiplier = when (significanceLevel) {
            SignificanceLevel.SEED_EVOLUTION -> 1.0  // Мягкий критерий для сидов
            SignificanceLevel.REPORTING -> 0.6       // Строгий критерий для отчетов
        }

        return Pair(interestScore * significanceMultiplier, jitAnomalyData)
    }

    /**
     * Обогащает существующие аномалии данными JIT-анализа.
     * Если обнаружены интересные JIT-паттерны, создает отдельную JIT-аномалию.
     *
     * @param anomalies список анализируемых аномалий
     * @param jitData данные JIT-анализа
     * @return обогащенный список аномалий
     */
    override fun enrichAnomaliesWithJIT(
        anomalies: List<PerformanceAnomalyGroup>,
        jitData: JITAnomalyData
    ): List<PerformanceAnomalyGroup> {
        val enrichedAnomalies = anomalies.map { anomaly ->
            if (anomaly.anomalyType in listOf(AnomalyGroupType.TIME, AnomalyGroupType.MEMORY)) {
                anomaly.copy(jitData = jitData)
            } else {
                anomaly
            }
        }.toMutableList()

        val bestComparison = jitData.comparisons.maxByOrNull { it.jitRelatedProbability }

        if (bestComparison != null && bestComparison.jitRelatedProbability > JIT_ANOMALY_PROBABILITY_THRESHOLD) {
            if (!enrichedAnomalies.any { it.anomalyType == AnomalyGroupType.JIT }) {
                val jitAnomaly = createJitAnomaly(bestComparison, jitData)
                enrichedAnomalies.add(jitAnomaly)
            }
        }

        return enrichedAnomalies
    }

    /**
     * Извлекает JIT-данные из логов и обогащает ими аномалии.
     *
     * @param anomalies список аномалий для обогащения
     * @param jvmExecutors список исполнителей JVM
     * @param metrics карта метрик производительности
     * @param jitAnalyzer анализатор JIT-логов
     * @return обогащенный список аномалий
     */
    override fun enrichAnomaliesWithJITFromLogs(
        anomalies: List<PerformanceAnomalyGroup>,
        jvmExecutors: List<JvmExecutor>,
        metrics: Map<JvmExecutor, PerformanceMetrics>,
        jitAnalyzer: JITAnalyzer
    ): List<PerformanceAnomalyGroup> {
        val profiles = collectJitProfilesWithAnalyzer(jvmExecutors, metrics, jitAnalyzer)
        if (profiles.size < 2) return anomalies

        val comparisons = createJvmComparisonsWithAnalyzer(jvmExecutors, metrics, profiles, jitAnalyzer)
        val jitData = JITAnomalyData(profiles, comparisons)

        return enrichAnomaliesWithJIT(anomalies, jitData)
    }

    // --- Вспомогательные методы для анализа производительности ---

    private fun handleSpecialCases(metrics: List<Pair<JvmExecutor, PerformanceMetrics>>): List<PerformanceAnomalyGroup> {
        val timeoutAnomalies = handleTimeoutAnomalies(metrics)
        val errorAnomalies = handleErrorAnomalies(metrics)

        return timeoutAnomalies + errorAnomalies
    }

    /**
     * Определяет пороги для обнаружения аномалий в зависимости от уровня значимости.
     */
    private data class Thresholds(val timeThreshold: Double, val memoryThreshold: Double)

    private fun getThresholds(significanceLevel: SignificanceLevel): Thresholds {
        return when (significanceLevel) {
            SignificanceLevel.REPORTING -> Thresholds(
                timeThreshold = significantTimeThreshold,
                memoryThreshold = significantMemoryThreshold
            )

            SignificanceLevel.SEED_EVOLUTION -> Thresholds(
                timeThreshold = potentialTimeThreshold,
                memoryThreshold = potentialMemoryThreshold
            )
        }
    }

    private fun createJitAnomaly(
        comparison: JITComparisonResult,
        jitData: JITAnomalyData
    ): PerformanceAnomalyGroup {
        val fasterName = comparison.fasterJvmProfile.jvmName
        val slowerName = comparison.slowerJvmProfile.jvmName
        val probability = comparison.jitRelatedProbability

        return PerformanceAnomalyGroup(
            anomalyType = AnomalyGroupType.JIT,
            fasterJvms = emptyList(),  // Заполняется при верификации
            slowerJvms = emptyList(),  // Заполняется при верификации
            averageDeviation = probability * 10,
            maxDeviation = probability * 15,
            minDeviation = probability * 5,
            pairwiseDeviations = mapOf(),
            description = "JIT-аномалия: $fasterName эффективнее компилирует код, чем $slowerName. " +
                    "Вероятность связи с JIT: ${String.format("%.1f%%", probability * 100)}",
            interestingnessScore = probability * JIT_RELATED_PROBABILITY_WEIGHT,
            jitData = jitData
        )
    }

    private fun handleTimeoutAnomalies(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>
    ): List<PerformanceAnomalyGroup> {
        val timeoutJvms = metrics.filter { (_, metric) -> metric.jvmExecutionResult.timedOut }
        val normalJvms = metrics.filter { (_, metric) -> !metric.jvmExecutionResult.timedOut }

        if (timeoutJvms.isEmpty() || normalJvms.isEmpty()) {
            return emptyList()
        }

        val timeoutResults = timeoutJvms.map { (jvm, metrics) ->
            JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metrics)
        }
        val normalResults = normalJvms.map { (jvm, metrics) ->
            JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metrics)
        }

        val description = formatTimeoutDescription(timeoutResults, normalResults)
        val interestingnessScore = calculateTimeoutInterestingness(timeoutJvms.size, metrics.size)

        return listOf(
            PerformanceAnomalyGroup(
                anomalyType = AnomalyGroupType.TIMEOUT,
                fasterJvms = normalResults,
                slowerJvms = timeoutResults,
                averageDeviation = TIMEOUT_DEVIATION,
                maxDeviation = TIMEOUT_DEVIATION,
                minDeviation = TIMEOUT_DEVIATION,
                pairwiseDeviations = emptyMap(),
                description = description,
                interestingnessScore = interestingnessScore
            )
        )
    }

    private fun formatTimeoutDescription(
        timeoutResults: List<JvmPerformanceResult>,
        normalResults: List<JvmPerformanceResult>
    ): String {
        return if (timeoutResults.size == 1) {
            "JVM ${timeoutResults.first().jvmName} завершилась по таймауту, в отличие от ${normalResults.size} других JVM"
        } else {
            "${timeoutResults.size} JVM завершились по таймауту, в отличие от ${normalResults.size} других JVM"
        }
    }

    private fun handleErrorAnomalies(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>
    ): List<PerformanceAnomalyGroup> {
        val errorJvms = metrics.filter { (_, metric) ->
            !metric.successfullyParsed && !metric.jvmExecutionResult.timedOut
        }
        val validJvms = metrics.filter { (_, metric) ->
            metric.successfullyParsed && !metric.jvmExecutionResult.timedOut
        }

        if (errorJvms.isEmpty() || validJvms.isEmpty()) {
            return emptyList()
        }

        return errorJvms
            .groupBy { (_, metric) -> metric.jvmExecutionResult.exitCode }
            .map { (exitCode, jvmsWithError) ->
                createErrorAnomalyGroup(exitCode, jvmsWithError, validJvms, metrics.size)
            }
    }

    private fun createErrorAnomalyGroup(
        exitCode: Int,
        jvmsWithError: List<Pair<JvmExecutor, PerformanceMetrics>>,
        validJvms: List<Pair<JvmExecutor, PerformanceMetrics>>,
        totalJvmsCount: Int
    ): PerformanceAnomalyGroup {
        val errorResults = jvmsWithError.map { (jvm, metrics) ->
            JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metrics)
        }
        val validResults = validJvms.map { (jvm, metrics) ->
            JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metrics)
        }

        val exitCodes = errorResults.associate { it.jvmName to exitCode }
        val description = formatErrorDescription(errorResults, exitCode)
        val interestingnessScore = calculateErrorInterestingness(jvmsWithError.size, totalJvmsCount)

        return PerformanceAnomalyGroup(
            anomalyType = AnomalyGroupType.ERROR,
            fasterJvms = validResults,
            slowerJvms = errorResults,
            averageDeviation = 0.0,
            maxDeviation = 0.0,
            minDeviation = 0.0,
            pairwiseDeviations = emptyMap(),
            description = description,
            exitCodes = exitCodes,
            interestingnessScore = interestingnessScore
        )
    }

    private fun formatErrorDescription(
        errorResults: List<JvmPerformanceResult>,
        exitCode: Int
    ): String {
        return if (errorResults.size == 1) {
            "JVM ${errorResults.first().jvmName} завершилась с ошибкой (код $exitCode), в отличие от других JVM"
        } else {
            "${errorResults.size} JVM завершились с ошибкой (код $exitCode), в отличие от других JVM"
        }
    }

    private fun <T : Number> findPerformanceAnomalies(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        groupType: AnomalyGroupType,
        threshold: Double,
        significanceLevel: SignificanceLevel,
        valueSelector: (PerformanceMetrics) -> T
    ): List<PerformanceAnomalyGroup> {
        val performanceGroups = groupJvmsByPerformanceWithError(
            metrics, valueSelector, threshold, significanceLevel
        )

        if (performanceGroups.size <= 1) return emptyList()

        val anomalyGroups = mutableListOf<PerformanceAnomalyGroup>()

        for (i in 0 until performanceGroups.size - 1) {
            for (j in i + 1 until performanceGroups.size) {
                val group1 = performanceGroups[i]
                val group2 = performanceGroups[j]

                val (avgValue1, avgError1) = calculateGroupStats(group1, valueSelector)
                val (avgValue2, avgError2) = calculateGroupStats(group2, valueSelector)

                if (!isSignificantDifference(avgValue1, avgError1, avgValue2, avgError2, significanceLevel)) {
                    continue
                }

                val avgDeviation = calculateAdjustedDeviation(
                    avgValue1, avgError1, avgValue2, avgError2, significanceLevel
                )

                if (avgDeviation <= threshold) continue

                val anomaly = createPerformanceAnomaly(
                    group1, group2, avgValue1, avgValue2, avgDeviation,
                    groupType, significanceLevel, valueSelector
                )

                anomalyGroups.add(anomaly)
            }
        }

        return anomalyGroups
    }

    /**
     * Вычисляет среднее значение и среднюю погрешность для группы JVM.
     */
    private fun <T : Number> calculateGroupStats(
        group: List<Pair<JvmExecutor, PerformanceMetrics>>,
        valueSelector: (PerformanceMetrics) -> T
    ): Pair<Double, Double> {
        val values = group.map { (_, metric) -> valueSelector(metric).toDouble() }
        val errors = group.map { (_, metric) -> metric.scoreError }

        return Pair(values.average(), errors.average())
    }

    private fun isSignificantDifference(
        value1: Double, error1: Double,
        value2: Double, error2: Double,
        significanceLevel: SignificanceLevel
    ): Boolean {
        return isDeviationStatisticallySignificant(
            value1, error1, value2, error2, significanceLevel
        )
    }

    private fun <T : Number> createPerformanceAnomaly(
        group1: List<Pair<JvmExecutor, PerformanceMetrics>>,
        group2: List<Pair<JvmExecutor, PerformanceMetrics>>,
        avgValue1: Double,
        avgValue2: Double,
        avgDeviation: Double,
        groupType: AnomalyGroupType,
        significanceLevel: SignificanceLevel,
        valueSelector: (PerformanceMetrics) -> T
    ): PerformanceAnomalyGroup {
        val (fasterGroup, slowerGroup) = if (avgValue1 < avgValue2) {
            Pair(group1, group2)
        } else {
            Pair(group2, group1)
        }

        val pairwiseDeviations = calculatePairwiseDeviationsWithError(
            fasterGroup, slowerGroup, significanceLevel, valueSelector
        )

        val deviationValues = pairwiseDeviations.values.flatMap { it.values }
        val minDeviation = deviationValues.minOrNull() ?: avgDeviation
        val maxDeviation = deviationValues.maxOrNull() ?: avgDeviation

        val fasterResults = fasterGroup.map { (jvm, metric) ->
            JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metric)
        }
        val slowerResults = slowerGroup.map { (jvm, metric) ->
            JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metric)
        }

        val interestingnessScore = calculatePerformanceGroupInterestingness(
            avgDeviation, fasterResults.size, slowerResults.size
        )

        val description = generateGroupDescriptionWithError(
            groupType, fasterResults, slowerResults, avgDeviation
        )

        return PerformanceAnomalyGroup(
            anomalyType = groupType,
            fasterJvms = fasterResults,
            slowerJvms = slowerResults,
            averageDeviation = avgDeviation,
            maxDeviation = maxDeviation,
            minDeviation = minDeviation,
            pairwiseDeviations = pairwiseDeviations,
            description = description,
            interestingnessScore = interestingnessScore
        )
    }

    /**
     * Проверяет, является ли разница статистически значимой с учетом погрешности измерений.
     */
    private fun isDeviationStatisticallySignificant(
        value1: Double, error1: Double,
        value2: Double, error2: Double,
        level: SignificanceLevel
    ): Boolean {
        val errorMultiplier = when (level) {
            SignificanceLevel.SEED_EVOLUTION -> 0.5
            SignificanceLevel.REPORTING -> 1.0
        }

        val adjustedError1 = error1 * errorMultiplier
        val adjustedError2 = error2 * errorMultiplier

        val lowerBound1 = value1 - adjustedError1
        val upperBound1 = value1 + adjustedError1
        val lowerBound2 = value2 - adjustedError2
        val upperBound2 = value2 + adjustedError2

        return !(upperBound1 >= lowerBound2 && upperBound2 >= lowerBound1)
    }

    private fun calculateAdjustedDeviation(
        value1: Double, error1: Double,
        value2: Double, error2: Double,
        level: SignificanceLevel
    ): Double {
        val rawDiff = abs(value1 - value2)

        val errorMultiplier = when (level) {
            SignificanceLevel.SEED_EVOLUTION -> 0.5
            SignificanceLevel.REPORTING -> 1.0
        }

        val adjustedError1 = error1 * errorMultiplier
        val adjustedError2 = error2 * errorMultiplier
        val errorSum = adjustedError1 + adjustedError2

        if (rawDiff <= errorSum) return 0.0

        val adjustedDiff = rawDiff - errorSum
        val minValue = min(value1, value2)

        // Защита от деления на очень маленькие числа
        if (minValue < 0.000001) return 0.0

        return (adjustedDiff / minValue) * 100.0
    }

    /**
     * Группирует JVM по производительности с учетом погрешности измерений.
     */
    private fun <T : Number> groupJvmsByPerformanceWithError(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        valueSelector: (PerformanceMetrics) -> T,
        threshold: Double,
        significanceLevel: SignificanceLevel
    ): List<List<Pair<JvmExecutor, PerformanceMetrics>>> {
        if (metrics.isEmpty()) return emptyList()

        val sortedMetrics = metrics.sortedBy { (_, metric) -> valueSelector(metric).toDouble() }
        val groups = mutableListOf<MutableList<Pair<JvmExecutor, PerformanceMetrics>>>()
        var currentGroup = mutableListOf(sortedMetrics.first())

        for (i in 1 until sortedMetrics.size) {
            val prev = sortedMetrics[i - 1]
            val current = sortedMetrics[i]

            val prevValue = valueSelector(prev.second).toDouble()
            val prevError = prev.second.scoreError
            val currentValue = valueSelector(current.second).toDouble()
            val currentError = current.second.scoreError

            val isStatisticallyClose = !isDeviationStatisticallySignificant(
                prevValue, prevError, currentValue, currentError, significanceLevel
            )

            val hasSmallDeviation = calculateAdjustedDeviation(
                prevValue, prevError, currentValue, currentError, significanceLevel
            ) <= threshold / 2

            if (isStatisticallyClose || hasSmallDeviation) {
                currentGroup.add(current)
            } else {
                groups.add(currentGroup)
                currentGroup = mutableListOf(current)
            }
        }

        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
        }

        return groups
    }

    /**
     * Рассчитывает попарные отклонения между JVM с учетом погрешности.
     */
    private fun <T : Number> calculatePairwiseDeviationsWithError(
        fasterGroup: List<Pair<JvmExecutor, PerformanceMetrics>>,
        slowerGroup: List<Pair<JvmExecutor, PerformanceMetrics>>,
        significanceLevel: SignificanceLevel,
        valueSelector: (PerformanceMetrics) -> T
    ): Map<String, Map<String, Double>> {
        val result = mutableMapOf<String, MutableMap<String, Double>>()

        for (slower in slowerGroup) {
            val slowerJvmName = slower.first::class.simpleName ?: "Unknown JVM"
            val slowerValue = valueSelector(slower.second).toDouble()
            val slowerError = slower.second.scoreError

            val deviationsForJvm = mutableMapOf<String, Double>()

            for (faster in fasterGroup) {
                val fasterJvmName = faster.first::class.simpleName ?: "Unknown JVM"
                val fasterValue = valueSelector(faster.second).toDouble()
                val fasterError = faster.second.scoreError

                // Вычисляем скорректированное отклонение
                val deviation = calculateAdjustedDeviation(
                    fasterValue, fasterError, slowerValue, slowerError, significanceLevel
                )

                // Сохраняем только значимые отклонения
                if (deviation > 0) {
                    deviationsForJvm[fasterJvmName] = deviation
                }
            }

            result[slowerJvmName] = deviationsForJvm
        }

        return result
    }

    private fun generateGroupDescriptionWithError(
        groupType: AnomalyGroupType,
        fasterResults: List<JvmPerformanceResult>,
        slowerResults: List<JvmPerformanceResult>,
        avgDeviation: Double
    ): String {
        val metricType = when (groupType) {
            AnomalyGroupType.TIME -> "времени выполнения"
            AnomalyGroupType.MEMORY -> "использования памяти"
            else -> "производительности"
        }

        val fasterJvms = fasterResults.joinToString(", ") { it.jvmName }
        val slowerJvms = slowerResults.joinToString(", ") { it.jvmName }
        val deviationFormatted = String.format("%.2f", avgDeviation)

        return "Группа JVM ($slowerJvms) статистически значимо медленнее по показателю $metricType на $deviationFormatted% по сравнению с группой ($fasterJvms)"
    }

    // --- Вспомогательные методы для расчета интересности ---

    private fun calculatePerformanceGroupInterestingness(
        avgDeviation: Double,
        fasterGroupSize: Int,
        slowerGroupSize: Int
    ): Double {
        var interestingness = avgDeviation / 10.0

        // Аномалия интереснее, если группы примерно равны по размеру
        val groupSizeRatio = min(fasterGroupSize, slowerGroupSize).toDouble() /
                max(fasterGroupSize, slowerGroupSize).toDouble()

        interestingness *= (0.5 + 0.5 * groupSizeRatio)

        return interestingness
    }

    private fun calculateTimeoutInterestingness(timeoutCount: Int, totalCount: Int): Double {
        // Таймаут интереснее, если он происходит только на части JVM
        return if (timeoutCount < totalCount) {
            TIMEOUT_INTERESTINGNESS_WEIGHT * (1.0 - timeoutCount.toDouble() / totalCount)
        } else {
            0.0
        }
    }

    private fun calculateErrorInterestingness(errorCount: Int, totalCount: Int): Double {
        // Ошибка интереснее, если она происходит только на части JVM
        return if (errorCount < totalCount) {
            ERROR_INTERESTINGNESS_WEIGHT * (1.0 - errorCount.toDouble() / totalCount)
        } else {
            0.0
        }
    }

    // --- Вспомогательные методы для JIT-анализа ---

    private fun collectJitProfiles(
        jvmExecutors: List<JvmExecutor>,
        metrics: Map<JvmExecutor, PerformanceMetrics>
    ): Map<String, JITProfile> {
        val profiles = mutableMapOf<String, JITProfile>()

        for (executor in jvmExecutors) {
            val jvmName = executor::class.simpleName ?: continue
            val metric = metrics[executor] ?: continue

            try {
                jitAnalyzer?.let {
                    profiles[jvmName] = it.analyzeJITLogs(executor, metric)
                }
            } catch (e: Exception) {
                println("Ошибка при анализе JIT-логов для $jvmName: ${e.message}")
            }
        }

        return profiles
    }

    private fun collectJitProfilesWithAnalyzer(
        jvmExecutors: List<JvmExecutor>,
        metrics: Map<JvmExecutor, PerformanceMetrics>,
        jitAnalyzer: JITAnalyzer
    ): Map<String, JITProfile> {
        val profiles = mutableMapOf<String, JITProfile>()

        for (executor in jvmExecutors) {
            val jvmName = executor::class.simpleName ?: continue
            val metric = metrics[executor] ?: continue

            try {
                profiles[jvmName] = jitAnalyzer.analyzeJITLogs(executor, metric)
            } catch (e: Exception) {
                println("Ошибка при анализе JIT-логов для $jvmName: ${e.message}")
            }
        }

        return profiles
    }

    private fun createJvmComparisons(
        jvmExecutors: List<JvmExecutor>,
        metrics: Map<JvmExecutor, PerformanceMetrics>,
        profiles: Map<String, JITProfile>
    ): List<JITComparisonResult> {
        val comparisons = mutableListOf<JITComparisonResult>()
        val jvmNames = profiles.keys.toList()

        for (i in 0 until jvmNames.size - 1) {
            for (j in i + 1 until jvmNames.size) {
                val jvm1 = jvmNames[i]
                val jvm2 = jvmNames[j]

                val exec1 = jvmExecutors.find { it::class.simpleName == jvm1 } ?: continue
                val exec2 = jvmExecutors.find { it::class.simpleName == jvm2 } ?: continue

                val time1 = metrics[exec1]?.avgTimeMs ?: continue
                val time2 = metrics[exec2]?.avgTimeMs ?: continue

                val (faster, slower) = if (time1 < time2) {
                    Pair(jvm1, jvm2)
                } else {
                    Pair(jvm2, jvm1)
                }

                jitAnalyzer?.let {
                    comparisons.add(
                        it.compareJITProfiles(
                            profiles[faster]!!,
                            profiles[slower]!!
                        )
                    )
                }
            }
        }

        return comparisons
    }

    private fun createJvmComparisonsWithAnalyzer(
        jvmExecutors: List<JvmExecutor>,
        metrics: Map<JvmExecutor, PerformanceMetrics>,
        profiles: Map<String, JITProfile>,
        jitAnalyzer: JITAnalyzer
    ): List<JITComparisonResult> {
        val comparisons = mutableListOf<JITComparisonResult>()
        val jvmNames = profiles.keys.toList()

        for (i in 0 until jvmNames.size - 1) {
            for (j in i + 1 until jvmNames.size) {
                val jvm1 = jvmNames[i]
                val jvm2 = jvmNames[j]

                val exec1 = jvmExecutors.find { it::class.simpleName == jvm1 } ?: continue
                val exec2 = jvmExecutors.find { it::class.simpleName == jvm2 } ?: continue

                val time1 = metrics[exec1]?.avgTimeMs ?: continue
                val time2 = metrics[exec2]?.avgTimeMs ?: continue

                val (faster, slower) = if (time1 < time2) {
                    Pair(jvm1, jvm2)
                } else {
                    Pair(jvm2, jvm1)
                }

                comparisons.add(
                    jitAnalyzer.compareJITProfiles(
                        profiles[faster]!!,
                        profiles[slower]!!
                    )
                )
            }
        }

        return comparisons
    }

    private fun calculateJITInterestScore(
        comparisons: List<JITComparisonResult>,
        jitProfiles: Map<String, JITProfile>
    ): Double {
        var interestScore = 0.0

        // Основная интересность от вероятности связи с JIT
        val maximumJITRelatedProbability = comparisons.maxOfOrNull { it.jitRelatedProbability } ?: 0.0
        interestScore += maximumJITRelatedProbability * JIT_RELATED_PROBABILITY_WEIGHT

        // Бонус за уникальные методы, скомпилированные только в одной JVM
        val maxUniqueMethodsCount = comparisons.maxOfOrNull {
            maxOf(it.uniqueOptimizationsInFaster.size, it.uniqueOptimizationsInSlower.size)
        } ?: 0
        interestScore += minOf(maxUniqueMethodsCount * JIT_UNIQUE_METHODS_WEIGHT, JIT_UNIQUE_METHODS_MAX)

        // Бонус за значимые деоптимизации
        val hasSignificantDeoptDifference = jitProfiles.values.map { it.deoptimizationCount }.distinct().size > 1
        if (hasSignificantDeoptDifference) {
            interestScore += JIT_DEOPT_DIFFERENCE_BONUS
        }

        // Бонус за горячие методы с высоким потенциалом проблем
        val hasInterestingHotMethods = detectInterestingHotMethods(comparisons)
        if (hasInterestingHotMethods) {
            interestScore += JIT_HOT_METHODS_BONUS
        }

        return interestScore
    }

    /**
     * Определяет наличие интересных паттернов в горячих методах.
     */
    private fun detectInterestingHotMethods(comparisons: List<JITComparisonResult>): Boolean {
        return comparisons.any { comparison ->
            comparison.hotMethods.any { method ->
                // Метод с деоптимизацией только в одной JVM
                (method.fasterJvmDeoptimized != method.slowerJvmDeoptimized) ||
                        // Большая разница в уровнях компиляции
                        (method.fasterJvmCompileLevel >= 0 && method.slowerJvmCompileLevel >= 0 &&
                                abs(method.fasterJvmCompileLevel - method.slowerJvmCompileLevel) >= 2)
            }
        }
    }
}

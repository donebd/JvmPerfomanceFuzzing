package infrastructure.performance

import infrastructure.jvm.JvmExecutor
import infrastructure.performance.anomaly.AnomalyGroupType
import infrastructure.performance.entity.JvmPerformanceResult
import infrastructure.performance.anomaly.PerformanceAnomalyGroup
import infrastructure.performance.entity.PerformanceMetrics
import kotlin.math.max
import kotlin.math.min

enum class AnomalyType {
    SIGNIFICANT,  // Явная, значимая аномалия (для отчетности)
    POTENTIAL     // Потенциальная, менее значимая (для отбора сидов)
}

class PerformanceAnalyzerImpl(
    private val significantTimeThreshold: Double = 30.0,
    private val significantMemoryThreshold: Double = 30.0,
    private val potentialTimeThreshold: Double = 10.0,
    private val potentialMemoryThreshold: Double = 10.0
) : PerformanceAnalyzer {

    override fun analyze(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        anomalyType: AnomalyType
    ): List<PerformanceAnomalyGroup> {
        if (metrics.size < 2) return emptyList() // Для анализа нужны хотя бы две JVM

        // Выбираем пороги в зависимости от типа анализа
        val timeThreshold = when (anomalyType) {
            AnomalyType.SIGNIFICANT -> significantTimeThreshold
            AnomalyType.POTENTIAL -> potentialTimeThreshold
        }
        val memoryThreshold = when (anomalyType) {
            AnomalyType.SIGNIFICANT -> significantMemoryThreshold
            AnomalyType.POTENTIAL -> potentialMemoryThreshold
        }

        val anomalyGroups = mutableListOf<PerformanceAnomalyGroup>()

        // Отфильтруем метрики с ошибками для дальнейшего анализа
        val validMetrics = metrics.filter { (_, metric) ->
            metric.successfullyParsed && !metric.jvmExecutionResult.timedOut
        }

        // 1. Сначала обрабатываем специальные случаи (таймауты, ошибки)
        val timeoutAnomalies = handleTimeoutAnomalies(metrics)
        val errorAnomalies = handleErrorAnomalies(metrics)

        anomalyGroups.addAll(timeoutAnomalies)
        anomalyGroups.addAll(errorAnomalies)

        // Только если есть валидные метрики для анализа
        if (validMetrics.size >= 2) {
            // 2. Группировка JVM по времени выполнения
            val timeAnomalies = findPerformanceAnomalies(
                validMetrics,
                AnomalyGroupType.TIME,
                timeThreshold,
                { it.avgTimeMs }
            )
            anomalyGroups.addAll(timeAnomalies)

            // 3. Группировка JVM по использованию памяти
            val memoryAnomalies = findPerformanceAnomalies(
                validMetrics,
                AnomalyGroupType.MEMORY,
                memoryThreshold,
                { it.avgMemoryUsageKb.toDouble() }
            )
            anomalyGroups.addAll(memoryAnomalies)
        }

        return anomalyGroups
    }

    private fun handleTimeoutAnomalies(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>
    ): List<PerformanceAnomalyGroup> {
        val timeoutJvms = metrics.filter { (_, metric) -> metric.jvmExecutionResult.timedOut }
        val normalJvms = metrics.filter { (_, metric) -> !metric.jvmExecutionResult.timedOut }

        if (timeoutJvms.isEmpty()) return emptyList()

        // Создаем группу аномалий только если не все JVM дали таймаут
        if (normalJvms.isNotEmpty()) {
            val timeoutResults = timeoutJvms.map { (jvm, metrics) ->
                JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metrics)
            }
            val normalResults = normalJvms.map { (jvm, metrics) ->
                JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metrics)
            }

            val description = if (timeoutJvms.size == 1) {
                "JVM ${timeoutResults.first().jvmName} завершилась по таймауту, в отличие от ${normalResults.size} других JVM"
            } else {
                "${timeoutResults.size} JVM завершились по таймауту, в отличие от ${normalResults.size} других JVM"
            }

            return listOf(
                PerformanceAnomalyGroup(
                    anomalyType = AnomalyGroupType.TIMEOUT,
                    fasterJvms = normalResults,
                    slowerJvms = timeoutResults,
                    averageDeviation = 100.0, // Таймаут считаем как 100% отклонение
                    maxDeviation = 100.0,
                    minDeviation = 100.0,
                    pairwiseDeviations = emptyMap(),
                    description = description,
                    interestingnessScore = calculateTimeoutInterestingness(timeoutJvms.size, metrics.size)
                )
            )
        }

        return emptyList()
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

        if (errorJvms.isEmpty()) return emptyList()

        // Группируем JVM по кодам ошибок
        val errorGroups = errorJvms.groupBy { (_, metric) -> metric.jvmExecutionResult.exitCode }

        val anomalyGroups = mutableListOf<PerformanceAnomalyGroup>()

        // Создаем группу аномалий для каждого типа ошибки
        errorGroups.forEach { (exitCode, jvmsWithError) ->
            // Создаем группу только если не все JVM дали одинаковую ошибку
            if (jvmsWithError.size < metrics.size) {
                val errorResults = jvmsWithError.map { (jvm, metrics) ->
                    JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metrics)
                }
                val validResults = validJvms.map { (jvm, metrics) ->
                    JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metrics)
                }

                val exitCodes = errorResults.associate { it.jvmName to exitCode }

                val description = if (errorResults.size == 1) {
                    "JVM ${errorResults.first().jvmName} завершилась с ошибкой (код $exitCode), в отличие от других JVM"
                } else {
                    "${errorResults.size} JVM завершились с ошибкой (код $exitCode), в отличие от других JVM"
                }

                anomalyGroups.add(
                    PerformanceAnomalyGroup(
                        anomalyType = AnomalyGroupType.ERROR,
                        fasterJvms = validResults,
                        slowerJvms = errorResults,
                        averageDeviation = 0.0,
                        maxDeviation = 0.0,
                        minDeviation = 0.0,
                        pairwiseDeviations = emptyMap(),
                        description = description,
                        exitCodes = exitCodes,
                        interestingnessScore = calculateErrorInterestingness(jvmsWithError.size, metrics.size)
                    )
                )
            }
        }

        return anomalyGroups
    }

    private fun <T : Number> findPerformanceAnomalies(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        groupType: AnomalyGroupType,
        threshold: Double,
        valueSelector: (PerformanceMetrics) -> T
    ): List<PerformanceAnomalyGroup> {
        // Группируем JVM по производительности с учетом погрешности
        val performanceGroups = groupJvmsByPerformanceWithError(metrics, valueSelector, threshold)

        if (performanceGroups.size <= 1) return emptyList()

        val anomalyGroups = mutableListOf<PerformanceAnomalyGroup>()

        // Ищем аномалии между группами
        for (i in 0 until performanceGroups.size - 1) {
            for (j in i + 1 until performanceGroups.size) {
                val group1 = performanceGroups[i]
                val group2 = performanceGroups[j]

                // Определяем какая группа быстрее
                val avgValue1 = group1.map { (_, metric) -> valueSelector(metric).toDouble() }.average()
                val avgValue2 = group2.map { (_, metric) -> valueSelector(metric).toDouble() }.average()

                // Средняя погрешность для групп
                val avgError1 = group1.map { (_, metric) -> metric.scoreError }.average()
                val avgError2 = group2.map { (_, metric) -> metric.scoreError }.average()

                // Проверяем статистическую значимость различий
                if (!isDeviationStatisticallySignificant(avgValue1, avgError1, avgValue2, avgError2)) {
                    continue // Пропускаем статистически незначимые различия
                }

                // Рассчитываем скорректированное отклонение
                val avgDeviation = calculateAdjustedDeviation(avgValue1, avgError1, avgValue2, avgError2)

                if (avgDeviation > threshold) {
                    // Определяем, какая группа быстрее/медленнее
                    val (fasterGroup, slowerGroup) = if (avgValue1 < avgValue2) {
                        Pair(group1, group2)
                    } else {
                        Pair(group2, group1)
                    }

                    // Рассчитываем детальные отклонения для каждой пары JVM с учетом погрешности
                    val pairwiseDeviations = calculatePairwiseDeviationsWithError(
                        fasterGroup, slowerGroup, valueSelector
                    )

                    // Находим минимальное и максимальное отклонение
                    val deviationValues = pairwiseDeviations.values.flatMap { it.values }
                    val minDeviation = deviationValues.minOrNull() ?: avgDeviation
                    val maxDeviation = deviationValues.maxOrNull() ?: avgDeviation

                    // Преобразуем для групповой аномалии
                    val fasterResults = fasterGroup.map { (jvm, metric) ->
                        JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metric)
                    }
                    val slowerResults = slowerGroup.map { (jvm, metric) ->
                        JvmPerformanceResult(jvm::class.simpleName ?: "Unknown JVM", metric)
                    }

                    // Создаем групповую аномалию
                    val anomalyGroup = PerformanceAnomalyGroup(
                        anomalyType = groupType,
                        fasterJvms = fasterResults,
                        slowerJvms = slowerResults,
                        averageDeviation = avgDeviation,
                        maxDeviation = maxDeviation,
                        minDeviation = minDeviation,
                        pairwiseDeviations = pairwiseDeviations,
                        description = generateGroupDescriptionWithError(
                            groupType, fasterResults, slowerResults, avgDeviation
                        ),
                        interestingnessScore = calculatePerformanceGroupInterestingness(
                            avgDeviation, fasterResults.size, slowerResults.size
                        )
                    )

                    anomalyGroups.add(anomalyGroup)
                }
            }
        }

        return anomalyGroups
    }

    /**
     * Проверяет, является ли разница статистически значимой с учетом погрешности измерений
     */
    private fun isDeviationStatisticallySignificant(
        value1: Double, error1: Double,
        value2: Double, error2: Double
    ): Boolean {
        // Проверяем, перекрываются ли доверительные интервалы
        val lowerBound1 = value1 - error1
        val upperBound1 = value1 + error1
        val lowerBound2 = value2 - error2
        val upperBound2 = value2 + error2

        // Если интервалы перекрываются, разница статистически незначима
        return !(upperBound1 >= lowerBound2 && upperBound2 >= lowerBound1)
    }

    /**
     * Рассчитывает скорректированное отклонение с учетом погрешности измерений
     */
    private fun calculateAdjustedDeviation(
        value1: Double, error1: Double,
        value2: Double, error2: Double
    ): Double {
        // Находим абсолютную разницу
        val rawDiff = Math.abs(value1 - value2)

        // Сумма погрешностей
        val errorSum = error1 + error2

        // Если разница меньше суммы погрешностей, считаем отклонение незначимым
        if (rawDiff <= errorSum) return 0.0

        // Иначе вычисляем скорректированное отклонение
        val adjustedDiff = rawDiff - errorSum
        val minValue = Math.min(value1, value2)

        // Защита от деления на очень маленькие числа
        if (minValue < 0.000001) return 0.0

        return (adjustedDiff / minValue) * 100.0
    }

    /**
     * Группирует JVM по производительности с учетом погрешности измерений
     */
    private fun <T : Number> groupJvmsByPerformanceWithError(
        metrics: List<Pair<JvmExecutor, PerformanceMetrics>>,
        valueSelector: (PerformanceMetrics) -> T,
        threshold: Double
    ): List<List<Pair<JvmExecutor, PerformanceMetrics>>> {
        if (metrics.isEmpty()) return emptyList()

        // Сортируем JVM по значению метрики
        val sortedMetrics = metrics.sortedBy { (_, metric) -> valueSelector(metric).toDouble() }

        // Группируем JVM с близкими значениями метрик с учетом погрешности
        val groups = mutableListOf<MutableList<Pair<JvmExecutor, PerformanceMetrics>>>()
        var currentGroup = mutableListOf(sortedMetrics.first())

        for (i in 1 until sortedMetrics.size) {
            val prev = valueSelector(sortedMetrics[i-1].second).toDouble()
            val prevError = sortedMetrics[i-1].second.scoreError
            val curr = valueSelector(sortedMetrics[i].second).toDouble()
            val currError = sortedMetrics[i].second.scoreError

            // Проверяем статистическую значимость различий
            if (!isDeviationStatisticallySignificant(prev, prevError, curr, currError) ||
                calculateAdjustedDeviation(prev, prevError, curr, currError) <= threshold / 2) {
                // JVM статистически близки по производительности - добавляем в текущую группу
                currentGroup.add(sortedMetrics[i])
            } else {
                // Значительное отличие - создаем новую группу
                groups.add(currentGroup)
                currentGroup = mutableListOf(sortedMetrics[i])
            }
        }

        // Добавляем последнюю группу
        if (currentGroup.isNotEmpty()) {
            groups.add(currentGroup)
        }

        return groups
    }

    /**
     * Рассчитывает попарные отклонения между JVM с учетом погрешности
     */
    private fun <T : Number> calculatePairwiseDeviationsWithError(
        fasterGroup: List<Pair<JvmExecutor, PerformanceMetrics>>,
        slowerGroup: List<Pair<JvmExecutor, PerformanceMetrics>>,
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

                // Используем скорректированное отклонение с учетом погрешности
                val deviation = calculateAdjustedDeviation(fasterValue, fasterError, slowerValue, slowerError)

                if (deviation > 0) {  // Сохраняем только значимые отклонения
                    deviationsForJvm[fasterJvmName] = deviation
                }
            }

            result[slowerJvmName] = deviationsForJvm
        }

        return result
    }

    private fun calculatePerformanceGroupInterestingness(
        avgDeviation: Double,
        fasterGroupSize: Int,
        slowerGroupSize: Int
    ): Double {
        // Базовая интересность зависит от величины отклонения
        var interestingness = avgDeviation / 10.0

        // Аномалия интереснее, если группы примерно равны по размеру
        val groupSizeRatio = min(fasterGroupSize, slowerGroupSize).toDouble() /
                max(fasterGroupSize, slowerGroupSize).toDouble()

        // Корректируем интересность с учетом размеров групп
        interestingness *= (0.5 + 0.5 * groupSizeRatio)

        return interestingness
    }

    private fun calculateTimeoutInterestingness(timeoutCount: Int, totalCount: Int): Double {
        // Таймаут интереснее, если он происходит только на части JVM
        return if (timeoutCount < totalCount) {
            50.0 * (1.0 - timeoutCount.toDouble() / totalCount)
        } else {
            0.0
        }
    }

    private fun calculateErrorInterestingness(
        errorCount: Int,
        totalCount: Int
    ): Double {
        // Ошибка интереснее, если она происходит только на части JVM
        return if (errorCount < totalCount) {
            30.0 * (1.0 - errorCount.toDouble() / totalCount)
        } else {
            0.0
        }
    }

    /**
     * Генерирует описание аномалии с указанием о статистической значимости
     */
    private fun generateGroupDescriptionWithError(
        groupType: AnomalyGroupType,
        fasterResults: List<JvmPerformanceResult>,
        slowerResults: List<JvmPerformanceResult>,
        avgDeviation: Double
    ): String {
        val metricType = when(groupType) {
            AnomalyGroupType.TIME -> "времени выполнения"
            AnomalyGroupType.MEMORY -> "использования памяти"
            else -> "производительности"
        }

        val fasterJvms = fasterResults.joinToString(", ") { it.jvmName }
        val slowerJvms = slowerResults.joinToString(", ") { it.jvmName }
        val deviationFormatted = String.format("%.2f", avgDeviation)

        return "Группа JVM ($slowerJvms) статистически значимо медленнее по показателю $metricType на $deviationFormatted% по сравнению с группой ($fasterJvms)"
    }

    override fun areAnomaliesInteresting(anomalies: List<PerformanceAnomalyGroup>): Boolean {
        if (anomalies.isEmpty()) return false

        // Проверяем специальные случаи
        val timeoutAnomalies = anomalies.filter { it.anomalyType == AnomalyGroupType.TIMEOUT }
        if (timeoutAnomalies.isNotEmpty() && timeoutAnomalies.all { it.interestingnessScore <= 0 }) {
            return false
        }

        val errorAnomalies = anomalies.filter { it.anomalyType == AnomalyGroupType.ERROR }
        return !(errorAnomalies.isNotEmpty() && errorAnomalies.all { it.interestingnessScore <= 0 })
    }

    override fun calculateOverallInterestingness(anomalies: List<PerformanceAnomalyGroup>): Double {
        if (!areAnomaliesInteresting(anomalies)) return 0.0

        return anomalies.sumOf { it.interestingnessScore } / anomalies.size
    }
}

package infrastructure.performance.entity

import infrastructure.performance.anomaly.PerformanceAnomalyGroup

/**
 * Результат анализа производительности с выявленными аномалиями.
 *
 * @property anomalies список обнаруженных аномалий производительности
 * @property significanceLevel уровень значимости, использованный при анализе
 * @property isInteresting признак того, что результат представляет интерес для дальнейшего исследования
 * @property interestingnessScore количественная оценка значимости результата (чем выше, тем интереснее)
 */
data class AnalysisResult(
    val anomalies: List<PerformanceAnomalyGroup>,
    val significanceLevel: SignificanceLevel,
    val isInteresting: Boolean,
    val interestingnessScore: Double
)

package infrastructure.performance.entity

import infrastructure.performance.anomaly.JITAnomalyData
import infrastructure.performance.anomaly.PerformanceAnomalyGroup

/**
 * Расширенный результат анализа производительности, включающий данные JIT-компиляции.
 * Содержит как стандартные метрики производительности, так и информацию о JIT-оптимизациях.
 *
 * @property anomalies список обнаруженных аномалий производительности
 * @property significanceLevel уровень значимости, использованный при анализе
 * @property isAnomaliesInteresting признак того, что аномалии представляют интерес для дальнейшего исследования
 * @property interestingnessScore количественная оценка значимости результата
 * @property jitData данные анализа JIT-компиляции или null, если JIT-анализ не выполнялся
 */
data class ExtendedAnalysisResult(
    val anomalies: List<PerformanceAnomalyGroup>,
    val significanceLevel: SignificanceLevel,
    val isAnomaliesInteresting: Boolean,
    val interestingnessScore: Double,
    val jitData: JITAnomalyData?
)

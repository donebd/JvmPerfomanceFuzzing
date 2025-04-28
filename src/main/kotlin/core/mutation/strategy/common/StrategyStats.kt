package core.mutation.strategy.common

import kotlin.math.exp

/**
 * Хранит статистику применения стратегии мутации для оценки её эффективности.
 */
data class StrategyStats(
    var totalApplications: Int = 0,
    var successfulMutations: Int = 0,
    var seedsGenerated: Int = 0,
    var anomaliesFound: Int = 0,
    var failures: Int = 0
) {
    /**
     * Рассчитывает эффективность стратегии на основе накопленной статистики.
     * Возвращает значение в диапазоне [0.1, 1.0], где:
     * - 0.1 - минимальная эффективность (для новых или неуспешных стратегий)
     * - 1.0 - максимальная эффективность (для стратегий с высоким числом найденных аномалий)
     *
     * Формула использует взвешенные метрики и сигмоидное преобразование.
     */
    fun calculateEffectiveness(): Double {
        if (totalApplications == 0) return MIN_EFFECTIVENESS

        val weights = mapOf(
            successfulMutations to WEIGHT_SUCCESSFUL_MUTATIONS,
            seedsGenerated to WEIGHT_SEEDS_GENERATED,
            anomaliesFound to WEIGHT_ANOMALIES_FOUND
        )

        val positiveScore = weights.entries.sumOf { (metric, weight) -> metric * weight }
        val negativeScore = failures * WEIGHT_FAILURES
        val baseScore = (positiveScore - negativeScore) / totalApplications

        // Сигмоидное преобразование в диапазон [MIN_EFFECTIVENESS, 1.0]
        return MIN_EFFECTIVENESS + (1.0 - MIN_EFFECTIVENESS) / (1.0 + exp(-SIGMOID_STEEPNESS * baseScore))
    }

    companion object {
        private const val MIN_EFFECTIVENESS = 0.1
        private const val WEIGHT_SUCCESSFUL_MUTATIONS = 0.2
        private const val WEIGHT_SEEDS_GENERATED = 0.5
        private const val WEIGHT_ANOMALIES_FOUND = 1.0
        private const val WEIGHT_FAILURES = 0.1
        private const val SIGMOID_STEEPNESS = 2.0
    }
}

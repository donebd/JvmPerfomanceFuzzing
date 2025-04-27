package core.mutation.strategy.common

import kotlin.math.exp

data class StrategyStats(
    var totalApplications: Int = 0,
    var successfulMutations: Int = 0,
    var seedsGenerated: Int = 0,
    var anomaliesFound: Int = 0,
    var failures: Int = 0
) {
    fun calculateEffectiveness(): Double {
        if (totalApplications == 0) return 0.1

        val baseScore = (0.2 * successfulMutations +
                0.5 * seedsGenerated +
                1.0 * anomaliesFound -
                0.1 * failures) / totalApplications

        // Преобразование через сигмоидную функцию в диапазон (0,1)
        return 0.1 + 0.9 / (1.0 + exp(-2 * baseScore))
    }
}
package core.mutation;

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import kotlin.math.exp
import kotlin.random.Random

class AdaptiveMutator(
    private val jimpleTranslator: JimpleTranslator,
    private val strategies: List<MutationStrategy>,
    private val explorationFactor: Double = 0.1
) : Mutator {
    private val strategyStats = mutableMapOf<String, StrategyStats>()
    private val random = Random

    private var lastMutationRecord: MutationRecord? = null

    data class StrategyStats(
        var totalApplications: Int = 0,
        var successfulMutations: Int = 0,
        var seedsGenerated: Int = 0,
        var anomaliesFound: Int = 0,
        var failures: Int = 0
    ) {
        fun calculateEffectiveness(): Double {
            if (totalApplications == 0) return 0.1

            val baseScore =
                (0.2 * successfulMutations + 0.5 * seedsGenerated + 1.0 * anomaliesFound - 0.3 * failures) / totalApplications

            // Преобразование через сигмоидную функцию в диапазон (0,1)
            // или можно использовать: return 1.0 / (1.0 + Math.exp(-baseScore))
            return 0.1 + 0.9 / (1.0 + exp(-2 * baseScore))
        }
    }

    override fun mutate(bytecode: ByteArray, className: String, packageName: String): ByteArray {
        val jimpleCode = jimpleTranslator.toJimple(bytecode, className)
        var mutatedJimple = jimpleCode.data

        // Выбор стратегии с использованием рулетки
        val selectedStrategy = selectStrategyByRoulette()
        val strategyName = selectedStrategy::class.simpleName ?: "Unknown"

        val newJimple = selectedStrategy.apply(mutatedJimple, className, packageName)
        lastMutationRecord = MutationRecord(
            parentSeedDescription = "",  // Будет заполнено в EvolutionaryFuzzer
            strategyName = strategyName
        )
        updateStats(strategyName, wasSuccessful = !newJimple.hadError)

        if (!newJimple.hadError) {
            mutatedJimple = newJimple.jimpleCode
        }

        return jimpleTranslator.toBytecode(mutatedJimple, className, packageName)
    }

    /**
     * Выбирает стратегию с помощью метода рулетки на основе весов эффективности
     */
    private fun selectStrategyByRoulette(): MutationStrategy {
        // Случайный выбор с вероятностью exploration для чистого исследования
        if (random.nextDouble() < explorationFactor) {
            return strategies.random()
        }

        // Рассчитываем веса для каждой стратегии
        val weightedStrategies = strategies.map { strategy ->
            val name = strategy::class.simpleName ?: "Unknown"
            val effectiveness = getEffectiveness(name)
            strategy to effectiveness
        }

        // Общая сумма весов
        val totalWeight = weightedStrategies.sumOf { it.second }

        // Если все веса нулевые, выбираем случайно
        if (totalWeight <= 0) {
            return strategies.random()
        }

        // Метод рулетки - выбор на основе относительных весов
        var cumulativeWeight = 0.0
        val randomPoint = random.nextDouble() * totalWeight

        for ((strategy, weight) in weightedStrategies) {
            cumulativeWeight += weight
            if (randomPoint <= cumulativeWeight) {
                return strategy
            }
        }

        // Fallback - случай, который теоретически не должен произойти
        return strategies.random()
    }

    private fun getEffectiveness(strategyName: String): Double {
        return strategyStats[strategyName]?.calculateEffectiveness() ?: 0.1
    }

    private fun updateStats(
        strategyName: String,
        wasSuccessful: Boolean,
    ) {
        val stats = strategyStats.getOrPut(strategyName) { StrategyStats() }
        stats.totalApplications++

        if (wasSuccessful) stats.successfulMutations++
    }

    fun getLastMutationRecord(): MutationRecord? {
        return lastMutationRecord
    }

    // Методы для обратной связи от фаззера
    fun notifyNewSeedGenerated(foundAnomaly: Boolean = false) {
        lastMutationRecord?.let { mutation ->
            val stats = strategyStats[mutation.strategyName] ?: return
            stats.seedsGenerated++
            if (foundAnomaly) stats.anomaliesFound++
        }
    }

    fun notifySeedRejected() {
        lastMutationRecord?.let { mutation ->
            val stats = strategyStats[mutation.strategyName] ?: return
            stats.failures++
        }
    }
}
package core.mutation

import core.mutation.strategy.common.MutationStrategy
import core.mutation.strategy.common.StrategyStats
import infrastructure.translator.JimpleTranslator
import kotlin.random.Random

/**
 * Адаптивный мутатор, который выбирает стратегии мутации на основе их эффективности.
 * Использует механизм "рулетки" с весами, основанными на предыдущих результатах.
 */
class AdaptiveMutator(
    private val jimpleTranslator: JimpleTranslator,
    private val strategies: List<MutationStrategy>,
    private val explorationFactor: Double = 0.2,
    private val forgetFactor: Double = 0.9,
    private val forgetFrequency: Int = 250
) : Mutator {
    private var iterationsSinceLastForget = 0
    private val strategyStats = mutableMapOf<String, StrategyStats>()
    private val random = Random

    private var lastMutationRecord: MutationRecord? = null

    override fun mutate(bytecode: ByteArray, className: String, packageName: String): ByteArray {
        refreshStatistics()

        val jimpleCode = jimpleTranslator.toJimple(bytecode, className)
        val selectedStrategy = selectStrategy()
        val strategyName = selectedStrategy::class.simpleName ?: "Unknown"

        val result = selectedStrategy.apply(jimpleCode.data, className, packageName)
        lastMutationRecord = MutationRecord(
            parentSeedDescription = "",  // Заполняется в EvolutionaryFuzzer
            strategyName = strategyName
        )

        updateStats(strategyName, wasSuccessful = !result.hadError)

        val finalJimple = if (!result.hadError) result.jimpleCode else jimpleCode.data
        return jimpleTranslator.toBytecode(finalJimple, className, packageName)
    }

    fun getLastMutationRecord(): MutationRecord? = lastMutationRecord

    fun notifyNewSeedGenerated(foundAnomaly: Boolean = false) {
        lastMutationRecord?.let { mutation ->
            strategyStats[mutation.strategyName]?.apply {
                seedsGenerated++
                if (foundAnomaly) anomaliesFound++
            }
        }
    }

    fun notifySeedRejected() {
        lastMutationRecord?.let { mutation ->
            strategyStats[mutation.strategyName]?.apply {
                failures++
            }
        }
    }

    private fun selectStrategy(): MutationStrategy {
        if (random.nextDouble() < explorationFactor) {
            return strategies.random()
        }

        val weightedStrategies = strategies.map { strategy ->
            val name = strategy::class.simpleName ?: "Unknown"
            val effectiveness = strategyStats[name]?.calculateEffectiveness() ?: 0.1
            strategy to effectiveness
        }

        val totalWeight = weightedStrategies.sumOf { it.second }
        if (totalWeight <= 0) {
            return strategies.random()
        }

        val randomPoint = random.nextDouble() * totalWeight
        var cumulativeWeight = 0.0

        for ((strategy, weight) in weightedStrategies) {
            cumulativeWeight += weight
            if (randomPoint <= cumulativeWeight) {
                return strategy
            }
        }

        return strategies.random()
    }

    private fun updateStats(strategyName: String, wasSuccessful: Boolean) {
        strategyStats.getOrPut(strategyName) { StrategyStats() }.apply {
            totalApplications++
            if (wasSuccessful) successfulMutations++
        }
    }

    private fun refreshStatistics() {
        if (++iterationsSinceLastForget >= forgetFrequency) {
            applyForgetFactor()
            iterationsSinceLastForget = 0
        }
    }

    private fun applyForgetFactor() {
        strategyStats.values.forEach { stats ->
            with(stats) {
                totalApplications = (totalApplications * forgetFactor).toInt()
                successfulMutations = (successfulMutations * forgetFactor).toInt()
                seedsGenerated = (seedsGenerated * forgetFactor).toInt()
                anomaliesFound = (anomaliesFound * forgetFactor).toInt()
                failures = (failures * forgetFactor).toInt()
            }
        }
    }
}
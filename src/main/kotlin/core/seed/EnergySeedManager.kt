package core.seed

import java.util.*
import kotlin.math.max

/**
 * Менеджер сидов, реализующий энергетико-интересностную модель отбора.
 *
 * Управляет популяцией сидов, добавляет новые и начальные сиды, регулирует энергию,
 * восстанавливает полезные сиды и удаляет неэффективные при переполнении пула.
 */
class EnergySeedManager(
    private val maxPoolSize: Int = 100,
    private val minEnergyThreshold: Int = 3,
    private val energyBoost: Int = 5
) : SeedManager {

    private val seedPool = mutableSetOf<Seed>()
    private val initialSeeds = mutableListOf<Seed>()
    private val random = Random()

    private val minActiveSeedsThreshold: Int
        get() = initialSeeds.size

    override fun addInitialSeeds(seeds: List<Seed>): Int {
        return seeds.count {
            initialSeeds.add(it.copy())
            addSeed(it)
        }
    }

    override fun addSeed(seed: Seed): Boolean {
        val isDuplicate = seedPool.any { it.bytecodeEntry.bytecode.contentEquals(seed.bytecodeEntry.bytecode) }
        if (isDuplicate) return false

        val added = seedPool.add(seed)
        if (added && seedPool.size > maxPoolSize) prunePool()
        return added
    }

    override fun selectSeedForMutation(): Seed? {
        if (seedPool.none { it.energy > 0 }) boostSeedEnergy()

        removeDeadSeeds()

        if (seedPool.isEmpty()) restoreInitialSeeds()
        if (seedPool.isEmpty()) return null

        if (seedPool.sumOf { it.energy } < seedPool.size) {
            seedPool.forEach { it.energy += energyBoost }
        }

        val activeSeeds = seedPool.filter { it.energy > 0 }
        if (activeSeeds.isEmpty()) return null

        return if (random.nextDouble() < 0.1) {
            activeSeeds.random()
        } else {
            selectWeightedByEnergy(activeSeeds)
        }
    }

    override fun decrementSeedEnergy(seed: Seed) {
        seedPool.find { it == seed }?.let {
            it.energy = max(0, it.energy - 1)
        }
    }

    override fun getSeedCount(): Int = seedPool.size

    // --------------------- Internal logic ---------------------

    private fun prunePool() {
        val toRemove = seedPool
            .filterNot { it.verified || isInitial(it) }
            .sortedBy { it.energy * (1.0 + it.interestingness) }
            .take(seedPool.size - maxPoolSize)
            .toMutableList()

        if (toRemove.size < seedPool.size - maxPoolSize) {
            val additional = seedPool
                .filter { it.verified && !isInitial(it) }
                .sortedBy { it.energy * (1.0 + it.interestingness) }
                .take((seedPool.size - maxPoolSize) - toRemove.size)

            toRemove.addAll(additional)
        }

        toRemove.forEach {
            seedPool.remove(it)
            println("Удален сид ${it.description}, энергия: ${it.energy}, интересность: ${it.interestingness}, верифицирован: ${it.verified}")
        }
    }

    private fun boostSeedEnergy() {
        val verifiedLow = seedPool.filter { it.verified && it.energy <= minEnergyThreshold }
        val additionalNeeded = minActiveSeedsThreshold - verifiedLow.size

        val nonVerifiedLow = if (additionalNeeded > 0) {
            seedPool
                .filter { !it.verified && it.energy <= minEnergyThreshold }
                .sortedByDescending { it.interestingness }
                .take(additionalNeeded)
        } else emptyList()

        val toBoost = (verifiedLow + nonVerifiedLow).toMutableSet()

        if (toBoost.size < minActiveSeedsThreshold) {
            val remainder = seedPool
                .filterNot { it in toBoost }
                .sortedBy { it.energy }
                .take(minActiveSeedsThreshold - toBoost.size)

            toBoost.addAll(remainder)
        }

        toBoost.forEach {
            it.energy += energyBoost
            println("Восстановлена энергия: ${it.description}, новая энергия: ${it.energy}, верифицирован: ${it.verified}")
        }

        boostInitialSeeds()
    }

    private fun boostInitialSeeds() {
        for (initial in initialSeeds) {
            if (initial.energy >= minEnergyThreshold) continue

            initial.energy += energyBoost

            val existing = seedPool.find { it.bytecodeEntry.bytecode.contentEquals(initial.bytecodeEntry.bytecode) }

            if (existing != null) {
                existing.energy = initial.energy
                if (initial.verified && !existing.verified) existing.verified = true
            } else {
                seedPool.add(initial.copy())
                println("Восстановлен начальный сид: ${initial.description}, энергия: ${initial.energy}, верифицирован: ${initial.verified}")
            }
        }
    }

    private fun restoreInitialSeeds() {
        for (initial in initialSeeds) {
            val restored = initial.copy().apply {
                energy = max(initial.energy, energyBoost)
            }
            seedPool.add(restored)
            println("Пул пуст: добавлен ${restored.description}, энергия: ${restored.energy}, верифицирован: ${restored.verified}")
        }
    }

    private fun removeDeadSeeds() {
        seedPool.removeIf { it.energy <= 0 && !it.verified && !isInitial(it) }
    }

    private fun isInitial(seed: Seed): Boolean {
        return initialSeeds.any { it.bytecodeEntry.bytecode.contentEquals(seed.bytecodeEntry.bytecode) }
    }

    private fun selectWeightedByEnergy(seeds: List<Seed>): Seed? {
        val verificationBonus = 2.0

        val weighted = seeds.map {
            val weight = if (it.verified) (it.energy * verificationBonus).toInt() else it.energy
            it to weight
        }

        val total = weighted.sumOf { it.second }.coerceAtLeast(1)
        var point = random.nextInt(total)

        for ((seed, weight) in weighted) {
            point -= weight
            if (point < 0) return seed
        }

        return seeds.randomOrNull()
    }
}

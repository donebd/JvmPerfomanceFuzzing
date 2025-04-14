package core.seed

import java.util.*
import kotlin.math.max

/**
 * Реализация менеджера сидов с применением стратегии выбора по энергии и интересности
 * с поддержкой верифицированных сидов
 */
class EnergySeedManager(
    private val maxPoolSize: Int = 100,
    private val minEnergyThreshold: Int = 3,   // Порог для восстановления энергии
    private val energyBoost: Int = 5           // Насколько пополнять энергию
) : SeedManager {
    private val seedPool = mutableSetOf<Seed>()
    private val random = Random()
    private val initialSeeds = mutableListOf<Seed>()

    private val minActiveSeedsThreshold: Int
        get() = initialSeeds.size

    override fun addInitialSeeds(seeds: List<Seed>): Int {
        var addedCount = 0

        for (seed in seeds) {
            initialSeeds.add(seed.copy())
            if (addSeed(seed)) {
                addedCount++
            }
        }

        return addedCount
    }

    override fun addSeed(seed: Seed): Boolean {
        // Проверяем дубликаты по байткоду
        val existingSeed = seedPool.find { it.bytecodeEntry.bytecode.contentEquals(seed.bytecodeEntry.bytecode) }
        if (existingSeed != null) {
            return false
        }

        val added = seedPool.add(seed)

        // Если пул переполнен, удаляем наименее ценные сиды
        if (added && seedPool.size > maxPoolSize) {
            pruneSeeds()
        }

        return added
    }

    private fun pruneSeeds() {
        // Находим сиды, которые не являются начальными и не верифицированы
        val removeCandidates = seedPool
            .filter { seed ->
                !seed.verified && initialSeeds.none { it.bytecodeEntry.bytecode.contentEquals(seed.bytecodeEntry.bytecode) }
            }
            .sortedBy { it.energy * (1.0 + it.interestingness) }

        val numberOfSeedsToRemove = seedPool.size - maxPoolSize

        // Если неверифицированных сидов недостаточно, придется удалить и некоторые верифицированные
        if (removeCandidates.size < numberOfSeedsToRemove) {
            // Добавляем верифицированные сиды, которые не являются начальными
            val additionalCandidates = seedPool
                .filter { seed ->
                    seed.verified && initialSeeds.none { it.bytecodeEntry.bytecode.contentEquals(seed.bytecodeEntry.bytecode) }
                }
                .sortedBy { it.energy * (1.0 + it.interestingness) }
                .take(numberOfSeedsToRemove - removeCandidates.size)

            // Удаляем сиды
            (removeCandidates + additionalCandidates).forEach { seedToRemove ->
                seedPool.remove(seedToRemove)
                println(
                    "Пул сидов переполнен: удален сид ${seedToRemove.description} " +
                            "с энергией ${seedToRemove.energy}, интересностью ${seedToRemove.interestingness}, " +
                            "верифицирован: ${seedToRemove.verified}"
                )
            }
        } else {
            // Удаляем только неверифицированные сиды
            removeCandidates.take(numberOfSeedsToRemove).forEach { seedToRemove ->
                seedPool.remove(seedToRemove)
                println(
                    "Пул сидов переполнен: удален сид ${seedToRemove.description} " +
                            "с энергией ${seedToRemove.energy}, интересностью ${seedToRemove.interestingness}"
                )
            }
        }
    }

    override fun selectSeedForMutation(): Seed? {
        val activeSeedsCount = seedPool.count { it.energy > 0 }

        // Восстанавливаем энергию при недостатке активных сидов
        if (activeSeedsCount < minActiveSeedsThreshold) {
            boostSeedEnergy()
        }

        // Удаляем неактивные сиды, кроме начальных и верифицированных
        if (seedPool.count { it.energy > 0 } >= minActiveSeedsThreshold) {
            seedPool.removeIf { seed ->
                seed.energy <= 0 &&
                        !seed.verified &&
                        initialSeeds.none { it.bytecodeEntry.bytecode.contentEquals(seed.bytecodeEntry.bytecode) }
            }
        }

        // Восстанавливаем начальные сиды если пул пуст
        if (seedPool.isEmpty() && initialSeeds.isNotEmpty()) {
            for (initialSeed in initialSeeds) {
                val seedCopy = initialSeed.copy().apply {
                    energy = max(initialSeed.energy, energyBoost)
                }
                seedPool.add(seedCopy)
                println(
                    "Пул сидов пуст: добавлен начальный сид ${seedCopy.description} " +
                            "с энергией ${seedCopy.energy}, верифицирован: ${seedCopy.verified}"
                )
            }
        }

        if (seedPool.isEmpty()) return null

        // Если общая энергия очень низкая, добавляем энергию всем сидам
        val totalEnergy = seedPool.sumOf { it.energy }
        if (totalEnergy < seedPool.size) {
            seedPool.forEach { it.energy += energyBoost }
        }

        // Приоритизируем верифицированные сиды, если они есть и имеют энергию
        val verifiedSeeds = seedPool.filter { it.verified && it.energy > 0 }
        if (verifiedSeeds.isNotEmpty()) {
            // Вероятностный выбор среди верифицированных сидов
            val verifiedTotalEnergy = max(1, verifiedSeeds.sumOf { it.energy })
            var randomPoint = random.nextInt(verifiedTotalEnergy)

            for (seed in verifiedSeeds) {
                randomPoint -= seed.energy
                if (randomPoint < 0) {
                    return seed
                }
            }

            return verifiedSeeds.random()
        }

        // Если нет верифицированных, используем обычный вероятностный выбор
        val finalTotalEnergy = max(1, seedPool.sumOf { it.energy })
        var randomPoint = random.nextInt(finalTotalEnergy)

        for (seed in seedPool) {
            randomPoint -= seed.energy
            if (randomPoint < 0) {
                return seed
            }
        }

        return seedPool.random()
    }

    private fun boostSeedEnergy() {
        // Сначала восстанавливаем верифицированные сиды с низкой энергией
        val verifiedLowEnergySeeds = seedPool.filter { it.verified && it.energy <= minEnergyThreshold }

        // Определяем сколько еще сидов нужно восстановить
        val additionalSeedsNeeded = minActiveSeedsThreshold - verifiedLowEnergySeeds.size

        val seedsToBoost = if (additionalSeedsNeeded > 0) {
            // Добавляем неверифицированные сиды с низкой энергией по интересности
            val nonVerifiedLowEnergySeeds = seedPool
                .filter { !it.verified && it.energy <= minEnergyThreshold }
                .sortedByDescending { it.interestingness }
                .take(additionalSeedsNeeded)

            verifiedLowEnergySeeds + nonVerifiedLowEnergySeeds
        } else {
            verifiedLowEnergySeeds
        }.toMutableList()

        // Если все еще недостаточно сидов, добавляем на основе энергии
        if (seedsToBoost.size < minActiveSeedsThreshold) {
            val remainingNeeded = minActiveSeedsThreshold - seedsToBoost.size
            val remainingSeeds = seedPool
                .filter { seed -> !seedsToBoost.contains(seed) }
                .sortedBy { it.energy }
                .take(remainingNeeded)

            seedsToBoost.addAll(remainingSeeds)
        }

        // Восстанавливаем энергию выбранным сидам
        seedsToBoost.forEach {
            it.energy += energyBoost
            println("Восстановлена энергия сида ${it.description}: новое значение ${it.energy}, верифицирован: ${it.verified}")
        }

        // Обрабатываем начальные сиды
        for (initialSeed in initialSeeds) {
            if (initialSeed.energy < minEnergyThreshold) {
                initialSeed.energy += energyBoost

                // Ищем соответствующий сид в пуле
                val existingSeed = seedPool.find {
                    it.bytecodeEntry.bytecode.contentEquals(initialSeed.bytecodeEntry.bytecode)
                }

                if (existingSeed != null) {
                    existingSeed.energy = initialSeed.energy
                    // Обновляем статус верификации, если начальный сид верифицирован
                    if (initialSeed.verified && !existingSeed.verified) {
                        existingSeed.verified = true
                    }
                } else {
                    seedPool.add(initialSeed.copy())
                    println("Восстановлен начальный сид ${initialSeed.description} с энергией ${initialSeed.energy}, верифицирован: ${initialSeed.verified}")
                }
            }
        }
    }

    override fun decrementSeedEnergy(seed: Seed) {
        seedPool.find { it == seed }?.let {
            it.energy = max(0, it.energy - 1)
        }
    }

    override fun getSeedCount(): Int = seedPool.size
}

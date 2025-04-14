package core.seed

/**
 * Менеджер для управления пулом сидов
 */
interface SeedManager {

    fun addInitialSeeds(seeds: List<Seed>): Int

    /**
     * Добавляет новый сид в пул
     * @return true если сид был добавлен, false если был отклонен (например, дубликат)
     */
    fun addSeed(seed: Seed): Boolean

    /**
     * Выбирает сид для мутации на основе стратегии
     */
    fun selectSeedForMutation(): Seed?

    /**
     * Уменьшает энергию сида после использования
     */
    fun decrementSeedEnergy(seed: Seed)

    /**
     * Возвращает текущее количество сидов в пуле
     */
    fun getSeedCount(): Int
}

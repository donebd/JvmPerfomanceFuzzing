package infrastructure.performance.anomaly

import core.seed.Seed

interface AnomalyRepository {
    /**
     * Сохраняет аномалии
     *
     * @param seed объект сида для сохранения его аномалий.
     */
    fun saveSeedAnomalies(seed: Seed)

    /**
     * Загружает все сиды с аномалиями из репозитория.
     * @return Список всех сидов, найденных в репозитории
     */
    fun getAllSeeds(): List<Seed>

    /**
     * Удаляет все аномалии из репозитория.
     */
    fun clear()

}

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
     * Удаляет все аномалии из репозитория.
     */
    fun clear()

}

package infrastructure.performance.verify

import core.seed.Seed
import infrastructure.jvm.JvmExecutor

/**
 * Интерфейс для проверки и подтверждения аномалий
 */
interface AnomalyVerifier {
    /**
     * Добавляет сид в очередь на детальную проверку
     */
    fun addSeedForVerification(seed: Seed)

    /**
     * Проверяет, нужно ли выполнить детальную проверку
     */
    fun shouldPerformDetailedCheck(): Boolean

    /**
     * Сообщает верификатору о новой итерации
     */
    fun onNewIteration()

    /**
     * Выполняет детальную проверку накопленных сидов
     */
    fun performDetailedCheck(
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>
    ): Int // Возвращает количество подтвержденных аномалий

    /**
     * Подтверждает аномалии для одного сида
     * Возвращает количество подтвержденных аномалий
     */
    fun confirmAnomaliesAndUpdateSeed(
        seed: Seed,
        jvmExecutors: List<JvmExecutor>,
        jvmOptions: List<String>,
        purpose: VerificationPurpose
    ): Int

    /**
     * Возвращает количество сидов, ожидающих проверки
     */
    fun getPendingSeedsCount(): Int
}

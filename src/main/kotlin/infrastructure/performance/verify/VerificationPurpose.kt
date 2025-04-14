package infrastructure.performance.verify

enum class VerificationPurpose {
    /**
     * Проверка для подтверждения потенциальной интересности сида
     * (используется в performDetailedCheck)
     */
    SEED_VERIFICATION,

    /**
     * Проверка для включения аномалии в финальный отчет
     * (используется при немедленной проверке значимых аномалий)
     */
    REPORTING
}

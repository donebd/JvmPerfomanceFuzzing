package infrastructure.performance.anomaly

enum class AnomalyGroupType {
    TIME,       // Аномалия по времени исполнения
    MEMORY,     // Аномалия по использованию памяти
    TIMEOUT,    // Аномалия типа таймаут
    ERROR,      // Аномалия с ошибками выполнения
    JIT         // Аномалия JIT-компиляции
}

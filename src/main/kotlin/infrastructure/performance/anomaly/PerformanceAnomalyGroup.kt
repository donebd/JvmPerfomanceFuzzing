package infrastructure.performance.anomaly

import infrastructure.performance.entity.JvmPerformanceResult

data class PerformanceAnomalyGroup(
    val anomalyType: AnomalyGroupType,
    // Группы JVM с метриками
    val fasterJvms: List<JvmPerformanceResult>,
    val slowerJvms: List<JvmPerformanceResult>,
    // Агрегированные метрики для групп
    val averageDeviation: Double,           // Среднее отклонение между группами
    val maxDeviation: Double,               // Максимальное отклонение между группами
    val minDeviation: Double,               // Минимальное отклонение между группами
    // Детализация отклонений для каждой пары JVM
    val pairwiseDeviations: Map<String, Map<String, Double>>, // Map<slowerJvm, Map<fasterJvm, deviation>>
    // Информация для репортинга
    val description: String,
    val interestingnessScore: Double,
    // Дополнительная информация для специальных случаев
    val exitCodes: Map<String, Int> = emptyMap(),        // Коды выхода для JVM с ошибками
    val errorDetails: Map<String, String> = emptyMap(),   // Детали ошибок
    val jitData: JITAnomalyData? = null
)

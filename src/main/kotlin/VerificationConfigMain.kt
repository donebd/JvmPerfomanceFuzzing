
import infrastructure.performance.anomaly.AnomalyGroupType
import tools.AnomalyConfirmationTool

fun main() {
    val sourceDir = "anomalies"
    val targetDir = "confirmed_anomalies"
    val limit = Int.MAX_VALUE
    val types: List<AnomalyGroupType>? = null

    println("Запуск верификации аномалий:")
    println("- Исходная папка: $sourceDir")
    println("- Папка для подтвержденных: $targetDir")
    println("- Лимит проверок: ${if (limit == Int.MAX_VALUE) "все" else limit}")
    println("- Типы аномалий: ${types?.joinToString() ?: "все"}")
    println()

    AnomalyConfirmationTool(
        potentialAnomaliesDir = sourceDir,
        confirmedAnomaliesDir = targetDir,
        maxSeedsToVerify = limit,
        onlyTypes = types
    ).run()
}

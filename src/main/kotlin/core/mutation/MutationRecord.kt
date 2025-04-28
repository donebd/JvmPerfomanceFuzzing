package core.mutation

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Запись о применённой мутации к исходному коду.
 *
 * @property parentSeedDescription Описание родительского сида
 * @property strategyName Название применённой стратегии мутации
 * @property timestamp Временная метка выполнения мутации
 */
data class MutationRecord(
    val parentSeedDescription: String,
    val strategyName: String,
    val timestamp: String = LocalDateTime.now().format(TIMESTAMP_FORMATTER)
) {
    companion object {
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}

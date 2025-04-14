package core.mutation

import java.text.SimpleDateFormat
import java.util.*

data class MutationRecord(
    val parentSeedDescription: String,     // Описание родительского сида
    val strategyName: String,              // Название примененной стратегии
    val timestamp: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
)

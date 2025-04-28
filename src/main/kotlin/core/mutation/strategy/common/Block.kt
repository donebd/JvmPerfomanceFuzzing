package core.mutation.strategy.common

/**
 * Структура данных для представления блока кода
 */
data class Block(val startUnit: soot.Unit, val endUnit: soot.Unit)

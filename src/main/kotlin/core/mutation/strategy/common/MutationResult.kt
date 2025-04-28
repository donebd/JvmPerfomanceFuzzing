package core.mutation.strategy.common

/**
 * Результат применения мутации к Jimple-коду.
 */
data class MutationResult(val jimpleCode: String, val hadError: Boolean)

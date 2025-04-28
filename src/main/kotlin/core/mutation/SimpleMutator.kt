package core.mutation

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator

class SimpleMutator(
    private val jimpleTranslator: JimpleTranslator,
    private val strategies: List<MutationStrategy>,
): Mutator {

    override fun mutate(bytecode: ByteArray, className: String, packageName: String): ByteArray {
        val jimpleCode = jimpleTranslator.toJimple(bytecode, className)

        var mutatedJimple = jimpleCode.data

        for (strategy in strategies.shuffled()) { // Перебор стратегий в случайном порядке
            try {
                val newMutation = strategy.apply(mutatedJimple, className, packageName)
                if (newMutation.hadError) {
                    println("Неудачная мутация")
                }
                mutatedJimple = newMutation.jimpleCode
            } catch (e: Exception) {
                println("Ошибка стратегии мутации: ${e.message}")
                println(e.stackTraceToString())
            }
        }

        return jimpleTranslator.toBytecode(mutatedJimple, className, packageName)
    }

}

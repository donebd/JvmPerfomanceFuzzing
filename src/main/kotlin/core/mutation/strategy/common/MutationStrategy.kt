package core.mutation.strategy.common

import infrastructure.translator.JimpleTranslator
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import soot.Body
import soot.SootClass
import soot.SootMethod
import java.io.PrintWriter
import java.io.StringWriter

data class MutationResult(val jimpleCode: String, val hadError: Boolean)

abstract class MutationStrategy(private val jimpleTranslator: JimpleTranslator) {

    /**
     * Применяет мутацию к предоставленному Jimple-коду.
     */
    fun apply(jimpleCode: String, className: String, packageName: String): MutationResult {
        try {
            // Шаг 1: Разбор класса
            val sootClass = jimpleTranslator.parseSootClass(jimpleCode, className, packageName)

            // Шаг 2: Выбор метода и клонирование тела
            val method = selectRandomMethod(sootClass) ?: return MutationResult(jimpleCode, true)

            // Шаг 3: Применение мутации
            applyMutation(method.activeBody)

            val bytecode = jimpleTranslator.toBytecode(sootClass)

            // Шаг 4: Проверка результата
            val errorCollector = StringWriter()
            CheckClassAdapter.verify(
                ClassReader(bytecode),
                false,
                PrintWriter(errorCollector)
            )

            // Если есть ошибки верификации - возвращаем оригинал
            if (errorCollector.toString().isNotEmpty()) {
                return MutationResult(jimpleCode, true)
            }

            // Шаг 5: Преобразование обратно в Jimple
            return MutationResult(jimpleTranslator.toJimple(bytecode, sootClass.shortName).data, false)

        } catch (e: Exception) {
            println(e.message)
            println(e.printStackTrace())
            return MutationResult(jimpleCode, true)
        }
    }

    /**
     * Абстрактный метод для применения конкретной мутации
     */
    protected abstract fun applyMutation(body: Body)

    /**
     * Выбирает случайный метод из класса для мутации
     */
    private fun selectRandomMethod(sootClass: SootClass): SootMethod? {
        return sootClass.methods
            .filter { it.hasActiveBody() && !it.name.startsWith("<") }
            .randomOrNull()
    }
}

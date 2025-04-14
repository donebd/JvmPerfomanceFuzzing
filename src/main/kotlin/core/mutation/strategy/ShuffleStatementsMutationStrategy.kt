package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.Value
import soot.jimple.*

/**
 * Стратегия мутации, перемешивающая порядок независимых операторов в методе.
 *
 * Мутатор находит последовательные независимые операторы присваивания,
 * не содержащие вызовов методов, и меняет порядок их следования.
 * Семантика программы при этом сохраняется, но порядок выполнения
 * операций изменяется.
 *
 * Мутатор выбирает блок из 2-4 независимых операторов и случайным
 * образом изменяет их порядок, сохраняя функциональную эквивалентность
 * программы, но потенциально влияя на её производительность.
 */
class ShuffleStatementsMutationStrategy(jimpleTranslator: JimpleTranslator) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        // Находим независимые операторы, которые можно безопасно перемешать
        val independentStatements = findIndependentStatements(body)

        // Нужно минимум 3 независимых оператора для перемешивания
        if (independentStatements.size < 3) return

        // Выбираем случайный блок из 2-4 операторов
        val blockSize = kotlin.random.Random.nextInt(2, minOf(4, independentStatements.size))
        val startIndex = kotlin.random.Random.nextInt(0, independentStatements.size - blockSize)

        // Получаем подмножество операторов для перемешивания
        val statementsToShuffle = independentStatements.subList(startIndex, startIndex + blockSize)

        // Перемешиваем операторы
        val shuffled = statementsToShuffle.toList().shuffled()

        // Временно извлекаем операторы из тела метода
        val units = body.units
        statementsToShuffle.forEach { units.remove(it) }

        // Находим точку вставки - оператор, после которого вставляем перемешанные операторы
        val firstOperatorIndex = independentStatements.indexOf(statementsToShuffle.first())
        val insertPoint = if (firstOperatorIndex > 0) {
            independentStatements[firstOperatorIndex - 1]
        } else {
            findFirstNonIdentityStatement(body)
        }

        // Вставляем перемешанные операторы
        var currentPoint = insertPoint
        for (stmt in shuffled) {
            units.insertAfter(stmt, currentPoint)
            currentPoint = stmt
        }
    }

    /**
     * Находит операторы, которые можно безопасно перемешать без нарушения семантики
     */
    private fun findIndependentStatements(body: Body): List<Stmt> {
        // Получаем все операторы из тела метода
        val allStatements = body.units.filterIsInstance<Stmt>()

        // Фильтруем только простые операторы присваивания, не участвующие в потоке управления
        return allStatements.filter { stmt ->
            // Исключаем операторы, влияющие на поток выполнения
            stmt !is IdentityStmt &&
                    stmt !is IfStmt &&
                    stmt !is GotoStmt &&
                    stmt !is SwitchStmt &&
                    stmt !is ReturnStmt &&
                    stmt !is ReturnVoidStmt &&
                    stmt !is ThrowStmt &&
                    // Проверяем операторы присваивания на отсутствие вызовов методов
                    !(stmt is AssignStmt && containsInvokeExpr(stmt.rightOp))
        }
    }

    /**
     * Проверяет, содержит ли выражение вызов метода
     */
    private fun containsInvokeExpr(value: Value): Boolean {
        // Проверяем, является ли значение вызовом метода
        if (value is InvokeExpr) return true

        // Проверяем, содержится ли вызов метода в операндах
        when (value) {
            is Expr -> {
                // Получаем все использованные значения из выражения
                val usedValues = value.getUseBoxes()
                // Проверяем каждое использованное значение
                for (useBox in usedValues) {
                    if (containsInvokeExpr(useBox.value)) return true
                }
            }
        }

        return false
    }

    /**
     * Находит первый не-identity оператор в теле метода
     */
    private fun findFirstNonIdentityStatement(body: Body): Stmt {
        for (unit in body.units) {
            if (unit is Stmt && unit !is IdentityStmt) {
                return unit
            }
        }
        // Если не найдено, возвращаем первый оператор (что маловероятно)
        return body.units.first() as Stmt
    }
}

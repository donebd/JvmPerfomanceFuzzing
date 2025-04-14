package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.IntType
import soot.jimple.*

/**
 * Стратегия мутации, оборачивающая случайные операторы в условные конструкции
 * с использованием случайно сгенерированных условий, которые делят выполнение
 * программы на два пути с определенной вероятностью.
 */
class ConditionalRandomWrapperMutationStrategy(jimpleTranslator: JimpleTranslator) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        // Случайно выбираем тип мутации
        val mutationType = MutationType.values().random()

        when (mutationType) {
            MutationType.WRAP_SIMPLE_STATEMENT -> wrapSimpleStatement(body)
            MutationType.WRAP_SIMPLE_BLOCK -> wrapSimpleBlock(body)
        }
    }

    /**
     * Типы мутаций
     */
    private enum class MutationType {
        WRAP_SIMPLE_STATEMENT,   // Оборачивание отдельного оператора
        WRAP_SIMPLE_BLOCK        // Оборачивание блока операторов (может включать циклы и условия)
    }

    /**
     * Оборачивает отдельный простой оператор в условие
     */
    private fun wrapSimpleStatement(body: Body) {
        // Находим безопасные операторы для оборачивания
        val safeStatements = findSafeStatementsToWrap(body)
        if (safeStatements.isEmpty()) return

        // Выбираем случайный оператор
        val targetStmt = safeStatements.random()

        // Создаем и вставляем условную конструкцию
        insertConditionalAroundStatement(body, targetStmt)
    }

    /**
     * Оборачивает блок операторов в условие (может включать циклы и ветвления)
     */
    private fun wrapSimpleBlock(body: Body) {
        // Найдем начальные точки простых блоков (включая заголовки циклов и условий)
        val blockHeaders = findBlockHeaders(body)
        if (blockHeaders.isEmpty()) return

        // Выбираем случайный блок
        val blockHeader = blockHeaders.random()

        // Определяем границы блока
        val (blockStart, blockEnd) = determineBlockBoundaries(body, blockHeader)

        // Создаем и вставляем условие вокруг блока
        insertConditionalAroundBlock(body, blockStart, blockEnd)
    }

    /**
     * Находит операторы, которые могут быть началом блока (включая циклы и условия)
     */
    private fun findBlockHeaders(body: Body): List<Stmt> {
        val result = ArrayList<Stmt>()

        // Ищем заголовки циклов и условий
        val units = body.units.toList()
        for (i in units.indices) {
            val unit = units[i]
            if (unit is IfStmt || (unit is GotoStmt && i > 0 && units[i-1] is AssignStmt)) {
                // Потенциальный заголовок цикла или условия
                result.add(unit as Stmt)
            }
        }

        return result
    }

    /**
     * Определяет границы блока (начало и конец)
     */
    private fun determineBlockBoundaries(body: Body, blockHeader: Stmt): Pair<Stmt, Stmt> {
        // Простая эвристика - берем текущий оператор и следующие 3-5 операторов
        val blockSize = kotlin.random.Random.nextInt(3, 6)
        var blockEnd = blockHeader

        val units = body.units.toList()
        val headerIndex = units.indexOf(blockHeader)

        // Находим конец блока
        for (i in 1 until blockSize) {
            val nextIndex = headerIndex + i
            if (nextIndex < units.size && units[nextIndex] is Stmt) {
                blockEnd = units[nextIndex] as Stmt
            } else {
                break
            }
        }

        return Pair(blockHeader, blockEnd)
    }

    /**
     * Вставляет условную конструкцию вокруг отдельного оператора
     */
    private fun insertConditionalAroundStatement(body: Body, targetStmt: Stmt) {
        // Создаём уникальную переменную condition
        val conditionVar = Jimple.v().newLocal("conditionVar_${System.nanoTime()}", IntType.v())
        body.locals.add(conditionVar)

        // Генерируем случайное значение
        val randomValue = kotlin.random.Random.nextInt(30, 70)
        val threshold = 50

        // Создаём метки для условного оператора
        val thenLabel = Jimple.v().newNopStmt()
        val elseLabel = Jimple.v().newNopStmt()
        val endIfLabel = Jimple.v().newNopStmt()

        // Создаём операторы
        val assignCondition = Jimple.v().newAssignStmt(conditionVar, IntConstant.v(randomValue))
        val conditionExpr = Jimple.v().newLeExpr(conditionVar, IntConstant.v(threshold))
        val ifStmt = Jimple.v().newIfStmt(conditionExpr, thenLabel)
        val gotoElse = Jimple.v().newGotoStmt(elseLabel)
        val gotoEnd = Jimple.v().newGotoStmt(endIfLabel)

        // Клонируем целевой оператор
        val clonedStmt = targetStmt.clone() as Stmt

        // Вставляем инструкции
        val units = body.units
        units.insertBefore(assignCondition, targetStmt)
        units.insertAfter(ifStmt, assignCondition)
        units.insertAfter(gotoElse, ifStmt)
        units.insertAfter(thenLabel, gotoElse)
        units.insertAfter(gotoEnd, targetStmt)
        units.insertAfter(elseLabel, gotoEnd)
        units.insertAfter(clonedStmt, elseLabel)
        units.insertAfter(endIfLabel, clonedStmt)
    }

    /**
     * Вставляет условную конструкцию вокруг блока операторов
     */
    private fun insertConditionalAroundBlock(body: Body, blockStart: Stmt, blockEnd: Stmt) {
        // Создаём уникальную переменную condition
        val conditionVar = Jimple.v().newLocal("conditionVar_${System.nanoTime()}", IntType.v())
        body.locals.add(conditionVar)

        // Генерируем случайное значение - всегда выбираем then-ветку
        val randomValue = 40  // Меньше порога
        val threshold = 50

        // Создаём метки условия
        val elseLabel = Jimple.v().newNopStmt()
        val endIfLabel = Jimple.v().newNopStmt()

        // Создаём операторы
        val assignCondition = Jimple.v().newAssignStmt(conditionVar, IntConstant.v(randomValue))
        val conditionExpr = Jimple.v().newGtExpr(conditionVar, IntConstant.v(threshold))
        val ifStmt = Jimple.v().newIfStmt(conditionExpr, elseLabel)
        val gotoEnd = Jimple.v().newGotoStmt(endIfLabel)

        // Вставляем условие перед блоком
        val units = body.units
        units.insertBefore(assignCondition, blockStart)
        units.insertAfter(ifStmt, assignCondition)

        // Вставляем переход после блока
        units.insertAfter(gotoEnd, blockEnd)
        units.insertAfter(elseLabel, gotoEnd)

        // Создаем пустой оператор для else-ветки
        val elseNop = Jimple.v().newNopStmt()
        units.insertAfter(elseNop, elseLabel)
        units.insertAfter(endIfLabel, elseNop)
    }

    /**
     * Находит безопасные операторы для оборачивания условием
     */
    private fun findSafeStatementsToWrap(body: Body): List<Stmt> {
        return body.units.filterIsInstance<Stmt>().filter { stmt ->
            // Исключаем критические операторы
            stmt !is IdentityStmt &&
                    stmt !is ReturnStmt &&
                    stmt !is ReturnVoidStmt &&
                    stmt !is ThrowStmt &&
                    stmt !is EnterMonitorStmt &&
                    stmt !is ExitMonitorStmt &&
                    // Позволяем оборачивать некоторые управляющие операторы
                    (stmt is AssignStmt || stmt is InvokeStmt ||
                            stmt is IfStmt || stmt is GotoStmt)
        }
    }
}

package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.Value
import soot.jimple.*
import java.util.*

/**
 * Стратегия мутации, инвертирующая условия ветвлений и
 * соответствующим образом корректирующая потоки управления
 */
class InvertBranchConditionMutator(jimpleTranslator: JimpleTranslator) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val units = body.units
        val ifStmts = units.filterIsInstance<IfStmt>().toList()

        if (ifStmts.isEmpty()) return

        // Выбираем случайный оператор if
        val targetIf = ifStmts[Random().nextInt(ifStmts.size)]

        // Получаем текущее условие
        val condition = targetIf.condition

        // Инвертируем условие
        val invertedCondition = getInvertedCondition(condition)

        // Создаем новую инструкцию if с инвертированным условием
        val targetBlock = targetIf.target
        val fallThroughBlock = units.getSuccOf(targetIf)

        // Заменяем оператор на новый с инвертированным условием
        // и поменянными местами целевыми блоками
        val newIfStmt = Jimple.v().newIfStmt(invertedCondition, fallThroughBlock)
        units.insertAfter(newIfStmt, targetIf)

        // Добавляем goto для перехода к оригинальной цели
        val gotoStmt = Jimple.v().newGotoStmt(targetBlock)
        units.insertAfter(gotoStmt, newIfStmt)

        // Удаляем оригинальный оператор if
        units.remove(targetIf)
    }

    private fun getInvertedCondition(condition: Value): Value {
        return when (condition) {
            is EqExpr -> Jimple.v().newNeExpr(condition.op1, condition.op2)
            is NeExpr -> Jimple.v().newEqExpr(condition.op1, condition.op2)
            is GtExpr -> Jimple.v().newLeExpr(condition.op1, condition.op2)
            is GeExpr -> Jimple.v().newLtExpr(condition.op1, condition.op2)
            is LtExpr -> Jimple.v().newGeExpr(condition.op1, condition.op2)
            is LeExpr -> Jimple.v().newGtExpr(condition.op1, condition.op2)
            else -> Jimple.v().newNeExpr(condition, IntConstant.v(0))
        }
    }
}
package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.Value
import soot.jimple.*

/**
 * Стратегия мутации, которая инвертирует условия в операторах ветвления if.
 * Заменяет условные выражения на противоположные и перенаправляет ветви выполнения
 * так, чтобы сохранить функциональную эквивалентность кода.
 *
 * Например, трансформирует условие "a > b" в "a <= b" с соответствующим изменением
 * логики ветвления. Такие инверсии могут выявлять различия в оптимизациях выполнения
 * условных переходов между разными реализациями JVM, особенно в случаях, когда
 * предсказание ветвлений влияет на производительность.
 */
class InvertBranchConditionMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val ifStmts = body.units.filterIsInstance<IfStmt>()
        val targetIf = ifStmts.randomOrNull() ?: return

        val inverted = invertCondition(targetIf.condition)
        val originalTarget = targetIf.target
        val fallThrough = body.units.getSuccOf(targetIf)

        val newIf = Jimple.v().newIfStmt(inverted, fallThrough)
        val gotoOriginal = Jimple.v().newGotoStmt(originalTarget)

        val units = body.units
        units.insertAfter(newIf, targetIf)
        units.insertAfter(gotoOriginal, newIf)
        units.remove(targetIf)
    }

    private fun invertCondition(condition: Value): Value = when (condition) {
        is EqExpr -> Jimple.v().newNeExpr(condition.op1, condition.op2)
        is NeExpr -> Jimple.v().newEqExpr(condition.op1, condition.op2)
        is GtExpr -> Jimple.v().newLeExpr(condition.op1, condition.op2)
        is GeExpr -> Jimple.v().newLtExpr(condition.op1, condition.op2)
        is LtExpr -> Jimple.v().newGeExpr(condition.op1, condition.op2)
        is LeExpr -> Jimple.v().newGtExpr(condition.op1, condition.op2)
        else -> Jimple.v().newNeExpr(condition, IntConstant.v(0))
    }
}

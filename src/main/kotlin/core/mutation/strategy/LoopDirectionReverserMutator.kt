package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.Local
import soot.PatchingChain
import soot.Value
import soot.jimple.*

class LoopDirectionReverserMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val loops = findSimpleLoops(body)
        val target = loops.randomOrNull() ?: return
        reverseLoop(body, target)
    }

    data class SimpleLoop(
        val initStmt: AssignStmt,
        val conditionStmt: IfStmt,
        val incrementStmt: AssignStmt,
        val loopVar: Local,
        val bound: Value
    )

    private fun findSimpleLoops(body: Body): List<SimpleLoop> {
        val units = body.units.toList()
        val loops = mutableListOf<SimpleLoop>()

        for (i in 0 until units.size - 2) {
            val init = units[i]
            val cond = units[i + 1]
            val incr = units[i + 2]

            if (init !is AssignStmt || cond !is IfStmt || incr !is AssignStmt) continue
            val varInit = init.leftOp as? Local ?: continue
            if (init.rightOp !is IntConstant) continue

            val condExpr = cond.condition as? BinopExpr ?: continue

            // Условие должно быть i < bound или i <= bound
            val isForward = when (condExpr) {
                is LtExpr, is LeExpr -> {
                    condExpr.op1 == varInit || condExpr.op2 == varInit
                }

                else -> false
            }
            if (!isForward) continue

            val rhs = incr.rightOp as? AddExpr ?: continue
            val isIncrement = rhs.op1 == varInit &&
                    (rhs.op2 is IntConstant && (rhs.op2 as IntConstant).value == 1)

            if (!isIncrement || incr.leftOp != varInit) continue

            val bound = when (condExpr) {
                is LtExpr, is LeExpr -> {
                    if (condExpr.op1 == varInit) condExpr.op2 else condExpr.op1
                }

                else -> continue
            }

            loops += SimpleLoop(init, cond, incr, varInit, bound)
        }

        return loops
    }

    private fun reverseLoop(body: Body, loop: SimpleLoop) {
        val units = body.units

        // Изменяем init: i = bound - 1
        val newInit = Jimple.v().newAssignStmt(
            loop.loopVar,
            Jimple.v().newSubExpr(loop.bound, IntConstant.v(1))
        )
        units.swap(loop.initStmt, newInit)

        // Изменяем условие: i >= 0
        val newCond = Jimple.v().newIfStmt(
            Jimple.v().newGeExpr(loop.loopVar, IntConstant.v(0)),
            loop.conditionStmt.target // same target
        )
        units.swap(loop.conditionStmt, newCond)

        // Изменяем инкремент на декремент: i = i - 1
        val newIncr = Jimple.v().newAssignStmt(
            loop.loopVar,
            Jimple.v().newSubExpr(loop.loopVar, IntConstant.v(1))
        )
        units.swap(loop.incrementStmt, newIncr)
    }

    // Утилита для замены инструкции
    private fun PatchingChain<soot.Unit>.swap(oldUnit: soot.Unit, newUnit: soot.Unit) {
        insertBefore(newUnit, oldUnit)
        remove(oldUnit)
    }
}

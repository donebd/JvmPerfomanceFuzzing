package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.IntType
import soot.Value
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import kotlin.random.Random

class RedundantComputationMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val candidates = findSafeInsertionPoints(body)
        val target = candidates.randomOrNull() ?: return

        val localGen = DefaultLocalGenerator(body)
        val lhs = localGen.generateLocal(IntType.v())
        body.locals.add(lhs)

        val expr = generateRandomArithmeticExpr()
        val assign = Jimple.v().newAssignStmt(lhs, expr)

        val units = body.units
        units.insertBefore(assign, target)
    }

    private fun findSafeInsertionPoints(body: Body): List<soot.Unit> =
        body.units.filter {
            it is AssignStmt &&
                    it !is IdentityStmt &&
                    it !is IfStmt &&
                    it !is GotoStmt &&
                    it !is ReturnStmt &&
                    it !is SwitchStmt &&
                    !containsInvokeExpr(it.rightOp)
        }

    private fun containsInvokeExpr(value: Value): Boolean {
        if (value is InvokeExpr) return true
        if (value is Expr) {
            return value.useBoxes.any { containsInvokeExpr(it.value) }
        }
        return false
    }

    private fun generateRandomArithmeticExpr(): BinopExpr {
        val v1 = IntConstant.v(Random.nextInt(1, 100))
        val v2 = IntConstant.v(Random.nextInt(1, 100))
        return when (Random.nextInt(4)) {
            0 -> Jimple.v().newAddExpr(v1, v2)
            1 -> Jimple.v().newSubExpr(v1, v2)
            2 -> Jimple.v().newMulExpr(v1, v2)
            else -> {
                val divisor = if (v2.value == 0) IntConstant.v(1) else v2
                Jimple.v().newDivExpr(v1, divisor)
            }
        }
    }
}

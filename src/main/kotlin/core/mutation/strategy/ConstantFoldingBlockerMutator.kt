package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.IntType
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import kotlin.random.Random

class ConstantFoldingBlockerMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val candidates = findSafeInsertionPoints(body)
        val targetStmt = candidates.randomOrNull() ?: return

        val localGen = DefaultLocalGenerator(body)
        val lhs = localGen.generateLocal(IntType.v())
        body.locals.add(lhs)

        val expr = createBlockedConstantExpr(body, localGen) ?: return
        val assign = Jimple.v().newAssignStmt(lhs, expr)

        body.units.insertBefore(assign, targetStmt)
    }

    private fun findSafeInsertionPoints(body: Body): List<soot.Unit> =
        body.units.filter {
            it is Stmt &&
                    it !is IdentityStmt &&
                    it !is ReturnStmt &&
                    it !is ReturnVoidStmt &&
                    it !is GotoStmt &&
                    it !is IfStmt &&
                    it !is SwitchStmt &&
                    it !is ThrowStmt
        }

    private fun createBlockedConstantExpr(body: Body, localGen: DefaultLocalGenerator): BinopExpr? {
        // Попробуем использовать реальные переменные, если есть
        val locals = body.locals.filter { it.type == IntType.v() }

        val left = locals.randomOrNull() ?: localGen.generateLocal(IntType.v()).also {
            body.locals.add(it)
            val init = Jimple.v().newAssignStmt(it, IntConstant.v(Random.nextInt(1, 20)))
            body.units.insertBefore(init, body.units.first())
        }

        val right = localGen.generateLocal(IntType.v())
        body.locals.add(right)

        // Пример имитации "геттера", чтобы помешать constant folding
        val initRight = Jimple.v().newAssignStmt(right, Jimple.v().newAddExpr(IntConstant.v(2), IntConstant.v(3)))
        body.units.insertBefore(initRight, body.units.first())

        return when (Random.nextInt(4)) {
            0 -> Jimple.v().newAddExpr(left, right)
            1 -> Jimple.v().newSubExpr(left, right)
            2 -> Jimple.v().newMulExpr(left, right)
            else -> Jimple.v().newDivExpr(left, right)
        }
    }
}

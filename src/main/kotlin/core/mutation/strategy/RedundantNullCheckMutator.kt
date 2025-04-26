package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.RefType
import soot.jimple.*
import kotlin.random.Random

class RedundantNullCheckMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val insertPoints = findInsertPoints(body)
        val targetStmt = insertPoints.randomOrNull() ?: return

        val refLocals = body.locals.filter { it.type is RefType }
        val local = refLocals.randomOrNull() ?: return

        val condition = if (Random.nextBoolean()) {
            Jimple.v().newEqExpr(local, NullConstant.v())
        } else {
            Jimple.v().newNeExpr(local, NullConstant.v())
        }

        val nop = Jimple.v().newNopStmt()
        val ifStmt = Jimple.v().newIfStmt(condition, nop)

        val units = body.units
        units.insertBefore(ifStmt, targetStmt)
        units.insertBefore(nop, targetStmt)
    }

    private fun findInsertPoints(body: Body): List<soot.Unit> =
        body.units.filter {
            it !is IdentityStmt &&
                    it !is ReturnStmt &&
                    it !is ReturnVoidStmt &&
                    it !is ThrowStmt
        }
}

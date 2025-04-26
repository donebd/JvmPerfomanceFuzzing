package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.IntType
import soot.Local
import soot.jimple.*
import kotlin.random.Random

class ConditionalRandomWrapperMutationStrategy(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    private enum class MutationType {
        WRAP_SIMPLE_STATEMENT,
        WRAP_SIMPLE_BLOCK
    }

    override fun applyMutation(body: Body) {
        when (MutationType.entries.toTypedArray().random()) {
            MutationType.WRAP_SIMPLE_STATEMENT -> wrapStatement(body)
            MutationType.WRAP_SIMPLE_BLOCK -> wrapBlock(body)
        }
    }

    private fun wrapStatement(body: Body) {
        val stmt = findWrappableStatements(body).randomOrNull() ?: return
        insertConditionalAroundStatement(body, stmt)
    }

    private fun wrapBlock(body: Body) {
        val header = findBlockHeaders(body).randomOrNull() ?: return
        val (start, end) = determineBlockBoundaries(body, header)
        insertConditionalAroundBlock(body, start, end)
    }

    private fun findWrappableStatements(body: Body): List<Stmt> =
        body.units.filterIsInstance<Stmt>().filter {
            it !is IdentityStmt &&
                    it !is ReturnStmt &&
                    it !is ReturnVoidStmt &&
                    it !is ThrowStmt &&
                    it !is EnterMonitorStmt &&
                    it !is ExitMonitorStmt &&
                    (it is AssignStmt || it is InvokeStmt || it is IfStmt || it is GotoStmt)
        }

    private fun findBlockHeaders(body: Body): List<Stmt> {
        val units = body.units.toList()
        return units.indices.mapNotNull { i ->
            val unit = units[i]
            when {
                unit is IfStmt -> unit
                unit is GotoStmt && i > 0 && units[i - 1] is AssignStmt -> unit
                else -> null
            }
        }
    }

    private fun determineBlockBoundaries(body: Body, start: Stmt): Pair<Stmt, Stmt> {
        val units = body.units.toList()
        val startIndex = units.indexOf(start)
        val size = Random.nextInt(3, 6)

        val end = (1 until size)
            .mapNotNull { i -> units.getOrNull(startIndex + i) as? Stmt }
            .lastOrNull() ?: start

        return start to end
    }

    private fun insertConditionalAroundStatement(body: Body, stmt: Stmt) {
        val units = body.units

        val conditionVar = createConditionLocal(body)
        val randomValue = Random.nextInt(30, 70)
        val threshold = 50

        val thenLabel = Jimple.v().newNopStmt()
        val elseLabel = Jimple.v().newNopStmt()
        val endIfLabel = Jimple.v().newNopStmt()

        val assign = Jimple.v().newAssignStmt(conditionVar, IntConstant.v(randomValue))
        val ifStmt = Jimple.v().newIfStmt(Jimple.v().newLeExpr(conditionVar, IntConstant.v(threshold)), thenLabel)
        val gotoElse = Jimple.v().newGotoStmt(elseLabel)
        val gotoEnd = Jimple.v().newGotoStmt(endIfLabel)

        val cloned = stmt.clone() as Stmt

        units.insertBefore(assign, stmt)
        units.insertAfter(ifStmt, assign)
        units.insertAfter(gotoElse, ifStmt)
        units.insertAfter(thenLabel, gotoElse)
        units.insertAfter(gotoEnd, stmt)
        units.insertAfter(elseLabel, gotoEnd)
        units.insertAfter(cloned, elseLabel)
        units.insertAfter(endIfLabel, cloned)
    }

    private fun insertConditionalAroundBlock(body: Body, start: Stmt, end: Stmt) {
        val units = body.units

        val conditionVar = createConditionLocal(body)
        val randomValue = 40  // always true
        val threshold = 50

        val elseLabel = Jimple.v().newNopStmt()
        val endIfLabel = Jimple.v().newNopStmt()
        val elseNop = Jimple.v().newNopStmt()

        val assign = Jimple.v().newAssignStmt(conditionVar, IntConstant.v(randomValue))
        val ifStmt = Jimple.v().newIfStmt(Jimple.v().newGtExpr(conditionVar, IntConstant.v(threshold)), elseLabel)
        val gotoEnd = Jimple.v().newGotoStmt(endIfLabel)

        units.insertBefore(assign, start)
        units.insertAfter(ifStmt, assign)

        units.insertAfter(gotoEnd, end)
        units.insertAfter(elseLabel, gotoEnd)
        units.insertAfter(elseNop, elseLabel)
        units.insertAfter(endIfLabel, elseNop)
    }

    private fun createConditionLocal(body: Body): Local {
        val name = "cond_${System.nanoTime()}"
        return Jimple.v().newLocal(name, IntType.v()).also {
            body.locals.add(it)
        }
    }
}

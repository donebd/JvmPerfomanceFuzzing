package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.Value
import soot.jimple.*
import kotlin.random.Random

/**
 * Стратегия мутации, которая переставляет порядок независимых инструкций в байткоде.
 * Выбирает блок последовательных независимых инструкций и случайным образом
 * перемешивает их порядок
 */
class ShuffleStatementsMutationStrategy(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val candidates = findIndependentStatements(body)
        if (candidates.size < 3) return

        val blockSize = Random.nextInt(2, minOf(4, candidates.size + 1))
        val startIndex = Random.nextInt(0, candidates.size - blockSize + 1)
        val block = candidates.subList(startIndex, startIndex + blockSize)

        val shuffled = block.shuffled()
        val units = body.units

        block.forEach { units.remove(it) }

        val insertAfter = candidates
            .take(startIndex)
            .lastOrNull()
            ?: findFirstNonIdentity(body)

        var insertPoint = insertAfter
        for (stmt in shuffled) {
            units.insertAfter(stmt, insertPoint)
            insertPoint = stmt
        }
    }

    private fun findIndependentStatements(body: Body): List<Stmt> =
        body.units.filterIsInstance<Stmt>().filter { stmt ->
            stmt !is IdentityStmt &&
                    stmt !is IfStmt &&
                    stmt !is GotoStmt &&
                    stmt !is SwitchStmt &&
                    stmt !is ReturnStmt &&
                    stmt !is ReturnVoidStmt &&
                    stmt !is ThrowStmt &&
                    (stmt !is AssignStmt || !containsInvokeExpr(stmt.rightOp))
        }

    private fun containsInvokeExpr(value: Value): Boolean {
        if (value is InvokeExpr) return true
        if (value is Expr) {
            return value.useBoxes.any { containsInvokeExpr(it.value) }
        }
        return false
    }

    private fun findFirstNonIdentity(body: Body): Stmt =
        body.units.firstOrNull { it is Stmt && it !is IdentityStmt } as? Stmt
            ?: body.units.first() as Stmt
}

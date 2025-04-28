package core.mutation.strategy

import core.mutation.strategy.common.Block
import core.mutation.strategy.common.BlockFinder
import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.IntType
import soot.Local
import soot.Unit
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import kotlin.random.Random

/**
 * Стратегия мутации, которая оборачивает существующие блоки кода или отдельные
 * операторы в циклы. Поддерживает как стандартные циклы с фиксированным числом
 * итераций (for), так и "бесконечные" циклы с условием выхода.
 *
 * Эта мутация позволяет тестировать различия в эффективности обработки циклов
 * разными реализациями JVM, включая стратегии JIT-компиляции, разворачивание циклов,
 * оптимизации условий выхода и другие специфические для циклов оптимизации.
 * Обертывание кода в циклы с небольшим числом итераций может выявлять различия
 * в накладных расходах на организацию циклов и предсказание ветвлений.
 */
class LoopWrapperMutationStrategy(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val units = body.units
        if (units.isEmpty()) return

        val blocks = BlockFinder(body).findBlocks()
        val useInfiniteLoop = Random.nextBoolean()

        if (blocks.isNotEmpty() && Random.nextBoolean()) {
            val block = blocks.random()
            if (useInfiniteLoop) wrapBlockInInfiniteLoop(body, block)
            else wrapBlockInForLoop(body, block)
        } else {
            val statements = findValidStatements(body)
            val target = statements.randomOrNull() ?: return
            if (useInfiniteLoop) wrapStatementInInfiniteLoop(body, target)
            else wrapStatementInForLoop(body, target)
        }
    }

    private fun findValidStatements(body: Body): List<Unit> =
        body.units.filterNot {
            it is ReturnStmt || it is ReturnVoidStmt || it is GotoStmt || it is IfStmt || it is IdentityStmt
        }

    private fun wrapStatementInForLoop(body: Body, target: Unit) {
        val loopVar = generateLoopVar(body)
        val bound = generateLoopBound()
        val loop = createForLoop(loopVar, bound)
        val units = body.units

        with(loop) {
            units.insertBefore(init, target)
            units.insertAfter(gotoCheck, init)
            units.insertAfter(check, gotoCheck)
            units.insertAfter(cond, check)
            units.insertAfter(gotoEnd, cond)
            units.insertAfter(bodyNop, gotoEnd)

            val cloned = target.clone() as Unit
            units.insertAfter(cloned, bodyNop)
            units.insertAfter(incr, cloned)
            units.insertAfter(gotoCheckAfter, incr)
            units.insertAfter(end, gotoCheckAfter)

            units.remove(target)
        }
    }

    private fun wrapStatementInInfiniteLoop(body: Body, target: Unit) {
        val loopVar = generateLoopVar(body)
        val bound = generateLoopBound()
        val loop = createInfiniteLoop(loopVar, bound)
        val units = body.units

        with(loop) {
            units.insertBefore(init, target)
            units.insertBefore(cond, target)
            units.insertAfter(target.clone() as Unit, cond)
            units.insertAfter(incr, target)
            units.insertAfter(back, incr)
            units.insertAfter(end, back)

            units.remove(target)
        }
    }

    private fun wrapBlockInForLoop(body: Body, block: Block) {
        val loopVar = generateLoopVar(body)
        val bound = generateLoopBound()
        val loop = createForLoop(loopVar, bound)
        val units = body.units

        with(loop) {
            units.insertBefore(init, block.startUnit)
            units.insertAfter(gotoCheck, init)
            units.insertAfter(check, gotoCheck)
            units.insertAfter(cond, check)
            units.insertAfter(gotoEnd, cond)
            units.insertAfter(bodyNop, gotoEnd)

            units.insertAfter(endBody, block.endUnit)
            units.insertAfter(incr, endBody)
            units.insertAfter(gotoCheckAfter, incr)
            units.insertAfter(end, gotoCheckAfter)
        }
    }

    private fun wrapBlockInInfiniteLoop(body: Body, block: Block) {
        val loopVar = generateLoopVar(body)
        val bound = generateLoopBound()
        val loop = createInfiniteLoop(loopVar, bound)
        val units = body.units

        val before = Jimple.v().newNopStmt()
        val after = Jimple.v().newNopStmt()

        units.insertBefore(before, block.startUnit)
        units.insertAfter(after, block.endUnit)

        with(loop) {
            units.insertBefore(init, before)
            units.insertAfter(start, init)
            units.insertAfter(cond, start)

            units.insertBefore(incr, after)
            units.insertBefore(back, after)
        }
    }

    private fun generateLoopVar(body: Body): Local {
        val local = DefaultLocalGenerator(body).generateLocal(IntType.v())
        body.locals.add(local)
        return local
    }

    private fun generateLoopBound(): IntConstant = IntConstant.v((1..5).random())

    // --- Loop Component Generators ---

    private data class ForLoop(
        val init: Unit,
        val check: Unit,
        val cond: Unit,
        val bodyNop: Unit,
        val incr: Unit,
        val gotoCheck: Unit,
        val gotoEnd: Unit,
        val gotoCheckAfter: Unit,
        val end: Unit,
        val endBody: Unit = Jimple.v().newNopStmt() // optional for block loop
    )

    private fun createForLoop(loopVar: Local, bound: IntConstant): ForLoop {
        val init = Jimple.v().newAssignStmt(loopVar, IntConstant.v(0))
        val check = Jimple.v().newNopStmt()
        val cond = Jimple.v().newIfStmt(Jimple.v().newLtExpr(loopVar, bound), Jimple.v().newNopStmt())
        val gotoEnd = Jimple.v().newGotoStmt(Jimple.v().newNopStmt())
        val bodyNop = cond.target
        val end = gotoEnd.target
        val incr = Jimple.v().newAssignStmt(loopVar, Jimple.v().newAddExpr(loopVar, IntConstant.v(1)))
        val gotoCheck = Jimple.v().newGotoStmt(check)
        val gotoCheckAfter = Jimple.v().newGotoStmt(check)

        return ForLoop(init, check, cond, bodyNop, incr, gotoCheck, gotoEnd, gotoCheckAfter, end)
    }

    private data class InfiniteLoop(
        val init: Unit,
        val start: Unit,
        val cond: Unit,
        val incr: Unit,
        val back: Unit,
        val end: Unit
    )

    private fun createInfiniteLoop(loopVar: Local, bound: IntConstant): InfiniteLoop {
        val init = Jimple.v().newAssignStmt(loopVar, IntConstant.v(0))
        val start = Jimple.v().newNopStmt()
        val end = Jimple.v().newNopStmt()
        val cond = Jimple.v().newIfStmt(Jimple.v().newGeExpr(loopVar, bound), end)
        val incr = Jimple.v().newAssignStmt(loopVar, Jimple.v().newAddExpr(loopVar, IntConstant.v(1)))
        val back = Jimple.v().newGotoStmt(start)

        return InfiniteLoop(init, start, cond, incr, back, end)
    }
}

package core.mutation.strategy

import core.mutation.strategy.common.Block
import core.mutation.strategy.common.BlockFinder
import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.Local
import soot.RefType
import soot.Scene
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import kotlin.random.Random

/**
 * Стратегия мутации, добавляющая конструкции обработки исключений (try-catch)
 * в Java-байткод. Оборачивает существующие блоки кода или отдельные операторы в
 * блоки try-catch, которые перехватывают RuntimeException.
 *
 * Эта мутация позволяет тестировать различия в производительности механизмов
 * обработки исключений между реализациями JVM. Несмотря на то, что добавленные
 * обработчики исключений никогда не выполняют полезную работу (исключения не
 * выбрасываются), они могут существенно влиять на оптимизации JIT-компилятора
 * и общую производительность.
 */
class ExceptionHandlingMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val validStmts = body.units.filterNot { it is IdentityStmt }.toList()
        if (validStmts.size < 3) return

        val blockFinder = BlockFinder(body)
        val blocks = blockFinder.findBlocks()

        if (blocks.isNotEmpty() && Random.nextBoolean()) {
            wrapBlockInTryCatch(body, blocks.random())
        } else {
            val candidates = findValidStatements(body)
            val target = candidates.randomOrNull() ?: return
            wrapStatementInTryCatch(body, target)
        }
    }

    private fun findValidStatements(body: Body): List<soot.Unit> =
        body.units.filterNot {
            it is ReturnStmt || it is ReturnVoidStmt ||
                    it is GotoStmt || it is IfStmt || it is IdentityStmt
        }

    private fun wrapStatementInTryCatch(body: Body, stmt: soot.Unit) {
        val units = body.units
        val (beforeTry, afterTry, catchStart, catchEnd) = createTryCatchNops()
        val exceptionLocal = createExceptionLocal(body)

        val clonedStmt = stmt.clone() as soot.Unit
        val catchStmt = Jimple.v().newIdentityStmt(exceptionLocal, Jimple.v().newCaughtExceptionRef())
        val gotoEnd = Jimple.v().newGotoStmt(catchEnd)

        units.insertBefore(beforeTry, stmt)
        units.insertAfter(clonedStmt, beforeTry)
        units.insertAfter(afterTry, clonedStmt)
        units.insertAfter(gotoEnd, afterTry)
        units.insertAfter(catchStart, gotoEnd)
        units.insertAfter(catchStmt, catchStart)
        units.insertAfter(catchEnd, catchStmt)

        units.remove(stmt)

        body.traps.add(
            Jimple.v().newTrap(
                Scene.v().getSootClass("java.lang.RuntimeException"),
                beforeTry,
                afterTry,
                catchStart
            )
        )
    }

    private fun wrapBlockInTryCatch(body: Body, block: Block) {
        val units = body.units
        val (beforeTry, afterTry, catchStart, catchEnd) = createTryCatchNops()
        val exceptionLocal = createExceptionLocal(body)

        val gotoEnd = Jimple.v().newGotoStmt(catchEnd)
        val catchStmt = Jimple.v().newIdentityStmt(exceptionLocal, Jimple.v().newCaughtExceptionRef())

        units.insertBefore(beforeTry, block.startUnit)
        units.insertAfter(afterTry, block.endUnit)
        units.insertAfter(gotoEnd, afterTry)
        units.insertAfter(catchStart, gotoEnd)
        units.insertAfter(catchStmt, catchStart)
        units.insertAfter(catchEnd, catchStmt)

        body.traps.add(
            Jimple.v().newTrap(
                Scene.v().getSootClass("java.lang.RuntimeException"),
                beforeTry,
                afterTry,
                catchStart
            )
        )
    }

    private fun createExceptionLocal(body: Body): Local {
        val local = DefaultLocalGenerator(body).generateLocal(RefType.v("java.lang.RuntimeException"))
        body.locals.add(local)
        return local
    }

    private fun createTryCatchNops(): List<soot.Unit> =
        List(4) { Jimple.v().newNopStmt() } // beforeTry, afterTry, catchStart, catchEnd
}

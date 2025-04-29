package core.mutation.strategy

import core.mutation.strategy.common.Block
import core.mutation.strategy.common.BlockFinder
import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.IntType
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import java.util.*

/**
 * Стратегия мутации, которая оборачивает существующие блоки кода или отдельные
 * операторы в циклы. Поддерживает как стандартные циклы с фиксированным числом
 * итераций (for), так и "бесконечные" циклы с условием выхода.
 *
 * Эта мутация позволяет тестировать различия в эффективности обработки циклов
 * разными реализациями JVM, включая стратегии JIT-компиляции, разворачивание циклов,
 * оптимизации условий выхода и другие специфические для циклов оптимизации.
 *
 * Генерируются циклы с различным числом итераций (от 1 до 1000):
 * - Циклы с малым числом итераций выявляют различия в накладных расходах на организацию
 *   циклов и предсказание ветвлений
 * - Циклы с большим числом итераций (>100) активируют JIT-компиляцию и позволяют
 *   тестировать различия в оптимизациях горячего кода, включая векторизацию и
 *   развертывание циклов
 */
class LoopWrapperMutationStrategy(jimpleTranslator: JimpleTranslator) : MutationStrategy(jimpleTranslator) {

    /**
     * Применяет случайную мутацию к телу метода
     */
    override fun applyMutation(body: Body) {
        val units = body.units
        if (units.isEmpty()) return

        val blockFinder = BlockFinder(body)
        val blocks = blockFinder.findBlocks()

        val useInfiniteLoop = Random().nextBoolean()

        if (blocks.isNotEmpty() && Random().nextBoolean()) {
            val targetBlock = blocks.random()

            if (useInfiniteLoop) {
                wrapBlockInInfiniteLoop(body, targetBlock)
            } else {
                wrapBlockInCleanForLoop(body, targetBlock)
            }
        } else {
            val validUnits = findValidStatements(body)
            if (validUnits.isEmpty()) return

            val targetUnit = validUnits.random()

            if (useInfiniteLoop) {
                wrapStatementInInfiniteLoop(body, targetUnit)
            } else {
                wrapStatementInCleanForLoop(body, targetUnit)
            }
        }
    }

    /**
     * Находит операторы, подходящие для мутации
     */
    private fun findValidStatements(body: Body): List<soot.Unit> {
        return body.units.toList().filter { unit ->
            !(unit is ReturnStmt || unit is ReturnVoidStmt ||
                    unit is GotoStmt || unit is IfStmt || unit is IdentityStmt)
        }
    }

    /**
     * Оборачивает отдельный оператор в чистый for-цикл
     */
    private fun wrapStatementInCleanForLoop(body: Body, targetUnit: soot.Unit) {
        val units = body.units
        val localGen = DefaultLocalGenerator(body)
        val loopVar = localGen.generateLocal(IntType.v())
        val loopBound = generateRandomLoopBound()

        val forLoopComponents = createCleanForLoopComponents(loopVar, loopBound)

        with(forLoopComponents) {
            units.insertBefore(initStmt, targetUnit)
            units.insertAfter(gotoCondition, initStmt)
            units.insertAfter(conditionCheck, gotoCondition)
            units.insertAfter(conditionStmt, conditionCheck)
            units.insertAfter(gotoEnd, conditionStmt)
            units.insertAfter(loopBody, gotoEnd)

            try {
                val clonedUnit = targetUnit.clone() as soot.Unit
                units.insertAfter(clonedUnit, loopBody)
                units.insertAfter(increment, clonedUnit)
            } catch (e: Exception) {
                units.insertAfter(increment, loopBody)
            }

            units.insertAfter(Jimple.v().newGotoStmt(conditionCheck), increment)
            units.insertAfter(endLoop, units.getSuccOf(increment))
        }

        units.remove(targetUnit)
    }

    /**
     * Оборачивает отдельный оператор в бесконечный цикл с break
     */
    private fun wrapStatementInInfiniteLoop(body: Body, targetUnit: soot.Unit) {
        val units = body.units
        val localGen = DefaultLocalGenerator(body)
        val loopVar = localGen.generateLocal(IntType.v())
        val loopBound = generateRandomLoopBound()

        val infiniteLoopComponents = createInfiniteLoopComponents(loopVar, loopBound)

        with(infiniteLoopComponents) {
            units.insertBefore(initStmt, targetUnit)
            units.insertBefore(conditionStmt, targetUnit)
            units.insertAfter(incrementStmt, targetUnit)
            units.insertAfter(loopBackStmt, incrementStmt)
            units.insertAfter(endLoop, loopBackStmt)
        }
    }

    /**
     * Оборачивает блок кода в чистый for-цикл
     */
    private fun wrapBlockInCleanForLoop(body: Body, block: Block) {
        val units = body.units
        val localGen = DefaultLocalGenerator(body)
        val loopVar = localGen.generateLocal(IntType.v())
        val loopBound = generateRandomLoopBound()

        val startUnit = block.startUnit
        val endUnit = block.endUnit

        val blockForLoopComponents = createBlockForLoopComponents(loopVar, loopBound)

        with(blockForLoopComponents) {
            units.insertBefore(initStmt, startUnit)
            units.insertAfter(gotoCondition, initStmt)
            units.insertAfter(conditionCheck, gotoCondition)
            units.insertAfter(conditionStmt, conditionCheck)
            units.insertAfter(gotoAfterLoop, conditionStmt)
            units.insertAfter(loopBodyStart, gotoAfterLoop)

            units.insertAfter(loopBodyEnd, endUnit)
            units.insertAfter(increment, loopBodyEnd)
            units.insertAfter(gotoConditionAfterIncrement, increment)
            units.insertAfter(afterLoop, gotoConditionAfterIncrement)
        }
    }

    /**
     * Оборачивает блок кода в бесконечный цикл с break
     */
    private fun wrapBlockInInfiniteLoop(body: Body, block: Block) {
        val units = body.units
        val localGen = DefaultLocalGenerator(body)
        val loopVar = localGen.generateLocal(IntType.v())
        val loopBound = generateRandomLoopBound()

        val startUnit = block.startUnit
        val endUnit = block.endUnit

        val beforeBlockNop = Jimple.v().newNopStmt()
        val afterBlockNop = Jimple.v().newNopStmt()

        units.insertBefore(beforeBlockNop, startUnit)
        units.insertAfter(afterBlockNop, endUnit)

        val infiniteLoopComponents = createBlockInfiniteLoopComponents(loopVar, loopBound, afterBlockNop)

        with(infiniteLoopComponents) {
            units.insertBefore(initStmt, beforeBlockNop)
            units.insertAfter(loopStart, initStmt)
            units.insertAfter(conditionStmt, loopStart)

            units.insertBefore(incrementStmt, afterBlockNop)
            units.insertBefore(loopBackStmt, afterBlockNop)
        }
    }

    /**
     * Генерирует случайное количество итераций для цикла
     */
    private fun generateRandomLoopBound(): IntConstant {
        return IntConstant.v((1..1000).random())
    }

    /**
     * Создает компоненты чистого for-цикла для оператора
     */
    private data class ForLoopComponents(
        val initStmt: soot.Unit,
        val conditionCheck: soot.Unit,
        val conditionStmt: soot.Unit,
        val loopBody: soot.Unit,
        val increment: soot.Unit,
        val endLoop: soot.Unit,
        val gotoCondition: soot.Unit,
        val gotoEnd: soot.Unit
    )

    private fun createCleanForLoopComponents(loopVar: soot.Local, loopBound: IntConstant): ForLoopComponents {
        val initStmt = Jimple.v().newAssignStmt(loopVar, IntConstant.v(0))
        val conditionCheck = Jimple.v().newNopStmt()
        val loopBody = Jimple.v().newNopStmt()
        val increment = Jimple.v().newAssignStmt(
            loopVar,
            Jimple.v().newAddExpr(loopVar, IntConstant.v(1))
        )
        val endLoop = Jimple.v().newNopStmt()

        val conditionStmt = Jimple.v().newIfStmt(
            Jimple.v().newLtExpr(loopVar, loopBound),
            loopBody
        )

        val gotoCondition = Jimple.v().newGotoStmt(conditionCheck)
        val gotoEnd = Jimple.v().newGotoStmt(endLoop)

        return ForLoopComponents(
            initStmt, conditionCheck, conditionStmt, loopBody,
            increment, endLoop, gotoCondition, gotoEnd
        )
    }

    /**
     * Создает компоненты бесконечного цикла для оператора
     */
    private data class InfiniteLoopComponents(
        val initStmt: soot.Unit,
        val conditionStmt: soot.Unit,
        val incrementStmt: soot.Unit,
        val loopBackStmt: soot.Unit,
        val endLoop: soot.Unit
    )

    private fun createInfiniteLoopComponents(loopVar: soot.Local, loopBound: IntConstant): InfiniteLoopComponents {
        val initStmt = Jimple.v().newAssignStmt(loopVar, IntConstant.v(0))
        val endLoop = Jimple.v().newNopStmt()
        val conditionStmt = Jimple.v().newIfStmt(
            Jimple.v().newGeExpr(loopVar, loopBound),
            endLoop
        )
        val incrementStmt = Jimple.v().newAssignStmt(
            loopVar,
            Jimple.v().newAddExpr(loopVar, IntConstant.v(1))
        )
        val loopBackStmt = Jimple.v().newGotoStmt(conditionStmt)

        return InfiniteLoopComponents(
            initStmt, conditionStmt, incrementStmt, loopBackStmt, endLoop
        )
    }

    /**
     * Создает компоненты чистого for-цикла для блока
     */
    private data class BlockForLoopComponents(
        val initStmt: soot.Unit,
        val conditionCheck: soot.Unit,
        val conditionStmt: soot.Unit,
        val loopBodyStart: soot.Unit,
        val loopBodyEnd: soot.Unit,
        val increment: soot.Unit,
        val afterLoop: soot.Unit,
        val gotoCondition: soot.Unit,
        val gotoAfterLoop: soot.Unit,
        val gotoConditionAfterIncrement: soot.Unit
    )

    private fun createBlockForLoopComponents(loopVar: soot.Local, loopBound: IntConstant): BlockForLoopComponents {
        val initStmt = Jimple.v().newAssignStmt(loopVar, IntConstant.v(0))
        val conditionCheck = Jimple.v().newNopStmt()
        val loopBodyStart = Jimple.v().newNopStmt()
        val loopBodyEnd = Jimple.v().newNopStmt()
        val increment = Jimple.v().newAssignStmt(
            loopVar,
            Jimple.v().newAddExpr(loopVar, IntConstant.v(1))
        )
        val afterLoop = Jimple.v().newNopStmt()

        val conditionStmt = Jimple.v().newIfStmt(
            Jimple.v().newLtExpr(loopVar, loopBound),
            loopBodyStart
        )

        val gotoCondition = Jimple.v().newGotoStmt(conditionCheck)
        val gotoAfterLoop = Jimple.v().newGotoStmt(afterLoop)
        val gotoConditionAfterIncrement = Jimple.v().newGotoStmt(conditionCheck)

        return BlockForLoopComponents(
            initStmt, conditionCheck, conditionStmt, loopBodyStart, loopBodyEnd,
            increment, afterLoop, gotoCondition, gotoAfterLoop, gotoConditionAfterIncrement
        )
    }

    /**
     * Создает компоненты бесконечного цикла для блока
     */
    private data class BlockInfiniteLoopComponents(
        val initStmt: soot.Unit,
        val loopStart: soot.Unit,
        val conditionStmt: soot.Unit,
        val incrementStmt: soot.Unit,
        val loopBackStmt: soot.Unit
    )

    private fun createBlockInfiniteLoopComponents(
        loopVar: soot.Local,
        loopBound: IntConstant,
        afterBlockNop: soot.Unit
    ): BlockInfiniteLoopComponents {
        val initStmt = Jimple.v().newAssignStmt(loopVar, IntConstant.v(0))
        val loopStart = Jimple.v().newNopStmt()
        val conditionStmt = Jimple.v().newIfStmt(
            Jimple.v().newGeExpr(loopVar, loopBound),
            afterBlockNop
        )
        val incrementStmt = Jimple.v().newAssignStmt(
            loopVar,
            Jimple.v().newAddExpr(loopVar, IntConstant.v(1))
        )
        val loopBackStmt = Jimple.v().newGotoStmt(loopStart)

        return BlockInfiniteLoopComponents(
            initStmt, loopStart, conditionStmt, incrementStmt, loopBackStmt
        )
    }

}

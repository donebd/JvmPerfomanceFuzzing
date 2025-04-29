package core.mutation.strategy

import core.mutation.strategy.common.Block
import core.mutation.strategy.common.BlockFinder
import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.RefType
import soot.Scene
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import java.util.*

/**
 * Стратегия мутации, добавляющая try-catch блоки вокруг случайных участков кода,
 * даже если исключения в них невозможны
 */
class ExceptionHandlingMutator(jimpleTranslator: JimpleTranslator) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val units = body.units
        val stmtList = units.filterNot { it is IdentityStmt }.toList()

        if (stmtList.size < 3) return

        try {
            // Используем BlockFinder как в LoopWrapperMutationStrategy
            val blockFinder = BlockFinder(body)
            val blocks = blockFinder.findBlocks()

            if (blocks.isNotEmpty() && Random().nextBoolean()) {
                // Оборачиваем блок в try-catch
                wrapBlockInTryCatch(body, blocks.random())
            } else {
                // Оборачиваем одиночный оператор
                val validUnits = findValidStatements(body)
                if (validUnits.isEmpty()) return
                wrapStatementInTryCatch(body, validUnits.random())
            }
        } catch (e: Exception) {
            // Игнорируем ошибки и оставляем код без изменений
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
     * Оборачивает отдельный оператор в try-catch блок
     */
    private fun wrapStatementInTryCatch(body: Body, targetUnit: soot.Unit) {
        val units = body.units

        // Создаем nop-метки для границ блоков
        val beforeTryStmt = Jimple.v().newNopStmt()
        val afterTryStmt = Jimple.v().newNopStmt()
        val catchStart = Jimple.v().newNopStmt()
        val catchEnd = Jimple.v().newNopStmt()

        // Создаем локальную переменную для исключения
        val localGen = DefaultLocalGenerator(body)
        val exceptionLocal = localGen.generateLocal(RefType.v("java.lang.RuntimeException"))

        // Создаем инструкцию catch для получения исключения
        val catchStmt = Jimple.v().newIdentityStmt(
            exceptionLocal,
            Jimple.v().newCaughtExceptionRef()
        )

        // Создаем goto для пропуска catch-блока при нормальном выполнении
        val gotoAfterCatch = Jimple.v().newGotoStmt(catchEnd)

        // Вставляем структуру try блока
        units.insertBefore(beforeTryStmt, targetUnit)
        // Клонируем оригинальный оператор внутрь try блока
        val clonedUnit = targetUnit.clone() as soot.Unit
        units.insertAfter(clonedUnit, beforeTryStmt)
        units.insertAfter(afterTryStmt, clonedUnit)
        units.insertAfter(gotoAfterCatch, afterTryStmt)

        // Вставляем catch блок
        units.insertAfter(catchStart, gotoAfterCatch)
        units.insertAfter(catchStmt, catchStart)

        // Создаем безопасный код для обработки исключения
        // Просто игнорируем исключение и продолжаем выполнение
        units.insertAfter(catchEnd, catchStmt)

        // Удаляем оригинальный оператор
        units.remove(targetUnit)

        // Добавляем trap (определение try-catch блока) в тело метода
        body.traps.add(Jimple.v().newTrap(
            Scene.v().getSootClass("java.lang.RuntimeException"),
            beforeTryStmt,
            afterTryStmt,
            catchStart
        ))
    }

    /**
     * Оборачивает блок кода в try-catch блок
     */
    private fun wrapBlockInTryCatch(body: Body, block: Block) {
        val units = body.units
        val startUnit = block.startUnit
        val endUnit = block.endUnit

        // Создаем nop-метки для границ блоков
        val beforeTryNop = Jimple.v().newNopStmt()
        val afterTryNop = Jimple.v().newNopStmt()
        val catchStartNop = Jimple.v().newNopStmt()
        val catchEndNop = Jimple.v().newNopStmt()

        // Вставляем метки до и после блока
        units.insertBefore(beforeTryNop, startUnit)
        units.insertAfter(afterTryNop, endUnit)

        // Создаем goto для пропуска catch-блока при нормальном выполнении
        val gotoAfterCatch = Jimple.v().newGotoStmt(catchEndNop)
        units.insertAfter(gotoAfterCatch, afterTryNop)

        // Вставляем catch блок
        units.insertAfter(catchStartNop, gotoAfterCatch)

        // Создаем локальную переменную для исключения
        val localGen = DefaultLocalGenerator(body)
        val exceptionLocal = localGen.generateLocal(RefType.v("java.lang.RuntimeException"))

        // Создаем инструкцию catch для получения исключения
        val catchStmt = Jimple.v().newIdentityStmt(
            exceptionLocal,
            Jimple.v().newCaughtExceptionRef()
        )
        units.insertAfter(catchStmt, catchStartNop)

        // Завершаем catch блок
        units.insertAfter(catchEndNop, catchStmt)

        // Добавляем trap (определение try-catch блока) в тело метода
        body.traps.add(Jimple.v().newTrap(
            Scene.v().getSootClass("java.lang.RuntimeException"),
            beforeTryNop,
            afterTryNop,
            catchStartNop
        ))
    }
}
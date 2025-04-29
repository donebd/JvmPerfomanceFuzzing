package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.Local
import soot.RefType
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import java.util.*

/**
 * Стратегия мутации, которая добавляет избыточные проверки на null для ссылочных переменных.
 * Мутатор создает разнообразные типы проверок, включая простые условия, вложенные проверки
 * и цепочки проверок на null с разными шаблонами, что позволяет тестировать оптимизации
 * предсказания переходов и исключения избыточных проверок в разных JVM.
 */
class RedundantNullCheckMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val insertPoints = findSafeInsertPoints(body)
        val targetStmt = insertPoints.randomOrNull() ?: return

        val refLocals = findAccessibleReferenceLocals(body, targetStmt)
        if (refLocals.isEmpty()) return

        val mutationType = Random().nextInt(6)

        when (mutationType) {
            0 -> insertSimpleNullCheck(body, targetStmt, refLocals)
            1 -> insertNullCheckWithEmptyBlock(body, targetStmt, refLocals)
            2 -> insertDoubleNullCheck(body, targetStmt, refLocals)
            3 -> insertNullCheckChain(body, targetStmt, refLocals)
            4 -> insertNestedNullChecks(body, targetStmt, refLocals)
            5 -> insertComplexNullCheck(body, targetStmt, refLocals)
        }
    }

    /**
     * Находит безопасные точки для вставки проверок на null
     */
    private fun findSafeInsertPoints(body: Body): List<soot.Unit> =
        body.units.filter {
            it !is IdentityStmt &&
                    it !is ReturnStmt &&
                    it !is ReturnVoidStmt &&
                    it !is ThrowStmt &&
                    it !is GotoStmt
        }

    /**
     * Находит доступные ссылочные переменные в данной точке программы
     * (Упрощенная версия - на практике стоит реализовать более точный анализ)
     */
    private fun findAccessibleReferenceLocals(body: Body, targetStmt: soot.Unit): List<Local> {
        return body.locals.filter { it.type is RefType }
    }

    /**
     * Вставляет простую проверку на null вида "if (obj == null) goto label; label:"
     */
    private fun insertSimpleNullCheck(body: Body, targetStmt: soot.Unit, refLocals: List<Local>) {
        val local = refLocals.random()
        val units = body.units

        val nop = Jimple.v().newNopStmt()

        val condition = if (Random().nextBoolean()) {
            Jimple.v().newEqExpr(local, NullConstant.v())
        } else {
            Jimple.v().newNeExpr(local, NullConstant.v())
        }

        val ifStmt = Jimple.v().newIfStmt(condition, nop)

        units.insertBefore(ifStmt, targetStmt)
        units.insertBefore(nop, targetStmt)
    }

    /**
     * Вставляет проверку с пустым блоком: "if (obj == null) { } else { }"
     */
    private fun insertNullCheckWithEmptyBlock(body: Body, targetStmt: soot.Unit, refLocals: List<Local>) {
        val local = refLocals.random()
        val units = body.units

        val thenStart = Jimple.v().newNopStmt()
        val elseStart = Jimple.v().newNopStmt()
        val endIf = Jimple.v().newNopStmt()

        val condition = if (Random().nextBoolean()) {
            Jimple.v().newEqExpr(local, NullConstant.v())
        } else {
            Jimple.v().newNeExpr(local, NullConstant.v())
        }

        val ifStmt = Jimple.v().newIfStmt(condition, elseStart)
        val gotoEnd = Jimple.v().newGotoStmt(endIf)

        units.insertBefore(ifStmt, targetStmt)
        units.insertBefore(thenStart, targetStmt)
        units.insertBefore(gotoEnd, targetStmt)
        units.insertBefore(elseStart, targetStmt)
        units.insertBefore(endIf, targetStmt)
    }

    /**
     * Вставляет двойную проверку: "if (obj1 == null || obj2 != null) { }"
     */
    private fun insertDoubleNullCheck(body: Body, targetStmt: soot.Unit, refLocals: List<Local>) {
        if (refLocals.size < 2) {
            insertSimpleNullCheck(body, targetStmt, refLocals)
            return
        }

        val locals = refLocals.shuffled().take(2)
        val local1 = locals[0]
        val local2 = locals[1]

        val units = body.units

        val secondCheck = Jimple.v().newNopStmt()
        val endIf = Jimple.v().newNopStmt()
        val trueBlock = Jimple.v().newNopStmt()

        val cond1 = Jimple.v().newEqExpr(local1, NullConstant.v())
        val ifCond1 = Jimple.v().newIfStmt(cond1, trueBlock)

        val cond2 = Jimple.v().newNeExpr(local2, NullConstant.v())
        val ifCond2 = Jimple.v().newIfStmt(cond2, trueBlock)

        val gotoEnd = Jimple.v().newGotoStmt(endIf)

        units.insertBefore(ifCond1, targetStmt)
        units.insertBefore(secondCheck, targetStmt)
        units.insertBefore(ifCond2, targetStmt)
        units.insertBefore(gotoEnd, targetStmt)
        units.insertBefore(trueBlock, targetStmt)
        units.insertBefore(endIf, targetStmt)
    }

    /**
     * Вставляет цепочку последовательных проверок на null
     */
    private fun insertNullCheckChain(body: Body, targetStmt: soot.Unit, refLocals: List<Local>) {
        val localCount = refLocals.size.coerceAtMost(3) // Максимум 3 проверки
        val selectedLocals = refLocals.shuffled().take(localCount)

        val units = body.units
        val endChain = Jimple.v().newNopStmt()

        var lastStmt = targetStmt
        for (local in selectedLocals) {
            val skipStmt = Jimple.v().newNopStmt()

            val condition = if (Random().nextBoolean()) {
                Jimple.v().newEqExpr(local, NullConstant.v())
            } else {
                Jimple.v().newNeExpr(local, NullConstant.v())
            }

            val ifStmt = Jimple.v().newIfStmt(condition, skipStmt)

            units.insertBefore(ifStmt, lastStmt)
            units.insertBefore(skipStmt, lastStmt)

            lastStmt = ifStmt
        }

        units.insertBefore(endChain, targetStmt)
    }

    /**
     * Вставляет вложенные проверки на null
     */
    private fun insertNestedNullChecks(body: Body, targetStmt: soot.Unit, refLocals: List<Local>) {
        if (refLocals.size < 2) {
            insertSimpleNullCheck(body, targetStmt, refLocals)
            return
        }

        val locals = refLocals.shuffled().take(2)
        val local1 = locals[0]
        val local2 = locals[1]

        val units = body.units

        val outerThen = Jimple.v().newNopStmt()
        val outerElse = Jimple.v().newNopStmt()
        val innerThen = Jimple.v().newNopStmt()
        val innerElse = Jimple.v().newNopStmt()
        val endNested = Jimple.v().newNopStmt()

        val outerCond = Jimple.v().newNeExpr(local1, NullConstant.v())
        val innerCond = Jimple.v().newNeExpr(local2, NullConstant.v())

        val outerIfStmt = Jimple.v().newIfStmt(outerCond, outerThen)
        val gotoOuterElse = Jimple.v().newGotoStmt(outerElse)
        val innerIfStmt = Jimple.v().newIfStmt(innerCond, innerThen)
        val gotoInnerElse = Jimple.v().newGotoStmt(innerElse)
        val gotoEnd1 = Jimple.v().newGotoStmt(endNested)
        val gotoEnd2 = Jimple.v().newGotoStmt(endNested)

        units.insertBefore(outerIfStmt, targetStmt)
        units.insertBefore(gotoOuterElse, targetStmt)
        units.insertBefore(outerThen, targetStmt)
        units.insertBefore(innerIfStmt, targetStmt)
        units.insertBefore(gotoInnerElse, targetStmt)
        units.insertBefore(innerThen, targetStmt)
        units.insertBefore(gotoEnd1, targetStmt)
        units.insertBefore(innerElse, targetStmt)
        units.insertBefore(gotoEnd2, targetStmt)
        units.insertBefore(outerElse, targetStmt)
        units.insertBefore(endNested, targetStmt)
    }

    /**
     * Вставляет сложную комбинированную проверку на null с побочным эффектом
     * (присвоение временной переменной)
     */
    private fun insertComplexNullCheck(body: Body, targetStmt: soot.Unit, refLocals: List<Local>) {
        val local = refLocals.random()
        val tmpLocal = DefaultLocalGenerator(body).generateLocal(local.type)

        val units = body.units

        val ifBlock = Jimple.v().newNopStmt()
        val elseBlock = Jimple.v().newNopStmt()
        val endIf = Jimple.v().newNopStmt()

        val condition = Jimple.v().newNeExpr(local, NullConstant.v())
        val ifStmt = Jimple.v().newIfStmt(condition, ifBlock)

        val thenAssign = Jimple.v().newAssignStmt(tmpLocal, local)
        val elseAssign = Jimple.v().newAssignStmt(tmpLocal, NullConstant.v())

        val gotoEnd = Jimple.v().newGotoStmt(endIf)

        units.insertBefore(ifStmt, targetStmt)
        units.insertBefore(elseBlock, targetStmt)
        units.insertBefore(elseAssign, targetStmt)
        units.insertBefore(gotoEnd, targetStmt)
        units.insertBefore(ifBlock, targetStmt)
        units.insertBefore(thenAssign, targetStmt)
        units.insertBefore(endIf, targetStmt)
    }
}

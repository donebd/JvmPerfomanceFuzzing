package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.IntType
import soot.PatchingChain
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import kotlin.random.Random

/**
 * Стратегия мутации, которая вставляет конструкции lookupswitch (оператор switch в байткоде)
 * в Java-код. Создает новую локальную переменную с убывающим счетчиком и оператор switch,
 * который направляет выполнение кода на различные метки в зависимости от значения счетчика.
 *
 * Эта мутация позволяет тестировать различия в эффективности таблиц переходов и диспетчеризации
 * lookupswitch между разными реализациями JVM. Особенно интересна для выявления различий
 * в оптимизациях условных переходов, когда JIT-компилятор принимает решение о применении
 * таблиц переходов или последовательных сравнений.
 */
class LookupSwitchMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    companion object {
        private const val LOOP_LIMIT = 5
    }

    override fun applyMutation(body: Body) {
        val units = body.units
        val validStmts = units.filterNot {
            it is IdentityStmt || it is ReturnStmt || it is ReturnVoidStmt
        }

        if (validStmts.size < 4) return

        val hookPoint = validStmts.random()
        val localGen = DefaultLocalGenerator(body)
        val switchVar = localGen.generateLocal(IntType.v())
        body.locals.add(switchVar)

        val (labels, lookupValues) = createSwitchTargets(units, validStmts)
        if (labels.isEmpty()) return

        val defaultLabel = createDefaultTarget(units, validStmts, labels.toSet())

        val switchStmt = Jimple.v().newLookupSwitchStmt(switchVar, lookupValues, labels, defaultLabel)
        val skipSwitch = Jimple.v().newNopStmt()

        val assignInit = Jimple.v().newAssignStmt(switchVar, IntConstant.v(LOOP_LIMIT))
        val decrement = Jimple.v().newAssignStmt(switchVar, Jimple.v().newSubExpr(switchVar, IntConstant.v(1)))
        val condition = Jimple.v().newIfStmt(Jimple.v().newLeExpr(switchVar, IntConstant.v(0)), skipSwitch)

        units.insertBefore(assignInit, validStmts.first())
        units.insertAfter(decrement, hookPoint)
        units.insertAfter(condition, decrement)
        units.insertAfter(switchStmt, condition)
        units.insertAfter(skipSwitch, switchStmt)

        body.validate()
    }

    private fun createSwitchTargets(
        units: PatchingChain<soot.Unit>,
        validStmts: List<soot.Unit>
    ): Pair<List<Stmt>, List<IntConstant>> {
        val caseCount = Random.nextInt(1, 4)
        val selected = mutableSetOf<Stmt>()
        val labels = mutableListOf<Stmt>()
        val values = mutableListOf<IntConstant>()

        repeat(caseCount) {
            val value = LOOP_LIMIT - it - 1
            val stmt = (0..5)
                .map { validStmts.random() as Stmt }
                .firstOrNull { it !in selected } ?: return@repeat

            selected += stmt

            val nop = Jimple.v().newNopStmt()
            if (units.contains(stmt)) {
                units.insertBefore(nop, stmt)
                labels += nop
                values += IntConstant.v(value)
            }
        }

        return labels to values
    }

    private fun createDefaultTarget(
        units: PatchingChain<soot.Unit>,
        validStmts: List<soot.Unit>,
        exclude: Set<Stmt>
    ): Stmt {
        val stmt = (0..5)
            .map { validStmts.random() as Stmt }
            .firstOrNull { it !in exclude } ?: validStmts.last() as Stmt

        val nop = Jimple.v().newNopStmt()
        if (units.contains(stmt)) {
            units.insertBefore(nop, stmt)
        } else {
            units.addLast(nop)
        }
        return nop
    }
}

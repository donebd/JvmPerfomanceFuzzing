package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.Local
import soot.Value
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import kotlin.math.min

/**
 * Стратегия мутации, которая изменяет направление выполнения циклов в Java-байткоде.
 * Находит и трансформирует стандартные циклы с увеличением счетчика
 * (например, for(int i=0; i<n; i++)) в эквивалентные циклы с обратным направлением
 * (for(int i=n-1; i>=0; i--)).
 *
 * Эта мутация помогает выявлять различия в эффективности обработки циклов
 * различными JVM, включая оптимизации предсказания ветвлений, кэширования и
 * предвыборки данных. Несмотря на функциональную эквивалентность, обратные циклы
 * могут существенно различаться в производительности из-за особенностей работы
 * процессоров и JIT-компиляторов.
 */
class LoopDirectionReverserMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val loops = findSimpleLoops(body)
        val target = loops.randomOrNull() ?: return

        reverseLoop(body, target)
        body.validate()
    }

    data class SimpleLoop(
        val initStmt: AssignStmt,
        val conditionStmt: IfStmt,
        val incrementStmt: AssignStmt,
        val loopVar: Local,
        val bound: Value
    )

    private fun findSimpleLoops(body: Body): List<SimpleLoop> {
        val units = body.units.toList()
        val loops = mutableListOf<SimpleLoop>()

        for (i in 0 until units.size - 2) {
            val stmt = units[i]

            if (stmt !is AssignStmt) continue
            val loopVar = stmt.leftOp as? Local ?: continue

            var conditionStmt: IfStmt? = null
            var incrementStmt: AssignStmt? = null
            var bound: Value? = null

            for (j in i + 1 until min(i + 10, units.size)) {
                val nextStmt = units[j]
                if (nextStmt is IfStmt) {
                    val condition = nextStmt.condition as? BinopExpr ?: continue

                    // Проверяем, что условие подходит для прямого цикла (i < bound или i <= bound)
                    if (condition is LtExpr || condition is LeExpr) {
                        if (condition.op1 == loopVar) {
                            bound = condition.op2
                            conditionStmt = nextStmt
                            break
                        } else if (condition.op2 == loopVar) {
                            bound = condition.op1
                            conditionStmt = nextStmt
                            break
                        }
                    }
                }
            }

            if (conditionStmt == null || bound == null) continue

            for (j in i + 1 until min(i + 15, units.size)) {
                val nextStmt = units[j]
                if (nextStmt is AssignStmt && nextStmt.leftOp == loopVar) {
                    val rhs = nextStmt.rightOp

                    if (rhs is AddExpr && rhs.op1 == loopVar) {
                        val increment = rhs.op2
                        if (increment is IntConstant && increment.value == 1) {
                            incrementStmt = nextStmt
                            break
                        }
                    }
                }
            }

            if (incrementStmt != null) {
                loops.add(SimpleLoop(stmt, conditionStmt, incrementStmt, loopVar, bound))
            }
        }

        return loops
    }

    private fun reverseLoop(body: Body, loop: SimpleLoop) {
        val units = body.units
        val localGen = DefaultLocalGenerator(body)

        val boundVar = if (loop.bound is IntConstant) {
            IntConstant.v(loop.bound.value - 1)
        } else {
            val tmpBound = localGen.generateLocal(loop.bound.type)

            val assignBound = Jimple.v().newAssignStmt(tmpBound, loop.bound)
            units.insertBefore(assignBound, loop.initStmt)

            val boundMinusOne = localGen.generateLocal(loop.bound.type)
            val subtractStmt = Jimple.v().newAssignStmt(
                boundMinusOne,
                Jimple.v().newSubExpr(tmpBound, IntConstant.v(1))
            )
            units.insertAfter(subtractStmt, assignBound)

            boundMinusOne
        }

        val newInit = Jimple.v().newAssignStmt(loop.loopVar, boundVar)

        val newCond = Jimple.v().newIfStmt(
            Jimple.v().newGeExpr(loop.loopVar, IntConstant.v(0)),
            loop.conditionStmt.target
        )

        val newIncr = Jimple.v().newAssignStmt(
            loop.loopVar,
            Jimple.v().newSubExpr(loop.loopVar, IntConstant.v(1))
        )

        safelyReplaceUnit(units, loop.initStmt, newInit)
        safelyReplaceUnit(units, loop.conditionStmt, newCond)
        safelyReplaceUnit(units, loop.incrementStmt, newIncr)
    }

    private fun safelyReplaceUnit(units: soot.util.Chain<soot.Unit>, oldUnit: soot.Unit, newUnit: soot.Unit) {
        try {
            val prevUnit = units.getPredOf(oldUnit)
            val nextUnit = units.getSuccOf(oldUnit)

            units.remove(oldUnit)

            if (prevUnit != null) {
                units.insertAfter(newUnit, prevUnit)
            } else if (nextUnit != null) {
                units.insertBefore(newUnit, nextUnit)
            } else {
                units.add(newUnit)
            }
        } catch (e: Exception) {
            units.insertBefore(newUnit, oldUnit)
            units.remove(oldUnit)
        }
    }
}

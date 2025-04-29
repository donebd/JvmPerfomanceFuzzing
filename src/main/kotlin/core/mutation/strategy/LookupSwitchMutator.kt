package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.Body
import soot.IntType
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import java.util.*

/**
 * Стратегия мутации, добавляющая конструкцию switch (lookup),
 * которая перенаправляет выполнение программы в случайные места метода
 */
class LookupSwitchMutator(jimpleTranslator: JimpleTranslator) : MutationStrategy(jimpleTranslator) {

    companion object {
        private const val LOOP_LIMIT = 5
    }

    override fun applyMutation(body: Body) {
        try {
            val units = body.units

            val validStmts = units.filterNot { unit ->
                unit is IdentityStmt || unit is ReturnStmt || unit is ReturnVoidStmt
            }.toList()

            if (validStmts.size < 4) return

            val random = Random()
            val hookingPointIndex = random.nextInt(validStmts.size - 1)
            val hookingPoint = validStmts[hookingPointIndex]

            val localGen = DefaultLocalGenerator(body)
            val switchVar = localGen.generateLocal(IntType.v())

            val caseCount = random.nextInt(3) + 1
            val lookupValues = ArrayList<IntConstant>()
            val labels = ArrayList<Stmt>()
            val selectedTargetPoints = HashSet<Stmt>()

            var counter = LOOP_LIMIT
            for (i in 0 until caseCount) {
                lookupValues.add(IntConstant.v(--counter))

                var targetPoint: Stmt
                var attempts = 0
                do {
                    targetPoint = validStmts[random.nextInt(validStmts.size)] as Stmt
                    attempts++
                } while (selectedTargetPoints.contains(targetPoint) && attempts < 5)

                selectedTargetPoints.add(targetPoint)

                val nop = Jimple.v().newNopStmt()
                if (units.contains(targetPoint)) {
                    units.insertBefore(nop, targetPoint)
                    labels.add(nop)
                }
            }

            if (labels.isEmpty()) return

            var defaultTargetPoint: Stmt
            var attempts = 0
            do {
                defaultTargetPoint = validStmts[random.nextInt(validStmts.size)] as Stmt
                attempts++
            } while (selectedTargetPoints.contains(defaultTargetPoint) && attempts < 5)

            val defaultNop = Jimple.v().newNopStmt()
            if (units.contains(defaultTargetPoint)) {
                units.insertBefore(defaultNop, defaultTargetPoint)
            } else {
                if (units.size > 0) {
                    units.addLast(defaultNop)
                } else {
                    units.add(defaultNop)
                }
            }

            val switchStmt = Jimple.v().newLookupSwitchStmt(
                switchVar, lookupValues, labels, defaultNop
            )

            val skipSwitch = Jimple.v().newNopStmt()

            val assignStmt = Jimple.v().newAssignStmt(
                switchVar, IntConstant.v(LOOP_LIMIT)
            )

            val subExpr = Jimple.v().newSubExpr(switchVar, IntConstant.v(1))
            val subStmt = Jimple.v().newAssignStmt(switchVar, subExpr)

            val condition = Jimple.v().newLeExpr(switchVar, IntConstant.v(0))
            val ifStmt = Jimple.v().newIfStmt(condition, skipSwitch)

            if (units.contains(validStmts.first())) {
                units.insertBefore(assignStmt, validStmts.first())
            } else {
                units.addFirst(assignStmt)
            }

            if (units.contains(hookingPoint)) {
                units.insertAfter(skipSwitch, hookingPoint)
                units.insertAfter(switchStmt, hookingPoint)
                units.insertAfter(ifStmt, hookingPoint)
                units.insertAfter(subStmt, hookingPoint)
            }

            body.validate()

        } catch (e: Exception) {
            println("Error in LookupSwitchMutator: ${e.message}")
            e.printStackTrace()
        }
    }
}

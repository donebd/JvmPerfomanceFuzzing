package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.*
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

            // Выбираем только исполняемые операторы (не идентификаторы)
            val validStmts = units.filterNot { unit ->
                unit is IdentityStmt || unit is ReturnStmt || unit is ReturnVoidStmt
            }.toList()

            if (validStmts.size < 4) return // Нужно достаточно операторов для мутации

            // Выбираем случайную точку вставки
            val random = Random()
            val hookingPointIndex = random.nextInt(validStmts.size - 1)
            val hookingPoint = validStmts[hookingPointIndex]

            // Создаем переменную-счетчик _M
            val localGen = DefaultLocalGenerator(body)
            val switchVar = localGen.generateLocal(IntType.v())

            // Создаем конструкцию switch
            val caseCount = random.nextInt(3) + 1 // 1-3 случая
            val lookupValues = ArrayList<IntConstant>()
            val labels = ArrayList<Stmt>()
            val selectedTargetPoints = HashSet<Stmt>()

            // Генерируем значения и метки для switch
            var counter = LOOP_LIMIT
            for (i in 0 until caseCount) {
                lookupValues.add(IntConstant.v(--counter))

                // Выбираем случайную целевую точку (не из точек вставки)
                var targetPoint: Stmt
                var attempts = 0
                do {
                    targetPoint = validStmts[random.nextInt(validStmts.size)] as Stmt
                    attempts++
                } while (selectedTargetPoints.contains(targetPoint) && attempts < 5)

                selectedTargetPoints.add(targetPoint)

                // Создаем nop-метку для перехода
                val nop = Jimple.v().newNopStmt()
                if (units.contains(targetPoint)) {
                    units.insertBefore(nop, targetPoint)
                    labels.add(nop)
                }
            }

            // Если не нашли подходящие метки, выходим
            if (labels.isEmpty()) return

            // Создаем default-метку и случай
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
                // Если целевая точка недоступна, вставляем Nop в безопасное место
                if (units.size > 0) {
                    units.addLast(defaultNop)  // Добавляем в конец, если не можем вставить перед точкой
                } else {
                    units.add(defaultNop)      // Добавляем, если цепочка пуста
                }
            }

            // Создаем инструкцию switch
            val switchStmt = Jimple.v().newLookupSwitchStmt(
                switchVar, lookupValues, labels, defaultNop
            )

            // Создаем метку для пропуска switch
            val skipSwitch = Jimple.v().newNopStmt()

            // Создаем инициализацию переменной
            val assignStmt = Jimple.v().newAssignStmt(
                switchVar, IntConstant.v(LOOP_LIMIT)
            )

            // Создаем декремент переменной
            val subExpr = Jimple.v().newSubExpr(switchVar, IntConstant.v(1))
            val subStmt = Jimple.v().newAssignStmt(switchVar, subExpr)

            // Создаем условие для пропуска switch
            val condition = Jimple.v().newLeExpr(switchVar, IntConstant.v(0))
            val ifStmt = Jimple.v().newIfStmt(condition, skipSwitch)

            // Вставляем структуру безопасно
            if (units.contains(validStmts.first())) {
                units.insertBefore(assignStmt, validStmts.first())
            } else {
                units.addFirst(assignStmt)
            }

            if (units.contains(hookingPoint)) {
                // Вставляем switch после выбранной точки
                units.insertAfter(skipSwitch, hookingPoint)
                units.insertAfter(switchStmt, hookingPoint)
                units.insertAfter(ifStmt, hookingPoint)
                units.insertAfter(subStmt, hookingPoint)
            }

            // Проверяем валидность кода
            body.validate()

        } catch (e: Exception) {
            println("Error in LookupSwitchMutator: ${e.message}")
            e.printStackTrace()
        }
    }
}
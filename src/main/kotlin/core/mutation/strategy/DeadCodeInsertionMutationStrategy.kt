package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.*
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.IdentityStmt
import soot.jimple.IntConstant
import soot.jimple.Jimple

/**
 * Стратегия мутации, добавляющая "мертвый код" в метод.
 *
 * Мутатор вставляет различные типы вычислений, результаты которых
 * не используются в дальнейшем коде. Теоретически, оптимизирующие JVM
 * должны распознавать и удалять такой код, но различия в реализациях
 * оптимизаций могут показать интересные отличия в производительности.
 *
 *
 * Мутатор вставляет один из нескольких типов "мертвого кода" в
 * случайную точку метода, сохраняя функциональную эквивалентность.
 */
class DeadCodeInsertionMutationStrategy(jimpleTranslator: JimpleTranslator) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        // Проверка наличия подходящих мест для вставки
        val insertPoints = body.units.filter { it !is IdentityStmt }
        if (insertPoints.isEmpty()) return

        // Выбираем случайную точку вставки
        val insertPoint = insertPoints.random()

        // Выбираем случайный тип мертвого кода
        val deadCodeType = DeadCodeType.values().random()

        // Вставляем выбранный тип мертвого кода
        when (deadCodeType) {
            DeadCodeType.UNUSED_MATH -> insertUnusedMathOperations(body, insertPoint)
            DeadCodeType.INVARIANT_LOOP -> insertInvariantLoop(body, insertPoint)
            DeadCodeType.DEAD_ARRAY_ACCESS -> insertDeadArrayAccess(body, insertPoint)
            DeadCodeType.DEAD_BRANCH -> insertDeadBranch(body, insertPoint)
        }
    }

    /**
     * Типы мертвого кода
     */
    private enum class DeadCodeType {
        UNUSED_MATH,        // Математические операции, результат которых не используется
        INVARIANT_LOOP,     // Цикл с инвариантным условием
        DEAD_ARRAY_ACCESS,  // Обращение к элементам массива без использования
        DEAD_BRANCH         // Ветвь с условием, которое всегда ложно
    }

    /**
     * Вставляет последовательность математических операций, результат которых не используется
     */
    private fun insertUnusedMathOperations(body: Body, insertPoint: soot.Unit) {
        val localGen = DefaultLocalGenerator(body)

        // Создаем несколько временных переменных
        val temp1 = localGen.generateLocal(IntType.v())
        val temp2 = localGen.generateLocal(IntType.v())
        val temp3 = localGen.generateLocal(IntType.v())

        // Создаем случайные значения и операции
        val value1 = IntConstant.v(kotlin.random.Random.nextInt(1, 100))
        val value2 = IntConstant.v(kotlin.random.Random.nextInt(1, 100))

        // Создаем операторы присваивания
        val assign1 = Jimple.v().newAssignStmt(temp1, value1)
        val assign2 = Jimple.v().newAssignStmt(temp2, value2)

        // Выполняем математическую операцию
        val addExpr = Jimple.v().newAddExpr(temp1, temp2)
        val mulExpr = Jimple.v().newMulExpr(addExpr, value1)
        val assign3 = Jimple.v().newAssignStmt(temp3, mulExpr)

        // Вставляем операторы перед выбранной точкой
        val units = body.units
        units.insertBefore(assign1, insertPoint)
        units.insertBefore(assign2, insertPoint)
        units.insertBefore(assign3, insertPoint)
    }

    /**
     * Вставляет цикл с инвариантным условием (всегда выполняется фиксированное число раз)
     */
    private fun insertInvariantLoop(body: Body, insertPoint: soot.Unit) {
        val localGen = DefaultLocalGenerator(body)
        val loopVar = localGen.generateLocal(IntType.v())
        val loopBound = IntConstant.v(kotlin.random.Random.nextInt(3, 10))

        // Создаем компоненты цикла
        val initStmt = Jimple.v().newAssignStmt(loopVar, IntConstant.v(0))
        val conditionCheck = Jimple.v().newNopStmt()
        val loopBody = Jimple.v().newNopStmt()
        val increment = Jimple.v().newAssignStmt(
            loopVar,
            Jimple.v().newAddExpr(loopVar, IntConstant.v(1))
        )
        val endLoop = Jimple.v().newNopStmt()

        val conditionStmt = Jimple.v().newIfStmt(
            Jimple.v().newGeExpr(loopVar, loopBound),
            endLoop
        )

        val gotoCondition = Jimple.v().newGotoStmt(conditionCheck)

        // Создаем тело цикла - просто инкремент неиспользуемой переменной
        val dummyVar = localGen.generateLocal(IntType.v())
        val dummyAssign = Jimple.v().newAssignStmt(dummyVar, IntConstant.v(0))
        val dummyIncrement = Jimple.v().newAssignStmt(
            dummyVar,
            Jimple.v().newAddExpr(dummyVar, IntConstant.v(1))
        )

        // Вставляем операторы в правильном порядке
        val units = body.units
        units.insertBefore(dummyAssign, insertPoint)
        units.insertBefore(initStmt, insertPoint)
        units.insertBefore(conditionCheck, insertPoint)
        units.insertBefore(conditionStmt, insertPoint)
        units.insertBefore(loopBody, insertPoint)
        units.insertBefore(dummyIncrement, insertPoint)
        units.insertBefore(increment, insertPoint)
        units.insertBefore(gotoCondition, insertPoint)
        units.insertBefore(endLoop, insertPoint)
    }

    /**
     * Вставляет создание массива и обращение к его элементам без использования результата
     */
    private fun insertDeadArrayAccess(body: Body, insertPoint: soot.Unit) {
        val localGen = DefaultLocalGenerator(body)
        val arrayLocal = localGen.generateLocal(ArrayType.v(IntType.v(), 1))
        val tempLocal = localGen.generateLocal(IntType.v())
        val size = IntConstant.v(kotlin.random.Random.nextInt(5, 10))

        // Создаем массив
        val newArrayExpr = Jimple.v().newNewArrayExpr(IntType.v(), size)
        val arrayAssign = Jimple.v().newAssignStmt(arrayLocal, newArrayExpr)

        // Заполняем массив и считываем значения без использования
        val units = body.units
        units.insertBefore(arrayAssign, insertPoint)

        // Заполняем несколько элементов массива
        for (i in 0 until 3) {
            val index = IntConstant.v(i)
            val value = IntConstant.v(i * 10)
            val arrayRef = Jimple.v().newArrayRef(arrayLocal, index)
            val setElement = Jimple.v().newAssignStmt(arrayRef, value)
            units.insertBefore(setElement, insertPoint)
        }

        // Считываем элемент без использования
        val readIndex = IntConstant.v(1)
        val readRef = Jimple.v().newArrayRef(arrayLocal, readIndex)
        val readElement = Jimple.v().newAssignStmt(tempLocal, readRef)
        units.insertBefore(readElement, insertPoint)
    }

    /**
     * Вставляет условный блок с условием, которое всегда ложно
     */
    private fun insertDeadBranch(body: Body, insertPoint: soot.Unit) {
        val localGen = DefaultLocalGenerator(body)
        val conditionVar = localGen.generateLocal(IntType.v())

        // Создаем переменную с константным значением
        val assignCond = Jimple.v().newAssignStmt(conditionVar, IntConstant.v(42))

        // Создаем метку для else-блока
        val elseLabel = Jimple.v().newNopStmt()
        val endIfLabel = Jimple.v().newNopStmt()

        // Создаем заведомо ложное условие: if (42 == 43) goto deadCode
        val condition = Jimple.v().newEqExpr(conditionVar, IntConstant.v(43))
        val ifStmt = Jimple.v().newIfStmt(condition, elseLabel)

        // Создаем "мертвый код" в блоке then
        val deadVar = localGen.generateLocal(IntType.v())
        val deadAssign = Jimple.v().newAssignStmt(deadVar, IntConstant.v(99))

        // Создаем переход в конец if
        val gotoEnd = Jimple.v().newGotoStmt(endIfLabel)

        // Вставляем операторы
        val units = body.units
        units.insertBefore(assignCond, insertPoint)
        units.insertBefore(ifStmt, insertPoint)
        units.insertBefore(deadAssign, insertPoint)
        units.insertBefore(gotoEnd, insertPoint)
        units.insertBefore(elseLabel, insertPoint)
        units.insertBefore(endIfLabel, insertPoint)
    }

    // Не забудьте добавить необходимые импорты
    // import soot.ArrayType
    // import soot.DefaultLocalGenerator
}

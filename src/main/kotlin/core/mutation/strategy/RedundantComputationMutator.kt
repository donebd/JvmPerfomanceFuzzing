package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.*
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import java.util.*

/**
 * Стратегия мутации, которая добавляет избыточные вычисления в байткод без изменения
 * функциональности программы. Эта стратегия вставляет цепочки арифметических операций,
 * результаты которых сохраняются в локальных переменных, но не влияют на логику программы.
 *
 * Мутатор генерирует различные типы вычислений от простых до сложных, что позволяет
 * тестировать эффективность JIT-оптимизаций между разными реализациями JVM.
 */
class RedundantComputationMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val candidates = findSafeInsertionPoints(body)
        if (candidates.isEmpty()) return

        val target = candidates.random()
        val localGen = DefaultLocalGenerator(body)
        body.units

        // Выбираем тип избыточных вычислений
        val mutationType = Random().nextInt(6)

        when (mutationType) {
            0 -> insertSimpleArithmetic(body, localGen, target)
            1 -> insertComplexArithmetic(body, localGen, target)
            2 -> insertComputationChain(body, localGen, target)
            3 -> insertConditionedComputation(body, localGen, target)
            4 -> insertSimpleStringOperation(body, localGen, target)
            5 -> insertComplexStringOperation(body, localGen, target)
        }
    }

    /**
     * Находит безопасные точки для вставки избыточных вычислений
     */
    private fun findSafeInsertionPoints(body: Body): List<soot.Unit> {
        return body.units.filter { unit ->
            // Избегаем специальных операторов, которые могут нарушить поток выполнения
            unit !is IdentityStmt &&
                    unit !is ReturnStmt && unit !is ReturnVoidStmt &&
                    unit !is ThrowStmt && unit !is GotoStmt &&
                    unit !is IfStmt && unit !is SwitchStmt
        }.toList()
    }

    /**
     * Вставляет простую арифметическую операцию (сложение, вычитание, умножение, деление)
     */
    private fun insertSimpleArithmetic(body: Body, localGen: DefaultLocalGenerator, target: soot.Unit) {
        val resultVar = localGen.generateLocal(IntType.v())
        val expr = generateRandomArithmeticExpr()

        val assign = Jimple.v().newAssignStmt(resultVar, expr)
        body.units.insertBefore(assign, target)
    }

    /**
     * Вставляет сложную арифметическую операцию (комбинированную)
     */
    private fun insertComplexArithmetic(body: Body, localGen: DefaultLocalGenerator, target: soot.Unit) {
        val resultVar = localGen.generateLocal(IntType.v())
        val tempVar1 = localGen.generateLocal(IntType.v())
        val tempVar2 = localGen.generateLocal(IntType.v())

        val expr1 = generateRandomArithmeticExpr()
        val expr2 = generateRandomArithmeticExpr()

        val units = body.units

        // Создаем вычисление вида: temp1 = a + b; temp2 = c * d; result = temp1 - temp2
        val assign1 = Jimple.v().newAssignStmt(tempVar1, expr1)
        val assign2 = Jimple.v().newAssignStmt(tempVar2, expr2)

        val finalExpr = when (Random().nextInt(4)) {
            0 -> Jimple.v().newAddExpr(tempVar1, tempVar2)
            1 -> Jimple.v().newSubExpr(tempVar1, tempVar2)
            2 -> Jimple.v().newMulExpr(tempVar1, tempVar2)
            else -> {
                val safeDivisor = Jimple.v().newAddExpr(tempVar2, IntConstant.v(1))
                Jimple.v().newDivExpr(tempVar1, safeDivisor)
            }
        }

        val assign3 = Jimple.v().newAssignStmt(resultVar, finalExpr)

        units.insertBefore(assign1, target)
        units.insertBefore(assign2, assign1)
        units.insertBefore(assign3, assign2)
    }

    /**
     * Вставляет цепочку вычислений, имитирующую реальные алгоритмы
     */
    private fun insertComputationChain(body: Body, localGen: DefaultLocalGenerator, target: soot.Unit) {
        val resultVar = localGen.generateLocal(IntType.v())
        val chainLength = Random().nextInt(5) + 3 // Создаем цепочку длиной от 3 до 7 операций

        val units = body.units

        val initExpr = IntConstant.v(Random().nextInt(100) + 1)
        val initAssign = Jimple.v().newAssignStmt(resultVar, initExpr)
        units.insertBefore(initAssign, target)

        var lastStmt = initAssign

        // Создаем цепочку операций, каждая использует результат предыдущей
        for (i in 0 until chainLength) {
            val operand = IntConstant.v(Random().nextInt(20) + 1)

            val expr = when (Random().nextInt(4)) {
                0 -> Jimple.v().newAddExpr(resultVar, operand)
                1 -> Jimple.v().newSubExpr(resultVar, operand)
                2 -> Jimple.v().newMulExpr(resultVar, operand)
                else -> {
                    if (operand.value == 0) {
                        Jimple.v().newAddExpr(resultVar, IntConstant.v(1))
                    } else {
                        Jimple.v().newDivExpr(resultVar, operand)
                    }
                }
            }

            val nextAssign = Jimple.v().newAssignStmt(resultVar, expr)
            units.insertAfter(nextAssign, lastStmt)
            lastStmt = nextAssign
        }
    }

    /**
     * Вставляет вычисление внутри условной конструкции
     */
    private fun insertConditionedComputation(body: Body, localGen: DefaultLocalGenerator, target: soot.Unit) {
        val condVar = localGen.generateLocal(IntType.v())
        val resultVar = localGen.generateLocal(IntType.v())

        val units = body.units

        val initCondition = Jimple.v().newAssignStmt(condVar, IntConstant.v(Random().nextInt(3)))
        val initResult = Jimple.v().newAssignStmt(resultVar, IntConstant.v(0))

        units.insertBefore(initCondition, target)
        units.insertBefore(initResult, target)

        val ifTrue = Jimple.v().newNopStmt()
        val ifEnd = Jimple.v().newNopStmt()

        // Создаем условие: if (condVar != 0) goto ifTrue
        val condition = Jimple.v().newIfStmt(
            Jimple.v().newNeExpr(condVar, IntConstant.v(0)),
            ifTrue
        )

        val elseBranch = Jimple.v().newAssignStmt(
            resultVar,
            generateRandomArithmeticExpr()
        )

        val gotoEnd = Jimple.v().newGotoStmt(ifEnd)

        val thenBranch = Jimple.v().newAssignStmt(
            resultVar,
            generateRandomArithmeticExpr()
        )

        units.insertBefore(condition, target)
        units.insertBefore(elseBranch, target)
        units.insertBefore(gotoEnd, target)
        units.insertBefore(ifTrue, target)
        units.insertBefore(thenBranch, target)
        units.insertBefore(ifEnd, target)
    }

    /**
     * Вставляет простую строковую операцию (конкатенация, интернирование)
     */
    private fun insertSimpleStringOperation(body: Body, localGen: DefaultLocalGenerator, target: soot.Unit) {
        val resultVar = localGen.generateLocal(RefType.v("java.lang.String"))
        val units = body.units

        // Получаем константные строки
        val str1 = StringConstant.v(generateRandomString(5))
        val str2 = StringConstant.v(generateRandomString(5))

        // Вариант 1: Простая конкатенация строк
        if (Random().nextBoolean()) {
            val strVar1 = localGen.generateLocal(RefType.v("java.lang.String"))
            val assignStr1 = Jimple.v().newAssignStmt(strVar1, str1)
            units.insertBefore(assignStr1, target)

            val concatExpr = Jimple.v().newVirtualInvokeExpr(
                strVar1,
                Scene.v().getMethod("<java.lang.String: java.lang.String concat(java.lang.String)>").makeRef(),
                listOf(str2)
            )
            val assign = Jimple.v().newAssignStmt(resultVar, concatExpr)
            units.insertBefore(assign, target)
        }
        // Вариант 2: Интернирование строки
        else {
            val assignConst = Jimple.v().newAssignStmt(resultVar, str1)

            val internExpr = Jimple.v().newVirtualInvokeExpr(
                resultVar,
                Scene.v().getMethod("<java.lang.String: java.lang.String intern()>").makeRef(),
                emptyList()
            )
            val assignIntern = Jimple.v().newAssignStmt(resultVar, internExpr)

            units.insertBefore(assignConst, target)
            units.insertBefore(assignIntern, target)
        }
    }

    /**
     * Вставляет сложную строковую операцию (StringBuilder, подстроки, замены)
     */
    private fun insertComplexStringOperation(body: Body, localGen: DefaultLocalGenerator, target: soot.Unit) {
        val resultVar = localGen.generateLocal(RefType.v("java.lang.String"))
        val sbVar = localGen.generateLocal(RefType.v("java.lang.StringBuilder"))
        val units = body.units

        val operationType = Random().nextInt(3)

        when (operationType) {
            // Операция со StringBuilder
            0 -> {
                val newSB = Jimple.v().newNewExpr(RefType.v("java.lang.StringBuilder"))
                val assignSB = Jimple.v().newAssignStmt(sbVar, newSB)

                val initSB = Jimple.v().newSpecialInvokeExpr(
                    sbVar,
                    Scene.v().getMethod("<java.lang.StringBuilder: void <init>()>").makeRef(),
                    emptyList()
                )
                val initStmt = Jimple.v().newInvokeStmt(initSB)

                val str1 = StringConstant.v(generateRandomString(4))
                val str2 = StringConstant.v(generateRandomString(4))
                val str3 = StringConstant.v(generateRandomString(4))

                val append1 = Jimple.v().newVirtualInvokeExpr(
                    sbVar,
                    Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>")
                        .makeRef(),
                    listOf(str1)
                )
                val append1Stmt = Jimple.v().newInvokeStmt(append1)

                val append2 = Jimple.v().newVirtualInvokeExpr(
                    sbVar,
                    Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>")
                        .makeRef(),
                    listOf(str2)
                )
                val append2Stmt = Jimple.v().newInvokeStmt(append2)

                val append3 = Jimple.v().newVirtualInvokeExpr(
                    sbVar,
                    Scene.v().getMethod("<java.lang.StringBuilder: java.lang.StringBuilder append(java.lang.String)>")
                        .makeRef(),
                    listOf(str3)
                )
                val append3Stmt = Jimple.v().newInvokeStmt(append3)

                val toString = Jimple.v().newVirtualInvokeExpr(
                    sbVar,
                    Scene.v().getMethod("<java.lang.StringBuilder: java.lang.String toString()>").makeRef(),
                    emptyList()
                )
                val toStringStmt = Jimple.v().newAssignStmt(resultVar, toString)

                units.insertBefore(assignSB, target)
                units.insertBefore(initStmt, target)
                units.insertBefore(append1Stmt, target)
                units.insertBefore(append2Stmt, target)
                units.insertBefore(append3Stmt, target)
                units.insertBefore(toStringStmt, target)
            }

            // Операции с подстроками
            1 -> {
                val originalStr = StringConstant.v(generateRandomString(10))
                val assignStr = Jimple.v().newAssignStmt(resultVar, originalStr)

                val startIdx = IntConstant.v(Random().nextInt(3))
                val endIdx = IntConstant.v(Random().nextInt(10) + 4)

                val substring = Jimple.v().newVirtualInvokeExpr(
                    resultVar,
                    Scene.v().getMethod("<java.lang.String: java.lang.String substring(int,int)>").makeRef(),
                    listOf(startIdx, endIdx)
                )
                val substringStmt = Jimple.v().newAssignStmt(resultVar, substring)

                units.insertBefore(assignStr, target)
                units.insertBefore(substringStmt, target)
            }

            // Операции с заменой символов
            2 -> {
                val originalStr = StringConstant.v(generateRandomString(8))
                val assignStr = Jimple.v().newAssignStmt(resultVar, originalStr)

                val oldChar = IntConstant.v('a'.code)
                val newChar = IntConstant.v('z'.code)

                val replace = Jimple.v().newVirtualInvokeExpr(
                    resultVar,
                    Scene.v().getMethod("<java.lang.String: java.lang.String replace(char,char)>").makeRef(),
                    listOf(oldChar, newChar)
                )
                val replaceStmt = Jimple.v().newAssignStmt(resultVar, replace)

                units.insertBefore(assignStr, target)
                units.insertBefore(replaceStmt, target)
            }
        }
    }

    /**
     * Генерирует случайное арифметическое выражение с константами
     */
    private fun generateRandomArithmeticExpr(): Value {
        val v1 = IntConstant.v(Random().nextInt(100) + 1)
        val v2 = IntConstant.v(Random().nextInt(100) + 1)

        return when (Random().nextInt(4)) {
            0 -> Jimple.v().newAddExpr(v1, v2)
            1 -> Jimple.v().newSubExpr(v1, v2)
            2 -> Jimple.v().newMulExpr(v1, v2)
            else -> {
                val divisor = if (v2.value == 0) IntConstant.v(1) else v2
                Jimple.v().newDivExpr(v1, divisor)
            }
        }
    }

    /**
     * Генерирует случайную строку указанной длины
     */
    private fun generateRandomString(length: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val random = Random()
        return (1..length)
            .map { chars[random.nextInt(chars.length)] }
            .joinToString("")
    }
}

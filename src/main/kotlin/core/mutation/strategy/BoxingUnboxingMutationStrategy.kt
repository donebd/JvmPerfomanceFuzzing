package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.*
import soot.jimple.*

/**
 * Стратегия мутации, которая добавляет ненужные операции автоупаковки/распаковки
 * примитивных типов.
 */
class BoxingUnboxingMutationStrategy(jimpleTranslator: JimpleTranslator) : MutationStrategy(jimpleTranslator) {

    // Информация о примитивных типах и их обертках
    private data class BoxingInfo(
        val wrapperClass: String,
        val primitiveType: Type,
        val unboxMethod: String
    )

    // Мапа соответствия примитивных типов информации о боксинге
    private val boxingInfoMap = mapOf(
        "int" to BoxingInfo(
            "java.lang.Integer",
            IntType.v(),
            "intValue"
        ),
        "boolean" to BoxingInfo(
            "java.lang.Boolean",
            BooleanType.v(),
            "booleanValue"
        ),
        "byte" to BoxingInfo(
            "java.lang.Byte",
            ByteType.v(),
            "byteValue"
        ),
        "char" to BoxingInfo(
            "java.lang.Character",
            CharType.v(),
            "charValue"
        ),
        "short" to BoxingInfo(
            "java.lang.Short",
            ShortType.v(),
            "shortValue"
        ),
        "long" to BoxingInfo(
            "java.lang.Long",
            LongType.v(),
            "longValue"
        ),
        "float" to BoxingInfo(
            "java.lang.Float",
            FloatType.v(),
            "floatValue"
        ),
        "double" to BoxingInfo(
            "java.lang.Double",
            DoubleType.v(),
            "doubleValue"
        )
    )

    override fun applyMutation(body: Body) {
        // Находим операторы присваивания и перемешиваем их
        val candidates = findSimpleAssignments(body).shuffled()

        if (candidates.isEmpty()) {
            return
        }

        // Определяем, сколько операторов будем мутировать (1-3, но не больше доступных)
        val maxMutations = kotlin.math.min(candidates.size, kotlin.random.Random.nextInt(1, 4))

        // Выбираем случайные операторы для мутации
        val targetsToMutate = candidates.take(maxMutations)

        // Применяем мутации к выбранным операторам
        // Создаем копию списка и мутируем в случайном порядке
        val shuffledTargets = targetsToMutate.shuffled()
        for (target in shuffledTargets) {
            applyBoxingUnboxing(body, target)
        }
    }

    /**
     * Находит простые операторы присваивания без сложной фильтрации
     */
    private fun findSimpleAssignments(body: Body): List<AssignStmt> {
        val result = mutableListOf<AssignStmt>()

        // Собираем все операторы присваивания
        val allAssignments = body.units.filterIsInstance<AssignStmt>()
            .filter { it !is IdentityStmt }
            .toList()

        // Перемешиваем их, чтобы каждый запуск мутатора обрабатывал их в другом порядке
        allAssignments.shuffled().forEach { stmt ->
            // Проверяем, что левая часть - локальная переменная
            if (stmt.leftOp is Local) {
                val leftOp = stmt.leftOp as Local

                // Проверяем только тип левой части - это должен быть примитивный тип
                if (leftOp.type is PrimType) {
                    result.add(stmt)
                }
            }
        }

        return result
    }

    /**
     * Применяет операции упаковки/распаковки к выбранному оператору
     */
    private fun applyBoxingUnboxing(body: Body, stmt: AssignStmt) {
        val leftOp = stmt.leftOp as Local
        val leftType = leftOp.type as PrimType
        val primitiveTypeName = leftType.toString()

        // Получаем информацию о боксинге для данного типа
        val boxingInfo = boxingInfoMap[primitiveTypeName] ?: run {
            return
        }

        try {
            val units = body.units

            // Создаем ссылку на тип обертки
            val wrapperType = RefType.v(boxingInfo.wrapperClass)

            // Создаем уникальные имена переменных с использованием текущего времени и случайного числа
            // Это обеспечит дополнительный недетерминизм между запусками
            val randomSuffix = kotlin.random.Random.nextInt(1000, 10000)
            val timestamp = System.nanoTime()

            // Обрабатываем правую часть присваивания
            val tempValueLocal = Jimple.v().newLocal(
                "temp_value_${timestamp}_${randomSuffix}",
                leftType
            )
            body.locals.add(tempValueLocal)

            // Создаем присваивание для временной переменной
            val tempAssignStmt = Jimple.v().newAssignStmt(tempValueLocal, stmt.rightOp)
            units.insertBefore(tempAssignStmt, stmt)

            // Создаем временную локальную переменную для хранения упакованного значения
            val boxedVar = Jimple.v().newLocal(
                "boxed_${timestamp}_${randomSuffix}",
                wrapperType
            )
            body.locals.add(boxedVar)

            // Создаем ссылку на метод valueOf вручную
            val valueOfMethodRef = Scene.v().makeMethodRef(
                Scene.v().getSootClass(boxingInfo.wrapperClass),
                "valueOf",
                listOf(leftType),
                wrapperType,
                true  // статический метод
            )

            // Создаем ссылку на метод распаковки
            val unboxMethodRef = Scene.v().makeMethodRef(
                Scene.v().getSootClass(boxingInfo.wrapperClass),
                boxingInfo.unboxMethod,
                listOf(),
                leftType,
                false  // нестатический метод
            )

            // Создаем выражения для упаковки и распаковки
            val boxingExpr = Jimple.v().newStaticInvokeExpr(valueOfMethodRef, tempValueLocal)
            val boxingStmt = Jimple.v().newAssignStmt(boxedVar, boxingExpr)

            val unboxingExpr = Jimple.v().newVirtualInvokeExpr(boxedVar, unboxMethodRef)
            val unboxingStmt = Jimple.v().newAssignStmt(leftOp, unboxingExpr)

            // Вставляем новые операторы
            units.insertBefore(boxingStmt, stmt)
            units.insertBefore(unboxingStmt, stmt)

            // Удаляем исходный оператор
            units.remove(stmt)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
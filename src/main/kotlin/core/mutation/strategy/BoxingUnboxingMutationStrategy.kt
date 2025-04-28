package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.*
import soot.jimple.AssignStmt
import soot.jimple.IdentityStmt
import soot.jimple.Jimple
import kotlin.math.min
import kotlin.random.Random

/**
 * Стратегия мутации, которая добавляет операции упаковки (boxing) и распаковки (unboxing)
 * примитивных типов в Java-байткоде. Заменяет прямые присваивания примитивных значений
 * на эквивалентную последовательность операций с использованием классов-оберток.
 */
class BoxingUnboxingMutationStrategy(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    private data class BoxingInfo(
        val wrapperClass: String,
        val primitiveType: Type,
        val unboxMethod: String
    )

    private val boxingInfoMap = mapOf(
        IntType.v() to BoxingInfo("java.lang.Integer", IntType.v(), "intValue"),
        BooleanType.v() to BoxingInfo("java.lang.Boolean", BooleanType.v(), "booleanValue"),
        ByteType.v() to BoxingInfo("java.lang.Byte", ByteType.v(), "byteValue"),
        CharType.v() to BoxingInfo("java.lang.Character", CharType.v(), "charValue"),
        ShortType.v() to BoxingInfo("java.lang.Short", ShortType.v(), "shortValue"),
        LongType.v() to BoxingInfo("java.lang.Long", LongType.v(), "longValue"),
        FloatType.v() to BoxingInfo("java.lang.Float", FloatType.v(), "floatValue"),
        DoubleType.v() to BoxingInfo("java.lang.Double", DoubleType.v(), "doubleValue")
    )

    override fun applyMutation(body: Body) {
        val candidates = findPrimitiveAssignments(body).shuffled()
        if (candidates.isEmpty()) return

        val targets = candidates.take(min(candidates.size, Random.nextInt(1, 4))).shuffled()
        targets.forEach { applyBoxingUnboxing(body, it) }
    }

    private fun findPrimitiveAssignments(body: Body): List<AssignStmt> =
        body.units
            .filterIsInstance<AssignStmt>()
            .filterNot { it is IdentityStmt }
            .filter { it.leftOp is Local && (it.leftOp.type is PrimType) }

    private fun applyBoxingUnboxing(body: Body, stmt: AssignStmt) {
        val left = stmt.leftOp as? Local ?: return
        val type = left.type as? PrimType ?: return
        val boxingInfo = boxingInfoMap[type] ?: return

        val units = body.units
        val wrapperType = RefType.v(boxingInfo.wrapperClass)

        val (tempName, boxedName) = generateUniqueNames()
        val tempLocal = Jimple.v().newLocal(tempName, type)
        val boxedLocal = Jimple.v().newLocal(boxedName, wrapperType)

        body.locals.addAll(listOf(tempLocal, boxedLocal))

        val valueOfRef = Scene.v().makeMethodRef(
            Scene.v().getSootClass(boxingInfo.wrapperClass),
            "valueOf",
            listOf(type),
            wrapperType,
            true
        )

        val unboxMethodRef = Scene.v().makeMethodRef(
            Scene.v().getSootClass(boxingInfo.wrapperClass),
            boxingInfo.unboxMethod,
            listOf(),
            type,
            false
        )

        val assignTemp = Jimple.v().newAssignStmt(tempLocal, stmt.rightOp)
        val boxStmt = Jimple.v().newAssignStmt(
            boxedLocal,
            Jimple.v().newStaticInvokeExpr(valueOfRef, tempLocal)
        )
        val unboxStmt = Jimple.v().newAssignStmt(
            left,
            Jimple.v().newVirtualInvokeExpr(boxedLocal, unboxMethodRef)
        )

        units.insertBefore(assignTemp, stmt)
        units.insertBefore(boxStmt, stmt)
        units.insertBefore(unboxStmt, stmt)
        units.remove(stmt)
    }

    private fun generateUniqueNames(): Pair<String, String> {
        val suffix = "${System.nanoTime()}_${Random.nextInt(1000, 10000)}"
        return "temp_$suffix" to "boxed_$suffix"
    }
}

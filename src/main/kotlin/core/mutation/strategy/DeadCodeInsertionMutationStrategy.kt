package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.*
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import kotlin.random.Random

class DeadCodeInsertionMutationStrategy(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    private enum class DeadCodeType {
        UNUSED_MATH,
        INVARIANT_LOOP,
        DEAD_ARRAY_ACCESS,
        DEAD_BRANCH
    }

    override fun applyMutation(body: Body) {
        val insertPoints = body.units.filterNot { it is IdentityStmt }
        val insertPoint = insertPoints.randomOrNull() ?: return

        when (DeadCodeType.entries.toTypedArray().random()) {
            DeadCodeType.UNUSED_MATH -> insertUnusedMath(body, insertPoint)
            DeadCodeType.INVARIANT_LOOP -> insertInvariantLoop(body, insertPoint)
            DeadCodeType.DEAD_ARRAY_ACCESS -> insertDeadArrayAccess(body, insertPoint)
            DeadCodeType.DEAD_BRANCH -> insertDeadBranch(body, insertPoint)
        }
    }

    private fun insertUnusedMath(body: Body, insertPoint: soot.Unit) {
        val gen = DefaultLocalGenerator(body)
        val temp1 = gen.generateLocal(IntType.v())
        val temp2 = gen.generateLocal(IntType.v())
        val temp3 = gen.generateLocal(IntType.v())

        val v1 = IntConstant.v(Random.nextInt(1, 100))
        val v2 = IntConstant.v(Random.nextInt(1, 100))

        val assign1 = Jimple.v().newAssignStmt(temp1, v1)
        val assign2 = Jimple.v().newAssignStmt(temp2, v2)
        val add = Jimple.v().newAddExpr(temp1, temp2)
        val mul = Jimple.v().newMulExpr(add, v1)
        val assign3 = Jimple.v().newAssignStmt(temp3, mul)

        body.units.insertBefore(listOf(assign1, assign2, assign3), insertPoint)
    }

    private fun insertInvariantLoop(body: Body, insertPoint: soot.Unit) {
        val gen = DefaultLocalGenerator(body)
        val loopVar = gen.generateLocal(IntType.v())
        val dummyVar = gen.generateLocal(IntType.v())
        val bound = IntConstant.v(Random.nextInt(3, 10))

        val init = Jimple.v().newAssignStmt(loopVar, IntConstant.v(0))
        val condCheck = Jimple.v().newNopStmt()
        val cond = Jimple.v().newIfStmt(Jimple.v().newGeExpr(loopVar, bound), Jimple.v().newNopStmt())
        val loopBody = Jimple.v().newAssignStmt(dummyVar, IntConstant.v(0))
        val incrementDummy = Jimple.v().newAssignStmt(dummyVar, Jimple.v().newAddExpr(dummyVar, IntConstant.v(1)))
        val incrementLoop = Jimple.v().newAssignStmt(loopVar, Jimple.v().newAddExpr(loopVar, IntConstant.v(1)))
        val jumpBack = Jimple.v().newGotoStmt(condCheck)
        val end = cond.target

        val stmts = listOf(
            loopBody, incrementDummy, incrementLoop, jumpBack
        )

        val units = body.units
        units.insertBefore(listOf(init, condCheck, cond), insertPoint)
        stmts.forEach { units.insertBefore(it, insertPoint) }
        units.insertBefore(end, insertPoint)
    }

    private fun insertDeadArrayAccess(body: Body, insertPoint: soot.Unit) {
        val gen = DefaultLocalGenerator(body)
        val array = gen.generateLocal(ArrayType.v(IntType.v(), 1))
        val temp = gen.generateLocal(IntType.v())
        val size = IntConstant.v(Random.nextInt(5, 10))

        val createArray = Jimple.v().newAssignStmt(array, Jimple.v().newNewArrayExpr(IntType.v(), size))

        val units = body.units
        units.insertBefore(createArray, insertPoint)

        repeat(3) { i ->
            val index = IntConstant.v(i)
            val value = IntConstant.v(i * 10)
            val ref = Jimple.v().newArrayRef(array, index)
            val stmt = Jimple.v().newAssignStmt(ref, value)
            units.insertBefore(stmt, insertPoint)
        }

        val read = Jimple.v().newAssignStmt(temp, Jimple.v().newArrayRef(array, IntConstant.v(1)))
        units.insertBefore(read, insertPoint)
    }

    private fun insertDeadBranch(body: Body, insertPoint: soot.Unit) {
        val gen = DefaultLocalGenerator(body)
        val condVar = gen.generateLocal(IntType.v())
        val deadVar = gen.generateLocal(IntType.v())

        val assignCond = Jimple.v().newAssignStmt(condVar, IntConstant.v(42))
        val falseCond = Jimple.v().newEqExpr(condVar, IntConstant.v(43))

        val elseLabel = Jimple.v().newNopStmt()
        val endIf = Jimple.v().newNopStmt()
        val ifStmt = Jimple.v().newIfStmt(falseCond, elseLabel)

        val deadStmt = Jimple.v().newAssignStmt(deadVar, IntConstant.v(99))
        val gotoEnd = Jimple.v().newGotoStmt(endIf)

        val units = body.units
        units.insertBefore(listOf(assignCond, ifStmt, deadStmt, gotoEnd, elseLabel, endIf), insertPoint)
    }

}

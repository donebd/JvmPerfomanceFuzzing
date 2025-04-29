package core.mutation.strategy

import core.mutation.strategy.common.MutationStrategy
import infrastructure.translator.JimpleTranslator
import soot.*
import soot.javaToJimple.DefaultLocalGenerator
import soot.jimple.*
import java.util.*

/**
 * Стратегия мутации, которая блокирует оптимизацию свертки констант в JVM,
 * используя непредсказуемые во время компиляции вычисления.
 */
class ConstantFoldingBlockerMutator(
    jimpleTranslator: JimpleTranslator
) : MutationStrategy(jimpleTranslator) {

    override fun applyMutation(body: Body) {
        val candidates = findSafeInsertionPoints(body)
        if (candidates.isEmpty()) return

        val targetStmt = candidates.random()
        val localGen = DefaultLocalGenerator(body)

        val blockingType = Random().nextInt(5)

        when (blockingType) {
            0 -> insertTimestampBlocker(body, localGen, targetStmt)
            1 -> insertConditionalBlocker(body, localGen, targetStmt)
            2 -> insertLoopBlocker(body, localGen, targetStmt)
            3 -> insertSystemPropertyBlocker(body, localGen, targetStmt)
            4 -> insertNanoTimeBlocker(body, localGen, targetStmt)
        }
    }

    private fun findSafeInsertionPoints(body: Body): List<soot.Unit> =
        body.units.filter {
            it is Stmt &&
                    it !is IdentityStmt &&
                    it !is ReturnStmt &&
                    it !is ReturnVoidStmt &&
                    it !is GotoStmt &&
                    it !is IfStmt &&
                    it !is SwitchStmt &&
                    it !is ThrowStmt
        }

    private fun insertTimestampBlocker(body: Body, localGen: DefaultLocalGenerator, targetStmt: soot.Unit) {
        val resultVar = localGen.generateLocal(IntType.v())
        val timeVar = localGen.generateLocal(LongType.v())
        val tmpVar = localGen.generateLocal(LongType.v())  // Промежуточная переменная
        val units = body.units

        val currentTimeRef = Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>").makeRef()
        val timeCall = Jimple.v().newStaticInvokeExpr(currentTimeRef, emptyList())
        val timeStmt = Jimple.v().newAssignStmt(timeVar, timeCall)

        val remainder = Jimple.v().newRemExpr(timeVar, LongConstant.v(100))
        val remainderStmt = Jimple.v().newAssignStmt(tmpVar, remainder)

        val cast = Jimple.v().newCastExpr(tmpVar, IntType.v())
        val castStmt = Jimple.v().newAssignStmt(resultVar, cast)

        val const = IntConstant.v(Random().nextInt(1000) + 1)
        val addExpr = Jimple.v().newAddExpr(resultVar, const)
        val addStmt = Jimple.v().newAssignStmt(resultVar, addExpr)

        units.insertBefore(timeStmt, targetStmt)
        units.insertBefore(remainderStmt, targetStmt)
        units.insertBefore(castStmt, targetStmt)
        units.insertBefore(addStmt, targetStmt)
    }

    private fun insertConditionalBlocker(body: Body, localGen: DefaultLocalGenerator, targetStmt: soot.Unit) {
        val resultVar = localGen.generateLocal(IntType.v())
        val condVar = localGen.generateLocal(IntType.v())
        val timeVar = localGen.generateLocal(LongType.v())
        val tmpVar = localGen.generateLocal(LongType.v())  // Промежуточная переменная
        val units = body.units

        val currentTimeRef = Scene.v().getMethod("<java.lang.System: long currentTimeMillis()>").makeRef()
        val timeCall = Jimple.v().newStaticInvokeExpr(currentTimeRef, emptyList())
        val timeStmt = Jimple.v().newAssignStmt(timeVar, timeCall)

        val andExpr = Jimple.v().newAndExpr(timeVar, LongConstant.v(1))
        val andStmt = Jimple.v().newAssignStmt(tmpVar, andExpr)

        val castExpr = Jimple.v().newCastExpr(tmpVar, IntType.v())
        val castStmt = Jimple.v().newAssignStmt(condVar, castExpr)

        val thenLabel = Jimple.v().newNopStmt()
        val elseLabel = Jimple.v().newNopStmt()
        val endIfLabel = Jimple.v().newNopStmt()

        val condition = Jimple.v().newEqExpr(condVar, IntConstant.v(0))
        val ifStmt = Jimple.v().newIfStmt(condition, elseLabel)

        val val1 = IntConstant.v(Random().nextInt(90) + 10)
        val val2 = IntConstant.v(Random().nextInt(90) + 10)

        val thenAssign = Jimple.v().newAssignStmt(resultVar, val1)
        val elseAssign = Jimple.v().newAssignStmt(resultVar, val2)

        val gotoEnd = Jimple.v().newGotoStmt(endIfLabel)

        units.insertBefore(timeStmt, targetStmt)
        units.insertBefore(andStmt, targetStmt)
        units.insertBefore(castStmt, targetStmt)
        units.insertBefore(ifStmt, targetStmt)
        units.insertBefore(thenLabel, targetStmt)
        units.insertBefore(thenAssign, targetStmt)
        units.insertBefore(gotoEnd, targetStmt)
        units.insertBefore(elseLabel, targetStmt)
        units.insertBefore(elseAssign, targetStmt)
        units.insertBefore(endIfLabel, targetStmt)
    }

    private fun insertNanoTimeBlocker(body: Body, localGen: DefaultLocalGenerator, targetStmt: soot.Unit) {
        val resultVar = localGen.generateLocal(IntType.v())
        val timeVar = localGen.generateLocal(LongType.v())
        val tmpVar = localGen.generateLocal(LongType.v())  // Промежуточная переменная
        val units = body.units

        val nanoTimeRef = Scene.v().getMethod("<java.lang.System: long nanoTime()>").makeRef()
        val timeCall = Jimple.v().newStaticInvokeExpr(nanoTimeRef, emptyList())
        val timeStmt = Jimple.v().newAssignStmt(timeVar, timeCall)

        val shiftExpr = Jimple.v().newShrExpr(timeVar, IntConstant.v(20))
        val shiftStmt = Jimple.v().newAssignStmt(timeVar, shiftExpr)

        val andExpr = Jimple.v().newAndExpr(timeVar, LongConstant.v(0xFF))
        val andStmt = Jimple.v().newAssignStmt(tmpVar, andExpr)

        val castExpr = Jimple.v().newCastExpr(tmpVar, IntType.v())
        val castStmt = Jimple.v().newAssignStmt(resultVar, castExpr)

        val mathExpr = Jimple.v().newMulExpr(resultVar, IntConstant.v(Random().nextInt(8) + 2))
        val mathStmt = Jimple.v().newAssignStmt(resultVar, mathExpr)

        units.insertBefore(timeStmt, targetStmt)
        units.insertBefore(shiftStmt, targetStmt)
        units.insertBefore(andStmt, targetStmt)
        units.insertBefore(castStmt, targetStmt)
        units.insertBefore(mathStmt, targetStmt)
    }

    private fun insertLoopBlocker(body: Body, localGen: DefaultLocalGenerator, targetStmt: soot.Unit) {
        val resultVar = localGen.generateLocal(IntType.v())
        val countVar = localGen.generateLocal(IntType.v())
        val units = body.units

        val iterations = Random().nextInt(4) + 3

        val initResult = Jimple.v().newAssignStmt(resultVar, IntConstant.v(0))
        val initCount = Jimple.v().newAssignStmt(countVar, IntConstant.v(0))

        val loopCheck = Jimple.v().newNopStmt()
        val loopBody = Jimple.v().newNopStmt()
        val loopEnd = Jimple.v().newNopStmt()

        val condition = Jimple.v().newLtExpr(countVar, IntConstant.v(iterations))
        val ifStmt = Jimple.v().newIfStmt(condition, loopBody)
        val gotoEnd = Jimple.v().newGotoStmt(loopEnd)

        val increment = Jimple.v().newAddExpr(resultVar, countVar)
        val updateResult = Jimple.v().newAssignStmt(resultVar, increment)

        val incrCount = Jimple.v().newAddExpr(countVar, IntConstant.v(1))
        val updateCount = Jimple.v().newAssignStmt(countVar, incrCount)

        val gotoCheck = Jimple.v().newGotoStmt(loopCheck)

        units.insertBefore(initResult, targetStmt)
        units.insertBefore(initCount, targetStmt)
        units.insertBefore(loopCheck, targetStmt)
        units.insertBefore(ifStmt, targetStmt)
        units.insertBefore(gotoEnd, targetStmt)
        units.insertBefore(loopBody, targetStmt)
        units.insertBefore(updateResult, targetStmt)
        units.insertBefore(updateCount, targetStmt)
        units.insertBefore(gotoCheck, targetStmt)
        units.insertBefore(loopEnd, targetStmt)
    }

    private fun insertSystemPropertyBlocker(body: Body, localGen: DefaultLocalGenerator, targetStmt: soot.Unit) {
        val resultVar = localGen.generateLocal(IntType.v())
        val tmpStrVar = localGen.generateLocal(RefType.v("java.lang.String"))
        val units = body.units

        val propNames = listOf("java.version", "os.name", "user.name", "java.vm.name")
        val propName = StringConstant.v(propNames.random())

        val getPropertyRef = Scene.v().getMethod("<java.lang.System: java.lang.String getProperty(java.lang.String)>").makeRef()
        val propertyCall = Jimple.v().newStaticInvokeExpr(getPropertyRef, listOf(propName))
        val getPropertyStmt = Jimple.v().newAssignStmt(tmpStrVar, propertyCall)

        val hashCodeRef = Scene.v().getMethod("<java.lang.String: int hashCode()>").makeRef()
        val hashCall = Jimple.v().newVirtualInvokeExpr(tmpStrVar, hashCodeRef, emptyList())
        val hashStmt = Jimple.v().newAssignStmt(resultVar, hashCall)

        val modExpr = Jimple.v().newRemExpr(resultVar, IntConstant.v(100))
        val modStmt = Jimple.v().newAssignStmt(resultVar, modExpr)

        val addExpr = Jimple.v().newAddExpr(resultVar, IntConstant.v(Random().nextInt(9) + 1))
        val addStmt = Jimple.v().newAssignStmt(resultVar, addExpr)

        units.insertBefore(getPropertyStmt, targetStmt)
        units.insertBefore(hashStmt, targetStmt)
        units.insertBefore(modStmt, targetStmt)
        units.insertBefore(addStmt, targetStmt)
    }
}

package core.mutation.strategy.common

import infrastructure.translator.JimpleTranslator
import org.objectweb.asm.ClassReader
import org.objectweb.asm.util.CheckClassAdapter
import soot.Body
import soot.SootClass
import soot.SootMethod
import java.io.PrintWriter
import java.io.StringWriter

data class MutationResult(val jimpleCode: String, val hadError: Boolean)

abstract class MutationStrategy(private val jimpleTranslator: JimpleTranslator) {

    fun apply(jimpleCode: String, className: String, packageName: String): MutationResult {
        try {
            val sootClass = jimpleTranslator.parseSootClass(jimpleCode, className, packageName)
            val method = selectRandomMethod(sootClass) ?: return MutationResult(jimpleCode, true)

            applyMutation(method.activeBody)

            val bytecode = jimpleTranslator.toBytecode(sootClass)
            if (!isValidBytecode(bytecode)) {
                return MutationResult(jimpleCode, true)
            }

            return MutationResult(
                jimpleTranslator.toJimple(bytecode, sootClass.shortName).data,
                false
            )
        } catch (e: Exception) {
            return MutationResult(jimpleCode, true)
        }
    }

    protected abstract fun applyMutation(body: Body)

    private fun selectRandomMethod(sootClass: SootClass): SootMethod? {
        return sootClass.methods
            .filter { it.hasActiveBody() && !it.name.startsWith("<") }
            .randomOrNull()
    }

    private fun isValidBytecode(bytecode: ByteArray): Boolean {
        val errorCollector = StringWriter()
        CheckClassAdapter.verify(
            ClassReader(bytecode),
            false,
            PrintWriter(errorCollector)
        )
        return errorCollector.toString().isEmpty()
    }
}

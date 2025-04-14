package infrastructure.bytecode

import java.io.File

class FileBytecodeProvider(private val filePath: String): ByteCodeProvider {

    override fun getBytecode(): ByteArray {
        return File(filePath).readBytes()
    }

}

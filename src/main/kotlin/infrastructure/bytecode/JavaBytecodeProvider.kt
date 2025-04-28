package infrastructure.bytecode

import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 * Поставщик байткода, который компилирует Java-файл в байткод.
 */
class JavaByteCodeProvider(private val javaFilePath: String) : ByteCodeProvider {

    override fun getBytecode(): ByteArray {
        val javaFile = File(javaFilePath).apply {
            if (!exists()) {
                throw IllegalArgumentException("Java файл не найден: $absolutePath")
            }
        }

        compileJavaFile(javaFile)

        val classFile = File(javaFile.parent, "${javaFile.nameWithoutExtension}.class").apply {
            if (!exists()) {
                throw IllegalStateException("Скомпилированный класс не найден: $absolutePath")
            }
        }

        return Files.readAllBytes(classFile.toPath())
    }

    private fun compileJavaFile(javaFile: File) {
        val process = ProcessBuilder("javac", javaFile.absolutePath)
            .directory(javaFile.parentFile)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw IOException("Ошибка компиляции Java файла ${javaFile.name}:\n$output")
        }
    }
}

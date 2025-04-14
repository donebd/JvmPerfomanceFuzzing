package infrastructure.bytecode

import java.io.File
import java.io.IOException
import java.nio.file.Files

class JavaByteCodeProvider(private val javaFilePath: String) : ByteCodeProvider {

    override fun getBytecode(): ByteArray {
        val javaFile = File(javaFilePath)

        if (!javaFile.exists()) {
            throw IllegalArgumentException("Файл не существует: $javaFilePath")
        }

        // 1. Компилируем Java файл в .class
        compileJavaFile(javaFile)

        // 2. Путь до скомпилированного .class файла
        val classFile = File(javaFile.parent, javaFile.nameWithoutExtension + ".class")

        if (!classFile.exists()) {
            throw IllegalStateException("Не удалось найти скомпилированный .class файл: ${classFile.absolutePath}")
        }

        // 3. Читаем байт-код из .class файла
        return Files.readAllBytes(classFile.toPath())
    }

    /**
     * Компилирует Java файл в .class с использованием javac.
     *
     * @param javaFile исходный файл .java для компиляции.
     */
    private fun compileJavaFile(javaFile: File) {
        val javacProcess = ProcessBuilder("javac", javaFile.absolutePath)
            .directory(javaFile.parentFile) // Рабочая директория для компиляции
            .start()

        // Ждем завершения процесса компиляции
        val exitCode = javacProcess.waitFor()

        if (exitCode != 0) {
            val errorMessage = javacProcess.errorStream.bufferedReader().readText()
            throw IOException("Ошибка компиляции Java файла: $errorMessage")
        }
    }
}

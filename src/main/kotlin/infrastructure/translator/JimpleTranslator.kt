package infrastructure.translator

import soot.G
import soot.Printer
import soot.Scene
import soot.SootClass
import soot.baf.BafASMBackend
import soot.options.Options
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.Path


class JimpleTranslator {

    /**
     * Преобразует JVM-байт-код в Jimple-представление.
     *
     * @param bytecode Байткод в формате [ByteArray].
     * @param className Имя класса.
     * @param packageName Имя пакета (опционально).
     * @return Объект [JimpleClass], содержащий Jimple-код и имя класса.
     */
    fun toJimple(bytecode: ByteArray, className: String, packageName: String? = "benchmark"): JimpleClass {
        val tempDir = Files.createTempDirectory("sootup_jimple").toAbsolutePath()
        try {
            saveBytecodeToTempFile(tempDir, bytecode, className, packageName)

            // Настраиваем Soot для загрузки байт-кода
            configureSoot(tempDir.toString(), true)

            // Загружаем классы из указанного директория
            val sootClass = Scene.v().loadClassAndSupport("${packageName}.$className")
            sootClass.methods.forEach { method ->
                if (method.isConcrete) {
                    method.retrieveActiveBody()
                }
            }
            val jimpleCode = extractJimpleCode(sootClass)

            return JimpleClass(jimpleCode, className)
        } finally {
            // Гарантированная очистка ресурсов
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun extractJimpleCode(sootClass: SootClass): String {
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        Printer.v().printTo(sootClass, printWriter)
        printWriter.flush()

        return writer.toString()
    }

    /**
     * Преобразует Jimple-представление обратно в JVM-байткод.
     *
     * @param jimpleCode Строковое представление Jimple.
     * @param className Имя класса.
     * @return Байткод в формате [ByteArray].
     * @throws IllegalArgumentException Если преобразование невозможно.
     */
    fun toBytecode(jimpleCode: String, className: String, packageName: String): ByteArray {
        try {
            return generateBytecode(parseSootClass(jimpleCode, className, packageName))
        } catch (e: Exception) {
            throw IllegalArgumentException("Ошибка преобразования Jimple в байт-код: ${e.message}", e)
        }
    }

    /**
     * Преобразует [SootClass] в байт-код.
     *
     * @param sootClass Объект [SootClass].
     * @return Байткод в формате [ByteArray].
     * @throws IllegalArgumentException Если преобразование невозможно.
     */
    fun toBytecode(sootClass: SootClass): ByteArray {
        try {
            return generateBytecode(sootClass)
        } catch (e: Exception) {
            throw IllegalArgumentException("Ошибка преобразования Jimple в байт-код: ${e.message}", e)
        }
    }

    /**
     * Парсит Jimple-код и создает объект [SootClass].
     *
     * @param jimpleCode Строковое представление Jimple.
     * @param className Имя класса.
     * @return Объект [SootClass].
     * @throws IllegalArgumentException Если парсинг не удался.
     */
    fun parseSootClass(jimpleCode: String, className: String, packageName: String): SootClass {
        val code = preprocessJimpleCode(jimpleCode)
        val tempDir = Files.createTempDirectory("jimple_temp")
        try {
            // Создаём временный файл с Jimple-кодом
            val cleanClassName = className.split(".").last()
            val jimpleClassName = "$packageName.$cleanClassName"
            val jimpleFile = tempDir.resolve("$jimpleClassName.jimple").toFile()
            jimpleFile.writeText(code)

            // Настраиваем Soot для загрузки Jimple
            configureSoot(tempDir.toAbsolutePath().toString(), false)

            val sootClass = Scene.v().loadClassAndSupport(jimpleClassName)
            sootClass.methods.forEach { method ->
                if (method.isConcrete) {
                    method.retrieveActiveBody()
                }
            }

            return sootClass
        } catch (e: Exception) {
            throw IllegalArgumentException("Ошибка парсинга Jimple: ${e.message}", e)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun preprocessJimpleCode(jimpleCode: String): String {
        // Замена булевых литералов на числовые эквиваленты
        return jimpleCode
            .replace(" == false", " == 0")
            .replace(" == true", " == 1")
            .replace(" != false", " != 0")
            .replace(" != true", " != 1")
    }

    /**
     * Настраивает Soot для работы с Jimple.
     */
    private fun configureSoot(classPath: String, toJimple: Boolean) {
        G.reset()
        Options.v().set_whole_program(false)
        if (toJimple) {
            Options.v().set_output_format(Options.output_format_jimple)
            Options.v().set_src_prec(Options.src_prec_class)
        } else {
            Options.v().set_output_format(Options.output_format_class)
            Options.v().set_src_prec(Options.src_prec_jimple)
        }
        Options.v().set_validate(true)
        Options.v().set_process_dir(listOf(classPath))
        Options.v().set_allow_phantom_refs(true)
        Options.v().set_keep_line_number(true)
        Options.v().set_ignore_resolving_levels(true)
        Options.v().set_prepend_classpath(true)
        Options.v().set_ignore_classpath_errors(true)

        Scene.v().loadNecessaryClasses()
    }

    /**
     * Генерирует байт-код `.class` из [SootClass].
     */
    private fun generateBytecode(sootClass: SootClass): ByteArray {
        val javaVersion = Options.v().java_version()
        val outputStream = ByteArrayOutputStream()

        outputStream.use { stream ->
            val backend = BafASMBackend(sootClass, javaVersion)
            backend.generateClassFile(stream)
            stream.flush()
            return stream.toByteArray()
        }
    }

    /**
     * Сохраняет байт-код во временный файл с учетом пакета.
     *
     * @param tempDir Временная директория для сохранения файла.
     * @param bytecode Байткод для сохранения.
     * @param className Имя класса.
     * @param packageName Имя пакета (опционально).
     * @return Путь к сохранённому файлу.
     */
    private fun saveBytecodeToTempFile(
        tempDir: Path,
        bytecode: ByteArray,
        className: String,
        packageName: String?
    ): File {
        val classFilePath = if (packageName != null) {
            val packagePath = packageName.replace('.', File.separatorChar)
            val dirPath = tempDir.resolve(packagePath)
            Files.createDirectories(dirPath)
            dirPath.resolve("$className.class")
        } else {
            tempDir.resolve("$className.class")
        }

        val classFile = classFilePath.toFile()
        classFile.writeBytes(bytecode)
        return classFile
    }
}

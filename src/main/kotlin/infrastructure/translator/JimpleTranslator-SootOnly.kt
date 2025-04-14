package infrastructure.translator
import org.apache.commons.io.FileUtils
import soot.*
import soot.jimple.*
import soot.options.Options
import soot.util.JasminOutputStream
import java.io.*
import java.nio.file.Files
import java.nio.file.Path

class `JimpleTranslator-SootOnly` {

    //    fun toJimpleSootUp(bytecode: ByteArray, className: String, packageName: String? = "benchmark"): JimpleClass {
//        val tempDir = Files.createTempDirectory("sootup_jimple").toAbsolutePath()
//        saveBytecodeToTempFile(tempDir, bytecode, className, packageName)
//
//        // Настраиваем входное место для анализа
//        val inputLocation: AnalysisInputLocation = PathBasedAnalysisInputLocation.create(tempDir, SourceType.Application)
//
//        // Создаем View для работы с классами
//        val view = JavaView(inputLocation)
//
//        val classes = view.classes
//        if (classes.isEmpty()) {
//            throw IllegalStateException("No classes found in the provided bytecode.")
//        }
//
//        val sootClass = classes.first()
//        // Генерируем Jimple-код
//        val jimpleCode = sootClass.print()
//
//        // Убираем ключевое слово "super" из Jimple-кода для дальнейшей конвертации в .class через soot
//        val cleanedJimpleCode = jimpleCode.replace("super class", "class")
//        return JimpleClass(cleanedJimpleCode, sootClass.name)
//    }

    /**
     * Преобразует JVM-байт-код в Jimple-представление.
     */
    fun toJimple(bytecode: ByteArray): String {
        val tempDir = Files.createTempDirectory("soot_jimple").toFile()
        val classFile = saveBytecodeToTempFile(tempDir.toPath(), bytecode)

        // Настройка Soot для генерации Jimple
        G.reset()
        Options.v().set_output_format(Options.output_format_jimple)
        Options.v().set_src_prec(Options.src_prec_class)
        Options.v().set_process_dir(listOf(tempDir.absolutePath))
        Options.v().set_keep_line_number(true)

        // Загружаем класс и генерируем Jimple
        Scene.v().loadNecessaryClasses()
        val sootClass = Scene.v().getSootClass(classFile.nameWithoutExtension)

        // Принудительно загружаем тела методов
        sootClass.methods.forEach { method ->
            if (method.isConcrete) {
                method.retrieveActiveBody()
            }
        }

        // Генерируем Jimple-код
        val writer = StringWriter()
        val printWriter = PrintWriter(writer)
        Printer.v().printTo(sootClass, printWriter)
        printWriter.flush()

        // Удаляем временный файл и директорию
        classFile.delete()
        tempDir.delete()

        return writer.toString()
    }

    /**
     * Преобразует Jimple-представление обратно в JVM-байткод.
     */
    fun toBytecode(jimpleCode: String, className: String): ByteArray {
        try {
            // Создаём временный файл с Jimple-кодом
            val tempDir = Files.createTempDirectory("jimple_temp")
            val jimpleFile = tempDir.resolve("$className.jimple").toFile()
            jimpleFile.writeText(jimpleCode)

            // Настраиваем Soot для загрузки Jimple
            configureSoot(tempDir.toAbsolutePath().toString())

            // Загружаем класс в Soot
            Scene.v().loadNecessaryClasses()
            val sootClass = Scene.v().getSootClass(className)
            sootClass.methods.forEach { method ->
                if (method.isConcrete) {
                    method.retrieveActiveBody()
                }
            }

            // Генерируем байткод
            return generateBytecode(sootClass)
        } catch (e: Exception) {
            throw IllegalArgumentException("Ошибка преобразования Jimple в байт-код: ${e.message}", e)
        }
    }

    /**
     * Настраивает Soot для работы с Jimple.
     */
    private fun configureSoot(classPath: String) {
        G.reset()
        Options.v().set_whole_program(true)
        Options.v().set_output_format(Options.output_format_class)
        Options.v().set_src_prec(Options.src_prec_jimple)
        Options.v().set_process_dir(listOf(classPath))
        Options.v().set_allow_phantom_refs(true)
        Options.v().set_keep_line_number(true)
    }

    /**
     * Генерирует байт-код `.class` из [SootClass].
     */
    private fun generateBytecode(sootClass: SootClass): ByteArray {
        val tempFile = File(FileUtils.getTempDirectoryPath() + File.separator + sootClass.name + ".class")
        val outputStream = JasminOutputStream(FileOutputStream(tempFile))
        val writerOut = PrintWriter(OutputStreamWriter(outputStream))
        val jasminClass = JasminClass(sootClass)

        jasminClass.print(writerOut)
        writerOut.flush()
        writerOut.close()

        return tempFile.readBytes().also { tempFile.delete() }
    }

    /**
     * Сохраняет байт-код во временный файл.
     */
    private fun saveBytecodeToTempFile(tempDir: Path, bytecode: ByteArray): File {
        val classFile = tempDir.resolve("HelloWorld.class").toFile()
        classFile.writeBytes(bytecode)
        return classFile
    }
}
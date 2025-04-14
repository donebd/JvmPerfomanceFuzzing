package infrastructure.jvm

import infrastructure.jvm.entity.JvmExecutionException
import infrastructure.jvm.entity.JvmExecutionResult
import java.io.File
import java.util.concurrent.TimeUnit

abstract class AbstractJvmExecutor(protected val jvmPath: String) : JvmExecutor {

    override fun execute(
        classPathDirectory: File,
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        executionTimeOut: Long,
        jvmOptions: List<String>
    ): JvmExecutionResult {
        // Формируем команду с параметрами
        val command = buildCommand(classPathString, mainClass, mainArgs, jvmOptions)

        return try {
            val process = ProcessBuilder(command)
                .directory(classPathDirectory)
                .redirectErrorStream(false)
                .start()

            // Асинхронное чтение вывода для избежания блокировки буферов
            val stdoutThread = startOutputReaderThread(process.inputStream)
            val stderrThread = startOutputReaderThread(process.errorStream)

            // Ждем с таймаутом
            val completed = process.waitFor(executionTimeOut, TimeUnit.SECONDS)

            // Если процесс не завершился в течение таймаута
            if (!completed) {
                // Жесткое завершение процесса (destroyForcibly более надежно, чем destroy)
                process.destroyForcibly()

                // Даем небольшое время на завершение после принудительного убийства
                process.waitFor(5, TimeUnit.SECONDS)

                stdoutThread.join(2000)
                stderrThread.join(2000)

                return JvmExecutionResult(
                    exitCode = -100, // Специальный код для таймаута
                    stdout = stdoutThread.output.toString(),
                    stderr = stderrThread.output.toString(),
                    timedOut = true
                )
            }

            // Дожидаемся завершения потоков чтения вывода
            stdoutThread.join()
            stderrThread.join()

            val exitCode = process.exitValue()

            JvmExecutionResult(
                exitCode = exitCode,
                stdout = stdoutThread.output.toString(),
                stderr = stderrThread.output.toString()
            )
        } catch (e: Exception) {
            throw JvmExecutionException("Ошибка выполнения JVM: ${e.message}", e)
        }
    }

    // Вспомогательный класс для чтения вывода в отдельном потоке
    private class OutputReader(private val inputStream: java.io.InputStream) : Thread() {
        val output = StringBuilder()

        override fun run() {
            try {
                inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        output.append(line).append("\n")
                    }
                }
            } catch (e: Exception) {
                // Игнорируем ошибки при завершении процесса
            }
        }
    }

    private fun startOutputReaderThread(inputStream: java.io.InputStream): OutputReader {
        val reader = OutputReader(inputStream)
        reader.start()
        return reader
    }

    /**
     * Формирует команду для запуска JVM.
     *
     * @param classPathString строка с полными путями для флага `-cp`.
     * @param mainClass имя класса с точкой входа.
     * @param mainArgs аргументы для запуска `main` класса.
     * @param jvmOptions дополнительные параметры JVM.
     * @return список аргументов для запуска процесса JVM.
     */
    protected abstract fun buildCommand(
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        jvmOptions: List<String>
    ): List<String>
}

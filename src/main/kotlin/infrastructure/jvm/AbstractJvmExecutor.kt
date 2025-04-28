package infrastructure.jvm

import infrastructure.jvm.entity.JvmExecutionException
import infrastructure.jvm.entity.JvmExecutionResult
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

/**
 * Базовая абстрактная реализация JvmExecutor, предоставляющая основную логику
 * запуска и контроля JVM-процесса с обработкой вывода и таймаутов.
 * Конкретные подклассы должны реализовать построение команды запуска.
 */
abstract class AbstractJvmExecutor(protected val jvmPath: String) : JvmExecutor {

    companion object {
        private const val FORCE_TERMINATION_TIMEOUT_SECONDS = 5L
        private const val OUTPUT_READER_JOIN_TIMEOUT_MS = 2000L
        private const val TIMEOUT_EXIT_CODE = -100
    }

    override fun execute(
        classPathDirectory: File,
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        executionTimeOut: Long,
        jvmOptions: List<String>
    ): JvmExecutionResult {
        val command = buildCommand(classPathString, mainClass, mainArgs, jvmOptions)

        try {
            val process = startProcess(command, classPathDirectory)
            val outputReaders = captureProcessOutput(process)

            val completed = process.waitFor(executionTimeOut, TimeUnit.SECONDS)

            return if (!completed) {
                handleProcessTimeout(process, outputReaders)
            } else {
                collectProcessResult(process, outputReaders)
            }
        } catch (e: Exception) {
            throw JvmExecutionException("Ошибка выполнения JVM: ${e.message}", e)
        }
    }

    private fun startProcess(command: List<String>, workDir: File): Process =
        ProcessBuilder(command)
            .directory(workDir)
            .redirectErrorStream(false)
            .start()

    private fun captureProcessOutput(process: Process): Pair<StreamReader, StreamReader> {
        val stdoutReader = StreamReader(process.inputStream)
        val stderrReader = StreamReader(process.errorStream)

        stdoutReader.start()
        stderrReader.start()

        return Pair(stdoutReader, stderrReader)
    }

    private fun handleProcessTimeout(
        process: Process,
        readers: Pair<StreamReader, StreamReader>
    ): JvmExecutionResult {
        process.destroyForcibly()
        process.waitFor(FORCE_TERMINATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)

        val (stdoutReader, stderrReader) = readers
        stdoutReader.join(OUTPUT_READER_JOIN_TIMEOUT_MS)
        stderrReader.join(OUTPUT_READER_JOIN_TIMEOUT_MS)

        return JvmExecutionResult(
            exitCode = TIMEOUT_EXIT_CODE,
            stdout = stdoutReader.output.toString(),
            stderr = stderrReader.output.toString(),
            timedOut = true
        )
    }

    private fun collectProcessResult(
        process: Process,
        readers: Pair<StreamReader, StreamReader>
    ): JvmExecutionResult {
        val (stdoutReader, stderrReader) = readers
        stdoutReader.join()
        stderrReader.join()

        return JvmExecutionResult(
            exitCode = process.exitValue(),
            stdout = stdoutReader.output.toString(),
            stderr = stderrReader.output.toString(),
            timedOut = false
        )
    }

    private class StreamReader(private val inputStream: InputStream) : Thread() {
        val output = StringBuilder()

        override fun run() {
            try {
                inputStream.bufferedReader().use { reader ->
                    reader.forEachLine { line ->
                        output.append(line).append("\n")
                    }
                }
            } catch (e: Exception) {
                // Исключения при закрытии потока - ожидаемое поведение
                // при принудительном завершении процесса
            }
        }
    }

    /**
     * Формирует команду для запуска JVM.
     *
     * @param classPathString строка с путями для флага `-cp`
     * @param mainClass имя класса с точкой входа
     * @param mainArgs аргументы для `main` метода
     * @param jvmOptions дополнительные параметры JVM
     * @return список аргументов для запуска процесса
     */
    protected abstract fun buildCommand(
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        jvmOptions: List<String>
    ): List<String>
}
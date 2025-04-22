package infrastructure.jvm

import infrastructure.jvm.entity.JvmExecutionResult
import java.io.File

class MockJvmExecutor : JvmExecutor {
    /**
     * Имитация запуска JVM.
     *
     * @param classPathDirectory папка с .class файлами.
     * @param classPathString строка с полными путями для флага `-cp`.
     * @param mainClass основной класс с точкой входа (например, для JMH).
     * @param mainArgs аргументы для запуска `main` класса.
     * @param jvmOptions дополнительные параметры JVM.
     * @return результат выполнения JVM.
     */
    override fun execute(
        classPathDirectory: File,
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        executionTimeOut: Long,
        jvmOptions: List<String>
    ): JvmExecutionResult {
        // Мокаем поведение JVM, просто возвращаем успешный результат
        val stdout = "JMH Benchmark Completed Successfully\n"
        val stderr = ""
        val exitCode = 0  // Симулируем успешное завершение

        // Возвращаем мок-результат
        return JvmExecutionResult(exitCode, stdout, stderr, false)
    }
}

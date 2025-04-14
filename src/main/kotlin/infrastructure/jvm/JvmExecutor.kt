package infrastructure.jvm

import infrastructure.jvm.entity.JvmExecutionResult
import java.io.File

interface JvmExecutor {
    /**
     * Запускает переданный байт-код на JVM.
     *
     * @param classPathDirectory папка с .class файлами.
     * @param classPathString строка с полными путями для флага `-cp`.
     * @param mainClass основной класс с точкой входа (например, для JMH).
     * @param mainArgs аргументы для запуска `main` класса.
     * @param jvmOptions дополнительные параметры JVM.
     * @return результат выполнения JVM.
     */
    fun execute(
        classPathDirectory: File,
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        executionTimeOut: Long,
        jvmOptions: List<String> = emptyList()
    ): JvmExecutionResult
}

package infrastructure.jit.parser

import infrastructure.jit.model.JITProfile

/**
 * Интерфейс для парсеров логов JIT-компиляции различных JVM.
 * Преобразует текстовые логи компиляции в структурированный профиль.
 */
interface JITLogParser {
    /**
     * Анализирует логи JIT-компиляции и создает структурированный профиль.
     *
     * @param stdout стандартный вывод процесса JVM, содержащий логи компиляции
     * @param stderr стандартный поток ошибок процесса JVM
     * @return структурированный профиль JIT-компиляции
     */
    fun parseCompilationLogs(stdout: String, stderr: String): JITProfile
}

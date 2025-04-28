package infrastructure.jvm

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import infrastructure.jvm.entity.JvmConfig
import java.io.File
import java.io.InputStream

/**
 * Предоставляет пути к исполняемым файлам различных JVM на основе конфигурационного файла
 * или автоматического поиска.
 */
class JvmConfigReader {

    private val objectMapper = ObjectMapper()

    fun getJvmPath(jvmType: JvmType): String {
        val config = loadConfig()
        return when (jvmType) {
            JvmType.HOT_SPOT -> validateJvmPath(config.hotSpotJvm) ?: findJvmPath("java-17-openjdk")
            JvmType.OPEN_J9 -> validateJvmPath(config.openJ9Jvm) ?: findJvmPath("ibm-semeru-17")
            JvmType.GRAAL_VM -> validateJvmPath(config.graalVmJvm) ?: findJvmPath("graalvm-ce-java17")
            JvmType.AXIOM -> validateJvmPath(config.axiomJvm) ?: findJvmPath("axiom-jvm-17")
        }
    }

    private fun loadConfig(): JvmConfig {
        val resourceStream: InputStream? = this.javaClass.getResourceAsStream("/jvm_config.json")
        if (resourceStream != null) {
            return objectMapper.readValue(resourceStream)
        } else {
            throw RuntimeException("Конфиг файл не найден в ресурсе")
        }
    }

    private fun validateJvmPath(jvmPath: String?): String? {
        if (jvmPath != null && File(jvmPath).exists()) {
            return jvmPath
        }
        return null
    }

    private fun findJvmPath(jvmPrefix: String): String {
        val jvmDir = File("/usr/lib/jvm/")

        if (jvmDir.exists() && jvmDir.isDirectory) {
            val matchingJvm = jvmDir.listFiles()
                ?.filter { it.isDirectory && it.name.startsWith(jvmPrefix) }
                ?.maxByOrNull { it.name } // Берем самую новую версию

            if (matchingJvm != null) {
                val javaBinary = File(matchingJvm, "bin/java")
                if (javaBinary.exists() && javaBinary.canExecute()) {
                    return javaBinary.absolutePath
                }
            }
        }

        throw IllegalStateException("Не удалось найти JVM с префиксом: $jvmPrefix в /usr/lib/jvm/")
    }
}

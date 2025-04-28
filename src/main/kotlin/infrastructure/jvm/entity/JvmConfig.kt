package infrastructure.jvm.entity

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Конфигурация путей к исполняемым файлам JVM.
 */
data class JvmConfig(
    @JsonProperty("HotSpotJvm") val hotSpotJvm: String? = null,
    @JsonProperty("OpenJ9Jvm") val openJ9Jvm: String? = null,
    @JsonProperty("GraalVmJvm") val graalVmJvm: String? = null,
    @JsonProperty("AxiomJvm") val axiomJvm: String? = null,
)

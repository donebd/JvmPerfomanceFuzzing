package infrastructure.jvm

/**
 * Типы поддерживаемых JVM.
 */
enum class JvmType(val key: String, val displayName: String) {
    HOT_SPOT("hotspot", "HotSpot JVM"),
    GRAAL_VM("graalvm", "GraalVM"),
    OPEN_J9("openj9", "OpenJ9"),
    AXIOM("axiom", "AxiomVM");

    companion object {
        fun fromKey(key: String) = entries.find { it.key.equals(key.trim(), ignoreCase = true) }
    }
}

fun jvmExecutorFromEnum(type: JvmType, configReader: JvmConfigReader): JvmExecutor =
    when (type) {
        JvmType.HOT_SPOT -> HotSpotJvmExecutor(configReader)
        JvmType.GRAAL_VM -> GraalVmExecutor(configReader)
        JvmType.OPEN_J9 -> OpenJ9JvmExecutor(configReader)
        JvmType.AXIOM -> AxiomJvmExecutor(configReader)
    }

package infrastructure.jvm

/**
 * Типы поддерживаемых JVM.
 */
enum class JvmType {
    HOT_SPOT,
    OPEN_J9,
    GRAAL_VM,
    AXIOM;
}

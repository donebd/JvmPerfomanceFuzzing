package core.seed

/**
 * Представляет байткод Java-класса вместе с метаданными.
 */
data class BytecodeEntry(
    val bytecode: ByteArray,
    val className: String,
    val packageName: String,
    val description: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BytecodeEntry
        return bytecode.contentEquals(other.bytecode) &&
                className == other.className &&
                packageName == other.packageName
    }

    override fun hashCode(): Int {
        var result = bytecode.contentHashCode()
        result = 31 * result + className.hashCode()
        result = 31 * result + packageName.hashCode()
        return result
    }
}
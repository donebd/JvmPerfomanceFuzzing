package infrastructure.bytecode

/**
 * Интерфейс для источников байткода Java.
 * Реализации предоставляют байткод из различных источников, таких как файлы, ресурсы или генераторы.
 */
interface ByteCodeProvider {
    fun getBytecode(): ByteArray
}

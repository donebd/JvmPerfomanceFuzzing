package core.mutation

interface Mutator {
    /**
     * Мутирует Jimple код, гарантируя валидность результирующего байт-кода.
     *
     * @param bytecode исходный байт-код.
     * @return валидный мутированный байт-код.
     */
    fun mutate(bytecode: ByteArray, className: String, packageName: String): ByteArray
}

package infrastructure.jvm

class AxiomJvmExecutor(configReader: JvmConfigReader) : AbstractJvmExecutor(configReader.getJvmPath(JvmType.AXIOM)) {

    override fun buildCommand(
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        jvmOptions: List<String>
    ): List<String> {
        return listOf(jvmPath) + jvmOptions + listOf("-cp", classPathString) + listOf(mainClass) + mainArgs
    }
}

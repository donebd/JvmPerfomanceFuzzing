package infrastructure.jvm

class GraalVmExecutor(configReader: JvmConfigReader) : AbstractJvmExecutor(configReader.getJvmPath(JvmType.GRAAL_VM)) {

    override fun buildCommand(
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        jvmOptions: List<String>
    ): List<String> {
        return listOf(jvmPath) + jvmOptions + listOf("-cp", classPathString) + listOf(mainClass) + mainArgs
    }
}

package infrastructure.jvm

class OpenJ9JvmExecutor(configReader: JvmConfigReader) : AbstractJvmExecutor(configReader.getJvmPath(JvmType.OPEN_J9)) {

    override fun buildCommand(
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        jvmOptions: List<String>
    ): List<String> {
        return listOf(jvmPath) + jvmOptions + listOf("-cp", classPathString) + listOf(mainClass) + mainArgs
    }
}

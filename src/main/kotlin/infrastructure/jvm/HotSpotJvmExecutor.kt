package infrastructure.jvm

class HotSpotJvmExecutor(configReader: JvmConfigReader) : AbstractJvmExecutor(configReader.getJvmPath(JvmType.HOT_SPOT)) {

    override fun buildCommand(
        classPathString: String,
        mainClass: String,
        mainArgs: List<String>,
        jvmOptions: List<String>
    ): List<String> {
        return listOf(jvmPath) + jvmOptions + listOf("-cp", classPathString) + listOf(mainClass) + mainArgs
    }
}

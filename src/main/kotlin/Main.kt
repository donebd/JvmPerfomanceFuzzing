import core.EvolutionaryFuzzer
import core.mutation.AdaptiveMutator
import core.seed.BytecodeEntry
import core.seed.EnergySeedManager
import infrastructure.bytecode.JavaByteCodeProvider
import infrastructure.jit.JITAnalyzer
import infrastructure.jvm.*
import infrastructure.launch.MutationStrategyType
import infrastructure.launch.mutationStrategyFromEnum
import infrastructure.performance.PerformanceAnalyzerImpl
import infrastructure.performance.PerformanceMeasurerImpl
import infrastructure.performance.anomaly.FileAnomalyRepository
import infrastructure.performance.verify.DetailedMeasurementAnomalyVerifier
import infrastructure.translator.JimpleTranslator
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import java.io.File

fun main(args: Array<String>) {
    val parser = ArgParser("jvm-perf-fuzzer")

    val jvmKeys by parser.option(
        ArgType.String, shortName = "j", fullName = "jvms",
        description = "Список JVM через запятую: ${JvmType.entries.joinToString(",") { it.key }}"
    ).default("")

    val defaultSeedsDir = "src/test/resources/InitialSeedExamples"
    val seedsDir by parser.option(
        ArgType.String, shortName = "s", fullName = "seedsDir", description = "Путь к директории с сидами"
    ).default(defaultSeedsDir)

    val iterations by parser.option(
        ArgType.Int, shortName = "n", description = "Максимальное число итераций"
    ).default(1000)

    val mutationStrategies by parser.option(
        ArgType.String,
        shortName = "m",
        fullName = "mutation-strategies",
        description = "Стратегии мутаций через запятую: ${MutationStrategyType.entries.joinToString(",") { it.name }}"
    ).default(MutationStrategyType.entries.joinToString(",") { it.name })

    val enableJitAnalysis by parser.option(
        ArgType.Boolean, fullName = "enable-jit-analysis", description = "Включить анализ JIT-логов"
    ).default(false)

    val anomalyDir by parser.option(
        ArgType.String, shortName = "a", fullName = "anomaliesDir", description = "Папка для сохранения аномалий"
    ).default("anomalies")

    parser.parse(args)

    println("==== Запуск JVM Performance Fuzzer ====")
    println("Используемые JVM: ${if (jvmKeys.isBlank()) "Дефолтные" else jvmKeys}")
    println("Директория сидов: $seedsDir")
    println("Максимальное число итераций: $iterations")
    println("Стратегии мутаций: $mutationStrategies")
    println("Директория аномалий: $anomalyDir")
    println("Анализ JIT-логов: $enableJitAnalysis")
    println("========================================")

    val packageName = "benchmark"
    val classNames: List<String> = if (seedsDir != defaultSeedsDir) {
        File(seedsDir)
            .listFiles { file -> file.isFile && file.extension == "java" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: error("Не удалось найти .java файлы в $seedsDir")
    } else {
        listOf(
            "ArrayManipulationTest",
            "BitOperations",
            "Boxing",
            "BubbleSort",
            "CollectionBenchmark",
            "CollectionsProcessor",
            "ConditionalExpressionTest",
            // "ExceptionHandlingExample",
            "ExceptionHandlingPatterns",
            "FloatingPointOperations",
            "LambdaAndStreams",
            // "MergeSort",
            "MathOperations",
            "MethodInliningTest",
            "MatrixMultiplier",
            // "PositiveNegative",
            "PrimeChecker",
            // "RecursiveFibonacci",
            "StringProcessor",
            "SwitchPatternTest",
        )
    }

    // ------ JVM executors ------
    val configReader = JvmConfigReader()
    val jvmExecutors = if (jvmKeys.isNotBlank()) {
        jvmKeys
            .split(",")
            .mapNotNull { JvmType.fromKey(it) }
            .map { jvmExecutorFromEnum(it, configReader) }
    } else {
        listOf(
            HotSpotJvmExecutor(configReader),
            GraalVmExecutor(configReader),
            OpenJ9JvmExecutor(configReader)
        )
    }

    // ------ Seeds ------
    val initialPool = classNames.map { className ->
        val byteCode = JavaByteCodeProvider("$seedsDir/$className.java").getBytecode()
        BytecodeEntry(byteCode, className, packageName)
    }

    // ------ Мутационные стратегии ------
    val jimpleTranslator = JimpleTranslator()
    val selectedStrategies = mutationStrategies
        .split(",")
        .mapNotNull { name ->
            runCatching { MutationStrategyType.valueOf(name.trim().uppercase()) }.getOrNull()
        }
        .map { mutationStrategyFromEnum(it, jimpleTranslator) }

    // Setup components
    val mutator = AdaptiveMutator(jimpleTranslator, selectedStrategies)
    val perfMeasurer = PerformanceMeasurerImpl()
    val perfAnalyzer = PerformanceAnalyzerImpl()
    val anomalyRepository = FileAnomalyRepository(anomalyDir)
    val jitLoggingOptionsProvider = if (enableJitAnalysis) JITLoggingOptionsProvider() else null
    val jitAnalyzer = if (enableJitAnalysis) JITAnalyzer(jitLoggingOptionsProvider!!) else null
    val anomalyVerifier = DetailedMeasurementAnomalyVerifier(perfMeasurer, perfAnalyzer, anomalyRepository, jitAnalyzer)

    // Create fuzzer and executors
    val fuzzer = EvolutionaryFuzzer(
        mutator,
        perfMeasurer,
        perfAnalyzer,
        EnergySeedManager(),
        anomalyVerifier,
        jitLoggingOptionsProvider,
        maxIterations = iterations
    )

    // Run fuzzer
    fuzzer.fuzz(initialPool, jvmExecutors)
}

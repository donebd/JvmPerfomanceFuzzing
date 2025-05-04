import core.EvolutionaryFuzzer
import core.mutation.AdaptiveMutator
import core.mutation.strategy.*
import core.seed.BytecodeEntry
import core.seed.EnergySeedManager
import infrastructure.bytecode.JavaByteCodeProvider
import infrastructure.jit.JITAnalyzer
import infrastructure.jvm.*
import infrastructure.performance.PerformanceAnalyzerImpl
import infrastructure.performance.PerformanceMeasurerImpl
import infrastructure.performance.anomaly.FileAnomalyRepository
import infrastructure.performance.verify.DetailedMeasurementAnomalyVerifier
import infrastructure.translator.JimpleTranslator

fun main() {
    val packageName = "benchmark"
    val classNames = listOf(
        "ArrayManipulationTest",
        "BitOperations",
        "Boxing",
        "BubbleSort",
        "CollectionBenchmark",
        "CollectionsProcessor",
        "ConditionalExpressionTest",
//        "ExceptionHandlingExample",
        "ExceptionHandlingPatterns",
        "FloatingPointOperations",
        "LambdaAndStreams",
//        "MergeSort",
        "MathOperations",
        "MethodInliningTest",
        "MatrixMultiplier",
//        "PositiveNegative",
        "PrimeChecker",
//        "RecursiveFibonacci",
        "StringProcessor",
        "SwitchPatternTest",
    )

    // Create bytecode entries
    val initialPool = classNames.map { className ->
        val byteCode = JavaByteCodeProvider("src/test/resources/InitialSeedExamples/$className.java").getBytecode()
        BytecodeEntry(byteCode, className, packageName)
    }

    // Setup translator and mutation strategies
    val jimpleTranslator = JimpleTranslator()
    val mutationStrategies = listOf(
        DeadCodeInsertionMutationStrategy(jimpleTranslator),
        ShuffleStatementsMutationStrategy(jimpleTranslator),
        ConditionalRandomWrapperMutationStrategy(jimpleTranslator),
        LoopWrapperMutationStrategy(jimpleTranslator),
        BoxingUnboxingMutationStrategy(jimpleTranslator),
        ExceptionHandlingMutator(jimpleTranslator),
        InvertBranchConditionMutator(jimpleTranslator),
        LookupSwitchMutator(jimpleTranslator),
        RedundantComputationMutator(jimpleTranslator),
        RedundantNullCheckMutator(jimpleTranslator),
        ConstantFoldingBlockerMutator(jimpleTranslator),
        LoopDirectionReverserMutator(jimpleTranslator)
    )

    // Setup components
    val mutator = AdaptiveMutator(jimpleTranslator, mutationStrategies)
    val perfMeasurer = PerformanceMeasurerImpl()
    val perfAnalyzer = PerformanceAnalyzerImpl()
    val anomalyRepository = FileAnomalyRepository("anomalies")
    val jitLoggingOptionsProvider = JITLoggingOptionsProvider()
    val jitAnalyzer = JITAnalyzer(jitLoggingOptionsProvider)
    val anomalyVerifier = DetailedMeasurementAnomalyVerifier(perfMeasurer, perfAnalyzer, anomalyRepository, jitAnalyzer)

    // Create fuzzer and executors
    val fuzzer = EvolutionaryFuzzer(
        mutator,
        perfMeasurer,
        perfAnalyzer,
        EnergySeedManager(),
        anomalyVerifier,
        jitLoggingOptionsProvider
    )

    val configReader = JvmConfigReader()
    val jvmExecutors = listOf(
        HotSpotJvmExecutor(configReader),
        GraalVmExecutor(configReader),
        OpenJ9JvmExecutor(configReader)
    )

    // Run fuzzer
    fuzzer.fuzz(initialPool, jvmExecutors)
}

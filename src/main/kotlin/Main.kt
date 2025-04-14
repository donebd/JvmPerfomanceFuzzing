import core.seed.BytecodeEntry
import core.EvolutionaryFuzzer
import core.mutation.AdaptiveMutator
import core.mutation.strategy.*
import core.seed.EnergySeedManager
import infrastructure.bytecode.JavaByteCodeProvider
import infrastructure.jvm.GraalVmExecutor
import infrastructure.jvm.HotSpotJvmExecutor
import infrastructure.jvm.JvmConfigReader
import infrastructure.performance.PerformanceAnalyzerImpl
import infrastructure.performance.PerformanceMeasurerImpl
import infrastructure.performance.verify.DetailedMeasurementAnomalyVerifier
import infrastructure.performance.anomaly.FileAnomalyRepository
import infrastructure.translator.JimpleTranslator

object BasicSetup {
    @JvmStatic
    fun main(args: Array<String>) {
        val packageName = "benchmark"
        val classNames = listOf(
            "MatrixMultiplier", "BubbleSort", "MathOperations",
            "Boxing", "PositiveNegative", "StringProcessor",
            "CollectionBenchmark", "PrimeChecker", "RecursiveFibonacci",
            "ExceptionHandlingExample"
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
            LookupSwitchMutator(jimpleTranslator)
        )

        // Setup components
        val mutator = AdaptiveMutator(jimpleTranslator, mutationStrategies)
        val perfMeasurer = PerformanceMeasurerImpl()
        val perfAnalyzer = PerformanceAnalyzerImpl()
        val anomalyRepository = FileAnomalyRepository("anomalies")
        val anomalyVerifier = DetailedMeasurementAnomalyVerifier(perfMeasurer, perfAnalyzer, anomalyRepository)

        // Create fuzzer and executors
        val fuzzer = EvolutionaryFuzzer(
            mutator,
            perfMeasurer,
            perfAnalyzer,
            EnergySeedManager(),
            anomalyVerifier
        )

        val configReader = JvmConfigReader()
        val jvmExecutors = listOf(
            HotSpotJvmExecutor(configReader),
            GraalVmExecutor(configReader)
        )

        // Run fuzzer
        fuzzer.fuzz(initialPool, jvmExecutors)
    }
}
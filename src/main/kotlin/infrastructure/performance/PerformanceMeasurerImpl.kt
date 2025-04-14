package infrastructure.performance

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import infrastructure.jvm.JvmExecutor
import infrastructure.jvm.entity.JvmExecutionResult
import infrastructure.performance.entity.JmhOptions
import infrastructure.performance.entity.PerformanceMetrics
import java.io.File
import javax.tools.ToolProvider

class PerformanceMeasurerImpl : PerformanceMeasurer {

    companion object {
        const val JMH_VERSION = "1.37"
        const val BENCHMARK_CLASS_NAME = "BenchmarkRunner"
    }

    override fun measure(
        executor: JvmExecutor,
        classpath: File,
        packageName: String,
        className: String,
        quickMeasurment: Boolean,
        jvmOptions: List<String>
    ): PerformanceMetrics {
        if (!classpath.exists() || !classpath.isDirectory) {
            throw IllegalArgumentException("Указанный classpath '${classpath.absolutePath}' не существует или не является директорией")
        }

        // Генерируем JMH-класс
        val benchmarkDir = File(classpath, packageName)
        val benchmarkFile = File(benchmarkDir, "$BENCHMARK_CLASS_NAME.java")
        val jsonResultFileName = "$className-${System.currentTimeMillis()}-result.json"
        val jsonResultFolder = File(classpath, "results")
        val jsonResultFile = File(jsonResultFolder, jsonResultFileName)
        val jmhConfig = if (quickMeasurment) {
            JmhOptions()
        } else {
            JmhOptions(5, 5, 5, 10)
        }

        benchmarkFile.writeText(generateJMHBenchmark(jmhConfig, className, jsonResultFile.absolutePath, packageName))
        compileBenchmarkClass(classpath, benchmarkFile)

        val jmhClasspath = getJmhClasspath()
        val fullClasspath = "${classpath.absolutePath}:$jmhClasspath"
        val relativeBenchmarkPath = "$packageName.$BENCHMARK_CLASS_NAME"
        val mainArgs = emptyList<String>()
        val jmhResult = executor.execute(classpath, fullClasspath, relativeBenchmarkPath, mainArgs, jmhConfig.executionTimeout, jvmOptions)

        // Парсим результаты JMH
        return parseJMHResults(jmhResult, jsonResultFile)
    }

    /**
     * Компиляция бенчмарка с classpath
     */
    private fun compileBenchmarkClass(classpath: File, benchmarkFile: File) {
        val compiler = ToolProvider.getSystemJavaCompiler()
        val fileManager = compiler.getStandardFileManager(null, null, null)

        val sources = listOf(benchmarkFile)
        val compilationUnits = fileManager.getJavaFileObjectsFromFiles(sources)

        val compileClasspath = "${classpath.absolutePath}:${getJmhClasspath()}"

        val options = listOf("-d", classpath.absolutePath, "-cp", compileClasspath)
        val task = compiler.getTask(null, fileManager, null, options, null, compilationUnits)

        if (!task.call()) {
            throw RuntimeException("Ошибка компиляции JMH-бенчмарка")
        }

        fileManager.close()
    }


    private fun getJmhClasspath(): String {
        val gradleCache = File(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1")
        if (!gradleCache.exists()) throw RuntimeException("Gradle cache не найден, возможно JMH не установлен")

        val jmhDependencies = listOf(
            "jmh-core-$JMH_VERSION.jar",
            "jmh-generator-annprocess-$JMH_VERSION.jar",
            "jopt-simple-5.0.4.jar",
            "commons-math3-3.6.1.jar"
        )

        val jmhJars = gradleCache.walk()
            .filter { it.isFile && jmhDependencies.any { dep -> it.name.contains(dep) } }
            .map { it.absolutePath }
            .toList()

        if (jmhJars.isEmpty()) {
            throw RuntimeException("Не найдены JMH JAR'ы версии $JMH_VERSION в Gradle кэше")
        }

        return jmhJars.joinToString(File.pathSeparator)
    }

    /**
     * Генерирует код JMH-бенчмарка.
     */
    private fun generateJMHBenchmark(jmhOptions: JmhOptions, className: String, resultFileName: String, packageName: String): String {
        return """
        package ${packageName};

        import org.openjdk.jmh.annotations.*;
        import org.openjdk.jmh.results.format.ResultFormatType;
        import org.openjdk.jmh.runner.Runner;
        import org.openjdk.jmh.runner.RunnerException;
        import org.openjdk.jmh.runner.options.Options;
        import org.openjdk.jmh.runner.options.OptionsBuilder;
        import org.openjdk.jmh.runner.options.TimeValue;
        
        import java.lang.management.ManagementFactory;
        import com.sun.management.OperatingSystemMXBean;
        import java.util.concurrent.TimeUnit;

        @BenchmarkMode(Mode.AverageTime)
        @OutputTimeUnit(TimeUnit.MILLISECONDS)
        @State(Scope.Thread)
        public class $BENCHMARK_CLASS_NAME {

            private $className instance = new $className();
            
            private OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            private long totalMemoryUsedInKB = 0;
            private int iterations = 0;

            @Benchmark
            public void benchmarkMethod() {
                instance.main(null);
            }

            @Setup(Level.Invocation)
            public void setup() {
                System.gc();
            }

            @TearDown(Level.Invocation)
            public void tearDown() {
                long usedMemory = osBean.getTotalPhysicalMemorySize() - osBean.getFreePhysicalMemorySize();
                totalMemoryUsedInKB += (usedMemory >> 10);
                iterations++;
            }
            
            @TearDown(Level.Trial)
            public void printAverageMemoryUsage() {
                long avgMemoryUsed = totalMemoryUsedInKB / iterations;
                System.out.println("AVERAGE_MEMORY_USAGE_KB: " + avgMemoryUsed);
            }

            public static void main(String[] args) throws RunnerException {
                Options opt = new OptionsBuilder()
                    .include($BENCHMARK_CLASS_NAME.class.getSimpleName())
                    .warmupIterations(${jmhOptions.warmupIterations})
                    .warmupTime(TimeValue.seconds(${jmhOptions.warmupTimeSeconds}))
                    .measurementIterations(${jmhOptions.measurementIterations})
                    .measurementTime(TimeValue.seconds(${jmhOptions.measurementTimeSeconds}))
                    .forks(${jmhOptions.forks})
                    .resultFormat(ResultFormatType.JSON)
                    .result("$resultFileName")
                    .build();

                new Runner(opt).run();
            }
        }
    """.trimIndent()
    }


    /**
     * Парсит результаты JMH и возвращает PerformanceMetrics.
     */
    private fun parseJMHResults(jmhResult: JvmExecutionResult, jsonResultFile: File): PerformanceMetrics {
        // Проверяем, завершился ли процесс по таймауту
        if (jmhResult.timedOut) {
            return PerformanceMetrics(
                avgTimeMs = -1.0,
                avgMemoryUsageKb = -1L,
                scoreError = 0.0,
                minTimeMs = 0.0,
                maxTimeMs = 0.0,
                successfullyParsed = false,
                jvmExecutionResult = jmhResult
            )
        }

        var avgTimeMs = -1.0
        var scoreError = 0.0
        var minTimeMs = 0.0
        var maxTimeMs = 0.0
        var avgMemoryUsageKb = -1L
        var successfullyParsed = false

        if (jsonResultFile.exists()) {
            try {
                val objectMapper = jacksonObjectMapper()
                val resultJson = objectMapper.readTree(jsonResultFile)

                if (!resultJson.isEmpty && resultJson.first() != null) {
                    val primaryMetric = resultJson.first()["primaryMetric"]
                    avgTimeMs = primaryMetric["score"].asDouble()
                    scoreError = primaryMetric["scoreError"].asDouble()
                    minTimeMs = primaryMetric["scorePercentiles"]["0.0"].asDouble()
                    maxTimeMs = primaryMetric["scorePercentiles"]["100.0"].asDouble()

                    val memoryUsageRegex = Regex("""AVERAGE_MEMORY_USAGE_KB: (\d+)""")
                    avgMemoryUsageKb = memoryUsageRegex.find(jmhResult.stdout)?.groupValues?.get(1)?.toLongOrNull() ?: -1L

                    successfullyParsed = true
                }
            } catch (e: Exception) {
                println("Ошибка парсинга результатов JMH: ${e.message}")
            }
        }

        if (!successfullyParsed) {
            jmhResult.exitCode = -100;
        }

        return PerformanceMetrics(
            avgTimeMs = avgTimeMs,
            avgMemoryUsageKb = avgMemoryUsageKb,
            scoreError = scoreError,
            minTimeMs = minTimeMs,
            maxTimeMs = maxTimeMs,
            successfullyParsed = successfullyParsed,
            jvmExecutionResult = jmhResult
        )
    }


}

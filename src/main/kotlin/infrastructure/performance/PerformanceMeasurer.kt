package infrastructure.performance

import infrastructure.jvm.JvmExecutor
import infrastructure.performance.entity.PerformanceMetrics
import java.io.File

interface PerformanceMeasurer {
    /**
     * Измеряет производительность переданного класса на конкретной JVM.
     *
     * @param executor объект для запуска JVM (инкапсулирует работу с конкретной JVM).
     * @param classpath директория, в которой лежит класс, который нужно измерить.
     * @param className полное имя класса, который нужно бенчмаркать (например, "com.example.MyClass").
     * @param jvmOptions дополнительные параметры JVM.
     * @return результат измерения производительности.
     */
    fun measure(executor: JvmExecutor, classpath: File, packageName: String, className: String, quickMeasurment: Boolean = true, jvmOptions: List<String> = emptyList()): PerformanceMetrics
}

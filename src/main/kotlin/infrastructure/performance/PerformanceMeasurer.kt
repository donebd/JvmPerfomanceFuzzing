package infrastructure.performance

import infrastructure.jvm.JvmExecutor
import infrastructure.performance.entity.JmhOptions
import infrastructure.performance.entity.PerformanceMetrics
import java.io.File

interface PerformanceMeasurer {

    /**
     * Измеряет производительность переданного класса на нескольких JVM за один проход.
     *
     * @param executors список объектов для запуска различных JVM.
     * @param classpath директория, в которой лежит класс, который нужно измерить.
     * @param packageName пакет класса.
     * @param className имя класса, который нужно бенчмаркать.
     * @param quickMeasurement флаг быстрого измерения (упрощенные настройки JMH).
     * @param jvmOptionsProvider функция, возвращающая параметры для конкретной JVM.
     * @return список пар (JVM-исполнитель, результат измерения).
     */
    fun measureAll(
        executors: List<JvmExecutor>,
        classpath: File,
        packageName: String,
        className: String,
        quickMeasurement: Boolean,
        jvmOptionsProvider: (JvmExecutor) -> List<String>
    ): List<Pair<JvmExecutor, PerformanceMetrics>>

    /**
     * Измеряет производительность переданного класса на нескольких JVM за один проход.
     *
     * @param executors список объектов для запуска различных JVM.
     * @param classpath директория, в которой лежит класс, который нужно измерить.
     * @param packageName пакет класса.
     * @param className имя класса, который нужно бенчмаркать.
     * @param jmhOptions опции прогона бенчмарка jmh
     * @param jvmOptionsProvider функция, возвращающая параметры для конкретной JVM.
     * @return список пар (JVM-исполнитель, результат измерения).
     */
    fun measureAll(
        executors: List<JvmExecutor>,
        classpath: File,
        packageName: String,
        className: String,
        jmhOptions: JmhOptions,
        jvmOptionsProvider: (JvmExecutor) -> List<String>
    ): List<Pair<JvmExecutor, PerformanceMetrics>>
}

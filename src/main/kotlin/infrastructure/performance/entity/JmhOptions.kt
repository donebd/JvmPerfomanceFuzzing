package infrastructure.performance.entity

data class JmhOptions(
    val warmupIterations: Long = 2,
    val warmupTimeSeconds: Long = 2,
    val measurementIterations: Long = 3,
    val measurementTimeSeconds: Long = 4,
    val forks: Int = 1
) {
    val executionTimeout: Long
        get(): Long {
            val jmhTime = warmupIterations * warmupTimeSeconds +
                    measurementIterations * measurementTimeSeconds;

            val timeoutTimeReserve = maxOf(10, (jmhTime * 0.25).toLong())

            return jmhTime + timeoutTimeReserve
        }
}

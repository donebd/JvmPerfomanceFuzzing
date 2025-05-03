package infrastructure.performance.entity

data class JmhOptions(
    val warmupIterations: Long = 1,
    val warmupTimeSeconds: Long = 4,
    val measurementIterations: Long = 3,
    val measurementTimeSeconds: Long = 5,
    val forks: Int = 1
) {
    val executionTimeout: Long
        get(): Long {
            val jmhTime = (warmupIterations * warmupTimeSeconds +
                    measurementIterations * measurementTimeSeconds) * forks

            val timeoutTimeReserve = maxOf(20, (jmhTime * 0.5).toLong())

            return jmhTime + timeoutTimeReserve
        }
}

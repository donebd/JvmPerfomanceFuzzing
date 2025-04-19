package infrastructure.performance.anomaly

import infrastructure.jit.model.JITComparisonResult
import infrastructure.jit.model.JITProfile

data class JITAnomalyData(
    val profiles: Map<String, JITProfile>,
    val comparisons: List<JITComparisonResult>
)

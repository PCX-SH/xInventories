package sh.pcx.xinventories.api.model

/**
 * Cache statistics.
 */
data class CacheStatistics(
    val size: Int,
    val maxSize: Int,
    val hitCount: Long,
    val missCount: Long,
    val loadCount: Long,
    val evictionCount: Long
) {
    val hitRate: Double
        get() = if (hitCount + missCount == 0L) 0.0 else hitCount.toDouble() / (hitCount + missCount)
}

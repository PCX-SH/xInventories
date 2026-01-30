package sh.pcx.xinventories.internal.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.CacheStatistics
import sh.pcx.xinventories.internal.util.Logging
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

/**
 * Tracks a rolling window of operation timings.
 */
class TimingTracker(private val windowSize: Int = 100) {
    private val timings = mutableListOf<Long>()
    private val lock = Any()

    fun record(durationMs: Long) {
        synchronized(lock) {
            timings.add(durationMs)
            if (timings.size > windowSize) {
                timings.removeAt(0)
            }
        }
    }

    fun getAverage(): Double {
        synchronized(lock) {
            if (timings.isEmpty()) return 0.0
            return timings.average()
        }
    }

    fun getMin(): Long {
        synchronized(lock) {
            return timings.minOrNull() ?: 0
        }
    }

    fun getMax(): Long {
        synchronized(lock) {
            return timings.maxOrNull() ?: 0
        }
    }

    fun getCount(): Int {
        synchronized(lock) {
            return timings.size
        }
    }
}

/**
 * Comprehensive plugin statistics.
 */
data class PluginMetrics(
    // Player stats
    val totalPlayersWithData: Int,
    val onlinePlayers: Int,

    // Storage stats
    val storageType: String,
    val storageSizeBytes: Long,
    val entryCount: Int,

    // Cache stats
    val cacheStats: CacheStatistics,

    // Performance stats
    val avgSaveTimeMs: Double,
    val avgLoadTimeMs: Double,
    val minSaveTimeMs: Long,
    val maxSaveTimeMs: Long,
    val minLoadTimeMs: Long,
    val maxLoadTimeMs: Long,

    // Operation counts
    val savesPerMinute: Double,
    val loadsPerMinute: Double,
    val totalSaves: Long,
    val totalLoads: Long,
    val totalErrors: Long,

    // Sync stats (if enabled)
    val syncEnabled: Boolean,
    val syncServerId: String?,

    // Audit stats (if enabled)
    val auditEnabled: Boolean,
    val auditEntryCount: Int,
    val auditStorageSizeBytes: Long,

    // Uptime
    val uptimeSeconds: Long
)

/**
 * Cache-specific metrics.
 */
data class CacheMetrics(
    val enabled: Boolean,
    val size: Int,
    val maxSize: Int,
    val hitCount: Long,
    val missCount: Long,
    val hitRate: Double,
    val evictionCount: Long,
    val dirtyEntries: Int,
    val writeBehindDelayMs: Long
)

/**
 * Storage-specific metrics.
 */
data class StorageMetrics(
    val type: String,
    val sizeBytes: Long,
    val sizeMB: Double,
    val entryCount: Int,
    val isHealthy: Boolean,
    val groupCount: Int,
    val playerCount: Int
)

/**
 * Performance-specific metrics.
 */
data class PerformanceMetrics(
    val avgSaveTimeMs: Double,
    val avgLoadTimeMs: Double,
    val minSaveTimeMs: Long,
    val maxSaveTimeMs: Long,
    val minLoadTimeMs: Long,
    val maxLoadTimeMs: Long,
    val savesPerMinute: Double,
    val loadsPerMinute: Double,
    val operationsPerMinute: Double,
    val totalSaves: Long,
    val totalLoads: Long,
    val totalErrors: Long,
    val errorRate: Double
)

/**
 * Service for tracking and reporting plugin metrics.
 * Provides comprehensive statistics for monitoring and debugging.
 */
class MetricsService(
    private val plugin: XInventories,
    private val scope: CoroutineScope
) {
    private val startTime = Instant.now()

    // Counters
    private val saveCount = LongAdder()
    private val loadCount = LongAdder()
    private val errorCount = LongAdder()

    // Per-minute tracking
    private val savesLastMinute = AtomicLong(0)
    private val loadsLastMinute = AtomicLong(0)
    private val lastMinuteSaves = AtomicLong(0)
    private val lastMinuteLoads = AtomicLong(0)

    // Timing trackers
    private val saveTimings = TimingTracker()
    private val loadTimings = TimingTracker()

    // Periodic job
    private var metricsJob: Job? = null

    /**
     * Initializes the metrics service.
     */
    fun initialize() {
        startPeriodicUpdates()
        Logging.debug { "MetricsService initialized" }
    }

    /**
     * Shuts down the metrics service.
     */
    fun shutdown() {
        metricsJob?.cancel()
        Logging.debug { "MetricsService shut down" }
    }

    // =========================================================================
    // Recording Methods
    // =========================================================================

    /**
     * Records a save operation.
     */
    fun recordSave(durationMs: Long) {
        saveCount.increment()
        savesLastMinute.incrementAndGet()
        saveTimings.record(durationMs)
    }

    /**
     * Records a load operation.
     */
    fun recordLoad(durationMs: Long) {
        loadCount.increment()
        loadsLastMinute.incrementAndGet()
        loadTimings.record(durationMs)
    }

    /**
     * Records an error.
     */
    fun recordError() {
        errorCount.increment()
    }

    /**
     * Times and records a save operation.
     */
    inline fun <T> timedSave(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block()
        } finally {
            recordSave(System.currentTimeMillis() - start)
        }
    }

    /**
     * Times and records a load operation.
     */
    inline fun <T> timedLoad(block: () -> T): T {
        val start = System.currentTimeMillis()
        try {
            return block()
        } finally {
            recordLoad(System.currentTimeMillis() - start)
        }
    }

    // =========================================================================
    // Query Methods
    // =========================================================================

    /**
     * Gets comprehensive plugin metrics.
     */
    suspend fun getMetrics(): PluginMetrics {
        val storageService = plugin.serviceManager.storageService
        val auditService = plugin.serviceManager.auditService
        val syncConfig = plugin.serviceManager.syncConfig

        val cacheStats = storageService.getCacheStats()
        val storageSizeBytes = storageService.getStorageSize()
        val entryCount = storageService.getEntryCount()
        val playerCount = storageService.getAllPlayerUUIDs().size

        return PluginMetrics(
            totalPlayersWithData = playerCount,
            onlinePlayers = plugin.server.onlinePlayers.size,
            storageType = storageService.storageType.name,
            storageSizeBytes = storageSizeBytes,
            entryCount = entryCount,
            cacheStats = cacheStats,
            avgSaveTimeMs = saveTimings.getAverage(),
            avgLoadTimeMs = loadTimings.getAverage(),
            minSaveTimeMs = saveTimings.getMin(),
            maxSaveTimeMs = saveTimings.getMax(),
            minLoadTimeMs = loadTimings.getMin(),
            maxLoadTimeMs = loadTimings.getMax(),
            savesPerMinute = lastMinuteSaves.get().toDouble(),
            loadsPerMinute = lastMinuteLoads.get().toDouble(),
            totalSaves = saveCount.sum(),
            totalLoads = loadCount.sum(),
            totalErrors = errorCount.sum(),
            syncEnabled = syncConfig?.enabled ?: false,
            syncServerId = syncConfig?.serverId,
            auditEnabled = auditService?.isEnabled ?: false,
            auditEntryCount = auditService?.getEntryCount() ?: 0,
            auditStorageSizeBytes = auditService?.getStorageSize() ?: 0,
            uptimeSeconds = Duration.between(startTime, Instant.now()).seconds
        )
    }

    /**
     * Gets cache-specific metrics.
     */
    fun getCacheMetrics(): CacheMetrics {
        val storageService = plugin.serviceManager.storageService
        val cache = storageService.cache
        val stats = cache.getStats()

        return CacheMetrics(
            enabled = cache.isEnabled(),
            size = stats.size,
            maxSize = stats.maxSize,
            hitCount = stats.hitCount,
            missCount = stats.missCount,
            hitRate = stats.hitRate,
            evictionCount = stats.evictionCount,
            dirtyEntries = cache.getDirtyCount(),
            writeBehindDelayMs = cache.writeBehindDelayMs
        )
    }

    /**
     * Gets storage-specific metrics.
     */
    suspend fun getStorageMetrics(): StorageMetrics {
        val storageService = plugin.serviceManager.storageService
        val groupService = plugin.serviceManager.groupService

        val sizeBytes = storageService.getStorageSize()
        val entryCount = storageService.getEntryCount()
        val playerCount = storageService.getAllPlayerUUIDs().size
        val isHealthy = storageService.isHealthy()

        return StorageMetrics(
            type = storageService.storageType.name,
            sizeBytes = sizeBytes,
            sizeMB = sizeBytes / (1024.0 * 1024.0),
            entryCount = entryCount,
            isHealthy = isHealthy,
            groupCount = groupService.getAllGroups().size,
            playerCount = playerCount
        )
    }

    /**
     * Gets performance-specific metrics.
     */
    fun getPerformanceMetrics(): PerformanceMetrics {
        val totalOps = saveCount.sum() + loadCount.sum()
        val errors = errorCount.sum()

        return PerformanceMetrics(
            avgSaveTimeMs = saveTimings.getAverage(),
            avgLoadTimeMs = loadTimings.getAverage(),
            minSaveTimeMs = saveTimings.getMin(),
            maxSaveTimeMs = saveTimings.getMax(),
            minLoadTimeMs = loadTimings.getMin(),
            maxLoadTimeMs = loadTimings.getMax(),
            savesPerMinute = lastMinuteSaves.get().toDouble(),
            loadsPerMinute = lastMinuteLoads.get().toDouble(),
            operationsPerMinute = lastMinuteSaves.get().toDouble() + lastMinuteLoads.get().toDouble(),
            totalSaves = saveCount.sum(),
            totalLoads = loadCount.sum(),
            totalErrors = errors,
            errorRate = if (totalOps > 0) errors.toDouble() / totalOps else 0.0
        )
    }

    /**
     * Gets the uptime in seconds.
     */
    fun getUptimeSeconds(): Long {
        return Duration.between(startTime, Instant.now()).seconds
    }

    /**
     * Formats uptime as a human-readable string.
     */
    fun getUptimeFormatted(): String {
        val seconds = getUptimeSeconds()
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            days > 0 -> "${days}d ${hours}h ${minutes}m"
            hours > 0 -> "${hours}h ${minutes}m ${secs}s"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }

    // =========================================================================
    // Internal Methods
    // =========================================================================

    private fun startPeriodicUpdates() {
        metricsJob = scope.launch {
            while (isActive) {
                delay(60_000) // Every minute

                // Update per-minute counters
                lastMinuteSaves.set(savesLastMinute.getAndSet(0))
                lastMinuteLoads.set(loadsLastMinute.getAndSet(0))
            }
        }
    }
}

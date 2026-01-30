package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.util.CronExpression
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing automatic expiration and cleanup of inactive player data.
 *
 * This service tracks player activity and can automatically delete data
 * for players who haven't been active for a configurable number of days.
 */
class ExpirationService(
    private val plugin: PluginContext,
    private val scope: CoroutineScope,
    private val storageService: StorageService,
    private val backupService: BackupService
) {
    private var config: ExpirationConfig = ExpirationConfig()
    private var scheduledJob: Job? = null
    private val lastActivityCache = ConcurrentHashMap<UUID, Instant>()
    private val excludedPlayers = ConcurrentHashMap.newKeySet<UUID>()

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    /**
     * Initializes the expiration service.
     */
    fun initialize() {
        loadConfig()

        if (config.enabled) {
            scheduleCleanupTask()
            Logging.info("ExpirationService initialized with ${config.inactivityDays} day threshold")
        } else {
            Logging.debug { "ExpirationService initialized (disabled)" }
        }
    }

    /**
     * Shuts down the expiration service.
     */
    fun shutdown() {
        scheduledJob?.cancel()
        scheduledJob = null
        lastActivityCache.clear()
        excludedPlayers.clear()
        Logging.debug { "ExpirationService shut down" }
    }

    /**
     * Reloads the expiration service configuration.
     */
    fun reload() {
        scheduledJob?.cancel()
        scheduledJob = null
        loadConfig()

        if (config.enabled) {
            scheduleCleanupTask()
        }

        Logging.debug { "ExpirationService reloaded" }
    }

    /**
     * Loads configuration from the plugin config.
     */
    private fun loadConfig() {
        val mainConfig = plugin.plugin.config

        config = ExpirationConfig(
            enabled = mainConfig.getBoolean("expiration.enabled", false),
            inactivityDays = mainConfig.getInt("expiration.inactivity-days", 90),
            excludePermission = mainConfig.getString("expiration.exclude-permission", "xinventories.expiration.exempt")!!,
            backupBeforeDelete = mainConfig.getBoolean("expiration.backup-before-delete", true),
            schedule = mainConfig.getString("expiration.schedule", "0 4 * * 0")!!
        )

        // Load excluded UUIDs from config
        excludedPlayers.clear()
        mainConfig.getStringList("expiration.excluded-players").forEach { uuidStr ->
            try {
                excludedPlayers.add(UUID.fromString(uuidStr))
            } catch (e: Exception) {
                Logging.warning("Invalid UUID in expiration exclusion list: $uuidStr")
            }
        }
    }

    /**
     * Records player activity.
     *
     * @param playerUuid The player's UUID
     */
    fun recordActivity(playerUuid: UUID) {
        lastActivityCache[playerUuid] = Instant.now()
    }

    /**
     * Gets the last activity time for a player.
     *
     * @param playerUuid The player's UUID
     * @return The last activity time, or null if unknown
     */
    suspend fun getLastActivity(playerUuid: UUID): Instant? {
        // Check cache first
        lastActivityCache[playerUuid]?.let { return it }

        // Check storage for last save timestamp
        val playerData = storageService.loadAllPlayerData(playerUuid)
        if (playerData.isNotEmpty()) {
            val latestTimestamp = playerData.values.maxOfOrNull { it.timestamp }
            if (latestTimestamp != null) {
                lastActivityCache[playerUuid] = latestTimestamp
                return latestTimestamp
            }
        }

        return null
    }

    /**
     * Checks if a player is excluded from expiration.
     *
     * @param playerUuid The player's UUID
     * @return true if excluded
     */
    fun isExcluded(playerUuid: UUID): Boolean {
        // Check explicit exclusion list
        if (excludedPlayers.contains(playerUuid)) {
            return true
        }

        // Check if player is online and has exempt permission
        val player = Bukkit.getPlayer(playerUuid)
        if (player != null && player.hasPermission(config.excludePermission)) {
            return true
        }

        // Check offline player permission (if available via a permission plugin)
        val offlinePlayer = Bukkit.getOfflinePlayer(playerUuid)
        if (offlinePlayer.isOp) {
            return true
        }

        return false
    }

    /**
     * Adds a player to the exclusion list.
     *
     * @param playerUuid The player's UUID
     */
    fun excludePlayer(playerUuid: UUID) {
        excludedPlayers.add(playerUuid)
        saveExcludedPlayers()
    }

    /**
     * Removes a player from the exclusion list.
     *
     * @param playerUuid The player's UUID
     */
    fun unexcludePlayer(playerUuid: UUID) {
        excludedPlayers.remove(playerUuid)
        saveExcludedPlayers()
    }

    /**
     * Gets all excluded player UUIDs.
     */
    fun getExcludedPlayers(): Set<UUID> = excludedPlayers.toSet()

    /**
     * Saves the excluded players list to config.
     */
    private fun saveExcludedPlayers() {
        plugin.plugin.config.set("expiration.excluded-players", excludedPlayers.map { it.toString() })
        plugin.plugin.saveConfig()
    }

    /**
     * Previews what would be deleted based on inactivity.
     *
     * @param days The inactivity threshold in days (uses config if null)
     * @return List of players that would be deleted with their last activity
     */
    suspend fun previewExpired(days: Int? = null): List<ExpiredPlayerInfo> {
        val threshold = Duration.ofDays((days ?: config.inactivityDays).toLong())
        val cutoff = Instant.now().minus(threshold)

        val allPlayers = storageService.getAllPlayerUUIDs()
        val expired = mutableListOf<ExpiredPlayerInfo>()

        for (playerUuid in allPlayers) {
            if (isExcluded(playerUuid)) continue

            val lastActivity = getLastActivity(playerUuid) ?: continue

            if (lastActivity.isBefore(cutoff)) {
                val groups = storageService.getPlayerGroups(playerUuid)
                val offlinePlayer = Bukkit.getOfflinePlayer(playerUuid)

                expired.add(ExpiredPlayerInfo(
                    uuid = playerUuid,
                    name = offlinePlayer.name ?: "Unknown",
                    lastActivity = lastActivity,
                    daysSinceActivity = Duration.between(lastActivity, Instant.now()).toDays(),
                    groupCount = groups.size
                ))
            }
        }

        return expired.sortedBy { it.lastActivity }
    }

    /**
     * Executes expiration cleanup.
     *
     * @param dryRun If true, only simulates the cleanup
     * @param days The inactivity threshold in days (uses config if null)
     * @return The cleanup result
     */
    suspend fun executeCleanup(dryRun: Boolean = false, days: Int? = null): ExpirationResult {
        val startTime = Instant.now()
        val expired = previewExpired(days)

        if (expired.isEmpty()) {
            return ExpirationResult(
                success = true,
                dryRun = dryRun,
                playersProcessed = 0,
                dataEntriesDeleted = 0,
                backupCreated = null,
                startTime = startTime,
                endTime = Instant.now(),
                errors = emptyList()
            )
        }

        var backupName: String? = null
        var totalDeleted = 0
        val errors = mutableListOf<String>()

        // Create backup if configured and not a dry run
        if (config.backupBeforeDelete && !dryRun) {
            try {
                val backupResult = backupService.createBackup("expiration-${System.currentTimeMillis()}")
                backupName = backupResult.getOrNull()?.name
                if (backupName != null) {
                    Logging.info("Created backup before expiration cleanup: $backupName")
                }
            } catch (e: Exception) {
                errors.add("Failed to create backup: ${e.message}")
                Logging.error("Failed to create backup before expiration cleanup", e)
            }
        }

        // Delete expired data
        if (!dryRun) {
            for (player in expired) {
                try {
                    val deleted = storageService.deleteAllPlayerData(player.uuid)
                    totalDeleted += deleted
                    lastActivityCache.remove(player.uuid)
                    Logging.debug { "Deleted ${player.groupCount} groups for expired player ${player.name} (${player.uuid})" }
                } catch (e: Exception) {
                    errors.add("Failed to delete data for ${player.uuid}: ${e.message}")
                    Logging.error("Failed to delete expired data for ${player.uuid}", e)
                }
            }
        } else {
            // For dry run, just calculate what would be deleted
            totalDeleted = expired.sumOf { it.groupCount }
        }

        return ExpirationResult(
            success = errors.isEmpty(),
            dryRun = dryRun,
            playersProcessed = expired.size,
            dataEntriesDeleted = totalDeleted,
            backupCreated = backupName,
            startTime = startTime,
            endTime = Instant.now(),
            errors = errors
        )
    }

    /**
     * Gets current expiration status.
     */
    fun getStatus(): ExpirationStatus {
        return ExpirationStatus(
            enabled = config.enabled,
            inactivityDays = config.inactivityDays,
            excludePermission = config.excludePermission,
            backupBeforeDelete = config.backupBeforeDelete,
            schedule = config.schedule,
            excludedPlayerCount = excludedPlayers.size,
            cachedActivityCount = lastActivityCache.size,
            nextScheduledRun = getNextScheduledRun()
        )
    }

    /**
     * Gets the next scheduled run time.
     */
    private fun getNextScheduledRun(): Instant? {
        if (!config.enabled || config.schedule.isBlank()) {
            return null
        }

        return try {
            val cron = CronExpression.parse(config.schedule)
            if (cron != null) {
                // Calculate next run based on schedule
                // For now, return a placeholder - the cron expression is used for matching
                Instant.now().plus(Duration.ofHours(1))
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Schedules the automatic cleanup task based on cron expression.
     */
    private fun scheduleCleanupTask() {
        if (config.schedule.isBlank()) {
            Logging.warning("Expiration schedule is empty, automatic cleanup disabled")
            return
        }

        scheduledJob = scope.launch {
            while (isActive) {
                try {
                    val nextRun = getNextScheduledRun()
                    if (nextRun == null) {
                        Logging.warning("Invalid expiration schedule: ${config.schedule}")
                        break
                    }

                    val delayMs = Duration.between(Instant.now(), nextRun).toMillis()
                    if (delayMs > 0) {
                        Logging.debug { "Next expiration cleanup scheduled for ${dateFormatter.format(nextRun)}" }
                        delay(delayMs)
                    }

                    // Execute cleanup
                    Logging.info("Running scheduled expiration cleanup...")
                    val result = executeCleanup(dryRun = false)

                    if (result.success) {
                        Logging.info("Expiration cleanup completed: ${result.playersProcessed} players, ${result.dataEntriesDeleted} entries deleted")
                    } else {
                        Logging.warning("Expiration cleanup completed with errors: ${result.errors.joinToString(", ")}")
                    }

                } catch (e: Exception) {
                    if (isActive) {
                        Logging.error("Error in expiration cleanup task", e)
                        delay(60_000) // Wait a minute before retrying
                    }
                }
            }
        }
    }
}

/**
 * Configuration for the expiration service.
 */
data class ExpirationConfig(
    /** Whether expiration is enabled */
    val enabled: Boolean = false,

    /** Number of days of inactivity before data expires */
    val inactivityDays: Int = 90,

    /** Permission that exempts players from expiration */
    val excludePermission: String = "xinventories.expiration.exempt",

    /** Whether to backup before deleting */
    val backupBeforeDelete: Boolean = true,

    /** Cron schedule for automatic cleanup */
    val schedule: String = "0 4 * * 0"
)

/**
 * Information about an expired player.
 */
data class ExpiredPlayerInfo(
    val uuid: UUID,
    val name: String,
    val lastActivity: Instant,
    val daysSinceActivity: Long,
    val groupCount: Int
)

/**
 * Result of an expiration cleanup operation.
 */
data class ExpirationResult(
    val success: Boolean,
    val dryRun: Boolean,
    val playersProcessed: Int,
    val dataEntriesDeleted: Int,
    val backupCreated: String?,
    val startTime: Instant,
    val endTime: Instant,
    val errors: List<String>
) {
    val duration: Duration get() = Duration.between(startTime, endTime)
}

/**
 * Current status of the expiration service.
 */
data class ExpirationStatus(
    val enabled: Boolean,
    val inactivityDays: Int,
    val excludePermission: String,
    val backupBeforeDelete: Boolean,
    val schedule: String,
    val excludedPlayerCount: Int,
    val cachedActivityCount: Int,
    val nextScheduledRun: Instant?
)

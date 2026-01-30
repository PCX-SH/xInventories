package sh.pcx.xinventories.internal.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.GameMode
import org.bukkit.entity.Player
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.VersionTrigger
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing inventory version history.
 * Handles creating, retrieving, restoring, and pruning versions.
 */
class VersioningService(
    private val plugin: PluginContext,
    private val scope: CoroutineScope,
    private val storageService: StorageService
) {
    // Track last version time per player to enforce minimum interval
    private val lastVersionTime = ConcurrentHashMap<UUID, Instant>()

    // Pruning job
    private var pruneJob: Job? = null

    private val config get() = plugin.configManager.mainConfig.versioning

    /**
     * Initializes the versioning service.
     */
    fun initialize() {
        if (!config.enabled) {
            Logging.info("Versioning is disabled")
            return
        }

        // Start automatic pruning job (runs daily)
        pruneJob = scope.launch {
            while (isActive) {
                delay(24 * 60 * 60 * 1000L) // 24 hours
                try {
                    pruneOldVersions()
                } catch (e: Exception) {
                    Logging.error("Error during automatic version pruning", e)
                }
            }
        }

        Logging.info("Versioning service initialized")
    }

    /**
     * Shuts down the versioning service.
     */
    fun shutdown() {
        pruneJob?.cancel()
        pruneJob = null
        lastVersionTime.clear()
    }

    /**
     * Creates a new version of a player's inventory.
     *
     * @param player The player
     * @param trigger What triggered this version creation
     * @param metadata Optional metadata to include
     * @param force If true, bypasses minimum interval check
     * @return The created version, or null if creation was skipped
     */
    suspend fun createVersion(
        player: Player,
        trigger: VersionTrigger,
        metadata: Map<String, String> = emptyMap(),
        force: Boolean = false
    ): InventoryVersion? {
        if (!config.enabled) return null

        // Check if this trigger type is enabled
        if (!force && !isTriggerEnabled(trigger)) {
            Logging.debug { "Trigger $trigger is disabled, skipping version creation" }
            return null
        }

        // Check minimum interval (unless forced or manual)
        if (!force && trigger != VersionTrigger.MANUAL) {
            val lastTime = lastVersionTime[player.uniqueId]
            if (lastTime != null) {
                val secondsSinceLastVersion = ChronoUnit.SECONDS.between(lastTime, Instant.now())
                if (secondsSinceLastVersion < config.minIntervalSeconds) {
                    Logging.debug { "Skipping version creation - only $secondsSinceLastVersion seconds since last version" }
                    return null
                }
            }
        }

        // Get current group
        val groupName = plugin.serviceManager.inventoryService.getCurrentGroup(player)
            ?: plugin.serviceManager.groupService.getGroupForWorld(player.world).name

        // Create player data snapshot
        val playerData = PlayerData.fromPlayer(player, groupName)

        // Build metadata
        val fullMetadata = mutableMapOf<String, String>()
        fullMetadata["world"] = player.world.name
        fullMetadata.putAll(metadata)

        // Create version
        val version = InventoryVersion.create(playerData, trigger, fullMetadata)

        // Save version
        val saved = saveVersion(version)
        if (!saved) {
            Logging.error("Failed to save version for ${player.name}")
            return null
        }

        // Update last version time
        lastVersionTime[player.uniqueId] = Instant.now()

        // Prune old versions for this player/group if needed
        prunePlayerVersions(player.uniqueId, groupName)

        Logging.debug { "Created version ${version.id} for ${player.name} (trigger: $trigger)" }
        return version
    }

    /**
     * Creates a version from existing player data.
     */
    suspend fun createVersionFromData(
        playerData: PlayerData,
        trigger: VersionTrigger,
        metadata: Map<String, String> = emptyMap()
    ): InventoryVersion? {
        if (!config.enabled) return null

        val version = InventoryVersion.create(playerData, trigger, metadata)

        val saved = saveVersion(version)
        if (!saved) {
            Logging.error("Failed to save version for ${playerData.uuid}")
            return null
        }

        // Prune old versions
        prunePlayerVersions(playerData.uuid, playerData.group)

        Logging.debug { "Created version ${version.id} for ${playerData.uuid} from data (trigger: $trigger)" }
        return version
    }

    /**
     * Gets versions for a player.
     *
     * @param playerUuid The player's UUID
     * @param group Optional group filter
     * @param limit Maximum number of versions to return
     * @return List of versions, ordered by timestamp descending
     */
    suspend fun getVersions(
        playerUuid: UUID,
        group: String? = null,
        limit: Int = 10
    ): List<InventoryVersion> {
        return storageService.storage.loadVersions(playerUuid, group, limit)
    }

    /**
     * Gets a specific version by ID.
     *
     * @param versionId The version ID
     * @return The version, or null if not found
     */
    suspend fun getVersion(versionId: String): InventoryVersion? {
        return storageService.storage.loadVersion(versionId)
    }

    /**
     * Restores a player's inventory from a version.
     *
     * @param player The player to restore
     * @param versionId The version ID to restore from
     * @param createBackup If true, creates a version of current inventory before restoring
     * @return Result indicating success or failure
     */
    suspend fun restoreVersion(
        player: Player,
        versionId: String,
        createBackup: Boolean = true
    ): Result<Unit> {
        val version = getVersion(versionId)
            ?: return Result.failure(IllegalArgumentException("Version not found: $versionId"))

        // Verify this version belongs to the player
        if (version.playerUuid != player.uniqueId) {
            return Result.failure(IllegalArgumentException("Version does not belong to this player"))
        }

        // Create backup of current state
        if (createBackup) {
            createVersion(
                player,
                VersionTrigger.MANUAL,
                mapOf("reason" to "backup_before_restore", "restored_version" to versionId),
                force = true
            )
        }

        // Get group settings
        val group = plugin.serviceManager.groupService.getGroup(version.group)
        val settings = group?.settings ?: sh.pcx.xinventories.api.model.GroupSettings()

        // Apply the version data to the player
        plugin.plugin.server.scheduler.runTask(plugin.plugin, Runnable {
            version.data.applyToPlayer(player, settings)
        })

        Logging.info("Restored version $versionId for ${player.name}")
        return Result.success(Unit)
    }

    /**
     * Deletes a specific version.
     *
     * @param versionId The version ID to delete
     * @return true if deleted
     */
    suspend fun deleteVersion(versionId: String): Boolean {
        return storageService.storage.deleteVersion(versionId)
    }

    /**
     * Prunes versions older than the retention period.
     *
     * @return Number of versions deleted
     */
    suspend fun pruneOldVersions(): Int {
        if (!config.enabled) return 0

        val cutoff = Instant.now().minus(config.retentionDays.toLong(), ChronoUnit.DAYS)
        val deleted = storageService.storage.pruneVersions(cutoff)

        if (deleted > 0) {
            Logging.info("Pruned $deleted old versions (older than ${config.retentionDays} days)")
        }

        return deleted
    }

    /**
     * Prunes versions for a specific player/group to stay within max limit.
     */
    private suspend fun prunePlayerVersions(playerUuid: UUID, group: String) {
        val versions = getVersions(playerUuid, group, config.maxVersionsPerPlayer + 10)

        if (versions.size > config.maxVersionsPerPlayer) {
            // Delete oldest versions beyond the limit
            val toDelete = versions.drop(config.maxVersionsPerPlayer)
            for (version in toDelete) {
                storageService.storage.deleteVersion(version.id)
            }

            Logging.debug { "Pruned ${toDelete.size} old versions for player $playerUuid in group $group" }
        }
    }

    /**
     * Checks if a trigger type is enabled in config.
     */
    private fun isTriggerEnabled(trigger: VersionTrigger): Boolean {
        return when (trigger) {
            VersionTrigger.WORLD_CHANGE -> config.triggerOn.worldChange
            VersionTrigger.DISCONNECT -> config.triggerOn.disconnect
            VersionTrigger.MANUAL -> config.triggerOn.manual
            VersionTrigger.DEATH -> true // Death versions are handled by DeathRecoveryService
            VersionTrigger.SCHEDULED -> true // Scheduled always allowed if called
        }
    }

    /**
     * Saves a version to storage.
     */
    private suspend fun saveVersion(version: InventoryVersion): Boolean {
        return storageService.storage.saveVersion(version)
    }

    /**
     * Gets the count of versions for a player.
     */
    suspend fun getVersionCount(playerUuid: UUID, group: String? = null): Int {
        val versions = getVersions(playerUuid, group, Int.MAX_VALUE)
        return versions.size
    }

    /**
     * Clears the last version time tracking for a player.
     * Called when player leaves.
     */
    fun clearPlayerTracking(playerUuid: UUID) {
        lastVersionTime.remove(playerUuid)
    }
}

package sh.pcx.xinventories.internal.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Service for managing death inventory recovery.
 * Handles capturing deaths, retrieving death records, and restoring inventories.
 */
class DeathRecoveryService(
    private val plugin: PluginContext,
    private val scope: CoroutineScope,
    private val storageService: StorageService
) {
    // Pruning job
    private var pruneJob: Job? = null

    private val config get() = plugin.configManager.mainConfig.deathRecovery

    /**
     * Initializes the death recovery service.
     */
    fun initialize() {
        if (!config.enabled) {
            Logging.info("Death recovery is disabled")
            return
        }

        // Start automatic pruning job (runs daily)
        pruneJob = scope.launch {
            while (isActive) {
                delay(24 * 60 * 60 * 1000L) // 24 hours
                try {
                    pruneOldDeathRecords()
                } catch (e: Exception) {
                    Logging.error("Error during automatic death record pruning", e)
                }
            }
        }

        Logging.info("Death recovery service initialized")
    }

    /**
     * Shuts down the death recovery service.
     */
    fun shutdown() {
        pruneJob?.cancel()
        pruneJob = null
    }

    /**
     * Captures a player's death and stores their inventory.
     * Called from the PlayerDeathEvent handler.
     *
     * @param player The player who died
     * @param damageCause The cause of the final damage
     * @param killerName Name of the killer (if any)
     * @param killerUuid UUID of the killer if it was a player
     * @return The created death record, or null if capture failed
     */
    suspend fun captureDeathInventory(
        player: Player,
        damageCause: EntityDamageEvent.DamageCause?,
        killerName: String?,
        killerUuid: UUID?
    ): DeathRecord? {
        if (!config.enabled) return null

        // Get current group
        val groupName = plugin.serviceManager.inventoryService.getCurrentGroup(player)
            ?: plugin.serviceManager.groupService.getGroupForWorld(player.world).name

        // Create player data snapshot (BEFORE death clears inventory)
        val playerData = PlayerData.fromPlayer(player, groupName)

        // Build location
        val location = player.location

        // Create death record
        val deathRecord = DeathRecord.create(
            playerData = playerData,
            location = if (config.storeLocation) location else Location(null, 0.0, 0.0, 0.0),
            deathCause = if (config.storeDeathCause) damageCause?.name else null,
            killerName = if (config.storeKiller) killerName else null,
            killerUuid = if (config.storeKiller) killerUuid else null
        )

        // Save death record
        val saved = saveDeathRecord(deathRecord)
        if (!saved) {
            Logging.error("Failed to save death record for ${player.name}")
            return null
        }

        // Prune old death records for this player
        prunePlayerDeathRecords(player.uniqueId)

        Logging.debug { "Captured death record ${deathRecord.id} for ${player.name} at ${deathRecord.getLocationString()}" }
        return deathRecord
    }

    /**
     * Captures death from existing player data (used when inventory already captured).
     */
    suspend fun captureDeathFromData(
        playerData: PlayerData,
        location: Location,
        damageCause: EntityDamageEvent.DamageCause?,
        killerName: String?,
        killerUuid: UUID?
    ): DeathRecord? {
        if (!config.enabled) return null

        val deathRecord = DeathRecord.create(
            playerData = playerData,
            location = if (config.storeLocation) location else Location(null, 0.0, 0.0, 0.0),
            deathCause = if (config.storeDeathCause) damageCause?.name else null,
            killerName = if (config.storeKiller) killerName else null,
            killerUuid = if (config.storeKiller) killerUuid else null
        )

        val saved = saveDeathRecord(deathRecord)
        if (!saved) {
            Logging.error("Failed to save death record for ${playerData.uuid}")
            return null
        }

        prunePlayerDeathRecords(playerData.uuid)

        Logging.debug { "Captured death record ${deathRecord.id} from data for ${playerData.uuid}" }
        return deathRecord
    }

    /**
     * Gets death records for a player.
     *
     * @param playerUuid The player's UUID
     * @param limit Maximum number of records to return
     * @return List of death records, ordered by timestamp descending
     */
    suspend fun getDeathRecords(playerUuid: UUID, limit: Int = 10): List<DeathRecord> {
        return storageService.storage.loadDeathRecords(playerUuid, limit)
    }

    /**
     * Gets a specific death record by ID.
     *
     * @param deathId The death record ID
     * @return The death record, or null if not found
     */
    suspend fun getDeathRecord(deathId: String): DeathRecord? {
        return storageService.storage.loadDeathRecord(deathId)
    }

    /**
     * Restores a player's inventory from a death record.
     *
     * @param player The player to restore
     * @param deathId The death record ID to restore from
     * @return Result indicating success or failure
     */
    suspend fun restoreDeathInventory(player: Player, deathId: String): Result<Unit> {
        val deathRecord = getDeathRecord(deathId)
            ?: return Result.failure(IllegalArgumentException("Death record not found: $deathId"))

        // Verify this death record belongs to the player
        if (deathRecord.playerUuid != player.uniqueId) {
            return Result.failure(IllegalArgumentException("Death record does not belong to this player"))
        }

        // Get group settings
        val group = plugin.serviceManager.groupService.getGroup(deathRecord.group)
        val settings = group?.settings ?: sh.pcx.xinventories.api.model.GroupSettings()

        // Apply the inventory data to the player
        plugin.plugin.server.scheduler.runTask(plugin.plugin, Runnable {
            deathRecord.inventoryData.applyToPlayer(player, settings)
        })

        Logging.info("Restored death inventory $deathId for ${player.name}")
        return Result.success(Unit)
    }

    /**
     * Deletes a specific death record.
     *
     * @param deathId The death record ID to delete
     * @return true if deleted
     */
    suspend fun deleteDeathRecord(deathId: String): Boolean {
        return storageService.storage.deleteDeathRecord(deathId)
    }

    /**
     * Prunes death records older than the retention period.
     *
     * @return Number of records deleted
     */
    suspend fun pruneOldDeathRecords(): Int {
        if (!config.enabled) return 0

        val cutoff = Instant.now().minus(config.retentionDays.toLong(), ChronoUnit.DAYS)
        val deleted = storageService.storage.pruneDeathRecords(cutoff)

        if (deleted > 0) {
            Logging.info("Pruned $deleted old death records (older than ${config.retentionDays} days)")
        }

        return deleted
    }

    /**
     * Prunes death records for a specific player to stay within max limit.
     */
    private suspend fun prunePlayerDeathRecords(playerUuid: UUID) {
        val records = getDeathRecords(playerUuid, config.maxDeathsPerPlayer + 10)

        if (records.size > config.maxDeathsPerPlayer) {
            // Delete oldest records beyond the limit
            val toDelete = records.drop(config.maxDeathsPerPlayer)
            for (record in toDelete) {
                storageService.storage.deleteDeathRecord(record.id)
            }

            Logging.debug { "Pruned ${toDelete.size} old death records for player $playerUuid" }
        }
    }

    /**
     * Saves a death record to storage.
     */
    private suspend fun saveDeathRecord(record: DeathRecord): Boolean {
        return storageService.storage.saveDeathRecord(record)
    }

    /**
     * Gets the count of death records for a player.
     */
    suspend fun getDeathRecordCount(playerUuid: UUID): Int {
        val records = getDeathRecords(playerUuid, Int.MAX_VALUE)
        return records.size
    }

    /**
     * Gets the most recent death record for a player.
     */
    suspend fun getMostRecentDeath(playerUuid: UUID): DeathRecord? {
        val records = getDeathRecords(playerUuid, 1)
        return records.firstOrNull()
    }
}

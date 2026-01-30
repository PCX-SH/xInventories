package sh.pcx.xinventories.internal.storage

import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import org.bukkit.GameMode
import java.time.Instant
import java.util.UUID

/**
 * Interface for all storage backends.
 * All operations are suspending functions to support async execution.
 */
interface Storage {

    /**
     * Initializes the storage backend.
     * Called once when the plugin enables.
     */
    suspend fun initialize()

    /**
     * Shuts down the storage backend.
     * Called when the plugin disables.
     */
    suspend fun shutdown()

    /**
     * Saves player data.
     *
     * @param data The player data to save
     * @return true if successful
     */
    suspend fun savePlayerData(data: PlayerData): Boolean

    /**
     * Loads player data for a specific group and gamemode.
     *
     * @param uuid Player UUID
     * @param group Group name
     * @param gameMode Optional gamemode (null = default/combined)
     * @return PlayerData or null if not found
     */
    suspend fun loadPlayerData(uuid: UUID, group: String, gameMode: GameMode?): PlayerData?

    /**
     * Loads all player data for a specific player across all groups.
     *
     * @param uuid Player UUID
     * @return Map of group name to PlayerData
     */
    suspend fun loadAllPlayerData(uuid: UUID): Map<String, PlayerData>

    /**
     * Deletes player data for a specific group and gamemode.
     *
     * @param uuid Player UUID
     * @param group Group name
     * @param gameMode Optional gamemode (null = all gamemodes in group)
     * @return true if any data was deleted
     */
    suspend fun deletePlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean

    /**
     * Deletes all player data for a player.
     *
     * @param uuid Player UUID
     * @return Number of entries deleted
     */
    suspend fun deleteAllPlayerData(uuid: UUID): Int

    /**
     * Checks if player data exists.
     *
     * @param uuid Player UUID
     * @param group Group name
     * @param gameMode Optional gamemode
     * @return true if data exists
     */
    suspend fun hasPlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean

    /**
     * Gets all unique player UUIDs in storage.
     *
     * @return Set of player UUIDs
     */
    suspend fun getAllPlayerUUIDs(): Set<UUID>

    /**
     * Gets all groups that have data for a player.
     *
     * @param uuid Player UUID
     * @return Set of group names
     */
    suspend fun getPlayerGroups(uuid: UUID): Set<String>

    /**
     * Saves multiple player data entries in a batch.
     * Default implementation calls savePlayerData for each entry.
     *
     * @param dataList List of player data to save
     * @return Number of entries successfully saved
     */
    suspend fun savePlayerDataBatch(dataList: List<PlayerData>): Int {
        var count = 0
        for (data in dataList) {
            if (savePlayerData(data)) count++
        }
        return count
    }

    /**
     * Gets the total number of stored entries.
     *
     * @return Entry count
     */
    suspend fun getEntryCount(): Int

    /**
     * Gets the storage size in bytes (approximate).
     *
     * @return Size in bytes, or -1 if unknown
     */
    suspend fun getStorageSize(): Long

    /**
     * Checks if the storage backend is healthy/connected.
     *
     * @return true if healthy
     */
    suspend fun isHealthy(): Boolean

    /**
     * Gets the name of this storage type.
     */
    val name: String

    // ═══════════════════════════════════════════════════════════════════
    // Inventory Version Storage
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Saves an inventory version.
     *
     * @param version The version to save
     * @return true if successful
     */
    suspend fun saveVersion(version: InventoryVersion): Boolean

    /**
     * Loads versions for a player.
     *
     * @param playerUuid Player UUID
     * @param group Optional group filter (null = all groups)
     * @param limit Maximum number of versions to return
     * @return List of versions, ordered by timestamp descending
     */
    suspend fun loadVersions(playerUuid: UUID, group: String?, limit: Int): List<InventoryVersion>

    /**
     * Loads a specific version by ID.
     *
     * @param versionId The version ID
     * @return The version, or null if not found
     */
    suspend fun loadVersion(versionId: String): InventoryVersion?

    /**
     * Deletes a specific version.
     *
     * @param versionId The version ID to delete
     * @return true if deleted
     */
    suspend fun deleteVersion(versionId: String): Boolean

    /**
     * Deletes all versions older than the specified timestamp.
     *
     * @param olderThan Delete versions before this timestamp
     * @return Number of versions deleted
     */
    suspend fun pruneVersions(olderThan: Instant): Int

    // ═══════════════════════════════════════════════════════════════════
    // Death Record Storage
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Saves a death record.
     *
     * @param record The death record to save
     * @return true if successful
     */
    suspend fun saveDeathRecord(record: DeathRecord): Boolean

    /**
     * Loads death records for a player.
     *
     * @param playerUuid Player UUID
     * @param limit Maximum number of records to return
     * @return List of death records, ordered by timestamp descending
     */
    suspend fun loadDeathRecords(playerUuid: UUID, limit: Int): List<DeathRecord>

    /**
     * Loads a specific death record by ID.
     *
     * @param deathId The death record ID
     * @return The death record, or null if not found
     */
    suspend fun loadDeathRecord(deathId: String): DeathRecord?

    /**
     * Deletes a specific death record.
     *
     * @param deathId The death record ID to delete
     * @return true if deleted
     */
    suspend fun deleteDeathRecord(deathId: String): Boolean

    /**
     * Deletes all death records older than the specified timestamp.
     *
     * @param olderThan Delete records before this timestamp
     * @return Number of records deleted
     */
    suspend fun pruneDeathRecords(olderThan: Instant): Int
}

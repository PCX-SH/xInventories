package sh.pcx.xinventories.internal.storage

import sh.pcx.xinventories.internal.model.PlayerData
import org.bukkit.GameMode
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
}

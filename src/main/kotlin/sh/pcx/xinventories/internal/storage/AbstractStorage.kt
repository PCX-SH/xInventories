package sh.pcx.xinventories.internal.storage

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.GameMode
import java.util.UUID

/**
 * Abstract base class for storage implementations.
 * Provides common functionality and error handling.
 */
abstract class AbstractStorage(
    protected val plugin: XInventories
) : Storage {

    protected var initialized = false

    override suspend fun initialize() {
        if (initialized) {
            Logging.warning("Storage already initialized")
            return
        }

        try {
            doInitialize()
            initialized = true
            Logging.info("$name storage initialized successfully")
        } catch (e: Exception) {
            Logging.error("Failed to initialize $name storage", e)
            throw e
        }
    }

    override suspend fun shutdown() {
        if (!initialized) return

        try {
            doShutdown()
            initialized = false
            Logging.info("$name storage shut down")
        } catch (e: Exception) {
            Logging.error("Error shutting down $name storage", e)
        }
    }

    override suspend fun savePlayerData(data: PlayerData): Boolean {
        if (!initialized) {
            Logging.warning("Cannot save: storage not initialized")
            return false
        }

        return try {
            doSavePlayerData(data)
            Logging.debug { "Saved data for ${data.uuid} in group ${data.group}" }
            true
        } catch (e: Exception) {
            Logging.error("Failed to save player data for ${data.uuid}", e)
            false
        }
    }

    override suspend fun loadPlayerData(uuid: UUID, group: String, gameMode: GameMode?): PlayerData? {
        if (!initialized) {
            Logging.warning("Cannot load: storage not initialized")
            return null
        }

        return try {
            val data = doLoadPlayerData(uuid, group, gameMode)
            Logging.debug { "Loaded data for $uuid in group $group: ${data != null}" }
            data
        } catch (e: Exception) {
            Logging.error("Failed to load player data for $uuid", e)
            null
        }
    }

    override suspend fun loadAllPlayerData(uuid: UUID): Map<String, PlayerData> {
        if (!initialized) {
            Logging.warning("Cannot load: storage not initialized")
            return emptyMap()
        }

        return try {
            val data = doLoadAllPlayerData(uuid)
            Logging.debug { "Loaded ${data.size} groups for $uuid" }
            data
        } catch (e: Exception) {
            Logging.error("Failed to load all player data for $uuid", e)
            emptyMap()
        }
    }

    override suspend fun deletePlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        if (!initialized) {
            Logging.warning("Cannot delete: storage not initialized")
            return false
        }

        return try {
            val deleted = doDeletePlayerData(uuid, group, gameMode)
            Logging.debug { "Deleted data for $uuid in group $group: $deleted" }
            deleted
        } catch (e: Exception) {
            Logging.error("Failed to delete player data for $uuid", e)
            false
        }
    }

    override suspend fun deleteAllPlayerData(uuid: UUID): Int {
        if (!initialized) {
            Logging.warning("Cannot delete: storage not initialized")
            return 0
        }

        return try {
            val count = doDeleteAllPlayerData(uuid)
            Logging.debug { "Deleted $count entries for $uuid" }
            count
        } catch (e: Exception) {
            Logging.error("Failed to delete all player data for $uuid", e)
            0
        }
    }

    override suspend fun hasPlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        if (!initialized) return false

        return try {
            doHasPlayerData(uuid, group, gameMode)
        } catch (e: Exception) {
            Logging.error("Failed to check player data for $uuid", e)
            false
        }
    }

    override suspend fun getAllPlayerUUIDs(): Set<UUID> {
        if (!initialized) return emptySet()

        return try {
            doGetAllPlayerUUIDs()
        } catch (e: Exception) {
            Logging.error("Failed to get all player UUIDs", e)
            emptySet()
        }
    }

    override suspend fun getPlayerGroups(uuid: UUID): Set<String> {
        if (!initialized) return emptySet()

        return try {
            doGetPlayerGroups(uuid)
        } catch (e: Exception) {
            Logging.error("Failed to get player groups for $uuid", e)
            emptySet()
        }
    }

    override suspend fun savePlayerDataBatch(dataList: List<PlayerData>): Int {
        if (!initialized) {
            Logging.warning("Cannot save batch: storage not initialized")
            return 0
        }

        return try {
            val count = doSavePlayerDataBatch(dataList)
            Logging.debug { "Batch saved $count/${dataList.size} entries" }
            count
        } catch (e: Exception) {
            Logging.error("Failed to save player data batch", e)
            0
        }
    }

    override suspend fun getEntryCount(): Int {
        if (!initialized) return 0

        return try {
            doGetEntryCount()
        } catch (e: Exception) {
            Logging.error("Failed to get entry count", e)
            0
        }
    }

    override suspend fun getStorageSize(): Long {
        if (!initialized) return -1

        return try {
            doGetStorageSize()
        } catch (e: Exception) {
            Logging.error("Failed to get storage size", e)
            -1
        }
    }

    override suspend fun isHealthy(): Boolean {
        if (!initialized) return false

        return try {
            doIsHealthy()
        } catch (e: Exception) {
            false
        }
    }

    // Abstract methods to be implemented by subclasses
    protected abstract suspend fun doInitialize()
    protected abstract suspend fun doShutdown()
    protected abstract suspend fun doSavePlayerData(data: PlayerData)
    protected abstract suspend fun doLoadPlayerData(uuid: UUID, group: String, gameMode: GameMode?): PlayerData?
    protected abstract suspend fun doLoadAllPlayerData(uuid: UUID): Map<String, PlayerData>
    protected abstract suspend fun doDeletePlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean
    protected abstract suspend fun doDeleteAllPlayerData(uuid: UUID): Int
    protected abstract suspend fun doHasPlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean
    protected abstract suspend fun doGetAllPlayerUUIDs(): Set<UUID>
    protected abstract suspend fun doGetPlayerGroups(uuid: UUID): Set<String>
    protected abstract suspend fun doGetEntryCount(): Int
    protected abstract suspend fun doGetStorageSize(): Long
    protected abstract suspend fun doIsHealthy(): Boolean

    protected open suspend fun doSavePlayerDataBatch(dataList: List<PlayerData>): Int {
        var count = 0
        for (data in dataList) {
            try {
                doSavePlayerData(data)
                count++
            } catch (e: Exception) {
                Logging.error("Failed to save data in batch for ${data.uuid}", e)
            }
        }
        return count
    }
}

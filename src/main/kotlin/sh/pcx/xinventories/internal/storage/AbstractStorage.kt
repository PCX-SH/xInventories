package sh.pcx.xinventories.internal.storage

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.TemporaryGroupAssignment
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.GameMode
import java.time.Instant
import java.util.UUID

/**
 * Abstract base class for storage implementations.
 * Provides common functionality and error handling.
 */
abstract class AbstractStorage(
    protected val plugin: PluginContext
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

    // ═══════════════════════════════════════════════════════════════════
    // Inventory Version Storage
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun saveVersion(version: InventoryVersion): Boolean {
        if (!initialized) {
            Logging.warning("Cannot save version: storage not initialized")
            return false
        }

        return try {
            doSaveVersion(version)
            Logging.debug { "Saved version ${version.id} for ${version.playerUuid}" }
            true
        } catch (e: Exception) {
            Logging.error("Failed to save version ${version.id}", e)
            false
        }
    }

    override suspend fun loadVersions(playerUuid: UUID, group: String?, limit: Int): List<InventoryVersion> {
        if (!initialized) {
            Logging.warning("Cannot load versions: storage not initialized")
            return emptyList()
        }

        return try {
            doLoadVersions(playerUuid, group, limit)
        } catch (e: Exception) {
            Logging.error("Failed to load versions for $playerUuid", e)
            emptyList()
        }
    }

    override suspend fun loadVersion(versionId: String): InventoryVersion? {
        if (!initialized) {
            Logging.warning("Cannot load version: storage not initialized")
            return null
        }

        return try {
            doLoadVersion(versionId)
        } catch (e: Exception) {
            Logging.error("Failed to load version $versionId", e)
            null
        }
    }

    override suspend fun deleteVersion(versionId: String): Boolean {
        if (!initialized) {
            Logging.warning("Cannot delete version: storage not initialized")
            return false
        }

        return try {
            doDeleteVersion(versionId)
        } catch (e: Exception) {
            Logging.error("Failed to delete version $versionId", e)
            false
        }
    }

    override suspend fun pruneVersions(olderThan: Instant): Int {
        if (!initialized) {
            Logging.warning("Cannot prune versions: storage not initialized")
            return 0
        }

        return try {
            doPruneVersions(olderThan)
        } catch (e: Exception) {
            Logging.error("Failed to prune versions", e)
            0
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Death Record Storage
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun saveDeathRecord(record: DeathRecord): Boolean {
        if (!initialized) {
            Logging.warning("Cannot save death record: storage not initialized")
            return false
        }

        return try {
            doSaveDeathRecord(record)
            Logging.debug { "Saved death record ${record.id} for ${record.playerUuid}" }
            true
        } catch (e: Exception) {
            Logging.error("Failed to save death record ${record.id}", e)
            false
        }
    }

    override suspend fun loadDeathRecords(playerUuid: UUID, limit: Int): List<DeathRecord> {
        if (!initialized) {
            Logging.warning("Cannot load death records: storage not initialized")
            return emptyList()
        }

        return try {
            doLoadDeathRecords(playerUuid, limit)
        } catch (e: Exception) {
            Logging.error("Failed to load death records for $playerUuid", e)
            emptyList()
        }
    }

    override suspend fun loadDeathRecord(deathId: String): DeathRecord? {
        if (!initialized) {
            Logging.warning("Cannot load death record: storage not initialized")
            return null
        }

        return try {
            doLoadDeathRecord(deathId)
        } catch (e: Exception) {
            Logging.error("Failed to load death record $deathId", e)
            null
        }
    }

    override suspend fun deleteDeathRecord(deathId: String): Boolean {
        if (!initialized) {
            Logging.warning("Cannot delete death record: storage not initialized")
            return false
        }

        return try {
            doDeleteDeathRecord(deathId)
        } catch (e: Exception) {
            Logging.error("Failed to delete death record $deathId", e)
            false
        }
    }

    override suspend fun pruneDeathRecords(olderThan: Instant): Int {
        if (!initialized) {
            Logging.warning("Cannot prune death records: storage not initialized")
            return 0
        }

        return try {
            doPruneDeathRecords(olderThan)
        } catch (e: Exception) {
            Logging.error("Failed to prune death records", e)
            0
        }
    }

    // Abstract methods for version storage
    protected abstract suspend fun doSaveVersion(version: InventoryVersion)
    protected abstract suspend fun doLoadVersions(playerUuid: UUID, group: String?, limit: Int): List<InventoryVersion>
    protected abstract suspend fun doLoadVersion(versionId: String): InventoryVersion?
    protected abstract suspend fun doDeleteVersion(versionId: String): Boolean
    protected abstract suspend fun doPruneVersions(olderThan: Instant): Int

    // Abstract methods for death record storage
    protected abstract suspend fun doSaveDeathRecord(record: DeathRecord)
    protected abstract suspend fun doLoadDeathRecords(playerUuid: UUID, limit: Int): List<DeathRecord>
    protected abstract suspend fun doLoadDeathRecord(deathId: String): DeathRecord?
    protected abstract suspend fun doDeleteDeathRecord(deathId: String): Boolean
    protected abstract suspend fun doPruneDeathRecords(olderThan: Instant): Int

    // ═══════════════════════════════════════════════════════════════════
    // Temporary Group Assignment Storage
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Saves a temporary group assignment.
     */
    override suspend fun saveTempGroupAssignment(assignment: TemporaryGroupAssignment): Boolean {
        if (!initialized) {
            Logging.warning("Cannot save temp group assignment - storage not initialized")
            return false
        }

        return try {
            doSaveTempGroupAssignment(assignment)
            true
        } catch (e: Exception) {
            Logging.error("Failed to save temp group assignment for ${assignment.playerUuid}", e)
            false
        }
    }

    /**
     * Loads a temporary group assignment for a player.
     */
    override suspend fun loadTempGroupAssignment(playerUuid: UUID): TemporaryGroupAssignment? {
        if (!initialized) return null

        return try {
            doLoadTempGroupAssignment(playerUuid)
        } catch (e: Exception) {
            Logging.error("Failed to load temp group assignment for $playerUuid", e)
            null
        }
    }

    /**
     * Loads all temporary group assignments.
     */
    override suspend fun loadAllTempGroupAssignments(): List<TemporaryGroupAssignment> {
        if (!initialized) return emptyList()

        return try {
            doLoadAllTempGroupAssignments()
        } catch (e: Exception) {
            Logging.error("Failed to load all temp group assignments", e)
            emptyList()
        }
    }

    /**
     * Deletes a temporary group assignment.
     */
    override suspend fun deleteTempGroupAssignment(playerUuid: UUID): Boolean {
        if (!initialized) return false

        return try {
            doDeleteTempGroupAssignment(playerUuid)
            true
        } catch (e: Exception) {
            Logging.error("Failed to delete temp group assignment for $playerUuid", e)
            false
        }
    }

    /**
     * Deletes expired temporary group assignments.
     */
    override suspend fun pruneExpiredTempGroups(): Int {
        if (!initialized) return 0

        return try {
            doPruneExpiredTempGroups()
        } catch (e: Exception) {
            Logging.error("Failed to prune expired temp groups", e)
            0
        }
    }

    // Abstract methods for temporary group storage
    protected abstract suspend fun doSaveTempGroupAssignment(assignment: TemporaryGroupAssignment)
    protected abstract suspend fun doLoadTempGroupAssignment(playerUuid: UUID): TemporaryGroupAssignment?
    protected abstract suspend fun doLoadAllTempGroupAssignments(): List<TemporaryGroupAssignment>
    protected abstract suspend fun doDeleteTempGroupAssignment(playerUuid: UUID)
    protected abstract suspend fun doPruneExpiredTempGroups(): Int
}

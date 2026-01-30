package sh.pcx.xinventories.internal.service

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import sh.pcx.xinventories.internal.sync.RedisClient
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages distributed locks for player data across multiple servers.
 * Uses Redis to coordinate lock acquisition and release.
 */
class DistributedLockManager(
    private val redisClient: RedisClient,
    private val serverId: String,
    private val lockTimeoutSeconds: Int = 60
) {
    companion object {
        private const val LOCK_KEY_PREFIX = "xinventories:lock:"
    }

    private val json = Json { encodeDefaults = true }

    // Local cache of locks we hold
    private val localLocks = ConcurrentHashMap<UUID, LockInfo>()

    /**
     * Attempts to acquire a lock on player data.
     *
     * @param playerUuid The player's UUID
     * @return true if lock was acquired, false if already locked by another server
     */
    fun acquireLock(playerUuid: UUID): Boolean {
        val key = lockKey(playerUuid)
        val lockInfo = LockInfo(
            serverId = serverId,
            timestamp = System.currentTimeMillis(),
            playerUuid = playerUuid.toString()
        )

        val lockValue = json.encodeToString(lockInfo)

        // Try to set the lock with NX (only if not exists)
        val acquired = redisClient.setNx(key, lockValue, lockTimeoutSeconds)

        if (acquired) {
            localLocks[playerUuid] = lockInfo
            Logging.debug { "Acquired lock for $playerUuid" }
            return true
        }

        // Check if we already hold the lock (reconnect scenario)
        val existingLock = getLockInfo(playerUuid)
        if (existingLock?.serverId == serverId) {
            // Refresh the lock
            redisClient.expire(key, lockTimeoutSeconds)
            localLocks[playerUuid] = existingLock
            Logging.debug { "Refreshed existing lock for $playerUuid" }
            return true
        }

        Logging.debug { "Lock for $playerUuid already held by ${existingLock?.serverId}" }
        return false
    }

    /**
     * Releases a lock on player data.
     *
     * @param playerUuid The player's UUID
     */
    fun releaseLock(playerUuid: UUID) {
        val key = lockKey(playerUuid)

        // Only release if we hold the lock
        val existingLock = getLockInfo(playerUuid)
        if (existingLock?.serverId == serverId) {
            redisClient.delete(key)
            localLocks.remove(playerUuid)
            Logging.debug { "Released lock for $playerUuid" }
        } else {
            Logging.warning("Attempted to release lock for $playerUuid but we don't hold it")
        }
    }

    /**
     * Checks if a player's data is locked.
     */
    fun isLocked(playerUuid: UUID): Boolean {
        val key = lockKey(playerUuid)
        return redisClient.exists(key)
    }

    /**
     * Gets the server ID that holds the lock for a player.
     */
    fun getLockHolder(playerUuid: UUID): String? {
        return getLockInfo(playerUuid)?.serverId
    }

    /**
     * Gets the full lock info for a player.
     */
    fun getLockInfo(playerUuid: UUID): LockInfo? {
        val key = lockKey(playerUuid)
        val value = redisClient.get(key) ?: return null

        return try {
            json.decodeFromString<LockInfo>(value)
        } catch (e: Exception) {
            Logging.error("Failed to parse lock info for $playerUuid: $value", e)
            null
        }
    }

    /**
     * Refreshes the TTL on a lock we hold.
     */
    fun refreshLock(playerUuid: UUID): Boolean {
        if (!localLocks.containsKey(playerUuid)) {
            return false
        }

        val key = lockKey(playerUuid)
        val existingLock = getLockInfo(playerUuid)

        if (existingLock?.serverId == serverId) {
            return redisClient.expire(key, lockTimeoutSeconds)
        }

        return false
    }

    /**
     * Releases all locks held by this server.
     */
    fun releaseAllLocks() {
        localLocks.keys.forEach { uuid ->
            releaseLock(uuid)
        }
        localLocks.clear()
    }

    /**
     * Cleans up locks held by a specific server (for shutdown handling).
     */
    fun cleanupLocksForServer(deadServerId: String) {
        // Find all lock keys
        val lockKeys = redisClient.keys("$LOCK_KEY_PREFIX*")

        lockKeys.forEach { key ->
            val value = redisClient.get(key) ?: return@forEach
            try {
                val lockInfo = json.decodeFromString<LockInfo>(value)
                if (lockInfo.serverId == deadServerId) {
                    redisClient.delete(key)
                    Logging.debug { "Cleaned up orphaned lock from $deadServerId: $key" }
                }
            } catch (e: Exception) {
                Logging.error("Failed to parse lock info for cleanup: $key", e)
            }
        }
    }

    /**
     * Gets all locks currently held by this server.
     */
    fun getLocalLocks(): Map<UUID, LockInfo> {
        return localLocks.toMap()
    }

    /**
     * Gets all locks in the system.
     */
    fun getAllLocks(): Map<UUID, LockInfo> {
        val locks = mutableMapOf<UUID, LockInfo>()
        val lockKeys = redisClient.keys("$LOCK_KEY_PREFIX*")

        lockKeys.forEach { key ->
            val value = redisClient.get(key) ?: return@forEach
            try {
                val lockInfo = json.decodeFromString<LockInfo>(value)
                val uuid = UUID.fromString(lockInfo.playerUuid)
                locks[uuid] = lockInfo
            } catch (e: Exception) {
                Logging.error("Failed to parse lock info: $key", e)
            }
        }

        return locks
    }

    /**
     * Checks if we hold a lock locally.
     */
    fun isLocallyLocked(playerUuid: UUID): Boolean {
        return localLocks.containsKey(playerUuid)
    }

    private fun lockKey(playerUuid: UUID): String {
        return "$LOCK_KEY_PREFIX$playerUuid"
    }
}

/**
 * Information about a lock.
 */
@Serializable
data class LockInfo(
    val serverId: String,
    val timestamp: Long,
    val playerUuid: String
) {
    fun getPlayerUUID(): UUID = UUID.fromString(playerUuid)

    fun age(): Long = System.currentTimeMillis() - timestamp
}

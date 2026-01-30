package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.internal.model.ServerInfo
import sh.pcx.xinventories.internal.model.SyncMessage
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Interface for cross-server synchronization operations.
 * Implementations may use Redis, plugin messaging, or other sync mechanisms.
 */
interface SyncService {

    /**
     * Whether sync is enabled and connected.
     */
    val isEnabled: Boolean

    /**
     * The unique identifier for this server.
     */
    val serverId: String

    /**
     * Initializes the sync service and establishes connections.
     */
    suspend fun initialize(): Boolean

    /**
     * Shuts down the sync service and releases resources.
     */
    suspend fun shutdown()

    /**
     * Gets information about all connected servers.
     */
    fun getConnectedServers(): List<ServerInfo>

    /**
     * Checks if a player's data is currently locked.
     */
    fun isPlayerLocked(uuid: UUID): Boolean

    /**
     * Gets the server ID that holds the lock for a player.
     */
    fun getLockHolder(uuid: UUID): String?

    /**
     * Attempts to acquire a lock on player data.
     * Returns true if the lock was acquired, false otherwise.
     */
    suspend fun acquireLock(uuid: UUID, timeoutMillis: Long = 5000): Boolean

    /**
     * Releases a lock on player data.
     */
    suspend fun releaseLock(uuid: UUID)

    /**
     * Transfers a lock to another server (for server transfers).
     */
    suspend fun transferLock(uuid: UUID, toServer: String): Boolean

    /**
     * Broadcasts a data update notification to other servers.
     */
    fun broadcastUpdate(uuid: UUID, group: String, version: Long)

    /**
     * Broadcasts a cache invalidation request.
     */
    fun broadcastInvalidation(uuid: UUID, group: String? = null)

    /**
     * Forces a sync for a specific player.
     */
    fun forceSyncPlayer(uuid: UUID): CompletableFuture<Result<Unit>>

    /**
     * Registers a handler for incoming sync messages.
     */
    fun onMessage(handler: (SyncMessage) -> Unit)

    /**
     * Registers a handler for cache invalidation events.
     */
    fun onCacheInvalidate(handler: (UUID, String?) -> Unit)

    /**
     * Gets sync statistics.
     */
    fun getStats(): SyncStats
}

/**
 * Statistics about sync operations.
 */
data class SyncStats(
    val messagesPublished: Long,
    val messagesReceived: Long,
    val locksAcquired: Long,
    val locksReleased: Long,
    val lockConflicts: Long,
    val connectedServers: Int,
    val lastHeartbeat: Long
)

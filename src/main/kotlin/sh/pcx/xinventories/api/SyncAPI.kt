package sh.pcx.xinventories.api

import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Public API for cross-server synchronization features.
 * Access via XInventoriesAPI.sync (nullable - only available when sync is enabled).
 */
interface SyncAPI {

    /**
     * Whether sync is enabled and connected.
     */
    val isEnabled: Boolean

    /**
     * The unique identifier for this server in the network.
     */
    val serverId: String

    /**
     * Gets information about all connected servers in the network.
     *
     * @return List of server information objects
     */
    fun getConnectedServers(): List<ServerInfo>

    /**
     * Checks if a player's data is currently locked by any server.
     *
     * @param uuid The player's UUID
     * @return true if the player's data is locked
     */
    fun isPlayerLocked(uuid: UUID): Boolean

    /**
     * Gets the server ID that currently holds the lock for a player.
     *
     * @param uuid The player's UUID
     * @return The server ID holding the lock, or null if not locked
     */
    fun getLockHolder(uuid: UUID): String?

    /**
     * Forces a sync for a specific player, refreshing their data from storage
     * and broadcasting cache invalidation to other servers.
     *
     * @param uuid The player's UUID
     * @return CompletableFuture that completes when sync is done
     */
    fun forceSyncPlayer(uuid: UUID): CompletableFuture<Result<Unit>>

    /**
     * Broadcasts a cache invalidation request for a player to all servers.
     * Other servers will reload this player's data from storage on next access.
     *
     * @param uuid The player's UUID
     * @param group Optional group to invalidate (null = all groups)
     */
    fun broadcastInvalidation(uuid: UUID, group: String? = null)

    /**
     * Gets the total number of players across all connected servers.
     *
     * @return Total player count across the network
     */
    fun getTotalNetworkPlayerCount(): Int

    /**
     * Checks if a specific server is healthy (receiving heartbeats).
     *
     * @param serverId The server ID to check
     * @return true if the server is healthy
     */
    fun isServerHealthy(serverId: String): Boolean

    /**
     * Information about a server in the network.
     */
    data class ServerInfo(
        /**
         * Unique identifier for this server.
         */
        val serverId: String,

        /**
         * Number of players currently on this server.
         */
        val playerCount: Int,

        /**
         * Timestamp of the last heartbeat from this server (epoch milliseconds).
         */
        val lastHeartbeat: Long,

        /**
         * Whether this server is considered healthy.
         */
        val isHealthy: Boolean
    )
}

package sh.pcx.xinventories.internal.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Base interface for all sync messages.
 * These messages are serialized to JSON and sent via Redis pub/sub.
 */
@Serializable
sealed class SyncMessage {

    /**
     * Request to acquire a lock on player data.
     * Sent when a player joins a server.
     */
    @Serializable
    @SerialName("acquire_lock")
    data class AcquireLock(
        val playerUuid: String,
        val serverId: String,
        val timestamp: Long
    ) : SyncMessage() {
        fun getPlayerUUID(): UUID = UUID.fromString(playerUuid)
    }

    /**
     * Release a lock on player data.
     * Sent when a player quits a server.
     */
    @Serializable
    @SerialName("release_lock")
    data class ReleaseLock(
        val playerUuid: String,
        val serverId: String
    ) : SyncMessage() {
        fun getPlayerUUID(): UUID = UUID.fromString(playerUuid)
    }

    /**
     * Transfer a lock from one server to another.
     * Sent during server transfers (e.g., BungeeCord/Velocity).
     */
    @Serializable
    @SerialName("transfer_lock")
    data class TransferLock(
        val playerUuid: String,
        val fromServer: String,
        val toServer: String
    ) : SyncMessage() {
        fun getPlayerUUID(): UUID = UUID.fromString(playerUuid)
    }

    /**
     * Notification that player data has been updated.
     * Other servers should invalidate their cache for this player.
     */
    @Serializable
    @SerialName("data_update")
    data class DataUpdate(
        val playerUuid: String,
        val group: String,
        val version: Long,
        val serverId: String
    ) : SyncMessage() {
        fun getPlayerUUID(): UUID = UUID.fromString(playerUuid)
    }

    /**
     * Request to invalidate cached data for a player.
     */
    @Serializable
    @SerialName("cache_invalidate")
    data class CacheInvalidate(
        val playerUuid: String,
        val group: String? = null
    ) : SyncMessage() {
        fun getPlayerUUID(): UUID = UUID.fromString(playerUuid)
    }

    /**
     * Heartbeat message to indicate server health.
     * Sent periodically by each server.
     */
    @Serializable
    @SerialName("heartbeat")
    data class Heartbeat(
        val serverId: String,
        val timestamp: Long,
        val playerCount: Int
    ) : SyncMessage()

    /**
     * Acknowledgment message for lock operations.
     */
    @Serializable
    @SerialName("lock_ack")
    data class LockAck(
        val playerUuid: String,
        val serverId: String,
        val granted: Boolean,
        val currentHolder: String? = null
    ) : SyncMessage() {
        fun getPlayerUUID(): UUID = UUID.fromString(playerUuid)
    }

    /**
     * Server shutdown notification.
     * Other servers should clean up any locks held by this server.
     */
    @Serializable
    @SerialName("server_shutdown")
    data class ServerShutdown(
        val serverId: String,
        val timestamp: Long
    ) : SyncMessage()

    companion object {
        /**
         * Creates an AcquireLock message.
         */
        fun acquireLock(playerUuid: UUID, serverId: String): AcquireLock {
            return AcquireLock(
                playerUuid = playerUuid.toString(),
                serverId = serverId,
                timestamp = System.currentTimeMillis()
            )
        }

        /**
         * Creates a ReleaseLock message.
         */
        fun releaseLock(playerUuid: UUID, serverId: String): ReleaseLock {
            return ReleaseLock(
                playerUuid = playerUuid.toString(),
                serverId = serverId
            )
        }

        /**
         * Creates a TransferLock message.
         */
        fun transferLock(playerUuid: UUID, fromServer: String, toServer: String): TransferLock {
            return TransferLock(
                playerUuid = playerUuid.toString(),
                fromServer = fromServer,
                toServer = toServer
            )
        }

        /**
         * Creates a DataUpdate message.
         */
        fun dataUpdate(playerUuid: UUID, group: String, version: Long, serverId: String): DataUpdate {
            return DataUpdate(
                playerUuid = playerUuid.toString(),
                group = group,
                version = version,
                serverId = serverId
            )
        }

        /**
         * Creates a CacheInvalidate message.
         */
        fun cacheInvalidate(playerUuid: UUID, group: String? = null): CacheInvalidate {
            return CacheInvalidate(
                playerUuid = playerUuid.toString(),
                group = group
            )
        }

        /**
         * Creates a Heartbeat message.
         */
        fun heartbeat(serverId: String, playerCount: Int): Heartbeat {
            return Heartbeat(
                serverId = serverId,
                timestamp = System.currentTimeMillis(),
                playerCount = playerCount
            )
        }

        /**
         * Creates a ServerShutdown message.
         */
        fun serverShutdown(serverId: String): ServerShutdown {
            return ServerShutdown(
                serverId = serverId,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}

package sh.pcx.xinventories.internal.model

import java.time.Instant

/**
 * Represents information about a server in the network.
 */
data class ServerInfo(
    /**
     * Unique identifier for this server.
     */
    val serverId: String,

    /**
     * Timestamp of the last heartbeat received from this server.
     */
    val lastHeartbeat: Instant,

    /**
     * Number of players currently on this server.
     */
    val playerCount: Int,

    /**
     * Whether this server is considered healthy based on heartbeat timing.
     */
    val isHealthy: Boolean
) {
    companion object {
        /**
         * Creates a ServerInfo from a heartbeat message.
         */
        fun fromHeartbeat(
            serverId: String,
            timestamp: Long,
            playerCount: Int,
            timeoutSeconds: Int
        ): ServerInfo {
            val heartbeatTime = Instant.ofEpochMilli(timestamp)
            val isHealthy = Instant.now().epochSecond - heartbeatTime.epochSecond < timeoutSeconds
            return ServerInfo(
                serverId = serverId,
                lastHeartbeat = heartbeatTime,
                playerCount = playerCount,
                isHealthy = isHealthy
            )
        }
    }

    /**
     * Returns the time since the last heartbeat in seconds.
     */
    fun secondsSinceHeartbeat(): Long {
        return Instant.now().epochSecond - lastHeartbeat.epochSecond
    }
}

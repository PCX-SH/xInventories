package sh.pcx.xinventories.internal.service

import kotlinx.coroutines.*
import sh.pcx.xinventories.internal.model.ServerInfo
import sh.pcx.xinventories.internal.model.SyncMessage
import sh.pcx.xinventories.internal.sync.RedisClient
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages server heartbeats for health monitoring in a multi-server environment.
 * Sends periodic heartbeats and tracks the health of other servers.
 */
class HeartbeatService(
    private val redisClient: RedisClient,
    private val serverId: String,
    private val channel: String,
    private val intervalSeconds: Int,
    private val timeoutSeconds: Int,
    private val scope: CoroutineScope,
    private val playerCountProvider: () -> Int
) {
    private var heartbeatJob: Job? = null
    private var cleanupJob: Job? = null

    private val _lastHeartbeatTime = AtomicLong(0)
    val lastHeartbeatTime: Long get() = _lastHeartbeatTime.get()

    // Track known servers
    private val knownServers = ConcurrentHashMap<String, ServerInfo>()

    // Listeners for server events
    private val onServerDeadListeners = mutableListOf<(String) -> Unit>()
    private val onServerAliveListeners = mutableListOf<(ServerInfo) -> Unit>()

    /**
     * Starts the heartbeat service.
     */
    fun start() {
        if (heartbeatJob?.isActive == true) {
            Logging.warning("Heartbeat service already running")
            return
        }

        Logging.info("Starting heartbeat service (interval: ${intervalSeconds}s, timeout: ${timeoutSeconds}s)")

        // Start heartbeat sender
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                sendHeartbeat()
                delay(intervalSeconds * 1000L)
            }
        }

        // Start cleanup job for dead servers
        cleanupJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                checkDeadServers()
                delay(timeoutSeconds * 1000L / 3) // Check more frequently than timeout
            }
        }
    }

    /**
     * Stops the heartbeat service.
     */
    fun stop() {
        heartbeatJob?.cancel()
        cleanupJob?.cancel()
        heartbeatJob = null
        cleanupJob = null
        Logging.info("Heartbeat service stopped")
    }

    /**
     * Sends a heartbeat to other servers.
     */
    private fun sendHeartbeat() {
        val playerCount = playerCountProvider()
        val message = SyncMessage.heartbeat(serverId, playerCount)

        if (redisClient.publish(channel, message)) {
            _lastHeartbeatTime.set(System.currentTimeMillis())
            Logging.debug { "Sent heartbeat: $playerCount players online" }
        } else {
            Logging.warning("Failed to send heartbeat")
        }
    }

    /**
     * Records a heartbeat from another server.
     */
    fun recordHeartbeat(serverId: String, timestamp: Long, playerCount: Int) {
        if (serverId == this.serverId) {
            return // Ignore our own heartbeat
        }

        val wasKnown = knownServers.containsKey(serverId)
        val serverInfo = ServerInfo.fromHeartbeat(
            serverId = serverId,
            timestamp = timestamp,
            playerCount = playerCount,
            timeoutSeconds = timeoutSeconds
        )

        knownServers[serverId] = serverInfo

        if (!wasKnown) {
            Logging.info("New server detected: $serverId ($playerCount players)")
            onServerAliveListeners.forEach { listener ->
                try {
                    listener(serverInfo)
                } catch (e: Exception) {
                    Logging.error("Error in server alive listener", e)
                }
            }
        }
    }

    /**
     * Checks for and handles dead servers.
     */
    private fun checkDeadServers() {
        val now = Instant.now()
        val deadServers = mutableListOf<String>()

        knownServers.forEach { (serverId, info) ->
            if (now.epochSecond - info.lastHeartbeat.epochSecond > timeoutSeconds) {
                deadServers.add(serverId)
            }
        }

        deadServers.forEach { deadServerId ->
            knownServers.remove(deadServerId)
            Logging.warning("Server $deadServerId appears to be dead (no heartbeat for ${timeoutSeconds}s)")

            onServerDeadListeners.forEach { listener ->
                try {
                    listener(deadServerId)
                } catch (e: Exception) {
                    Logging.error("Error in server dead listener", e)
                }
            }
        }
    }

    /**
     * Records that a server has shut down gracefully.
     */
    fun recordShutdown(serverId: String) {
        knownServers.remove(serverId)
        Logging.info("Server $serverId shut down gracefully")
    }

    /**
     * Gets all known servers and their status.
     */
    fun getKnownServers(): Map<String, ServerInfo> {
        return knownServers.toMap()
    }

    /**
     * Gets only healthy servers.
     */
    fun getHealthyServers(): List<ServerInfo> {
        return knownServers.values.filter { it.isHealthy }
    }

    /**
     * Checks if a specific server is healthy.
     */
    fun isServerHealthy(serverId: String): Boolean {
        return knownServers[serverId]?.isHealthy == true
    }

    /**
     * Gets the total player count across all known servers.
     */
    fun getTotalPlayerCount(): Int {
        return knownServers.values.sumOf { it.playerCount } + playerCountProvider()
    }

    /**
     * Registers a listener for when a server is detected as dead.
     */
    fun onServerDead(listener: (String) -> Unit) {
        onServerDeadListeners.add(listener)
    }

    /**
     * Registers a listener for when a new server is detected.
     */
    fun onServerAlive(listener: (ServerInfo) -> Unit) {
        onServerAliveListeners.add(listener)
    }

    /**
     * Forces an immediate heartbeat.
     */
    fun forceHeartbeat() {
        sendHeartbeat()
    }

    /**
     * Gets this server's info.
     */
    fun getOwnServerInfo(): ServerInfo {
        return ServerInfo(
            serverId = serverId,
            lastHeartbeat = Instant.ofEpochMilli(lastHeartbeatTime),
            playerCount = playerCountProvider(),
            isHealthy = true
        )
    }
}

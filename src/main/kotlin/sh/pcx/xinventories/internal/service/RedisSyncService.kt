package sh.pcx.xinventories.internal.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.ServerInfo
import sh.pcx.xinventories.internal.model.SyncMessage
import sh.pcx.xinventories.internal.sync.RedisClient
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Redis-based implementation of SyncService.
 * Uses Redis pub/sub for messaging and Redis keys for distributed locking.
 */
class RedisSyncService(
    private val plugin: XInventories,
    private val scope: CoroutineScope,
    private val config: SyncConfig
) : SyncService {

    private var redisClient: RedisClient? = null
    private lateinit var lockManager: DistributedLockManager
    private lateinit var heartbeatService: HeartbeatService

    private val messageHandlers = mutableListOf<(SyncMessage) -> Unit>()
    private val cacheInvalidateHandlers = mutableListOf<(UUID, String?) -> Unit>()
    private val connectedServers = ConcurrentHashMap<String, ServerInfo>()

    // Statistics
    private val messagesPublished = AtomicLong(0)
    private val messagesReceived = AtomicLong(0)
    private val locksAcquired = AtomicLong(0)
    private val locksReleased = AtomicLong(0)
    private val lockConflicts = AtomicLong(0)

    override val isEnabled: Boolean
        get() = redisClient?.isConnected() == true

    override val serverId: String
        get() = config.serverId

    override suspend fun initialize(): Boolean {
        if (!config.enabled) {
            Logging.info("Sync is disabled in configuration")
            return false
        }

        Logging.info("Initializing Redis sync service...")

        // Create Redis client
        redisClient = RedisClient(
            host = config.redis.host,
            port = config.redis.port,
            password = config.redis.password.ifBlank { null },
            timeout = config.redis.timeout,
            scope = scope
        )

        // Connect to Redis
        if (!redisClient!!.connect()) {
            Logging.error("Failed to connect to Redis - sync disabled")
            redisClient = null
            return false
        }

        // Initialize lock manager
        lockManager = DistributedLockManager(
            redisClient = redisClient!!,
            serverId = config.serverId,
            lockTimeoutSeconds = config.transferLock.timeoutSeconds
        )

        // Initialize heartbeat service
        heartbeatService = HeartbeatService(
            redisClient = redisClient!!,
            serverId = config.serverId,
            channel = config.redis.channel,
            intervalSeconds = config.heartbeat.intervalSeconds,
            timeoutSeconds = config.heartbeat.timeoutSeconds,
            scope = scope,
            playerCountProvider = { plugin.server.onlinePlayers.size }
        )

        // Subscribe to sync channel
        redisClient!!.subscribe(config.redis.channel)
        redisClient!!.onMessage(config.redis.channel) { message ->
            handleMessage(message)
        }

        // Start heartbeat
        heartbeatService.start()

        Logging.info("Redis sync service initialized (server: ${config.serverId})")
        return true
    }

    override suspend fun shutdown() {
        Logging.info("Shutting down Redis sync service...")

        // Send shutdown notification
        redisClient?.publish(
            config.redis.channel,
            SyncMessage.serverShutdown(config.serverId)
        )

        // Stop heartbeat
        if (::heartbeatService.isInitialized) {
            heartbeatService.stop()
        }

        // Release all locks held by this server
        if (::lockManager.isInitialized) {
            lockManager.releaseAllLocks()
        }

        // Disconnect from Redis
        redisClient?.disconnect()
        redisClient = null

        Logging.info("Redis sync service shut down")
    }

    override fun getConnectedServers(): List<ServerInfo> {
        return connectedServers.values.toList()
    }

    override fun isPlayerLocked(uuid: UUID): Boolean {
        return lockManager.isLocked(uuid)
    }

    override fun getLockHolder(uuid: UUID): String? {
        return lockManager.getLockHolder(uuid)
    }

    override suspend fun acquireLock(uuid: UUID, timeoutMillis: Long): Boolean {
        val startTime = System.currentTimeMillis()
        val pollInterval = 100L // Poll every 100ms

        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (lockManager.acquireLock(uuid)) {
                locksAcquired.incrementAndGet()
                Logging.debug { "Acquired lock for $uuid" }

                // Broadcast lock acquisition
                redisClient?.publish(
                    config.redis.channel,
                    SyncMessage.acquireLock(uuid, serverId)
                )
                messagesPublished.incrementAndGet()

                return true
            }

            val holder = lockManager.getLockHolder(uuid)
            Logging.debug { "Lock for $uuid held by $holder, waiting..." }
            lockConflicts.incrementAndGet()

            delay(pollInterval)
        }

        Logging.warning("Failed to acquire lock for $uuid within ${timeoutMillis}ms")
        return false
    }

    override suspend fun releaseLock(uuid: UUID) {
        lockManager.releaseLock(uuid)
        locksReleased.incrementAndGet()

        // Broadcast lock release
        redisClient?.publish(
            config.redis.channel,
            SyncMessage.releaseLock(uuid, serverId)
        )
        messagesPublished.incrementAndGet()

        Logging.debug { "Released lock for $uuid" }
    }

    override suspend fun transferLock(uuid: UUID, toServer: String): Boolean {
        if (!config.transferLock.enabled) {
            // Just release the lock and let the other server acquire it
            releaseLock(uuid)
            return true
        }

        Logging.debug { "Transferring lock for $uuid to $toServer" }

        // Broadcast transfer request
        redisClient?.publish(
            config.redis.channel,
            SyncMessage.transferLock(uuid, serverId, toServer)
        )
        messagesPublished.incrementAndGet()

        // Release our lock
        lockManager.releaseLock(uuid)
        locksReleased.incrementAndGet()

        return true
    }

    override fun broadcastUpdate(uuid: UUID, group: String, version: Long) {
        redisClient?.publish(
            config.redis.channel,
            SyncMessage.dataUpdate(uuid, group, version, serverId)
        )
        messagesPublished.incrementAndGet()
    }

    override fun broadcastInvalidation(uuid: UUID, group: String?) {
        redisClient?.publish(
            config.redis.channel,
            SyncMessage.cacheInvalidate(uuid, group)
        )
        messagesPublished.incrementAndGet()
    }

    override fun forceSyncPlayer(uuid: UUID): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            try {
                // Invalidate cache and reload from storage
                broadcastInvalidation(uuid)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override fun onMessage(handler: (SyncMessage) -> Unit) {
        messageHandlers.add(handler)
    }

    override fun onCacheInvalidate(handler: (UUID, String?) -> Unit) {
        cacheInvalidateHandlers.add(handler)
    }

    override fun getStats(): SyncStats {
        return SyncStats(
            messagesPublished = messagesPublished.get(),
            messagesReceived = messagesReceived.get(),
            locksAcquired = locksAcquired.get(),
            locksReleased = locksReleased.get(),
            lockConflicts = lockConflicts.get(),
            connectedServers = connectedServers.size,
            lastHeartbeat = if (::heartbeatService.isInitialized) {
                heartbeatService.lastHeartbeatTime
            } else 0L
        )
    }

    /**
     * Handles incoming sync messages.
     */
    private fun handleMessage(message: SyncMessage) {
        messagesReceived.incrementAndGet()

        // Ignore our own messages for certain types
        when (message) {
            is SyncMessage.Heartbeat -> {
                if (message.serverId != serverId) {
                    handleHeartbeat(message)
                }
            }
            is SyncMessage.ServerShutdown -> {
                if (message.serverId != serverId) {
                    handleServerShutdown(message)
                }
            }
            is SyncMessage.CacheInvalidate -> {
                // Always handle cache invalidates, even our own
                handleCacheInvalidate(message)
            }
            is SyncMessage.DataUpdate -> {
                if (message.serverId != serverId) {
                    handleDataUpdate(message)
                }
            }
            is SyncMessage.AcquireLock -> {
                Logging.debug { "Lock acquired by ${message.serverId} for ${message.playerUuid}" }
            }
            is SyncMessage.ReleaseLock -> {
                Logging.debug { "Lock released by ${message.serverId} for ${message.playerUuid}" }
            }
            is SyncMessage.TransferLock -> {
                if (message.toServer == serverId) {
                    handleTransferLock(message)
                }
            }
            is SyncMessage.LockAck -> {
                Logging.debug { "Lock ack for ${message.playerUuid}: ${message.granted}" }
            }
        }

        // Dispatch to registered handlers
        messageHandlers.forEach { handler ->
            try {
                handler(message)
            } catch (e: Exception) {
                Logging.error("Error in sync message handler", e)
            }
        }
    }

    private fun handleHeartbeat(message: SyncMessage.Heartbeat) {
        val serverInfo = ServerInfo.fromHeartbeat(
            serverId = message.serverId,
            timestamp = message.timestamp,
            playerCount = message.playerCount,
            timeoutSeconds = config.heartbeat.timeoutSeconds
        )
        connectedServers[message.serverId] = serverInfo
        Logging.debug { "Heartbeat from ${message.serverId}: ${message.playerCount} players" }
    }

    private fun handleServerShutdown(message: SyncMessage.ServerShutdown) {
        connectedServers.remove(message.serverId)
        Logging.info("Server ${message.serverId} shut down, cleaning up locks...")

        // Clean up any orphaned locks from the shutdown server
        lockManager.cleanupLocksForServer(message.serverId)
    }

    private fun handleCacheInvalidate(message: SyncMessage.CacheInvalidate) {
        val uuid = message.getPlayerUUID()
        cacheInvalidateHandlers.forEach { handler ->
            try {
                handler(uuid, message.group)
            } catch (e: Exception) {
                Logging.error("Error in cache invalidate handler", e)
            }
        }
    }

    private fun handleDataUpdate(message: SyncMessage.DataUpdate) {
        // Invalidate local cache for this player/group
        val uuid = message.getPlayerUUID()
        cacheInvalidateHandlers.forEach { handler ->
            try {
                handler(uuid, message.group)
            } catch (e: Exception) {
                Logging.error("Error in cache invalidate handler", e)
            }
        }
    }

    private fun handleTransferLock(message: SyncMessage.TransferLock) {
        Logging.debug { "Received lock transfer for ${message.playerUuid} from ${message.fromServer}" }
        // The lock should be available now, we'll acquire it when the player joins
    }
}

/**
 * Sync configuration data class.
 */
data class SyncConfig(
    val enabled: Boolean = false,
    val mode: SyncMode = SyncMode.REDIS,
    val serverId: String = "server-1",
    val redis: RedisConfig = RedisConfig(),
    val conflicts: ConflictConfig = ConflictConfig(),
    val transferLock: TransferLockConfig = TransferLockConfig(),
    val heartbeat: HeartbeatConfig = HeartbeatConfig()
)

enum class SyncMode {
    REDIS,
    PLUGIN_MESSAGING,
    MYSQL_NOTIFY
}

data class RedisConfig(
    val host: String = "localhost",
    val port: Int = 6379,
    val password: String = "",
    val channel: String = "xinventories:sync",
    val timeout: Int = 5000
)

data class ConflictConfig(
    val strategy: sh.pcx.xinventories.internal.model.ConflictStrategy =
        sh.pcx.xinventories.internal.model.ConflictStrategy.LAST_WRITE_WINS,
    val mergeRules: sh.pcx.xinventories.internal.model.MergeRules =
        sh.pcx.xinventories.internal.model.MergeRules()
)

data class TransferLockConfig(
    val enabled: Boolean = true,
    val timeoutSeconds: Int = 10
)

data class HeartbeatConfig(
    val intervalSeconds: Int = 30,
    val timeoutSeconds: Int = 90
)

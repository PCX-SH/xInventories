package sh.pcx.xinventories.internal.sync

import kotlinx.coroutines.*
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import redis.clients.jedis.JedisPubSub
import sh.pcx.xinventories.internal.model.SyncMessage
import sh.pcx.xinventories.internal.util.Logging
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Redis client wrapper for sync operations.
 * Manages connection pooling, pub/sub subscriptions, and key-value operations.
 */
class RedisClient(
    private val host: String,
    private val port: Int,
    private val password: String?,
    private val timeout: Int,
    private val scope: CoroutineScope
) {
    private var pool: JedisPool? = null
    private var subscriber: RedisPubSubHandler? = null
    private var subscriberJob: Job? = null
    private val connected = AtomicBoolean(false)
    private val messageHandlers = ConcurrentHashMap<String, MutableList<(SyncMessage) -> Unit>>()

    /**
     * Initializes the Redis connection pool.
     */
    fun connect(): Boolean {
        return try {
            val config = JedisPoolConfig().apply {
                maxTotal = 10
                maxIdle = 5
                minIdle = 1
                testOnBorrow = true
                testOnReturn = true
                testWhileIdle = true
                setMaxWait(Duration.ofMillis(timeout.toLong()))
                blockWhenExhausted = true
            }

            pool = if (password.isNullOrBlank()) {
                JedisPool(config, host, port, timeout)
            } else {
                JedisPool(config, host, port, timeout, password)
            }

            // Test connection
            pool?.resource?.use { jedis ->
                jedis.ping()
            }

            connected.set(true)
            Logging.info("Connected to Redis at $host:$port")
            true
        } catch (e: Exception) {
            Logging.error("Failed to connect to Redis at $host:$port", e)
            false
        }
    }

    /**
     * Disconnects from Redis and cleans up resources.
     */
    fun disconnect() {
        try {
            subscriberJob?.cancel()
            subscriber?.unsubscribe()
            pool?.close()
            connected.set(false)
            Logging.info("Disconnected from Redis")
        } catch (e: Exception) {
            Logging.error("Error disconnecting from Redis", e)
        }
    }

    /**
     * Checks if the client is connected to Redis.
     */
    fun isConnected(): Boolean = connected.get() && pool?.isClosed == false

    /**
     * Gets a Jedis instance from the pool.
     */
    private fun getJedis(): Jedis? {
        return try {
            pool?.resource
        } catch (e: Exception) {
            Logging.error("Failed to get Jedis resource", e)
            null
        }
    }

    /**
     * Publishes a message to a channel.
     */
    fun publish(channel: String, message: SyncMessage): Boolean {
        return try {
            getJedis()?.use { jedis ->
                val json = MessageSerializer.serialize(message)
                jedis.publish(channel, json)
                Logging.debug { "Published to $channel: ${message::class.simpleName}" }
                true
            } ?: false
        } catch (e: Exception) {
            Logging.error("Failed to publish message to $channel", e)
            false
        }
    }

    /**
     * Subscribes to a channel and routes messages to registered handlers.
     */
    fun subscribe(channel: String) {
        if (subscriber != null) {
            Logging.warning("Already subscribed to Redis channels")
            return
        }

        subscriber = RedisPubSubHandler(channel) { channelName, message ->
            handleMessage(channelName, message)
        }

        subscriberJob = scope.launch(Dispatchers.IO) {
            try {
                // Use a separate connection for subscription (blocking operation)
                val subscribeJedis = if (password.isNullOrBlank()) {
                    Jedis(host, port, timeout)
                } else {
                    Jedis(host, port, timeout).also { it.auth(password) }
                }

                subscribeJedis.use { jedis ->
                    Logging.info("Subscribing to Redis channel: $channel")
                    jedis.subscribe(subscriber, channel)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Logging.error("Redis subscription error", e)
                }
            }
        }
    }

    /**
     * Registers a handler for messages on a specific channel.
     */
    fun onMessage(channel: String, handler: (SyncMessage) -> Unit) {
        messageHandlers.getOrPut(channel) { mutableListOf() }.add(handler)
    }

    /**
     * Removes all handlers for a channel.
     */
    fun removeHandlers(channel: String) {
        messageHandlers.remove(channel)
    }

    /**
     * Handles incoming messages and routes to registered handlers.
     */
    private fun handleMessage(channel: String, jsonMessage: String) {
        val message = MessageSerializer.deserialize(jsonMessage)
        if (message == null) {
            Logging.warning("Received invalid message on $channel: $jsonMessage")
            return
        }

        Logging.debug { "Received from $channel: ${message::class.simpleName}" }

        messageHandlers[channel]?.forEach { handler ->
            try {
                handler(message)
            } catch (e: Exception) {
                Logging.error("Error in message handler for $channel", e)
            }
        }
    }

    // ==================== Key-Value Operations ====================

    /**
     * Sets a key with optional expiration.
     */
    fun set(key: String, value: String, expirationSeconds: Int? = null): Boolean {
        return try {
            getJedis()?.use { jedis ->
                if (expirationSeconds != null) {
                    jedis.setex(key, expirationSeconds.toLong(), value)
                } else {
                    jedis.set(key, value)
                }
                true
            } ?: false
        } catch (e: Exception) {
            Logging.error("Failed to set key: $key", e)
            false
        }
    }

    /**
     * Gets a value by key.
     */
    fun get(key: String): String? {
        return try {
            getJedis()?.use { jedis ->
                jedis.get(key)
            }
        } catch (e: Exception) {
            Logging.error("Failed to get key: $key", e)
            null
        }
    }

    /**
     * Deletes a key.
     */
    fun delete(key: String): Boolean {
        return try {
            getJedis()?.use { jedis ->
                jedis.del(key) > 0
            } ?: false
        } catch (e: Exception) {
            Logging.error("Failed to delete key: $key", e)
            false
        }
    }

    /**
     * Checks if a key exists.
     */
    fun exists(key: String): Boolean {
        return try {
            getJedis()?.use { jedis ->
                jedis.exists(key)
            } ?: false
        } catch (e: Exception) {
            Logging.error("Failed to check key existence: $key", e)
            false
        }
    }

    /**
     * Sets a key only if it doesn't exist (for locking).
     * Returns true if the key was set, false if it already existed.
     */
    fun setNx(key: String, value: String, expirationSeconds: Int): Boolean {
        return try {
            getJedis()?.use { jedis ->
                val result = jedis.set(key, value, redis.clients.jedis.params.SetParams().nx().ex(expirationSeconds.toLong()))
                result == "OK"
            } ?: false
        } catch (e: Exception) {
            Logging.error("Failed to setNx key: $key", e)
            false
        }
    }

    /**
     * Gets all keys matching a pattern.
     */
    fun keys(pattern: String): Set<String> {
        return try {
            getJedis()?.use { jedis ->
                jedis.keys(pattern)
            } ?: emptySet()
        } catch (e: Exception) {
            Logging.error("Failed to get keys matching: $pattern", e)
            emptySet()
        }
    }

    /**
     * Sets the expiration time on a key.
     */
    fun expire(key: String, seconds: Int): Boolean {
        return try {
            getJedis()?.use { jedis ->
                jedis.expire(key, seconds.toLong()) == 1L
            } ?: false
        } catch (e: Exception) {
            Logging.error("Failed to set expiration on key: $key", e)
            false
        }
    }

    /**
     * Gets the TTL of a key in seconds.
     */
    fun ttl(key: String): Long {
        return try {
            getJedis()?.use { jedis ->
                jedis.ttl(key)
            } ?: -2
        } catch (e: Exception) {
            Logging.error("Failed to get TTL for key: $key", e)
            -2
        }
    }

    /**
     * Internal pub/sub handler class.
     */
    private class RedisPubSubHandler(
        private val targetChannel: String,
        private val onMessage: (String, String) -> Unit
    ) : JedisPubSub() {

        override fun onMessage(channel: String, message: String) {
            if (channel == targetChannel) {
                onMessage(channel, message)
            }
        }

        override fun onSubscribe(channel: String, subscribedChannels: Int) {
            Logging.debug { "Subscribed to channel: $channel (total: $subscribedChannels)" }
        }

        override fun onUnsubscribe(channel: String, subscribedChannels: Int) {
            Logging.debug { "Unsubscribed from channel: $channel (remaining: $subscribedChannels)" }
        }

        override fun onPSubscribe(pattern: String, subscribedChannels: Int) {
            Logging.debug { "Pattern subscribed: $pattern" }
        }

        override fun onPUnsubscribe(pattern: String, subscribedChannels: Int) {
            Logging.debug { "Pattern unsubscribed: $pattern" }
        }

        override fun onPMessage(pattern: String, channel: String, message: String) {
            onMessage(channel, message)
        }
    }
}

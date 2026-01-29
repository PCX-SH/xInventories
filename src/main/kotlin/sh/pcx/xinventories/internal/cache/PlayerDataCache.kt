package sh.pcx.xinventories.internal.cache

import sh.pcx.xinventories.api.model.CacheStatistics
import sh.pcx.xinventories.internal.config.CacheConfig
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import sh.pcx.xinventories.internal.util.PlayerDataSerializer
import org.bukkit.GameMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Specialized cache for player inventory data.
 * Provides convenient methods for player-specific operations.
 */
class PlayerDataCache(config: CacheConfig) {

    private val cache: Cache<String, PlayerData>
    private val enabled: Boolean = config.enabled

    // Track dirty entries for write-behind
    private val dirtyEntries = ConcurrentHashMap.newKeySet<String>()

    // Write-behind delay in milliseconds
    val writeBehindDelayMs: Long = config.writeBehindSeconds * 1000L

    init {
        cache = if (enabled) {
            CaffeineCache(
                maxSize = config.maxSize,
                ttlMinutes = config.ttlMinutes,
                recordStats = true
            )
        } else {
            // No-op cache when disabled
            NoOpCache()
        }

        Logging.debug { "PlayerDataCache initialized (enabled=$enabled, maxSize=${config.maxSize}, ttl=${config.ttlMinutes}min)" }
    }

    /**
     * Gets player data from cache.
     *
     * @param uuid Player UUID
     * @param group Group name
     * @param gameMode Optional gamemode
     * @return Cached PlayerData or null
     */
    fun get(uuid: UUID, group: String, gameMode: GameMode?): PlayerData? {
        if (!enabled) return null
        val key = makeKey(uuid, group, gameMode)
        return cache.get(key)
    }

    /**
     * Gets player data from cache, loading from source if not present.
     *
     * @param uuid Player UUID
     * @param group Group name
     * @param gameMode Optional gamemode
     * @param loader Function to load data if not cached
     * @return PlayerData or null
     */
    suspend fun getOrLoad(
        uuid: UUID,
        group: String,
        gameMode: GameMode?,
        loader: suspend () -> PlayerData?
    ): PlayerData? {
        if (!enabled) return loader()

        val key = makeKey(uuid, group, gameMode)
        return cache.get(key, loader)
    }

    /**
     * Puts player data into the cache.
     *
     * @param data The player data to cache
     * @param markDirty Whether to mark as dirty for write-behind
     */
    fun put(data: PlayerData, markDirty: Boolean = false) {
        if (!enabled) return

        val key = makeKey(data.uuid, data.group, data.gameMode)
        cache.put(key, data)

        if (markDirty) {
            dirtyEntries.add(key)
            data.dirty = true
        }

        Logging.debug { "Cached data for ${data.uuid} in ${data.group}/${data.gameMode}" }
    }

    /**
     * Invalidates cache entries for a player.
     *
     * @param uuid Player UUID
     * @return Number of entries invalidated
     */
    fun invalidatePlayer(uuid: UUID): Int {
        if (!enabled) return 0

        val prefix = uuid.toString()
        val count = cache.invalidateIf { key -> key.startsWith(prefix) }

        // Also remove from dirty tracking
        dirtyEntries.removeIf { it.startsWith(prefix) }

        Logging.debug { "Invalidated $count cache entries for $uuid" }
        return count
    }

    /**
     * Invalidates a specific cache entry.
     *
     * @param uuid Player UUID
     * @param group Group name
     * @param gameMode Optional gamemode
     * @return The invalidated data, or null
     */
    fun invalidate(uuid: UUID, group: String, gameMode: GameMode?): PlayerData? {
        if (!enabled) return null

        val key = makeKey(uuid, group, gameMode)
        dirtyEntries.remove(key)
        return cache.invalidate(key)
    }

    /**
     * Invalidates all cache entries for a group.
     *
     * @param group Group name
     * @return Number of entries invalidated
     */
    fun invalidateGroup(group: String): Int {
        if (!enabled) return 0

        val suffix = "_${group}_"
        val count = cache.invalidateIf { key -> key.contains(suffix) }

        dirtyEntries.removeIf { it.contains(suffix) }

        Logging.debug { "Invalidated $count cache entries for group $group" }
        return count
    }

    /**
     * Clears the entire cache.
     *
     * @return Number of entries cleared
     */
    fun clear(): Int {
        if (!enabled) return 0

        dirtyEntries.clear()
        val count = cache.invalidateAll()

        Logging.debug { "Cleared $count cache entries" }
        return count
    }

    /**
     * Gets all dirty entries that need to be persisted.
     *
     * @return List of dirty PlayerData
     */
    fun getDirtyEntries(): List<PlayerData> {
        if (!enabled) return emptyList()

        return dirtyEntries.mapNotNull { key ->
            cache.get(key)?.takeIf { it.dirty }
        }
    }

    /**
     * Marks entries as clean after persistence.
     *
     * @param keys Keys that were persisted
     */
    fun markClean(keys: Collection<String>) {
        dirtyEntries.removeAll(keys.toSet())
        keys.forEach { key ->
            cache.get(key)?.dirty = false
        }
    }

    /**
     * Marks a specific entry as clean.
     */
    fun markClean(uuid: UUID, group: String, gameMode: GameMode?) {
        val key = makeKey(uuid, group, gameMode)
        dirtyEntries.remove(key)
        cache.get(key)?.dirty = false
    }

    /**
     * Gets all cached data for a player.
     *
     * @param uuid Player UUID
     * @return Map of group_gamemode to PlayerData
     */
    fun getAllForPlayer(uuid: UUID): Map<String, PlayerData> {
        if (!enabled) return emptyMap()

        val prefix = uuid.toString()
        return cache.keys()
            .filter { it.startsWith(prefix) }
            .mapNotNull { key ->
                cache.get(key)?.let { key to it }
            }
            .toMap()
    }

    /**
     * Checks if data is cached.
     */
    fun contains(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        if (!enabled) return false
        return cache.contains(makeKey(uuid, group, gameMode))
    }

    /**
     * Gets cache statistics.
     */
    fun getStats(): CacheStatistics {
        return cache.stats()
    }

    /**
     * Gets the number of dirty entries.
     */
    fun getDirtyCount(): Int = dirtyEntries.size

    /**
     * Performs cache maintenance.
     */
    fun cleanUp() {
        cache.cleanUp()
    }

    /**
     * Whether caching is enabled.
     */
    fun isEnabled(): Boolean = enabled

    private fun makeKey(uuid: UUID, group: String, gameMode: GameMode?): String {
        return PlayerDataSerializer.cacheKey(uuid, group, gameMode)
    }

    /**
     * No-op cache implementation for when caching is disabled.
     */
    private class NoOpCache<K : Any, V : Any> : Cache<K, V> {
        override fun get(key: K): V? = null
        override suspend fun get(key: K, loader: suspend () -> V?): V? = loader()
        override fun put(key: K, value: V) {}
        override fun putIfAbsent(key: K, value: V): Boolean = false
        override fun invalidate(key: K): V? = null
        override fun invalidateIf(predicate: (K) -> Boolean): Int = 0
        override fun invalidateAll(): Int = 0
        override fun contains(key: K): Boolean = false
        override fun keys(): Set<K> = emptySet()
        override fun size(): Int = 0
        override fun stats(): CacheStatistics = CacheStatistics(0, 0, 0, 0, 0, 0)
        override fun cleanUp() {}
    }
}

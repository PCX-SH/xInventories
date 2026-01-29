package sh.pcx.xinventories.internal.cache

import sh.pcx.xinventories.api.model.CacheStatistics

/**
 * Generic cache interface for xInventories.
 */
interface Cache<K, V> {

    /**
     * Gets a value from the cache.
     *
     * @param key The cache key
     * @return The cached value, or null if not present
     */
    fun get(key: K): V?

    /**
     * Gets a value from the cache, loading it if not present.
     *
     * @param key The cache key
     * @param loader Function to load the value if not cached
     * @return The cached or loaded value
     */
    suspend fun get(key: K, loader: suspend () -> V?): V?

    /**
     * Puts a value into the cache.
     *
     * @param key The cache key
     * @param value The value to cache
     */
    fun put(key: K, value: V)

    /**
     * Puts a value into the cache if not already present.
     *
     * @param key The cache key
     * @param value The value to cache
     * @return true if the value was added, false if already present
     */
    fun putIfAbsent(key: K, value: V): Boolean

    /**
     * Removes a value from the cache.
     *
     * @param key The cache key
     * @return The removed value, or null if not present
     */
    fun invalidate(key: K): V?

    /**
     * Removes all values matching a predicate.
     *
     * @param predicate Function to test keys
     * @return Number of entries removed
     */
    fun invalidateIf(predicate: (K) -> Boolean): Int

    /**
     * Clears the entire cache.
     *
     * @return Number of entries removed
     */
    fun invalidateAll(): Int

    /**
     * Checks if a key is present in the cache.
     *
     * @param key The cache key
     * @return true if present
     */
    fun contains(key: K): Boolean

    /**
     * Gets all keys in the cache.
     *
     * @return Set of all keys
     */
    fun keys(): Set<K>

    /**
     * Gets the current size of the cache.
     *
     * @return Number of entries
     */
    fun size(): Int

    /**
     * Gets cache statistics.
     *
     * @return CacheStatistics
     */
    fun stats(): CacheStatistics

    /**
     * Performs cache maintenance (eviction, cleanup).
     */
    fun cleanUp()
}

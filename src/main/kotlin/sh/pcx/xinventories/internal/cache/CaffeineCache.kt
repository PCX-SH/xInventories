package sh.pcx.xinventories.internal.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Cache as CaffeineNativeCache
import sh.pcx.xinventories.api.model.CacheStatistics
import kotlinx.coroutines.runBlocking
import java.util.concurrent.TimeUnit

/**
 * Caffeine-based cache implementation.
 */
class CaffeineCache<K : Any, V : Any>(
    maxSize: Int,
    ttlMinutes: Int,
    recordStats: Boolean = true
) : Cache<K, V> {

    private val cache: CaffeineNativeCache<K, V>
    private val maxCacheSize: Int = maxSize

    init {
        val builder = Caffeine.newBuilder()
            .maximumSize(maxSize.toLong())

        if (ttlMinutes > 0) {
            builder.expireAfterAccess(ttlMinutes.toLong(), TimeUnit.MINUTES)
        }

        if (recordStats) {
            builder.recordStats()
        }

        cache = builder.build()
    }

    override fun get(key: K): V? {
        return cache.getIfPresent(key)
    }

    override suspend fun get(key: K, loader: suspend () -> V?): V? {
        // Check cache first
        cache.getIfPresent(key)?.let { return it }

        // Load value
        val value = loader()

        // Cache if not null
        if (value != null) {
            cache.put(key, value)
        }

        return value
    }

    override fun put(key: K, value: V) {
        cache.put(key, value)
    }

    override fun putIfAbsent(key: K, value: V): Boolean {
        val existing = cache.getIfPresent(key)
        if (existing == null) {
            cache.put(key, value)
            return true
        }
        return false
    }

    override fun invalidate(key: K): V? {
        val value = cache.getIfPresent(key)
        cache.invalidate(key)
        return value
    }

    override fun invalidateIf(predicate: (K) -> Boolean): Int {
        val keysToRemove = cache.asMap().keys.filter(predicate)
        cache.invalidateAll(keysToRemove)
        return keysToRemove.size
    }

    override fun invalidateAll(): Int {
        val size = cache.estimatedSize().toInt()
        cache.invalidateAll()
        return size
    }

    override fun contains(key: K): Boolean {
        return cache.getIfPresent(key) != null
    }

    override fun keys(): Set<K> {
        return cache.asMap().keys.toSet()
    }

    override fun size(): Int {
        return cache.estimatedSize().toInt()
    }

    override fun stats(): CacheStatistics {
        val stats = cache.stats()
        return CacheStatistics(
            size = cache.estimatedSize().toInt(),
            maxSize = maxCacheSize,
            hitCount = stats.hitCount(),
            missCount = stats.missCount(),
            loadCount = stats.loadCount(),
            evictionCount = stats.evictionCount()
        )
    }

    override fun cleanUp() {
        cache.cleanUp()
    }
}

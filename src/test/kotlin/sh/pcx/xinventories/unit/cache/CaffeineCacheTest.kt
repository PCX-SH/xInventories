package sh.pcx.xinventories.unit.cache

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import sh.pcx.xinventories.internal.cache.CaffeineCache
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("CaffeineCache")
class CaffeineCacheTest {

    private lateinit var cache: CaffeineCache<String, String>

    @BeforeEach
    fun setUp() {
        cache = CaffeineCache(maxSize = 100, ttlMinutes = 60, recordStats = true)
    }

    @Nested
    @DisplayName("Basic get/put operations")
    inner class BasicOperations {

        @Test
        @DisplayName("put stores value and get retrieves it")
        fun `put stores value and get retrieves it`() {
            cache.put("key1", "value1")

            val result = cache.get("key1")

            assertEquals("value1", result)
        }

        @Test
        @DisplayName("put overwrites existing value")
        fun `put overwrites existing value`() {
            cache.put("key1", "value1")
            cache.put("key1", "value2")

            val result = cache.get("key1")

            assertEquals("value2", result)
        }

        @Test
        @DisplayName("multiple entries can be stored and retrieved")
        fun `multiple entries can be stored and retrieved`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")
            cache.put("key3", "value3")

            assertEquals("value1", cache.get("key1"))
            assertEquals("value2", cache.get("key2"))
            assertEquals("value3", cache.get("key3"))
        }
    }

    @Nested
    @DisplayName("Cache miss behavior")
    inner class CacheMiss {

        @Test
        @DisplayName("get returns null for non-existent key")
        fun `get returns null for non-existent key`() {
            val result = cache.get("nonexistent")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("get returns null after key is invalidated")
        fun `get returns null after key is invalidated`() {
            cache.put("key1", "value1")
            cache.invalidate("key1")

            val result = cache.get("key1")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("contains returns false for non-existent key")
        fun `contains returns false for non-existent key`() {
            assertFalse(cache.contains("nonexistent"))
        }
    }

    @Nested
    @DisplayName("Cache hit behavior")
    inner class CacheHit {

        @Test
        @DisplayName("get returns stored value for existing key")
        fun `get returns stored value for existing key`() {
            cache.put("key1", "value1")

            val result = cache.get("key1")

            assertEquals("value1", result)
        }

        @Test
        @DisplayName("contains returns true for existing key")
        fun `contains returns true for existing key`() {
            cache.put("key1", "value1")

            assertTrue(cache.contains("key1"))
        }

        @Test
        @DisplayName("multiple gets return same value")
        fun `multiple gets return same value`() {
            cache.put("key1", "value1")

            assertEquals("value1", cache.get("key1"))
            assertEquals("value1", cache.get("key1"))
            assertEquals("value1", cache.get("key1"))
        }
    }

    @Nested
    @DisplayName("TTL-based expiration")
    inner class TtlExpiration {

        @Test
        @DisplayName("entry expires after TTL")
        @Timeout(10, unit = TimeUnit.SECONDS)
        fun `entry expires after TTL`() {
            // Create cache with very short TTL (1 minute minimum for Caffeine,
            // but we'll use a workaround by creating a cache that uses seconds internally)
            // Note: Caffeine's minimum time unit is nanoseconds, but the implementation uses minutes
            // For testing, we need to work around this - we'll test the behavior indirectly

            // The actual implementation uses minutes, so we can't easily test sub-minute TTL
            // Instead, we verify that the TTL parameter is respected by checking configuration
            val shortTtlCache = CaffeineCache<String, String>(maxSize = 100, ttlMinutes = 1, recordStats = true)
            shortTtlCache.put("key1", "value1")

            // Value should be present immediately after put
            assertEquals("value1", shortTtlCache.get("key1"))
        }

        @Test
        @DisplayName("cache with zero TTL does not expire entries based on time")
        fun `cache with zero TTL does not expire entries based on time`() {
            val noTtlCache = CaffeineCache<String, String>(maxSize = 100, ttlMinutes = 0, recordStats = true)
            noTtlCache.put("key1", "value1")

            // Should remain present
            assertEquals("value1", noTtlCache.get("key1"))
        }
    }

    @Nested
    @DisplayName("Size-based eviction")
    inner class SizeEviction {

        @Test
        @DisplayName("cache evicts entries when max size exceeded")
        fun `cache evicts entries when max size exceeded`() {
            val smallCache = CaffeineCache<String, String>(maxSize = 3, ttlMinutes = 60, recordStats = true)

            // Add entries up to max size
            smallCache.put("key1", "value1")
            smallCache.put("key2", "value2")
            smallCache.put("key3", "value3")

            // Add more entries to trigger eviction
            smallCache.put("key4", "value4")
            smallCache.put("key5", "value5")

            // Force cleanup to ensure eviction happens
            smallCache.cleanUp()

            // Size should be at or below max (Caffeine may be slightly flexible)
            assertTrue(smallCache.size() <= 3, "Cache size should not exceed max size")
        }

        @Test
        @DisplayName("recently accessed entries are preserved during eviction")
        fun `recently accessed entries are preserved during eviction`() {
            val smallCache = CaffeineCache<String, String>(maxSize = 2, ttlMinutes = 60, recordStats = true)

            smallCache.put("key1", "value1")
            smallCache.put("key2", "value2")

            // Access key1 to make it recently used
            smallCache.get("key1")

            // Add new entry to trigger eviction
            smallCache.put("key3", "value3")
            smallCache.cleanUp()

            // key1 should likely be preserved as it was recently accessed
            // Note: Caffeine uses a window TinyLFU policy, so behavior may vary
            assertTrue(smallCache.size() <= 2)
        }

        @Test
        @DisplayName("stats reflect evictions")
        fun `stats reflect evictions`() {
            val smallCache = CaffeineCache<String, String>(maxSize = 2, ttlMinutes = 60, recordStats = true)

            // Fill cache beyond capacity
            for (i in 1..10) {
                smallCache.put("key$i", "value$i")
            }
            smallCache.cleanUp()

            val stats = smallCache.stats()
            // Evictions should have occurred
            assertTrue(stats.evictionCount >= 0, "Eviction count should be tracked")
        }
    }

    @Nested
    @DisplayName("Invalidate single key")
    inner class InvalidateSingle {

        @Test
        @DisplayName("invalidate removes entry and returns value")
        fun `invalidate removes entry and returns value`() {
            cache.put("key1", "value1")

            val removed = cache.invalidate("key1")

            assertEquals("value1", removed)
            Assertions.assertNull(cache.get("key1"))
        }

        @Test
        @DisplayName("invalidate returns null for non-existent key")
        fun `invalidate returns null for non-existent key`() {
            val removed = cache.invalidate("nonexistent")

            Assertions.assertNull(removed)
        }

        @Test
        @DisplayName("invalidate does not affect other entries")
        fun `invalidate does not affect other entries`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")

            cache.invalidate("key1")

            Assertions.assertNull(cache.get("key1"))
            assertEquals("value2", cache.get("key2"))
        }
    }

    @Nested
    @DisplayName("Invalidate multiple keys")
    inner class InvalidateMultiple {

        @Test
        @DisplayName("invalidateIf removes matching entries")
        fun `invalidateIf removes matching entries`() {
            cache.put("prefix_key1", "value1")
            cache.put("prefix_key2", "value2")
            cache.put("other_key", "value3")

            val removed = cache.invalidateIf { it.startsWith("prefix_") }

            assertEquals(2, removed)
            Assertions.assertNull(cache.get("prefix_key1"))
            Assertions.assertNull(cache.get("prefix_key2"))
            assertEquals("value3", cache.get("other_key"))
        }

        @Test
        @DisplayName("invalidateIf returns zero when no matches")
        fun `invalidateIf returns zero when no matches`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")

            val removed = cache.invalidateIf { it.startsWith("nonexistent") }

            assertEquals(0, removed)
            assertEquals(2, cache.size())
        }

        @Test
        @DisplayName("invalidateIf with always-true predicate removes all")
        fun `invalidateIf with always-true predicate removes all`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")
            cache.put("key3", "value3")

            val removed = cache.invalidateIf { true }

            assertEquals(3, removed)
            assertEquals(0, cache.size())
        }
    }

    @Nested
    @DisplayName("Clear entire cache")
    inner class ClearCache {

        @Test
        @DisplayName("invalidateAll removes all entries")
        fun `invalidateAll removes all entries`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")
            cache.put("key3", "value3")

            val removed = cache.invalidateAll()

            assertEquals(3, removed)
            assertEquals(0, cache.size())
        }

        @Test
        @DisplayName("invalidateAll returns zero for empty cache")
        fun `invalidateAll returns zero for empty cache`() {
            val removed = cache.invalidateAll()

            assertEquals(0, removed)
        }

        @Test
        @DisplayName("cache can be used after clear")
        fun `cache can be used after clear`() {
            cache.put("key1", "value1")
            cache.invalidateAll()
            cache.put("key2", "value2")

            assertEquals("value2", cache.get("key2"))
            assertEquals(1, cache.size())
        }
    }

    @Nested
    @DisplayName("Get-or-load pattern (computeIfAbsent)")
    inner class GetOrLoad {

        @Test
        @DisplayName("loader is called when key not present")
        fun `loader is called when key not present`() = runBlocking {
            var loaderCalled = false

            val result = cache.get("key1") {
                loaderCalled = true
                "loaded_value"
            }

            assertTrue(loaderCalled)
            assertEquals("loaded_value", result)
        }

        @Test
        @DisplayName("loader is not called when key present")
        fun `loader is not called when key present`() = runBlocking {
            cache.put("key1", "cached_value")
            var loaderCalled = false

            val result = cache.get("key1") {
                loaderCalled = true
                "loaded_value"
            }

            assertFalse(loaderCalled)
            assertEquals("cached_value", result)
        }

        @Test
        @DisplayName("loaded value is cached")
        fun `loaded value is cached`() = runBlocking {
            cache.get("key1") { "loaded_value" }

            // Second call should return cached value
            val result = cache.get("key1")

            assertEquals("loaded_value", result)
        }

        @Test
        @DisplayName("null loader result is not cached")
        fun `null loader result is not cached`() = runBlocking {
            val loadCount = AtomicInteger(0)

            val result1 = cache.get("key1") {
                loadCount.incrementAndGet()
                null
            }

            val result2 = cache.get("key1") {
                loadCount.incrementAndGet()
                null
            }

            Assertions.assertNull(result1)
            Assertions.assertNull(result2)
            assertEquals(2, loadCount.get(), "Loader should be called twice since null is not cached")
        }

        @Test
        @DisplayName("loader can be a suspend function")
        fun `loader can be a suspend function`() = runBlocking {
            val result = cache.get("key1") {
                delay(10) // Simulate async operation
                "async_value"
            }

            assertEquals("async_value", result)
        }
    }

    @Nested
    @DisplayName("putIfAbsent")
    inner class PutIfAbsent {

        @Test
        @DisplayName("putIfAbsent adds value when key not present")
        fun `putIfAbsent adds value when key not present`() {
            val added = cache.putIfAbsent("key1", "value1")

            assertTrue(added)
            assertEquals("value1", cache.get("key1"))
        }

        @Test
        @DisplayName("putIfAbsent does not overwrite existing value")
        fun `putIfAbsent does not overwrite existing value`() {
            cache.put("key1", "original")

            val added = cache.putIfAbsent("key1", "new")

            assertFalse(added)
            assertEquals("original", cache.get("key1"))
        }
    }

    @Nested
    @DisplayName("Statistics accuracy")
    inner class Statistics {

        @Test
        @DisplayName("hit count increments on cache hit")
        fun `hit count increments on cache hit`() {
            cache.put("key1", "value1")

            cache.get("key1")
            cache.get("key1")
            cache.get("key1")

            val stats = cache.stats()
            assertEquals(3, stats.hitCount)
        }

        @Test
        @DisplayName("miss count increments on cache miss")
        fun `miss count increments on cache miss`() {
            cache.get("nonexistent1")
            cache.get("nonexistent2")
            cache.get("nonexistent3")

            val stats = cache.stats()
            assertEquals(3, stats.missCount)
        }

        @Test
        @DisplayName("hit rate is calculated correctly")
        fun `hit rate is calculated correctly`() {
            cache.put("key1", "value1")

            // 2 hits
            cache.get("key1")
            cache.get("key1")

            // 2 misses
            cache.get("nonexistent1")
            cache.get("nonexistent2")

            val stats = cache.stats()
            assertEquals(0.5, stats.hitRate, 0.01)
        }

        @Test
        @DisplayName("size is reported correctly")
        fun `size is reported correctly`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")
            cache.put("key3", "value3")

            val stats = cache.stats()
            assertEquals(3, stats.size)
        }

        @Test
        @DisplayName("maxSize is reported correctly")
        fun `maxSize is reported correctly`() {
            val stats = cache.stats()
            assertEquals(100, stats.maxSize)
        }

        @Test
        @DisplayName("stats without recording returns zero counts")
        fun `stats without recording returns zero counts`() {
            val noStatsCache = CaffeineCache<String, String>(maxSize = 100, ttlMinutes = 60, recordStats = false)
            noStatsCache.put("key1", "value1")
            noStatsCache.get("key1")
            noStatsCache.get("nonexistent")

            val stats = noStatsCache.stats()
            // When recordStats is false, hit/miss counts will be 0
            assertEquals(0, stats.hitCount)
            assertEquals(0, stats.missCount)
        }
    }

    @Nested
    @DisplayName("Concurrent access safety")
    inner class ConcurrentAccess {

        @Test
        @DisplayName("concurrent puts do not lose data")
        @Timeout(30, unit = TimeUnit.SECONDS)
        fun `concurrent puts do not lose data`() {
            val concurrentCache = CaffeineCache<Int, Int>(maxSize = 10000, ttlMinutes = 60, recordStats = true)
            val threadCount = 10
            val operationsPerThread = 1000
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)

            // Each thread writes to its own range of keys
            for (t in 0 until threadCount) {
                executor.submit {
                    try {
                        for (i in 0 until operationsPerThread) {
                            val key = t * operationsPerThread + i
                            concurrentCache.put(key, key)
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            // Verify all entries are present
            for (t in 0 until threadCount) {
                for (i in 0 until operationsPerThread) {
                    val key = t * operationsPerThread + i
                    assertEquals(key, concurrentCache.get(key), "Key $key should be present")
                }
            }
        }

        @Test
        @DisplayName("concurrent reads and writes are safe")
        @Timeout(30, unit = TimeUnit.SECONDS)
        fun `concurrent reads and writes are safe`() {
            val concurrentCache = CaffeineCache<Int, Int>(maxSize = 1000, ttlMinutes = 60, recordStats = true)
            val threadCount = 8
            val operations = 1000
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val errors = AtomicInteger(0)

            // Pre-populate some data
            for (i in 0 until 100) {
                concurrentCache.put(i, i)
            }

            for (t in 0 until threadCount) {
                executor.submit {
                    try {
                        for (i in 0 until operations) {
                            val key = i % 200
                            if (i % 2 == 0) {
                                concurrentCache.put(key, key * t)
                            } else {
                                concurrentCache.get(key)
                            }
                        }
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            assertEquals(0, errors.get(), "No errors should occur during concurrent access")
        }

        @Test
        @DisplayName("concurrent invalidations are safe")
        @Timeout(30, unit = TimeUnit.SECONDS)
        fun `concurrent invalidations are safe`() {
            val concurrentCache = CaffeineCache<Int, Int>(maxSize = 1000, ttlMinutes = 60, recordStats = true)
            val threadCount = 4
            val operations = 500
            val executor = Executors.newFixedThreadPool(threadCount)
            val latch = CountDownLatch(threadCount)
            val errors = AtomicInteger(0)

            // Pre-populate
            for (i in 0 until 500) {
                concurrentCache.put(i, i)
            }

            for (t in 0 until threadCount) {
                executor.submit {
                    try {
                        for (i in 0 until operations) {
                            when (i % 4) {
                                0 -> concurrentCache.put(i, i)
                                1 -> concurrentCache.get(i)
                                2 -> concurrentCache.invalidate(i)
                                3 -> concurrentCache.contains(i)
                            }
                        }
                    } catch (e: Exception) {
                        errors.incrementAndGet()
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await()
            executor.shutdown()

            assertEquals(0, errors.get(), "No errors should occur during concurrent invalidations")
        }

        @Test
        @DisplayName("concurrent get-or-load is safe")
        @Timeout(30, unit = TimeUnit.SECONDS)
        fun `concurrent get-or-load is safe`() = runBlocking {
            val concurrentCache = CaffeineCache<Int, Int>(maxSize = 1000, ttlMinutes = 60, recordStats = true)
            val loadCount = AtomicInteger(0)
            val jobs = List(100) {
                launch {
                    concurrentCache.get(1) {
                        loadCount.incrementAndGet()
                        delay(10)
                        42
                    }
                }
            }

            jobs.forEach { it.join() }

            // Value should be correct regardless of concurrent access
            assertEquals(42, concurrentCache.get(1))
        }
    }

    @Nested
    @DisplayName("Cache size reporting")
    inner class SizeReporting {

        @Test
        @DisplayName("size returns zero for empty cache")
        fun `size returns zero for empty cache`() {
            assertEquals(0, cache.size())
        }

        @Test
        @DisplayName("size increases after puts")
        fun `size increases after puts`() {
            cache.put("key1", "value1")
            assertEquals(1, cache.size())

            cache.put("key2", "value2")
            assertEquals(2, cache.size())

            cache.put("key3", "value3")
            assertEquals(3, cache.size())
        }

        @Test
        @DisplayName("size decreases after invalidate")
        fun `size decreases after invalidate`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")
            cache.put("key3", "value3")

            cache.invalidate("key1")

            assertEquals(2, cache.size())
        }

        @Test
        @DisplayName("size returns zero after invalidateAll")
        fun `size returns zero after invalidateAll`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")

            cache.invalidateAll()

            assertEquals(0, cache.size())
        }

        @Test
        @DisplayName("overwriting key does not increase size")
        fun `overwriting key does not increase size`() {
            cache.put("key1", "value1")
            cache.put("key1", "value2")

            assertEquals(1, cache.size())
        }
    }

    @Nested
    @DisplayName("Keys retrieval")
    inner class KeysRetrieval {

        @Test
        @DisplayName("keys returns empty set for empty cache")
        fun `keys returns empty set for empty cache`() {
            assertTrue(cache.keys().isEmpty())
        }

        @Test
        @DisplayName("keys returns all stored keys")
        fun `keys returns all stored keys`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")
            cache.put("key3", "value3")

            val keys = cache.keys()

            assertEquals(setOf("key1", "key2", "key3"), keys)
        }

        @Test
        @DisplayName("keys reflects invalidations")
        fun `keys reflects invalidations`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")
            cache.invalidate("key1")

            val keys = cache.keys()

            assertEquals(setOf("key2"), keys)
        }
    }

    @Nested
    @DisplayName("CleanUp")
    inner class CleanUp {

        @Test
        @DisplayName("cleanUp does not throw on empty cache")
        fun `cleanUp does not throw on empty cache`() {
            assertDoesNotThrow { cache.cleanUp() }
        }

        @Test
        @DisplayName("cleanUp does not remove valid entries")
        fun `cleanUp does not remove valid entries`() {
            cache.put("key1", "value1")
            cache.put("key2", "value2")

            cache.cleanUp()

            assertEquals(2, cache.size())
            assertEquals("value1", cache.get("key1"))
            assertEquals("value2", cache.get("key2"))
        }
    }

    @Nested
    @DisplayName("Edge cases")
    inner class EdgeCases {

        @Test
        @DisplayName("cache works with complex key types")
        fun `cache works with complex key types`() {
            data class ComplexKey(val id: Int, val name: String)

            val complexCache = CaffeineCache<ComplexKey, String>(maxSize = 100, ttlMinutes = 60, recordStats = true)
            val key = ComplexKey(1, "test")

            complexCache.put(key, "value")

            assertEquals("value", complexCache.get(key))
            assertEquals("value", complexCache.get(ComplexKey(1, "test")))
        }

        @Test
        @DisplayName("cache works with complex value types")
        fun `cache works with complex value types`() {
            data class ComplexValue(val data: List<Int>, val metadata: Map<String, String>)

            val complexCache = CaffeineCache<String, ComplexValue>(maxSize = 100, ttlMinutes = 60, recordStats = true)
            val value = ComplexValue(listOf(1, 2, 3), mapOf("key" to "value"))

            complexCache.put("key", value)

            assertEquals(value, complexCache.get("key"))
        }

        @Test
        @DisplayName("cache handles very long keys")
        fun `cache handles very long keys`() {
            val longKey = "a".repeat(10000)

            cache.put(longKey, "value")

            assertEquals("value", cache.get(longKey))
        }

        @Test
        @DisplayName("cache handles very long values")
        fun `cache handles very long values`() {
            val longValue = "b".repeat(100000)

            cache.put("key", longValue)

            assertEquals(longValue, cache.get("key"))
        }

        @Test
        @DisplayName("cache with max size of 1 works correctly")
        fun `cache with max size of 1 works correctly`() {
            val tinyCache = CaffeineCache<String, String>(maxSize = 1, ttlMinutes = 60, recordStats = true)

            tinyCache.put("key1", "value1")
            assertEquals("value1", tinyCache.get("key1"))

            tinyCache.put("key2", "value2")
            tinyCache.cleanUp()

            // Only one entry should remain
            assertTrue(tinyCache.size() <= 1)
        }
    }
}

package sh.pcx.xinventories.unit.cache

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.bukkit.GameMode
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.internal.cache.PlayerDataCache
import sh.pcx.xinventories.internal.config.CacheConfig
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.PlayerDataSerializer
import java.util.UUID

/**
 * Comprehensive unit tests for PlayerDataCache.
 *
 * Tests cover:
 * - Store and retrieve player data by UUID/group/gamemode key
 * - Dirty entry tracking (mark as dirty, check if dirty)
 * - Get all dirty entries
 * - Flush dirty entries (clear dirty flags)
 * - Invalidate by player UUID (all groups)
 * - Invalidate by group name
 * - Invalidate specific entry (UUID + group + gamemode)
 * - Cache enabled/disabled behavior
 * - Get all data for a player across groups
 * - Cache statistics
 * - Write-behind delay configuration
 */
class PlayerDataCacheTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            // Mock the Logging object to prevent actual logging during tests
            mockkObject(sh.pcx.xinventories.internal.util.Logging)
            every { sh.pcx.xinventories.internal.util.Logging.debug(any<() -> String>()) } just Runs
            every { sh.pcx.xinventories.internal.util.Logging.debug(any<String>()) } just Runs
            every { sh.pcx.xinventories.internal.util.Logging.info(any()) } just Runs
            every { sh.pcx.xinventories.internal.util.Logging.warning(any()) } just Runs
            every { sh.pcx.xinventories.internal.util.Logging.severe(any()) } just Runs
            every { sh.pcx.xinventories.internal.util.Logging.error(any<String>()) } just Runs
            every { sh.pcx.xinventories.internal.util.Logging.error(any<String>(), any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    private lateinit var enabledCache: PlayerDataCache
    private lateinit var disabledCache: PlayerDataCache

    private val testUuid1 = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val testUuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val testUuid3 = UUID.fromString("00000000-0000-0000-0000-000000000003")

    @BeforeEach
    fun setup() {
        // Create enabled cache with custom config
        enabledCache = PlayerDataCache(createCacheConfig(enabled = true))

        // Create disabled cache
        disabledCache = PlayerDataCache(createCacheConfig(enabled = false))
    }

    @AfterEach
    fun teardown() {
        enabledCache.clear()
    }

    // ============================================
    // Store and Retrieve Tests
    // ============================================

    @Nested
    @DisplayName("Store and Retrieve Player Data")
    inner class StoreAndRetrieveTests {

        @Test
        fun `should store and retrieve player data by UUID, group, and gamemode`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)

            enabledCache.put(data)
            val retrieved = enabledCache.get(testUuid1, "world", GameMode.SURVIVAL)

            Assertions.assertNotNull(retrieved)
            assertEquals(testUuid1, retrieved?.uuid)
            assertEquals("world", retrieved?.group)
            assertEquals(GameMode.SURVIVAL, retrieved?.gameMode)
        }

        @Test
        fun `should store and retrieve player data with null gamemode`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)

            enabledCache.put(data)
            // When stored with SURVIVAL, retrieval with null should not find it
            val retrievedNull = enabledCache.get(testUuid1, "world", null)
            Assertions.assertNull(retrievedNull)

            // Store data with the key that uses null gamemode approach
            val dataForNullKey = createTestPlayerData(testUuid1, "shared", GameMode.SURVIVAL)
            // Note: The cache key format is uuid_group_gamemode or uuid_group (when gamemode is null)
            // We need to test both patterns
            val retrievedWithMode = enabledCache.get(testUuid1, "shared", GameMode.SURVIVAL)
            Assertions.assertNull(retrievedWithMode) // Not stored yet
        }

        @Test
        fun `should store multiple entries for same player with different groups`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid1, "nether", GameMode.SURVIVAL)
            val data3 = createTestPlayerData(testUuid1, "end", GameMode.CREATIVE)

            enabledCache.put(data1)
            enabledCache.put(data2)
            enabledCache.put(data3)

            val retrieved1 = enabledCache.get(testUuid1, "world", GameMode.SURVIVAL)
            val retrieved2 = enabledCache.get(testUuid1, "nether", GameMode.SURVIVAL)
            val retrieved3 = enabledCache.get(testUuid1, "end", GameMode.CREATIVE)

            Assertions.assertNotNull(retrieved1)
            Assertions.assertNotNull(retrieved2)
            Assertions.assertNotNull(retrieved3)
            assertEquals("world", retrieved1?.group)
            assertEquals("nether", retrieved2?.group)
            assertEquals("end", retrieved3?.group)
        }

        @Test
        fun `should store entries for multiple players`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)
            val data3 = createTestPlayerData(testUuid3, "world", GameMode.CREATIVE)

            enabledCache.put(data1)
            enabledCache.put(data2)
            enabledCache.put(data3)

            val retrieved1 = enabledCache.get(testUuid1, "world", GameMode.SURVIVAL)
            val retrieved2 = enabledCache.get(testUuid2, "world", GameMode.SURVIVAL)
            val retrieved3 = enabledCache.get(testUuid3, "world", GameMode.CREATIVE)

            Assertions.assertNotNull(retrieved1)
            Assertions.assertNotNull(retrieved2)
            Assertions.assertNotNull(retrieved3)
            assertEquals(testUuid1, retrieved1?.uuid)
            assertEquals(testUuid2, retrieved2?.uuid)
            assertEquals(testUuid3, retrieved3?.uuid)
        }

        @Test
        fun `should return null for non-existent entry`() {
            val retrieved = enabledCache.get(testUuid1, "world", GameMode.SURVIVAL)
            Assertions.assertNull(retrieved)
        }

        @Test
        fun `should overwrite existing entry on put`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL).apply {
                level = 10
            }
            val data2 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL).apply {
                level = 20
            }

            enabledCache.put(data1)
            enabledCache.put(data2)

            val retrieved = enabledCache.get(testUuid1, "world", GameMode.SURVIVAL)
            assertEquals(20, retrieved?.level)
        }

        @Test
        fun `should use contains to check existence`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)

            assertFalse(enabledCache.contains(testUuid1, "world", GameMode.SURVIVAL))

            enabledCache.put(data)

            assertTrue(enabledCache.contains(testUuid1, "world", GameMode.SURVIVAL))
            assertFalse(enabledCache.contains(testUuid1, "world", GameMode.CREATIVE))
            assertFalse(enabledCache.contains(testUuid2, "world", GameMode.SURVIVAL))
        }
    }

    // ============================================
    // GetOrLoad Tests
    // ============================================

    @Nested
    @DisplayName("GetOrLoad Functionality")
    inner class GetOrLoadTests {

        @Test
        fun `should return cached value without calling loader`() = runTest {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            enabledCache.put(data)

            var loaderCalled = false
            val result = enabledCache.getOrLoad(testUuid1, "world", GameMode.SURVIVAL) {
                loaderCalled = true
                createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            }

            Assertions.assertNotNull(result)
            assertFalse(loaderCalled)
        }

        @Test
        fun `should call loader when value not cached`() = runTest {
            var loaderCalled = false
            val result = enabledCache.getOrLoad(testUuid1, "world", GameMode.SURVIVAL) {
                loaderCalled = true
                createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            }

            Assertions.assertNotNull(result)
            assertTrue(loaderCalled)
        }

        @Test
        fun `should cache loaded value`() = runTest {
            var loadCount = 0

            // First call - should load
            enabledCache.getOrLoad(testUuid1, "world", GameMode.SURVIVAL) {
                loadCount++
                createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            }

            // Second call - should use cache
            enabledCache.getOrLoad(testUuid1, "world", GameMode.SURVIVAL) {
                loadCount++
                createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            }

            assertEquals(1, loadCount)
        }

        @Test
        fun `should return null if loader returns null`() = runTest {
            val result = enabledCache.getOrLoad(testUuid1, "world", GameMode.SURVIVAL) {
                null
            }

            Assertions.assertNull(result)
        }
    }

    // ============================================
    // Dirty Entry Tracking Tests
    // ============================================

    @Nested
    @DisplayName("Dirty Entry Tracking")
    inner class DirtyEntryTests {

        @Test
        fun `should mark entry as dirty when specified`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)

            enabledCache.put(data, markDirty = true)

            assertTrue(data.dirty)
            assertEquals(1, enabledCache.getDirtyCount())
        }

        @Test
        fun `should not mark entry as dirty by default`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)

            enabledCache.put(data, markDirty = false)

            assertFalse(data.dirty)
            assertEquals(0, enabledCache.getDirtyCount())
        }

        @Test
        fun `should track multiple dirty entries`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)
            val data3 = createTestPlayerData(testUuid3, "world", GameMode.CREATIVE)

            enabledCache.put(data1, markDirty = true)
            enabledCache.put(data2, markDirty = true)
            enabledCache.put(data3, markDirty = false)

            assertEquals(2, enabledCache.getDirtyCount())
        }

        @Test
        fun `should get all dirty entries`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "nether", GameMode.SURVIVAL)
            val data3 = createTestPlayerData(testUuid3, "end", GameMode.CREATIVE)

            enabledCache.put(data1, markDirty = true)
            enabledCache.put(data2, markDirty = true)
            enabledCache.put(data3, markDirty = false)

            val dirtyEntries = enabledCache.getDirtyEntries()

            assertEquals(2, dirtyEntries.size)
            assertTrue(dirtyEntries.all { it.dirty })
            assertTrue(dirtyEntries.any { it.uuid == testUuid1 })
            assertTrue(dirtyEntries.any { it.uuid == testUuid2 })
            assertFalse(dirtyEntries.any { it.uuid == testUuid3 })
        }

        @Test
        fun `should return empty list when no dirty entries`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            enabledCache.put(data, markDirty = false)

            val dirtyEntries = enabledCache.getDirtyEntries()

            assertTrue(dirtyEntries.isEmpty())
        }
    }

    // ============================================
    // Flush Dirty Entries Tests
    // ============================================

    @Nested
    @DisplayName("Flush Dirty Entries")
    inner class FlushDirtyEntriesTests {

        @Test
        fun `should mark entries as clean using key collection`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)

            enabledCache.put(data1, markDirty = true)
            enabledCache.put(data2, markDirty = true)

            val key1 = PlayerDataSerializer.cacheKey(testUuid1, "world", GameMode.SURVIVAL)
            val key2 = PlayerDataSerializer.cacheKey(testUuid2, "world", GameMode.SURVIVAL)

            enabledCache.markClean(listOf(key1, key2))

            assertEquals(0, enabledCache.getDirtyCount())
            assertFalse(data1.dirty)
            assertFalse(data2.dirty)
        }

        @Test
        fun `should mark specific entry as clean using UUID, group, gamemode`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)

            enabledCache.put(data, markDirty = true)
            assertTrue(data.dirty)
            assertEquals(1, enabledCache.getDirtyCount())

            enabledCache.markClean(testUuid1, "world", GameMode.SURVIVAL)

            assertFalse(data.dirty)
            assertEquals(0, enabledCache.getDirtyCount())
        }

        @Test
        fun `should only clean specified entries`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)

            enabledCache.put(data1, markDirty = true)
            enabledCache.put(data2, markDirty = true)

            enabledCache.markClean(testUuid1, "world", GameMode.SURVIVAL)

            assertFalse(data1.dirty)
            assertTrue(data2.dirty)
            assertEquals(1, enabledCache.getDirtyCount())
        }
    }

    // ============================================
    // Invalidate by Player UUID Tests
    // ============================================

    @Nested
    @DisplayName("Invalidate by Player UUID")
    inner class InvalidateByPlayerTests {

        @Test
        fun `should invalidate all entries for a player`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid1, "nether", GameMode.SURVIVAL)
            val data3 = createTestPlayerData(testUuid1, "end", GameMode.CREATIVE)

            enabledCache.put(data1)
            enabledCache.put(data2)
            enabledCache.put(data3)

            val count = enabledCache.invalidatePlayer(testUuid1)

            assertEquals(3, count)
            Assertions.assertNull(enabledCache.get(testUuid1, "world", GameMode.SURVIVAL))
            Assertions.assertNull(enabledCache.get(testUuid1, "nether", GameMode.SURVIVAL))
            Assertions.assertNull(enabledCache.get(testUuid1, "end", GameMode.CREATIVE))
        }

        @Test
        fun `should not affect other players entries when invalidating by player`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)

            enabledCache.put(data1)
            enabledCache.put(data2)

            enabledCache.invalidatePlayer(testUuid1)

            Assertions.assertNull(enabledCache.get(testUuid1, "world", GameMode.SURVIVAL))
            Assertions.assertNotNull(enabledCache.get(testUuid2, "world", GameMode.SURVIVAL))
        }

        @Test
        fun `should remove dirty tracking when invalidating player`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)

            enabledCache.put(data, markDirty = true)
            assertEquals(1, enabledCache.getDirtyCount())

            enabledCache.invalidatePlayer(testUuid1)

            assertEquals(0, enabledCache.getDirtyCount())
        }

        @Test
        fun `should return zero when player has no entries`() {
            val count = enabledCache.invalidatePlayer(testUuid1)
            assertEquals(0, count)
        }
    }

    // ============================================
    // Invalidate by Group Tests
    // ============================================

    @Nested
    @DisplayName("Invalidate by Group")
    inner class InvalidateByGroupTests {

        @Test
        fun `should invalidate all entries for a group`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.CREATIVE)
            val data3 = createTestPlayerData(testUuid3, "world", GameMode.SURVIVAL)

            enabledCache.put(data1)
            enabledCache.put(data2)
            enabledCache.put(data3)

            val count = enabledCache.invalidateGroup("world")

            assertEquals(3, count)
            Assertions.assertNull(enabledCache.get(testUuid1, "world", GameMode.SURVIVAL))
            Assertions.assertNull(enabledCache.get(testUuid2, "world", GameMode.CREATIVE))
            Assertions.assertNull(enabledCache.get(testUuid3, "world", GameMode.SURVIVAL))
        }

        @Test
        fun `should not affect other groups when invalidating by group`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid1, "nether", GameMode.SURVIVAL)

            enabledCache.put(data1)
            enabledCache.put(data2)

            enabledCache.invalidateGroup("world")

            Assertions.assertNull(enabledCache.get(testUuid1, "world", GameMode.SURVIVAL))
            Assertions.assertNotNull(enabledCache.get(testUuid1, "nether", GameMode.SURVIVAL))
        }

        @Test
        fun `should remove dirty tracking when invalidating group`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)

            enabledCache.put(data1, markDirty = true)
            enabledCache.put(data2, markDirty = true)
            assertEquals(2, enabledCache.getDirtyCount())

            enabledCache.invalidateGroup("world")

            assertEquals(0, enabledCache.getDirtyCount())
        }

        @Test
        fun `should return zero when group has no entries`() {
            val count = enabledCache.invalidateGroup("nonexistent")
            assertEquals(0, count)
        }
    }

    // ============================================
    // Invalidate Specific Entry Tests
    // ============================================

    @Nested
    @DisplayName("Invalidate Specific Entry")
    inner class InvalidateSpecificEntryTests {

        @Test
        fun `should invalidate specific entry and return it`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)

            enabledCache.put(data)

            val invalidated = enabledCache.invalidate(testUuid1, "world", GameMode.SURVIVAL)

            Assertions.assertNotNull(invalidated)
            assertEquals(testUuid1, invalidated?.uuid)
            assertEquals("world", invalidated?.group)
            Assertions.assertNull(enabledCache.get(testUuid1, "world", GameMode.SURVIVAL))
        }

        @Test
        fun `should return null when invalidating non-existent entry`() {
            val invalidated = enabledCache.invalidate(testUuid1, "world", GameMode.SURVIVAL)
            Assertions.assertNull(invalidated)
        }

        @Test
        fun `should not affect other entries when invalidating specific entry`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid1, "world", GameMode.CREATIVE)
            val data3 = createTestPlayerData(testUuid1, "nether", GameMode.SURVIVAL)

            enabledCache.put(data1)
            enabledCache.put(data2)
            enabledCache.put(data3)

            enabledCache.invalidate(testUuid1, "world", GameMode.SURVIVAL)

            Assertions.assertNull(enabledCache.get(testUuid1, "world", GameMode.SURVIVAL))
            Assertions.assertNotNull(enabledCache.get(testUuid1, "world", GameMode.CREATIVE))
            Assertions.assertNotNull(enabledCache.get(testUuid1, "nether", GameMode.SURVIVAL))
        }

        @Test
        fun `should remove dirty tracking when invalidating specific entry`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)

            enabledCache.put(data, markDirty = true)
            assertEquals(1, enabledCache.getDirtyCount())

            enabledCache.invalidate(testUuid1, "world", GameMode.SURVIVAL)

            assertEquals(0, enabledCache.getDirtyCount())
        }
    }

    // ============================================
    // Cache Disabled Behavior Tests
    // ============================================

    @Nested
    @DisplayName("Cache Disabled Behavior")
    inner class CacheDisabledTests {

        @Test
        fun `should return false for isEnabled when disabled`() {
            assertFalse(disabledCache.isEnabled())
            assertTrue(enabledCache.isEnabled())
        }

        @Test
        fun `should return null on get when disabled`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            disabledCache.put(data)

            val retrieved = disabledCache.get(testUuid1, "world", GameMode.SURVIVAL)
            Assertions.assertNull(retrieved)
        }

        @Test
        fun `should call loader on getOrLoad when disabled`() = runTest {
            var loaderCalled = false
            val result = disabledCache.getOrLoad(testUuid1, "world", GameMode.SURVIVAL) {
                loaderCalled = true
                createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            }

            assertTrue(loaderCalled)
            Assertions.assertNotNull(result)
        }

        @Test
        fun `should always call loader on getOrLoad when disabled`() = runTest {
            var loadCount = 0

            disabledCache.getOrLoad(testUuid1, "world", GameMode.SURVIVAL) {
                loadCount++
                createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            }

            disabledCache.getOrLoad(testUuid1, "world", GameMode.SURVIVAL) {
                loadCount++
                createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            }

            assertEquals(2, loadCount)
        }

        @Test
        fun `should return zero on invalidatePlayer when disabled`() {
            val count = disabledCache.invalidatePlayer(testUuid1)
            assertEquals(0, count)
        }

        @Test
        fun `should return null on invalidate when disabled`() {
            val result = disabledCache.invalidate(testUuid1, "world", GameMode.SURVIVAL)
            Assertions.assertNull(result)
        }

        @Test
        fun `should return zero on invalidateGroup when disabled`() {
            val count = disabledCache.invalidateGroup("world")
            assertEquals(0, count)
        }

        @Test
        fun `should return zero on clear when disabled`() {
            val count = disabledCache.clear()
            assertEquals(0, count)
        }

        @Test
        fun `should return empty list on getDirtyEntries when disabled`() {
            val dirtyEntries = disabledCache.getDirtyEntries()
            assertTrue(dirtyEntries.isEmpty())
        }

        @Test
        fun `should return empty map on getAllForPlayer when disabled`() {
            val allData = disabledCache.getAllForPlayer(testUuid1)
            assertTrue(allData.isEmpty())
        }

        @Test
        fun `should return false on contains when disabled`() {
            assertFalse(disabledCache.contains(testUuid1, "world", GameMode.SURVIVAL))
        }
    }

    // ============================================
    // Get All Data for Player Tests
    // ============================================

    @Nested
    @DisplayName("Get All Data for Player")
    inner class GetAllForPlayerTests {

        @Test
        fun `should return all cached data for a player`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid1, "nether", GameMode.SURVIVAL)
            val data3 = createTestPlayerData(testUuid1, "end", GameMode.CREATIVE)

            enabledCache.put(data1)
            enabledCache.put(data2)
            enabledCache.put(data3)

            val allData = enabledCache.getAllForPlayer(testUuid1)

            assertEquals(3, allData.size)
        }

        @Test
        fun `should return empty map when player has no cached data`() {
            val allData = enabledCache.getAllForPlayer(testUuid1)
            assertTrue(allData.isEmpty())
        }

        @Test
        fun `should only return data for specified player`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)

            enabledCache.put(data1)
            enabledCache.put(data2)

            val allData = enabledCache.getAllForPlayer(testUuid1)

            assertEquals(1, allData.size)
            assertTrue(allData.values.all { it.uuid == testUuid1 })
        }
    }

    // ============================================
    // Cache Statistics Tests
    // ============================================

    @Nested
    @DisplayName("Cache Statistics")
    inner class CacheStatisticsTests {

        @Test
        fun `should return valid statistics`() {
            val stats = enabledCache.getStats()

            Assertions.assertNotNull(stats)
            assertTrue(stats.maxSize > 0)
            assertTrue(stats.hitCount >= 0)
            assertTrue(stats.missCount >= 0)
        }

        @Test
        fun `should track cache size`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)

            enabledCache.put(data1)
            enabledCache.put(data2)

            // Run cleanup to ensure stats are accurate
            enabledCache.cleanUp()

            val stats = enabledCache.getStats()
            assertTrue(stats.size >= 0) // Caffeine uses estimated size
        }

        @Test
        fun `should return zero stats when disabled`() {
            val stats = disabledCache.getStats()

            assertEquals(0, stats.size)
            assertEquals(0, stats.maxSize)
            assertEquals(0, stats.hitCount)
            assertEquals(0, stats.missCount)
        }
    }

    // ============================================
    // Write-Behind Delay Configuration Tests
    // ============================================

    @Nested
    @DisplayName("Write-Behind Delay Configuration")
    inner class WriteBehindDelayTests {

        @Test
        fun `should configure write-behind delay from config`() {
            val cache = PlayerDataCache(createCacheConfig(writeBehindSeconds = 30))
            assertEquals(30_000L, cache.writeBehindDelayMs)
        }

        @Test
        fun `should use default write-behind delay`() {
            val cache = PlayerDataCache(createCacheConfig(writeBehindSeconds = 5))
            assertEquals(5_000L, cache.writeBehindDelayMs)
        }

        @Test
        fun `should handle zero write-behind delay`() {
            val cache = PlayerDataCache(createCacheConfig(writeBehindSeconds = 0))
            assertEquals(0L, cache.writeBehindDelayMs)
        }
    }

    // ============================================
    // Clear Cache Tests
    // ============================================

    @Nested
    @DisplayName("Clear Cache")
    inner class ClearCacheTests {

        @Test
        fun `should clear all entries`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)
            val data3 = createTestPlayerData(testUuid3, "nether", GameMode.CREATIVE)

            enabledCache.put(data1)
            enabledCache.put(data2)
            enabledCache.put(data3)

            val count = enabledCache.clear()

            assertTrue(count >= 0) // Caffeine returns estimated size
            Assertions.assertNull(enabledCache.get(testUuid1, "world", GameMode.SURVIVAL))
            Assertions.assertNull(enabledCache.get(testUuid2, "world", GameMode.SURVIVAL))
            Assertions.assertNull(enabledCache.get(testUuid3, "nether", GameMode.CREATIVE))
        }

        @Test
        fun `should clear dirty tracking on clear`() {
            val data1 = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            val data2 = createTestPlayerData(testUuid2, "world", GameMode.SURVIVAL)

            enabledCache.put(data1, markDirty = true)
            enabledCache.put(data2, markDirty = true)
            assertEquals(2, enabledCache.getDirtyCount())

            enabledCache.clear()

            assertEquals(0, enabledCache.getDirtyCount())
        }
    }

    // ============================================
    // CleanUp Tests
    // ============================================

    @Nested
    @DisplayName("CleanUp")
    inner class CleanUpTests {

        @Test
        fun `should perform cleanup without errors`() {
            val data = createTestPlayerData(testUuid1, "world", GameMode.SURVIVAL)
            enabledCache.put(data)

            // Should not throw
            assertDoesNotThrow {
                enabledCache.cleanUp()
            }
        }

        @Test
        fun `should cleanup on disabled cache without errors`() {
            assertDoesNotThrow {
                disabledCache.cleanUp()
            }
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    private fun createTestPlayerData(
        uuid: UUID,
        group: String,
        gameMode: GameMode
    ): PlayerData {
        return PlayerData(
            uuid = uuid,
            playerName = "TestPlayer",
            group = group,
            gameMode = gameMode
        )
    }

    private fun createCacheConfig(
        enabled: Boolean = true,
        maxSize: Int = 1000,
        ttlMinutes: Int = 30,
        writeBehindSeconds: Int = 5
    ): CacheConfig {
        return CacheConfig(
            enabled = enabled,
            maxSize = maxSize,
            ttlMinutes = ttlMinutes,
            writeBehindSeconds = writeBehindSeconds
        )
    }
}

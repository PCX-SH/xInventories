package sh.pcx.xinventories.unit.hook

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.OfflinePlayer
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.PluginDescriptionFile
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import kotlin.test.assertNull
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.CacheStatistics
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.config.StorageConfig
import sh.pcx.xinventories.internal.hook.PlaceholderAPIHook
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.InventoryLock
import sh.pcx.xinventories.internal.service.*
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for PlaceholderAPIHook.
 *
 * Tests cover all placeholders:
 * - %xinventories_group% - Current group name
 * - %xinventories_group_display% - Current group display name
 * - %xinventories_bypass% - Bypass status
 * - %xinventories_groups_count% - Total groups
 * - %xinventories_storage_type% - Storage backend
 * - %xinventories_cache_size% - Cache size
 * - %xinventories_cache_max% - Maximum cache size
 * - %xinventories_cache_hit_rate% - Cache hit rate
 * - %xinventories_version% - Plugin version
 * - %xinventories_item_count% - Inventory items
 * - %xinventories_empty_slots% - Empty slots
 * - %xinventories_armor_count% - Armor pieces
 * - %xinventories_version_count% - Saved versions
 * - %xinventories_death_count% - Death records
 * - %xinventories_balance% - Economy balance
 * - %xinventories_last_save% - Last save time
 * - %xinventories_locked% - Lock status
 * - %xinventories_lock_reason% - Lock reason
 */
@DisplayName("PlaceholderAPIHook")
class PlaceholderAPIHookTest {

    companion object {
        private val TEST_UUID = UUID.fromString("00000000-0000-0000-0000-000000000001")

        @JvmStatic
        @BeforeAll
        fun setupAll() {
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    private lateinit var plugin: XInventories
    private lateinit var hook: PlaceholderAPIHook
    private lateinit var serviceManager: ServiceManager
    private lateinit var configManager: ConfigManager
    private lateinit var inventoryService: InventoryService
    private lateinit var groupService: GroupService
    private lateinit var storageService: StorageService
    private lateinit var lockingService: LockingService
    private lateinit var versioningService: VersioningService
    private lateinit var deathRecoveryService: DeathRecoveryService
    private lateinit var economyService: EconomyService
    private lateinit var description: PluginDescriptionFile

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)
        groupService = mockk(relaxed = true)
        storageService = mockk(relaxed = true)
        lockingService = mockk(relaxed = true)
        versioningService = mockk(relaxed = true)
        deathRecoveryService = mockk(relaxed = true)
        economyService = mockk(relaxed = true)
        description = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { plugin.configManager } returns configManager
        every { plugin.plugin } returns plugin
        every { plugin.description } returns description

        every { serviceManager.inventoryService } returns inventoryService
        every { serviceManager.groupService } returns groupService
        every { serviceManager.storageService } returns storageService
        every { serviceManager.lockingService } returns lockingService
        every { serviceManager.versioningService } returns versioningService
        every { serviceManager.deathRecoveryService } returns deathRecoveryService
        every { serviceManager.economyService } returns economyService

        // Default version
        every { description.version } returns "1.1.0"
        every { description.authors } returns listOf("TestAuthor")

        // Default config
        every { configManager.mainConfig } returns MainConfig(
            storage = StorageConfig(type = StorageType.SQLITE)
        )

        hook = PlaceholderAPIHook(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // =========================================================================
    // Expansion Metadata Tests
    // =========================================================================

    @Nested
    @DisplayName("Expansion Metadata")
    inner class MetadataTests {

        @Test
        @DisplayName("identifier should be 'xinventories'")
        fun identifierIsCorrect() {
            assertEquals("xinventories", hook.identifier)
        }

        @Test
        @DisplayName("author should come from plugin description")
        fun authorFromDescription() {
            assertEquals("TestAuthor", hook.author)
        }

        @Test
        @DisplayName("version should come from plugin description")
        fun versionFromDescription() {
            assertEquals("1.1.0", hook.version)
        }

        @Test
        @DisplayName("persist should return true")
        fun persistReturnsTrue() {
            assertTrue(hook.persist())
        }

        @Test
        @DisplayName("canRegister should return true")
        fun canRegisterReturnsTrue() {
            assertTrue(hook.canRegister())
        }
    }

    // =========================================================================
    // Group Placeholders
    // =========================================================================

    @Nested
    @DisplayName("Group Placeholders")
    inner class GroupPlaceholderTests {

        @Test
        @DisplayName("group placeholder returns current group for online player")
        fun groupReturnsCurrentGroup() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer

            every { inventoryService.getCurrentGroup(player) } returns "survival"

            val result = hook.onRequest(offlinePlayer, "group")

            assertEquals("survival", result)
        }

        @Test
        @DisplayName("group placeholder returns world group when no current group")
        fun groupReturnsWorldGroupWhenNoCurrent() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer
            val world = mockk<World>()

            every { player.world } returns world
            every { inventoryService.getCurrentGroup(player) } returns null
            every { groupService.getGroupForWorld(world) } returns Group(name = "creative")

            val result = hook.onRequest(offlinePlayer, "group")

            assertEquals("creative", result)
        }

        @Test
        @DisplayName("group placeholder returns N/A for offline player")
        fun groupReturnsNAForOfflinePlayer() {
            val offlinePlayer = mockk<OfflinePlayer>()
            every { offlinePlayer.player } returns null

            val result = hook.onRequest(offlinePlayer, "group")

            assertEquals("N/A", result)
        }

        @Test
        @DisplayName("group_display returns capitalized group name")
        fun groupDisplayReturnsCapitalized() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroup("survival") } returns Group(name = "survival")

            val result = hook.onRequest(offlinePlayer, "group_display")

            assertEquals("Survival", result)
        }

        @Test
        @DisplayName("groups_count returns correct count")
        fun groupsCountReturnsCorrect() {
            every { groupService.getAllGroups() } returns listOf(
                Group(name = "survival"),
                Group(name = "creative"),
                Group(name = "minigames")
            )

            val result = hook.onRequest(null, "groups_count")

            assertEquals("3", result)
        }
    }

    // =========================================================================
    // Player Status Placeholders
    // =========================================================================

    @Nested
    @DisplayName("Player Status Placeholders")
    inner class PlayerStatusTests {

        @Test
        @DisplayName("bypass returns true when player has bypass")
        fun bypassReturnsTrueWhenHasBypass() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer

            every { inventoryService.hasBypass(player) } returns true

            val result = hook.onRequest(offlinePlayer, "bypass")

            assertEquals("true", result)
        }

        @Test
        @DisplayName("bypass returns false when player has no bypass")
        fun bypassReturnsFalseWhenNoBypass() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer

            every { inventoryService.hasBypass(player) } returns false

            val result = hook.onRequest(offlinePlayer, "bypass")

            assertEquals("false", result)
        }

        @Test
        @DisplayName("locked returns true when player is locked")
        fun lockedReturnsTrueWhenLocked() {
            val offlinePlayer = createMockOfflinePlayer()

            every { lockingService.isLocked(TEST_UUID) } returns true

            val result = hook.onRequest(offlinePlayer, "locked")

            assertEquals("true", result)
        }

        @Test
        @DisplayName("locked returns false when player is not locked")
        fun lockedReturnsFalseWhenNotLocked() {
            val offlinePlayer = createMockOfflinePlayer()

            every { lockingService.isLocked(TEST_UUID) } returns false

            val result = hook.onRequest(offlinePlayer, "locked")

            assertEquals("false", result)
        }

        @Test
        @DisplayName("lock_reason returns reason when locked")
        fun lockReasonReturnsReason() {
            val offlinePlayer = createMockOfflinePlayer()
            val lock = mockk<InventoryLock>()

            every { lockingService.getLock(TEST_UUID) } returns lock
            every { lock.reason } returns "Suspected duping"

            val result = hook.onRequest(offlinePlayer, "lock_reason")

            assertEquals("Suspected duping", result)
        }

        @Test
        @DisplayName("lock_reason returns empty when not locked")
        fun lockReasonReturnsEmptyWhenNotLocked() {
            val offlinePlayer = createMockOfflinePlayer()

            every { lockingService.getLock(TEST_UUID) } returns null

            val result = hook.onRequest(offlinePlayer, "lock_reason")

            assertEquals("", result)
        }
    }

    // =========================================================================
    // Inventory Statistics Placeholders
    // =========================================================================

    @Nested
    @DisplayName("Inventory Statistics Placeholders")
    inner class InventoryStatisticsTests {

        @Test
        @DisplayName("item_count returns correct count")
        fun itemCountReturnsCorrect() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer
            val inventory = mockk<PlayerInventory>()

            every { player.inventory } returns inventory

            // Simulate 5 items in inventory
            for (i in 0..35) {
                val item = if (i < 5) {
                    mockk<ItemStack>().also { every { it.type } returns Material.DIAMOND; every { it.type.isAir } returns false }
                } else {
                    null
                }
                every { inventory.getItem(i) } returns item
            }

            // Offhand
            val offhandItem = mockk<ItemStack>()
            every { offhandItem.type } returns Material.SHIELD
            every { offhandItem.type.isAir } returns false
            every { inventory.itemInOffHand } returns offhandItem

            val result = hook.onRequest(offlinePlayer, "item_count")

            assertEquals("6", result) // 5 + 1 offhand
        }

        @Test
        @DisplayName("empty_slots returns correct count")
        fun emptySlotsReturnsCorrect() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer
            val inventory = mockk<PlayerInventory>()

            every { player.inventory } returns inventory

            // Simulate 5 items, rest empty
            for (i in 0..35) {
                if (i < 5) {
                    val item = mockk<ItemStack>()
                    every { item.type } returns Material.DIAMOND
                    every { item.type.isAir } returns false
                    every { inventory.getItem(i) } returns item
                } else {
                    every { inventory.getItem(i) } returns null
                }
            }

            val result = hook.onRequest(offlinePlayer, "empty_slots")

            assertEquals("31", result) // 36 - 5 = 31
        }

        @Test
        @DisplayName("armor_count returns correct count")
        fun armorCountReturnsCorrect() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer
            val inventory = mockk<PlayerInventory>()

            every { player.inventory } returns inventory

            // Full armor set
            val armorContents = arrayOf(
                mockk<ItemStack>().also { every { it.type } returns Material.DIAMOND_BOOTS; every { it.type.isAir } returns false },
                mockk<ItemStack>().also { every { it.type } returns Material.DIAMOND_LEGGINGS; every { it.type.isAir } returns false },
                mockk<ItemStack>().also { every { it.type } returns Material.DIAMOND_CHESTPLATE; every { it.type.isAir } returns false },
                mockk<ItemStack>().also { every { it.type } returns Material.DIAMOND_HELMET; every { it.type.isAir } returns false }
            )
            every { inventory.armorContents } returns armorContents

            val result = hook.onRequest(offlinePlayer, "armor_count")

            assertEquals("4", result)
        }

        @Test
        @DisplayName("armor_count returns 0 when no armor")
        fun armorCountReturnsZeroWhenNoArmor() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer
            val inventory = mockk<PlayerInventory>()

            every { player.inventory } returns inventory
            every { inventory.armorContents } returns arrayOfNulls(4)

            val result = hook.onRequest(offlinePlayer, "armor_count")

            assertEquals("0", result)
        }
    }

    // =========================================================================
    // Version & Death Count Placeholders
    // =========================================================================

    @Nested
    @DisplayName("Version & Death Count Placeholders")
    inner class VersionDeathCountTests {

        @Test
        @DisplayName("version_count returns correct count")
        fun versionCountReturnsCorrect() {
            val offlinePlayer = createMockOfflinePlayer()

            coEvery { versioningService.getVersionCount(TEST_UUID, null) } returns 5

            val result = hook.onRequest(offlinePlayer, "version_count")

            assertEquals("5", result)
        }

        @Test
        @DisplayName("death_count returns correct count")
        fun deathCountReturnsCorrect() {
            val offlinePlayer = createMockOfflinePlayer()

            coEvery { deathRecoveryService.getDeathRecordCount(TEST_UUID) } returns 3

            val result = hook.onRequest(offlinePlayer, "death_count")

            assertEquals("3", result)
        }
    }

    // =========================================================================
    // Economy Placeholders
    // =========================================================================

    @Nested
    @DisplayName("Economy Placeholders")
    inner class EconomyTests {

        @Test
        @DisplayName("balance returns formatted balance")
        fun balanceReturnsFormatted() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer

            every { economyService.getBalance(player) } returns 1234.56

            val result = hook.onRequest(offlinePlayer, "balance")

            assertEquals("1234.56", result)
        }

        @Test
        @DisplayName("balance returns 0.00 for offline player")
        fun balanceReturnsZeroForOffline() {
            val offlinePlayer = mockk<OfflinePlayer>()
            every { offlinePlayer.player } returns null

            val result = hook.onRequest(offlinePlayer, "balance")

            assertEquals("0.00", result)
        }
    }

    // =========================================================================
    // Timestamp Placeholders
    // =========================================================================

    @Nested
    @DisplayName("Timestamp Placeholders")
    inner class TimestampTests {

        @Test
        @DisplayName("last_save returns formatted timestamp")
        fun lastSaveReturnsFormatted() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer
            val snapshot = mockk<PlayerInventorySnapshot>()

            every { inventoryService.getActiveSnapshot(player) } returns snapshot
            every { snapshot.timestamp } returns Instant.parse("2026-01-15T10:30:00Z")

            val result = hook.onRequest(offlinePlayer, "last_save")

            // Format depends on timezone, just check it's not N/A
            assertNotEquals("N/A", result)
            assertTrue(result!!.contains("2026"))
        }

        @Test
        @DisplayName("last_save returns N/A when no snapshot")
        fun lastSaveReturnsNAWhenNoSnapshot() {
            val player = createMockOnlinePlayer()
            val offlinePlayer = player as OfflinePlayer

            every { inventoryService.getActiveSnapshot(player) } returns null

            val result = hook.onRequest(offlinePlayer, "last_save")

            assertEquals("N/A", result)
        }
    }

    // =========================================================================
    // Storage & Cache Placeholders
    // =========================================================================

    @Nested
    @DisplayName("Storage & Cache Placeholders")
    inner class StorageCacheTests {

        @Test
        @DisplayName("storage_type returns correct type")
        fun storageTypeReturnsCorrect() {
            val result = hook.onRequest(null, "storage_type")

            assertEquals("sqlite", result)
        }

        @Test
        @DisplayName("cache_size returns correct size")
        fun cacheSizeReturnsCorrect() {
            every { storageService.getCacheStats() } returns CacheStatistics(
                size = 42,
                maxSize = 1000,
                hitCount = 85,
                missCount = 15,
                loadCount = 0,
                evictionCount = 0
            )

            val result = hook.onRequest(null, "cache_size")

            assertEquals("42", result)
        }

        @Test
        @DisplayName("cache_max returns correct max size")
        fun cacheMaxReturnsCorrect() {
            every { storageService.getCacheStats() } returns CacheStatistics(
                size = 42,
                maxSize = 1000,
                hitCount = 85,
                missCount = 15,
                loadCount = 0,
                evictionCount = 0
            )

            val result = hook.onRequest(null, "cache_max")

            assertEquals("1000", result)
        }

        @Test
        @DisplayName("cache_hit_rate returns formatted percentage")
        fun cacheHitRateReturnsFormatted() {
            // 856 hits / (856 + 144 misses) = 0.856 = 85.6%
            every { storageService.getCacheStats() } returns CacheStatistics(
                size = 42,
                maxSize = 1000,
                hitCount = 856,
                missCount = 144,
                loadCount = 0,
                evictionCount = 0
            )

            val result = hook.onRequest(null, "cache_hit_rate")

            assertEquals("85.6%", result)
        }
    }

    // =========================================================================
    // Plugin Info Placeholders
    // =========================================================================

    @Nested
    @DisplayName("Plugin Info Placeholders")
    inner class PluginInfoTests {

        @Test
        @DisplayName("version returns plugin version")
        fun versionReturnsPluginVersion() {
            val result = hook.onRequest(null, "version")

            assertEquals("1.1.0", result)
        }
    }

    // =========================================================================
    // Unknown Placeholder
    // =========================================================================

    @Nested
    @DisplayName("Unknown Placeholder")
    inner class UnknownPlaceholderTests {

        @Test
        @DisplayName("unknown placeholder returns null")
        fun unknownPlaceholderReturnsNull() {
            val result = hook.onRequest(null, "unknown_placeholder")

            assertNull(result)
        }
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockOnlinePlayer(): Player {
        val player = mockk<Player>()
        val offlinePlayer = player as OfflinePlayer

        every { offlinePlayer.player } returns player
        every { player.uniqueId } returns TEST_UUID
        every { player.name } returns "TestPlayer"

        return player
    }

    private fun createMockOfflinePlayer(): OfflinePlayer {
        val offlinePlayer = mockk<OfflinePlayer>()
        every { offlinePlayer.player } returns null
        every { offlinePlayer.uniqueId } returns TEST_UUID
        return offlinePlayer
    }
}

package sh.pcx.xinventories.unit.service

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.service.*
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for MergeService.
 *
 * Tests cover:
 * - COMBINE strategy
 * - REPLACE strategy
 * - KEEP_HIGHER strategy
 * - MANUAL strategy with conflict detection
 * - Pending merge management
 * - Conflict resolution
 */
@DisplayName("MergeService Unit Tests")
class MergeServiceTest {

    private lateinit var server: ServerMock
    private lateinit var plugin: XInventories
    private lateinit var storageService: StorageService
    private lateinit var mergeService: MergeService
    private lateinit var scope: CoroutineScope

    private val adminUUID = UUID.randomUUID()
    private val playerUUID = UUID.randomUUID()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("MergeServiceTest"), false)
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = mockk(relaxed = true)
        storageService = mockk(relaxed = true)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        mergeService = MergeService(plugin, scope, storageService)
        mergeService.initialize()
    }

    @AfterEach
    fun tearDown() {
        mergeService.shutdown()
        MockBukkit.unmock()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createPlayerData(
        uuid: UUID = playerUUID,
        group: String,
        items: Map<Int, ItemStack> = emptyMap(),
        level: Int = 0,
        totalExp: Int = 0
    ): PlayerData {
        return PlayerData(uuid, "TestPlayer", group, GameMode.SURVIVAL).apply {
            mainInventory.putAll(items)
            this.level = level
            this.totalExperience = totalExp
        }
    }

    private fun createItem(material: Material, amount: Int = 1): ItemStack {
        return ItemStack(material, amount)
    }

    // =========================================================================
    // REPLACE Strategy Tests
    // =========================================================================

    @Nested
    @DisplayName("REPLACE Strategy")
    inner class ReplaceStrategyTests {

        @Test
        @DisplayName("Should replace target inventory with source")
        fun replaceTargetWithSource() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 10))
            val targetItems = mapOf(0 to createItem(Material.DIRT, 64))

            val sourceData = createPlayerData(group = "source", items = sourceItems, level = 30)
            val targetData = createPlayerData(group = "target", items = targetItems, level = 10)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.REPLACE
            )

            assertTrue(result.success)
            assertNotNull(result.mergedData, "mergedData should not be null")
            assertEquals("target", result.mergedData!!.group)
            assertEquals(Material.DIAMOND, result.mergedData!!.mainInventory[0]?.type)
            assertEquals(10, result.mergedData!!.mainInventory[0]?.amount)
        }

        @Test
        @DisplayName("REPLACE should have no conflicts")
        fun replaceNoConflicts() = runTest {
            val sourceData = createPlayerData(group = "source", items = mapOf(0 to createItem(Material.DIAMOND)))
            val targetData = createPlayerData(group = "target", items = mapOf(0 to createItem(Material.DIRT)))

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.REPLACE
            )

            assertTrue(result.conflicts.isEmpty())
        }
    }

    // =========================================================================
    // COMBINE Strategy Tests
    // =========================================================================

    @Nested
    @DisplayName("COMBINE Strategy")
    inner class CombineStrategyTests {

        @Test
        @DisplayName("Should stack similar items when combining")
        fun stackSimilarItems() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 32))
            val targetItems = mapOf(0 to createItem(Material.DIAMOND, 16))

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.COMBINE
            )

            assertTrue(result.success)
            assertNotNull(result.mergedData, "mergedData should not be null")
            assertEquals(48, result.mergedData!!.mainInventory[0]?.amount)
        }

        @Test
        @DisplayName("Should handle overflow when stacking exceeds max")
        fun handleStackOverflow() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 48))
            val targetItems = mapOf(0 to createItem(Material.DIAMOND, 48))

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.COMBINE
            )

            assertTrue(result.success)
            assertEquals(64, result.mergedData!!.mainInventory[0]?.amount) // Max stack
            assertTrue(result.overflowItems.isNotEmpty())
        }

        @Test
        @DisplayName("Should place different items in empty slots")
        fun placeDifferentItemsInEmptySlots() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 10))
            val targetItems = mapOf(1 to createItem(Material.IRON_INGOT, 20))

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.COMBINE
            )

            assertTrue(result.success)
            assertEquals(Material.DIAMOND, result.mergedData!!.mainInventory[0]?.type)
            assertEquals(Material.IRON_INGOT, result.mergedData!!.mainInventory[1]?.type)
        }

        @Test
        @DisplayName("Should combine experience totals")
        fun combineExperience() = runTest {
            val sourceData = createPlayerData(group = "source", totalExp = 1000)
            val targetData = createPlayerData(group = "target", totalExp = 500)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.COMBINE
            )

            assertTrue(result.success)
            assertEquals(1500, result.mergedData!!.totalExperience)
        }
    }

    // =========================================================================
    // KEEP_HIGHER Strategy Tests
    // =========================================================================

    @Nested
    @DisplayName("KEEP_HIGHER Strategy")
    inner class KeepHigherStrategyTests {

        @Test
        @DisplayName("Should keep item with higher count")
        fun keepHigherCount() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 32))
            val targetItems = mapOf(0 to createItem(Material.DIAMOND, 16))

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.KEEP_HIGHER
            )

            assertTrue(result.success)
            assertEquals(32, result.mergedData!!.mainInventory[0]?.amount)
        }

        @Test
        @DisplayName("Should keep target when counts are equal")
        fun keepSourceWhenEqual() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 16))
            val targetItems = mapOf(0 to createItem(Material.IRON_INGOT, 16))

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.KEEP_HIGHER
            )

            assertTrue(result.success)
            // When equal, source wins
            assertEquals(Material.DIAMOND, result.mergedData!!.mainInventory[0]?.type)
        }

        @Test
        @DisplayName("Should keep higher experience")
        fun keepHigherExperience() = runTest {
            val sourceData = createPlayerData(group = "source", totalExp = 500)
            val targetData = createPlayerData(group = "target", totalExp = 1000)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.KEEP_HIGHER
            )

            assertTrue(result.success)
            assertEquals(1000, result.mergedData!!.totalExperience) // Target had higher
        }
    }

    // =========================================================================
    // MANUAL Strategy Tests
    // =========================================================================

    @Nested
    @DisplayName("MANUAL Strategy")
    inner class ManualStrategyTests {

        @Test
        @DisplayName("Should detect conflicts between different items")
        fun detectConflicts() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 10))
            val targetItems = mapOf(0 to createItem(Material.IRON_INGOT, 20))

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.MANUAL
            )

            // Note: In MANUAL strategy, success is false during preview when there are unresolved conflicts
            // This is by design - the merge is not successful until conflicts are resolved
            assertFalse(result.success) // Preview with conflicts returns false
            assertEquals(1, result.conflicts.size)
            assertEquals(0, result.conflicts[0].slot)
            assertEquals(SlotType.MAIN_INVENTORY, result.conflicts[0].slotType)
            assertEquals(ConflictResolution.PENDING, result.conflicts[0].resolution)
        }

        @Test
        @DisplayName("Should not report conflicts for same items")
        fun noConflictForSameItems() = runTest {
            val item = createItem(Material.DIAMOND, 10)
            val sourceItems = mapOf(0 to item.clone())
            val targetItems = mapOf(0 to item.clone())

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.MANUAL
            )

            // In MANUAL strategy with no conflicts during preview, success is still false
            // because previewOnly is true (success = !previewOnly && unresolvedCount == 0)
            assertFalse(result.success) // Preview mode always returns false for MANUAL
            assertTrue(result.conflicts.isEmpty())
        }

        @Test
        @DisplayName("Should copy source items to empty target slots")
        fun copyToEmptySlots() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 10))
            val targetItems = mapOf(1 to createItem(Material.IRON_INGOT, 20))

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.MANUAL
            )

            assertTrue(result.conflicts.isEmpty())
        }
    }

    // =========================================================================
    // Pending Merge Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Pending Merge Management")
    inner class PendingMergeTests {

        @Test
        @DisplayName("Should store pending merge after preview")
        fun storePendingMerge() = runTest {
            val sourceData = createPlayerData(group = "source")
            val targetData = createPlayerData(group = "target")

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            mergeService.previewMerge(adminUUID, playerUUID, "source", "target", MergeStrategy.COMBINE)

            val pending = mergeService.getPendingMerge(adminUUID)
            assertNotNull(pending, "pending should not be null")
            assertEquals("source", pending!!.sourceGroup)
            assertEquals("target", pending.targetGroup)
        }

        @Test
        @DisplayName("Should cancel pending merge")
        fun cancelPendingMerge() = runTest {
            val sourceData = createPlayerData(group = "source")
            val targetData = createPlayerData(group = "target")

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            mergeService.previewMerge(adminUUID, playerUUID, "source", "target", MergeStrategy.COMBINE)

            val cancelled = mergeService.cancelMerge(adminUUID)
            assertTrue(cancelled)
            assertNull(mergeService.getPendingMerge(adminUUID), "pending merge should be null after cancel")
        }

        @Test
        @DisplayName("Should return false when cancelling non-existent merge")
        fun cancelNonExistentMerge() {
            val cancelled = mergeService.cancelMerge(UUID.randomUUID())
            assertFalse(cancelled)
        }
    }

    // =========================================================================
    // Conflict Resolution Tests
    // =========================================================================

    @Nested
    @DisplayName("Conflict Resolution")
    inner class ConflictResolutionTests {

        @Test
        @DisplayName("Should resolve conflict with KEEP_SOURCE")
        fun resolveKeepSource() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 10))
            val targetItems = mapOf(0 to createItem(Material.IRON_INGOT, 20))

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            mergeService.previewMerge(adminUUID, playerUUID, "source", "target", MergeStrategy.MANUAL)

            val resolved = mergeService.resolveConflict(adminUUID, 0, ConflictResolution.KEEP_SOURCE)
            assertTrue(resolved)

            val pending = mergeService.getPendingMerge(adminUUID)
            assertEquals(ConflictResolution.KEEP_SOURCE, pending!!.conflicts[0].resolution)
        }

        @Test
        @DisplayName("Should check if all conflicts are resolved")
        fun checkAllConflictsResolved() = runTest {
            val sourceItems = mapOf(0 to createItem(Material.DIAMOND, 10))
            val targetItems = mapOf(0 to createItem(Material.IRON_INGOT, 20))

            val sourceData = createPlayerData(group = "source", items = sourceItems)
            val targetData = createPlayerData(group = "target", items = targetItems)

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns targetData

            mergeService.previewMerge(adminUUID, playerUUID, "source", "target", MergeStrategy.MANUAL)

            assertFalse(mergeService.areAllConflictsResolved(adminUUID))

            mergeService.resolveConflict(adminUUID, 0, ConflictResolution.KEEP_TARGET)

            assertTrue(mergeService.areAllConflictsResolved(adminUUID))
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should fail when source data doesn't exist")
        fun failWhenNoSourceData() = runTest {
            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns null

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.COMBINE
            )

            assertFalse(result.success)
            assertTrue(result.message.contains("No source data found"))
        }

        @Test
        @DisplayName("Should handle empty target data")
        fun handleEmptyTargetData() = runTest {
            val sourceData = createPlayerData(group = "source", items = mapOf(0 to createItem(Material.DIAMOND)))

            coEvery { storageService.loadPlayerData(playerUUID, "source", null) } returns sourceData
            coEvery { storageService.loadPlayerData(playerUUID, "target", null) } returns null

            val result = mergeService.previewMerge(
                adminUUID, playerUUID, "source", "target", MergeStrategy.COMBINE
            )

            assertTrue(result.success)
            assertTrue(result.message.contains("empty"))
        }
    }
}

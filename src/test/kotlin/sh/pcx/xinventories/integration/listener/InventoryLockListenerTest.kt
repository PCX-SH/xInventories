package sh.pcx.xinventories.integration.listener

import io.mockk.*
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.LockingConfig
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.listener.InventoryLockListener
import sh.pcx.xinventories.internal.service.LockingService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for InventoryLockListener.
 *
 * Tests cover:
 * - Inventory click blocking for locked players
 * - Inventory drag blocking for locked players
 * - Item drop blocking for locked players
 * - Item pickup blocking for locked players
 * - Hand swap blocking for locked players
 * - Bypass permission handling
 * - Lock message display
 */
@DisplayName("InventoryLockListener Tests")
class InventoryLockListenerTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.severe(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    private lateinit var server: ServerMock
    private lateinit var plugin: XInventories
    private lateinit var lockingService: LockingService
    private lateinit var serviceManager: ServiceManager
    private lateinit var configManager: ConfigManager
    private lateinit var listener: InventoryLockListener

    private val testUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()

        // Create mocks
        plugin = mockk(relaxed = true)
        lockingService = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        configManager = mockk(relaxed = true)

        // Configure plugin mock
        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.lockingService } returns lockingService
        every { plugin.configManager } returns configManager
        every { plugin.plugin } returns plugin
        every { plugin.server } returns server

        // Default config with locking enabled
        val mainConfig = MainConfig(
            locking = LockingConfig(enabled = true, allowAdminBypass = true)
        )
        every { configManager.mainConfig } returns mainConfig

        // Default: player is not locked and cannot bypass
        every { lockingService.isLocked(any<UUID>()) } returns false
        every { lockingService.canBypass(any()) } returns false

        // Create listener
        listener = InventoryLockListener(plugin)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
        clearAllMocks()
    }

    // ============================================================
    // Inventory Click Tests
    // ============================================================

    @Nested
    @DisplayName("Inventory Click Blocking")
    inner class InventoryClickBlockingTests {

        @Test
        @DisplayName("should cancel inventory click for locked player")
        fun shouldCancelClickForLockedPlayer() {
            val player = server.addPlayer()
            val event = createInventoryClickEvent(player)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns false

            listener.onInventoryClick(event)

            assertTrue(event.isCancelled)
            verify { lockingService.showLockMessage(player) }
            verify { Logging.debug(any<() -> String>()) }
        }

        @Test
        @DisplayName("should not cancel inventory click for unlocked player")
        fun shouldNotCancelClickForUnlockedPlayer() {
            val player = server.addPlayer()
            val event = createInventoryClickEvent(player)

            every { lockingService.isLocked(player.uniqueId) } returns false

            listener.onInventoryClick(event)

            assertFalse(event.isCancelled)
            verify(exactly = 0) { lockingService.showLockMessage(any()) }
        }

        @Test
        @DisplayName("should not cancel inventory click for player with bypass")
        fun shouldNotCancelClickForPlayerWithBypass() {
            val player = server.addPlayer()
            val event = createInventoryClickEvent(player)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns true

            listener.onInventoryClick(event)

            assertFalse(event.isCancelled)
            verify(exactly = 0) { lockingService.showLockMessage(any()) }
        }

        @Test
        @DisplayName("should not cancel when locking is disabled")
        fun shouldNotCancelWhenLockingDisabled() {
            val player = server.addPlayer()
            val event = createInventoryClickEvent(player)

            val mainConfig = MainConfig(
                locking = LockingConfig(enabled = false)
            )
            every { configManager.mainConfig } returns mainConfig

            every { lockingService.isLocked(player.uniqueId) } returns true

            listener.onInventoryClick(event)

            assertFalse(event.isCancelled)
        }

        @Test
        @DisplayName("should ignore non-player entities")
        fun shouldIgnoreNonPlayerEntities() {
            val nonPlayerEntity = mockk<org.bukkit.entity.Villager>(relaxed = true)
            val view = mockk<InventoryView>(relaxed = true)
            val inventory = mockk<Inventory>(relaxed = true)

            every { view.topInventory } returns inventory
            every { view.bottomInventory } returns inventory
            every { view.type } returns InventoryType.CHEST

            val event = InventoryClickEvent(
                view,
                InventoryType.SlotType.CONTAINER,
                0,
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
            )

            // Event with non-player whoClicked should be ignored
            listener.onInventoryClick(event)

            assertFalse(event.isCancelled)
            verify(exactly = 0) { lockingService.isLocked(any<UUID>()) }
        }
    }

    // ============================================================
    // Inventory Drag Tests
    // ============================================================

    @Nested
    @DisplayName("Inventory Drag Blocking")
    inner class InventoryDragBlockingTests {

        @Test
        @DisplayName("should cancel inventory drag for locked player")
        fun shouldCancelDragForLockedPlayer() {
            val player = server.addPlayer()
            val event = createInventoryDragEvent(player)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns false

            listener.onInventoryDrag(event)

            assertTrue(event.isCancelled)
            verify { lockingService.showLockMessage(player) }
        }

        @Test
        @DisplayName("should not cancel inventory drag for unlocked player")
        fun shouldNotCancelDragForUnlockedPlayer() {
            val player = server.addPlayer()
            val event = createInventoryDragEvent(player)

            every { lockingService.isLocked(player.uniqueId) } returns false

            listener.onInventoryDrag(event)

            assertFalse(event.isCancelled)
        }

        @Test
        @DisplayName("should not cancel inventory drag for player with bypass")
        fun shouldNotCancelDragForPlayerWithBypass() {
            val player = server.addPlayer()
            val event = createInventoryDragEvent(player)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns true

            listener.onInventoryDrag(event)

            assertFalse(event.isCancelled)
        }
    }

    // ============================================================
    // Item Drop Tests
    // ============================================================

    @Nested
    @DisplayName("Item Drop Blocking")
    inner class ItemDropBlockingTests {

        @Test
        @DisplayName("should cancel item drop for locked player")
        fun shouldCancelDropForLockedPlayer() {
            val player = server.addPlayer()
            val droppedItem = mockk<org.bukkit.entity.Item>(relaxed = true)
            val event = PlayerDropItemEvent(player, droppedItem)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns false

            listener.onPlayerDropItem(event)

            assertTrue(event.isCancelled)
            verify { lockingService.showLockMessage(player) }
        }

        @Test
        @DisplayName("should not cancel item drop for unlocked player")
        fun shouldNotCancelDropForUnlockedPlayer() {
            val player = server.addPlayer()
            val droppedItem = mockk<org.bukkit.entity.Item>(relaxed = true)
            val event = PlayerDropItemEvent(player, droppedItem)

            every { lockingService.isLocked(player.uniqueId) } returns false

            listener.onPlayerDropItem(event)

            assertFalse(event.isCancelled)
        }

        @Test
        @DisplayName("should not cancel item drop for player with bypass")
        fun shouldNotCancelDropForPlayerWithBypass() {
            val player = server.addPlayer()
            val droppedItem = mockk<org.bukkit.entity.Item>(relaxed = true)
            val event = PlayerDropItemEvent(player, droppedItem)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns true

            listener.onPlayerDropItem(event)

            assertFalse(event.isCancelled)
        }
    }

    // ============================================================
    // Item Pickup Tests
    // ============================================================

    @Nested
    @DisplayName("Item Pickup Blocking")
    inner class ItemPickupBlockingTests {

        @Test
        @DisplayName("should cancel item pickup for locked player")
        fun shouldCancelPickupForLockedPlayer() {
            val player = server.addPlayer()
            val item = mockk<org.bukkit.entity.Item>(relaxed = true)
            val event = EntityPickupItemEvent(player, item, 0)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns false

            listener.onPlayerPickupItem(event)

            assertTrue(event.isCancelled)
            // Note: showLockMessage is NOT called for pickup to avoid spam
            verify(exactly = 0) { lockingService.showLockMessage(any()) }
        }

        @Test
        @DisplayName("should not cancel item pickup for unlocked player")
        fun shouldNotCancelPickupForUnlockedPlayer() {
            val player = server.addPlayer()
            val item = mockk<org.bukkit.entity.Item>(relaxed = true)
            val event = EntityPickupItemEvent(player, item, 0)

            every { lockingService.isLocked(player.uniqueId) } returns false

            listener.onPlayerPickupItem(event)

            assertFalse(event.isCancelled)
        }

        @Test
        @DisplayName("should not cancel item pickup for player with bypass")
        fun shouldNotCancelPickupForPlayerWithBypass() {
            val player = server.addPlayer()
            val item = mockk<org.bukkit.entity.Item>(relaxed = true)
            val event = EntityPickupItemEvent(player, item, 0)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns true

            listener.onPlayerPickupItem(event)

            assertFalse(event.isCancelled)
        }

        @Test
        @DisplayName("should ignore non-player entities for pickup")
        fun shouldIgnoreNonPlayerEntitiesForPickup() {
            val zombie = mockk<org.bukkit.entity.Zombie>(relaxed = true)
            val item = mockk<org.bukkit.entity.Item>(relaxed = true)
            val event = EntityPickupItemEvent(zombie, item, 0)

            listener.onPlayerPickupItem(event)

            assertFalse(event.isCancelled)
            verify(exactly = 0) { lockingService.isLocked(any<UUID>()) }
        }
    }

    // ============================================================
    // Hand Swap Tests
    // ============================================================

    @Nested
    @DisplayName("Hand Swap Blocking")
    inner class HandSwapBlockingTests {

        @Test
        @DisplayName("should cancel hand swap for locked player")
        fun shouldCancelSwapForLockedPlayer() {
            val player = server.addPlayer()
            val mainHand = mockk<ItemStack>(relaxed = true)
            val offHand = mockk<ItemStack>(relaxed = true)
            val event = PlayerSwapHandItemsEvent(player, mainHand, offHand)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns false

            listener.onPlayerSwapHandItems(event)

            assertTrue(event.isCancelled)
            verify { lockingService.showLockMessage(player) }
        }

        @Test
        @DisplayName("should not cancel hand swap for unlocked player")
        fun shouldNotCancelSwapForUnlockedPlayer() {
            val player = server.addPlayer()
            val mainHand = mockk<ItemStack>(relaxed = true)
            val offHand = mockk<ItemStack>(relaxed = true)
            val event = PlayerSwapHandItemsEvent(player, mainHand, offHand)

            every { lockingService.isLocked(player.uniqueId) } returns false

            listener.onPlayerSwapHandItems(event)

            assertFalse(event.isCancelled)
        }

        @Test
        @DisplayName("should not cancel hand swap for player with bypass")
        fun shouldNotCancelSwapForPlayerWithBypass() {
            val player = server.addPlayer()
            val mainHand = mockk<ItemStack>(relaxed = true)
            val offHand = mockk<ItemStack>(relaxed = true)
            val event = PlayerSwapHandItemsEvent(player, mainHand, offHand)

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns true

            listener.onPlayerSwapHandItems(event)

            assertFalse(event.isCancelled)
        }
    }

    // ============================================================
    // Event Priority Tests
    // ============================================================

    @Nested
    @DisplayName("Event Priority")
    inner class EventPriorityTests {

        @Test
        @DisplayName("all handlers have HIGHEST priority")
        fun allHandlersHaveHighestPriority() {
            val methods = listOf(
                "onInventoryClick" to InventoryClickEvent::class.java,
                "onInventoryDrag" to InventoryDragEvent::class.java,
                "onPlayerDropItem" to PlayerDropItemEvent::class.java,
                "onPlayerPickupItem" to EntityPickupItemEvent::class.java,
                "onPlayerSwapHandItems" to PlayerSwapHandItemsEvent::class.java
            )

            for ((methodName, paramType) in methods) {
                val method = InventoryLockListener::class.java.getDeclaredMethod(methodName, paramType)
                val annotation = method.getAnnotation(org.bukkit.event.EventHandler::class.java)

                assertNotNull(annotation, "Method $methodName should have @EventHandler annotation")
                assertEquals(
                    org.bukkit.event.EventPriority.HIGHEST,
                    annotation.priority,
                    "Method $methodName should have HIGHEST priority"
                )
                assertTrue(annotation.ignoreCancelled, "Method $methodName should ignore cancelled events")
            }
        }
    }

    // ============================================================
    // Integration Tests
    // ============================================================

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {

        @Test
        @DisplayName("should block all inventory actions for locked player")
        fun shouldBlockAllActionsForLockedPlayer() {
            val player = server.addPlayer()

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns false

            // Click
            val clickEvent = createInventoryClickEvent(player)
            listener.onInventoryClick(clickEvent)
            assertTrue(clickEvent.isCancelled, "Click should be cancelled")

            // Drag
            val dragEvent = createInventoryDragEvent(player)
            listener.onInventoryDrag(dragEvent)
            assertTrue(dragEvent.isCancelled, "Drag should be cancelled")

            // Drop
            val droppedItem = mockk<org.bukkit.entity.Item>(relaxed = true)
            val dropEvent = PlayerDropItemEvent(player, droppedItem)
            listener.onPlayerDropItem(dropEvent)
            assertTrue(dropEvent.isCancelled, "Drop should be cancelled")

            // Pickup
            val item = mockk<org.bukkit.entity.Item>(relaxed = true)
            val pickupEvent = EntityPickupItemEvent(player, item, 0)
            listener.onPlayerPickupItem(pickupEvent)
            assertTrue(pickupEvent.isCancelled, "Pickup should be cancelled")

            // Swap
            val mainHand = mockk<ItemStack>(relaxed = true)
            val offHand = mockk<ItemStack>(relaxed = true)
            val swapEvent = PlayerSwapHandItemsEvent(player, mainHand, offHand)
            listener.onPlayerSwapHandItems(swapEvent)
            assertTrue(swapEvent.isCancelled, "Swap should be cancelled")
        }

        @Test
        @DisplayName("should allow all inventory actions for bypassing player")
        fun shouldAllowAllActionsForBypassingPlayer() {
            val player = server.addPlayer()

            every { lockingService.isLocked(player.uniqueId) } returns true
            every { lockingService.canBypass(player) } returns true

            // Click
            val clickEvent = createInventoryClickEvent(player)
            listener.onInventoryClick(clickEvent)
            assertFalse(clickEvent.isCancelled, "Click should not be cancelled for bypass")

            // Drag
            val dragEvent = createInventoryDragEvent(player)
            listener.onInventoryDrag(dragEvent)
            assertFalse(dragEvent.isCancelled, "Drag should not be cancelled for bypass")

            // Drop
            val droppedItem = mockk<org.bukkit.entity.Item>(relaxed = true)
            val dropEvent = PlayerDropItemEvent(player, droppedItem)
            listener.onPlayerDropItem(dropEvent)
            assertFalse(dropEvent.isCancelled, "Drop should not be cancelled for bypass")

            // Pickup
            val item = mockk<org.bukkit.entity.Item>(relaxed = true)
            val pickupEvent = EntityPickupItemEvent(player, item, 0)
            listener.onPlayerPickupItem(pickupEvent)
            assertFalse(pickupEvent.isCancelled, "Pickup should not be cancelled for bypass")

            // Swap
            val mainHand = mockk<ItemStack>(relaxed = true)
            val offHand = mockk<ItemStack>(relaxed = true)
            val swapEvent = PlayerSwapHandItemsEvent(player, mainHand, offHand)
            listener.onPlayerSwapHandItems(swapEvent)
            assertFalse(swapEvent.isCancelled, "Swap should not be cancelled for bypass")
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private fun createInventoryClickEvent(player: Player): InventoryClickEvent {
        val view = player.openInventory
        return InventoryClickEvent(
            view,
            InventoryType.SlotType.CONTAINER,
            0,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        )
    }

    private fun createInventoryDragEvent(player: Player): InventoryDragEvent {
        val view = player.openInventory
        return InventoryDragEvent(
            view,
            null,
            ItemStack(org.bukkit.Material.STONE),
            false,
            mapOf(0 to ItemStack(org.bukkit.Material.STONE))
        )
    }
}

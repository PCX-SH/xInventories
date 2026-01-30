package sh.pcx.xinventories.integration.listener

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.junit.jupiter.api.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.config.PerformanceConfig
import sh.pcx.xinventories.internal.listener.WorldChangeListener
import sh.pcx.xinventories.internal.service.InventoryService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for WorldChangeListener.
 *
 * Tests cover:
 * - World change handling
 * - Teleport event logging
 * - Delay configuration
 * - Same world edge case
 */
@DisplayName("WorldChangeListener Tests")
class WorldChangeListenerTest {

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
    private lateinit var inventoryService: InventoryService
    private lateinit var serviceManager: ServiceManager
    private lateinit var configManager: ConfigManager
    private lateinit var listener: WorldChangeListener

    private lateinit var world1: World
    private lateinit var world2: World

    private val testUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()

        // Create worlds
        world1 = server.addSimpleWorld("world")
        world2 = server.addSimpleWorld("creative")

        // Create mocks
        plugin = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        configManager = mockk(relaxed = true)

        // Configure plugin mock
        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.inventoryService } returns inventoryService
        every { plugin.configManager } returns configManager
        every { plugin.server } returns server

        // Default config with no delay
        val mainConfig = MainConfig(
            performance = PerformanceConfig(saveDelayTicks = 0)
        )
        every { configManager.mainConfig } returns mainConfig

        // Make plugin.launch execute blocks immediately using Unconfined dispatcher
        val testJob = SupervisorJob()
        every { plugin.coroutineContext } returns (testJob + Dispatchers.Unconfined)

        // Create listener
        listener = WorldChangeListener(plugin)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
        clearAllMocks()
    }

    // ============================================================
    // World Change Handling Tests
    // ============================================================

    @Nested
    @DisplayName("World Change Handling")
    inner class WorldChangeHandlingTests {

        @Test
        @DisplayName("should call handleWorldChange when player changes world")
        fun shouldCallHandleWorldChange() {
            val player = server.addPlayer()
            player.teleport(Location(world2, 0.0, 64.0, 0.0))

            val event = PlayerChangedWorldEvent(player, world1)

            coEvery {
                inventoryService.handleWorldChange(any(), any(), any())
            } just Runs

            listener.onWorldChange(event)

            coVerify { inventoryService.handleWorldChange(player, "world", "creative") }
        }

        @Test
        @DisplayName("should pass correct from and to world names")
        fun shouldPassCorrectWorldNames() {
            val player = server.addPlayer()
            player.teleport(Location(world2, 0.0, 64.0, 0.0))

            val event = PlayerChangedWorldEvent(player, world1)

            val capturedFrom = slot<String>()
            val capturedTo = slot<String>()
            coEvery {
                inventoryService.handleWorldChange(any(), capture(capturedFrom), capture(capturedTo))
            } just Runs

            listener.onWorldChange(event)

            assertTrue(capturedFrom.isCaptured)
            assertTrue(capturedTo.isCaptured)
            assertEquals("world", capturedFrom.captured)
            assertEquals("creative", capturedTo.captured)
        }

        @Test
        @DisplayName("should handle exception during world change gracefully")
        fun shouldHandleExceptionGracefully() {
            val player = server.addPlayer()
            player.teleport(Location(world2, 0.0, 64.0, 0.0))

            val event = PlayerChangedWorldEvent(player, world1)

            coEvery {
                inventoryService.handleWorldChange(any(), any(), any())
            } throws RuntimeException("Test exception")

            assertDoesNotThrow {
                listener.onWorldChange(event)
            }

            verify { Logging.error(match { it.contains("Failed to handle world change") }, any()) }
        }
    }

    // ============================================================
    // Same World Edge Case Tests
    // ============================================================

    @Nested
    @DisplayName("Same World Edge Case")
    inner class SameWorldEdgeCaseTests {

        @Test
        @DisplayName("should not call handleWorldChange when from and to world are the same")
        fun shouldNotCallWhenSameWorld() {
            val player = server.addPlayer()
            // Player is in world1 and "changed" to world1 (edge case)
            player.teleport(Location(world1, 0.0, 64.0, 0.0))

            val event = PlayerChangedWorldEvent(player, world1)

            listener.onWorldChange(event)

            coVerify(exactly = 0) { inventoryService.handleWorldChange(any(), any(), any()) }
        }
    }

    // ============================================================
    // Delay Configuration Tests
    // ============================================================

    @Nested
    @DisplayName("Delay Configuration")
    inner class DelayConfigurationTests {

        @Test
        @DisplayName("should use scheduler delay when saveDelayTicks is positive")
        fun shouldUseSchedulerDelayWhenPositive() {
            val player = server.addPlayer()
            player.teleport(Location(world2, 0.0, 64.0, 0.0))

            val mainConfig = MainConfig(
                performance = PerformanceConfig(saveDelayTicks = 5)
            )
            every { configManager.mainConfig } returns mainConfig

            coEvery {
                inventoryService.handleWorldChange(any(), any(), any())
            } just Runs

            val event = PlayerChangedWorldEvent(player, world1)

            listener.onWorldChange(event)

            // Before delay - should not have been called yet
            coVerify(exactly = 0) { inventoryService.handleWorldChange(any(), any(), any()) }

            // After delay ticks
            server.scheduler.performTicks(6)

            coVerify(exactly = 1) { inventoryService.handleWorldChange(any(), any(), any()) }
        }

        @Test
        @DisplayName("should not use scheduler when saveDelayTicks is zero")
        fun shouldNotUseSchedulerWhenZero() {
            val player = server.addPlayer()
            player.teleport(Location(world2, 0.0, 64.0, 0.0))

            val mainConfig = MainConfig(
                performance = PerformanceConfig(saveDelayTicks = 0)
            )
            every { configManager.mainConfig } returns mainConfig

            coEvery {
                inventoryService.handleWorldChange(any(), any(), any())
            } just Runs

            val event = PlayerChangedWorldEvent(player, world1)

            listener.onWorldChange(event)

            // Should be called immediately without scheduler
            coVerify(exactly = 1) { inventoryService.handleWorldChange(any(), any(), any()) }
        }
    }

    // ============================================================
    // Teleport Event Tests
    // ============================================================

    @Nested
    @DisplayName("Teleport Event")
    inner class TeleportEventTests {

        @Test
        @DisplayName("should log debug for cross-world teleport")
        fun shouldLogDebugForCrossWorldTeleport() {
            val player = server.addPlayer()
            val fromLocation = Location(world1, 0.0, 64.0, 0.0)
            val toLocation = Location(world2, 0.0, 64.0, 0.0)

            val event = PlayerTeleportEvent(
                player,
                fromLocation,
                toLocation,
                PlayerTeleportEvent.TeleportCause.PLUGIN
            )

            listener.onTeleport(event)

            verify { Logging.debug(any<() -> String>()) }
        }

        @Test
        @DisplayName("should not log for same-world teleport")
        fun shouldNotLogForSameWorldTeleport() {
            val player = server.addPlayer()
            val fromLocation = Location(world1, 0.0, 64.0, 0.0)
            val toLocation = Location(world1, 100.0, 64.0, 100.0)

            val event = PlayerTeleportEvent(
                player,
                fromLocation,
                toLocation,
                PlayerTeleportEvent.TeleportCause.PLUGIN
            )

            listener.onTeleport(event)

            // Debug should not be called for same-world teleport
            verify(exactly = 0) { Logging.debug(any<() -> String>()) }
        }

        @Test
        @DisplayName("should handle all teleport causes")
        fun shouldHandleAllTeleportCauses() {
            val player = server.addPlayer()
            val fromLocation = Location(world1, 0.0, 64.0, 0.0)
            val toLocation = Location(world2, 0.0, 64.0, 0.0)

            for (cause in PlayerTeleportEvent.TeleportCause.entries) {
                val event = PlayerTeleportEvent(player, fromLocation, toLocation, cause)

                assertDoesNotThrow {
                    listener.onTeleport(event)
                }
            }
        }
    }

    // ============================================================
    // Event Priority Tests
    // ============================================================

    @Nested
    @DisplayName("Event Priority")
    inner class EventPriorityTests {

        @Test
        @DisplayName("world change handler has MONITOR priority")
        fun worldChangeHasMonitorPriority() {
            val method = WorldChangeListener::class.java.getDeclaredMethod(
                "onWorldChange",
                PlayerChangedWorldEvent::class.java
            )
            val annotation = method.getAnnotation(org.bukkit.event.EventHandler::class.java)

            assertNotNull(annotation)
            assertEquals(org.bukkit.event.EventPriority.MONITOR, annotation.priority)
            assertTrue(annotation.ignoreCancelled)
        }

        @Test
        @DisplayName("teleport handler has MONITOR priority")
        fun teleportHasMonitorPriority() {
            val method = WorldChangeListener::class.java.getDeclaredMethod(
                "onTeleport",
                PlayerTeleportEvent::class.java
            )
            val annotation = method.getAnnotation(org.bukkit.event.EventHandler::class.java)

            assertNotNull(annotation)
            assertEquals(org.bukkit.event.EventPriority.MONITOR, annotation.priority)
            assertTrue(annotation.ignoreCancelled)
        }
    }

    // ============================================================
    // Player Offline Handling Tests
    // ============================================================

    @Nested
    @DisplayName("Player Offline Handling")
    inner class PlayerOfflineHandlingTests {

        @Test
        @DisplayName("should handle player going offline during delayed world change")
        fun shouldHandlePlayerOfflineDuringDelay() {
            val player = server.addPlayer()
            player.teleport(Location(world2, 0.0, 64.0, 0.0))

            val mainConfig = MainConfig(
                performance = PerformanceConfig(saveDelayTicks = 5)
            )
            every { configManager.mainConfig } returns mainConfig

            val event = PlayerChangedWorldEvent(player, world1)

            listener.onWorldChange(event)

            // Player disconnects before delay completes
            player.disconnect()

            // After delay ticks - player is offline
            server.scheduler.performTicks(6)

            // Should not crash, and handleWorldChange should not be called for offline player
            coVerify(exactly = 0) { inventoryService.handleWorldChange(any(), any(), any()) }
        }
    }

    // ============================================================
    // Integration Tests
    // ============================================================

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {

        @Test
        @DisplayName("should handle multiple rapid world changes")
        fun shouldHandleMultipleRapidWorldChanges() {
            val player = server.addPlayer()

            val mainConfig = MainConfig(
                performance = PerformanceConfig(saveDelayTicks = 0)
            )
            every { configManager.mainConfig } returns mainConfig

            coEvery {
                inventoryService.handleWorldChange(any(), any(), any())
            } just Runs

            // Simulate rapid world changes
            player.teleport(Location(world2, 0.0, 64.0, 0.0))
            listener.onWorldChange(PlayerChangedWorldEvent(player, world1))

            player.teleport(Location(world1, 0.0, 64.0, 0.0))
            listener.onWorldChange(PlayerChangedWorldEvent(player, world2))

            player.teleport(Location(world2, 0.0, 64.0, 0.0))
            listener.onWorldChange(PlayerChangedWorldEvent(player, world1))

            coVerify(exactly = 3) { inventoryService.handleWorldChange(any(), any(), any()) }
        }

        @Test
        @DisplayName("should handle world changes from different players")
        fun shouldHandleWorldChangesFromDifferentPlayers() {
            val player1 = server.addPlayer("Player1")
            val player2 = server.addPlayer("Player2")

            val mainConfig = MainConfig(
                performance = PerformanceConfig(saveDelayTicks = 0)
            )
            every { configManager.mainConfig } returns mainConfig

            coEvery {
                inventoryService.handleWorldChange(any(), any(), any())
            } just Runs

            player1.teleport(Location(world2, 0.0, 64.0, 0.0))
            player2.teleport(Location(world2, 0.0, 64.0, 0.0))

            listener.onWorldChange(PlayerChangedWorldEvent(player1, world1))
            listener.onWorldChange(PlayerChangedWorldEvent(player2, world1))

            coVerify { inventoryService.handleWorldChange(player1, "world", "creative") }
            coVerify { inventoryService.handleWorldChange(player2, "world", "creative") }
        }
    }

    // ============================================================
    // Debug Logging Tests
    // ============================================================

    @Nested
    @DisplayName("Debug Logging")
    inner class DebugLoggingTests {

        @Test
        @DisplayName("should log debug message on world change")
        fun shouldLogDebugOnWorldChange() {
            val player = server.addPlayer()
            player.teleport(Location(world2, 0.0, 64.0, 0.0))

            val mainConfig = MainConfig(
                performance = PerformanceConfig(saveDelayTicks = 0)
            )
            every { configManager.mainConfig } returns mainConfig

            coEvery {
                inventoryService.handleWorldChange(any(), any(), any())
            } just Runs

            val event = PlayerChangedWorldEvent(player, world1)

            listener.onWorldChange(event)

            verify { Logging.debug(any<() -> String>()) }
        }
    }
}

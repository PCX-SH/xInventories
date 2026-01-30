package sh.pcx.xinventories.integration.listener

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.event.player.PlayerGameModeChangeEvent
import org.junit.jupiter.api.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.FeaturesConfig
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.listener.GameModeListener
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.InventoryService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for GameModeListener.
 *
 * Tests cover:
 * - Gamemode change handling with separate inventories
 * - Skipping when separate inventories is disabled
 * - Same gamemode edge case
 * - Delayed execution via scheduler
 */
@DisplayName("GameModeListener Tests")
class GameModeListenerTest {

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
    private lateinit var groupService: GroupService
    private lateinit var serviceManager: ServiceManager
    private lateinit var configManager: ConfigManager
    private lateinit var listener: GameModeListener

    private val testUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()

        // Create mocks
        plugin = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)
        groupService = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        configManager = mockk(relaxed = true)

        // Configure plugin mock
        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.inventoryService } returns inventoryService
        every { serviceManager.groupService } returns groupService
        every { plugin.configManager } returns configManager
        every { plugin.server } returns server

        // Default config with separate gamemode inventories enabled
        val mainConfig = MainConfig(
            features = FeaturesConfig(separateGamemodeInventories = true)
        )
        every { configManager.mainConfig } returns mainConfig

        // Default group with separate gamemode inventories enabled
        val defaultGroup = Group(
            name = "survival",
            isDefault = true,
            settings = GroupSettings(separateGameModeInventories = true)
        )
        every { groupService.getGroupForWorld(any<World>()) } returns defaultGroup

        // Make plugin.launch execute blocks immediately using Unconfined dispatcher
        val testJob = SupervisorJob()
        every { plugin.coroutineContext } returns (testJob + Dispatchers.Unconfined)

        // Create listener
        listener = GameModeListener(plugin)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
        clearAllMocks()
    }

    // ============================================================
    // Gamemode Change Handling Tests
    // ============================================================

    @Nested
    @DisplayName("Gamemode Change Handling")
    inner class GameModeChangeHandlingTests {

        @Test
        @DisplayName("should call handleGameModeChange when gamemode changes")
        fun shouldCallHandleGameModeChange() {
            val player = server.addPlayer()
            player.gameMode = GameMode.SURVIVAL
            val event = PlayerGameModeChangeEvent(player, GameMode.CREATIVE)

            val mainConfig = MainConfig(
                features = FeaturesConfig(separateGamemodeInventories = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(separateGameModeInventories = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            coEvery {
                inventoryService.handleGameModeChange(any(), any(), any())
            } just Runs

            listener.onGameModeChange(event)

            // Execute the scheduled task
            server.scheduler.performTicks(2)

            coVerify { inventoryService.handleGameModeChange(player, GameMode.SURVIVAL, GameMode.CREATIVE) }
        }

        @Test
        @DisplayName("should pass correct old and new gamemodes")
        fun shouldPassCorrectGameModes() {
            val player = server.addPlayer()
            player.gameMode = GameMode.ADVENTURE
            val event = PlayerGameModeChangeEvent(player, GameMode.SPECTATOR)

            val mainConfig = MainConfig(
                features = FeaturesConfig(separateGamemodeInventories = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(separateGameModeInventories = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            val capturedOld = slot<GameMode>()
            val capturedNew = slot<GameMode>()
            coEvery {
                inventoryService.handleGameModeChange(any(), capture(capturedOld), capture(capturedNew))
            } just Runs

            listener.onGameModeChange(event)
            server.scheduler.performTicks(2)

            assertTrue(capturedOld.isCaptured)
            assertTrue(capturedNew.isCaptured)
            assertEquals(GameMode.ADVENTURE, capturedOld.captured)
            assertEquals(GameMode.SPECTATOR, capturedNew.captured)
        }

        @Test
        @DisplayName("should handle all gamemode combinations")
        fun shouldHandleAllGameModeCombinations() {
            val mainConfig = MainConfig(
                features = FeaturesConfig(separateGamemodeInventories = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(separateGameModeInventories = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            coEvery {
                inventoryService.handleGameModeChange(any(), any(), any())
            } just Runs

            val gameModes = listOf(GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR)

            for (oldMode in gameModes) {
                for (newMode in gameModes) {
                    if (oldMode != newMode) {
                        val player = server.addPlayer()
                        player.gameMode = oldMode
                        val event = PlayerGameModeChangeEvent(player, newMode)

                        listener.onGameModeChange(event)
                    }
                }
            }

            server.scheduler.performTicks(20)

            // Should have been called for each valid combination (4*3 = 12)
            coVerify(atLeast = 12) { inventoryService.handleGameModeChange(any(), any(), any()) }
        }
    }

    // ============================================================
    // Skip When Disabled Tests
    // ============================================================

    @Nested
    @DisplayName("Skip When Disabled")
    inner class SkipWhenDisabledTests {

        @Test
        @DisplayName("should not call handleGameModeChange when global config is disabled")
        fun shouldNotCallWhenGlobalDisabled() {
            val player = server.addPlayer()
            player.gameMode = GameMode.SURVIVAL
            val event = PlayerGameModeChangeEvent(player, GameMode.CREATIVE)

            val mainConfig = MainConfig(
                features = FeaturesConfig(separateGamemodeInventories = false)
            )
            every { configManager.mainConfig } returns mainConfig

            listener.onGameModeChange(event)
            server.scheduler.performTicks(2)

            coVerify(exactly = 0) { inventoryService.handleGameModeChange(any(), any(), any()) }
        }

        @Test
        @DisplayName("should not call handleGameModeChange when group config is disabled")
        fun shouldNotCallWhenGroupDisabled() {
            val player = server.addPlayer()
            player.gameMode = GameMode.SURVIVAL
            val event = PlayerGameModeChangeEvent(player, GameMode.CREATIVE)

            val mainConfig = MainConfig(
                features = FeaturesConfig(separateGamemodeInventories = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(separateGameModeInventories = false)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            listener.onGameModeChange(event)
            server.scheduler.performTicks(2)

            coVerify(exactly = 0) { inventoryService.handleGameModeChange(any(), any(), any()) }
        }
    }

    // ============================================================
    // Same Gamemode Edge Case Tests
    // ============================================================

    @Nested
    @DisplayName("Same Gamemode Edge Case")
    inner class SameGameModeEdgeCaseTests {

        @Test
        @DisplayName("should not call handleGameModeChange when gamemode is the same")
        fun shouldNotCallWhenSameGameMode() {
            val player = server.addPlayer()
            player.gameMode = GameMode.SURVIVAL
            val event = PlayerGameModeChangeEvent(player, GameMode.SURVIVAL)

            val mainConfig = MainConfig(
                features = FeaturesConfig(separateGamemodeInventories = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(separateGameModeInventories = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            listener.onGameModeChange(event)
            server.scheduler.performTicks(2)

            coVerify(exactly = 0) { inventoryService.handleGameModeChange(any(), any(), any()) }
        }
    }

    // ============================================================
    // Scheduler Delay Tests
    // ============================================================

    @Nested
    @DisplayName("Scheduler Delay")
    inner class SchedulerDelayTests {

        @Test
        @DisplayName("should use scheduler to delay gamemode change handling")
        fun shouldUseSchedulerToDelay() {
            val player = server.addPlayer()
            player.gameMode = GameMode.SURVIVAL
            val event = PlayerGameModeChangeEvent(player, GameMode.CREATIVE)

            val mainConfig = MainConfig(
                features = FeaturesConfig(separateGamemodeInventories = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(separateGameModeInventories = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            coEvery {
                inventoryService.handleGameModeChange(any(), any(), any())
            } just Runs

            listener.onGameModeChange(event)

            // Before tick - should not have been called yet
            coVerify(exactly = 0) { inventoryService.handleGameModeChange(any(), any(), any()) }

            // After tick - should be called
            server.scheduler.performTicks(2)

            coVerify(exactly = 1) { inventoryService.handleGameModeChange(any(), any(), any()) }
        }
    }

    // ============================================================
    // Error Handling Tests
    // ============================================================

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("should handle exception during gamemode change gracefully")
        fun shouldHandleExceptionGracefully() {
            val player = server.addPlayer()
            player.gameMode = GameMode.SURVIVAL
            val event = PlayerGameModeChangeEvent(player, GameMode.CREATIVE)

            val mainConfig = MainConfig(
                features = FeaturesConfig(separateGamemodeInventories = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(separateGameModeInventories = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            coEvery {
                inventoryService.handleGameModeChange(any(), any(), any())
            } throws RuntimeException("Test exception")

            assertDoesNotThrow {
                listener.onGameModeChange(event)
                server.scheduler.performTicks(2)
            }

            verify { Logging.error(match { it.contains("Failed to handle gamemode change") }, any()) }
        }
    }

    // ============================================================
    // Event Priority Tests
    // ============================================================

    @Nested
    @DisplayName("Event Priority")
    inner class EventPriorityTests {

        @Test
        @DisplayName("gamemode change handler has MONITOR priority")
        fun gameModeChangeHasMonitorPriority() {
            val method = GameModeListener::class.java.getDeclaredMethod(
                "onGameModeChange",
                PlayerGameModeChangeEvent::class.java
            )
            val annotation = method.getAnnotation(org.bukkit.event.EventHandler::class.java)

            assertNotNull(annotation)
            assertEquals(org.bukkit.event.EventPriority.MONITOR, annotation.priority)
            assertTrue(annotation.ignoreCancelled)
        }
    }

    // ============================================================
    // Debug Logging Tests
    // ============================================================

    @Nested
    @DisplayName("Debug Logging")
    inner class DebugLoggingTests {

        @Test
        @DisplayName("should log debug message on gamemode change")
        fun shouldLogDebugOnGameModeChange() {
            val player = server.addPlayer()
            player.gameMode = GameMode.SURVIVAL
            val event = PlayerGameModeChangeEvent(player, GameMode.CREATIVE)

            val mainConfig = MainConfig(
                features = FeaturesConfig(separateGamemodeInventories = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(separateGameModeInventories = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            coEvery {
                inventoryService.handleGameModeChange(any(), any(), any())
            } just Runs

            listener.onGameModeChange(event)

            verify { Logging.debug(any<() -> String>()) }
        }
    }
}

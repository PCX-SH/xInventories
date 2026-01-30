package sh.pcx.xinventories.integration.listener

import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.damage.DamageSource
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.DeathRecoveryConfig
import sh.pcx.xinventories.internal.config.FeaturesConfig
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.listener.InventoryListener
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.DeathRecoveryService
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.InventoryService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Comprehensive tests for InventoryListener.
 *
 * Tests cover:
 * - Death capture for death recovery
 * - Clear-on-death functionality
 * - Player respawn handling
 * - Killer info capture
 * - Configuration options
 */
@DisplayName("InventoryListener Tests")
class InventoryListenerTest {

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
    private lateinit var deathRecoveryService: DeathRecoveryService
    private lateinit var serviceManager: ServiceManager
    private lateinit var configManager: ConfigManager
    private lateinit var listener: InventoryListener

    private val testUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val killerUuid = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()

        // Create mocks
        plugin = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)
        groupService = mockk(relaxed = true)
        deathRecoveryService = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        configManager = mockk(relaxed = true)

        // Configure plugin mock
        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.inventoryService } returns inventoryService
        every { serviceManager.groupService } returns groupService
        every { serviceManager.deathRecoveryService } returns deathRecoveryService
        every { plugin.configManager } returns configManager
        every { plugin.plugin } returns plugin
        every { plugin.server } returns server

        // Default config
        val mainConfig = MainConfig(
            features = FeaturesConfig(clearOnDeath = false),
            deathRecovery = DeathRecoveryConfig(enabled = true)
        )
        every { configManager.mainConfig } returns mainConfig

        // Default group
        val defaultGroup = Group(
            name = "survival",
            isDefault = true,
            settings = GroupSettings(clearOnDeath = false)
        )
        every { groupService.getGroupForWorld(any<World>()) } returns defaultGroup

        // Make plugin.launch execute blocks immediately using Unconfined dispatcher
        val testJob = SupervisorJob()
        every { plugin.coroutineContext } returns (testJob + Dispatchers.Unconfined)

        // Create listener
        listener = InventoryListener(plugin)
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
        clearAllMocks()
    }

    // ============================================================
    // Death Recovery Capture Tests
    // ============================================================

    @Nested
    @DisplayName("Death Recovery Capture")
    inner class DeathRecoveryCaptureTests {

        @Test
        @DisplayName("should capture death inventory when death recovery is enabled")
        fun shouldCaptureDeathWhenEnabled() {
            val player = server.addPlayer()
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                deathRecovery = DeathRecoveryConfig(enabled = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val mockDeathRecord = mockk<DeathRecord>(relaxed = true)
            every { mockDeathRecord.id } returns "death-123"
            coEvery {
                deathRecoveryService.captureDeathInventory(any(), any(), any(), any())
            } returns mockDeathRecord

            listener.onPlayerDeathCapture(event)

            coVerify { deathRecoveryService.captureDeathInventory(player, any(), any(), any()) }
        }

        @Test
        @DisplayName("should not capture death when death recovery is disabled")
        fun shouldNotCaptureDeathWhenDisabled() {
            val player = server.addPlayer()
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                deathRecovery = DeathRecoveryConfig(enabled = false)
            )
            every { configManager.mainConfig } returns mainConfig

            listener.onPlayerDeathCapture(event)

            coVerify(exactly = 0) { deathRecoveryService.captureDeathInventory(any(), any(), any(), any()) }
        }

        @Test
        @DisplayName("should capture damage cause from last damage event")
        fun shouldCaptureDamageCause() {
            // Use MockK player to be able to mock lastDamageCause
            val player = mockk<Player>(relaxed = true)
            val world = server.addSimpleWorld("world")
            every { player.uniqueId } returns testUuid
            every { player.name } returns "TestPlayer"
            every { player.world } returns world
            every { player.inventory } returns mockk(relaxed = true)
            every { player.enderChest } returns mockk(relaxed = true)
            every { player.exp } returns 0f
            every { player.level } returns 0
            every { player.health } returns 20.0
            every { player.foodLevel } returns 20

            // Set up last damage cause
            val damageEvent = mockk<EntityDamageEvent>()
            every { damageEvent.cause } returns EntityDamageEvent.DamageCause.FALL
            every { player.lastDamageCause } returns damageEvent

            val event = createDeathEventWithMockPlayer(player)

            val mainConfig = MainConfig(
                deathRecovery = DeathRecoveryConfig(enabled = true)
            )
            every { configManager.mainConfig } returns mainConfig

            var capturedCause: EntityDamageEvent.DamageCause? = null
            coEvery {
                deathRecoveryService.captureDeathInventory(any(), any(), any(), any())
            } coAnswers {
                capturedCause = secondArg()
                mockk(relaxed = true)
            }

            listener.onPlayerDeathCapture(event)

            assertEquals(EntityDamageEvent.DamageCause.FALL, capturedCause)
        }

        @Test
        @DisplayName("should capture killer info when killed by player")
        fun shouldCaptureKillerInfoWhenKilledByPlayer() {
            // Use MockK players to be able to mock lastDamageCause
            val player = mockk<Player>(relaxed = true)
            val killer = mockk<Player>(relaxed = true)
            val world = server.addSimpleWorld("world")

            every { player.uniqueId } returns testUuid
            every { player.name } returns "TestPlayer"
            every { player.world } returns world
            every { player.inventory } returns mockk(relaxed = true)
            every { player.enderChest } returns mockk(relaxed = true)
            every { player.exp } returns 0f
            every { player.level } returns 0
            every { player.health } returns 20.0
            every { player.foodLevel } returns 20

            every { killer.uniqueId } returns killerUuid
            every { killer.name } returns "Killer"

            // Set up entity damage by player
            val damageEvent = mockk<EntityDamageByEntityEvent>()
            every { damageEvent.cause } returns EntityDamageEvent.DamageCause.ENTITY_ATTACK
            every { damageEvent.damager } returns killer
            every { player.lastDamageCause } returns damageEvent

            val event = createDeathEventWithMockPlayer(player)

            val mainConfig = MainConfig(
                deathRecovery = DeathRecoveryConfig(enabled = true)
            )
            every { configManager.mainConfig } returns mainConfig

            var capturedKillerName: String? = null
            var capturedKillerUuid: UUID? = null
            coEvery {
                deathRecoveryService.captureDeathInventory(any(), any(), any(), any())
            } coAnswers {
                capturedKillerName = thirdArg()
                capturedKillerUuid = arg(3)
                mockk(relaxed = true)
            }

            listener.onPlayerDeathCapture(event)

            assertEquals("Killer", capturedKillerName)
            assertEquals(killerUuid, capturedKillerUuid)
        }

        @Test
        @DisplayName("should capture entity type name when killed by mob")
        fun shouldCaptureEntityTypeWhenKilledByMob() {
            // Use MockK player to be able to mock lastDamageCause
            val player = mockk<Player>(relaxed = true)
            val world = server.addSimpleWorld("world")
            every { player.uniqueId } returns testUuid
            every { player.name } returns "TestPlayer"
            every { player.world } returns world
            every { player.inventory } returns mockk(relaxed = true)
            every { player.enderChest } returns mockk(relaxed = true)
            every { player.exp } returns 0f
            every { player.level } returns 0
            every { player.health } returns 20.0
            every { player.foodLevel } returns 20

            // Create a mock mob as the damager
            val mob = mockk<org.bukkit.entity.Zombie>(relaxed = true)
            every { mob.type } returns EntityType.ZOMBIE

            val damageEvent = mockk<EntityDamageByEntityEvent>()
            every { damageEvent.cause } returns EntityDamageEvent.DamageCause.ENTITY_ATTACK
            every { damageEvent.damager } returns mob
            every { player.lastDamageCause } returns damageEvent

            val event = createDeathEventWithMockPlayer(player)

            val mainConfig = MainConfig(
                deathRecovery = DeathRecoveryConfig(enabled = true)
            )
            every { configManager.mainConfig } returns mainConfig

            var capturedKillerName: String? = null
            var capturedKillerUuid: UUID? = null
            coEvery {
                deathRecoveryService.captureDeathInventory(any(), any(), any(), any())
            } coAnswers {
                capturedKillerName = thirdArg()
                capturedKillerUuid = arg(3)
                mockk(relaxed = true)
            }

            listener.onPlayerDeathCapture(event)

            assertEquals("ZOMBIE", capturedKillerName)
            assertEquals(null, capturedKillerUuid)
        }

        @Test
        @DisplayName("should handle exception during death capture gracefully")
        fun shouldHandleExceptionDuringCapture() {
            val player = server.addPlayer()
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                deathRecovery = DeathRecoveryConfig(enabled = true)
            )
            every { configManager.mainConfig } returns mainConfig

            coEvery {
                deathRecoveryService.captureDeathInventory(any(), any(), any(), any())
            } throws RuntimeException("Test exception")

            assertDoesNotThrow {
                listener.onPlayerDeathCapture(event)
            }

            verify { Logging.error(match { it.contains("Failed to capture death inventory") }, any()) }
        }
    }

    // ============================================================
    // Clear on Death Tests
    // ============================================================

    @Nested
    @DisplayName("Clear on Death")
    inner class ClearOnDeathTests {

        @Test
        @DisplayName("should clear stored inventory when global clearOnDeath is enabled")
        fun shouldClearInventoryWhenGlobalEnabled() {
            val player = server.addPlayer()
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                features = FeaturesConfig(clearOnDeath = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(clearOnDeath = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            coEvery { inventoryService.clearPlayerData(any(), any(), any()) } returns true

            listener.onPlayerDeath(event)

            coVerify { inventoryService.clearPlayerData(player.uniqueId, "survival", any()) }
        }

        @Test
        @DisplayName("should clear stored inventory when group clearOnDeath is enabled")
        fun shouldClearInventoryWhenGroupEnabled() {
            val player = server.addPlayer()
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                features = FeaturesConfig(clearOnDeath = false)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "minigames",
                settings = GroupSettings(clearOnDeath = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            coEvery { inventoryService.clearPlayerData(any(), any(), any()) } returns true

            listener.onPlayerDeath(event)

            coVerify { inventoryService.clearPlayerData(player.uniqueId, "minigames", any()) }
        }

        @Test
        @DisplayName("should not clear inventory when clearOnDeath is disabled everywhere")
        fun shouldNotClearWhenDisabled() {
            val player = server.addPlayer()
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                features = FeaturesConfig(clearOnDeath = false)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(clearOnDeath = false)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            listener.onPlayerDeath(event)

            coVerify(exactly = 0) { inventoryService.clearPlayerData(any(), any(), any()) }
        }

        @Test
        @DisplayName("should pass gameMode when separateGameModeInventories is enabled")
        fun shouldPassGameModeWhenSeparateEnabled() {
            val player = server.addPlayer()
            player.gameMode = GameMode.CREATIVE
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                features = FeaturesConfig(clearOnDeath = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(
                    clearOnDeath = true,
                    separateGameModeInventories = true
                )
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            var capturedGameMode: GameMode? = null
            coEvery {
                inventoryService.clearPlayerData(any(), any(), any())
            } coAnswers {
                capturedGameMode = thirdArg()
                true
            }

            listener.onPlayerDeath(event)

            assertEquals(GameMode.CREATIVE, capturedGameMode)
        }

        @Test
        @DisplayName("should pass null gameMode when separateGameModeInventories is disabled")
        fun shouldPassNullGameModeWhenSeparateDisabled() {
            val player = server.addPlayer()
            player.gameMode = GameMode.CREATIVE
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                features = FeaturesConfig(clearOnDeath = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(
                    clearOnDeath = true,
                    separateGameModeInventories = false
                )
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            var capturedGameMode: GameMode? = GameMode.SURVIVAL // Set to non-null to detect if it's changed
            var wasInvoked = false
            coEvery {
                inventoryService.clearPlayerData(any(), any(), anyNullable())
            } coAnswers {
                wasInvoked = true
                capturedGameMode = thirdArg()
                true
            }

            listener.onPlayerDeath(event)

            assertTrue(wasInvoked)
            assertEquals(null, capturedGameMode)
        }

        @Test
        @DisplayName("should handle exception during clear gracefully")
        fun shouldHandleExceptionDuringClear() {
            val player = server.addPlayer()
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                features = FeaturesConfig(clearOnDeath = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(clearOnDeath = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            coEvery {
                inventoryService.clearPlayerData(any(), any(), any())
            } throws RuntimeException("Test exception")

            assertDoesNotThrow {
                listener.onPlayerDeath(event)
            }

            verify { Logging.error(match { it.contains("Failed to clear inventory on death") }, any()) }
        }
    }

    // ============================================================
    // Player Respawn Tests
    // ============================================================

    @Nested
    @DisplayName("Player Respawn")
    inner class PlayerRespawnTests {

        @Test
        @DisplayName("should log debug message on respawn")
        fun shouldLogDebugOnRespawn() {
            val player = server.addPlayer()
            val world = server.addSimpleWorld("world")
            val respawnLocation = Location(world, 0.0, 64.0, 0.0)
            val event = PlayerRespawnEvent(player, respawnLocation, false)

            listener.onPlayerRespawn(event)

            verify { Logging.debug(any<() -> String>()) }
        }

        @Test
        @DisplayName("should not modify respawn event")
        fun shouldNotModifyRespawnEvent() {
            val player = server.addPlayer()
            val world = server.addSimpleWorld("world")
            val respawnLocation = Location(world, 100.0, 64.0, 100.0)
            val event = PlayerRespawnEvent(player, respawnLocation, false)

            listener.onPlayerRespawn(event)

            // Respawn location should remain unchanged
            assertEquals(100.0, event.respawnLocation.x)
            assertEquals(64.0, event.respawnLocation.y)
            assertEquals(100.0, event.respawnLocation.z)
        }
    }

    // ============================================================
    // Event Priority Tests
    // ============================================================

    @Nested
    @DisplayName("Event Priority")
    inner class EventPriorityTests {

        @Test
        @DisplayName("death capture handler has LOWEST priority")
        fun deathCaptureHasLowestPriority() {
            val method = InventoryListener::class.java.getDeclaredMethod(
                "onPlayerDeathCapture",
                PlayerDeathEvent::class.java
            )
            val annotation = method.getAnnotation(org.bukkit.event.EventHandler::class.java)

            assertNotNull(annotation)
            assertEquals(org.bukkit.event.EventPriority.LOWEST, annotation.priority)
        }

        @Test
        @DisplayName("death handler has MONITOR priority")
        fun deathHandlerHasMonitorPriority() {
            val method = InventoryListener::class.java.getDeclaredMethod(
                "onPlayerDeath",
                PlayerDeathEvent::class.java
            )
            val annotation = method.getAnnotation(org.bukkit.event.EventHandler::class.java)

            assertNotNull(annotation)
            assertEquals(org.bukkit.event.EventPriority.MONITOR, annotation.priority)
            assertTrue(annotation.ignoreCancelled)
        }

        @Test
        @DisplayName("respawn handler has MONITOR priority")
        fun respawnHandlerHasMonitorPriority() {
            val method = InventoryListener::class.java.getDeclaredMethod(
                "onPlayerRespawn",
                PlayerRespawnEvent::class.java
            )
            val annotation = method.getAnnotation(org.bukkit.event.EventHandler::class.java)

            assertNotNull(annotation)
            assertEquals(org.bukkit.event.EventPriority.MONITOR, annotation.priority)
            assertTrue(annotation.ignoreCancelled)
        }
    }

    // ============================================================
    // Integration Tests
    // ============================================================

    @Nested
    @DisplayName("Integration Tests")
    inner class IntegrationTests {

        @Test
        @DisplayName("should handle death and capture in sequence")
        fun shouldHandleDeathAndCaptureInSequence() {
            val player = server.addPlayer()
            val event = createDeathEvent(player)

            val mainConfig = MainConfig(
                features = FeaturesConfig(clearOnDeath = true),
                deathRecovery = DeathRecoveryConfig(enabled = true)
            )
            every { configManager.mainConfig } returns mainConfig

            val group = Group(
                name = "survival",
                settings = GroupSettings(clearOnDeath = true)
            )
            every { groupService.getGroupForWorld(any<World>()) } returns group

            coEvery {
                deathRecoveryService.captureDeathInventory(any(), any(), any(), any())
            } returns mockk(relaxed = true)
            coEvery { inventoryService.clearPlayerData(any(), any(), any()) } returns true

            // Both handlers should be called
            listener.onPlayerDeathCapture(event)
            listener.onPlayerDeath(event)

            coVerify { deathRecoveryService.captureDeathInventory(any(), any(), any(), any()) }
            coVerify { inventoryService.clearPlayerData(any(), any(), any()) }
        }

        @Test
        @DisplayName("should handle multiple deaths from different players")
        fun shouldHandleMultipleDeathsFromDifferentPlayers() {
            val player1 = server.addPlayer("Player1")
            val player2 = server.addPlayer("Player2")

            val mainConfig = MainConfig(
                deathRecovery = DeathRecoveryConfig(enabled = true)
            )
            every { configManager.mainConfig } returns mainConfig

            coEvery {
                deathRecoveryService.captureDeathInventory(any(), any(), any(), any())
            } returns mockk(relaxed = true)

            listener.onPlayerDeathCapture(createDeathEvent(player1))
            listener.onPlayerDeathCapture(createDeathEvent(player2))

            coVerify { deathRecoveryService.captureDeathInventory(player1, any(), any(), any()) }
            coVerify { deathRecoveryService.captureDeathInventory(player2, any(), any(), any()) }
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    private fun createDeathEvent(player: Player): PlayerDeathEvent {
        val damageSource = mockk<DamageSource>(relaxed = true)
        return PlayerDeathEvent(
            player,
            damageSource,
            mutableListOf<ItemStack>(),
            100,
            "died"
        )
    }

    private fun createDeathEventWithMockPlayer(player: Player): PlayerDeathEvent {
        val damageSource = mockk<DamageSource>(relaxed = true)
        return PlayerDeathEvent(
            player,
            damageSource,
            mutableListOf<ItemStack>(),
            100,
            "died"
        )
    }
}

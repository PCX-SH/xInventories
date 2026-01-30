package sh.pcx.xinventories.unit.service

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.potion.PotionEffect
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.DeathRecoveryConfig
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.service.DeathRecoveryService
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.InventoryService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.service.StorageService
import sh.pcx.xinventories.internal.storage.Storage
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for DeathRecoveryService.
 *
 * Tests cover:
 * - Initialization and shutdown
 * - Capturing death inventory records
 * - Retrieving death records for players
 * - Restoring inventory from death records
 * - Getting latest death record
 * - Pruning old death records based on retention settings
 * - Enabled/disabled state based on config
 * - Config options for location, death cause, and killer storage
 */
@DisplayName("DeathRecoveryService Unit Tests")
class DeathRecoveryServiceTest {

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var mainConfig: MainConfig
    private lateinit var serviceManager: ServiceManager
    private lateinit var storageService: StorageService
    private lateinit var storage: Storage
    private lateinit var inventoryService: InventoryService
    private lateinit var groupService: GroupService
    private lateinit var deathRecoveryService: DeathRecoveryService
    private lateinit var scope: CoroutineScope
    private lateinit var server: Server
    private lateinit var scheduler: BukkitScheduler

    private val playerUUID = UUID.randomUUID()
    private val playerName = "TestPlayer"
    private val killerUUID = UUID.randomUUID()
    private val killerName = "KillerPlayer"

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("DeathRecoveryServiceTest"), false)
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
        plugin = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        mainConfig = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        storageService = mockk(relaxed = true)
        storage = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)
        groupService = mockk(relaxed = true)
        server = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        every { plugin.configManager } returns configManager
        every { plugin.serviceManager } returns serviceManager
        every { plugin.plugin } returns plugin
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { configManager.mainConfig } returns mainConfig
        every { serviceManager.inventoryService } returns inventoryService
        every { serviceManager.groupService } returns groupService
        every { storageService.storage } returns storage

        // Default to enabled config
        every { mainConfig.deathRecovery } returns DeathRecoveryConfig(
            enabled = true,
            maxDeathsPerPlayer = 5,
            retentionDays = 3,
            storeLocation = true,
            storeDeathCause = true,
            storeKiller = true
        )

        deathRecoveryService = DeathRecoveryService(plugin, scope, storageService)
    }

    @AfterEach
    fun tearDown() {
        deathRecoveryService.shutdown()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockPlayer(
        uuid: UUID = playerUUID,
        name: String = playerName,
        worldName: String = "world"
    ): Player {
        val world = mockk<World>(relaxed = true).apply {
            every { this@apply.name } returns worldName
        }

        val location = mockk<Location>(relaxed = true).apply {
            every { this@apply.world } returns world
            every { x } returns 100.0
            every { y } returns 64.0
            every { z } returns 200.0
        }

        val itemInOffHand = mockk<ItemStack>(relaxed = true)
        every { itemInOffHand.type.isAir } returns true

        val inventory = mockk<PlayerInventory>(relaxed = true)
        every { inventory.getItem(any<Int>()) } returns null
        every { inventory.armorContents } returns arrayOfNulls(4)
        every { inventory.itemInOffHand } returns itemInOffHand

        val enderChest = mockk<org.bukkit.inventory.Inventory>(relaxed = true)
        every { enderChest.size } returns 27
        every { enderChest.getItem(any<Int>()) } returns null

        val player = mockk<Player>(relaxed = true)
        every { player.uniqueId } returns uuid
        every { player.name } returns name
        every { player.world } returns world
        every { player.location } returns location
        every { player.inventory } returns inventory
        every { player.enderChest } returns enderChest
        every { player.gameMode } returns GameMode.SURVIVAL
        every { player.health } returns 20.0
        every { player.foodLevel } returns 20
        every { player.saturation } returns 5.0f
        every { player.exhaustion } returns 0.0f
        every { player.exp } returns 0.0f
        every { player.level } returns 0
        every { player.totalExperience } returns 0
        every { player.activePotionEffects } returns emptyList<PotionEffect>()
        every { player.isFlying } returns false
        every { player.allowFlight } returns false
        every { player.displayName } returns name
        every { player.fallDistance } returns 0.0f
        every { player.fireTicks } returns 0
        every { player.maximumAir } returns 300
        every { player.remainingAir } returns 300

        return player
    }

    private fun createMockDeathRecord(
        id: String = UUID.randomUUID().toString(),
        playerUuid: UUID = playerUUID,
        timestamp: Instant = Instant.now(),
        world: String = "world",
        group: String = "survival",
        deathCause: String? = "FALL",
        killerName: String? = null,
        killerUuid: UUID? = null
    ): DeathRecord {
        val playerData = PlayerData(playerUuid, this.playerName, group, GameMode.SURVIVAL)
        return DeathRecord(
            id = id,
            playerUuid = playerUuid,
            timestamp = timestamp,
            world = world,
            x = 100.0,
            y = 64.0,
            z = 200.0,
            deathCause = deathCause,
            killerName = killerName,
            killerUuid = killerUuid,
            group = group,
            gameMode = GameMode.SURVIVAL,
            inventoryData = playerData
        )
    }

    private fun createMockGroup(name: String = "survival"): Group {
        return Group(
            name = name,
            settings = GroupSettings()
        )
    }

    // =========================================================================
    // Initialization Tests
    // =========================================================================

    @Nested
    @DisplayName("Initialization")
    inner class InitializationTests {

        @Test
        @DisplayName("Should initialize when enabled")
        fun initializeWhenEnabled() {
            deathRecoveryService.initialize()

            verify { Logging.info("Death recovery service initialized") }
        }

        @Test
        @DisplayName("Should not initialize when disabled")
        fun notInitializeWhenDisabled() {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(enabled = false)

            deathRecoveryService.initialize()

            verify { Logging.info("Death recovery is disabled") }
        }

        @Test
        @DisplayName("Should handle multiple initialize calls")
        fun handleMultipleInitializeCalls() {
            deathRecoveryService.initialize()
            deathRecoveryService.initialize()

            // Should not throw, and pruning job should still work
            verify(atLeast = 1) { Logging.info("Death recovery service initialized") }
        }
    }

    // =========================================================================
    // Shutdown Tests
    // =========================================================================

    @Nested
    @DisplayName("Shutdown")
    inner class ShutdownTests {

        @Test
        @DisplayName("Should shutdown cleanly after initialization")
        fun shutdownCleanlyAfterInitialization() {
            deathRecoveryService.initialize()
            deathRecoveryService.shutdown()

            // Should not throw
        }

        @Test
        @DisplayName("Should handle shutdown when not initialized")
        fun shutdownWhenNotInitialized() {
            deathRecoveryService.shutdown()

            // Should not throw
        }

        @Test
        @DisplayName("Should handle multiple shutdown calls")
        fun handleMultipleShutdownCalls() {
            deathRecoveryService.initialize()
            deathRecoveryService.shutdown()
            deathRecoveryService.shutdown()

            // Should not throw
        }
    }

    // =========================================================================
    // Capture Death Inventory Tests
    // =========================================================================

    @Nested
    @DisplayName("captureDeathInventory")
    inner class CaptureDeathInventoryTests {

        @Test
        @DisplayName("Should capture death inventory successfully")
        fun captureDeathInventorySuccessfully() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup("survival")

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "survival") } returns PlayerData(
                playerUUID, playerName, "survival", GameMode.SURVIVAL
            )

            val result = deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNotNull(result)
            assertEquals(playerUUID, result?.playerUuid)
            coVerify { storage.saveDeathRecord(any()) }

            unmockkObject(PlayerData)
        }

        @Test
        @DisplayName("Should return null when disabled")
        fun returnNullWhenDisabled() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(enabled = false)

            val player = createMockPlayer()

            val result = deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNull(result)
            coVerify(exactly = 0) { storage.saveDeathRecord(any()) }
        }

        @Test
        @DisplayName("Should return null when save fails")
        fun returnNullWhenSaveFails() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup("survival")

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns false

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "survival") } returns PlayerData(
                playerUUID, playerName, "survival", GameMode.SURVIVAL
            )

            val result = deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNull(result)
            verify { Logging.error(match<String> { it.contains("Failed to save death record") }) }

            unmockkObject(PlayerData)
        }

        @Test
        @DisplayName("Should use group from world when getCurrentGroup returns null")
        fun useGroupFromWorldWhenCurrentGroupNull() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup("world_group")

            every { inventoryService.getCurrentGroup(player) } returns null
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "world_group") } returns PlayerData(
                playerUUID, playerName, "world_group", GameMode.SURVIVAL
            )

            val result = deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNotNull(result)
            verify { PlayerData.fromPlayer(player, "world_group") }

            unmockkObject(PlayerData)
        }

        @Test
        @DisplayName("Should store killer information when enabled")
        fun storeKillerInformationWhenEnabled() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup("survival")

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "survival") } returns PlayerData(
                playerUUID, playerName, "survival", GameMode.SURVIVAL
            )

            val result = deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                killerName,
                killerUUID
            )

            assertNotNull(result)
            coVerify {
                storage.saveDeathRecord(match { record ->
                    record.killerName == killerName && record.killerUuid == killerUUID
                })
            }

            unmockkObject(PlayerData)
        }

        @Test
        @DisplayName("Should not store killer information when disabled")
        fun notStoreKillerInformationWhenDisabled() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(
                enabled = true,
                storeKiller = false
            )

            val player = createMockPlayer()
            val group = createMockGroup("survival")

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "survival") } returns PlayerData(
                playerUUID, playerName, "survival", GameMode.SURVIVAL
            )

            val result = deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                killerName,
                killerUUID
            )

            assertNotNull(result)
            coVerify {
                storage.saveDeathRecord(match { record ->
                    record.killerName == null && record.killerUuid == null
                })
            }

            unmockkObject(PlayerData)
        }

        @Test
        @DisplayName("Should not store death cause when disabled")
        fun notStoreDeathCauseWhenDisabled() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(
                enabled = true,
                storeDeathCause = false
            )

            val player = createMockPlayer()
            val group = createMockGroup("survival")

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "survival") } returns PlayerData(
                playerUUID, playerName, "survival", GameMode.SURVIVAL
            )

            val result = deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNotNull(result)
            coVerify {
                storage.saveDeathRecord(match { record ->
                    record.deathCause == null
                })
            }

            unmockkObject(PlayerData)
        }

        @Test
        @DisplayName("Should prune old death records after capture")
        fun pruneOldDeathRecordsAfterCapture() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup("survival")

            // Create more records than maxDeathsPerPlayer (5)
            val existingRecords = (1..7).map { i ->
                createMockDeathRecord(
                    id = "death-$i",
                    timestamp = Instant.now().minus(i.toLong(), ChronoUnit.HOURS)
                )
            }

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns existingRecords
            coEvery { storage.deleteDeathRecord(any()) } returns true

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "survival") } returns PlayerData(
                playerUUID, playerName, "survival", GameMode.SURVIVAL
            )

            deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            // Should delete the 2 oldest records (7 - 5 = 2)
            coVerify(exactly = 2) { storage.deleteDeathRecord(any()) }

            unmockkObject(PlayerData)
        }
    }

    // =========================================================================
    // Capture Death From Data Tests
    // =========================================================================

    @Nested
    @DisplayName("captureDeathFromData")
    inner class CaptureDeathFromDataTests {

        @Test
        @DisplayName("Should capture death from existing player data")
        fun captureDeathFromExistingPlayerData() = runTest {
            val playerData = PlayerData(playerUUID, playerName, "survival", GameMode.SURVIVAL)
            val world = mockk<World>(relaxed = true)
            every { world.name } returns "world"
            val location = mockk<Location>(relaxed = true)
            every { location.world } returns world
            every { location.x } returns 100.0
            every { location.y } returns 64.0
            every { location.z } returns 200.0

            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            val result = deathRecoveryService.captureDeathFromData(
                playerData,
                location,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNotNull(result)
            assertEquals(playerUUID, result?.playerUuid)
            coVerify { storage.saveDeathRecord(any()) }
        }

        @Test
        @DisplayName("Should return null when disabled")
        fun returnNullWhenDisabled() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(enabled = false)

            val playerData = PlayerData(playerUUID, playerName, "survival", GameMode.SURVIVAL)
            val location = mockk<Location>(relaxed = true)

            val result = deathRecoveryService.captureDeathFromData(
                playerData,
                location,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNull(result)
        }

        @Test
        @DisplayName("Should return null when save fails")
        fun returnNullWhenSaveFails() = runTest {
            val playerData = PlayerData(playerUUID, playerName, "survival", GameMode.SURVIVAL)
            val world = mockk<World>(relaxed = true)
            every { world.name } returns "world"
            val location = mockk<Location>(relaxed = true)
            every { location.world } returns world
            every { location.x } returns 100.0
            every { location.y } returns 64.0
            every { location.z } returns 200.0

            coEvery { storage.saveDeathRecord(any()) } returns false

            val result = deathRecoveryService.captureDeathFromData(
                playerData,
                location,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNull(result)
            verify { Logging.error(match<String> { it.contains("Failed to save death record") }) }
        }

        @Test
        @DisplayName("Should not store location when disabled")
        fun notStoreLocationWhenDisabled() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(
                enabled = true,
                storeLocation = false
            )

            val playerData = PlayerData(playerUUID, playerName, "survival", GameMode.SURVIVAL)
            val world = mockk<World>(relaxed = true)
            every { world.name } returns "world"
            val location = mockk<Location>(relaxed = true)
            every { location.world } returns world
            every { location.x } returns 100.0
            every { location.y } returns 64.0
            every { location.z } returns 200.0

            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            val result = deathRecoveryService.captureDeathFromData(
                playerData,
                location,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNotNull(result)
            coVerify {
                storage.saveDeathRecord(match { record ->
                    record.x == 0.0 && record.y == 0.0 && record.z == 0.0
                })
            }
        }
    }

    // =========================================================================
    // Get Death Records Tests
    // =========================================================================

    @Nested
    @DisplayName("getDeathRecords")
    inner class GetDeathRecordsTests {

        @Test
        @DisplayName("Should return death records for player")
        fun returnDeathRecordsForPlayer() = runTest {
            val records = listOf(
                createMockDeathRecord(id = "death-1"),
                createMockDeathRecord(id = "death-2")
            )

            coEvery { storage.loadDeathRecords(playerUUID, 10) } returns records

            val result = deathRecoveryService.getDeathRecords(playerUUID)

            assertEquals(2, result.size)
            assertEquals("death-1", result[0].id)
            assertEquals("death-2", result[1].id)
        }

        @Test
        @DisplayName("Should return empty list when no records")
        fun returnEmptyListWhenNoRecords() = runTest {
            coEvery { storage.loadDeathRecords(playerUUID, 10) } returns emptyList()

            val result = deathRecoveryService.getDeathRecords(playerUUID)

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("Should use custom limit")
        fun useCustomLimit() = runTest {
            coEvery { storage.loadDeathRecords(playerUUID, 5) } returns emptyList()

            deathRecoveryService.getDeathRecords(playerUUID, 5)

            coVerify { storage.loadDeathRecords(playerUUID, 5) }
        }
    }

    // =========================================================================
    // Get Death Record Tests
    // =========================================================================

    @Nested
    @DisplayName("getDeathRecord")
    inner class GetDeathRecordTests {

        @Test
        @DisplayName("Should return death record by ID")
        fun returnDeathRecordById() = runTest {
            val record = createMockDeathRecord(id = "death-123")

            coEvery { storage.loadDeathRecord("death-123") } returns record

            val result = deathRecoveryService.getDeathRecord("death-123")

            assertNotNull(result)
            assertEquals("death-123", result?.id)
        }

        @Test
        @DisplayName("Should return null when record not found")
        fun returnNullWhenRecordNotFound() = runTest {
            coEvery { storage.loadDeathRecord("nonexistent") } returns null

            val result = deathRecoveryService.getDeathRecord("nonexistent")

            assertNull(result)
        }
    }

    // =========================================================================
    // Restore Death Inventory Tests
    // =========================================================================

    @Nested
    @DisplayName("restoreDeathInventory")
    inner class RestoreDeathInventoryTests {

        @Test
        @DisplayName("Should restore death inventory successfully")
        fun restoreDeathInventorySuccessfully() = runTest {
            val player = createMockPlayer()
            val record = createMockDeathRecord(id = "death-123")
            val group = createMockGroup("survival")
            val bukkitTask = mockk<org.bukkit.scheduler.BukkitTask>(relaxed = true)
            val runnableSlot = slot<Runnable>()

            coEvery { storage.loadDeathRecord("death-123") } returns record
            every { groupService.getGroup("survival") } returns group
            every { scheduler.runTask(eq(plugin), capture(runnableSlot)) } returns bukkitTask

            val result = deathRecoveryService.restoreDeathInventory(player, "death-123")

            assertTrue(result.isSuccess)
            verify { Logging.info(match<String> { it.contains("Restored death inventory") }) }
            // Verify a runnable was scheduled for the main thread
            verify { scheduler.runTask(eq(plugin), any<Runnable>()) }
            assertTrue(runnableSlot.isCaptured)
        }

        @Test
        @DisplayName("Should fail when death record not found")
        fun failWhenDeathRecordNotFound() = runTest {
            val player = createMockPlayer()

            coEvery { storage.loadDeathRecord("nonexistent") } returns null

            val result = deathRecoveryService.restoreDeathInventory(player, "nonexistent")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Death record not found") == true)
        }

        @Test
        @DisplayName("Should fail when death record belongs to different player")
        fun failWhenDeathRecordBelongsToDifferentPlayer() = runTest {
            val player = createMockPlayer()
            val otherPlayerUUID = UUID.randomUUID()
            val record = createMockDeathRecord(id = "death-123", playerUuid = otherPlayerUUID)

            coEvery { storage.loadDeathRecord("death-123") } returns record

            val result = deathRecoveryService.restoreDeathInventory(player, "death-123")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("does not belong to this player") == true)
        }

        @Test
        @DisplayName("Should use default settings when group not found")
        fun useDefaultSettingsWhenGroupNotFound() = runTest {
            val player = createMockPlayer()
            val record = createMockDeathRecord(id = "death-123")
            val bukkitTask = mockk<org.bukkit.scheduler.BukkitTask>(relaxed = true)
            val runnableSlot = slot<Runnable>()

            coEvery { storage.loadDeathRecord("death-123") } returns record
            every { groupService.getGroup("survival") } returns null
            every { scheduler.runTask(eq(plugin), capture(runnableSlot)) } returns bukkitTask

            val result = deathRecoveryService.restoreDeathInventory(player, "death-123")

            assertTrue(result.isSuccess)
            // Verify a runnable was scheduled for the main thread (even with no group)
            verify { scheduler.runTask(eq(plugin), any<Runnable>()) }
            assertTrue(runnableSlot.isCaptured)
        }
    }

    // =========================================================================
    // Delete Death Record Tests
    // =========================================================================

    @Nested
    @DisplayName("deleteDeathRecord")
    inner class DeleteDeathRecordTests {

        @Test
        @DisplayName("Should delete death record successfully")
        fun deleteDeathRecordSuccessfully() = runTest {
            coEvery { storage.deleteDeathRecord("death-123") } returns true

            val result = deathRecoveryService.deleteDeathRecord("death-123")

            assertTrue(result)
            coVerify { storage.deleteDeathRecord("death-123") }
        }

        @Test
        @DisplayName("Should return false when delete fails")
        fun returnFalseWhenDeleteFails() = runTest {
            coEvery { storage.deleteDeathRecord("death-123") } returns false

            val result = deathRecoveryService.deleteDeathRecord("death-123")

            assertFalse(result)
        }
    }

    // =========================================================================
    // Prune Old Death Records Tests
    // =========================================================================

    @Nested
    @DisplayName("pruneOldDeathRecords")
    inner class PruneOldDeathRecordsTests {

        @Test
        @DisplayName("Should prune old death records")
        fun pruneOldDeathRecords() = runTest {
            coEvery { storage.pruneDeathRecords(any()) } returns 5

            val result = deathRecoveryService.pruneOldDeathRecords()

            assertEquals(5, result)
            verify { Logging.info(match<String> { it.contains("Pruned 5 old death records") }) }
        }

        @Test
        @DisplayName("Should return 0 when disabled")
        fun returnZeroWhenDisabled() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(enabled = false)

            val result = deathRecoveryService.pruneOldDeathRecords()

            assertEquals(0, result)
            coVerify(exactly = 0) { storage.pruneDeathRecords(any()) }
        }

        @Test
        @DisplayName("Should return 0 when no records to prune")
        fun returnZeroWhenNoRecordsToPrune() = runTest {
            coEvery { storage.pruneDeathRecords(any()) } returns 0

            // Clear previous mocks to avoid interference
            clearMocks(Logging, answers = false)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs

            val result = deathRecoveryService.pruneOldDeathRecords()

            assertEquals(0, result)
            // Should not log "Pruned X" when no records deleted
            verify(exactly = 0) { Logging.info(match<String> { it.contains("Pruned") && it.contains("old death records") }) }
        }

        @Test
        @DisplayName("Should use retention days from config")
        fun useRetentionDaysFromConfig() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(
                enabled = true,
                retentionDays = 7
            )

            coEvery { storage.pruneDeathRecords(any()) } returns 0

            deathRecoveryService.pruneOldDeathRecords()

            coVerify {
                storage.pruneDeathRecords(match { cutoff ->
                    val expectedCutoff = Instant.now().minus(7, ChronoUnit.DAYS)
                    // Allow 1 second tolerance
                    cutoff.epochSecond in (expectedCutoff.epochSecond - 1)..(expectedCutoff.epochSecond + 1)
                })
            }
        }
    }

    // =========================================================================
    // Get Death Record Count Tests
    // =========================================================================

    @Nested
    @DisplayName("getDeathRecordCount")
    inner class GetDeathRecordCountTests {

        @Test
        @DisplayName("Should return count of death records")
        fun returnCountOfDeathRecords() = runTest {
            val records = listOf(
                createMockDeathRecord(id = "death-1"),
                createMockDeathRecord(id = "death-2"),
                createMockDeathRecord(id = "death-3")
            )

            coEvery { storage.loadDeathRecords(playerUUID, Int.MAX_VALUE) } returns records

            val result = deathRecoveryService.getDeathRecordCount(playerUUID)

            assertEquals(3, result)
        }

        @Test
        @DisplayName("Should return 0 when no records")
        fun returnZeroWhenNoRecords() = runTest {
            coEvery { storage.loadDeathRecords(playerUUID, Int.MAX_VALUE) } returns emptyList()

            val result = deathRecoveryService.getDeathRecordCount(playerUUID)

            assertEquals(0, result)
        }
    }

    // =========================================================================
    // Get Most Recent Death Tests
    // =========================================================================

    @Nested
    @DisplayName("getMostRecentDeath")
    inner class GetMostRecentDeathTests {

        @Test
        @DisplayName("Should return most recent death record")
        fun returnMostRecentDeathRecord() = runTest {
            val record = createMockDeathRecord(id = "death-latest")

            coEvery { storage.loadDeathRecords(playerUUID, 1) } returns listOf(record)

            val result = deathRecoveryService.getMostRecentDeath(playerUUID)

            assertNotNull(result)
            assertEquals("death-latest", result?.id)
        }

        @Test
        @DisplayName("Should return null when no records")
        fun returnNullWhenNoRecords() = runTest {
            coEvery { storage.loadDeathRecords(playerUUID, 1) } returns emptyList()

            val result = deathRecoveryService.getMostRecentDeath(playerUUID)

            assertNull(result)
        }
    }

    // =========================================================================
    // Config Options Tests
    // =========================================================================

    @Nested
    @DisplayName("Config Options")
    inner class ConfigOptionsTests {

        @Test
        @DisplayName("Should respect maxDeathsPerPlayer setting")
        fun respectMaxDeathsPerPlayerSetting() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(
                enabled = true,
                maxDeathsPerPlayer = 3
            )

            val player = createMockPlayer()
            val group = createMockGroup("survival")

            // Create more records than maxDeathsPerPlayer
            val existingRecords = (1..5).map { i ->
                createMockDeathRecord(
                    id = "death-$i",
                    timestamp = Instant.now().minus(i.toLong(), ChronoUnit.HOURS)
                )
            }

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns existingRecords
            coEvery { storage.deleteDeathRecord(any()) } returns true

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "survival") } returns PlayerData(
                playerUUID, playerName, "survival", GameMode.SURVIVAL
            )

            deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            // Should delete the 2 oldest records (5 - 3 = 2)
            coVerify(exactly = 2) { storage.deleteDeathRecord(any()) }

            unmockkObject(PlayerData)
        }

        @Test
        @DisplayName("Should store all info when all options enabled")
        fun storeAllInfoWhenAllOptionsEnabled() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(
                enabled = true,
                storeLocation = true,
                storeDeathCause = true,
                storeKiller = true
            )

            val playerData = PlayerData(playerUUID, playerName, "survival", GameMode.SURVIVAL)
            val world = mockk<World>(relaxed = true)
            every { world.name } returns "world"
            val location = mockk<Location>(relaxed = true)
            every { location.world } returns world
            every { location.x } returns 100.0
            every { location.y } returns 64.0
            every { location.z } returns 200.0

            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            deathRecoveryService.captureDeathFromData(
                playerData,
                location,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                killerName,
                killerUUID
            )

            coVerify {
                storage.saveDeathRecord(match { record ->
                    record.x == 100.0 &&
                    record.y == 64.0 &&
                    record.z == 200.0 &&
                    record.deathCause == "ENTITY_ATTACK" &&
                    record.killerName == killerName &&
                    record.killerUuid == killerUUID
                })
            }
        }

        @Test
        @DisplayName("Should not store any optional info when all options disabled")
        fun notStoreOptionalInfoWhenAllOptionsDisabled() = runTest {
            every { mainConfig.deathRecovery } returns DeathRecoveryConfig(
                enabled = true,
                storeLocation = false,
                storeDeathCause = false,
                storeKiller = false
            )

            val playerData = PlayerData(playerUUID, playerName, "survival", GameMode.SURVIVAL)
            val world = mockk<World>(relaxed = true)
            every { world.name } returns "world"
            val location = mockk<Location>(relaxed = true)
            every { location.world } returns world
            every { location.x } returns 100.0
            every { location.y } returns 64.0
            every { location.z } returns 200.0

            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            deathRecoveryService.captureDeathFromData(
                playerData,
                location,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                killerName,
                killerUUID
            )

            coVerify {
                storage.saveDeathRecord(match { record ->
                    record.x == 0.0 &&
                    record.y == 0.0 &&
                    record.z == 0.0 &&
                    record.deathCause == null &&
                    record.killerName == null &&
                    record.killerUuid == null
                })
            }
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null damage cause")
        fun handleNullDamageCause() = runTest {
            val playerData = PlayerData(playerUUID, playerName, "survival", GameMode.SURVIVAL)
            val world = mockk<World>(relaxed = true)
            every { world.name } returns "world"
            val location = mockk<Location>(relaxed = true)
            every { location.world } returns world
            every { location.x } returns 100.0
            every { location.y } returns 64.0
            every { location.z } returns 200.0

            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            val result = deathRecoveryService.captureDeathFromData(
                playerData,
                location,
                null,
                null,
                null
            )

            assertNotNull(result)
            coVerify {
                storage.saveDeathRecord(match { record ->
                    record.deathCause == null
                })
            }
        }

        @Test
        @DisplayName("Should handle world with null name")
        fun handleWorldWithNullName() = runTest {
            val playerData = PlayerData(playerUUID, playerName, "survival", GameMode.SURVIVAL)
            val location = mockk<Location>(relaxed = true)
            every { location.world } returns null
            every { location.x } returns 100.0
            every { location.y } returns 64.0
            every { location.z } returns 200.0

            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            val result = deathRecoveryService.captureDeathFromData(
                playerData,
                location,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNotNull(result)
            coVerify {
                storage.saveDeathRecord(match { record ->
                    record.world == "unknown"
                })
            }
        }

        @Test
        @DisplayName("Should handle empty death records list when pruning")
        fun handleEmptyDeathRecordsWhenPruning() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup("survival")

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "survival") } returns PlayerData(
                playerUUID, playerName, "survival", GameMode.SURVIVAL
            )

            val result = deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            assertNotNull(result)
            coVerify(exactly = 0) { storage.deleteDeathRecord(any()) }

            unmockkObject(PlayerData)
        }

        @Test
        @DisplayName("Should handle exactly maxDeathsPerPlayer records")
        fun handleExactlyMaxDeathsPerPlayerRecords() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup("survival")

            // Create exactly maxDeathsPerPlayer (5) records
            val existingRecords = (1..5).map { i ->
                createMockDeathRecord(
                    id = "death-$i",
                    timestamp = Instant.now().minus(i.toLong(), ChronoUnit.HOURS)
                )
            }

            every { inventoryService.getCurrentGroup(player) } returns "survival"
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveDeathRecord(any()) } returns true
            coEvery { storage.loadDeathRecords(playerUUID, any()) } returns existingRecords
            coEvery { storage.deleteDeathRecord(any()) } returns true

            mockkObject(PlayerData)
            every { PlayerData.fromPlayer(player, "survival") } returns PlayerData(
                playerUUID, playerName, "survival", GameMode.SURVIVAL
            )

            deathRecoveryService.captureDeathInventory(
                player,
                EntityDamageEvent.DamageCause.FALL,
                null,
                null
            )

            // Should not delete any records since we're at exactly the limit
            coVerify(exactly = 0) { storage.deleteDeathRecord(any()) }

            unmockkObject(PlayerData)
        }

        @Test
        @DisplayName("Should handle different damage causes")
        fun handleDifferentDamageCauses() = runTest {
            val playerData = PlayerData(playerUUID, playerName, "survival", GameMode.SURVIVAL)
            val world = mockk<World>(relaxed = true)
            every { world.name } returns "world"
            val location = mockk<Location>(relaxed = true)
            every { location.world } returns world
            every { location.x } returns 100.0
            every { location.y } returns 64.0
            every { location.z } returns 200.0

            val damageCauses = listOf(
                EntityDamageEvent.DamageCause.FALL,
                EntityDamageEvent.DamageCause.DROWNING,
                EntityDamageEvent.DamageCause.FIRE,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK,
                EntityDamageEvent.DamageCause.VOID
            )

            for (cause in damageCauses) {
                coEvery { storage.saveDeathRecord(any()) } returns true
                coEvery { storage.loadDeathRecords(playerUUID, any()) } returns emptyList()

                val result = deathRecoveryService.captureDeathFromData(
                    playerData,
                    location,
                    cause,
                    null,
                    null
                )

                assertNotNull(result)
                coVerify {
                    storage.saveDeathRecord(match { record ->
                        record.deathCause == cause.name
                    })
                }

                clearMocks(storage, answers = false)
            }
        }
    }
}

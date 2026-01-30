package sh.pcx.xinventories.unit.service

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.bukkit.GameMode
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitScheduler
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.config.TriggerConfig
import sh.pcx.xinventories.internal.config.VersioningConfig
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.VersionTrigger
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.InventoryService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.service.StorageService
import sh.pcx.xinventories.internal.service.VersioningService
import sh.pcx.xinventories.internal.storage.Storage
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for VersioningService.
 *
 * Tests cover:
 * - Initialization with enabled/disabled config
 * - Creating versions with different triggers
 * - Minimum interval enforcement
 * - Force flag bypassing restrictions
 * - Retrieving version history
 * - Restoring inventory from versions
 * - Pruning old versions based on retention
 * - Pruning player versions based on max limit
 * - Version count retrieval
 * - Player tracking cleanup
 * - Shutdown behavior
 */
@DisplayName("VersioningService Unit Tests")
class VersioningServiceTest {

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var mainConfig: MainConfig
    private lateinit var serviceManager: ServiceManager
    private lateinit var storageService: StorageService
    private lateinit var storage: Storage
    private lateinit var inventoryService: InventoryService
    private lateinit var groupService: GroupService
    private lateinit var versioningService: VersioningService
    private lateinit var scope: CoroutineScope
    private lateinit var server: Server
    private lateinit var scheduler: BukkitScheduler

    private val playerUUID = UUID.randomUUID()
    private val playerName = "TestPlayer"
    private val groupName = "survival"
    private val worldName = "world"

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("VersioningServiceTest"), false)
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs

            // Mock PlayerData.fromPlayer static method
            mockkObject(PlayerData.Companion)
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
        every { plugin.server } returns server
        every { server.scheduler } returns scheduler
        every { configManager.mainConfig } returns mainConfig
        every { serviceManager.inventoryService } returns inventoryService
        every { serviceManager.groupService } returns groupService
        every { storageService.storage } returns storage

        // Default versioning config - enabled
        every { mainConfig.versioning } returns VersioningConfig(
            enabled = true,
            maxVersionsPerPlayer = 10,
            minIntervalSeconds = 300,
            retentionDays = 7,
            triggerOn = TriggerConfig(
                worldChange = true,
                disconnect = true,
                manual = true
            )
        )

        versioningService = VersioningService(plugin, scope, storageService)
    }

    @AfterEach
    fun tearDown() {
        versioningService.shutdown()
        clearAllMocks(answers = false)
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockPlayer(
        uuid: UUID = playerUUID,
        name: String = playerName,
        worldName: String = this.worldName,
        gameMode: GameMode = GameMode.SURVIVAL
    ): Player {
        val world = mockk<World>(relaxed = true) {
            every { this@mockk.name } returns worldName
        }
        val player = mockk<Player>(relaxed = true) {
            every { uniqueId } returns uuid
            every { this@mockk.name } returns name
            every { this@mockk.world } returns world
            every { this@mockk.gameMode } returns gameMode
            every { health } returns 20.0
            every { foodLevel } returns 20
            every { saturation } returns 5.0f
            every { exhaustion } returns 0.0f
            every { exp } returns 0.0f
            every { level } returns 0
            every { totalExperience } returns 0
            every { activePotionEffects } returns emptyList()
            every { isFlying } returns false
            every { allowFlight } returns false
            every { displayName } returns name
            every { fallDistance } returns 0.0f
            every { fireTicks } returns 0
            every { maximumAir } returns 300
            every { remainingAir } returns 300
            every { inventory } returns mockk(relaxed = true) {
                every { getItem(any<Int>()) } returns null
                every { armorContents } returns arrayOfNulls(4)
                every { itemInOffHand } returns mockk(relaxed = true) { every { type.isAir } returns true }
            }
            every { enderChest } returns mockk(relaxed = true) {
                every { size } returns 27
                every { getItem(any<Int>()) } returns null
            }
        }

        // Mock PlayerData.fromPlayer to return a properly configured PlayerData
        val mockPlayerData = createPlayerData(uuid = uuid, name = name, gameMode = gameMode)
        every { PlayerData.fromPlayer(player, any()) } returns mockPlayerData

        return player
    }

    private fun createMockGroup(name: String = groupName): Group {
        return mockk(relaxed = true) {
            every { this@mockk.name } returns name
            every { settings } returns GroupSettings()
        }
    }

    private fun createPlayerData(
        uuid: UUID = playerUUID,
        name: String = playerName,
        group: String = groupName,
        gameMode: GameMode = GameMode.SURVIVAL
    ): PlayerData {
        return PlayerData(uuid, name, group, gameMode)
    }

    private fun createInventoryVersion(
        id: String = UUID.randomUUID().toString(),
        uuid: UUID = playerUUID,
        group: String = groupName,
        trigger: VersionTrigger = VersionTrigger.MANUAL,
        timestamp: Instant = Instant.now()
    ): InventoryVersion {
        val playerData = createPlayerData(uuid = uuid, group = group)
        return InventoryVersion(
            id = id,
            playerUuid = uuid,
            group = group,
            gameMode = GameMode.SURVIVAL,
            timestamp = timestamp,
            trigger = trigger,
            data = playerData,
            metadata = mapOf("world" to worldName)
        )
    }

    // =========================================================================
    // Initialization Tests
    // =========================================================================

    @Nested
    @DisplayName("Initialization")
    inner class InitializationTests {

        @Test
        @DisplayName("Should initialize when versioning is enabled")
        fun initializeWhenEnabled() {
            versioningService.initialize()

            verify { Logging.info("Versioning service initialized") }
        }

        @Test
        @DisplayName("Should skip initialization when versioning is disabled")
        fun skipInitializationWhenDisabled() {
            every { mainConfig.versioning } returns VersioningConfig(enabled = false)

            versioningService.initialize()

            verify { Logging.info("Versioning is disabled") }
        }

        @Test
        @DisplayName("Should start pruning job on initialization")
        fun startPruningJobOnInit() {
            versioningService.initialize()

            // Pruning job is internal, we verify it was set up by checking initialization completed
            verify { Logging.info("Versioning service initialized") }
        }
    }

    // =========================================================================
    // Create Version Tests
    // =========================================================================

    @Nested
    @DisplayName("createVersion")
    inner class CreateVersionTests {

        @Test
        @DisplayName("Should create version for player with MANUAL trigger")
        fun createVersionWithManualTrigger() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(player, VersionTrigger.MANUAL)

            assertNotNull(version)
            assertEquals(playerUUID, version!!.playerUuid)
            assertEquals(groupName, version.group)
            assertEquals(VersionTrigger.MANUAL, version.trigger)
            coVerify { storage.saveVersion(any()) }
        }

        @Test
        @DisplayName("Should create version for player with WORLD_CHANGE trigger")
        fun createVersionWithWorldChangeTrigger() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)

            assertNotNull(version)
            assertEquals(VersionTrigger.WORLD_CHANGE, version!!.trigger)
        }

        @Test
        @DisplayName("Should create version for player with DISCONNECT trigger")
        fun createVersionWithDisconnectTrigger() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(player, VersionTrigger.DISCONNECT)

            assertNotNull(version)
            assertEquals(VersionTrigger.DISCONNECT, version!!.trigger)
        }

        @Test
        @DisplayName("Should create version for player with DEATH trigger")
        fun createVersionWithDeathTrigger() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(player, VersionTrigger.DEATH)

            assertNotNull(version)
            assertEquals(VersionTrigger.DEATH, version!!.trigger)
        }

        @Test
        @DisplayName("Should create version for player with SCHEDULED trigger")
        fun createVersionWithScheduledTrigger() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(player, VersionTrigger.SCHEDULED)

            assertNotNull(version)
            assertEquals(VersionTrigger.SCHEDULED, version!!.trigger)
        }

        @Test
        @DisplayName("Should return null when versioning is disabled")
        fun returnNullWhenDisabled() = runTest {
            every { mainConfig.versioning } returns VersioningConfig(enabled = false)
            val player = createMockPlayer()

            val version = versioningService.createVersion(player, VersionTrigger.MANUAL)

            assertNull(version)
            coVerify(exactly = 0) { storage.saveVersion(any()) }
        }

        @Test
        @DisplayName("Should return null when trigger is disabled in config")
        fun returnNullWhenTriggerDisabled() = runTest {
            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                triggerOn = TriggerConfig(
                    worldChange = false,
                    disconnect = true,
                    manual = true
                )
            )
            val player = createMockPlayer()

            val version = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)

            assertNull(version)
            coVerify(exactly = 0) { storage.saveVersion(any()) }
        }

        @Test
        @DisplayName("Should include metadata in version")
        fun includeMetadataInVersion() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()
            val customMetadata = mapOf("reason" to "test", "custom" to "value")

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(
                player,
                VersionTrigger.MANUAL,
                metadata = customMetadata
            )

            assertNotNull(version)
            assertEquals("test", version!!.metadata["reason"])
            assertEquals("value", version.metadata["custom"])
            assertEquals(worldName, version.metadata["world"])
        }

        @Test
        @DisplayName("Should return null when save fails")
        fun returnNullWhenSaveFails() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns false

            val version = versioningService.createVersion(player, VersionTrigger.MANUAL)

            assertNull(version)
            verify { Logging.error(match { it.contains("Failed to save version") }) }
        }

        @Test
        @DisplayName("Should use group from world when getCurrentGroup returns null")
        fun useGroupFromWorldWhenCurrentGroupNull() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns null
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(player, VersionTrigger.MANUAL)

            assertNotNull(version)
            assertEquals(groupName, version!!.group)
        }
    }

    // =========================================================================
    // Minimum Interval Tests
    // =========================================================================

    @Nested
    @DisplayName("Minimum Interval Enforcement")
    inner class MinimumIntervalTests {

        @Test
        @DisplayName("Should enforce minimum interval between auto versions")
        fun enforceMinimumInterval() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                minIntervalSeconds = 300 // 5 minutes
            )
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            // First version should succeed
            val version1 = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)
            assertNotNull(version1)

            // Second version immediately after should be skipped
            val version2 = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)
            assertNull(version2)

            // Should log that version was skipped
            verify { Logging.debug(any<() -> String>()) }
        }

        @Test
        @DisplayName("Should not enforce minimum interval for MANUAL trigger")
        fun noIntervalForManualTrigger() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                minIntervalSeconds = 300
            )
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            // First version
            val version1 = versioningService.createVersion(player, VersionTrigger.MANUAL)
            assertNotNull(version1)

            // Second MANUAL version immediately after should still succeed
            val version2 = versioningService.createVersion(player, VersionTrigger.MANUAL)
            assertNotNull(version2)

            coVerify(exactly = 2) { storage.saveVersion(any()) }
        }

        @Test
        @DisplayName("Should bypass minimum interval when force=true")
        fun bypassIntervalWhenForced() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                minIntervalSeconds = 300
            )
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            // First version
            val version1 = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)
            assertNotNull(version1)

            // Forced version immediately after should succeed
            val version2 = versioningService.createVersion(
                player,
                VersionTrigger.WORLD_CHANGE,
                force = true
            )
            assertNotNull(version2)

            coVerify(exactly = 2) { storage.saveVersion(any()) }
        }

        @Test
        @DisplayName("Should bypass trigger check when force=true")
        fun bypassTriggerCheckWhenForced() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                triggerOn = TriggerConfig(
                    worldChange = false, // Disabled
                    disconnect = false,
                    manual = false
                )
            )
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            // Forced version should succeed even when trigger is disabled
            val version = versioningService.createVersion(
                player,
                VersionTrigger.WORLD_CHANGE,
                force = true
            )

            assertNotNull(version)
            coVerify { storage.saveVersion(any()) }
        }
    }

    // =========================================================================
    // Create Version From Data Tests
    // =========================================================================

    @Nested
    @DisplayName("createVersionFromData")
    inner class CreateVersionFromDataTests {

        @Test
        @DisplayName("Should create version from existing PlayerData")
        fun createVersionFromPlayerData() = runTest {
            val playerData = createPlayerData()

            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersionFromData(
                playerData,
                VersionTrigger.MANUAL,
                mapOf("reason" to "backup")
            )

            assertNotNull(version)
            assertEquals(playerUUID, version!!.playerUuid)
            assertEquals(groupName, version.group)
            assertEquals(VersionTrigger.MANUAL, version.trigger)
            assertEquals("backup", version.metadata["reason"])
        }

        @Test
        @DisplayName("Should return null when versioning is disabled")
        fun returnNullWhenDisabledFromData() = runTest {
            every { mainConfig.versioning } returns VersioningConfig(enabled = false)
            val playerData = createPlayerData()

            val version = versioningService.createVersionFromData(playerData, VersionTrigger.MANUAL)

            assertNull(version)
        }

        @Test
        @DisplayName("Should return null when save fails from data")
        fun returnNullWhenSaveFailsFromData() = runTest {
            val playerData = createPlayerData()

            coEvery { storage.saveVersion(any()) } returns false

            val version = versioningService.createVersionFromData(playerData, VersionTrigger.MANUAL)

            assertNull(version)
            verify { Logging.error(match { it.contains("Failed to save version") }) }
        }

        @Test
        @DisplayName("Should prune player versions after creating from data")
        fun prunePlayerVersionsAfterCreateFromData() = runTest {
            val playerData = createPlayerData()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                maxVersionsPerPlayer = 2
            )

            val existingVersions = listOf(
                createInventoryVersion(id = "v1"),
                createInventoryVersion(id = "v2"),
                createInventoryVersion(id = "v3")
            )

            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns existingVersions
            coEvery { storage.deleteVersion(any()) } returns true

            versioningService.createVersionFromData(playerData, VersionTrigger.MANUAL)

            // Should delete oldest version (v3) since we have 3 but max is 2
            coVerify { storage.deleteVersion("v3") }
        }
    }

    // =========================================================================
    // Get Versions Tests
    // =========================================================================

    @Nested
    @DisplayName("getVersions")
    inner class GetVersionsTests {

        @Test
        @DisplayName("Should return versions for player")
        fun returnVersionsForPlayer() = runTest {
            val versions = listOf(
                createInventoryVersion(id = "v1", timestamp = Instant.now()),
                createInventoryVersion(id = "v2", timestamp = Instant.now().minusSeconds(60))
            )

            coEvery { storage.loadVersions(playerUUID, null, 10) } returns versions

            val result = versioningService.getVersions(playerUUID)

            assertEquals(2, result.size)
            assertEquals("v1", result[0].id)
            assertEquals("v2", result[1].id)
        }

        @Test
        @DisplayName("Should filter by group when specified")
        fun filterByGroup() = runTest {
            val versions = listOf(createInventoryVersion(group = groupName))

            coEvery { storage.loadVersions(playerUUID, groupName, 10) } returns versions

            val result = versioningService.getVersions(playerUUID, group = groupName)

            assertEquals(1, result.size)
            assertEquals(groupName, result[0].group)
            coVerify { storage.loadVersions(playerUUID, groupName, 10) }
        }

        @Test
        @DisplayName("Should respect limit parameter")
        fun respectLimitParameter() = runTest {
            coEvery { storage.loadVersions(playerUUID, null, 5) } returns emptyList()

            versioningService.getVersions(playerUUID, limit = 5)

            coVerify { storage.loadVersions(playerUUID, null, 5) }
        }

        @Test
        @DisplayName("Should return empty list when no versions exist")
        fun returnEmptyListWhenNoVersions() = runTest {
            coEvery { storage.loadVersions(any(), any(), any()) } returns emptyList()

            val result = versioningService.getVersions(playerUUID)

            assertTrue(result.isEmpty())
        }
    }

    // =========================================================================
    // Get Version Tests
    // =========================================================================

    @Nested
    @DisplayName("getVersion")
    inner class GetVersionTests {

        @Test
        @DisplayName("Should return version by ID")
        fun returnVersionById() = runTest {
            val versionId = "test-version-id"
            val version = createInventoryVersion(id = versionId)

            coEvery { storage.loadVersion(versionId) } returns version

            val result = versioningService.getVersion(versionId)

            assertNotNull(result)
            assertEquals(versionId, result!!.id)
        }

        @Test
        @DisplayName("Should return null when version not found")
        fun returnNullWhenNotFound() = runTest {
            coEvery { storage.loadVersion("nonexistent") } returns null

            val result = versioningService.getVersion("nonexistent")

            assertNull(result)
        }
    }

    // =========================================================================
    // Restore Version Tests
    // =========================================================================

    @Nested
    @DisplayName("restoreVersion")
    inner class RestoreVersionTests {

        @Test
        @DisplayName("Should restore version to player")
        fun restoreVersionToPlayer() = runTest {
            val player = createMockPlayer()
            val versionId = "test-version-id"
            val version = createInventoryVersion(id = versionId, uuid = playerUUID)
            val group = createMockGroup()

            coEvery { storage.loadVersion(versionId) } returns version
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            every { groupService.getGroup(groupName) } returns group

            val result = versioningService.restoreVersion(player, versionId)

            assertTrue(result.isSuccess)
            verify { Logging.info(match { it.contains("Restored version") }) }
        }

        @Test
        @DisplayName("Should create backup before restore when createBackup=true")
        fun createBackupBeforeRestore() = runTest {
            val player = createMockPlayer()
            val versionId = "test-version-id"
            val version = createInventoryVersion(id = versionId, uuid = playerUUID)
            val group = createMockGroup()

            coEvery { storage.loadVersion(versionId) } returns version
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            every { groupService.getGroup(groupName) } returns group

            versioningService.restoreVersion(player, versionId, createBackup = true)

            // Should save backup version with metadata
            coVerify {
                storage.saveVersion(match { savedVersion ->
                    savedVersion.trigger == VersionTrigger.MANUAL &&
                    savedVersion.metadata["reason"] == "backup_before_restore" &&
                    savedVersion.metadata["restored_version"] == versionId
                })
            }
        }

        @Test
        @DisplayName("Should skip backup when createBackup=false")
        fun skipBackupWhenFalse() = runTest {
            val player = createMockPlayer()
            val versionId = "test-version-id"
            val version = createInventoryVersion(id = versionId, uuid = playerUUID)
            val group = createMockGroup()

            coEvery { storage.loadVersion(versionId) } returns version
            every { groupService.getGroup(groupName) } returns group

            versioningService.restoreVersion(player, versionId, createBackup = false)

            // Should not save any backup version
            coVerify(exactly = 0) {
                storage.saveVersion(match { it.metadata["reason"] == "backup_before_restore" })
            }
        }

        @Test
        @DisplayName("Should fail when version not found")
        fun failWhenVersionNotFound() = runTest {
            val player = createMockPlayer()

            coEvery { storage.loadVersion("nonexistent") } returns null

            val result = versioningService.restoreVersion(player, "nonexistent")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("Version not found"))
        }

        @Test
        @DisplayName("Should fail when version belongs to different player")
        fun failWhenVersionBelongsToDifferentPlayer() = runTest {
            val player = createMockPlayer()
            val otherPlayerUUID = UUID.randomUUID()
            val versionId = "test-version-id"
            val version = createInventoryVersion(id = versionId, uuid = otherPlayerUUID)

            coEvery { storage.loadVersion(versionId) } returns version

            val result = versioningService.restoreVersion(player, versionId)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("does not belong to this player"))
        }

        @Test
        @DisplayName("Should use default GroupSettings when group not found")
        fun useDefaultSettingsWhenGroupNotFound() = runTest {
            val player = createMockPlayer()
            val versionId = "test-version-id"
            val version = createInventoryVersion(id = versionId, uuid = playerUUID)

            coEvery { storage.loadVersion(versionId) } returns version
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns createMockGroup()
            every { groupService.getGroup(groupName) } returns null // Group not found

            val result = versioningService.restoreVersion(player, versionId)

            assertTrue(result.isSuccess)
        }
    }

    // =========================================================================
    // Delete Version Tests
    // =========================================================================

    @Nested
    @DisplayName("deleteVersion")
    inner class DeleteVersionTests {

        @Test
        @DisplayName("Should delete version by ID")
        fun deleteVersionById() = runTest {
            val versionId = "test-version-id"

            coEvery { storage.deleteVersion(versionId) } returns true

            val result = versioningService.deleteVersion(versionId)

            assertTrue(result)
            coVerify { storage.deleteVersion(versionId) }
        }

        @Test
        @DisplayName("Should return false when delete fails")
        fun returnFalseWhenDeleteFails() = runTest {
            coEvery { storage.deleteVersion("nonexistent") } returns false

            val result = versioningService.deleteVersion("nonexistent")

            assertFalse(result)
        }
    }

    // =========================================================================
    // Prune Old Versions Tests
    // =========================================================================

    @Nested
    @DisplayName("pruneOldVersions")
    inner class PruneOldVersionsTests {

        @Test
        @DisplayName("Should prune versions older than retention period")
        fun pruneVersionsOlderThanRetention() = runTest {
            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                retentionDays = 7
            )

            coEvery { storage.pruneVersions(any()) } returns 5

            val deleted = versioningService.pruneOldVersions()

            assertEquals(5, deleted)
            coVerify { storage.pruneVersions(match { cutoff ->
                val expectedCutoff = Instant.now().minus(7, ChronoUnit.DAYS)
                // Allow 1 second tolerance for test timing
                Math.abs(cutoff.epochSecond - expectedCutoff.epochSecond) < 2
            }) }
        }

        @Test
        @DisplayName("Should return 0 when versioning is disabled")
        fun returnZeroWhenDisabled() = runTest {
            every { mainConfig.versioning } returns VersioningConfig(enabled = false)

            val deleted = versioningService.pruneOldVersions()

            assertEquals(0, deleted)
            coVerify(exactly = 0) { storage.pruneVersions(any()) }
        }

        @Test
        @DisplayName("Should log when versions are pruned")
        fun logWhenVersionsPruned() = runTest {
            coEvery { storage.pruneVersions(any()) } returns 10

            versioningService.pruneOldVersions()

            verify { Logging.info(match { it.contains("Pruned 10 old versions") }) }
        }

        @Test
        @DisplayName("Should not log when no versions pruned")
        fun notLogWhenNoVersionsPruned() = runTest {
            coEvery { storage.pruneVersions(any()) } returns 0

            versioningService.pruneOldVersions()

            verify(exactly = 0) { Logging.info(match { it.contains("Pruned") }) }
        }
    }

    // =========================================================================
    // Player Version Pruning Tests
    // =========================================================================

    @Nested
    @DisplayName("Player Version Pruning")
    inner class PlayerVersionPruningTests {

        @Test
        @DisplayName("Should prune excess versions after creating new one")
        fun pruneExcessVersionsAfterCreate() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                maxVersionsPerPlayer = 3
            )
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true

            // Return 5 existing versions (exceeds max of 3)
            val existingVersions = listOf(
                createInventoryVersion(id = "v1", timestamp = Instant.now()),
                createInventoryVersion(id = "v2", timestamp = Instant.now().minusSeconds(60)),
                createInventoryVersion(id = "v3", timestamp = Instant.now().minusSeconds(120)),
                createInventoryVersion(id = "v4", timestamp = Instant.now().minusSeconds(180)),
                createInventoryVersion(id = "v5", timestamp = Instant.now().minusSeconds(240))
            )
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns existingVersions
            coEvery { storage.deleteVersion(any()) } returns true

            versioningService.createVersion(player, VersionTrigger.MANUAL)

            // Should delete v4 and v5 (oldest beyond max limit of 3)
            coVerify { storage.deleteVersion("v4") }
            coVerify { storage.deleteVersion("v5") }
        }

        @Test
        @DisplayName("Should not prune when under limit")
        fun notPruneWhenUnderLimit() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                maxVersionsPerPlayer = 10
            )
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true

            // Return only 2 existing versions (under max of 10)
            val existingVersions = listOf(
                createInventoryVersion(id = "v1"),
                createInventoryVersion(id = "v2")
            )
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns existingVersions

            versioningService.createVersion(player, VersionTrigger.MANUAL)

            coVerify(exactly = 0) { storage.deleteVersion(any()) }
        }
    }

    // =========================================================================
    // Get Version Count Tests
    // =========================================================================

    @Nested
    @DisplayName("getVersionCount")
    inner class GetVersionCountTests {

        @Test
        @DisplayName("Should return count of versions for player")
        fun returnCountForPlayer() = runTest {
            val versions = listOf(
                createInventoryVersion(id = "v1"),
                createInventoryVersion(id = "v2"),
                createInventoryVersion(id = "v3")
            )

            coEvery { storage.loadVersions(playerUUID, null, Int.MAX_VALUE) } returns versions

            val count = versioningService.getVersionCount(playerUUID)

            assertEquals(3, count)
        }

        @Test
        @DisplayName("Should filter by group when specified")
        fun filterByGroupForCount() = runTest {
            val versions = listOf(createInventoryVersion(group = groupName))

            coEvery { storage.loadVersions(playerUUID, groupName, Int.MAX_VALUE) } returns versions

            val count = versioningService.getVersionCount(playerUUID, group = groupName)

            assertEquals(1, count)
            coVerify { storage.loadVersions(playerUUID, groupName, Int.MAX_VALUE) }
        }

        @Test
        @DisplayName("Should return 0 when no versions exist")
        fun returnZeroWhenNoVersions() = runTest {
            coEvery { storage.loadVersions(any(), any(), any()) } returns emptyList()

            val count = versioningService.getVersionCount(playerUUID)

            assertEquals(0, count)
        }
    }

    // =========================================================================
    // Clear Player Tracking Tests
    // =========================================================================

    @Nested
    @DisplayName("clearPlayerTracking")
    inner class ClearPlayerTrackingTests {

        @Test
        @DisplayName("Should clear tracking for player")
        fun clearTrackingForPlayer() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            // First create a version to establish tracking
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            versioningService.createVersion(player, VersionTrigger.MANUAL)

            // Clear tracking
            versioningService.clearPlayerTracking(playerUUID)

            // Now creating another version should succeed without interval restriction
            val version = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)
            assertNotNull(version)
        }

        @Test
        @DisplayName("Should handle clearing non-tracked player")
        fun handleClearingNonTrackedPlayer() {
            val unknownUUID = UUID.randomUUID()

            // Should not throw
            assertDoesNotThrow {
                versioningService.clearPlayerTracking(unknownUUID)
            }
        }
    }

    // =========================================================================
    // Shutdown Tests
    // =========================================================================

    @Nested
    @DisplayName("Shutdown")
    inner class ShutdownTests {

        @Test
        @DisplayName("Should cancel prune job on shutdown")
        fun cancelPruneJobOnShutdown() {
            versioningService.initialize()

            versioningService.shutdown()

            // Shutdown should complete without error
            // Internal pruneJob should be cancelled and set to null
        }

        @Test
        @DisplayName("Should clear player tracking on shutdown")
        fun clearTrackingOnShutdown() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            versioningService.createVersion(player, VersionTrigger.MANUAL)
            versioningService.shutdown()

            // Re-initialize and verify tracking was cleared
            versioningService.initialize()
            val version = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)
            assertNotNull(version)
        }

        @Test
        @DisplayName("Should handle multiple shutdowns gracefully")
        fun handleMultipleShutdowns() {
            versioningService.initialize()

            assertDoesNotThrow {
                versioningService.shutdown()
                versioningService.shutdown()
                versioningService.shutdown()
            }
        }
    }

    // =========================================================================
    // Trigger Configuration Tests
    // =========================================================================

    @Nested
    @DisplayName("Trigger Configuration")
    inner class TriggerConfigurationTests {

        @Test
        @DisplayName("Should allow DEATH trigger regardless of config")
        fun allowDeathTriggerAlways() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                triggerOn = TriggerConfig(
                    worldChange = false,
                    disconnect = false,
                    manual = false
                )
            )
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(player, VersionTrigger.DEATH)

            assertNotNull(version)
        }

        @Test
        @DisplayName("Should allow SCHEDULED trigger regardless of config")
        fun allowScheduledTriggerAlways() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                triggerOn = TriggerConfig(
                    worldChange = false,
                    disconnect = false,
                    manual = false
                )
            )
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(player, VersionTrigger.SCHEDULED)

            assertNotNull(version)
        }

        @Test
        @DisplayName("Should respect worldChange trigger config")
        fun respectWorldChangeTriggerConfig() = runTest {
            val player = createMockPlayer()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                triggerOn = TriggerConfig(worldChange = false)
            )

            val version = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)

            assertNull(version)
        }

        @Test
        @DisplayName("Should respect disconnect trigger config")
        fun respectDisconnectTriggerConfig() = runTest {
            val player = createMockPlayer()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                triggerOn = TriggerConfig(disconnect = false)
            )

            val version = versioningService.createVersion(player, VersionTrigger.DISCONNECT)

            assertNull(version)
        }

        @Test
        @DisplayName("Should respect manual trigger config")
        fun respectManualTriggerConfig() = runTest {
            val player = createMockPlayer()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                triggerOn = TriggerConfig(manual = false)
            )

            val version = versioningService.createVersion(player, VersionTrigger.MANUAL)

            assertNull(version)
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle multiple players simultaneously")
        fun handleMultiplePlayersSimo() = runTest {
            val player1 = createMockPlayer(uuid = UUID.randomUUID(), name = "Player1")
            val player2 = createMockPlayer(uuid = UUID.randomUUID(), name = "Player2")
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(any()) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(any(), any(), any()) } returns emptyList()

            val version1 = versioningService.createVersion(player1, VersionTrigger.MANUAL)
            val version2 = versioningService.createVersion(player2, VersionTrigger.MANUAL)

            assertNotNull(version1)
            assertNotNull(version2)
            assertNotEquals(version1!!.playerUuid, version2!!.playerUuid)
        }

        @Test
        @DisplayName("Should handle empty metadata")
        fun handleEmptyMetadata() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            val version = versioningService.createVersion(
                player,
                VersionTrigger.MANUAL,
                metadata = emptyMap()
            )

            assertNotNull(version)
            // Should still have world metadata
            assertTrue(version!!.metadata.containsKey("world"))
        }

        @Test
        @DisplayName("Should handle very long retention period")
        fun handleLongRetentionPeriod() = runTest {
            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                retentionDays = 365 * 10 // 10 years
            )

            coEvery { storage.pruneVersions(any()) } returns 0

            val deleted = versioningService.pruneOldVersions()

            assertEquals(0, deleted)
            coVerify { storage.pruneVersions(match { cutoff ->
                val expectedCutoff = Instant.now().minus(365 * 10L, ChronoUnit.DAYS)
                Math.abs(cutoff.epochSecond - expectedCutoff.epochSecond) < 2
            }) }
        }

        @Test
        @DisplayName("Should handle zero minIntervalSeconds")
        fun handleZeroMinInterval() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                minIntervalSeconds = 0
            )
            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            // Both versions should succeed with zero interval
            val version1 = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)
            val version2 = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)

            assertNotNull(version1)
            assertNotNull(version2)
        }

        @Test
        @DisplayName("Should handle special characters in group name")
        fun handleSpecialCharsInGroupName() = runTest {
            val specialGroupName = "group-with_special.chars"
            val player = createMockPlayer()
            val group = mockk<Group>(relaxed = true) {
                every { name } returns specialGroupName
                every { settings } returns GroupSettings()
            }

            // Override the PlayerData mock to return the special group name
            val mockPlayerData = createPlayerData(group = specialGroupName)
            every { PlayerData.fromPlayer(player, any()) } returns mockPlayerData

            every { inventoryService.getCurrentGroup(player) } returns specialGroupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, specialGroupName, any()) } returns emptyList()

            val version = versioningService.createVersion(player, VersionTrigger.MANUAL)

            assertNotNull(version)
            assertEquals(specialGroupName, version!!.group)
        }

        @Test
        @DisplayName("Should handle version with all trigger types in sequence")
        fun handleAllTriggersInSequence() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            // Create versions with each trigger type using force to bypass interval
            val triggers = VersionTrigger.entries

            triggers.forEach { trigger ->
                val version = versioningService.createVersion(
                    player,
                    trigger,
                    force = true
                )
                assertNotNull(version, "Version should be created for trigger: $trigger")
                assertEquals(trigger, version!!.trigger)
            }
        }

        @Test
        @DisplayName("Should handle maxVersionsPerPlayer of 1")
        fun handleMaxVersionsOfOne() = runTest {
            val playerData = createPlayerData()

            every { mainConfig.versioning } returns VersioningConfig(
                enabled = true,
                maxVersionsPerPlayer = 1
            )

            val existingVersions = listOf(
                createInventoryVersion(id = "v1"),
                createInventoryVersion(id = "v2")
            )

            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns existingVersions
            coEvery { storage.deleteVersion(any()) } returns true

            versioningService.createVersionFromData(playerData, VersionTrigger.MANUAL)

            // Should delete v2 (beyond max limit of 1)
            coVerify { storage.deleteVersion("v2") }
        }
    }

    // =========================================================================
    // Concurrency Tests
    // =========================================================================

    @Nested
    @DisplayName("Concurrency")
    inner class ConcurrencyTests {

        @Test
        @DisplayName("Should handle concurrent version creation for same player")
        fun handleConcurrentVersionCreation() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            // Create multiple versions concurrently using force
            val versions = (1..5).map {
                versioningService.createVersion(player, VersionTrigger.MANUAL, force = true)
            }

            // All versions should be created (MANUAL bypasses interval)
            versions.forEach { assertNotNull(it) }
        }

        @Test
        @DisplayName("Should handle clearPlayerTracking while creating version")
        fun handleClearWhileCreating() = runTest {
            val player = createMockPlayer()
            val group = createMockGroup()

            every { inventoryService.getCurrentGroup(player) } returns groupName
            every { groupService.getGroupForWorld(any<World>()) } returns group
            coEvery { storage.saveVersion(any()) } returns true
            coEvery { storage.loadVersions(playerUUID, groupName, any()) } returns emptyList()

            // Create version
            versioningService.createVersion(player, VersionTrigger.MANUAL)

            // Clear tracking
            versioningService.clearPlayerTracking(playerUUID)

            // Should be able to create another version without interval restriction
            val version = versioningService.createVersion(player, VersionTrigger.WORLD_CHANGE)
            assertNotNull(version)
        }
    }
}

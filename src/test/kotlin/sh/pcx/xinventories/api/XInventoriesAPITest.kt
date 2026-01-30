package sh.pcx.xinventories.api

import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.event.*
import sh.pcx.xinventories.api.model.*
import sh.pcx.xinventories.internal.api.XInventoriesAPIImpl
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.config.StorageConfig
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.service.*
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

@DisplayName("XInventoriesAPI")
class XInventoriesAPITest {

    private lateinit var plugin: XInventories
    private lateinit var api: XInventoriesAPIImpl
    private lateinit var serviceManager: ServiceManager
    private lateinit var storageService: StorageService
    private lateinit var inventoryService: InventoryService
    private lateinit var groupService: GroupService
    private lateinit var migrationService: MigrationService
    private lateinit var backupService: BackupService
    private lateinit var configManager: ConfigManager
    private lateinit var logger: Logger

    private val testUuid = UUID.randomUUID()
    private val testPlayerName = "TestPlayer"

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        storageService = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)
        groupService = mockk(relaxed = true)
        migrationService = mockk(relaxed = true)
        backupService = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        logger = mockk(relaxed = true)

        every { plugin.plugin } returns plugin
        every { plugin.serviceManager } returns serviceManager
        every { plugin.configManager } returns configManager
        every { plugin.logger } returns logger
        every { serviceManager.storageService } returns storageService
        every { serviceManager.inventoryService } returns inventoryService
        every { serviceManager.groupService } returns groupService
        every { serviceManager.migrationService } returns migrationService
        every { serviceManager.backupService } returns backupService

        api = XInventoriesAPIImpl(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // ═══════════════════════════════════════════════════════════════════
    // Inventory Operations
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Player Data")
    inner class GetPlayerData {

        @Test
        @DisplayName("returns CompletableFuture for online player")
        fun returnsCompletableFutureForOnlinePlayer() {
            val player = mockk<Player>()
            every { player.uniqueId } returns testUuid
            val snapshot = createTestSnapshot()
            val playerData = mockk<PlayerData>()
            every { playerData.toSnapshot() } returns snapshot
            coEvery { storageService.loadPlayerData(testUuid, "survival", null) } returns playerData

            val future = api.getPlayerData(player, "survival")

            Assertions.assertNotNull(future)
            assertTrue(future is CompletableFuture)
            val result = future.get(5, TimeUnit.SECONDS)
            Assertions.assertNotNull(result)
            assertEquals(testUuid, result?.uuid)
        }

        @Test
        @DisplayName("returns CompletableFuture with gamemode override")
        fun returnsCompletableFutureWithGamemodeOverride() {
            val player = mockk<Player>()
            every { player.uniqueId } returns testUuid
            val snapshot = createTestSnapshot(gameMode = GameMode.CREATIVE)
            val playerData = mockk<PlayerData>()
            every { playerData.toSnapshot() } returns snapshot
            coEvery { storageService.loadPlayerData(testUuid, "creative", GameMode.CREATIVE) } returns playerData

            val future = api.getPlayerData(player, "creative", GameMode.CREATIVE)

            val result = future.get(5, TimeUnit.SECONDS)
            Assertions.assertNotNull(result)
            assertEquals(GameMode.CREATIVE, result?.gameMode)
        }

        @Test
        @DisplayName("returns null when data not found")
        fun returnsNullWhenDataNotFound() {
            val player = mockk<Player>()
            every { player.uniqueId } returns testUuid
            coEvery { storageService.loadPlayerData(testUuid, "nonexistent", null) } returns null

            val future = api.getPlayerData(player, "nonexistent")

            val result = future.get(5, TimeUnit.SECONDS)
            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("Get Player Data by UUID (Offline)")
    inner class GetPlayerDataByUUID {

        @Test
        @DisplayName("returns CompletableFuture for offline player by UUID")
        fun returnsCompletableFutureForOfflinePlayer() {
            val offlineUuid = UUID.randomUUID()
            val snapshot = createTestSnapshot(uuid = offlineUuid)
            val playerData = mockk<PlayerData>()
            every { playerData.toSnapshot() } returns snapshot
            coEvery { storageService.loadPlayerData(offlineUuid, "survival", null) } returns playerData

            val future = api.getPlayerData(offlineUuid, "survival")

            val result = future.get(5, TimeUnit.SECONDS)
            Assertions.assertNotNull(result)
            assertEquals(offlineUuid, result?.uuid)
        }

        @Test
        @DisplayName("returns data for specific gamemode")
        fun returnsDataForSpecificGamemode() {
            val offlineUuid = UUID.randomUUID()
            val snapshot = createTestSnapshot(uuid = offlineUuid, gameMode = GameMode.ADVENTURE)
            val playerData = mockk<PlayerData>()
            every { playerData.toSnapshot() } returns snapshot
            coEvery { storageService.loadPlayerData(offlineUuid, "adventure", GameMode.ADVENTURE) } returns playerData

            val future = api.getPlayerData(offlineUuid, "adventure", GameMode.ADVENTURE)

            val result = future.get(5, TimeUnit.SECONDS)
            Assertions.assertNotNull(result)
            assertEquals(GameMode.ADVENTURE, result?.gameMode)
        }

        @Test
        @DisplayName("returns null for unknown offline player")
        fun returnsNullForUnknownOfflinePlayer() {
            val unknownUuid = UUID.randomUUID()
            coEvery { storageService.loadPlayerData(unknownUuid, any(), any()) } returns null

            val future = api.getPlayerData(unknownUuid, "survival")

            val result = future.get(5, TimeUnit.SECONDS)
            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("Get All Player Data")
    inner class GetAllPlayerData {

        @Test
        @DisplayName("returns all snapshots across groups")
        fun returnsAllSnapshotsAcrossGroups() {
            val survivalSnapshot = createTestSnapshot(group = "survival")
            val creativeSnapshot = createTestSnapshot(group = "creative")
            val survivalData = mockk<PlayerData>()
            val creativeData = mockk<PlayerData>()
            every { survivalData.toSnapshot() } returns survivalSnapshot
            every { creativeData.toSnapshot() } returns creativeSnapshot
            coEvery { storageService.loadAllPlayerData(testUuid) } returns mapOf(
                "survival" to survivalData,
                "creative" to creativeData
            )

            val future = api.getAllPlayerData(testUuid)

            val result = future.get(5, TimeUnit.SECONDS)
            assertEquals(2, result.size)
            assertTrue(result.containsKey("survival"))
            assertTrue(result.containsKey("creative"))
            assertEquals("survival", result["survival"]?.group)
            assertEquals("creative", result["creative"]?.group)
        }

        @Test
        @DisplayName("returns empty map when no data exists")
        fun returnsEmptyMapWhenNoDataExists() {
            coEvery { storageService.loadAllPlayerData(testUuid) } returns emptyMap()

            val future = api.getAllPlayerData(testUuid)

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("Save Player Data")
    inner class SavePlayerData {

        @Test
        @DisplayName("saves player data to current group")
        fun savesPlayerDataToCurrentGroup() {
            val player = mockk<Player>()
            coEvery { inventoryService.saveInventory(player, null) } returns true

            val future = api.savePlayerData(player)

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
            coVerify { inventoryService.saveInventory(player, null) }
        }

        @Test
        @DisplayName("saves player data to specified group")
        fun savesPlayerDataToSpecifiedGroup() {
            val player = mockk<Player>()
            coEvery { inventoryService.saveInventory(player, "creative") } returns true

            val future = api.savePlayerData(player, "creative")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
            coVerify { inventoryService.saveInventory(player, "creative") }
        }

        @Test
        @DisplayName("returns failure on exception")
        fun returnsFailureOnException() {
            val player = mockk<Player>()
            coEvery { inventoryService.saveInventory(player, any()) } throws RuntimeException("Save failed")

            val future = api.savePlayerData(player)

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Save failed") == true)
        }
    }

    @Nested
    @DisplayName("Load Player Data")
    inner class LoadPlayerData {

        @Test
        @DisplayName("loads and applies inventory data")
        fun loadsAndAppliesInventoryData() {
            val player = mockk<Player>()
            val group = createTestGroup("survival")
            every { groupService.getGroup("survival") } returns group
            coEvery { inventoryService.loadInventory(player, any(), InventoryLoadEvent.LoadReason.API) } returns true

            val future = api.loadPlayerData(player, "survival")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
            coVerify { inventoryService.loadInventory(player, any(), InventoryLoadEvent.LoadReason.API) }
        }

        @Test
        @DisplayName("returns failure for non-existent group")
        fun returnsFailureForNonExistentGroup() {
            val player = mockk<Player>()
            every { groupService.getGroup("nonexistent") } returns null

            val future = api.loadPlayerData(player, "nonexistent")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Group not found") == true)
        }

        @Test
        @DisplayName("loads with specific gamemode")
        fun loadsWithSpecificGamemode() {
            val player = mockk<Player>()
            val group = createTestGroup("creative")
            every { groupService.getGroup("creative") } returns group
            coEvery { inventoryService.loadInventory(player, any(), InventoryLoadEvent.LoadReason.API) } returns true

            val future = api.loadPlayerData(player, "creative", GameMode.CREATIVE)

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
        }
    }

    @Nested
    @DisplayName("Switch Inventory")
    inner class SwitchInventory {

        @Test
        @DisplayName("switches inventory between groups")
        fun switchesInventoryBetweenGroups() {
            val player = mockk<Player>()
            val group = createTestGroup("creative")
            every { groupService.getGroup("creative") } returns group
            coEvery { inventoryService.saveInventory(player) } returns true
            coEvery { inventoryService.loadInventory(player, any(), InventoryLoadEvent.LoadReason.API) } returns true

            val future = api.switchInventory(player, "creative")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
            coVerify(ordering = Ordering.ORDERED) {
                inventoryService.saveInventory(player)
                inventoryService.loadInventory(player, any(), InventoryLoadEvent.LoadReason.API)
            }
        }

        @Test
        @DisplayName("returns failure for non-existent target group")
        fun returnsFailureForNonExistentTargetGroup() {
            val player = mockk<Player>()
            every { groupService.getGroup("nonexistent") } returns null

            val future = api.switchInventory(player, "nonexistent")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Group not found") == true)
        }

        @Test
        @DisplayName("switches with gamemode override")
        fun switchesWithGamemodeOverride() {
            val player = mockk<Player>()
            val group = createTestGroup("adventure")
            every { groupService.getGroup("adventure") } returns group
            coEvery { inventoryService.saveInventory(player) } returns true
            coEvery { inventoryService.loadInventory(player, any(), InventoryLoadEvent.LoadReason.API) } returns true

            val future = api.switchInventory(player, "adventure", GameMode.ADVENTURE)

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
        }
    }

    @Nested
    @DisplayName("Clear Player Data")
    inner class ClearPlayerData {

        @Test
        @DisplayName("clears player data for group")
        fun clearsPlayerDataForGroup() {
            coEvery { storageService.deletePlayerData(testUuid, "survival", null) } returns true

            val future = api.clearPlayerData(testUuid, "survival")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
            coVerify { storageService.deletePlayerData(testUuid, "survival", null) }
        }

        @Test
        @DisplayName("clears player data for specific gamemode")
        fun clearsPlayerDataForSpecificGamemode() {
            coEvery { storageService.deletePlayerData(testUuid, "creative", GameMode.CREATIVE) } returns true

            val future = api.clearPlayerData(testUuid, "creative", GameMode.CREATIVE)

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
            coVerify { storageService.deletePlayerData(testUuid, "creative", GameMode.CREATIVE) }
        }

        @Test
        @DisplayName("returns failure on exception")
        fun returnsFailureOnException() {
            coEvery { storageService.deletePlayerData(testUuid, any(), any()) } throws RuntimeException("Delete failed")

            val future = api.clearPlayerData(testUuid, "survival")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isFailure)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Group Operations
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Group")
    inner class GetGroup {

        @Test
        @DisplayName("returns group by name")
        fun returnsGroupByName() {
            val group = createTestGroup("survival")
            every { groupService.getGroup("survival") } returns group

            val result = api.getGroup("survival")

            Assertions.assertNotNull(result)
            assertEquals("survival", result?.name)
        }

        @Test
        @DisplayName("returns null for non-existent group")
        fun returnsNullForNonExistentGroup() {
            every { groupService.getGroup("nonexistent") } returns null

            val result = api.getGroup("nonexistent")

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("Get All Groups")
    inner class GetAllGroups {

        @Test
        @DisplayName("returns list of all groups")
        fun returnsListOfAllGroups() {
            val groups = listOf(
                createTestGroup("survival"),
                createTestGroup("creative"),
                createTestGroup("default", isDefault = true)
            )
            every { groupService.getAllGroups() } returns groups

            val result = api.getGroups()

            assertEquals(3, result.size)
            assertTrue(result.any { it.name == "survival" })
            assertTrue(result.any { it.name == "creative" })
            assertTrue(result.any { it.name == "default" })
        }

        @Test
        @DisplayName("returns empty list when no groups")
        fun returnsEmptyListWhenNoGroups() {
            every { groupService.getAllGroups() } returns emptyList()

            val result = api.getGroups()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("Get Group for World")
    inner class GetGroupForWorld {

        @Test
        @DisplayName("returns group for world object")
        fun returnsGroupForWorldObject() {
            val world = mockk<World>()
            val group = createTestGroup("survival", worlds = setOf("survival_world"))
            every { groupService.getGroupForWorld(world) } returns group

            val result = api.getGroupForWorld(world)

            assertEquals("survival", result.name)
        }

        @Test
        @DisplayName("returns group for world name")
        fun returnsGroupForWorldName() {
            val group = createTestGroup("creative", worlds = setOf("creative_world"))
            every { groupService.getGroupForWorld("creative_world") } returns group

            val result = api.getGroupForWorld("creative_world")

            assertEquals("creative", result.name)
        }
    }

    @Nested
    @DisplayName("Get Default Group")
    inner class GetDefaultGroup {

        @Test
        @DisplayName("returns default group")
        fun returnsDefaultGroup() {
            val defaultGroup = createTestGroup("default", isDefault = true)
            every { groupService.getDefaultGroup() } returns defaultGroup

            val result = api.getDefaultGroup()

            assertEquals("default", result.name)
            assertTrue(result.isDefault)
        }
    }

    @Nested
    @DisplayName("Create Group")
    inner class CreateGroup {

        @Test
        @DisplayName("creates group with default settings")
        fun createsGroupWithDefaultSettings() {
            val createdGroup = InventoryGroup(
                name = "newgroup",
                worlds = emptySet(),
                patterns = emptyList(),
                priority = 0,
                parent = null,
                settings = GroupSettings(),
                isDefault = false
            )
            every { groupService.createGroup(
                name = "newgroup",
                settings = any(),
                worlds = any(),
                patterns = any(),
                priority = any(),
                parent = any()
            ) } returns Result.success(createdGroup)

            val result = api.createGroup("newgroup")

            assertTrue(result.isSuccess)
            assertEquals("newgroup", result.getOrNull()?.name)
        }

        @Test
        @DisplayName("creates group with custom settings")
        fun createsGroupWithCustomSettings() {
            val settings = GroupSettings(saveHealth = false, saveExperience = false)
            val createdGroup = InventoryGroup(
                name = "customgroup",
                worlds = setOf("world1", "world2"),
                patterns = listOf("pattern_.*"),
                priority = 10,
                parent = "default",
                settings = settings,
                isDefault = false
            )
            every { groupService.createGroup(
                name = "customgroup",
                settings = settings,
                worlds = setOf("world1", "world2"),
                patterns = listOf("pattern_.*"),
                priority = 10,
                parent = "default"
            ) } returns Result.success(createdGroup)

            val result = api.createGroup(
                name = "customgroup",
                settings = settings,
                worlds = setOf("world1", "world2"),
                patterns = listOf("pattern_.*"),
                priority = 10,
                parent = "default"
            )

            assertTrue(result.isSuccess)
            assertEquals("customgroup", result.getOrNull()?.name)
            assertEquals(10, result.getOrNull()?.priority)
        }

        @Test
        @DisplayName("returns failure for duplicate group name")
        fun returnsFailureForDuplicateGroupName() {
            every { groupService.createGroup(
                name = "existing",
                settings = any(),
                worlds = any(),
                patterns = any(),
                priority = any(),
                parent = any()
            ) } returns Result.failure(IllegalArgumentException("Group already exists"))

            val result = api.createGroup("existing")

            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("Delete Group")
    inner class DeleteGroup {

        @Test
        @DisplayName("deletes existing group")
        fun deletesExistingGroup() {
            every { groupService.deleteGroup("survival") } returns Result.success(Unit)

            val result = api.deleteGroup("survival")

            assertTrue(result.isSuccess)
            verify { groupService.deleteGroup("survival") }
        }

        @Test
        @DisplayName("returns failure when deleting default group")
        fun returnsFailureWhenDeletingDefaultGroup() {
            every { groupService.deleteGroup("default") } returns Result.failure(
                IllegalArgumentException("Cannot delete default group")
            )

            val result = api.deleteGroup("default")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("returns failure for non-existent group")
        fun returnsFailureForNonExistentGroup() {
            every { groupService.deleteGroup("nonexistent") } returns Result.failure(
                IllegalArgumentException("Group not found")
            )

            val result = api.deleteGroup("nonexistent")

            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("Modify Group")
    inner class ModifyGroup {

        @Test
        @DisplayName("modifies group with modifier")
        fun modifiesGroupWithModifier() {
            val modifiedGroup = InventoryGroup(
                name = "survival",
                worlds = setOf("new_world"),
                patterns = emptyList(),
                priority = 5,
                parent = null,
                settings = GroupSettings(),
                isDefault = false
            )
            every { groupService.modifyGroup("survival", any()) } returns Result.success(modifiedGroup)

            val result = api.modifyGroup("survival") {
                addWorld("new_world")
                setPriority(5)
            }

            assertTrue(result.isSuccess)
            assertEquals("survival", result.getOrNull()?.name)
            verify { groupService.modifyGroup("survival", any()) }
        }

        @Test
        @DisplayName("returns failure for non-existent group")
        fun returnsFailureForNonExistentGroup() {
            every { groupService.modifyGroup("nonexistent", any()) } returns Result.failure(
                IllegalArgumentException("Group not found")
            )

            val result = api.modifyGroup("nonexistent") {
                setPriority(10)
            }

            assertTrue(result.isFailure)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // World Operations
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Assign World to Group")
    inner class AssignWorldToGroup {

        @Test
        @DisplayName("assigns world to group")
        fun assignsWorldToGroup() {
            every { groupService.assignWorldToGroup("new_world", "survival") } returns Result.success(Unit)

            val result = api.assignWorldToGroup("new_world", "survival")

            assertTrue(result.isSuccess)
            verify { groupService.assignWorldToGroup("new_world", "survival") }
        }

        @Test
        @DisplayName("returns failure for non-existent group")
        fun returnsFailureForNonExistentGroup() {
            every { groupService.assignWorldToGroup("world", "nonexistent") } returns Result.failure(
                IllegalArgumentException("Group not found")
            )

            val result = api.assignWorldToGroup("world", "nonexistent")

            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("Unassign World")
    inner class UnassignWorld {

        @Test
        @DisplayName("unassigns world from group")
        fun unassignsWorldFromGroup() {
            every { groupService.unassignWorld("world") } returns Result.success(Unit)

            val result = api.unassignWorld("world")

            assertTrue(result.isSuccess)
            verify { groupService.unassignWorld("world") }
        }

        @Test
        @DisplayName("returns failure for unassigned world")
        fun returnsFailureForUnassignedWorld() {
            every { groupService.unassignWorld("unassigned") } returns Result.failure(
                IllegalArgumentException("World not assigned")
            )

            val result = api.unassignWorld("unassigned")

            assertTrue(result.isFailure)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Pattern Operations
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Add Pattern")
    inner class AddPattern {

        @Test
        @DisplayName("adds pattern to group")
        fun addsPatternToGroup() {
            every { groupService.addPattern("survival", "survival_.*") } returns Result.success(Unit)

            val result = api.addPattern("survival", "survival_.*")

            assertTrue(result.isSuccess)
            verify { groupService.addPattern("survival", "survival_.*") }
        }

        @Test
        @DisplayName("returns failure for invalid pattern")
        fun returnsFailureForInvalidPattern() {
            every { groupService.addPattern("survival", "[invalid") } returns Result.failure(
                IllegalArgumentException("Invalid regex pattern")
            )

            val result = api.addPattern("survival", "[invalid")

            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("Remove Pattern")
    inner class RemovePattern {

        @Test
        @DisplayName("removes pattern from group")
        fun removesPatternFromGroup() {
            every { groupService.removePattern("survival", "survival_.*") } returns Result.success(Unit)

            val result = api.removePattern("survival", "survival_.*")

            assertTrue(result.isSuccess)
            verify { groupService.removePattern("survival", "survival_.*") }
        }

        @Test
        @DisplayName("returns failure for non-existent pattern")
        fun returnsFailureForNonExistentPattern() {
            every { groupService.removePattern("survival", "nonexistent") } returns Result.failure(
                IllegalArgumentException("Pattern not found")
            )

            val result = api.removePattern("survival", "nonexistent")

            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("Get Patterns")
    inner class GetPatterns {

        @Test
        @DisplayName("returns patterns for group")
        fun returnsPatternsForGroup() {
            every { groupService.getPatterns("survival") } returns listOf("survival_.*", "sv_.*")

            val result = api.getPatterns("survival")

            assertEquals(2, result.size)
            assertTrue(result.contains("survival_.*"))
            assertTrue(result.contains("sv_.*"))
        }

        @Test
        @DisplayName("returns empty list for group without patterns")
        fun returnsEmptyListForGroupWithoutPatterns() {
            every { groupService.getPatterns("default") } returns emptyList()

            val result = api.getPatterns("default")

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("Test Pattern")
    inner class TestPattern {

        @Test
        @DisplayName("returns true when world matches pattern")
        fun returnsTrueWhenWorldMatchesPattern() {
            val group = createTestGroup("survival", patterns = listOf("survival_.*"))
            every { groupService.getGroup("survival") } returns group

            val result = api.testPattern("survival_world", "survival")

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when world does not match pattern")
        fun returnsFalseWhenWorldDoesNotMatchPattern() {
            val group = createTestGroup("survival", patterns = listOf("survival_.*"))
            every { groupService.getGroup("survival") } returns group

            val result = api.testPattern("creative_world", "survival")

            assertFalse(result)
        }

        @Test
        @DisplayName("returns false for non-existent group")
        fun returnsFalseForNonExistentGroup() {
            every { groupService.getGroup("nonexistent") } returns null

            val result = api.testPattern("any_world", "nonexistent")

            assertFalse(result)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Storage Operations
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Storage Type")
    inner class GetStorageType {

        @Test
        @DisplayName("returns current storage type YAML")
        fun returnsCurrentStorageTypeYaml() {
            val mainConfig = mockk<MainConfig>()
            val storageConfig = mockk<StorageConfig>()
            every { configManager.mainConfig } returns mainConfig
            every { mainConfig.storage } returns storageConfig
            every { storageConfig.type } returns StorageType.YAML

            val result = api.getStorageType()

            assertEquals(StorageType.YAML, result)
        }

        @Test
        @DisplayName("returns current storage type SQLITE")
        fun returnsCurrentStorageTypeSqlite() {
            val mainConfig = mockk<MainConfig>()
            val storageConfig = mockk<StorageConfig>()
            every { configManager.mainConfig } returns mainConfig
            every { mainConfig.storage } returns storageConfig
            every { storageConfig.type } returns StorageType.SQLITE

            val result = api.getStorageType()

            assertEquals(StorageType.SQLITE, result)
        }

        @Test
        @DisplayName("returns current storage type MYSQL")
        fun returnsCurrentStorageTypeMysql() {
            val mainConfig = mockk<MainConfig>()
            val storageConfig = mockk<StorageConfig>()
            every { configManager.mainConfig } returns mainConfig
            every { mainConfig.storage } returns storageConfig
            every { storageConfig.type } returns StorageType.MYSQL

            val result = api.getStorageType()

            assertEquals(StorageType.MYSQL, result)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Cache Operations
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Get Cache Statistics")
    inner class GetCacheStatistics {

        @Test
        @DisplayName("returns cache statistics")
        fun returnsCacheStatistics() {
            val stats = CacheStatistics(
                size = 100,
                maxSize = 1000,
                hitCount = 500,
                missCount = 50,
                loadCount = 550,
                evictionCount = 10
            )
            every { storageService.getCacheStats() } returns stats

            val result = api.getCacheStats()

            assertEquals(100, result.size)
            assertEquals(1000, result.maxSize)
            assertEquals(500, result.hitCount)
            assertEquals(50, result.missCount)
            assertEquals(550, result.loadCount)
            assertEquals(10, result.evictionCount)
        }

        @Test
        @DisplayName("calculates correct hit rate")
        fun calculatesCorrectHitRate() {
            val stats = CacheStatistics(
                size = 50,
                maxSize = 100,
                hitCount = 80,
                missCount = 20,
                loadCount = 100,
                evictionCount = 5
            )
            every { storageService.getCacheStats() } returns stats

            val result = api.getCacheStats()

            assertEquals(0.8, result.hitRate, 0.001)
        }
    }

    @Nested
    @DisplayName("Invalidate Cache")
    inner class InvalidateCache {

        @Test
        @DisplayName("invalidates all cache entries for player")
        fun invalidatesAllCacheEntriesForPlayer() {
            every { storageService.invalidateCache(testUuid) } just Runs

            api.invalidateCache(testUuid)

            verify { storageService.invalidateCache(testUuid) }
        }

        @Test
        @DisplayName("invalidates specific cache entry")
        fun invalidatesSpecificCacheEntry() {
            every { storageService.invalidateCache(testUuid, "survival", GameMode.SURVIVAL) } just Runs

            api.invalidateCache(testUuid, "survival", GameMode.SURVIVAL)

            verify { storageService.invalidateCache(testUuid, "survival", GameMode.SURVIVAL) }
        }

        @Test
        @DisplayName("invalidates cache entry without gamemode")
        fun invalidatesCacheEntryWithoutGamemode() {
            every { storageService.invalidateCache(testUuid, "survival", null) } just Runs

            api.invalidateCache(testUuid, "survival")

            verify { storageService.invalidateCache(testUuid, "survival", null) }
        }
    }

    @Nested
    @DisplayName("Clear Cache")
    inner class ClearCache {

        @Test
        @DisplayName("clears entire cache")
        fun clearsEntireCache() {
            every { storageService.clearCache() } returns 0

            api.clearCache()

            verify { storageService.clearCache() }
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Event Subscriptions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Subscribe to InventorySwitchEvent")
    inner class SubscribeToInventorySwitchEvent {

        @Test
        @DisplayName("returns subscription object")
        fun returnsSubscriptionObject() {
            val subscription = api.onInventorySwitch { }

            Assertions.assertNotNull(subscription)
            Assertions.assertNotNull(subscription.id)
            assertTrue(subscription.isActive)
        }

        @Test
        @DisplayName("handler receives correct context")
        fun handlerReceivesCorrectContext() {
            var receivedContext: InventorySwitchContext? = null
            api.onInventorySwitch { context -> receivedContext = context }

            val player = mockk<Player>()
            val fromWorld = mockk<World>()
            val toWorld = mockk<World>()
            val fromGroup = InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
            val toGroup = InventoryGroup("creative", emptySet(), emptyList(), 0, null, GroupSettings(), false)
            val snapshot = createTestSnapshot()

            val context = InventorySwitchContext(player, fromGroup, toGroup, fromWorld, toWorld, snapshot)
            api.dispatchSwitch(context)

            Assertions.assertNotNull(receivedContext)
            assertEquals(fromGroup, receivedContext?.fromGroup)
            assertEquals(toGroup, receivedContext?.toGroup)
        }
    }

    @Nested
    @DisplayName("Subscribe to InventorySaveEvent")
    inner class SubscribeToInventorySaveEvent {

        @Test
        @DisplayName("returns subscription object")
        fun returnsSubscriptionObject() {
            val subscription = api.onInventorySave { }

            Assertions.assertNotNull(subscription)
            assertTrue(subscription.isActive)
        }

        @Test
        @DisplayName("handler receives correct context")
        fun handlerReceivesCorrectContext() {
            var receivedContext: InventorySaveContext? = null
            api.onInventorySave { context -> receivedContext = context }

            val player = mockk<Player>()
            val group = InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
            val snapshot = createTestSnapshot()

            val context = InventorySaveContext(player, group, snapshot, async = true)
            api.dispatchSave(context)

            Assertions.assertNotNull(receivedContext)
            assertEquals(group, receivedContext?.group)
            assertTrue(receivedContext?.async == true)
        }
    }

    @Nested
    @DisplayName("Subscribe to InventoryLoadEvent")
    inner class SubscribeToInventoryLoadEvent {

        @Test
        @DisplayName("returns subscription object")
        fun returnsSubscriptionObject() {
            val subscription = api.onInventoryLoad { }

            Assertions.assertNotNull(subscription)
            assertTrue(subscription.isActive)
        }

        @Test
        @DisplayName("handler receives correct context")
        fun handlerReceivesCorrectContext() {
            var receivedContext: InventoryLoadContext? = null
            api.onInventoryLoad { context -> receivedContext = context }

            val player = mockk<Player>()
            val group = InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
            val snapshot = createTestSnapshot()

            val context = InventoryLoadContext(player, group, snapshot, InventoryLoadEvent.LoadReason.API)
            api.dispatchLoad(context)

            Assertions.assertNotNull(receivedContext)
            assertEquals(group, receivedContext?.group)
            assertEquals(InventoryLoadEvent.LoadReason.API, receivedContext?.reason)
        }
    }

    @Nested
    @DisplayName("Subscribe to GroupChangeEvent")
    inner class SubscribeToGroupChangeEvent {

        @Test
        @DisplayName("returns subscription object")
        fun returnsSubscriptionObject() {
            val subscription = api.onGroupChange { }

            Assertions.assertNotNull(subscription)
            assertTrue(subscription.isActive)
        }

        @Test
        @DisplayName("handler receives correct context")
        fun handlerReceivesCorrectContext() {
            var receivedContext: GroupChangeContext? = null
            api.onGroupChange { context -> receivedContext = context }

            val player = mockk<Player>()
            val oldGroup = InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
            val newGroup = InventoryGroup("creative", emptySet(), emptyList(), 0, null, GroupSettings(), false)

            val context = GroupChangeContext(player, oldGroup, newGroup)
            api.dispatchGroupChange(context)

            Assertions.assertNotNull(receivedContext)
            assertEquals(oldGroup, receivedContext?.oldGroup)
            assertEquals(newGroup, receivedContext?.newGroup)
        }

        @Test
        @DisplayName("handler receives null for old group when joining")
        fun handlerReceivesNullForOldGroupWhenJoining() {
            var receivedContext: GroupChangeContext? = null
            api.onGroupChange { context -> receivedContext = context }

            val player = mockk<Player>()
            val newGroup = InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)

            val context = GroupChangeContext(player, null, newGroup)
            api.dispatchGroupChange(context)

            Assertions.assertNotNull(receivedContext)
            Assertions.assertNull(receivedContext?.oldGroup)
            assertEquals(newGroup, receivedContext?.newGroup)
        }
    }

    @Nested
    @DisplayName("Unsubscribe from Events")
    inner class UnsubscribeFromEvents {

        @Test
        @DisplayName("unsubscribe stops handler from being called")
        fun unsubscribeStopsHandlerFromBeingCalled() {
            var callCount = 0
            val subscription = api.onInventorySwitch { callCount++ }

            val context = createSwitchContext()
            api.dispatchSwitch(context)
            assertEquals(1, callCount)

            subscription.unsubscribe()
            api.dispatchSwitch(context)
            assertEquals(1, callCount) // Still 1, handler not called
        }

        @Test
        @DisplayName("subscription isActive becomes false after unsubscribe")
        fun subscriptionIsActiveBecomesFalseAfterUnsubscribe() {
            val subscription = api.onInventorySave { }

            assertTrue(subscription.isActive)
            subscription.unsubscribe()
            assertFalse(subscription.isActive)
        }

        @Test
        @DisplayName("multiple unsubscribe calls are safe")
        fun multipleUnsubscribeCallsAreSafe() {
            val subscription = api.onInventoryLoad { }

            subscription.unsubscribe()
            subscription.unsubscribe() // Should not throw

            assertFalse(subscription.isActive)
        }
    }

    @Nested
    @DisplayName("Handler Error Handling")
    inner class HandlerErrorHandling {

        @Test
        @DisplayName("handler errors do not break other handlers")
        fun handlerErrorsDoNotBreakOtherHandlers() {
            var handler1Called = false
            var handler2Called = false
            var handler3Called = false

            api.onInventorySwitch { handler1Called = true }
            api.onInventorySwitch { throw RuntimeException("Test error") }
            api.onInventorySwitch { handler3Called = true }

            // Also test that handler2 was set up
            api.onInventorySwitch { handler2Called = true }

            val context = createSwitchContext()
            api.dispatchSwitch(context)

            assertTrue(handler1Called)
            assertTrue(handler2Called)
            assertTrue(handler3Called)
        }

        @Test
        @DisplayName("handler errors are logged")
        fun handlerErrorsAreLogged() {
            api.onInventorySwitch { throw RuntimeException("Test error message") }

            val context = createSwitchContext()
            api.dispatchSwitch(context)

            verify { logger.warning(match<String> { it.contains("Error in switch handler") }) }
        }

        @Test
        @DisplayName("save handler errors do not break other handlers")
        fun saveHandlerErrorsDoNotBreakOtherHandlers() {
            var handler1Called = false
            var handler2Called = false

            api.onInventorySave { handler1Called = true }
            api.onInventorySave { throw RuntimeException("Save error") }
            api.onInventorySave { handler2Called = true }

            val context = createSaveContext()
            api.dispatchSave(context)

            assertTrue(handler1Called)
            assertTrue(handler2Called)
        }

        @Test
        @DisplayName("load handler errors do not break other handlers")
        fun loadHandlerErrorsDoNotBreakOtherHandlers() {
            var handler1Called = false
            var handler2Called = false

            api.onInventoryLoad { handler1Called = true }
            api.onInventoryLoad { throw RuntimeException("Load error") }
            api.onInventoryLoad { handler2Called = true }

            val context = createLoadContext()
            api.dispatchLoad(context)

            assertTrue(handler1Called)
            assertTrue(handler2Called)
        }

        @Test
        @DisplayName("group change handler errors do not break other handlers")
        fun groupChangeHandlerErrorsDoNotBreakOtherHandlers() {
            var handler1Called = false
            var handler2Called = false

            api.onGroupChange { handler1Called = true }
            api.onGroupChange { throw RuntimeException("Group change error") }
            api.onGroupChange { handler2Called = true }

            val context = createGroupChangeContext()
            api.dispatchGroupChange(context)

            assertTrue(handler1Called)
            assertTrue(handler2Called)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // XInventoriesProvider
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("XInventoriesProvider")
    inner class XInventoriesProviderTest {

        @BeforeEach
        fun setUp() {
            // Ensure clean state
            XInventoriesProvider.unregister()
        }

        @AfterEach
        fun tearDown() {
            XInventoriesProvider.unregister()
        }

        @Test
        @DisplayName("isAvailable returns false when not registered")
        fun isAvailableReturnsFalseWhenNotRegistered() {
            assertFalse(XInventoriesProvider.isAvailable())
        }

        @Test
        @DisplayName("isAvailable returns true when registered")
        fun isAvailableReturnsTrueWhenRegistered() {
            XInventoriesProvider.register(api)

            assertTrue(XInventoriesProvider.isAvailable())
        }

        @Test
        @DisplayName("get returns API when registered")
        fun getReturnsApiWhenRegistered() {
            XInventoriesProvider.register(api)

            val result = XInventoriesProvider.get()

            Assertions.assertNotNull(result)
            assertSame(api, result)
        }

        @Test
        @DisplayName("get throws when not registered")
        fun getThrowsWhenNotRegistered() {
            val exception = assertThrows<IllegalStateException> {
                XInventoriesProvider.get()
            }

            assertTrue(exception.message?.contains("not available") == true)
        }

        @Test
        @DisplayName("getOrNull returns null when not registered")
        fun getOrNullReturnsNullWhenNotRegistered() {
            val result = XInventoriesProvider.getOrNull()

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("getOrNull returns API when registered")
        fun getOrNullReturnsApiWhenRegistered() {
            XInventoriesProvider.register(api)

            val result = XInventoriesProvider.getOrNull()

            Assertions.assertNotNull(result)
            assertSame(api, result)
        }

        @Test
        @DisplayName("register throws when already registered")
        fun registerThrowsWhenAlreadyRegistered() {
            XInventoriesProvider.register(api)

            val exception = assertThrows<IllegalStateException> {
                XInventoriesProvider.register(mockk())
            }

            assertTrue(exception.message?.contains("already registered") == true)
        }

        @Test
        @DisplayName("unregister clears API reference")
        fun unregisterClearsApiReference() {
            XInventoriesProvider.register(api)
            assertTrue(XInventoriesProvider.isAvailable())

            XInventoriesProvider.unregister()

            assertFalse(XInventoriesProvider.isAvailable())
        }

        @Test
        @DisplayName("can re-register after unregister")
        fun canReRegisterAfterUnregister() {
            XInventoriesProvider.register(api)
            XInventoriesProvider.unregister()

            val newApi = mockk<XInventoriesAPI>()
            XInventoriesProvider.register(newApi)

            assertSame(newApi, XInventoriesProvider.get())
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Subscription Interface
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Subscription Interface")
    inner class SubscriptionInterfaceTest {

        @Test
        @DisplayName("subscription has unique ID")
        fun subscriptionHasUniqueId() {
            val sub1 = api.onInventorySwitch { }
            val sub2 = api.onInventorySwitch { }

            assertNotEquals(sub1.id, sub2.id)
        }

        @Test
        @DisplayName("subscription ID is valid UUID")
        fun subscriptionIdIsValidUuid() {
            val subscription = api.onInventorySave { }

            assertDoesNotThrow { UUID.fromString(subscription.id) }
        }

        @Test
        @DisplayName("new subscription is active")
        fun newSubscriptionIsActive() {
            val subscription = api.onInventoryLoad { }

            assertTrue(subscription.isActive)
        }

        @Test
        @DisplayName("unsubscribed subscription is not active")
        fun unsubscribedSubscriptionIsNotActive() {
            val subscription = api.onGroupChange { }
            subscription.unsubscribe()

            assertFalse(subscription.isActive)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helper Methods
    // ═══════════════════════════════════════════════════════════════════

    private fun createTestSnapshot(
        uuid: UUID = testUuid,
        playerName: String = testPlayerName,
        group: String = "survival",
        gameMode: GameMode = GameMode.SURVIVAL
    ): PlayerInventorySnapshot {
        return PlayerInventorySnapshot(
            uuid = uuid,
            playerName = playerName,
            group = group,
            gameMode = gameMode,
            contents = InventoryContents.empty(),
            health = 20.0,
            maxHealth = 20.0,
            foodLevel = 20,
            saturation = 5.0f,
            exhaustion = 0.0f,
            experience = 0.0f,
            level = 0,
            totalExperience = 0,
            potionEffects = emptyList(),
            timestamp = Instant.now()
        )
    }

    private fun createTestGroup(
        name: String,
        worlds: Set<String> = emptySet(),
        patterns: List<String> = emptyList(),
        priority: Int = 0,
        parent: String? = null,
        settings: GroupSettings = GroupSettings(),
        isDefault: Boolean = false
    ): Group {
        return mockk<Group> {
            every { this@mockk.name } returns name
            every { this@mockk.worlds } returns worlds
            every { this@mockk.patternStrings } returns patterns
            every { this@mockk.priority } returns priority
            every { this@mockk.parent } returns parent
            every { this@mockk.settings } returns settings
            every { this@mockk.isDefault } returns isDefault
            every { matchesPattern(any()) } answers {
                val worldName = firstArg<String>()
                patterns.any { pattern ->
                    try {
                        Regex(pattern).matches(worldName)
                    } catch (e: Exception) {
                        false
                    }
                }
            }
            every { toApiModel() } returns InventoryGroup(
                name = name,
                worlds = worlds,
                patterns = patterns,
                priority = priority,
                parent = parent,
                settings = settings,
                isDefault = isDefault
            )
        }
    }

    private fun createSwitchContext(): InventorySwitchContext {
        val player = mockk<Player>()
        val fromWorld = mockk<World>()
        val toWorld = mockk<World>()
        val fromGroup = InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
        val toGroup = InventoryGroup("creative", emptySet(), emptyList(), 0, null, GroupSettings(), false)
        val snapshot = createTestSnapshot()
        return InventorySwitchContext(player, fromGroup, toGroup, fromWorld, toWorld, snapshot)
    }

    private fun createSaveContext(): InventorySaveContext {
        val player = mockk<Player>()
        val group = InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
        val snapshot = createTestSnapshot()
        return InventorySaveContext(player, group, snapshot, async = false)
    }

    private fun createLoadContext(): InventoryLoadContext {
        val player = mockk<Player>()
        val group = InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
        val snapshot = createTestSnapshot()
        return InventoryLoadContext(player, group, snapshot, InventoryLoadEvent.LoadReason.API)
    }

    private fun createGroupChangeContext(): GroupChangeContext {
        val player = mockk<Player>()
        val oldGroup = InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
        val newGroup = InventoryGroup("creative", emptySet(), emptyList(), 0, null, GroupSettings(), false)
        return GroupChangeContext(player, oldGroup, newGroup)
    }
}

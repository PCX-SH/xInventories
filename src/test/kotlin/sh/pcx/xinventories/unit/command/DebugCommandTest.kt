package sh.pcx.xinventories.unit.command

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.PluginDescriptionFile
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.CacheStatistics
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryContents
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.cache.PlayerDataCache
import sh.pcx.xinventories.internal.command.subcommand.DebugCommand
import sh.pcx.xinventories.internal.config.CacheConfig
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.config.MysqlConfig
import sh.pcx.xinventories.internal.config.PoolConfig
import sh.pcx.xinventories.internal.config.SqliteConfig
import sh.pcx.xinventories.internal.config.StorageConfig
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.InventoryService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.service.StorageService
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for DebugCommand.
 *
 * Tests cover:
 * - Subcommand properties (name, permission, usage, description)
 * - execute() with "info" action - showing general debug info
 * - execute() with "player" action - showing player-specific debug info
 * - execute() with "storage" action - showing storage debug info
 * - Default to "info" when no action specified
 * - tabComplete() - tab completions for actions and player names
 */
@DisplayName("DebugCommand Unit Tests")
class DebugCommandTest {

    private lateinit var plugin: XInventories
    private lateinit var serviceManager: ServiceManager
    private lateinit var configManager: ConfigManager
    private lateinit var mainConfig: MainConfig
    private lateinit var storageService: StorageService
    private lateinit var groupService: GroupService
    private lateinit var inventoryService: InventoryService
    private lateinit var cache: PlayerDataCache
    private lateinit var descriptionFile: PluginDescriptionFile
    private lateinit var debugCommand: DebugCommand

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("DebugCommandTest"), false)
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
        serviceManager = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        mainConfig = mockk(relaxed = true)
        storageService = mockk(relaxed = true)
        groupService = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)
        cache = mockk(relaxed = true)
        descriptionFile = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { plugin.configManager } returns configManager
        every { plugin.description } returns descriptionFile
        every { configManager.mainConfig } returns mainConfig
        every { serviceManager.storageService } returns storageService
        every { serviceManager.groupService } returns groupService
        every { serviceManager.inventoryService } returns inventoryService
        every { storageService.cache } returns cache

        // Mock Bukkit static methods
        mockkStatic(Bukkit::class)

        debugCommand = DebugCommand()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Bukkit::class)
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockPlayer(
        uuid: UUID = UUID.randomUUID(),
        name: String = "TestPlayer",
        worldName: String = "world",
        gameMode: GameMode = GameMode.SURVIVAL
    ): Player {
        val world = mockk<World>(relaxed = true)
        every { world.name } returns worldName

        return mockk<Player>(relaxed = true).apply {
            every { uniqueId } returns uuid
            every { this@apply.name } returns name
            every { this@apply.world } returns world
            every { this@apply.gameMode } returns gameMode
        }
    }

    private fun createMockCommandSender(name: String = "CONSOLE"): CommandSender {
        return mockk<CommandSender>(relaxed = true).apply {
            every { this@apply.name } returns name
        }
    }

    private fun createMockGroup(
        name: String = "survival",
        separateGameModeInventories: Boolean = true
    ): Group {
        return mockk<Group>(relaxed = true).apply {
            every { this@apply.name } returns name
            every { settings } returns GroupSettings(separateGameModeInventories = separateGameModeInventories)
        }
    }

    private fun createMockSnapshot(
        uuid: UUID = UUID.randomUUID(),
        playerName: String = "TestPlayer",
        group: String = "survival",
        gameMode: GameMode = GameMode.SURVIVAL,
        itemCount: Int = 10
    ): PlayerInventorySnapshot {
        val contents = mockk<InventoryContents>(relaxed = true)
        every { contents.totalItems() } returns itemCount

        return PlayerInventorySnapshot(
            uuid = uuid,
            playerName = playerName,
            group = group,
            gameMode = gameMode,
            contents = contents,
            health = 20.0,
            maxHealth = 20.0,
            foodLevel = 20,
            saturation = 5.0f,
            exhaustion = 0.0f,
            experience = 0.5f,
            level = 10,
            totalExperience = 1000,
            potionEffects = emptyList(),
            timestamp = Instant.now()
        )
    }

    private fun createCacheStats(
        size: Int = 50,
        maxSize: Int = 1000,
        hitCount: Long = 100,
        missCount: Long = 20
    ): CacheStatistics {
        return CacheStatistics(
            size = size,
            maxSize = maxSize,
            hitCount = hitCount,
            missCount = missCount,
            loadCount = 50,
            evictionCount = 5
        )
    }

    // =========================================================================
    // Property Tests
    // =========================================================================

    @Nested
    @DisplayName("Subcommand Properties")
    inner class PropertyTests {

        @Test
        @DisplayName("Should have correct name")
        fun hasCorrectName() {
            assertEquals("debug", debugCommand.name)
        }

        @Test
        @DisplayName("Should have correct permission")
        fun hasCorrectPermission() {
            assertEquals("xinventories.command.debug", debugCommand.permission)
        }

        @Test
        @DisplayName("Should have correct usage")
        fun hasCorrectUsage() {
            assertEquals("/xinv debug [info|player|storage]", debugCommand.usage)
        }

        @Test
        @DisplayName("Should have correct description")
        fun hasCorrectDescription() {
            assertEquals("Debug information", debugCommand.description)
        }

        @Test
        @DisplayName("Should have empty aliases by default")
        fun hasEmptyAliases() {
            assertTrue(debugCommand.aliases.isEmpty())
        }

        @Test
        @DisplayName("Should not be player-only command")
        fun notPlayerOnlyCommand() {
            assertFalse(debugCommand.playerOnly)
        }
    }

    // =========================================================================
    // Execute - Info Action Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Info Action")
    inner class ExecuteInfoActionTests {

        @Test
        @DisplayName("Should show info when 'info' action specified")
        fun showInfoWhenInfoActionSpecified() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup("survival"), createMockGroup("creative"))
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 100
            coEvery { storageService.getStorageSize() } returns 1024L
            coEvery { storageService.isHealthy() } returns true

            val result = debugCommand.execute(plugin, sender, arrayOf("info"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Debug Info") }) }
            verify { sender.sendMessage(match<String> { it.contains("Version") }) }
            verify { sender.sendMessage(match<String> { it.contains("Storage Type") }) }
        }

        @Test
        @DisplayName("Should default to info when no action specified")
        fun defaultToInfoWhenNoAction() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            val result = debugCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Debug Info") }) }
        }

        @Test
        @DisplayName("Should default to info when unknown action specified")
        fun defaultToInfoWhenUnknownAction() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            val result = debugCommand.execute(plugin, sender, arrayOf("unknown"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Debug Info") }) }
        }

        @Test
        @DisplayName("Should show version information")
        fun showVersionInformation() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "2.5.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            debugCommand.execute(plugin, sender, arrayOf("info"))

            verify { sender.sendMessage(match<String> { it.contains("2.5.0") }) }
        }

        @Test
        @DisplayName("Should show storage type")
        fun showStorageType() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.SQLITE)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            debugCommand.execute(plugin, sender, arrayOf("info"))

            verify { sender.sendMessage(match<String> { it.contains("SQLITE") }) }
        }

        @Test
        @DisplayName("Should show cache enabled status")
        fun showCacheEnabledStatus() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = false)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            debugCommand.execute(plugin, sender, arrayOf("info"))

            verify { sender.sendMessage(match<String> { it.contains("Cache Enabled") && it.contains("false") }) }
        }

        @Test
        @DisplayName("Should show group count")
        fun showGroupCount() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(
                createMockGroup("survival"),
                createMockGroup("creative"),
                createMockGroup("minigames")
            )
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            debugCommand.execute(plugin, sender, arrayOf("info"))

            verify { sender.sendMessage(match<String> { it.contains("Groups") && it.contains("3") }) }
        }

        @Test
        @DisplayName("Should show default group name")
        fun showDefaultGroupName() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default_group")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            debugCommand.execute(plugin, sender, arrayOf("info"))

            verify { sender.sendMessage(match<String> { it.contains("Default Group") && it.contains("default_group") }) }
        }

        @Test
        @DisplayName("Should show online players count")
        fun showOnlinePlayersCount() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")
            val players = listOf(
                createMockPlayer(name = "Player1"),
                createMockPlayer(name = "Player2"),
                createMockPlayer(name = "Player3")
            )

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns players
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            debugCommand.execute(plugin, sender, arrayOf("info"))

            verify { sender.sendMessage(match<String> { it.contains("Online Players") && it.contains("3") }) }
        }

        @Test
        @DisplayName("Should show cache statistics")
        fun showCacheStatistics() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns CacheStatistics(
                size = 75,
                maxSize = 500,
                hitCount = 200,
                missCount = 50,
                loadCount = 100,
                evictionCount = 10
            )
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            debugCommand.execute(plugin, sender, arrayOf("info"))

            verify { sender.sendMessage(match<String> { it.contains("Cache Size") && it.contains("75") && it.contains("500") }) }
            verify { sender.sendMessage(match<String> { it.contains("Cache Hit Rate") }) }
        }

        @Test
        @DisplayName("Should show healthy storage status")
        fun showHealthyStorageStatus() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            debugCommand.execute(plugin, sender, arrayOf("info"))

            verify { sender.sendMessage(match<String> { it.contains("Storage Health") && it.contains("Healthy") }) }
        }

        @Test
        @DisplayName("Should show unhealthy storage status")
        fun showUnhealthyStorageStatus() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns false

            debugCommand.execute(plugin, sender, arrayOf("info"))

            verify { sender.sendMessage(match<String> { it.contains("Storage Health") && it.contains("Unhealthy") }) }
        }

        @Test
        @DisplayName("Should handle negative storage size")
        fun handleNegativeStorageSize() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns -1L
            coEvery { storageService.isHealthy() } returns true

            val result = debugCommand.execute(plugin, sender, arrayOf("info"))

            assertTrue(result)
            // Storage size message should not be sent when size is negative
            verify(exactly = 0) { sender.sendMessage(match<String> { it.contains("Storage Size") }) }
        }

        @Test
        @DisplayName("Should handle action case insensitively")
        fun handleActionCaseInsensitively() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            val result = debugCommand.execute(plugin, sender, arrayOf("INFO"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Debug Info") }) }
        }
    }

    // =========================================================================
    // Execute - Player Action Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Player Action")
    inner class ExecutePlayerActionTests {

        @Test
        @DisplayName("Should show player info with player name argument")
        fun showPlayerInfoWithPlayerName() = runTest {
            val sender = createMockCommandSender()
            val targetUuid = UUID.randomUUID()
            val targetPlayer = createMockPlayer(uuid = targetUuid, name = "TargetPlayer", worldName = "world")
            val group = createMockGroup("survival")
            val snapshot = createMockSnapshot(uuid = targetUuid, group = "survival", itemCount = 25)

            every { Bukkit.getPlayer("TargetPlayer") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "survival"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns snapshot
            every { inventoryService.hasBypass(targetPlayer) } returns false

            val result = debugCommand.execute(plugin, sender, arrayOf("player", "TargetPlayer"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Player Debug") && it.contains("TargetPlayer") }) }
            verify { sender.sendMessage(match<String> { it.contains("UUID") }) }
            verify { sender.sendMessage(match<String> { it.contains("World") }) }
            verify { sender.sendMessage(match<String> { it.contains("GameMode") }) }
        }

        @Test
        @DisplayName("Should show player info for sender when sender is Player and no name provided")
        fun showPlayerInfoForSenderWhenPlayer() = runTest {
            val playerUuid = UUID.randomUUID()
            val playerSender = createMockPlayer(uuid = playerUuid, name = "PlayerSender", worldName = "nether")
            val group = createMockGroup("survival")
            val snapshot = createMockSnapshot(uuid = playerUuid, group = "survival")

            every { groupService.getGroupForWorld(playerSender.world) } returns group
            every { inventoryService.getCurrentGroup(playerSender) } returns "survival"
            every { inventoryService.getActiveSnapshot(playerSender) } returns snapshot
            every { inventoryService.hasBypass(playerSender) } returns false

            val result = debugCommand.execute(plugin, playerSender, arrayOf("player"))

            assertTrue(result)
            verify { playerSender.sendMessage(match<String> { it.contains("Player Debug") && it.contains("PlayerSender") }) }
        }

        @Test
        @DisplayName("Should show error when console runs player action without name")
        fun showErrorWhenConsoleWithoutPlayerName() = runTest {
            val sender = createMockCommandSender()

            val result = debugCommand.execute(plugin, sender, arrayOf("player"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Specify a player name") }) }
        }

        @Test
        @DisplayName("Should show error when player not found")
        fun showErrorWhenPlayerNotFound() = runTest {
            val sender = createMockCommandSender()

            every { Bukkit.getPlayer("NonExistent") } returns null

            val result = debugCommand.execute(plugin, sender, arrayOf("player", "NonExistent"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Player not found") }) }
        }

        @Test
        @DisplayName("Should show current group from inventory service")
        fun showCurrentGroupFromInventoryService() = runTest {
            val sender = createMockCommandSender()
            val targetPlayer = createMockPlayer(name = "Target")
            val group = createMockGroup("creative")

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "creative"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns null
            every { inventoryService.hasBypass(targetPlayer) } returns false

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            verify { sender.sendMessage(match<String> { it.contains("Current Group") && it.contains("creative") }) }
        }

        @Test
        @DisplayName("Should fallback to world group when current group is null")
        fun fallbackToWorldGroupWhenCurrentGroupNull() = runTest {
            val sender = createMockCommandSender()
            val targetPlayer = createMockPlayer(name = "Target", worldName = "skyblock")
            val group = createMockGroup("skyblock_group")

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns null
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns null
            every { inventoryService.hasBypass(targetPlayer) } returns false

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            verify { sender.sendMessage(match<String> { it.contains("Current Group") && it.contains("skyblock_group") }) }
        }

        @Test
        @DisplayName("Should show bypass status as true")
        fun showBypassStatusTrue() = runTest {
            val sender = createMockCommandSender()
            val targetPlayer = createMockPlayer(name = "Target")
            val group = createMockGroup("survival")

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "survival"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns null
            every { inventoryService.hasBypass(targetPlayer) } returns true

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            verify { sender.sendMessage(match<String> { it.contains("Has Bypass") && it.contains("true") }) }
        }

        @Test
        @DisplayName("Should show bypass status as false")
        fun showBypassStatusFalse() = runTest {
            val sender = createMockCommandSender()
            val targetPlayer = createMockPlayer(name = "Target")
            val group = createMockGroup("survival")

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "survival"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns null
            every { inventoryService.hasBypass(targetPlayer) } returns false

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            verify { sender.sendMessage(match<String> { it.contains("Has Bypass") && it.contains("false") }) }
        }

        @Test
        @DisplayName("Should show snapshot details when active snapshot exists")
        fun showSnapshotDetailsWhenExists() = runTest {
            val sender = createMockCommandSender()
            val targetUuid = UUID.randomUUID()
            val targetPlayer = createMockPlayer(uuid = targetUuid, name = "Target")
            val group = createMockGroup("survival")
            val snapshot = createMockSnapshot(
                uuid = targetUuid,
                group = "survival",
                gameMode = GameMode.CREATIVE,
                itemCount = 42
            )

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "survival"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns snapshot
            every { inventoryService.hasBypass(targetPlayer) } returns false

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            verify { sender.sendMessage(match<String> { it.contains("Snapshot Group") && it.contains("survival") }) }
            verify { sender.sendMessage(match<String> { it.contains("Snapshot GameMode") && it.contains("CREATIVE") }) }
            verify { sender.sendMessage(match<String> { it.contains("Snapshot Items") && it.contains("42") }) }
            verify { sender.sendMessage(match<String> { it.contains("Snapshot Time") }) }
        }

        @Test
        @DisplayName("Should show none when no active snapshot")
        fun showNoneWhenNoActiveSnapshot() = runTest {
            val sender = createMockCommandSender()
            val targetPlayer = createMockPlayer(name = "Target")
            val group = createMockGroup("survival")

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "survival"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns null
            every { inventoryService.hasBypass(targetPlayer) } returns false

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            verify { sender.sendMessage(match<String> { it.contains("Active Snapshot") && it.contains("none") }) }
        }

        @Test
        @DisplayName("Should show world group info")
        fun showWorldGroupInfo() = runTest {
            val sender = createMockCommandSender()
            val targetPlayer = createMockPlayer(name = "Target", worldName = "world_nether")
            val group = createMockGroup("nether_group", separateGameModeInventories = true)

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "nether_group"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns null
            every { inventoryService.hasBypass(targetPlayer) } returns false

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            verify { sender.sendMessage(match<String> { it.contains("World Group") && it.contains("nether_group") }) }
            verify { sender.sendMessage(match<String> { it.contains("Separate GM Inventories") && it.contains("true") }) }
        }

        @Test
        @DisplayName("Should show player UUID")
        fun showPlayerUUID() = runTest {
            val sender = createMockCommandSender()
            val targetUuid = UUID.fromString("12345678-1234-1234-1234-123456789012")
            val targetPlayer = createMockPlayer(uuid = targetUuid, name = "Target")
            val group = createMockGroup("survival")

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "survival"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns null
            every { inventoryService.hasBypass(targetPlayer) } returns false

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            verify { sender.sendMessage(match<String> { it.contains("UUID") && it.contains("12345678-1234-1234-1234-123456789012") }) }
        }

        @Test
        @DisplayName("Should show player world name")
        fun showPlayerWorldName() = runTest {
            val sender = createMockCommandSender()
            // Create world mock with explicit name property
            val world = mockk<World>(relaxed = true)
            every { world.name } returns "test_world_123"

            val targetPlayer = mockk<Player>(relaxed = true)
            every { targetPlayer.uniqueId } returns UUID.randomUUID()
            every { targetPlayer.name } returns "Target"
            every { targetPlayer.world } returns world
            every { targetPlayer.gameMode } returns GameMode.SURVIVAL

            val group = createMockGroup("survival")
            val sentMessages = mutableListOf<String>()

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "survival"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns null
            every { inventoryService.hasBypass(targetPlayer) } returns false
            every { sender.sendMessage(capture(sentMessages)) } just Runs

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            // Verify that "World:" message contains the world name
            assertTrue(sentMessages.any { it.contains("World:") && it.contains("test_world_123") },
                "Expected message containing 'World:' and 'test_world_123', but got: $sentMessages")
        }

        @Test
        @DisplayName("Should show player gamemode")
        fun showPlayerGamemode() = runTest {
            val sender = createMockCommandSender()
            val targetPlayer = createMockPlayer(name = "Target", gameMode = GameMode.ADVENTURE)
            val group = createMockGroup("survival")

            every { Bukkit.getPlayer("Target") } returns targetPlayer
            every { groupService.getGroupForWorld(targetPlayer.world) } returns group
            every { inventoryService.getCurrentGroup(targetPlayer) } returns "survival"
            every { inventoryService.getActiveSnapshot(targetPlayer) } returns null
            every { inventoryService.hasBypass(targetPlayer) } returns false

            debugCommand.execute(plugin, sender, arrayOf("player", "Target"))

            verify { sender.sendMessage(match<String> { it.contains("GameMode") && it.contains("ADVENTURE") }) }
        }
    }

    // =========================================================================
    // Execute - Storage Action Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Storage Action")
    inner class ExecuteStorageActionTests {

        @Test
        @DisplayName("Should show storage info for YAML storage type")
        fun showStorageInfoForYaml() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 150
            coEvery { storageService.getStorageSize() } returns 51200L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID(), UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 5

            val result = debugCommand.execute(plugin, sender, arrayOf("storage"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Storage Debug Info") }) }
            verify { sender.sendMessage(match<String> { it.contains("Type") && it.contains("YAML") }) }
            verify { sender.sendMessage(match<String> { it.contains("plugins/xInventories/data/players/") }) }
        }

        @Test
        @DisplayName("Should show storage info for SQLite storage type")
        fun showStorageInfoForSqlite() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(
                type = StorageType.SQLITE,
                sqlite = SqliteConfig(file = "data/custom.db")
            )
            coEvery { storageService.getEntryCount() } returns 500
            coEvery { storageService.getStorageSize() } returns 102400L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 0

            val result = debugCommand.execute(plugin, sender, arrayOf("storage"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Type") && it.contains("SQLITE") }) }
            verify { sender.sendMessage(match<String> { it.contains("File") && it.contains("data/custom.db") }) }
        }

        @Test
        @DisplayName("Should show storage info for MySQL storage type")
        fun showStorageInfoForMysql() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(
                type = StorageType.MYSQL,
                mysql = MysqlConfig(
                    host = "db.example.com",
                    port = 3306,
                    database = "xinventories",
                    pool = PoolConfig(maximumPoolSize = 15)
                )
            )
            coEvery { storageService.getEntryCount() } returns 1000
            coEvery { storageService.getStorageSize() } returns 1048576L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 10

            val result = debugCommand.execute(plugin, sender, arrayOf("storage"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Type") && it.contains("MYSQL") }) }
            verify { sender.sendMessage(match<String> { it.contains("Host") && it.contains("db.example.com:3306") }) }
            verify { sender.sendMessage(match<String> { it.contains("Database") && it.contains("xinventories") }) }
            verify { sender.sendMessage(match<String> { it.contains("Pool Size") && it.contains("15") }) }
        }

        @Test
        @DisplayName("Should show total entry count")
        fun showTotalEntryCount() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 2500
            coEvery { storageService.getStorageSize() } returns 51200L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 0

            debugCommand.execute(plugin, sender, arrayOf("storage"))

            verify { sender.sendMessage(match<String> { it.contains("Total Entries") && it.contains("2500") }) }
        }

        @Test
        @DisplayName("Should show unique player count")
        fun showUniquePlayerCount() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 100
            coEvery { storageService.getStorageSize() } returns 51200L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
            )
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 0

            debugCommand.execute(plugin, sender, arrayOf("storage"))

            verify { sender.sendMessage(match<String> { it.contains("Unique Players") && it.contains("5") }) }
        }

        @Test
        @DisplayName("Should show storage size when positive")
        fun showStorageSizeWhenPositive() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 100
            coEvery { storageService.getStorageSize() } returns 1048576L // 1 MB
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 0

            debugCommand.execute(plugin, sender, arrayOf("storage"))

            verify { sender.sendMessage(match<String> { it.contains("Storage Size") }) }
        }

        @Test
        @DisplayName("Should not show storage size when negative")
        fun notShowStorageSizeWhenNegative() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 100
            coEvery { storageService.getStorageSize() } returns -1L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 0

            debugCommand.execute(plugin, sender, arrayOf("storage"))

            verify(exactly = 0) { sender.sendMessage(match<String> { it.contains("Storage Size") }) }
        }

        @Test
        @DisplayName("Should show healthy status")
        fun showHealthyStatus() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 100
            coEvery { storageService.getStorageSize() } returns 51200L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 0

            debugCommand.execute(plugin, sender, arrayOf("storage"))

            verify { sender.sendMessage(match<String> { it.contains("Health") && it.contains("Healthy") }) }
        }

        @Test
        @DisplayName("Should show unhealthy status")
        fun showUnhealthyStatus() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 100
            coEvery { storageService.getStorageSize() } returns 51200L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns false
            every { cache.getDirtyCount() } returns 0

            debugCommand.execute(plugin, sender, arrayOf("storage"))

            verify { sender.sendMessage(match<String> { it.contains("Health") && it.contains("Unhealthy") }) }
        }

        @Test
        @DisplayName("Should show cache dirty entries count")
        fun showCacheDirtyEntriesCount() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 100
            coEvery { storageService.getStorageSize() } returns 51200L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 25

            debugCommand.execute(plugin, sender, arrayOf("storage"))

            verify { sender.sendMessage(match<String> { it.contains("Cache Dirty Entries") && it.contains("25") }) }
        }

        @Test
        @DisplayName("Should handle storage action case insensitively")
        fun handleStorageActionCaseInsensitively() = runTest {
            val sender = createMockCommandSender()

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 100
            coEvery { storageService.getStorageSize() } returns 51200L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 0

            val result = debugCommand.execute(plugin, sender, arrayOf("STORAGE"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Storage Debug Info") }) }
        }
    }

    // =========================================================================
    // Tab Complete Tests
    // =========================================================================

    @Nested
    @DisplayName("tabComplete()")
    inner class TabCompleteTests {

        @Test
        @DisplayName("Should return all actions for empty first argument")
        fun returnAllActionsForEmptyFirstArg() {
            val sender = createMockCommandSender()

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf(""))

            assertEquals(3, completions.size)
            assertTrue(completions.contains("info"))
            assertTrue(completions.contains("player"))
            assertTrue(completions.contains("storage"))
        }

        @Test
        @DisplayName("Should filter actions by partial match")
        fun filterActionsByPartialMatch() {
            val sender = createMockCommandSender()

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("i"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("info"))
        }

        @Test
        @DisplayName("Should filter actions by 'p' prefix")
        fun filterActionsByPPrefix() {
            val sender = createMockCommandSender()

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("p"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("player"))
        }

        @Test
        @DisplayName("Should filter actions by 's' prefix")
        fun filterActionsBySPrefix() {
            val sender = createMockCommandSender()

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("s"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("storage"))
        }

        @Test
        @DisplayName("Should return empty list for non-matching prefix")
        fun returnEmptyListForNonMatchingPrefix() {
            val sender = createMockCommandSender()

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("x"))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return player names for 'player' action")
        fun returnPlayerNamesForPlayerAction() {
            val sender = createMockCommandSender()
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Bob"),
                createMockPlayer(name = "Charlie")
            )

            every { Bukkit.getOnlinePlayers() } returns players

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("player", ""))

            assertEquals(3, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Bob"))
            assertTrue(completions.contains("Charlie"))
        }

        @Test
        @DisplayName("Should filter player names by partial match")
        fun filterPlayerNamesByPartialMatch() {
            val sender = createMockCommandSender()
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Adam"),
                createMockPlayer(name = "Bob")
            )

            every { Bukkit.getOnlinePlayers() } returns players

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("player", "A"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Adam"))
            assertFalse(completions.contains("Bob"))
        }

        @Test
        @DisplayName("Should filter player names case insensitively")
        fun filterPlayerNamesCaseInsensitively() {
            val sender = createMockCommandSender()
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Adam"),
                createMockPlayer(name = "Bob")
            )

            every { Bukkit.getOnlinePlayers() } returns players

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("player", "a"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Adam"))
        }

        @Test
        @DisplayName("Should return empty list for non-player actions at second argument")
        fun returnEmptyListForNonPlayerActionsAtSecondArg() {
            val sender = createMockCommandSender()

            val completionsForInfo = debugCommand.tabComplete(plugin, sender, arrayOf("info", ""))
            val completionsForStorage = debugCommand.tabComplete(plugin, sender, arrayOf("storage", ""))

            assertTrue(completionsForInfo.isEmpty())
            assertTrue(completionsForStorage.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list for third argument and beyond")
        fun returnEmptyListForThirdArgumentAndBeyond() {
            val sender = createMockCommandSender()

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("player", "Alice", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should handle empty online players list")
        fun handleEmptyOnlinePlayersList() {
            val sender = createMockCommandSender()

            every { Bukkit.getOnlinePlayers() } returns emptyList()

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("player", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should handle action filter case insensitively")
        fun handleActionFilterCaseInsensitively() {
            val sender = createMockCommandSender()

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("I"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("info"))
        }
    }

    // =========================================================================
    // Permission Tests
    // =========================================================================

    @Nested
    @DisplayName("Permission Checks")
    inner class PermissionTests {

        @Test
        @DisplayName("Should return true when sender has permission")
        fun returnTrueWhenHasPermission() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission("xinventories.command.debug") } returns true

            val result = debugCommand.hasPermission(sender)

            assertTrue(result)
        }

        @Test
        @DisplayName("Should return false when sender lacks permission")
        fun returnFalseWhenLacksPermission() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission("xinventories.command.debug") } returns false

            val result = debugCommand.hasPermission(sender)

            assertFalse(result)
        }

        @Test
        @DisplayName("Should check correct permission string")
        fun checkCorrectPermissionString() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission(any<String>()) } returns true

            debugCommand.hasPermission(sender)

            verify(exactly = 1) { sender.hasPermission("xinventories.command.debug") }
        }
    }

    // =========================================================================
    // Subcommand Interface Compliance Tests
    // =========================================================================

    @Nested
    @DisplayName("Subcommand Interface Compliance")
    inner class SubcommandInterfaceComplianceTests {

        @Test
        @DisplayName("Should implement all required properties")
        fun implementAllRequiredProperties() {
            assertTrue(debugCommand.name.isNotEmpty())
            assertTrue(debugCommand.permission.isNotEmpty())
            assertTrue(debugCommand.usage.isNotEmpty())
            assertTrue(debugCommand.description.isNotEmpty())
            assertTrue(debugCommand.aliases.isEmpty() || debugCommand.aliases.isNotEmpty())
            assertFalse(debugCommand.playerOnly)
        }

        @Test
        @DisplayName("Should have non-blank name")
        fun haveNonBlankName() {
            assertTrue(debugCommand.name.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank permission")
        fun haveNonBlankPermission() {
            assertTrue(debugCommand.permission.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank usage")
        fun haveNonBlankUsage() {
            assertTrue(debugCommand.usage.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank description")
        fun haveNonBlankDescription() {
            assertTrue(debugCommand.description.isNotBlank())
        }

        @Test
        @DisplayName("Should have permission starting with xinventories")
        fun havePermissionStartingWithXinventories() {
            assertTrue(debugCommand.permission.startsWith("xinventories."))
        }

        @Test
        @DisplayName("Should have usage containing command name")
        fun haveUsageContainingCommandName() {
            assertTrue(debugCommand.usage.contains(debugCommand.name))
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should always return true from execute")
        fun alwaysReturnTrueFromExecute() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            val resultInfo = debugCommand.execute(plugin, sender, arrayOf("info"))
            val resultStorage = debugCommand.execute(plugin, sender, arrayOf("storage"))
            val resultPlayer = debugCommand.execute(plugin, sender, arrayOf("player"))
            val resultUnknown = debugCommand.execute(plugin, sender, arrayOf("unknown"))
            val resultEmpty = debugCommand.execute(plugin, sender, emptyArray())

            assertTrue(resultInfo)
            assertTrue(resultStorage)
            assertTrue(resultPlayer)
            assertTrue(resultUnknown)
            assertTrue(resultEmpty)
        }

        @Test
        @DisplayName("Should handle extra arguments gracefully")
        fun handleExtraArgumentsGracefully() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            val result = debugCommand.execute(plugin, sender, arrayOf("info", "extra", "args", "ignored"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Debug Info") }) }
        }

        @Test
        @DisplayName("Should handle mixed case action arguments")
        fun handleMixedCaseActionArguments() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            val resultMixed = debugCommand.execute(plugin, sender, arrayOf("InFo"))

            assertTrue(resultMixed)
            verify { sender.sendMessage(match<String> { it.contains("Debug Info") }) }
        }

        @Test
        @DisplayName("Should handle player action with Player sender querying self")
        fun handlePlayerActionWithPlayerSenderQueryingSelf() = runTest {
            val playerUuid = UUID.randomUUID()
            val playerSender = createMockPlayer(uuid = playerUuid, name = "SelfPlayer")
            val group = createMockGroup("survival")

            every { groupService.getGroupForWorld(playerSender.world) } returns group
            every { inventoryService.getCurrentGroup(playerSender) } returns "survival"
            every { inventoryService.getActiveSnapshot(playerSender) } returns null
            every { inventoryService.hasBypass(playerSender) } returns false

            val result = debugCommand.execute(plugin, playerSender, arrayOf("player"))

            assertTrue(result)
            verify { playerSender.sendMessage(match<String> { it.contains("SelfPlayer") }) }
        }

        @Test
        @DisplayName("Should handle zero cache stats")
        fun handleZeroCacheStats() = runTest {
            val sender = createMockCommandSender()
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns emptyList()
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns CacheStatistics(0, 0, 0, 0, 0, 0)
            coEvery { storageService.getEntryCount() } returns 0
            coEvery { storageService.getStorageSize() } returns 0L
            coEvery { storageService.isHealthy() } returns true

            val result = debugCommand.execute(plugin, sender, arrayOf("info"))

            assertTrue(result)
            verify { sender.sendMessage(match<String> { it.contains("Cache Size") && it.contains("0") }) }
        }

        @Test
        @DisplayName("Should handle special characters in player names for tab completion")
        fun handleSpecialCharactersInPlayerNamesForTabCompletion() {
            val sender = createMockCommandSender()
            val players = listOf(
                createMockPlayer(name = "Player_With_Underscore"),
                createMockPlayer(name = "Player123"),
                createMockPlayer(name = "xX_Pro_Xx")
            )

            every { Bukkit.getOnlinePlayers() } returns players

            val completions = debugCommand.tabComplete(plugin, sender, arrayOf("player", "x"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("xX_Pro_Xx"))
        }
    }

    // =========================================================================
    // Different Sender Type Tests
    // =========================================================================

    @Nested
    @DisplayName("Different Sender Types")
    inner class DifferentSenderTypeTests {

        @Test
        @DisplayName("Should work with console CommandSender for info action")
        fun workWithConsoleForInfoAction() = runTest {
            val consoleSender = createMockCommandSender("CONSOLE")
            val defaultGroup = createMockGroup("default")

            every { descriptionFile.version } returns "1.0.0"
            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            every { mainConfig.cache } returns CacheConfig(enabled = true)
            every { groupService.getAllGroups() } returns listOf(createMockGroup())
            every { groupService.getDefaultGroup() } returns defaultGroup
            every { Bukkit.getOnlinePlayers() } returns emptyList()
            every { storageService.getCacheStats() } returns createCacheStats()
            coEvery { storageService.getEntryCount() } returns 50
            coEvery { storageService.getStorageSize() } returns 2048L
            coEvery { storageService.isHealthy() } returns true

            val result = debugCommand.execute(plugin, consoleSender, arrayOf("info"))

            assertTrue(result)
            verify { consoleSender.sendMessage(match<String> { it.contains("Debug Info") }) }
        }

        @Test
        @DisplayName("Should work with Player sender for storage action")
        fun workWithPlayerForStorageAction() = runTest {
            val player = createMockPlayer(name = "AdminPlayer")

            every { mainConfig.storage } returns StorageConfig(type = StorageType.YAML)
            coEvery { storageService.getEntryCount() } returns 100
            coEvery { storageService.getStorageSize() } returns 51200L
            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(UUID.randomUUID())
            coEvery { storageService.isHealthy() } returns true
            every { cache.getDirtyCount() } returns 0

            val result = debugCommand.execute(plugin, player, arrayOf("storage"))

            assertTrue(result)
            verify { player.sendMessage(match<String> { it.contains("Storage Debug Info") }) }
        }
    }
}

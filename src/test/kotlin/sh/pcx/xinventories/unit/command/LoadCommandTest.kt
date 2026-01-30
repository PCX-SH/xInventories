package sh.pcx.xinventories.unit.command

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.bukkit.Bukkit
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.event.InventoryLoadEvent
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.command.subcommand.LoadCommand
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.InventoryService
import sh.pcx.xinventories.internal.service.MessageService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.logging.Logger

/**
 * Unit tests for LoadCommand.
 *
 * Tests cover:
 * - Command properties (name, permission, usage, description)
 * - execute() - Loading player's inventory from storage
 * - Player argument is required
 * - Optional group argument - loads from specific group
 * - Default to world's group if no group specified
 * - Player not found error handling
 * - Group not found error handling
 * - Success/failure message sending
 * - tabComplete() - Player names and group names
 */
@DisplayName("LoadCommand Unit Tests")
class LoadCommandTest {

    private lateinit var plugin: XInventories
    private lateinit var serviceManager: ServiceManager
    private lateinit var groupService: GroupService
    private lateinit var inventoryService: InventoryService
    private lateinit var messageService: MessageService
    private lateinit var sender: CommandSender
    private lateinit var loadCommand: LoadCommand

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("LoadCommandTest"), false)
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs

            // Mock Bukkit static methods
            mockkStatic(Bukkit::class)
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
        groupService = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        sender = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.groupService } returns groupService
        every { serviceManager.inventoryService } returns inventoryService
        every { serviceManager.messageService } returns messageService

        loadCommand = LoadCommand()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockPlayer(name: String): Player {
        return mockk<Player>(relaxed = true).apply {
            every { this@apply.name } returns name
            every { this@apply.world } returns mockk<World>(relaxed = true)
        }
    }

    private fun createMockGroup(
        name: String,
        worlds: Set<String> = emptySet(),
        patterns: List<String> = emptyList(),
        priority: Int = 0,
        parent: String? = null,
        isDefault: Boolean = false
    ): Group {
        return mockk<Group>(relaxed = true).apply {
            every { this@apply.name } returns name
            every { this@apply.worlds } returns worlds
            every { patternStrings } returns patterns
            every { this@apply.priority } returns priority
            every { this@apply.parent } returns parent
            every { this@apply.isDefault } returns isDefault
        }
    }

    private fun createMockInventoryGroup(
        name: String,
        worlds: Set<String> = emptySet(),
        patterns: List<String> = emptyList(),
        priority: Int = 0,
        parent: String? = null,
        isDefault: Boolean = false
    ): InventoryGroup {
        return InventoryGroup(
            name = name,
            worlds = worlds,
            patterns = patterns,
            priority = priority,
            parent = parent,
            settings = GroupSettings(),
            isDefault = isDefault
        )
    }

    // =========================================================================
    // Command Properties Tests
    // =========================================================================

    @Nested
    @DisplayName("Command Properties")
    inner class CommandPropertiesTests {

        @Test
        @DisplayName("Should have correct name")
        fun hasCorrectName() {
            assertEquals("load", loadCommand.name)
        }

        @Test
        @DisplayName("Should have correct permission")
        fun hasCorrectPermission() {
            assertEquals("xinventories.command.load", loadCommand.permission)
        }

        @Test
        @DisplayName("Should have correct usage string")
        fun hasCorrectUsage() {
            assertEquals("/xinv load <player> [group]", loadCommand.usage)
        }

        @Test
        @DisplayName("Should have correct description")
        fun hasCorrectDescription() {
            assertEquals("Force load inventory", loadCommand.description)
        }

        @Test
        @DisplayName("Should not be player-only command")
        fun isNotPlayerOnly() {
            assertFalse(loadCommand.playerOnly)
        }

        @Test
        @DisplayName("Should have empty aliases")
        fun hasEmptyAliases() {
            assertTrue(loadCommand.aliases.isEmpty())
        }
    }

    // =========================================================================
    // Execute with No Arguments Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute with No Arguments")
    inner class ExecuteNoArgsTests {

        @Test
        @DisplayName("Should show usage when no arguments provided")
        fun showUsageWhenNoArgs() = runTest {
            val result = loadCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
            verify {
                messageService.send(
                    sender,
                    "invalid-syntax",
                    "usage" to "/xinv load <player> [group]"
                )
            }
        }
    }

    // =========================================================================
    // Execute with Player Not Found Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute with Player Not Found")
    inner class ExecutePlayerNotFoundTests {

        @Test
        @DisplayName("Should show error when player not found")
        fun showErrorWhenPlayerNotFound() = runTest {
            every { Bukkit.getPlayer("NonExistentPlayer") } returns null

            val result = loadCommand.execute(plugin, sender, arrayOf("NonExistentPlayer"))

            assertTrue(result)
            verify { messageService.send(sender, "player-not-found", "player" to "NonExistentPlayer") }
        }

        @Test
        @DisplayName("Should handle player lookup case-sensitively")
        fun handlePlayerLookupCaseSensitively() = runTest {
            every { Bukkit.getPlayer("testplayer") } returns null
            every { Bukkit.getPlayer("TestPlayer") } returns createMockPlayer("TestPlayer")

            val result = loadCommand.execute(plugin, sender, arrayOf("testplayer"))

            assertTrue(result)
            verify { messageService.send(sender, "player-not-found", "player" to "testplayer") }
        }

        @Test
        @DisplayName("Should include player name in error message")
        fun includePlayerNameInErrorMessage() = runTest {
            every { Bukkit.getPlayer("SpecificName123") } returns null

            val result = loadCommand.execute(plugin, sender, arrayOf("SpecificName123"))

            assertTrue(result)
            verify { messageService.send(sender, "player-not-found", "player" to "SpecificName123") }
        }
    }

    // =========================================================================
    // Execute with Group Not Found Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute with Group Not Found")
    inner class ExecuteGroupNotFoundTests {

        @Test
        @DisplayName("Should show error when specified group not found")
        fun showErrorWhenGroupNotFound() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi("nonexistent_group") } returns null

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer", "nonexistent_group"))

            assertTrue(result)
            verify { messageService.send(sender, "group-not-found", "group" to "nonexistent_group") }
        }

        @Test
        @DisplayName("Should include group name in error message")
        fun includeGroupNameInErrorMessage() = runTest {
            val targetPlayer = createMockPlayer("Player1")
            every { Bukkit.getPlayer("Player1") } returns targetPlayer
            every { groupService.getGroupApi("MyCustomGroup") } returns null

            val result = loadCommand.execute(plugin, sender, arrayOf("Player1", "MyCustomGroup"))

            assertTrue(result)
            verify { messageService.send(sender, "group-not-found", "group" to "MyCustomGroup") }
        }

        @Test
        @DisplayName("Should not proceed to load when group not found")
        fun shouldNotProceedToLoadWhenGroupNotFound() = runTest {
            val targetPlayer = createMockPlayer("Player1")
            every { Bukkit.getPlayer("Player1") } returns targetPlayer
            every { groupService.getGroupApi("invalid_group") } returns null

            loadCommand.execute(plugin, sender, arrayOf("Player1", "invalid_group"))

            coVerify(exactly = 0) {
                inventoryService.loadInventory(any(), any(), any())
            }
        }
    }

    // =========================================================================
    // Execute with Default Group (No Group Argument) Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute with Default Group (No Group Argument)")
    inner class ExecuteDefaultGroupTests {

        @Test
        @DisplayName("Should use world's group when no group specified")
        fun useWorldGroupWhenNoGroupSpecified() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("survival")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(targetPlayer, worldGroup, InventoryLoadEvent.LoadReason.COMMAND) } returns true

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer"))

            assertTrue(result)
            coVerify { inventoryService.loadInventory(targetPlayer, worldGroup, InventoryLoadEvent.LoadReason.COMMAND) }
        }

        @Test
        @DisplayName("Should send success message when load succeeds with default group")
        fun sendSuccessMessageWhenLoadSucceedsWithDefaultGroup() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("default")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(targetPlayer, worldGroup, InventoryLoadEvent.LoadReason.COMMAND) } returns true

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer"))

            assertTrue(result)
            verify { messageService.send(sender, "inventory-loaded", "player" to "TestPlayer") }
        }

        @Test
        @DisplayName("Should send failure message when load fails with default group")
        fun sendFailureMessageWhenLoadFailsWithDefaultGroup() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("default")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(targetPlayer, worldGroup, InventoryLoadEvent.LoadReason.COMMAND) } returns false

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer"))

            assertTrue(result)
            verify { messageService.send(sender, "inventory-load-failed", "player" to "TestPlayer") }
        }
    }

    // =========================================================================
    // Execute with Specified Group Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute with Specified Group")
    inner class ExecuteSpecifiedGroupTests {

        @Test
        @DisplayName("Should use specified group when provided")
        fun useSpecifiedGroupWhenProvided() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val specifiedGroup = createMockInventoryGroup("creative")

            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi("creative") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(targetPlayer, specifiedGroup, InventoryLoadEvent.LoadReason.COMMAND) } returns true

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer", "creative"))

            assertTrue(result)
            coVerify { inventoryService.loadInventory(targetPlayer, specifiedGroup, InventoryLoadEvent.LoadReason.COMMAND) }
        }

        @Test
        @DisplayName("Should send success message when load succeeds with specified group")
        fun sendSuccessMessageWhenLoadSucceedsWithSpecifiedGroup() = runTest {
            val targetPlayer = createMockPlayer("Player1")
            val specifiedGroup = createMockInventoryGroup("skyblock")

            every { Bukkit.getPlayer("Player1") } returns targetPlayer
            every { groupService.getGroupApi("skyblock") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(targetPlayer, specifiedGroup, InventoryLoadEvent.LoadReason.COMMAND) } returns true

            val result = loadCommand.execute(plugin, sender, arrayOf("Player1", "skyblock"))

            assertTrue(result)
            verify { messageService.send(sender, "inventory-loaded", "player" to "Player1") }
        }

        @Test
        @DisplayName("Should send failure message when load fails with specified group")
        fun sendFailureMessageWhenLoadFailsWithSpecifiedGroup() = runTest {
            val targetPlayer = createMockPlayer("Player1")
            val specifiedGroup = createMockInventoryGroup("adventure")

            every { Bukkit.getPlayer("Player1") } returns targetPlayer
            every { groupService.getGroupApi("adventure") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(targetPlayer, specifiedGroup, InventoryLoadEvent.LoadReason.COMMAND) } returns false

            val result = loadCommand.execute(plugin, sender, arrayOf("Player1", "adventure"))

            assertTrue(result)
            verify { messageService.send(sender, "inventory-load-failed", "player" to "Player1") }
        }

        @Test
        @DisplayName("Should not call getGroupForWorldApi when group is specified")
        fun shouldNotCallGetGroupForWorldApiWhenGroupSpecified() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val specifiedGroup = createMockInventoryGroup("custom")

            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi("custom") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            loadCommand.execute(plugin, sender, arrayOf("TestPlayer", "custom"))

            verify(exactly = 0) { groupService.getGroupForWorldApi(any<World>()) }
        }
    }

    // =========================================================================
    // Execute Load Reason Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute Load Reason")
    inner class ExecuteLoadReasonTests {

        @Test
        @DisplayName("Should use COMMAND load reason")
        fun useCommandLoadReason() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("survival")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            loadCommand.execute(plugin, sender, arrayOf("TestPlayer"))

            coVerify {
                inventoryService.loadInventory(
                    targetPlayer,
                    worldGroup,
                    InventoryLoadEvent.LoadReason.COMMAND
                )
            }
        }

        @Test
        @DisplayName("Should use COMMAND load reason with specified group")
        fun useCommandLoadReasonWithSpecifiedGroup() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val specifiedGroup = createMockInventoryGroup("creative")

            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi("creative") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            loadCommand.execute(plugin, sender, arrayOf("TestPlayer", "creative"))

            coVerify {
                inventoryService.loadInventory(
                    targetPlayer,
                    specifiedGroup,
                    InventoryLoadEvent.LoadReason.COMMAND
                )
            }
        }
    }

    // =========================================================================
    // Execute Always Returns True Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute Always Returns True")
    inner class ExecuteAlwaysReturnsTrueTests {

        @Test
        @DisplayName("Should return true when no arguments")
        fun returnTrueWhenNoArgs() = runTest {
            val result = loadCommand.execute(plugin, sender, emptyArray())
            assertTrue(result)
        }

        @Test
        @DisplayName("Should return true when player not found")
        fun returnTrueWhenPlayerNotFound() = runTest {
            every { Bukkit.getPlayer("unknown") } returns null
            val result = loadCommand.execute(plugin, sender, arrayOf("unknown"))
            assertTrue(result)
        }

        @Test
        @DisplayName("Should return true when group not found")
        fun returnTrueWhenGroupNotFound() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi("invalid") } returns null

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer", "invalid"))
            assertTrue(result)
        }

        @Test
        @DisplayName("Should return true when load succeeds")
        fun returnTrueWhenLoadSucceeds() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("survival")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer"))
            assertTrue(result)
        }

        @Test
        @DisplayName("Should return true when load fails")
        fun returnTrueWhenLoadFails() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("survival")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns false

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer"))
            assertTrue(result)
        }
    }

    // =========================================================================
    // Tab Complete Tests - First Argument (Player Names)
    // =========================================================================

    @Nested
    @DisplayName("Tab Complete - First Argument (Player Names)")
    inner class TabCompleteFirstArgTests {

        @Test
        @DisplayName("Should return all online players when first arg is empty")
        fun returnAllOnlinePlayersWhenFirstArgEmpty() {
            val player1 = createMockPlayer("Alice")
            val player2 = createMockPlayer("Bob")
            val player3 = createMockPlayer("Charlie")
            every { Bukkit.getOnlinePlayers() } returns listOf(player1, player2, player3)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf(""))

            assertEquals(3, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Bob"))
            assertTrue(completions.contains("Charlie"))
        }

        @Test
        @DisplayName("Should filter players by prefix")
        fun filterPlayersByPrefix() {
            val player1 = createMockPlayer("Alice")
            val player2 = createMockPlayer("Adam")
            val player3 = createMockPlayer("Bob")
            every { Bukkit.getOnlinePlayers() } returns listOf(player1, player2, player3)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("A"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Adam"))
            assertFalse(completions.contains("Bob"))
        }

        @Test
        @DisplayName("Should be case-insensitive for player filtering")
        fun beCaseInsensitiveForPlayerFiltering() {
            val player1 = createMockPlayer("Alice")
            val player2 = createMockPlayer("adam")
            every { Bukkit.getOnlinePlayers() } returns listOf(player1, player2)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("a"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("adam"))
        }

        @Test
        @DisplayName("Should return empty list when no players match")
        fun returnEmptyWhenNoPlayersMatch() {
            val player1 = createMockPlayer("Alice")
            val player2 = createMockPlayer("Bob")
            every { Bukkit.getOnlinePlayers() } returns listOf(player1, player2)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("Z"))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should handle empty online players list")
        fun handleEmptyOnlinePlayersList() {
            every { Bukkit.getOnlinePlayers() } returns emptyList()

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf(""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should match partial player name")
        fun matchPartialPlayerName() {
            val player1 = createMockPlayer("PlayerOne")
            val player2 = createMockPlayer("PlayerTwo")
            val player3 = createMockPlayer("Admin")
            every { Bukkit.getOnlinePlayers() } returns listOf(player1, player2, player3)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("Player"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("PlayerOne"))
            assertTrue(completions.contains("PlayerTwo"))
        }
    }

    // =========================================================================
    // Tab Complete Tests - Second Argument (Group Names)
    // =========================================================================

    @Nested
    @DisplayName("Tab Complete - Second Argument (Group Names)")
    inner class TabCompleteSecondArgTests {

        @Test
        @DisplayName("Should return all groups when second arg is empty")
        fun returnAllGroupsWhenSecondArgEmpty() {
            val group1 = createMockGroup("survival")
            val group2 = createMockGroup("creative")
            val group3 = createMockGroup("adventure")
            every { groupService.getAllGroups() } returns listOf(group1, group2, group3)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("PlayerName", ""))

            assertEquals(3, completions.size)
            assertTrue(completions.contains("survival"))
            assertTrue(completions.contains("creative"))
            assertTrue(completions.contains("adventure"))
        }

        @Test
        @DisplayName("Should filter groups by prefix")
        fun filterGroupsByPrefix() {
            val group1 = createMockGroup("survival")
            val group2 = createMockGroup("skyblock")
            val group3 = createMockGroup("creative")
            every { groupService.getAllGroups() } returns listOf(group1, group2, group3)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("PlayerName", "s"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("survival"))
            assertTrue(completions.contains("skyblock"))
            assertFalse(completions.contains("creative"))
        }

        @Test
        @DisplayName("Should be case-insensitive for group filtering")
        fun beCaseInsensitiveForGroupFiltering() {
            val group1 = createMockGroup("Survival")
            val group2 = createMockGroup("SKYBLOCK")
            val group3 = createMockGroup("creative")
            every { groupService.getAllGroups() } returns listOf(group1, group2, group3)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("PlayerName", "S"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Survival"))
            assertTrue(completions.contains("SKYBLOCK"))
        }

        @Test
        @DisplayName("Should return empty list when no groups match")
        fun returnEmptyWhenNoGroupsMatch() {
            val group1 = createMockGroup("survival")
            val group2 = createMockGroup("creative")
            every { groupService.getAllGroups() } returns listOf(group1, group2)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("PlayerName", "x"))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should handle empty groups list")
        fun handleEmptyGroupsList() {
            every { groupService.getAllGroups() } returns emptyList()

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("PlayerName", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should match partial group name")
        fun matchPartialGroupName() {
            val group1 = createMockGroup("survival_main")
            val group2 = createMockGroup("survival_nether")
            val group3 = createMockGroup("creative")
            every { groupService.getAllGroups() } returns listOf(group1, group2, group3)

            val completions = loadCommand.tabComplete(plugin, sender, arrayOf("PlayerName", "survival"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("survival_main"))
            assertTrue(completions.contains("survival_nether"))
        }
    }

    // =========================================================================
    // Tab Complete Tests - Third Argument and Beyond
    // =========================================================================

    @Nested
    @DisplayName("Tab Complete - Third Argument and Beyond")
    inner class TabCompleteThirdArgTests {

        @Test
        @DisplayName("Should return empty list for third argument")
        fun returnEmptyForThirdArg() {
            val completions = loadCommand.tabComplete(
                plugin, sender, arrayOf("PlayerName", "group", "")
            )

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list for fourth argument")
        fun returnEmptyForFourthArg() {
            val completions = loadCommand.tabComplete(
                plugin, sender, arrayOf("PlayerName", "group", "extra", "")
            )

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list for many arguments")
        fun returnEmptyForManyArgs() {
            val completions = loadCommand.tabComplete(
                plugin, sender, arrayOf("a", "b", "c", "d", "e", "f")
            )

            assertTrue(completions.isEmpty())
        }
    }

    // =========================================================================
    // Permission Check Tests
    // =========================================================================

    @Nested
    @DisplayName("Permission Check")
    inner class PermissionCheckTests {

        @Test
        @DisplayName("Should check correct permission via hasPermission")
        fun checkCorrectPermission() {
            every { sender.hasPermission("xinventories.command.load") } returns true

            val result = loadCommand.hasPermission(sender)

            assertTrue(result)
            verify { sender.hasPermission("xinventories.command.load") }
        }

        @Test
        @DisplayName("Should return false when permission denied")
        fun returnFalseWhenPermissionDenied() {
            every { sender.hasPermission("xinventories.command.load") } returns false

            val result = loadCommand.hasPermission(sender)

            assertFalse(result)
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle player name with special characters in lookup")
        fun handlePlayerNameWithSpecialCharacters() = runTest {
            every { Bukkit.getPlayer("Player_123") } returns null

            val result = loadCommand.execute(plugin, sender, arrayOf("Player_123"))

            assertTrue(result)
            verify { messageService.send(sender, "player-not-found", "player" to "Player_123") }
        }

        @Test
        @DisplayName("Should handle group name with special characters")
        fun handleGroupNameWithSpecialCharacters() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val specifiedGroup = createMockInventoryGroup("my-group_v2")

            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi("my-group_v2") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer", "my-group_v2"))

            assertTrue(result)
            coVerify { inventoryService.loadInventory(targetPlayer, specifiedGroup, any()) }
        }

        @Test
        @DisplayName("Should handle numeric group name")
        fun handleNumericGroupName() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val specifiedGroup = createMockInventoryGroup("123")

            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi("123") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer", "123"))

            assertTrue(result)
            coVerify { inventoryService.loadInventory(targetPlayer, specifiedGroup, any()) }
        }

        @Test
        @DisplayName("Should handle extra arguments after group name (should be ignored)")
        fun handleExtraArgumentsAfterGroupName() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val specifiedGroup = createMockInventoryGroup("survival")

            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi("survival") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            // Extra arguments after group should be ignored
            val result = loadCommand.execute(
                plugin, sender, arrayOf("TestPlayer", "survival", "extra", "args")
            )

            assertTrue(result)
            coVerify { inventoryService.loadInventory(targetPlayer, specifiedGroup, any()) }
        }

        @Test
        @DisplayName("Should handle very long player name")
        fun handleVeryLongPlayerName() = runTest {
            val longName = "a".repeat(100)
            every { Bukkit.getPlayer(longName) } returns null

            val result = loadCommand.execute(plugin, sender, arrayOf(longName))

            assertTrue(result)
            verify { messageService.send(sender, "player-not-found", "player" to longName) }
        }

        @Test
        @DisplayName("Should handle very long group name")
        fun handleVeryLongGroupName() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val longGroupName = "group" + "a".repeat(100)

            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi(longGroupName) } returns null

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer", longGroupName))

            assertTrue(result)
            verify { messageService.send(sender, "group-not-found", "group" to longGroupName) }
        }
    }

    // =========================================================================
    // Sender Types Tests
    // =========================================================================

    @Nested
    @DisplayName("Sender Types")
    inner class SenderTypesTests {

        @Test
        @DisplayName("Should work with console sender")
        fun workWithConsoleSender() = runTest {
            val consoleSender = mockk<CommandSender>(relaxed = true)
            val targetPlayer = createMockPlayer("TestPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("survival")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            val result = loadCommand.execute(plugin, consoleSender, arrayOf("TestPlayer"))

            assertTrue(result)
            verify { messageService.send(consoleSender, "inventory-loaded", "player" to "TestPlayer") }
        }

        @Test
        @DisplayName("Should work with player sender")
        fun workWithPlayerSender() = runTest {
            val playerSender = mockk<Player>(relaxed = true)
            val targetPlayer = createMockPlayer("TargetPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("survival")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TargetPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            val result = loadCommand.execute(plugin, playerSender, arrayOf("TargetPlayer"))

            assertTrue(result)
            verify { messageService.send(playerSender, "inventory-loaded", "player" to "TargetPlayer") }
        }

        @Test
        @DisplayName("Should allow player to load their own inventory")
        fun allowPlayerToLoadOwnInventory() = runTest {
            val playerSender = createMockPlayer("SelfPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("survival")

            every { playerSender.world } returns world
            every { Bukkit.getPlayer("SelfPlayer") } returns playerSender
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            val result = loadCommand.execute(plugin, playerSender, arrayOf("SelfPlayer"))

            assertTrue(result)
            coVerify { inventoryService.loadInventory(playerSender, worldGroup, any()) }
        }
    }

    // =========================================================================
    // Full Workflow Tests
    // =========================================================================

    @Nested
    @DisplayName("Full Workflow Tests")
    inner class FullWorkflowTests {

        @Test
        @DisplayName("Should handle complete load workflow with default group")
        fun handleCompleteLoadWorkflowWithDefaultGroup() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("survival")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(targetPlayer, worldGroup, InventoryLoadEvent.LoadReason.COMMAND) } returns true

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer"))

            assertTrue(result)

            // Verify workflow order
            verifyOrder {
                Bukkit.getPlayer("TestPlayer")
                groupService.getGroupForWorldApi(world)
            }
            coVerify { inventoryService.loadInventory(targetPlayer, worldGroup, InventoryLoadEvent.LoadReason.COMMAND) }
            verify { messageService.send(sender, "inventory-loaded", "player" to "TestPlayer") }
        }

        @Test
        @DisplayName("Should handle complete load workflow with specified group")
        fun handleCompleteLoadWorkflowWithSpecifiedGroup() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val specifiedGroup = createMockInventoryGroup("creative")

            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupApi("creative") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(targetPlayer, specifiedGroup, InventoryLoadEvent.LoadReason.COMMAND) } returns true

            val result = loadCommand.execute(plugin, sender, arrayOf("TestPlayer", "creative"))

            assertTrue(result)

            // Verify workflow order
            verifyOrder {
                Bukkit.getPlayer("TestPlayer")
                groupService.getGroupApi("creative")
            }
            coVerify { inventoryService.loadInventory(targetPlayer, specifiedGroup, InventoryLoadEvent.LoadReason.COMMAND) }
            verify { messageService.send(sender, "inventory-loaded", "player" to "TestPlayer") }
        }

        @Test
        @DisplayName("Should handle multiple loads for different players")
        fun handleMultipleLoadsForDifferentPlayers() = runTest {
            val player1 = createMockPlayer("Player1")
            val player2 = createMockPlayer("Player2")
            val world1 = mockk<World>(relaxed = true)
            val world2 = mockk<World>(relaxed = true)
            val group1 = createMockInventoryGroup("survival")
            val group2 = createMockInventoryGroup("creative")

            every { player1.world } returns world1
            every { player2.world } returns world2
            every { Bukkit.getPlayer("Player1") } returns player1
            every { Bukkit.getPlayer("Player2") } returns player2
            every { groupService.getGroupForWorldApi(world1) } returns group1
            every { groupService.getGroupForWorldApi(world2) } returns group2
            coEvery { inventoryService.loadInventory(player1, group1, any()) } returns true
            coEvery { inventoryService.loadInventory(player2, group2, any()) } returns true

            // Load for player 1
            val result1 = loadCommand.execute(plugin, sender, arrayOf("Player1"))
            assertTrue(result1)
            verify { messageService.send(sender, "inventory-loaded", "player" to "Player1") }

            // Load for player 2
            val result2 = loadCommand.execute(plugin, sender, arrayOf("Player2"))
            assertTrue(result2)
            verify { messageService.send(sender, "inventory-loaded", "player" to "Player2") }
        }

        @Test
        @DisplayName("Should handle load attempt after player disconnects")
        fun handleLoadAttemptAfterPlayerDisconnects() = runTest {
            // Player was online, but disconnected before command executed
            every { Bukkit.getPlayer("DisconnectedPlayer") } returns null

            val result = loadCommand.execute(plugin, sender, arrayOf("DisconnectedPlayer"))

            assertTrue(result)
            verify { messageService.send(sender, "player-not-found", "player" to "DisconnectedPlayer") }
            coVerify(exactly = 0) { inventoryService.loadInventory(any(), any(), any()) }
        }
    }

    // =========================================================================
    // Service Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Service Integration")
    inner class ServiceIntegrationTests {

        @Test
        @DisplayName("Should get services from plugin's service manager")
        fun getServicesFromServiceManager() = runTest {
            val targetPlayer = createMockPlayer("TestPlayer")
            val world = mockk<World>(relaxed = true)
            val worldGroup = createMockInventoryGroup("survival")

            every { targetPlayer.world } returns world
            every { Bukkit.getPlayer("TestPlayer") } returns targetPlayer
            every { groupService.getGroupForWorldApi(world) } returns worldGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            loadCommand.execute(plugin, sender, arrayOf("TestPlayer"))

            verify { plugin.serviceManager }
            verify { serviceManager.messageService }
            verify { serviceManager.inventoryService }
            verify { serviceManager.groupService }
        }

        @Test
        @DisplayName("Should call inventoryService.loadInventory with correct parameters")
        fun callInventoryServiceWithCorrectParameters() = runTest {
            val targetPlayer = createMockPlayer("TargetPlayer")
            val specifiedGroup = createMockInventoryGroup(
                name = "custom_group",
                worlds = setOf("world1", "world2"),
                priority = 10
            )

            every { Bukkit.getPlayer("TargetPlayer") } returns targetPlayer
            every { groupService.getGroupApi("custom_group") } returns specifiedGroup
            coEvery { inventoryService.loadInventory(any(), any(), any()) } returns true

            loadCommand.execute(plugin, sender, arrayOf("TargetPlayer", "custom_group"))

            coVerify {
                inventoryService.loadInventory(
                    match { it == targetPlayer },
                    match { it.name == "custom_group" && it.priority == 10 },
                    match { it == InventoryLoadEvent.LoadReason.COMMAND }
                )
            }
        }
    }
}

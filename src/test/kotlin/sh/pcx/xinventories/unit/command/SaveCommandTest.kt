package sh.pcx.xinventories.unit.command

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.command.subcommand.SaveCommand
import sh.pcx.xinventories.internal.service.InventoryService
import sh.pcx.xinventories.internal.service.MessageService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for SaveCommand.
 *
 * Tests cover:
 * - Subcommand properties (name, permission, usage, description)
 * - execute() - Saving player's own inventory
 * - execute() with player argument - Saving another player's inventory
 * - Permission check for saving others (xinventories.command.save.others)
 * - Handling console sender without player argument
 * - Player not found error handling
 * - Success/failure message sending
 * - tabComplete() - Player name completions (only with save.others permission)
 */
@DisplayName("SaveCommand Unit Tests")
class SaveCommandTest {

    private lateinit var plugin: XInventories
    private lateinit var serviceManager: ServiceManager
    private lateinit var messageService: MessageService
    private lateinit var inventoryService: InventoryService
    private lateinit var saveCommand: SaveCommand

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("SaveCommandTest"), false)
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
        messageService = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.messageService } returns messageService
        every { serviceManager.inventoryService } returns inventoryService

        // Mock Bukkit static methods
        mockkStatic(Bukkit::class)

        saveCommand = SaveCommand()
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
        name: String = "TestPlayer"
    ): Player {
        return mockk<Player>(relaxed = true).apply {
            every { uniqueId } returns uuid
            every { this@apply.name } returns name
        }
    }

    private fun createMockCommandSender(name: String = "CONSOLE"): CommandSender {
        return mockk<CommandSender>(relaxed = true).apply {
            every { this@apply.name } returns name
        }
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
            assertEquals("save", saveCommand.name)
        }

        @Test
        @DisplayName("Should have correct permission")
        fun hasCorrectPermission() {
            assertEquals("xinventories.command.save", saveCommand.permission)
        }

        @Test
        @DisplayName("Should have correct usage")
        fun hasCorrectUsage() {
            assertEquals("/xinv save [player]", saveCommand.usage)
        }

        @Test
        @DisplayName("Should have correct description")
        fun hasCorrectDescription() {
            assertEquals("Force save inventory", saveCommand.description)
        }

        @Test
        @DisplayName("Should have empty aliases by default")
        fun hasEmptyAliases() {
            assertTrue(saveCommand.aliases.isEmpty())
        }

        @Test
        @DisplayName("Should not be player-only command")
        fun notPlayerOnlyCommand() {
            assertFalse(saveCommand.playerOnly)
        }
    }

    // =========================================================================
    // Execute - Self Save Tests (Player saving their own inventory)
    // =========================================================================

    @Nested
    @DisplayName("execute() - Self Save")
    inner class ExecuteSelfSaveTests {

        @Test
        @DisplayName("Should save own inventory when Player executes without arguments")
        fun saveOwnInventoryWhenPlayerExecutesWithoutArgs() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns true

            val result = saveCommand.execute(plugin, player, emptyArray())

            assertTrue(result)
            coVerify(exactly = 1) { inventoryService.saveInventory(player) }
        }

        @Test
        @DisplayName("Should send success message when self save succeeds")
        fun sendSuccessMessageWhenSelfSaveSucceeds() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns true

            saveCommand.execute(plugin, player, emptyArray())

            verify(exactly = 1) { messageService.send(player, "inventory-saved-self") }
        }

        @Test
        @DisplayName("Should send failure message when self save fails")
        fun sendFailureMessageWhenSelfSaveFails() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns false

            saveCommand.execute(plugin, player, emptyArray())

            verify(exactly = 1) { messageService.send(player, "inventory-save-failed", "player" to "TestPlayer") }
        }

        @Test
        @DisplayName("Should return true regardless of save result")
        fun returnTrueRegardlessOfSaveResult() = runTest {
            val player = createMockPlayer(name = "TestPlayer")

            coEvery { inventoryService.saveInventory(player) } returns true
            val resultSuccess = saveCommand.execute(plugin, player, emptyArray())

            coEvery { inventoryService.saveInventory(player) } returns false
            val resultFailure = saveCommand.execute(plugin, player, emptyArray())

            assertTrue(resultSuccess)
            assertTrue(resultFailure)
        }

        @Test
        @DisplayName("Should not check save.others permission when saving own inventory")
        fun notCheckSaveOthersPermissionWhenSavingOwn() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns true

            saveCommand.execute(plugin, player, emptyArray())

            verify(exactly = 0) { player.hasPermission("xinventories.command.save.others") }
        }

        @Test
        @DisplayName("Should use player instance as target when no arguments")
        fun usePlayerInstanceAsTargetWhenNoArgs() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns true

            saveCommand.execute(plugin, player, emptyArray())

            coVerify { inventoryService.saveInventory(player) }
            verify(exactly = 0) { Bukkit.getPlayer(any<String>()) }
        }
    }

    // =========================================================================
    // Execute - Save Others Tests (Saving another player's inventory)
    // =========================================================================

    @Nested
    @DisplayName("execute() - Save Others")
    inner class ExecuteSaveOthersTests {

        @Test
        @DisplayName("Should check save.others permission when player argument provided")
        fun checkSaveOthersPermissionWhenPlayerArgProvided() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            verify(exactly = 1) { sender.hasPermission("xinventories.command.save.others") }
        }

        @Test
        @DisplayName("Should send no-permission message when lacking save.others permission")
        fun sendNoPermissionMessageWhenLackingSaveOthersPermission() = runTest {
            val sender = createMockPlayer(name = "NormalPlayer")
            every { sender.hasPermission("xinventories.command.save.others") } returns false

            val result = saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            assertTrue(result)
            verify(exactly = 1) { messageService.send(sender, "no-permission") }
            coVerify(exactly = 0) { inventoryService.saveInventory(any()) }
        }

        @Test
        @DisplayName("Should not call Bukkit.getPlayer when lacking permission")
        fun notCallGetPlayerWhenLackingPermission() = runTest {
            val sender = createMockPlayer(name = "NormalPlayer")
            every { sender.hasPermission("xinventories.command.save.others") } returns false

            saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            verify(exactly = 0) { Bukkit.getPlayer(any<String>()) }
        }

        @Test
        @DisplayName("Should save target player's inventory when has permission and player found")
        fun saveTargetPlayerInventoryWhenHasPermissionAndPlayerFound() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            coVerify(exactly = 1) { inventoryService.saveInventory(target) }
        }

        @Test
        @DisplayName("Should send success message with player name when saving another player")
        fun sendSuccessMessageWithPlayerNameWhenSavingAnother() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            verify(exactly = 1) { messageService.send(sender, "inventory-saved", "player" to "TargetPlayer") }
        }

        @Test
        @DisplayName("Should send failure message with player name when save fails for another player")
        fun sendFailureMessageWithPlayerNameWhenSaveFailsForAnother() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns false

            saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            verify(exactly = 1) { messageService.send(sender, "inventory-save-failed", "player" to "TargetPlayer") }
        }

        @Test
        @DisplayName("Should use inventory-saved message (not inventory-saved-self) for others")
        fun useInventorySavedMessageForOthers() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            verify(exactly = 0) { messageService.send(sender, "inventory-saved-self") }
            verify(exactly = 1) { messageService.send(sender, "inventory-saved", "player" to "TargetPlayer") }
        }

        @Test
        @DisplayName("Should work with console sender saving another player")
        fun workWithConsoleSenderSavingAnotherPlayer() = runTest {
            val sender = createMockCommandSender("CONSOLE")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            val result = saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            assertTrue(result)
            coVerify(exactly = 1) { inventoryService.saveInventory(target) }
            verify(exactly = 1) { messageService.send(sender, "inventory-saved", "player" to "TargetPlayer") }
        }
    }

    // =========================================================================
    // Execute - Player Not Found Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Player Not Found")
    inner class ExecutePlayerNotFoundTests {

        @Test
        @DisplayName("Should send player-not-found message when target player not online")
        fun sendPlayerNotFoundMessageWhenTargetNotOnline() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("NonExistentPlayer") } returns null

            val result = saveCommand.execute(plugin, sender, arrayOf("NonExistentPlayer"))

            assertTrue(result)
            verify(exactly = 1) { messageService.send(sender, "player-not-found", "player" to "NonExistentPlayer") }
        }

        @Test
        @DisplayName("Should not call saveInventory when player not found")
        fun notCallSaveInventoryWhenPlayerNotFound() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("NonExistentPlayer") } returns null

            saveCommand.execute(plugin, sender, arrayOf("NonExistentPlayer"))

            coVerify(exactly = 0) { inventoryService.saveInventory(any()) }
        }

        @Test
        @DisplayName("Should include player name in player-not-found message placeholder")
        fun includePlayerNameInPlayerNotFoundPlaceholder() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("SpecificPlayer123") } returns null

            saveCommand.execute(plugin, sender, arrayOf("SpecificPlayer123"))

            verify(exactly = 1) { messageService.send(sender, "player-not-found", "player" to "SpecificPlayer123") }
        }

        @Test
        @DisplayName("Should work with console sender when player not found")
        fun workWithConsoleSenderWhenPlayerNotFound() = runTest {
            val sender = createMockCommandSender("CONSOLE")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("OfflinePlayer") } returns null

            val result = saveCommand.execute(plugin, sender, arrayOf("OfflinePlayer"))

            assertTrue(result)
            verify(exactly = 1) { messageService.send(sender, "player-not-found", "player" to "OfflinePlayer") }
        }
    }

    // =========================================================================
    // Execute - Console Without Arguments Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Console Without Arguments")
    inner class ExecuteConsoleWithoutArgumentsTests {

        @Test
        @DisplayName("Should send player-only message when console executes without arguments")
        fun sendPlayerOnlyMessageWhenConsoleExecutesWithoutArgs() = runTest {
            val sender = createMockCommandSender("CONSOLE")

            val result = saveCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
            verify(exactly = 1) { messageService.send(sender, "player-only") }
        }

        @Test
        @DisplayName("Should not call saveInventory when console executes without arguments")
        fun notCallSaveInventoryWhenConsoleExecutesWithoutArgs() = runTest {
            val sender = createMockCommandSender("CONSOLE")

            saveCommand.execute(plugin, sender, emptyArray())

            coVerify(exactly = 0) { inventoryService.saveInventory(any()) }
        }

        @Test
        @DisplayName("Should not check any permission when console has no args")
        fun notCheckAnyPermissionWhenConsoleHasNoArgs() = runTest {
            val sender = createMockCommandSender("CONSOLE")

            saveCommand.execute(plugin, sender, emptyArray())

            verify(exactly = 0) { sender.hasPermission(any<String>()) }
        }

        @Test
        @DisplayName("Should return true even when console lacks player argument")
        fun returnTrueWhenConsoleLacksPlayerArgument() = runTest {
            val sender = createMockCommandSender("CONSOLE")

            val result = saveCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
        }
    }

    // =========================================================================
    // Execute - Message Key Tests
    // =========================================================================

    @Nested
    @DisplayName("Message Keys")
    inner class MessageKeyTests {

        @Test
        @DisplayName("Should use 'inventory-saved-self' for successful self save")
        fun useInventorySavedSelfForSuccessfulSelfSave() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns true

            saveCommand.execute(plugin, player, emptyArray())

            verify(exactly = 1) { messageService.send(player, "inventory-saved-self") }
        }

        @Test
        @DisplayName("Should use 'inventory-saved' for successful other save")
        fun useInventorySavedForSuccessfulOtherSave() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            verify(exactly = 1) { messageService.send(sender, "inventory-saved", "player" to "TargetPlayer") }
        }

        @Test
        @DisplayName("Should use 'inventory-save-failed' for failed save")
        fun useInventorySaveFailedForFailedSave() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns false

            saveCommand.execute(plugin, player, emptyArray())

            verify(exactly = 1) { messageService.send(player, "inventory-save-failed", "player" to "TestPlayer") }
        }

        @Test
        @DisplayName("Should use 'no-permission' when lacking save.others permission")
        fun useNoPermissionWhenLackingSaveOthersPermission() = runTest {
            val sender = createMockPlayer(name = "NormalPlayer")
            every { sender.hasPermission("xinventories.command.save.others") } returns false

            saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            verify(exactly = 1) { messageService.send(sender, "no-permission") }
        }

        @Test
        @DisplayName("Should use 'player-only' when console executes without arguments")
        fun usePlayerOnlyWhenConsoleExecutesWithoutArguments() = runTest {
            val sender = createMockCommandSender("CONSOLE")

            saveCommand.execute(plugin, sender, emptyArray())

            verify(exactly = 1) { messageService.send(sender, "player-only") }
        }

        @Test
        @DisplayName("Should use 'player-not-found' when target player not found")
        fun usePlayerNotFoundWhenTargetPlayerNotFound() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("NonExistent") } returns null

            saveCommand.execute(plugin, sender, arrayOf("NonExistent"))

            verify(exactly = 1) { messageService.send(sender, "player-not-found", "player" to "NonExistent") }
        }
    }

    // =========================================================================
    // Tab Complete Tests
    // =========================================================================

    @Nested
    @DisplayName("tabComplete()")
    inner class TabCompleteTests {

        @Test
        @DisplayName("Should return player names when has save.others permission and args size is 1")
        fun returnPlayerNamesWhenHasPermissionAndArgsSize1() {
            val sender = createMockPlayer(name = "AdminPlayer")
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Bob"),
                createMockPlayer(name = "Charlie")
            )

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getOnlinePlayers() } returns players

            val completions = saveCommand.tabComplete(plugin, sender, arrayOf(""))

            assertEquals(3, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Bob"))
            assertTrue(completions.contains("Charlie"))
        }

        @Test
        @DisplayName("Should filter player names by partial match")
        fun filterPlayerNamesByPartialMatch() {
            val sender = createMockPlayer(name = "AdminPlayer")
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Adam"),
                createMockPlayer(name = "Bob")
            )

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getOnlinePlayers() } returns players

            val completions = saveCommand.tabComplete(plugin, sender, arrayOf("A"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Adam"))
            assertFalse(completions.contains("Bob"))
        }

        @Test
        @DisplayName("Should filter player names case insensitively")
        fun filterPlayerNamesCaseInsensitively() {
            val sender = createMockPlayer(name = "AdminPlayer")
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Adam"),
                createMockPlayer(name = "Bob")
            )

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getOnlinePlayers() } returns players

            val completions = saveCommand.tabComplete(plugin, sender, arrayOf("a"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Adam"))
        }

        @Test
        @DisplayName("Should return empty list when lacking save.others permission")
        fun returnEmptyListWhenLackingPermission() {
            val sender = createMockPlayer(name = "NormalPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns false

            val completions = saveCommand.tabComplete(plugin, sender, arrayOf(""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should not call Bukkit.getOnlinePlayers when lacking permission")
        fun notCallGetOnlinePlayersWhenLackingPermission() {
            val sender = createMockPlayer(name = "NormalPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns false

            saveCommand.tabComplete(plugin, sender, arrayOf(""))

            verify(exactly = 0) { Bukkit.getOnlinePlayers() }
        }

        @Test
        @DisplayName("Should return empty list when args size is not 1")
        fun returnEmptyListWhenArgsSizeIsNot1() {
            val sender = createMockPlayer(name = "AdminPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true

            val completionsEmpty = saveCommand.tabComplete(plugin, sender, emptyArray())
            val completionsTwo = saveCommand.tabComplete(plugin, sender, arrayOf("arg1", "arg2"))

            assertTrue(completionsEmpty.isEmpty())
            assertTrue(completionsTwo.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list when no players match prefix")
        fun returnEmptyListWhenNoPlayersMatchPrefix() {
            val sender = createMockPlayer(name = "AdminPlayer")
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Bob")
            )

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getOnlinePlayers() } returns players

            val completions = saveCommand.tabComplete(plugin, sender, arrayOf("xyz"))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list when no players online")
        fun returnEmptyListWhenNoPlayersOnline() {
            val sender = createMockPlayer(name = "AdminPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getOnlinePlayers() } returns emptyList()

            val completions = saveCommand.tabComplete(plugin, sender, arrayOf(""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should work with console sender having permission")
        fun workWithConsoleSenderHavingPermission() {
            val sender = createMockCommandSender("CONSOLE")
            val players = listOf(
                createMockPlayer(name = "Player1"),
                createMockPlayer(name = "Player2")
            )

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getOnlinePlayers() } returns players

            val completions = saveCommand.tabComplete(plugin, sender, arrayOf("P"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Player1"))
            assertTrue(completions.contains("Player2"))
        }

        @Test
        @DisplayName("Should handle special characters in player names")
        fun handleSpecialCharactersInPlayerNames() {
            val sender = createMockPlayer(name = "AdminPlayer")
            val players = listOf(
                createMockPlayer(name = "Player_With_Underscore"),
                createMockPlayer(name = "Player123"),
                createMockPlayer(name = "xX_Pro_Xx")
            )

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getOnlinePlayers() } returns players

            val completions = saveCommand.tabComplete(plugin, sender, arrayOf("x"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("xX_Pro_Xx"))
        }

        @Test
        @DisplayName("Should check correct permission for tab completion")
        fun checkCorrectPermissionForTabCompletion() {
            val sender = createMockPlayer(name = "TestPlayer")

            every { sender.hasPermission(any<String>()) } returns false

            saveCommand.tabComplete(plugin, sender, arrayOf(""))

            verify(exactly = 1) { sender.hasPermission("xinventories.command.save.others") }
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
            every { sender.hasPermission("xinventories.command.save") } returns true

            val result = saveCommand.hasPermission(sender)

            assertTrue(result)
        }

        @Test
        @DisplayName("Should return false when sender lacks permission")
        fun returnFalseWhenLacksPermission() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission("xinventories.command.save") } returns false

            val result = saveCommand.hasPermission(sender)

            assertFalse(result)
        }

        @Test
        @DisplayName("Should check correct permission string")
        fun checkCorrectPermissionString() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission(any<String>()) } returns true

            saveCommand.hasPermission(sender)

            verify(exactly = 1) { sender.hasPermission("xinventories.command.save") }
        }
    }

    // =========================================================================
    // Service Manager Access Tests
    // =========================================================================

    @Nested
    @DisplayName("Service Manager Access")
    inner class ServiceManagerAccessTests {

        @Test
        @DisplayName("Should access message service through service manager")
        fun accessMessageServiceThroughServiceManager() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns true

            saveCommand.execute(plugin, player, emptyArray())

            verify(atLeast = 1) { plugin.serviceManager }
            verify(atLeast = 1) { serviceManager.messageService }
        }

        @Test
        @DisplayName("Should access inventory service through service manager")
        fun accessInventoryServiceThroughServiceManager() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns true

            saveCommand.execute(plugin, player, emptyArray())

            verify(exactly = 1) { serviceManager.inventoryService }
        }

        @Test
        @DisplayName("Should use correct service instances")
        fun useCorrectServiceInstances() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            val differentMessageService = mockk<MessageService>(relaxed = true)
            val differentInventoryService = mockk<InventoryService>(relaxed = true)

            every { serviceManager.messageService } returns differentMessageService
            every { serviceManager.inventoryService } returns differentInventoryService
            coEvery { differentInventoryService.saveInventory(player) } returns true

            saveCommand.execute(plugin, player, emptyArray())

            coVerify(exactly = 1) { differentInventoryService.saveInventory(player) }
            verify(exactly = 1) { differentMessageService.send(player, "inventory-saved-self") }
            verify(exactly = 0) { messageService.send(any(), any()) }
            coVerify(exactly = 0) { inventoryService.saveInventory(any()) }
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle player saving themselves via explicit name argument")
        fun handlePlayerSavingThemselvesViaExplicitName() = runTest {
            val player = createMockPlayer(name = "TestPlayer")

            every { player.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TestPlayer") } returns player
            coEvery { inventoryService.saveInventory(player) } returns true

            saveCommand.execute(plugin, player, arrayOf("TestPlayer"))

            // When player == sender, should use inventory-saved-self message
            verify(exactly = 1) { messageService.send(player, "inventory-saved-self") }
            verify(exactly = 0) { messageService.send(player, "inventory-saved", any()) }
        }

        @Test
        @DisplayName("Should handle multiple consecutive save commands")
        fun handleMultipleConsecutiveSaveCommands() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returns true

            repeat(5) {
                val result = saveCommand.execute(plugin, player, emptyArray())
                assertTrue(result)
            }

            coVerify(exactly = 5) { inventoryService.saveInventory(player) }
            verify(exactly = 5) { messageService.send(player, "inventory-saved-self") }
        }

        @Test
        @DisplayName("Should handle alternating success and failure")
        fun handleAlternatingSuccessAndFailure() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            coEvery { inventoryService.saveInventory(player) } returnsMany listOf(true, false, true, false)

            // First call - success
            saveCommand.execute(plugin, player, emptyArray())
            verify(exactly = 1) { messageService.send(player, "inventory-saved-self") }

            // Second call - failure
            saveCommand.execute(plugin, player, emptyArray())
            verify(exactly = 1) { messageService.send(player, "inventory-save-failed", "player" to "TestPlayer") }

            // Third call - success
            saveCommand.execute(plugin, player, emptyArray())
            verify(exactly = 2) { messageService.send(player, "inventory-saved-self") }

            // Fourth call - failure
            saveCommand.execute(plugin, player, emptyArray())
            verify(exactly = 2) { messageService.send(player, "inventory-save-failed", "player" to "TestPlayer") }
        }

        @Test
        @DisplayName("Should handle empty player name argument")
        fun handleEmptyPlayerNameArgument() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("") } returns null

            val result = saveCommand.execute(plugin, sender, arrayOf(""))

            assertTrue(result)
            verify(exactly = 1) { messageService.send(sender, "player-not-found", "player" to "") }
        }

        @Test
        @DisplayName("Should handle extra arguments beyond player name")
        fun handleExtraArgumentsBeyondPlayerName() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            // Extra arguments should be ignored
            val result = saveCommand.execute(plugin, sender, arrayOf("TargetPlayer", "extra", "args", "ignored"))

            assertTrue(result)
            coVerify(exactly = 1) { inventoryService.saveInventory(target) }
            verify(exactly = 1) { messageService.send(sender, "inventory-saved", "player" to "TargetPlayer") }
        }

        @Test
        @DisplayName("Should always return true from execute")
        fun alwaysReturnTrueFromExecute() = runTest {
            val player = createMockPlayer(name = "TestPlayer")
            val consoleSender = createMockCommandSender("CONSOLE")

            coEvery { inventoryService.saveInventory(any()) } returns true
            every { player.hasPermission("xinventories.command.save.others") } returns false

            // Player saving self
            assertTrue(saveCommand.execute(plugin, player, emptyArray()))

            // Player lacking permission for saving others
            assertTrue(saveCommand.execute(plugin, player, arrayOf("Other")))

            // Console without args
            assertTrue(saveCommand.execute(plugin, consoleSender, emptyArray()))

            // Console with valid player
            every { consoleSender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TestPlayer") } returns player
            assertTrue(saveCommand.execute(plugin, consoleSender, arrayOf("TestPlayer")))

            // Console with invalid player
            every { Bukkit.getPlayer("NonExistent") } returns null
            assertTrue(saveCommand.execute(plugin, consoleSender, arrayOf("NonExistent")))
        }

        @Test
        @DisplayName("Should handle player name with different case in Bukkit lookup")
        fun handlePlayerNameWithDifferentCaseInBukkitLookup() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("targetplayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            val result = saveCommand.execute(plugin, sender, arrayOf("targetplayer"))

            assertTrue(result)
            // Should use the actual player name from the Player object, not the argument
            verify(exactly = 1) { messageService.send(sender, "inventory-saved", "player" to "TargetPlayer") }
        }
    }

    // =========================================================================
    // Different Sender Type Tests
    // =========================================================================

    @Nested
    @DisplayName("Different Sender Types")
    inner class DifferentSenderTypeTests {

        @Test
        @DisplayName("Should work with Player sender saving self")
        fun workWithPlayerSenderSavingSelf() = runTest {
            val player = createMockPlayer(name = "PlayerSender")
            coEvery { inventoryService.saveInventory(player) } returns true

            val result = saveCommand.execute(plugin, player, emptyArray())

            assertTrue(result)
            coVerify(exactly = 1) { inventoryService.saveInventory(player) }
            verify(exactly = 1) { messageService.send(player, "inventory-saved-self") }
        }

        @Test
        @DisplayName("Should work with console CommandSender saving another player")
        fun workWithConsoleCommandSenderSavingAnother() = runTest {
            val consoleSender = createMockCommandSender("CONSOLE")
            val target = createMockPlayer(name = "TargetPlayer")

            every { consoleSender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            val result = saveCommand.execute(plugin, consoleSender, arrayOf("TargetPlayer"))

            assertTrue(result)
            coVerify(exactly = 1) { inventoryService.saveInventory(target) }
            verify(exactly = 1) { messageService.send(consoleSender, "inventory-saved", "player" to "TargetPlayer") }
        }

        @Test
        @DisplayName("Should work with different Player instances")
        fun workWithDifferentPlayerInstances() = runTest {
            val player1 = createMockPlayer(name = "Player1")
            val player2 = createMockPlayer(name = "Player2")
            coEvery { inventoryService.saveInventory(player1) } returns true
            coEvery { inventoryService.saveInventory(player2) } returns true

            saveCommand.execute(plugin, player1, emptyArray())
            saveCommand.execute(plugin, player2, emptyArray())

            coVerify(exactly = 1) { inventoryService.saveInventory(player1) }
            coVerify(exactly = 1) { inventoryService.saveInventory(player2) }
            verify(exactly = 1) { messageService.send(player1, "inventory-saved-self") }
            verify(exactly = 1) { messageService.send(player2, "inventory-saved-self") }
        }

        @Test
        @DisplayName("Should handle Player sender saving different target")
        fun handlePlayerSenderSavingDifferentTarget() = runTest {
            val sender = createMockPlayer(name = "AdminPlayer")
            val target = createMockPlayer(name = "TargetPlayer")

            every { sender.hasPermission("xinventories.command.save.others") } returns true
            every { Bukkit.getPlayer("TargetPlayer") } returns target
            coEvery { inventoryService.saveInventory(target) } returns true

            val result = saveCommand.execute(plugin, sender, arrayOf("TargetPlayer"))

            assertTrue(result)
            coVerify(exactly = 1) { inventoryService.saveInventory(target) }
            coVerify(exactly = 0) { inventoryService.saveInventory(sender) }
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
            assertTrue(saveCommand.name.isNotEmpty())
            assertTrue(saveCommand.permission.isNotEmpty())
            assertTrue(saveCommand.usage.isNotEmpty())
            assertTrue(saveCommand.description.isNotEmpty())
            // aliases can be empty but should not be null
            assertTrue(saveCommand.aliases.isEmpty() || saveCommand.aliases.isNotEmpty())
            assertFalse(saveCommand.playerOnly) // default value check
        }

        @Test
        @DisplayName("Should have non-blank name")
        fun haveNonBlankName() {
            assertTrue(saveCommand.name.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank permission")
        fun haveNonBlankPermission() {
            assertTrue(saveCommand.permission.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank usage")
        fun haveNonBlankUsage() {
            assertTrue(saveCommand.usage.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank description")
        fun haveNonBlankDescription() {
            assertTrue(saveCommand.description.isNotBlank())
        }

        @Test
        @DisplayName("Should have permission starting with xinventories")
        fun havePermissionStartingWithXinventories() {
            assertTrue(saveCommand.permission.startsWith("xinventories."))
        }

        @Test
        @DisplayName("Should have usage containing command name")
        fun haveUsageContainingCommandName() {
            assertTrue(saveCommand.usage.contains(saveCommand.name))
        }
    }
}

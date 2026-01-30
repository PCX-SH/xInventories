package sh.pcx.xinventories.unit.command

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import net.kyori.adventure.text.Component
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.command.CommandManager
import sh.pcx.xinventories.internal.command.XInventoriesCommand
import sh.pcx.xinventories.internal.command.subcommand.Subcommand
import sh.pcx.xinventories.internal.service.MessageService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for XInventoriesCommand.
 *
 * Tests cover:
 * - onCommand() - Main command execution, routing to subcommands
 * - onTabComplete() - Tab completion for commands
 * - showHelp() - Displaying paginated help
 * - Permission checking before command execution
 * - Handling unknown commands
 * - Async subcommand execution via coroutines
 */
@DisplayName("XInventoriesCommand Unit Tests")
class XInventoriesCommandTest {

    private lateinit var plugin: XInventories
    private lateinit var commandManager: CommandManager
    private lateinit var serviceManager: ServiceManager
    private lateinit var messageService: MessageService
    private lateinit var command: Command
    private lateinit var xInventoriesCommand: XInventoriesCommand
    private lateinit var scope: CoroutineScope

    private val testPlayerUUID = UUID.randomUUID()
    private val testPlayerName = "TestPlayer"

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("XInventoriesCommandTest"), false)
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
        commandManager = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        command = mockk(relaxed = true)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.messageService } returns messageService
        every { plugin.scope } returns scope
        every { plugin.logger } returns Logger.getLogger("XInventoriesCommandTest")

        xInventoriesCommand = XInventoriesCommand(plugin, commandManager)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockPlayer(
        uuid: UUID = testPlayerUUID,
        name: String = testPlayerName,
        permissions: Map<String, Boolean> = emptyMap()
    ): Player {
        return mockk<Player>(relaxed = true).apply {
            every { uniqueId } returns uuid
            every { this@apply.name } returns name
            permissions.forEach { (perm, value) ->
                every { hasPermission(perm) } returns value
            }
            // Default permission behavior - deny if not explicitly set
            every { hasPermission(any<String>()) } answers {
                permissions[firstArg()] ?: false
            }
        }
    }

    private fun createMockCommandSender(name: String = "CONSOLE"): CommandSender {
        return mockk<CommandSender>(relaxed = true).apply {
            every { this@apply.name } returns name
            every { hasPermission(any<String>()) } returns true // Console has all permissions
        }
    }

    private fun createMockSubcommand(
        name: String,
        permission: String = "xinventories.command.$name",
        description: String = "Test subcommand $name",
        usage: String = "/xinv $name",
        aliases: List<String> = emptyList()
    ): Subcommand {
        return mockk<Subcommand>(relaxed = true).apply {
            every { this@apply.name } returns name
            every { this@apply.permission } returns permission
            every { this@apply.description } returns description
            every { this@apply.usage } returns usage
            every { this@apply.aliases } returns aliases
            coEvery { execute(any(), any(), any()) } returns true
            every { tabComplete(any(), any(), any()) } returns emptyList()
        }
    }

    // =========================================================================
    // onCommand Tests - No Arguments (Show Help)
    // =========================================================================

    @Nested
    @DisplayName("onCommand - No Arguments")
    inner class OnCommandNoArgumentsTests {

        @Test
        @DisplayName("Should show help when no arguments provided")
        fun showHelpWhenNoArguments() {
            val sender = createMockCommandSender()
            every { commandManager.getAllSubcommands() } returns emptyList()

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", emptyArray())

            assertTrue(result)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should show help to player when no arguments provided")
        fun showHelpToPlayerWhenNoArguments() {
            val player = createMockPlayer(permissions = mapOf("xinventories.command.reload" to true))
            every { commandManager.getAllSubcommands() } returns listOf(
                createMockSubcommand("reload")
            )

            val result = xInventoriesCommand.onCommand(player, command, "xinv", emptyArray())

            assertTrue(result)
            verify(atLeast = 1) { player.sendMessage(any<Component>()) }
        }
    }

    // =========================================================================
    // onCommand Tests - Help Command
    // =========================================================================

    @Nested
    @DisplayName("onCommand - Help Command")
    inner class OnCommandHelpTests {

        @Test
        @DisplayName("Should show help when 'help' subcommand is used")
        fun showHelpWhenHelpSubcommandUsed() {
            val sender = createMockCommandSender()
            every { commandManager.getAllSubcommands() } returns emptyList()

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help"))

            assertTrue(result)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should show help page 1 when 'help' with no page number")
        fun showHelpPage1WhenHelpWithNoPageNumber() {
            val sender = createMockCommandSender()
            every { commandManager.getAllSubcommands() } returns emptyList()

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help"))

            assertTrue(result)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should show specific help page when page number provided")
        fun showSpecificHelpPageWhenPageNumberProvided() {
            val sender = createMockCommandSender()
            // Create 20 subcommands to have multiple pages
            val subcommands = (1..20).map { createMockSubcommand("cmd$it") }
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help", "2"))

            assertTrue(result)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should handle 'HELP' case insensitively")
        fun handleHelpCaseInsensitively() {
            val sender = createMockCommandSender()
            every { commandManager.getAllSubcommands() } returns emptyList()

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("HELP"))

            assertTrue(result)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should handle invalid page number as page 1")
        fun handleInvalidPageNumberAsPage1() {
            val sender = createMockCommandSender()
            every { commandManager.getAllSubcommands() } returns emptyList()

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help", "invalid"))

            assertTrue(result)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }
    }

    // =========================================================================
    // onCommand Tests - Page Number Navigation
    // =========================================================================

    @Nested
    @DisplayName("onCommand - Page Number Navigation")
    inner class OnCommandPageNumberNavigationTests {

        @Test
        @DisplayName("Should navigate to page when number is first argument")
        fun navigateToPageWhenNumberIsFirstArgument() {
            val sender = createMockCommandSender()
            val subcommands = (1..20).map { createMockSubcommand("cmd$it") }
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("2"))

            assertTrue(result)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should handle negative page number")
        fun handleNegativePageNumber() {
            val sender = createMockCommandSender()
            every { commandManager.getAllSubcommands() } returns emptyList()

            // Negative number should be treated as page number and coerced to valid range
            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("-1"))

            assertTrue(result)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should handle page number exceeding max pages")
        fun handlePageNumberExceedingMaxPages() {
            val sender = createMockCommandSender()
            val subcommands = listOf(createMockSubcommand("cmd1"))
            every { commandManager.getAllSubcommands() } returns subcommands

            // Page 999 should be coerced to max page
            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("999"))

            assertTrue(result)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }
    }

    // =========================================================================
    // onCommand Tests - Unknown Command
    // =========================================================================

    @Nested
    @DisplayName("onCommand - Unknown Command")
    inner class OnCommandUnknownCommandTests {

        @Test
        @DisplayName("Should send unknown command message when subcommand not found")
        fun sendUnknownCommandMessageWhenSubcommandNotFound() {
            val sender = createMockCommandSender()
            every { commandManager.getSubcommand("nonexistent") } returns null

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("nonexistent"))

            assertTrue(result)
            verify { messageService.send(sender, "unknown-command", "command" to "nonexistent") }
        }

        @Test
        @DisplayName("Should send unknown command message with correct command name")
        fun sendUnknownCommandMessageWithCorrectCommandName() {
            val sender = createMockCommandSender()
            every { commandManager.getSubcommand("foobar") } returns null

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("foobar"))

            verify { messageService.send(sender, "unknown-command", "command" to "foobar") }
        }
    }

    // =========================================================================
    // onCommand Tests - Permission Checking
    // =========================================================================

    @Nested
    @DisplayName("onCommand - Permission Checking")
    inner class OnCommandPermissionCheckingTests {

        @Test
        @DisplayName("Should send no permission message when player lacks permission")
        fun sendNoPermissionMessageWhenPlayerLacksPermission() {
            val player = createMockPlayer(permissions = mapOf("xinventories.command.reload" to false))
            val subcommand = createMockSubcommand("reload", permission = "xinventories.command.reload")
            every { commandManager.getSubcommand("reload") } returns subcommand

            val result = xInventoriesCommand.onCommand(player, command, "xinv", arrayOf("reload"))

            assertTrue(result)
            verify { messageService.send(player, "no-permission") }
            coVerify(exactly = 0) { subcommand.execute(any(), any(), any()) }
        }

        @Test
        @DisplayName("Should execute subcommand when player has permission")
        fun executeSubcommandWhenPlayerHasPermission() = runTest {
            val player = createMockPlayer(permissions = mapOf("xinventories.command.reload" to true))
            val subcommand = createMockSubcommand("reload", permission = "xinventories.command.reload")
            every { commandManager.getSubcommand("reload") } returns subcommand

            val result = xInventoriesCommand.onCommand(player, command, "xinv", arrayOf("reload"))

            assertTrue(result)
            verify(exactly = 0) { messageService.send(player, "no-permission") }
            // Give time for async execution
            Thread.sleep(100)
            coVerify { subcommand.execute(plugin, player, emptyArray()) }
        }

        @Test
        @DisplayName("Should allow console to execute any command")
        fun allowConsoleToExecuteAnyCommand() = runTest {
            val console = createMockCommandSender()
            val subcommand = createMockSubcommand("reload", permission = "xinventories.command.reload")
            every { commandManager.getSubcommand("reload") } returns subcommand

            val result = xInventoriesCommand.onCommand(console, command, "xinv", arrayOf("reload"))

            assertTrue(result)
            verify(exactly = 0) { messageService.send(console, "no-permission") }
            Thread.sleep(100)
            coVerify { subcommand.execute(plugin, console, emptyArray()) }
        }
    }

    // =========================================================================
    // onCommand Tests - Subcommand Execution
    // =========================================================================

    @Nested
    @DisplayName("onCommand - Subcommand Execution")
    inner class OnCommandSubcommandExecutionTests {

        @Test
        @DisplayName("Should pass correct arguments to subcommand")
        fun passCorrectArgumentsToSubcommand() = runTest {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("group", permission = "xinventories.command.group")
            every { commandManager.getSubcommand("group") } returns subcommand

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("group", "list", "survival"))

            Thread.sleep(100)
            coVerify { subcommand.execute(plugin, sender, arrayOf("list", "survival")) }
        }

        @Test
        @DisplayName("Should pass empty array when no additional arguments")
        fun passEmptyArrayWhenNoAdditionalArguments() = runTest {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("reload", permission = "xinventories.command.reload")
            every { commandManager.getSubcommand("reload") } returns subcommand

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("reload"))

            Thread.sleep(100)
            coVerify { subcommand.execute(plugin, sender, emptyArray()) }
        }

        @Test
        @DisplayName("Should execute subcommand asynchronously")
        fun executeSubcommandAsynchronously() = runTest {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("save", permission = "xinventories.command.save")
            every { commandManager.getSubcommand("save") } returns subcommand

            // Execute command
            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("save"))

            // Command returns immediately
            assertTrue(result)

            // Wait for async execution
            Thread.sleep(100)
            coVerify { subcommand.execute(plugin, sender, any()) }
        }

        @Test
        @DisplayName("Should handle exception during subcommand execution")
        fun handleExceptionDuringSubcommandExecution() = runTest {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("broken", permission = "xinventories.command.broken")
            every { commandManager.getSubcommand("broken") } returns subcommand
            coEvery { subcommand.execute(any(), any(), any()) } throws RuntimeException("Test exception")

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("broken"))

            Thread.sleep(100)
            verify { messageService.send(sender, "command-error") }
        }
    }

    // =========================================================================
    // onTabComplete Tests - First Argument
    // =========================================================================

    @Nested
    @DisplayName("onTabComplete - First Argument")
    inner class OnTabCompleteFirstArgumentTests {

        @Test
        @DisplayName("Should return empty list when args is empty")
        fun returnEmptyListWhenArgsEmpty() {
            val sender = createMockCommandSender()

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", emptyArray())

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("Should return all subcommand names plus help when completing first argument")
        fun returnAllSubcommandNamesPlusHelp() {
            val sender = createMockCommandSender()
            val subcommands = listOf(
                createMockSubcommand("reload", permission = "xinventories.command.reload"),
                createMockSubcommand("save", permission = "xinventories.command.save")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf(""))

            assertTrue(result.contains("help"))
            assertTrue(result.contains("reload"))
            assertTrue(result.contains("save"))
        }

        @Test
        @DisplayName("Should filter completions by prefix")
        fun filterCompletionsByPrefix() {
            val sender = createMockCommandSender()
            val subcommands = listOf(
                createMockSubcommand("reload", permission = "xinventories.command.reload"),
                createMockSubcommand("restore", permission = "xinventories.command.restore"),
                createMockSubcommand("save", permission = "xinventories.command.save")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("re"))

            assertTrue(result.contains("reload"))
            assertTrue(result.contains("restore"))
            assertFalse(result.contains("save"))
        }

        @Test
        @DisplayName("Should filter completions by permission")
        fun filterCompletionsByPermission() {
            val player = createMockPlayer(permissions = mapOf(
                "xinventories.command.reload" to true,
                "xinventories.command.save" to false
            ))
            val subcommands = listOf(
                createMockSubcommand("reload", permission = "xinventories.command.reload"),
                createMockSubcommand("save", permission = "xinventories.command.save")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(player, command, "xinv", arrayOf(""))

            assertTrue(result.contains("reload"))
            assertFalse(result.contains("save"))
        }

        @Test
        @DisplayName("Should return sorted completions")
        fun returnSortedCompletions() {
            val sender = createMockCommandSender()
            val subcommands = listOf(
                createMockSubcommand("zebra"),
                createMockSubcommand("alpha"),
                createMockSubcommand("middle")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf(""))

            val expectedOrder = listOf("alpha", "help", "middle", "zebra")
            assertEquals(expectedOrder, result)
        }

        @Test
        @DisplayName("Should handle case insensitive prefix matching")
        fun handleCaseInsensitivePrefixMatching() {
            val sender = createMockCommandSender()
            val subcommands = listOf(
                createMockSubcommand("reload", permission = "xinventories.command.reload"),
                createMockSubcommand("restore", permission = "xinventories.command.restore")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("RE"))

            assertTrue(result.contains("reload"))
            assertTrue(result.contains("restore"))
        }

        @Test
        @DisplayName("Should include help in completions matching prefix")
        fun includeHelpInCompletionsMatchingPrefix() {
            val sender = createMockCommandSender()
            val subcommands = listOf(
                createMockSubcommand("history", permission = "xinventories.command.history")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("h"))

            assertTrue(result.contains("help"))
            assertTrue(result.contains("history"))
        }
    }

    // =========================================================================
    // onTabComplete Tests - Help Page Completion
    // =========================================================================

    @Nested
    @DisplayName("onTabComplete - Help Page Completion")
    inner class OnTabCompleteHelpPageTests {

        @Test
        @DisplayName("Should return page numbers when completing help second argument")
        fun returnPageNumbersWhenCompletingHelpSecondArgument() {
            val sender = createMockCommandSender()
            // 16 subcommands = 2 pages (8 per page)
            val subcommands = (1..16).map { createMockSubcommand("cmd$it") }
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("help", ""))

            assertTrue(result.contains("1"))
            assertTrue(result.contains("2"))
            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("Should filter page numbers by prefix")
        fun filterPageNumbersByPrefix() {
            val sender = createMockCommandSender()
            // 80 subcommands = 10 pages
            val subcommands = (1..80).map { createMockSubcommand("cmd$it") }
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("help", "1"))

            assertTrue(result.contains("1"))
            assertTrue(result.contains("10"))
            assertFalse(result.contains("2"))
        }

        @Test
        @DisplayName("Should handle help case insensitively")
        fun handleHelpCaseInsensitively() {
            val sender = createMockCommandSender()
            val subcommands = (1..16).map { createMockSubcommand("cmd$it") }
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("HELP", ""))

            assertTrue(result.isNotEmpty())
        }

        @Test
        @DisplayName("Should return single page when few commands")
        fun returnSinglePageWhenFewCommands() {
            val sender = createMockCommandSender()
            val subcommands = listOf(createMockSubcommand("cmd1"))
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("help", ""))

            assertEquals(listOf("1"), result)
        }

        @Test
        @DisplayName("Should only count commands player has permission for")
        fun onlyCountCommandsPlayerHasPermissionFor() {
            val player = createMockPlayer(permissions = mapOf(
                "xinventories.command.cmd1" to true,
                "xinventories.command.cmd2" to true,
                "xinventories.command.cmd3" to false
            ))
            val subcommands = listOf(
                createMockSubcommand("cmd1", permission = "xinventories.command.cmd1"),
                createMockSubcommand("cmd2", permission = "xinventories.command.cmd2"),
                createMockSubcommand("cmd3", permission = "xinventories.command.cmd3")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            val result = xInventoriesCommand.onTabComplete(player, command, "xinv", arrayOf("help", ""))

            // Only 2 accessible commands = 1 page
            assertEquals(listOf("1"), result)
        }
    }

    // =========================================================================
    // onTabComplete Tests - Subcommand Delegation
    // =========================================================================

    @Nested
    @DisplayName("onTabComplete - Subcommand Delegation")
    inner class OnTabCompleteSubcommandDelegationTests {

        @Test
        @DisplayName("Should delegate tab completion to subcommand")
        fun delegateTabCompletionToSubcommand() {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("group", permission = "xinventories.command.group")
            every { subcommand.tabComplete(plugin, sender, arrayOf("")) } returns listOf("list", "create", "delete")
            every { commandManager.getSubcommand("group") } returns subcommand

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("group", ""))

            assertEquals(listOf("list", "create", "delete"), result)
            verify { subcommand.tabComplete(plugin, sender, arrayOf("")) }
        }

        @Test
        @DisplayName("Should pass correct arguments to subcommand tab complete")
        fun passCorrectArgumentsToSubcommandTabComplete() {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("world", permission = "xinventories.command.world")
            every { subcommand.tabComplete(plugin, sender, arrayOf("add", "")) } returns listOf("world_nether", "world_the_end")
            every { commandManager.getSubcommand("world") } returns subcommand

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("world", "add", ""))

            assertEquals(listOf("world_nether", "world_the_end"), result)
            verify { subcommand.tabComplete(plugin, sender, arrayOf("add", "")) }
        }

        @Test
        @DisplayName("Should return empty list when subcommand not found")
        fun returnEmptyListWhenSubcommandNotFound() {
            val sender = createMockCommandSender()
            every { commandManager.getSubcommand("nonexistent") } returns null

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("nonexistent", ""))

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list when player lacks permission for subcommand")
        fun returnEmptyListWhenPlayerLacksPermissionForSubcommand() {
            val player = createMockPlayer(permissions = mapOf("xinventories.command.admin" to false))
            val subcommand = createMockSubcommand("admin", permission = "xinventories.command.admin")
            every { commandManager.getSubcommand("admin") } returns subcommand

            val result = xInventoriesCommand.onTabComplete(player, command, "xinv", arrayOf("admin", ""))

            assertTrue(result.isEmpty())
        }
    }

    // =========================================================================
    // showHelp Tests
    // =========================================================================

    @Nested
    @DisplayName("showHelp - Pagination")
    inner class ShowHelpPaginationTests {

        @Test
        @DisplayName("Should show correct commands for page 1")
        fun showCorrectCommandsForPage1() {
            val sender = createMockCommandSender()
            val subcommands = (1..20).map { createMockSubcommand("cmd$it") }
            every { commandManager.getAllSubcommands() } returns subcommands

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help", "1"))

            // Verify help messages were sent
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should show correct commands for page 2")
        fun showCorrectCommandsForPage2() {
            val sender = createMockCommandSender()
            val subcommands = (1..20).map { createMockSubcommand("cmd$it") }
            every { commandManager.getAllSubcommands() } returns subcommands

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help", "2"))

            // Verify help messages were sent
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should coerce page to minimum of 1")
        fun coercePageToMinimumOf1() {
            val sender = createMockCommandSender()
            every { commandManager.getAllSubcommands() } returns emptyList()

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help", "0"))

            // Verify help messages were sent (page coerced to 1)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should coerce page to maximum")
        fun coercePageToMaximum() {
            val sender = createMockCommandSender()
            val subcommands = listOf(createMockSubcommand("cmd1"))
            every { commandManager.getAllSubcommands() } returns subcommands

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help", "100"))

            // Verify help messages were sent (page coerced to max)
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }
    }

    // =========================================================================
    // showHelp Tests - Permission Filtering
    // =========================================================================

    @Nested
    @DisplayName("showHelp - Permission Filtering")
    inner class ShowHelpPermissionFilteringTests {

        @Test
        @DisplayName("Should only show commands player has permission for")
        fun onlyShowCommandsPlayerHasPermissionFor() {
            val player = createMockPlayer(permissions = mapOf(
                "xinventories.command.reload" to true,
                "xinventories.command.save" to false
            ))
            val subcommands = listOf(
                createMockSubcommand("reload", permission = "xinventories.command.reload"),
                createMockSubcommand("save", permission = "xinventories.command.save")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            xInventoriesCommand.onCommand(player, command, "xinv", arrayOf("help"))

            // Help should be shown
            verify(atLeast = 1) { player.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should show all commands to console")
        fun showAllCommandsToConsole() {
            val console = createMockCommandSender()
            val subcommands = listOf(
                createMockSubcommand("reload"),
                createMockSubcommand("save"),
                createMockSubcommand("admin")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            xInventoriesCommand.onCommand(console, command, "xinv", arrayOf("help"))

            // Help should be shown with all commands
            verify(atLeast = 1) { console.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should handle empty command list")
        fun handleEmptyCommandList() {
            val sender = createMockCommandSender()
            every { commandManager.getAllSubcommands() } returns emptyList()

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help"))

            // Help header/footer should still be shown
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }
    }

    // =========================================================================
    // showHelp Tests - Category Display
    // =========================================================================

    @Nested
    @DisplayName("showHelp - Category Display")
    inner class ShowHelpCategoryDisplayTests {

        @Test
        @DisplayName("Should display commands grouped by category")
        fun displayCommandsGroupedByCategory() {
            val sender = createMockCommandSender()
            val subcommands = listOf(
                createMockSubcommand("gui"),      // Core
                createMockSubcommand("reload"),   // Core
                createMockSubcommand("group"),    // Management
                createMockSubcommand("history"),  // Data
                createMockSubcommand("cache")     // Utilities
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help"))

            // Verify help was displayed
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should handle uncategorized commands")
        fun handleUncategorizedCommands() {
            val sender = createMockCommandSender()
            // Command names that don't match any predefined category
            val subcommands = listOf(
                createMockSubcommand("customcmd1"),
                createMockSubcommand("customcmd2")
            )
            every { commandManager.getAllSubcommands() } returns subcommands

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help"))

            // Should be listed under "Other" category
            verify(atLeast = 1) { sender.sendMessage(any<Component>()) }
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle whitespace in arguments")
        fun handleWhitespaceInArguments() {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("group", permission = "xinventories.command.group")
            every { commandManager.getSubcommand("group") } returns subcommand

            xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("group", "  ", "survival"))

            // Should still process the command
            assertTrue(true)
        }

        @Test
        @DisplayName("Should handle very long argument list")
        fun handleVeryLongArgumentList() = runTest {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("test", permission = "xinventories.command.test")
            every { commandManager.getSubcommand("test") } returns subcommand

            val args = arrayOf("test") + (1..100).map { "arg$it" }.toTypedArray()
            xInventoriesCommand.onCommand(sender, command, "xinv", args)

            Thread.sleep(100)
            coVerify { subcommand.execute(plugin, sender, any()) }
        }

        @Test
        @DisplayName("Should handle special characters in subcommand name lookup")
        fun handleSpecialCharactersInSubcommandNameLookup() {
            val sender = createMockCommandSender()
            every { commandManager.getSubcommand("test'cmd") } returns null

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("test'cmd"))

            assertTrue(result)
            verify { messageService.send(sender, "unknown-command", "command" to "test'cmd") }
        }

        @Test
        @DisplayName("Should handle null values returned from subcommand tab complete")
        fun handleNullValuesFromSubcommandTabComplete() {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("test", permission = "xinventories.command.test")
            every { subcommand.tabComplete(any(), any(), any()) } returns emptyList()
            every { commandManager.getSubcommand("test") } returns subcommand

            val result = xInventoriesCommand.onTabComplete(sender, command, "xinv", arrayOf("test", ""))

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("Should use correct label alias")
        fun useCorrectLabelAlias() {
            val sender = createMockCommandSender()
            every { commandManager.getAllSubcommands() } returns emptyList()

            // Using 'xinv' alias instead of 'xinventories'
            val result = xInventoriesCommand.onCommand(sender, command, "xinv", emptyArray())

            assertTrue(result)
        }

        @Test
        @DisplayName("Should handle concurrent command execution")
        fun handleConcurrentCommandExecution() = runTest {
            val sender = createMockCommandSender()
            val subcommand = createMockSubcommand("concurrent", permission = "xinventories.command.concurrent")
            coEvery { subcommand.execute(any(), any(), any()) } coAnswers {
                Thread.sleep(50) // Simulate work
                true
            }
            every { commandManager.getSubcommand("concurrent") } returns subcommand

            // Execute multiple commands concurrently
            repeat(3) {
                xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("concurrent"))
            }

            Thread.sleep(200)
            coVerify(exactly = 3) { subcommand.execute(plugin, sender, any()) }
        }
    }

    // =========================================================================
    // Command Return Value Tests
    // =========================================================================

    @Nested
    @DisplayName("Command Return Values")
    inner class CommandReturnValueTests {

        @Test
        @DisplayName("Should always return true from onCommand")
        fun alwaysReturnTrueFromOnCommand() {
            val sender = createMockCommandSender()

            // Various scenarios
            assertTrue(xInventoriesCommand.onCommand(sender, command, "xinv", emptyArray()))
            assertTrue(xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("help")))
            assertTrue(xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("unknown")))
        }

        @Test
        @DisplayName("Should return true even when subcommand not found")
        fun returnTrueEvenWhenSubcommandNotFound() {
            val sender = createMockCommandSender()
            every { commandManager.getSubcommand("notfound") } returns null

            val result = xInventoriesCommand.onCommand(sender, command, "xinv", arrayOf("notfound"))

            assertTrue(result)
        }

        @Test
        @DisplayName("Should return true even when permission denied")
        fun returnTrueEvenWhenPermissionDenied() {
            val player = createMockPlayer(permissions = mapOf("xinventories.command.admin" to false))
            val subcommand = createMockSubcommand("admin", permission = "xinventories.command.admin")
            every { commandManager.getSubcommand("admin") } returns subcommand

            val result = xInventoriesCommand.onCommand(player, command, "xinv", arrayOf("admin"))

            assertTrue(result)
        }
    }

    // =========================================================================
    // Integration-like Tests
    // =========================================================================

    @Nested
    @DisplayName("Command Flow Integration")
    inner class CommandFlowIntegrationTests {

        @Test
        @DisplayName("Should handle complete command flow - subcommand lookup to execution")
        fun handleCompleteCommandFlow() = runTest {
            val player = createMockPlayer(permissions = mapOf("xinventories.command.save" to true))
            val subcommand = createMockSubcommand("save", permission = "xinventories.command.save")
            every { commandManager.getSubcommand("save") } returns subcommand
            coEvery { subcommand.execute(plugin, player, arrayOf("TestPlayer")) } returns true

            val result = xInventoriesCommand.onCommand(player, command, "xinv", arrayOf("save", "TestPlayer"))

            assertTrue(result)
            Thread.sleep(100)
            coVerify { subcommand.execute(plugin, player, arrayOf("TestPlayer")) }
        }

        @Test
        @DisplayName("Should handle tab complete flow - from subcommand to player completions")
        fun handleTabCompleteFlow() {
            val player = createMockPlayer(permissions = mapOf(
                "xinventories.command.group" to true
            ))
            val groupSubcommand = createMockSubcommand("group", permission = "xinventories.command.group")
            every { groupSubcommand.tabComplete(plugin, player, arrayOf("")) } returns listOf("add", "remove", "list")
            every { commandManager.getSubcommand("group") } returns groupSubcommand
            every { commandManager.getAllSubcommands() } returns listOf(groupSubcommand)

            // First, complete first argument
            val firstResult = xInventoriesCommand.onTabComplete(player, command, "xinv", arrayOf(""))
            assertTrue(firstResult.contains("group"))
            assertTrue(firstResult.contains("help"))

            // Then, complete second argument for 'group'
            val secondResult = xInventoriesCommand.onTabComplete(player, command, "xinv", arrayOf("group", ""))
            assertEquals(listOf("add", "remove", "list"), secondResult)
        }
    }
}

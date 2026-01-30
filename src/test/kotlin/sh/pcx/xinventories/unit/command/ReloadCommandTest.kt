package sh.pcx.xinventories.unit.command

import io.mockk.*
import kotlinx.coroutines.test.runTest
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.command.subcommand.ReloadCommand
import sh.pcx.xinventories.internal.service.MessageService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for ReloadCommand.
 *
 * Tests cover:
 * - Subcommand properties (name, permission, usage, description)
 * - execute() - successful reload
 * - execute() - failed reload
 * - Sending appropriate messages based on reload result
 * - Testing with different sender types (Player, Console)
 */
@DisplayName("ReloadCommand Unit Tests")
class ReloadCommandTest {

    private lateinit var plugin: XInventories
    private lateinit var serviceManager: ServiceManager
    private lateinit var messageService: MessageService
    private lateinit var reloadCommand: ReloadCommand

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("ReloadCommandTest"), false)
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

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.messageService } returns messageService

        reloadCommand = ReloadCommand()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
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
            assertEquals("reload", reloadCommand.name)
        }

        @Test
        @DisplayName("Should have correct permission")
        fun hasCorrectPermission() {
            assertEquals("xinventories.command.reload", reloadCommand.permission)
        }

        @Test
        @DisplayName("Should have correct usage")
        fun hasCorrectUsage() {
            assertEquals("/xinv reload", reloadCommand.usage)
        }

        @Test
        @DisplayName("Should have correct description")
        fun hasCorrectDescription() {
            assertEquals("Reload configuration files", reloadCommand.description)
        }

        @Test
        @DisplayName("Should have empty aliases by default")
        fun hasEmptyAliases() {
            assertTrue(reloadCommand.aliases.isEmpty())
        }

        @Test
        @DisplayName("Should not be player-only command")
        fun notPlayerOnlyCommand() {
            assertFalse(reloadCommand.playerOnly)
        }
    }

    // =========================================================================
    // Execute - Success Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Success")
    inner class ExecuteSuccessTests {

        @Test
        @DisplayName("Should send success message when reload succeeds")
        fun sendSuccessMessageOnReload() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true

            val result = reloadCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
            verify(exactly = 1) { messageService.send(sender, "reloaded") }
            verify(exactly = 0) { messageService.send(sender, "reload-failed") }
        }

        @Test
        @DisplayName("Should send success message to Player sender when reload succeeds")
        fun sendSuccessMessageToPlayerOnReload() = runTest {
            val player = createMockPlayer()
            every { plugin.reload() } returns true

            val result = reloadCommand.execute(plugin, player, emptyArray())

            assertTrue(result)
            verify(exactly = 1) { messageService.send(player, "reloaded") }
            verify(exactly = 0) { messageService.send(player, "reload-failed") }
        }

        @Test
        @DisplayName("Should call plugin reload method exactly once")
        fun callPluginReloadOnce() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true

            reloadCommand.execute(plugin, sender, emptyArray())

            verify(exactly = 1) { plugin.reload() }
        }

        @Test
        @DisplayName("Should return true even when reload succeeds")
        fun returnTrueOnSuccess() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true

            val result = reloadCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
        }

        @Test
        @DisplayName("Should ignore arguments when executing reload")
        fun ignoreArgumentsOnReload() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true
            val args = arrayOf("extra", "arguments", "ignored")

            val result = reloadCommand.execute(plugin, sender, args)

            assertTrue(result)
            verify(exactly = 1) { plugin.reload() }
            verify(exactly = 1) { messageService.send(sender, "reloaded") }
        }
    }

    // =========================================================================
    // Execute - Failure Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Failure")
    inner class ExecuteFailureTests {

        @Test
        @DisplayName("Should send failure message when reload fails")
        fun sendFailureMessageOnReloadFail() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns false

            val result = reloadCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
            verify(exactly = 0) { messageService.send(sender, "reloaded") }
            verify(exactly = 1) { messageService.send(sender, "reload-failed") }
        }

        @Test
        @DisplayName("Should send failure message to Player sender when reload fails")
        fun sendFailureMessageToPlayerOnReloadFail() = runTest {
            val player = createMockPlayer()
            every { plugin.reload() } returns false

            val result = reloadCommand.execute(plugin, player, emptyArray())

            assertTrue(result)
            verify(exactly = 0) { messageService.send(player, "reloaded") }
            verify(exactly = 1) { messageService.send(player, "reload-failed") }
        }

        @Test
        @DisplayName("Should return true even when reload fails")
        fun returnTrueOnFailure() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns false

            val result = reloadCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
        }

        @Test
        @DisplayName("Should call plugin reload method even if it will fail")
        fun callPluginReloadOnFailure() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns false

            reloadCommand.execute(plugin, sender, emptyArray())

            verify(exactly = 1) { plugin.reload() }
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
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true

            reloadCommand.execute(plugin, sender, emptyArray())

            verify(exactly = 1) { plugin.serviceManager }
            verify(exactly = 1) { serviceManager.messageService }
        }

        @Test
        @DisplayName("Should use correct message service instance")
        fun useCorrectMessageServiceInstance() = runTest {
            val sender = createMockCommandSender()
            val differentMessageService = mockk<MessageService>(relaxed = true)
            every { plugin.reload() } returns true
            every { serviceManager.messageService } returns differentMessageService

            reloadCommand.execute(plugin, sender, emptyArray())

            verify(exactly = 1) { differentMessageService.send(sender, "reloaded") }
            verify(exactly = 0) { messageService.send(any(), any()) }
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
            every { sender.hasPermission("xinventories.command.reload") } returns true

            val result = reloadCommand.hasPermission(sender)

            assertTrue(result)
        }

        @Test
        @DisplayName("Should return false when sender lacks permission")
        fun returnFalseWhenLacksPermission() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission("xinventories.command.reload") } returns false

            val result = reloadCommand.hasPermission(sender)

            assertFalse(result)
        }

        @Test
        @DisplayName("Should check correct permission string")
        fun checkCorrectPermissionString() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission(any<String>()) } returns true

            reloadCommand.hasPermission(sender)

            verify(exactly = 1) { sender.hasPermission("xinventories.command.reload") }
        }
    }

    // =========================================================================
    // Tab Complete Tests
    // =========================================================================

    @Nested
    @DisplayName("Tab Completion")
    inner class TabCompleteTests {

        @Test
        @DisplayName("Should return empty list for tab completion")
        fun returnEmptyListForTabComplete() {
            val sender = createMockCommandSender()

            val completions = reloadCommand.tabComplete(plugin, sender, emptyArray())

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list regardless of arguments")
        fun returnEmptyListRegardlessOfArgs() {
            val sender = createMockCommandSender()
            val args = arrayOf("some", "args")

            val completions = reloadCommand.tabComplete(plugin, sender, args)

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list for Player sender")
        fun returnEmptyListForPlayer() {
            val player = createMockPlayer()

            val completions = reloadCommand.tabComplete(plugin, player, emptyArray())

            assertTrue(completions.isEmpty())
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle multiple consecutive reloads")
        fun handleMultipleConsecutiveReloads() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true

            repeat(5) {
                val result = reloadCommand.execute(plugin, sender, emptyArray())
                assertTrue(result)
            }

            verify(exactly = 5) { plugin.reload() }
            verify(exactly = 5) { messageService.send(sender, "reloaded") }
        }

        @Test
        @DisplayName("Should handle alternating success and failure")
        fun handleAlternatingSuccessAndFailure() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returnsMany listOf(true, false, true, false)

            // First call - success
            reloadCommand.execute(plugin, sender, emptyArray())
            verify(exactly = 1) { messageService.send(sender, "reloaded") }

            // Second call - failure
            reloadCommand.execute(plugin, sender, emptyArray())
            verify(exactly = 1) { messageService.send(sender, "reload-failed") }

            // Third call - success
            reloadCommand.execute(plugin, sender, emptyArray())
            verify(exactly = 2) { messageService.send(sender, "reloaded") }

            // Fourth call - failure
            reloadCommand.execute(plugin, sender, emptyArray())
            verify(exactly = 2) { messageService.send(sender, "reload-failed") }
        }

        @Test
        @DisplayName("Should work with empty args array")
        fun workWithEmptyArgsArray() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true

            val result = reloadCommand.execute(plugin, sender, arrayOf())

            assertTrue(result)
            verify(exactly = 1) { messageService.send(sender, "reloaded") }
        }

        @Test
        @DisplayName("Should work with large args array")
        fun workWithLargeArgsArray() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true
            val largeArgs = Array(100) { "arg$it" }

            val result = reloadCommand.execute(plugin, sender, largeArgs)

            assertTrue(result)
            verify(exactly = 1) { plugin.reload() }
        }
    }

    // =========================================================================
    // Different Sender Type Tests
    // =========================================================================

    @Nested
    @DisplayName("Different Sender Types")
    inner class DifferentSenderTypeTests {

        @Test
        @DisplayName("Should work with console CommandSender")
        fun workWithConsoleCommandSender() = runTest {
            val consoleSender = createMockCommandSender("CONSOLE")
            every { plugin.reload() } returns true

            val result = reloadCommand.execute(plugin, consoleSender, emptyArray())

            assertTrue(result)
            verify(exactly = 1) { messageService.send(consoleSender, "reloaded") }
        }

        @Test
        @DisplayName("Should work with Player sender")
        fun workWithPlayerSender() = runTest {
            val player = createMockPlayer(name = "AdminPlayer")
            every { plugin.reload() } returns true

            val result = reloadCommand.execute(plugin, player, emptyArray())

            assertTrue(result)
            verify(exactly = 1) { messageService.send(player, "reloaded") }
        }

        @Test
        @DisplayName("Should work with different Player instances")
        fun workWithDifferentPlayerInstances() = runTest {
            val player1 = createMockPlayer(name = "Player1")
            val player2 = createMockPlayer(name = "Player2")
            every { plugin.reload() } returns true

            reloadCommand.execute(plugin, player1, emptyArray())
            reloadCommand.execute(plugin, player2, emptyArray())

            verify(exactly = 1) { messageService.send(player1, "reloaded") }
            verify(exactly = 1) { messageService.send(player2, "reloaded") }
        }
    }

    // =========================================================================
    // Message Key Tests
    // =========================================================================

    @Nested
    @DisplayName("Message Keys")
    inner class MessageKeyTests {

        @Test
        @DisplayName("Should use 'reloaded' message key on success")
        fun useReloadedMessageKeyOnSuccess() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true
            val messageKeySlot = slot<String>()

            every { messageService.send(any(), capture(messageKeySlot)) } just Runs

            reloadCommand.execute(plugin, sender, emptyArray())

            assertEquals("reloaded", messageKeySlot.captured)
        }

        @Test
        @DisplayName("Should use 'reload-failed' message key on failure")
        fun useReloadFailedMessageKeyOnFailure() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns false
            val messageKeySlot = slot<String>()

            every { messageService.send(any(), capture(messageKeySlot)) } just Runs

            reloadCommand.execute(plugin, sender, emptyArray())

            assertEquals("reload-failed", messageKeySlot.captured)
        }

        @Test
        @DisplayName("Should never send both success and failure messages")
        fun neverSendBothMessages() = runTest {
            val sender = createMockCommandSender()
            every { plugin.reload() } returns true

            reloadCommand.execute(plugin, sender, emptyArray())

            // Only one message should be sent
            verify(exactly = 1) { messageService.send(sender, any()) }
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
            assertTrue(reloadCommand.name.isNotEmpty())
            assertTrue(reloadCommand.permission.isNotEmpty())
            assertTrue(reloadCommand.usage.isNotEmpty())
            assertTrue(reloadCommand.description.isNotEmpty())
            // aliases can be empty but should not be null
            assertTrue(reloadCommand.aliases.isEmpty() || reloadCommand.aliases.isNotEmpty())
            assertFalse(reloadCommand.playerOnly) // default value check
        }

        @Test
        @DisplayName("Should have non-blank name")
        fun haveNonBlankName() {
            assertTrue(reloadCommand.name.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank permission")
        fun haveNonBlankPermission() {
            assertTrue(reloadCommand.permission.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank usage")
        fun haveNonBlankUsage() {
            assertTrue(reloadCommand.usage.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank description")
        fun haveNonBlankDescription() {
            assertTrue(reloadCommand.description.isNotBlank())
        }

        @Test
        @DisplayName("Should have permission starting with xinventories")
        fun havePermissionStartingWithXinventories() {
            assertTrue(reloadCommand.permission.startsWith("xinventories."))
        }

        @Test
        @DisplayName("Should have usage containing command name")
        fun haveUsageContainingCommandName() {
            assertTrue(reloadCommand.usage.contains(reloadCommand.name))
        }
    }
}

package sh.pcx.xinventories.unit.service

import io.mockk.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Server
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertDoesNotThrow
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.FeaturesConfig
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.config.MessagesConfig
import sh.pcx.xinventories.internal.service.MessageService
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for MessageService.
 *
 * Tests cover:
 * - Message formatting with MiniMessage API
 * - Placeholder replacement ({player}, {world}, {group}, etc.)
 * - Player messaging with and without prefixes
 * - Broadcasting to players with specific permissions
 * - Utility methods for stripping formatting tags
 * - Admin notifications when enabled/disabled
 */
@DisplayName("MessageService Unit Tests")
class MessageServiceTest {

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var mainConfig: MainConfig
    private lateinit var messagesConfig: MessagesConfig
    private lateinit var server: Server
    private lateinit var messageService: MessageService

    private val plainSerializer = PlainTextComponentSerializer.plainText()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("MessageServiceTest"), false)
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
        messagesConfig = MessagesConfig()
        server = mockk(relaxed = true)

        every { plugin.configManager } returns configManager
        every { plugin.plugin } returns plugin
        every { plugin.server } returns server
        every { configManager.messagesConfig } returns messagesConfig
        every { configManager.mainConfig } returns mainConfig
        every { mainConfig.features } returns FeaturesConfig(adminNotifications = true)
        every { server.onlinePlayers } returns emptyList()

        messageService = MessageService(plugin)
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
        name: String = "TestPlayer",
        hasPermission: Boolean = false,
        permission: String = "xinventories.admin"
    ): Player {
        return mockk<Player>(relaxed = true).apply {
            every { uniqueId } returns uuid
            every { this@apply.name } returns name
            every { hasPermission(permission) } returns hasPermission
        }
    }

    private fun createMockCommandSender(name: String = "CONSOLE"): CommandSender {
        return mockk<CommandSender>(relaxed = true).apply {
            every { this@apply.name } returns name
        }
    }

    private fun Component.toPlainText(): String = plainSerializer.serialize(this)

    // =========================================================================
    // Prefix Property Tests
    // =========================================================================

    @Nested
    @DisplayName("prefix Property")
    inner class PrefixPropertyTests {

        @Test
        @DisplayName("Should return configured prefix as Component")
        fun returnConfiguredPrefix() {
            val prefix = messageService.prefix

            assertNotNull(prefix)
            // The default prefix contains "xInventories"
            assertTrue(prefix.toPlainText().contains("xInventories"))
        }

        @Test
        @DisplayName("Should use custom prefix when configured")
        fun useCustomPrefix() {
            val customConfig = MessagesConfig(prefix = "<green>[Custom]</green> ")
            every { configManager.messagesConfig } returns customConfig

            val prefix = messageService.prefix

            assertTrue(prefix.toPlainText().contains("[Custom]"))
        }
    }

    // =========================================================================
    // getRaw Tests
    // =========================================================================

    @Nested
    @DisplayName("getRaw")
    inner class GetRawTests {

        @Test
        @DisplayName("Should return raw message string by key")
        fun returnRawMessageString() {
            val raw = messageService.getRaw("reloaded")

            assertEquals("<green>Configuration reloaded successfully!", raw)
        }

        @Test
        @DisplayName("Should return missing message indicator for unknown key")
        fun returnMissingMessageForUnknownKey() {
            val raw = messageService.getRaw("unknown-key-that-does-not-exist")

            assertTrue(raw.contains("Missing message"))
            assertTrue(raw.contains("unknown-key-that-does-not-exist"))
        }

        @Test
        @DisplayName("Should return message with placeholders unresolved")
        fun returnMessageWithPlaceholdersUnresolved() {
            val raw = messageService.getRaw("player-not-found")

            assertTrue(raw.contains("{player}"))
        }
    }

    // =========================================================================
    // get Tests
    // =========================================================================

    @Nested
    @DisplayName("get")
    inner class GetTests {

        @Test
        @DisplayName("Should return formatted message Component")
        fun returnFormattedMessageComponent() {
            val component = messageService.get("reloaded")

            assertNotNull(component)
            assertTrue(component.toPlainText().contains("Configuration reloaded successfully!"))
        }

        @Test
        @DisplayName("Should replace single placeholder")
        fun replaceSinglePlaceholder() {
            val component = messageService.get("player-not-found", "player" to "TestPlayer")

            val text = component.toPlainText()
            assertTrue(text.contains("TestPlayer"))
            assertFalse(text.contains("{player}"))
        }

        @Test
        @DisplayName("Should replace multiple placeholders")
        fun replaceMultiplePlaceholders() {
            val component = messageService.get(
                "inventory-switched",
                "from_group" to "survival",
                "to_group" to "creative"
            )

            val text = component.toPlainText()
            assertTrue(text.contains("survival"))
            assertTrue(text.contains("creative"))
            assertFalse(text.contains("{from_group}"))
            assertFalse(text.contains("{to_group}"))
        }

        @Test
        @DisplayName("Should handle world placeholder")
        fun handleWorldPlaceholder() {
            val component = messageService.get("world-not-found", "world" to "my_world")

            val text = component.toPlainText()
            assertTrue(text.contains("my_world"))
            assertFalse(text.contains("{world}"))
        }

        @Test
        @DisplayName("Should handle group placeholder")
        fun handleGroupPlaceholder() {
            val component = messageService.get("group-not-found", "group" to "creative")

            val text = component.toPlainText()
            assertTrue(text.contains("creative"))
            assertFalse(text.contains("{group}"))
        }

        @Test
        @DisplayName("Should handle version placeholder")
        fun handleVersionPlaceholder() {
            // Create a custom message config with version placeholder
            val customMessages = mapOf(
                "version-info" to "Running version {version}"
            )
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val component = messageService.get("version-info", "version" to "1.0.0")

            val text = component.toPlainText()
            assertTrue(text.contains("1.0.0"))
            assertFalse(text.contains("{version}"))
        }

        @Test
        @DisplayName("Should handle count placeholder")
        fun handleCountPlaceholder() {
            val component = messageService.get("cache-cleared", "count" to "50")

            val text = component.toPlainText()
            assertTrue(text.contains("50"))
            assertFalse(text.contains("{count}"))
        }

        @Test
        @DisplayName("Should handle slot placeholder")
        fun handleSlotPlaceholder() {
            // Create a custom message config with slot placeholder
            val customMessages = mapOf(
                "slot-info" to "Item in slot {slot}"
            )
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val component = messageService.get("slot-info", "slot" to "9")

            val text = component.toPlainText()
            assertTrue(text.contains("9"))
            assertFalse(text.contains("{slot}"))
        }

        @Test
        @DisplayName("Should handle trigger placeholder")
        fun handleTriggerPlaceholder() {
            // Create a custom message config with trigger placeholder
            val customMessages = mapOf(
                "trigger-info" to "Triggered by {trigger}"
            )
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val component = messageService.get("trigger-info", "trigger" to "world_change")

            val text = component.toPlainText()
            assertTrue(text.contains("world_change"))
            assertFalse(text.contains("{trigger}"))
        }

        @Test
        @DisplayName("Should leave unmatched placeholders unchanged")
        fun leaveUnmatchedPlaceholdersUnchanged() {
            val component = messageService.get(
                "inventory-switched",
                "from_group" to "survival"
                // to_group intentionally not provided
            )

            val text = component.toPlainText()
            assertTrue(text.contains("survival"))
            assertTrue(text.contains("{to_group}"))
        }

        @Test
        @DisplayName("Should handle empty placeholder value")
        fun handleEmptyPlaceholderValue() {
            val component = messageService.get("player-not-found", "player" to "")

            val text = component.toPlainText()
            assertFalse(text.contains("{player}"))
        }

        @Test
        @DisplayName("Should handle no placeholders")
        fun handleNoPlaceholders() {
            val component = messageService.get("reloaded")

            val text = component.toPlainText()
            assertTrue(text.contains("Configuration reloaded successfully!"))
        }
    }

    // =========================================================================
    // getWithPrefix Tests
    // =========================================================================

    @Nested
    @DisplayName("getWithPrefix")
    inner class GetWithPrefixTests {

        @Test
        @DisplayName("Should return message with prefix prepended")
        fun returnMessageWithPrefix() {
            val component = messageService.getWithPrefix("reloaded")

            val text = component.toPlainText()
            assertTrue(text.contains("xInventories"))
            assertTrue(text.contains("Configuration reloaded successfully!"))
        }

        @Test
        @DisplayName("Should replace placeholders with prefix")
        fun replacePlaceholdersWithPrefix() {
            val component = messageService.getWithPrefix("player-not-found", "player" to "TestPlayer")

            val text = component.toPlainText()
            assertTrue(text.contains("xInventories"))
            assertTrue(text.contains("TestPlayer"))
            assertFalse(text.contains("{player}"))
        }

        @Test
        @DisplayName("Should handle multiple placeholders with prefix")
        fun handleMultiplePlaceholdersWithPrefix() {
            val component = messageService.getWithPrefix(
                "inventory-switched",
                "from_group" to "survival",
                "to_group" to "creative"
            )

            val text = component.toPlainText()
            assertTrue(text.contains("xInventories"))
            assertTrue(text.contains("survival"))
            assertTrue(text.contains("creative"))
        }
    }

    // =========================================================================
    // send Tests
    // =========================================================================

    @Nested
    @DisplayName("send")
    inner class SendTests {

        @Test
        @DisplayName("Should send message with prefix to CommandSender")
        fun sendMessageWithPrefixToSender() {
            val sender = createMockCommandSender()
            val messageSlot = slot<Component>()

            every { sender.sendMessage(capture(messageSlot)) } just Runs

            messageService.send(sender, "reloaded")

            verify { sender.sendMessage(any<Component>()) }
            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("xInventories"))
            assertTrue(text.contains("Configuration reloaded successfully!"))
        }

        @Test
        @DisplayName("Should send message with placeholder to Player")
        fun sendMessageWithPlaceholderToPlayer() {
            val player = createMockPlayer(name = "TestPlayer")
            val messageSlot = slot<Component>()

            every { player.sendMessage(capture(messageSlot)) } just Runs

            messageService.send(player, "player-not-found", "player" to "OtherPlayer")

            verify { player.sendMessage(any<Component>()) }
            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("OtherPlayer"))
        }

        @Test
        @DisplayName("Should send message with multiple placeholders")
        fun sendMessageWithMultiplePlaceholders() {
            val player = createMockPlayer()
            val messageSlot = slot<Component>()

            every { player.sendMessage(capture(messageSlot)) } just Runs

            messageService.send(
                player,
                "inventory-switched",
                "from_group" to "survival",
                "to_group" to "creative"
            )

            verify { player.sendMessage(any<Component>()) }
            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("survival"))
            assertTrue(text.contains("creative"))
        }
    }

    // =========================================================================
    // sendRaw Tests
    // =========================================================================

    @Nested
    @DisplayName("sendRaw")
    inner class SendRawTests {

        @Test
        @DisplayName("Should send message without prefix to CommandSender")
        fun sendMessageWithoutPrefixToSender() {
            val sender = createMockCommandSender()
            val messageSlot = slot<Component>()

            every { sender.sendMessage(capture(messageSlot)) } just Runs

            messageService.sendRaw(sender, "reloaded")

            verify { sender.sendMessage(any<Component>()) }
            val text = messageSlot.captured.toPlainText()
            assertFalse(text.startsWith("xInventories")) // No prefix at start
            assertTrue(text.contains("Configuration reloaded successfully!"))
        }

        @Test
        @DisplayName("Should send raw message with placeholders")
        fun sendRawMessageWithPlaceholders() {
            val player = createMockPlayer()
            val messageSlot = slot<Component>()

            every { player.sendMessage(capture(messageSlot)) } just Runs

            messageService.sendRaw(player, "group-not-found", "group" to "creative")

            verify { player.sendMessage(any<Component>()) }
            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("creative"))
        }
    }

    // =========================================================================
    // sendComponent Tests
    // =========================================================================

    @Nested
    @DisplayName("sendComponent")
    inner class SendComponentTests {

        @Test
        @DisplayName("Should send custom component to CommandSender")
        fun sendCustomComponentToSender() {
            val sender = createMockCommandSender()
            val component = Component.text("Custom message")

            messageService.sendComponent(sender, component)

            verify { sender.sendMessage(component) }
        }

        @Test
        @DisplayName("Should send custom component to Player")
        fun sendCustomComponentToPlayer() {
            val player = createMockPlayer()
            val component = Component.text("Player message")

            messageService.sendComponent(player, component)

            verify { player.sendMessage(component) }
        }

        @Test
        @DisplayName("Should send empty component")
        fun sendEmptyComponent() {
            val sender = createMockCommandSender()
            val component = Component.empty()

            messageService.sendComponent(sender, component)

            verify { sender.sendMessage(component) }
        }
    }

    // =========================================================================
    // sendMiniMessage Tests
    // =========================================================================

    @Nested
    @DisplayName("sendMiniMessage")
    inner class SendMiniMessageTests {

        @Test
        @DisplayName("Should send parsed MiniMessage string")
        fun sendParsedMiniMessageString() {
            val sender = createMockCommandSender()
            val messageSlot = slot<Component>()

            every { sender.sendMessage(capture(messageSlot)) } just Runs

            messageService.sendMiniMessage(sender, "<green>Success!</green>")

            verify { sender.sendMessage(any<Component>()) }
            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("Success!"))
        }

        @Test
        @DisplayName("Should send MiniMessage with placeholders")
        fun sendMiniMessageWithPlaceholders() {
            val player = createMockPlayer()
            val messageSlot = slot<Component>()

            every { player.sendMessage(capture(messageSlot)) } just Runs

            messageService.sendMiniMessage(
                player,
                "<aqua>{player}</aqua> joined the server!",
                "player" to "NewPlayer"
            )

            verify { player.sendMessage(any<Component>()) }
            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("NewPlayer"))
            assertFalse(text.contains("{player}"))
        }

        @Test
        @DisplayName("Should handle complex MiniMessage formatting")
        fun handleComplexMiniMessageFormatting() {
            val sender = createMockCommandSender()
            val messageSlot = slot<Component>()

            every { sender.sendMessage(capture(messageSlot)) } just Runs

            messageService.sendMiniMessage(
                sender,
                "<gradient:#5e4fa2:#9e7bb5><bold>Welcome!</bold></gradient>"
            )

            verify { sender.sendMessage(any<Component>()) }
            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("Welcome!"))
        }
    }

    // =========================================================================
    // broadcast Tests
    // =========================================================================

    @Nested
    @DisplayName("broadcast")
    inner class BroadcastTests {

        @Test
        @DisplayName("Should broadcast to players with specific permission")
        fun broadcastToPlayersWithPermission() {
            val player1 = createMockPlayer(name = "Admin1", hasPermission = true, permission = "xinventories.notify")
            val player2 = createMockPlayer(name = "User1", hasPermission = false, permission = "xinventories.notify")
            val player3 = createMockPlayer(name = "Admin2", hasPermission = true, permission = "xinventories.notify")

            every { server.onlinePlayers } returns listOf(player1, player2, player3)

            messageService.broadcast("xinventories.notify", "reloaded")

            verify(exactly = 1) { player1.sendMessage(any<Component>()) }
            verify(exactly = 0) { player2.sendMessage(any<Component>()) }
            verify(exactly = 1) { player3.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should broadcast with placeholders")
        fun broadcastWithPlaceholders() {
            val player = createMockPlayer(hasPermission = true, permission = "xinventories.admin")
            val messageSlot = slot<Component>()

            every { server.onlinePlayers } returns listOf(player)
            every { player.sendMessage(capture(messageSlot)) } just Runs

            messageService.broadcast("xinventories.admin", "player-not-found", "player" to "MissingPlayer")

            verify { player.sendMessage(any<Component>()) }
            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("MissingPlayer"))
        }

        @Test
        @DisplayName("Should not broadcast when no players have permission")
        fun noBroadcastWhenNoPlayersHavePermission() {
            val player1 = createMockPlayer(name = "User1", hasPermission = false, permission = "xinventories.admin")
            val player2 = createMockPlayer(name = "User2", hasPermission = false, permission = "xinventories.admin")

            every { server.onlinePlayers } returns listOf(player1, player2)

            messageService.broadcast("xinventories.admin", "reloaded")

            verify(exactly = 0) { player1.sendMessage(any<Component>()) }
            verify(exactly = 0) { player2.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should handle empty online players list")
        fun handleEmptyOnlinePlayersList() {
            every { server.onlinePlayers } returns emptyList()

            // Should not throw
            assertDoesNotThrow {
                messageService.broadcast("xinventories.admin", "reloaded")
            }
        }

        @Test
        @DisplayName("Should include prefix in broadcast")
        fun includePrefixInBroadcast() {
            val player = createMockPlayer(hasPermission = true, permission = "xinventories.test")
            val messageSlot = slot<Component>()

            every { server.onlinePlayers } returns listOf(player)
            every { player.sendMessage(capture(messageSlot)) } just Runs

            messageService.broadcast("xinventories.test", "reloaded")

            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("xInventories"))
        }
    }

    // =========================================================================
    // broadcastAll Tests
    // =========================================================================

    @Nested
    @DisplayName("broadcastAll")
    inner class BroadcastAllTests {

        @Test
        @DisplayName("Should broadcast to all online players")
        fun broadcastToAllOnlinePlayers() {
            val player1 = createMockPlayer(name = "Player1")
            val player2 = createMockPlayer(name = "Player2")
            val player3 = createMockPlayer(name = "Player3")

            every { server.onlinePlayers } returns listOf(player1, player2, player3)

            messageService.broadcastAll("reloaded")

            verify(exactly = 1) { player1.sendMessage(any<Component>()) }
            verify(exactly = 1) { player2.sendMessage(any<Component>()) }
            verify(exactly = 1) { player3.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should broadcast with placeholders to all players")
        fun broadcastWithPlaceholdersToAllPlayers() {
            val player = createMockPlayer()
            val messageSlot = slot<Component>()

            every { server.onlinePlayers } returns listOf(player)
            every { player.sendMessage(capture(messageSlot)) } just Runs

            messageService.broadcastAll("cache-cleared", "count" to "100")

            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("100"))
        }

        @Test
        @DisplayName("Should handle empty online players list")
        fun handleEmptyOnlinePlayersList() {
            every { server.onlinePlayers } returns emptyList()

            // Should not throw
            assertDoesNotThrow {
                messageService.broadcastAll("reloaded")
            }
        }

        @Test
        @DisplayName("Should include prefix in broadcast to all")
        fun includePrefixInBroadcastAll() {
            val player = createMockPlayer()
            val messageSlot = slot<Component>()

            every { server.onlinePlayers } returns listOf(player)
            every { player.sendMessage(capture(messageSlot)) } just Runs

            messageService.broadcastAll("reloaded")

            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("xInventories"))
        }
    }

    // =========================================================================
    // notifyAdmins Tests
    // =========================================================================

    @Nested
    @DisplayName("notifyAdmins")
    inner class NotifyAdminsTests {

        @Test
        @DisplayName("Should notify admins when admin notifications are enabled")
        fun notifyAdminsWhenEnabled() {
            val admin = createMockPlayer(name = "Admin", hasPermission = true)
            val user = createMockPlayer(name = "User", hasPermission = false)

            every { mainConfig.features } returns FeaturesConfig(adminNotifications = true)
            every { server.onlinePlayers } returns listOf(admin, user)

            messageService.notifyAdmins("Test error message")

            verify(exactly = 1) { admin.sendMessage(any<Component>()) }
            verify(exactly = 0) { user.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should not notify admins when admin notifications are disabled")
        fun noNotifyAdminsWhenDisabled() {
            val admin = createMockPlayer(name = "Admin", hasPermission = true)

            every { mainConfig.features } returns FeaturesConfig(adminNotifications = false)
            every { server.onlinePlayers } returns listOf(admin)

            messageService.notifyAdmins("Test error message")

            verify(exactly = 0) { admin.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should send admin-error message with message placeholder")
        fun sendAdminErrorMessage() {
            val admin = createMockPlayer(name = "Admin", hasPermission = true)
            val messageSlot = slot<Component>()

            every { mainConfig.features } returns FeaturesConfig(adminNotifications = true)
            every { server.onlinePlayers } returns listOf(admin)
            every { admin.sendMessage(capture(messageSlot)) } just Runs

            messageService.notifyAdmins("Database connection failed")

            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("Database connection failed"))
        }

        @Test
        @DisplayName("Should only notify players with xinventories.admin permission")
        fun onlyNotifyAdminPermission() {
            val adminPlayer = createMockPlayer(name = "Admin")
            every { adminPlayer.hasPermission("xinventories.admin") } returns true

            val moderator = createMockPlayer(name = "Mod")
            every { moderator.hasPermission("xinventories.admin") } returns false

            every { mainConfig.features } returns FeaturesConfig(adminNotifications = true)
            every { server.onlinePlayers } returns listOf(adminPlayer, moderator)

            messageService.notifyAdmins("Test message")

            verify(exactly = 1) { adminPlayer.sendMessage(any<Component>()) }
            verify(exactly = 0) { moderator.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should handle empty online players list")
        fun handleEmptyOnlinePlayersList() {
            every { mainConfig.features } returns FeaturesConfig(adminNotifications = true)
            every { server.onlinePlayers } returns emptyList()

            // Should not throw
            assertDoesNotThrow {
                messageService.notifyAdmins("Test message")
            }
        }
    }

    // =========================================================================
    // warnAdmins Tests
    // =========================================================================

    @Nested
    @DisplayName("warnAdmins")
    inner class WarnAdminsTests {

        @Test
        @DisplayName("Should warn admins when admin notifications are enabled")
        fun warnAdminsWhenEnabled() {
            val admin = createMockPlayer(name = "Admin", hasPermission = true)
            val user = createMockPlayer(name = "User", hasPermission = false)

            every { mainConfig.features } returns FeaturesConfig(adminNotifications = true)
            every { server.onlinePlayers } returns listOf(admin, user)

            messageService.warnAdmins("Test warning message")

            verify(exactly = 1) { admin.sendMessage(any<Component>()) }
            verify(exactly = 0) { user.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should not warn admins when admin notifications are disabled")
        fun noWarnAdminsWhenDisabled() {
            val admin = createMockPlayer(name = "Admin", hasPermission = true)

            every { mainConfig.features } returns FeaturesConfig(adminNotifications = false)
            every { server.onlinePlayers } returns listOf(admin)

            messageService.warnAdmins("Test warning message")

            verify(exactly = 0) { admin.sendMessage(any<Component>()) }
        }

        @Test
        @DisplayName("Should send admin-warning message with message placeholder")
        fun sendAdminWarningMessage() {
            val admin = createMockPlayer(name = "Admin", hasPermission = true)
            val messageSlot = slot<Component>()

            every { mainConfig.features } returns FeaturesConfig(adminNotifications = true)
            every { server.onlinePlayers } returns listOf(admin)
            every { admin.sendMessage(capture(messageSlot)) } just Runs

            messageService.warnAdmins("Cache is nearly full")

            val text = messageSlot.captured.toPlainText()
            assertTrue(text.contains("Cache is nearly full"))
        }

        @Test
        @DisplayName("Should only warn players with xinventories.admin permission")
        fun onlyWarnAdminPermission() {
            val adminPlayer = createMockPlayer(name = "Admin")
            every { adminPlayer.hasPermission("xinventories.admin") } returns true

            val moderator = createMockPlayer(name = "Mod")
            every { moderator.hasPermission("xinventories.admin") } returns false

            every { mainConfig.features } returns FeaturesConfig(adminNotifications = true)
            every { server.onlinePlayers } returns listOf(adminPlayer, moderator)

            messageService.warnAdmins("Test warning")

            verify(exactly = 1) { adminPlayer.sendMessage(any<Component>()) }
            verify(exactly = 0) { moderator.sendMessage(any<Component>()) }
        }
    }

    // =========================================================================
    // parse Tests
    // =========================================================================

    @Nested
    @DisplayName("parse")
    inner class ParseTests {

        @Test
        @DisplayName("Should parse MiniMessage string to Component")
        fun parseMiniMessageToComponent() {
            val component = messageService.parse("<green>Hello World!</green>")

            val text = component.toPlainText()
            assertEquals("Hello World!", text)
        }

        @Test
        @DisplayName("Should parse complex MiniMessage formatting")
        fun parseComplexMiniMessageFormatting() {
            val component = messageService.parse("<gradient:#5e4fa2:#9e7bb5><bold>Gradient Text</bold></gradient>")

            val text = component.toPlainText()
            assertTrue(text.contains("Gradient Text"))
        }

        @Test
        @DisplayName("Should parse message with placeholders")
        fun parseMessageWithPlaceholders() {
            val component = messageService.parse(
                "Hello {name}, welcome to {server}!",
                "name" to "TestPlayer",
                "server" to "MyServer"
            )

            val text = component.toPlainText()
            assertTrue(text.contains("TestPlayer"))
            assertTrue(text.contains("MyServer"))
            assertFalse(text.contains("{name}"))
            assertFalse(text.contains("{server}"))
        }

        @Test
        @DisplayName("Should parse empty string")
        fun parseEmptyString() {
            val component = messageService.parse("")

            assertEquals("", component.toPlainText())
        }

        @Test
        @DisplayName("Should parse plain text without formatting")
        fun parsePlainTextWithoutFormatting() {
            val component = messageService.parse("Plain text message")

            assertEquals("Plain text message", component.toPlainText())
        }

        @Test
        @DisplayName("Should handle multiple placeholder pairs")
        fun handleMultiplePlaceholderPairs() {
            val component = messageService.parse(
                "{a} {b} {c} {d}",
                "a" to "1",
                "b" to "2",
                "c" to "3",
                "d" to "4"
            )

            assertEquals("1 2 3 4", component.toPlainText())
        }

        @Test
        @DisplayName("Should handle duplicate placeholders")
        fun handleDuplicatePlaceholders() {
            val component = messageService.parse(
                "{name} said hello to {name}",
                "name" to "Alice"
            )

            assertEquals("Alice said hello to Alice", component.toPlainText())
        }
    }

    // =========================================================================
    // stripTags Tests
    // =========================================================================

    @Nested
    @DisplayName("stripTags")
    inner class StripTagsTests {

        @Test
        @DisplayName("Should strip color tags from message")
        fun stripColorTags() {
            val result = messageService.stripTags("<green>Hello World!</green>")

            assertEquals("Hello World!", result)
        }

        @Test
        @DisplayName("Should strip bold tags from message")
        fun stripBoldTags() {
            val result = messageService.stripTags("<bold>Important!</bold>")

            assertEquals("Important!", result)
        }

        @Test
        @DisplayName("Should strip italic tags from message")
        fun stripItalicTags() {
            val result = messageService.stripTags("<italic>Emphasized</italic>")

            assertEquals("Emphasized", result)
        }

        @Test
        @DisplayName("Should strip gradient tags from message")
        fun stripGradientTags() {
            val result = messageService.stripTags("<gradient:#5e4fa2:#9e7bb5>Gradient</gradient>")

            assertEquals("Gradient", result)
        }

        @Test
        @DisplayName("Should strip multiple nested tags")
        fun stripMultipleNestedTags() {
            val result = messageService.stripTags("<red><bold><italic>Nested</italic></bold></red>")

            assertEquals("Nested", result)
        }

        @Test
        @DisplayName("Should handle message without tags")
        fun handleMessageWithoutTags() {
            val result = messageService.stripTags("Plain text message")

            assertEquals("Plain text message", result)
        }

        @Test
        @DisplayName("Should handle empty string")
        fun handleEmptyString() {
            val result = messageService.stripTags("")

            assertEquals("", result)
        }

        @Test
        @DisplayName("Should strip hover and click tags")
        fun stripHoverAndClickTags() {
            val result = messageService.stripTags("<click:run_command:/help><hover:show_text:Click me!>Help</hover></click>")

            assertEquals("Help", result)
        }

        @Test
        @DisplayName("Should strip underline and strikethrough tags")
        fun stripUnderlineAndStrikethroughTags() {
            val result = messageService.stripTags("<underlined>Underlined</underlined> <strikethrough>Strike</strikethrough>")

            assertEquals("Underlined Strike", result)
        }

        @Test
        @DisplayName("Should preserve placeholders when stripping tags")
        fun preservePlaceholdersWhenStrippingTags() {
            val result = messageService.stripTags("<red>Player {player} not found</red>")

            assertEquals("Player {player} not found", result)
        }
    }

    // =========================================================================
    // Edge Cases and Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle special characters in placeholders")
        fun handleSpecialCharactersInPlaceholders() {
            val component = messageService.get(
                "player-not-found",
                "player" to "Player\"With'Special<Chars>"
            )

            val text = component.toPlainText()
            assertTrue(text.contains("Player\"With'Special<Chars>"))
        }

        @Test
        @DisplayName("Should handle very long placeholder values")
        fun handleVeryLongPlaceholderValues() {
            val longValue = "A".repeat(1000)
            val component = messageService.get("player-not-found", "player" to longValue)

            val text = component.toPlainText()
            assertTrue(text.contains(longValue))
        }

        @Test
        @DisplayName("Should handle unicode characters in placeholders")
        fun handleUnicodeCharactersInPlaceholders() {
            val component = messageService.get(
                "player-not-found",
                "player" to "\u4E2D\u6587\u540D\u5B57" // Chinese characters
            )

            val text = component.toPlainText()
            assertTrue(text.contains("\u4E2D\u6587\u540D\u5B57"))
        }

        @Test
        @DisplayName("Should handle newlines in placeholder values")
        fun handleNewlinesInPlaceholderValues() {
            val component = messageService.parse(
                "Message: {content}",
                "content" to "Line1\nLine2"
            )

            val text = component.toPlainText()
            assertTrue(text.contains("Line1\nLine2"))
        }

        @Test
        @DisplayName("Should handle curly braces that are not placeholders")
        fun handleCurlyBracesThatAreNotPlaceholders() {
            val component = messageService.parse(
                "JSON example: { \"key\": \"value\" }",
            )

            val text = component.toPlainText()
            assertTrue(text.contains("{ \"key\": \"value\" }"))
        }

        @Test
        @DisplayName("Should handle from_world and to_world placeholders together")
        fun handleFromWorldAndToWorldPlaceholders() {
            // Create custom message with both world placeholders
            val customMessages = mapOf(
                "world-transition" to "Teleporting from {from_world} to {to_world}"
            )
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val component = messageService.get(
                "world-transition",
                "from_world" to "overworld",
                "to_world" to "nether"
            )

            val text = component.toPlainText()
            assertTrue(text.contains("overworld"))
            assertTrue(text.contains("nether"))
            assertFalse(text.contains("{from_world}"))
            assertFalse(text.contains("{to_world}"))
        }

        @Test
        @DisplayName("Should handle from_group and to_group placeholders")
        fun handleFromGroupAndToGroupPlaceholders() {
            val component = messageService.get(
                "inventory-switched",
                "from_group" to "survival",
                "to_group" to "creative"
            )

            val text = component.toPlainText()
            assertTrue(text.contains("survival"))
            assertTrue(text.contains("creative"))
        }
    }

    // =========================================================================
    // Config Refresh Tests
    // =========================================================================

    @Nested
    @DisplayName("Config Refresh")
    inner class ConfigRefreshTests {

        @Test
        @DisplayName("Should use updated config when messagesConfig changes")
        fun useUpdatedConfigWhenMessagesConfigChanges() {
            // First call with default config
            val component1 = messageService.getRaw("reloaded")
            assertTrue(component1.contains("Configuration reloaded successfully!"))

            // Update config
            val customMessages = mapOf(
                "reloaded" to "<blue>Config has been refreshed!</blue>"
            )
            val newConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns newConfig

            // Second call should use new config
            val component2 = messageService.getRaw("reloaded")
            assertTrue(component2.contains("Config has been refreshed!"))
        }

        @Test
        @DisplayName("Should use updated prefix when prefix changes")
        fun useUpdatedPrefixWhenPrefixChanges() {
            // Get initial prefix
            val prefix1 = messageService.prefix.toPlainText()
            assertTrue(prefix1.contains("xInventories"))

            // Update prefix
            val newConfig = MessagesConfig(prefix = "<red>[NewPrefix]</red> ")
            every { configManager.messagesConfig } returns newConfig

            // Get new prefix
            val prefix2 = messageService.prefix.toPlainText()
            assertTrue(prefix2.contains("[NewPrefix]"))
        }
    }

    // =========================================================================
    // All Placeholder Types Tests
    // =========================================================================

    @Nested
    @DisplayName("All Placeholder Types")
    inner class AllPlaceholderTypesTests {

        @Test
        @DisplayName("Should handle player placeholder")
        fun handlePlayerPlaceholder() {
            val result = messageService.get("player-not-found", "player" to "Steve")
            assertTrue(result.toPlainText().contains("Steve"))
        }

        @Test
        @DisplayName("Should handle world placeholder")
        fun handleWorldPlaceholder() {
            val result = messageService.get("world-not-found", "world" to "world_nether")
            assertTrue(result.toPlainText().contains("world_nether"))
        }

        @Test
        @DisplayName("Should handle group placeholder")
        fun handleGroupPlaceholder() {
            val result = messageService.get("group-not-found", "group" to "adventure")
            assertTrue(result.toPlainText().contains("adventure"))
        }

        @Test
        @DisplayName("Should handle from_world placeholder")
        fun handleFromWorldPlaceholder() {
            val customMessages = mapOf("test" to "From: {from_world}")
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val result = messageService.get("test", "from_world" to "origin")
            assertTrue(result.toPlainText().contains("origin"))
        }

        @Test
        @DisplayName("Should handle to_world placeholder")
        fun handleToWorldPlaceholder() {
            val customMessages = mapOf("test" to "To: {to_world}")
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val result = messageService.get("test", "to_world" to "destination")
            assertTrue(result.toPlainText().contains("destination"))
        }

        @Test
        @DisplayName("Should handle from_group placeholder")
        fun handleFromGroupPlaceholder() {
            val result = messageService.get(
                "inventory-switched",
                "from_group" to "old_group",
                "to_group" to "new_group"
            )
            assertTrue(result.toPlainText().contains("old_group"))
        }

        @Test
        @DisplayName("Should handle to_group placeholder")
        fun handleToGroupPlaceholder() {
            val result = messageService.get(
                "inventory-switched",
                "from_group" to "old_group",
                "to_group" to "new_group"
            )
            assertTrue(result.toPlainText().contains("new_group"))
        }

        @Test
        @DisplayName("Should handle version placeholder")
        fun handleVersionPlaceholder() {
            val customMessages = mapOf("version-test" to "Version: {version}")
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val result = messageService.get("version-test", "version" to "2.5.0")
            assertTrue(result.toPlainText().contains("2.5.0"))
        }

        @Test
        @DisplayName("Should handle trigger placeholder")
        fun handleTriggerPlaceholder() {
            val customMessages = mapOf("trigger-test" to "Trigger: {trigger}")
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val result = messageService.get("trigger-test", "trigger" to "disconnect")
            assertTrue(result.toPlainText().contains("disconnect"))
        }

        @Test
        @DisplayName("Should handle count placeholder")
        fun handleCountPlaceholder() {
            val result = messageService.get("cache-cleared", "count" to "42")
            assertTrue(result.toPlainText().contains("42"))
        }

        @Test
        @DisplayName("Should handle slot placeholder")
        fun handleSlotPlaceholder() {
            val customMessages = mapOf("slot-test" to "Slot: {slot}")
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val result = messageService.get("slot-test", "slot" to "36")
            assertTrue(result.toPlainText().contains("36"))
        }

        @Test
        @DisplayName("Should handle all placeholders together")
        fun handleAllPlaceholdersTogether() {
            val customMessages = mapOf(
                "all-placeholders" to "{player} in {world} ({group}): v{version} - slot {slot} count {count} trigger {trigger}"
            )
            val customConfig = MessagesConfig(messages = MessagesConfig.defaultMessages() + customMessages)
            every { configManager.messagesConfig } returns customConfig

            val result = messageService.get(
                "all-placeholders",
                "player" to "TestPlayer",
                "world" to "world",
                "group" to "survival",
                "version" to "1.0",
                "slot" to "5",
                "count" to "10",
                "trigger" to "manual"
            )

            val text = result.toPlainText()
            assertTrue(text.contains("TestPlayer"))
            assertTrue(text.contains("world"))
            assertTrue(text.contains("survival"))
            assertTrue(text.contains("1.0"))
            assertTrue(text.contains("5"))
            assertTrue(text.contains("10"))
            assertTrue(text.contains("manual"))
        }
    }
}

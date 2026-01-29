package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.miniMessage
import sh.pcx.xinventories.internal.util.toComponent
import net.kyori.adventure.text.Component
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Service for handling message formatting and sending using MiniMessage.
 */
class MessageService(private val plugin: XInventories) {

    private val config get() = plugin.configManager.messagesConfig

    /**
     * Gets the configured prefix.
     */
    val prefix: Component get() = config.prefix.toComponent()

    /**
     * Gets a raw message string by key.
     */
    fun getRaw(key: String): String = config.getMessage(key)

    /**
     * Gets a formatted message component.
     *
     * @param key Message key
     * @param placeholders Placeholder replacements
     * @return Formatted Component
     */
    fun get(key: String, vararg placeholders: Pair<String, String>): Component {
        var message = config.getMessage(key)

        // Replace simple placeholders
        placeholders.forEach { (placeholder, value) ->
            message = message.replace("{$placeholder}", value)
        }

        return miniMessage.deserialize(message)
    }

    /**
     * Gets a formatted message with prefix.
     */
    fun getWithPrefix(key: String, vararg placeholders: Pair<String, String>): Component {
        return Component.text()
            .append(prefix)
            .append(get(key, *placeholders))
            .build()
    }

    /**
     * Sends a message to a CommandSender.
     *
     * @param sender The recipient
     * @param key Message key
     * @param placeholders Placeholder replacements
     */
    fun send(sender: CommandSender, key: String, vararg placeholders: Pair<String, String>) {
        sender.sendMessage(getWithPrefix(key, *placeholders))
    }

    /**
     * Sends a message without prefix.
     */
    fun sendRaw(sender: CommandSender, key: String, vararg placeholders: Pair<String, String>) {
        sender.sendMessage(get(key, *placeholders))
    }

    /**
     * Sends a custom component message.
     */
    fun sendComponent(sender: CommandSender, component: Component) {
        sender.sendMessage(component)
    }

    /**
     * Sends a custom message string (parsed as MiniMessage).
     */
    fun sendMiniMessage(sender: CommandSender, message: String, vararg placeholders: Pair<String, String>) {
        sender.sendMessage(message.toComponent(*placeholders))
    }

    /**
     * Broadcasts a message to all online players with a specific permission.
     */
    fun broadcast(permission: String, key: String, vararg placeholders: Pair<String, String>) {
        val message = getWithPrefix(key, *placeholders)
        plugin.server.onlinePlayers
            .filter { it.hasPermission(permission) }
            .forEach { it.sendMessage(message) }
    }

    /**
     * Broadcasts a message to all online players.
     */
    fun broadcastAll(key: String, vararg placeholders: Pair<String, String>) {
        val message = getWithPrefix(key, *placeholders)
        plugin.server.onlinePlayers.forEach { it.sendMessage(message) }
    }

    /**
     * Sends an error notification to admins.
     */
    fun notifyAdmins(message: String) {
        if (!plugin.configManager.mainConfig.features.adminNotifications) return

        val component = get("admin-error", "message" to message)
        plugin.server.onlinePlayers
            .filter { it.hasPermission("xinventories.admin") }
            .forEach { it.sendMessage(component) }
    }

    /**
     * Sends a warning notification to admins.
     */
    fun warnAdmins(message: String) {
        if (!plugin.configManager.mainConfig.features.adminNotifications) return

        val component = get("admin-warning", "message" to message)
        plugin.server.onlinePlayers
            .filter { it.hasPermission("xinventories.admin") }
            .forEach { it.sendMessage(component) }
    }

    /**
     * Parses a MiniMessage string to Component.
     */
    fun parse(message: String): Component = miniMessage.deserialize(message)

    /**
     * Parses a MiniMessage string with placeholders.
     */
    fun parse(message: String, vararg placeholders: Pair<String, String>): Component {
        var result = message
        placeholders.forEach { (key, value) ->
            result = result.replace("{$key}", value)
        }
        return miniMessage.deserialize(result)
    }

    /**
     * Strips MiniMessage tags from a string.
     */
    fun stripTags(message: String): String = miniMessage.stripTags(message)
}

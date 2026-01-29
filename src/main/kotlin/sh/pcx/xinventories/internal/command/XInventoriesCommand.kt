package sh.pcx.xinventories.internal.command

import sh.pcx.xinventories.XInventories
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

/**
 * Main command executor for /xinventories (/xinv).
 */
class XInventoriesCommand(
    private val plugin: XInventories,
    private val commandManager: CommandManager
) : CommandExecutor, TabCompleter {

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<String>
    ): Boolean {
        val messages = plugin.serviceManager.messageService

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        val subcommandName = args[0]
        val subcommand = commandManager.getSubcommand(subcommandName)

        if (subcommand == null) {
            messages.send(sender, "unknown-command", "command" to subcommandName)
            return true
        }

        // Check permission
        if (!sender.hasPermission(subcommand.permission)) {
            messages.send(sender, "no-permission")
            return true
        }

        // Execute subcommand asynchronously
        val subArgs = args.drop(1).toTypedArray()

        plugin.scope.launch {
            try {
                subcommand.execute(plugin, sender, subArgs)
            } catch (e: Exception) {
                plugin.logger.severe("Error executing command ${subcommand.name}: ${e.message}")
                e.printStackTrace()
                messages.send(sender, "command-error")
            }
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<String>
    ): List<String> {
        if (args.isEmpty()) {
            return emptyList()
        }

        if (args.size == 1) {
            // Complete subcommand names
            return commandManager.getAllSubcommands()
                .filter { sender.hasPermission(it.permission) }
                .map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
                .sorted()
        }

        // Delegate to subcommand
        val subcommand = commandManager.getSubcommand(args[0])
        if (subcommand == null || !sender.hasPermission(subcommand.permission)) {
            return emptyList()
        }

        return subcommand.tabComplete(plugin, sender, args.drop(1).toTypedArray())
    }

    private fun showHelp(sender: CommandSender) {
        val primaryColor = TextColor.color(0x5e4fa2)  // Purple
        val secondaryColor = TextColor.color(0x9e7bb5) // Light purple
        val accentColor = TextColor.color(0x5dade2)   // Cyan/blue

        fun send(component: Component) = sender.sendMessage(component)

        // Header with gradient effect
        send(Component.empty())
        send(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
        send(
            Component.text()
                .append(Component.text("  ◆ ", primaryColor))
                .append(Component.text("x", primaryColor).decorate(TextDecoration.BOLD))
                .append(Component.text("Inventories", secondaryColor).decorate(TextDecoration.BOLD))
                .append(Component.text(" Commands", NamedTextColor.GRAY))
                .build()
        )
        send(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))

        // Get available subcommands
        val subcommands = commandManager.getAllSubcommands()
            .filter { sender.hasPermission(it.permission) }
            .sortedBy { it.name }

        // Group commands by category
        val coreCommands = listOf("gui", "reload", "save", "load")
        val managementCommands = listOf("group", "world", "pattern")
        val utilityCommands = listOf("cache", "backup", "convert", "debug")

        // Core Commands
        val availableCore = subcommands.filter { it.name in coreCommands }
        if (availableCore.isNotEmpty()) {
            send(Component.empty())
            send(Component.text("  Core Commands", accentColor).decorate(TextDecoration.BOLD))
            availableCore.forEach { cmd -> send(buildCommandEntry(cmd, primaryColor, accentColor)) }
        }

        // Management Commands
        val availableManagement = subcommands.filter { it.name in managementCommands }
        if (availableManagement.isNotEmpty()) {
            send(Component.empty())
            send(Component.text("  Management", accentColor).decorate(TextDecoration.BOLD))
            availableManagement.forEach { cmd -> send(buildCommandEntry(cmd, primaryColor, accentColor)) }
        }

        // Utility Commands
        val availableUtility = subcommands.filter { it.name in utilityCommands }
        if (availableUtility.isNotEmpty()) {
            send(Component.empty())
            send(Component.text("  Utilities", accentColor).decorate(TextDecoration.BOLD))
            availableUtility.forEach { cmd -> send(buildCommandEntry(cmd, primaryColor, accentColor)) }
        }

        // Any other commands not categorized
        val otherCommands = subcommands.filter {
            it.name !in coreCommands && it.name !in managementCommands && it.name !in utilityCommands
        }
        if (otherCommands.isNotEmpty()) {
            send(Component.empty())
            send(Component.text("  Other", accentColor).decorate(TextDecoration.BOLD))
            otherCommands.forEach { cmd -> send(buildCommandEntry(cmd, primaryColor, accentColor)) }
        }

        // Footer
        send(Component.empty())
        send(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
        send(
            Component.text()
                .append(Component.text("  Tip: ", NamedTextColor.GRAY))
                .append(Component.text("Click", accentColor).decorate(TextDecoration.UNDERLINED))
                .append(Component.text(" a command to use it!", NamedTextColor.GRAY))
                .build()
        )
        send(Component.empty())
    }

    private fun buildCommandEntry(
        subcommand: sh.pcx.xinventories.internal.command.subcommand.Subcommand,
        primaryColor: TextColor,
        accentColor: TextColor
    ): Component {
        // Extract the base command for clicking (e.g., "/xinv gui" from "/xinv gui")
        val baseCommand = "/xinv ${subcommand.name}"

        // Build hover text
        val hoverText = Component.text()
            .append(Component.text(subcommand.usage, accentColor).decorate(TextDecoration.BOLD))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text(subcommand.description, NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("Permission: ", NamedTextColor.DARK_GRAY))
            .append(Component.text(subcommand.permission, NamedTextColor.GRAY))
            .append(Component.newline())
            .append(Component.newline())
            .append(Component.text("▶ Click to run", NamedTextColor.GREEN))
            .build()

        return Component.text()
            .append(Component.text("    "))
            .append(
                Component.text("▸ ", primaryColor)
            )
            .append(
                Component.text(subcommand.name, accentColor)
                    .clickEvent(ClickEvent.suggestCommand(baseCommand + " "))
                    .hoverEvent(HoverEvent.showText(hoverText))
            )
            .append(
                Component.text(" - ", NamedTextColor.DARK_GRAY)
            )
            .append(
                Component.text(subcommand.description, NamedTextColor.GRAY)
                    .clickEvent(ClickEvent.suggestCommand(baseCommand + " "))
                    .hoverEvent(HoverEvent.showText(hoverText))
            )
            .build()
    }
}

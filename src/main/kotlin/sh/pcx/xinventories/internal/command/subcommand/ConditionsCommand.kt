package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.CommandSender

/**
 * Command to view conditions for a group.
 * Usage: /xinv conditions <group>
 */
class ConditionsCommand : Subcommand {

    override val name = "conditions"
    override val aliases = listOf("cond")
    override val permission = "xinventories.command.group"
    override val usage = "/xinv conditions <group>"
    override val description = "View conditions for a group"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val groupService = plugin.serviceManager.groupService

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        val groupName = args[0]
        val group = groupService.getGroup(groupName)

        if (group == null) {
            messages.send(sender, "group-not-found", "group" to groupName)
            return true
        }

        val conditions = group.conditions

        // Build header
        val header = Component.text()
            .append(Component.text("Conditions for group ", NamedTextColor.GRAY))
            .append(Component.text(groupName, NamedTextColor.GOLD, TextDecoration.BOLD))
            .build()
        sender.sendMessage(header)

        if (conditions == null || !conditions.hasConditions()) {
            sender.sendMessage(Component.text("  No conditions defined", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("  Group uses world/pattern matching only", NamedTextColor.GRAY))
            return true
        }

        // Show logic mode
        val logicMode = if (conditions.requireAll) "AND (all must match)" else "OR (any can match)"
        sender.sendMessage(Component.text()
            .append(Component.text("  Logic: ", NamedTextColor.GRAY))
            .append(Component.text(logicMode, NamedTextColor.AQUA))
            .build())

        // Show permission condition
        conditions.permission?.let { perm ->
            sender.sendMessage(Component.text()
                .append(Component.text("  Permission: ", NamedTextColor.GRAY))
                .append(Component.text(perm, NamedTextColor.GREEN))
                .build())
        }

        // Show schedule conditions
        conditions.schedule?.takeIf { it.isNotEmpty() }?.let { ranges ->
            sender.sendMessage(Component.text("  Schedule:", NamedTextColor.GRAY))
            for ((index, range) in ranges.withIndex()) {
                val active = if (range.isActive()) " (ACTIVE)" else ""
                sender.sendMessage(Component.text()
                    .append(Component.text("    ${index + 1}. ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("${range.getStartZoned()} - ${range.getEndZoned()}", NamedTextColor.YELLOW))
                    .append(Component.text(active, NamedTextColor.GREEN))
                    .build())
            }
        }

        // Show cron condition
        conditions.cron?.let { cron ->
            sender.sendMessage(Component.text()
                .append(Component.text("  Cron: ", NamedTextColor.GRAY))
                .append(Component.text(cron, NamedTextColor.YELLOW))
                .build())
        }

        // Show placeholder conditions
        val allPlaceholders = conditions.getAllPlaceholderConditions()
        if (allPlaceholders.isNotEmpty()) {
            sender.sendMessage(Component.text("  Placeholders:", NamedTextColor.GRAY))
            for (condition in allPlaceholders) {
                sender.sendMessage(Component.text()
                    .append(Component.text("    - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(condition.toDisplayString(), NamedTextColor.LIGHT_PURPLE))
                    .build())
            }
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> plugin.serviceManager.groupService.getAllGroups()
                .map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
            else -> emptyList()
        }
    }
}

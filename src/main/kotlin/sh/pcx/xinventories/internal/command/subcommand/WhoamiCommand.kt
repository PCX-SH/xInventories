package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.PluginContext
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command to show which group a player is in and why.
 * Usage: /xinv whoami
 */
class WhoamiCommand : Subcommand {

    override val name = "whoami"
    override val aliases = listOf("who", "me")
    override val permission = "xinventories.command"
    override val usage = "/xinv whoami"
    override val description = "Show your current inventory group and why"
    override val playerOnly = true

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val player = sender as Player
        val groupService = plugin.serviceManager.groupService
        val inventoryService = plugin.serviceManager.inventoryService
        val conditionEvaluator = plugin.serviceManager.conditionEvaluator

        // Get current group
        val currentGroupName = inventoryService.getCurrentGroup(player)
            ?: groupService.getGroupForPlayer(player).name
        val currentGroup = groupService.getGroup(currentGroupName)

        // Header
        val header = Component.text()
            .append(Component.text("=== ", NamedTextColor.DARK_GRAY))
            .append(Component.text("Inventory Status", NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(" ===", NamedTextColor.DARK_GRAY))
            .build()
        sender.sendMessage(header)

        // Current world
        sender.sendMessage(Component.text()
            .append(Component.text("World: ", NamedTextColor.GRAY))
            .append(Component.text(player.world.name, NamedTextColor.WHITE))
            .build())

        // Current group
        sender.sendMessage(Component.text()
            .append(Component.text("Group: ", NamedTextColor.GRAY))
            .append(Component.text(currentGroupName, NamedTextColor.GREEN, TextDecoration.BOLD))
            .build())

        // GameMode
        sender.sendMessage(Component.text()
            .append(Component.text("GameMode: ", NamedTextColor.GRAY))
            .append(Component.text(player.gameMode.name, NamedTextColor.WHITE))
            .build())

        // Match reason
        val matchReason = groupService.getGroupMatchReason(player)
        sender.sendMessage(Component.text()
            .append(Component.text("Reason: ", NamedTextColor.GRAY))
            .append(Component.text(matchReason, NamedTextColor.AQUA))
            .build())

        // Show conditions if group has them
        currentGroup?.conditions?.let { conditions ->
            if (conditions.hasConditions()) {
                val result = conditionEvaluator.evaluateConditions(player, currentGroup)

                sender.sendMessage(Component.text())
                sender.sendMessage(Component.text()
                    .append(Component.text("Condition Details:", NamedTextColor.YELLOW))
                    .build())

                if (result.matchedConditions.isNotEmpty()) {
                    sender.sendMessage(Component.text()
                        .append(Component.text("  Matched: ", NamedTextColor.GREEN))
                        .append(Component.text(result.matchedConditions.joinToString(", "), NamedTextColor.WHITE))
                        .build())
                }

                if (result.failedConditions.isNotEmpty()) {
                    sender.sendMessage(Component.text()
                        .append(Component.text("  Failed: ", NamedTextColor.RED))
                        .append(Component.text(result.failedConditions.joinToString(", "), NamedTextColor.WHITE))
                        .build())
                }
            }
        }

        // Show other groups that could match
        val otherMatchingGroups = groupService.getAllGroups()
            .filter { it.name != currentGroupName }
            .filter { group ->
                val worldName = player.world.name
                group.worlds.contains(worldName) ||
                group.matchesPattern(worldName) ||
                (group.conditions?.hasConditions() == true &&
                    conditionEvaluator.evaluateConditions(player, group).matches)
            }

        if (otherMatchingGroups.isNotEmpty()) {
            sender.sendMessage(Component.text())
            sender.sendMessage(Component.text()
                .append(Component.text("Other matching groups (lower priority):", NamedTextColor.GRAY))
                .build())

            for (group in otherMatchingGroups.sortedByDescending { it.priority }.take(5)) {
                val priorityInfo = "(priority: ${group.priority})"
                sender.sendMessage(Component.text()
                    .append(Component.text("  - ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(group.name, NamedTextColor.YELLOW))
                    .append(Component.text(" $priorityInfo", NamedTextColor.DARK_GRAY))
                    .build())
            }
        }

        // Check if locked
        val lockingService = plugin.serviceManager.lockingService
        val lock = lockingService.getLock(player.uniqueId)
        if (lock != null) {
            sender.sendMessage(Component.text())
            sender.sendMessage(Component.text()
                .append(Component.text("Inventory Locked: ", NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(lock.reason ?: "No reason given", NamedTextColor.WHITE))
                .build())
            sender.sendMessage(Component.text()
                .append(Component.text("  Expires: ", NamedTextColor.GRAY))
                .append(Component.text(lock.getRemainingTimeString(), NamedTextColor.YELLOW))
                .build())
        }

        return true
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return emptyList()
    }
}

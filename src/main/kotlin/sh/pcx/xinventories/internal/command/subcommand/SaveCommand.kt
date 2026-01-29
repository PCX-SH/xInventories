package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Force saves player inventory.
 */
class SaveCommand : Subcommand {

    override val name = "save"
    override val permission = "xinventories.command.save"
    override val usage = "/xinv save [player]"
    override val description = "Force save inventory"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val inventoryService = plugin.serviceManager.inventoryService

        val target: Player? = when {
            args.isNotEmpty() -> {
                // Check permission for saving others
                if (!sender.hasPermission("xinventories.command.save.others")) {
                    messages.send(sender, "no-permission")
                    return true
                }
                Bukkit.getPlayer(args[0])
            }
            sender is Player -> sender
            else -> {
                messages.send(sender, "player-only")
                return true
            }
        }

        if (target == null) {
            messages.send(sender, "player-not-found", "player" to (args.getOrNull(0) ?: ""))
            return true
        }

        val success = inventoryService.saveInventory(target)

        if (success) {
            if (target == sender) {
                messages.send(sender, "inventory-saved-self")
            } else {
                messages.send(sender, "inventory-saved", "player" to target.name)
            }
        } else {
            messages.send(sender, "inventory-save-failed", "player" to target.name)
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        if (args.size == 1 && sender.hasPermission("xinventories.command.save.others")) {
            return Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
        }
        return emptyList()
    }
}

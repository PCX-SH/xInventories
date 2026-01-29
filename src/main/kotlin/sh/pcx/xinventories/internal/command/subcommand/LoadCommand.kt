package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.event.InventoryLoadEvent
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Force loads player inventory.
 */
class LoadCommand : Subcommand {

    override val name = "load"
    override val permission = "xinventories.command.load"
    override val usage = "/xinv load <player> [group]"
    override val description = "Force load inventory"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val inventoryService = plugin.serviceManager.inventoryService
        val groupService = plugin.serviceManager.groupService

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        val target = Bukkit.getPlayer(args[0])
        if (target == null) {
            messages.send(sender, "player-not-found", "player" to args[0])
            return true
        }

        val groupName = args.getOrNull(1)
        val group = if (groupName != null) {
            groupService.getGroupApi(groupName) ?: run {
                messages.send(sender, "group-not-found", "group" to groupName)
                return true
            }
        } else {
            groupService.getGroupForWorldApi(target.world)
        }

        val success = inventoryService.loadInventory(target, group, InventoryLoadEvent.LoadReason.COMMAND)

        if (success) {
            messages.send(sender, "inventory-loaded", "player" to target.name)
        } else {
            messages.send(sender, "inventory-load-failed", "player" to target.name)
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
            2 -> plugin.serviceManager.groupService.getAllGroups()
                .map { it.name }
                .filter { it.lowercase().startsWith(args[1].lowercase()) }
            else -> emptyList()
        }
    }
}

package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * Command to unlock player inventories.
 * Usage: /xinv unlock <player>
 */
class UnlockCommand : Subcommand {

    override val name = "unlock"
    override val aliases = listOf("unfreeze")
    override val permission = "xinventories.command.lock"
    override val usage = "/xinv unlock <player>"
    override val description = "Unlock a player's inventory"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val lockingService = plugin.serviceManager.lockingService
        val config = plugin.configManager.mainConfig.locking

        if (!config.enabled) {
            sender.sendMessage(Component.text("Inventory locking is disabled in config.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        val targetName = args[0]
        val target = Bukkit.getOfflinePlayer(targetName)

        if (!target.hasPlayedBefore() && !target.isOnline) {
            sender.sendMessage(Component.text("Player '$targetName' not found.", NamedTextColor.RED))
            return true
        }

        // Check if locked
        if (!lockingService.isLocked(target.uniqueId)) {
            sender.sendMessage(Component.text()
                .append(Component.text(target.name ?: targetName, NamedTextColor.WHITE))
                .append(Component.text(" is not locked.", NamedTextColor.YELLOW))
                .build())
            return true
        }

        // Unlock
        val success = lockingService.unlockInventory(target.uniqueId)

        if (success) {
            sender.sendMessage(Component.text()
                .append(Component.text("Unlocked inventory for ", NamedTextColor.GREEN))
                .append(Component.text(target.name ?: targetName, NamedTextColor.WHITE, TextDecoration.BOLD))
                .build())

            // Notify the unlocked player if online
            target.player?.let { onlineTarget ->
                onlineTarget.sendMessage(Component.text()
                    .append(Component.text("Your inventory has been unlocked.", NamedTextColor.GREEN))
                    .build())
            }
        } else {
            sender.sendMessage(Component.text("Failed to unlock inventory.", NamedTextColor.RED))
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        if (args.size != 1) return emptyList()

        val lockingService = plugin.serviceManager.lockingService
        val lockedPlayers = lockingService.getLockedPlayers()
            .mapNotNull { Bukkit.getOfflinePlayer(it.playerUuid).name }

        return lockedPlayers.filter { it.lowercase().startsWith(args[0].lowercase()) }
    }
}

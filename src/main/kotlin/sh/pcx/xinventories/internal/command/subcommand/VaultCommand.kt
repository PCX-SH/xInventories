package sh.pcx.xinventories.internal.command.subcommand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.menu.ConfiscationVaultGUI

/**
 * Command for accessing the confiscation vault.
 *
 * Usage:
 * - /xinv vault - View your confiscated items
 * - /xinv vault <player> - View another player's confiscated items (admin)
 * - /xinv vault claim - Claim all your confiscated items
 * - /xinv vault claim <player> - Claim all confiscated items for a player (admin)
 */
class VaultCommand : Subcommand {

    override val name = "vault"
    override val aliases = listOf("confiscated", "confiscation")
    override val permission = "xinventories.command.vault"
    override val usage = "/xinv vault [player|claim]"
    override val description = "View and claim confiscated items"
    override val playerOnly = true

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val player = sender as Player
        val messages = plugin.serviceManager.messageService
        val restrictionService = plugin.serviceManager.restrictionService

        when {
            args.isEmpty() -> {
                // View own vault
                ConfiscationVaultGUI(plugin, player.uniqueId, player.name, false).open(player)
            }

            args[0].equals("claim", ignoreCase = true) -> {
                if (args.size > 1) {
                    // Claim for another player (admin)
                    if (!player.hasPermission("xinventories.command.vault.others")) {
                        messages.send(sender, "no-permission")
                        return true
                    }

                    val targetName = args[1]
                    val targetPlayer = Bukkit.getPlayer(targetName)
                    if (targetPlayer == null) {
                        messages.send(sender, "player-not-found", "player" to targetName)
                        return true
                    }

                    val claimed = restrictionService.claimAllConfiscatedItems(targetPlayer)
                    player.sendMessage(Component.text("Claimed $claimed confiscated items for ${targetPlayer.name}", NamedTextColor.GREEN))
                } else {
                    // Claim own items
                    val claimed = restrictionService.claimAllConfiscatedItems(player)
                    if (claimed > 0) {
                        player.sendMessage(Component.text("Claimed $claimed confiscated items", NamedTextColor.GREEN))
                    } else {
                        player.sendMessage(Component.text("You have no confiscated items to claim", NamedTextColor.YELLOW))
                    }
                }
            }

            args[0].equals("count", ignoreCase = true) -> {
                val targetUuid = if (args.size > 1 && player.hasPermission("xinventories.command.vault.others")) {
                    val targetPlayer = Bukkit.getOfflinePlayer(args[1])
                    targetPlayer.uniqueId
                } else {
                    player.uniqueId
                }

                val count = restrictionService.getConfiscatedItemCount(targetUuid)
                val targetName = if (targetUuid == player.uniqueId) "You have" else "${args[1]} has"
                player.sendMessage(Component.text("$targetName $count confiscated items", NamedTextColor.GOLD))
            }

            else -> {
                // View another player's vault (admin)
                if (!player.hasPermission("xinventories.command.vault.others")) {
                    messages.send(sender, "no-permission")
                    return true
                }

                val targetName = args[0]
                val targetPlayer = Bukkit.getOfflinePlayer(targetName)

                if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                    messages.send(sender, "player-not-found", "player" to targetName)
                    return true
                }

                ConfiscationVaultGUI(
                    plugin,
                    targetPlayer.uniqueId,
                    targetPlayer.name ?: targetName,
                    true
                ).open(player)
            }
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> {
                val completions = mutableListOf("claim", "count")
                if (sender.hasPermission("xinventories.command.vault.others")) {
                    completions.addAll(Bukkit.getOnlinePlayers().map { it.name })
                }
                completions.filter { it.lowercase().startsWith(args[0].lowercase()) }
            }
            2 -> {
                if ((args[0].equals("claim", ignoreCase = true) || args[0].equals("count", ignoreCase = true))
                    && sender.hasPermission("xinventories.command.vault.others")) {
                    Bukkit.getOnlinePlayers().map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                } else {
                    emptyList()
                }
            }
            else -> emptyList()
        }
    }
}

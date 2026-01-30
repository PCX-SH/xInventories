package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.service.EconomyService
import sh.pcx.xinventories.internal.service.GroupService
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command for managing per-group economy balances.
 *
 * Usage:
 * - /xinv balance <player> [group] - View balance
 * - /xinv balance <player> set <group> <amount> - Set balance
 * - /xinv balance <player> transfer <from> <to> <amount> - Transfer between groups
 */
class BalanceCommand(
    private val economyService: EconomyService,
    private val groupService: GroupService
) : Subcommand {

    override val name = "balance"
    override val aliases = listOf("bal", "money")
    override val permission = "xinventories.command.balance"
    override val usage = "/xinv balance <player> [group] | set <group> <amount> | transfer <from> <to> <amount>"
    override val description = "Manage per-group economy balances"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService

        if (!economyService.isEnabled()) {
            messages.send(sender, "economy-disabled")
            return true
        }

        if (args.isEmpty()) {
            // Show own balance if player
            if (sender is Player) {
                showBalance(sender, sender, null, messages)
                return true
            }
            sender.sendMessage("Usage: $usage")
            return true
        }

        // First arg is either a player name or subcommand
        val firstArg = args[0].lowercase()

        when (firstArg) {
            "set" -> {
                if (args.size < 4) {
                    sender.sendMessage("Usage: /xinv balance set <player> <group> <amount>")
                    return true
                }
                return handleSet(sender, args.drop(1).toTypedArray(), messages)
            }
            "transfer" -> {
                if (args.size < 5) {
                    sender.sendMessage("Usage: /xinv balance transfer <player> <from> <to> <amount>")
                    return true
                }
                return handleTransfer(sender, args.drop(1).toTypedArray(), messages)
            }
            else -> {
                // First arg is player name
                val target = Bukkit.getOfflinePlayer(args[0])
                if (!target.hasPlayedBefore() && !target.isOnline) {
                    messages.send(sender, "player-not-found", "player" to args[0])
                    return true
                }

                // Check for nested subcommand
                if (args.size >= 2) {
                    when (args[1].lowercase()) {
                        "set" -> {
                            if (args.size < 4) {
                                sender.sendMessage("Usage: /xinv balance <player> set <group> <amount>")
                                return true
                            }
                            return handleSetForPlayer(sender, target.uniqueId, args.drop(2).toTypedArray(), messages)
                        }
                        "transfer" -> {
                            if (args.size < 5) {
                                sender.sendMessage("Usage: /xinv balance <player> transfer <from> <to> <amount>")
                                return true
                            }
                            return handleTransferForPlayer(sender, target.uniqueId, args.drop(2).toTypedArray(), messages)
                        }
                        else -> {
                            // Second arg is group name
                            val group = args[1]
                            if (groupService.getGroup(group) == null) {
                                messages.send(sender, "group-not-found", "group" to group)
                                return true
                            }
                            showBalance(sender, target, group, messages)
                            return true
                        }
                    }
                }

                // Show all balances for player
                showBalance(sender, target, null, messages)
                return true
            }
        }
    }

    private fun showBalance(
        sender: CommandSender,
        target: org.bukkit.OfflinePlayer,
        group: String?,
        messages: sh.pcx.xinventories.internal.service.MessageService
    ) {
        val isSelf = sender is Player && sender.uniqueId == target.uniqueId

        if (group != null) {
            // Show specific group balance
            val balance = economyService.getBalance(target.uniqueId, group)
            if (isSelf) {
                sender.sendMessage("${formatCurrency(balance)} in group '$group'")
            } else {
                sender.sendMessage("${target.name}'s balance in '$group': ${formatCurrency(balance)}")
            }
        } else {
            // Show all balances
            val balances = economyService.getAllBalances(target.uniqueId)

            if (balances.isEmpty()) {
                sender.sendMessage("No balances found.")
                return
            }

            if (isSelf) {
                sender.sendMessage("Your balances:")
            } else {
                sender.sendMessage("${target.name}'s balances:")
            }

            balances.forEach { (groupName, balance) ->
                sender.sendMessage("  $groupName: ${formatCurrency(balance)}")
            }

            val total = balances.values.sum()
            sender.sendMessage("  Total: ${formatCurrency(total)}")
        }
    }

    private fun handleSet(
        sender: CommandSender,
        args: Array<String>,
        messages: sh.pcx.xinventories.internal.service.MessageService
    ): Boolean {
        if (!sender.hasPermission("xinventories.command.balance.set")) {
            messages.send(sender, "no-permission")
            return true
        }

        // args: player, group, amount
        val target = Bukkit.getOfflinePlayer(args[0])
        if (!target.hasPlayedBefore() && !target.isOnline) {
            messages.send(sender, "player-not-found", "player" to args[0])
            return true
        }

        return handleSetForPlayer(sender, target.uniqueId, args.drop(1).toTypedArray(), messages)
    }

    private fun handleSetForPlayer(
        sender: CommandSender,
        targetUuid: java.util.UUID,
        args: Array<String>,
        messages: sh.pcx.xinventories.internal.service.MessageService
    ): Boolean {
        if (!sender.hasPermission("xinventories.command.balance.set")) {
            messages.send(sender, "no-permission")
            return true
        }

        // args: group, amount
        val group = args[0]
        val amount = args.getOrNull(1)?.toDoubleOrNull()

        if (groupService.getGroup(group) == null) {
            messages.send(sender, "group-not-found", "group" to group)
            return true
        }

        if (amount == null || amount < 0) {
            sender.sendMessage("Invalid amount. Must be a positive number.")
            return true
        }

        val target = Bukkit.getOfflinePlayer(targetUuid)
        val success = economyService.setBalance(targetUuid, group, amount)

        if (success) {
            sender.sendMessage("Set ${target.name}'s balance in '$group' to ${formatCurrency(amount)}")
        } else {
            sender.sendMessage("Failed to set balance.")
        }

        return true
    }

    private fun handleTransfer(
        sender: CommandSender,
        args: Array<String>,
        messages: sh.pcx.xinventories.internal.service.MessageService
    ): Boolean {
        if (!sender.hasPermission("xinventories.command.balance.transfer")) {
            messages.send(sender, "no-permission")
            return true
        }

        // args: player, from, to, amount
        val target = Bukkit.getOfflinePlayer(args[0])
        if (!target.hasPlayedBefore() && !target.isOnline) {
            messages.send(sender, "player-not-found", "player" to args[0])
            return true
        }

        return handleTransferForPlayer(sender, target.uniqueId, args.drop(1).toTypedArray(), messages)
    }

    private fun handleTransferForPlayer(
        sender: CommandSender,
        targetUuid: java.util.UUID,
        args: Array<String>,
        messages: sh.pcx.xinventories.internal.service.MessageService
    ): Boolean {
        if (!sender.hasPermission("xinventories.command.balance.transfer")) {
            messages.send(sender, "no-permission")
            return true
        }

        // args: from, to, amount
        val fromGroup = args[0]
        val toGroup = args[1]
        val amount = args.getOrNull(2)?.toDoubleOrNull()

        if (groupService.getGroup(fromGroup) == null) {
            messages.send(sender, "group-not-found", "group" to fromGroup)
            return true
        }

        if (groupService.getGroup(toGroup) == null) {
            messages.send(sender, "group-not-found", "group" to toGroup)
            return true
        }

        if (amount == null || amount <= 0) {
            sender.sendMessage("Invalid amount. Must be a positive number.")
            return true
        }

        val target = Bukkit.getOfflinePlayer(targetUuid)
        val success = economyService.transfer(targetUuid, fromGroup, toGroup, amount)

        if (success) {
            sender.sendMessage("Transferred ${formatCurrency(amount)} from '$fromGroup' to '$toGroup' for ${target.name}")
        } else {
            sender.sendMessage("Failed to transfer. Insufficient funds or invalid groups.")
        }

        return true
    }

    private fun formatCurrency(amount: Double): String {
        return String.format("$%.2f", amount)
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> {
                // Player names or subcommands
                val completions = mutableListOf("set", "transfer")
                completions.addAll(Bukkit.getOnlinePlayers().map { it.name })
                completions.filter { it.lowercase().startsWith(args[0].lowercase()) }
            }
            2 -> {
                when (args[0].lowercase()) {
                    "set", "transfer" -> {
                        // Player name
                        Bukkit.getOnlinePlayers().map { it.name }
                            .filter { it.lowercase().startsWith(args[1].lowercase()) }
                    }
                    else -> {
                        // Group name or subcommands for player
                        val completions = mutableListOf("set", "transfer")
                        completions.addAll(groupService.getAllGroups().map { it.name })
                        completions.filter { it.lowercase().startsWith(args[1].lowercase()) }
                    }
                }
            }
            3 -> {
                when (args[0].lowercase()) {
                    "set" -> {
                        // Group name
                        groupService.getAllGroups().map { it.name }
                            .filter { it.lowercase().startsWith(args[2].lowercase()) }
                    }
                    "transfer" -> {
                        // From group
                        groupService.getAllGroups().map { it.name }
                            .filter { it.lowercase().startsWith(args[2].lowercase()) }
                    }
                    else -> {
                        when (args[1].lowercase()) {
                            "set" -> {
                                groupService.getAllGroups().map { it.name }
                                    .filter { it.lowercase().startsWith(args[2].lowercase()) }
                            }
                            "transfer" -> {
                                groupService.getAllGroups().map { it.name }
                                    .filter { it.lowercase().startsWith(args[2].lowercase()) }
                            }
                            else -> emptyList()
                        }
                    }
                }
            }
            4 -> {
                when (args[0].lowercase()) {
                    "set" -> listOf("100", "1000", "10000")
                    "transfer" -> {
                        // To group
                        groupService.getAllGroups().map { it.name }
                            .filter { it.lowercase().startsWith(args[3].lowercase()) }
                    }
                    else -> {
                        when (args[1].lowercase()) {
                            "set" -> listOf("100", "1000", "10000")
                            "transfer" -> {
                                groupService.getAllGroups().map { it.name }
                                    .filter { it.lowercase().startsWith(args[3].lowercase()) }
                            }
                            else -> emptyList()
                        }
                    }
                }
            }
            5 -> {
                when (args[0].lowercase()) {
                    "transfer" -> listOf("100", "1000", "10000")
                    else -> {
                        if (args[1].lowercase() == "transfer") {
                            listOf("100", "1000", "10000")
                        } else {
                            emptyList()
                        }
                    }
                }
            }
            else -> emptyList()
        }
    }
}

package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.InventoryLock
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.Duration

/**
 * Command to lock player inventories.
 * Usage: /xinv lock <player> [reason] [duration]
 *        /xinv lock list
 *        /xinv lock status <player>
 */
class LockCommand : Subcommand {

    override val name = "lock"
    override val aliases = listOf("freeze")
    override val permission = "xinventories.command.lock"
    override val usage = "/xinv lock <player|list|status> [reason] [duration]"
    override val description = "Lock a player's inventory"

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

        when (args[0].lowercase()) {
            "list" -> {
                // List all locked players
                val locks = lockingService.getLockedPlayers()

                if (locks.isEmpty()) {
                    sender.sendMessage(Component.text("No players are currently locked.", NamedTextColor.YELLOW))
                    return true
                }

                sender.sendMessage(Component.text()
                    .append(Component.text("=== ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("Locked Players (${locks.size})", NamedTextColor.GOLD, TextDecoration.BOLD))
                    .append(Component.text(" ===", NamedTextColor.DARK_GRAY))
                    .build())

                for (lock in locks) {
                    val playerName = Bukkit.getOfflinePlayer(lock.playerUuid).name ?: lock.playerUuid.toString()
                    val lockedByName = lock.lockedBy?.let { Bukkit.getOfflinePlayer(it).name } ?: "System"

                    sender.sendMessage(Component.text()
                        .append(Component.text("  - ", NamedTextColor.DARK_GRAY))
                        .append(Component.text(playerName, NamedTextColor.WHITE))
                        .append(Component.text(" by ", NamedTextColor.GRAY))
                        .append(Component.text(lockedByName, NamedTextColor.AQUA))
                        .append(Component.text(" (", NamedTextColor.GRAY))
                        .append(Component.text(lock.getRemainingTimeString(), NamedTextColor.YELLOW))
                        .append(Component.text(")", NamedTextColor.GRAY))
                        .build())

                    lock.reason?.let { reason ->
                        sender.sendMessage(Component.text()
                            .append(Component.text("    Reason: ", NamedTextColor.DARK_GRAY))
                            .append(Component.text(reason, NamedTextColor.GRAY))
                            .build())
                    }
                }

                return true
            }

            "status" -> {
                // Check status of a specific player
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv lock status <player>")
                    return true
                }

                val targetName = args[1]
                val target = Bukkit.getOfflinePlayer(targetName)

                if (!target.hasPlayedBefore() && !target.isOnline) {
                    sender.sendMessage(Component.text("Player '$targetName' not found.", NamedTextColor.RED))
                    return true
                }

                val lock = lockingService.getLock(target.uniqueId)

                if (lock == null) {
                    sender.sendMessage(Component.text()
                        .append(Component.text(target.name ?: targetName, NamedTextColor.WHITE))
                        .append(Component.text(" is not locked.", NamedTextColor.GREEN))
                        .build())
                } else {
                    val lockedByName = lock.lockedBy?.let { Bukkit.getOfflinePlayer(it).name } ?: "System"

                    sender.sendMessage(Component.text()
                        .append(Component.text(target.name ?: targetName, NamedTextColor.WHITE))
                        .append(Component.text(" is ", NamedTextColor.GRAY))
                        .append(Component.text("LOCKED", NamedTextColor.RED, TextDecoration.BOLD))
                        .build())

                    sender.sendMessage(Component.text()
                        .append(Component.text("  Locked by: ", NamedTextColor.GRAY))
                        .append(Component.text(lockedByName, NamedTextColor.AQUA))
                        .build())

                    sender.sendMessage(Component.text()
                        .append(Component.text("  Expires: ", NamedTextColor.GRAY))
                        .append(Component.text(lock.getRemainingTimeString(), NamedTextColor.YELLOW))
                        .build())

                    sender.sendMessage(Component.text()
                        .append(Component.text("  Scope: ", NamedTextColor.GRAY))
                        .append(Component.text(lock.scope.name, NamedTextColor.WHITE))
                        .build())

                    lock.reason?.let { reason ->
                        sender.sendMessage(Component.text()
                            .append(Component.text("  Reason: ", NamedTextColor.GRAY))
                            .append(Component.text(reason, NamedTextColor.WHITE))
                            .build())
                    }
                }

                return true
            }

            else -> {
                // Lock a player: /xinv lock <player> [reason] [duration]
                val targetName = args[0]
                val target = Bukkit.getOfflinePlayer(targetName)

                if (!target.hasPlayedBefore() && !target.isOnline) {
                    sender.sendMessage(Component.text("Player '$targetName' not found.", NamedTextColor.RED))
                    return true
                }

                // Check if already locked
                if (lockingService.isLocked(target.uniqueId)) {
                    sender.sendMessage(Component.text()
                        .append(Component.text(target.name ?: targetName, NamedTextColor.WHITE))
                        .append(Component.text(" is already locked.", NamedTextColor.RED))
                        .build())
                    return true
                }

                // Parse optional arguments
                var reason: String? = null
                var duration: Duration? = null

                if (args.size >= 2) {
                    // Check if second arg is a duration
                    val parsed = InventoryLock.parseDuration(args[1])
                    if (parsed != null) {
                        duration = parsed
                        // Remaining args are reason
                        if (args.size > 2) {
                            reason = args.drop(2).joinToString(" ")
                        }
                    } else {
                        // Check if last arg is duration
                        val lastArg = args.last()
                        val lastParsed = InventoryLock.parseDuration(lastArg)
                        if (lastParsed != null && args.size > 2) {
                            duration = lastParsed
                            reason = args.drop(1).dropLast(1).joinToString(" ")
                        } else {
                            reason = args.drop(1).joinToString(" ")
                        }
                    }
                }

                // Get locker UUID
                val lockedBy = if (sender is Player) sender.uniqueId else null

                // Create lock
                val lock = lockingService.lockInventory(target.uniqueId, lockedBy, duration, reason)

                if (lock == null) {
                    sender.sendMessage(Component.text("Failed to lock inventory (may have been cancelled).", NamedTextColor.RED))
                    return true
                }

                // Success message
                sender.sendMessage(Component.text()
                    .append(Component.text("Locked inventory for ", NamedTextColor.GREEN))
                    .append(Component.text(target.name ?: targetName, NamedTextColor.WHITE, TextDecoration.BOLD))
                    .build())

                sender.sendMessage(Component.text()
                    .append(Component.text("  Duration: ", NamedTextColor.GRAY))
                    .append(Component.text(lock.getRemainingTimeString(), NamedTextColor.YELLOW))
                    .build())

                if (reason != null) {
                    sender.sendMessage(Component.text()
                        .append(Component.text("  Reason: ", NamedTextColor.GRAY))
                        .append(Component.text(reason, NamedTextColor.WHITE))
                        .build())
                }

                // Notify the locked player if online
                target.player?.let { onlineTarget ->
                    lockingService.showLockMessage(onlineTarget)
                }

                return true
            }
        }
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> {
                val options = mutableListOf("list", "status")
                options.addAll(Bukkit.getOnlinePlayers().map { it.name })
                options.filter { it.lowercase().startsWith(args[0].lowercase()) }
            }
            2 -> when (args[0].lowercase()) {
                "status" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                "list" -> emptyList()
                else -> listOf("30s", "5m", "1h", "1d")
                    .filter { it.startsWith(args[1].lowercase()) }
            }
            else -> emptyList()
        }
    }
}

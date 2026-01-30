package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.toReadableSize
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Command for exporting player inventory data to JSON.
 *
 * Usage:
 * - /xinv export <player> [group] [file]
 * - /xinv export all <group> [file]
 * - /xinv export list
 */
class ExportCommand : Subcommand {

    override val name = "export"
    override val aliases = listOf("exp")
    override val permission = "xinventories.admin.export"
    override val usage = "/xinv export <player|all|list> [group] [file]"
    override val description = "Export player inventory data to JSON"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val exportService = plugin.serviceManager.exportService

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        val subcommand = args[0].lowercase()

        when (subcommand) {
            "list" -> {
                // List available export files
                val files = exportService.listExportFiles()

                messages.sendRaw(sender, "export-list-header")

                if (files.isEmpty()) {
                    messages.sendRaw(sender, "export-list-empty")
                } else {
                    for (file in files.take(20)) {
                        val timestamp = Instant.ofEpochMilli(file.lastModified())
                        messages.sendRaw(sender, "export-list-entry",
                            "name" to file.name,
                            "time" to dateFormatter.format(timestamp),
                            "size" to file.length().toReadableSize()
                        )
                    }

                    if (files.size > 20) {
                        sender.sendMessage("... and ${files.size - 20} more files")
                    }
                }

                sender.sendMessage("")
                sender.sendMessage("Export directory: ${exportService.getExportDirectory().absolutePath}")

                return true
            }

            "all" -> {
                // Export all players in a group
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv export all <group> [file]")
                    return true
                }

                val groupName = args[1]
                val fileName = args.getOrNull(2)

                // Validate group exists
                if (plugin.serviceManager.groupService.getGroup(groupName) == null) {
                    messages.send(sender, "group-not-found", "group" to groupName)
                    return true
                }

                messages.sendRaw(sender, "export-in-progress", "target" to "group $groupName")

                val result = exportService.exportGroup(groupName, fileName)

                if (result.success) {
                    messages.sendRaw(sender, "export-success",
                        "count" to result.playerCount.toString(),
                        "file" to (result.filePath ?: "unknown")
                    )
                } else {
                    messages.sendRaw(sender, "export-failed", "error" to result.message)
                }

                return true
            }

            else -> {
                // Export single player: /xinv export <player> [group] [file]
                val playerName = args[0]
                val groupName = args.getOrNull(1)
                val fileName = args.getOrNull(2)

                // Find player (offline lookup)
                @Suppress("DEPRECATION")
                val targetPlayer = Bukkit.getOfflinePlayer(playerName)
                if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                    messages.send(sender, "player-not-found", "player" to playerName)
                    return true
                }

                // Determine group
                val group = if (groupName != null) {
                    if (plugin.serviceManager.groupService.getGroup(groupName) == null) {
                        messages.send(sender, "group-not-found", "group" to groupName)
                        return true
                    }
                    groupName
                } else {
                    // Get player's current group if online, otherwise require group parameter
                    val onlinePlayer = targetPlayer.player
                    if (onlinePlayer != null) {
                        plugin.serviceManager.inventoryService.getCurrentGroup(onlinePlayer)
                            ?: plugin.serviceManager.groupService.getGroupForWorld(onlinePlayer.world).name
                    } else {
                        // Player is offline, need to specify group
                        messages.sendRaw(sender, "export-group-required")
                        return true
                    }
                }

                messages.sendRaw(sender, "export-in-progress", "target" to "$playerName in $group")

                val result = exportService.exportPlayer(
                    playerUUID = targetPlayer.uniqueId,
                    group = group,
                    fileName = fileName
                )

                if (result.success) {
                    messages.sendRaw(sender, "export-player-success",
                        "player" to playerName,
                        "group" to group,
                        "file" to (result.filePath ?: "unknown")
                    )
                } else {
                    messages.sendRaw(sender, "export-failed", "error" to result.message)
                }

                return true
            }
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("Export inventory data to JSON:")
        sender.sendMessage("")
        sender.sendMessage("  /xinv export <player> [group] [file]")
        sender.sendMessage("    Export a single player's inventory")
        sender.sendMessage("    Group defaults to player's current group if online")
        sender.sendMessage("")
        sender.sendMessage("  /xinv export all <group> [file]")
        sender.sendMessage("    Export all players in a group")
        sender.sendMessage("")
        sender.sendMessage("  /xinv export list")
        sender.sendMessage("    List available export files")
        sender.sendMessage("")
        sender.sendMessage("Files are saved to: plugins/xInventories/exports/")
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> {
                val options = mutableListOf("all", "list")
                options.addAll(Bukkit.getOnlinePlayers().map { it.name })
                options.filter { it.lowercase().startsWith(args[0].lowercase()) }
            }
            2 -> {
                if (args[0].lowercase() == "list") {
                    emptyList()
                } else {
                    // Group names
                    plugin.serviceManager.groupService.getAllGroups()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                }
            }
            3 -> {
                // File name - no completion
                emptyList()
            }
            else -> emptyList()
        }
    }
}

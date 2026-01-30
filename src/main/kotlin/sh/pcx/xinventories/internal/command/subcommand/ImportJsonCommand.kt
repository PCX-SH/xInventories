package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * Command for importing player inventory data from JSON.
 *
 * Usage:
 * - /xinv importjson <player> <file> [group] [--overwrite]
 * - /xinv importjson validate <file>
 * - /xinv importjson bulk <file> [group] [--overwrite]
 */
class ImportJsonCommand : Subcommand {

    override val name = "importjson"
    override val aliases = listOf("impjson", "jsonimport")
    override val permission = "xinventories.admin.import"
    override val usage = "/xinv importjson <player|validate|bulk> <file> [group] [--overwrite]"
    override val description = "Import player inventory data from JSON"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val exportService = plugin.serviceManager.exportService

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        val subcommand = args[0].lowercase()

        when (subcommand) {
            "validate" -> {
                // Validate a file without importing
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv importjson validate <file>")
                    return true
                }

                val filePath = args[1]

                messages.sendRaw(sender, "import-validating", "file" to filePath)

                val result = exportService.validateImport(filePath)

                if (result.valid) {
                    messages.sendRaw(sender, "import-validation-success")
                    sender.sendMessage("")
                    sender.sendMessage("  Version: ${result.version}")
                    sender.sendMessage("  Players: ${result.playerCount}")
                    sender.sendMessage("  Group: ${result.groupName ?: "N/A"}")

                    if (result.warnings.isNotEmpty()) {
                        sender.sendMessage("")
                        sender.sendMessage("Warnings:")
                        result.warnings.forEach { warning ->
                            sender.sendMessage("  - $warning")
                        }
                    }

                    sender.sendMessage("")
                    sender.sendMessage("File is valid and ready for import.")
                } else {
                    messages.sendRaw(sender, "import-validation-failed")
                    sender.sendMessage("")
                    sender.sendMessage("Errors:")
                    result.errors.forEach { error ->
                        sender.sendMessage("  - $error")
                    }
                }

                return true
            }

            "bulk" -> {
                // Import a bulk export file
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv importjson bulk <file> [group] [--overwrite]")
                    return true
                }

                val filePath = args[1]
                var targetGroup: String? = null
                var overwrite = false

                // Parse remaining arguments
                for (i in 2 until args.size) {
                    val arg = args[i]
                    when {
                        arg == "--overwrite" || arg == "-o" -> overwrite = true
                        !arg.startsWith("-") && targetGroup == null -> targetGroup = arg
                    }
                }

                // Validate target group if provided
                if (targetGroup != null && plugin.serviceManager.groupService.getGroup(targetGroup) == null) {
                    messages.send(sender, "group-not-found", "group" to targetGroup)
                    return true
                }

                messages.sendRaw(sender, "import-in-progress", "file" to filePath)

                val result = exportService.importFromFile(
                    filePath = filePath,
                    targetGroup = targetGroup,
                    overwrite = overwrite
                )

                if (result.success) {
                    messages.sendRaw(sender, "import-success",
                        "imported" to result.playersImported.toString(),
                        "skipped" to result.playersSkipped.toString(),
                        "failed" to result.playersFailed.toString()
                    )
                } else {
                    messages.sendRaw(sender, "import-failed", "error" to result.message)
                }

                if (result.errors.isNotEmpty()) {
                    sender.sendMessage("")
                    sender.sendMessage("Errors (${result.errors.size}):")
                    result.errors.take(5).forEach { error ->
                        sender.sendMessage("  - $error")
                    }
                    if (result.errors.size > 5) {
                        sender.sendMessage("  ... and ${result.errors.size - 5} more")
                    }
                }

                return true
            }

            else -> {
                // Import single player: /xinv importjson <player> <file> [group] [--overwrite]
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv importjson <player> <file> [group] [--overwrite]")
                    return true
                }

                val playerName = args[0]
                val filePath = args[1]
                var targetGroup: String? = null
                var overwrite = false

                // Parse remaining arguments
                for (i in 2 until args.size) {
                    val arg = args[i]
                    when {
                        arg == "--overwrite" || arg == "-o" -> overwrite = true
                        !arg.startsWith("-") && targetGroup == null -> targetGroup = arg
                    }
                }

                // Find player (offline lookup)
                @Suppress("DEPRECATION")
                val targetPlayer = Bukkit.getOfflinePlayer(playerName)
                if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                    messages.send(sender, "player-not-found", "player" to playerName)
                    return true
                }

                // Validate target group if provided
                if (targetGroup != null && plugin.serviceManager.groupService.getGroup(targetGroup) == null) {
                    messages.send(sender, "group-not-found", "group" to targetGroup)
                    return true
                }

                messages.sendRaw(sender, "import-player-in-progress",
                    "player" to playerName,
                    "file" to filePath
                )

                val result = exportService.importFromFile(
                    filePath = filePath,
                    targetPlayerUUID = targetPlayer.uniqueId,
                    targetGroup = targetGroup,
                    overwrite = overwrite
                )

                if (result.success) {
                    if (result.playersImported > 0) {
                        messages.sendRaw(sender, "import-player-success",
                            "player" to playerName,
                            "group" to (targetGroup ?: "original")
                        )
                    } else if (result.playersSkipped > 0) {
                        messages.sendRaw(sender, "import-player-skipped",
                            "player" to playerName
                        )
                    }
                } else {
                    messages.sendRaw(sender, "import-failed", "error" to result.message)

                    if (result.errors.isNotEmpty()) {
                        result.errors.forEach { error ->
                            sender.sendMessage("  - $error")
                        }
                    }
                }

                return true
            }
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("Import inventory data from JSON:")
        sender.sendMessage("")
        sender.sendMessage("  /xinv importjson <player> <file> [group] [--overwrite]")
        sender.sendMessage("    Import data for a specific player")
        sender.sendMessage("    Use --overwrite to replace existing data")
        sender.sendMessage("")
        sender.sendMessage("  /xinv importjson bulk <file> [group] [--overwrite]")
        sender.sendMessage("    Import a bulk export file (multiple players)")
        sender.sendMessage("")
        sender.sendMessage("  /xinv importjson validate <file>")
        sender.sendMessage("    Validate a file without importing")
        sender.sendMessage("")
        sender.sendMessage("Files are read from: plugins/xInventories/exports/")
        sender.sendMessage("Or specify an absolute path")
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        val exportService = plugin.serviceManager.exportService

        return when (args.size) {
            1 -> {
                val options = mutableListOf("validate", "bulk")
                options.addAll(Bukkit.getOnlinePlayers().map { it.name })
                options.filter { it.lowercase().startsWith(args[0].lowercase()) }
            }
            2 -> {
                // File names from exports directory
                exportService.listExportFiles()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
            }
            3 -> {
                if (args[0].lowercase() == "validate") {
                    emptyList()
                } else {
                    // Group names + --overwrite
                    val options = mutableListOf("--overwrite")
                    options.addAll(plugin.serviceManager.groupService.getAllGroups().map { it.name })
                    options.filter { it.lowercase().startsWith(args[2].lowercase()) }
                }
            }
            4 -> {
                listOf("--overwrite").filter { it.lowercase().startsWith(args[3].lowercase()) }
            }
            else -> emptyList()
        }
    }
}

package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.menu.MergePreviewGUI
import sh.pcx.xinventories.internal.service.MergeStrategy
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command for merging inventories between groups.
 *
 * Usage:
 * - /xinv merge <player> <source-group> <target-group> [strategy] [--preview]
 * - /xinv merge confirm
 * - /xinv merge cancel
 */
class MergeCommand : Subcommand {

    override val name = "merge"
    override val aliases = listOf("combine")
    override val permission = "xinventories.admin.merge"
    override val usage = "/xinv merge <player> <source-group> <target-group> [strategy] [--preview]"
    override val description = "Merge inventories between groups"
    override val playerOnly = false

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val mergeService = plugin.serviceManager.mergeService

        if (args.isEmpty()) {
            showHelp(sender)
            return true
        }

        val subcommand = args[0].lowercase()

        when (subcommand) {
            "confirm" -> {
                if (sender !is Player) {
                    messages.send(sender, "player-only")
                    return true
                }

                val pending = mergeService.getPendingMerge(sender.uniqueId)
                if (pending == null) {
                    messages.sendRaw(sender, "merge-no-pending")
                    return true
                }

                // Check if using MANUAL strategy and conflicts are unresolved
                if (pending.strategy == MergeStrategy.MANUAL && !mergeService.areAllConflictsResolved(sender.uniqueId)) {
                    val unresolved = pending.conflicts.count { it.resolution == sh.pcx.xinventories.internal.service.ConflictResolution.PENDING }
                    messages.sendRaw(sender, "merge-unresolved-conflicts", "count" to unresolved.toString())
                    return true
                }

                val result = mergeService.confirmMerge(sender.uniqueId)

                if (result.success) {
                    messages.sendRaw(sender, "merge-confirmed",
                        "player" to pending.sourceData.playerName,
                        "source" to pending.sourceGroup,
                        "target" to pending.targetGroup
                    )

                    if (result.overflowItems.isNotEmpty()) {
                        messages.sendRaw(sender, "merge-overflow-warning", "count" to result.overflowItems.size.toString())
                    }
                } else {
                    messages.sendRaw(sender, "merge-failed", "error" to result.message)
                }

                return true
            }

            "cancel" -> {
                if (sender !is Player) {
                    messages.send(sender, "player-only")
                    return true
                }

                val cancelled = mergeService.cancelMerge(sender.uniqueId)
                if (cancelled) {
                    messages.sendRaw(sender, "merge-cancelled")
                } else {
                    messages.sendRaw(sender, "merge-no-pending")
                }

                return true
            }

            "gui" -> {
                if (sender !is Player) {
                    messages.send(sender, "player-only")
                    return true
                }

                val pending = mergeService.getPendingMerge(sender.uniqueId)
                if (pending == null) {
                    messages.sendRaw(sender, "merge-no-pending")
                    return true
                }

                MergePreviewGUI(plugin, sender, pending).open(sender)
                return true
            }

            else -> {
                // Parse merge command: <player> <source-group> <target-group> [strategy] [--preview]
                if (args.size < 3) {
                    messages.send(sender, "invalid-syntax", "usage" to usage)
                    return true
                }

                val targetPlayerName = args[0]
                val sourceGroup = args[1]
                val targetGroup = args[2]

                // Parse optional arguments
                var strategy = MergeStrategy.COMBINE
                var previewMode = false

                for (i in 3 until args.size) {
                    val arg = args[i].lowercase()
                    when {
                        arg == "--preview" || arg == "-p" -> previewMode = true
                        arg == "combine" -> strategy = MergeStrategy.COMBINE
                        arg == "replace" -> strategy = MergeStrategy.REPLACE
                        arg == "keep_higher" || arg == "keephigher" -> strategy = MergeStrategy.KEEP_HIGHER
                        arg == "manual" -> strategy = MergeStrategy.MANUAL
                    }
                }

                // Find target player (offline lookup)
                @Suppress("DEPRECATION")
                val targetPlayer = Bukkit.getOfflinePlayer(targetPlayerName)
                if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                    messages.send(sender, "player-not-found", "player" to targetPlayerName)
                    return true
                }

                // Validate groups exist
                val groupService = plugin.serviceManager.groupService
                if (groupService.getGroup(sourceGroup) == null) {
                    messages.send(sender, "group-not-found", "group" to sourceGroup)
                    return true
                }
                if (groupService.getGroup(targetGroup) == null) {
                    messages.send(sender, "group-not-found", "group" to targetGroup)
                    return true
                }

                if (sourceGroup == targetGroup) {
                    messages.sendRaw(sender, "merge-same-group")
                    return true
                }

                val adminUUID = if (sender is Player) sender.uniqueId else java.util.UUID.fromString("00000000-0000-0000-0000-000000000000")

                // Always preview first
                val result = mergeService.previewMerge(
                    adminUUID = adminUUID,
                    targetPlayerUUID = targetPlayer.uniqueId,
                    sourceGroup = sourceGroup,
                    targetGroup = targetGroup,
                    strategy = strategy
                )

                // Show preview results
                messages.sendRaw(sender, "merge-preview-header",
                    "player" to (targetPlayer.name ?: targetPlayerName),
                    "source" to sourceGroup,
                    "target" to targetGroup,
                    "strategy" to strategy.name
                )

                sender.sendMessage("")

                if (result.conflicts.isNotEmpty()) {
                    messages.sendRaw(sender, "merge-conflicts-found", "count" to result.conflicts.size.toString())
                }

                if (result.overflowItems.isNotEmpty()) {
                    messages.sendRaw(sender, "merge-overflow-preview", "count" to result.overflowItems.size.toString())
                }

                sender.sendMessage(result.message)
                sender.sendMessage("")

                if (result.success) {
                    if (previewMode || strategy == MergeStrategy.MANUAL && result.conflicts.isNotEmpty()) {
                        if (sender is Player && strategy == MergeStrategy.MANUAL && result.conflicts.isNotEmpty()) {
                            messages.sendRaw(sender, "merge-use-gui")
                        } else {
                            messages.sendRaw(sender, "merge-confirm-prompt")
                        }
                    } else {
                        // Execute immediately if not preview and no manual resolution needed
                        val execResult = mergeService.confirmMerge(adminUUID)
                        if (execResult.success) {
                            messages.sendRaw(sender, "merge-confirmed",
                                "player" to (targetPlayer.name ?: targetPlayerName),
                                "source" to sourceGroup,
                                "target" to targetGroup
                            )
                        } else {
                            messages.sendRaw(sender, "merge-failed", "error" to execResult.message)
                        }
                    }
                } else {
                    messages.sendRaw(sender, "merge-failed", "error" to result.message)
                }

                return true
            }
        }
    }

    private fun showHelp(sender: CommandSender) {
        sender.sendMessage("Merge inventories between groups:")
        sender.sendMessage("")
        sender.sendMessage("  /xinv merge <player> <source> <target> [strategy] [--preview]")
        sender.sendMessage("    Merge player's inventory from source group to target group")
        sender.sendMessage("")
        sender.sendMessage("  Strategies:")
        sender.sendMessage("    combine     - Add items, stack where possible (default)")
        sender.sendMessage("    replace     - Replace target with source")
        sender.sendMessage("    keep_higher - Keep item with higher count per slot")
        sender.sendMessage("    manual      - Resolve conflicts manually via GUI")
        sender.sendMessage("")
        sender.sendMessage("  /xinv merge confirm")
        sender.sendMessage("    Confirm a pending merge operation")
        sender.sendMessage("")
        sender.sendMessage("  /xinv merge cancel")
        sender.sendMessage("    Cancel a pending merge operation")
        sender.sendMessage("")
        sender.sendMessage("  /xinv merge gui")
        sender.sendMessage("    Open merge preview GUI for manual resolution")
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> {
                val options = mutableListOf("confirm", "cancel", "gui")
                options.addAll(Bukkit.getOnlinePlayers().map { it.name })
                options.filter { it.lowercase().startsWith(args[0].lowercase()) }
            }
            2 -> {
                // Source group
                if (args[0].lowercase() in listOf("confirm", "cancel", "gui")) {
                    emptyList()
                } else {
                    plugin.serviceManager.groupService.getAllGroups()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                }
            }
            3 -> {
                // Target group
                if (args[0].lowercase() in listOf("confirm", "cancel", "gui")) {
                    emptyList()
                } else {
                    plugin.serviceManager.groupService.getAllGroups()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) && it != args[1] }
                }
            }
            4 -> {
                // Strategy
                listOf("combine", "replace", "keep_higher", "manual", "--preview")
                    .filter { it.lowercase().startsWith(args[3].lowercase()) }
            }
            5 -> {
                // --preview if strategy was provided
                listOf("--preview")
                    .filter { it.lowercase().startsWith(args[4].lowercase()) }
            }
            else -> emptyList()
        }
    }
}

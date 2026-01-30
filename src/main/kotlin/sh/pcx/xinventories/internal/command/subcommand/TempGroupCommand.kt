package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.event.TemporaryGroupRemoveEvent
import sh.pcx.xinventories.internal.model.TemporaryGroupAssignment
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Command for managing temporary group assignments.
 *
 * Allows admins to assign players to temporary groups for events,
 * minigames, or other time-limited scenarios.
 */
class TempGroupCommand : Subcommand {

    override val name = "tempgroup"
    override val aliases = listOf("tg", "tempg")
    override val permission = "xinventories.admin.tempgroup"
    override val usage = "/xinv tempgroup <assign|remove|list|extend>"
    override val description = "Manage temporary group assignments"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val tempGroupService = plugin.serviceManager.temporaryGroupService

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        when (args[0].lowercase()) {
            "assign" -> handleAssign(plugin, sender, args, tempGroupService)
            "remove" -> handleRemove(plugin, sender, args, tempGroupService)
            "list" -> handleList(plugin, sender, args, tempGroupService)
            "extend" -> handleExtend(plugin, sender, args, tempGroupService)
            "info" -> handleInfo(plugin, sender, args, tempGroupService)
            else -> messages.send(sender, "invalid-syntax", "usage" to usage)
        }

        return true
    }

    private suspend fun handleAssign(
        plugin: PluginContext,
        sender: CommandSender,
        args: Array<String>,
        tempGroupService: sh.pcx.xinventories.internal.service.TemporaryGroupService
    ) {
        val messages = plugin.serviceManager.messageService

        // /xinv tempgroup assign <player> <group> <duration>
        if (args.size < 4) {
            messages.send(sender, "invalid-syntax",
                "usage" to "/xinv tempgroup assign <player> <group> <duration>"
            )
            return
        }

        val playerName = args[1]
        val groupName = args[2]
        val durationStr = args[3]

        // Find the player
        val player = Bukkit.getPlayerExact(playerName)
        if (player == null) {
            messages.send(sender, "player-not-online", "player" to playerName)
            return
        }

        // Validate group exists
        val group = plugin.serviceManager.groupService.getGroup(groupName)
        if (group == null) {
            messages.send(sender, "group-not-found", "group" to groupName)
            return
        }

        // Parse duration
        val duration = TemporaryGroupAssignment.parseDuration(durationStr)
        if (duration == null) {
            messages.send(sender, "tempgroup-invalid-duration", "input" to durationStr)
            return
        }

        // Get assigner name
        val assignedBy = if (sender is Player) sender.name else "CONSOLE"

        // Assign the temporary group
        val result = tempGroupService.assignTemporaryGroup(player, groupName, duration, assignedBy)

        result.fold(
            onSuccess = { assignment ->
                messages.send(sender, "tempgroup-assign-success",
                    "player" to player.name,
                    "group" to groupName,
                    "duration" to assignment.getRemainingTimeString()
                )
            },
            onFailure = { error ->
                messages.send(sender, "tempgroup-assign-failed",
                    "error" to (error.message ?: "Unknown error")
                )
            }
        )
    }

    private suspend fun handleRemove(
        plugin: PluginContext,
        sender: CommandSender,
        args: Array<String>,
        tempGroupService: sh.pcx.xinventories.internal.service.TemporaryGroupService
    ) {
        val messages = plugin.serviceManager.messageService

        // /xinv tempgroup remove <player>
        if (args.size < 2) {
            messages.send(sender, "invalid-syntax",
                "usage" to "/xinv tempgroup remove <player>"
            )
            return
        }

        val playerName = args[1]
        val uuid = resolvePlayerUuid(playerName)

        if (uuid == null) {
            messages.send(sender, "player-not-found", "player" to playerName)
            return
        }

        // Check if player has a temp group
        val assignment = tempGroupService.getAssignment(uuid)
        if (assignment == null) {
            messages.send(sender, "tempgroup-not-assigned", "player" to playerName)
            return
        }

        // Remove the assignment
        val removed = tempGroupService.removeTemporaryGroup(uuid, TemporaryGroupRemoveEvent.RemovalReason.MANUAL)

        if (removed) {
            messages.send(sender, "tempgroup-remove-success",
                "player" to playerName,
                "group" to assignment.temporaryGroup
            )
        } else {
            messages.send(sender, "tempgroup-remove-failed", "player" to playerName)
        }
    }

    private fun handleList(
        plugin: PluginContext,
        sender: CommandSender,
        args: Array<String>,
        tempGroupService: sh.pcx.xinventories.internal.service.TemporaryGroupService
    ) {
        val messages = plugin.serviceManager.messageService

        // /xinv tempgroup list [player]
        if (args.size >= 2) {
            // Show specific player
            val playerName = args[1]
            val uuid = resolvePlayerUuid(playerName)

            if (uuid == null) {
                messages.send(sender, "player-not-found", "player" to playerName)
                return
            }

            val assignment = tempGroupService.getAssignment(uuid)
            if (assignment == null) {
                messages.send(sender, "tempgroup-not-assigned", "player" to playerName)
                return
            }

            messages.sendRaw(sender, "tempgroup-info-header", "player" to playerName)
            sendAssignmentInfo(sender, messages, assignment, playerName)
            return
        }

        // List all assignments
        val assignments = tempGroupService.getAllAssignments()

        if (assignments.isEmpty()) {
            messages.send(sender, "tempgroup-list-empty")
            return
        }

        messages.sendRaw(sender, "tempgroup-list-header",
            "count" to assignments.size.toString()
        )

        assignments.forEach { (uuid, assignment) ->
            val playerName = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString().substring(0, 8)
            messages.sendRaw(sender, "tempgroup-list-entry",
                "player" to playerName,
                "group" to assignment.temporaryGroup,
                "remaining" to assignment.getRemainingTimeString()
            )
        }
    }

    private suspend fun handleExtend(
        plugin: PluginContext,
        sender: CommandSender,
        args: Array<String>,
        tempGroupService: sh.pcx.xinventories.internal.service.TemporaryGroupService
    ) {
        val messages = plugin.serviceManager.messageService

        // /xinv tempgroup extend <player> <duration>
        if (args.size < 3) {
            messages.send(sender, "invalid-syntax",
                "usage" to "/xinv tempgroup extend <player> <duration>"
            )
            return
        }

        val playerName = args[1]
        val durationStr = args[2]

        val uuid = resolvePlayerUuid(playerName)
        if (uuid == null) {
            messages.send(sender, "player-not-found", "player" to playerName)
            return
        }

        // Check if player has a temp group
        if (!tempGroupService.hasTemporaryGroup(uuid)) {
            messages.send(sender, "tempgroup-not-assigned", "player" to playerName)
            return
        }

        // Parse duration
        val duration = TemporaryGroupAssignment.parseDuration(durationStr)
        if (duration == null) {
            messages.send(sender, "tempgroup-invalid-duration", "input" to durationStr)
            return
        }

        // Extend the assignment
        val result = tempGroupService.extendTemporaryGroup(uuid, duration)

        result.fold(
            onSuccess = { assignment ->
                messages.send(sender, "tempgroup-extend-success",
                    "player" to playerName,
                    "extension" to TemporaryGroupAssignment.formatDuration(duration),
                    "remaining" to assignment.getRemainingTimeString()
                )
            },
            onFailure = { error ->
                messages.send(sender, "tempgroup-extend-failed",
                    "error" to (error.message ?: "Unknown error")
                )
            }
        )
    }

    private fun handleInfo(
        plugin: PluginContext,
        sender: CommandSender,
        args: Array<String>,
        tempGroupService: sh.pcx.xinventories.internal.service.TemporaryGroupService
    ) {
        val messages = plugin.serviceManager.messageService

        // /xinv tempgroup info <player>
        if (args.size < 2) {
            messages.send(sender, "invalid-syntax",
                "usage" to "/xinv tempgroup info <player>"
            )
            return
        }

        val playerName = args[1]
        val uuid = resolvePlayerUuid(playerName)

        if (uuid == null) {
            messages.send(sender, "player-not-found", "player" to playerName)
            return
        }

        val assignment = tempGroupService.getAssignment(uuid)
        if (assignment == null) {
            messages.send(sender, "tempgroup-not-assigned", "player" to playerName)
            return
        }

        messages.sendRaw(sender, "tempgroup-info-header", "player" to playerName)
        sendAssignmentInfo(sender, messages, assignment, playerName)
    }

    private fun sendAssignmentInfo(
        sender: CommandSender,
        messages: sh.pcx.xinventories.internal.service.MessageService,
        assignment: TemporaryGroupAssignment,
        playerName: String
    ) {
        messages.sendRaw(sender, "tempgroup-info-group",
            "group" to assignment.temporaryGroup
        )
        messages.sendRaw(sender, "tempgroup-info-original",
            "group" to assignment.originalGroup
        )
        messages.sendRaw(sender, "tempgroup-info-remaining",
            "time" to assignment.getRemainingTimeString()
        )
        messages.sendRaw(sender, "tempgroup-info-assigned-by",
            "by" to assignment.assignedBy
        )
        messages.sendRaw(sender, "tempgroup-info-assigned-at",
            "time" to dateFormatter.format(assignment.assignedAt)
        )
        messages.sendRaw(sender, "tempgroup-info-expires-at",
            "time" to dateFormatter.format(assignment.expiresAt)
        )
        assignment.reason?.let {
            messages.sendRaw(sender, "tempgroup-info-reason",
                "reason" to it
            )
        }
    }

    private fun resolvePlayerUuid(nameOrUuid: String): java.util.UUID? {
        // Try UUID first
        try {
            return java.util.UUID.fromString(nameOrUuid)
        } catch (e: Exception) {
            // Not a UUID, try player name
        }

        // Try online player
        val onlinePlayer = Bukkit.getPlayerExact(nameOrUuid)
        if (onlinePlayer != null) {
            return onlinePlayer.uniqueId
        }

        // Try offline player
        @Suppress("DEPRECATION")
        val offlinePlayer = Bukkit.getOfflinePlayer(nameOrUuid)
        if (offlinePlayer.hasPlayedBefore()) {
            return offlinePlayer.uniqueId
        }

        return null
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("assign", "remove", "list", "extend", "info")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "assign", "remove", "extend", "info" -> {
                    Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "assign" -> {
                    plugin.serviceManager.groupService.getAllGroups()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                }
                "extend" -> {
                    listOf("30m", "1h", "2h", "12h", "1d", "2d", "1w")
                        .filter { it.startsWith(args[2].lowercase()) }
                }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "assign" -> {
                    listOf("30m", "1h", "2h", "12h", "1d", "2d", "1w")
                        .filter { it.startsWith(args[3].lowercase()) }
                }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

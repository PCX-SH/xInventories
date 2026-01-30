package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.service.ExpiredPlayerInfo
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Command for managing player data expiration.
 *
 * Provides status, preview, execution, and exclusion management
 * for the automatic data cleanup feature.
 */
class ExpirationCommand : Subcommand {

    override val name = "expiration"
    override val aliases = listOf("expire", "cleanup")
    override val permission = "xinventories.admin.expiration"
    override val usage = "/xinv expiration <status|preview|run|exclude>"
    override val description = "Manage player data expiration"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val expirationService = plugin.serviceManager.expirationService

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        when (args[0].lowercase()) {
            "status" -> handleStatus(plugin, sender, expirationService)
            "preview" -> handlePreview(plugin, sender, args, expirationService)
            "run" -> handleRun(plugin, sender, args, expirationService)
            "exclude" -> handleExclude(plugin, sender, args, expirationService)
            "unexclude" -> handleUnexclude(plugin, sender, args, expirationService)
            "list" -> handleListExcluded(plugin, sender, expirationService)
            else -> messages.send(sender, "invalid-syntax", "usage" to usage)
        }

        return true
    }

    private suspend fun handleStatus(
        plugin: XInventories,
        sender: CommandSender,
        expirationService: sh.pcx.xinventories.internal.service.ExpirationService
    ) {
        val messages = plugin.serviceManager.messageService
        val status = expirationService.getStatus()

        messages.sendRaw(sender, "expiration-status-header")
        messages.sendRaw(sender, "expiration-status-enabled",
            "enabled" to if (status.enabled) "<green>Enabled" else "<red>Disabled"
        )
        messages.sendRaw(sender, "expiration-status-days",
            "days" to status.inactivityDays.toString()
        )
        messages.sendRaw(sender, "expiration-status-permission",
            "permission" to status.excludePermission
        )
        messages.sendRaw(sender, "expiration-status-backup",
            "backup" to if (status.backupBeforeDelete) "<green>Yes" else "<yellow>No"
        )
        messages.sendRaw(sender, "expiration-status-schedule",
            "schedule" to status.schedule
        )
        messages.sendRaw(sender, "expiration-status-excluded",
            "count" to status.excludedPlayerCount.toString()
        )

        if (status.nextScheduledRun != null) {
            messages.sendRaw(sender, "expiration-status-next-run",
                "time" to dateFormatter.format(status.nextScheduledRun)
            )
        }
    }

    private suspend fun handlePreview(
        plugin: XInventories,
        sender: CommandSender,
        args: Array<String>,
        expirationService: sh.pcx.xinventories.internal.service.ExpirationService
    ) {
        val messages = plugin.serviceManager.messageService

        val days = args.getOrNull(1)?.toIntOrNull()

        messages.send(sender, "expiration-preview-scanning")

        val expired = expirationService.previewExpired(days)

        if (expired.isEmpty()) {
            messages.send(sender, "expiration-preview-none")
            return
        }

        messages.sendRaw(sender, "expiration-preview-header",
            "count" to expired.size.toString(),
            "days" to (days ?: expirationService.getStatus().inactivityDays).toString()
        )

        // Show first 10 players
        expired.take(10).forEach { player ->
            messages.sendRaw(sender, "expiration-preview-entry",
                "name" to player.name,
                "uuid" to player.uuid.toString().substring(0, 8),
                "days" to player.daysSinceActivity.toString(),
                "groups" to player.groupCount.toString()
            )
        }

        if (expired.size > 10) {
            messages.sendRaw(sender, "expiration-preview-more",
                "count" to (expired.size - 10).toString()
            )
        }

        val totalGroups = expired.sumOf { it.groupCount }
        messages.sendRaw(sender, "expiration-preview-summary",
            "players" to expired.size.toString(),
            "entries" to totalGroups.toString()
        )
    }

    private suspend fun handleRun(
        plugin: XInventories,
        sender: CommandSender,
        args: Array<String>,
        expirationService: sh.pcx.xinventories.internal.service.ExpirationService
    ) {
        val messages = plugin.serviceManager.messageService

        val confirm = args.any { it.equals("--confirm", ignoreCase = true) }
        val dryRun = args.any { it.equals("--dry-run", ignoreCase = true) }

        if (!confirm && !dryRun) {
            // Show preview and ask for confirmation
            val expired = expirationService.previewExpired()

            if (expired.isEmpty()) {
                messages.send(sender, "expiration-preview-none")
                return
            }

            val totalGroups = expired.sumOf { it.groupCount }
            messages.send(sender, "expiration-run-confirm",
                "players" to expired.size.toString(),
                "entries" to totalGroups.toString()
            )
            return
        }

        messages.send(sender, if (dryRun) "expiration-run-dryrun-start" else "expiration-run-start")

        val result = expirationService.executeCleanup(dryRun = dryRun)

        if (result.success) {
            messages.send(sender, if (dryRun) "expiration-run-dryrun-complete" else "expiration-run-complete",
                "players" to result.playersProcessed.toString(),
                "entries" to result.dataEntriesDeleted.toString(),
                "duration" to "${result.duration.toMillis()}ms"
            )

            if (result.backupCreated != null) {
                messages.send(sender, "expiration-run-backup",
                    "name" to result.backupCreated
                )
            }
        } else {
            messages.send(sender, "expiration-run-errors",
                "count" to result.errors.size.toString()
            )
            result.errors.take(5).forEach { error ->
                messages.sendRaw(sender, "expiration-run-error-entry",
                    "error" to error
                )
            }
        }
    }

    private fun handleExclude(
        plugin: XInventories,
        sender: CommandSender,
        args: Array<String>,
        expirationService: sh.pcx.xinventories.internal.service.ExpirationService
    ) {
        val messages = plugin.serviceManager.messageService

        if (args.size < 2) {
            messages.send(sender, "invalid-syntax", "usage" to "/xinv expiration exclude <player>")
            return
        }

        val playerName = args[1]
        val uuid = resolvePlayer(playerName)

        if (uuid == null) {
            messages.send(sender, "player-not-found", "player" to playerName)
            return
        }

        if (expirationService.isExcluded(uuid)) {
            messages.send(sender, "expiration-already-excluded", "player" to playerName)
            return
        }

        expirationService.excludePlayer(uuid)
        messages.send(sender, "expiration-excluded", "player" to playerName)
    }

    private fun handleUnexclude(
        plugin: XInventories,
        sender: CommandSender,
        args: Array<String>,
        expirationService: sh.pcx.xinventories.internal.service.ExpirationService
    ) {
        val messages = plugin.serviceManager.messageService

        if (args.size < 2) {
            messages.send(sender, "invalid-syntax", "usage" to "/xinv expiration unexclude <player>")
            return
        }

        val playerName = args[1]
        val uuid = resolvePlayer(playerName)

        if (uuid == null) {
            messages.send(sender, "player-not-found", "player" to playerName)
            return
        }

        if (!expirationService.getExcludedPlayers().contains(uuid)) {
            messages.send(sender, "expiration-not-excluded", "player" to playerName)
            return
        }

        expirationService.unexcludePlayer(uuid)
        messages.send(sender, "expiration-unexcluded", "player" to playerName)
    }

    private fun handleListExcluded(
        plugin: XInventories,
        sender: CommandSender,
        expirationService: sh.pcx.xinventories.internal.service.ExpirationService
    ) {
        val messages = plugin.serviceManager.messageService
        val excluded = expirationService.getExcludedPlayers()

        if (excluded.isEmpty()) {
            messages.send(sender, "expiration-excluded-none")
            return
        }

        messages.sendRaw(sender, "expiration-excluded-header",
            "count" to excluded.size.toString()
        )

        excluded.forEach { uuid ->
            val name = Bukkit.getOfflinePlayer(uuid).name ?: "Unknown"
            messages.sendRaw(sender, "expiration-excluded-entry",
                "name" to name,
                "uuid" to uuid.toString().substring(0, 8)
            )
        }
    }

    private fun resolvePlayer(nameOrUuid: String): UUID? {
        // Try UUID first
        try {
            return UUID.fromString(nameOrUuid)
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

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("status", "preview", "run", "exclude", "unexclude", "list")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "preview" -> listOf("30", "60", "90", "180", "365")
                    .filter { it.startsWith(args[1]) }
                "exclude", "unexclude" -> Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                "run" -> listOf("--confirm", "--dry-run")
                    .filter { it.startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "run" -> listOf("--confirm", "--dry-run")
                    .filter { !args.contains(it) && it.startsWith(args[2].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

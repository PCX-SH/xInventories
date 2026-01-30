package sh.pcx.xinventories.internal.command.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import sh.pcx.xinventories.PluginContext
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * Command to view inventory version history.
 * Usage: /xinv history <player> [group]
 */
class HistoryCommand : Subcommand {

    override val name = "history"
    override val aliases = listOf("hist", "versions")
    override val permission = "xinventories.command.history"
    override val usage = "/xinv history <player> [group]"
    override val description = "View inventory version history"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val versioningService = plugin.serviceManager.versioningService

        if (!plugin.configManager.mainConfig.versioning.enabled) {
            messages.send(sender, "versioning-disabled")
            return true
        }

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        // Get player UUID
        val playerName = args[0]
        val targetPlayer = Bukkit.getPlayer(playerName)
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)

        val playerUuid = targetPlayer?.uniqueId ?: offlinePlayer.uniqueId
        val displayName = targetPlayer?.name ?: offlinePlayer.name ?: playerName

        if (!offlinePlayer.hasPlayedBefore() && targetPlayer == null) {
            messages.send(sender, "player-not-found", "player" to playerName)
            return true
        }

        val group = args.getOrNull(1)

        // Fetch versions
        val versions = versioningService.getVersions(playerUuid, group, 10)

        if (versions.isEmpty()) {
            messages.send(sender, "no-versions-found",
                "player" to displayName,
                "group" to (group ?: "all")
            )
            return true
        }

        // Display versions
        messages.send(sender, "version-history-header",
            "player" to displayName,
            "count" to versions.size.toString()
        )

        versions.forEachIndexed { index, version ->
            val formattedTime = dateFormatter.format(version.timestamp)
            messages.send(sender, "version-history-entry",
                "index" to (index + 1).toString(),
                "id" to version.id.substring(0, 8),
                "full_id" to version.id,
                "group" to version.group,
                "trigger" to version.trigger.name.lowercase().replace("_", " "),
                "time" to formattedTime,
                "relative_time" to version.getRelativeTimeDescription(),
                "items" to version.getItemSummary()
            )
        }

        messages.send(sender, "version-history-footer",
            "restore_usage" to "/xinv restore $displayName <version-id>"
        )

        return true
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
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

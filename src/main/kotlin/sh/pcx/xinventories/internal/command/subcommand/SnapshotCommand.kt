package sh.pcx.xinventories.internal.command.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.model.VersionTrigger

/**
 * Command to create a manual inventory snapshot.
 * Usage: /xinv snapshot <player> [group]
 */
class SnapshotCommand : Subcommand {

    override val name = "snapshot"
    override val aliases = listOf("snap", "backup")
    override val permission = "xinventories.command.snapshot"
    override val usage = "/xinv snapshot <player> [reason]"
    override val description = "Create a manual inventory snapshot"

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val versioningService = plugin.serviceManager.versioningService

        if (!plugin.configManager.mainConfig.versioning.enabled) {
            messages.send(sender, "versioning-disabled")
            return true
        }

        if (!plugin.configManager.mainConfig.versioning.triggerOn.manual) {
            messages.send(sender, "manual-snapshots-disabled")
            return true
        }

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        val playerName = args[0]

        // Player must be online to snapshot
        val target = Bukkit.getPlayer(playerName)
        if (target == null) {
            messages.send(sender, "player-must-be-online", "player" to playerName)
            return true
        }

        // Optional reason
        val reason = if (args.size > 1) {
            args.drop(1).joinToString(" ")
        } else {
            "Manual snapshot by ${sender.name}"
        }

        // Create metadata
        val metadata = mutableMapOf<String, String>()
        metadata["created_by"] = sender.name
        metadata["reason"] = reason

        // Create the snapshot
        val version = versioningService.createVersion(
            target,
            VersionTrigger.MANUAL,
            metadata,
            force = true
        )

        if (version != null) {
            messages.send(sender, "snapshot-created",
                "player" to target.name,
                "version" to version.id.substring(0, 8),
                "group" to version.group
            )
        } else {
            messages.send(sender, "snapshot-failed", "player" to target.name)
        }

        return true
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
            else -> emptyList()
        }
    }
}

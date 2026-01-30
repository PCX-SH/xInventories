package sh.pcx.xinventories.internal.command.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import sh.pcx.xinventories.PluginContext

/**
 * Command to restore a player's inventory from a version.
 * Usage: /xinv restore <player> <version-id>
 */
class RestoreCommand : Subcommand {

    override val name = "restore"
    override val aliases = listOf("rollback")
    override val permission = "xinventories.command.restore"
    override val usage = "/xinv restore <player> <version-id>"
    override val description = "Restore inventory from a version"

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val versioningService = plugin.serviceManager.versioningService

        if (!plugin.configManager.mainConfig.versioning.enabled) {
            messages.send(sender, "versioning-disabled")
            return true
        }

        if (args.size < 2) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        val playerName = args[0]
        val versionId = args[1]

        // Player must be online to restore
        val target = Bukkit.getPlayer(playerName)
        if (target == null) {
            messages.send(sender, "player-must-be-online", "player" to playerName)
            return true
        }

        // Expand short version IDs
        val fullVersionId = if (versionId.length < 36) {
            // Try to find a matching version
            val versions = versioningService.getVersions(target.uniqueId, null, 100)
            versions.find { it.id.startsWith(versionId) }?.id
        } else {
            versionId
        }

        if (fullVersionId == null) {
            messages.send(sender, "version-not-found", "version" to versionId)
            return true
        }

        // Restore the version
        val result = versioningService.restoreVersion(target, fullVersionId, createBackup = true)

        result.fold(
            onSuccess = {
                messages.send(sender, "version-restored",
                    "player" to target.name,
                    "version" to fullVersionId.substring(0, 8)
                )
                messages.send(target, "your-inventory-restored")
            },
            onFailure = { error ->
                messages.send(sender, "version-restore-failed",
                    "player" to target.name,
                    "error" to (error.message ?: "Unknown error")
                )
            }
        )

        return true
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
            2 -> {
                // Could potentially load recent version IDs here for tab completion
                // For now, just provide a hint
                listOf("<version-id>")
            }
            else -> emptyList()
        }
    }
}

package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.StorageType
import org.bukkit.command.CommandSender

/**
 * Converts/migrates data between storage backends.
 */
class ConvertCommand : Subcommand {

    override val name = "convert"
    override val aliases = listOf("migrate")
    override val permission = "xinventories.command.convert"
    override val usage = "/xinv convert <from> <to>"
    override val description = "Convert between storage types"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val migrationService = plugin.serviceManager.migrationService

        if (args.size < 2) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            sender.sendMessage("Available types: yaml, sqlite, mysql")
            return true
        }

        // Check if migration is already in progress
        if (migrationService.isMigrationInProgress()) {
            messages.send(sender, "convert-in-progress")
            return true
        }

        val from = try {
            StorageType.valueOf(args[0].uppercase())
        } catch (e: Exception) {
            messages.send(sender, "invalid-syntax", "usage" to "$usage (types: yaml, sqlite, mysql)")
            return true
        }

        val to = try {
            StorageType.valueOf(args[1].uppercase())
        } catch (e: Exception) {
            messages.send(sender, "invalid-syntax", "usage" to "$usage (types: yaml, sqlite, mysql)")
            return true
        }

        if (from == to) {
            sender.sendMessage("Source and target storage types cannot be the same.")
            return true
        }

        // Validate configurations
        migrationService.validateStorageConfig(from).onFailure { e ->
            sender.sendMessage("Invalid $from configuration: ${e.message}")
            return true
        }

        migrationService.validateStorageConfig(to).onFailure { e ->
            sender.sendMessage("Invalid $to configuration: ${e.message}")
            return true
        }

        messages.send(sender, "convert-started", "source" to from.name)

        val result = migrationService.migrate(from, to)

        result.fold(
            onSuccess = { report ->
                messages.send(sender, "convert-complete", "count" to report.playersProcessed.toString())

                if (report.errors.isNotEmpty()) {
                    sender.sendMessage("${report.errors.size} errors occurred during migration. Check console for details.")
                }
            },
            onFailure = { e ->
                messages.send(sender, "convert-failed")
                sender.sendMessage("Error: ${e.message}")
            }
        )

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        val types = listOf("yaml", "sqlite", "mysql")

        return when (args.size) {
            1 -> types.filter { it.startsWith(args[0].lowercase()) }
            2 -> types.filter { it.startsWith(args[1].lowercase()) && it != args[0].lowercase() }
            else -> emptyList()
        }
    }
}

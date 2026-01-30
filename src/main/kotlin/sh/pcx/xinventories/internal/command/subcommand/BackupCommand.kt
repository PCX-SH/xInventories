package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.util.toReadableSize
import org.bukkit.command.CommandSender
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Manages backups.
 */
class BackupCommand : Subcommand {

    override val name = "backup"
    override val aliases = listOf("backups")
    override val permission = "xinventories.command.backup"
    override val usage = "/xinv backup <create|restore|list|delete> [name/id]"
    override val description = "Manage backups"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val backupService = plugin.serviceManager.backupService

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> {
                val name = args.getOrNull(1)

                messages.send(sender, "backup-in-progress")

                val result = backupService.createBackup(name)

                result.fold(
                    onSuccess = { metadata ->
                        messages.send(sender, "backup-created", "name" to metadata.name)
                    },
                    onFailure = {
                        messages.send(sender, "backup-create-failed")
                    }
                )
            }

            "restore" -> {
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv backup restore <id> [confirm]")
                    return true
                }

                val backupId = args[1]
                val confirm = args.getOrNull(2)?.lowercase() == "confirm"

                // Check if backup exists
                val backup = backupService.getBackup(backupId)
                if (backup == null) {
                    messages.send(sender, "backup-not-found", "name" to backupId)
                    return true
                }

                if (!confirm) {
                    messages.send(sender, "backup-restore-confirm", "name" to backupId)
                    return true
                }

                val result = backupService.restoreBackup(backupId)

                result.fold(
                    onSuccess = {
                        messages.send(sender, "backup-restored", "name" to backup.name)
                    },
                    onFailure = {
                        messages.send(sender, "backup-restore-failed")
                    }
                )
            }

            "list" -> {
                val backups = backupService.listBackups()

                messages.sendRaw(sender, "backup-list-header")

                if (backups.isEmpty()) {
                    messages.sendRaw(sender, "backup-list-empty")
                } else {
                    backups.forEach { backup ->
                        messages.sendRaw(sender, "backup-list-entry",
                            "name" to "${backup.name} (${backup.id})",
                            "time" to dateFormatter.format(backup.timestamp),
                            "size" to backup.sizeBytes.toReadableSize()
                        )
                    }
                }
            }

            "delete" -> {
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv backup delete <id>")
                    return true
                }

                val backupId = args[1]
                val result = backupService.deleteBackup(backupId)

                result.fold(
                    onSuccess = {
                        messages.send(sender, "backup-deleted", "name" to backupId)
                    },
                    onFailure = {
                        messages.send(sender, "backup-not-found", "name" to backupId)
                    }
                )
            }

            else -> {
                messages.send(sender, "invalid-syntax", "usage" to usage)
            }
        }

        return true
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("create", "restore", "list", "delete")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "restore", "delete" -> {
                    // Would need to list backup IDs - simplified for now
                    emptyList()
                }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "restore" -> listOf("confirm")
                    .filter { it.startsWith(args[2].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import org.bukkit.command.CommandSender

/**
 * Manages inventory groups.
 */
class GroupCommand : Subcommand {

    override val name = "group"
    override val aliases = listOf("groups", "g")
    override val permission = "xinventories.command.group"
    override val usage = "/xinv group <create|delete|list|info> [name]"
    override val description = "Manage inventory groups"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val groupService = plugin.serviceManager.groupService

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        when (args[0].lowercase()) {
            "create" -> {
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv group create <name>")
                    return true
                }

                val name = args[1]
                val result = groupService.createGroup(name)

                result.fold(
                    onSuccess = { messages.send(sender, "group-created", "group" to name) },
                    onFailure = { messages.send(sender, "group-already-exists", "group" to name) }
                )
            }

            "delete", "remove" -> {
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv group delete <name>")
                    return true
                }

                val name = args[1]
                val result = groupService.deleteGroup(name)

                result.fold(
                    onSuccess = { messages.send(sender, "group-deleted", "group" to name) },
                    onFailure = { e ->
                        if (e.message?.contains("default") == true) {
                            messages.send(sender, "group-cannot-delete-default")
                        } else {
                            messages.send(sender, "group-not-found", "group" to name)
                        }
                    }
                )
            }

            "list" -> {
                val groups = groupService.getAllGroupsApi()

                messages.sendRaw(sender, "group-list-header")

                if (groups.isEmpty()) {
                    messages.sendRaw(sender, "group-list-empty")
                } else {
                    groups.forEach { group ->
                        messages.sendRaw(sender, "group-list-entry",
                            "group" to group.name,
                            "count" to group.worlds.size.toString()
                        )
                    }
                }
            }

            "info" -> {
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv group info <name>")
                    return true
                }

                val name = args[1]
                val group = groupService.getGroupApi(name)

                if (group == null) {
                    messages.send(sender, "group-not-found", "group" to name)
                    return true
                }

                messages.sendRaw(sender, "group-info-header", "group" to name)
                messages.sendRaw(sender, "group-info-worlds",
                    "worlds" to if (group.worlds.isEmpty()) "none" else group.worlds.joinToString(", ")
                )
                messages.sendRaw(sender, "group-info-patterns",
                    "patterns" to if (group.patterns.isEmpty()) "none" else group.patterns.joinToString(", ")
                )
                messages.sendRaw(sender, "group-info-priority", "priority" to group.priority.toString())
                messages.sendRaw(sender, "group-info-parent", "parent" to (group.parent ?: "none"))
            }

            else -> {
                messages.send(sender, "invalid-syntax", "usage" to usage)
            }
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("create", "delete", "list", "info")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "delete", "info" -> plugin.serviceManager.groupService.getAllGroups()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

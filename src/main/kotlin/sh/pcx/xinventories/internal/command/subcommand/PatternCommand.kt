package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.isValidRegex
import org.bukkit.command.CommandSender

/**
 * Manages world patterns for groups.
 */
class PatternCommand : Subcommand {

    override val name = "pattern"
    override val aliases = listOf("patterns", "p")
    override val permission = "xinventories.command.pattern"
    override val usage = "/xinv pattern <add|remove|list> <group> [pattern]"
    override val description = "Manage group patterns"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val groupService = plugin.serviceManager.groupService

        if (args.size < 2) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        val action = args[0].lowercase()
        val groupName = args[1]

        // Verify group exists
        if (groupService.getGroup(groupName) == null) {
            messages.send(sender, "group-not-found", "group" to groupName)
            return true
        }

        when (action) {
            "add" -> {
                if (args.size < 3) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv pattern add <group> <pattern>")
                    return true
                }

                val pattern = args[2]

                // Validate regex
                if (!pattern.isValidRegex()) {
                    messages.send(sender, "pattern-invalid", "pattern" to pattern)
                    return true
                }

                val result = groupService.addPattern(groupName, pattern)

                result.fold(
                    onSuccess = {
                        messages.send(sender, "pattern-added",
                            "pattern" to pattern,
                            "group" to groupName
                        )
                    },
                    onFailure = {
                        messages.send(sender, "pattern-already-exists",
                            "pattern" to pattern,
                            "group" to groupName
                        )
                    }
                )
            }

            "remove", "delete" -> {
                if (args.size < 3) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv pattern remove <group> <pattern>")
                    return true
                }

                val pattern = args[2]
                val result = groupService.removePattern(groupName, pattern)

                result.fold(
                    onSuccess = {
                        messages.send(sender, "pattern-removed",
                            "pattern" to pattern,
                            "group" to groupName
                        )
                    },
                    onFailure = {
                        messages.send(sender, "pattern-not-found",
                            "pattern" to pattern,
                            "group" to groupName
                        )
                    }
                )
            }

            "list" -> {
                val patterns = groupService.getPatterns(groupName)

                messages.sendRaw(sender, "pattern-list-header", "group" to groupName)

                if (patterns.isEmpty()) {
                    messages.sendRaw(sender, "pattern-list-empty")
                } else {
                    patterns.forEach { pattern ->
                        messages.sendRaw(sender, "pattern-list-entry", "pattern" to pattern)
                    }
                }
            }

            else -> {
                messages.send(sender, "invalid-syntax", "usage" to usage)
            }
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("add", "remove", "list")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> plugin.serviceManager.groupService.getAllGroups()
                .map { it.name }
                .filter { it.lowercase().startsWith(args[1].lowercase()) }
            3 -> when (args[0].lowercase()) {
                "remove" -> plugin.serviceManager.groupService.getPatterns(args[1])
                    .filter { it.lowercase().startsWith(args[2].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * Manages world-to-group assignments.
 */
class WorldCommand : Subcommand {

    override val name = "world"
    override val aliases = listOf("worlds", "w")
    override val permission = "xinventories.command.world"
    override val usage = "/xinv world <assign|unassign|list> [world] [group]"
    override val description = "Manage world assignments"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val groupService = plugin.serviceManager.groupService

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        when (args[0].lowercase()) {
            "assign", "set" -> {
                if (args.size < 3) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv world assign <world> <group>")
                    return true
                }

                val worldName = args[1]
                val groupName = args[2]

                // Verify world exists
                if (Bukkit.getWorld(worldName) == null) {
                    messages.send(sender, "world-not-found", "world" to worldName)
                    return true
                }

                val result = groupService.assignWorldToGroup(worldName, groupName)

                result.fold(
                    onSuccess = {
                        messages.send(sender, "world-assigned",
                            "world" to worldName,
                            "group" to groupName
                        )
                    },
                    onFailure = { messages.send(sender, "group-not-found", "group" to groupName) }
                )
            }

            "unassign", "remove" -> {
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv world unassign <world>")
                    return true
                }

                val worldName = args[1]
                groupService.unassignWorld(worldName)
                messages.send(sender, "world-unassigned", "world" to worldName, "group" to "")
            }

            "list" -> {
                val assignments = groupService.getWorldAssignments()
                val allWorlds = Bukkit.getWorlds().map { it.name }

                messages.sendRaw(sender, "world-list-header")

                allWorlds.forEach { worldName ->
                    val explicitGroup = assignments[worldName]
                    val resolvedGroup = groupService.getGroupForWorld(worldName)

                    when {
                        explicitGroup != null -> {
                            messages.sendRaw(sender, "world-list-entry",
                                "world" to worldName,
                                "group" to explicitGroup
                            )
                        }
                        resolvedGroup.matchesPattern(worldName) -> {
                            messages.sendRaw(sender, "world-list-pattern",
                                "world" to worldName,
                                "group" to resolvedGroup.name
                            )
                        }
                        else -> {
                            messages.sendRaw(sender, "world-list-default",
                                "world" to worldName,
                                "group" to resolvedGroup.name
                            )
                        }
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
            1 -> listOf("assign", "unassign", "list")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "assign", "unassign" -> Bukkit.getWorlds()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "assign" -> plugin.serviceManager.groupService.getAllGroups()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[2].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

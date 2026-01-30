package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.TemplateApplyTrigger
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Command for managing inventory templates.
 *
 * Usage:
 * - /xinv template list - List all templates
 * - /xinv template view <name> - View template details
 * - /xinv template create <name> [from-player] - Create a template
 * - /xinv template apply <name> <player> - Apply template to a player
 * - /xinv template delete <name> - Delete a template
 * - /xinv template edit <name> - Edit template (opens GUI)
 */
class TemplateCommand : Subcommand {

    override val name = "template"
    override val aliases = listOf("templates", "tmpl")
    override val permission = "xinventories.command.template"
    override val usage = "/xinv template <list|view|create|apply|delete|edit> [args...]"
    override val description = "Manage inventory templates"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val templateService = plugin.serviceManager.templateService

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        when (args[0].lowercase()) {
            "list" -> {
                val templates = templateService.getAllTemplates()

                messages.sendRaw(sender, "template-list-header")

                if (templates.isEmpty()) {
                    messages.sendRaw(sender, "template-list-empty")
                } else {
                    templates.sortedBy { it.name }.forEach { template ->
                        val creator = template.createdBy?.let {
                            Bukkit.getOfflinePlayer(it).name ?: "Unknown"
                        } ?: "System"

                        messages.sendRaw(sender, "template-list-entry",
                            "name" to template.name,
                            "display" to template.getEffectiveDisplayName(),
                            "creator" to creator,
                            "created" to dateFormatter.format(template.createdAt)
                        )
                    }
                }
            }

            "view" -> {
                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv template view <name>")
                    return true
                }

                val templateName = args[1]
                val template = templateService.getTemplate(templateName)

                if (template == null) {
                    messages.send(sender, "template-not-found", "name" to templateName)
                    return true
                }

                messages.sendRaw(sender, "template-view-header", "name" to template.name)

                template.displayName?.let {
                    messages.sendRaw(sender, "template-view-display", "value" to it)
                }

                template.description?.let {
                    messages.sendRaw(sender, "template-view-description", "value" to it)
                }

                val creator = template.createdBy?.let {
                    Bukkit.getOfflinePlayer(it).name ?: "Unknown"
                } ?: "System"
                messages.sendRaw(sender, "template-view-creator", "value" to creator)
                messages.sendRaw(sender, "template-view-created", "value" to dateFormatter.format(template.createdAt))

                // Summary of contents
                val itemCount = template.inventory.mainInventory.size +
                        template.inventory.armorInventory.size +
                        (if (template.inventory.offhand != null) 1 else 0)

                messages.sendRaw(sender, "template-view-items", "count" to itemCount.toString())
                messages.sendRaw(sender, "template-view-level", "level" to template.inventory.level.toString())
                messages.sendRaw(sender, "template-view-effects", "count" to template.inventory.potionEffects.size.toString())

                // If player, offer to open GUI
                if (sender is Player) {
                    messages.sendRaw(sender, "template-view-gui-hint")
                }
            }

            "create" -> {
                if (!sender.hasPermission("xinventories.command.template.create")) {
                    messages.send(sender, "no-permission")
                    return true
                }

                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv template create <name> [from-player]")
                    return true
                }

                val templateName = args[1]

                // Check if template already exists
                if (templateService.getTemplate(templateName) != null) {
                    messages.send(sender, "template-already-exists", "name" to templateName)
                    return true
                }

                // Determine source player
                val sourcePlayer: Player = if (args.size >= 3) {
                    val targetName = args[2]
                    Bukkit.getPlayer(targetName) ?: run {
                        messages.send(sender, "player-not-found", "player" to targetName)
                        return true
                    }
                } else if (sender is Player) {
                    sender
                } else {
                    messages.send(sender, "player-required")
                    return true
                }

                val result = templateService.createTemplate(
                    name = templateName,
                    player = sourcePlayer,
                    displayName = null,
                    description = null
                )

                result.fold(
                    onSuccess = { template ->
                        messages.send(sender, "template-created",
                            "name" to template.name,
                            "player" to sourcePlayer.name
                        )
                    },
                    onFailure = { error ->
                        messages.send(sender, "template-create-failed", "error" to (error.message ?: "Unknown error"))
                    }
                )
            }

            "apply" -> {
                if (!sender.hasPermission("xinventories.command.template.apply")) {
                    messages.send(sender, "no-permission")
                    return true
                }

                if (args.size < 3) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv template apply <name> <player>")
                    return true
                }

                val templateName = args[1]
                val playerName = args[2]

                val template = templateService.getTemplate(templateName)
                if (template == null) {
                    messages.send(sender, "template-not-found", "name" to templateName)
                    return true
                }

                val targetPlayer = Bukkit.getPlayer(playerName)
                if (targetPlayer == null) {
                    messages.send(sender, "player-not-found", "player" to playerName)
                    return true
                }

                val groupService = plugin.serviceManager.groupService
                val group = groupService.getGroupForWorldApi(targetPlayer.world)

                val success = templateService.applyTemplate(
                    player = targetPlayer,
                    template = template,
                    group = group,
                    trigger = TemplateApplyTrigger.MANUAL,
                    clearFirst = true
                )

                if (success) {
                    messages.send(sender, "template-applied",
                        "name" to template.name,
                        "player" to targetPlayer.name
                    )

                    if (sender != targetPlayer) {
                        messages.send(targetPlayer, "template-applied-target", "name" to template.name)
                    }
                } else {
                    messages.send(sender, "template-apply-failed", "name" to template.name)
                }
            }

            "delete" -> {
                if (!sender.hasPermission("xinventories.command.template.delete")) {
                    messages.send(sender, "no-permission")
                    return true
                }

                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv template delete <name>")
                    return true
                }

                val templateName = args[1]

                if (templateService.getTemplate(templateName) == null) {
                    messages.send(sender, "template-not-found", "name" to templateName)
                    return true
                }

                val deleted = templateService.deleteTemplate(templateName)

                if (deleted) {
                    messages.send(sender, "template-deleted", "name" to templateName)
                } else {
                    messages.send(sender, "template-delete-failed", "name" to templateName)
                }
            }

            "edit" -> {
                if (!sender.hasPermission("xinventories.command.template.edit")) {
                    messages.send(sender, "no-permission")
                    return true
                }

                if (sender !is Player) {
                    messages.send(sender, "player-only")
                    return true
                }

                if (args.size < 2) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv template edit <name>")
                    return true
                }

                val templateName = args[1]
                val template = templateService.getTemplate(templateName)

                if (template == null) {
                    messages.send(sender, "template-not-found", "name" to templateName)
                    return true
                }

                // TODO: Open template editor GUI
                messages.send(sender, "feature-not-implemented", "feature" to "Template Editor GUI")
            }

            "reload" -> {
                if (!sender.hasPermission("xinventories.command.template.reload")) {
                    messages.send(sender, "no-permission")
                    return true
                }

                templateService.reload()
                messages.send(sender, "templates-reloaded", "count" to templateService.getAllTemplates().size.toString())
            }

            else -> {
                messages.send(sender, "invalid-syntax", "usage" to usage)
            }
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        val templateService = plugin.serviceManager.templateService

        return when (args.size) {
            1 -> listOf("list", "view", "create", "apply", "delete", "edit", "reload")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "view", "apply", "delete", "edit" -> {
                    templateService.getAllTemplates()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[1].lowercase()) }
                }
                "create" -> emptyList() // User provides new name
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "create", "apply" -> {
                    Bukkit.getOnlinePlayers()
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

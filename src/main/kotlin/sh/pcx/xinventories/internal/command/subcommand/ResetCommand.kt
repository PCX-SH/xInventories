package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.TemplateApplyTrigger
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command for resetting player inventory to template.
 *
 * Usage:
 * - /xinv reset - Reset own inventory to current group's template
 * - /xinv reset [group] - Reset own inventory to specified group's template
 */
class ResetCommand : Subcommand {

    override val name = "reset"
    override val aliases = listOf("resetinv", "resetinventory")
    override val permission = "xinventories.reset"
    override val usage = "/xinv reset [group]"
    override val description = "Reset your inventory to the group template"
    override val playerOnly = true

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val groupService = plugin.serviceManager.groupService
        val templateService = plugin.serviceManager.templateService

        // This command is player-only
        if (sender !is Player) {
            messages.send(sender, "player-only")
            return true
        }

        // Determine which group to reset to
        val targetGroupName = if (args.isNotEmpty()) {
            args[0]
        } else {
            groupService.getGroupForWorld(sender.world).name
        }

        // Get the group
        val group = groupService.getGroup(targetGroupName)
        if (group == null) {
            messages.send(sender, "group-not-found", "group" to targetGroupName)
            return true
        }

        // Check if the group has template settings
        val templateSettings = group.templateSettings
        if (templateSettings == null || !templateSettings.enabled) {
            messages.send(sender, "no-template-for-group", "group" to group.name)
            return true
        }

        // Check if reset is allowed for this group
        if (!templateSettings.allowReset) {
            messages.send(sender, "reset-not-allowed", "group" to group.name)
            return true
        }

        // Get the template
        val templateName = templateSettings.templateName ?: group.name
        val template = templateService.getTemplate(templateName)

        if (template == null) {
            messages.send(sender, "template-not-found", "name" to templateName)
            return true
        }

        // Apply the template
        val groupApi = group.toApiModel()
        val success = templateService.applyTemplate(
            player = sender,
            template = template,
            group = groupApi,
            trigger = TemplateApplyTrigger.MANUAL,
            clearFirst = templateSettings.clearInventoryFirst
        )

        if (success) {
            messages.send(sender, "inventory-reset",
                "template" to template.getEffectiveDisplayName(),
                "group" to group.name
            )
        } else {
            messages.send(sender, "reset-failed")
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        val groupService = plugin.serviceManager.groupService

        return when (args.size) {
            1 -> {
                // Suggest groups that have templates with allowReset enabled
                groupService.getAllGroups()
                    .filter { it.templateSettings?.enabled == true && it.templateSettings?.allowReset == true }
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[0].lowercase()) }
            }
            else -> emptyList()
        }
    }
}

package sh.pcx.xinventories.internal.command.subcommand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.service.BulkOperationStatus
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Command for performing bulk operations on player inventories.
 *
 * Usage:
 * - /xinv bulk clear <group> [--confirm]
 * - /xinv bulk apply-template <group> <template> [--confirm]
 * - /xinv bulk reset-stats <group> [--confirm]
 * - /xinv bulk export <group> <file>
 */
class BulkCommand : Subcommand {

    override val name = "bulk"
    override val permission = "xinventories.admin.bulk"
    override val usage = "/xinv bulk <clear|apply-template|reset-stats|export> <group> [args...]"
    override val description = "Perform bulk operations on inventories"

    // Track pending confirmations
    private val pendingConfirmations = ConcurrentHashMap<UUID, PendingBulkOperation>()

    private data class PendingBulkOperation(
        val operation: String,
        val group: String,
        val args: List<String>,
        val timestamp: Long = System.currentTimeMillis()
    ) {
        fun isExpired(): Boolean = System.currentTimeMillis() - timestamp > 30_000 // 30 seconds
    }

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        val bulkService = plugin.serviceManager.bulkOperationService
        val messages = plugin.serviceManager.messageService

        when (args[0].lowercase()) {
            "clear" -> handleClear(plugin, sender, args.drop(1).toTypedArray())
            "apply-template" -> handleApplyTemplate(plugin, sender, args.drop(1).toTypedArray())
            "reset-stats" -> handleResetStats(plugin, sender, args.drop(1).toTypedArray())
            "export" -> handleExport(plugin, sender, args.drop(1).toTypedArray())
            "status" -> handleStatus(plugin, sender)
            "cancel" -> handleCancel(plugin, sender, args.drop(1).toTypedArray())
            else -> {
                sender.sendMessage(Component.text("Unknown bulk operation: ${args[0]}", NamedTextColor.RED))
                sendUsage(sender)
            }
        }

        return true
    }

    private suspend fun handleClear(plugin: PluginContext, sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /xinv bulk clear <group> [--confirm]", NamedTextColor.RED))
            return
        }

        val groupName = args[0]
        val hasConfirm = args.contains("--confirm")
        val senderId = getSenderId(sender)

        // Verify group exists
        if (plugin.serviceManager.groupService.getGroup(groupName) == null) {
            sender.sendMessage(Component.text("Group '$groupName' not found.", NamedTextColor.RED))
            return
        }

        if (!hasConfirm) {
            // Store pending confirmation
            pendingConfirmations[senderId] = PendingBulkOperation("clear", groupName, emptyList())
            sender.sendMessage(Component.text("", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("WARNING: This will delete ALL inventory data for group '$groupName'!", NamedTextColor.RED))
            sender.sendMessage(Component.text("Run '/xinv bulk clear $groupName --confirm' to proceed.", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("This cannot be undone!", NamedTextColor.RED))
            sender.sendMessage(Component.text("", NamedTextColor.YELLOW))
            return
        }

        // Execute operation
        sender.sendMessage(Component.text("Starting bulk clear operation for group '$groupName'...", NamedTextColor.YELLOW))

        val result = plugin.serviceManager.bulkOperationService.clearGroup(sender, groupName) { progress ->
            if (progress.processed % 50 == 0) {
                sender.sendMessage(Component.text("Progress: ${progress.processed}/${progress.totalPlayers} (${progress.percentComplete}%)", NamedTextColor.GRAY))
            }
        }

        // Report result
        when (result.status) {
            BulkOperationStatus.COMPLETED -> {
                sender.sendMessage(Component.text("Bulk clear completed!", NamedTextColor.GREEN))
                sender.sendMessage(Component.text("  Processed: ${result.totalPlayers} players", NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  Success: ${result.successCount}", NamedTextColor.GREEN))
                sender.sendMessage(Component.text("  Failed: ${result.failCount}", NamedTextColor.RED))
                sender.sendMessage(Component.text("  Duration: ${result.durationMs}ms", NamedTextColor.GRAY))
            }
            BulkOperationStatus.FAILED -> {
                sender.sendMessage(Component.text("Bulk clear failed: ${result.error}", NamedTextColor.RED))
            }
            BulkOperationStatus.CANCELLED -> {
                sender.sendMessage(Component.text("Bulk clear was cancelled.", NamedTextColor.YELLOW))
            }
            else -> {}
        }

        pendingConfirmations.remove(senderId)
    }

    private suspend fun handleApplyTemplate(plugin: PluginContext, sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /xinv bulk apply-template <group> <template> [--confirm]", NamedTextColor.RED))
            return
        }

        val groupName = args[0]
        val templateName = args[1]
        val hasConfirm = args.contains("--confirm")
        val senderId = getSenderId(sender)

        // Verify group exists
        if (plugin.serviceManager.groupService.getGroup(groupName) == null) {
            sender.sendMessage(Component.text("Group '$groupName' not found.", NamedTextColor.RED))
            return
        }

        // Verify template exists
        if (plugin.serviceManager.templateService.getTemplate(templateName) == null) {
            sender.sendMessage(Component.text("Template '$templateName' not found.", NamedTextColor.RED))
            return
        }

        if (!hasConfirm) {
            pendingConfirmations[senderId] = PendingBulkOperation("apply-template", groupName, listOf(templateName))
            sender.sendMessage(Component.text("", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("WARNING: This will apply template '$templateName' to ALL players in group '$groupName'!", NamedTextColor.RED))
            sender.sendMessage(Component.text("Run '/xinv bulk apply-template $groupName $templateName --confirm' to proceed.", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("", NamedTextColor.YELLOW))
            return
        }

        sender.sendMessage(Component.text("Starting bulk template application for group '$groupName'...", NamedTextColor.YELLOW))

        val result = plugin.serviceManager.bulkOperationService.applyTemplateToGroup(sender, groupName, templateName) { progress ->
            if (progress.processed % 50 == 0) {
                sender.sendMessage(Component.text("Progress: ${progress.processed}/${progress.totalPlayers} (${progress.percentComplete}%)", NamedTextColor.GRAY))
            }
        }

        when (result.status) {
            BulkOperationStatus.COMPLETED -> {
                sender.sendMessage(Component.text("Bulk template application completed!", NamedTextColor.GREEN))
                sender.sendMessage(Component.text("  Processed: ${result.totalPlayers} players", NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  Success: ${result.successCount}", NamedTextColor.GREEN))
                sender.sendMessage(Component.text("  Failed: ${result.failCount}", NamedTextColor.RED))
                sender.sendMessage(Component.text("  Duration: ${result.durationMs}ms", NamedTextColor.GRAY))
            }
            BulkOperationStatus.FAILED -> {
                sender.sendMessage(Component.text("Bulk template application failed: ${result.error}", NamedTextColor.RED))
            }
            BulkOperationStatus.CANCELLED -> {
                sender.sendMessage(Component.text("Bulk template application was cancelled.", NamedTextColor.YELLOW))
            }
            else -> {}
        }

        pendingConfirmations.remove(senderId)
    }

    private suspend fun handleResetStats(plugin: PluginContext, sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /xinv bulk reset-stats <group> [--confirm]", NamedTextColor.RED))
            return
        }

        val groupName = args[0]
        val hasConfirm = args.contains("--confirm")
        val senderId = getSenderId(sender)

        if (plugin.serviceManager.groupService.getGroup(groupName) == null) {
            sender.sendMessage(Component.text("Group '$groupName' not found.", NamedTextColor.RED))
            return
        }

        if (!hasConfirm) {
            pendingConfirmations[senderId] = PendingBulkOperation("reset-stats", groupName, emptyList())
            sender.sendMessage(Component.text("", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("WARNING: This will reset health, hunger, XP for ALL players in group '$groupName'!", NamedTextColor.RED))
            sender.sendMessage(Component.text("Run '/xinv bulk reset-stats $groupName --confirm' to proceed.", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("", NamedTextColor.YELLOW))
            return
        }

        sender.sendMessage(Component.text("Starting bulk stats reset for group '$groupName'...", NamedTextColor.YELLOW))

        val result = plugin.serviceManager.bulkOperationService.resetStatsForGroup(sender, groupName) { progress ->
            if (progress.processed % 50 == 0) {
                sender.sendMessage(Component.text("Progress: ${progress.processed}/${progress.totalPlayers} (${progress.percentComplete}%)", NamedTextColor.GRAY))
            }
        }

        when (result.status) {
            BulkOperationStatus.COMPLETED -> {
                sender.sendMessage(Component.text("Bulk stats reset completed!", NamedTextColor.GREEN))
                sender.sendMessage(Component.text("  Processed: ${result.totalPlayers} players", NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  Success: ${result.successCount}", NamedTextColor.GREEN))
                sender.sendMessage(Component.text("  Failed: ${result.failCount}", NamedTextColor.RED))
                sender.sendMessage(Component.text("  Duration: ${result.durationMs}ms", NamedTextColor.GRAY))
            }
            BulkOperationStatus.FAILED -> {
                sender.sendMessage(Component.text("Bulk stats reset failed: ${result.error}", NamedTextColor.RED))
            }
            BulkOperationStatus.CANCELLED -> {
                sender.sendMessage(Component.text("Bulk stats reset was cancelled.", NamedTextColor.YELLOW))
            }
            else -> {}
        }

        pendingConfirmations.remove(senderId)
    }

    private suspend fun handleExport(plugin: PluginContext, sender: CommandSender, args: Array<String>) {
        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /xinv bulk export <group> <file>", NamedTextColor.RED))
            return
        }

        val groupName = args[0]
        var fileName = args[1]

        if (plugin.serviceManager.groupService.getGroup(groupName) == null) {
            sender.sendMessage(Component.text("Group '$groupName' not found.", NamedTextColor.RED))
            return
        }

        // Ensure file has .json extension
        if (!fileName.endsWith(".json")) {
            fileName += ".json"
        }

        val outputFile = File(plugin.plugin.dataFolder, "exports/$fileName")

        sender.sendMessage(Component.text("Starting bulk export for group '$groupName'...", NamedTextColor.YELLOW))

        val result = plugin.serviceManager.bulkOperationService.exportGroup(sender, groupName, outputFile) { progress ->
            if (progress.processed % 50 == 0) {
                sender.sendMessage(Component.text("Progress: ${progress.processed}/${progress.totalPlayers} (${progress.percentComplete}%)", NamedTextColor.GRAY))
            }
        }

        when (result.status) {
            BulkOperationStatus.COMPLETED -> {
                sender.sendMessage(Component.text("Bulk export completed!", NamedTextColor.GREEN))
                sender.sendMessage(Component.text("  Exported: ${result.successCount} players", NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  File: ${outputFile.absolutePath}", NamedTextColor.GRAY))
                sender.sendMessage(Component.text("  Duration: ${result.durationMs}ms", NamedTextColor.GRAY))
            }
            BulkOperationStatus.FAILED -> {
                sender.sendMessage(Component.text("Bulk export failed: ${result.error}", NamedTextColor.RED))
            }
            BulkOperationStatus.CANCELLED -> {
                sender.sendMessage(Component.text("Bulk export was cancelled.", NamedTextColor.YELLOW))
            }
            else -> {}
        }
    }

    private fun handleStatus(plugin: PluginContext, sender: CommandSender) {
        val activeOps = plugin.serviceManager.bulkOperationService.getActiveOperations()

        if (activeOps.isEmpty()) {
            sender.sendMessage(Component.text("No bulk operations currently running.", NamedTextColor.GRAY))
            return
        }

        sender.sendMessage(Component.text("Active Bulk Operations:", NamedTextColor.GOLD))
        for (op in activeOps) {
            sender.sendMessage(Component.text("  ${op.operationId}: ${op.operationType} on '${op.group}'", NamedTextColor.YELLOW))
            sender.sendMessage(Component.text("    Progress: ${op.processed}/${op.totalPlayers} (${op.percentComplete}%)", NamedTextColor.GRAY))
        }
    }

    private suspend fun handleCancel(plugin: PluginContext, sender: CommandSender, args: Array<String>) {
        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /xinv bulk cancel <operation-id>", NamedTextColor.RED))
            return
        }

        val operationId = args[0]
        val cancelled = plugin.serviceManager.bulkOperationService.cancelOperation(operationId)

        if (cancelled) {
            sender.sendMessage(Component.text("Operation '$operationId' cancelled.", NamedTextColor.GREEN))
        } else {
            sender.sendMessage(Component.text("Operation '$operationId' not found or already completed.", NamedTextColor.RED))
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("Bulk Operations:", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("  /xinv bulk clear <group> [--confirm]", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("  /xinv bulk apply-template <group> <template> [--confirm]", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("  /xinv bulk reset-stats <group> [--confirm]", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("  /xinv bulk export <group> <file>", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("  /xinv bulk status", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("  /xinv bulk cancel <operation-id>", NamedTextColor.YELLOW))
    }

    private fun getSenderId(sender: CommandSender): UUID {
        return if (sender is org.bukkit.entity.Player) {
            sender.uniqueId
        } else {
            UUID(0, 0) // Console UUID
        }
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("clear", "apply-template", "reset-stats", "export", "status", "cancel")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "clear", "apply-template", "reset-stats", "export" ->
                    plugin.serviceManager.groupService.getAllGroups()
                        .map { it.name }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                "cancel" ->
                    plugin.serviceManager.bulkOperationService.getActiveOperations()
                        .map { it.operationId }
                        .filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "apply-template" ->
                    plugin.serviceManager.templateService.getAllTemplates()
                        .map { it.name }
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                "clear", "reset-stats" -> listOf("--confirm")
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                else -> emptyList()
            }
            4 -> when (args[0].lowercase()) {
                "apply-template" -> listOf("--confirm")
                    .filter { it.startsWith(args[3], ignoreCase = true) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

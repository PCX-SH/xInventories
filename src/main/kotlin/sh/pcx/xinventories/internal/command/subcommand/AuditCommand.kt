package sh.pcx.xinventories.internal.command.subcommand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.AuditAction
import sh.pcx.xinventories.internal.model.AuditEntry
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Command for viewing and searching audit logs.
 *
 * Usage:
 * - /xinv audit <player> [--limit 50]
 * - /xinv audit search <action> [--from date] [--to date]
 * - /xinv audit export <file> [--from date] [--to date]
 */
class AuditCommand : Subcommand {

    override val name = "audit"
    override val permission = "xinventories.admin.audit"
    override val usage = "/xinv audit <player|search|export> [args...]"
    override val description = "View and search audit logs"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val auditService = plugin.serviceManager.auditService

        if (auditService == null || !auditService.isEnabled) {
            sender.sendMessage(Component.text("Audit logging is not enabled.", NamedTextColor.RED))
            return true
        }

        if (args.isEmpty()) {
            sendUsage(sender)
            return true
        }

        when (args[0].lowercase()) {
            "search" -> handleSearch(plugin, sender, args.drop(1).toTypedArray())
            "export" -> handleExport(plugin, sender, args.drop(1).toTypedArray())
            "stats" -> handleStats(plugin, sender)
            else -> handlePlayerLookup(plugin, sender, args)
        }

        return true
    }

    private suspend fun handlePlayerLookup(plugin: XInventories, sender: CommandSender, args: Array<String>) {
        val auditService = plugin.serviceManager.auditService ?: return

        val playerName = args[0]
        val limit = parseLimit(args)

        // Try to find player UUID
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)
        if (!offlinePlayer.hasPlayedBefore() && !offlinePlayer.isOnline) {
            sender.sendMessage(Component.text("Player '$playerName' not found.", NamedTextColor.RED))
            return
        }

        val uuid = offlinePlayer.uniqueId
        val entries = auditService.getEntriesForPlayer(uuid, limit)

        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("No audit entries found for $playerName.", NamedTextColor.GRAY))
            return
        }

        sender.sendMessage(Component.text("Audit Log for $playerName (${entries.size} entries):", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("----------------------------------------", NamedTextColor.DARK_GRAY))

        for (entry in entries) {
            sender.sendMessage(formatEntry(entry))
        }

        sender.sendMessage(Component.text("----------------------------------------", NamedTextColor.DARK_GRAY))
    }

    private suspend fun handleSearch(plugin: XInventories, sender: CommandSender, args: Array<String>) {
        val auditService = plugin.serviceManager.auditService ?: return

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /xinv audit search <action> [--from date] [--to date]", NamedTextColor.RED))
            sender.sendMessage(Component.text("Actions: ${AuditAction.entries.joinToString(", ") { it.name }}", NamedTextColor.GRAY))
            return
        }

        val actionName = args[0]
        val action = AuditAction.fromName(actionName)
        if (action == null) {
            sender.sendMessage(Component.text("Unknown action: $actionName", NamedTextColor.RED))
            sender.sendMessage(Component.text("Valid actions: ${AuditAction.entries.joinToString(", ") { it.name }}", NamedTextColor.GRAY))
            return
        }

        val from = parseDate(args, "--from")
        val to = parseDate(args, "--to")
        val limit = parseLimit(args)

        val entries = auditService.searchByAction(action, from, to, limit)

        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("No audit entries found for action $actionName.", NamedTextColor.GRAY))
            return
        }

        sender.sendMessage(Component.text("Search Results for $actionName (${entries.size} entries):", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("----------------------------------------", NamedTextColor.DARK_GRAY))

        for (entry in entries) {
            sender.sendMessage(formatEntry(entry))
        }

        sender.sendMessage(Component.text("----------------------------------------", NamedTextColor.DARK_GRAY))
    }

    private suspend fun handleExport(plugin: XInventories, sender: CommandSender, args: Array<String>) {
        val auditService = plugin.serviceManager.auditService ?: return

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /xinv audit export <file> [--from date] [--to date]", NamedTextColor.RED))
            return
        }

        if (!sender.hasPermission("xinventories.admin.audit.export")) {
            sender.sendMessage(Component.text("You don't have permission to export audit logs.", NamedTextColor.RED))
            return
        }

        var fileName = args[0]
        if (!fileName.endsWith(".csv")) {
            fileName += ".csv"
        }

        val from = parseDate(args, "--from") ?: Instant.now().minus(30, ChronoUnit.DAYS)
        val to = parseDate(args, "--to") ?: Instant.now()

        val entries = auditService.getEntriesInRange(from, to)

        if (entries.isEmpty()) {
            sender.sendMessage(Component.text("No audit entries found in the specified range.", NamedTextColor.GRAY))
            return
        }

        val outputFile = File(plugin.dataFolder, "exports/$fileName")
        val success = auditService.exportToCsv(entries, outputFile)

        if (success) {
            sender.sendMessage(Component.text("Exported ${entries.size} audit entries to ${outputFile.absolutePath}", NamedTextColor.GREEN))
        } else {
            sender.sendMessage(Component.text("Failed to export audit log.", NamedTextColor.RED))
        }
    }

    private suspend fun handleStats(plugin: XInventories, sender: CommandSender) {
        val auditService = plugin.serviceManager.auditService ?: return

        val entryCount = auditService.getEntryCount()
        val storageSize = auditService.getStorageSize()
        val config = auditService.config

        sender.sendMessage(Component.text("Audit Log Statistics:", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("  Total entries: $entryCount", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("  Storage size: ${formatBytes(storageSize)}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("  Retention: ${config.retentionDays} days", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("  Log views: ${if (config.logViews) "enabled" else "disabled"}", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("  Log saves: ${if (config.logSaves) "enabled" else "disabled"}", NamedTextColor.GRAY))
    }

    private fun formatEntry(entry: AuditEntry): Component {
        val timestamp = LocalDateTime.ofInstant(entry.timestamp, ZoneId.systemDefault())
            .format(timeFormatter)

        val actionColor = if (entry.action.isDestructive) NamedTextColor.RED else NamedTextColor.GREEN

        val component = Component.text()
            .append(Component.text("[$timestamp] ", NamedTextColor.GRAY))
            .append(Component.text(entry.actorName, NamedTextColor.YELLOW))
            .append(Component.text(" ${entry.action.displayName} ", actionColor))
            .append(Component.text(entry.targetName, NamedTextColor.AQUA))

        if (entry.group != null) {
            component.append(Component.text(" in ", NamedTextColor.GRAY))
            component.append(Component.text(entry.group, NamedTextColor.WHITE))
        }

        if (entry.details != null) {
            component.hoverEvent(HoverEvent.showText(
                Component.text(entry.details, NamedTextColor.WHITE)
            ))
        }

        return component.build()
    }

    private fun parseLimit(args: Array<String>): Int {
        val limitIndex = args.indexOfFirst { it == "--limit" }
        return if (limitIndex >= 0 && limitIndex + 1 < args.size) {
            args[limitIndex + 1].toIntOrNull() ?: 50
        } else {
            50
        }
    }

    private fun parseDate(args: Array<String>, flag: String): Instant? {
        val flagIndex = args.indexOfFirst { it == flag }
        if (flagIndex < 0 || flagIndex + 1 >= args.size) return null

        val dateStr = args[flagIndex + 1]
        return try {
            val localDate = java.time.LocalDate.parse(dateStr, dateFormatter)
            localDate.atStartOfDay(ZoneId.systemDefault()).toInstant()
        } catch (e: Exception) {
            null
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun sendUsage(sender: CommandSender) {
        sender.sendMessage(Component.text("Audit Commands:", NamedTextColor.GOLD))
        sender.sendMessage(Component.text("  /xinv audit <player> [--limit 50]", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("    View audit log for a player", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("  /xinv audit search <action> [--from date] [--to date]", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("    Search by action type", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("  /xinv audit export <file> [--from date] [--to date]", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("    Export audit log to CSV", NamedTextColor.GRAY))
        sender.sendMessage(Component.text("  /xinv audit stats", NamedTextColor.YELLOW))
        sender.sendMessage(Component.text("    View audit log statistics", NamedTextColor.GRAY))
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> {
                val suggestions = mutableListOf("search", "export", "stats")
                suggestions.addAll(Bukkit.getOnlinePlayers().map { it.name })
                suggestions.filter { it.startsWith(args[0], ignoreCase = true) }
            }
            2 -> when (args[0].lowercase()) {
                "search" -> AuditAction.entries.map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                else -> listOf("--limit").filter { it.startsWith(args[1], ignoreCase = true) }
            }
            3 -> when (args[0].lowercase()) {
                "search" -> listOf("--from", "--to", "--limit")
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                else -> if (args[1] == "--limit") emptyList() else listOf("--limit")
            }
            else -> listOf("--from", "--to", "--limit")
                .filter { !args.contains(it) }
                .filter { it.startsWith(args.last(), ignoreCase = true) }
        }
    }
}

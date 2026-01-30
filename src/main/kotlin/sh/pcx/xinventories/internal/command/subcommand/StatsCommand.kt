package sh.pcx.xinventories.internal.command.subcommand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.command.CommandSender
import sh.pcx.xinventories.PluginContext

/**
 * Command for viewing plugin statistics and metrics.
 *
 * Usage:
 * - /xinv stats - Overview of all statistics
 * - /xinv stats cache - Cache statistics
 * - /xinv stats storage - Storage statistics
 * - /xinv stats performance - Performance metrics
 */
class StatsCommand : Subcommand {

    override val name = "stats"
    override val aliases = listOf("metrics", "statistics")
    override val permission = "xinventories.admin.stats"
    override val usage = "/xinv stats [cache|storage|performance]"
    override val description = "View plugin statistics"

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val metricsService = plugin.serviceManager.metricsService

        when (args.getOrNull(0)?.lowercase()) {
            "cache" -> showCacheStats(sender, metricsService)
            "storage" -> showStorageStats(sender, metricsService)
            "performance" -> showPerformanceStats(sender, metricsService)
            else -> showOverview(plugin, sender, metricsService)
        }

        return true
    }

    private suspend fun showOverview(plugin: PluginContext, sender: CommandSender, metricsService: sh.pcx.xinventories.internal.service.MetricsService) {
        val metrics = metricsService.getMetrics()

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
        sender.sendMessage(Component.text()
            .append(Component.text("  xInventories ", NamedTextColor.AQUA, TextDecoration.BOLD))
            .append(Component.text("Statistics", NamedTextColor.WHITE))
            .build())
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))

        // General
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("  General", NamedTextColor.GOLD, TextDecoration.BOLD))
        sender.sendMessage(formatStat("    Uptime", metricsService.getUptimeFormatted()))
        sender.sendMessage(formatStat("    Online Players", "${metrics.onlinePlayers}"))
        sender.sendMessage(formatStat("    Players with Data", "${metrics.totalPlayersWithData}"))

        // Storage
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("  Storage", NamedTextColor.GOLD, TextDecoration.BOLD))
        sender.sendMessage(formatStat("    Type", metrics.storageType))
        sender.sendMessage(formatStat("    Size", formatBytes(metrics.storageSizeBytes)))
        sender.sendMessage(formatStat("    Entries", "${metrics.entryCount}"))

        // Cache
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("  Cache", NamedTextColor.GOLD, TextDecoration.BOLD))
        sender.sendMessage(formatStat("    Size", "${metrics.cacheStats.size}/${metrics.cacheStats.maxSize}"))
        sender.sendMessage(formatStat("    Hit Rate", "${String.format("%.1f", metrics.cacheStats.hitRate * 100)}%"))

        // Performance
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("  Performance", NamedTextColor.GOLD, TextDecoration.BOLD))
        sender.sendMessage(formatStat("    Avg Save", "${String.format("%.2f", metrics.avgSaveTimeMs)}ms"))
        sender.sendMessage(formatStat("    Avg Load", "${String.format("%.2f", metrics.avgLoadTimeMs)}ms"))
        sender.sendMessage(formatStat("    Saves/min", "${metrics.savesPerMinute.toInt()}"))
        sender.sendMessage(formatStat("    Loads/min", "${metrics.loadsPerMinute.toInt()}"))

        // Sync (if enabled)
        if (metrics.syncEnabled) {
            sender.sendMessage(Component.empty())
            sender.sendMessage(Component.text("  Sync", NamedTextColor.GOLD, TextDecoration.BOLD))
            sender.sendMessage(formatStat("    Status", "Enabled", NamedTextColor.GREEN))
            sender.sendMessage(formatStat("    Server ID", metrics.syncServerId ?: "N/A"))
        }

        // Audit (if enabled)
        if (metrics.auditEnabled) {
            sender.sendMessage(Component.empty())
            sender.sendMessage(Component.text("  Audit", NamedTextColor.GOLD, TextDecoration.BOLD))
            sender.sendMessage(formatStat("    Entries", "${metrics.auditEntryCount}"))
            sender.sendMessage(formatStat("    Size", formatBytes(metrics.auditStorageSizeBytes)))
        }

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))
        sender.sendMessage(Component.text("  Use /xinv stats <cache|storage|performance> for details", NamedTextColor.GRAY))
        sender.sendMessage(Component.empty())
    }

    private fun showCacheStats(sender: CommandSender, metricsService: sh.pcx.xinventories.internal.service.MetricsService) {
        val cache = metricsService.getCacheMetrics()

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("Cache Statistics", NamedTextColor.GOLD, TextDecoration.BOLD))
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))

        if (!cache.enabled) {
            sender.sendMessage(Component.text("  Cache is disabled", NamedTextColor.RED))
            return
        }

        sender.sendMessage(formatStat("  Status", "Enabled", NamedTextColor.GREEN))
        sender.sendMessage(formatStat("  Size", "${cache.size}/${cache.maxSize}"))
        sender.sendMessage(formatStat("  Fill %", "${String.format("%.1f", cache.size.toDouble() / cache.maxSize * 100)}%"))
        sender.sendMessage(Component.empty())
        sender.sendMessage(formatStat("  Hit Count", "${cache.hitCount}"))
        sender.sendMessage(formatStat("  Miss Count", "${cache.missCount}"))
        sender.sendMessage(formatStat("  Hit Rate", "${String.format("%.2f", cache.hitRate * 100)}%"))
        sender.sendMessage(formatStat("  Evictions", "${cache.evictionCount}"))
        sender.sendMessage(Component.empty())
        sender.sendMessage(formatStat("  Dirty Entries", "${cache.dirtyEntries}"))
        sender.sendMessage(formatStat("  Write-Behind Delay", "${cache.writeBehindDelayMs}ms"))
    }

    private suspend fun showStorageStats(sender: CommandSender, metricsService: sh.pcx.xinventories.internal.service.MetricsService) {
        val storage = metricsService.getStorageMetrics()

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("Storage Statistics", NamedTextColor.GOLD, TextDecoration.BOLD))
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))

        val healthColor = if (storage.isHealthy) NamedTextColor.GREEN else NamedTextColor.RED
        val healthText = if (storage.isHealthy) "Healthy" else "Unhealthy"

        sender.sendMessage(formatStat("  Type", storage.type))
        sender.sendMessage(formatStat("  Status", healthText, healthColor))
        sender.sendMessage(Component.empty())
        sender.sendMessage(formatStat("  Size", formatBytes(storage.sizeBytes)))
        sender.sendMessage(formatStat("  Entries", "${storage.entryCount}"))
        sender.sendMessage(formatStat("  Players", "${storage.playerCount}"))
        sender.sendMessage(formatStat("  Groups", "${storage.groupCount}"))

        if (storage.playerCount > 0) {
            val avgEntriesPerPlayer = storage.entryCount.toDouble() / storage.playerCount
            sender.sendMessage(formatStat("  Avg Entries/Player", String.format("%.1f", avgEntriesPerPlayer)))
        }
    }

    private fun showPerformanceStats(sender: CommandSender, metricsService: sh.pcx.xinventories.internal.service.MetricsService) {
        val perf = metricsService.getPerformanceMetrics()

        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("Performance Metrics", NamedTextColor.GOLD, TextDecoration.BOLD))
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY))

        // Save timings
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("  Save Operations", NamedTextColor.YELLOW))
        sender.sendMessage(formatStat("    Total", "${perf.totalSaves}"))
        sender.sendMessage(formatStat("    Per Minute", "${perf.savesPerMinute.toInt()}"))
        sender.sendMessage(formatStat("    Avg Time", "${String.format("%.2f", perf.avgSaveTimeMs)}ms"))
        sender.sendMessage(formatStat("    Min Time", "${perf.minSaveTimeMs}ms"))
        sender.sendMessage(formatStat("    Max Time", "${perf.maxSaveTimeMs}ms"))

        // Load timings
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("  Load Operations", NamedTextColor.YELLOW))
        sender.sendMessage(formatStat("    Total", "${perf.totalLoads}"))
        sender.sendMessage(formatStat("    Per Minute", "${perf.loadsPerMinute.toInt()}"))
        sender.sendMessage(formatStat("    Avg Time", "${String.format("%.2f", perf.avgLoadTimeMs)}ms"))
        sender.sendMessage(formatStat("    Min Time", "${perf.minLoadTimeMs}ms"))
        sender.sendMessage(formatStat("    Max Time", "${perf.maxLoadTimeMs}ms"))

        // Errors
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("  Errors", NamedTextColor.YELLOW))
        sender.sendMessage(formatStat("    Total Errors", "${perf.totalErrors}"))
        sender.sendMessage(formatStat("    Error Rate", "${String.format("%.4f", perf.errorRate * 100)}%"))

        // Throughput
        sender.sendMessage(Component.empty())
        sender.sendMessage(Component.text("  Throughput", NamedTextColor.YELLOW))
        sender.sendMessage(formatStat("    Ops/Minute", "${perf.operationsPerMinute.toInt()}"))
    }

    private fun formatStat(label: String, value: String, valueColor: NamedTextColor = NamedTextColor.WHITE): Component {
        return Component.text()
            .append(Component.text(label, NamedTextColor.GRAY))
            .append(Component.text(": ", NamedTextColor.DARK_GRAY))
            .append(Component.text(value, valueColor))
            .build()
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${String.format("%.1f", bytes / 1024.0)} KB"
            bytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", bytes / (1024.0 * 1024))} MB"
            else -> "${String.format("%.2f", bytes / (1024.0 * 1024 * 1024))} GB"
        }
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("cache", "storage", "performance")
                .filter { it.startsWith(args[0], ignoreCase = true) }
            else -> emptyList()
        }
    }
}

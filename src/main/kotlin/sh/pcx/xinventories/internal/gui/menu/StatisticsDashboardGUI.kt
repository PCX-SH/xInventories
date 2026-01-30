package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.service.CacheMetrics
import sh.pcx.xinventories.internal.service.PerformanceMetrics
import sh.pcx.xinventories.internal.service.PluginMetrics
import sh.pcx.xinventories.internal.service.StorageMetrics

/**
 * Statistics dashboard GUI showing plugin health and performance metrics.
 *
 * Features:
 * - Storage statistics (total players, data size, backend type)
 * - Cache performance (hit rate, size, memory usage)
 * - Operation counts (saves/loads per minute)
 * - Error rates
 * - Sync status (if enabled)
 * - Refresh button
 * - Colored indicators (green=good, yellow=warning, red=critical)
 */
class StatisticsDashboardGUI(
    plugin: XInventories
) : AbstractGUI(
    plugin,
    Component.text("Statistics Dashboard", NamedTextColor.GOLD),
    45
) {
    private var metrics: PluginMetrics? = null
    private var cacheMetrics: CacheMetrics? = null
    private var storageMetrics: StorageMetrics? = null
    private var performanceMetrics: PerformanceMetrics? = null

    init {
        loadMetrics()
    }

    private fun loadMetrics() {
        plugin.scope.launch {
            val metricsService = plugin.serviceManager.metricsService

            metrics = metricsService.getMetrics()
            cacheMetrics = metricsService.getCacheMetrics()
            storageMetrics = metricsService.getStorageMetrics()
            performanceMetrics = metricsService.getPerformanceMetrics()

            setupItems()
        }
    }

    private fun setupItems() {
        items.clear()

        // Fill border
        fillBorder(GUIComponents.filler())

        // Storage statistics (left side)
        setupStorageStats()

        // Cache performance (center)
        setupCacheStats()

        // Performance metrics (right side)
        setupPerformanceStats()

        // Sync status (bottom center)
        setupSyncStatus()

        // Navigation
        setupNavigation()
    }

    private fun setupStorageStats() {
        val storage = storageMetrics
        val m = metrics

        // Storage header
        setItem(1, 1, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Storage Statistics", NamedTextColor.AQUA)
            .lore(Component.text("Backend: ${storage?.type ?: "Unknown"}", NamedTextColor.WHITE))
            .lore(Component.empty())
            .lore(Component.text("Total Players: ${storage?.playerCount ?: 0}", NamedTextColor.GRAY))
            .lore(Component.text("Total Entries: ${storage?.entryCount ?: 0}", NamedTextColor.GRAY))
            .lore(Component.text("Groups: ${storage?.groupCount ?: 0}", NamedTextColor.GRAY))
            .build()
        )

        // Data size indicator
        val sizeMB = storage?.sizeMB ?: 0.0
        val sizeColor = when {
            sizeMB < 100 -> NamedTextColor.GREEN
            sizeMB < 500 -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }

        setItem(2, 1, GUIItemBuilder()
            .material(Material.PAPER)
            .name(Component.text("Data Size", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            .lore(Component.text(String.format("%.2f MB", sizeMB), sizeColor))
            .lore(Component.empty())
            .lore(Component.text("${storage?.sizeBytes ?: 0} bytes", NamedTextColor.DARK_GRAY))
            .build()
        )

        // Health indicator
        val healthy = storage?.isHealthy ?: false
        val healthMaterial = if (healthy) Material.LIME_DYE else Material.RED_DYE
        val healthColor = if (healthy) NamedTextColor.GREEN else NamedTextColor.RED
        val healthText = if (healthy) "Healthy" else "Unhealthy"

        setItem(3, 1, GUIItemBuilder()
            .material(healthMaterial)
            .name(Component.text("Storage Health", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            .lore(Component.text(healthText, healthColor))
            .build()
        )
    }

    private fun setupCacheStats() {
        val cache = cacheMetrics

        // Cache header
        val cacheEnabled = cache?.enabled ?: false
        val cacheMaterial = if (cacheEnabled) Material.ENDER_EYE else Material.ENDER_PEARL

        setItem(1, 4, GUIItemBuilder()
            .material(cacheMaterial)
            .name("Cache Performance", NamedTextColor.LIGHT_PURPLE)
            .lore(Component.text("Status: ${if (cacheEnabled) "Enabled" else "Disabled"}",
                if (cacheEnabled) NamedTextColor.GREEN else NamedTextColor.RED))
            .lore(Component.empty())
            .lore(Component.text("Size: ${cache?.size ?: 0}/${cache?.maxSize ?: 0}", NamedTextColor.GRAY))
            .lore(Component.text("Dirty Entries: ${cache?.dirtyEntries ?: 0}", NamedTextColor.GRAY))
            .build()
        )

        // Hit rate indicator
        val hitRate = (cache?.hitRate ?: 0.0) * 100
        val hitRateColor = when {
            hitRate >= 80 -> NamedTextColor.GREEN
            hitRate >= 50 -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }

        setItem(2, 4, GUIItemBuilder()
            .material(Material.TARGET)
            .name(Component.text("Cache Hit Rate", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            .lore(Component.text(String.format("%.1f%%", hitRate), hitRateColor))
            .lore(Component.empty())
            .lore(Component.text("Hits: ${cache?.hitCount ?: 0}", NamedTextColor.GRAY))
            .lore(Component.text("Misses: ${cache?.missCount ?: 0}", NamedTextColor.GRAY))
            .build()
        )

        // Eviction count
        setItem(3, 4, GUIItemBuilder()
            .material(Material.HOPPER)
            .name(Component.text("Cache Evictions", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            .lore(Component.text("${cache?.evictionCount ?: 0}", NamedTextColor.YELLOW))
            .lore(Component.empty())
            .lore(Component.text("Write-behind delay: ${cache?.writeBehindDelayMs ?: 0}ms", NamedTextColor.DARK_GRAY))
            .build()
        )
    }

    private fun setupPerformanceStats() {
        val perf = performanceMetrics
        val m = metrics

        // Operations header
        setItem(1, 7, GUIItemBuilder()
            .material(Material.REDSTONE)
            .name("Operation Metrics", NamedTextColor.RED)
            .lore(Component.text("Saves/min: ${String.format("%.1f", perf?.savesPerMinute ?: 0.0)}", NamedTextColor.GRAY))
            .lore(Component.text("Loads/min: ${String.format("%.1f", perf?.loadsPerMinute ?: 0.0)}", NamedTextColor.GRAY))
            .lore(Component.empty())
            .lore(Component.text("Total Saves: ${perf?.totalSaves ?: 0}", NamedTextColor.DARK_GRAY))
            .lore(Component.text("Total Loads: ${perf?.totalLoads ?: 0}", NamedTextColor.DARK_GRAY))
            .build()
        )

        // Timing stats
        val avgSaveTime = perf?.avgSaveTimeMs ?: 0.0
        val timingColor = when {
            avgSaveTime < 50 -> NamedTextColor.GREEN
            avgSaveTime < 200 -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }

        setItem(2, 7, GUIItemBuilder()
            .material(Material.CLOCK)
            .name(Component.text("Timing Statistics", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            .lore(Component.text("Avg Save: ${String.format("%.1f", avgSaveTime)}ms", timingColor))
            .lore(Component.text("Avg Load: ${String.format("%.1f", perf?.avgLoadTimeMs ?: 0.0)}ms", NamedTextColor.GRAY))
            .lore(Component.empty())
            .lore(Component.text("Min/Max Save: ${perf?.minSaveTimeMs ?: 0}/${perf?.maxSaveTimeMs ?: 0}ms", NamedTextColor.DARK_GRAY))
            .lore(Component.text("Min/Max Load: ${perf?.minLoadTimeMs ?: 0}/${perf?.maxLoadTimeMs ?: 0}ms", NamedTextColor.DARK_GRAY))
            .build()
        )

        // Error rate
        val errorRate = (perf?.errorRate ?: 0.0) * 100
        val errorColor = when {
            errorRate < 1 -> NamedTextColor.GREEN
            errorRate < 5 -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }
        val errorMaterial = when {
            errorRate < 1 -> Material.LIME_DYE
            errorRate < 5 -> Material.YELLOW_DYE
            else -> Material.RED_DYE
        }

        setItem(3, 7, GUIItemBuilder()
            .material(errorMaterial)
            .name(Component.text("Error Rate", NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false))
            .lore(Component.text(String.format("%.2f%%", errorRate), errorColor))
            .lore(Component.empty())
            .lore(Component.text("Total Errors: ${perf?.totalErrors ?: 0}", NamedTextColor.DARK_GRAY))
            .build()
        )
    }

    private fun setupSyncStatus() {
        val m = metrics
        val syncEnabled = m?.syncEnabled ?: false

        if (syncEnabled) {
            setItem(4, 4, GUIItemBuilder()
                .material(Material.ENDER_PEARL)
                .name("Sync Status", NamedTextColor.DARK_AQUA)
                .lore(Component.text("Status: Enabled", NamedTextColor.GREEN))
                .lore(Component.text("Server ID: ${m?.syncServerId ?: "Unknown"}", NamedTextColor.GRAY))
                .build()
            )
        } else {
            setItem(4, 4, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("Sync Status", NamedTextColor.DARK_AQUA)
                .lore(Component.text("Status: Disabled", NamedTextColor.GRAY))
                .build()
            )
        }

        // Uptime
        val metricsService = plugin.serviceManager.metricsService
        setItem(4, 2, GUIItemBuilder()
            .material(Material.EXPERIENCE_BOTTLE)
            .name("Uptime", NamedTextColor.GREEN)
            .lore(Component.text(metricsService.getUptimeFormatted(), NamedTextColor.WHITE))
            .build()
        )

        // Online players
        setItem(4, 6, GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name("Online Players", NamedTextColor.AQUA)
            .lore(Component.text("${m?.onlinePlayers ?: 0} online", NamedTextColor.WHITE))
            .lore(Component.text("${m?.totalPlayersWithData ?: 0} total with data", NamedTextColor.GRAY))
            .build()
        )
    }

    private fun setupNavigation() {
        // Refresh button
        setItem(4, 0, GUIItemBuilder()
            .material(Material.SUNFLOWER)
            .name("Refresh", NamedTextColor.YELLOW)
            .lore("Click to refresh statistics")
            .onClick { event ->
                val player = event.whoClicked as Player
                StatisticsDashboardGUI(plugin).open(player)
            }
            .build()
        )

        // Audit log shortcut
        setItem(4, 1, GUIItemBuilder()
            .material(Material.WRITABLE_BOOK)
            .name("View Audit Log", NamedTextColor.LIGHT_PURPLE)
            .lore("Click to open audit log viewer")
            .onClick { event ->
                val player = event.whoClicked as Player
                AuditLogGUI(plugin).open(player)
            }
            .build()
        )

        // Close button
        setItem(4, 8, GUIComponents.closeButton())
    }

    override fun fillEmptySlots(inventory: Inventory) {
        val filler = GUIComponents.filler()
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

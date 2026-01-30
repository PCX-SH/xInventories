package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

/**
 * Economy overview GUI showing per-group balance totals and leaderboards.
 *
 * Features:
 * - Per-group balance totals
 * - Top balances per group (leaderboard)
 * - Total economy value
 * - Click group to see player balances
 * - Economy health metrics
 */
class EconomyOverviewGUI(
    plugin: PluginContext,
    private val selectedGroup: String? = null,
    private var currentPage: Int = 0
) : AbstractGUI(
    plugin,
    if (selectedGroup != null) Component.text("Economy: $selectedGroup", NamedTextColor.GOLD)
    else Component.text("Economy Overview", NamedTextColor.GOLD),
    54
) {
    companion object {
        private const val PLAYERS_PER_PAGE = 21
        private val CURRENCY_FORMAT = NumberFormat.getCurrencyInstance(Locale.US)
    }

    private var groupTotals: Map<String, Double> = emptyMap()
    private var topPlayers: List<Pair<String, Double>> = emptyList()
    private var totalEconomyValue: Double = 0.0
    private var totalPages: Int = 1

    init {
        loadEconomyData()
    }

    private fun loadEconomyData() {
        plugin.scope.launch {
            val economyService = plugin.serviceManager.economyService
            val storageService = plugin.serviceManager.storageService
            val groupService = plugin.serviceManager.groupService

            if (!economyService.isEnabled() || !economyService.isSeparateByGroup()) {
                setupDisabledView()
                return@launch
            }

            // Calculate group totals
            val groups = groupService.getAllGroups()
            val playerUUIDs = storageService.getAllPlayerUUIDs()
            val totals = mutableMapOf<String, Double>()
            val playerBalances = mutableMapOf<UUID, MutableMap<String, Double>>()

            // Load all balances
            for (uuid in playerUUIDs) {
                val balances = economyService.getAllBalances(uuid)
                playerBalances[uuid] = balances.toMutableMap()

                balances.forEach { (group, balance) ->
                    totals[group] = (totals[group] ?: 0.0) + balance
                }
            }

            groupTotals = totals
            totalEconomyValue = totals.values.sum()

            // If a group is selected, load top players for that group
            if (selectedGroup != null) {
                topPlayers = playerBalances.mapNotNull { (uuid, balances) ->
                    val balance = balances[selectedGroup] ?: return@mapNotNull null
                    if (balance <= 0.0) return@mapNotNull null
                    val player = plugin.plugin.server.getOfflinePlayer(uuid)
                    val name = player.name ?: uuid.toString().take(8)
                    name to balance
                }.sortedByDescending { it.second }

                totalPages = maxOf(1, (topPlayers.size + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE)
                if (currentPage >= totalPages) currentPage = totalPages - 1
            }

            setupItems()
        }
    }

    private fun setupDisabledView() {
        items.clear()
        fillBorder(GUIComponents.filler())

        setItem(2, 4, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Economy Disabled", NamedTextColor.RED)
            .lore("Per-group economy is not enabled")
            .lore("")
            .lore("Enable in config.yml:")
            .lore("economy:")
            .lore("  enabled: true")
            .lore("  separate-by-group: true")
            .build()
        )

        setItem(4, 4, GUIComponents.closeButton())
    }

    private fun setupItems() {
        items.clear()
        fillBorder(GUIComponents.filler())

        if (selectedGroup == null) {
            setupOverview()
        } else {
            setupGroupDetail()
        }

        setupNavigation()
    }

    private fun setupOverview() {
        // Total economy value
        setItem(0, 4, GUIItemBuilder()
            .material(Material.GOLD_BLOCK)
            .name("Total Economy Value", NamedTextColor.GOLD)
            .lore(Component.text(formatCurrency(totalEconomyValue), NamedTextColor.YELLOW))
            .lore(Component.empty())
            .lore(Component.text("${groupTotals.size} groups", NamedTextColor.GRAY))
            .build()
        )

        // Group totals
        var slot = 10
        val groups = groupTotals.entries.sortedByDescending { it.value }

        for ((group, total) in groups) {
            if (slot >= 44) break
            // Skip border slots
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++
                continue
            }

            val healthColor = getEconomyHealthColor(total)
            val material = getGroupMaterial(group)

            setItem(slot, GUIItemBuilder()
                .material(material)
                .name(Component.text(group, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text("Total: ", NamedTextColor.GRAY)
                    .append(Component.text(formatCurrency(total), healthColor)))
                .lore(Component.empty())
                .lore(Component.text("Click to view leaderboard", NamedTextColor.YELLOW))
                .onClick { event ->
                    val player = event.whoClicked as Player
                    EconomyOverviewGUI(plugin, group, 0).open(player)
                }
                .build()
            )
            slot++
        }

        // Economy health indicator
        val wealthDistribution = calculateWealthDistribution()
        val healthColor = when {
            wealthDistribution < 0.3 -> NamedTextColor.GREEN
            wealthDistribution < 0.6 -> NamedTextColor.YELLOW
            else -> NamedTextColor.RED
        }
        val healthText = when {
            wealthDistribution < 0.3 -> "Healthy (balanced)"
            wealthDistribution < 0.6 -> "Moderate concentration"
            else -> "High concentration"
        }

        setItem(4, 4, GUIItemBuilder()
            .material(Material.HEART_OF_THE_SEA)
            .name("Economy Health", NamedTextColor.LIGHT_PURPLE)
            .lore(Component.text(healthText, healthColor))
            .lore(Component.empty())
            .lore(Component.text("Gini coefficient: ${String.format("%.2f", wealthDistribution)}", NamedTextColor.DARK_GRAY))
            .build()
        )
    }

    private fun setupGroupDetail() {
        // Group header
        val groupTotal = groupTotals[selectedGroup] ?: 0.0

        setItem(0, 4, GUIItemBuilder()
            .material(Material.GOLD_INGOT)
            .name(Component.text(selectedGroup!!, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false))
            .lore(Component.text("Total: ", NamedTextColor.GRAY)
                .append(Component.text(formatCurrency(groupTotal), NamedTextColor.YELLOW)))
            .lore(Component.text("${topPlayers.size} players", NamedTextColor.GRAY))
            .build()
        )

        // Leaderboard
        val startIndex = currentPage * PLAYERS_PER_PAGE
        val pageEntries = topPlayers.drop(startIndex).take(PLAYERS_PER_PAGE)

        var slot = 10
        var rank = startIndex + 1
        for ((name, balance) in pageEntries) {
            if (slot >= 44) break
            // Skip border slots
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++
                continue
            }

            val rankColor = when (rank) {
                1 -> NamedTextColor.GOLD
                2 -> NamedTextColor.GRAY
                3 -> NamedTextColor.GOLD
                else -> NamedTextColor.WHITE
            }
            val rankMaterial = when (rank) {
                1 -> Material.GOLD_NUGGET
                2 -> Material.IRON_NUGGET
                3 -> Material.COPPER_INGOT
                else -> Material.PAPER
            }

            setItem(slot, GUIItemBuilder()
                .material(rankMaterial)
                .name(Component.text("#$rank $name", rankColor).decoration(TextDecoration.ITALIC, false))
                .lore(Component.text(formatCurrency(balance), NamedTextColor.GREEN))
                .build()
            )
            slot++
            rank++
        }
    }

    private fun setupNavigation() {
        // Back button
        if (selectedGroup != null) {
            setItem(5, 0, GUIComponents.backButton {
                // Handled in onClick
            }.let { item ->
                GUIItem(item.itemStack) { event ->
                    val player = event.whoClicked as Player
                    EconomyOverviewGUI(plugin, null, 0).open(player)
                }
            })
        }

        // Refresh
        setItem(5, 1, GUIItemBuilder()
            .material(Material.SUNFLOWER)
            .name("Refresh", NamedTextColor.YELLOW)
            .lore("Click to refresh data")
            .onClick { event ->
                val player = event.whoClicked as Player
                EconomyOverviewGUI(plugin, selectedGroup, currentPage).open(player)
            }
            .build()
        )

        if (selectedGroup != null) {
            // Page info
            setItem(5, 4, GUIItemBuilder()
                .material(Material.BOOK)
                .name("Page ${currentPage + 1} of $totalPages", NamedTextColor.WHITE)
                .lore("${topPlayers.size} players")
                .build()
            )

            // Previous page
            if (currentPage > 0) {
                setItem(5, 3, GUIComponents.previousPageButton(currentPage + 1) {
                    // Handled below
                }.let { item ->
                    GUIItem(item.itemStack) { event ->
                        val player = event.whoClicked as Player
                        EconomyOverviewGUI(plugin, selectedGroup, currentPage - 1).open(player)
                    }
                })
            }

            // Next page
            if (currentPage < totalPages - 1) {
                setItem(5, 5, GUIComponents.nextPageButton(currentPage + 1) {
                    // Handled below
                }.let { item ->
                    GUIItem(item.itemStack) { event ->
                        val player = event.whoClicked as Player
                        EconomyOverviewGUI(plugin, selectedGroup, currentPage + 1).open(player)
                    }
                })
            }
        }

        // Close button
        setItem(5, 8, GUIComponents.closeButton())
    }

    private fun formatCurrency(amount: Double): String {
        return CURRENCY_FORMAT.format(amount)
    }

    private fun getEconomyHealthColor(total: Double): NamedTextColor {
        return when {
            total < 1000 -> NamedTextColor.WHITE
            total < 100000 -> NamedTextColor.GREEN
            total < 1000000 -> NamedTextColor.YELLOW
            else -> NamedTextColor.GOLD
        }
    }

    private fun getGroupMaterial(group: String): Material {
        // Try to match group name to a thematic material
        return when {
            group.contains("survival", ignoreCase = true) -> Material.GRASS_BLOCK
            group.contains("creative", ignoreCase = true) -> Material.COMMAND_BLOCK
            group.contains("skyblock", ignoreCase = true) -> Material.SAND
            group.contains("prison", ignoreCase = true) -> Material.IRON_BARS
            group.contains("minigame", ignoreCase = true) -> Material.FIREWORK_ROCKET
            else -> Material.CHEST
        }
    }

    private fun calculateWealthDistribution(): Double {
        // Simple Gini coefficient calculation
        if (topPlayers.isEmpty()) return 0.0

        val balances = topPlayers.map { it.second }.sorted()
        val n = balances.size
        val mean = balances.average()

        if (mean == 0.0) return 0.0

        var sumDifferences = 0.0
        for (i in balances.indices) {
            for (j in balances.indices) {
                sumDifferences += kotlin.math.abs(balances[i] - balances[j])
            }
        }

        return sumDifferences / (2 * n * n * mean)
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

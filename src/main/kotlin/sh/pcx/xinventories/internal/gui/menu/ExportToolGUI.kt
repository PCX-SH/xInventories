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
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.Group
import java.util.UUID

/**
 * Export tool GUI for exporting player inventory data.
 *
 * Features:
 * - Select export scope (single player, group, all)
 * - Select players/groups via GUI
 * - Choose export format (JSON)
 * - Configure options (include versions, include deaths)
 * - Export button
 * - Show export file location when done
 */
class ExportToolGUI(
    plugin: XInventories,
    private val scope: ExportScope = ExportScope.SELECT,
    private val selectedPlayer: UUID? = null,
    private val selectedPlayerName: String? = null,
    private val selectedGroup: String? = null,
    private val includeVersions: Boolean = false,
    private val includeDeaths: Boolean = false,
    private val currentPage: Int = 0
) : AbstractGUI(
    plugin,
    getTitle(scope, selectedPlayerName, selectedGroup),
    54
) {
    enum class ExportScope {
        SELECT,
        SINGLE_PLAYER,
        GROUP,
        ALL
    }

    companion object {
        private const val ITEMS_PER_PAGE = 21

        private fun getTitle(scope: ExportScope, playerName: String?, groupName: String?): Component {
            return when (scope) {
                ExportScope.SELECT -> Component.text("Export Tool", NamedTextColor.GREEN)
                ExportScope.SINGLE_PLAYER -> Component.text("Export: ${playerName ?: "Select Player"}", NamedTextColor.GREEN)
                ExportScope.GROUP -> Component.text("Export: ${groupName ?: "Select Group"}", NamedTextColor.GREEN)
                ExportScope.ALL -> Component.text("Export: All Data", NamedTextColor.GREEN)
            }
        }
    }

    private var players: List<Pair<UUID, String>> = emptyList()
    private var groups: List<Group> = emptyList()
    private var totalPages: Int = 1

    init {
        loadData()
    }

    private fun loadData() {
        plugin.scope.launch {
            when (scope) {
                ExportScope.SINGLE_PLAYER -> {
                    val storageService = plugin.serviceManager.storageService
                    val allUUIDs = storageService.getAllPlayerUUIDs()
                    players = allUUIDs.map { uuid ->
                        val player = plugin.server.getOfflinePlayer(uuid)
                        uuid to (player.name ?: uuid.toString().take(8))
                    }.sortedBy { it.second }
                    totalPages = maxOf(1, (players.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE)
                }
                ExportScope.GROUP -> {
                    groups = plugin.serviceManager.groupService.getAllGroups()
                    totalPages = maxOf(1, (groups.size + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE)
                }
                else -> {}
            }

            setupItems()
        }
    }

    private fun setupItems() {
        items.clear()
        fillBorder(GUIComponents.filler())

        when (scope) {
            ExportScope.SELECT -> setupScopeSelection()
            ExportScope.SINGLE_PLAYER -> setupPlayerSelection()
            ExportScope.GROUP -> setupGroupSelection()
            ExportScope.ALL -> setupAllExport()
        }
    }

    private fun setupScopeSelection() {
        // Header
        setItem(1, 4, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Select Export Scope", NamedTextColor.AQUA)
            .lore("Choose what to export")
            .build()
        )

        // Single player option
        setItem(2, 2, GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name("Single Player", NamedTextColor.YELLOW)
            .lore("Export one player's data")
            .lore("")
            .lore("Click to select a player")
            .onClick { event ->
                val player = event.whoClicked as Player
                ExportToolGUI(plugin, ExportScope.SINGLE_PLAYER).open(player)
            }
            .build()
        )

        // Group option
        setItem(2, 4, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Entire Group", NamedTextColor.GREEN)
            .lore("Export all players in a group")
            .lore("")
            .lore("Click to select a group")
            .onClick { event ->
                val player = event.whoClicked as Player
                ExportToolGUI(plugin, ExportScope.GROUP).open(player)
            }
            .build()
        )

        // All data option
        setItem(2, 6, GUIItemBuilder()
            .material(Material.ENDER_CHEST)
            .name("All Data", NamedTextColor.LIGHT_PURPLE)
            .lore("Export everything")
            .lore("")
            .lore("Warning: May take a while!")
            .onClick { event ->
                val player = event.whoClicked as Player
                ExportToolGUI(plugin, ExportScope.ALL).open(player)
            }
            .build()
        )

        // Close button
        setItem(5, 4, GUIComponents.closeButton())
    }

    private fun setupPlayerSelection() {
        // Back button
        setItem(0, 0, GUIComponents.backButton {
            // Handled below
        }.let { item ->
            GUIItem(item.itemStack) { event ->
                val player = event.whoClicked as Player
                ExportToolGUI(plugin, ExportScope.SELECT).open(player)
            }
        })

        if (selectedPlayer == null) {
            // Player list
            val startIndex = currentPage * ITEMS_PER_PAGE
            val pageItems = players.drop(startIndex).take(ITEMS_PER_PAGE)

            var slot = 10
            for ((uuid, name) in pageItems) {
                if (slot >= 44) break
                if (slot % 9 == 0 || slot % 9 == 8) {
                    slot++
                    continue
                }

                setItem(slot, GUIItemBuilder()
                    .material(Material.PLAYER_HEAD)
                    .name(Component.text(name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
                    .lore(Component.text(uuid.toString().take(16) + "...", NamedTextColor.DARK_GRAY))
                    .lore("")
                    .lore("Click to select")
                    .onClick { event ->
                        val player = event.whoClicked as Player
                        ExportToolGUI(plugin, ExportScope.SINGLE_PLAYER, uuid, name).open(player)
                    }
                    .build()
                )
                slot++
            }

            // Pagination
            setupPagination()
        } else {
            // Export configuration
            setupExportConfig()
        }
    }

    private fun setupGroupSelection() {
        // Back button
        setItem(0, 0, GUIComponents.backButton {
            // Handled below
        }.let { item ->
            GUIItem(item.itemStack) { event ->
                val player = event.whoClicked as Player
                ExportToolGUI(plugin, ExportScope.SELECT).open(player)
            }
        })

        if (selectedGroup == null) {
            // Group list
            var slot = 10
            for (group in groups) {
                if (slot >= 44) break
                if (slot % 9 == 0 || slot % 9 == 8) {
                    slot++
                    continue
                }

                setItem(slot, GUIItemBuilder()
                    .material(Material.CHEST)
                    .name(Component.text(group.name, NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
                    .lore(Component.text("${group.worlds.size} worlds", NamedTextColor.GRAY))
                    .lore("")
                    .lore("Click to select")
                    .onClick { event ->
                        val player = event.whoClicked as Player
                        ExportToolGUI(plugin, ExportScope.GROUP, selectedGroup = group.name).open(player)
                    }
                    .build()
                )
                slot++
            }
        } else {
            // Export configuration
            setupExportConfig()
        }
    }

    private fun setupAllExport() {
        // Back button
        setItem(0, 0, GUIComponents.backButton {
            // Handled below
        }.let { item ->
            GUIItem(item.itemStack) { event ->
                val player = event.whoClicked as Player
                ExportToolGUI(plugin, ExportScope.SELECT).open(player)
            }
        })

        // Export configuration
        setupExportConfig()
    }

    private fun setupExportConfig() {
        // Header with selection info
        val headerText = when (scope) {
            ExportScope.SINGLE_PLAYER -> "Export: $selectedPlayerName"
            ExportScope.GROUP -> "Export Group: $selectedGroup"
            ExportScope.ALL -> "Export All Data"
            else -> "Export"
        }

        setItem(1, 4, GUIItemBuilder()
            .material(Material.WRITABLE_BOOK)
            .name(headerText, NamedTextColor.AQUA)
            .lore("Configure export options below")
            .build()
        )

        // Format (always JSON for now)
        setItem(2, 2, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Format: JSON", NamedTextColor.WHITE)
            .lore("Export format is JSON")
            .lore("")
            .lore("Human-readable format")
            .lore("Compatible with import")
            .build()
        )

        // Include versions toggle
        setItem(2, 4, GUIItemBuilder()
            .material(if (includeVersions) Material.LIME_DYE else Material.GRAY_DYE)
            .name("Include Versions", if (includeVersions) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore(if (includeVersions) "Version history will be included" else "Version history will be skipped")
            .lore("")
            .lore("Click to toggle")
            .onClick { event ->
                val player = event.whoClicked as Player
                ExportToolGUI(plugin, scope, selectedPlayer, selectedPlayerName, selectedGroup, !includeVersions, includeDeaths, currentPage).open(player)
            }
            .build()
        )

        // Include deaths toggle
        setItem(2, 6, GUIItemBuilder()
            .material(if (includeDeaths) Material.LIME_DYE else Material.GRAY_DYE)
            .name("Include Deaths", if (includeDeaths) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore(if (includeDeaths) "Death records will be included" else "Death records will be skipped")
            .lore("")
            .lore("Click to toggle")
            .onClick { event ->
                val player = event.whoClicked as Player
                ExportToolGUI(plugin, scope, selectedPlayer, selectedPlayerName, selectedGroup, includeVersions, !includeDeaths, currentPage).open(player)
            }
            .build()
        )

        // Export button
        setItem(4, 4, GUIItemBuilder()
            .material(Material.EMERALD_BLOCK)
            .name("Export Now", NamedTextColor.GREEN)
            .lore("Click to start export")
            .onClick { event ->
                val player = event.whoClicked as Player
                executeExport(player)
            }
            .build()
        )

        // Close button
        setItem(5, 8, GUIComponents.closeButton())
    }

    private fun setupPagination() {
        // Page info
        setItem(5, 4, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Page ${currentPage + 1} of $totalPages", NamedTextColor.WHITE)
            .build()
        )

        // Previous page
        if (currentPage > 0) {
            setItem(5, 3, GUIComponents.previousPageButton(currentPage + 1) {
                // Handled below
            }.let { item ->
                GUIItem(item.itemStack) { event ->
                    val player = event.whoClicked as Player
                    ExportToolGUI(plugin, scope, selectedPlayer, selectedPlayerName, selectedGroup, includeVersions, includeDeaths, currentPage - 1).open(player)
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
                    ExportToolGUI(plugin, scope, selectedPlayer, selectedPlayerName, selectedGroup, includeVersions, includeDeaths, currentPage + 1).open(player)
                }
            })
        }

        // Close button
        setItem(5, 8, GUIComponents.closeButton())
    }

    private fun executeExport(player: Player) {
        player.sendMessage(Component.text("Starting export...", NamedTextColor.YELLOW))

        plugin.scope.launch {
            val exportService = plugin.serviceManager.exportService

            val result = when (scope) {
                ExportScope.SINGLE_PLAYER -> {
                    if (selectedPlayer == null || selectedGroup == null) {
                        // Need to get all groups for this player
                        val groupService = plugin.serviceManager.groupService
                        val groups = groupService.getAllGroups()
                        var lastResult = exportService.exportPlayer(selectedPlayer!!, groups.first().name)

                        for (group in groups.drop(1)) {
                            lastResult = exportService.exportPlayer(selectedPlayer, group.name)
                        }
                        lastResult
                    } else {
                        exportService.exportPlayer(selectedPlayer, selectedGroup)
                    }
                }
                ExportScope.GROUP -> {
                    if (selectedGroup != null) {
                        exportService.exportGroup(selectedGroup)
                    } else {
                        sh.pcx.xinventories.internal.service.ExportResult(
                            success = false,
                            filePath = null,
                            playerCount = 0,
                            message = "No group selected"
                        )
                    }
                }
                ExportScope.ALL -> {
                    // Export all groups
                    val groupService = plugin.serviceManager.groupService
                    val groups = groupService.getAllGroups()
                    var totalPlayers = 0
                    var lastPath: String? = null

                    for (group in groups) {
                        val groupResult = exportService.exportGroup(group.name)
                        if (groupResult.success) {
                            totalPlayers += groupResult.playerCount
                            lastPath = groupResult.filePath
                        }
                    }

                    sh.pcx.xinventories.internal.service.ExportResult(
                        success = true,
                        filePath = exportService.getExportDirectory().absolutePath,
                        playerCount = totalPlayers,
                        message = "Exported ${groups.size} groups to ${exportService.getExportDirectory().absolutePath}"
                    )
                }
                else -> sh.pcx.xinventories.internal.service.ExportResult(
                    success = false,
                    filePath = null,
                    playerCount = 0,
                    message = "Invalid scope"
                )
            }

            if (result.success) {
                player.sendMessage(Component.text("Export complete!", NamedTextColor.GREEN))
                player.sendMessage(Component.text("File: ", NamedTextColor.GRAY)
                    .append(Component.text(result.filePath ?: "Unknown", NamedTextColor.YELLOW)))
                player.sendMessage(Component.text("Players: ${result.playerCount}", NamedTextColor.GRAY))
            } else {
                player.sendMessage(Component.text("Export failed: ${result.message}", NamedTextColor.RED))
            }
        }
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

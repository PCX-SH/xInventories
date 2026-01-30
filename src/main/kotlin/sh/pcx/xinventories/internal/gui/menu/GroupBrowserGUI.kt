package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
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
 * GUI for browsing and managing groups with player counts and quick actions.
 *
 * Features:
 * - List all groups with player counts
 * - Show group icon (configurable or auto-generated)
 * - Click to view group details
 * - Quick actions menu (clear all inventories, apply template to group)
 * - Show worlds in each group
 * - Link to existing GroupDetailGUI
 */
class GroupBrowserGUI(
    plugin: XInventories,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Group Browser", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 36
    private val groups: List<Group>
    private val playerCountsPerGroup: Map<String, Int>

    init {
        groups = plugin.serviceManager.groupService.getAllGroups().sortedBy { it.name }
        playerCountsPerGroup = calculatePlayerCounts()
        setupItems()
    }

    /**
     * Calculates the number of players with data in each group.
     */
    private fun calculatePlayerCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()

        runBlocking {
            val storageService = plugin.serviceManager.storageService
            val allUuids = storageService.getAllPlayerUUIDs()

            for (group in groups) {
                var count = 0
                for (uuid in allUuids) {
                    val playerGroups = storageService.getPlayerGroups(uuid)
                    if (group.name in playerGroups) {
                        count++
                    }
                }
                counts[group.name] = count
            }
        }

        return counts
    }

    private fun setupItems() {
        // Fill bottom two rows with filler
        for (i in 36..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (groups.isEmpty()) 0 else (groups.size - 1) / itemsPerPage

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to main menu")
            .onClick { event ->
                val player = event.whoClicked as Player
                MainMenuGUI(plugin).open(player)
            }
            .build()
        )

        // Bulk operations button
        setItem(46, GUIItemBuilder()
            .material(Material.COMMAND_BLOCK)
            .name("Bulk Operations", NamedTextColor.LIGHT_PURPLE)
            .lore("Perform operations on")
            .lore("all players in a group")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationsGUI(plugin).open(player)
            }
            .build()
        )

        // Search button
        setItem(47, GUIItemBuilder()
            .material(Material.COMPASS)
            .name("Search Inventories", NamedTextColor.AQUA)
            .lore("Search for items across")
            .lore("player inventories")
            .onClick { event ->
                val player = event.whoClicked as Player
                InventorySearchGUI(plugin).open(player)
            }
            .build()
        )

        // Previous page button
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .lore("Go to page $page")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    GroupBrowserGUI(plugin, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("${groups.size} total groups")
            .build()
        )

        // Next page button
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .lore("Go to page ${page + 2}")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    GroupBrowserGUI(plugin, page + 1).open(player)
                }
                .build()
            )
        }

        // Close button
        setItem(53, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )

        // Populate groups
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, groups.size)

        for (i in startIndex until endIndex) {
            val group = groups[i]
            val slot = i - startIndex

            setItem(slot, createGroupItem(group))
        }
    }

    private fun createGroupItem(group: Group): GUIItem {
        val playerCount = playerCountsPerGroup[group.name] ?: 0
        val onlinePlayers = countOnlinePlayersInGroup(group.name)
        val material = getGroupMaterial(group)
        val color = if (group.isDefault) NamedTextColor.LIGHT_PURPLE else NamedTextColor.AQUA

        return GUIItemBuilder()
            .material(material)
            .name(group.name, color)
            .lore("Players: $playerCount (${onlinePlayers} online)")
            .lore("Worlds: ${group.worlds.size}")
            .lore("Patterns: ${group.patterns.size}")
            .lore("Priority: ${group.priority}")
            .lore("")
            .apply {
                if (group.worlds.isNotEmpty()) {
                    lore("Worlds:")
                    group.worlds.take(5).forEach { world ->
                        lore("  - $world")
                    }
                    if (group.worlds.size > 5) {
                        lore("  ... and ${group.worlds.size - 5} more")
                    }
                }
            }
            .lore("")
            .lore(if (group.isDefault) "Default group" else "")
            .lore("Left-click: View details")
            .lore("Right-click: Quick actions")
            .onClick { event ->
                val player = event.whoClicked as Player
                if (event.isRightClick) {
                    GroupQuickActionsGUI(plugin, group).open(player)
                } else {
                    GroupDetailGUI(plugin, group.name).open(player)
                }
            }
            .build()
    }

    private fun getGroupMaterial(group: Group): Material {
        return when {
            group.isDefault -> Material.ENDER_CHEST
            group.name.contains("creative", ignoreCase = true) -> Material.COMMAND_BLOCK
            group.name.contains("survival", ignoreCase = true) -> Material.GRASS_BLOCK
            group.name.contains("adventure", ignoreCase = true) -> Material.MAP
            group.name.contains("skyblock", ignoreCase = true) -> Material.OAK_SAPLING
            group.name.contains("minigame", ignoreCase = true) -> Material.GOLDEN_APPLE
            group.name.contains("pvp", ignoreCase = true) -> Material.IRON_SWORD
            group.name.contains("build", ignoreCase = true) -> Material.BRICKS
            else -> Material.CHEST
        }
    }

    private fun countOnlinePlayersInGroup(groupName: String): Int {
        return Bukkit.getOnlinePlayers().count { player ->
            val playerGroup = plugin.serviceManager.groupService.getGroupForPlayer(player)
            playerGroup.name == groupName
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

/**
 * GUI for quick actions on a group.
 */
class GroupQuickActionsGUI(
    plugin: XInventories,
    private val group: Group
) : AbstractGUI(
    plugin,
    Component.text("Group: ${group.name}", NamedTextColor.DARK_AQUA),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler())

        // Group info
        setItem(1, 1, GUIItemBuilder()
            .material(if (group.isDefault) Material.ENDER_CHEST else Material.CHEST)
            .name(group.name, NamedTextColor.GOLD)
            .lore("Worlds: ${group.worlds.size}")
            .lore("Priority: ${group.priority}")
            .build()
        )

        // View details button
        setItem(1, 3, GUIItemBuilder()
            .material(Material.BOOK)
            .name("View Details", NamedTextColor.AQUA)
            .lore("View full group details")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupDetailGUI(plugin, group.name).open(player)
            }
            .build()
        )

        // Clear all inventories button
        setItem(1, 5, GUIItemBuilder()
            .material(Material.TNT)
            .name("Clear All Inventories", NamedTextColor.RED)
            .lore("Clear inventories for all")
            .lore("players in this group")
            .lore("")
            .lore("WARNING: This cannot be undone!")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupClearConfirmGUI(plugin, group).open(player)
            }
            .build()
        )

        // Apply template button
        setItem(1, 7, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Apply Template", NamedTextColor.GREEN)
            .lore("Apply a template to all")
            .lore("players in this group")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupTemplateSelectGUI(plugin, group).open(player)
            }
            .build()
        )

        // Back button
        setItem(2, 0, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to group browser")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupBrowserGUI(plugin).open(player)
            }
            .build()
        )

        // Close button
        setItem(2, 8, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )
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

/**
 * GUI for confirming group inventory clear.
 */
class GroupClearConfirmGUI(
    plugin: XInventories,
    private val group: Group
) : AbstractGUI(
    plugin,
    Component.text("Clear Group Inventories?", NamedTextColor.DARK_RED),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler(Material.RED_STAINED_GLASS_PANE))

        // Warning info
        setItem(1, 4, GUIItemBuilder()
            .material(Material.TNT)
            .name("Clear All Inventories?", NamedTextColor.RED)
            .lore("Group: ${group.name}")
            .lore("")
            .lore("This will DELETE all inventory")
            .lore("data for every player in this group!")
            .lore("")
            .lore("This action cannot be undone!")
            .build()
        )

        // Confirm button
        setItem(1, 2, GUIItemBuilder()
            .material(Material.LIME_WOOL)
            .name("Confirm Clear", NamedTextColor.GREEN)
            .lore("Clear all inventories")
            .onClick { event ->
                val player = event.whoClicked as Player
                player.sendMessage(Component.text("Starting bulk clear operation...", NamedTextColor.YELLOW))
                player.closeInventory()

                // Execute bulk clear
                runBlocking {
                    val result = plugin.serviceManager.bulkOperationService.clearGroup(player, group.name)
                    player.sendMessage(Component.text(
                        "Cleared ${result.successCount}/${result.totalPlayers} player inventories in group '${group.name}'",
                        if (result.failCount > 0) NamedTextColor.YELLOW else NamedTextColor.GREEN
                    ))
                }
            }
            .build()
        )

        // Cancel button
        setItem(1, 6, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Cancel", NamedTextColor.RED)
            .lore("Go back")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupQuickActionsGUI(plugin, group).open(player)
            }
            .build()
        )
    }

    override fun fillEmptySlots(inventory: Inventory) {
        val filler = GUIComponents.filler(Material.RED_STAINED_GLASS_PANE)
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

/**
 * GUI for selecting a template to apply to a group.
 */
class GroupTemplateSelectGUI(
    plugin: XInventories,
    private val group: Group,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Select Template - ${group.name}", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 45
    private val templates = plugin.serviceManager.templateService.getAllTemplates()
        .sortedBy { it.name.lowercase() }

    init {
        setupItems()
    }

    private fun setupItems() {
        // Fill bottom row with filler
        for (i in 45..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (templates.isEmpty()) 0 else (templates.size - 1) / itemsPerPage

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to group actions")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupQuickActionsGUI(plugin, group).open(player)
            }
            .build()
        )

        // Info
        if (templates.isEmpty()) {
            setItem(22, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("No Templates", NamedTextColor.RED)
                .lore("No templates are available")
                .lore("")
                .lore("Create a template first with:")
                .lore("/xinv template create <name>")
                .build()
            )
        }

        // Previous page button
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    GroupTemplateSelectGUI(plugin, group, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("${templates.size} total templates")
            .build()
        )

        // Next page button
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    GroupTemplateSelectGUI(plugin, group, page + 1).open(player)
                }
                .build()
            )
        }

        // Close button
        setItem(53, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )

        // Populate templates
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, templates.size)

        for (i in startIndex until endIndex) {
            val template = templates[i]
            val slot = i - startIndex

            setItem(slot, GUIItemBuilder()
                .material(Material.CHEST)
                .name(template.getEffectiveDisplayName(), NamedTextColor.GOLD)
                .lore("ID: ${template.name}")
                .apply {
                    template.description?.let { lore(it) }
                }
                .lore("")
                .lore("Click to apply to group")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    GroupTemplateConfirmGUI(plugin, group, template).open(player)
                }
                .build()
            )
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

/**
 * GUI for confirming template application to a group.
 */
class GroupTemplateConfirmGUI(
    plugin: XInventories,
    private val group: Group,
    private val template: sh.pcx.xinventories.internal.model.InventoryTemplate
) : AbstractGUI(
    plugin,
    Component.text("Apply Template to Group?", NamedTextColor.DARK_AQUA),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler())

        // Info
        setItem(1, 4, GUIItemBuilder()
            .material(Material.NETHER_STAR)
            .name("Apply Template?", NamedTextColor.GOLD)
            .lore("Template: ${template.name}")
            .lore("Group: ${group.name}")
            .lore("")
            .lore("This will apply the template")
            .lore("to ALL players in this group!")
            .build()
        )

        // Confirm button
        setItem(1, 2, GUIItemBuilder()
            .material(Material.LIME_WOOL)
            .name("Confirm", NamedTextColor.GREEN)
            .lore("Apply template to all players")
            .onClick { event ->
                val player = event.whoClicked as Player
                player.sendMessage(Component.text("Starting bulk template apply...", NamedTextColor.YELLOW))
                player.closeInventory()

                // Execute bulk apply
                runBlocking {
                    val result = plugin.serviceManager.bulkOperationService.applyTemplateToGroup(
                        player, group.name, template.name
                    )
                    player.sendMessage(Component.text(
                        "Applied template '${template.name}' to ${result.successCount}/${result.totalPlayers} players in group '${group.name}'",
                        if (result.failCount > 0) NamedTextColor.YELLOW else NamedTextColor.GREEN
                    ))
                }
            }
            .build()
        )

        // Cancel button
        setItem(1, 6, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Cancel", NamedTextColor.RED)
            .lore("Go back")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupTemplateSelectGUI(plugin, group).open(player)
            }
            .build()
        )
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

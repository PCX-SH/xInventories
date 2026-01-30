package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * Main admin menu for xInventories.
 */
class MainMenuGUI(plugin: XInventories) : AbstractGUI(
    plugin,
    Component.text("xInventories Admin", NamedTextColor.DARK_AQUA),
    45
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        // Fill border
        fillBorder(GUIComponents.filler())

        // === Row 1: Core Management ===

        // Groups button
        setItem(1, 1, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Manage Groups", NamedTextColor.AQUA)
            .lore("View and edit inventory groups")
            .lore("")
            .lore("Click to open group management")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupListGUI(plugin).open(player)
            }
            .build()
        )

        // Worlds button
        setItem(1, 3, GUIItemBuilder()
            .material(Material.GRASS_BLOCK)
            .name("Manage Worlds", NamedTextColor.GREEN)
            .lore("Assign worlds to groups")
            .lore("")
            .lore("Click to open world management")
            .onClick { event ->
                val player = event.whoClicked as Player
                WorldManagerGUI(plugin).open(player)
            }
            .build()
        )

        // Players button
        setItem(1, 5, GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name("Player Management", NamedTextColor.AQUA)
            .lore("View and manage player inventories")
            .lore("")
            .lore("Click to browse players")
            .onClick { event ->
                val player = event.whoClicked as Player
                PlayerListGUI(plugin).open(player)
            }
            .build()
        )

        // Storage info button
        setItem(1, 7, GUIItemBuilder()
            .material(Material.ENDER_CHEST)
            .name("Storage Info", NamedTextColor.LIGHT_PURPLE)
            .lore("View storage statistics")
            .lore("and system information")
            .onClick { event ->
                val player = event.whoClicked as Player
                showStorageInfo(player)
            }
            .build()
        )

        // === Row 2: Administration Tools ===

        // Template Manager
        setItem(2, 1, GUIItemBuilder()
            .material(Material.BOOKSHELF)
            .name("Template Manager", NamedTextColor.GOLD)
            .lore("Create and manage inventory templates")
            .lore("")
            .lore("Templates can be applied to")
            .lore("players or entire groups")
            .lore("")
            .lore("Click to open")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplateManagerGUI(plugin).open(player)
            }
            .build()
        )

        // Group Browser
        setItem(2, 3, GUIItemBuilder()
            .material(Material.COMPASS)
            .name("Group Browser", NamedTextColor.YELLOW)
            .lore("Browse groups with player counts")
            .lore("")
            .lore("Quick actions for groups")
            .lore("like clear or apply templates")
            .lore("")
            .lore("Click to open")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupBrowserGUI(plugin).open(player)
            }
            .build()
        )

        // Bulk Operations
        setItem(2, 5, GUIItemBuilder()
            .material(Material.COMMAND_BLOCK)
            .name("Bulk Operations", NamedTextColor.LIGHT_PURPLE)
            .lore("Perform operations on all")
            .lore("players in a group")
            .lore("")
            .lore("Clear, apply templates,")
            .lore("reset stats, or export")
            .lore("")
            .lore("Click to open")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationsGUI(plugin).open(player)
            }
            .build()
        )

        // Inventory Search
        setItem(2, 7, GUIItemBuilder()
            .material(Material.SPYGLASS)
            .name("Inventory Search", NamedTextColor.AQUA)
            .lore("Search for items across")
            .lore("all player inventories")
            .lore("")
            .lore("Find by material, name,")
            .lore("or enchantment")
            .lore("")
            .lore("Click to open")
            .onClick { event ->
                val player = event.whoClicked as Player
                InventorySearchGUI(plugin).open(player)
            }
            .build()
        )

        // === Row 3: Info section ===

        // Info item
        setItem(3, 4, GUIItemBuilder()
            .material(Material.BOOK)
            .name("xInventories", NamedTextColor.GOLD)
            .lore("Per-world inventory management")
            .lore("")
            .lore("Use /xinv help for commands")
            .build()
        )

        // Close button
        setItem(4, 4, GUIComponents.closeButton())
    }

    private fun showStorageInfo(player: Player) {
        val storageService = plugin.serviceManager.storageService
        val config = plugin.configManager.mainConfig
        val cacheStats = storageService.getCacheStats()

        player.sendMessage(Component.text("=== Storage Info ===", NamedTextColor.GOLD))
        player.sendMessage(Component.text("Type: ${config.storage.type}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("Cache: ${cacheStats.size}/${cacheStats.maxSize}", NamedTextColor.GRAY))
        player.sendMessage(Component.text("Hit Rate: ${String.format("%.1f", cacheStats.hitRate * 100)}%", NamedTextColor.GRAY))
    }

    override fun fillEmptySlots(inventory: Inventory) {
        // Fill any remaining empty slots both visually AND in the items map
        val filler = GUIComponents.filler()
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

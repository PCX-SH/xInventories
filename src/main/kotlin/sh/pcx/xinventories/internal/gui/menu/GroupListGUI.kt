package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.Group
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * GUI for listing and managing groups.
 */
class GroupListGUI(
    plugin: XInventories,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Inventory Groups", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 45
    private val groups: List<Group>

    init {
        groups = plugin.serviceManager.groupService.getAllGroups().sortedBy { it.name }
        setupItems()
    }

    private fun setupItems() {
        // Fill bottom row
        for (i in 45..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (groups.isEmpty()) 0 else (groups.size - 1) / itemsPerPage

        // Navigation - Back button
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

        // Create group button
        setItem(46, GUIItemBuilder()
            .material(Material.EMERALD)
            .name("Create Group", NamedTextColor.GREEN)
            .lore("Create a new inventory group")
            .lore("")
            .lore("Use command:")
            .lore("/xinv group create <name>")
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
                    GroupListGUI(plugin, page - 1).open(player)
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
                    GroupListGUI(plugin, page + 1).open(player)
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
        val material = if (group.isDefault) Material.ENDER_CHEST else Material.CHEST
        val color = if (group.isDefault) NamedTextColor.LIGHT_PURPLE else NamedTextColor.AQUA

        return GUIItemBuilder()
            .material(material)
            .name(group.name, color)
            .lore("Worlds: ${group.worlds.size}")
            .lore("Patterns: ${group.patterns.size}")
            .lore("Priority: ${group.priority}")
            .lore("")
            .lore(if (group.isDefault) "Default group" else "")
            .lore("Click to edit this group")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupEditorGUI(plugin, group.name).open(player)
            }
            .build()
    }

    override fun fillEmptySlots(inventory: org.bukkit.inventory.Inventory) {
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

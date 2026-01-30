package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * GUI showing details of a specific group.
 */
class GroupDetailGUI(
    plugin: PluginContext,
    private val groupName: String
) : AbstractGUI(
    plugin,
    Component.text("Group: $groupName", NamedTextColor.DARK_AQUA),
    45
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        val groupService = plugin.serviceManager.groupService
        val group = groupService.getGroup(groupName)

        if (group == null) {
            // Group not found
            setItem(22, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("Group Not Found", NamedTextColor.RED)
                .build()
            )
            setItem(40, GUIComponents.backButton {})
            return
        }

        // Fill border
        fillBorder(GUIComponents.filler())

        // Group info
        setItem(1, 1, GUIItemBuilder()
            .material(if (group.isDefault) Material.ENDER_CHEST else Material.CHEST)
            .name(group.name, NamedTextColor.GOLD)
            .lore(if (group.isDefault) "Default Group" else "Custom Group")
            .build()
        )

        // Settings info
        setItem(1, 3, GUIItemBuilder()
            .material(Material.COMPARATOR)
            .name("Settings", NamedTextColor.YELLOW)
            .lore("Separate GameMode Inventories: ${group.settings.separateGameModeInventories}")
            .lore("Save Ender Chest: ${group.settings.saveEnderChest}")
            .build()
        )

        // Worlds list
        val worldsLore = if (group.worlds.isEmpty()) {
            listOf("No explicit worlds")
        } else {
            group.worlds.take(10).map { "- $it" } +
                    if (group.worlds.size > 10) listOf("... and ${group.worlds.size - 10} more") else emptyList()
        }

        setItem(1, 5, GUIItemBuilder()
            .material(Material.GRASS_BLOCK)
            .name("Worlds (${group.worlds.size})", NamedTextColor.GREEN)
            .apply {
                worldsLore.forEach { lore(it) }
            }
            .build()
        )

        // Patterns list
        val patternsLore = if (group.patterns.isEmpty()) {
            listOf("No patterns defined")
        } else {
            group.patterns.take(10).map { "- $it" } +
                    if (group.patterns.size > 10) listOf("... and ${group.patterns.size - 10} more") else emptyList()
        }

        setItem(1, 7, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Patterns (${group.patterns.size})", NamedTextColor.AQUA)
            .apply {
                patternsLore.forEach { lore(it) }
            }
            .build()
        )

        // Statistics
        setItem(3, 4, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Statistics", NamedTextColor.LIGHT_PURPLE)
            .lore("Players with data in this group")
            .lore("Use /xinv debug for more info")
            .build()
        )

        // Back button
        setItem(4, 0, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.RED)
            .lore("Return to group list")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupListGUI(plugin).open(player)
            }
            .build()
        )

        // Close button
        setItem(4, 8, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )
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

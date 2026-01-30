package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.Group
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * GUI for editing a group's configuration.
 */
class GroupEditorGUI(
    plugin: PluginContext,
    private val groupName: String
) : AbstractGUI(
    plugin,
    Component.text("Edit Group: $groupName", NamedTextColor.DARK_AQUA),
    54
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        val groupService = plugin.serviceManager.groupService
        val group = groupService.getGroup(groupName)

        if (group == null) {
            setItem(22, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("Group Not Found", NamedTextColor.RED)
                .build()
            )
            setItem(49, createBackButton())
            return
        }

        fillBorder(GUIComponents.filler())

        // Group info header
        setItem(4, GUIItemBuilder()
            .material(if (group.isDefault) Material.ENDER_CHEST else Material.CHEST)
            .name(group.name, NamedTextColor.GOLD)
            .lore(if (group.isDefault) "Default Group (cannot delete)" else "Custom Group")
            .lore("")
            .lore("Priority: ${group.priority}")
            .build()
        )

        // === WORLDS SECTION ===
        setItem(19, GUIItemBuilder()
            .material(Material.GRASS_BLOCK)
            .name("Manage Worlds", NamedTextColor.GREEN)
            .lore("Worlds: ${group.worlds.size}")
            .lore("")
            .lore("Click to manage world assignments")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupWorldsGUI(plugin, groupName).open(player)
            }
            .build()
        )

        // === PATTERNS SECTION ===
        setItem(21, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Manage Patterns", NamedTextColor.AQUA)
            .lore("Patterns: ${group.patterns.size}")
            .lore("")
            .lore("Click to manage regex patterns")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupPatternsGUI(plugin, groupName).open(player)
            }
            .build()
        )

        // === SETTINGS SECTION ===
        setItem(23, GUIItemBuilder()
            .material(Material.COMPARATOR)
            .name("Group Settings", NamedTextColor.YELLOW)
            .lore("Click to modify group settings")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupSettingsGUI(plugin, groupName).open(player)
            }
            .build()
        )

        // === PRIORITY SECTION ===
        setItem(25, GUIItemBuilder()
            .material(Material.ANVIL)
            .name("Priority: ${group.priority}", NamedTextColor.LIGHT_PURPLE)
            .lore("Higher priority wins conflicts")
            .lore("")
            .lore("Left-click: +1")
            .lore("Right-click: -1")
            .lore("Shift+click: +/-10")
            .onClick { event ->
                val player = event.whoClicked as Player
                val delta = when {
                    event.isShiftClick && event.isLeftClick -> 10
                    event.isShiftClick && event.isRightClick -> -10
                    event.isLeftClick -> 1
                    event.isRightClick -> -1
                    else -> 0
                }
                if (delta != 0) {
                    groupService.modifyGroup(groupName) {
                        setPriority(group.priority + delta)
                    }
                    player.sendMessage(Component.text("Priority updated to ${group.priority + delta}", NamedTextColor.AQUA))
                    GroupEditorGUI(plugin, groupName).open(player)
                }
            }
            .build()
        )

        // === DELETE BUTTON ===
        if (!group.isDefault) {
            setItem(40, GUIItemBuilder()
                .material(Material.TNT)
                .name("Delete Group", NamedTextColor.RED)
                .lore("WARNING: This cannot be undone!")
                .lore("")
                .lore("Shift+click to delete")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    if (event.isShiftClick) {
                        val result = groupService.deleteGroup(groupName)
                        if (result.isSuccess) {
                            player.sendMessage(Component.text("Group '$groupName' deleted!", NamedTextColor.GREEN))
                            GroupListGUI(plugin).open(player)
                        } else {
                            player.sendMessage(Component.text("Failed to delete group: ${result.exceptionOrNull()?.message}", NamedTextColor.RED))
                        }
                    } else {
                        player.sendMessage(Component.text("Shift+click to confirm deletion", NamedTextColor.GRAY))
                    }
                }
                .build()
            )
        }

        // Back button
        setItem(45, createBackButton())

        // Close button
        setItem(53, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )
    }

    private fun createBackButton(): GUIItem {
        return GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to group list")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupListGUI(plugin).open(player)
            }
            .build()
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

package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * GUI for managing worlds assigned to a group.
 */
class GroupWorldsGUI(
    plugin: PluginContext,
    private val groupName: String,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Worlds: $groupName", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 36
    private val allWorlds: List<WorldEntry>

    init {
        val groupService = plugin.serviceManager.groupService
        val group = groupService.getGroup(groupName)
        val assignedWorlds = group?.worlds ?: emptySet()

        // Get all loaded worlds and mark which are assigned
        allWorlds = Bukkit.getWorlds()
            .map { world ->
                val currentGroup = groupService.getGroupForWorld(world.name)
                WorldEntry(
                    name = world.name,
                    assignedToThisGroup = assignedWorlds.contains(world.name),
                    currentGroupName = currentGroup.name,
                    isPatternMatch = currentGroup.matchesPattern(world.name) && !currentGroup.worlds.contains(world.name)
                )
            }
            .sortedWith(compareByDescending<WorldEntry> { it.assignedToThisGroup }.thenBy { it.name })

        setupItems()
    }

    private fun setupItems() {
        val groupService = plugin.serviceManager.groupService

        // Fill bottom two rows for navigation
        for (i in 36..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (allWorlds.isEmpty()) 0 else (allWorlds.size - 1) / itemsPerPage

        // Populate worlds
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, allWorlds.size)

        for (i in startIndex until endIndex) {
            val world = allWorlds[i]
            val slot = i - startIndex
            setItem(slot, createWorldItem(world))
        }

        // Info
        setItem(40, GUIItemBuilder()
            .material(Material.BOOK)
            .name("World Assignment", NamedTextColor.AQUA)
            .lore("Green = Assigned to this group")
            .lore("Yellow = Assigned to another group")
            .lore("Gray = Uses pattern/default")
            .lore("")
            .lore("Click a world to toggle assignment")
            .build()
        )

        // Navigation - Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to group editor")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupEditorGUI(plugin, groupName).open(player)
            }
            .build()
        )

        // Previous page
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.AQUA)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    GroupWorldsGUI(plugin, groupName, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.AQUA)
            .lore("${allWorlds.size} worlds")
            .build()
        )

        // Next page
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.AQUA)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    GroupWorldsGUI(plugin, groupName, page + 1).open(player)
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
    }

    private fun createWorldItem(world: WorldEntry): GUIItem {
        val material = when {
            world.assignedToThisGroup -> Material.LIME_CONCRETE
            world.currentGroupName != groupName && !world.isPatternMatch -> Material.YELLOW_CONCRETE
            else -> Material.GRAY_CONCRETE
        }

        val color = when {
            world.assignedToThisGroup -> NamedTextColor.GREEN
            world.currentGroupName != groupName -> NamedTextColor.YELLOW
            else -> NamedTextColor.GRAY
        }

        val statusLore = when {
            world.assignedToThisGroup -> "Assigned to this group"
            world.isPatternMatch -> "Matched by pattern (${world.currentGroupName})"
            world.currentGroupName == groupName -> "Using default group"
            else -> "Assigned to: ${world.currentGroupName}"
        }

        return GUIItemBuilder()
            .material(material)
            .name(world.name, color)
            .lore(statusLore)
            .lore("")
            .lore(if (world.assignedToThisGroup) "Click to remove from group" else "Click to add to group")
            .onClick { event ->
                val player = event.whoClicked as Player
                val groupService = plugin.serviceManager.groupService

                if (world.assignedToThisGroup) {
                    // Remove from this group
                    groupService.modifyGroup(groupName) {
                        removeWorld(world.name)
                    }
                    player.sendMessage(Component.text("Removed '${world.name}' from group '$groupName'", NamedTextColor.AQUA))
                } else {
                    // Add to this group (removes from others)
                    groupService.assignWorldToGroup(world.name, groupName)
                    player.sendMessage(Component.text("Added '${world.name}' to group '$groupName'", NamedTextColor.GREEN))
                }

                // Refresh
                GroupWorldsGUI(plugin, groupName, page).open(player)
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

    private data class WorldEntry(
        val name: String,
        val assignedToThisGroup: Boolean,
        val currentGroupName: String,
        val isPatternMatch: Boolean
    )
}

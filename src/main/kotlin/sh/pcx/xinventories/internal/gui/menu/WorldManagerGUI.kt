package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
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
 * GUI for managing all world-to-group assignments.
 */
class WorldManagerGUI(
    plugin: XInventories,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("World Management", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 36
    private val allWorlds: List<WorldInfo>

    init {
        val groupService = plugin.serviceManager.groupService
        val worldAssignments = groupService.getWorldAssignments()

        allWorlds = Bukkit.getWorlds().map { world ->
            val resolvedGroup = groupService.getGroupForWorld(world.name)
            val isExplicit = worldAssignments.containsKey(world.name)
            val isPattern = resolvedGroup.matchesPattern(world.name) && !resolvedGroup.worlds.contains(world.name)

            WorldInfo(
                name = world.name,
                groupName = resolvedGroup.name,
                assignmentType = when {
                    isExplicit -> AssignmentType.EXPLICIT
                    isPattern -> AssignmentType.PATTERN
                    else -> AssignmentType.DEFAULT
                }
            )
        }.sortedBy { it.name }

        setupItems()
    }

    private fun setupItems() {
        val groupService = plugin.serviceManager.groupService
        val groups = groupService.getAllGroups()

        // Fill bottom two rows
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
            setItem(slot, createWorldItem(world, groups))
        }

        // Info
        setItem(40, GUIItemBuilder()
            .material(Material.BOOK)
            .name("World Assignments", NamedTextColor.AQUA)
            .lore("Click a world to cycle through groups")
            .lore("")
            .lore("Green = Explicitly assigned")
            .lore("Yellow = Pattern match")
            .lore("Gray = Using default group")
            .build()
        )

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

        // Previous page
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.AQUA)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    WorldManagerGUI(plugin, page - 1).open(player)
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
                    WorldManagerGUI(plugin, page + 1).open(player)
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

    private fun createWorldItem(world: WorldInfo, groups: List<Group>): GUIItem {
        val material = when (world.assignmentType) {
            AssignmentType.EXPLICIT -> Material.LIME_CONCRETE
            AssignmentType.PATTERN -> Material.YELLOW_CONCRETE
            AssignmentType.DEFAULT -> Material.GRAY_CONCRETE
        }

        val color = when (world.assignmentType) {
            AssignmentType.EXPLICIT -> NamedTextColor.GREEN
            AssignmentType.PATTERN -> NamedTextColor.YELLOW
            AssignmentType.DEFAULT -> NamedTextColor.GRAY
        }

        val assignmentLore = when (world.assignmentType) {
            AssignmentType.EXPLICIT -> "Explicitly assigned"
            AssignmentType.PATTERN -> "Matched by pattern"
            AssignmentType.DEFAULT -> "Using default group"
        }

        return GUIItemBuilder()
            .material(material)
            .name(world.name, color)
            .lore("Group: ${world.groupName}")
            .lore(assignmentLore)
            .lore("")
            .lore("Left-click: Cycle to next group")
            .lore("Right-click: Remove assignment")
            .onClick { event ->
                val player = event.whoClicked as Player
                val groupService = plugin.serviceManager.groupService

                if (event.isRightClick) {
                    // Remove explicit assignment
                    groupService.unassignWorld(world.name)
                    player.sendMessage(Component.text("Removed assignment for '${world.name}'", NamedTextColor.AQUA))
                } else {
                    // Cycle to next group
                    val sortedGroups = groups.sortedBy { it.name }
                    val currentIndex = sortedGroups.indexOfFirst { it.name == world.groupName }
                    val nextIndex = (currentIndex + 1) % sortedGroups.size
                    val nextGroup = sortedGroups[nextIndex]

                    groupService.assignWorldToGroup(world.name, nextGroup.name)
                    player.sendMessage(Component.text("Assigned '${world.name}' to group '${nextGroup.name}'", NamedTextColor.GREEN))
                }

                // Refresh
                WorldManagerGUI(plugin, page).open(player)
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

    private data class WorldInfo(
        val name: String,
        val groupName: String,
        val assignmentType: AssignmentType
    )

    private enum class AssignmentType {
        EXPLICIT,
        PATTERN,
        DEFAULT
    }
}

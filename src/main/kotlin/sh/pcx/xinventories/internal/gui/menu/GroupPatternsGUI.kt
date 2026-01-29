package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
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
 * GUI for managing patterns in a group.
 */
class GroupPatternsGUI(
    plugin: XInventories,
    private val groupName: String,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Patterns: $groupName", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 36
    private val patterns: List<String>

    init {
        val groupService = plugin.serviceManager.groupService
        val group = groupService.getGroup(groupName)
        patterns = group?.patternStrings ?: emptyList()
        setupItems()
    }

    private fun setupItems() {
        val groupService = plugin.serviceManager.groupService

        // Fill bottom two rows for navigation
        for (i in 36..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (patterns.isEmpty()) 0 else (patterns.size - 1) / itemsPerPage

        // Populate patterns
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, patterns.size)

        for (i in startIndex until endIndex) {
            val pattern = patterns[i]
            val slot = i - startIndex
            setItem(slot, createPatternItem(pattern))
        }

        // Add pattern info/button
        setItem(38, GUIItemBuilder()
            .material(Material.EMERALD)
            .name("Add Pattern", NamedTextColor.GREEN)
            .lore("Use command to add patterns:")
            .lore("/xinv pattern add $groupName <regex>")
            .lore("")
            .lore("Example patterns:")
            .lore("  ^survival_.*  (starts with survival_)")
            .lore("  .*_nether$    (ends with _nether)")
            .lore("  ^(hub|lobby)$ (exactly hub or lobby)")
            .build()
        )

        // Info
        setItem(40, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Pattern Info", NamedTextColor.AQUA)
            .lore("Patterns are regex expressions")
            .lore("used to auto-assign worlds.")
            .lore("")
            .lore("Worlds matching a pattern will")
            .lore("use this group's inventory.")
            .lore("")
            .lore("Click a pattern to remove it.")
            .build()
        )

        // Test pattern button
        setItem(42, GUIItemBuilder()
            .material(Material.SPYGLASS)
            .name("Test Patterns", NamedTextColor.YELLOW)
            .lore("See which worlds match patterns")
            .onClick { event ->
                val player = event.whoClicked as Player
                showPatternMatches(player)
            }
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
                    GroupPatternsGUI(plugin, groupName, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.AQUA)
            .lore("${patterns.size} patterns")
            .build()
        )

        // Next page
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.AQUA)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    GroupPatternsGUI(plugin, groupName, page + 1).open(player)
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

        // Show message if no patterns
        if (patterns.isEmpty()) {
            setItem(13, GUIItemBuilder()
                .material(Material.STRUCTURE_VOID)
                .name("No Patterns", NamedTextColor.GRAY)
                .lore("This group has no patterns defined.")
                .lore("")
                .lore("Use /xinv pattern add $groupName <regex>")
                .lore("to add a pattern.")
                .build()
            )
        }
    }

    private fun createPatternItem(pattern: String): GUIItem {
        // Count how many loaded worlds match this pattern
        val matchingWorlds = Bukkit.getWorlds().filter { world ->
            try {
                Regex(pattern).matches(world.name)
            } catch (e: Exception) {
                false
            }
        }

        return GUIItemBuilder()
            .material(Material.PAPER)
            .name(pattern, NamedTextColor.AQUA)
            .lore("Matching worlds: ${matchingWorlds.size}")
            .apply {
                if (matchingWorlds.isNotEmpty()) {
                    matchingWorlds.take(5).forEach { lore("  - ${it.name}") }
                    if (matchingWorlds.size > 5) {
                        lore("  ... and ${matchingWorlds.size - 5} more")
                    }
                }
            }
            .lore("")
            .lore("Click to remove this pattern")
            .onClick { event ->
                val player = event.whoClicked as Player
                val groupService = plugin.serviceManager.groupService
                val result = groupService.removePattern(groupName, pattern)

                if (result.isSuccess) {
                    player.sendMessage(Component.text("Removed pattern '$pattern' from group '$groupName'", NamedTextColor.AQUA))
                } else {
                    player.sendMessage(Component.text("Failed to remove pattern: ${result.exceptionOrNull()?.message}", NamedTextColor.RED))
                }

                // Refresh
                GroupPatternsGUI(plugin, groupName, page).open(player)
            }
            .build()
    }

    private fun showPatternMatches(player: Player) {
        val groupService = plugin.serviceManager.groupService
        val group = groupService.getGroup(groupName) ?: return

        player.sendMessage(Component.text("=== Pattern Matches for $groupName ===", NamedTextColor.AQUA))

        if (group.patterns.isEmpty()) {
            player.sendMessage(Component.text("No patterns defined.", NamedTextColor.GRAY))
            return
        }

        Bukkit.getWorlds().forEach { world ->
            if (group.matchesPattern(world.name)) {
                player.sendMessage(Component.text("  ${world.name}", NamedTextColor.GREEN))
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

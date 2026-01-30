package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.InventoryTemplate
import sh.pcx.xinventories.internal.service.BulkOperationProgress
import sh.pcx.xinventories.internal.service.BulkOperationType
import java.io.File

/**
 * GUI for performing bulk operations on player inventories.
 *
 * Features:
 * - Select target group from list
 * - Select operation type (clear, apply template, reset stats, export)
 * - For template operations: select template
 * - Confirmation dialog with affected player count
 * - Progress indicator during operation
 * - Results summary when complete
 * - Cancel button for running operations
 */
class BulkOperationsGUI(
    plugin: PluginContext
) : AbstractGUI(
    plugin,
    Component.text("Bulk Operations", NamedTextColor.DARK_AQUA),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler())

        // Clear inventories
        setItem(1, 2, GUIItemBuilder()
            .material(Material.TNT)
            .name("Clear Inventories", NamedTextColor.RED)
            .lore("Clear all inventories for")
            .lore("all players in a group")
            .lore("")
            .lore("Click to select a group")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationGroupSelectGUI(plugin, BulkOperationType.CLEAR).open(player)
            }
            .build()
        )

        // Apply template
        setItem(1, 4, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Apply Template", NamedTextColor.GREEN)
            .lore("Apply a template to all")
            .lore("players in a group")
            .lore("")
            .lore("Click to select a group")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationGroupSelectGUI(plugin, BulkOperationType.APPLY_TEMPLATE).open(player)
            }
            .build()
        )

        // Reset stats
        setItem(1, 6, GUIItemBuilder()
            .material(Material.EXPERIENCE_BOTTLE)
            .name("Reset Stats", NamedTextColor.YELLOW)
            .lore("Reset health, hunger, XP for")
            .lore("all players in a group")
            .lore("")
            .lore("Click to select a group")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationGroupSelectGUI(plugin, BulkOperationType.RESET_STATS).open(player)
            }
            .build()
        )

        // Export data
        setItem(2, 4, GUIItemBuilder()
            .material(Material.WRITABLE_BOOK)
            .name("Export Data", NamedTextColor.AQUA)
            .lore("Export all player data for")
            .lore("a group to a file")
            .lore("")
            .lore("Click to select a group")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationGroupSelectGUI(plugin, BulkOperationType.EXPORT).open(player)
            }
            .build()
        )

        // Active operations
        val activeOps = plugin.serviceManager.bulkOperationService.getActiveOperations()
        if (activeOps.isNotEmpty()) {
            setItem(0, 4, GUIItemBuilder()
                .material(Material.CLOCK)
                .name("Active Operations", NamedTextColor.LIGHT_PURPLE)
                .lore("${activeOps.size} operation(s) running")
                .lore("")
                .lore("Click to view status")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    BulkOperationStatusGUI(plugin).open(player)
                }
                .build()
            )
        }

        // Back button
        setItem(2, 0, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to main menu")
            .onClick { event ->
                val player = event.whoClicked as Player
                MainMenuGUI(plugin).open(player)
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
 * GUI for selecting a group for bulk operations.
 */
class BulkOperationGroupSelectGUI(
    plugin: PluginContext,
    private val operationType: BulkOperationType,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Select Group - ${operationType.name}", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 45
    private val groups = plugin.serviceManager.groupService.getAllGroups()
        .sortedBy { it.name.lowercase() }

    init {
        setupItems()
    }

    private fun setupItems() {
        // Fill bottom row with filler
        for (i in 45..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (groups.isEmpty()) 0 else (groups.size - 1) / itemsPerPage

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to bulk operations")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationsGUI(plugin).open(player)
            }
            .build()
        )

        // Previous page button
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    BulkOperationGroupSelectGUI(plugin, operationType, page - 1).open(player)
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
                .onClick { event ->
                    val player = event.whoClicked as Player
                    BulkOperationGroupSelectGUI(plugin, operationType, page + 1).open(player)
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

            val playerCount = countPlayersInGroup(group.name)
            val material = if (group.isDefault) Material.ENDER_CHEST else Material.CHEST
            val color = if (group.isDefault) NamedTextColor.LIGHT_PURPLE else NamedTextColor.AQUA

            setItem(slot, GUIItemBuilder()
                .material(material)
                .name(group.name, color)
                .lore("Players: $playerCount")
                .lore("Worlds: ${group.worlds.size}")
                .lore("")
                .lore("Click to select")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    when (operationType) {
                        BulkOperationType.APPLY_TEMPLATE -> {
                            BulkTemplateSelectGUI(plugin, group).open(player)
                        }
                        else -> {
                            BulkOperationConfirmGUI(plugin, group, operationType).open(player)
                        }
                    }
                }
                .build()
            )
        }
    }

    private fun countPlayersInGroup(groupName: String): Int {
        return runBlocking {
            val storageService = plugin.serviceManager.storageService
            val allUuids = storageService.getAllPlayerUUIDs()
            var count = 0
            for (uuid in allUuids) {
                val playerGroups = storageService.getPlayerGroups(uuid)
                if (groupName in playerGroups) {
                    count++
                }
            }
            count
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
 * GUI for selecting a template for bulk apply operations.
 */
class BulkTemplateSelectGUI(
    plugin: PluginContext,
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
            .lore("Return to group selection")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationGroupSelectGUI(plugin, BulkOperationType.APPLY_TEMPLATE).open(player)
            }
            .build()
        )

        // Info if no templates
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
                    BulkTemplateSelectGUI(plugin, group, page - 1).open(player)
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
                    BulkTemplateSelectGUI(plugin, group, page + 1).open(player)
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
                .lore("Click to select")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    BulkOperationConfirmGUI(plugin, group, BulkOperationType.APPLY_TEMPLATE, template).open(player)
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
 * GUI for confirming a bulk operation.
 */
class BulkOperationConfirmGUI(
    plugin: PluginContext,
    private val group: Group,
    private val operationType: BulkOperationType,
    private val template: InventoryTemplate? = null
) : AbstractGUI(
    plugin,
    Component.text("Confirm ${operationType.name}", NamedTextColor.DARK_AQUA),
    27
) {

    private val playerCount: Int

    init {
        playerCount = countPlayersInGroup(group.name)
        setupItems()
    }

    private fun countPlayersInGroup(groupName: String): Int {
        return runBlocking {
            val storageService = plugin.serviceManager.storageService
            val allUuids = storageService.getAllPlayerUUIDs()
            var count = 0
            for (uuid in allUuids) {
                val playerGroups = storageService.getPlayerGroups(uuid)
                if (groupName in playerGroups) {
                    count++
                }
            }
            count
        }
    }

    private fun setupItems() {
        val warningColor = when (operationType) {
            BulkOperationType.CLEAR -> Material.RED_STAINED_GLASS_PANE
            else -> Material.YELLOW_STAINED_GLASS_PANE
        }

        fillBorder(GUIComponents.filler(warningColor))

        // Operation info
        val infoMaterial = when (operationType) {
            BulkOperationType.CLEAR -> Material.TNT
            BulkOperationType.APPLY_TEMPLATE -> Material.CHEST
            BulkOperationType.RESET_STATS -> Material.EXPERIENCE_BOTTLE
            BulkOperationType.EXPORT -> Material.WRITABLE_BOOK
        }

        val infoColor = when (operationType) {
            BulkOperationType.CLEAR -> NamedTextColor.RED
            BulkOperationType.APPLY_TEMPLATE -> NamedTextColor.GREEN
            BulkOperationType.RESET_STATS -> NamedTextColor.YELLOW
            BulkOperationType.EXPORT -> NamedTextColor.AQUA
        }

        setItem(1, 4, GUIItemBuilder()
            .material(infoMaterial)
            .name("Confirm ${getOperationName()}", infoColor)
            .lore("Group: ${group.name}")
            .lore("Affected players: $playerCount")
            .apply {
                if (template != null) {
                    lore("Template: ${template.name}")
                }
            }
            .lore("")
            .apply {
                when (operationType) {
                    BulkOperationType.CLEAR -> {
                        lore("WARNING: This will DELETE")
                        lore("all inventory data!")
                        lore("This cannot be undone!")
                    }
                    BulkOperationType.APPLY_TEMPLATE -> {
                        lore("This will REPLACE all player")
                        lore("inventories with the template!")
                    }
                    BulkOperationType.RESET_STATS -> {
                        lore("This will reset health, hunger,")
                        lore("and experience for all players.")
                    }
                    BulkOperationType.EXPORT -> {
                        lore("This will export all player")
                        lore("data to a JSON file.")
                    }
                }
            }
            .build()
        )

        // Confirm button
        setItem(1, 2, GUIItemBuilder()
            .material(Material.LIME_WOOL)
            .name("Confirm", NamedTextColor.GREEN)
            .lore("Execute operation")
            .onClick { event ->
                val player = event.whoClicked as Player
                executeOperation(player)
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
                BulkOperationsGUI(plugin).open(player)
            }
            .build()
        )
    }

    private fun getOperationName(): String = when (operationType) {
        BulkOperationType.CLEAR -> "Clear Inventories"
        BulkOperationType.APPLY_TEMPLATE -> "Apply Template"
        BulkOperationType.RESET_STATS -> "Reset Stats"
        BulkOperationType.EXPORT -> "Export Data"
    }

    private fun executeOperation(player: Player) {
        player.closeInventory()
        player.sendMessage(Component.text("Starting ${getOperationName().lowercase()}...", NamedTextColor.YELLOW))

        runBlocking {
            val result = when (operationType) {
                BulkOperationType.CLEAR -> {
                    plugin.serviceManager.bulkOperationService.clearGroup(player, group.name)
                }
                BulkOperationType.APPLY_TEMPLATE -> {
                    plugin.serviceManager.bulkOperationService.applyTemplateToGroup(
                        player, group.name, template!!.name
                    )
                }
                BulkOperationType.RESET_STATS -> {
                    plugin.serviceManager.bulkOperationService.resetStatsForGroup(player, group.name)
                }
                BulkOperationType.EXPORT -> {
                    val exportFile = File(plugin.plugin.dataFolder, "exports/${group.name}-${System.currentTimeMillis()}.json")
                    plugin.serviceManager.bulkOperationService.exportGroup(player, group.name, exportFile)
                }
            }

            val statusColor = if (result.failCount > 0) NamedTextColor.YELLOW else NamedTextColor.GREEN
            player.sendMessage(Component.text(
                "${getOperationName()} complete: ${result.successCount}/${result.totalPlayers} successful (${result.durationMs}ms)",
                statusColor
            ))

            if (result.failCount > 0) {
                player.sendMessage(Component.text(
                    "Failed: ${result.failCount} players",
                    NamedTextColor.RED
                ))
            }

            if (result.error != null) {
                player.sendMessage(Component.text(
                    "Error: ${result.error}",
                    NamedTextColor.RED
                ))
            }
        }
    }

    override fun fillEmptySlots(inventory: Inventory) {
        val warningColor = when (operationType) {
            BulkOperationType.CLEAR -> Material.RED_STAINED_GLASS_PANE
            else -> Material.YELLOW_STAINED_GLASS_PANE
        }
        val filler = GUIComponents.filler(warningColor)
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

/**
 * GUI for viewing active bulk operation status.
 */
class BulkOperationStatusGUI(
    plugin: PluginContext
) : AbstractGUI(
    plugin,
    Component.text("Operation Status", NamedTextColor.DARK_AQUA),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler())

        val activeOps = plugin.serviceManager.bulkOperationService.getActiveOperations()

        if (activeOps.isEmpty()) {
            setItem(1, 4, GUIItemBuilder()
                .material(Material.CLOCK)
                .name("No Active Operations", NamedTextColor.GRAY)
                .lore("There are no bulk operations")
                .lore("currently running.")
                .build()
            )
        } else {
            // Show first few active operations
            var slot = 10
            for (op in activeOps.take(5)) {
                setItem(slot, createOperationItem(op))
                slot++
            }
        }

        // Back button
        setItem(2, 0, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to bulk operations")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationsGUI(plugin).open(player)
            }
            .build()
        )

        // Refresh button
        setItem(2, 4, GUIItemBuilder()
            .material(Material.SUNFLOWER)
            .name("Refresh", NamedTextColor.YELLOW)
            .lore("Update status")
            .onClick { event ->
                val player = event.whoClicked as Player
                BulkOperationStatusGUI(plugin).open(player)
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

    private fun createOperationItem(progress: BulkOperationProgress) = GUIItemBuilder()
        .material(Material.CLOCK)
        .name("${progress.operationType.name}", NamedTextColor.GOLD)
        .lore("Group: ${progress.group}")
        .lore("Progress: ${progress.percentComplete}%")
        .lore("Processed: ${progress.processed}/${progress.totalPlayers}")
        .lore("Success: ${progress.successful}")
        .lore("Failed: ${progress.failed}")
        .lore("")
        .lore("Click to cancel")
        .onClick { event ->
            val player = event.whoClicked as Player
            runBlocking {
                val cancelled = plugin.serviceManager.bulkOperationService.cancelOperation(progress.operationId)
                if (cancelled) {
                    player.sendMessage(Component.text("Operation cancelled", NamedTextColor.YELLOW))
                } else {
                    player.sendMessage(Component.text("Could not cancel operation", NamedTextColor.RED))
                }
            }
            BulkOperationStatusGUI(plugin).open(player)
        }
        .build()

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

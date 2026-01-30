package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.ComparisonOperator
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.GroupConditions
import sh.pcx.xinventories.internal.model.PlaceholderCondition
import sh.pcx.xinventories.internal.model.TimeRange
import sh.pcx.xinventories.internal.util.Logging
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * GUI for editing conditional group assignments.
 *
 * Features:
 * - Select group to configure conditions
 * - Visual condition builder:
 *   - Permission condition (anvil input for permission node)
 *   - Schedule condition (time range selector)
 *   - Placeholder condition (placeholder + operator + value)
 * - Add/remove conditions
 * - Toggle require-all vs require-any
 * - Test condition against a player
 * - Save/cancel buttons
 */
class ConditionalGroupsGUI(
    plugin: XInventories,
    private val groupName: String
) : AbstractGUI(
    plugin,
    Component.text("Conditions: $groupName", NamedTextColor.DARK_PURPLE),
    54
) {
    private val group: Group? = plugin.serviceManager.groupService.getGroup(groupName)

    // Working copy of conditions for editing
    private var workingConditions: GroupConditions = group?.conditions?.copy() ?: GroupConditions.empty()

    // Track if changes have been made
    private var hasChanges: Boolean = false

    // Current page for placeholder conditions list
    private var currentPage: Int = 0

    // Date/time formatter
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    init {
        setupItems()
    }

    private fun setupItems() {
        items.clear()

        if (group == null) {
            // Group not found
            setItem(22, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("Group Not Found", NamedTextColor.RED)
                .lore("The group '$groupName' does not exist")
                .build()
            )
            setItem(49, GUIComponents.closeButton())
            return
        }

        // Fill border
        fillBorder(GUIComponents.filler())

        // === Row 0: Mode toggle ===

        // Require all/any toggle
        setItem(4, createRequireModeToggle())

        // === Row 1: Permission condition ===

        setItem(10, GUIItemBuilder()
            .material(Material.NAME_TAG)
            .name("Permission Condition", NamedTextColor.GOLD)
            .lore(if (workingConditions.permission != null) {
                "Current: ${workingConditions.permission}"
            } else {
                "Not set"
            })
            .lore("")
            .lore("Left-click: Set permission")
            .lore("Right-click: Remove permission")
            .onClick { event ->
                when (event.click) {
                    ClickType.RIGHT, ClickType.SHIFT_RIGHT -> {
                        workingConditions = workingConditions.copy(permission = null)
                        hasChanges = true
                        setupItems()
                        refreshInventory()
                    }
                    else -> {
                        val player = event.whoClicked as Player
                        startPermissionInput(player)
                    }
                }
            }
            .build()
        )

        // Permission info
        setItem(11, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Permission Info", NamedTextColor.AQUA)
            .lore("Permission conditions check if a player")
            .lore("has a specific permission node.")
            .lore("")
            .lore("Example: xinventories.group.vip")
            .build()
        )

        // === Row 2: Schedule conditions ===

        setItem(19, GUIItemBuilder()
            .material(Material.CLOCK)
            .name("Schedule Conditions", NamedTextColor.YELLOW)
            .lore("Time ranges when this group is active")
            .lore("")
            .apply {
                val schedules = workingConditions.schedule
                if (schedules.isNullOrEmpty()) {
                    lore("No schedules defined")
                } else {
                    schedules.take(5).forEach { range ->
                        val start = dateTimeFormatter.format(range.getStartZoned())
                        val end = dateTimeFormatter.format(range.getEndZoned())
                        lore("- $start to $end")
                    }
                    if (schedules.size > 5) {
                        lore("... and ${schedules.size - 5} more")
                    }
                }
            }
            .lore("")
            .lore("Left-click: Add schedule")
            .lore("Right-click: Clear all schedules")
            .onClick { event ->
                when (event.click) {
                    ClickType.RIGHT, ClickType.SHIFT_RIGHT -> {
                        workingConditions = workingConditions.copy(schedule = null)
                        hasChanges = true
                        setupItems()
                        refreshInventory()
                    }
                    else -> {
                        val player = event.whoClicked as Player
                        startScheduleInput(player)
                    }
                }
            }
            .build()
        )

        // Schedule info
        setItem(20, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Schedule Info", NamedTextColor.AQUA)
            .lore("Schedule conditions limit when the")
            .lore("group is active to specific time ranges.")
            .lore("")
            .lore("Format: yyyy-MM-dd'T'HH:mm:ss")
            .lore("Example: 2026-01-01T00:00:00")
            .build()
        )

        // === Row 2 continued: Cron condition ===

        setItem(23, GUIItemBuilder()
            .material(Material.REPEATER)
            .name("Cron Condition", NamedTextColor.LIGHT_PURPLE)
            .lore(if (workingConditions.cron != null) {
                "Current: ${workingConditions.cron}"
            } else {
                "Not set"
            })
            .lore("")
            .lore("Left-click: Set cron expression")
            .lore("Right-click: Remove cron")
            .onClick { event ->
                when (event.click) {
                    ClickType.RIGHT, ClickType.SHIFT_RIGHT -> {
                        workingConditions = workingConditions.copy(cron = null)
                        hasChanges = true
                        setupItems()
                        refreshInventory()
                    }
                    else -> {
                        val player = event.whoClicked as Player
                        startCronInput(player)
                    }
                }
            }
            .build()
        )

        // Cron info
        setItem(24, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Cron Info", NamedTextColor.AQUA)
            .lore("Cron expressions define recurring")
            .lore("time-based conditions.")
            .lore("")
            .lore("Format: minute hour day month weekday")
            .lore("Example: 0 18-22 * * FRI,SAT")
            .lore("(Fridays and Saturdays 6-10 PM)")
            .build()
        )

        // === Row 3: Placeholder conditions ===

        setItem(28, GUIItemBuilder()
            .material(Material.OAK_SIGN)
            .name("Placeholder Conditions", NamedTextColor.GREEN)
            .lore("PlaceholderAPI-based conditions")
            .lore("")
            .apply {
                val placeholders = workingConditions.getAllPlaceholderConditions()
                if (placeholders.isEmpty()) {
                    lore("No placeholders defined")
                } else {
                    placeholders.take(5).forEach { cond ->
                        lore("- ${cond.toDisplayString()}")
                    }
                    if (placeholders.size > 5) {
                        lore("... and ${placeholders.size - 5} more")
                    }
                }
            }
            .lore("")
            .lore("Left-click: Add placeholder condition")
            .lore("Right-click: Clear all placeholders")
            .onClick { event ->
                when (event.click) {
                    ClickType.RIGHT, ClickType.SHIFT_RIGHT -> {
                        workingConditions = workingConditions.copy(
                            placeholder = null,
                            placeholders = null
                        )
                        hasChanges = true
                        setupItems()
                        refreshInventory()
                    }
                    else -> {
                        val player = event.whoClicked as Player
                        startPlaceholderInput(player)
                    }
                }
            }
            .build()
        )

        // Placeholder info
        setItem(29, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Placeholder Info", NamedTextColor.AQUA)
            .lore("Placeholder conditions evaluate")
            .lore("PlaceholderAPI placeholders.")
            .lore("")
            .lore("Format: %placeholder% operator value")
            .lore("Example: %player_level% >= 50")
            .lore("")
            .lore("Operators: =, !=, >, <, >=, <=, contains")
            .build()
        )

        // === Row 3 continued: Placeholder list (paginated) ===
        setupPlaceholderList()

        // === Row 4: Test condition ===

        setItem(40, GUIItemBuilder()
            .material(Material.ENDER_EYE)
            .name("Test Conditions", NamedTextColor.DARK_AQUA)
            .lore("Click to test conditions against")
            .lore("yourself or another player")
            .onClick { event ->
                val player = event.whoClicked as Player
                testConditionsAgainstPlayer(player, player)
            }
            .build()
        )

        // === Row 5: Navigation and actions ===

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.RED)
            .lore("Return to group list")
            .lore("")
            .lore(if (hasChanges) "Warning: Unsaved changes!" else "")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupListGUI(plugin).open(player)
            }
            .build()
        )

        // Cancel button
        setItem(47, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Cancel", NamedTextColor.RED)
            .lore("Discard all changes")
            .onClick { event ->
                hasChanges = false
                workingConditions = group.conditions?.copy() ?: GroupConditions.empty()
                setupItems()
                refreshInventory()
                val player = event.whoClicked as Player
                player.sendMessage(Component.text("Changes discarded.", NamedTextColor.YELLOW))
            }
            .build()
        )

        // Save button
        setItem(51, GUIItemBuilder()
            .material(if (hasChanges) Material.LIME_WOOL else Material.GRAY_WOOL)
            .name(if (hasChanges) "Save Changes" else "No Changes",
                  if (hasChanges) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore(if (hasChanges) "Click to save conditions" else "Make changes to enable saving")
            .onClick { event ->
                if (hasChanges) {
                    saveChanges()
                    val player = event.whoClicked as Player
                    player.sendMessage(Component.text("Conditions saved!", NamedTextColor.GREEN))
                    setupItems()
                    refreshInventory()
                }
            }
            .build()
        )

        // Close button
        setItem(53, GUIComponents.closeButton())
    }

    private fun createRequireModeToggle(): GUIItem {
        val requireAll = workingConditions.requireAll
        return GUIItemBuilder()
            .material(if (requireAll) Material.IRON_BARS else Material.STRING)
            .name(if (requireAll) "Mode: Require ALL" else "Mode: Require ANY",
                  if (requireAll) NamedTextColor.RED else NamedTextColor.GREEN)
            .lore(if (requireAll) {
                "ALL conditions must be met (AND logic)"
            } else {
                "ANY condition can be met (OR logic)"
            })
            .lore("")
            .lore("Click to toggle")
            .onClick { event ->
                workingConditions = workingConditions.copy(requireAll = !workingConditions.requireAll)
                hasChanges = true
                setupItems()
                refreshInventory()
            }
            .build()
    }

    private fun setupPlaceholderList() {
        val placeholders = workingConditions.getAllPlaceholderConditions()
        val placeholderSlots = listOf(32, 33, 34)  // Small display area for individual placeholders

        placeholders.take(3).forEachIndexed { index, condition ->
            if (index < placeholderSlots.size) {
                val slot = placeholderSlots[index]
                setItem(slot, GUIItemBuilder()
                    .material(Material.COMPARATOR)
                    .name(condition.placeholder, NamedTextColor.YELLOW)
                    .lore("Operator: ${condition.operator.name}")
                    .lore("Value: ${condition.value}")
                    .lore("")
                    .lore("Click to remove")
                    .onClick { event ->
                        removePlaceholderCondition(condition)
                        hasChanges = true
                        setupItems()
                        refreshInventory()
                    }
                    .build()
                )
            }
        }

        // Fill remaining slots
        for (i in placeholders.size until placeholderSlots.size) {
            val slot = placeholderSlots[i]
            setItem(slot, GUIItemBuilder()
                .material(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name("Empty Slot", NamedTextColor.GRAY)
                .build()
            )
        }
    }

    private fun removePlaceholderCondition(condition: PlaceholderCondition) {
        val current = workingConditions.getAllPlaceholderConditions().toMutableList()
        current.remove(condition)

        workingConditions = if (current.isEmpty()) {
            workingConditions.copy(placeholder = null, placeholders = null)
        } else if (current.size == 1) {
            workingConditions.copy(placeholder = current[0], placeholders = null)
        } else {
            workingConditions.copy(placeholder = null, placeholders = current)
        }
    }

    private fun startPermissionInput(player: Player) {
        player.closeInventory()
        player.sendMessage(Component.text("Enter the permission node (e.g., xinventories.group.vip):", NamedTextColor.YELLOW))

        plugin.inputManager.awaitChatInput(player) { input ->
            val permission = input.trim()
            if (permission.isNotEmpty()) {
                workingConditions = workingConditions.copy(permission = permission)
                hasChanges = true
                player.sendMessage(Component.text("Permission set: $permission", NamedTextColor.GREEN))
            }
            reopenGUI(player)
        }
    }

    private fun startScheduleInput(player: Player) {
        player.closeInventory()
        player.sendMessage(Component.text("Enter the schedule start time (yyyy-MM-dd'T'HH:mm:ss or YYYY-MM-DD HH:mm):", NamedTextColor.YELLOW))

        plugin.inputManager.awaitChatInput(player) { startInput ->
            player.sendMessage(Component.text("Enter the schedule end time:", NamedTextColor.YELLOW))

            plugin.inputManager.awaitChatInput(player) { endInput ->
                val range = TimeRange.fromStrings(
                    startInput.trim().replace(" ", "T") + if (!startInput.contains(":")) "T00:00:00" else ":00",
                    endInput.trim().replace(" ", "T") + if (!endInput.contains(":")) "T23:59:59" else ":00"
                )

                if (range != null) {
                    val currentSchedules = workingConditions.schedule?.toMutableList() ?: mutableListOf()
                    currentSchedules.add(range)
                    workingConditions = workingConditions.copy(schedule = currentSchedules)
                    hasChanges = true
                    player.sendMessage(Component.text("Schedule added!", NamedTextColor.GREEN))
                } else {
                    player.sendMessage(Component.text("Invalid date format. Use yyyy-MM-dd'T'HH:mm:ss", NamedTextColor.RED))
                }

                reopenGUI(player)
            }
        }
    }

    private fun startCronInput(player: Player) {
        player.closeInventory()
        player.sendMessage(Component.text("Enter the cron expression (minute hour day month weekday):", NamedTextColor.YELLOW))
        player.sendMessage(Component.text("Example: 0 18-22 * * FRI,SAT", NamedTextColor.GRAY))

        plugin.inputManager.awaitChatInput(player) { input ->
            val cron = input.trim()
            if (cron.isNotEmpty()) {
                workingConditions = workingConditions.copy(cron = cron)
                hasChanges = true
                player.sendMessage(Component.text("Cron expression set: $cron", NamedTextColor.GREEN))
            }
            reopenGUI(player)
        }
    }

    private fun startPlaceholderInput(player: Player) {
        player.closeInventory()
        player.sendMessage(Component.text("Enter the placeholder (e.g., %player_level%):", NamedTextColor.YELLOW))

        plugin.inputManager.awaitChatInput(player) { placeholderInput ->
            val placeholder = placeholderInput.trim()

            player.sendMessage(Component.text("Enter the operator (=, !=, >, <, >=, <=, contains):", NamedTextColor.YELLOW))

            plugin.inputManager.awaitChatInput(player) { operatorInput ->
                val operator = ComparisonOperator.fromString(operatorInput.trim())

                if (operator == null) {
                    player.sendMessage(Component.text("Invalid operator. Use: =, !=, >, <, >=, <=, contains", NamedTextColor.RED))
                    reopenGUI(player)
                    return@awaitChatInput
                }

                player.sendMessage(Component.text("Enter the comparison value:", NamedTextColor.YELLOW))

                plugin.inputManager.awaitChatInput(player) { valueInput ->
                    val value = valueInput.trim()

                    val condition = PlaceholderCondition(placeholder, operator, value)
                    val currentConditions = workingConditions.getAllPlaceholderConditions().toMutableList()
                    currentConditions.add(condition)

                    workingConditions = if (currentConditions.size == 1) {
                        workingConditions.copy(placeholder = currentConditions[0], placeholders = null)
                    } else {
                        workingConditions.copy(placeholder = null, placeholders = currentConditions)
                    }

                    hasChanges = true
                    player.sendMessage(Component.text("Placeholder condition added: ${condition.toDisplayString()}", NamedTextColor.GREEN))

                    reopenGUI(player)
                }
            }
        }
    }

    private fun testConditionsAgainstPlayer(viewer: Player, target: Player) {
        if (!workingConditions.hasConditions()) {
            viewer.sendMessage(Component.text("No conditions defined for this group.", NamedTextColor.YELLOW))
            return
        }

        // Create a temporary group with working conditions for testing
        val testGroup = Group(
            name = groupName,
            conditions = workingConditions
        )

        val result = plugin.serviceManager.conditionEvaluator.evaluateConditions(target, testGroup, useCache = false)

        viewer.sendMessage(Component.text("=== Condition Test Results ===", NamedTextColor.GOLD))
        viewer.sendMessage(Component.text("Target: ${target.name}", NamedTextColor.GRAY))
        viewer.sendMessage(Component.text("Group: $groupName", NamedTextColor.GRAY))
        viewer.sendMessage(Component.text(
            "Result: ${if (result.matches) "MATCH" else "NO MATCH"}",
            if (result.matches) NamedTextColor.GREEN else NamedTextColor.RED
        ))

        if (result.matchedConditions.isNotEmpty()) {
            viewer.sendMessage(Component.text("Matched: ${result.matchedConditions.joinToString(", ")}", NamedTextColor.GREEN))
        }
        if (result.failedConditions.isNotEmpty()) {
            viewer.sendMessage(Component.text("Failed: ${result.failedConditions.joinToString(", ")}", NamedTextColor.RED))
        }

        viewer.sendMessage(Component.text("Mode: ${if (workingConditions.requireAll) "ALL required" else "ANY sufficient"}", NamedTextColor.GRAY))
    }

    private fun reopenGUI(player: Player) {
        val newGUI = ConditionalGroupsGUI(plugin, groupName).apply {
            this.workingConditions = this@ConditionalGroupsGUI.workingConditions
            this.hasChanges = this@ConditionalGroupsGUI.hasChanges
        }
        newGUI.open(player)
    }

    private fun saveChanges() {
        if (group == null) return

        // Update the group's conditions
        group.conditions = if (workingConditions.hasConditions()) workingConditions else null

        // Save to config
        plugin.serviceManager.groupService.saveToConfig()

        // Clear the condition evaluator cache
        plugin.serviceManager.conditionEvaluator.clearCache()

        hasChanges = false
        Logging.info("Saved conditions for group '$groupName'")
    }

    private fun refreshInventory() {
        val inv = inventory ?: return
        for (i in 0 until size) {
            val guiItem = items[i]
            inv.setItem(i, guiItem?.itemStack)
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

    override fun onClose(event: InventoryCloseEvent) {
        if (hasChanges) {
            val player = event.player as? Player ?: return
            player.sendMessage(
                Component.text("Warning: You have unsaved changes to conditions!", NamedTextColor.YELLOW)
            )
        }
    }
}

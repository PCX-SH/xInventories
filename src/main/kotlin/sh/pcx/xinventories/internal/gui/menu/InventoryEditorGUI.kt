package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.AuditAction
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import java.util.*

/**
 * GUI for editing a player's stored inventory.
 * Allows drag-and-drop item placement, adding/removing items, and modifying amounts.
 */
class InventoryEditorGUI(
    plugin: PluginContext,
    private val targetUUID: UUID,
    private val targetName: String,
    private val groupName: String,
    private val gameMode: GameMode = GameMode.SURVIVAL
) : AbstractGUI(
    plugin,
    Component.text("Editing: $targetName", NamedTextColor.GOLD),
    54
) {

    // Working copy of the inventory that can be modified
    private val workingInventory = mutableMapOf<Int, ItemStack?>()
    private val workingArmor = mutableMapOf<Int, ItemStack?>()
    private var workingOffhand: ItemStack? = null

    // Original data for comparison
    private var originalData: PlayerData? = null

    // Track if changes have been made
    private var hasUnsavedChanges = false

    // Editable slots (main inventory: 0-35)
    private val editableSlots = (0..35).toSet()

    init {
        loadData()
        setupItems()
    }

    private fun loadData() {
        originalData = runBlocking {
            plugin.serviceManager.storageService.load(targetUUID, groupName, gameMode)
        }

        // Copy to working inventory
        originalData?.let { data ->
            data.mainInventory.forEach { (slot, item) ->
                workingInventory[slot] = item.clone()
            }
            data.armorInventory.forEach { (slot, item) ->
                workingArmor[slot] = item.clone()
            }
            workingOffhand = data.offhand?.clone()
        }
    }

    private fun setupItems() {
        val data = originalData

        if (data == null) {
            // No data - show error
            setItem(22, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("No Data", NamedTextColor.RED)
                .lore("No inventory data found for this group")
                .lore("Cannot edit non-existent data")
                .build()
            )

            setItem(49, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Back", NamedTextColor.GRAY)
                .lore("Return to player details")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    PlayerDetailGUI(plugin, targetUUID, targetName).open(player)
                }
                .build()
            )
            return
        }

        // Display main inventory contents (editable)
        for (i in 0..35) {
            val item = workingInventory[i]
            if (item != null && item.type != Material.AIR) {
                // Create editable item wrapper
                setItem(i, createEditableItem(i, item))
            } else {
                // Empty slot - clickable to add items
                setItem(i, createEmptySlot(i))
            }
        }

        // Armor slots (display only in this view - slots 36-39)
        val armorSlots = listOf(39, 38, 37, 36) // Helmet, chest, legs, boots
        for ((index, armorSlot) in armorSlots.withIndex()) {
            val item = workingArmor[3 - index]
            if (item != null && item.type != Material.AIR) {
                setItem(armorSlot, GUIItem(item.clone()))
            } else {
                val placeholder = when (index) {
                    0 -> createArmorPlaceholder("Helmet", Material.LEATHER_HELMET)
                    1 -> createArmorPlaceholder("Chestplate", Material.LEATHER_CHESTPLATE)
                    2 -> createArmorPlaceholder("Leggings", Material.LEATHER_LEGGINGS)
                    3 -> createArmorPlaceholder("Boots", Material.LEATHER_BOOTS)
                    else -> GUIComponents.filler()
                }
                setItem(armorSlot, placeholder)
            }
        }

        // Offhand (slot 40)
        val offhandItem = workingOffhand
        if (offhandItem != null && offhandItem.type != Material.AIR) {
            setItem(40, GUIItem(offhandItem.clone()))
        } else {
            setItem(40, createArmorPlaceholder("Offhand", Material.SHIELD))
        }

        // Control panel (bottom row)
        setupControlPanel()
    }

    private fun setupControlPanel() {
        // Fill bottom row with different color to indicate controls
        for (i in 45..53) {
            setItem(i, GUIComponents.filler(Material.BLACK_STAINED_GLASS_PANE))
        }

        // Save button
        setItem(45, GUIItemBuilder()
            .material(Material.LIME_WOOL)
            .name("Save Changes", NamedTextColor.GREEN)
            .lore("Click to save all changes")
            .lore("")
            .lore(if (hasUnsavedChanges) "You have unsaved changes!" else "No changes to save")
            .onClick { event ->
                val player = event.whoClicked as Player
                saveChanges(player)
            }
            .build()
        )

        // Cancel button
        setItem(46, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Cancel", NamedTextColor.RED)
            .lore("Discard changes and go back")
            .lore("")
            .lore(if (hasUnsavedChanges) "Warning: Unsaved changes will be lost!" else "")
            .onClick { event ->
                val player = event.whoClicked as Player
                if (hasUnsavedChanges) {
                    // Show confirmation
                    ConfirmationGUI(
                        plugin,
                        Component.text("Discard Changes?", NamedTextColor.RED),
                        "You have unsaved changes.",
                        "Click Confirm to discard them.",
                        onConfirm = {
                            PlayerDetailGUI(plugin, targetUUID, targetName).open(player)
                        },
                        onCancel = {
                            InventoryEditorGUI(plugin, targetUUID, targetName, groupName, gameMode).open(player)
                        }
                    ).open(player)
                } else {
                    PlayerDetailGUI(plugin, targetUUID, targetName).open(player)
                }
            }
            .build()
        )

        // Clear inventory button
        setItem(47, GUIItemBuilder()
            .material(Material.TNT)
            .name("Clear Inventory", NamedTextColor.DARK_RED)
            .lore("Remove all items from inventory")
            .lore("")
            .lore("Shift-click to confirm")
            .onClick { event ->
                if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                    clearWorkingInventory()
                    hasUnsavedChanges = true
                    refreshDisplay(event.whoClicked as Player)
                    (event.whoClicked as Player).sendMessage(
                        Component.text("Inventory cleared. Remember to save!", NamedTextColor.YELLOW)
                    )
                } else {
                    (event.whoClicked as Player).sendMessage(
                        Component.text("Shift-click to confirm clearing inventory.", NamedTextColor.RED)
                    )
                }
            }
            .build()
        )

        // Unsaved changes indicator
        val indicatorMaterial = if (hasUnsavedChanges) Material.YELLOW_WOOL else Material.GREEN_WOOL
        val indicatorName = if (hasUnsavedChanges) "Unsaved Changes" else "No Changes"
        val indicatorColor = if (hasUnsavedChanges) NamedTextColor.YELLOW else NamedTextColor.GREEN

        setItem(49, GUIItemBuilder()
            .material(indicatorMaterial)
            .name(indicatorName, indicatorColor)
            .lore(if (hasUnsavedChanges) "Click Save to keep changes" else "All changes saved")
            .build()
        )

        // Instructions
        setItem(51, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Instructions", NamedTextColor.AQUA)
            .lore("Left-click: Pick up/place item")
            .lore("Right-click: Decrease amount")
            .lore("Shift-click: Remove item")
            .lore("Middle-click: Clone item")
            .build()
        )

        // Close button
        setItem(53, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .lore(if (hasUnsavedChanges) "Warning: Unsaved changes!" else "")
            .onClick { event ->
                val player = event.whoClicked as Player
                if (hasUnsavedChanges) {
                    player.sendMessage(
                        Component.text("You have unsaved changes! Save or Cancel first.", NamedTextColor.RED)
                    )
                } else {
                    player.closeInventory()
                }
            }
            .build()
        )
    }

    private fun createEditableItem(slot: Int, item: ItemStack): GUIItem {
        val displayItem = item.clone()
        val meta = displayItem.itemMeta

        // Add edit indicator to lore
        val existingLore = meta.lore()?.toMutableList() ?: mutableListOf()
        existingLore.add(Component.empty())
        existingLore.add(
            Component.text("Click to edit", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        )
        meta.lore(existingLore)
        displayItem.itemMeta = meta

        return GUIItem(displayItem) { event ->
            handleItemClick(event, slot)
        }
    }

    private fun createEmptySlot(slot: Int): GUIItem {
        val item = ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Empty Slot")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )
        meta.lore(listOf(
            Component.text("Slot #$slot")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Click with item to place")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta

        return GUIItem(item) { event ->
            handleItemClick(event, slot)
        }
    }

    private fun createArmorPlaceholder(name: String, material: Material): GUIItem {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("$name (View Only)")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )
        meta.lore(listOf(
            Component.text("Armor editing not supported")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta
        return GUIItem(item)
    }

    private fun handleItemClick(event: InventoryClickEvent, slot: Int) {
        val player = event.whoClicked as Player

        // Only allow editing main inventory slots
        if (slot !in editableSlots) {
            player.sendMessage(Component.text("This slot cannot be edited.", NamedTextColor.RED))
            return
        }

        val currentItem = workingInventory[slot]
        val cursorItem = event.cursor

        when (event.click) {
            ClickType.LEFT -> {
                // Swap cursor with slot
                if (cursorItem != null && cursorItem.type != Material.AIR) {
                    // Place cursor item
                    workingInventory[slot] = cursorItem.clone()
                    hasUnsavedChanges = true
                    refreshDisplay(player)
                } else if (currentItem != null && currentItem.type != Material.AIR) {
                    // Pick up item (just show info for now since cursor manipulation is complex in GUIs)
                    player.sendMessage(
                        Component.text("Item: ${currentItem.type.name} x${currentItem.amount}", NamedTextColor.AQUA)
                    )
                }
            }

            ClickType.RIGHT -> {
                // Decrease amount by 1
                if (currentItem != null && currentItem.type != Material.AIR) {
                    if (currentItem.amount > 1) {
                        currentItem.amount = currentItem.amount - 1
                        workingInventory[slot] = currentItem
                    } else {
                        workingInventory.remove(slot)
                    }
                    hasUnsavedChanges = true
                    refreshDisplay(player)
                }
            }

            ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT -> {
                // Remove item completely
                if (currentItem != null) {
                    workingInventory.remove(slot)
                    hasUnsavedChanges = true
                    refreshDisplay(player)
                    player.sendMessage(
                        Component.text("Removed item from slot $slot", NamedTextColor.YELLOW)
                    )
                }
            }

            ClickType.MIDDLE -> {
                // Show item info
                if (currentItem != null && currentItem.type != Material.AIR) {
                    player.sendMessage(Component.text("=== Item Info ===", NamedTextColor.GOLD))
                    player.sendMessage(Component.text("Type: ${currentItem.type.name}", NamedTextColor.GRAY))
                    player.sendMessage(Component.text("Amount: ${currentItem.amount}", NamedTextColor.GRAY))
                    currentItem.enchantments.forEach { (ench, level) ->
                        player.sendMessage(Component.text("Enchant: ${ench.key.key} $level", NamedTextColor.AQUA))
                    }
                }
            }

            else -> {
                // Other click types - ignore
            }
        }
    }

    private fun refreshDisplay(player: Player) {
        // Clear and re-setup items
        items.clear()
        setupItems()

        // Update the actual inventory
        inventory?.let { inv ->
            items.forEach { (slot, guiItem) ->
                if (slot in 0 until size) {
                    inv.setItem(slot, guiItem.itemStack)
                }
            }
        }
    }

    private fun clearWorkingInventory() {
        workingInventory.clear()
    }

    private fun saveChanges(player: Player) {
        if (!hasUnsavedChanges) {
            player.sendMessage(Component.text("No changes to save.", NamedTextColor.YELLOW))
            return
        }

        // Create updated PlayerData
        val original = originalData ?: return

        plugin.scope.launch(plugin.storageDispatcher) {
            try {
                // Build updated player data
                val updatedData = PlayerData(
                    uuid = targetUUID,
                    playerName = targetName,
                    group = groupName,
                    gameMode = gameMode
                )

                // Copy working inventory
                workingInventory.forEach { (slot, item) ->
                    if (item != null && item.type != Material.AIR) {
                        updatedData.mainInventory[slot] = item.clone()
                    }
                }

                // Copy armor (unchanged)
                original.armorInventory.forEach { (slot, item) ->
                    updatedData.armorInventory[slot] = item.clone()
                }

                // Copy offhand (unchanged)
                updatedData.offhand = original.offhand?.clone()

                // Copy ender chest (unchanged)
                original.enderChest.forEach { (slot, item) ->
                    updatedData.enderChest[slot] = item.clone()
                }

                // Copy other state
                updatedData.health = original.health
                updatedData.maxHealth = original.maxHealth
                updatedData.foodLevel = original.foodLevel
                updatedData.saturation = original.saturation
                updatedData.exhaustion = original.exhaustion
                updatedData.experience = original.experience
                updatedData.level = original.level
                updatedData.totalExperience = original.totalExperience
                updatedData.potionEffects.addAll(original.potionEffects)

                // Save to storage
                plugin.serviceManager.storageService.savePlayerData(updatedData)

                // Log to audit
                plugin.serviceManager.auditService?.logAdminEdit(
                    admin = player,
                    targetUuid = targetUUID,
                    targetName = targetName,
                    group = groupName,
                    details = "Edited inventory via GUI"
                )

                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    player.sendMessage(
                        Component.text("Changes saved successfully!", NamedTextColor.GREEN)
                    )
                    hasUnsavedChanges = false

                    // Update original data reference
                    originalData = updatedData

                    // Refresh display
                    refreshDisplay(player)
                }

            } catch (e: Exception) {
                Logging.error("Failed to save inventory changes for $targetName", e)
                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    player.sendMessage(
                        Component.text("Failed to save changes: ${e.message}", NamedTextColor.RED)
                    )
                }
            }
        }
    }

    override fun onClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        if (hasUnsavedChanges) {
            player.sendMessage(
                Component.text("Warning: You had unsaved changes that were discarded.", NamedTextColor.RED)
            )
        }
    }

    override fun fillEmptySlots(inventory: Inventory) {
        // Don't fill empty slots in editor - they should be clickable
        // Only fill non-inventory areas
        val fillerSlots = (41..44).toList()
        val filler = GUIComponents.filler(Material.GRAY_STAINED_GLASS_PANE)

        for (slot in fillerSlots) {
            if (!items.containsKey(slot)) {
                items[slot] = filler
                inventory.setItem(slot, filler.itemStack)
            }
        }
    }
}

/**
 * A simple confirmation dialog GUI.
 */
class ConfirmationGUI(
    plugin: PluginContext,
    title: Component,
    private val line1: String,
    private val line2: String,
    private val onConfirm: () -> Unit,
    private val onCancel: () -> Unit
) : AbstractGUI(plugin, title, 27) {

    init {
        setupItems()
    }

    private fun setupItems() {
        // Fill with gray glass
        fillBorder(GUIComponents.filler())

        // Info
        setItem(13, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Confirmation Required", NamedTextColor.YELLOW)
            .lore(line1)
            .lore(line2)
            .build()
        )

        // Confirm button
        setItem(11, GUIItemBuilder()
            .material(Material.LIME_WOOL)
            .name("Confirm", NamedTextColor.GREEN)
            .lore("Click to confirm")
            .onClick { event ->
                event.whoClicked.closeInventory()
                onConfirm()
            }
            .build()
        )

        // Cancel button
        setItem(15, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Cancel", NamedTextColor.RED)
            .lore("Click to cancel")
            .onClick { event ->
                event.whoClicked.closeInventory()
                onCancel()
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

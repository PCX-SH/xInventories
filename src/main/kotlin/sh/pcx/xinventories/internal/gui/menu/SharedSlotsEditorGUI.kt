package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.SharedSlotConfigEntry
import sh.pcx.xinventories.internal.config.SharedSlotsConfigSection
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.SharedSlotEntry
import sh.pcx.xinventories.internal.model.SharedSlotsConfig
import sh.pcx.xinventories.internal.model.SlotMode
import sh.pcx.xinventories.internal.util.Logging
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory

/**
 * GUI for editing shared inventory slots configuration.
 *
 * Features:
 * - Visual representation of inventory slots (36 slots + armor + offhand)
 * - Click slot to toggle shared status
 * - Color coding: green=shared, gray=not shared
 * - Configure sync behavior per slot (PRESERVE, CLEAR, SYNC)
 * - Preset buttons (share hotbar, share armor, share all)
 * - Save/cancel buttons
 */
class SharedSlotsEditorGUI(
    plugin: XInventories
) : AbstractGUI(
    plugin,
    Component.text("Shared Slots Editor", NamedTextColor.DARK_GREEN),
    54
) {
    // Working copy of shared slots config for editing (using internal model)
    private var workingConfig: SharedSlotsConfig = plugin.configManager.mainConfig.sharedSlots.toSharedSlotsConfig()

    // Track if changes have been made
    private var hasChanges: Boolean = false

    // Map to track slot modes for each inventory slot
    private val slotModes: MutableMap<Int, SlotMode> = mutableMapOf()

    // Slot mapping: GUI slot -> Player inventory slot
    // Layout:
    // Row 0: Controls and presets
    // Row 1-4: Main inventory (slots 9-35) displayed as 3x9 grid
    // Row 5: Hotbar (0-8) + Armor (36-39) + Offhand (40)

    // GUI slots for main inventory (9-35)
    private val mainInventorySlots = mapOf(
        // Row 1 of GUI (slots 9-17) -> Player inventory 9-17
        9 to 9, 10 to 10, 11 to 11, 12 to 12, 13 to 13, 14 to 14, 15 to 15, 16 to 16, 17 to 17,
        // Row 2 of GUI (slots 18-26) -> Player inventory 18-26
        18 to 18, 19 to 19, 20 to 20, 21 to 21, 22 to 22, 23 to 23, 24 to 24, 25 to 25, 26 to 26,
        // Row 3 of GUI (slots 27-35) -> Player inventory 27-35
        27 to 27, 28 to 28, 29 to 29, 30 to 30, 31 to 31, 32 to 32, 33 to 33, 34 to 34, 35 to 35
    )

    // GUI slots for hotbar (0-8)
    private val hotbarSlots = mapOf(
        36 to 0, 37 to 1, 38 to 2, 39 to 3, 40 to 4, 41 to 5, 42 to 6, 43 to 7, 44 to 8
    )

    // GUI slots for armor and offhand
    private val armorSlot = 45 // Helmet (39)
    private val chestplateSlot = 46 // Chestplate (38)
    private val leggingsSlot = 47 // Leggings (37)
    private val bootsSlot = 48 // Boots (36)
    private val offhandSlot = 49 // Offhand (40)

    init {
        loadCurrentConfig()
        setupItems()
    }

    private fun loadCurrentConfig() {
        // Initialize slot modes from current config
        slotModes.clear()
        for (slot in 0..40) {
            val entry = workingConfig.getEntryForSlot(slot)
            slotModes[slot] = entry?.mode ?: SlotMode.PRESERVE
        }
    }

    private fun setupItems() {
        items.clear()

        // === Row 0: Controls ===

        // Enable/Disable toggle
        setItem(0, createEnabledToggle())

        // Preset: Share Hotbar
        setItem(2, GUIItemBuilder()
            .material(Material.GOLDEN_SWORD)
            .name("Preset: Share Hotbar", NamedTextColor.GOLD)
            .lore("Click to toggle all hotbar slots")
            .lore("as shared (PRESERVE mode)")
            .onClick { event ->
                togglePreset(0..8)
                hasChanges = true
                setupItems()
                refreshInventory()
            }
            .build()
        )

        // Preset: Share Armor
        setItem(4, GUIItemBuilder()
            .material(Material.DIAMOND_CHESTPLATE)
            .name("Preset: Share Armor", NamedTextColor.AQUA)
            .lore("Click to toggle all armor slots")
            .lore("as shared (PRESERVE mode)")
            .onClick { event ->
                togglePreset(36..39)
                hasChanges = true
                setupItems()
                refreshInventory()
            }
            .build()
        )

        // Preset: Share All
        setItem(6, GUIItemBuilder()
            .material(Material.BEACON)
            .name("Preset: Share All", NamedTextColor.LIGHT_PURPLE)
            .lore("Click to toggle ALL slots")
            .lore("as shared (PRESERVE mode)")
            .onClick { event ->
                togglePreset(0..40)
                hasChanges = true
                setupItems()
                refreshInventory()
            }
            .build()
        )

        // Clear All
        setItem(8, GUIItemBuilder()
            .material(Material.TNT)
            .name("Clear All Shared Slots", NamedTextColor.RED)
            .lore("Click to remove all shared slots")
            .onClick { event ->
                for (slot in 0..40) {
                    slotModes.remove(slot)
                }
                hasChanges = true
                setupItems()
                refreshInventory()
            }
            .build()
        )

        // === Rows 1-3: Main inventory display ===
        setupMainInventorySlots()

        // === Row 4: Hotbar display ===
        setupHotbarSlots()

        // === Row 5: Armor, offhand, and action buttons ===
        setupArmorAndOffhandSlots()

        // Save button
        setItem(51, GUIItemBuilder()
            .material(if (hasChanges) Material.LIME_WOOL else Material.GRAY_WOOL)
            .name(if (hasChanges) "Save Changes" else "No Changes",
                  if (hasChanges) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore(if (hasChanges) "Click to save shared slots config" else "Make changes to enable saving")
            .onClick { event ->
                if (hasChanges) {
                    saveChanges()
                    val player = event.whoClicked as Player
                    player.sendMessage(Component.text("Shared slots configuration saved!", NamedTextColor.GREEN))
                    setupItems()
                    refreshInventory()
                }
            }
            .build()
        )

        // Cancel button
        setItem(52, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Cancel", NamedTextColor.RED)
            .lore("Discard all changes")
            .onClick { event ->
                hasChanges = false
                workingConfig = plugin.configManager.mainConfig.sharedSlots.toSharedSlotsConfig()
                loadCurrentConfig()
                setupItems()
                refreshInventory()
                val player = event.whoClicked as Player
                player.sendMessage(Component.text("Changes discarded.", NamedTextColor.YELLOW))
            }
            .build()
        )

        // Close button
        setItem(53, GUIComponents.closeButton())
    }

    private fun createEnabledToggle(): GUIItem {
        return GUIItemBuilder()
            .material(if (workingConfig.enabled) Material.LIME_DYE else Material.GRAY_DYE)
            .name(if (workingConfig.enabled) "Shared Slots: ENABLED" else "Shared Slots: DISABLED",
                  if (workingConfig.enabled) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore("Click to toggle")
            .onClick { event ->
                workingConfig = workingConfig.copy(enabled = !workingConfig.enabled)
                hasChanges = true
                setupItems()
                refreshInventory()
            }
            .build()
    }

    private fun setupMainInventorySlots() {
        for ((guiSlot, invSlot) in mainInventorySlots) {
            setItem(guiSlot, createSlotButton(invSlot, "Inv Slot $invSlot"))
        }
    }

    private fun setupHotbarSlots() {
        for ((guiSlot, invSlot) in hotbarSlots) {
            setItem(guiSlot, createSlotButton(invSlot, "Hotbar ${invSlot + 1}"))
        }
    }

    private fun setupArmorAndOffhandSlots() {
        // Helmet
        setItem(armorSlot, createSlotButton(39, "Helmet", Material.IRON_HELMET))
        // Chestplate
        setItem(chestplateSlot, createSlotButton(38, "Chestplate", Material.IRON_CHESTPLATE))
        // Leggings
        setItem(leggingsSlot, createSlotButton(37, "Leggings", Material.IRON_LEGGINGS))
        // Boots
        setItem(bootsSlot, createSlotButton(36, "Boots", Material.IRON_BOOTS))
        // Offhand
        setItem(offhandSlot, createSlotButton(40, "Offhand", Material.SHIELD))

        // Info item
        setItem(50, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Slot Mode Info", NamedTextColor.AQUA)
            .lore("PRESERVE: Keep items across group switches")
            .lore("LOCK: Keep items AND prevent modification")
            .lore("SYNC: Sync items across all groups")
            .lore("")
            .lore("Left-click: Toggle shared status")
            .lore("Right-click: Cycle slot mode")
            .build()
        )
    }

    private fun createSlotButton(invSlot: Int, name: String, forceMaterial: Material? = null): GUIItem {
        val isShared = workingConfig.isSharedSlot(invSlot)
        val mode = slotModes[invSlot]

        val material = forceMaterial ?: when {
            !isShared -> Material.GRAY_STAINED_GLASS_PANE
            mode == SlotMode.LOCK -> Material.RED_STAINED_GLASS_PANE
            mode == SlotMode.SYNC -> Material.BLUE_STAINED_GLASS_PANE
            else -> Material.LIME_STAINED_GLASS_PANE
        }

        val color = when {
            !isShared -> NamedTextColor.GRAY
            mode == SlotMode.LOCK -> NamedTextColor.RED
            mode == SlotMode.SYNC -> NamedTextColor.BLUE
            else -> NamedTextColor.GREEN
        }

        val statusText = if (isShared) "SHARED ($mode)" else "Not Shared"

        return GUIItemBuilder()
            .material(material)
            .name(name, color)
            .lore("Status: $statusText")
            .lore("")
            .lore("Left-click: Toggle shared")
            .lore("Right-click: Cycle mode")
            .onClick { event ->
                when (event.click) {
                    ClickType.RIGHT -> {
                        // Cycle through modes
                        if (isShared) {
                            val modes = SlotMode.entries.toTypedArray()
                            val currentMode = slotModes[invSlot] ?: SlotMode.PRESERVE
                            val currentIndex = modes.indexOf(currentMode)
                            val nextIndex = (currentIndex + 1) % modes.size
                            slotModes[invSlot] = modes[nextIndex]
                        } else {
                            // Enable sharing first
                            slotModes[invSlot] = SlotMode.PRESERVE
                        }
                    }
                    else -> {
                        // Toggle shared status
                        if (isShared) {
                            slotModes.remove(invSlot)
                        } else {
                            slotModes[invSlot] = SlotMode.PRESERVE
                        }
                    }
                }
                rebuildWorkingConfig()
                hasChanges = true
                setupItems()
                refreshInventory()
            }
            .build()
    }

    private fun togglePreset(slotRange: IntRange) {
        // Check if all slots in range are already shared
        val allShared = slotRange.all { workingConfig.isSharedSlot(it) }

        if (allShared) {
            // Remove all
            for (slot in slotRange) {
                slotModes.remove(slot)
            }
        } else {
            // Add all with PRESERVE mode
            for (slot in slotRange) {
                if (!workingConfig.isSharedSlot(slot)) {
                    slotModes[slot] = SlotMode.PRESERVE
                }
            }
        }
        rebuildWorkingConfig()
    }

    private fun rebuildWorkingConfig() {
        // Group slots by mode
        val slotsByMode = slotModes.entries.groupBy({ it.value }, { it.key })

        val entries = mutableListOf<SharedSlotEntry>()

        for ((mode, slots) in slotsByMode) {
            if (slots.isNotEmpty()) {
                entries.add(SharedSlotEntry(
                    slots = slots.sorted(),
                    mode = mode
                ))
            }
        }

        workingConfig = workingConfig.copy(slots = entries)
    }

    private fun saveChanges() {
        rebuildWorkingConfig()

        // Convert working config to config section format
        val configSection = SharedSlotsConfigSection(
            enabled = workingConfig.enabled,
            slots = workingConfig.slots.map { entry ->
                SharedSlotConfigEntry(
                    slot = if (entry.slots == null) entry.slot else null,
                    slots = entry.slots,
                    mode = entry.mode.name,
                    item = null  // Item config not edited in this GUI
                )
            }
        )

        // Update the main config
        val mainConfig = plugin.configManager.mainConfig
        val updatedConfig = mainConfig.copy(sharedSlots = configSection)

        // Save to config file
        plugin.configManager.saveMainConfig(updatedConfig)

        // Update the shared slot service with the internal model
        plugin.serviceManager.sharedSlotService.updateConfig(workingConfig)

        hasChanges = false
        Logging.info("Saved shared slots configuration")
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
                Component.text("Warning: You have unsaved changes to shared slots!", NamedTextColor.YELLOW)
            )
        }
    }
}

package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.service.ConflictResolution
import sh.pcx.xinventories.internal.service.MergeConflict
import sh.pcx.xinventories.internal.service.PendingMerge
import sh.pcx.xinventories.internal.service.SlotType
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack

/**
 * GUI for previewing and resolving merge conflicts.
 *
 * Layout (54 slots - 6 rows):
 * Row 0: Header info
 * Row 1-2: Source inventory preview (18 slots)
 * Row 3: Conflict indicators / divider
 * Row 4-5: Target inventory preview (18 slots) + action buttons
 */
class MergePreviewGUI(
    plugin: PluginContext,
    private val admin: Player,
    private val pendingMerge: PendingMerge
) : AbstractGUI(
    plugin,
    Component.text()
        .append(Component.text("Merge: ", NamedTextColor.GOLD))
        .append(Component.text(pendingMerge.sourceGroup, NamedTextColor.AQUA))
        .append(Component.text(" -> ", NamedTextColor.GRAY))
        .append(Component.text(pendingMerge.targetGroup, NamedTextColor.GREEN))
        .build(),
    54
) {

    private val conflictSlots = mutableSetOf<Int>()

    init {
        setupItems()
    }

    private fun setupItems() {
        // Row 0: Header info
        setupHeaderRow()

        // Row 1-2: Source inventory (slots 9-26)
        setupSourceInventory()

        // Row 3: Divider with conflict indicators
        setupDividerRow()

        // Row 4-5: Target inventory (slots 36-53) with action buttons
        setupTargetInventory()

        // Action buttons
        setupActionButtons()
    }

    private fun setupHeaderRow() {
        // Fill header with decorative glass
        for (i in 0..8) {
            setItem(i, GUIComponents.filler(Material.BLUE_STAINED_GLASS_PANE))
        }

        // Player info
        setItem(1, GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name("Player: ${pendingMerge.sourceData.playerName}", NamedTextColor.GOLD)
            .lore("Merging inventories between groups")
            .build()
        )

        // Source group info
        setItem(3, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Source: ${pendingMerge.sourceGroup}", NamedTextColor.AQUA)
            .lore("Items: ${countItems(pendingMerge.sourceData.mainInventory)}")
            .lore("Armor: ${pendingMerge.sourceData.armorInventory.size} pieces")
            .lore("Level: ${pendingMerge.sourceData.level}")
            .build()
        )

        // Strategy info
        setItem(4, GUIItemBuilder()
            .material(Material.COMPARATOR)
            .name("Strategy: ${pendingMerge.strategy.name}", NamedTextColor.YELLOW)
            .lore(getStrategyDescription())
            .build()
        )

        // Target group info
        setItem(5, GUIItemBuilder()
            .material(Material.ENDER_CHEST)
            .name("Target: ${pendingMerge.targetGroup}", NamedTextColor.GREEN)
            .lore("Items: ${countItems(pendingMerge.targetData.mainInventory)}")
            .lore("Armor: ${pendingMerge.targetData.armorInventory.size} pieces")
            .lore("Level: ${pendingMerge.targetData.level}")
            .build()
        )

        // Conflict count
        val conflictCount = pendingMerge.conflicts.size
        val unresolvedCount = pendingMerge.conflicts.count { it.resolution == ConflictResolution.PENDING }
        setItem(7, GUIItemBuilder()
            .material(if (unresolvedCount > 0) Material.ORANGE_WOOL else Material.LIME_WOOL)
            .name(if (conflictCount == 0) "No Conflicts" else "$conflictCount Conflict(s)",
                if (unresolvedCount > 0) NamedTextColor.GOLD else NamedTextColor.GREEN)
            .lore(if (unresolvedCount > 0) "$unresolvedCount unresolved" else "All resolved")
            .build()
        )
    }

    private fun setupSourceInventory() {
        // Label
        setItem(9, GUIItemBuilder()
            .material(Material.CYAN_STAINED_GLASS_PANE)
            .name("Source Inventory", NamedTextColor.AQUA)
            .build()
        )

        // Display first 17 slots of source inventory (slots 10-26)
        for (i in 0..16) {
            val guiSlot = 10 + i
            val item = pendingMerge.sourceData.mainInventory[i]

            if (item != null) {
                val isConflict = pendingMerge.conflicts.any { it.slot == i && it.slotType == SlotType.MAIN_INVENTORY }
                if (isConflict) {
                    conflictSlots.add(guiSlot)
                    setItem(guiSlot, createConflictSourceItem(item, i))
                } else {
                    setItem(guiSlot, GUIItem(item.clone()))
                }
            } else {
                setItem(guiSlot, GUIComponents.filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
            }
        }
    }

    private fun setupDividerRow() {
        // Row 3: Divider (slots 27-35)
        for (i in 27..35) {
            val slotIndex = i - 27

            // Check if this slot has a conflict
            val conflict = pendingMerge.conflicts.find {
                it.slotType == SlotType.MAIN_INVENTORY && it.slot == slotIndex
            }

            if (conflict != null) {
                setItem(i, createConflictIndicator(conflict))
            } else {
                setItem(i, GUIComponents.filler(Material.GRAY_STAINED_GLASS_PANE))
            }
        }
    }

    private fun setupTargetInventory() {
        // Label
        setItem(36, GUIItemBuilder()
            .material(Material.LIME_STAINED_GLASS_PANE)
            .name("Target Inventory", NamedTextColor.GREEN)
            .build()
        )

        // Display first 17 slots of target inventory (slots 37-53)
        for (i in 0..16) {
            val guiSlot = 37 + i
            val item = pendingMerge.targetData.mainInventory[i]

            if (item != null) {
                val isConflict = pendingMerge.conflicts.any { it.slot == i && it.slotType == SlotType.MAIN_INVENTORY }
                if (isConflict) {
                    conflictSlots.add(guiSlot)
                    setItem(guiSlot, createConflictTargetItem(item, i))
                } else {
                    setItem(guiSlot, GUIItem(item.clone()))
                }
            } else {
                setItem(guiSlot, GUIComponents.filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE))
            }
        }
    }

    private fun setupActionButtons() {
        // Confirm button (slot 45)
        val unresolvedCount = pendingMerge.conflicts.count { it.resolution == ConflictResolution.PENDING }
        val canConfirm = unresolvedCount == 0

        setItem(45, GUIItemBuilder()
            .material(if (canConfirm) Material.LIME_WOOL else Material.GRAY_WOOL)
            .name(if (canConfirm) "Confirm Merge" else "Resolve Conflicts First",
                if (canConfirm) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore(if (canConfirm) "Click to execute the merge" else "$unresolvedCount conflict(s) unresolved")
            .onClick { event ->
                if (canConfirm) {
                    val player = event.whoClicked as Player
                    player.closeInventory()

                    runBlocking {
                        val result = plugin.serviceManager.mergeService.confirmMerge(player.uniqueId)
                        if (result.success) {
                            plugin.serviceManager.messageService.sendRaw(player, "merge-confirmed",
                                "player" to pendingMerge.sourceData.playerName,
                                "source" to pendingMerge.sourceGroup,
                                "target" to pendingMerge.targetGroup
                            )
                        } else {
                            plugin.serviceManager.messageService.sendRaw(player, "merge-failed",
                                "error" to result.message
                            )
                        }
                    }
                }
            }
            .build()
        )

        // Cancel button (slot 53)
        setItem(53, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Cancel", NamedTextColor.RED)
            .lore("Cancel the merge operation")
            .onClick { event ->
                val player = event.whoClicked as Player
                plugin.serviceManager.mergeService.cancelMerge(player.uniqueId)
                player.closeInventory()
                plugin.serviceManager.messageService.sendRaw(player, "merge-cancelled")
            }
            .build()
        )

        // Keep all source button (slot 47)
        if (pendingMerge.conflicts.isNotEmpty()) {
            setItem(47, GUIItemBuilder()
                .material(Material.CYAN_WOOL)
                .name("Keep All Source", NamedTextColor.AQUA)
                .lore("Resolve all conflicts by keeping source items")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    resolveAllConflicts(ConflictResolution.KEEP_SOURCE)
                    MergePreviewGUI(plugin, admin, plugin.serviceManager.mergeService.getPendingMerge(player.uniqueId)!!).open(player)
                }
                .build()
            )

            // Keep all target button (slot 49)
            setItem(49, GUIItemBuilder()
                .material(Material.LIME_WOOL)
                .name("Keep All Target", NamedTextColor.GREEN)
                .lore("Resolve all conflicts by keeping target items")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    resolveAllConflicts(ConflictResolution.KEEP_TARGET)
                    MergePreviewGUI(plugin, admin, plugin.serviceManager.mergeService.getPendingMerge(player.uniqueId)!!).open(player)
                }
                .build()
            )

            // Combine all button (slot 51)
            setItem(51, GUIItemBuilder()
                .material(Material.YELLOW_WOOL)
                .name("Combine All", NamedTextColor.YELLOW)
                .lore("Try to combine items where possible")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    resolveAllConflicts(ConflictResolution.COMBINE)
                    MergePreviewGUI(plugin, admin, plugin.serviceManager.mergeService.getPendingMerge(player.uniqueId)!!).open(player)
                }
                .build()
            )
        }
    }

    private fun createConflictSourceItem(item: ItemStack, slot: Int): GUIItem {
        val displayItem = item.clone()
        val meta = displayItem.itemMeta
        val existingLore = meta.lore() ?: mutableListOf()
        existingLore.add(Component.empty())
        existingLore.add(Component.text("CONFLICT - Slot $slot", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
        existingLore.add(Component.text("Click to keep SOURCE item", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
        meta.lore(existingLore)
        displayItem.itemMeta = meta

        return GUIItem(displayItem) { event ->
            val player = event.whoClicked as Player
            plugin.serviceManager.mergeService.resolveConflict(player.uniqueId, slot, ConflictResolution.KEEP_SOURCE)
            MergePreviewGUI(plugin, admin, plugin.serviceManager.mergeService.getPendingMerge(player.uniqueId)!!).open(player)
        }
    }

    private fun createConflictTargetItem(item: ItemStack, slot: Int): GUIItem {
        val displayItem = item.clone()
        val meta = displayItem.itemMeta
        val existingLore = meta.lore() ?: mutableListOf()
        existingLore.add(Component.empty())
        existingLore.add(Component.text("CONFLICT - Slot $slot", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
        existingLore.add(Component.text("Click to keep TARGET item", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
        meta.lore(existingLore)
        displayItem.itemMeta = meta

        return GUIItem(displayItem) { event ->
            val player = event.whoClicked as Player
            plugin.serviceManager.mergeService.resolveConflict(player.uniqueId, slot, ConflictResolution.KEEP_TARGET)
            MergePreviewGUI(plugin, admin, plugin.serviceManager.mergeService.getPendingMerge(player.uniqueId)!!).open(player)
        }
    }

    private fun createConflictIndicator(conflict: MergeConflict): GUIItem {
        val material = when (conflict.resolution) {
            ConflictResolution.PENDING -> Material.ORANGE_STAINED_GLASS_PANE
            ConflictResolution.KEEP_SOURCE -> Material.CYAN_STAINED_GLASS_PANE
            ConflictResolution.KEEP_TARGET -> Material.LIME_STAINED_GLASS_PANE
            ConflictResolution.COMBINE -> Material.YELLOW_STAINED_GLASS_PANE
        }

        val statusText = when (conflict.resolution) {
            ConflictResolution.PENDING -> "Unresolved"
            ConflictResolution.KEEP_SOURCE -> "Keep Source"
            ConflictResolution.KEEP_TARGET -> "Keep Target"
            ConflictResolution.COMBINE -> "Combine"
        }

        return GUIItemBuilder()
            .material(material)
            .name("Slot ${conflict.slot}: $statusText",
                when (conflict.resolution) {
                    ConflictResolution.PENDING -> NamedTextColor.GOLD
                    ConflictResolution.KEEP_SOURCE -> NamedTextColor.AQUA
                    ConflictResolution.KEEP_TARGET -> NamedTextColor.GREEN
                    ConflictResolution.COMBINE -> NamedTextColor.YELLOW
                }
            )
            .lore("Source: ${conflict.sourceItem?.type?.name ?: "Empty"} x${conflict.sourceItem?.amount ?: 0}")
            .lore("Target: ${conflict.targetItem?.type?.name ?: "Empty"} x${conflict.targetItem?.amount ?: 0}")
            .lore("")
            .lore("Click to cycle resolution")
            .onClick { event ->
                val player = event.whoClicked as Player
                val nextResolution = when (conflict.resolution) {
                    ConflictResolution.PENDING -> ConflictResolution.KEEP_SOURCE
                    ConflictResolution.KEEP_SOURCE -> ConflictResolution.KEEP_TARGET
                    ConflictResolution.KEEP_TARGET -> ConflictResolution.COMBINE
                    ConflictResolution.COMBINE -> ConflictResolution.KEEP_SOURCE
                }
                plugin.serviceManager.mergeService.resolveConflict(player.uniqueId, conflict.slot, nextResolution)
                MergePreviewGUI(plugin, admin, plugin.serviceManager.mergeService.getPendingMerge(player.uniqueId)!!).open(player)
            }
            .build()
    }

    private fun resolveAllConflicts(resolution: ConflictResolution) {
        for (conflict in pendingMerge.conflicts) {
            plugin.serviceManager.mergeService.resolveConflict(admin.uniqueId, conflict.slot, resolution)
        }
    }

    private fun countItems(inventory: Map<Int, ItemStack>): Int {
        return inventory.values.sumOf { it.amount }
    }

    private fun getStrategyDescription(): String {
        return when (pendingMerge.strategy) {
            sh.pcx.xinventories.internal.service.MergeStrategy.COMBINE -> "Add items, stack where possible"
            sh.pcx.xinventories.internal.service.MergeStrategy.REPLACE -> "Replace target with source"
            sh.pcx.xinventories.internal.service.MergeStrategy.KEEP_HIGHER -> "Keep higher count per slot"
            sh.pcx.xinventories.internal.service.MergeStrategy.MANUAL -> "Resolve conflicts manually"
        }
    }

    override fun fillEmptySlots(inventory: Inventory) {
        // Don't fill - we handle all slots explicitly
    }
}

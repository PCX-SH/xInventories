package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.VersionTrigger
import sh.pcx.xinventories.internal.util.Logging
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * GUI for browsing and restoring inventory versions.
 * Shows all saved versions with timestamps, allows previewing and restoring.
 */
class VersionBrowserGUI(
    plugin: PluginContext,
    private val targetUUID: UUID,
    private val targetName: String,
    private val groupFilter: String? = null,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Versions: $targetName", NamedTextColor.DARK_AQUA),
    54
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val itemsPerPage = 28 // 4 rows x 7 columns
    private var versions: List<InventoryVersion> = emptyList()
    private var selectedVersion: InventoryVersion? = null

    init {
        loadVersions()
        setupItems()
    }

    private fun loadVersions() {
        versions = runBlocking {
            plugin.serviceManager.versioningService.getVersions(
                playerUuid = targetUUID,
                group = groupFilter,
                limit = 100 // Load more for pagination
            )
        }
    }

    private fun setupItems() {
        // Fill border
        fillBorder(GUIComponents.filler())

        // Header info
        setItem(4, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Version History", NamedTextColor.GOLD)
            .lore("Player: $targetName")
            .lore(if (groupFilter != null) "Group: $groupFilter" else "All groups")
            .lore("")
            .lore("Total versions: ${versions.size}")
            .build()
        )

        // Populate versions
        if (versions.isEmpty()) {
            setItem(22, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("No Versions", NamedTextColor.RED)
                .lore("No saved versions found")
                .lore("")
                .lore("Versions are created on:")
                .lore("- World changes")
                .lore("- Disconnects")
                .lore("- Manual snapshots")
                .build()
            )
        } else {
            displayVersions()
        }

        // Navigation and controls
        setupNavigationRow()
    }

    private fun displayVersions() {
        val maxPage = if (versions.isEmpty()) 0 else (versions.size - 1) / itemsPerPage
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, versions.size)

        // Create slot grid (rows 1-4, columns 1-7)
        val versionSlots = mutableListOf<Int>()
        for (row in 1..4) {
            for (col in 1..7) {
                versionSlots.add(row * 9 + col)
            }
        }

        for (i in startIndex until endIndex) {
            val slotIndex = i - startIndex
            if (slotIndex >= versionSlots.size) break

            val version = versions[i]
            setItem(versionSlots[slotIndex], createVersionItem(version, i + 1))
        }

        // Page controls
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .lore("Go to page $page")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    VersionBrowserGUI(plugin, targetUUID, targetName, groupFilter, page - 1).open(player)
                }
                .build()
            )
        }

        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.WHITE)
            .lore("${versions.size} total versions")
            .build()
        )

        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .lore("Go to page ${page + 2}")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    VersionBrowserGUI(plugin, targetUUID, targetName, groupFilter, page + 1).open(player)
                }
                .build()
            )
        }
    }

    private fun createVersionItem(version: InventoryVersion, displayNumber: Int): GUIItem {
        val material = when (version.trigger) {
            VersionTrigger.WORLD_CHANGE -> Material.COMPASS
            VersionTrigger.DISCONNECT -> Material.ENDER_PEARL
            VersionTrigger.MANUAL -> Material.WRITABLE_BOOK
            VersionTrigger.DEATH -> Material.BONE
            VersionTrigger.SCHEDULED -> Material.CLOCK
        }

        val triggerColor = when (version.trigger) {
            VersionTrigger.WORLD_CHANGE -> NamedTextColor.AQUA
            VersionTrigger.DISCONNECT -> NamedTextColor.YELLOW
            VersionTrigger.MANUAL -> NamedTextColor.GREEN
            VersionTrigger.DEATH -> NamedTextColor.RED
            VersionTrigger.SCHEDULED -> NamedTextColor.LIGHT_PURPLE
        }

        val item = ItemStack(material)
        val meta = item.itemMeta

        meta.displayName(
            Component.text("Version #$displayNumber")
                .color(NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false)
        )

        val lore = mutableListOf<Component>()

        // Timestamp
        lore.add(
            Component.text("Time: ${dateFormatter.format(version.timestamp)}")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )

        // Relative time
        lore.add(
            Component.text("(${version.getRelativeTimeDescription()})")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )

        lore.add(Component.empty())

        // Trigger
        lore.add(
            Component.text("Trigger: ")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
                .append(
                    Component.text(formatTrigger(version.trigger))
                        .color(triggerColor)
                )
        )

        // Group
        lore.add(
            Component.text("Group: ${version.group}")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )

        // Game mode
        version.gameMode?.let { gm ->
            lore.add(
                Component.text("GameMode: ${gm.name}")
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
        }

        // Item summary
        lore.add(Component.empty())
        lore.add(
            Component.text("Contents: ${version.getItemSummary()}")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
        )

        // Metadata
        if (version.metadata.isNotEmpty()) {
            lore.add(Component.empty())
            version.metadata["world"]?.let { world ->
                lore.add(
                    Component.text("World: $world")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                )
            }
            version.metadata["reason"]?.let { reason ->
                lore.add(
                    Component.text("Reason: $reason")
                        .color(NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                )
            }
        }

        lore.add(Component.empty())
        lore.add(
            Component.text("Left-click: Preview")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        )
        lore.add(
            Component.text("Right-click: Restore")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        )
        lore.add(
            Component.text("Shift-right: Delete")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        )

        meta.lore(lore)
        item.itemMeta = meta

        return GUIItem(item) { event ->
            val player = event.whoClicked as Player

            when (event.click) {
                ClickType.LEFT -> {
                    // Preview version
                    VersionPreviewGUI(plugin, targetUUID, targetName, version).open(player)
                }
                ClickType.RIGHT -> {
                    // Restore version - show confirmation
                    showRestoreConfirmation(player, version)
                }
                ClickType.SHIFT_RIGHT -> {
                    // Delete version - show confirmation
                    showDeleteConfirmation(player, version)
                }
                else -> {
                    // Ignore other click types
                }
            }
        }
    }

    private fun formatTrigger(trigger: VersionTrigger): String {
        return when (trigger) {
            VersionTrigger.WORLD_CHANGE -> "World Change"
            VersionTrigger.DISCONNECT -> "Disconnect"
            VersionTrigger.MANUAL -> "Manual Snapshot"
            VersionTrigger.DEATH -> "Death"
            VersionTrigger.SCHEDULED -> "Scheduled"
        }
    }

    private fun showRestoreConfirmation(player: Player, version: InventoryVersion) {
        // Check if player is online
        val targetPlayer = Bukkit.getPlayer(targetUUID)
        if (targetPlayer == null) {
            player.sendMessage(
                Component.text("Cannot restore: $targetName is not online.", NamedTextColor.RED)
            )
            return
        }

        ConfirmationGUI(
            plugin,
            Component.text("Restore Version?", NamedTextColor.YELLOW),
            "Restore version from ${version.getRelativeTimeDescription()}",
            "This will overwrite their current inventory!",
            onConfirm = {
                restoreVersion(player, version, targetPlayer)
            },
            onCancel = {
                VersionBrowserGUI(plugin, targetUUID, targetName, groupFilter, page).open(player)
            }
        ).open(player)
    }

    private fun restoreVersion(admin: Player, version: InventoryVersion, targetPlayer: Player) {
        plugin.scope.launch(plugin.storageDispatcher) {
            try {
                val result = plugin.serviceManager.versioningService.restoreVersion(
                    player = targetPlayer,
                    versionId = version.id,
                    createBackup = true
                )

                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    if (result.isSuccess) {
                        admin.sendMessage(
                            Component.text("Successfully restored version from ${version.getRelativeTimeDescription()}!", NamedTextColor.GREEN)
                        )
                        targetPlayer.sendMessage(
                            Component.text("Your inventory has been restored by ${admin.name}.", NamedTextColor.YELLOW)
                        )

                        // Log to audit
                        plugin.serviceManager.auditService?.logVersionRestore(
                            admin = admin,
                            targetUuid = targetUUID,
                            targetName = targetName,
                            group = version.group,
                            versionId = 0 // Version ID is a string, using 0 as placeholder
                        )
                    } else {
                        admin.sendMessage(
                            Component.text("Failed to restore version: ${result.exceptionOrNull()?.message}", NamedTextColor.RED)
                        )
                    }

                    // Reopen the browser
                    VersionBrowserGUI(plugin, targetUUID, targetName, groupFilter, page).open(admin)
                }
            } catch (e: Exception) {
                Logging.error("Failed to restore version for $targetName", e)
                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    admin.sendMessage(
                        Component.text("Error restoring version: ${e.message}", NamedTextColor.RED)
                    )
                    VersionBrowserGUI(plugin, targetUUID, targetName, groupFilter, page).open(admin)
                }
            }
        }
    }

    private fun showDeleteConfirmation(player: Player, version: InventoryVersion) {
        ConfirmationGUI(
            plugin,
            Component.text("Delete Version?", NamedTextColor.RED),
            "Delete version from ${version.getRelativeTimeDescription()}",
            "This action cannot be undone!",
            onConfirm = {
                deleteVersion(player, version)
            },
            onCancel = {
                VersionBrowserGUI(plugin, targetUUID, targetName, groupFilter, page).open(player)
            }
        ).open(player)
    }

    private fun deleteVersion(admin: Player, version: InventoryVersion) {
        plugin.scope.launch(plugin.storageDispatcher) {
            try {
                val deleted = plugin.serviceManager.versioningService.deleteVersion(version.id)

                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    if (deleted) {
                        admin.sendMessage(
                            Component.text("Version deleted successfully.", NamedTextColor.GREEN)
                        )
                    } else {
                        admin.sendMessage(
                            Component.text("Failed to delete version.", NamedTextColor.RED)
                        )
                    }

                    // Reload and reopen
                    VersionBrowserGUI(plugin, targetUUID, targetName, groupFilter, page).open(admin)
                }
            } catch (e: Exception) {
                Logging.error("Failed to delete version", e)
                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    admin.sendMessage(
                        Component.text("Error deleting version: ${e.message}", NamedTextColor.RED)
                    )
                    VersionBrowserGUI(plugin, targetUUID, targetName, groupFilter, page).open(admin)
                }
            }
        }
    }

    private fun setupNavigationRow() {
        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to player details")
            .onClick { event ->
                val player = event.whoClicked as Player
                PlayerDetailGUI(plugin, targetUUID, targetName).open(player)
            }
            .build()
        )

        // Filter by group
        setItem(46, GUIItemBuilder()
            .material(Material.HOPPER)
            .name("Filter by Group", NamedTextColor.AQUA)
            .lore(if (groupFilter != null) "Current: $groupFilter" else "Showing all groups")
            .lore("")
            .lore("Click to toggle filter")
            .onClick { event ->
                val player = event.whoClicked as Player
                // Toggle between showing all and filtering by current world's group
                val newFilter = if (groupFilter == null) {
                    plugin.serviceManager.groupService.getGroupForWorld(player.world).name
                } else {
                    null
                }
                VersionBrowserGUI(plugin, targetUUID, targetName, newFilter, 0).open(player)
            }
            .build()
        )

        // Create manual snapshot (if target is online)
        val targetOnline = Bukkit.getPlayer(targetUUID) != null
        if (targetOnline) {
            setItem(52, GUIItemBuilder()
                .material(Material.SPYGLASS)
                .name("Create Snapshot", NamedTextColor.GREEN)
                .lore("Create a manual version snapshot")
                .lore("of the player's current inventory")
                .onClick { event ->
                    val admin = event.whoClicked as Player
                    val target = Bukkit.getPlayer(targetUUID)
                    if (target != null) {
                        createManualSnapshot(admin, target)
                    } else {
                        admin.sendMessage(Component.text("Player is no longer online.", NamedTextColor.RED))
                    }
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

    private fun createManualSnapshot(admin: Player, target: Player) {
        plugin.scope.launch(plugin.storageDispatcher) {
            try {
                val version = plugin.serviceManager.versioningService.createVersion(
                    player = target,
                    trigger = VersionTrigger.MANUAL,
                    metadata = mapOf("created_by" to admin.name),
                    force = true
                )

                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    if (version != null) {
                        admin.sendMessage(
                            Component.text("Created manual snapshot for ${target.name}.", NamedTextColor.GREEN)
                        )
                    } else {
                        admin.sendMessage(
                            Component.text("Failed to create snapshot.", NamedTextColor.RED)
                        )
                    }

                    // Reload and reopen
                    VersionBrowserGUI(plugin, targetUUID, targetName, groupFilter, 0).open(admin)
                }
            } catch (e: Exception) {
                Logging.error("Failed to create manual snapshot", e)
                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    admin.sendMessage(
                        Component.text("Error creating snapshot: ${e.message}", NamedTextColor.RED)
                    )
                }
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

/**
 * GUI for previewing a specific version's contents.
 */
class VersionPreviewGUI(
    plugin: PluginContext,
    private val targetUUID: UUID,
    private val targetName: String,
    private val version: InventoryVersion
) : AbstractGUI(
    plugin,
    Component.text("Preview: ${version.getRelativeTimeDescription()}", NamedTextColor.AQUA),
    54
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    // Store current player data for diff comparison
    private var currentData: sh.pcx.xinventories.internal.model.PlayerData? = null

    init {
        loadCurrentData()
        setupItems()
    }

    private fun loadCurrentData() {
        currentData = runBlocking {
            plugin.serviceManager.storageService.load(
                targetUUID,
                version.group,
                version.gameMode ?: org.bukkit.GameMode.SURVIVAL
            )
        }
    }

    private fun setupItems() {
        val data = version.data

        // Display inventory contents
        // Main inventory (slots 0-35)
        for (i in 0..35) {
            val versionItem = data.mainInventory[i]
            val currentItem = currentData?.mainInventory?.get(i)
            val isDifferent = !itemsMatch(versionItem, currentItem)

            if (versionItem != null && versionItem.type != Material.AIR) {
                setItem(i, createPreviewItem(versionItem, isDifferent, currentItem))
            } else if (isDifferent && currentItem != null) {
                // Item was added since this version (show as removed in preview)
                setItem(i, createRemovedIndicator(currentItem))
            }
        }

        // Armor slots
        val armorSlots = listOf(39, 38, 37, 36)
        for ((index, armorSlot) in armorSlots.withIndex()) {
            val versionItem = data.armorInventory[3 - index]
            val currentItem = currentData?.armorInventory?.get(3 - index)
            val isDifferent = !itemsMatch(versionItem, currentItem)

            if (versionItem != null && versionItem.type != Material.AIR) {
                setItem(armorSlot, createPreviewItem(versionItem, isDifferent, currentItem))
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

        // Offhand
        val versionOffhand = data.offhand
        val currentOffhand = currentData?.offhand
        val offhandDifferent = !itemsMatch(versionOffhand, currentOffhand)

        if (versionOffhand != null && versionOffhand.type != Material.AIR) {
            setItem(40, createPreviewItem(versionOffhand, offhandDifferent, currentOffhand))
        } else {
            setItem(40, createArmorPlaceholder("Offhand", Material.SHIELD))
        }

        // Info panel
        setupInfoPanel()
    }

    private fun itemsMatch(item1: ItemStack?, item2: ItemStack?): Boolean {
        if (item1 == null && item2 == null) return true
        if (item1 == null || item2 == null) return false
        return item1.type == item2.type && item1.amount == item2.amount
    }

    private fun createPreviewItem(item: ItemStack, isDifferent: Boolean, currentItem: ItemStack?): GUIItem {
        val displayItem = item.clone()
        val meta = displayItem.itemMeta

        val existingLore = meta.lore()?.toMutableList() ?: mutableListOf()
        existingLore.add(Component.empty())

        if (isDifferent) {
            existingLore.add(
                Component.text("* CHANGED *")
                    .color(NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false)
                    .decoration(TextDecoration.BOLD, true)
            )
            if (currentItem == null || currentItem.type == Material.AIR) {
                existingLore.add(
                    Component.text("Currently: Empty")
                        .color(NamedTextColor.RED)
                        .decoration(TextDecoration.ITALIC, false)
                )
            } else {
                existingLore.add(
                    Component.text("Currently: ${currentItem.type.name} x${currentItem.amount}")
                        .color(NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)
                )
            }
        } else {
            existingLore.add(
                Component.text("(unchanged)")
                    .color(NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
        }

        meta.lore(existingLore)
        displayItem.itemMeta = meta

        return GUIItem(displayItem)
    }

    private fun createRemovedIndicator(currentItem: ItemStack): GUIItem {
        val item = ItemStack(Material.RED_STAINED_GLASS_PANE)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Item Added Since")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        )
        meta.lore(listOf(
            Component.text("This slot was empty in this version")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.empty(),
            Component.text("Currently: ${currentItem.type.name} x${currentItem.amount}")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta
        return GUIItem(item)
    }

    private fun createArmorPlaceholder(name: String, material: Material): GUIItem {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Empty $name Slot")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )
        item.itemMeta = meta
        return GUIItem(item)
    }

    private fun setupInfoPanel() {
        // Fill bottom row
        for (i in 45..53) {
            setItem(i, GUIComponents.filler(Material.BLACK_STAINED_GLASS_PANE))
        }

        // Version info
        setItem(45, GUIItemBuilder()
            .material(Material.CLOCK)
            .name("Version Info", NamedTextColor.GOLD)
            .lore("Created: ${dateFormatter.format(version.timestamp)}")
            .lore("Trigger: ${version.trigger.name}")
            .lore("Group: ${version.group}")
            .build()
        )

        // Stats info
        val data = version.data
        setItem(46, GUIItemBuilder()
            .material(Material.EXPERIENCE_BOTTLE)
            .name("Player Stats", NamedTextColor.GREEN)
            .lore("Level: ${data.level}")
            .lore("Health: ${String.format("%.1f", data.health)}/${String.format("%.1f", data.maxHealth)}")
            .lore("Food: ${data.foodLevel}")
            .build()
        )

        // Diff legend
        setItem(48, GUIItemBuilder()
            .material(Material.YELLOW_WOOL)
            .name("Changed Items", NamedTextColor.YELLOW)
            .lore("Items marked with * CHANGED *")
            .lore("are different from current inventory")
            .build()
        )

        // Back button
        setItem(49, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back to Versions", NamedTextColor.GRAY)
            .lore("Return to version list")
            .onClick { event ->
                val player = event.whoClicked as Player
                VersionBrowserGUI(plugin, targetUUID, targetName).open(player)
            }
            .build()
        )

        // Restore button
        val targetOnline = Bukkit.getPlayer(targetUUID) != null
        if (targetOnline) {
            setItem(52, GUIItemBuilder()
                .material(Material.LIME_WOOL)
                .name("Restore This Version", NamedTextColor.GREEN)
                .lore("Click to restore this version")
                .lore("to the player's inventory")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    // Navigate back to browser which handles restoration
                    player.sendMessage(
                        Component.text("Use the version browser to restore. Right-click on the version.", NamedTextColor.YELLOW)
                    )
                    VersionBrowserGUI(plugin, targetUUID, targetName).open(player)
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

    override fun fillEmptySlots(inventory: Inventory) {
        val filler = GUIComponents.filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

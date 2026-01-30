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
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.util.Logging
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * GUI for viewing and recovering player death records.
 * Shows death history with locations, causes, and inventory contents.
 */
class DeathRecoveryGUI(
    plugin: XInventories,
    private val targetUUID: UUID,
    private val targetName: String,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Deaths: $targetName", NamedTextColor.RED),
    54
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val itemsPerPage = 28 // 4 rows x 7 columns
    private var deathRecords: List<DeathRecord> = emptyList()

    init {
        loadDeathRecords()
        setupItems()
    }

    private fun loadDeathRecords() {
        deathRecords = runBlocking {
            plugin.serviceManager.deathRecoveryService.getDeathRecords(targetUUID, 100)
        }
    }

    private fun setupItems() {
        // Fill border
        fillBorder(GUIComponents.filler())

        // Header info
        setItem(4, GUIItemBuilder()
            .material(Material.SKELETON_SKULL)
            .name("Death Recovery", NamedTextColor.RED)
            .lore("Player: $targetName")
            .lore("")
            .lore("Total death records: ${deathRecords.size}")
            .lore("")
            .lore("Click a death to view/restore")
            .build()
        )

        // Populate death records
        if (deathRecords.isEmpty()) {
            setItem(22, GUIItemBuilder()
                .material(Material.TOTEM_OF_UNDYING)
                .name("No Deaths Recorded", NamedTextColor.GREEN)
                .lore("This player has no recorded deaths")
                .lore("")
                .lore("Deaths are recorded when:")
                .lore("- Death recovery is enabled")
                .lore("- Player dies in a tracked world")
                .build()
            )
        } else {
            displayDeathRecords()
        }

        // Navigation and controls
        setupNavigationRow()
    }

    private fun displayDeathRecords() {
        val maxPage = if (deathRecords.isEmpty()) 0 else (deathRecords.size - 1) / itemsPerPage
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, deathRecords.size)

        // Create slot grid (rows 1-4, columns 1-7)
        val recordSlots = mutableListOf<Int>()
        for (row in 1..4) {
            for (col in 1..7) {
                recordSlots.add(row * 9 + col)
            }
        }

        for (i in startIndex until endIndex) {
            val slotIndex = i - startIndex
            if (slotIndex >= recordSlots.size) break

            val deathRecord = deathRecords[i]
            setItem(recordSlots[slotIndex], createDeathRecordItem(deathRecord, i + 1))
        }

        // Page controls
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .lore("Go to page $page")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    DeathRecoveryGUI(plugin, targetUUID, targetName, page - 1).open(player)
                }
                .build()
            )
        }

        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.WHITE)
            .lore("${deathRecords.size} total deaths")
            .build()
        )

        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .lore("Go to page ${page + 2}")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    DeathRecoveryGUI(plugin, targetUUID, targetName, page + 1).open(player)
                }
                .build()
            )
        }
    }

    private fun createDeathRecordItem(deathRecord: DeathRecord, displayNumber: Int): GUIItem {
        // Choose material based on death cause
        val material = when {
            deathRecord.killerUuid != null -> Material.IRON_SWORD // PvP death
            deathRecord.killerName != null -> Material.ZOMBIE_HEAD // Mob kill
            deathRecord.deathCause?.contains("FALL") == true -> Material.FEATHER
            deathRecord.deathCause?.contains("LAVA") == true -> Material.LAVA_BUCKET
            deathRecord.deathCause?.contains("FIRE") == true -> Material.BLAZE_POWDER
            deathRecord.deathCause?.contains("DROWN") == true -> Material.WATER_BUCKET
            deathRecord.deathCause?.contains("VOID") == true -> Material.END_PORTAL_FRAME
            deathRecord.deathCause?.contains("EXPLOSION") == true -> Material.TNT
            deathRecord.deathCause?.contains("MAGIC") == true -> Material.SPLASH_POTION
            deathRecord.deathCause?.contains("WITHER") == true -> Material.WITHER_SKELETON_SKULL
            else -> Material.BONE
        }

        val item = ItemStack(material)
        val meta = item.itemMeta

        meta.displayName(
            Component.text("Death #$displayNumber")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        )

        val lore = mutableListOf<Component>()

        // Timestamp
        lore.add(
            Component.text("Time: ${dateFormatter.format(deathRecord.timestamp)}")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )

        // Relative time
        lore.add(
            Component.text("(${deathRecord.getRelativeTimeDescription()})")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )

        lore.add(Component.empty())

        // Death cause
        lore.add(
            Component.text("Cause: ${deathRecord.getDeathDescription()}")
                .color(NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false)
        )

        // Location
        lore.add(
            Component.text("Location: ${deathRecord.getLocationString()}")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )

        // Group
        lore.add(
            Component.text("Group: ${deathRecord.group}")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )

        // Item summary
        lore.add(Component.empty())
        lore.add(
            Component.text("Contents: ${deathRecord.getItemSummary()}")
                .color(NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false)
        )

        lore.add(Component.empty())
        lore.add(
            Component.text("Left-click: Preview inventory")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        )
        lore.add(
            Component.text("Right-click: Restore to player")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        )
        lore.add(
            Component.text("Shift-right: Delete record")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        )

        meta.lore(lore)
        item.itemMeta = meta

        return GUIItem(item) { event ->
            val player = event.whoClicked as Player

            when (event.click) {
                ClickType.LEFT -> {
                    // Preview death inventory
                    DeathPreviewGUI(plugin, targetUUID, targetName, deathRecord).open(player)
                }
                ClickType.RIGHT -> {
                    // Restore death inventory - show confirmation
                    showRestoreConfirmation(player, deathRecord)
                }
                ClickType.SHIFT_RIGHT -> {
                    // Delete death record - show confirmation
                    showDeleteConfirmation(player, deathRecord)
                }
                else -> {
                    // Ignore other click types
                }
            }
        }
    }

    private fun showRestoreConfirmation(player: Player, deathRecord: DeathRecord) {
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
            Component.text("Restore Death Inventory?", NamedTextColor.YELLOW),
            "Restore inventory from death ${deathRecord.getRelativeTimeDescription()}",
            "This will overwrite their current inventory!",
            onConfirm = {
                restoreDeathInventory(player, deathRecord, targetPlayer)
            },
            onCancel = {
                DeathRecoveryGUI(plugin, targetUUID, targetName, page).open(player)
            }
        ).open(player)
    }

    private fun restoreDeathInventory(admin: Player, deathRecord: DeathRecord, targetPlayer: Player) {
        plugin.scope.launch(plugin.storageDispatcher) {
            try {
                val result = plugin.serviceManager.deathRecoveryService.restoreDeathInventory(
                    player = targetPlayer,
                    deathId = deathRecord.id
                )

                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    if (result.isSuccess) {
                        admin.sendMessage(
                            Component.text("Successfully restored death inventory from ${deathRecord.getRelativeTimeDescription()}!", NamedTextColor.GREEN)
                        )
                        targetPlayer.sendMessage(
                            Component.text("Your death inventory has been restored by ${admin.name}.", NamedTextColor.YELLOW)
                        )

                        // Log to audit
                        plugin.serviceManager.auditService?.logDeathRestore(
                            admin = admin,
                            targetUuid = targetUUID,
                            targetName = targetName,
                            group = deathRecord.group,
                            deathId = 0 // Death ID is a string, using 0 as placeholder
                        )
                    } else {
                        admin.sendMessage(
                            Component.text("Failed to restore death inventory: ${result.exceptionOrNull()?.message}", NamedTextColor.RED)
                        )
                    }

                    // Reopen the death recovery GUI
                    DeathRecoveryGUI(plugin, targetUUID, targetName, page).open(admin)
                }
            } catch (e: Exception) {
                Logging.error("Failed to restore death inventory for $targetName", e)
                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    admin.sendMessage(
                        Component.text("Error restoring death inventory: ${e.message}", NamedTextColor.RED)
                    )
                    DeathRecoveryGUI(plugin, targetUUID, targetName, page).open(admin)
                }
            }
        }
    }

    private fun showDeleteConfirmation(player: Player, deathRecord: DeathRecord) {
        ConfirmationGUI(
            plugin,
            Component.text("Delete Death Record?", NamedTextColor.RED),
            "Delete death record from ${deathRecord.getRelativeTimeDescription()}",
            "This action cannot be undone!",
            onConfirm = {
                deleteDeathRecord(player, deathRecord)
            },
            onCancel = {
                DeathRecoveryGUI(plugin, targetUUID, targetName, page).open(player)
            }
        ).open(player)
    }

    private fun deleteDeathRecord(admin: Player, deathRecord: DeathRecord) {
        plugin.scope.launch(plugin.storageDispatcher) {
            try {
                val deleted = plugin.serviceManager.deathRecoveryService.deleteDeathRecord(deathRecord.id)

                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    if (deleted) {
                        admin.sendMessage(
                            Component.text("Death record deleted successfully.", NamedTextColor.GREEN)
                        )
                    } else {
                        admin.sendMessage(
                            Component.text("Failed to delete death record.", NamedTextColor.RED)
                        )
                    }

                    // Reload and reopen
                    DeathRecoveryGUI(plugin, targetUUID, targetName, page).open(admin)
                }
            } catch (e: Exception) {
                Logging.error("Failed to delete death record", e)
                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    admin.sendMessage(
                        Component.text("Error deleting death record: ${e.message}", NamedTextColor.RED)
                    )
                    DeathRecoveryGUI(plugin, targetUUID, targetName, page).open(admin)
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

        // Statistics
        setItem(46, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Death Statistics", NamedTextColor.AQUA)
            .lore("Total deaths: ${deathRecords.size}")
            .lore("")
            .lore(if (deathRecords.isNotEmpty()) {
                "Most recent: ${deathRecords.first().getRelativeTimeDescription()}"
            } else {
                "No deaths recorded"
            })
            .build()
        )

        // Teleport to most recent death (if admin has permission)
        if (deathRecords.isNotEmpty()) {
            val mostRecent = deathRecords.first()
            setItem(52, GUIItemBuilder()
                .material(Material.ENDER_PEARL)
                .name("Teleport to Death Location", NamedTextColor.LIGHT_PURPLE)
                .lore("Most recent death location:")
                .lore(mostRecent.getLocationString())
                .lore("")
                .lore("Click to teleport")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    teleportToDeathLocation(player, mostRecent)
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

    private fun teleportToDeathLocation(player: Player, deathRecord: DeathRecord) {
        val world = Bukkit.getWorld(deathRecord.world)
        if (world == null) {
            player.sendMessage(
                Component.text("World '${deathRecord.world}' not found!", NamedTextColor.RED)
            )
            return
        }

        val location = org.bukkit.Location(world, deathRecord.x, deathRecord.y, deathRecord.z)
        player.teleport(location)
        player.sendMessage(
            Component.text("Teleported to death location: ${deathRecord.getLocationString()}", NamedTextColor.GREEN)
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
 * GUI for previewing a death record's inventory contents.
 */
class DeathPreviewGUI(
    plugin: XInventories,
    private val targetUUID: UUID,
    private val targetName: String,
    private val deathRecord: DeathRecord
) : AbstractGUI(
    plugin,
    Component.text("Death Preview: ${deathRecord.getRelativeTimeDescription()}", NamedTextColor.RED),
    54
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    init {
        setupItems()
    }

    private fun setupItems() {
        val data = deathRecord.inventoryData

        // Display inventory contents
        // Main inventory (slots 0-35)
        for (i in 0..35) {
            val item = data.mainInventory[i]
            if (item != null && item.type != Material.AIR) {
                setItem(i, GUIItem(item.clone()))
            }
        }

        // Armor slots
        val armorSlots = listOf(39, 38, 37, 36)
        for ((index, armorSlot) in armorSlots.withIndex()) {
            val item = data.armorInventory[3 - index]
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

        // Offhand
        val offhandItem = data.offhand
        if (offhandItem != null && offhandItem.type != Material.AIR) {
            setItem(40, GUIItem(offhandItem.clone()))
        } else {
            setItem(40, createArmorPlaceholder("Offhand", Material.SHIELD))
        }

        // Info panel
        setupInfoPanel()
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

        // Death info
        setItem(45, GUIItemBuilder()
            .material(Material.SKELETON_SKULL)
            .name("Death Info", NamedTextColor.RED)
            .lore("Time: ${dateFormatter.format(deathRecord.timestamp)}")
            .lore("Cause: ${deathRecord.getDeathDescription()}")
            .lore("Location: ${deathRecord.getLocationString()}")
            .build()
        )

        // Stats info
        val data = deathRecord.inventoryData
        setItem(46, GUIItemBuilder()
            .material(Material.EXPERIENCE_BOTTLE)
            .name("Player Stats at Death", NamedTextColor.GREEN)
            .lore("Level: ${data.level}")
            .lore("XP Progress: ${String.format("%.1f", data.experience * 100)}%")
            .lore("Health: ${String.format("%.1f", data.health)}")
            .lore("Food: ${data.foodLevel}")
            .build()
        )

        // Item count
        val totalItems = data.mainInventory.size + data.armorInventory.size + (if (data.offhand != null) 1 else 0)
        setItem(47, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Inventory Contents", NamedTextColor.AQUA)
            .lore("Main inventory: ${data.mainInventory.size} items")
            .lore("Armor: ${data.armorInventory.size} pieces")
            .lore("Offhand: ${if (data.offhand != null) "Yes" else "No"}")
            .lore("")
            .lore("Total: $totalItems items")
            .build()
        )

        // Back button
        setItem(49, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back to Deaths", NamedTextColor.GRAY)
            .lore("Return to death list")
            .onClick { event ->
                val player = event.whoClicked as Player
                DeathRecoveryGUI(plugin, targetUUID, targetName).open(player)
            }
            .build()
        )

        // Restore button (if target is online)
        val targetPlayer = Bukkit.getPlayer(targetUUID)
        if (targetPlayer != null) {
            setItem(52, GUIItemBuilder()
                .material(Material.LIME_WOOL)
                .name("Restore This Death", NamedTextColor.GREEN)
                .lore("Click to restore this death")
                .lore("inventory to the player")
                .lore("")
                .lore("Warning: This will overwrite")
                .lore("their current inventory!")
                .onClick { event ->
                    val admin = event.whoClicked as Player
                    showRestoreConfirmation(admin, targetPlayer)
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

    private fun showRestoreConfirmation(admin: Player, targetPlayer: Player) {
        ConfirmationGUI(
            plugin,
            Component.text("Restore Death Inventory?", NamedTextColor.YELLOW),
            "Restore inventory from death ${deathRecord.getRelativeTimeDescription()}",
            "This will overwrite their current inventory!",
            onConfirm = {
                restoreDeathInventory(admin, targetPlayer)
            },
            onCancel = {
                DeathPreviewGUI(plugin, targetUUID, targetName, deathRecord).open(admin)
            }
        ).open(admin)
    }

    private fun restoreDeathInventory(admin: Player, targetPlayer: Player) {
        plugin.scope.launch(plugin.storageDispatcher) {
            try {
                val result = plugin.serviceManager.deathRecoveryService.restoreDeathInventory(
                    player = targetPlayer,
                    deathId = deathRecord.id
                )

                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    if (result.isSuccess) {
                        admin.sendMessage(
                            Component.text("Successfully restored death inventory from ${deathRecord.getRelativeTimeDescription()}!", NamedTextColor.GREEN)
                        )
                        targetPlayer.sendMessage(
                            Component.text("Your death inventory has been restored by ${admin.name}.", NamedTextColor.YELLOW)
                        )

                        // Log to audit
                        plugin.serviceManager.auditService?.logDeathRestore(
                            admin = admin,
                            targetUuid = targetUUID,
                            targetName = targetName,
                            group = deathRecord.group,
                            deathId = 0
                        )
                    } else {
                        admin.sendMessage(
                            Component.text("Failed to restore death inventory: ${result.exceptionOrNull()?.message}", NamedTextColor.RED)
                        )
                    }

                    // Return to death list
                    DeathRecoveryGUI(plugin, targetUUID, targetName).open(admin)
                }
            } catch (e: Exception) {
                Logging.error("Failed to restore death inventory for $targetName", e)
                kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                    admin.sendMessage(
                        Component.text("Error restoring death inventory: ${e.message}", NamedTextColor.RED)
                    )
                    DeathRecoveryGUI(plugin, targetUUID, targetName).open(admin)
                }
            }
        }
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

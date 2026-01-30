package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.event.InventoryUnlockEvent
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.InventoryLock
import sh.pcx.xinventories.internal.model.LockScope
import sh.pcx.xinventories.internal.util.Logging
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.inventory.ClickType
import org.bukkit.inventory.Inventory
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * GUI for managing inventory locks.
 *
 * Features:
 * - List all active inventory locks
 * - Show lock details (player, reason, duration remaining, locked by)
 * - Unlock player button
 * - Create new lock (player selector + reason input + duration)
 * - Filter by: all, expiring soon, permanent
 * - Pagination
 * - Bulk unlock option
 */
class LockManagerGUI(
    plugin: PluginContext,
    private var filterMode: FilterMode = FilterMode.ALL
) : AbstractGUI(
    plugin,
    Component.text("Lock Manager", NamedTextColor.DARK_RED),
    54
) {
    enum class FilterMode {
        ALL,
        EXPIRING_SOON,  // Expiring within 1 hour
        PERMANENT
    }

    // Current page for lock list
    private var currentPage: Int = 0

    // Number of locks we can display per page (slots 10-16, 19-25, 28-34 = 21 slots)
    private val locksPerPage: Int = 21

    // Slots for displaying locks
    private val lockSlots: List<Int> = listOf(
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34
    )

    // Date formatter for display
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    init {
        setupItems()
    }

    private fun setupItems() {
        items.clear()

        // Fill border
        fillBorder(GUIComponents.filler())

        // === Row 0: Controls ===

        // Filter buttons
        setItem(1, createFilterButton(FilterMode.ALL, Material.BOOK, "All Locks"))
        setItem(2, createFilterButton(FilterMode.EXPIRING_SOON, Material.CLOCK, "Expiring Soon"))
        setItem(3, createFilterButton(FilterMode.PERMANENT, Material.BEDROCK, "Permanent"))

        // Create new lock button
        setItem(5, GUIItemBuilder()
            .material(Material.ANVIL)
            .name("Create New Lock", NamedTextColor.GOLD)
            .lore("Click to lock a player's inventory")
            .onClick { event ->
                val player = event.whoClicked as Player
                startCreateLock(player)
            }
            .build()
        )

        // Bulk unlock button
        setItem(7, GUIItemBuilder()
            .material(Material.TNT)
            .name("Bulk Unlock", NamedTextColor.RED)
            .lore("Shift+Click to unlock ALL players")
            .lore("")
            .lore("This action cannot be undone!")
            .onClick { event ->
                if (event.click == ClickType.SHIFT_LEFT || event.click == ClickType.SHIFT_RIGHT) {
                    bulkUnlock()
                    val player = event.whoClicked as Player
                    player.sendMessage(Component.text("All locks have been removed!", NamedTextColor.RED))
                    setupItems()
                    refreshInventory()
                }
            }
            .build()
        )

        // Refresh button
        setItem(8, GUIItemBuilder()
            .material(Material.SUNFLOWER)
            .name("Refresh", NamedTextColor.YELLOW)
            .lore("Click to refresh the lock list")
            .onClick { event ->
                setupItems()
                refreshInventory()
            }
            .build()
        )

        // === Lock list ===
        setupLockList()

        // === Row 5: Navigation ===

        // Previous page
        if (currentPage > 0) {
            setItem(45, GUIComponents.previousPageButton(currentPage + 1) {
                currentPage--
                setupItems()
                refreshInventory()
            })
        } else {
            setItem(45, GUIComponents.filler())
        }

        // Info display
        val locks = getFilteredLocks()
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Lock Statistics", NamedTextColor.AQUA)
            .lore("Total Locks: ${plugin.serviceManager.lockingService.getLockedPlayers().size}")
            .lore("Showing: ${locks.size} (${filterMode.name})")
            .lore("Page: ${currentPage + 1}")
            .build()
        )

        // Next page
        val totalPages = (locks.size + locksPerPage - 1) / locksPerPage
        if (currentPage < totalPages - 1) {
            setItem(53, GUIComponents.nextPageButton(currentPage + 1) {
                currentPage++
                setupItems()
                refreshInventory()
            })
        } else {
            setItem(53, GUIComponents.filler())
        }

        // Close button
        setItem(52, GUIComponents.closeButton())
    }

    private fun createFilterButton(mode: FilterMode, material: Material, name: String): GUIItem {
        val isActive = filterMode == mode
        return GUIItemBuilder()
            .material(if (isActive) Material.LIME_DYE else material)
            .name(name, if (isActive) NamedTextColor.GREEN else NamedTextColor.WHITE)
            .lore(if (isActive) "Currently selected" else "Click to filter")
            .onClick { event ->
                filterMode = mode
                currentPage = 0
                setupItems()
                refreshInventory()
            }
            .build()
    }

    private fun getFilteredLocks(): List<InventoryLock> {
        val allLocks = plugin.serviceManager.lockingService.getLockedPlayers()
        val oneHourFromNow = Instant.now().plus(Duration.ofHours(1))

        return when (filterMode) {
            FilterMode.ALL -> allLocks
            FilterMode.EXPIRING_SOON -> allLocks.filter { lock ->
                lock.expiresAt != null && lock.expiresAt.isBefore(oneHourFromNow)
            }
            FilterMode.PERMANENT -> allLocks.filter { lock ->
                lock.expiresAt == null
            }
        }.sortedBy { it.lockedAt }
    }

    private fun setupLockList() {
        val locks = getFilteredLocks()

        // Calculate pagination
        val startIndex = currentPage * locksPerPage
        val endIndex = minOf(startIndex + locksPerPage, locks.size)
        val locksToShow = if (startIndex < locks.size) {
            locks.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        // Display locks
        locksToShow.forEachIndexed { index, lock ->
            if (index < lockSlots.size) {
                val slot = lockSlots[index]
                setItem(slot, createLockItem(lock))
            }
        }

        // Fill remaining slots with empty indicators
        for (i in locksToShow.size until lockSlots.size) {
            val slot = lockSlots[i]
            setItem(slot, GUIItemBuilder()
                .material(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name("No Lock", NamedTextColor.GRAY)
                .build()
            )
        }
    }

    private fun createLockItem(lock: InventoryLock): GUIItem {
        val playerName = Bukkit.getOfflinePlayer(lock.playerUuid).name ?: lock.playerUuid.toString()
        val lockedByName = lock.lockedBy?.let { Bukkit.getOfflinePlayer(it).name } ?: "SYSTEM"

        val material = when {
            lock.expiresAt == null -> Material.BEDROCK  // Permanent
            lock.expiresAt.isBefore(Instant.now().plus(Duration.ofMinutes(30))) -> Material.ORANGE_WOOL  // Expiring very soon
            lock.expiresAt.isBefore(Instant.now().plus(Duration.ofHours(1))) -> Material.YELLOW_WOOL  // Expiring soon
            else -> Material.RED_WOOL  // Not expiring soon
        }

        val color = when {
            lock.expiresAt == null -> NamedTextColor.DARK_PURPLE
            lock.expiresAt.isBefore(Instant.now().plus(Duration.ofMinutes(30))) -> NamedTextColor.GOLD
            else -> NamedTextColor.RED
        }

        val builder = GUIItemBuilder()
            .material(material)
            .name(playerName, color)
            .lore("Locked by: $lockedByName")
            .lore("Locked at: ${dateFormatter.format(lock.lockedAt)}")

        if (lock.expiresAt != null) {
            builder.lore("Expires: ${lock.getRemainingTimeString()}")
        } else {
            builder.lore("Duration: Permanent")
        }

        if (lock.reason != null) {
            builder.lore("")
            builder.lore("Reason: ${lock.reason}")
        }

        builder.lore("")
        builder.lore("Scope: ${lock.scope.name}")
        if (lock.scope == LockScope.GROUP && lock.lockedGroup != null) {
            builder.lore("Group: ${lock.lockedGroup}")
        }
        if (lock.scope == LockScope.SLOTS && lock.lockedSlots != null) {
            builder.lore("Slots: ${lock.lockedSlots.joinToString(", ")}")
        }

        builder.lore("")
        builder.lore("Left-click: Unlock player")
        builder.lore("Right-click: View player inventory")

        return builder
            .onClick { event ->
                val player = event.whoClicked as Player
                when (event.click) {
                    ClickType.RIGHT, ClickType.SHIFT_RIGHT -> {
                        // View player inventory
                        val targetName = Bukkit.getOfflinePlayer(lock.playerUuid).name ?: lock.playerUuid.toString()
                        PlayerDetailGUI(plugin, lock.playerUuid, targetName).open(player)
                    }
                    else -> {
                        // Unlock the player
                        val success = plugin.serviceManager.lockingService.unlockInventory(
                            lock.playerUuid,
                            InventoryUnlockEvent.UnlockReason.MANUAL
                        )
                        if (success) {
                            player.sendMessage(Component.text("Unlocked $playerName!", NamedTextColor.GREEN))
                        } else {
                            player.sendMessage(Component.text("Failed to unlock $playerName", NamedTextColor.RED))
                        }
                        setupItems()
                        refreshInventory()
                    }
                }
            }
            .build()
    }

    private fun startCreateLock(player: Player) {
        player.closeInventory()
        player.sendMessage(Component.text("Enter the player name to lock:", NamedTextColor.YELLOW))

        plugin.inputManager.awaitChatInput(player) { playerName ->
            val targetPlayer = Bukkit.getOfflinePlayer(playerName)
            if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline) {
                player.sendMessage(Component.text("Player not found: $playerName", NamedTextColor.RED))
                LockManagerGUI(plugin, filterMode).open(player)
                return@awaitChatInput
            }

            player.sendMessage(Component.text("Enter lock duration (e.g., 30m, 1h, 1d, or 'permanent'):", NamedTextColor.YELLOW))

            plugin.inputManager.awaitChatInput(player) { durationStr ->
                val duration = if (durationStr.equals("permanent", ignoreCase = true)) {
                    null
                } else {
                    InventoryLock.parseDuration(durationStr)
                }

                if (duration == null && !durationStr.equals("permanent", ignoreCase = true)) {
                    player.sendMessage(Component.text("Invalid duration format. Use: 30s, 5m, 1h, 1d, 1w, or 'permanent'", NamedTextColor.RED))
                    LockManagerGUI(plugin, filterMode).open(player)
                    return@awaitChatInput
                }

                player.sendMessage(Component.text("Enter lock reason (or 'none' for no reason):", NamedTextColor.YELLOW))

                plugin.inputManager.awaitChatInput(player) { reasonStr ->
                    val reason = if (reasonStr.equals("none", ignoreCase = true)) null else reasonStr

                    val lock = plugin.serviceManager.lockingService.lockInventory(
                        playerUuid = targetPlayer.uniqueId,
                        lockedBy = player.uniqueId,
                        duration = duration,
                        reason = reason,
                        scope = LockScope.ALL
                    )

                    if (lock != null) {
                        player.sendMessage(Component.text("Locked ${targetPlayer.name}'s inventory!", NamedTextColor.GREEN))
                    } else {
                        player.sendMessage(Component.text("Failed to lock inventory (may have been cancelled)", NamedTextColor.RED))
                    }

                    LockManagerGUI(plugin, filterMode).open(player)
                }
            }
        }
    }

    private fun bulkUnlock() {
        val locks = plugin.serviceManager.lockingService.getLockedPlayers()
        var count = 0

        for (lock in locks) {
            if (plugin.serviceManager.lockingService.unlockInventory(
                lock.playerUuid,
                InventoryUnlockEvent.UnlockReason.MANUAL
            )) {
                count++
            }
        }

        Logging.info("Bulk unlocked $count players")
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
}

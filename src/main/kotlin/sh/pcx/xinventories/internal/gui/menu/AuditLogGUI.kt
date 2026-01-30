package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.AuditAction
import sh.pcx.xinventories.internal.model.AuditEntry
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * GUI for viewing audit log entries with filtering and pagination.
 *
 * Features:
 * - Paginated log viewer (newest first)
 * - Show: timestamp, actor, target, action, details
 * - Filter by action type
 * - Filter by player (anvil input)
 * - Filter by date range
 * - Click entry for full details
 * - Export to file button
 */
class AuditLogGUI(
    plugin: XInventories,
    private val filterAction: AuditAction? = null,
    private val filterPlayer: UUID? = null,
    private val filterPlayerName: String? = null,
    private val filterFrom: Instant? = null,
    private val filterTo: Instant? = null,
    private var currentPage: Int = 0
) : AbstractGUI(
    plugin,
    buildTitle(filterAction, filterPlayerName),
    54
) {
    companion object {
        private const val ENTRIES_PER_PAGE = 28
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
        private val FULL_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())

        private fun buildTitle(action: AuditAction?, playerName: String?): Component {
            val title = StringBuilder("Audit Log")
            if (action != null) title.append(" [${action.name}]")
            if (playerName != null) title.append(" - $playerName")
            return Component.text(title.toString(), NamedTextColor.DARK_PURPLE)
        }
    }

    private var entries: List<AuditEntry> = emptyList()
    private var totalPages: Int = 1

    init {
        loadEntries()
    }

    private fun loadEntries() {
        val auditService = plugin.serviceManager.auditService ?: return

        plugin.scope.launch {
            entries = when {
                filterAction != null -> auditService.searchByAction(filterAction, filterFrom, filterTo, 500)
                filterPlayer != null -> auditService.getEntriesForPlayer(filterPlayer, 500)
                filterFrom != null && filterTo != null -> auditService.getEntriesInRange(filterFrom, filterTo, 500)
                else -> auditService.getEntriesInRange(
                    Instant.now().minus(7, ChronoUnit.DAYS),
                    Instant.now(),
                    500
                )
            }

            totalPages = maxOf(1, (entries.size + ENTRIES_PER_PAGE - 1) / ENTRIES_PER_PAGE)
            if (currentPage >= totalPages) currentPage = totalPages - 1

            setupItems()
        }
    }

    private fun setupItems() {
        items.clear()

        // Fill border
        fillBorder(GUIComponents.filler())

        // Filter buttons (top row)
        setupFilterButtons()

        // Audit entries (rows 1-4, columns 1-7)
        setupEntryItems()

        // Navigation (bottom row)
        setupNavigation()
    }

    private fun setupFilterButtons() {
        // Action filter dropdown
        setItem(0, 2, GUIItemBuilder()
            .material(Material.HOPPER)
            .name("Filter by Action", NamedTextColor.YELLOW)
            .lore("Current: ${filterAction?.name ?: "All"}")
            .lore("")
            .lore("Click to cycle through actions")
            .onClick { event ->
                val player = event.whoClicked as Player
                val actions = listOf(null) + AuditAction.entries.toList()
                val currentIndex = actions.indexOf(filterAction)
                val nextAction = actions[(currentIndex + 1) % actions.size]
                AuditLogGUI(plugin, nextAction, filterPlayer, filterPlayerName, filterFrom, filterTo, 0).open(player)
            }
            .build()
        )

        // Player filter (anvil input simulation via chat)
        setItem(0, 4, GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name("Filter by Player", NamedTextColor.AQUA)
            .lore("Current: ${filterPlayerName ?: "All"}")
            .lore("")
            .lore("Click to enter player name")
            .lore("Shift+Click to clear")
            .onClick { event ->
                val player = event.whoClicked as Player
                if (event.isShiftClick) {
                    // Clear filter
                    AuditLogGUI(plugin, filterAction, null, null, filterFrom, filterTo, 0).open(player)
                } else {
                    player.closeInventory()
                    player.sendMessage(Component.text("Enter a player name to filter by (or 'cancel'):", NamedTextColor.YELLOW))
                    plugin.guiManager.registerChatInput(player) { input ->
                        if (input.lowercase() == "cancel") {
                            AuditLogGUI(plugin, filterAction, filterPlayer, filterPlayerName, filterFrom, filterTo, currentPage).open(player)
                        } else {
                            val targetPlayer = plugin.server.getOfflinePlayer(input)
                            AuditLogGUI(plugin, filterAction, targetPlayer.uniqueId, input, filterFrom, filterTo, 0).open(player)
                        }
                    }
                }
            }
            .build()
        )

        // Date range filter
        setItem(0, 6, GUIItemBuilder()
            .material(Material.CLOCK)
            .name("Filter by Date", NamedTextColor.GOLD)
            .lore(getDateFilterDescription())
            .lore("")
            .lore("Left-click: Last 24 hours")
            .lore("Right-click: Last 7 days")
            .lore("Shift-click: All time")
            .onClick { event ->
                val player = event.whoClicked as Player
                val (from, to) = when {
                    event.isShiftClick -> null to null
                    event.isRightClick -> Instant.now().minus(7, ChronoUnit.DAYS) to Instant.now()
                    else -> Instant.now().minus(24, ChronoUnit.HOURS) to Instant.now()
                }
                AuditLogGUI(plugin, filterAction, filterPlayer, filterPlayerName, from, to, 0).open(player)
            }
            .build()
        )
    }

    private fun getDateFilterDescription(): String {
        return when {
            filterFrom == null && filterTo == null -> "Current: All time"
            filterFrom != null && filterTo != null -> {
                val fromStr = FULL_DATE_FORMATTER.format(filterFrom)
                val toStr = FULL_DATE_FORMATTER.format(filterTo)
                "Current: $fromStr to $toStr"
            }
            else -> "Current: Custom"
        }
    }

    private fun setupEntryItems() {
        val startIndex = currentPage * ENTRIES_PER_PAGE
        val pageEntries = entries.drop(startIndex).take(ENTRIES_PER_PAGE)

        var slot = 10 // Start at row 1, col 1
        for (entry in pageEntries) {
            // Skip border slots
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++
            }
            if (slot >= 44) break

            setItem(slot, createEntryItem(entry))
            slot++
        }
    }

    private fun createEntryItem(entry: AuditEntry): GUIItem {
        val material = getActionMaterial(entry.action)
        val color = getActionColor(entry.action)

        return GUIItemBuilder()
            .material(material)
            .name(Component.text(entry.action.name, color).decoration(TextDecoration.ITALIC, false))
            .lore(Component.text(DATE_FORMATTER.format(entry.timestamp), NamedTextColor.GRAY))
            .lore(Component.text("Actor: ${entry.actorName}", NamedTextColor.WHITE))
            .lore(Component.text("Target: ${entry.targetName}", NamedTextColor.WHITE))
            .lore(Component.empty())
            .lore(Component.text("Click for details", NamedTextColor.YELLOW))
            .onClick { event ->
                val player = event.whoClicked as Player
                showEntryDetails(player, entry)
            }
            .build()
    }

    private fun getActionMaterial(action: AuditAction): Material {
        return when (action) {
            AuditAction.INVENTORY_SAVE -> Material.CHEST
            AuditAction.INVENTORY_LOAD -> Material.ENDER_CHEST
            AuditAction.INVENTORY_CLEAR -> Material.LAVA_BUCKET
            AuditAction.ITEM_ADD -> Material.EMERALD
            AuditAction.ITEM_REMOVE -> Material.REDSTONE
            AuditAction.ITEM_MODIFY -> Material.ANVIL
            AuditAction.VERSION_RESTORE -> Material.CLOCK
            AuditAction.DEATH_RESTORE -> Material.TOTEM_OF_UNDYING
            AuditAction.TEMPLATE_APPLY -> Material.WRITABLE_BOOK
            AuditAction.BULK_OPERATION, AuditAction.BULK_CLEAR, AuditAction.BULK_EXPORT, AuditAction.BULK_RESET_STATS -> Material.COMMAND_BLOCK
            AuditAction.LOCK_APPLY -> Material.IRON_DOOR
            AuditAction.LOCK_REMOVE -> Material.OAK_DOOR
            AuditAction.ADMIN_EDIT -> Material.GOLDEN_PICKAXE
            AuditAction.ADMIN_VIEW -> Material.ENDER_EYE
            AuditAction.GROUP_CHANGE -> Material.END_PORTAL_FRAME
        }
    }

    private fun getActionColor(action: AuditAction): NamedTextColor {
        return when {
            action.isDestructive -> NamedTextColor.RED
            action.name.startsWith("ADMIN") -> NamedTextColor.GOLD
            action.name.startsWith("LOCK") -> NamedTextColor.YELLOW
            action.name.startsWith("BULK") -> NamedTextColor.LIGHT_PURPLE
            else -> NamedTextColor.GREEN
        }
    }

    private fun showEntryDetails(player: Player, entry: AuditEntry) {
        player.sendMessage(Component.text("═══════════════════════════════════════", NamedTextColor.DARK_PURPLE))
        player.sendMessage(Component.text("Audit Entry #${entry.id}", NamedTextColor.LIGHT_PURPLE))
        player.sendMessage(Component.text("═══════════════════════════════════════", NamedTextColor.DARK_PURPLE))
        player.sendMessage(Component.text("Timestamp: ", NamedTextColor.GRAY)
            .append(Component.text(FULL_DATE_FORMATTER.format(entry.timestamp), NamedTextColor.WHITE)))
        player.sendMessage(Component.text("Action: ", NamedTextColor.GRAY)
            .append(Component.text(entry.action.name, getActionColor(entry.action))))
        player.sendMessage(Component.text("Actor: ", NamedTextColor.GRAY)
            .append(Component.text(entry.actorName, NamedTextColor.WHITE)))
        if (entry.actor != null) {
            player.sendMessage(Component.text("Actor UUID: ", NamedTextColor.GRAY)
                .append(Component.text(entry.actor.toString(), NamedTextColor.DARK_GRAY)))
        }
        player.sendMessage(Component.text("Target: ", NamedTextColor.GRAY)
            .append(Component.text(entry.targetName, NamedTextColor.WHITE)))
        player.sendMessage(Component.text("Target UUID: ", NamedTextColor.GRAY)
            .append(Component.text(entry.target.toString(), NamedTextColor.DARK_GRAY)))
        if (entry.group != null) {
            player.sendMessage(Component.text("Group: ", NamedTextColor.GRAY)
                .append(Component.text(entry.group, NamedTextColor.AQUA)))
        }
        if (entry.details != null) {
            player.sendMessage(Component.text("Details: ", NamedTextColor.GRAY)
                .append(Component.text(entry.details, NamedTextColor.WHITE)))
        }
        if (entry.serverId != null) {
            player.sendMessage(Component.text("Server: ", NamedTextColor.GRAY)
                .append(Component.text(entry.serverId, NamedTextColor.DARK_GRAY)))
        }
        player.sendMessage(Component.text("═══════════════════════════════════════", NamedTextColor.DARK_PURPLE))
    }

    private fun setupNavigation() {
        // Back button
        setItem(5, 0, GUIComponents.backButton {
            // No parent GUI - just close
        })

        // Export button
        setItem(5, 2, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Export to File", NamedTextColor.GREEN)
            .lore("Export ${entries.size} entries to CSV")
            .lore("")
            .lore("Click to export")
            .onClick { event ->
                val player = event.whoClicked as Player
                exportToFile(player)
            }
            .build()
        )

        // Page info
        setItem(5, 4, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Page ${currentPage + 1} of $totalPages", NamedTextColor.WHITE)
            .lore("${entries.size} total entries")
            .build()
        )

        // Previous page
        if (currentPage > 0) {
            setItem(5, 3, GUIComponents.previousPageButton(currentPage + 1) {
                // Navigate handled in onClick
            }.let { item ->
                GUIItem(item.itemStack) { event ->
                    val player = event.whoClicked as Player
                    AuditLogGUI(plugin, filterAction, filterPlayer, filterPlayerName, filterFrom, filterTo, currentPage - 1).open(player)
                }
            })
        }

        // Next page
        if (currentPage < totalPages - 1) {
            setItem(5, 5, GUIComponents.nextPageButton(currentPage + 1) {
                // Navigate handled in onClick
            }.let { item ->
                GUIItem(item.itemStack) { event ->
                    val player = event.whoClicked as Player
                    AuditLogGUI(plugin, filterAction, filterPlayer, filterPlayerName, filterFrom, filterTo, currentPage + 1).open(player)
                }
            })
        }

        // Close button
        setItem(5, 8, GUIComponents.closeButton())
    }

    private fun exportToFile(player: Player) {
        val auditService = plugin.serviceManager.auditService
        if (auditService == null) {
            player.sendMessage(Component.text("Audit service not available.", NamedTextColor.RED))
            return
        }

        plugin.scope.launch {
            val timestamp = System.currentTimeMillis()
            val exportDir = File(plugin.dataFolder, "exports")
            exportDir.mkdirs()
            val file = File(exportDir, "audit_export_$timestamp.csv")

            val success = auditService.exportToCsv(entries, file)
            if (success) {
                player.sendMessage(Component.text("Exported ${entries.size} entries to: ", NamedTextColor.GREEN)
                    .append(Component.text(file.name, NamedTextColor.YELLOW)))
            } else {
                player.sendMessage(Component.text("Failed to export audit log.", NamedTextColor.RED))
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

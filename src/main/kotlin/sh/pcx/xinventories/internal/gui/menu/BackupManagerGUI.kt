package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.model.BackupMetadata
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.util.toReadableSize
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Backup manager GUI for viewing and managing backups.
 *
 * Features:
 * - List all backups (newest first)
 * - Show: date, size, player count
 * - Create new backup button
 * - Restore backup (with confirmation)
 * - Delete old backups
 * - Pagination
 */
class BackupManagerGUI(
    plugin: PluginContext,
    private var currentPage: Int = 0,
    private val confirmingRestore: String? = null,
    private val confirmingDelete: String? = null
) : AbstractGUI(
    plugin,
    Component.text("Backup Manager", NamedTextColor.BLUE),
    54
) {
    companion object {
        private const val BACKUPS_PER_PAGE = 21
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault())
    }

    private var backups: List<BackupMetadata> = emptyList()
    private var totalPages: Int = 1

    init {
        loadBackups()
    }

    private fun loadBackups() {
        plugin.scope.launch {
            val backupService = plugin.serviceManager.backupService
            backups = backupService.listBackups()

            totalPages = maxOf(1, (backups.size + BACKUPS_PER_PAGE - 1) / BACKUPS_PER_PAGE)
            if (currentPage >= totalPages) currentPage = totalPages - 1

            setupItems()
        }
    }

    private fun setupItems() {
        items.clear()
        fillBorder(GUIComponents.filler())

        // Top row - actions
        setupActionButtons()

        // Main content - backup list
        if (confirmingRestore != null) {
            setupRestoreConfirmation()
        } else if (confirmingDelete != null) {
            setupDeleteConfirmation()
        } else {
            setupBackupList()
        }

        // Bottom row - navigation
        setupNavigation()
    }

    private fun setupActionButtons() {
        // Create backup button
        setItem(0, 2, GUIItemBuilder()
            .material(Material.EMERALD)
            .name("Create New Backup", NamedTextColor.GREEN)
            .lore("Click to create a backup now")
            .lore("")
            .lore("This will save all player data")
            .lore("to a compressed file.")
            .onClick { event ->
                val player = event.whoClicked as Player
                createBackup(player)
            }
            .build()
        )

        // Storage info
        val totalSize = backups.sumOf { it.sizeBytes }
        setItem(0, 4, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Backup Statistics", NamedTextColor.AQUA)
            .lore(Component.text("Total Backups: ${backups.size}", NamedTextColor.GRAY))
            .lore(Component.text("Total Size: ${totalSize.toReadableSize()}", NamedTextColor.GRAY))
            .lore(Component.empty())
            .lore(Component.text("Auto-backup: ${if (plugin.configManager.mainConfig.backup.autoBackup) "Enabled" else "Disabled"}",
                if (plugin.configManager.mainConfig.backup.autoBackup) NamedTextColor.GREEN else NamedTextColor.GRAY))
            .build()
        )

        // Cleanup button
        setItem(0, 6, GUIItemBuilder()
            .material(Material.LAVA_BUCKET)
            .name("Cleanup Old Backups", NamedTextColor.RED)
            .lore("Remove backups exceeding limit")
            .lore("")
            .lore("Max backups: ${plugin.configManager.mainConfig.backup.maxBackups}")
            .onClick { event ->
                val player = event.whoClicked as Player
                player.sendMessage(Component.text("Backup cleanup is handled automatically.", NamedTextColor.YELLOW))
            }
            .build()
        )
    }

    private fun setupBackupList() {
        val startIndex = currentPage * BACKUPS_PER_PAGE
        val pageBackups = backups.drop(startIndex).take(BACKUPS_PER_PAGE)

        var slot = 10
        for (backup in pageBackups) {
            if (slot >= 44) break
            // Skip border slots
            if (slot % 9 == 0 || slot % 9 == 8) {
                slot++
                continue
            }

            setItem(slot, createBackupItem(backup))
            slot++
        }

        if (backups.isEmpty()) {
            setItem(22, GUIItemBuilder()
                .material(Material.GRAY_STAINED_GLASS_PANE)
                .name("No Backups Found", NamedTextColor.GRAY)
                .lore("Click 'Create New Backup' to get started")
                .build()
            )
        }
    }

    private fun createBackupItem(backup: BackupMetadata): GUIItem {
        val material = if (backup.compressed) Material.CHEST_MINECART else Material.CHEST
        val dateStr = DATE_FORMATTER.format(backup.timestamp)

        return GUIItemBuilder()
            .material(material)
            .name(Component.text(backup.name, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            .lore(Component.text("Date: $dateStr", NamedTextColor.GRAY))
            .lore(Component.text("Size: ${backup.sizeBytes.toReadableSize()}", NamedTextColor.GRAY))
            .lore(Component.text("Players: ${if (backup.playerCount >= 0) backup.playerCount else "Unknown"}", NamedTextColor.GRAY))
            .lore(Component.text("Compressed: ${if (backup.compressed) "Yes" else "No"}", NamedTextColor.GRAY))
            .lore(Component.empty())
            .lore(Component.text("Left-click: Restore", NamedTextColor.GREEN))
            .lore(Component.text("Right-click: Delete", NamedTextColor.RED))
            .onClick { event ->
                val player = event.whoClicked as Player
                if (event.isRightClick) {
                    BackupManagerGUI(plugin, currentPage, null, backup.id).open(player)
                } else {
                    BackupManagerGUI(plugin, currentPage, backup.id, null).open(player)
                }
            }
            .build()
    }

    private fun setupRestoreConfirmation() {
        val backup = backups.find { it.id == confirmingRestore }
        if (backup == null) {
            setupBackupList()
            return
        }

        // Warning message
        setItem(2, 4, GUIItemBuilder()
            .material(Material.NETHER_STAR)
            .name("Restore Backup?", NamedTextColor.GOLD)
            .lore(Component.text("Backup: ${backup.name}", NamedTextColor.WHITE))
            .lore(Component.text("Date: ${DATE_FORMATTER.format(backup.timestamp)}", NamedTextColor.GRAY))
            .lore(Component.empty())
            .lore(Component.text("WARNING: This will overwrite", NamedTextColor.RED))
            .lore(Component.text("current player data!", NamedTextColor.RED))
            .build()
        )

        // Confirm button
        setItem(3, 2, GUIComponents.confirmButton {
            // Handled in custom onClick
        }.let { item ->
            GUIItem(item.itemStack) { event ->
                val player = event.whoClicked as Player
                confirmingRestore?.let { backupId -> restoreBackup(player, backupId) }
            }
        })

        // Cancel button
        setItem(3, 6, GUIComponents.cancelButton {
            // Handled in custom onClick
        }.let { item ->
            GUIItem(item.itemStack) { event ->
                val player = event.whoClicked as Player
                BackupManagerGUI(plugin, currentPage).open(player)
            }
        })
    }

    private fun setupDeleteConfirmation() {
        val backup = backups.find { it.id == confirmingDelete }
        if (backup == null) {
            setupBackupList()
            return
        }

        // Warning message
        setItem(2, 4, GUIItemBuilder()
            .material(Material.TNT)
            .name("Delete Backup?", NamedTextColor.RED)
            .lore(Component.text("Backup: ${backup.name}", NamedTextColor.WHITE))
            .lore(Component.text("Date: ${DATE_FORMATTER.format(backup.timestamp)}", NamedTextColor.GRAY))
            .lore(Component.empty())
            .lore(Component.text("This action cannot be undone!", NamedTextColor.RED))
            .build()
        )

        // Confirm button
        setItem(3, 2, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Delete", NamedTextColor.RED)
            .lore("Click to permanently delete")
            .onClick { event ->
                val player = event.whoClicked as Player
                confirmingDelete?.let { backupId -> deleteBackup(player, backupId) }
            }
            .build()
        )

        // Cancel button
        setItem(3, 6, GUIComponents.cancelButton {
            // Handled below
        }.let { item ->
            GUIItem(item.itemStack) { event ->
                val player = event.whoClicked as Player
                BackupManagerGUI(plugin, currentPage).open(player)
            }
        })
    }

    private fun setupNavigation() {
        // Page info
        setItem(5, 4, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Page ${currentPage + 1} of $totalPages", NamedTextColor.WHITE)
            .lore("${backups.size} total backups")
            .build()
        )

        // Previous page
        if (currentPage > 0 && confirmingRestore == null && confirmingDelete == null) {
            setItem(5, 3, GUIComponents.previousPageButton(currentPage + 1) {
                // Handled below
            }.let { item ->
                GUIItem(item.itemStack) { event ->
                    val player = event.whoClicked as Player
                    BackupManagerGUI(plugin, currentPage - 1).open(player)
                }
            })
        }

        // Next page
        if (currentPage < totalPages - 1 && confirmingRestore == null && confirmingDelete == null) {
            setItem(5, 5, GUIComponents.nextPageButton(currentPage + 1) {
                // Handled below
            }.let { item ->
                GUIItem(item.itemStack) { event ->
                    val player = event.whoClicked as Player
                    BackupManagerGUI(plugin, currentPage + 1).open(player)
                }
            })
        }

        // Close button
        setItem(5, 8, GUIComponents.closeButton())
    }

    private fun createBackup(player: Player) {
        player.sendMessage(Component.text("Creating backup...", NamedTextColor.YELLOW))

        plugin.scope.launch {
            val backupService = plugin.serviceManager.backupService
            val result = backupService.createBackup()

            result.onSuccess { metadata ->
                player.sendMessage(Component.text("Backup created: ", NamedTextColor.GREEN)
                    .append(Component.text(metadata.name, NamedTextColor.YELLOW)))
                BackupManagerGUI(plugin, 0).open(player)
            }

            result.onFailure { error ->
                player.sendMessage(Component.text("Failed to create backup: ${error.message}", NamedTextColor.RED))
            }
        }
    }

    private fun restoreBackup(player: Player, backupId: String) {
        player.sendMessage(Component.text("Restoring backup...", NamedTextColor.YELLOW))

        plugin.scope.launch {
            val backupService = plugin.serviceManager.backupService
            val result = backupService.restoreBackup(backupId)

            result.onSuccess {
                player.sendMessage(Component.text("Backup restored successfully!", NamedTextColor.GREEN))
                BackupManagerGUI(plugin, currentPage).open(player)
            }

            result.onFailure { error ->
                player.sendMessage(Component.text("Failed to restore backup: ${error.message}", NamedTextColor.RED))
            }
        }
    }

    private fun deleteBackup(player: Player, backupId: String) {
        plugin.scope.launch {
            val backupService = plugin.serviceManager.backupService
            val result = backupService.deleteBackup(backupId)

            result.onSuccess {
                player.sendMessage(Component.text("Backup deleted.", NamedTextColor.GREEN))
                BackupManagerGUI(plugin, currentPage).open(player)
            }

            result.onFailure { error ->
                player.sendMessage(Component.text("Failed to delete backup: ${error.message}", NamedTextColor.RED))
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

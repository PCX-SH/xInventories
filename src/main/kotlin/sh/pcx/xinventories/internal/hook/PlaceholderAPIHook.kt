package sh.pcx.xinventories.internal.hook

import kotlinx.coroutines.runBlocking
import sh.pcx.xinventories.PluginContext
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * PlaceholderAPI expansion for xInventories.
 *
 * Placeholders:
 * - %xinventories_group% - Current group name
 * - %xinventories_group_display% - Current group display name (with formatting)
 * - %xinventories_bypass% - Whether player has bypass enabled
 * - %xinventories_groups_count% - Total number of groups
 * - %xinventories_storage_type% - Current storage backend type
 * - %xinventories_cache_size% - Current cache size
 * - %xinventories_cache_max% - Maximum cache size
 * - %xinventories_cache_hit_rate% - Cache hit rate percentage
 * - %xinventories_version% - Plugin version
 * - %xinventories_item_count% - Total items in inventory
 * - %xinventories_empty_slots% - Empty inventory slots
 * - %xinventories_armor_count% - Equipped armor pieces
 * - %xinventories_version_count% - Number of saved versions
 * - %xinventories_death_count% - Number of death records
 * - %xinventories_balance% - Per-group economy balance
 * - %xinventories_last_save% - Last save timestamp
 * - %xinventories_locked% - "true" or "false"
 * - %xinventories_lock_reason% - Lock reason if locked
 */
class PlaceholderAPIHook(private val plugin: PluginContext) : PlaceholderExpansion() {

    companion object {
        private val TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault())
    }

    override fun getIdentifier(): String = "xinventories"

    override fun getAuthor(): String = plugin.plugin.description.authors.joinToString(", ")

    @Suppress("DEPRECATION")
    override fun getVersion(): String = plugin.plugin.description.version

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val online = player?.player

        return when (params.lowercase()) {
            // =========================================================================
            // Group Information
            // =========================================================================

            "group" -> {
                if (online == null) return "N/A"
                plugin.serviceManager.inventoryService.getCurrentGroup(online)
                    ?: plugin.serviceManager.groupService.getGroupForWorld(online.world).name
            }

            "group_display" -> {
                if (online == null) return "N/A"
                val groupName = plugin.serviceManager.inventoryService.getCurrentGroup(online)
                    ?: plugin.serviceManager.groupService.getGroupForWorld(online.world).name

                // Get the group and return its display name (capitalized)
                val group = plugin.serviceManager.groupService.getGroup(groupName)
                group?.name?.replaceFirstChar { it.uppercase() } ?: groupName
            }

            "groups_count" -> {
                plugin.serviceManager.groupService.getAllGroups().size.toString()
            }

            // =========================================================================
            // Player Status
            // =========================================================================

            "bypass" -> {
                if (online == null) return "false"
                plugin.serviceManager.inventoryService.hasBypass(online).toString()
            }

            "locked" -> {
                if (player == null) return "false"
                plugin.serviceManager.lockingService.isLocked(player.uniqueId).toString()
            }

            "lock_reason" -> {
                if (player == null) return ""
                plugin.serviceManager.lockingService.getLock(player.uniqueId)?.reason ?: ""
            }

            // =========================================================================
            // Inventory Statistics
            // =========================================================================

            "item_count" -> {
                if (online == null) return "0"
                countInventoryItems(online)
            }

            "empty_slots" -> {
                if (online == null) return "0"
                countEmptySlots(online)
            }

            "armor_count" -> {
                if (online == null) return "0"
                countArmorPieces(online)
            }

            // =========================================================================
            // Version & Death Records
            // =========================================================================

            "version_count" -> {
                if (player == null) return "0"
                getVersionCount(player)
            }

            "death_count" -> {
                if (player == null) return "0"
                getDeathCount(player)
            }

            // =========================================================================
            // Economy
            // =========================================================================

            "balance" -> {
                if (online == null) return "0.00"
                getGroupBalance(online)
            }

            // =========================================================================
            // Timestamps
            // =========================================================================

            "last_save" -> {
                if (online == null) return "N/A"
                getLastSaveTimestamp(online)
            }

            // =========================================================================
            // Storage & Cache
            // =========================================================================

            "storage_type" -> {
                plugin.configManager.mainConfig.storage.type.name.lowercase()
            }

            "cache_size" -> {
                plugin.serviceManager.storageService.getCacheStats().size.toString()
            }

            "cache_max" -> {
                plugin.serviceManager.storageService.getCacheStats().maxSize.toString()
            }

            "cache_hit_rate" -> {
                val rate = plugin.serviceManager.storageService.getCacheStats().hitRate
                String.format("%.1f%%", rate * 100)
            }

            // =========================================================================
            // Plugin Info
            // =========================================================================

            "version" -> {
                @Suppress("DEPRECATION")
                plugin.plugin.description.version
            }

            else -> null
        }
    }

    /**
     * Counts the total number of non-empty items in a player's inventory.
     */
    private fun countInventoryItems(player: org.bukkit.entity.Player): String {
        var count = 0

        // Main inventory (slots 0-35)
        for (i in 0..35) {
            val item = player.inventory.getItem(i)
            if (item != null && !item.type.isAir) {
                count++
            }
        }

        // Offhand
        val offhand = player.inventory.itemInOffHand
        if (!offhand.type.isAir) {
            count++
        }

        return count.toString()
    }

    /**
     * Counts the number of empty slots in a player's inventory.
     */
    private fun countEmptySlots(player: org.bukkit.entity.Player): String {
        var emptyCount = 0

        // Main inventory (slots 0-35)
        for (i in 0..35) {
            val item = player.inventory.getItem(i)
            if (item == null || item.type.isAir) {
                emptyCount++
            }
        }

        return emptyCount.toString()
    }

    /**
     * Counts the number of equipped armor pieces.
     */
    private fun countArmorPieces(player: org.bukkit.entity.Player): String {
        var count = 0

        player.inventory.armorContents.forEach { item ->
            if (item != null && !item.type.isAir) {
                count++
            }
        }

        return count.toString()
    }

    /**
     * Gets the number of saved versions for a player.
     */
    private fun getVersionCount(player: OfflinePlayer): String {
        return try {
            runBlocking {
                plugin.serviceManager.versioningService.getVersionCount(player.uniqueId).toString()
            }
        } catch (e: Exception) {
            "0"
        }
    }

    /**
     * Gets the number of death records for a player.
     */
    private fun getDeathCount(player: OfflinePlayer): String {
        return try {
            runBlocking {
                plugin.serviceManager.deathRecoveryService.getDeathRecordCount(player.uniqueId).toString()
            }
        } catch (e: Exception) {
            "0"
        }
    }

    /**
     * Gets the player's balance for their current group.
     */
    private fun getGroupBalance(player: org.bukkit.entity.Player): String {
        return try {
            val balance = plugin.serviceManager.economyService.getBalance(player)
            String.format("%.2f", balance)
        } catch (e: Exception) {
            "0.00"
        }
    }

    /**
     * Gets the timestamp of the player's last inventory save.
     */
    private fun getLastSaveTimestamp(player: org.bukkit.entity.Player): String {
        return try {
            val snapshot = plugin.serviceManager.inventoryService.getActiveSnapshot(player)
            if (snapshot != null) {
                TIMESTAMP_FORMATTER.format(snapshot.timestamp)
            } else {
                "N/A"
            }
        } catch (e: Exception) {
            "N/A"
        }
    }
}

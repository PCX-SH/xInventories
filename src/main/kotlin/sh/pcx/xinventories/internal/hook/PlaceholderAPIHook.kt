package sh.pcx.xinventories.internal.hook

import sh.pcx.xinventories.XInventories
import me.clip.placeholderapi.expansion.PlaceholderExpansion
import org.bukkit.OfflinePlayer

/**
 * PlaceholderAPI expansion for xInventories.
 *
 * Placeholders:
 * - %xinventories_group% - Current group name
 * - %xinventories_bypass% - Whether player has bypass enabled
 * - %xinventories_groups_count% - Total number of groups
 * - %xinventories_storage_type% - Current storage backend type
 * - %xinventories_cache_size% - Current cache size
 * - %xinventories_cache_hit_rate% - Cache hit rate percentage
 */
class PlaceholderAPIHook(private val plugin: XInventories) : PlaceholderExpansion() {

    override fun getIdentifier(): String = "xinventories"

    override fun getAuthor(): String = plugin.description.authors.joinToString(", ")

    @Suppress("DEPRECATION")
    override fun getVersion(): String = plugin.description.version

    override fun persist(): Boolean = true

    override fun canRegister(): Boolean = true

    override fun onRequest(player: OfflinePlayer?, params: String): String? {
        val online = player?.player

        return when (params.lowercase()) {
            "group" -> {
                if (online == null) return "N/A"
                plugin.serviceManager.inventoryService.getCurrentGroup(online)
                    ?: plugin.serviceManager.groupService.getGroupForWorld(online.world).name
            }

            "bypass" -> {
                if (online == null) return "false"
                plugin.serviceManager.inventoryService.hasBypass(online).toString()
            }

            "groups_count" -> {
                plugin.serviceManager.groupService.getAllGroups().size.toString()
            }

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

            "version" -> {
                @Suppress("DEPRECATION")
                plugin.description.version
            }

            else -> null
        }
    }
}

package sh.pcx.xinventories.internal.listener

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent

/**
 * Handles inventory-related events like death.
 */
class InventoryListener(private val plugin: XInventories) : Listener {

    private val inventoryService get() = plugin.serviceManager.inventoryService
    private val groupService get() = plugin.serviceManager.groupService
    private val config get() = plugin.configManager.mainConfig

    /**
     * Handles player death for clear-on-death functionality.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerDeath(event: PlayerDeathEvent) {
        val player = event.entity

        // Check if clear on death is enabled globally
        if (!config.features.clearOnDeath) {
            // Check group-specific setting
            val group = groupService.getGroupForWorld(player.world)
            if (!group.settings.clearOnDeath) return
        }

        Logging.debug { "Player ${player.name} died, clear-on-death is enabled" }

        // The game already handles dropping items on death
        // We just need to clear the stored inventory if configured
        val group = groupService.getGroupForWorld(player.world)

        if (group.settings.clearOnDeath) {
            plugin.launch {
                try {
                    // Clear the player's stored inventory for this group
                    val gameMode = if (group.settings.separateGameModeInventories) {
                        player.gameMode
                    } else {
                        null
                    }

                    inventoryService.clearPlayerData(player.uniqueId, group.name, gameMode)
                    Logging.debug { "Cleared stored inventory for ${player.name} on death" }
                } catch (e: Exception) {
                    Logging.error("Failed to clear inventory on death for ${player.name}", e)
                }
            }
        }
    }

    /**
     * Handles player respawn.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onPlayerRespawn(event: PlayerRespawnEvent) {
        val player = event.player

        // Check if respawn world is different from death world
        // This is handled by WorldChangeListener if applicable

        Logging.debug { "Player ${player.name} respawning in ${event.respawnLocation.world?.name}" }

        // If the respawn location is in a different group, the world change listener will handle it
        // Otherwise, the player keeps their (now empty due to death) inventory
    }
}

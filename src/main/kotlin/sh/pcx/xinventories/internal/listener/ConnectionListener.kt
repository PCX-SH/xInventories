package sh.pcx.xinventories.internal.listener

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Handles player connection events (join/quit).
 */
class ConnectionListener(private val plugin: XInventories) : Listener {

    private val inventoryService get() = plugin.serviceManager.inventoryService

    /**
     * Handles player join - loads their inventory for the current world.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player

        Logging.debug { "Player ${player.name} joining, loading inventory..." }

        // Use a coroutine to handle async loading
        plugin.launch {
            try {
                inventoryService.handlePlayerJoin(player)
            } catch (e: Exception) {
                Logging.error("Failed to load inventory for ${player.name} on join", e)
            }
        }
    }

    /**
     * Handles player quit - saves their inventory.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player

        Logging.debug { "Player ${player.name} quitting, saving inventory..." }

        // Use a coroutine to handle async saving
        plugin.launch {
            try {
                inventoryService.handlePlayerQuit(player)
            } catch (e: Exception) {
                Logging.error("Failed to save inventory for ${player.name} on quit", e)
            }
        }
    }
}

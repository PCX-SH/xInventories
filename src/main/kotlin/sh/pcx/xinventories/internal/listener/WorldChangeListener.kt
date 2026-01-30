package sh.pcx.xinventories.internal.listener

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerTeleportEvent

/**
 * Handles world change events for inventory switching.
 */
class WorldChangeListener(private val plugin: PluginContext) : Listener {

    private val inventoryService get() = plugin.serviceManager.inventoryService
    private val config get() = plugin.configManager.mainConfig

    /**
     * Handles world change after teleportation completes.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onWorldChange(event: PlayerChangedWorldEvent) {
        val player = event.player
        val fromWorld = event.from.name
        val toWorld = player.world.name

        // Same world (shouldn't happen, but check anyway)
        if (fromWorld == toWorld) return

        Logging.debug { "Player ${player.name} changed world: $fromWorld -> $toWorld" }

        // Delay the world change handling slightly to ensure player is fully in the new world
        val delayTicks = config.performance.saveDelayTicks.toLong()

        if (delayTicks > 0) {
            plugin.plugin.server.scheduler.runTaskLater(plugin.plugin, Runnable {
                handleWorldChange(player.uniqueId, fromWorld, toWorld)
            }, delayTicks)
        } else {
            handleWorldChange(player.uniqueId, fromWorld, toWorld)
        }
    }

    private fun handleWorldChange(playerUuid: java.util.UUID, fromWorld: String, toWorld: String) {
        val player = plugin.plugin.server.getPlayer(playerUuid) ?: return

        plugin.launch {
            try {
                inventoryService.handleWorldChange(player, fromWorld, toWorld)
            } catch (e: Exception) {
                Logging.error("Failed to handle world change for ${player.name}", e)
            }
        }
    }

    /**
     * Optional: Pre-teleport handling for cross-world teleports.
     * This can be used to save inventory before teleport if needed.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(event: PlayerTeleportEvent) {
        val player = event.player
        val from = event.from
        val to = event.to

        // Only handle cross-world teleports
        if (from.world == to.world) return

        // The actual inventory switch is handled by onWorldChange
        // This event can be used for pre-processing if needed

        Logging.debug { "Player ${player.name} teleporting from ${from.world?.name} to ${to.world?.name}" }
    }
}

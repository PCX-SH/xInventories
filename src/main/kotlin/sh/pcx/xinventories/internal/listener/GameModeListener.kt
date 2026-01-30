package sh.pcx.xinventories.internal.listener

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerGameModeChangeEvent

/**
 * Handles gamemode change events for separate gamemode inventories.
 */
class GameModeListener(private val plugin: PluginContext) : Listener {

    private val inventoryService get() = plugin.serviceManager.inventoryService
    private val groupService get() = plugin.serviceManager.groupService
    private val config get() = plugin.configManager.mainConfig

    /**
     * Handles gamemode changes.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onGameModeChange(event: PlayerGameModeChangeEvent) {
        val player = event.player
        val oldGameMode = player.gameMode
        val newGameMode = event.newGameMode

        // Same gamemode (shouldn't happen, but check anyway)
        if (oldGameMode == newGameMode) return

        // Check if separate gamemode inventories is enabled globally
        if (!config.features.separateGamemodeInventories) return

        // Check if the player's current group has separate gamemode inventories
        val group = groupService.getGroupForWorld(player.world)
        if (!group.settings.separateGameModeInventories) return

        Logging.debug { "Player ${player.name} changing gamemode: $oldGameMode -> $newGameMode" }

        // Handle gamemode change after a tick to ensure the gamemode is fully changed
        plugin.plugin.server.scheduler.runTaskLater(plugin.plugin, Runnable {
            plugin.launch {
                try {
                    inventoryService.handleGameModeChange(player, oldGameMode, newGameMode)
                } catch (e: Exception) {
                    Logging.error("Failed to handle gamemode change for ${player.name}", e)
                }
            }
        }, 1L)
    }
}

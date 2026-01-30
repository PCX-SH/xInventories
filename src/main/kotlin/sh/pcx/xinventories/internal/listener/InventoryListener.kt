package sh.pcx.xinventories.internal.listener

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.launch
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerRespawnEvent
import java.util.UUID

/**
 * Handles inventory-related events like death.
 */
class InventoryListener(private val plugin: PluginContext) : Listener {

    private val inventoryService get() = plugin.serviceManager.inventoryService
    private val groupService get() = plugin.serviceManager.groupService
    private val deathRecoveryService get() = plugin.serviceManager.deathRecoveryService
    private val config get() = plugin.configManager.mainConfig

    /**
     * Handles player death for death recovery and clear-on-death functionality.
     * Uses LOWEST priority to capture inventory BEFORE other plugins modify it.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onPlayerDeathCapture(event: PlayerDeathEvent) {
        val player = event.entity

        // Capture death inventory for death recovery (if enabled)
        if (config.deathRecovery.enabled) {
            captureDeathInventory(player, event)
        }
    }

    /**
     * Handles player death for clear-on-death functionality.
     * Uses MONITOR priority to run after death inventory capture.
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
     * Captures the player's inventory at time of death for death recovery.
     */
    private fun captureDeathInventory(player: Player, event: PlayerDeathEvent) {
        // Get death cause
        val damageCause = player.lastDamageCause?.cause

        // Get killer info
        var killerName: String? = null
        var killerUuid: UUID? = null

        val lastDamage = player.lastDamageCause
        if (lastDamage is EntityDamageByEntityEvent) {
            val damager = lastDamage.damager
            if (damager is Player) {
                killerName = damager.name
                killerUuid = damager.uniqueId
            } else {
                killerName = damager.type.name
            }
        }

        // Capture the inventory asynchronously
        plugin.launch {
            try {
                val deathRecord = deathRecoveryService.captureDeathInventory(
                    player,
                    damageCause,
                    killerName,
                    killerUuid
                )

                if (deathRecord != null) {
                    Logging.debug { "Captured death inventory for ${player.name} (${deathRecord.id})" }
                }
            } catch (e: Exception) {
                Logging.error("Failed to capture death inventory for ${player.name}", e)
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

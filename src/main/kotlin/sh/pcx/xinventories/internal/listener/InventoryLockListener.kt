package sh.pcx.xinventories.internal.listener

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent

/**
 * Listener that prevents inventory modifications for locked players.
 */
class InventoryLockListener(private val plugin: XInventories) : Listener {

    private val lockingService get() = plugin.serviceManager.lockingService
    private val config get() = plugin.configManager.mainConfig.locking

    /**
     * Prevents inventory clicks for locked players.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        if (isLockedAndCannotBypass(player)) {
            event.isCancelled = true
            lockingService.showLockMessage(player)
            Logging.debug { "Blocked inventory click for locked player ${player.name}" }
        }
    }

    /**
     * Prevents inventory drags for locked players.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return

        if (isLockedAndCannotBypass(player)) {
            event.isCancelled = true
            lockingService.showLockMessage(player)
            Logging.debug { "Blocked inventory drag for locked player ${player.name}" }
        }
    }

    /**
     * Prevents item drops for locked players.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerDropItem(event: PlayerDropItemEvent) {
        val player = event.player

        if (isLockedAndCannotBypass(player)) {
            event.isCancelled = true
            lockingService.showLockMessage(player)
            Logging.debug { "Blocked item drop for locked player ${player.name}" }
        }
    }

    /**
     * Prevents item pickups for locked players.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return

        if (isLockedAndCannotBypass(player)) {
            event.isCancelled = true
            // Don't show message for pickup to avoid spam
            Logging.debug { "Blocked item pickup for locked player ${player.name}" }
        }
    }

    /**
     * Prevents hand swapping for locked players.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlayerSwapHandItems(event: PlayerSwapHandItemsEvent) {
        val player = event.player

        if (isLockedAndCannotBypass(player)) {
            event.isCancelled = true
            lockingService.showLockMessage(player)
            Logging.debug { "Blocked hand swap for locked player ${player.name}" }
        }
    }

    /**
     * Checks if a player is locked and cannot bypass the lock.
     */
    private fun isLockedAndCannotBypass(player: Player): Boolean {
        if (!config.enabled) return false
        if (!lockingService.isLocked(player.uniqueId)) return false
        if (lockingService.canBypass(player)) return false
        return true
    }
}

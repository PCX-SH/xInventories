package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.internal.model.InventoryLock
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Called when a player's inventory is about to be locked.
 * Can be cancelled to prevent the lock.
 */
class InventoryLockEvent(
    player: Player,
    /**
     * The lock being applied.
     */
    val lock: InventoryLock
) : PlayerEvent(player), Cancellable {

    private var cancelled = false

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        cancelled = cancel
    }

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }

    override fun getHandlers(): HandlerList = handlerList
}

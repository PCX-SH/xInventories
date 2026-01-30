package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.internal.model.InventoryLock
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Called when a player's inventory is unlocked.
 * This event is not cancellable.
 */
class InventoryUnlockEvent(
    player: Player,
    /**
     * The lock that was removed.
     */
    val lock: InventoryLock,
    /**
     * The reason for the unlock.
     */
    val reason: UnlockReason
) : PlayerEvent(player) {

    /**
     * Reasons why an inventory was unlocked.
     */
    enum class UnlockReason {
        /**
         * Manually unlocked by an admin.
         */
        MANUAL,

        /**
         * Lock expired automatically.
         */
        EXPIRED,

        /**
         * Unlocked by another plugin via API.
         */
        PLUGIN
    }

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }

    override fun getHandlers(): HandlerList = handlerList
}

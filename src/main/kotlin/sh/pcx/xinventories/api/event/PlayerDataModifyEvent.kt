package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Called when player data is modified through the API or GUI.
 */
class PlayerDataModifyEvent(
    player: Player,
    val group: InventoryGroup,
    val oldSnapshot: PlayerInventorySnapshot,
    var newSnapshot: PlayerInventorySnapshot,
    val modifier: String // Plugin/player who made the change
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

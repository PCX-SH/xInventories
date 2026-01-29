package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Called before an inventory is loaded.
 * The snapshot can be modified before it's applied.
 */
class InventoryLoadEvent(
    player: Player,
    val group: InventoryGroup,
    var snapshot: PlayerInventorySnapshot,
    val reason: LoadReason
) : PlayerEvent(player), Cancellable {

    enum class LoadReason {
        WORLD_CHANGE,
        GAMEMODE_CHANGE,
        JOIN,
        COMMAND,
        API
    }

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

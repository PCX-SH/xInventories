package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Called after an inventory is saved.
 */
class InventorySaveEvent(
    player: Player,
    val group: InventoryGroup,
    val snapshot: PlayerInventorySnapshot,
    async: Boolean
) : PlayerEvent(player, async) {

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }

    override fun getHandlers(): HandlerList = handlerList
}

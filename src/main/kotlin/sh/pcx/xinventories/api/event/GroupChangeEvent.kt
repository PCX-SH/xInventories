package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.api.model.InventoryGroup
import org.bukkit.entity.Player
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Called when a player's group changes.
 */
class GroupChangeEvent(
    player: Player,
    val oldGroup: InventoryGroup?,
    val newGroup: InventoryGroup,
    val reason: ChangeReason
) : PlayerEvent(player) {

    enum class ChangeReason {
        WORLD_CHANGE,
        ASSIGNMENT_CHANGE,
        GROUP_DELETED,
        MANUAL
    }

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }

    override fun getHandlers(): HandlerList = handlerList
}

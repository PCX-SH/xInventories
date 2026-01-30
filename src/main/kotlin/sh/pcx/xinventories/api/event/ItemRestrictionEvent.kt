package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.model.RestrictionAction
import sh.pcx.xinventories.internal.model.RestrictionResult
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import org.bukkit.inventory.ItemStack

/**
 * Called when an item is checked against group restrictions.
 *
 * This event fires for each item that matches a restriction pattern.
 * The result can be modified to change how the restriction is handled.
 *
 * Cancelling this event will allow the item through regardless of restrictions.
 */
class ItemRestrictionEvent(
    player: Player,

    /**
     * The group being entered or exited.
     */
    val group: InventoryGroup,

    /**
     * The item being checked.
     */
    val item: ItemStack,

    /**
     * The pattern that matched this item.
     */
    val pattern: String,

    /**
     * Whether the player is entering or exiting the group.
     */
    val action: RestrictionAction,

    /**
     * The initial result of the restriction check.
     */
    initialResult: RestrictionResult
) : PlayerEvent(player), Cancellable {

    private var cancelled = false

    /**
     * The result of the restriction check.
     * Can be modified to change the action taken.
     */
    var result: RestrictionResult = initialResult

    /**
     * The slot index where the item was found.
     * -1 if the item is from armor or offhand.
     */
    var slotIndex: Int = -1

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

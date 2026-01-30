package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.model.InventoryTemplate
import sh.pcx.xinventories.internal.model.TemplateApplyTrigger
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Called when an inventory template is about to be applied to a player.
 *
 * This event is cancellable. If cancelled, the template will not be applied.
 */
class TemplateApplyEvent(
    player: Player,

    /**
     * The group the player is entering.
     */
    val group: InventoryGroup,

    /**
     * The template being applied.
     */
    val template: InventoryTemplate,

    /**
     * The reason/trigger for applying the template.
     */
    val reason: TemplateApplyTrigger
) : PlayerEvent(player), Cancellable {

    private var cancelled = false

    /**
     * Whether to clear the player's inventory before applying the template.
     */
    var clearInventoryFirst: Boolean = true

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

package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

/**
 * Called before an inventory switch occurs.
 * Cancellable - if cancelled, the switch will not happen.
 */
class InventorySwitchEvent(
    player: Player,
    val fromGroup: InventoryGroup,
    val toGroup: InventoryGroup,
    val fromWorld: World,
    val toWorld: World,
    val currentSnapshot: PlayerInventorySnapshot,
    val reason: SwitchReason
) : PlayerEvent(player), Cancellable {

    enum class SwitchReason {
        WORLD_CHANGE,
        GAMEMODE_CHANGE,
        COMMAND,
        API,
        PLUGIN
    }

    private var cancelled = false

    /**
     * Override the snapshot that will be loaded.
     * Set to null to use default behavior.
     */
    var overrideSnapshot: PlayerInventorySnapshot? = null

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

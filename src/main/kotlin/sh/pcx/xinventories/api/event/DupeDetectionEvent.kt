package sh.pcx.xinventories.api.event

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent
import sh.pcx.xinventories.internal.service.DupeDetection

/**
 * Called when a potential item duplication exploit is detected.
 *
 * Cancelling this event will prevent the default handling (logging, admin notification, freezing).
 *
 * @property detection The detection details including type, severity, and description
 */
class DupeDetectionEvent(
    player: Player,
    val detection: DupeDetection
) : PlayerEvent(player), Cancellable {

    private var cancelled = false

    /**
     * Gets the type of dupe detection that was triggered.
     */
    val detectionType get() = detection.type

    /**
     * Gets the severity of this detection.
     */
    val severity get() = detection.severity

    /**
     * Gets the detailed description of what triggered this detection.
     */
    val details get() = detection.details

    override fun isCancelled(): Boolean = cancelled

    override fun setCancelled(cancel: Boolean) {
        this.cancelled = cancel
    }

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }

    override fun getHandlers(): HandlerList = handlerList
}

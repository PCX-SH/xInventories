package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.internal.model.TemporaryGroupAssignment
import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/**
 * Event fired when a temporary group assignment is about to expire.
 *
 * This event is cancellable - if cancelled, the player will remain
 * in their temporary group and the assignment will be extended by
 * a short duration (1 minute by default).
 *
 * @property player The player whose temporary group is expiring
 * @property assignment The temporary group assignment that is expiring
 */
class TemporaryGroupExpireEvent(
    val player: Player,
    val assignment: TemporaryGroupAssignment
) : Event(), Cancellable {

    /**
     * The temporary group name.
     */
    val temporaryGroup: String get() = assignment.temporaryGroup

    /**
     * The original group the player will be returned to.
     */
    val originalGroup: String get() = assignment.originalGroup

    /**
     * Who assigned the temporary group.
     */
    val assignedBy: String get() = assignment.assignedBy

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

/**
 * Event fired when a temporary group is assigned to a player.
 *
 * This event is fired after the assignment is made but before
 * the inventory switch occurs.
 *
 * @property player The player receiving the temporary group
 * @property assignment The new temporary group assignment
 */
class TemporaryGroupAssignEvent(
    val player: Player,
    val assignment: TemporaryGroupAssignment
) : Event(), Cancellable {

    /**
     * The temporary group being assigned.
     */
    val temporaryGroup: String get() = assignment.temporaryGroup

    /**
     * The player's original group.
     */
    val originalGroup: String get() = assignment.originalGroup

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

/**
 * Event fired when a temporary group assignment is removed.
 *
 * This event is informational and cannot be cancelled.
 *
 * @property playerUuid The UUID of the player (may be offline)
 * @property assignment The assignment that was removed
 * @property reason The reason for removal
 */
class TemporaryGroupRemoveEvent(
    val playerUuid: java.util.UUID,
    val assignment: TemporaryGroupAssignment,
    val reason: RemovalReason
) : Event() {

    /**
     * The temporary group that was removed.
     */
    val temporaryGroup: String get() = assignment.temporaryGroup

    /**
     * The player's original group.
     */
    val originalGroup: String get() = assignment.originalGroup

    /**
     * Reasons for temporary group removal.
     */
    enum class RemovalReason {
        /** Assignment expired naturally */
        EXPIRED,
        /** Manually removed by admin */
        MANUAL,
        /** Plugin shutdown/reload */
        PLUGIN_DISABLE,
        /** Player data was deleted */
        DATA_DELETED,
        /** API call removed the assignment */
        API
    }

    companion object {
        @JvmStatic
        private val handlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList = handlerList
    }

    override fun getHandlers(): HandlerList = handlerList
}

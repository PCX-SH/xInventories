package sh.pcx.xinventories.api

/**
 * Represents a subscription to an event.
 * Call [unsubscribe] to stop receiving events.
 */
interface Subscription {
    /**
     * Unique ID for this subscription.
     */
    val id: String

    /**
     * Whether this subscription is active.
     */
    val isActive: Boolean

    /**
     * Unsubscribes from the event.
     */
    fun unsubscribe()
}

package sh.pcx.xinventories.internal.model

/**
 * Defines the scope of an inventory lock.
 */
enum class LockScope {
    /**
     * Locks the player's entire inventory across all groups.
     */
    ALL,

    /**
     * Locks only the player's inventory for their current group.
     */
    GROUP,

    /**
     * Locks specific inventory slots only.
     * Note: Slot-specific locking requires additional slot configuration.
     */
    SLOTS
}

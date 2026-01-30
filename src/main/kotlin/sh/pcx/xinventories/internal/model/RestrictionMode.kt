package sh.pcx.xinventories.internal.model

/**
 * Mode for item restriction enforcement.
 */
enum class RestrictionMode {
    /**
     * Items on the blacklist are restricted, all others allowed.
     */
    BLACKLIST,

    /**
     * Only items on the whitelist are allowed, all others restricted.
     */
    WHITELIST,

    /**
     * No restrictions are applied.
     */
    NONE
}

/**
 * Action to take when a restriction violation is detected.
 */
enum class RestrictionViolationAction {
    /**
     * Remove the violating items from the player's inventory.
     */
    REMOVE,

    /**
     * Prevent the player from entering/exiting the group.
     */
    PREVENT,

    /**
     * Drop the items on the ground.
     */
    DROP,

    /**
     * Move items to a vault for later recovery.
     */
    MOVE_TO_VAULT
}

/**
 * Context of a restriction check (entering or exiting a group).
 */
enum class RestrictionAction {
    /**
     * Player is entering a group.
     */
    ENTERING,

    /**
     * Player is exiting a group.
     */
    EXITING
}

/**
 * Result of a restriction check.
 */
enum class RestrictionResult {
    /**
     * The item is allowed.
     */
    ALLOW,

    /**
     * The item should be removed.
     */
    REMOVE,

    /**
     * The switch should be prevented.
     */
    PREVENT
}

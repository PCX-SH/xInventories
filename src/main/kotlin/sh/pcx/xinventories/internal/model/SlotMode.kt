package sh.pcx.xinventories.internal.model

/**
 * Mode for shared slot behavior.
 */
enum class SlotMode {
    /**
     * Keep whatever is in the slot across group switches.
     * The slot content persists but can be modified by the player.
     */
    PRESERVE,

    /**
     * Keep the slot contents AND prevent player modification.
     * Players cannot add, remove, or modify items in locked slots.
     */
    LOCK,

    /**
     * Keep in sync across all groups.
     * Changes to the slot reflect everywhere immediately.
     */
    SYNC
}

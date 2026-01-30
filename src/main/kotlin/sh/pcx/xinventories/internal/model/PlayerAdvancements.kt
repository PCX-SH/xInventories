package sh.pcx.xinventories.internal.model

/**
 * Represents player advancement/achievement progress for per-group tracking.
 *
 * Advancements are stored as a set of advancement keys (NamespacedKey strings).
 * Only completed advancements are stored.
 *
 * Examples:
 * - "minecraft:story/mine_stone"
 * - "minecraft:nether/return_to_sender"
 * - "minecraft:adventure/kill_a_mob"
 */
data class PlayerAdvancements(
    /**
     * Set of completed advancement keys (NamespacedKey format).
     */
    val completedAdvancements: Set<String> = emptySet()
) {

    /**
     * Checks if a specific advancement is completed.
     *
     * @param advancementKey The advancement key (e.g., "minecraft:story/mine_stone")
     * @return True if the advancement is completed
     */
    fun isCompleted(advancementKey: String): Boolean = completedAdvancements.contains(advancementKey)

    /**
     * Creates a new PlayerAdvancements with an additional completed advancement.
     *
     * @param advancementKey The advancement key to add
     * @return A new PlayerAdvancements instance with the added advancement
     */
    fun withCompleted(advancementKey: String): PlayerAdvancements {
        return copy(completedAdvancements = completedAdvancements + advancementKey)
    }

    /**
     * Creates a new PlayerAdvancements with multiple completed advancements.
     *
     * @param advancementKeys The advancement keys to add
     * @return A new PlayerAdvancements instance with the added advancements
     */
    fun withAllCompleted(advancementKeys: Set<String>): PlayerAdvancements {
        return copy(completedAdvancements = completedAdvancements + advancementKeys)
    }

    /**
     * Creates a new PlayerAdvancements with an advancement removed.
     *
     * @param advancementKey The advancement key to remove
     * @return A new PlayerAdvancements instance without the specified advancement
     */
    fun withoutCompleted(advancementKey: String): PlayerAdvancements {
        return copy(completedAdvancements = completedAdvancements - advancementKey)
    }

    /**
     * Checks if this contains any advancements.
     */
    fun isEmpty(): Boolean = completedAdvancements.isEmpty()

    /**
     * Checks if this contains advancements.
     */
    fun isNotEmpty(): Boolean = completedAdvancements.isNotEmpty()

    /**
     * Returns the number of completed advancements.
     */
    val size: Int get() = completedAdvancements.size

    companion object {
        /**
         * Creates an empty PlayerAdvancements instance.
         */
        fun empty(): PlayerAdvancements = PlayerAdvancements()
    }
}

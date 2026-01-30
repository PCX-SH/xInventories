package sh.pcx.xinventories.internal.model

/**
 * Represents player statistics data for per-group tracking.
 *
 * Statistics are stored as a map of statistic keys to their integer values.
 * The key format is: "STATISTIC_TYPE" or "STATISTIC_TYPE:MATERIAL" or "STATISTIC_TYPE:ENTITY_TYPE"
 *
 * Examples:
 * - "PLAY_ONE_MINUTE" -> total ticks played
 * - "MINE_BLOCK:DIAMOND_ORE" -> diamonds mined
 * - "KILL_ENTITY:ZOMBIE" -> zombies killed
 * - "DEATHS" -> total deaths
 */
data class PlayerStatistics(
    /**
     * Map of statistic keys to their values.
     * Keys follow Bukkit's Statistic enum naming with optional type suffix.
     */
    val statistics: Map<String, Int> = emptyMap()
) {

    /**
     * Gets a specific statistic value.
     *
     * @param key The statistic key
     * @return The statistic value, or 0 if not present
     */
    fun get(key: String): Int = statistics[key] ?: 0

    /**
     * Creates a new PlayerStatistics with an updated value.
     *
     * @param key The statistic key
     * @param value The new value
     * @return A new PlayerStatistics instance with the updated value
     */
    fun with(key: String, value: Int): PlayerStatistics {
        return copy(statistics = statistics + (key to value))
    }

    /**
     * Creates a new PlayerStatistics with multiple updated values.
     *
     * @param updates Map of statistic keys to new values
     * @return A new PlayerStatistics instance with the updated values
     */
    fun withAll(updates: Map<String, Int>): PlayerStatistics {
        return copy(statistics = statistics + updates)
    }

    /**
     * Checks if this contains any statistics.
     */
    fun isEmpty(): Boolean = statistics.isEmpty()

    /**
     * Checks if this contains statistics.
     */
    fun isNotEmpty(): Boolean = statistics.isNotEmpty()

    /**
     * Returns the number of tracked statistics.
     */
    val size: Int get() = statistics.size

    companion object {
        /**
         * Creates an empty PlayerStatistics instance.
         */
        fun empty(): PlayerStatistics = PlayerStatistics()
    }
}

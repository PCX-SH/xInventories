package sh.pcx.xinventories.internal.model

/**
 * Conditions that must be met for a player to be assigned to a group.
 * All non-null conditions must be satisfied based on the [requireAll] setting.
 */
data class GroupConditions(
    /**
     * Permission that the player must have.
     * Example: "xinventories.group.vip"
     */
    val permission: String? = null,

    /**
     * List of time ranges when this group is active.
     * If multiple ranges are specified, the group is active if ANY range matches.
     */
    val schedule: List<TimeRange>? = null,

    /**
     * Cron expression for when this group is active.
     * Format: minute hour day-of-month month day-of-week
     * Example: "0 18-22 * * FRI,SAT" (Fridays and Saturdays 6-10 PM)
     */
    val cron: String? = null,

    /**
     * PlaceholderAPI condition that must be satisfied.
     */
    val placeholder: PlaceholderCondition? = null,

    /**
     * List of PlaceholderAPI conditions (for multiple placeholder checks).
     */
    val placeholders: List<PlaceholderCondition>? = null,

    /**
     * If true, ALL specified conditions must be met (AND logic).
     * If false, ANY condition being met is sufficient (OR logic).
     */
    val requireAll: Boolean = true
) {
    /**
     * Checks if any conditions are defined.
     */
    fun hasConditions(): Boolean {
        return permission != null ||
            !schedule.isNullOrEmpty() ||
            cron != null ||
            placeholder != null ||
            !placeholders.isNullOrEmpty()
    }

    /**
     * Gets all placeholder conditions as a single list.
     */
    fun getAllPlaceholderConditions(): List<PlaceholderCondition> {
        val result = mutableListOf<PlaceholderCondition>()
        placeholder?.let { result.add(it) }
        placeholders?.let { result.addAll(it) }
        return result
    }

    /**
     * Returns a human-readable summary of the conditions.
     */
    fun toDisplayString(): String {
        val parts = mutableListOf<String>()

        permission?.let { parts.add("permission: $it") }
        schedule?.takeIf { it.isNotEmpty() }?.let {
            parts.add("schedule: ${it.size} time range(s)")
        }
        cron?.let { parts.add("cron: $it") }
        placeholder?.let { parts.add("placeholder: ${it.toDisplayString()}") }
        placeholders?.takeIf { it.isNotEmpty() }?.let {
            parts.add("placeholders: ${it.size} condition(s)")
        }

        if (parts.isEmpty()) {
            return "No conditions"
        }

        val logic = if (requireAll) "AND" else "OR"
        return parts.joinToString(" $logic ")
    }

    companion object {
        /**
         * Creates an empty GroupConditions with no requirements.
         */
        fun empty(): GroupConditions = GroupConditions()

        /**
         * Creates a GroupConditions with only a permission requirement.
         */
        fun withPermission(permission: String): GroupConditions {
            return GroupConditions(permission = permission)
        }

        /**
         * Creates a GroupConditions with only a schedule requirement.
         */
        fun withSchedule(vararg ranges: TimeRange): GroupConditions {
            return GroupConditions(schedule = ranges.toList())
        }

        /**
         * Creates a GroupConditions with only a cron requirement.
         */
        fun withCron(cron: String): GroupConditions {
            return GroupConditions(cron = cron)
        }
    }
}

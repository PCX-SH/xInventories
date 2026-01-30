package sh.pcx.xinventories.internal.model

/**
 * Configuration for item restrictions within a group.
 *
 * Item restrictions control which items players can carry into or out of groups.
 * Patterns support various matching syntaxes for materials and enchantments.
 */
data class RestrictionConfig(
    /**
     * The restriction mode (BLACKLIST, WHITELIST, or NONE).
     */
    val mode: RestrictionMode = RestrictionMode.NONE,

    /**
     * Action to take when a violation is detected.
     */
    val onViolation: RestrictionViolationAction = RestrictionViolationAction.REMOVE,

    /**
     * Whether to notify the player of restriction violations.
     */
    val notifyPlayer: Boolean = true,

    /**
     * Whether to notify admins of restriction violations.
     */
    val notifyAdmins: Boolean = true,

    /**
     * Patterns for blacklisted items (used when mode is BLACKLIST).
     *
     * Pattern syntax:
     * - MATERIAL - Exact material match
     * - MATERIAL:* - Material with any variant
     * - MATERIAL:ENCHANT:LEVEL - Material with specific enchant level
     * - MATERIAL:ENCHANT:LEVEL+ - Material with enchant at or above level
     * - *:ENCHANT:LEVEL+ - Any item with enchant restriction
     */
    val blacklist: List<String> = emptyList(),

    /**
     * Patterns for whitelisted items (used when mode is WHITELIST).
     */
    val whitelist: List<String> = emptyList(),

    /**
     * Items to strip when leaving this group (regardless of mode).
     * These items are removed when the player exits the group.
     */
    val stripOnExit: List<String> = emptyList()
) {
    /**
     * Gets the active pattern list based on the current mode.
     */
    fun getActivePatterns(): List<String> = when (mode) {
        RestrictionMode.BLACKLIST -> blacklist
        RestrictionMode.WHITELIST -> whitelist
        RestrictionMode.NONE -> emptyList()
    }

    /**
     * Checks if restrictions are enabled.
     */
    fun isEnabled(): Boolean = mode != RestrictionMode.NONE

    /**
     * Creates a copy with an added blacklist pattern.
     */
    fun withBlacklistPattern(pattern: String): RestrictionConfig {
        return copy(blacklist = blacklist + pattern)
    }

    /**
     * Creates a copy with a removed blacklist pattern.
     */
    fun withoutBlacklistPattern(pattern: String): RestrictionConfig {
        return copy(blacklist = blacklist - pattern)
    }

    /**
     * Creates a copy with an added whitelist pattern.
     */
    fun withWhitelistPattern(pattern: String): RestrictionConfig {
        return copy(whitelist = whitelist + pattern)
    }

    /**
     * Creates a copy with a removed whitelist pattern.
     */
    fun withoutWhitelistPattern(pattern: String): RestrictionConfig {
        return copy(whitelist = whitelist - pattern)
    }

    /**
     * Creates a copy with an added strip-on-exit pattern.
     */
    fun withStripOnExitPattern(pattern: String): RestrictionConfig {
        return copy(stripOnExit = stripOnExit + pattern)
    }

    /**
     * Creates a copy with a removed strip-on-exit pattern.
     */
    fun withoutStripOnExitPattern(pattern: String): RestrictionConfig {
        return copy(stripOnExit = stripOnExit - pattern)
    }

    companion object {
        /**
         * Creates a disabled restriction config.
         */
        fun disabled(): RestrictionConfig = RestrictionConfig()

        /**
         * Creates a blacklist restriction config.
         */
        fun blacklist(vararg patterns: String): RestrictionConfig {
            return RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = patterns.toList()
            )
        }

        /**
         * Creates a whitelist restriction config.
         */
        fun whitelist(vararg patterns: String): RestrictionConfig {
            return RestrictionConfig(
                mode = RestrictionMode.WHITELIST,
                whitelist = patterns.toList()
            )
        }
    }
}

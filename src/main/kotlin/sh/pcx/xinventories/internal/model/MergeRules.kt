package sh.pcx.xinventories.internal.model

/**
 * Configuration for how to merge different aspects of player data.
 * Used when ConflictStrategy is MERGE.
 */
data class MergeRules(
    /**
     * How to merge inventory contents.
     */
    val inventory: MergeRule = MergeRule.NEWER,

    /**
     * How to merge experience/levels.
     */
    val experience: MergeRule = MergeRule.HIGHER,

    /**
     * How to merge health values.
     */
    val health: MergeRule = MergeRule.CURRENT_SERVER,

    /**
     * How to merge potion effects.
     */
    val potionEffects: MergeRule = MergeRule.UNION
) {
    companion object {
        /**
         * Default merge rules that prioritize newer data and higher values.
         */
        val DEFAULT = MergeRules()

        /**
         * Conservative merge rules that prioritize keeping existing data.
         */
        val CONSERVATIVE = MergeRules(
            inventory = MergeRule.OLDER,
            experience = MergeRule.HIGHER,
            health = MergeRule.HIGHER,
            potionEffects = MergeRule.UNION
        )

        /**
         * Aggressive merge rules that always take the newer data.
         */
        val AGGRESSIVE = MergeRules(
            inventory = MergeRule.NEWER,
            experience = MergeRule.NEWER,
            health = MergeRule.NEWER,
            potionEffects = MergeRule.NEWER
        )
    }
}

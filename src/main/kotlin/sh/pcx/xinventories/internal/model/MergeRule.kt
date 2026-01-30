package sh.pcx.xinventories.internal.model

/**
 * Defines how to resolve conflicts when merging player data from multiple sources.
 */
enum class MergeRule {
    /**
     * Use data with the newer timestamp.
     */
    NEWER,

    /**
     * Use data with the older timestamp.
     */
    OLDER,

    /**
     * Use the higher numeric value.
     */
    HIGHER,

    /**
     * Use the lower numeric value.
     */
    LOWER,

    /**
     * Always use the current server's value.
     */
    CURRENT_SERVER,

    /**
     * Combine values (for lists/collections).
     */
    UNION,

    /**
     * Keep only common elements (for lists/collections).
     */
    INTERSECTION
}

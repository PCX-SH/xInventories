package sh.pcx.xinventories.internal.model

/**
 * Strategy for handling conflicts when the same player data is modified on multiple servers.
 */
enum class ConflictStrategy {
    /**
     * The most recent save wins. Simple and fast, but may lose data.
     */
    LAST_WRITE_WINS,

    /**
     * Attempt to intelligently merge data using configured merge rules.
     */
    MERGE,

    /**
     * Alert an admin for manual resolution. Data is held until resolved.
     */
    PROMPT_ADMIN
}

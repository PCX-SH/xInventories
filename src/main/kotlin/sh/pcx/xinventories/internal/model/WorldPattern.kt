package sh.pcx.xinventories.internal.model

/**
 * Represents a compiled regex pattern for world matching.
 */
data class WorldPattern(
    val pattern: String,
    val regex: Regex
) {
    /**
     * Tests if a world name matches this pattern.
     */
    fun matches(worldName: String): Boolean = regex.matches(worldName)

    companion object {
        /**
         * Creates a WorldPattern from a regex string.
         * Returns null if the pattern is invalid.
         */
        fun fromString(pattern: String): WorldPattern? {
            return try {
                WorldPattern(pattern, Regex(pattern))
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Creates a WorldPattern from a regex string, throwing on invalid patterns.
         */
        fun fromStringOrThrow(pattern: String): WorldPattern {
            return fromString(pattern)
                ?: throw IllegalArgumentException("Invalid regex pattern: $pattern")
        }
    }
}

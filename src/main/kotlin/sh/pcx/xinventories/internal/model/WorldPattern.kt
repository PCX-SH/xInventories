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

    /**
     * Equals based on pattern string only (Regex doesn't have value-based equals).
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WorldPattern) return false
        return pattern == other.pattern
    }

    /**
     * HashCode based on pattern string only.
     */
    override fun hashCode(): Int = pattern.hashCode()

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

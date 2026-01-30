package sh.pcx.xinventories.internal.model

/**
 * Represents a group from an import source.
 * Contains the group name and the worlds it covers.
 */
data class ImportGroup(
    /**
     * The name of the group in the source plugin.
     */
    val name: String,

    /**
     * The worlds that belong to this group.
     */
    val worlds: Set<String>,

    /**
     * Whether this is the default group in the source plugin.
     */
    val isDefault: Boolean = false,

    /**
     * Additional metadata from the source plugin.
     */
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Checks if this group contains a specific world.
     */
    fun containsWorld(worldName: String): Boolean = worlds.contains(worldName)

    /**
     * Gets a display string for this group.
     */
    fun toDisplayString(): String = buildString {
        append(name)
        if (isDefault) append(" (default)")
        if (worlds.isNotEmpty()) {
            append(" [")
            append(worlds.joinToString(", "))
            append("]")
        }
    }
}

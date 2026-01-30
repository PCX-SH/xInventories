package sh.pcx.xinventories.internal.model

import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup

/**
 * Internal mutable group representation.
 */
class Group(
    val name: String,
    worlds: Set<String> = emptySet(),
    patterns: List<WorldPattern> = emptyList(),
    var priority: Int = 0,
    var parent: String? = null,
    var settings: GroupSettings = GroupSettings(),
    var isDefault: Boolean = false,
    var conditions: GroupConditions? = null,
    var templateSettings: TemplateSettings? = null,
    var restrictions: RestrictionConfig? = null,
    /** Set of settings field names that were explicitly set in config (for merge inheritance) */
    var explicitSettings: Set<String> = emptySet()
) {
    private val _worlds = worlds.toMutableSet()
    private val _patterns = patterns.toMutableList()

    val worlds: Set<String> get() = _worlds.toSet()
    val patterns: List<WorldPattern> get() = _patterns.toList()
    val patternStrings: List<String> get() = _patterns.map { it.pattern }

    /**
     * Adds a world to this group.
     */
    fun addWorld(world: String): Boolean = _worlds.add(world)

    /**
     * Removes a world from this group.
     */
    fun removeWorld(world: String): Boolean = _worlds.remove(world)

    /**
     * Sets the worlds for this group.
     */
    fun setWorlds(worlds: Set<String>) {
        _worlds.clear()
        _worlds.addAll(worlds)
    }

    /**
     * Adds a pattern to this group.
     * Returns false if the pattern is invalid or already exists.
     */
    fun addPattern(pattern: String): Boolean {
        if (_patterns.any { it.pattern == pattern }) return false
        val worldPattern = WorldPattern.fromString(pattern) ?: return false
        return _patterns.add(worldPattern)
    }

    /**
     * Removes a pattern from this group.
     */
    fun removePattern(pattern: String): Boolean {
        return _patterns.removeIf { it.pattern == pattern }
    }

    /**
     * Sets the patterns for this group.
     * Returns false if any pattern is invalid.
     */
    fun setPatterns(patterns: List<String>): Boolean {
        val compiled = patterns.mapNotNull { WorldPattern.fromString(it) }
        if (compiled.size != patterns.size) return false
        _patterns.clear()
        _patterns.addAll(compiled)
        return true
    }

    /**
     * Checks if a world name matches this group (either directly or via pattern).
     */
    fun containsWorld(worldName: String): Boolean {
        if (_worlds.contains(worldName)) return true
        return _patterns.any { it.matches(worldName) }
    }

    /**
     * Checks if a world matches via pattern (not direct assignment).
     */
    fun matchesPattern(worldName: String): Boolean {
        return _patterns.any { it.matches(worldName) }
    }

    /**
     * Converts to the immutable API model.
     */
    fun toApiModel(): InventoryGroup = InventoryGroup(
        name = name,
        worlds = worlds,
        patterns = patternStrings,
        priority = priority,
        parent = parent,
        settings = settings,
        isDefault = isDefault
    )

    companion object {
        /**
         * Creates a Group from the API model.
         */
        fun fromApiModel(model: InventoryGroup): Group {
            val patterns = model.patterns.mapNotNull { WorldPattern.fromString(it) }
            return Group(
                name = model.name,
                worlds = model.worlds,
                patterns = patterns,
                priority = model.priority,
                parent = model.parent,
                settings = model.settings,
                isDefault = model.isDefault
            )
        }
    }
}

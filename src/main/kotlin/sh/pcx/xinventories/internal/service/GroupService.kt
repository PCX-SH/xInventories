package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupModifier
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.config.GroupConfig
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.WorldPattern
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.World
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing inventory groups.
 */
class GroupService(private val plugin: XInventories) {

    private val groups = ConcurrentHashMap<String, Group>()
    private var defaultGroupName: String = "survival"

    /**
     * Initializes groups from configuration.
     */
    fun initialize() {
        loadFromConfig()
        Logging.info("Loaded ${groups.size} inventory groups")
    }

    /**
     * Reloads groups from configuration.
     */
    fun reload() {
        groups.clear()
        loadFromConfig()
        Logging.info("Reloaded ${groups.size} inventory groups")
    }

    private fun loadFromConfig() {
        val config = plugin.configManager.groupsConfig

        config.groups.forEach { (name, groupConfig) ->
            val patterns = groupConfig.patterns.mapNotNull { WorldPattern.fromString(it) }

            val group = Group(
                name = name,
                worlds = groupConfig.worlds.toSet(),
                patterns = patterns,
                priority = groupConfig.priority,
                parent = groupConfig.parent,
                settings = groupConfig.settings,
                isDefault = name == config.defaultGroup
            )

            groups[name] = group
        }

        defaultGroupName = config.defaultGroup

        // Ensure default group exists
        if (!groups.containsKey(defaultGroupName)) {
            Logging.warning("Default group '$defaultGroupName' not found, creating empty group")
            groups[defaultGroupName] = Group(
                name = defaultGroupName,
                isDefault = true
            )
        }
    }

    /**
     * Gets a group by name.
     */
    fun getGroup(name: String): Group? = groups[name]

    /**
     * Gets a group as the API model.
     */
    fun getGroupApi(name: String): InventoryGroup? = groups[name]?.toApiModel()

    /**
     * Gets all groups.
     */
    fun getAllGroups(): List<Group> = groups.values.toList()

    /**
     * Gets all groups as API models.
     */
    fun getAllGroupsApi(): List<InventoryGroup> = groups.values.map { it.toApiModel() }

    /**
     * Gets the default group.
     */
    fun getDefaultGroup(): Group = groups[defaultGroupName]
        ?: throw IllegalStateException("Default group not configured")

    /**
     * Gets the default group as API model.
     */
    fun getDefaultGroupApi(): InventoryGroup = getDefaultGroup().toApiModel()

    /**
     * Gets the group for a world.
     * Resolution order: explicit assignment -> pattern match -> default group
     */
    fun getGroupForWorld(world: World): Group = getGroupForWorld(world.name)

    /**
     * Gets the group for a world by name.
     */
    fun getGroupForWorld(worldName: String): Group {
        // Check explicit world assignments (highest priority first)
        val explicitMatches = groups.values
            .filter { it.worlds.contains(worldName) }
            .sortedByDescending { it.priority }

        if (explicitMatches.isNotEmpty()) {
            return explicitMatches.first()
        }

        // Check pattern matches
        val patternMatches = groups.values
            .filter { it.matchesPattern(worldName) }
            .sortedByDescending { it.priority }

        if (patternMatches.isNotEmpty()) {
            return patternMatches.first()
        }

        // Return default group
        return getDefaultGroup()
    }

    /**
     * Gets the group for a world as API model.
     */
    fun getGroupForWorldApi(world: World): InventoryGroup = getGroupForWorld(world).toApiModel()
    fun getGroupForWorldApi(worldName: String): InventoryGroup = getGroupForWorld(worldName).toApiModel()

    /**
     * Creates a new group.
     */
    fun createGroup(
        name: String,
        settings: GroupSettings = GroupSettings(),
        worlds: Set<String> = emptySet(),
        patterns: List<String> = emptyList(),
        priority: Int = 0,
        parent: String? = null
    ): Result<InventoryGroup> {
        if (groups.containsKey(name)) {
            return Result.failure(IllegalArgumentException("Group '$name' already exists"))
        }

        val compiledPatterns = patterns.mapNotNull { WorldPattern.fromString(it) }
        if (compiledPatterns.size != patterns.size) {
            return Result.failure(IllegalArgumentException("Invalid regex pattern in patterns list"))
        }

        val group = Group(
            name = name,
            worlds = worlds,
            patterns = compiledPatterns,
            priority = priority,
            parent = parent,
            settings = settings,
            isDefault = false
        )

        groups[name] = group
        saveToConfig()

        Logging.info("Created group '$name'")
        return Result.success(group.toApiModel())
    }

    /**
     * Deletes a group.
     */
    fun deleteGroup(name: String): Result<Unit> {
        if (name == defaultGroupName) {
            return Result.failure(IllegalArgumentException("Cannot delete the default group"))
        }

        if (!groups.containsKey(name)) {
            return Result.failure(IllegalArgumentException("Group '$name' not found"))
        }

        groups.remove(name)
        saveToConfig()

        Logging.info("Deleted group '$name'")
        return Result.success(Unit)
    }

    /**
     * Modifies an existing group.
     */
    fun modifyGroup(name: String, modifier: GroupModifier.() -> Unit): Result<InventoryGroup> {
        val group = groups[name]
            ?: return Result.failure(IllegalArgumentException("Group '$name' not found"))

        val groupModifier = GroupModifierImpl(group)
        modifier(groupModifier)

        saveToConfig()

        Logging.info("Modified group '$name'")
        return Result.success(group.toApiModel())
    }

    /**
     * Assigns a world to a group.
     */
    fun assignWorldToGroup(worldName: String, groupName: String): Result<Unit> {
        val group = groups[groupName]
            ?: return Result.failure(IllegalArgumentException("Group '$groupName' not found"))

        // Remove from other groups first
        groups.values.forEach { g ->
            if (g.name != groupName) {
                g.removeWorld(worldName)
            }
        }

        group.addWorld(worldName)
        saveToConfig()

        Logging.info("Assigned world '$worldName' to group '$groupName'")
        return Result.success(Unit)
    }

    /**
     * Removes a world's explicit assignment.
     */
    fun unassignWorld(worldName: String): Result<Unit> {
        var found = false
        groups.values.forEach { group ->
            if (group.removeWorld(worldName)) {
                found = true
            }
        }

        if (found) {
            saveToConfig()
            Logging.info("Unassigned world '$worldName'")
        }

        return Result.success(Unit)
    }

    /**
     * Gets all explicit world-to-group assignments.
     */
    fun getWorldAssignments(): Map<String, String> {
        val assignments = mutableMapOf<String, String>()
        groups.values.forEach { group ->
            group.worlds.forEach { world ->
                assignments[world] = group.name
            }
        }
        return assignments
    }

    /**
     * Adds a pattern to a group.
     */
    fun addPattern(groupName: String, pattern: String): Result<Unit> {
        val group = groups[groupName]
            ?: return Result.failure(IllegalArgumentException("Group '$groupName' not found"))

        if (!group.addPattern(pattern)) {
            return Result.failure(IllegalArgumentException("Invalid or duplicate pattern: $pattern"))
        }

        saveToConfig()
        return Result.success(Unit)
    }

    /**
     * Removes a pattern from a group.
     */
    fun removePattern(groupName: String, pattern: String): Result<Unit> {
        val group = groups[groupName]
            ?: return Result.failure(IllegalArgumentException("Group '$groupName' not found"))

        if (!group.removePattern(pattern)) {
            return Result.failure(IllegalArgumentException("Pattern not found: $pattern"))
        }

        saveToConfig()
        return Result.success(Unit)
    }

    /**
     * Gets patterns for a group.
     */
    fun getPatterns(groupName: String): List<String> {
        return groups[groupName]?.patternStrings ?: emptyList()
    }

    /**
     * Tests if a world matches a pattern in a group.
     */
    fun testPattern(worldName: String, groupName: String): Boolean {
        return groups[groupName]?.matchesPattern(worldName) ?: false
    }

    /**
     * Saves current groups to configuration.
     */
    private fun saveToConfig() {
        plugin.configManager.saveGroupsConfig()
    }

    /**
     * Implementation of GroupModifier for modifying groups.
     */
    private class GroupModifierImpl(private val group: Group) : GroupModifier {
        override fun addWorld(world: String) {
            group.addWorld(world)
        }

        override fun removeWorld(world: String) {
            group.removeWorld(world)
        }

        override fun setWorlds(worlds: Set<String>) {
            group.setWorlds(worlds)
        }

        override fun addPattern(pattern: String) {
            group.addPattern(pattern)
        }

        override fun removePattern(pattern: String) {
            group.removePattern(pattern)
        }

        override fun setPatterns(patterns: List<String>) {
            group.setPatterns(patterns)
        }

        override fun setPriority(priority: Int) {
            group.priority = priority
        }

        override fun setParent(parent: String?) {
            group.parent = parent
        }

        override fun modifySettings(modifier: GroupSettings.() -> GroupSettings) {
            group.settings = modifier(group.settings)
        }
    }
}

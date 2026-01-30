package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.model.GroupModifier
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.config.ConditionsConfig
import sh.pcx.xinventories.internal.config.GroupConfig
import sh.pcx.xinventories.internal.config.RestrictionConfigSection
import sh.pcx.xinventories.internal.config.TemplateConfigSection
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.GroupConditions
import sh.pcx.xinventories.internal.model.PlaceholderCondition
import sh.pcx.xinventories.internal.model.RestrictionConfig
import sh.pcx.xinventories.internal.model.RestrictionMode
import sh.pcx.xinventories.internal.model.RestrictionViolationAction
import sh.pcx.xinventories.internal.model.TemplateApplyTrigger
import sh.pcx.xinventories.internal.model.TemplateSettings
import sh.pcx.xinventories.internal.model.TimeRange
import sh.pcx.xinventories.internal.model.WorldPattern
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing inventory groups.
 */
class GroupService(private val plugin: PluginContext) {

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
            val conditions = parseConditions(groupConfig.conditions)
            val templateSettings = parseTemplateSettings(groupConfig.template)
            val restrictionConfig = parseRestrictionConfig(groupConfig.restrictions)

            val group = Group(
                name = name,
                worlds = groupConfig.worlds.toSet(),
                patterns = patterns,
                priority = groupConfig.priority,
                parent = groupConfig.parent,
                settings = groupConfig.settings,
                isDefault = name == config.defaultGroup,
                conditions = conditions,
                templateSettings = templateSettings,
                restrictions = restrictionConfig,
                explicitSettings = groupConfig.explicitSettings
            )

            groups[name] = group

            if (conditions?.hasConditions() == true) {
                Logging.debug { "Group '$name' has conditions: ${conditions.toDisplayString()}" }
            }
            if (templateSettings?.enabled == true) {
                Logging.debug { "Group '$name' has template: ${templateSettings.templateName ?: name}, trigger: ${templateSettings.applyOn}" }
            }
            if (restrictionConfig?.isEnabled() == true) {
                Logging.debug { "Group '$name' has restrictions: mode=${restrictionConfig.mode}" }
            }
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
     * Parses template configuration into TemplateSettings model.
     */
    private fun parseTemplateSettings(config: TemplateConfigSection?): TemplateSettings? {
        if (config == null) return null

        val applyTrigger = try {
            TemplateApplyTrigger.valueOf(config.applyOn.uppercase())
        } catch (e: Exception) {
            TemplateApplyTrigger.NONE
        }

        return TemplateSettings(
            enabled = config.enabled,
            templateName = config.templateName,
            applyOn = applyTrigger,
            allowReset = config.allowReset,
            clearInventoryFirst = config.clearInventoryFirst
        )
    }

    /**
     * Parses restriction configuration into RestrictionConfig model.
     */
    private fun parseRestrictionConfig(config: RestrictionConfigSection?): RestrictionConfig? {
        if (config == null) return null

        val mode = try {
            RestrictionMode.valueOf(config.mode.uppercase())
        } catch (e: Exception) {
            RestrictionMode.NONE
        }

        val onViolation = try {
            RestrictionViolationAction.valueOf(config.onViolation.uppercase())
        } catch (e: Exception) {
            RestrictionViolationAction.REMOVE
        }

        return RestrictionConfig(
            mode = mode,
            onViolation = onViolation,
            notifyPlayer = config.notifyPlayer,
            notifyAdmins = config.notifyAdmins,
            blacklist = config.blacklist,
            whitelist = config.whitelist,
            stripOnExit = config.stripOnExit
        )
    }

    /**
     * Parses conditions configuration into GroupConditions model.
     */
    private fun parseConditions(config: ConditionsConfig?): GroupConditions? {
        if (config == null) return null

        val scheduleRanges = config.schedule?.mapNotNull { scheduleConfig ->
            TimeRange.fromStrings(scheduleConfig.start, scheduleConfig.end, scheduleConfig.timezone)
        }

        val placeholderCondition = config.placeholder?.let {
            PlaceholderCondition.fromConfig(it.placeholder, it.operator, it.value)
        }

        val placeholderConditions = config.placeholders?.mapNotNull {
            PlaceholderCondition.fromConfig(it.placeholder, it.operator, it.value)
        }

        return GroupConditions(
            permission = config.permission,
            schedule = scheduleRanges,
            cron = config.cron,
            placeholder = placeholderCondition,
            placeholders = placeholderConditions,
            requireAll = config.requireAll
        )
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
     * Gets the group for a player, considering both world and conditions.
     * Resolution order:
     * 1. Groups with conditions that match the player (highest priority first)
     * 2. Explicit world assignment (highest priority first)
     * 3. Pattern match (highest priority first)
     * 4. Default group
     */
    fun getGroupForPlayer(player: Player): Group {
        val worldName = player.world.name
        val conditionEvaluator = plugin.serviceManager.conditionEvaluator

        // Get all groups that could match this world
        val worldMatchingGroups = groups.values.filter { group ->
            group.worlds.contains(worldName) ||
            group.matchesPattern(worldName) ||
            group.worlds.isEmpty() && group.patterns.isEmpty() // Groups with only conditions
        }

        // First, check groups with conditions (they have higher priority)
        val conditionalGroups = worldMatchingGroups
            .filter { it.conditions?.hasConditions() == true }
            .sortedByDescending { it.priority }

        for (group in conditionalGroups) {
            val result = conditionEvaluator.evaluateConditions(player, group)
            if (result.matches) {
                Logging.debug { "Player ${player.name} matched conditional group '${group.name}'" }
                return group
            }
        }

        // Fall back to world-based matching (existing logic)
        return getGroupForWorld(worldName)
    }

    /**
     * Gets the group for a player as API model.
     */
    fun getGroupForPlayerApi(player: Player): InventoryGroup = getGroupForPlayer(player).toApiModel()

    /**
     * Gets the reason why a player is in their current group.
     */
    fun getGroupMatchReason(player: Player): String {
        val worldName = player.world.name
        val conditionEvaluator = plugin.serviceManager.conditionEvaluator

        // Check conditional groups first
        val conditionalGroups = groups.values
            .filter { it.conditions?.hasConditions() == true }
            .filter {
                it.worlds.contains(worldName) ||
                it.matchesPattern(worldName) ||
                it.worlds.isEmpty() && it.patterns.isEmpty()
            }
            .sortedByDescending { it.priority }

        for (group in conditionalGroups) {
            val result = conditionEvaluator.evaluateConditions(player, group)
            if (result.matches) {
                return "Matched conditional group '${group.name}': ${result.matchedConditions.joinToString(", ")}"
            }
        }

        // Check world-based matching
        val explicitMatch = groups.values
            .filter { it.worlds.contains(worldName) }
            .maxByOrNull { it.priority }

        if (explicitMatch != null) {
            return "Explicit world assignment to '${explicitMatch.name}'"
        }

        val patternMatch = groups.values
            .filter { it.matchesPattern(worldName) }
            .maxByOrNull { it.priority }

        if (patternMatch != null) {
            return "Pattern match to '${patternMatch.name}'"
        }

        return "Default group '${defaultGroupName}'"
    }

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

    // ═══════════════════════════════════════════════════════════════════
    // World Group Inheritance
    // ═══════════════════════════════════════════════════════════════════

    // Cache for resolved settings to avoid repeated inheritance resolution
    private val resolvedSettingsCache = ConcurrentHashMap<String, GroupSettings>()

    /**
     * Resolves the effective settings for a group, including inherited settings.
     *
     * Resolution order (PWI-style):
     * 1. Start with global player defaults from config.yml
     * 2. Apply parent group settings (if any)
     * 3. Merge with group's explicit settings (only explicitly set values override)
     *
     * @param groupName The name of the group
     * @return The resolved settings, or default settings if group not found
     */
    fun resolveSettings(groupName: String): GroupSettings {
        // Check cache first
        resolvedSettingsCache[groupName]?.let { return it }

        val group = getGroup(groupName) ?: return GroupSettings()

        // Check for circular inheritance
        val visited = mutableSetOf<String>()
        if (hasCircularInheritance(groupName, visited)) {
            Logging.warning("Circular inheritance detected for group '$groupName', using own settings")
            resolvedSettingsCache[groupName] = group.settings
            return group.settings
        }

        // Start with global player defaults, then apply parent settings if any
        val globalDefaults = plugin.configManager.mainConfig.player.toGroupSettings()
        val parentName = group.parent
        val baseSettings = if (parentName != null) {
            resolveSettings(parentName)
        } else {
            globalDefaults
        }

        // Merge group's explicit settings on top (only explicitly set fields override)
        val merged = baseSettings.merge(group.settings, group.explicitSettings.ifEmpty { null })
        resolvedSettingsCache[groupName] = merged

        Logging.debug { "Resolved settings for '$groupName' (parent: '$parentName', explicit: ${group.explicitSettings.size} fields)" }
        return merged
    }

    /**
     * Gets the resolved settings for a group as an API-friendly result.
     *
     * @param groupName The name of the group
     * @return Result containing the resolved settings or an error
     */
    fun resolveSettingsResult(groupName: String): Result<GroupSettings> {
        val group = getGroup(groupName)
            ?: return Result.failure(IllegalArgumentException("Group '$groupName' not found"))

        return try {
            Result.success(resolveSettings(groupName))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Checks if a group has circular inheritance.
     *
     * @param groupName The starting group name
     * @param visited Set of already visited group names
     * @return true if circular inheritance is detected
     */
    fun hasCircularInheritance(groupName: String, visited: MutableSet<String> = mutableSetOf()): Boolean {
        if (visited.contains(groupName)) {
            return true
        }

        val group = getGroup(groupName) ?: return false
        val parentName = group.parent ?: return false

        visited.add(groupName)
        return hasCircularInheritance(parentName, visited)
    }

    /**
     * Validates inheritance for all groups.
     *
     * @return Map of group name to list of validation errors
     */
    fun validateInheritance(): Map<String, List<String>> {
        val errors = mutableMapOf<String, MutableList<String>>()

        for ((name, group) in groups) {
            val groupErrors = mutableListOf<String>()

            // Check parent exists
            group.parent?.let { parentName ->
                if (!groups.containsKey(parentName)) {
                    groupErrors.add("Parent group '$parentName' does not exist")
                }
            }

            // Check for circular inheritance
            if (hasCircularInheritance(name)) {
                groupErrors.add("Circular inheritance detected")
            }

            if (groupErrors.isNotEmpty()) {
                errors[name] = groupErrors
            }
        }

        return errors
    }

    /**
     * Gets the inheritance chain for a group.
     *
     * @param groupName The starting group name
     * @return List of group names from child to root, or empty if group not found
     */
    fun getInheritanceChain(groupName: String): List<String> {
        val chain = mutableListOf<String>()
        val visited = mutableSetOf<String>()

        var current: String? = groupName
        while (current != null && !visited.contains(current)) {
            val group = getGroup(current) ?: break
            chain.add(current)
            visited.add(current)
            current = group.parent
        }

        return chain
    }

    /**
     * Gets all groups that inherit from a given group (direct children).
     *
     * @param groupName The parent group name
     * @return List of child group names
     */
    fun getChildGroups(groupName: String): List<String> {
        return groups.values
            .filter { it.parent == groupName }
            .map { it.name }
    }

    /**
     * Gets all groups that inherit from a given group (all descendants).
     *
     * @param groupName The ancestor group name
     * @return List of descendant group names
     */
    fun getAllDescendants(groupName: String): List<String> {
        val descendants = mutableListOf<String>()
        val toProcess = getChildGroups(groupName).toMutableList()

        while (toProcess.isNotEmpty()) {
            val child = toProcess.removeAt(0)
            descendants.add(child)
            toProcess.addAll(getChildGroups(child))
        }

        return descendants
    }

    /**
     * Clears the resolved settings cache.
     * Should be called when group settings or inheritance changes.
     */
    fun clearSettingsCache() {
        resolvedSettingsCache.clear()
        Logging.debug { "Cleared resolved settings cache" }
    }

    /**
     * Saves current groups to configuration.
     */
    fun saveToConfig() {
        plugin.configManager.saveGroupsConfig()
        clearSettingsCache() // Clear cache when config changes
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

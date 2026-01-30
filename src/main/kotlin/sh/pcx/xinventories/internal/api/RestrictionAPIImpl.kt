package sh.pcx.xinventories.internal.api

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.RestrictionAPI
import sh.pcx.xinventories.internal.model.ItemPattern
import sh.pcx.xinventories.internal.model.RestrictionAction
import sh.pcx.xinventories.internal.model.RestrictionConfig
import sh.pcx.xinventories.internal.model.RestrictionMode

/**
 * Implementation of the RestrictionAPI.
 * Adapts internal RestrictionService to the public API interface.
 */
class RestrictionAPIImpl(private val plugin: PluginContext) : RestrictionAPI {

    private val restrictionService get() = plugin.serviceManager.restrictionService
    private val groupService get() = plugin.serviceManager.groupService

    override fun getRestrictionConfig(groupName: String): RestrictionConfig? {
        val group = groupService.getGroup(groupName) ?: return null
        return group.restrictions
    }

    override fun setRestrictionConfig(groupName: String, config: RestrictionConfig): Boolean {
        val group = groupService.getGroup(groupName) ?: return false
        // The group service doesn't have a direct setter for restriction config
        // This would need to be added to the GroupService for full implementation
        return false
    }

    override fun setRestrictionMode(groupName: String, mode: RestrictionMode): Boolean {
        val config = getRestrictionConfig(groupName) ?: return false
        val newConfig = config.copy(mode = mode)
        return setRestrictionConfig(groupName, newConfig)
    }

    override fun addBlacklistPattern(groupName: String, pattern: String): Result<Unit> {
        val validation = validatePattern(pattern)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull()!!)
        }

        val config = getRestrictionConfig(groupName)
            ?: return Result.failure(IllegalArgumentException("Group not found: $groupName"))

        val newBlacklist = config.blacklist + pattern
        val newConfig = config.copy(blacklist = newBlacklist)
        return if (setRestrictionConfig(groupName, newConfig)) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to update restriction config"))
        }
    }

    override fun removeBlacklistPattern(groupName: String, pattern: String): Boolean {
        val config = getRestrictionConfig(groupName) ?: return false
        if (pattern !in config.blacklist) return false

        val newBlacklist = config.blacklist - pattern
        val newConfig = config.copy(blacklist = newBlacklist)
        return setRestrictionConfig(groupName, newConfig)
    }

    override fun addWhitelistPattern(groupName: String, pattern: String): Result<Unit> {
        val validation = validatePattern(pattern)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull()!!)
        }

        val config = getRestrictionConfig(groupName)
            ?: return Result.failure(IllegalArgumentException("Group not found: $groupName"))

        val newWhitelist = config.whitelist + pattern
        val newConfig = config.copy(whitelist = newWhitelist)
        return if (setRestrictionConfig(groupName, newConfig)) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to update restriction config"))
        }
    }

    override fun removeWhitelistPattern(groupName: String, pattern: String): Boolean {
        val config = getRestrictionConfig(groupName) ?: return false
        if (pattern !in config.whitelist) return false

        val newWhitelist = config.whitelist - pattern
        val newConfig = config.copy(whitelist = newWhitelist)
        return setRestrictionConfig(groupName, newConfig)
    }

    override fun addStripOnExitPattern(groupName: String, pattern: String): Result<Unit> {
        val validation = validatePattern(pattern)
        if (validation.isFailure) {
            return Result.failure(validation.exceptionOrNull()!!)
        }

        val config = getRestrictionConfig(groupName)
            ?: return Result.failure(IllegalArgumentException("Group not found: $groupName"))

        val newStripOnExit = config.stripOnExit + pattern
        val newConfig = config.copy(stripOnExit = newStripOnExit)
        return if (setRestrictionConfig(groupName, newConfig)) {
            Result.success(Unit)
        } else {
            Result.failure(Exception("Failed to update restriction config"))
        }
    }

    override fun removeStripOnExitPattern(groupName: String, pattern: String): Boolean {
        val config = getRestrictionConfig(groupName) ?: return false
        if (pattern !in config.stripOnExit) return false

        val newStripOnExit = config.stripOnExit - pattern
        val newConfig = config.copy(stripOnExit = newStripOnExit)
        return setRestrictionConfig(groupName, newConfig)
    }

    override fun testItem(item: ItemStack, groupName: String): Pair<Boolean, String?> {
        val config = getRestrictionConfig(groupName)
            ?: return false to null

        return restrictionService.testItem(item, config)
    }

    override fun testStripOnExit(item: ItemStack, groupName: String): String? {
        val config = getRestrictionConfig(groupName) ?: return null
        return restrictionService.shouldStripOnExit(item, config)
    }

    override fun checkPlayerInventory(player: Player, groupName: String): Map<Int, Pair<ItemStack, String>> {
        val config = getRestrictionConfig(groupName) ?: return emptyMap()
        return restrictionService.checkPlayerInventory(player, config, RestrictionAction.ENTERING)
    }

    override fun parsePattern(pattern: String): ItemPattern {
        return restrictionService.parsePattern(pattern)
    }

    override fun validatePattern(pattern: String): Result<ItemPattern> {
        return restrictionService.validatePattern(pattern)
    }

    override fun getAllPatterns(groupName: String): List<Pair<String, Boolean>> {
        val config = getRestrictionConfig(groupName) ?: return emptyList()
        return restrictionService.getAllPatterns(config)
    }
}

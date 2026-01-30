package sh.pcx.xinventories.api

import sh.pcx.xinventories.internal.model.ItemPattern
import sh.pcx.xinventories.internal.model.RestrictionConfig
import sh.pcx.xinventories.internal.model.RestrictionMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * API for managing item restrictions.
 *
 * Item restrictions control which items players can carry into or out of groups.
 * Supports blacklist mode (specified items blocked) and whitelist mode (only
 * specified items allowed).
 *
 * // TODO: Add to XInventoriesAPI:
 * // val restrictions: RestrictionAPI
 */
interface RestrictionAPI {

    /**
     * Gets the restriction config for a group.
     *
     * @param groupName The group name
     * @return The restriction config, or null if group not found
     */
    fun getRestrictionConfig(groupName: String): RestrictionConfig?

    /**
     * Sets the restriction config for a group.
     *
     * @param groupName The group name
     * @param config The new restriction config
     * @return true if set successfully
     */
    fun setRestrictionConfig(groupName: String, config: RestrictionConfig): Boolean

    /**
     * Sets the restriction mode for a group.
     *
     * @param groupName The group name
     * @param mode The restriction mode
     * @return true if set successfully
     */
    fun setRestrictionMode(groupName: String, mode: RestrictionMode): Boolean

    /**
     * Adds a pattern to a group's blacklist.
     *
     * @param groupName The group name
     * @param pattern The item pattern
     * @return Result indicating success or failure (with validation error)
     */
    fun addBlacklistPattern(groupName: String, pattern: String): Result<Unit>

    /**
     * Removes a pattern from a group's blacklist.
     *
     * @param groupName The group name
     * @param pattern The item pattern
     * @return true if removed, false if not found
     */
    fun removeBlacklistPattern(groupName: String, pattern: String): Boolean

    /**
     * Adds a pattern to a group's whitelist.
     *
     * @param groupName The group name
     * @param pattern The item pattern
     * @return Result indicating success or failure (with validation error)
     */
    fun addWhitelistPattern(groupName: String, pattern: String): Result<Unit>

    /**
     * Removes a pattern from a group's whitelist.
     *
     * @param groupName The group name
     * @param pattern The item pattern
     * @return true if removed, false if not found
     */
    fun removeWhitelistPattern(groupName: String, pattern: String): Boolean

    /**
     * Adds a pattern to the strip-on-exit list.
     *
     * @param groupName The group name
     * @param pattern The item pattern
     * @return Result indicating success or failure
     */
    fun addStripOnExitPattern(groupName: String, pattern: String): Result<Unit>

    /**
     * Removes a pattern from the strip-on-exit list.
     *
     * @param groupName The group name
     * @param pattern The item pattern
     * @return true if removed, false if not found
     */
    fun removeStripOnExitPattern(groupName: String, pattern: String): Boolean

    /**
     * Tests if an item matches any restriction patterns for a group.
     *
     * @param item The item to test
     * @param groupName The group name
     * @return Pair of (isRestricted, matchingPattern)
     */
    fun testItem(item: ItemStack, groupName: String): Pair<Boolean, String?>

    /**
     * Tests if an item would be stripped on exit for a group.
     *
     * @param item The item to test
     * @param groupName The group name
     * @return The matching pattern, or null if not stripped
     */
    fun testStripOnExit(item: ItemStack, groupName: String): String?

    /**
     * Checks a player's entire inventory against restrictions.
     *
     * @param player The player
     * @param groupName The group name
     * @return Map of slot -> (item, matching pattern) for all violations
     */
    fun checkPlayerInventory(player: Player, groupName: String): Map<Int, Pair<ItemStack, String>>

    /**
     * Parses a pattern string into an ItemPattern.
     *
     * @param pattern The pattern string
     * @return The parsed pattern
     */
    fun parsePattern(pattern: String): ItemPattern

    /**
     * Validates a pattern string.
     *
     * @param pattern The pattern string
     * @return Result containing the parsed pattern or validation error
     */
    fun validatePattern(pattern: String): Result<ItemPattern>

    /**
     * Gets all patterns for a group.
     *
     * @param groupName The group name
     * @return List of patterns with their type (true = restricted, false = allowed)
     */
    fun getAllPatterns(groupName: String): List<Pair<String, Boolean>>
}

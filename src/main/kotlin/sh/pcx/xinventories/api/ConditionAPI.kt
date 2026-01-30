package sh.pcx.xinventories.api

import sh.pcx.xinventories.internal.service.ConditionEvaluationResult
import org.bukkit.entity.Player

/**
 * API for evaluating group conditions.
 */
interface ConditionAPI {

    /**
     * Evaluates conditions for a specific group against a player.
     *
     * @param player The player to evaluate conditions for
     * @param groupName The name of the group
     * @return ConditionEvaluationResult with match status and details, or null if group not found
     */
    fun evaluateConditions(player: Player, groupName: String): ConditionEvaluationResult?

    /**
     * Gets all active condition results for a player across all groups.
     *
     * @param player The player to check
     * @return Map of group name to evaluation result for all groups with conditions
     */
    fun getActiveConditionsForPlayer(player: Player): Map<String, ConditionEvaluationResult>

    /**
     * Gets a human-readable description of why a player matched or didn't match a group.
     *
     * @param player The player
     * @param groupName The group name
     * @return Description string, or null if group not found
     */
    fun getMatchReason(player: Player, groupName: String): String?

    /**
     * Invalidates the condition cache for a player.
     * Call this when a player's permissions or placeholders change.
     *
     * @param player The player to invalidate cache for
     */
    fun invalidateCache(player: Player)

    /**
     * Checks if PlaceholderAPI is available for placeholder conditions.
     *
     * @return true if PlaceholderAPI is loaded and available
     */
    fun isPlaceholderAPIAvailable(): Boolean

    /**
     * Parses a placeholder for a player (requires PlaceholderAPI).
     *
     * @param player The player
     * @param placeholder The placeholder string (e.g., "%player_level%")
     * @return The resolved value, or the original placeholder if PAPI unavailable
     */
    fun parsePlaceholder(player: Player, placeholder: String): String
}

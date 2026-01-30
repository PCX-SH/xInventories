package sh.pcx.xinventories.internal.integration

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.model.PlaceholderCondition
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Integration with PlaceholderAPI for placeholder-based conditions.
 * Uses soft dependency pattern - checks if PAPI is present before using.
 */
class PlaceholderAPIIntegration(private val plugin: PluginContext) {

    private var papiAvailable: Boolean = false

    init {
        checkAvailability()
    }

    /**
     * Checks if PlaceholderAPI is available.
     */
    fun checkAvailability() {
        papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null
        if (papiAvailable) {
            Logging.debug { "PlaceholderAPI found - placeholder conditions enabled" }
        } else {
            Logging.debug { "PlaceholderAPI not found - placeholder conditions disabled" }
        }
    }

    /**
     * Returns whether PlaceholderAPI is available.
     */
    fun isAvailable(): Boolean = papiAvailable

    /**
     * Parses placeholders in a string for a player.
     *
     * @param player The player to parse placeholders for
     * @param text The text containing placeholders
     * @return The text with placeholders replaced, or original text if PAPI unavailable
     */
    fun parsePlaceholders(player: Player, text: String): String {
        if (!papiAvailable) return text

        return try {
            me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text)
        } catch (e: Exception) {
            Logging.debug { "Failed to parse placeholder: ${e.message}" }
            text
        }
    }

    /**
     * Evaluates a placeholder condition for a player.
     *
     * @param player The player to evaluate the condition for
     * @param condition The condition to evaluate
     * @return true if the condition is met, false otherwise (also false if PAPI unavailable)
     */
    fun evaluateCondition(player: Player, condition: PlaceholderCondition): Boolean {
        if (!papiAvailable) {
            Logging.debug { "PlaceholderAPI not available, condition evaluation returns false" }
            return false
        }

        return try {
            val actualValue = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(
                player,
                condition.placeholder
            )

            // If placeholder wasn't replaced (returns original), condition fails
            if (actualValue == condition.placeholder) {
                Logging.debug { "Placeholder ${condition.placeholder} not resolved for ${player.name}" }
                return false
            }

            val result = condition.evaluate(actualValue)
            Logging.debug {
                "Placeholder condition: ${condition.toDisplayString()} " +
                "| actual='$actualValue' | result=$result"
            }
            result
        } catch (e: Exception) {
            Logging.debug { "Failed to evaluate placeholder condition: ${e.message}" }
            false
        }
    }

    /**
     * Evaluates multiple placeholder conditions for a player.
     *
     * @param player The player to evaluate conditions for
     * @param conditions The list of conditions to evaluate
     * @param requireAll If true, all conditions must be met; if false, any condition suffices
     * @return true if conditions are satisfied according to requireAll logic
     */
    fun evaluateConditions(
        player: Player,
        conditions: List<PlaceholderCondition>,
        requireAll: Boolean = true
    ): Boolean {
        if (conditions.isEmpty()) return true
        if (!papiAvailable) return false

        return if (requireAll) {
            conditions.all { evaluateCondition(player, it) }
        } else {
            conditions.any { evaluateCondition(player, it) }
        }
    }

    /**
     * Gets the raw value of a placeholder for a player.
     *
     * @param player The player to get the placeholder value for
     * @param placeholder The placeholder string
     * @return The resolved value or null if unavailable/unresolved
     */
    fun getPlaceholderValue(player: Player, placeholder: String): String? {
        if (!papiAvailable) return null

        return try {
            val value = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, placeholder)
            if (value == placeholder) null else value
        } catch (e: Exception) {
            null
        }
    }
}

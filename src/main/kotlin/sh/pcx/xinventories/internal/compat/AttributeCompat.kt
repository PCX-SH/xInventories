package sh.pcx.xinventories.internal.compat

import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeInstance
import org.bukkit.entity.Player

/**
 * Compatibility layer for Bukkit Attribute API changes across Minecraft versions.
 *
 * ## Version History
 *
 * ### Minecraft 1.21.2+ (Paper 1.21.2+)
 * Attribute names were simplified by removing the "GENERIC_" prefix:
 * - `GENERIC_MAX_HEALTH` -> `MAX_HEALTH`
 * - `GENERIC_ATTACK_DAMAGE` -> `ATTACK_DAMAGE`
 * - `GENERIC_MOVEMENT_SPEED` -> `MOVEMENT_SPEED`
 * - etc.
 *
 * ### Minecraft 1.20.5 - 1.21.1 (Paper 1.20.5 - 1.21.1)
 * Uses the original `GENERIC_*` naming convention.
 *
 * ## Usage
 *
 * This utility provides runtime detection to support both naming conventions,
 * allowing the plugin to work on Paper 1.20.5 through 1.21.x+ without code changes.
 *
 * ```kotlin
 * // Get max health attribute instance
 * val maxHealthAttr = AttributeCompat.getMaxHealth(player)
 *
 * // Get max health value with fallback
 * val maxHealth = AttributeCompat.getMaxHealthValue(player, 20.0)
 * ```
 */
object AttributeCompat {

    /**
     * The max health attribute, detected at runtime.
     *
     * Will resolve to:
     * - `MAX_HEALTH` on 1.21.2+
     * - `GENERIC_MAX_HEALTH` on 1.20.5 - 1.21.1
     */
    val MAX_HEALTH: Attribute by lazy {
        detectAttribute("MAX_HEALTH", "GENERIC_MAX_HEALTH")
    }

    /**
     * Gets the max health [AttributeInstance] for a player.
     *
     * @param player The player to get the attribute for
     * @return The attribute instance, or null if not found
     */
    fun getMaxHealth(player: Player): AttributeInstance? {
        return player.getAttribute(MAX_HEALTH)
    }

    /**
     * Gets the max health value for a player, with a default fallback.
     *
     * @param player The player to get max health for
     * @param default The default value if the attribute is not found (default: 20.0)
     * @return The player's max health value, or the default
     */
    fun getMaxHealthValue(player: Player, default: Double = 20.0): Double {
        return getMaxHealth(player)?.value ?: default
    }

    /**
     * Detects which attribute name is available at runtime.
     *
     * @param newName The new name (1.21.2+)
     * @param oldName The old name (pre-1.21.2)
     * @return The Attribute enum value that exists on this server
     * @throws IllegalStateException if neither name is found
     */
    private fun detectAttribute(newName: String, oldName: String): Attribute {
        return try {
            // Try the new name first (1.21.2+)
            Attribute.valueOf(newName)
        } catch (e: IllegalArgumentException) {
            try {
                // Fall back to the old name (1.20.5 - 1.21.1)
                Attribute.valueOf(oldName)
            } catch (e2: IllegalArgumentException) {
                // This shouldn't happen on supported versions
                throw IllegalStateException(
                    "Could not find attribute: tried '$newName' and '$oldName'. " +
                    "This may indicate an incompatible server version. " +
                    "xInventories requires Paper 1.20.5 or newer."
                )
            }
        }
    }
}

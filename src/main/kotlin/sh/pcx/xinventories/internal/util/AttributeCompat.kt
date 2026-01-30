package sh.pcx.xinventories.internal.util

import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeInstance
import org.bukkit.entity.Player

/**
 * Compatibility layer for Bukkit Attribute API changes.
 *
 * In Minecraft 1.21.2+, attribute names were simplified:
 * - GENERIC_MAX_HEALTH -> MAX_HEALTH
 * - GENERIC_ATTACK_DAMAGE -> ATTACK_DAMAGE
 * - etc.
 *
 * This utility provides runtime detection to support both naming conventions.
 */
object AttributeCompat {

    /**
     * The max health attribute, detected at runtime.
     * Will be MAX_HEALTH on 1.21.2+ or GENERIC_MAX_HEALTH on earlier versions.
     */
    val MAX_HEALTH: Attribute by lazy {
        detectAttribute("MAX_HEALTH", "GENERIC_MAX_HEALTH")
    }

    /**
     * Gets the max health attribute instance for a player.
     */
    fun getMaxHealth(player: Player): AttributeInstance? {
        return player.getAttribute(MAX_HEALTH)
    }

    /**
     * Gets the max health value for a player, with a default fallback.
     */
    fun getMaxHealthValue(player: Player, default: Double = 20.0): Double {
        return getMaxHealth(player)?.value ?: default
    }

    /**
     * Detects which attribute name is available at runtime.
     */
    private fun detectAttribute(newName: String, oldName: String): Attribute {
        return try {
            // Try the new name first (1.21.2+)
            Attribute.valueOf(newName)
        } catch (e: IllegalArgumentException) {
            try {
                // Fall back to the old name (pre-1.21.2)
                Attribute.valueOf(oldName)
            } catch (e2: IllegalArgumentException) {
                // This shouldn't happen, but log and throw if it does
                throw IllegalStateException(
                    "Could not find attribute: tried '$newName' and '$oldName'. " +
                    "This may indicate an incompatible server version."
                )
            }
        }
    }
}

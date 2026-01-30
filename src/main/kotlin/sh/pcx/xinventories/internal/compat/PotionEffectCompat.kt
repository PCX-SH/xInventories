package sh.pcx.xinventories.internal.compat

import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.potion.PotionEffectType
import sh.pcx.xinventories.internal.util.Logging

/**
 * Compatibility layer for PotionEffectType lookups across Minecraft versions.
 *
 * ## Version History
 *
 * ### Minecraft 1.20.5+ (Paper 1.20.5+)
 * PotionEffectType is accessed via Registry.POTION_EFFECT_TYPE using NamespacedKey.
 * The old `PotionEffectType.getByName()` method is deprecated but still functional.
 *
 * ### Pre-1.20.5
 * PotionEffectType used static fields and getByName() was the primary lookup method.
 *
 * ## Usage
 *
 * This utility provides a unified API for looking up potion effect types by name,
 * supporting both namespaced keys (e.g., "minecraft:speed") and simple names (e.g., "SPEED").
 *
 * ```kotlin
 * // Lookup by simple name
 * val speed = PotionEffectCompat.getByName("SPEED")
 *
 * // Lookup by namespaced key
 * val regen = PotionEffectCompat.getByName("minecraft:regeneration")
 *
 * // Lookup with null safety
 * val effect = PotionEffectCompat.getByName("some_effect") ?: return
 * ```
 */
object PotionEffectCompat {

    /**
     * Gets a [PotionEffectType] by its name.
     *
     * Supports multiple input formats:
     * - Namespaced key: "minecraft:speed", "minecraft:regeneration"
     * - Simple name: "SPEED", "speed", "REGENERATION"
     * - Legacy format: "INCREASE_DAMAGE" (maps to strength)
     *
     * @param name The name of the potion effect type
     * @return The [PotionEffectType], or null if not found
     */
    fun getByName(name: String): PotionEffectType? {
        if (name.isBlank()) return null

        return try {
            // Try Registry-based lookup first (preferred for 1.20.5+)
            getFromRegistry(name) ?: getByNameLegacy(name)
        } catch (e: Exception) {
            Logging.debug { "Failed to resolve PotionEffectType for '$name': ${e.message}" }
            null
        }
    }

    /**
     * Looks up a potion effect type from the Bukkit Registry.
     *
     * @param name The name (simple or namespaced)
     * @return The [PotionEffectType], or null if not found
     */
    @Suppress("UnstableApiUsage")
    private fun getFromRegistry(name: String): PotionEffectType? {
        val namespacedKey = parseNamespacedKey(name) ?: return null
        return try {
            Registry.POTION_EFFECT_TYPE.get(namespacedKey)
        } catch (e: Exception) {
            // Registry lookup failed, will fall back to legacy
            null
        }
    }

    /**
     * Parses a string into a [NamespacedKey].
     *
     * Handles:
     * - "minecraft:speed" -> NamespacedKey.minecraft("speed")
     * - "speed" -> NamespacedKey.minecraft("speed")
     * - "SPEED" -> NamespacedKey.minecraft("speed")
     *
     * @param name The name to parse
     * @return The [NamespacedKey], or null if invalid
     */
    private fun parseNamespacedKey(name: String): NamespacedKey? {
        return try {
            if (name.contains(":")) {
                NamespacedKey.fromString(name.lowercase())
            } else {
                // Convert legacy names to registry keys
                val registryName = convertLegacyName(name.lowercase())
                NamespacedKey.minecraft(registryName)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Converts legacy effect names to their modern registry equivalents.
     *
     * Some effect names changed between versions:
     * - INCREASE_DAMAGE -> strength (1.20.5+)
     * - DAMAGE_RESISTANCE -> resistance (1.20.5+)
     * - SLOW -> slowness (1.20.5+)
     * - FAST_DIGGING -> haste (1.20.5+)
     * - SLOW_DIGGING -> mining_fatigue (1.20.5+)
     * - HEAL -> instant_health (1.20.5+)
     * - HARM -> instant_damage (1.20.5+)
     * - CONFUSION -> nausea (1.20.5+)
     * - JUMP -> jump_boost (1.20.5+)
     *
     * @param name The name to convert
     * @return The modern registry name
     */
    private fun convertLegacyName(name: String): String {
        return when (name) {
            "increase_damage" -> "strength"
            "damage_resistance" -> "resistance"
            "slow" -> "slowness"
            "fast_digging" -> "haste"
            "slow_digging" -> "mining_fatigue"
            "heal" -> "instant_health"
            "harm" -> "instant_damage"
            "confusion" -> "nausea"
            "jump" -> "jump_boost"
            else -> name
        }
    }

    /**
     * Falls back to the deprecated getByName() method for compatibility.
     *
     * This method is deprecated in newer versions but provides
     * backward compatibility for older servers.
     *
     * @param name The effect name
     * @return The [PotionEffectType], or null if not found
     */
    @Suppress("DEPRECATION")
    private fun getByNameLegacy(name: String): PotionEffectType? {
        // Try uppercase first (traditional format)
        val uppercase = name.uppercase()
        PotionEffectType.getByName(uppercase)?.let { return it }

        // Try lowercase
        PotionEffectType.getByName(name.lowercase())?.let { return it }

        // Try as-is
        return PotionEffectType.getByName(name)
    }
}

package sh.pcx.xinventories.internal.util

import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.Registry

/**
 * Serializes and deserializes PotionEffect data for storage.
 */
object PotionSerializer {

    /**
     * Serializes a list of potion effects to a list of maps.
     */
    fun serializeEffects(effects: List<PotionEffect>): List<Map<String, Any>> {
        return effects.map { effect ->
            mapOf(
                "type" to effect.type.key.toString(),
                "duration" to effect.duration,
                "amplifier" to effect.amplifier,
                "ambient" to effect.isAmbient,
                "particles" to effect.hasParticles(),
                "icon" to effect.hasIcon()
            )
        }
    }

    /**
     * Deserializes a list of potion effects from a list of maps.
     */
    @Suppress("UNCHECKED_CAST")
    fun deserializeEffects(data: List<Map<String, Any>>?): List<PotionEffect> {
        if (data == null) return emptyList()

        return data.mapNotNull { map ->
            try {
                val typeKey = map["type"] as? String ?: return@mapNotNull null
                val type = parsePotionEffectType(typeKey) ?: return@mapNotNull null
                val duration = (map["duration"] as? Number)?.toInt() ?: 0
                val amplifier = (map["amplifier"] as? Number)?.toInt() ?: 0
                val ambient = map["ambient"] as? Boolean ?: false
                val particles = map["particles"] as? Boolean ?: true
                val icon = map["icon"] as? Boolean ?: true

                PotionEffect(type, duration, amplifier, ambient, particles, icon)
            } catch (e: Exception) {
                Logging.debug { "Failed to deserialize potion effect: ${e.message}" }
                null
            }
        }
    }

    /**
     * Parses a potion effect type from a string key.
     */
    @Suppress("UnstableApiUsage")
    private fun parsePotionEffectType(key: String): PotionEffectType? {
        return try {
            // Try parsing as namespaced key first
            val namespacedKey = if (key.contains(":")) {
                org.bukkit.NamespacedKey.fromString(key)
            } else {
                org.bukkit.NamespacedKey.minecraft(key.lowercase())
            }

            namespacedKey?.let { Registry.POTION_EFFECT_TYPE.get(it) }
        } catch (e: Exception) {
            Logging.debug { "Failed to parse potion effect type: $key" }
            null
        }
    }

    /**
     * Deserializes a single potion effect from its components.
     */
    fun deserializeEffect(
        typeKey: String,
        duration: Int,
        amplifier: Int,
        ambient: Boolean,
        particles: Boolean,
        icon: Boolean
    ): PotionEffect? {
        return try {
            val type = parsePotionEffectType(typeKey) ?: return null
            PotionEffect(type, duration, amplifier, ambient, particles, icon)
        } catch (e: Exception) {
            Logging.debug { "Failed to deserialize potion effect: ${e.message}" }
            null
        }
    }

    /**
     * Serializes effects to a compact string format for SQL storage.
     */
    fun serializeEffectsToString(effects: List<PotionEffect>): String {
        if (effects.isEmpty()) return ""

        return effects.joinToString(";") { effect ->
            "${effect.type.key}:${effect.duration}:${effect.amplifier}:${effect.isAmbient}:${effect.hasParticles()}:${effect.hasIcon()}"
        }
    }

    /**
     * Deserializes effects from a compact string format.
     *
     * Handles both formats:
     * - Namespaced: "minecraft:speed:200:2:true:false:true"
     * - Simple: "speed:200:2:true:false:true"
     */
    fun deserializeEffectsFromString(data: String): List<PotionEffect> {
        if (data.isBlank()) return emptyList()

        return data.split(";").mapNotNull { effectStr ->
            try {
                val parts = effectStr.split(":")

                // Handle namespaced format (minecraft:effect:duration:...)
                // vs simple format (effect:duration:...)
                val (typeKey, dataOffset) = if (parts.size >= 7 && parts[0] == "minecraft") {
                    // Namespaced format: parts[0]=minecraft, parts[1]=effect_name
                    "${parts[0]}:${parts[1]}" to 2
                } else if (parts.size >= 6) {
                    // Simple format: parts[0]=effect_name
                    parts[0] to 1
                } else {
                    return@mapNotNull null
                }

                val type = parsePotionEffectType(typeKey) ?: return@mapNotNull null
                val duration = parts.getOrNull(dataOffset)?.toIntOrNull() ?: return@mapNotNull null
                val amplifier = parts.getOrNull(dataOffset + 1)?.toIntOrNull() ?: return@mapNotNull null
                val ambient = parts.getOrNull(dataOffset + 2)?.toBooleanStrictOrNull() ?: false
                val particles = parts.getOrNull(dataOffset + 3)?.toBooleanStrictOrNull() ?: true
                val icon = parts.getOrNull(dataOffset + 4)?.toBooleanStrictOrNull() ?: true

                PotionEffect(type, duration, amplifier, ambient, particles, icon)
            } catch (e: Exception) {
                Logging.debug { "Failed to parse effect from string: $effectStr" }
                null
            }
        }
    }
}

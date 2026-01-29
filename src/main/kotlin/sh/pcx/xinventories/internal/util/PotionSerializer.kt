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
     */
    fun deserializeEffectsFromString(data: String): List<PotionEffect> {
        if (data.isBlank()) return emptyList()

        return data.split(";").mapNotNull { effectStr ->
            try {
                val parts = effectStr.split(":")
                if (parts.size < 6) return@mapNotNull null

                val type = parsePotionEffectType(parts[0]) ?: return@mapNotNull null
                val duration = parts[1].toIntOrNull() ?: return@mapNotNull null
                val amplifier = parts[2].toIntOrNull() ?: return@mapNotNull null
                val ambient = parts[3].toBooleanStrictOrNull() ?: false
                val particles = parts[4].toBooleanStrictOrNull() ?: true
                val icon = parts[5].toBooleanStrictOrNull() ?: true

                PotionEffect(type, duration, amplifier, ambient, particles, icon)
            } catch (e: Exception) {
                Logging.debug { "Failed to parse effect from string: $effectStr" }
                null
            }
        }
    }
}

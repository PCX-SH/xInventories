package sh.pcx.xinventories.internal.model

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.bukkit.NamespacedKey

/**
 * Represents a parsed item pattern for matching items.
 *
 * Pattern syntax:
 * - MATERIAL - Exact material match
 * - MATERIAL:* - Material with any variant (same as exact for most cases)
 * - MATERIAL:ENCHANT:LEVEL - Material with specific enchant level
 * - MATERIAL:ENCHANT:LEVEL+ - Material with enchant at or above level
 * - *:ENCHANT:LEVEL - Any item with specific enchant level
 * - *:ENCHANT:LEVEL+ - Any item with enchant at or above level
 */
sealed class ItemPattern {

    /**
     * Checks if an item matches this pattern.
     */
    abstract fun matches(item: ItemStack): Boolean

    /**
     * Gets the original pattern string.
     */
    abstract val patternString: String

    /**
     * Matches an exact material type.
     */
    data class ExactMaterial(
        val material: Material,
        override val patternString: String
    ) : ItemPattern() {
        override fun matches(item: ItemStack): Boolean {
            return item.type == material
        }
    }

    /**
     * Matches a material with any variant (wildcard).
     */
    data class MaterialWildcard(
        val material: Material,
        override val patternString: String
    ) : ItemPattern() {
        override fun matches(item: ItemStack): Boolean {
            return item.type == material
        }
    }

    /**
     * Matches a material with a specific enchantment at an exact level.
     */
    data class MaterialWithEnchant(
        val material: Material?,
        val enchantment: Enchantment,
        val level: Int,
        val orHigher: Boolean,
        override val patternString: String
    ) : ItemPattern() {
        override fun matches(item: ItemStack): Boolean {
            // Check material if specified
            if (material != null && item.type != material) {
                return false
            }

            // Check enchantment
            val itemLevel = item.getEnchantmentLevel(enchantment)
            if (itemLevel == 0) {
                return false
            }

            return if (orHigher) {
                itemLevel >= level
            } else {
                itemLevel == level
            }
        }
    }

    /**
     * Matches any item with a specific enchantment.
     */
    data class AnyWithEnchant(
        val enchantment: Enchantment,
        val level: Int,
        val orHigher: Boolean,
        override val patternString: String
    ) : ItemPattern() {
        override fun matches(item: ItemStack): Boolean {
            val itemLevel = item.getEnchantmentLevel(enchantment)
            if (itemLevel == 0) {
                return false
            }

            return if (orHigher) {
                itemLevel >= level
            } else {
                itemLevel == level
            }
        }
    }

    /**
     * Matches all spawn eggs.
     */
    data class SpawnEggWildcard(
        override val patternString: String
    ) : ItemPattern() {
        override fun matches(item: ItemStack): Boolean {
            return item.type.name.endsWith("_SPAWN_EGG")
        }
    }

    /**
     * Invalid pattern that never matches.
     */
    data class Invalid(
        override val patternString: String,
        val reason: String
    ) : ItemPattern() {
        override fun matches(item: ItemStack): Boolean = false
    }

    companion object {
        /**
         * Parses a pattern string into an ItemPattern.
         */
        fun parse(pattern: String): ItemPattern {
            val trimmed = pattern.trim().uppercase()

            // Handle spawn egg wildcard
            if (trimmed == "SPAWN_EGG:*") {
                return SpawnEggWildcard(pattern)
            }

            val parts = trimmed.split(":")

            return when (parts.size) {
                1 -> {
                    // Simple material match: MATERIAL
                    parseMaterial(parts[0], pattern)
                }
                2 -> {
                    // Material with wildcard or enchant: MATERIAL:* or MATERIAL:ENCHANT
                    if (parts[1] == "*") {
                        parseMaterialWildcard(parts[0], pattern)
                    } else {
                        Invalid(pattern, "Invalid pattern format. Expected MATERIAL:* or MATERIAL:ENCHANT:LEVEL")
                    }
                }
                3 -> {
                    // Material with enchant and level: MATERIAL:ENCHANT:LEVEL or *:ENCHANT:LEVEL
                    parseEnchantPattern(parts[0], parts[1], parts[2], pattern)
                }
                else -> {
                    Invalid(pattern, "Invalid pattern format")
                }
            }
        }

        private fun parseMaterial(materialStr: String, pattern: String): ItemPattern {
            val material = Material.matchMaterial(materialStr)
            return if (material != null) {
                ExactMaterial(material, pattern)
            } else {
                Invalid(pattern, "Unknown material: $materialStr")
            }
        }

        private fun parseMaterialWildcard(materialStr: String, pattern: String): ItemPattern {
            val material = Material.matchMaterial(materialStr)
            return if (material != null) {
                MaterialWildcard(material, pattern)
            } else {
                Invalid(pattern, "Unknown material: $materialStr")
            }
        }

        private fun parseEnchantPattern(
            materialStr: String,
            enchantStr: String,
            levelStr: String,
            pattern: String
        ): ItemPattern {
            // Parse level (may end with + for "or higher")
            val orHigher = levelStr.endsWith("+")
            val levelNumStr = if (orHigher) levelStr.dropLast(1) else levelStr
            val level = levelNumStr.toIntOrNull()
                ?: return Invalid(pattern, "Invalid enchantment level: $levelStr")

            // Parse enchantment
            val enchantment = parseEnchantment(enchantStr)
                ?: return Invalid(pattern, "Unknown enchantment: $enchantStr")

            // Parse material (may be * for any)
            return if (materialStr == "*") {
                AnyWithEnchant(enchantment, level, orHigher, pattern)
            } else {
                val material = Material.matchMaterial(materialStr)
                if (material != null) {
                    MaterialWithEnchant(material, enchantment, level, orHigher, pattern)
                } else {
                    Invalid(pattern, "Unknown material: $materialStr")
                }
            }
        }

        private fun parseEnchantment(name: String): Enchantment? {
            // Try direct lookup
            val key = NamespacedKey.minecraft(name.lowercase())
            val direct = Enchantment.getByKey(key)
            if (direct != null) return direct

            // Try common aliases
            return when (name.uppercase()) {
                "SHARPNESS" -> Enchantment.SHARPNESS
                "SMITE" -> Enchantment.SMITE
                "BANE_OF_ARTHROPODS", "BANE" -> Enchantment.BANE_OF_ARTHROPODS
                "KNOCKBACK" -> Enchantment.KNOCKBACK
                "FIRE_ASPECT" -> Enchantment.FIRE_ASPECT
                "LOOTING" -> Enchantment.LOOTING
                "SWEEPING", "SWEEPING_EDGE" -> Enchantment.SWEEPING_EDGE
                "EFFICIENCY" -> Enchantment.EFFICIENCY
                "SILK_TOUCH" -> Enchantment.SILK_TOUCH
                "UNBREAKING" -> Enchantment.UNBREAKING
                "FORTUNE" -> Enchantment.FORTUNE
                "POWER" -> Enchantment.POWER
                "PUNCH" -> Enchantment.PUNCH
                "FLAME" -> Enchantment.FLAME
                "INFINITY" -> Enchantment.INFINITY
                "LUCK_OF_THE_SEA", "LUCK" -> Enchantment.LUCK_OF_THE_SEA
                "LURE" -> Enchantment.LURE
                "LOYALTY" -> Enchantment.LOYALTY
                "IMPALING" -> Enchantment.IMPALING
                "RIPTIDE" -> Enchantment.RIPTIDE
                "CHANNELING" -> Enchantment.CHANNELING
                "MULTISHOT" -> Enchantment.MULTISHOT
                "QUICK_CHARGE" -> Enchantment.QUICK_CHARGE
                "PIERCING" -> Enchantment.PIERCING
                "MENDING" -> Enchantment.MENDING
                "VANISHING_CURSE", "VANISHING" -> Enchantment.VANISHING_CURSE
                "BINDING_CURSE", "BINDING" -> Enchantment.BINDING_CURSE
                "PROTECTION" -> Enchantment.PROTECTION
                "FIRE_PROTECTION" -> Enchantment.FIRE_PROTECTION
                "FEATHER_FALLING" -> Enchantment.FEATHER_FALLING
                "BLAST_PROTECTION" -> Enchantment.BLAST_PROTECTION
                "PROJECTILE_PROTECTION" -> Enchantment.PROJECTILE_PROTECTION
                "RESPIRATION" -> Enchantment.RESPIRATION
                "AQUA_AFFINITY" -> Enchantment.AQUA_AFFINITY
                "THORNS" -> Enchantment.THORNS
                "DEPTH_STRIDER" -> Enchantment.DEPTH_STRIDER
                "FROST_WALKER" -> Enchantment.FROST_WALKER
                "SOUL_SPEED" -> Enchantment.SOUL_SPEED
                "SWIFT_SNEAK" -> Enchantment.SWIFT_SNEAK
                else -> null
            }
        }
    }
}

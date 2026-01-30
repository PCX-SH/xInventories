package sh.pcx.xinventories.internal.util

import sh.pcx.xinventories.api.model.InventoryContents
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import sh.pcx.xinventories.internal.model.PlayerAdvancements
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.PlayerStatistics
import sh.pcx.xinventories.internal.serializer.AdvancementSerializer
import sh.pcx.xinventories.internal.serializer.RecipeSerializer
import sh.pcx.xinventories.internal.serializer.StatisticsSerializer
import org.bukkit.GameMode
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.time.Instant
import java.util.UUID

/**
 * Serializes and deserializes complete PlayerData for storage.
 */
object PlayerDataSerializer {

    /**
     * Serializes PlayerData to a YAML configuration section.
     */
    fun toYaml(data: PlayerData, section: ConfigurationSection) {
        section.set("uuid", data.uuid.toString())
        section.set("player-name", data.playerName)
        section.set("group", data.group)
        section.set("gamemode", data.gameMode.name)
        section.set("timestamp", data.timestamp.toEpochMilli())

        // State
        section.set("health", data.health)
        section.set("max-health", data.maxHealth)
        section.set("food-level", data.foodLevel)
        section.set("saturation", data.saturation.toDouble())
        section.set("exhaustion", data.exhaustion.toDouble())
        section.set("experience", data.experience.toDouble())
        section.set("level", data.level)
        section.set("total-experience", data.totalExperience)

        // Inventories
        section.set("inventory.main", InventorySerializer.inventoryToYamlMap(data.mainInventory))
        section.set("inventory.armor", InventorySerializer.inventoryToYamlMap(data.armorInventory))
        section.set("inventory.offhand", data.offhand?.serialize())
        section.set("inventory.ender-chest", InventorySerializer.inventoryToYamlMap(data.enderChest))

        // Potion effects
        section.set("potion-effects", PotionSerializer.serializeEffects(data.potionEffects))

        // Economy balances
        if (data.balances.isNotEmpty()) {
            val balancesSection = section.createSection("balances")
            data.balances.forEach { (group, balance) ->
                balancesSection.set(group, balance)
            }
        }

        // Version for sync
        section.set("version", data.version)

        // Statistics (optional - only if present)
        data.statistics?.let { stats ->
            if (stats.isNotEmpty()) {
                val statsSection = section.createSection("statistics")
                stats.statistics.forEach { (key, value) ->
                    statsSection.set(key, value)
                }
            }
        }

        // Advancements (optional - only if present)
        data.advancements?.let { advs ->
            if (advs.isNotEmpty()) {
                section.set("advancements", AdvancementSerializer.serializeToList(advs))
            }
        }

        // Recipes (optional - only if present)
        data.recipes?.let { recs ->
            if (recs.isNotEmpty()) {
                section.set("recipes", RecipeSerializer.serializeToList(recs))
            }
        }

        // PWI-style player state
        section.set("is-flying", data.isFlying)
        section.set("allow-flight", data.allowFlight)
        data.displayName?.let { section.set("display-name", it) }
        section.set("fall-distance", data.fallDistance.toDouble())
        section.set("fire-ticks", data.fireTicks)
        section.set("maximum-air", data.maximumAir)
        section.set("remaining-air", data.remainingAir)
    }

    /**
     * Deserializes PlayerData from a YAML configuration section.
     */
    @Suppress("UNCHECKED_CAST")
    fun fromYaml(section: ConfigurationSection): PlayerData? {
        return try {
            val uuid = section.getString("uuid")?.let { UUID.fromString(it) } ?: return null
            val playerName = section.getString("player-name") ?: return null
            val group = section.getString("group") ?: return null
            val gameMode = section.getString("gamemode")?.let {
                try { GameMode.valueOf(it) } catch (e: Exception) { GameMode.SURVIVAL }
            } ?: GameMode.SURVIVAL

            val data = PlayerData(uuid, playerName, group, gameMode)

            data.timestamp = section.getLong("timestamp", System.currentTimeMillis())
                .let { Instant.ofEpochMilli(it) }

            // State
            data.health = section.getDouble("health", 20.0)
            data.maxHealth = section.getDouble("max-health", 20.0)
            data.foodLevel = section.getInt("food-level", 20)
            data.saturation = section.getDouble("saturation", 5.0).toFloat()
            data.exhaustion = section.getDouble("exhaustion", 0.0).toFloat()
            data.experience = section.getDouble("experience", 0.0).toFloat()
            data.level = section.getInt("level", 0)
            data.totalExperience = section.getInt("total-experience", 0)

            // Inventories
            val mainMap = section.getConfigurationSection("inventory.main")?.getValues(false)
            val armorMap = section.getConfigurationSection("inventory.armor")?.getValues(false)
            val enderMap = section.getConfigurationSection("inventory.ender-chest")?.getValues(false)

            data.mainInventory.putAll(InventorySerializer.yamlMapToInventory(mainMap))
            data.armorInventory.putAll(InventorySerializer.yamlMapToInventory(armorMap))
            data.offhand = InventorySerializer.mapToItemStack(
                section.getConfigurationSection("inventory.offhand")?.getValues(false)
            )
            data.enderChest.putAll(InventorySerializer.yamlMapToInventory(enderMap))

            // Potion effects
            val effectsList = section.getList("potion-effects") as? List<Map<String, Any>>
            data.potionEffects.addAll(PotionSerializer.deserializeEffects(effectsList))

            // Economy balances
            section.getConfigurationSection("balances")?.let { balancesSection ->
                balancesSection.getKeys(false).forEach { groupName ->
                    data.balances[groupName] = balancesSection.getDouble(groupName)
                }
            }

            // Version for sync
            data.version = section.getLong("version", 0)

            // Statistics (optional)
            section.getConfigurationSection("statistics")?.let { statsSection ->
                val statsMap = statsSection.getKeys(false).associateWith { key ->
                    statsSection.getInt(key)
                }
                if (statsMap.isNotEmpty()) {
                    data.statistics = PlayerStatistics(statsMap)
                }
            }

            // Advancements (optional)
            section.getStringList("advancements").takeIf { it.isNotEmpty() }?.let { advList ->
                data.advancements = AdvancementSerializer.deserializeFromList(advList)
            }

            // Recipes (optional)
            section.getStringList("recipes").takeIf { it.isNotEmpty() }?.let { recList ->
                data.recipes = RecipeSerializer.deserializeFromList(recList)
            }

            // PWI-style player state
            data.isFlying = section.getBoolean("is-flying", false)
            data.allowFlight = section.getBoolean("allow-flight", false)
            data.displayName = section.getString("display-name")
            data.fallDistance = section.getDouble("fall-distance", 0.0).toFloat()
            data.fireTicks = section.getInt("fire-ticks", 0)
            data.maximumAir = section.getInt("maximum-air", 300)
            data.remainingAir = section.getInt("remaining-air", 300)

            data
        } catch (e: Exception) {
            Logging.error("Failed to deserialize player data from YAML", e)
            null
        }
    }

    /**
     * Deserializes PlayerData from a YAML configuration section with default values.
     * Used when the section might not contain uuid/group/gamemode fields.
     */
    @Suppress("UNCHECKED_CAST")
    fun fromYamlSection(
        section: ConfigurationSection,
        defaultUuid: UUID,
        defaultGroup: String,
        defaultGameMode: GameMode
    ): PlayerData? {
        return try {
            val uuid = section.getString("uuid")?.let { UUID.fromString(it) } ?: defaultUuid
            val playerName = section.getString("player-name") ?: "Unknown"
            val group = section.getString("group") ?: defaultGroup
            val gameMode = section.getString("gamemode")?.let {
                try { GameMode.valueOf(it) } catch (e: Exception) { defaultGameMode }
            } ?: defaultGameMode

            val data = PlayerData(uuid, playerName, group, gameMode)

            data.timestamp = section.getLong("timestamp", System.currentTimeMillis())
                .let { Instant.ofEpochMilli(it) }

            // State
            data.health = section.getDouble("health", 20.0)
            data.maxHealth = section.getDouble("max-health", 20.0)
            data.foodLevel = section.getInt("food-level", 20)
            data.saturation = section.getDouble("saturation", 5.0).toFloat()
            data.exhaustion = section.getDouble("exhaustion", 0.0).toFloat()
            data.experience = section.getDouble("experience", 0.0).toFloat()
            data.level = section.getInt("level", 0)
            data.totalExperience = section.getInt("total-experience", 0)

            // Inventories
            val mainMap = section.getConfigurationSection("inventory.main")?.getValues(false)
            val armorMap = section.getConfigurationSection("inventory.armor")?.getValues(false)
            val enderMap = section.getConfigurationSection("inventory.ender-chest")?.getValues(false)

            data.mainInventory.putAll(InventorySerializer.yamlMapToInventory(mainMap))
            data.armorInventory.putAll(InventorySerializer.yamlMapToInventory(armorMap))
            data.offhand = InventorySerializer.mapToItemStack(
                section.getConfigurationSection("inventory.offhand")?.getValues(false)
            )
            data.enderChest.putAll(InventorySerializer.yamlMapToInventory(enderMap))

            // Potion effects
            val effectsList = section.getList("potion-effects") as? List<Map<String, Any>>
            data.potionEffects.addAll(PotionSerializer.deserializeEffects(effectsList))

            // Economy balances
            section.getConfigurationSection("balances")?.let { balancesSection ->
                balancesSection.getKeys(false).forEach { groupName ->
                    data.balances[groupName] = balancesSection.getDouble(groupName)
                }
            }

            // Version for sync
            data.version = section.getLong("version", 0)

            // Statistics (optional)
            section.getConfigurationSection("statistics")?.let { statsSection ->
                val statsMap = statsSection.getKeys(false).associateWith { key ->
                    statsSection.getInt(key)
                }
                if (statsMap.isNotEmpty()) {
                    data.statistics = PlayerStatistics(statsMap)
                }
            }

            // Advancements (optional)
            section.getStringList("advancements").takeIf { it.isNotEmpty() }?.let { advList ->
                data.advancements = AdvancementSerializer.deserializeFromList(advList)
            }

            // Recipes (optional)
            section.getStringList("recipes").takeIf { it.isNotEmpty() }?.let { recList ->
                data.recipes = RecipeSerializer.deserializeFromList(recList)
            }

            // PWI-style player state
            data.isFlying = section.getBoolean("is-flying", false)
            data.allowFlight = section.getBoolean("allow-flight", false)
            data.displayName = section.getString("display-name")
            data.fallDistance = section.getDouble("fall-distance", 0.0).toFloat()
            data.fireTicks = section.getInt("fire-ticks", 0)
            data.maximumAir = section.getInt("maximum-air", 300)
            data.remainingAir = section.getInt("remaining-air", 300)

            data
        } catch (e: Exception) {
            Logging.error("Failed to deserialize player data from YAML section", e)
            null
        }
    }

    /**
     * Serializes PlayerData to a map for SQL storage (Base64 encoded).
     */
    fun toSqlMap(data: PlayerData): Map<String, Any?> {
        return mapOf(
            "uuid" to data.uuid.toString(),
            "player_name" to data.playerName,
            "group_name" to data.group,
            "gamemode" to data.gameMode.name,
            "timestamp" to data.timestamp.toEpochMilli(),
            "health" to data.health,
            "max_health" to data.maxHealth,
            "food_level" to data.foodLevel,
            "saturation" to data.saturation,
            "exhaustion" to data.exhaustion,
            "experience" to data.experience,
            "level" to data.level,
            "total_experience" to data.totalExperience,
            "main_inventory" to InventorySerializer.serializeInventoryMap(data.mainInventory),
            "armor_inventory" to InventorySerializer.serializeInventoryMap(data.armorInventory),
            "offhand" to InventorySerializer.serializeItemStack(data.offhand),
            "ender_chest" to InventorySerializer.serializeInventoryMap(data.enderChest),
            "potion_effects" to PotionSerializer.serializeEffectsToString(data.potionEffects),
            "balances" to serializeBalancesMap(data.balances),
            "version" to data.version,
            "statistics" to (data.statistics?.let { StatisticsSerializer.serializeToString(it) } ?: ""),
            "advancements" to (data.advancements?.let { AdvancementSerializer.serializeToString(it) } ?: ""),
            "recipes" to (data.recipes?.let { RecipeSerializer.serializeToString(it) } ?: ""),
            // PWI-style player state
            "is_flying" to data.isFlying,
            "allow_flight" to data.allowFlight,
            "display_name" to (data.displayName ?: ""),
            "fall_distance" to data.fallDistance,
            "fire_ticks" to data.fireTicks,
            "maximum_air" to data.maximumAir,
            "remaining_air" to data.remainingAir
        )
    }

    /**
     * Serializes economy balances to JSON string.
     */
    private fun serializeBalancesMap(balances: Map<String, Double>): String {
        if (balances.isEmpty()) return ""
        return balances.entries.joinToString(";") { "${it.key}=${it.value}" }
    }

    /**
     * Deserializes economy balances from JSON string.
     */
    private fun deserializeBalancesMap(str: String?): Map<String, Double> {
        if (str.isNullOrEmpty()) return emptyMap()
        return str.split(";")
            .filter { it.contains("=") }
            .associate {
                val parts = it.split("=", limit = 2)
                parts[0] to (parts.getOrNull(1)?.toDoubleOrNull() ?: 0.0)
            }
    }

    /**
     * Deserializes PlayerData from SQL row data.
     */
    fun fromSqlMap(row: Map<String, Any?>): PlayerData? {
        return try {
            val uuid = (row["uuid"] as? String)?.let { UUID.fromString(it) } ?: return null
            val playerName = row["player_name"] as? String ?: return null
            val group = row["group_name"] as? String ?: return null
            val gameMode = (row["gamemode"] as? String)?.let {
                try { GameMode.valueOf(it) } catch (e: Exception) { GameMode.SURVIVAL }
            } ?: GameMode.SURVIVAL

            val data = PlayerData(uuid, playerName, group, gameMode)

            data.timestamp = (row["timestamp"] as? Number)?.toLong()
                ?.let { Instant.ofEpochMilli(it) } ?: Instant.now()

            data.health = (row["health"] as? Number)?.toDouble() ?: 20.0
            data.maxHealth = (row["max_health"] as? Number)?.toDouble() ?: 20.0
            data.foodLevel = (row["food_level"] as? Number)?.toInt() ?: 20
            data.saturation = (row["saturation"] as? Number)?.toFloat() ?: 5.0f
            data.exhaustion = (row["exhaustion"] as? Number)?.toFloat() ?: 0.0f
            data.experience = (row["experience"] as? Number)?.toFloat() ?: 0.0f
            data.level = (row["level"] as? Number)?.toInt() ?: 0
            data.totalExperience = (row["total_experience"] as? Number)?.toInt() ?: 0

            val mainInv = row["main_inventory"] as? String ?: ""
            val armorInv = row["armor_inventory"] as? String ?: ""
            val offhandStr = row["offhand"] as? String ?: ""
            val enderInv = row["ender_chest"] as? String ?: ""
            val effectsStr = row["potion_effects"] as? String ?: ""

            data.mainInventory.putAll(InventorySerializer.deserializeInventoryMap(mainInv))
            data.armorInventory.putAll(InventorySerializer.deserializeInventoryMap(armorInv))
            data.offhand = InventorySerializer.deserializeItemStack(offhandStr)
            data.enderChest.putAll(InventorySerializer.deserializeInventoryMap(enderInv))
            data.potionEffects.addAll(PotionSerializer.deserializeEffectsFromString(effectsStr))

            // Economy balances
            val balancesStr = row["balances"] as? String
            data.balances.putAll(deserializeBalancesMap(balancesStr))

            // Version for sync
            data.version = (row["version"] as? Number)?.toLong() ?: 0

            // Statistics (optional)
            val statsStr = row["statistics"] as? String
            if (!statsStr.isNullOrBlank()) {
                data.statistics = StatisticsSerializer.deserializeFromString(statsStr)
            }

            // Advancements (optional)
            val advsStr = row["advancements"] as? String
            if (!advsStr.isNullOrBlank()) {
                data.advancements = AdvancementSerializer.deserializeFromString(advsStr)
            }

            // Recipes (optional)
            val recsStr = row["recipes"] as? String
            if (!recsStr.isNullOrBlank()) {
                data.recipes = RecipeSerializer.deserializeFromString(recsStr)
            }

            // PWI-style player state (optional - backwards compatible with older DBs)
            data.isFlying = (row["is_flying"] as? Boolean) ?: (row["is_flying"] as? Number)?.toInt() == 1
            data.allowFlight = (row["allow_flight"] as? Boolean) ?: (row["allow_flight"] as? Number)?.toInt() == 1
            val displayNameStr = row["display_name"] as? String
            data.displayName = if (displayNameStr.isNullOrBlank()) null else displayNameStr
            data.fallDistance = (row["fall_distance"] as? Number)?.toFloat() ?: 0.0f
            data.fireTicks = (row["fire_ticks"] as? Number)?.toInt() ?: 0
            data.maximumAir = (row["maximum_air"] as? Number)?.toInt() ?: 300
            data.remainingAir = (row["remaining_air"] as? Number)?.toInt() ?: 300

            data
        } catch (e: Exception) {
            Logging.error("Failed to deserialize player data from SQL", e)
            null
        }
    }

    /**
     * Serializes a PlayerInventorySnapshot to YAML string.
     */
    fun snapshotToYamlString(snapshot: PlayerInventorySnapshot): String {
        val yaml = YamlConfiguration()
        val data = PlayerData.fromSnapshot(snapshot)
        toYaml(data, yaml)
        return yaml.saveToString()
    }

    /**
     * Deserializes a PlayerInventorySnapshot from YAML string.
     */
    fun snapshotFromYamlString(yamlString: String): PlayerInventorySnapshot? {
        return try {
            val yaml = YamlConfiguration()
            yaml.loadFromString(yamlString)
            fromYaml(yaml)?.toSnapshot()
        } catch (e: Exception) {
            Logging.error("Failed to parse snapshot from YAML string", e)
            null
        }
    }

    /**
     * Creates a cache key for player data.
     */
    fun cacheKey(uuid: UUID, group: String, gameMode: GameMode?): String {
        return if (gameMode != null) {
            "${uuid}_${group}_${gameMode.name}"
        } else {
            "${uuid}_${group}"
        }
    }

    /**
     * Parses a cache key back to components.
     */
    fun parseCacheKey(key: String): Triple<UUID, String, GameMode?>? {
        return try {
            val parts = key.split("_")
            if (parts.size < 2) return null

            val uuid = UUID.fromString(parts[0])
            val group = parts[1]
            val gameMode = if (parts.size > 2) {
                try { GameMode.valueOf(parts[2]) } catch (e: Exception) { null }
            } else null

            Triple(uuid, group, gameMode)
        } catch (e: Exception) {
            null
        }
    }
}

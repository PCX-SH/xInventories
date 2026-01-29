package sh.pcx.xinventories.internal.util

import sh.pcx.xinventories.api.model.InventoryContents
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import sh.pcx.xinventories.internal.model.PlayerData
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

            data
        } catch (e: Exception) {
            Logging.error("Failed to deserialize player data from YAML", e)
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
            "potion_effects" to PotionSerializer.serializeEffectsToString(data.potionEffects)
        )
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

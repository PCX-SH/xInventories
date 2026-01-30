package sh.pcx.xinventories.internal.model

import org.bukkit.GameMode
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import java.time.Instant
import java.util.UUID

/**
 * Wrapper for player data imported from another plugin.
 * Contains the converted data along with source information for tracking.
 */
data class ImportedPlayerData(
    /**
     * The player's UUID.
     */
    val uuid: UUID,

    /**
     * The player's name (may be outdated).
     */
    val playerName: String,

    /**
     * The source group this data came from.
     */
    val sourceGroup: String,

    /**
     * The game mode this data is for (if applicable).
     */
    val gameMode: GameMode?,

    /**
     * Main inventory contents (slot -> item).
     */
    val mainInventory: Map<Int, ItemStack>,

    /**
     * Armor inventory contents (0=boots, 1=leggings, 2=chestplate, 3=helmet).
     */
    val armorInventory: Map<Int, ItemStack>,

    /**
     * Offhand item.
     */
    val offhand: ItemStack?,

    /**
     * Ender chest contents.
     */
    val enderChest: Map<Int, ItemStack>,

    /**
     * Player health (0.0 - max health).
     */
    val health: Double,

    /**
     * Max health value.
     */
    val maxHealth: Double,

    /**
     * Food level (0 - 20).
     */
    val foodLevel: Int,

    /**
     * Saturation level.
     */
    val saturation: Float,

    /**
     * Exhaustion level.
     */
    val exhaustion: Float,

    /**
     * Experience progress (0.0 - 1.0).
     */
    val experience: Float,

    /**
     * Experience level.
     */
    val level: Int,

    /**
     * Total experience points.
     */
    val totalExperience: Int,

    /**
     * Active potion effects.
     */
    val potionEffects: List<PotionEffect>,

    /**
     * Economy balance from source (if available).
     */
    val balance: Double?,

    /**
     * When this data was last modified in the source.
     */
    val sourceTimestamp: Instant?,

    /**
     * The import source identifier (e.g., "pwi", "mvi", "myworlds").
     */
    val sourceId: String,

    /**
     * Additional metadata from the source.
     */
    val metadata: Map<String, String> = emptyMap()
) {

    /**
     * Converts this imported data to xInventories PlayerData format.
     *
     * @param targetGroup The xInventories group to assign to
     * @return Converted PlayerData ready for storage
     */
    fun toPlayerData(targetGroup: String): PlayerData {
        val playerData = PlayerData(
            uuid = uuid,
            playerName = playerName,
            group = targetGroup,
            gameMode = gameMode ?: GameMode.SURVIVAL
        )

        // Copy inventory contents
        mainInventory.forEach { (slot, item) ->
            playerData.mainInventory[slot] = item.clone()
        }

        armorInventory.forEach { (slot, item) ->
            playerData.armorInventory[slot] = item.clone()
        }

        playerData.offhand = offhand?.clone()

        enderChest.forEach { (slot, item) ->
            playerData.enderChest[slot] = item.clone()
        }

        // Copy state
        playerData.health = health
        playerData.maxHealth = maxHealth
        playerData.foodLevel = foodLevel
        playerData.saturation = saturation
        playerData.exhaustion = exhaustion
        playerData.experience = experience
        playerData.level = level
        playerData.totalExperience = totalExperience

        // Copy potion effects
        playerData.potionEffects.addAll(potionEffects)

        // Set timestamp
        playerData.timestamp = sourceTimestamp ?: Instant.now()

        return playerData
    }

    /**
     * Gets a summary of the inventory contents.
     */
    fun getInventorySummary(): String = buildString {
        val totalItems = mainInventory.size + armorInventory.size +
                         (if (offhand != null) 1 else 0) + enderChest.size
        append("$totalItems items")
        if (balance != null) {
            append(", balance: ${"%.2f".format(balance)}")
        }
        append(", level: $level")
    }

    companion object {
        /**
         * Creates empty imported player data.
         */
        fun empty(
            uuid: UUID,
            playerName: String,
            sourceGroup: String,
            sourceId: String,
            gameMode: GameMode? = null
        ) = ImportedPlayerData(
            uuid = uuid,
            playerName = playerName,
            sourceGroup = sourceGroup,
            gameMode = gameMode,
            mainInventory = emptyMap(),
            armorInventory = emptyMap(),
            offhand = null,
            enderChest = emptyMap(),
            health = 20.0,
            maxHealth = 20.0,
            foodLevel = 20,
            saturation = 5.0f,
            exhaustion = 0.0f,
            experience = 0.0f,
            level = 0,
            totalExperience = 0,
            potionEffects = emptyList(),
            balance = null,
            sourceTimestamp = null,
            sourceId = sourceId
        )
    }
}

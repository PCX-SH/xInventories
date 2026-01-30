package sh.pcx.xinventories.internal.model

import sh.pcx.xinventories.api.model.InventoryContents
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import org.bukkit.GameMode
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import java.time.Instant
import java.util.UUID

/**
 * Internal mutable player data representation.
 * Used for storage and manipulation before converting to immutable snapshots.
 */
class PlayerData(
    val uuid: UUID,
    var playerName: String,
    var group: String,
    var gameMode: GameMode
) {
    // Inventory contents
    val mainInventory: MutableMap<Int, ItemStack> = mutableMapOf()
    val armorInventory: MutableMap<Int, ItemStack> = mutableMapOf()
    var offhand: ItemStack? = null
    val enderChest: MutableMap<Int, ItemStack> = mutableMapOf()

    // Player state
    var health: Double = 20.0
    var maxHealth: Double = 20.0
    var foodLevel: Int = 20
    var saturation: Float = 5.0f
    var exhaustion: Float = 0.0f
    var experience: Float = 0.0f
    var level: Int = 0
    var totalExperience: Int = 0
    val potionEffects: MutableList<PotionEffect> = mutableListOf()

    // Metadata
    var timestamp: Instant = Instant.now()
    var dirty: Boolean = false

    // Version tracking for cross-server sync conflict detection
    // Increment on each save for conflict resolution
    var version: Long = 0

    // Per-group economy balances
    // Map of group name to balance - used when separateEconomy is enabled per group
    val balances: MutableMap<String, Double> = mutableMapOf()

    /**
     * Clears all inventory contents.
     */
    fun clearInventory() {
        mainInventory.clear()
        armorInventory.clear()
        offhand = null
        enderChest.clear()
        dirty = true
    }

    /**
     * Clears all potion effects.
     */
    fun clearEffects() {
        potionEffects.clear()
        dirty = true
    }

    /**
     * Resets player state to defaults.
     */
    fun resetState() {
        health = 20.0
        maxHealth = 20.0
        foodLevel = 20
        saturation = 5.0f
        exhaustion = 0.0f
        experience = 0.0f
        level = 0
        totalExperience = 0
        potionEffects.clear()
        dirty = true
    }

    /**
     * Loads data from a player's current state.
     */
    @Suppress("UnstableApiUsage")
    fun loadFromPlayer(player: Player) {
        playerName = player.name
        gameMode = player.gameMode

        // Main inventory
        mainInventory.clear()
        for (i in 0..35) {
            player.inventory.getItem(i)?.let { item ->
                if (!item.type.isAir) {
                    mainInventory[i] = item.clone()
                }
            }
        }

        // Armor
        armorInventory.clear()
        player.inventory.armorContents.forEachIndexed { index, item ->
            item?.let {
                if (!it.type.isAir) {
                    armorInventory[index] = it.clone()
                }
            }
        }

        // Offhand
        offhand = player.inventory.itemInOffHand.let {
            if (it.type.isAir) null else it.clone()
        }

        // Ender chest
        enderChest.clear()
        for (i in 0 until player.enderChest.size) {
            player.enderChest.getItem(i)?.let { item ->
                if (!item.type.isAir) {
                    enderChest[i] = item.clone()
                }
            }
        }

        // State
        health = player.health
        maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
        foodLevel = player.foodLevel
        saturation = player.saturation
        exhaustion = player.exhaustion
        experience = player.exp
        level = player.level
        totalExperience = player.totalExperience

        // Effects
        potionEffects.clear()
        potionEffects.addAll(player.activePotionEffects)

        timestamp = Instant.now()
        dirty = false
    }

    /**
     * Applies this data to a player.
     */
    @Suppress("UnstableApiUsage")
    fun applyToPlayer(player: Player, settings: sh.pcx.xinventories.api.model.GroupSettings) {
        // Clear current inventory
        player.inventory.clear()

        // Apply main inventory
        mainInventory.forEach { (slot, item) ->
            player.inventory.setItem(slot, item.clone())
        }

        // Apply armor
        val armorContents = arrayOfNulls<ItemStack>(4)
        armorInventory.forEach { (slot, item) ->
            armorContents[slot] = item.clone()
        }
        player.inventory.armorContents = armorContents

        // Apply offhand
        player.inventory.setItemInOffHand(offhand?.clone())

        // Apply ender chest if enabled
        if (settings.saveEnderChest) {
            player.enderChest.clear()
            enderChest.forEach { (slot, item) ->
                player.enderChest.setItem(slot, item.clone())
            }
        }

        // Apply state based on settings
        if (settings.saveHealth) {
            val maxHealthAttr = player.getAttribute(Attribute.MAX_HEALTH)
            if (maxHealthAttr != null) {
                maxHealthAttr.baseValue = maxHealth
            }
            player.health = health.coerceIn(0.0, player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0)
        }

        if (settings.saveHunger) {
            player.foodLevel = foodLevel
        }

        if (settings.saveSaturation) {
            player.saturation = saturation
        }

        if (settings.saveExhaustion) {
            player.exhaustion = exhaustion
        }

        if (settings.saveExperience) {
            player.exp = experience
            player.level = level
            player.totalExperience = totalExperience
        }

        if (settings.savePotionEffects) {
            // Clear existing effects
            player.activePotionEffects.forEach { effect ->
                player.removePotionEffect(effect.type)
            }
            // Apply saved effects
            potionEffects.forEach { effect ->
                player.addPotionEffect(effect)
            }
        }

        if (settings.saveGameMode) {
            player.gameMode = gameMode
        }
    }

    /**
     * Converts to an immutable snapshot.
     */
    fun toSnapshot(): PlayerInventorySnapshot = PlayerInventorySnapshot(
        uuid = uuid,
        playerName = playerName,
        group = group,
        gameMode = gameMode,
        contents = InventoryContents(
            main = mainInventory.mapValues { it.value.clone() },
            armor = armorInventory.mapValues { it.value.clone() },
            offhand = offhand?.clone(),
            enderChest = enderChest.mapValues { it.value.clone() }
        ),
        health = health,
        maxHealth = maxHealth,
        foodLevel = foodLevel,
        saturation = saturation,
        exhaustion = exhaustion,
        experience = experience,
        level = level,
        totalExperience = totalExperience,
        potionEffects = potionEffects.toList(),
        timestamp = timestamp
    )

    /**
     * Loads data from a snapshot.
     */
    fun loadFromSnapshot(snapshot: PlayerInventorySnapshot) {
        playerName = snapshot.playerName
        group = snapshot.group
        gameMode = snapshot.gameMode

        mainInventory.clear()
        snapshot.contents.main.forEach { (slot, item) ->
            mainInventory[slot] = item.clone()
        }

        armorInventory.clear()
        snapshot.contents.armor.forEach { (slot, item) ->
            armorInventory[slot] = item.clone()
        }

        offhand = snapshot.contents.offhand?.clone()

        enderChest.clear()
        snapshot.contents.enderChest.forEach { (slot, item) ->
            enderChest[slot] = item.clone()
        }

        health = snapshot.health
        maxHealth = snapshot.maxHealth
        foodLevel = snapshot.foodLevel
        saturation = snapshot.saturation
        exhaustion = snapshot.exhaustion
        experience = snapshot.experience
        level = snapshot.level
        totalExperience = snapshot.totalExperience

        potionEffects.clear()
        potionEffects.addAll(snapshot.potionEffects)

        timestamp = snapshot.timestamp
        dirty = false
    }

    companion object {
        /**
         * Creates PlayerData from a player's current state.
         */
        fun fromPlayer(player: Player, group: String): PlayerData {
            return PlayerData(
                uuid = player.uniqueId,
                playerName = player.name,
                group = group,
                gameMode = player.gameMode
            ).also { it.loadFromPlayer(player) }
        }

        /**
         * Creates PlayerData from a snapshot.
         */
        fun fromSnapshot(snapshot: PlayerInventorySnapshot): PlayerData {
            return PlayerData(
                uuid = snapshot.uuid,
                playerName = snapshot.playerName,
                group = snapshot.group,
                gameMode = snapshot.gameMode
            ).also { it.loadFromSnapshot(snapshot) }
        }

        /**
         * Creates empty PlayerData.
         */
        fun empty(uuid: UUID, playerName: String, group: String, gameMode: GameMode): PlayerData {
            return PlayerData(uuid, playerName, group, gameMode)
        }
    }
}

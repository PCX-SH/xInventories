package sh.pcx.xinventories.internal.model

import sh.pcx.xinventories.api.model.InventoryContents
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import sh.pcx.xinventories.internal.compat.AttributeCompat
import sh.pcx.xinventories.internal.serializer.AdvancementSerializer
import sh.pcx.xinventories.internal.serializer.RecipeSerializer
import sh.pcx.xinventories.internal.serializer.StatisticsSerializer
import org.bukkit.GameMode
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

    // PWI-style player state
    var isFlying: Boolean = false
    var allowFlight: Boolean = false
    var displayName: String? = null
    var fallDistance: Float = 0.0f
    var fireTicks: Int = 0
    var maximumAir: Int = 300
    var remainingAir: Int = 300

    // Metadata
    var timestamp: Instant = Instant.now()
    var dirty: Boolean = false

    // Version tracking for cross-server sync conflict detection
    // Increment on each save for conflict resolution
    var version: Long = 0

    // Per-group economy balances
    // Map of group name to balance - used when separateEconomy is enabled per group
    val balances: MutableMap<String, Double> = mutableMapOf()

    // Per-group player statistics (opt-in)
    // Stored as a map of statistic keys to values
    var statistics: PlayerStatistics? = null

    // Per-group player advancements (opt-in)
    // Stored as a set of completed advancement keys
    var advancements: PlayerAdvancements? = null

    // Per-group discovered recipes (opt-in)
    // Stored as a set of recipe keys
    var recipes: Set<String>? = null

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
        statistics = null
        advancements = null
        recipes = null
        isFlying = false
        allowFlight = false
        displayName = null
        fallDistance = 0.0f
        fireTicks = 0
        maximumAir = 300
        remainingAir = 300
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
        maxHealth = AttributeCompat.getMaxHealth(player)?.value ?: 20.0
        foodLevel = player.foodLevel
        saturation = player.saturation
        exhaustion = player.exhaustion
        experience = player.exp
        level = player.level
        totalExperience = player.totalExperience

        // Effects
        potionEffects.clear()
        potionEffects.addAll(player.activePotionEffects)

        // PWI-style player state
        isFlying = player.isFlying
        allowFlight = player.allowFlight
        displayName = player.displayName
        fallDistance = player.fallDistance
        fireTicks = player.fireTicks
        maximumAir = player.maximumAir
        remainingAir = player.remainingAir

        // Note: Statistics, advancements, and recipes are loaded separately
        // via loadFromPlayerExtended() when their respective settings are enabled.
        // This is to avoid performance impact when these features are disabled.

        timestamp = Instant.now()
        dirty = false
    }

    /**
     * Loads extended player data (statistics, advancements, recipes) based on settings.
     * This is separate from loadFromPlayer() for performance reasons.
     */
    fun loadFromPlayerExtended(player: Player, settings: sh.pcx.xinventories.api.model.GroupSettings) {
        if (settings.saveStatistics) {
            statistics = StatisticsSerializer.collectFromPlayer(player)
        }

        if (settings.saveAdvancements) {
            advancements = AdvancementSerializer.collectFromPlayer(player)
        }

        if (settings.saveRecipes) {
            recipes = RecipeSerializer.collectFromPlayer(player)
        }
    }

    /**
     * Applies this data to a player.
     */
    @Suppress("UnstableApiUsage")
    fun applyToPlayer(player: Player, settings: sh.pcx.xinventories.api.model.GroupSettings) {
        // Apply inventory only if save-inventory is enabled
        if (settings.saveInventory) {
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
        }

        // Apply ender chest if enabled
        if (settings.saveEnderChest) {
            player.enderChest.clear()
            enderChest.forEach { (slot, item) ->
                player.enderChest.setItem(slot, item.clone())
            }
        }

        // Apply state based on settings
        if (settings.saveHealth) {
            val maxHealthAttr = AttributeCompat.getMaxHealth(player)
            if (maxHealthAttr != null) {
                maxHealthAttr.baseValue = maxHealth
            }
            player.health = health.coerceIn(0.0, AttributeCompat.getMaxHealth(player)?.value ?: 20.0)
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

        // Apply statistics if enabled and saved
        if (settings.saveStatistics && statistics != null) {
            StatisticsSerializer.applyToPlayer(player, statistics!!)
        }

        // Apply advancements if enabled and saved
        if (settings.saveAdvancements && advancements != null) {
            AdvancementSerializer.applyToPlayer(player, advancements!!)
        }

        // Apply recipes if enabled and saved
        if (settings.saveRecipes && recipes != null) {
            RecipeSerializer.applyToPlayer(player, recipes!!)
        }

        // Apply PWI-style player state
        if (settings.saveAllowFlight) {
            player.allowFlight = allowFlight
        }

        if (settings.saveFlying) {
            // Only set flying if player is allowed to fly
            if (player.allowFlight || settings.saveAllowFlight && allowFlight) {
                player.isFlying = isFlying
            }
        }

        if (settings.saveDisplayName && displayName != null) {
            player.setDisplayName(displayName)
        }

        if (settings.saveFallDistance) {
            player.fallDistance = fallDistance
        }

        if (settings.saveFireTicks) {
            player.fireTicks = fireTicks
        }

        if (settings.saveMaximumAir) {
            player.maximumAir = maximumAir
        }

        if (settings.saveRemainingAir) {
            player.remainingAir = remainingAir.coerceAtMost(player.maximumAir)
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

package sh.pcx.xinventories.api.model

import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import java.time.Instant

/**
 * Builder for modifying snapshots.
 */
class SnapshotBuilder(snapshot: PlayerInventorySnapshot) {
    private val uuid = snapshot.uuid
    private val playerName = snapshot.playerName
    private val group = snapshot.group
    private val gameMode = snapshot.gameMode

    private var mainInventory: MutableMap<Int, ItemStack> = snapshot.contents.main.toMutableMap()
    private var armorInventory: MutableMap<Int, ItemStack> = snapshot.contents.armor.toMutableMap()
    private var offhandItem: ItemStack? = snapshot.contents.offhand?.clone()
    private var enderChestInventory: MutableMap<Int, ItemStack> = snapshot.contents.enderChest.toMutableMap()

    var health: Double = snapshot.health
    var maxHealth: Double = snapshot.maxHealth
    var foodLevel: Int = snapshot.foodLevel
    var saturation: Float = snapshot.saturation
    var exhaustion: Float = snapshot.exhaustion
    var experience: Float = snapshot.experience
    var level: Int = snapshot.level
    var totalExperience: Int = snapshot.totalExperience
    var potionEffects: MutableList<PotionEffect> = snapshot.potionEffects.toMutableList()

    fun setItem(slot: Int, item: ItemStack?): SnapshotBuilder {
        if (item == null || item.type.isAir) {
            mainInventory.remove(slot)
        } else {
            mainInventory[slot] = item.clone()
        }
        return this
    }

    fun setArmor(slot: Int, item: ItemStack?): SnapshotBuilder {
        if (item == null || item.type.isAir) {
            armorInventory.remove(slot)
        } else {
            armorInventory[slot] = item.clone()
        }
        return this
    }

    fun setOffhand(item: ItemStack?): SnapshotBuilder {
        offhandItem = item?.clone()
        return this
    }

    fun clearInventory(): SnapshotBuilder {
        mainInventory.clear()
        armorInventory.clear()
        offhandItem = null
        return this
    }

    fun clearEffects(): SnapshotBuilder {
        potionEffects.clear()
        return this
    }

    fun addEffect(effect: PotionEffect): SnapshotBuilder {
        potionEffects.add(effect)
        return this
    }

    fun build(): PlayerInventorySnapshot = PlayerInventorySnapshot(
        uuid = uuid,
        playerName = playerName,
        group = group,
        gameMode = gameMode,
        contents = InventoryContents(
            main = mainInventory.toMap(),
            armor = armorInventory.toMap(),
            offhand = offhandItem,
            enderChest = enderChestInventory.toMap()
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
        timestamp = Instant.now()
    )
}

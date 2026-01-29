package sh.pcx.xinventories.api.model

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Inventory contents container.
 */
data class InventoryContents(
    /** Main inventory slots 0-35 */
    val main: Map<Int, ItemStack>,
    /** Armor slots 0-3 (boots, leggings, chestplate, helmet) */
    val armor: Map<Int, ItemStack>,
    /** Offhand item */
    val offhand: ItemStack?,
    /** Ender chest contents */
    val enderChest: Map<Int, ItemStack>
) {
    companion object {
        fun empty(): InventoryContents = InventoryContents(
            main = emptyMap(),
            armor = emptyMap(),
            offhand = null,
            enderChest = emptyMap()
        )

        fun fromPlayer(player: Player): InventoryContents {
            val main = mutableMapOf<Int, ItemStack>()
            val armor = mutableMapOf<Int, ItemStack>()
            val enderChest = mutableMapOf<Int, ItemStack>()

            // Main inventory (slots 0-35)
            for (i in 0..35) {
                player.inventory.getItem(i)?.let { item ->
                    if (!item.type.isAir) {
                        main[i] = item.clone()
                    }
                }
            }

            // Armor (0 = boots, 1 = leggings, 2 = chestplate, 3 = helmet)
            player.inventory.armorContents.forEachIndexed { index, item ->
                item?.let {
                    if (!it.type.isAir) {
                        armor[index] = it.clone()
                    }
                }
            }

            // Offhand
            val offhandItem = player.inventory.itemInOffHand.let {
                if (it.type.isAir) null else it.clone()
            }

            // Ender chest
            for (i in 0 until player.enderChest.size) {
                player.enderChest.getItem(i)?.let { item ->
                    if (!item.type.isAir) {
                        enderChest[i] = item.clone()
                    }
                }
            }

            return InventoryContents(
                main = main,
                armor = armor,
                offhand = offhandItem,
                enderChest = enderChest
            )
        }
    }

    /**
     * Applies these contents to a player.
     */
    fun applyTo(player: Player) {
        // Clear current inventory
        player.inventory.clear()

        // Apply main inventory
        main.forEach { (slot, item) ->
            player.inventory.setItem(slot, item.clone())
        }

        // Apply armor
        val armorContents = arrayOfNulls<ItemStack>(4)
        armor.forEach { (slot, item) ->
            armorContents[slot] = item.clone()
        }
        player.inventory.armorContents = armorContents

        // Apply offhand
        player.inventory.setItemInOffHand(offhand?.clone())

        // Apply ender chest
        player.enderChest.clear()
        enderChest.forEach { (slot, item) ->
            player.enderChest.setItem(slot, item.clone())
        }
    }

    /**
     * Gets total item count across all inventories.
     */
    fun totalItems(): Int {
        var count = main.size + armor.size + enderChest.size
        if (offhand != null) count++
        return count
    }

    /**
     * Checks if all inventories are empty.
     */
    fun isEmpty(): Boolean = main.isEmpty() && armor.isEmpty() && offhand == null && enderChest.isEmpty()
}

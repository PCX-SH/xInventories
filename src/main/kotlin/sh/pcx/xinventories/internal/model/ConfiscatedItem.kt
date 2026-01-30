package sh.pcx.xinventories.internal.model

import org.bukkit.inventory.ItemStack
import java.time.Instant
import java.util.UUID

/**
 * Represents a confiscated item stored in the player vault.
 * Items are confiscated when they violate item restrictions with MOVE_TO_VAULT action.
 */
data class ConfiscatedItem(
    val id: Long = -1,
    val playerUuid: UUID,
    val itemData: String,
    val itemType: String,
    val itemName: String,
    val amount: Int,
    val confiscatedAt: Instant,
    val reason: String,
    val groupName: String,
    val worldName: String?
) {
    /**
     * Deserializes the stored item data back to an ItemStack.
     */
    fun toItemStack(): ItemStack? {
        return sh.pcx.xinventories.internal.util.InventorySerializer.deserializeItemStack(itemData)
    }

    companion object {
        /**
         * Creates a ConfiscatedItem from an ItemStack.
         */
        fun fromItemStack(
            playerUuid: UUID,
            item: ItemStack,
            reason: String,
            groupName: String,
            worldName: String?
        ): ConfiscatedItem {
            val itemData = sh.pcx.xinventories.internal.util.InventorySerializer.serializeItemStack(item)
            val displayName = item.itemMeta?.displayName()?.let {
                net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(it)
            } ?: item.type.name.lowercase().replace("_", " ")

            return ConfiscatedItem(
                playerUuid = playerUuid,
                itemData = itemData,
                itemType = item.type.name,
                itemName = displayName,
                amount = item.amount,
                confiscatedAt = Instant.now(),
                reason = reason,
                groupName = groupName,
                worldName = worldName
            )
        }
    }
}

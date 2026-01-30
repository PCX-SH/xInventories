package sh.pcx.xinventories.api

import sh.pcx.xinventories.internal.model.ItemConfig
import sh.pcx.xinventories.internal.model.SharedSlotEntry
import sh.pcx.xinventories.internal.model.SharedSlotsConfig
import sh.pcx.xinventories.internal.model.SlotMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * API for managing shared inventory slots.
 *
 * Shared slots persist across group switches and can optionally be locked
 * from player modification or synced across all groups.
 *
 * Slot Reference:
 * - Hotbar: 0-8
 * - Inventory: 9-35
 * - Armor: 36 (boots), 37 (leggings), 38 (chestplate), 39 (helmet)
 * - Offhand: 40
 *
 * // TODO: Add to XInventoriesAPI:
 * // val sharedSlots: SharedSlotsAPI
 */
interface SharedSlotsAPI {

    /**
     * Gets the current shared slots configuration.
     *
     * @return The shared slots config
     */
    fun getConfig(): SharedSlotsConfig

    /**
     * Updates the shared slots configuration.
     *
     * @param config The new configuration
     */
    fun setConfig(config: SharedSlotsConfig)

    /**
     * Checks if shared slots are enabled.
     *
     * @return true if enabled
     */
    fun isEnabled(): Boolean

    /**
     * Enables or disables shared slots.
     *
     * @param enabled Whether to enable shared slots
     */
    fun setEnabled(enabled: Boolean)

    /**
     * Checks if a slot is a shared slot.
     *
     * @param slot The slot index (0-40)
     * @return true if the slot is shared
     */
    fun isSharedSlot(slot: Int): Boolean

    /**
     * Checks if a slot is locked (cannot be modified by player).
     *
     * @param slot The slot index
     * @return true if the slot is locked
     */
    fun isLockedSlot(slot: Int): Boolean

    /**
     * Checks if a slot is synced across all groups.
     *
     * @param slot The slot index
     * @return true if the slot is synced
     */
    fun isSyncedSlot(slot: Int): Boolean

    /**
     * Gets the entry configuration for a slot.
     *
     * @param slot The slot index
     * @return The slot entry, or null if not a shared slot
     */
    fun getSlotEntry(slot: Int): SharedSlotEntry?

    /**
     * Adds a new shared slot entry.
     *
     * @param entry The slot entry to add
     * @return true if added successfully
     */
    fun addSlotEntry(entry: SharedSlotEntry): Boolean

    /**
     * Removes a shared slot entry.
     *
     * @param slot The slot index to remove
     * @return true if removed
     */
    fun removeSlotEntry(slot: Int): Boolean

    /**
     * Sets the mode for a shared slot.
     *
     * @param slot The slot index
     * @param mode The slot mode
     * @return true if set successfully
     */
    fun setSlotMode(slot: Int, mode: SlotMode): Boolean

    /**
     * Sets an enforced item for a slot.
     *
     * @param slot The slot index
     * @param item The item config, or null to remove enforcement
     * @return true if set successfully
     */
    fun setEnforcedItem(slot: Int, item: ItemConfig?): Boolean

    /**
     * Gets all shared slot indices.
     *
     * @return Set of all shared slot indices
     */
    fun getAllSharedSlots(): Set<Int>

    /**
     * Gets all slots with a specific mode.
     *
     * @param mode The slot mode
     * @return Set of slot indices with that mode
     */
    fun getSlotsWithMode(mode: SlotMode): Set<Int>

    /**
     * Preserves a player's shared slot contents.
     * Should be called before a group switch.
     *
     * @param player The player
     */
    fun preserveSlots(player: Player)

    /**
     * Restores a player's shared slot contents.
     * Should be called after a group switch.
     *
     * @param player The player
     */
    fun restoreSlots(player: Player)

    /**
     * Gets the cached item for a player's shared slot.
     *
     * @param playerUuid The player's UUID
     * @param slot The slot index
     * @return The cached item, or null if none
     */
    fun getCachedItem(playerUuid: UUID, slot: Int): ItemStack?

    /**
     * Sets the cached item for a player's shared slot.
     *
     * @param playerUuid The player's UUID
     * @param slot The slot index
     * @param item The item, or null to clear
     */
    fun setCachedItem(playerUuid: UUID, slot: Int, item: ItemStack?)

    /**
     * Clears all cached data for a player.
     *
     * @param playerUuid The player's UUID
     */
    fun clearPlayerCache(playerUuid: UUID)

    /**
     * Applies all enforced items to a player.
     *
     * @param player The player
     */
    fun applyEnforcedItems(player: Player)
}

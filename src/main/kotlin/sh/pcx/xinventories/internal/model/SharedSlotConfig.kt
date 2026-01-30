package sh.pcx.xinventories.internal.model

import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Configuration for an enforced item in a shared slot.
 */
data class ItemConfig(
    /**
     * The material type.
     */
    val type: Material,

    /**
     * The item amount.
     */
    val amount: Int = 1,

    /**
     * Custom display name (supports color codes).
     */
    val displayName: String? = null,

    /**
     * Custom lore lines (supports color codes).
     */
    val lore: List<String>? = null
) {
    /**
     * Creates an ItemStack from this config.
     */
    fun toItemStack(): ItemStack {
        val item = ItemStack(type, amount)
        val meta = item.itemMeta

        if (meta != null) {
            if (displayName != null) {
                meta.setDisplayName(displayName.replace('&', '\u00A7'))
            }
            if (lore != null) {
                meta.lore = lore.map { it.replace('&', '\u00A7') }
            }
            item.itemMeta = meta
        }

        return item
    }

    companion object {
        /**
         * Creates an ItemConfig from an ItemStack.
         */
        fun fromItemStack(item: ItemStack): ItemConfig {
            val meta = item.itemMeta
            return ItemConfig(
                type = item.type,
                amount = item.amount,
                displayName = meta?.displayName,
                lore = meta?.lore
            )
        }
    }
}

/**
 * Configuration for a shared slot entry.
 */
data class SharedSlotEntry(
    /**
     * Single slot index, or null if using slots list.
     *
     * Slot reference:
     * - Hotbar: 0-8
     * - Inventory: 9-35
     * - Armor: 36 (boots), 37 (leggings), 38 (chestplate), 39 (helmet)
     * - Offhand: 40
     */
    val slot: Int? = null,

    /**
     * List of slot indices, or null if using single slot.
     */
    val slots: List<Int>? = null,

    /**
     * The mode for this shared slot.
     */
    val mode: SlotMode = SlotMode.PRESERVE,

    /**
     * Optional enforced item for this slot.
     * If set, this item will always be placed in the slot.
     */
    val item: ItemConfig? = null
) {
    /**
     * Gets all slot indices covered by this entry.
     */
    fun getSlotIndices(): List<Int> {
        return when {
            slots != null -> slots
            slot != null -> listOf(slot)
            else -> emptyList()
        }
    }

    /**
     * Validates that this entry has valid slot configuration.
     */
    fun isValid(): Boolean {
        val indices = getSlotIndices()
        if (indices.isEmpty()) return false

        // All slots must be in valid range (0-40)
        return indices.all { it in 0..40 }
    }

    companion object {
        // Slot index constants
        const val HOTBAR_START = 0
        const val HOTBAR_END = 8
        const val INVENTORY_START = 9
        const val INVENTORY_END = 35
        const val ARMOR_BOOTS = 36
        const val ARMOR_LEGGINGS = 37
        const val ARMOR_CHESTPLATE = 38
        const val ARMOR_HELMET = 39
        const val OFFHAND = 40

        /**
         * Creates a shared slot entry for a single slot.
         */
        fun single(slot: Int, mode: SlotMode = SlotMode.PRESERVE, item: ItemConfig? = null): SharedSlotEntry {
            return SharedSlotEntry(slot = slot, mode = mode, item = item)
        }

        /**
         * Creates a shared slot entry for multiple slots.
         */
        fun multiple(slots: List<Int>, mode: SlotMode = SlotMode.PRESERVE): SharedSlotEntry {
            return SharedSlotEntry(slots = slots, mode = mode)
        }

        /**
         * Creates a shared slot entry for all armor slots.
         */
        fun allArmor(mode: SlotMode = SlotMode.PRESERVE): SharedSlotEntry {
            return SharedSlotEntry(
                slots = listOf(ARMOR_BOOTS, ARMOR_LEGGINGS, ARMOR_CHESTPLATE, ARMOR_HELMET),
                mode = mode
            )
        }

        /**
         * Creates a shared slot entry for the offhand.
         */
        fun offhand(mode: SlotMode = SlotMode.PRESERVE, item: ItemConfig? = null): SharedSlotEntry {
            return SharedSlotEntry(slot = OFFHAND, mode = mode, item = item)
        }

        /**
         * Creates a shared slot entry for all hotbar slots.
         */
        fun allHotbar(mode: SlotMode = SlotMode.PRESERVE): SharedSlotEntry {
            return SharedSlotEntry(slots = (HOTBAR_START..HOTBAR_END).toList(), mode = mode)
        }
    }
}

/**
 * Configuration for shared slots across all groups.
 */
data class SharedSlotsConfig(
    /**
     * Whether shared slots functionality is enabled.
     */
    val enabled: Boolean = true,

    /**
     * List of shared slot entries.
     */
    val slots: List<SharedSlotEntry> = emptyList()
) {
    /**
     * Gets all slot indices that are shared.
     */
    fun getAllSharedSlots(): Set<Int> {
        return slots.flatMap { it.getSlotIndices() }.toSet()
    }

    /**
     * Gets the entry for a specific slot, if any.
     */
    fun getEntryForSlot(slot: Int): SharedSlotEntry? {
        return slots.find { it.getSlotIndices().contains(slot) }
    }

    /**
     * Gets all slots with a specific mode.
     */
    fun getSlotsWithMode(mode: SlotMode): Set<Int> {
        return slots
            .filter { it.mode == mode }
            .flatMap { it.getSlotIndices() }
            .toSet()
    }

    /**
     * Checks if a slot is shared.
     */
    fun isSharedSlot(slot: Int): Boolean {
        return enabled && getAllSharedSlots().contains(slot)
    }

    /**
     * Checks if a slot is locked.
     */
    fun isLockedSlot(slot: Int): Boolean {
        return enabled && getEntryForSlot(slot)?.mode == SlotMode.LOCK
    }

    /**
     * Validates all entries in this config.
     */
    fun isValid(): Boolean {
        return slots.all { it.isValid() }
    }

    companion object {
        /**
         * Creates a disabled shared slots config.
         */
        fun disabled(): SharedSlotsConfig = SharedSlotsConfig(enabled = false)

        /**
         * Creates a default shared slots config with common settings.
         */
        fun withDefaults(): SharedSlotsConfig {
            return SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    // Hotbar slot 9 (index 8) for server menu
                    SharedSlotEntry.single(
                        slot = 8,
                        mode = SlotMode.PRESERVE,
                        item = ItemConfig(
                            type = Material.COMPASS,
                            displayName = "&6Server Menu",
                            lore = listOf("&7Right-click to open")
                        )
                    )
                )
            )
        }
    }
}

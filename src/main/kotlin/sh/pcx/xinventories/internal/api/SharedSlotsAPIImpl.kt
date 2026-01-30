package sh.pcx.xinventories.internal.api

import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.SharedSlotsAPI
import sh.pcx.xinventories.internal.model.ItemConfig
import sh.pcx.xinventories.internal.model.SharedSlotEntry
import sh.pcx.xinventories.internal.model.SharedSlotsConfig
import sh.pcx.xinventories.internal.model.SlotMode
import java.util.UUID

/**
 * Implementation of the SharedSlotsAPI.
 * Adapts internal SharedSlotService to the public API interface.
 */
class SharedSlotsAPIImpl(private val plugin: XInventories) : SharedSlotsAPI {

    private val sharedSlotService get() = plugin.serviceManager.sharedSlotService

    // Local config cache since the service doesn't expose config directly
    private var currentConfig: SharedSlotsConfig = plugin.configManager.mainConfig.sharedSlots.toSharedSlotsConfig()

    override fun getConfig(): SharedSlotsConfig {
        return currentConfig
    }

    override fun setConfig(config: SharedSlotsConfig) {
        currentConfig = config
        sharedSlotService.updateConfig(config)
    }

    override fun isEnabled(): Boolean {
        return currentConfig.enabled
    }

    override fun setEnabled(enabled: Boolean) {
        currentConfig = currentConfig.copy(enabled = enabled)
        sharedSlotService.updateConfig(currentConfig)
    }

    override fun isSharedSlot(slot: Int): Boolean {
        return sharedSlotService.isSharedSlot(slot)
    }

    override fun isLockedSlot(slot: Int): Boolean {
        return sharedSlotService.isLockedSlot(slot)
    }

    override fun isSyncedSlot(slot: Int): Boolean {
        val entry = sharedSlotService.getEntryForSlot(slot) ?: return false
        return entry.mode == SlotMode.SYNC
    }

    override fun getSlotEntry(slot: Int): SharedSlotEntry? {
        return sharedSlotService.getEntryForSlot(slot)
    }

    override fun addSlotEntry(entry: SharedSlotEntry): Boolean {
        // Check if any slot in the entry already exists
        val existingSlots = currentConfig.getAllSharedSlots()
        for (slot in entry.getSlotIndices()) {
            if (slot in existingSlots) {
                return false
            }
        }

        val newSlots = currentConfig.slots + entry
        currentConfig = currentConfig.copy(slots = newSlots)
        sharedSlotService.updateConfig(currentConfig)
        return true
    }

    override fun removeSlotEntry(slot: Int): Boolean {
        val entry = sharedSlotService.getEntryForSlot(slot) ?: return false
        val newSlots = currentConfig.slots.filter { it != entry }
        if (newSlots.size == currentConfig.slots.size) return false

        currentConfig = currentConfig.copy(slots = newSlots)
        sharedSlotService.updateConfig(currentConfig)
        return true
    }

    override fun setSlotMode(slot: Int, mode: SlotMode): Boolean {
        val entry = sharedSlotService.getEntryForSlot(slot) ?: return false
        val newEntry = entry.copy(mode = mode)

        val newSlots = currentConfig.slots.map {
            if (it == entry) newEntry else it
        }

        currentConfig = currentConfig.copy(slots = newSlots)
        sharedSlotService.updateConfig(currentConfig)
        return true
    }

    override fun setEnforcedItem(slot: Int, item: ItemConfig?): Boolean {
        val entry = sharedSlotService.getEntryForSlot(slot) ?: return false
        val newEntry = entry.copy(item = item)

        val newSlots = currentConfig.slots.map {
            if (it == entry) newEntry else it
        }

        currentConfig = currentConfig.copy(slots = newSlots)
        sharedSlotService.updateConfig(currentConfig)
        return true
    }

    override fun getAllSharedSlots(): Set<Int> {
        return currentConfig.getAllSharedSlots()
    }

    override fun getSlotsWithMode(mode: SlotMode): Set<Int> {
        val slots = mutableSetOf<Int>()
        for (entry in currentConfig.slots) {
            if (entry.mode == mode) {
                slots.addAll(entry.getSlotIndices())
            }
        }
        return slots
    }

    override fun preserveSlots(player: Player) {
        sharedSlotService.preserveSharedSlots(player)
    }

    override fun restoreSlots(player: Player) {
        sharedSlotService.restoreSharedSlots(player)
    }

    override fun getCachedItem(playerUuid: UUID, slot: Int): ItemStack? {
        // The internal service doesn't expose direct cache access
        // This would require additional implementation in SharedSlotService
        return null
    }

    override fun setCachedItem(playerUuid: UUID, slot: Int, item: ItemStack?) {
        // The internal service doesn't expose direct cache manipulation
        // This would require additional implementation in SharedSlotService
    }

    override fun clearPlayerCache(playerUuid: UUID) {
        sharedSlotService.cleanup(playerUuid)
    }

    override fun applyEnforcedItems(player: Player) {
        sharedSlotService.applyEnforcedItems(player)
    }
}

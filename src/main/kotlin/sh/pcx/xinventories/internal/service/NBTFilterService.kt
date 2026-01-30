package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.model.FilterAction
import sh.pcx.xinventories.internal.model.NBTFilter
import sh.pcx.xinventories.internal.model.NBTFilterConfig
import sh.pcx.xinventories.internal.model.NBTFilterResult
import sh.pcx.xinventories.internal.model.NBTFilterType
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing NBT-based item filters.
 *
 * This service provides advanced item filtering capabilities based on
 * NBT data such as enchantments, custom model data, display names, and lore.
 */
class NBTFilterService(
    private val plugin: XInventories,
    private val scope: CoroutineScope,
    private val messageService: MessageService
) {
    // Cache for group NBT filter configurations
    private val filterConfigCache = ConcurrentHashMap<String, NBTFilterConfig>()

    /**
     * Initializes the NBT filter service.
     */
    fun initialize() {
        Logging.debug { "NBTFilterService initialized" }
    }

    /**
     * Shuts down the NBT filter service.
     */
    fun shutdown() {
        filterConfigCache.clear()
        Logging.debug { "NBTFilterService shut down" }
    }

    /**
     * Reloads the NBT filter service.
     */
    fun reload() {
        filterConfigCache.clear()
        Logging.debug { "NBTFilterService reloaded" }
    }

    /**
     * Checks an item against all NBT filters for a group.
     *
     * @param item The item to check
     * @param config The NBT filter configuration
     * @return The filter result
     */
    fun checkItem(item: ItemStack, config: NBTFilterConfig): NBTFilterResult {
        if (!config.enabled || config.filters.isEmpty()) {
            return NBTFilterResult.NO_MATCH
        }

        val meta = item.itemMeta ?: return NBTFilterResult.NO_MATCH

        // Check each filter
        for (filter in config.filters) {
            val result = checkItemAgainstFilter(item, meta, filter)
            if (result.matched) {
                return result
            }
        }

        return NBTFilterResult.NO_MATCH
    }

    /**
     * Checks an item against a single NBT filter.
     */
    private fun checkItemAgainstFilter(item: ItemStack, meta: ItemMeta, filter: NBTFilter): NBTFilterResult {
        return when (filter.type) {
            NBTFilterType.ENCHANTMENT -> checkEnchantmentFilter(item, filter)
            NBTFilterType.CUSTOM_MODEL_DATA -> checkCustomModelDataFilter(meta, filter)
            NBTFilterType.DISPLAY_NAME -> checkDisplayNameFilter(meta, filter)
            NBTFilterType.LORE -> checkLoreFilter(meta, filter)
            NBTFilterType.ATTRIBUTE -> checkAttributeFilter(meta, filter)
        }
    }

    /**
     * Checks if an item matches an enchantment filter.
     */
    private fun checkEnchantmentFilter(item: ItemStack, filter: NBTFilter): NBTFilterResult {
        val enchantmentName = filter.enchantment ?: return NBTFilterResult.NO_MATCH

        // Try to find the enchantment
        val enchantment = findEnchantment(enchantmentName) ?: return NBTFilterResult.NO_MATCH

        val enchants = item.enchantments
        if (!enchants.containsKey(enchantment)) {
            return NBTFilterResult.NO_MATCH
        }

        val level = enchants[enchantment] ?: 0

        // Check level bounds
        val minOk = filter.minLevel == null || level >= filter.minLevel
        val maxOk = filter.maxLevel == null || level <= filter.maxLevel

        return if (minOk && maxOk) {
            NBTFilterResult.matched(filter, "Enchantment ${enchantment.key.key} level $level")
        } else {
            NBTFilterResult.NO_MATCH
        }
    }

    /**
     * Finds an enchantment by name (supports both namespace:key and just key).
     */
    private fun findEnchantment(name: String): Enchantment? {
        // Try direct lookup with namespace
        if (name.contains(":")) {
            val key = NamespacedKey.fromString(name.lowercase())
            if (key != null) {
                return Enchantment.getByKey(key)
            }
        }

        // Try minecraft namespace
        val minecraftKey = NamespacedKey.minecraft(name.lowercase())
        val byMinecraftKey = Enchantment.getByKey(minecraftKey)
        if (byMinecraftKey != null) {
            return byMinecraftKey
        }

        // Try legacy name lookup (for backwards compatibility)
        @Suppress("DEPRECATION")
        return Enchantment.getByName(name.uppercase())
    }

    /**
     * Checks if an item matches a custom model data filter.
     */
    private fun checkCustomModelDataFilter(meta: ItemMeta, filter: NBTFilter): NBTFilterResult {
        val values = filter.customModelData ?: return NBTFilterResult.NO_MATCH

        if (!meta.hasCustomModelData()) {
            return NBTFilterResult.NO_MATCH
        }

        val modelData = meta.customModelData

        return if (values.contains(modelData)) {
            NBTFilterResult.matched(filter, "Custom model data: $modelData")
        } else {
            NBTFilterResult.NO_MATCH
        }
    }

    /**
     * Checks if an item matches a display name filter.
     */
    private fun checkDisplayNameFilter(meta: ItemMeta, filter: NBTFilter): NBTFilterResult {
        val pattern = filter.compiledNamePattern ?: return NBTFilterResult.NO_MATCH

        if (!meta.hasDisplayName()) {
            return NBTFilterResult.NO_MATCH
        }

        // Get display name as plain text
        val displayName = meta.displayName()?.let {
            PlainTextComponentSerializer.plainText().serialize(it)
        } ?: return NBTFilterResult.NO_MATCH

        return if (pattern.containsMatchIn(displayName)) {
            NBTFilterResult.matched(filter, "Display name matches: $displayName")
        } else {
            NBTFilterResult.NO_MATCH
        }
    }

    /**
     * Checks if an item matches a lore filter.
     */
    private fun checkLoreFilter(meta: ItemMeta, filter: NBTFilter): NBTFilterResult {
        val pattern = filter.compiledLorePattern ?: return NBTFilterResult.NO_MATCH

        if (!meta.hasLore()) {
            return NBTFilterResult.NO_MATCH
        }

        val lore = meta.lore()?.map { line ->
            PlainTextComponentSerializer.plainText().serialize(line)
        } ?: return NBTFilterResult.NO_MATCH

        for (line in lore) {
            if (pattern.containsMatchIn(line)) {
                return NBTFilterResult.matched(filter, "Lore matches: $line")
            }
        }

        return NBTFilterResult.NO_MATCH
    }

    /**
     * Checks if an item matches an attribute filter.
     */
    private fun checkAttributeFilter(meta: ItemMeta, filter: NBTFilter): NBTFilterResult {
        val attributeName = filter.attributeName ?: return NBTFilterResult.NO_MATCH

        if (!meta.hasAttributeModifiers()) {
            return NBTFilterResult.NO_MATCH
        }

        val modifiers = meta.attributeModifiers ?: return NBTFilterResult.NO_MATCH

        for (entry in modifiers.entries()) {
            val attribute = entry.key
            if (attribute.key.key.equals(attributeName, ignoreCase = true) ||
                attribute.name().equals(attributeName, ignoreCase = true)) {
                return NBTFilterResult.matched(filter, "Attribute: ${attribute.key.key}")
            }
        }

        return NBTFilterResult.NO_MATCH
    }

    /**
     * Checks all items in a player's inventory against NBT filters.
     *
     * @param player The player to check
     * @param config The NBT filter configuration
     * @return Map of slot index to (item, filter result)
     */
    fun checkPlayerInventory(
        player: Player,
        config: NBTFilterConfig
    ): Map<Int, Pair<ItemStack, NBTFilterResult>> {
        if (!config.enabled || config.filters.isEmpty()) {
            return emptyMap()
        }

        val violations = mutableMapOf<Int, Pair<ItemStack, NBTFilterResult>>()

        // Check main inventory (slots 0-35)
        for (i in 0..35) {
            val item = player.inventory.getItem(i) ?: continue
            if (item.type.isAir) continue

            val result = checkItem(item, config)
            if (result.matched && result.filter?.action != FilterAction.ALLOW) {
                violations[i] = item to result
            }
        }

        // Check armor (slots 36-39)
        val armorContents = player.inventory.armorContents
        for ((index, item) in armorContents.withIndex()) {
            if (item == null || item.type.isAir) continue

            val result = checkItem(item, config)
            if (result.matched && result.filter?.action != FilterAction.ALLOW) {
                violations[36 + index] = item to result
            }
        }

        // Check offhand (slot 40)
        val offhand = player.inventory.itemInOffHand
        if (!offhand.type.isAir) {
            val result = checkItem(offhand, config)
            if (result.matched && result.filter?.action != FilterAction.ALLOW) {
                violations[40] = offhand to result
            }
        }

        return violations
    }

    /**
     * Applies NBT filter actions to violating items.
     *
     * @param player The player
     * @param group The inventory group
     * @param violations Map of slot to (item, filter result)
     * @return Number of items affected
     */
    fun applyFilterActions(
        player: Player,
        group: InventoryGroup,
        violations: Map<Int, Pair<ItemStack, NBTFilterResult>>
    ): Int {
        if (violations.isEmpty()) return 0

        var affected = 0

        for ((slot, violation) in violations) {
            val (item, result) = violation
            val filter = result.filter ?: continue

            when (filter.action) {
                FilterAction.ALLOW -> {
                    // Item is explicitly allowed, do nothing
                }
                FilterAction.REMOVE -> {
                    clearSlot(player, slot)
                    affected++
                    Logging.debug { "NBT filter removed item from ${player.name} slot $slot: ${result.matchReason}" }
                }
                FilterAction.DROP -> {
                    player.world.dropItemNaturally(player.location, item)
                    clearSlot(player, slot)
                    affected++
                    Logging.debug { "NBT filter dropped item from ${player.name} slot $slot: ${result.matchReason}" }
                }
            }
        }

        // Notify player if items were affected
        if (affected > 0) {
            messageService.send(player, "nbt-filter-removed",
                "count" to affected.toString(),
                "group" to group.name
            )
        }

        return affected
    }

    /**
     * Clears an inventory slot.
     */
    private fun clearSlot(player: Player, slot: Int) {
        when {
            slot in 0..35 -> player.inventory.setItem(slot, null)
            slot in 36..39 -> {
                val armor = player.inventory.armorContents
                armor[slot - 36] = null
                player.inventory.armorContents = armor
            }
            slot == 40 -> player.inventory.setItemInOffHand(null)
        }
    }

    /**
     * Gets or loads NBT filter config for a group.
     *
     * @param groupName The group name
     * @return The NBT filter configuration
     */
    fun getFilterConfig(groupName: String): NBTFilterConfig {
        return filterConfigCache.getOrPut(groupName) {
            loadFilterConfig(groupName)
        }
    }

    /**
     * Loads NBT filter configuration from the group config.
     */
    private fun loadFilterConfig(groupName: String): NBTFilterConfig {
        val group = plugin.serviceManager.groupService.getGroup(groupName)
            ?: return NBTFilterConfig.DISABLED

        val restrictions = group.restrictions ?: return NBTFilterConfig.DISABLED

        // NBT filters would be loaded from the restrictions config
        // For now, return disabled until config parsing is implemented
        return NBTFilterConfig.DISABLED
    }

    /**
     * Validates an NBT filter configuration.
     *
     * @param config The configuration to validate
     * @return List of validation errors, empty if valid
     */
    fun validateConfig(config: NBTFilterConfig): List<String> {
        val errors = mutableListOf<String>()

        val filterErrors = config.validate()
        filterErrors.forEach { (index, filterErrorList) ->
            filterErrorList.forEach { error ->
                errors.add("Filter #${index + 1}: $error")
            }
        }

        return errors
    }

    /**
     * Tests if a specific item would be filtered.
     *
     * @param item The item to test
     * @param config The NBT filter configuration
     * @return Pair of (would be filtered, filter result)
     */
    fun testItem(item: ItemStack, config: NBTFilterConfig): Pair<Boolean, NBTFilterResult> {
        val result = checkItem(item, config)
        val wouldFilter = result.matched && result.filter?.action != FilterAction.ALLOW
        return wouldFilter to result
    }
}

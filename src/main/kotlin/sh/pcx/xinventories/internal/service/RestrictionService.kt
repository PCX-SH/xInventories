package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.event.ItemRestrictionEvent
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.model.ConfiscatedItem
import sh.pcx.xinventories.internal.model.ItemPattern
import sh.pcx.xinventories.internal.model.RestrictionAction
import sh.pcx.xinventories.internal.model.RestrictionConfig
import sh.pcx.xinventories.internal.model.RestrictionMode
import sh.pcx.xinventories.internal.model.RestrictionResult
import sh.pcx.xinventories.internal.model.RestrictionViolationAction
import sh.pcx.xinventories.internal.storage.ConfiscationStorage
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing item restrictions.
 *
 * Handles pattern parsing, item matching, and violation handling.
 */
class RestrictionService(
    private val plugin: XInventories,
    private val scope: CoroutineScope,
    private val messageService: MessageService
) {
    // Cache parsed patterns for performance
    private val patternCache = ConcurrentHashMap<String, ItemPattern>()

    // Storage for confiscated items
    private lateinit var confiscationStorage: ConfiscationStorage

    /**
     * Initializes the restriction service.
     */
    fun initialize() {
        confiscationStorage = ConfiscationStorage(plugin)
        runBlocking {
            confiscationStorage.initialize()
        }
        Logging.debug { "RestrictionService initialized" }
    }

    /**
     * Shuts down the restriction service.
     */
    fun shutdown() {
        if (::confiscationStorage.isInitialized) {
            runBlocking {
                confiscationStorage.shutdown()
            }
        }
    }

    /**
     * Parses a pattern string into an ItemPattern.
     * Results are cached for performance.
     */
    fun parsePattern(pattern: String): ItemPattern {
        return patternCache.computeIfAbsent(pattern) { ItemPattern.parse(it) }
    }

    /**
     * Checks if an item matches any of the given patterns.
     */
    fun matchesAnyPattern(item: ItemStack, patterns: List<String>): String? {
        for (patternStr in patterns) {
            val pattern = parsePattern(patternStr)
            if (pattern.matches(item)) {
                return patternStr
            }
        }
        return null
    }

    /**
     * Checks an item against the restriction config.
     *
     * @return The matching pattern if restricted, null if allowed
     */
    fun checkItem(item: ItemStack, config: RestrictionConfig): String? {
        if (!config.isEnabled()) return null

        return when (config.mode) {
            RestrictionMode.BLACKLIST -> {
                // Item is restricted if it matches any blacklist pattern
                matchesAnyPattern(item, config.blacklist)
            }
            RestrictionMode.WHITELIST -> {
                // Item is restricted if it does NOT match any whitelist pattern
                val matches = matchesAnyPattern(item, config.whitelist)
                if (matches == null) {
                    // Doesn't match whitelist - restricted
                    "WHITELIST:${item.type.name}"
                } else {
                    // Matches whitelist - allowed
                    null
                }
            }
            RestrictionMode.NONE -> null
        }
    }

    /**
     * Checks if an item should be stripped on exit.
     */
    fun shouldStripOnExit(item: ItemStack, config: RestrictionConfig): String? {
        if (config.stripOnExit.isEmpty()) return null
        return matchesAnyPattern(item, config.stripOnExit)
    }

    /**
     * Checks all items in a player's inventory against restrictions.
     *
     * @return Map of slot -> (item, matching pattern)
     */
    fun checkPlayerInventory(
        player: Player,
        config: RestrictionConfig,
        action: RestrictionAction
    ): Map<Int, Pair<ItemStack, String>> {
        val violations = mutableMapOf<Int, Pair<ItemStack, String>>()

        // Check main inventory (slots 0-35)
        for (i in 0..35) {
            val item = player.inventory.getItem(i) ?: continue
            if (item.type.isAir) continue

            val pattern = when (action) {
                RestrictionAction.ENTERING -> checkItem(item, config)
                RestrictionAction.EXITING -> shouldStripOnExit(item, config) ?: checkItem(item, config)
            }

            if (pattern != null) {
                violations[i] = item to pattern
            }
        }

        // Check armor (slots 36-39)
        val armorContents = player.inventory.armorContents
        for ((index, item) in armorContents.withIndex()) {
            if (item == null || item.type.isAir) continue

            val pattern = when (action) {
                RestrictionAction.ENTERING -> checkItem(item, config)
                RestrictionAction.EXITING -> shouldStripOnExit(item, config) ?: checkItem(item, config)
            }

            if (pattern != null) {
                violations[36 + index] = item to pattern
            }
        }

        // Check offhand (slot 40)
        val offhand = player.inventory.itemInOffHand
        if (!offhand.type.isAir) {
            val pattern = when (action) {
                RestrictionAction.ENTERING -> checkItem(offhand, config)
                RestrictionAction.EXITING -> shouldStripOnExit(offhand, config) ?: checkItem(offhand, config)
            }

            if (pattern != null) {
                violations[40] = offhand to pattern
            }
        }

        return violations
    }

    /**
     * Handles restriction violations for a player.
     *
     * @return true if the player is allowed to proceed, false if blocked
     */
    fun handleViolations(
        player: Player,
        group: InventoryGroup,
        config: RestrictionConfig,
        violations: Map<Int, Pair<ItemStack, String>>,
        action: RestrictionAction
    ): Boolean {
        if (violations.isEmpty()) return true

        val results = mutableListOf<Pair<Int, RestrictionResult>>()
        var shouldBlock = false

        for ((slot, violation) in violations) {
            val (item, pattern) = violation

            // Determine initial result based on violation action
            val initialResult = when (config.onViolation) {
                RestrictionViolationAction.REMOVE -> RestrictionResult.REMOVE
                RestrictionViolationAction.PREVENT -> RestrictionResult.PREVENT
                RestrictionViolationAction.DROP -> RestrictionResult.REMOVE
                RestrictionViolationAction.MOVE_TO_VAULT -> RestrictionResult.REMOVE
            }

            // Fire event
            val event = ItemRestrictionEvent(player, group, item, pattern, action, initialResult)
            event.slotIndex = slot
            Bukkit.getPluginManager().callEvent(event)

            if (!event.isCancelled) {
                results.add(slot to event.result)

                if (event.result == RestrictionResult.PREVENT) {
                    shouldBlock = true
                }
            }
        }

        // Apply results if not blocking
        if (!shouldBlock) {
            for ((slot, result) in results) {
                if (result == RestrictionResult.REMOVE) {
                    val item = when {
                        slot in 0..35 -> player.inventory.getItem(slot)
                        slot in 36..39 -> player.inventory.armorContents[slot - 36]
                        slot == 40 -> player.inventory.itemInOffHand
                        else -> null
                    }

                    if (item != null) {
                        // Handle based on violation action
                        when (config.onViolation) {
                            RestrictionViolationAction.DROP -> {
                                player.world.dropItemNaturally(player.location, item)
                            }
                            RestrictionViolationAction.MOVE_TO_VAULT -> {
                                // Store item in confiscation vault
                                val pattern = violations[slot]?.second ?: "unknown"
                                val confiscatedItem = ConfiscatedItem.fromItemStack(
                                    playerUuid = player.uniqueId,
                                    item = item.clone(),
                                    reason = "Restricted item: $pattern",
                                    groupName = group.name,
                                    worldName = player.world.name
                                )
                                scope.launch {
                                    val id = confiscationStorage.storeItem(confiscatedItem)
                                    if (id > 0) {
                                        Logging.debug { "Confiscated item ${item.type.name} from ${player.name} (id: $id)" }
                                    } else {
                                        Logging.warning("Failed to store confiscated item, dropping instead")
                                        plugin.server.scheduler.runTask(plugin, Runnable {
                                            player.world.dropItemNaturally(player.location, item)
                                        })
                                    }
                                }
                            }
                            else -> {
                                // REMOVE - just delete the item
                            }
                        }

                        // Clear the slot
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
                }
            }
        }

        // Notify player
        if (config.notifyPlayer && violations.isNotEmpty()) {
            val itemNames = violations.values.map { it.first.type.name }.distinct().take(3)
            val itemList = if (itemNames.size > 3) {
                itemNames.joinToString(", ") + "..."
            } else {
                itemNames.joinToString(", ")
            }

            if (shouldBlock) {
                messageService.send(player, "restriction-blocked", "items" to itemList)
            } else {
                messageService.send(player, "restriction-removed", "items" to itemList)
            }
        }

        // Notify admins
        if (config.notifyAdmins && violations.isNotEmpty()) {
            val message = "Player ${player.name} had ${violations.size} restricted items ${if (shouldBlock) "blocked" else "removed"} in group ${group.name}"
            Logging.notifyAdmins(message)
        }

        return !shouldBlock
    }

    /**
     * Tests if a specific item would be restricted.
     */
    fun testItem(
        item: ItemStack,
        config: RestrictionConfig
    ): Pair<Boolean, String?> {
        val pattern = checkItem(item, config)
        return (pattern != null) to pattern
    }

    /**
     * Gets all patterns for a restriction config.
     */
    fun getAllPatterns(config: RestrictionConfig): List<Pair<String, Boolean>> {
        val result = mutableListOf<Pair<String, Boolean>>()

        when (config.mode) {
            RestrictionMode.BLACKLIST -> {
                config.blacklist.forEach { result.add(it to true) }
            }
            RestrictionMode.WHITELIST -> {
                config.whitelist.forEach { result.add(it to false) }
            }
            RestrictionMode.NONE -> {}
        }

        config.stripOnExit.forEach { result.add("STRIP:$it" to true) }

        return result
    }

    /**
     * Validates a pattern string.
     */
    fun validatePattern(pattern: String): Result<ItemPattern> {
        val parsed = parsePattern(pattern)
        return if (parsed is ItemPattern.Invalid) {
            Result.failure(IllegalArgumentException(parsed.reason))
        } else {
            Result.success(parsed)
        }
    }

    /**
     * Clears the pattern cache.
     */
    fun clearCache() {
        patternCache.clear()
        Logging.debug { "Pattern cache cleared" }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Confiscation Vault Methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets all confiscated items for a player.
     */
    suspend fun getConfiscatedItems(playerUuid: UUID, limit: Int = 100): List<ConfiscatedItem> {
        return confiscationStorage.getItemsForPlayer(playerUuid, limit)
    }

    /**
     * Gets a specific confiscated item by ID.
     */
    suspend fun getConfiscatedItem(id: Long): ConfiscatedItem? {
        return confiscationStorage.getItemById(id)
    }

    /**
     * Claims a confiscated item, returning it to the player.
     * Returns the item if successful, null if failed.
     */
    suspend fun claimConfiscatedItem(player: Player, id: Long): ConfiscatedItem? {
        val item = confiscationStorage.getItemById(id) ?: return null

        // Verify ownership
        if (item.playerUuid != player.uniqueId) {
            Logging.warning("Player ${player.name} tried to claim item $id that belongs to ${item.playerUuid}")
            return null
        }

        // Deserialize the item
        val itemStack = item.toItemStack()
        if (itemStack == null) {
            Logging.warning("Failed to deserialize confiscated item $id")
            return null
        }

        // Give item to player (or drop if inventory full)
        val leftover = player.inventory.addItem(itemStack)
        if (leftover.isNotEmpty()) {
            leftover.values.forEach { leftoverItem ->
                player.world.dropItemNaturally(player.location, leftoverItem)
            }
        }

        // Delete from storage
        confiscationStorage.deleteItem(id)

        Logging.debug { "Player ${player.name} claimed confiscated item $id (${item.itemType})" }
        return item
    }

    /**
     * Claims all confiscated items for a player.
     * Returns the number of items claimed.
     */
    suspend fun claimAllConfiscatedItems(player: Player): Int {
        val items = confiscationStorage.getItemsForPlayer(player.uniqueId)
        var claimed = 0

        for (item in items) {
            val itemStack = item.toItemStack() ?: continue

            val leftover = player.inventory.addItem(itemStack)
            if (leftover.isNotEmpty()) {
                leftover.values.forEach { leftoverItem ->
                    player.world.dropItemNaturally(player.location, leftoverItem)
                }
            }

            confiscationStorage.deleteItem(item.id)
            claimed++
        }

        if (claimed > 0) {
            Logging.debug { "Player ${player.name} claimed $claimed confiscated items" }
        }

        return claimed
    }

    /**
     * Gets the count of confiscated items for a player.
     */
    suspend fun getConfiscatedItemCount(playerUuid: UUID): Int {
        return confiscationStorage.getCountForPlayer(playerUuid)
    }

    /**
     * Deletes a confiscated item without returning it.
     * For admin use only.
     */
    suspend fun deleteConfiscatedItem(id: Long): Boolean {
        return confiscationStorage.deleteItem(id)
    }

    /**
     * Deletes all confiscated items for a player.
     * For admin use only.
     */
    suspend fun deleteAllConfiscatedItems(playerUuid: UUID): Int {
        return confiscationStorage.deleteAllForPlayer(playerUuid)
    }

    /**
     * Cleans up old confiscated items.
     */
    suspend fun cleanupConfiscations(retentionDays: Int): Int {
        return confiscationStorage.cleanup(retentionDays)
    }
}

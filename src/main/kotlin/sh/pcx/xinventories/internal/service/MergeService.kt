package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Merge strategy for combining inventories.
 */
enum class MergeStrategy {
    /**
     * Add items from source to target, stacking where possible.
     * Items that can't fit are reported as overflow.
     */
    COMBINE,

    /**
     * Replace target with source completely.
     */
    REPLACE,

    /**
     * For each slot, keep the item with higher count.
     * If counts are equal or items differ, source wins.
     */
    KEEP_HIGHER,

    /**
     * Return conflicts for GUI resolution.
     * No automatic merging, user decides each slot.
     */
    MANUAL
}

/**
 * Represents a conflict at a specific slot during merge.
 */
data class MergeConflict(
    val slot: Int,
    val slotType: SlotType,
    val sourceItem: ItemStack?,
    val targetItem: ItemStack?,
    val resolution: ConflictResolution = ConflictResolution.PENDING
)

/**
 * Type of inventory slot.
 */
enum class SlotType {
    MAIN_INVENTORY,
    ARMOR,
    OFFHAND,
    ENDER_CHEST
}

/**
 * Resolution for a conflict.
 */
enum class ConflictResolution {
    PENDING,
    KEEP_SOURCE,
    KEEP_TARGET,
    COMBINE
}

/**
 * Result of a merge operation.
 */
data class MergeResult(
    val success: Boolean,
    val mergedData: PlayerData?,
    val conflicts: List<MergeConflict>,
    val overflowItems: List<ItemStack>,
    val message: String
)

/**
 * Pending merge operation waiting for confirmation or resolution.
 */
data class PendingMerge(
    val playerUUID: UUID,
    val targetPlayerUUID: UUID,
    val sourceGroup: String,
    val targetGroup: String,
    val sourceData: PlayerData,
    val targetData: PlayerData,
    val strategy: MergeStrategy,
    val conflicts: MutableList<MergeConflict>,
    val previewMode: Boolean,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Service for merging inventories between groups.
 *
 * Supports multiple merge strategies:
 * - COMBINE: Add items from source to target, stack where possible
 * - REPLACE: Replace target with source
 * - KEEP_HIGHER: For each slot, keep item with higher count
 * - MANUAL: Return conflicts for GUI resolution
 */
class MergeService(
    private val plugin: XInventories,
    private val scope: CoroutineScope,
    private val storageService: StorageService
) {
    // Pending merges per player (admin performing the merge)
    private val pendingMerges = ConcurrentHashMap<UUID, PendingMerge>()

    // Expiration time for pending merges (5 minutes)
    private val pendingMergeExpiration = 5 * 60 * 1000L

    /**
     * Initializes the merge service.
     */
    fun initialize() {
        Logging.debug { "MergeService initialized" }
    }

    /**
     * Shuts down the merge service.
     */
    fun shutdown() {
        pendingMerges.clear()
    }

    /**
     * Previews a merge operation without applying it.
     *
     * @param targetPlayerUUID The player whose inventories will be merged
     * @param sourceGroup The source group to merge from
     * @param targetGroup The target group to merge into
     * @param strategy The merge strategy to use
     * @param gameMode The gamemode for data lookup (null for non-separated)
     * @return MergeResult with preview information
     */
    suspend fun previewMerge(
        adminUUID: UUID,
        targetPlayerUUID: UUID,
        sourceGroup: String,
        targetGroup: String,
        strategy: MergeStrategy = MergeStrategy.COMBINE,
        gameMode: GameMode? = null
    ): MergeResult {
        // Load source and target data
        val sourceData = storageService.loadPlayerData(targetPlayerUUID, sourceGroup, gameMode)
        val targetData = storageService.loadPlayerData(targetPlayerUUID, targetGroup, gameMode)

        if (sourceData == null) {
            return MergeResult(
                success = false,
                mergedData = null,
                conflicts = emptyList(),
                overflowItems = emptyList(),
                message = "No source data found for player in group '$sourceGroup'"
            )
        }

        if (targetData == null) {
            // Target is empty, just copy source
            val pending = PendingMerge(
                playerUUID = adminUUID,
                targetPlayerUUID = targetPlayerUUID,
                sourceGroup = sourceGroup,
                targetGroup = targetGroup,
                sourceData = sourceData,
                targetData = PlayerData.empty(targetPlayerUUID, sourceData.playerName, targetGroup, gameMode ?: GameMode.SURVIVAL),
                strategy = strategy,
                conflicts = mutableListOf(),
                previewMode = true
            )
            pendingMerges[adminUUID] = pending

            return MergeResult(
                success = true,
                mergedData = sourceData.copy(targetGroup),
                conflicts = emptyList(),
                overflowItems = emptyList(),
                message = "Target is empty. Source data will be copied directly."
            )
        }

        // Perform merge preview
        val result = performMerge(sourceData, targetData, strategy, previewOnly = true)

        // Store pending merge for confirmation
        val pending = PendingMerge(
            playerUUID = adminUUID,
            targetPlayerUUID = targetPlayerUUID,
            sourceGroup = sourceGroup,
            targetGroup = targetGroup,
            sourceData = sourceData,
            targetData = targetData,
            strategy = strategy,
            conflicts = result.conflicts.toMutableList(),
            previewMode = true
        )
        pendingMerges[adminUUID] = pending

        return result
    }

    /**
     * Executes a merge operation.
     */
    suspend fun executeMerge(
        adminUUID: UUID,
        targetPlayerUUID: UUID,
        sourceGroup: String,
        targetGroup: String,
        strategy: MergeStrategy = MergeStrategy.COMBINE,
        gameMode: GameMode? = null,
        conflictResolutions: Map<Int, ConflictResolution>? = null
    ): MergeResult {
        val sourceData = storageService.loadPlayerData(targetPlayerUUID, sourceGroup, gameMode)
        val targetData = storageService.loadPlayerData(targetPlayerUUID, targetGroup, gameMode)

        if (sourceData == null) {
            return MergeResult(
                success = false,
                mergedData = null,
                conflicts = emptyList(),
                overflowItems = emptyList(),
                message = "No source data found for player in group '$sourceGroup'"
            )
        }

        val effectiveTarget = targetData ?: PlayerData.empty(
            targetPlayerUUID,
            sourceData.playerName,
            targetGroup,
            gameMode ?: GameMode.SURVIVAL
        )

        val result = performMerge(sourceData, effectiveTarget, strategy, previewOnly = false, conflictResolutions)

        if (result.success && result.mergedData != null) {
            // Save the merged data
            val saveSuccess = storageService.savePlayerData(result.mergedData)
            if (!saveSuccess) {
                return MergeResult(
                    success = false,
                    mergedData = null,
                    conflicts = emptyList(),
                    overflowItems = emptyList(),
                    message = "Failed to save merged data"
                )
            }

            // Clear pending merge
            pendingMerges.remove(adminUUID)

            Logging.info("Merged inventory for ${sourceData.playerName}: $sourceGroup -> $targetGroup (strategy: $strategy)")
        }

        return result
    }

    /**
     * Confirms a pending merge.
     */
    suspend fun confirmMerge(adminUUID: UUID): MergeResult {
        val pending = pendingMerges[adminUUID]
            ?: return MergeResult(
                success = false,
                mergedData = null,
                conflicts = emptyList(),
                overflowItems = emptyList(),
                message = "No pending merge found. Use /xinv merge to start a new merge."
            )

        // Check expiration
        if (System.currentTimeMillis() - pending.createdAt > pendingMergeExpiration) {
            pendingMerges.remove(adminUUID)
            return MergeResult(
                success = false,
                mergedData = null,
                conflicts = emptyList(),
                overflowItems = emptyList(),
                message = "Pending merge expired. Please start a new merge."
            )
        }

        // Convert conflict resolutions
        val resolutions = pending.conflicts.associate { it.slot to it.resolution }

        return executeMerge(
            adminUUID,
            pending.targetPlayerUUID,
            pending.sourceGroup,
            pending.targetGroup,
            pending.strategy,
            pending.sourceData.gameMode,
            resolutions
        )
    }

    /**
     * Cancels a pending merge.
     */
    fun cancelMerge(adminUUID: UUID): Boolean {
        return pendingMerges.remove(adminUUID) != null
    }

    /**
     * Gets pending merge for an admin.
     */
    fun getPendingMerge(adminUUID: UUID): PendingMerge? {
        val pending = pendingMerges[adminUUID] ?: return null

        // Check expiration
        if (System.currentTimeMillis() - pending.createdAt > pendingMergeExpiration) {
            pendingMerges.remove(adminUUID)
            return null
        }

        return pending
    }

    /**
     * Updates conflict resolution for a pending merge.
     */
    fun resolveConflict(adminUUID: UUID, slot: Int, resolution: ConflictResolution): Boolean {
        val pending = pendingMerges[adminUUID] ?: return false

        val conflict = pending.conflicts.find { it.slot == slot } ?: return false
        val index = pending.conflicts.indexOf(conflict)
        pending.conflicts[index] = conflict.copy(resolution = resolution)

        return true
    }

    /**
     * Checks if all conflicts in a pending merge are resolved.
     */
    fun areAllConflictsResolved(adminUUID: UUID): Boolean {
        val pending = pendingMerges[adminUUID] ?: return false
        return pending.conflicts.all { it.resolution != ConflictResolution.PENDING }
    }

    /**
     * Performs the actual merge operation.
     */
    private fun performMerge(
        source: PlayerData,
        target: PlayerData,
        strategy: MergeStrategy,
        previewOnly: Boolean,
        conflictResolutions: Map<Int, ConflictResolution>? = null
    ): MergeResult {
        return when (strategy) {
            MergeStrategy.REPLACE -> performReplaceMerge(source, target, previewOnly)
            MergeStrategy.COMBINE -> performCombineMerge(source, target, previewOnly)
            MergeStrategy.KEEP_HIGHER -> performKeepHigherMerge(source, target, previewOnly)
            MergeStrategy.MANUAL -> performManualMerge(source, target, previewOnly, conflictResolutions)
        }
    }

    /**
     * REPLACE strategy: Simply copy source to target.
     */
    private fun performReplaceMerge(
        source: PlayerData,
        target: PlayerData,
        previewOnly: Boolean
    ): MergeResult {
        val merged = source.copy(target.group)

        return MergeResult(
            success = true,
            mergedData = merged,
            conflicts = emptyList(),
            overflowItems = emptyList(),
            message = "Target inventory will be replaced with source inventory."
        )
    }

    /**
     * COMBINE strategy: Add items from source to target, stacking where possible.
     */
    private fun performCombineMerge(
        source: PlayerData,
        target: PlayerData,
        previewOnly: Boolean
    ): MergeResult {
        val merged = target.deepCopy()
        val overflowItems = mutableListOf<ItemStack>()

        // Combine main inventory
        for ((slot, sourceItem) in source.mainInventory) {
            val targetItem = merged.mainInventory[slot]

            if (targetItem == null) {
                merged.mainInventory[slot] = sourceItem.clone()
            } else if (canStack(sourceItem, targetItem)) {
                val totalAmount = sourceItem.amount + targetItem.amount
                val maxStack = sourceItem.maxStackSize

                if (totalAmount <= maxStack) {
                    targetItem.amount = totalAmount
                } else {
                    targetItem.amount = maxStack
                    val overflow = sourceItem.clone()
                    overflow.amount = totalAmount - maxStack
                    overflowItems.add(overflow)
                }
            } else {
                // Find empty slot or add to overflow
                val emptySlot = findEmptySlot(merged.mainInventory, 36)
                if (emptySlot != null) {
                    merged.mainInventory[emptySlot] = sourceItem.clone()
                } else {
                    overflowItems.add(sourceItem.clone())
                }
            }
        }

        // Combine armor (prefer source if target is empty)
        for ((slot, sourceItem) in source.armorInventory) {
            if (!merged.armorInventory.containsKey(slot)) {
                merged.armorInventory[slot] = sourceItem.clone()
            }
        }

        // Combine offhand (prefer source if target is empty)
        if (merged.offhand == null && source.offhand != null) {
            merged.offhand = source.offhand!!.clone()
        } else if (merged.offhand != null && source.offhand != null) {
            overflowItems.add(source.offhand!!.clone())
        }

        // Combine ender chest
        for ((slot, sourceItem) in source.enderChest) {
            val targetItem = merged.enderChest[slot]

            if (targetItem == null) {
                merged.enderChest[slot] = sourceItem.clone()
            } else if (canStack(sourceItem, targetItem)) {
                val totalAmount = sourceItem.amount + targetItem.amount
                val maxStack = sourceItem.maxStackSize

                if (totalAmount <= maxStack) {
                    targetItem.amount = totalAmount
                } else {
                    targetItem.amount = maxStack
                    val overflow = sourceItem.clone()
                    overflow.amount = totalAmount - maxStack
                    overflowItems.add(overflow)
                }
            } else {
                val emptySlot = findEmptySlot(merged.enderChest, 27)
                if (emptySlot != null) {
                    merged.enderChest[emptySlot] = sourceItem.clone()
                } else {
                    overflowItems.add(sourceItem.clone())
                }
            }
        }

        // Combine experience (add them)
        merged.totalExperience = target.totalExperience + source.totalExperience
        merged.level = calculateLevelFromExp(merged.totalExperience)
        merged.experience = calculateExpProgress(merged.totalExperience, merged.level)

        val message = if (overflowItems.isEmpty()) {
            "Inventories combined successfully."
        } else {
            "Inventories combined. ${overflowItems.size} item(s) could not fit and will be dropped."
        }

        return MergeResult(
            success = true,
            mergedData = merged,
            conflicts = emptyList(),
            overflowItems = overflowItems,
            message = message
        )
    }

    /**
     * KEEP_HIGHER strategy: For each slot, keep the item with higher count.
     */
    private fun performKeepHigherMerge(
        source: PlayerData,
        target: PlayerData,
        previewOnly: Boolean
    ): MergeResult {
        val merged = target.deepCopy()

        // Merge main inventory
        val allSlots = (source.mainInventory.keys + target.mainInventory.keys).toSet()
        for (slot in allSlots) {
            val sourceItem = source.mainInventory[slot]
            val targetItem = target.mainInventory[slot]

            merged.mainInventory[slot] = selectHigherCountItem(sourceItem, targetItem) ?: continue
        }

        // Merge armor
        for (slot in 0..3) {
            val sourceItem = source.armorInventory[slot]
            val targetItem = target.armorInventory[slot]
            val selected = selectHigherCountItem(sourceItem, targetItem)
            if (selected != null) {
                merged.armorInventory[slot] = selected
            } else {
                merged.armorInventory.remove(slot)
            }
        }

        // Merge offhand
        merged.offhand = selectHigherCountItem(source.offhand, target.offhand)

        // Merge ender chest
        val allEnderSlots = (source.enderChest.keys + target.enderChest.keys).toSet()
        for (slot in allEnderSlots) {
            val sourceItem = source.enderChest[slot]
            val targetItem = target.enderChest[slot]
            merged.enderChest[slot] = selectHigherCountItem(sourceItem, targetItem) ?: continue
        }

        // Keep higher experience
        if (source.totalExperience > target.totalExperience) {
            merged.totalExperience = source.totalExperience
            merged.level = source.level
            merged.experience = source.experience
        }

        // Keep higher health/food
        merged.health = maxOf(source.health, target.health)
        merged.foodLevel = maxOf(source.foodLevel, target.foodLevel)
        merged.saturation = maxOf(source.saturation, target.saturation)

        return MergeResult(
            success = true,
            mergedData = merged,
            conflicts = emptyList(),
            overflowItems = emptyList(),
            message = "Kept higher-count items from each inventory."
        )
    }

    /**
     * MANUAL strategy: Detect conflicts and wait for resolution.
     */
    private fun performManualMerge(
        source: PlayerData,
        target: PlayerData,
        previewOnly: Boolean,
        resolutions: Map<Int, ConflictResolution>?
    ): MergeResult {
        val conflicts = mutableListOf<MergeConflict>()
        val merged = target.deepCopy()

        // Detect conflicts in main inventory
        val allSlots = (source.mainInventory.keys + target.mainInventory.keys).toSet()
        for (slot in allSlots) {
            val sourceItem = source.mainInventory[slot]
            val targetItem = target.mainInventory[slot]

            if (sourceItem != null && targetItem != null && !itemsEqual(sourceItem, targetItem)) {
                val resolution = resolutions?.get(slot) ?: ConflictResolution.PENDING
                conflicts.add(MergeConflict(slot, SlotType.MAIN_INVENTORY, sourceItem.clone(), targetItem.clone(), resolution))

                if (!previewOnly && resolution != ConflictResolution.PENDING) {
                    when (resolution) {
                        ConflictResolution.KEEP_SOURCE -> merged.mainInventory[slot] = sourceItem.clone()
                        ConflictResolution.KEEP_TARGET -> { /* already has target */ }
                        ConflictResolution.COMBINE -> {
                            if (canStack(sourceItem, targetItem)) {
                                targetItem.amount = minOf(targetItem.amount + sourceItem.amount, targetItem.maxStackSize)
                            }
                        }
                        ConflictResolution.PENDING -> { /* should not happen */ }
                    }
                }
            } else if (sourceItem != null && targetItem == null) {
                merged.mainInventory[slot] = sourceItem.clone()
            }
        }

        // Detect armor conflicts
        for (slot in 0..3) {
            val sourceItem = source.armorInventory[slot]
            val targetItem = target.armorInventory[slot]

            if (sourceItem != null && targetItem != null && !itemsEqual(sourceItem, targetItem)) {
                val globalSlot = 100 + slot // Use offset for armor slots
                val resolution = resolutions?.get(globalSlot) ?: ConflictResolution.PENDING
                conflicts.add(MergeConflict(slot, SlotType.ARMOR, sourceItem.clone(), targetItem.clone(), resolution))

                if (!previewOnly && resolution != ConflictResolution.PENDING) {
                    when (resolution) {
                        ConflictResolution.KEEP_SOURCE -> merged.armorInventory[slot] = sourceItem.clone()
                        ConflictResolution.KEEP_TARGET -> { /* already has target */ }
                        else -> { /* armor can't combine */ }
                    }
                }
            } else if (sourceItem != null && targetItem == null) {
                merged.armorInventory[slot] = sourceItem.clone()
            }
        }

        // Detect offhand conflict
        if (source.offhand != null && target.offhand != null && !itemsEqual(source.offhand!!, target.offhand!!)) {
            val resolution = resolutions?.get(200) ?: ConflictResolution.PENDING // Use 200 for offhand
            conflicts.add(MergeConflict(0, SlotType.OFFHAND, source.offhand?.clone(), target.offhand?.clone(), resolution))

            if (!previewOnly && resolution != ConflictResolution.PENDING) {
                when (resolution) {
                    ConflictResolution.KEEP_SOURCE -> merged.offhand = source.offhand?.clone()
                    ConflictResolution.KEEP_TARGET -> { /* already has target */ }
                    else -> { /* offhand can't combine */ }
                }
            }
        } else if (source.offhand != null && target.offhand == null) {
            merged.offhand = source.offhand?.clone()
        }

        // Detect ender chest conflicts
        val allEnderSlots = (source.enderChest.keys + target.enderChest.keys).toSet()
        for (slot in allEnderSlots) {
            val sourceItem = source.enderChest[slot]
            val targetItem = target.enderChest[slot]

            if (sourceItem != null && targetItem != null && !itemsEqual(sourceItem, targetItem)) {
                val globalSlot = 300 + slot // Use offset for ender chest
                val resolution = resolutions?.get(globalSlot) ?: ConflictResolution.PENDING
                conflicts.add(MergeConflict(slot, SlotType.ENDER_CHEST, sourceItem.clone(), targetItem.clone(), resolution))

                if (!previewOnly && resolution != ConflictResolution.PENDING) {
                    when (resolution) {
                        ConflictResolution.KEEP_SOURCE -> merged.enderChest[slot] = sourceItem.clone()
                        ConflictResolution.KEEP_TARGET -> { /* already has target */ }
                        ConflictResolution.COMBINE -> {
                            if (canStack(sourceItem, targetItem)) {
                                targetItem.amount = minOf(targetItem.amount + sourceItem.amount, targetItem.maxStackSize)
                            }
                        }
                        ConflictResolution.PENDING -> { /* should not happen */ }
                    }
                }
            } else if (sourceItem != null && targetItem == null) {
                merged.enderChest[slot] = sourceItem.clone()
            }
        }

        val unresolvedCount = conflicts.count { it.resolution == ConflictResolution.PENDING }
        val success = !previewOnly && unresolvedCount == 0

        val message = when {
            previewOnly && conflicts.isNotEmpty() ->
                "Found ${conflicts.size} conflict(s). Use the merge GUI or resolve conflicts before confirming."
            previewOnly && conflicts.isEmpty() ->
                "No conflicts found. Ready to merge."
            !previewOnly && unresolvedCount > 0 ->
                "$unresolvedCount conflict(s) still unresolved."
            !previewOnly && unresolvedCount == 0 ->
                "Merge completed with all conflicts resolved."
            else -> "Merge completed."
        }

        return MergeResult(
            success = success,
            mergedData = if (success) merged else null,
            conflicts = conflicts,
            overflowItems = emptyList(),
            message = message
        )
    }

    // Helper functions

    private fun canStack(item1: ItemStack, item2: ItemStack): Boolean {
        return item1.isSimilar(item2) && item1.amount < item1.maxStackSize
    }

    private fun itemsEqual(item1: ItemStack, item2: ItemStack): Boolean {
        return item1.isSimilar(item2) && item1.amount == item2.amount
    }

    private fun findEmptySlot(inventory: Map<Int, ItemStack>, maxSlots: Int): Int? {
        for (i in 0 until maxSlots) {
            if (!inventory.containsKey(i)) {
                return i
            }
        }
        return null
    }

    private fun selectHigherCountItem(source: ItemStack?, target: ItemStack?): ItemStack? {
        return when {
            source == null && target == null -> null
            source == null -> target?.clone()
            target == null -> source.clone()
            source.amount >= target.amount -> source.clone()
            else -> target.clone()
        }
    }

    private fun PlayerData.copy(newGroup: String): PlayerData {
        val copy = PlayerData(uuid, playerName, newGroup, gameMode)
        copy.loadFromSnapshot(this.toSnapshot())
        copy.group = newGroup
        return copy
    }

    private fun PlayerData.deepCopy(): PlayerData {
        val copy = PlayerData(uuid, playerName, group, gameMode)
        copy.loadFromSnapshot(this.toSnapshot())
        return copy
    }

    // Experience calculation helpers
    private fun calculateLevelFromExp(totalExp: Int): Int {
        var exp = totalExp
        var level = 0

        while (exp >= getExpForNextLevel(level)) {
            exp -= getExpForNextLevel(level)
            level++
        }

        return level
    }

    private fun calculateExpProgress(totalExp: Int, level: Int): Float {
        var exp = totalExp
        for (i in 0 until level) {
            exp -= getExpForNextLevel(i)
        }
        return exp.toFloat() / getExpForNextLevel(level).toFloat()
    }

    private fun getExpForNextLevel(level: Int): Int {
        return when {
            level >= 30 -> 112 + (level - 30) * 9
            level >= 15 -> 37 + (level - 15) * 5
            else -> 7 + level * 2
        }
    }
}

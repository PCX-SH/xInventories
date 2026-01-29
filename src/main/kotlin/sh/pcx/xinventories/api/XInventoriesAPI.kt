package sh.pcx.xinventories.api

import sh.pcx.xinventories.api.event.*
import sh.pcx.xinventories.api.model.*
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Main API interface for xInventories.
 * Access via [XInventoriesProvider.get].
 */
interface XInventoriesAPI {

    // ═══════════════════════════════════════════════════════════════════
    // Inventory Operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets a snapshot of player inventory data for a specific group.
     * This returns cached data if available, otherwise loads from storage.
     *
     * @param player The player
     * @param group The group name
     * @param gameMode Optional gamemode (uses player's current if null)
     * @return CompletableFuture containing the snapshot, or null if not found
     */
    fun getPlayerData(
        player: Player,
        group: String,
        gameMode: GameMode? = null
    ): CompletableFuture<PlayerInventorySnapshot?>

    /**
     * Gets a snapshot of player inventory data by UUID.
     * Player does not need to be online.
     */
    fun getPlayerData(
        uuid: UUID,
        group: String,
        gameMode: GameMode? = null
    ): CompletableFuture<PlayerInventorySnapshot?>

    /**
     * Gets all inventory snapshots for a player across all groups.
     */
    fun getAllPlayerData(uuid: UUID): CompletableFuture<Map<String, PlayerInventorySnapshot>>

    /**
     * Saves the player's current inventory to their current group.
     *
     * @param player The player to save
     * @param group Optional group override (uses current if null)
     * @return CompletableFuture that completes when save is done
     */
    fun savePlayerData(
        player: Player,
        group: String? = null
    ): CompletableFuture<Result<Unit>>

    /**
     * Loads and applies inventory data to a player.
     * Fires [InventoryLoadEvent] before applying.
     *
     * @param player The player to load
     * @param group The group to load from
     * @param gameMode Optional gamemode (uses player's current if null)
     * @return CompletableFuture containing the result
     */
    fun loadPlayerData(
        player: Player,
        group: String,
        gameMode: GameMode? = null
    ): CompletableFuture<Result<Unit>>

    /**
     * Gets the currently active snapshot for an online player.
     * Returns null if player has no loaded data.
     */
    fun getActiveSnapshot(player: Player): PlayerInventorySnapshot?

    /**
     * Manually triggers an inventory switch for a player.
     * Useful for programmatic world changes.
     */
    fun switchInventory(
        player: Player,
        toGroup: String,
        gameMode: GameMode? = null
    ): CompletableFuture<Result<Unit>>

    /**
     * Clears a player's inventory data for a specific group.
     */
    fun clearPlayerData(
        uuid: UUID,
        group: String,
        gameMode: GameMode? = null
    ): CompletableFuture<Result<Unit>>

    // ═══════════════════════════════════════════════════════════════════
    // Group Operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets a group by name.
     */
    fun getGroup(name: String): InventoryGroup?

    /**
     * Gets all configured groups.
     */
    fun getGroups(): List<InventoryGroup>

    /**
     * Gets the group a world belongs to.
     */
    fun getGroupForWorld(world: World): InventoryGroup
    fun getGroupForWorld(worldName: String): InventoryGroup

    /**
     * Gets the default group.
     */
    fun getDefaultGroup(): InventoryGroup

    /**
     * Creates a new group.
     */
    fun createGroup(
        name: String,
        settings: GroupSettings = GroupSettings(),
        worlds: Set<String> = emptySet(),
        patterns: List<String> = emptyList(),
        priority: Int = 0,
        parent: String? = null
    ): Result<InventoryGroup>

    /**
     * Deletes a group. Cannot delete the default group.
     */
    fun deleteGroup(name: String): Result<Unit>

    /**
     * Modifies an existing group using a builder pattern.
     */
    fun modifyGroup(name: String, modifier: GroupModifier.() -> Unit): Result<InventoryGroup>

    // ═══════════════════════════════════════════════════════════════════
    // World Operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Assigns a world to a group.
     */
    fun assignWorldToGroup(worldName: String, groupName: String): Result<Unit>

    /**
     * Removes a world's explicit group assignment.
     */
    fun unassignWorld(worldName: String): Result<Unit>

    /**
     * Gets all explicit world-to-group assignments.
     */
    fun getWorldAssignments(): Map<String, String>

    /**
     * Checks which group a world would resolve to.
     * Considers: explicit assignment → pattern match → default group
     */
    fun resolveWorldGroup(worldName: String): InventoryGroup

    // ═══════════════════════════════════════════════════════════════════
    // Pattern Operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Adds a regex pattern to a group.
     */
    fun addPattern(groupName: String, pattern: String): Result<Unit>

    /**
     * Removes a pattern from a group.
     */
    fun removePattern(groupName: String, pattern: String): Result<Unit>

    /**
     * Gets all patterns for a group.
     */
    fun getPatterns(groupName: String): List<String>

    /**
     * Tests if a world name matches any pattern in a group.
     */
    fun testPattern(worldName: String, groupName: String): Boolean

    // ═══════════════════════════════════════════════════════════════════
    // Storage Operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets the current storage type.
     */
    fun getStorageType(): StorageType

    /**
     * Migrates data between storage backends.
     */
    fun migrateStorage(
        from: StorageType,
        to: StorageType
    ): CompletableFuture<Result<MigrationReport>>

    /**
     * Creates a backup of all data.
     */
    fun createBackup(name: String? = null): CompletableFuture<Result<BackupMetadata>>

    /**
     * Restores from a backup.
     */
    fun restoreBackup(backupId: String): CompletableFuture<Result<Unit>>

    /**
     * Lists available backups.
     */
    fun listBackups(): CompletableFuture<List<BackupMetadata>>

    /**
     * Deletes a backup.
     */
    fun deleteBackup(backupId: String): CompletableFuture<Result<Unit>>

    // ═══════════════════════════════════════════════════════════════════
    // Cache Operations
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Gets cache statistics.
     */
    fun getCacheStats(): CacheStatistics

    /**
     * Invalidates all cache entries for a player.
     */
    fun invalidateCache(uuid: UUID)

    /**
     * Invalidates a specific cache entry.
     */
    fun invalidateCache(uuid: UUID, group: String, gameMode: GameMode? = null)

    /**
     * Clears the entire cache.
     */
    fun clearCache()

    // ═══════════════════════════════════════════════════════════════════
    // Bypass Management
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Adds a bypass for a player.
     * @param group Specific group, or null for all groups
     */
    fun addBypass(uuid: UUID, group: String? = null)

    /**
     * Removes a bypass from a player.
     */
    fun removeBypass(uuid: UUID, group: String? = null)

    /**
     * Checks if a player has bypass.
     */
    fun hasBypass(uuid: UUID, group: String? = null): Boolean

    /**
     * Gets all bypassed players.
     */
    fun getBypasses(): Map<UUID, Set<String?>>

    // ═══════════════════════════════════════════════════════════════════
    // Event Subscriptions (Alternative to Bukkit Events)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Subscribes to inventory switch events.
     */
    fun onInventorySwitch(handler: (InventorySwitchContext) -> Unit): Subscription

    /**
     * Subscribes to inventory save events.
     */
    fun onInventorySave(handler: (InventorySaveContext) -> Unit): Subscription

    /**
     * Subscribes to inventory load events.
     */
    fun onInventoryLoad(handler: (InventoryLoadContext) -> Unit): Subscription

    /**
     * Subscribes to group change events.
     */
    fun onGroupChange(handler: (GroupChangeContext) -> Unit): Subscription
}

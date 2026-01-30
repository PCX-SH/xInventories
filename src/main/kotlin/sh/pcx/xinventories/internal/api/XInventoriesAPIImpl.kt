package sh.pcx.xinventories.internal.api

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.*
import sh.pcx.xinventories.api.event.*
import sh.pcx.xinventories.api.model.*
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.PlayerData
import kotlinx.coroutines.runBlocking
import org.bukkit.GameMode
import org.bukkit.World
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the public xInventories API.
 */
class XInventoriesAPIImpl(private val plugin: XInventories) : XInventoriesAPI {

    private val switchHandlers = mutableListOf<Pair<(InventorySwitchContext) -> Unit, SubscriptionImpl>>()
    private val saveHandlers = mutableListOf<Pair<(InventorySaveContext) -> Unit, SubscriptionImpl>>()
    private val loadHandlers = mutableListOf<Pair<(InventoryLoadContext) -> Unit, SubscriptionImpl>>()
    private val groupChangeHandlers = mutableListOf<Pair<(GroupChangeContext) -> Unit, SubscriptionImpl>>()

    // ==================== v1.1.0 Feature API Properties ====================

    // Lazy-initialized API adapters
    private val versioningApi: InventoryVersioningAPI by lazy { VersioningAPIImpl(plugin) }
    private val deathRecoveryApi: DeathRecoveryAPI by lazy { DeathRecoveryAPIImpl(plugin) }
    private val templateApi: TemplateAPI by lazy { TemplateAPIImpl(plugin) }
    private val restrictionApi: RestrictionAPI by lazy { RestrictionAPIImpl(plugin) }
    private val sharedSlotsApi: SharedSlotsAPI by lazy { SharedSlotsAPIImpl(plugin) }
    private val conditionApi: ConditionAPI by lazy { ConditionAPIImpl(plugin) }
    private val lockingApi: InventoryLockingAPI by lazy { LockingAPIImpl(plugin) }
    private val economyApi: EconomyAPI by lazy { EconomyAPIImpl(plugin) }
    private val importApi: ImportAPI by lazy { ImportAPIImpl(plugin) }
    private val syncApi: SyncAPI by lazy { SyncAPIImpl(plugin) }

    override val versioning: InventoryVersioningAPI?
        get() = if (plugin.configManager.mainConfig.versioning.enabled) versioningApi else null

    override val deathRecovery: DeathRecoveryAPI?
        get() = if (plugin.configManager.mainConfig.deathRecovery.enabled) deathRecoveryApi else null

    override val templates: TemplateAPI
        get() = templateApi

    override val restrictions: RestrictionAPI
        get() = restrictionApi

    override val sharedSlots: SharedSlotsAPI
        get() = sharedSlotsApi

    override val conditions: ConditionAPI
        get() = conditionApi

    override val locking: InventoryLockingAPI
        get() = lockingApi

    override val economy: EconomyAPI?
        get() = if (plugin.configManager.mainConfig.economy.enabled) economyApi else null

    override val importing: ImportAPI
        get() = importApi

    override val sync: SyncAPI?
        get() = if (plugin.configManager.mainConfig.sync.enabled) syncApi else null

    // ==================== Inventory Operations ====================

    override fun getPlayerData(player: Player, group: String, gameMode: GameMode?): CompletableFuture<PlayerInventorySnapshot?> {
        return getPlayerData(player.uniqueId, group, gameMode)
    }

    override fun getPlayerData(uuid: UUID, group: String, gameMode: GameMode?): CompletableFuture<PlayerInventorySnapshot?> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                plugin.serviceManager.storageService.loadPlayerData(uuid, group, gameMode)?.toSnapshot()
            }
        }
    }

    override fun getAllPlayerData(uuid: UUID): CompletableFuture<Map<String, PlayerInventorySnapshot>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                plugin.serviceManager.storageService.loadAllPlayerData(uuid)
                    .mapValues { it.value.toSnapshot() }
            }
        }
    }

    override fun savePlayerData(player: Player, group: String?): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                try {
                    plugin.serviceManager.inventoryService.saveInventory(player, group)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override fun loadPlayerData(player: Player, group: String, gameMode: GameMode?): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                try {
                    val groupObj = plugin.serviceManager.groupService.getGroup(group)?.toApiModel()
                        ?: return@runBlocking Result.failure(Exception("Group not found: $group"))
                    plugin.serviceManager.inventoryService.loadInventory(
                        player,
                        groupObj,
                        InventoryLoadEvent.LoadReason.API
                    )
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override fun getActiveSnapshot(player: Player): PlayerInventorySnapshot? {
        return plugin.serviceManager.inventoryService.getActiveSnapshot(player)
    }

    override fun switchInventory(player: Player, toGroup: String, gameMode: GameMode?): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                try {
                    val group = plugin.serviceManager.groupService.getGroup(toGroup)
                        ?: return@runBlocking Result.failure(Exception("Group not found: $toGroup"))
                    plugin.serviceManager.inventoryService.saveInventory(player)
                    plugin.serviceManager.inventoryService.loadInventory(
                        player,
                        group.toApiModel(),
                        InventoryLoadEvent.LoadReason.API
                    )
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    override fun clearPlayerData(uuid: UUID, group: String, gameMode: GameMode?): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                try {
                    plugin.serviceManager.storageService.deletePlayerData(uuid, group, gameMode)
                    Result.success(Unit)
                } catch (e: Exception) {
                    Result.failure(e)
                }
            }
        }
    }

    // ==================== Group Operations ====================

    override fun getGroup(name: String): InventoryGroup? {
        return plugin.serviceManager.groupService.getGroup(name)?.toApiModel()
    }

    override fun getGroups(): List<InventoryGroup> {
        return plugin.serviceManager.groupService.getAllGroups().map { it.toApiModel() }
    }

    override fun getGroupForWorld(world: World): InventoryGroup {
        return plugin.serviceManager.groupService.getGroupForWorld(world).toApiModel()
    }

    override fun getGroupForWorld(worldName: String): InventoryGroup {
        return plugin.serviceManager.groupService.getGroupForWorld(worldName).toApiModel()
    }

    override fun getDefaultGroup(): InventoryGroup {
        return plugin.serviceManager.groupService.getDefaultGroup().toApiModel()
    }

    override fun createGroup(
        name: String,
        settings: GroupSettings,
        worlds: Set<String>,
        patterns: List<String>,
        priority: Int,
        parent: String?
    ): Result<InventoryGroup> {
        return plugin.serviceManager.groupService.createGroup(
            name = name,
            settings = settings,
            worlds = worlds,
            patterns = patterns,
            priority = priority,
            parent = parent
        )
    }

    override fun deleteGroup(name: String): Result<Unit> {
        return plugin.serviceManager.groupService.deleteGroup(name)
    }

    override fun modifyGroup(name: String, modifier: GroupModifier.() -> Unit): Result<InventoryGroup> {
        return plugin.serviceManager.groupService.modifyGroup(name, modifier)
    }

    // ==================== World Operations ====================

    override fun assignWorldToGroup(worldName: String, groupName: String): Result<Unit> {
        return plugin.serviceManager.groupService.assignWorldToGroup(worldName, groupName)
    }

    override fun unassignWorld(worldName: String): Result<Unit> {
        return plugin.serviceManager.groupService.unassignWorld(worldName)
    }

    override fun getWorldAssignments(): Map<String, String> {
        val assignments = mutableMapOf<String, String>()
        plugin.serviceManager.groupService.getAllGroups().forEach { group ->
            group.worlds.forEach { world ->
                assignments[world] = group.name
            }
        }
        return assignments
    }

    override fun resolveWorldGroup(worldName: String): InventoryGroup {
        return plugin.serviceManager.groupService.getGroupForWorld(worldName).toApiModel()
    }

    // ==================== Pattern Operations ====================

    override fun addPattern(groupName: String, pattern: String): Result<Unit> {
        return plugin.serviceManager.groupService.addPattern(groupName, pattern)
    }

    override fun removePattern(groupName: String, pattern: String): Result<Unit> {
        return plugin.serviceManager.groupService.removePattern(groupName, pattern)
    }

    override fun getPatterns(groupName: String): List<String> {
        return plugin.serviceManager.groupService.getPatterns(groupName)
    }

    override fun testPattern(worldName: String, groupName: String): Boolean {
        val group = plugin.serviceManager.groupService.getGroup(groupName) ?: return false
        return group.matchesPattern(worldName)
    }

    // ==================== Storage Operations ====================

    override fun getStorageType(): StorageType {
        return plugin.configManager.mainConfig.storage.type
    }

    override fun migrateStorage(from: StorageType, to: StorageType): CompletableFuture<Result<MigrationReport>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                plugin.serviceManager.migrationService.migrate(from, to)
            }
        }
    }

    override fun createBackup(name: String?): CompletableFuture<Result<BackupMetadata>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                plugin.serviceManager.backupService.createBackup(name)
            }
        }
    }

    override fun restoreBackup(backupId: String): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                plugin.serviceManager.backupService.restoreBackup(backupId)
            }
        }
    }

    override fun listBackups(): CompletableFuture<List<BackupMetadata>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                plugin.serviceManager.backupService.listBackups()
            }
        }
    }

    override fun deleteBackup(backupId: String): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                plugin.serviceManager.backupService.deleteBackup(backupId)
            }
        }
    }

    // ==================== Cache Operations ====================

    override fun getCacheStats(): CacheStatistics {
        return plugin.serviceManager.storageService.getCacheStats()
    }

    override fun invalidateCache(uuid: UUID) {
        plugin.serviceManager.storageService.invalidateCache(uuid)
    }

    override fun invalidateCache(uuid: UUID, group: String, gameMode: GameMode?) {
        plugin.serviceManager.storageService.invalidateCache(uuid, group, gameMode)
    }

    override fun clearCache() {
        plugin.serviceManager.storageService.clearCache()
    }

    // ==================== Bypass Management ====================

    override fun addBypass(uuid: UUID, group: String?) {
        plugin.serviceManager.inventoryService.addBypass(uuid, group)
    }

    override fun removeBypass(uuid: UUID, group: String?) {
        plugin.serviceManager.inventoryService.removeBypass(uuid, group)
    }

    override fun hasBypass(uuid: UUID, group: String?): Boolean {
        return plugin.serviceManager.inventoryService.hasBypass(uuid, group)
    }

    override fun getBypasses(): Map<UUID, Set<String?>> {
        return plugin.serviceManager.inventoryService.getBypasses()
    }

    // ==================== Event Subscriptions ====================

    override fun onInventorySwitch(handler: (InventorySwitchContext) -> Unit): Subscription {
        val sub = SubscriptionImpl { switchHandlers.removeIf { it.second == this } }
        switchHandlers.add(handler to sub)
        return sub
    }

    override fun onInventorySave(handler: (InventorySaveContext) -> Unit): Subscription {
        val sub = SubscriptionImpl { saveHandlers.removeIf { it.second == this } }
        saveHandlers.add(handler to sub)
        return sub
    }

    override fun onInventoryLoad(handler: (InventoryLoadContext) -> Unit): Subscription {
        val sub = SubscriptionImpl { loadHandlers.removeIf { it.second == this } }
        loadHandlers.add(handler to sub)
        return sub
    }

    override fun onGroupChange(handler: (GroupChangeContext) -> Unit): Subscription {
        val sub = SubscriptionImpl { groupChangeHandlers.removeIf { it.second == this } }
        groupChangeHandlers.add(handler to sub)
        return sub
    }

    // ==================== Internal Event Dispatching ====================

    fun dispatchSwitch(context: InventorySwitchContext) {
        switchHandlers.forEach { (handler, sub) ->
            if (!sub.isCancelled()) {
                try { handler(context) } catch (e: Exception) {
                    plugin.logger.warning("Error in switch handler: ${e.message}")
                }
            }
        }
    }

    fun dispatchSave(context: InventorySaveContext) {
        saveHandlers.forEach { (handler, sub) ->
            if (!sub.isCancelled()) {
                try { handler(context) } catch (e: Exception) {
                    plugin.logger.warning("Error in save handler: ${e.message}")
                }
            }
        }
    }

    fun dispatchLoad(context: InventoryLoadContext) {
        loadHandlers.forEach { (handler, sub) ->
            if (!sub.isCancelled()) {
                try { handler(context) } catch (e: Exception) {
                    plugin.logger.warning("Error in load handler: ${e.message}")
                }
            }
        }
    }

    fun dispatchGroupChange(context: GroupChangeContext) {
        groupChangeHandlers.forEach { (handler, sub) ->
            if (!sub.isCancelled()) {
                try { handler(context) } catch (e: Exception) {
                    plugin.logger.warning("Error in group change handler: ${e.message}")
                }
            }
        }
    }

    private class SubscriptionImpl(private val onCancel: () -> Unit) : Subscription {
        override val id: String = UUID.randomUUID().toString()
        private var cancelled = false
        override val isActive: Boolean get() = !cancelled

        override fun unsubscribe() {
            if (!cancelled) {
                cancelled = true
                onCancel()
            }
        }

        fun isCancelled(): Boolean = cancelled
    }
}

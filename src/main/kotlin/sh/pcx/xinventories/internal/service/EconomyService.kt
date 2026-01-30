package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing per-group economy balances.
 * Wraps Vault economy operations and routes them to the correct group storage.
 *
 * NOTE: This service requires the `balances` field to be added to PlayerData.
 * Integration point: PlayerData.balances: MutableMap<String, Double> = mutableMapOf()
 */
class EconomyService(
    private val plugin: XInventories,
    private val storageService: StorageService,
    private val groupService: GroupService
) {

    // In-memory balance cache for quick access
    // Key: "uuid:group", Value: balance
    private val balanceCache = ConcurrentHashMap<String, Double>()

    // Pending balance changes for batch saving
    private val pendingChanges = ConcurrentHashMap<String, Double>()

    // Whether economy integration is enabled
    private var enabled: Boolean = false

    // Whether balances are separated by group
    private var separateByGroup: Boolean = true

    // Bridge to Vault economy (loaded only when Vault is present)
    private var vaultBridge: VaultEconomyBridge? = null

    /**
     * Initializes the economy service.
     */
    fun initialize() {
        val config = plugin.configManager.mainConfig
        val economyConfig = config.economy

        enabled = economyConfig.enabled
        separateByGroup = economyConfig.separateByGroup

        if (!enabled) {
            Logging.info("Economy integration is disabled")
            return
        }

        // Try to get the existing Vault economy provider to wrap (only if Vault is present)
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                vaultBridge = VaultEconomyBridge()
                if (vaultBridge?.initialize() == true) {
                    Logging.info("Found existing Vault economy provider: ${vaultBridge?.getProviderName()}")
                } else {
                    vaultBridge = null
                    Logging.info("No existing Vault economy provider found - xInventories will provide economy")
                }
            } catch (e: Exception) {
                vaultBridge = null
                Logging.debug { "Vault bridge initialization failed: ${e.message}" }
            }
        } else {
            Logging.info("Vault not found - economy will use internal storage only")
        }

        Logging.info("EconomyService initialized (enabled: $enabled, separate by group: $separateByGroup)")
    }

    /**
     * Checks if economy integration is enabled.
     */
    fun isEnabled(): Boolean = enabled

    /**
     * Checks if balances are separated by group.
     */
    fun isSeparateByGroup(): Boolean = separateByGroup

    /**
     * Gets a player's balance for a specific group.
     *
     * @param player The player
     * @param group The group name (or null for current group)
     * @return The player's balance in the group
     */
    fun getBalance(player: Player, group: String? = null): Double {
        if (!enabled) return vaultBridge?.getBalance(player) ?: 0.0

        val targetGroup = group ?: groupService.getGroupForWorld(player.world).name
        return getBalance(player.uniqueId, targetGroup)
    }

    /**
     * Gets a player's balance for a specific group by UUID.
     *
     * @param uuid The player's UUID
     * @param group The group name
     * @return The player's balance in the group
     */
    fun getBalance(uuid: UUID, group: String): Double {
        if (!enabled || !separateByGroup) {
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            return vaultBridge?.getBalance(offlinePlayer) ?: 0.0
        }

        val cacheKey = getCacheKey(uuid, group)

        // Check cache first
        balanceCache[cacheKey]?.let { return it }

        // Check pending changes
        pendingChanges[cacheKey]?.let { return it }

        // Load from storage via blocking call (should be called from appropriate context)
        return try {
            kotlinx.coroutines.runBlocking {
                loadBalanceFromStorage(uuid, group)
            }
        } catch (e: Exception) {
            Logging.debug { "Failed to load balance for $uuid in $group: ${e.message}" }
            0.0
        }
    }

    /**
     * Loads a balance from storage.
     */
    private suspend fun loadBalanceFromStorage(uuid: UUID, group: String): Double {
        // Try to load player data for the group
        val playerData = storageService.loadPlayerData(uuid, group, null)
        val balance = playerData?.balances?.get(group) ?: 0.0

        // Cache the result
        val cacheKey = getCacheKey(uuid, group)
        balanceCache[cacheKey] = balance

        return balance
    }

    /**
     * Gets all balances for a player across all groups.
     *
     * @param uuid The player's UUID
     * @return Map of group name to balance
     */
    fun getAllBalances(uuid: UUID): Map<String, Double> {
        if (!enabled || !separateByGroup) {
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            val globalBalance = vaultBridge?.getBalance(offlinePlayer) ?: 0.0
            return mapOf("global" to globalBalance)
        }

        val balances = mutableMapOf<String, Double>()

        // Get all groups and their balances
        groupService.getAllGroups().forEach { group ->
            val balance = getBalance(uuid, group.name)
            if (balance > 0.0) {
                balances[group.name] = balance
            }
        }

        return balances
    }

    /**
     * Sets a player's balance for a specific group.
     *
     * @param player The player
     * @param amount The new balance
     * @param group The group name (or null for current group)
     * @return True if successful
     */
    fun setBalance(player: Player, amount: Double, group: String? = null): Boolean {
        if (!enabled) {
            return vaultBridge?.setBalance(player, amount) ?: false
        }

        val targetGroup = group ?: groupService.getGroupForWorld(player.world).name
        return setBalance(player.uniqueId, targetGroup, amount)
    }

    /**
     * Sets a player's balance for a specific group by UUID.
     *
     * @param uuid The player's UUID
     * @param group The group name
     * @param amount The new balance
     * @return True if successful
     */
    fun setBalance(uuid: UUID, group: String, amount: Double): Boolean {
        if (!enabled) return false

        if (!separateByGroup) {
            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            return vaultBridge?.setBalance(offlinePlayer, amount) ?: false
        }

        val cacheKey = getCacheKey(uuid, group)
        val sanitizedAmount = amount.coerceAtLeast(0.0)

        balanceCache[cacheKey] = sanitizedAmount
        pendingChanges[cacheKey] = sanitizedAmount

        // Schedule save
        scheduleSave(uuid, group)

        Logging.debug { "Set balance for $uuid in $group to $sanitizedAmount" }
        return true
    }

    /**
     * Deposits money to a player's balance in a group.
     *
     * @param player The player
     * @param amount The amount to deposit
     * @param group The group name (or null for current group)
     * @return True if successful
     */
    fun deposit(player: Player, amount: Double, group: String? = null): Boolean {
        if (amount < 0) return false

        val targetGroup = group ?: groupService.getGroupForWorld(player.world).name
        val currentBalance = getBalance(player, targetGroup)
        return setBalance(player, currentBalance + amount, targetGroup)
    }

    /**
     * Withdraws money from a player's balance in a group.
     *
     * @param player The player
     * @param amount The amount to withdraw
     * @param group The group name (or null for current group)
     * @return True if successful, false if insufficient funds
     */
    fun withdraw(player: Player, amount: Double, group: String? = null): Boolean {
        if (amount < 0) return false

        val targetGroup = group ?: groupService.getGroupForWorld(player.world).name
        val currentBalance = getBalance(player, targetGroup)

        if (currentBalance < amount) {
            return false
        }

        return setBalance(player, currentBalance - amount, targetGroup)
    }

    /**
     * Transfers money between groups for a player.
     *
     * @param player The player
     * @param fromGroup The source group
     * @param toGroup The destination group
     * @param amount The amount to transfer
     * @return True if successful
     */
    fun transfer(player: Player, fromGroup: String, toGroup: String, amount: Double): Boolean {
        return transfer(player.uniqueId, fromGroup, toGroup, amount)
    }

    /**
     * Transfers money between groups for a player by UUID.
     *
     * @param uuid The player's UUID
     * @param fromGroup The source group
     * @param toGroup The destination group
     * @param amount The amount to transfer
     * @return True if successful
     */
    fun transfer(uuid: UUID, fromGroup: String, toGroup: String, amount: Double): Boolean {
        if (!enabled || !separateByGroup) {
            Logging.debug { "Transfer not available: enabled=$enabled, separateByGroup=$separateByGroup" }
            return false
        }

        if (amount <= 0) {
            return false
        }

        if (fromGroup == toGroup) {
            return true // No-op
        }

        val fromBalance = getBalance(uuid, fromGroup)
        if (fromBalance < amount) {
            return false
        }

        // Perform the transfer
        setBalance(uuid, fromGroup, fromBalance - amount)
        val toBalance = getBalance(uuid, toGroup)
        setBalance(uuid, toGroup, toBalance + amount)

        Logging.debug { "Transferred $amount from $fromGroup to $toGroup for $uuid" }
        return true
    }

    /**
     * Checks if a player has at least the specified amount in a group.
     *
     * @param player The player
     * @param amount The amount to check
     * @param group The group name (or null for current group)
     * @return True if the player has sufficient funds
     */
    fun has(player: Player, amount: Double, group: String? = null): Boolean {
        val targetGroup = group ?: groupService.getGroupForWorld(player.world).name
        return getBalance(player, targetGroup) >= amount
    }

    /**
     * Checks if a group has separate economy enabled.
     *
     * @param groupName The group name
     * @return True if the group uses separate economy
     */
    fun hasGroupEconomy(groupName: String): Boolean {
        if (!enabled || !separateByGroup) return false

        val group = groupService.getGroup(groupName) ?: return false
        return group.settings.separateEconomy
    }

    /**
     * Saves all pending balance changes.
     */
    suspend fun savePendingChanges() {
        if (pendingChanges.isEmpty()) return

        val changes = pendingChanges.toMap()
        pendingChanges.clear()

        // Group changes by player UUID
        val playerChanges = mutableMapOf<UUID, MutableMap<String, Double>>()
        changes.forEach { (key, balance) ->
            val parts = key.split(":")
            if (parts.size == 2) {
                try {
                    val uuid = UUID.fromString(parts[0])
                    val group = parts[1]
                    playerChanges.getOrPut(uuid) { mutableMapOf() }[group] = balance
                } catch (e: Exception) {
                    Logging.debug { "Invalid cache key: $key" }
                }
            }
        }

        // Save each player's balances
        playerChanges.forEach { (uuid, balances) ->
            try {
                savePlayerBalances(uuid, balances)
            } catch (e: Exception) {
                Logging.error("Failed to save balances for $uuid", e)
            }
        }

        Logging.debug { "Saved balance changes for ${playerChanges.size} players" }
    }

    /**
     * Saves balance changes for a single player.
     */
    private suspend fun savePlayerBalances(uuid: UUID, balances: Map<String, Double>) {
        balances.forEach { (group, balance) ->
            // Load existing player data or create new
            var playerData = storageService.loadPlayerData(uuid, group, null)
            if (playerData == null) {
                val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
                playerData = sh.pcx.xinventories.internal.model.PlayerData(
                    uuid = uuid,
                    playerName = offlinePlayer.name ?: uuid.toString(),
                    group = group,
                    gameMode = org.bukkit.GameMode.SURVIVAL
                )
            }

            // Update the balance
            playerData.balances[group] = balance
            playerData.dirty = true

            // Save the player data
            storageService.savePlayerData(playerData)
        }
    }

    /**
     * Invalidates the cache for a player.
     */
    fun invalidateCache(uuid: UUID) {
        val prefix = "$uuid:"
        balanceCache.keys.filter { it.startsWith(prefix) }.forEach {
            balanceCache.remove(it)
        }
    }

    /**
     * Clears all caches.
     */
    fun clearCache() {
        balanceCache.clear()
    }

    /**
     * Shuts down the economy service.
     */
    suspend fun shutdown() {
        savePendingChanges()
        clearCache()
        Logging.debug { "EconomyService shut down" }
    }

    private fun getCacheKey(uuid: UUID, group: String): String {
        return "$uuid:$group"
    }

    private fun scheduleSave(uuid: UUID, group: String) {
        // Save will be handled by the main save cycle
        // Could add debouncing here if needed
    }

    /**
     * Gets the name of the wrapped economy provider, if any.
     */
    fun getWrappedEconomyName(): String? {
        return vaultBridge?.getProviderName()
    }
}

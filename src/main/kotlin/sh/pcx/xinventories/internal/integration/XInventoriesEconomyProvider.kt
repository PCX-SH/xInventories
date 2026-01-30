package sh.pcx.xinventories.internal.integration

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.service.EconomyService
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.util.Logging
import net.milkbowl.vault.economy.Economy
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.ServicePriority

/**
 * Vault Economy provider implementation for xInventories.
 * Routes economy operations to the correct group based on the player's current world.
 *
 * This provider registers with Bukkit's ServiceManager and intercepts all Vault
 * economy operations, routing them to per-group balances when configured.
 */
class XInventoriesEconomyProvider(
    private val plugin: XInventories,
    private val economyService: EconomyService,
    private val groupService: GroupService
) : Economy {

    private var registered = false

    /**
     * Registers this economy provider with Bukkit's ServiceManager.
     */
    fun register() {
        if (registered) {
            Logging.warning("XInventoriesEconomyProvider is already registered")
            return
        }

        try {
            Bukkit.getServicesManager().register(
                Economy::class.java,
                this,
                plugin,
                ServicePriority.High
            )
            registered = true
            Logging.info("Registered xInventories as Vault economy provider")
        } catch (e: Exception) {
            Logging.error("Failed to register economy provider", e)
        }
    }

    /**
     * Unregisters this economy provider.
     */
    fun unregister() {
        if (!registered) return

        try {
            Bukkit.getServicesManager().unregister(Economy::class.java, this)
            registered = false
            Logging.info("Unregistered xInventories economy provider")
        } catch (e: Exception) {
            Logging.error("Failed to unregister economy provider", e)
        }
    }

    /**
     * Checks if this provider is registered.
     */
    fun isRegistered(): Boolean = registered

    // ═══════════════════════════════════════════════════════════════════
    // Economy Interface Implementation
    // ═══════════════════════════════════════════════════════════════════

    override fun isEnabled(): Boolean = economyService.isEnabled()

    override fun getName(): String = "xInventories"

    override fun hasBankSupport(): Boolean = false // No bank support for now

    override fun fractionalDigits(): Int = 2

    override fun format(amount: Double): String {
        return String.format("$%.2f", amount)
    }

    override fun currencyNamePlural(): String = "dollars"

    override fun currencyNameSingular(): String = "dollar"

    // ═══════════════════════════════════════════════════════════════════
    // Player Account Methods
    // ═══════════════════════════════════════════════════════════════════

    @Deprecated("Use OfflinePlayer variant")
    override fun hasAccount(playerName: String): Boolean {
        val player = Bukkit.getOfflinePlayer(playerName)
        return hasAccount(player)
    }

    override fun hasAccount(player: OfflinePlayer): Boolean {
        // All players have accounts
        return true
    }

    @Deprecated("Use OfflinePlayer variant")
    override fun hasAccount(playerName: String, worldName: String): Boolean {
        val player = Bukkit.getOfflinePlayer(playerName)
        return hasAccount(player, worldName)
    }

    override fun hasAccount(player: OfflinePlayer, worldName: String): Boolean {
        // All players have accounts in all worlds
        return true
    }

    // ═══════════════════════════════════════════════════════════════════
    // Balance Methods
    // ═══════════════════════════════════════════════════════════════════

    @Deprecated("Use OfflinePlayer variant")
    override fun getBalance(playerName: String): Double {
        val player = Bukkit.getOfflinePlayer(playerName)
        return getBalance(player)
    }

    override fun getBalance(player: OfflinePlayer): Double {
        // Get the player's current world group
        val onlinePlayer = player.player
        val group = if (onlinePlayer != null) {
            groupService.getGroupForWorld(onlinePlayer.world).name
        } else {
            // For offline players, use default group
            groupService.getDefaultGroup().name
        }
        return economyService.getBalance(player.uniqueId, group)
    }

    @Deprecated("Use OfflinePlayer variant")
    override fun getBalance(playerName: String, worldName: String): Double {
        val player = Bukkit.getOfflinePlayer(playerName)
        return getBalance(player, worldName)
    }

    override fun getBalance(player: OfflinePlayer, worldName: String): Double {
        val group = groupService.getGroupForWorld(worldName).name
        return economyService.getBalance(player.uniqueId, group)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Has Methods
    // ═══════════════════════════════════════════════════════════════════

    @Deprecated("Use OfflinePlayer variant")
    override fun has(playerName: String, amount: Double): Boolean {
        val player = Bukkit.getOfflinePlayer(playerName)
        return has(player, amount)
    }

    override fun has(player: OfflinePlayer, amount: Double): Boolean {
        return getBalance(player) >= amount
    }

    @Deprecated("Use OfflinePlayer variant")
    override fun has(playerName: String, worldName: String, amount: Double): Boolean {
        val player = Bukkit.getOfflinePlayer(playerName)
        return has(player, worldName, amount)
    }

    override fun has(player: OfflinePlayer, worldName: String, amount: Double): Boolean {
        return getBalance(player, worldName) >= amount
    }

    // ═══════════════════════════════════════════════════════════════════
    // Withdraw Methods
    // ═══════════════════════════════════════════════════════════════════

    @Deprecated("Use OfflinePlayer variant")
    override fun withdrawPlayer(playerName: String, amount: Double): EconomyResponse {
        val player = Bukkit.getOfflinePlayer(playerName)
        return withdrawPlayer(player, amount)
    }

    override fun withdrawPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        if (amount < 0) {
            return EconomyResponse(
                0.0,
                getBalance(player),
                EconomyResponse.ResponseType.FAILURE,
                "Cannot withdraw negative amounts"
            )
        }

        val balance = getBalance(player)
        if (balance < amount) {
            return EconomyResponse(
                0.0,
                balance,
                EconomyResponse.ResponseType.FAILURE,
                "Insufficient funds"
            )
        }

        // Get current group for online player, default for offline
        val onlinePlayer = player.player
        val group = if (onlinePlayer != null) {
            groupService.getGroupForWorld(onlinePlayer.world).name
        } else {
            groupService.getDefaultGroup().name
        }

        val success = economyService.setBalance(player.uniqueId, group, balance - amount)
        return if (success) {
            EconomyResponse(
                amount,
                balance - amount,
                EconomyResponse.ResponseType.SUCCESS,
                null
            )
        } else {
            EconomyResponse(
                0.0,
                balance,
                EconomyResponse.ResponseType.FAILURE,
                "Failed to withdraw"
            )
        }
    }

    @Deprecated("Use OfflinePlayer variant")
    override fun withdrawPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse {
        val player = Bukkit.getOfflinePlayer(playerName)
        return withdrawPlayer(player, worldName, amount)
    }

    override fun withdrawPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse {
        if (amount < 0) {
            return EconomyResponse(
                0.0,
                getBalance(player, worldName),
                EconomyResponse.ResponseType.FAILURE,
                "Cannot withdraw negative amounts"
            )
        }

        val balance = getBalance(player, worldName)
        if (balance < amount) {
            return EconomyResponse(
                0.0,
                balance,
                EconomyResponse.ResponseType.FAILURE,
                "Insufficient funds"
            )
        }

        val group = groupService.getGroupForWorld(worldName).name
        val success = economyService.setBalance(player.uniqueId, group, balance - amount)

        return if (success) {
            EconomyResponse(
                amount,
                balance - amount,
                EconomyResponse.ResponseType.SUCCESS,
                null
            )
        } else {
            EconomyResponse(
                0.0,
                balance,
                EconomyResponse.ResponseType.FAILURE,
                "Failed to withdraw"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Deposit Methods
    // ═══════════════════════════════════════════════════════════════════

    @Deprecated("Use OfflinePlayer variant")
    override fun depositPlayer(playerName: String, amount: Double): EconomyResponse {
        val player = Bukkit.getOfflinePlayer(playerName)
        return depositPlayer(player, amount)
    }

    override fun depositPlayer(player: OfflinePlayer, amount: Double): EconomyResponse {
        if (amount < 0) {
            return EconomyResponse(
                0.0,
                getBalance(player),
                EconomyResponse.ResponseType.FAILURE,
                "Cannot deposit negative amounts"
            )
        }

        val onlinePlayer = player.player
        val group = if (onlinePlayer != null) {
            groupService.getGroupForWorld(onlinePlayer.world).name
        } else {
            groupService.getDefaultGroup().name
        }

        val balance = getBalance(player)
        val success = economyService.setBalance(player.uniqueId, group, balance + amount)

        return if (success) {
            EconomyResponse(
                amount,
                balance + amount,
                EconomyResponse.ResponseType.SUCCESS,
                null
            )
        } else {
            EconomyResponse(
                0.0,
                balance,
                EconomyResponse.ResponseType.FAILURE,
                "Failed to deposit"
            )
        }
    }

    @Deprecated("Use OfflinePlayer variant")
    override fun depositPlayer(playerName: String, worldName: String, amount: Double): EconomyResponse {
        val player = Bukkit.getOfflinePlayer(playerName)
        return depositPlayer(player, worldName, amount)
    }

    override fun depositPlayer(player: OfflinePlayer, worldName: String, amount: Double): EconomyResponse {
        if (amount < 0) {
            return EconomyResponse(
                0.0,
                getBalance(player, worldName),
                EconomyResponse.ResponseType.FAILURE,
                "Cannot deposit negative amounts"
            )
        }

        val group = groupService.getGroupForWorld(worldName).name
        val balance = getBalance(player, worldName)
        val success = economyService.setBalance(player.uniqueId, group, balance + amount)

        return if (success) {
            EconomyResponse(
                amount,
                balance + amount,
                EconomyResponse.ResponseType.SUCCESS,
                null
            )
        } else {
            EconomyResponse(
                0.0,
                balance,
                EconomyResponse.ResponseType.FAILURE,
                "Failed to deposit"
            )
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Account Creation Methods
    // ═══════════════════════════════════════════════════════════════════

    @Deprecated("Use OfflinePlayer variant")
    override fun createPlayerAccount(playerName: String): Boolean {
        // Accounts are created automatically
        return true
    }

    override fun createPlayerAccount(player: OfflinePlayer): Boolean {
        // Accounts are created automatically
        return true
    }

    @Deprecated("Use OfflinePlayer variant")
    override fun createPlayerAccount(playerName: String, worldName: String): Boolean {
        // Accounts are created automatically
        return true
    }

    override fun createPlayerAccount(player: OfflinePlayer, worldName: String): Boolean {
        // Accounts are created automatically
        return true
    }

    // ═══════════════════════════════════════════════════════════════════
    // Bank Methods (Not Supported)
    // ═══════════════════════════════════════════════════════════════════

    override fun createBank(name: String, player: String): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    override fun createBank(name: String, player: OfflinePlayer): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    override fun deleteBank(name: String): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    override fun bankBalance(name: String): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    override fun bankHas(name: String, amount: Double): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    override fun bankWithdraw(name: String, amount: Double): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    override fun bankDeposit(name: String, amount: Double): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    @Deprecated("Use OfflinePlayer variant")
    override fun isBankOwner(name: String, playerName: String): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    override fun isBankOwner(name: String, player: OfflinePlayer): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    @Deprecated("Use OfflinePlayer variant")
    override fun isBankMember(name: String, playerName: String): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    override fun isBankMember(name: String, player: OfflinePlayer): EconomyResponse {
        return EconomyResponse(
            0.0, 0.0,
            EconomyResponse.ResponseType.NOT_IMPLEMENTED,
            "xInventories does not support banks"
        )
    }

    override fun getBanks(): List<String> = emptyList()
}

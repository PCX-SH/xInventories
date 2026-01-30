package sh.pcx.xinventories.internal.service

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player

/**
 * Bridge class that isolates all Vault Economy dependencies.
 * This class is only loaded when Vault is confirmed to be present,
 * preventing NoClassDefFoundError when Vault is not installed.
 */
class VaultEconomyBridge {

    private var economy: Economy? = null

    /**
     * Initializes the bridge by finding the Vault economy provider.
     * @return true if an economy provider was found
     */
    fun initialize(): Boolean {
        val rsp = Bukkit.getServicesManager().getRegistration(Economy::class.java)
        if (rsp != null) {
            economy = rsp.provider
            return true
        }
        return false
    }

    /**
     * Gets the name of the economy provider.
     */
    fun getProviderName(): String? = economy?.name

    /**
     * Gets a player's balance.
     */
    fun getBalance(player: Player): Double {
        return economy?.getBalance(player) ?: 0.0
    }

    /**
     * Gets an offline player's balance.
     */
    fun getBalance(player: OfflinePlayer): Double {
        return economy?.getBalance(player) ?: 0.0
    }

    /**
     * Sets a player's balance by depositing or withdrawing the difference.
     * @return true if successful
     */
    fun setBalance(player: Player, amount: Double): Boolean {
        val econ = economy ?: return false
        val current = econ.getBalance(player)
        val diff = amount - current
        return if (diff > 0) {
            econ.depositPlayer(player, diff).transactionSuccess()
        } else {
            econ.withdrawPlayer(player, -diff).transactionSuccess()
        }
    }

    /**
     * Sets an offline player's balance by depositing or withdrawing the difference.
     * @return true if successful
     */
    fun setBalance(player: OfflinePlayer, amount: Double): Boolean {
        val econ = economy ?: return false
        val current = econ.getBalance(player)
        val diff = amount - current
        return if (diff > 0) {
            econ.depositPlayer(player, diff).transactionSuccess()
        } else {
            econ.withdrawPlayer(player, -diff).transactionSuccess()
        }
    }

    /**
     * Deposits money to a player's account.
     * @return true if successful
     */
    fun deposit(player: Player, amount: Double): Boolean {
        return economy?.depositPlayer(player, amount)?.transactionSuccess() ?: false
    }

    /**
     * Withdraws money from a player's account.
     * @return true if successful
     */
    fun withdraw(player: Player, amount: Double): Boolean {
        return economy?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false
    }

    /**
     * Checks if a player has at least the specified amount.
     */
    fun has(player: Player, amount: Double): Boolean {
        return economy?.has(player, amount) ?: false
    }
}

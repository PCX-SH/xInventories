package sh.pcx.xinventories.api

import org.bukkit.entity.Player
import java.util.UUID

/**
 * API for per-group economy operations.
 * Provides access to group-specific balances when economy integration is enabled.
 */
interface EconomyAPI {

    /**
     * Checks if economy integration is enabled.
     * @return True if economy features are available
     */
    fun isEnabled(): Boolean

    /**
     * Checks if balances are separated by group.
     * When false, all groups share the same balance.
     * @return True if balances are per-group
     */
    fun isSeparateByGroup(): Boolean

    /**
     * Gets a player's balance for a specific group.
     *
     * @param player The player
     * @param group The group name (null for current group)
     * @return The player's balance in the group
     */
    fun getBalance(player: Player, group: String? = null): Double

    /**
     * Gets a player's balance for a specific group by UUID.
     *
     * @param uuid The player's UUID
     * @param group The group name
     * @return The player's balance in the group
     */
    fun getBalance(uuid: UUID, group: String): Double

    /**
     * Gets all balances for a player across all groups.
     *
     * @param uuid The player's UUID
     * @return Map of group name to balance
     */
    fun getAllBalances(uuid: UUID): Map<String, Double>

    /**
     * Sets a player's balance for a specific group.
     *
     * @param player The player
     * @param amount The new balance
     * @param group The group name (null for current group)
     * @return True if successful
     */
    fun setBalance(player: Player, amount: Double, group: String? = null): Boolean

    /**
     * Sets a player's balance for a specific group by UUID.
     *
     * @param uuid The player's UUID
     * @param group The group name
     * @param amount The new balance
     * @return True if successful
     */
    fun setBalance(uuid: UUID, group: String, amount: Double): Boolean

    /**
     * Deposits money to a player's balance in a group.
     *
     * @param player The player
     * @param amount The amount to deposit
     * @param group The group name (null for current group)
     * @return True if successful
     */
    fun deposit(player: Player, amount: Double, group: String? = null): Boolean

    /**
     * Withdraws money from a player's balance in a group.
     *
     * @param player The player
     * @param amount The amount to withdraw
     * @param group The group name (null for current group)
     * @return True if successful, false if insufficient funds
     */
    fun withdraw(player: Player, amount: Double, group: String? = null): Boolean

    /**
     * Transfers money between groups for a player.
     *
     * @param player The player
     * @param fromGroup The source group
     * @param toGroup The destination group
     * @param amount The amount to transfer
     * @return True if successful
     */
    fun transfer(player: Player, fromGroup: String, toGroup: String, amount: Double): Boolean

    /**
     * Transfers money between groups for a player by UUID.
     *
     * @param uuid The player's UUID
     * @param fromGroup The source group
     * @param toGroup The destination group
     * @param amount The amount to transfer
     * @return True if successful
     */
    fun transfer(uuid: UUID, fromGroup: String, toGroup: String, amount: Double): Boolean

    /**
     * Checks if a player has at least the specified amount in a group.
     *
     * @param player The player
     * @param amount The amount to check
     * @param group The group name (null for current group)
     * @return True if the player has sufficient funds
     */
    fun has(player: Player, amount: Double, group: String? = null): Boolean

    /**
     * Checks if a group has separate economy enabled.
     *
     * @param groupName The group name
     * @return True if the group uses separate economy
     */
    fun hasGroupEconomy(groupName: String): Boolean
}

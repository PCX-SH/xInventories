package sh.pcx.xinventories.internal.hook

import sh.pcx.xinventories.PluginContext
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Integration with Vault for permission checking.
 */
class VaultHook(private val plugin: PluginContext) {

    private var permission: Permission? = null
    private var initialized = false

    /**
     * Initializes the Vault hook.
     */
    fun initialize(): Boolean {
        val rsp = Bukkit.getServicesManager().getRegistration(Permission::class.java)
        if (rsp != null) {
            permission = rsp.provider
            initialized = true
            return true
        }
        return false
    }

    /**
     * Checks if the hook is initialized.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Checks if a player has a permission.
     */
    fun hasPermission(player: Player, permission: String): Boolean {
        return this.permission?.has(player, permission) ?: player.hasPermission(permission)
    }

    /**
     * Checks if a player is in a specific permission group.
     */
    fun isInGroup(player: Player, group: String): Boolean {
        return permission?.playerInGroup(player, group) ?: false
    }

    /**
     * Gets the primary group of a player.
     */
    fun getPrimaryGroup(player: Player): String? {
        return try {
            permission?.getPrimaryGroup(player)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Gets all groups a player is in.
     */
    fun getPlayerGroups(player: Player): Array<String> {
        return try {
            permission?.getPlayerGroups(player) ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
    }
}

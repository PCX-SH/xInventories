package sh.pcx.xinventories.internal.hook

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.integration.XInventoriesEconomyProvider
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Manages integration hooks with other plugins.
 */
class HookManager(private val plugin: PluginContext) {

    private var placeholderAPIHook: PlaceholderAPIHook? = null
    private var vaultHook: VaultHook? = null
    private var luckPermsHook: LuckPermsHook? = null
    private var economyProvider: XInventoriesEconomyProvider? = null

    /**
     * Registers all available hooks.
     */
    fun registerHooks() {
        // PlaceholderAPI
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                placeholderAPIHook = PlaceholderAPIHook(plugin)
                placeholderAPIHook?.register()
                Logging.info("PlaceholderAPI hook registered")
            } catch (e: Exception) {
                Logging.warning("Failed to register PlaceholderAPI hook: ${e.message}")
            }
        }

        // Vault
        if (Bukkit.getPluginManager().getPlugin("Vault") != null) {
            try {
                vaultHook = VaultHook(plugin)
                if (vaultHook?.initialize() == true) {
                    Logging.info("Vault permission hook registered")
                }

                // Register economy provider if economy integration is enabled
                val economyConfig = plugin.configManager.mainConfig.economy
                if (economyConfig.enabled && economyConfig.separateByGroup) {
                    registerEconomyProvider()
                }
            } catch (e: Exception) {
                Logging.warning("Failed to register Vault hook: ${e.message}")
            }
        }

        // LuckPerms
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            try {
                luckPermsHook = LuckPermsHook(plugin)
                if (luckPermsHook?.initialize() == true) {
                    Logging.info("LuckPerms context hook registered")
                } else {
                    luckPermsHook = null
                }
            } catch (e: NoClassDefFoundError) {
                // LuckPerms API classes not available
                Logging.debug { "LuckPerms API not available on classpath" }
                luckPermsHook = null
            } catch (e: Exception) {
                Logging.warning("Failed to register LuckPerms hook: ${e.message}")
                luckPermsHook = null
            }
        }
    }

    /**
     * Registers the xInventories economy provider with Vault.
     */
    private fun registerEconomyProvider() {
        try {
            economyProvider = XInventoriesEconomyProvider(
                plugin,
                plugin.serviceManager.economyService,
                plugin.serviceManager.groupService
            )
            economyProvider?.register()
        } catch (e: Exception) {
            Logging.warning("Failed to register economy provider: ${e.message}")
        }
    }

    /**
     * Unregisters all hooks.
     */
    fun unregisterHooks() {
        placeholderAPIHook?.unregister()
        placeholderAPIHook = null

        // Unregister LuckPerms hook
        luckPermsHook?.unregister()
        luckPermsHook = null

        // Unregister economy provider
        economyProvider?.unregister()
        economyProvider = null

        vaultHook = null
    }

    /**
     * Checks if PlaceholderAPI is available.
     */
    fun hasPlaceholderAPI(): Boolean = placeholderAPIHook != null

    /**
     * Checks if Vault is available.
     */
    fun hasVault(): Boolean = vaultHook?.isInitialized() == true

    /**
     * Checks if LuckPerms is available.
     */
    fun hasLuckPerms(): Boolean = luckPermsHook?.isInitialized() == true

    /**
     * Gets the Vault hook, if available.
     */
    fun getVaultHook(): VaultHook? = vaultHook

    /**
     * Gets the LuckPerms hook, if available.
     */
    fun getLuckPermsHook(): LuckPermsHook? = luckPermsHook

    /**
     * Signals to LuckPerms that a player's context has changed.
     * This should be called when a player changes inventory groups.
     *
     * @param player The player whose context changed
     */
    fun signalLuckPermsContextUpdate(player: Player) {
        luckPermsHook?.signalContextUpdate(player)
    }
}

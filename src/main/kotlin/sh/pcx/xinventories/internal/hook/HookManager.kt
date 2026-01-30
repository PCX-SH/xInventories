package sh.pcx.xinventories.internal.hook

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.integration.XInventoriesEconomyProvider
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.Bukkit

/**
 * Manages integration hooks with other plugins.
 */
class HookManager(private val plugin: XInventories) {

    private var placeholderAPIHook: PlaceholderAPIHook? = null
    private var vaultHook: VaultHook? = null
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
     * Gets the Vault hook, if available.
     */
    fun getVaultHook(): VaultHook? = vaultHook
}

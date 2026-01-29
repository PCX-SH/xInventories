package sh.pcx.xinventories.internal.hook

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.Bukkit

/**
 * Manages integration hooks with other plugins.
 */
class HookManager(private val plugin: XInventories) {

    private var placeholderAPIHook: PlaceholderAPIHook? = null
    private var vaultHook: VaultHook? = null

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
                    Logging.info("Vault hook registered")
                }
            } catch (e: Exception) {
                Logging.warning("Failed to register Vault hook: ${e.message}")
            }
        }
    }

    /**
     * Unregisters all hooks.
     */
    fun unregisterHooks() {
        placeholderAPIHook?.unregister()
        placeholderAPIHook = null

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

package sh.pcx.xinventories

import sh.pcx.xinventories.api.XInventoriesProvider
import sh.pcx.xinventories.internal.api.XInventoriesAPIImpl
import sh.pcx.xinventories.internal.command.CommandManager
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.gui.GUIManager
import sh.pcx.xinventories.internal.hook.HookManager
import sh.pcx.xinventories.internal.listener.ListenerManager
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.AsyncStorageDispatcher
import sh.pcx.xinventories.internal.util.BukkitMainThreadDispatcher
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.bukkit.plugin.java.JavaPlugin

/**
 * Main plugin class for xInventories.
 * Provides advanced per-world inventory management for Paper servers.
 */
class XInventories : JavaPlugin(), CoroutineScope {

    // Coroutine dispatchers
    lateinit var mainThreadDispatcher: BukkitMainThreadDispatcher
        private set
    lateinit var storageDispatcher: AsyncStorageDispatcher
        private set

    // Configuration manager
    lateinit var configManager: ConfigManager
        private set

    // Service manager
    lateinit var serviceManager: ServiceManager
        private set

    // Listener manager
    lateinit var listenerManager: ListenerManager
        private set

    // Command manager
    lateinit var commandManager: CommandManager
        private set

    // GUI manager
    lateinit var guiManager: GUIManager
        private set

    // Hook manager
    lateinit var hookManager: HookManager
        private set

    // API implementation
    lateinit var api: XInventoriesAPIImpl
        private set

    // Coroutine scope job
    private val job = SupervisorJob()
    override val coroutineContext get() = job + mainThreadDispatcher

    // Expose coroutine scope for external use
    val scope: CoroutineScope get() = this

    @Suppress("DEPRECATION")
    override fun onEnable() {
        // Initialize logging
        Logging.init(logger, false)
        Logging.info("Enabling xInventories v${description.version}")

        // Initialize dispatchers
        mainThreadDispatcher = BukkitMainThreadDispatcher(this)
        storageDispatcher = AsyncStorageDispatcher(4) // Default pool size, will be updated from config

        // Save default config if it doesn't exist
        saveDefaultConfig()
        // Note: groups.yml and messages.yml are saved by ConfigManager if needed

        // Initialize ConfigManager
        configManager = ConfigManager(this)
        if (!configManager.loadAll()) {
            Logging.severe("Failed to load configuration! Plugin may not function correctly.")
        }

        // Update dispatcher pool size from config
        val poolSize = configManager.mainConfig.performance.threadPoolSize
        if (poolSize != 4) {
            storageDispatcher.shutdown()
            storageDispatcher = AsyncStorageDispatcher(poolSize)
        }

        // Initialize ServiceManager
        serviceManager = ServiceManager(this, this)
        runBlocking {
            try {
                serviceManager.initialize()
            } catch (e: Exception) {
                Logging.error("Failed to initialize services", e)
                Logging.severe("Plugin may not function correctly!")
            }
        }

        // Register Listeners
        listenerManager = ListenerManager(this)
        listenerManager.registerAll()

        // Register Commands
        commandManager = CommandManager(this)
        commandManager.initialize()

        // Initialize GUI Manager
        guiManager = GUIManager(this)
        guiManager.initialize()

        // Initialize API and register provider
        api = XInventoriesAPIImpl(this)
        XInventoriesProvider.register(api)

        // Initialize hooks (PlaceholderAPI, Vault)
        hookManager = HookManager(this)
        hookManager.registerHooks()

        Logging.info("xInventories enabled successfully!")
    }

    override fun onDisable() {
        Logging.info("Disabling xInventories...")

        // Unregister hooks
        if (::hookManager.isInitialized) {
            hookManager.unregisterHooks()
        }

        // Shutdown GUI manager
        if (::guiManager.isInitialized) {
            guiManager.shutdown()
        }

        // Shutdown command manager
        if (::commandManager.isInitialized) {
            commandManager.shutdown()
        }

        // Unregister listeners
        if (::listenerManager.isInitialized) {
            listenerManager.unregisterAll()
        }

        // Save all online player data
        if (::serviceManager.isInitialized && serviceManager.isInitialized()) {
            runBlocking {
                try {
                    // Save online players
                    server.onlinePlayers.forEach { player ->
                        try {
                            serviceManager.inventoryService.saveInventory(player)
                        } catch (e: Exception) {
                            Logging.error("Failed to save inventory for ${player.name}", e)
                        }
                    }

                    // Shutdown services
                    serviceManager.shutdown()
                } catch (e: Exception) {
                    Logging.error("Error during shutdown", e)
                }
            }
        }

        // Unregister API
        if (XInventoriesProvider.isAvailable()) {
            XInventoriesProvider.unregister()
        }

        // Cancel all coroutines
        job.cancel()
        cancel("Plugin disabled")

        // Shutdown storage dispatcher
        if (::storageDispatcher.isInitialized) {
            storageDispatcher.shutdown()
        }

        Logging.info("xInventories disabled.")
    }

    /**
     * Reloads the plugin configuration.
     */
    fun reload(): Boolean {
        Logging.info("Reloading xInventories configuration...")

        val configSuccess = configManager.reloadAll()

        if (::serviceManager.isInitialized) {
            serviceManager.reload()
        }

        if (configSuccess) {
            Logging.info("Configuration reloaded successfully.")
        } else {
            Logging.warning("Failed to reload some configuration files.")
        }

        return configSuccess
    }

    companion object {
        /**
         * Gets the plugin instance.
         */
        @JvmStatic
        fun getInstance(): XInventories {
            return getPlugin(XInventories::class.java)
        }
    }
}

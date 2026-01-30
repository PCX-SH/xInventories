package sh.pcx.xinventories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie
import org.bukkit.plugin.java.JavaPlugin
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
import sh.pcx.xinventories.internal.util.StartupBanner
import sh.pcx.xinventories.loader.LoaderBootstrap

/**
 * Bootstrap implementation for xInventories.
 *
 * This class is instantiated by the [sh.pcx.xinventories.loader.XInventoriesLoader]
 * after dependencies have been downloaded and loaded. It contains the actual
 * plugin logic and delegates lifecycle methods from the loader.
 */
class XInventoriesBootstrap : LoaderBootstrap, PluginContext {

    // Reference to the loader plugin
    private lateinit var loader: JavaPlugin

    // PluginContext implementation
    override val plugin: JavaPlugin
        get() = loader

    // Coroutine dispatchers
    override lateinit var mainThreadDispatcher: CoroutineDispatcher
        private set
    override lateinit var storageDispatcher: AsyncStorageDispatcher
        private set

    // Configuration manager
    override lateinit var configManager: ConfigManager
        private set

    // Service manager
    override lateinit var serviceManager: ServiceManager
        private set

    // Listener manager
    override lateinit var listenerManager: ListenerManager
        private set

    // Command manager
    override lateinit var commandManager: CommandManager
        private set

    // GUI manager
    override lateinit var guiManager: GUIManager
        private set

    // Hook manager
    override lateinit var hookManager: HookManager
        private set

    // API implementation
    lateinit var api: XInventoriesAPIImpl
        private set

    // Coroutine scope job
    private val job = SupervisorJob()
    override val coroutineContext
        get() = job + mainThreadDispatcher

    // Expose coroutine scope for external use
    override val scope: CoroutineScope
        get() = this

    override fun onLoad(loader: JavaPlugin) {
        this.loader = loader
        // Nothing to do on load currently
    }

    @Suppress("DEPRECATION")
    override fun onEnable(loader: JavaPlugin) {
        this.loader = loader

        // Initialize logging
        Logging.init(loader.logger, false)
        Logging.info("Enabling xInventories v${loader.description.version}")

        // Initialize dispatchers
        mainThreadDispatcher = BukkitMainThreadDispatcher(loader)
        storageDispatcher = AsyncStorageDispatcher(4)

        // Save default config if it doesn't exist
        loader.saveDefaultConfig()

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

        // Initialize bStats metrics
        initializeMetrics()

        // Display startup banner
        StartupBanner.display(this)

        Logging.info("xInventories enabled successfully!")
    }

    override fun onDisable(loader: JavaPlugin) {
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
                    loader.server.onlinePlayers.forEach { player ->
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

    /** Reloads the plugin configuration. */
    override fun reload(): Boolean {
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

    /** Initializes bStats metrics collection. */
    private fun initializeMetrics() {
        // Check if metrics are enabled in config
        if (!configManager.mainConfig.metrics) {
            Logging.debug { "bStats metrics disabled in config" }
            return
        }

        // bStats plugin ID
        val pluginId = 29163

        try {
            val metrics = Metrics(loader, pluginId)

            // Storage type chart
            metrics.addCustomChart(
                SimplePie("storage_type") {
                    configManager.mainConfig.storage.type.name.lowercase()
                }
            )

            // Number of groups chart
            metrics.addCustomChart(
                SimplePie("group_count") {
                    val count = serviceManager.groupService.getAllGroups().size
                    when {
                        count <= 1 -> "1"
                        count <= 3 -> "2-3"
                        count <= 5 -> "4-5"
                        count <= 10 -> "6-10"
                        else -> "10+"
                    }
                }
            )

            // Sync enabled chart
            metrics.addCustomChart(
                SimplePie("sync_enabled") {
                    if (configManager.mainConfig.sync.enabled) "enabled" else "disabled"
                }
            )

            // Economy integration chart
            metrics.addCustomChart(
                SimplePie("economy_integration") {
                    if (configManager.mainConfig.economy.enabled) "enabled" else "disabled"
                }
            )

            // Versioning enabled chart
            metrics.addCustomChart(
                SimplePie("versioning_enabled") {
                    if (configManager.mainConfig.versioning.enabled) "enabled" else "disabled"
                }
            )

            // Death recovery enabled chart
            metrics.addCustomChart(
                SimplePie("death_recovery_enabled") {
                    if (configManager.mainConfig.deathRecovery.enabled) "enabled"
                    else "disabled"
                }
            )

            Logging.debug { "bStats metrics initialized" }
        } catch (e: Exception) {
            Logging.debug { "Failed to initialize bStats: ${e.message}" }
        }
    }

    companion object {
        // Static reference for legacy compatibility
        private var instance: XInventoriesBootstrap? = null

        /**
         * Gets the bootstrap instance.
         * @throws IllegalStateException if called before plugin is enabled
         */
        @JvmStatic
        fun getInstance(): XInventoriesBootstrap {
            return instance ?: throw IllegalStateException("xInventories is not enabled")
        }

        internal fun setInstance(bootstrap: XInventoriesBootstrap?) {
            instance = bootstrap
        }
    }
}

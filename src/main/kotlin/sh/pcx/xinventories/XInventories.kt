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

/**
 * Main plugin class for xInventories. Provides advanced per-world inventory management for Paper
 * servers.
 */
class XInventories : JavaPlugin(), CoroutineScope {

    // Coroutine dispatchers
    lateinit var mainThreadDispatcher: CoroutineDispatcher
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
    override val coroutineContext
        get() = job + mainThreadDispatcher

    // Expose coroutine scope for external use
    val scope: CoroutineScope
        get() = this

    @Suppress("DEPRECATION")
    override fun onEnable() {
        // Initialize logging
        Logging.init(logger, false)
        Logging.info("Enabling xInventories v${description.version}")

        // Initialize dispatchers
        mainThreadDispatcher = BukkitMainThreadDispatcher(this)
        storageDispatcher =
                AsyncStorageDispatcher(4) // Default pool size, will be updated from config

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

        // Initialize bStats metrics
        initializeMetrics()

        // Display startup banner
        StartupBanner.display(this)

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

    /** Reloads the plugin configuration. */
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
            val metrics = Metrics(this, pluginId)

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
        /** Gets the plugin instance. */
        @JvmStatic
        fun getInstance(): XInventories {
            return getPlugin(XInventories::class.java)
        }
    }
}

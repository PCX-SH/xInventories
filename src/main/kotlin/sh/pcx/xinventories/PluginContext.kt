package sh.pcx.xinventories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import org.bukkit.plugin.java.JavaPlugin
import sh.pcx.xinventories.internal.command.CommandManager
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.gui.GUIManager
import sh.pcx.xinventories.internal.hook.HookManager
import sh.pcx.xinventories.internal.listener.ListenerManager
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.AsyncStorageDispatcher

/**
 * Interface providing access to plugin resources and services.
 *
 * This interface abstracts the plugin context, allowing components to work
 * with both the traditional [XInventories] plugin class and the new
 * [XInventoriesBootstrap] loaded through the dependency loader.
 *
 * All managers and services should depend on this interface rather than
 * concrete plugin classes for flexibility.
 *
 * Note: This interface deliberately avoids properties like `server`, `dataFolder`,
 * `logger`, `description`, `config`, and `isEnabled` to prevent conflicts with
 * JavaPlugin's final methods when implemented by classes that extend JavaPlugin.
 * Use `plugin.server`, `plugin.dataFolder`, etc. instead.
 */
interface PluginContext : CoroutineScope {

    /**
     * The underlying JavaPlugin instance.
     * Use this for Bukkit API calls that require a Plugin reference.
     * Access server, dataFolder, logger, etc. through this property.
     */
    val plugin: JavaPlugin

    /**
     * Dispatcher for main thread operations.
     */
    val mainThreadDispatcher: CoroutineDispatcher

    /**
     * Dispatcher for async storage operations.
     */
    val storageDispatcher: AsyncStorageDispatcher

    /**
     * The configuration manager.
     */
    val configManager: ConfigManager

    /**
     * The service manager containing all plugin services.
     */
    val serviceManager: ServiceManager

    /**
     * The listener manager for event handling.
     */
    val listenerManager: ListenerManager

    /**
     * The command manager for command handling.
     */
    val commandManager: CommandManager

    /**
     * The GUI manager for inventory GUIs.
     */
    val guiManager: GUIManager

    /**
     * The hook manager for external plugin integrations.
     */
    val hookManager: HookManager

    /**
     * The coroutine scope for launching coroutines.
     */
    val scope: CoroutineScope
        get() = this

    /**
     * Reloads the plugin configuration.
     *
     * @return true if reload was successful
     */
    fun reload(): Boolean
}

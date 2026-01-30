package sh.pcx.xinventories.loader

import org.bukkit.plugin.java.JavaPlugin

/**
 * Interface that the actual plugin implementation must implement.
 *
 * This allows the loader to instantiate and delegate lifecycle methods
 * to the real plugin without having a compile-time dependency on it.
 */
interface LoaderBootstrap {

    /**
     * Called when the plugin is loaded (before enable).
     * Corresponds to [JavaPlugin.onLoad].
     *
     * @param loader The loader plugin instance
     */
    fun onLoad(loader: JavaPlugin)

    /**
     * Called when the plugin is enabled.
     * Corresponds to [JavaPlugin.onEnable].
     *
     * @param loader The loader plugin instance
     */
    fun onEnable(loader: JavaPlugin)

    /**
     * Called when the plugin is disabled.
     * Corresponds to [JavaPlugin.onDisable].
     *
     * @param loader The loader plugin instance
     */
    fun onDisable(loader: JavaPlugin)
}

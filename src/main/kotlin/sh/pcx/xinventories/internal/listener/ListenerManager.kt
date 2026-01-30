package sh.pcx.xinventories.internal.listener

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.event.HandlerList

/**
 * Manages registration and unregistration of all event listeners.
 */
class ListenerManager(private val context: PluginContext) {

    private val listeners = mutableListOf<org.bukkit.event.Listener>()
    private var registered = false

    /**
     * Registers all event listeners.
     */
    fun registerAll() {
        if (registered) {
            Logging.warning("Listeners already registered")
            return
        }

        // Create listeners
        listeners.add(ConnectionListener(context))
        listeners.add(WorldChangeListener(context))
        listeners.add(GameModeListener(context))
        listeners.add(InventoryListener(context))
        listeners.add(InventoryLockListener(context))

        // Register with Bukkit
        listeners.forEach { listener ->
            context.plugin.server.pluginManager.registerEvents(listener, context.plugin)
        }

        registered = true
        Logging.debug { "Registered ${listeners.size} event listeners" }
    }

    /**
     * Unregisters all event listeners.
     */
    fun unregisterAll() {
        if (!registered) return

        // Unregister all handlers for this plugin
        HandlerList.unregisterAll(context.plugin)

        listeners.clear()
        registered = false
        Logging.debug { "Unregistered all event listeners" }
    }

    /**
     * Checks if listeners are registered.
     */
    fun isRegistered(): Boolean = registered
}

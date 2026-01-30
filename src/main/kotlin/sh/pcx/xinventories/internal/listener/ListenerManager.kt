package sh.pcx.xinventories.internal.listener

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.event.HandlerList

/**
 * Manages registration and unregistration of all event listeners.
 */
class ListenerManager(private val plugin: XInventories) {

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
        listeners.add(ConnectionListener(plugin))
        listeners.add(WorldChangeListener(plugin))
        listeners.add(GameModeListener(plugin))
        listeners.add(InventoryListener(plugin))
        listeners.add(InventoryLockListener(plugin))

        // Register with Bukkit
        listeners.forEach { listener ->
            plugin.server.pluginManager.registerEvents(listener, plugin)
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
        HandlerList.unregisterAll(plugin)

        listeners.clear()
        registered = false
        Logging.debug { "Unregistered all event listeners" }
    }

    /**
     * Checks if listeners are registered.
     */
    fun isRegistered(): Boolean = registered
}

package sh.pcx.xinventories.internal.gui

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryAction
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages GUI instances and handles inventory events.
 */
class GUIManager(private val plugin: XInventories) : Listener {

    private val openGUIs = ConcurrentHashMap<UUID, GUI>()
    private val openInventories = ConcurrentHashMap<UUID, Inventory>()

    fun initialize() {
        plugin.server.pluginManager.registerEvents(this, plugin)
        Logging.debug("GUIManager initialized and events registered")
    }

    /**
     * Opens a GUI for a player.
     */
    fun open(player: Player, gui: GUI) {
        Logging.debug("Opening GUI ${gui.javaClass.simpleName} for ${player.name}")

        // Create the inventory first
        val inventory = gui.createInventory(player)

        // Track the GUI BEFORE opening the inventory
        openGUIs[player.uniqueId] = gui
        openInventories[player.uniqueId] = inventory

        // Open for player - this may trigger InventoryCloseEvent for any previous inventory
        player.openInventory(inventory)

        Logging.debug("GUI opened, tracking ${openGUIs.size} GUIs")
    }

    /**
     * Closes any GUI the player has open.
     */
    fun close(player: Player) {
        openGUIs.remove(player.uniqueId)
        openInventories.remove(player.uniqueId)
    }

    /**
     * Gets the GUI a player has open, if any.
     */
    fun getOpenGUI(player: Player): GUI? = openGUIs[player.uniqueId]

    /**
     * Checks if a player has a GUI open.
     */
    fun hasGUIOpen(player: Player): Boolean = openGUIs.containsKey(player.uniqueId)

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onInventoryClick(event: InventoryClickEvent) {
        val player = event.whoClicked as? Player ?: return

        // Check if this player has a GUI open
        val gui = openGUIs[player.uniqueId] ?: return
        val trackedInventory = openInventories[player.uniqueId] ?: return

        // Verify this is actually our GUI inventory
        if (event.view.topInventory != trackedInventory) {
            return
        }

        // Cancel ALL clicks unconditionally - this prevents any item movement
        event.isCancelled = true
        event.result = org.bukkit.event.Event.Result.DENY

        // Block specific actions that could move items
        if (event.action == InventoryAction.MOVE_TO_OTHER_INVENTORY ||
            event.action == InventoryAction.COLLECT_TO_CURSOR ||
            event.action == InventoryAction.HOTBAR_SWAP ||
            event.action == InventoryAction.HOTBAR_MOVE_AND_READD) {
            return // Already cancelled, don't process click
        }

        // Only process clicks in the top inventory (our GUI)
        val clickedInventory = event.clickedInventory ?: return
        if (clickedInventory != event.view.topInventory) {
            // Click was in player's inventory - already cancelled, just return
            return
        }

        // Let the GUI handle the click for its logic
        try {
            gui.onClick(event)
        } catch (e: Exception) {
            Logging.error("Error handling GUI click for ${player.name}", e)
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val player = event.whoClicked as? Player ?: return

        // Check if player has a GUI open
        if (!openGUIs.containsKey(player.uniqueId)) return

        val trackedInventory = openInventories[player.uniqueId] ?: return

        // Verify this is our GUI
        if (event.view.topInventory != trackedInventory) {
            return
        }

        // Cancel all drag events in our GUIs
        event.isCancelled = true
        event.result = org.bukkit.event.Event.Result.DENY
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return

        // Check if the closed inventory is our tracked inventory
        val trackedInventory = openInventories[player.uniqueId]
        if (trackedInventory != null && event.inventory == trackedInventory) {
            val gui = openGUIs.remove(player.uniqueId)
            openInventories.remove(player.uniqueId)

            // Notify the GUI
            try {
                gui?.onClose(event)
            } catch (e: Exception) {
                Logging.error("Error handling GUI close for ${player.name}", e)
            }

            Logging.debug("GUI closed for ${player.name}, tracking ${openGUIs.size} GUIs")
        }
    }

    fun shutdown() {
        // Close all open GUIs
        openGUIs.keys.toList().forEach { uuid ->
            plugin.server.getPlayer(uuid)?.closeInventory()
        }
        openGUIs.clear()
        openInventories.clear()
    }
}

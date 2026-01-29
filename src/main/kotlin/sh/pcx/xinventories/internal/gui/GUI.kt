package sh.pcx.xinventories.internal.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory

/**
 * Base interface for GUI menus.
 */
interface GUI {

    /**
     * The title of this GUI.
     */
    val title: Component

    /**
     * The size of this GUI in slots (must be multiple of 9).
     */
    val size: Int

    /**
     * Creates the inventory for this GUI.
     */
    fun createInventory(player: Player): Inventory

    /**
     * Opens this GUI for the given player.
     */
    fun open(player: Player)

    /**
     * Handles a click event in this GUI.
     * @return true if the event should be cancelled
     */
    fun onClick(event: InventoryClickEvent): Boolean

    /**
     * Handles the GUI being closed.
     */
    fun onClose(event: InventoryCloseEvent)

    /**
     * Refreshes the GUI contents.
     */
    fun refresh(player: Player)
}

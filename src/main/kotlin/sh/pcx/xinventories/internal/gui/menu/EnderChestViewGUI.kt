package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.PlayerData
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * GUI for viewing a player's stored ender chest contents.
 */
class EnderChestViewGUI(
    plugin: PluginContext,
    private val targetUUID: UUID,
    private val targetName: String,
    private val groupName: String,
    private val gameMode: GameMode
) : AbstractGUI(
    plugin,
    Component.text("$targetName - Ender Chest", NamedTextColor.DARK_PURPLE),
    36
) {

    private var playerData: PlayerData? = null

    init {
        loadData()
        setupItems()
    }

    private fun loadData() {
        playerData = runBlocking {
            plugin.serviceManager.storageService.load(targetUUID, groupName, gameMode)
        }
    }

    private fun setupItems() {
        val data = playerData

        if (data == null) {
            setItem(13, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("No Data", NamedTextColor.RED)
                .lore("No ender chest data found")
                .build()
            )

            setItem(31, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Back", NamedTextColor.GRAY)
                .lore("Return to inventory view")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    InventoryViewGUI(plugin, targetUUID, targetName, groupName, gameMode).open(player)
                }
                .build()
            )
            return
        }

        // Display ender chest contents (27 slots)
        for (i in 0..26) {
            val item = data.enderChest[i] ?: ItemStack(Material.AIR)
            if (item.type != Material.AIR) {
                setItem(i, GUIItem(item.clone()))
            }
        }

        // Bottom row - controls
        for (i in 27..35) {
            setItem(i, GUIComponents.filler(Material.PURPLE_STAINED_GLASS_PANE))
        }

        // Info
        setItem(27, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Ender Chest", NamedTextColor.LIGHT_PURPLE)
            .lore("Group: $groupName")
            .lore("GameMode: $gameMode")
            .build()
        )

        // Back button
        setItem(31, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to inventory view")
            .onClick { event ->
                val player = event.whoClicked as Player
                InventoryViewGUI(plugin, targetUUID, targetName, groupName, gameMode).open(player)
            }
            .build()
        )

        // Close button
        setItem(35, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )
    }

    override fun fillEmptySlots(inventory: org.bukkit.inventory.Inventory) {
        // Fill any remaining empty slots both visually AND in the items map
        val filler = GUIComponents.filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

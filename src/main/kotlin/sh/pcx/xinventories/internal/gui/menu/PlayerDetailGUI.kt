package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.PlayerData
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * GUI showing details and inventory snapshots for a player.
 */
class PlayerDetailGUI(
    plugin: XInventories,
    private val targetUUID: UUID,
    private val targetName: String
) : AbstractGUI(
    plugin,
    Component.text("Player: $targetName", NamedTextColor.DARK_AQUA),
    54
) {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    init {
        setupItems()
    }

    private fun setupItems() {
        // Fill border
        fillBorder(GUIComponents.filler())

        // Player head info
        val headItem = ItemStack(Material.PLAYER_HEAD)
        val headMeta = headItem.itemMeta as SkullMeta
        headMeta.owningPlayer = Bukkit.getOfflinePlayer(targetUUID)
        headMeta.displayName(
            Component.text(targetName)
                .color(NamedTextColor.GOLD)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        )

        val isOnline = Bukkit.getPlayer(targetUUID) != null
        headMeta.lore(listOf(
            Component.text("UUID: ${targetUUID}")
                .color(NamedTextColor.GRAY)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false),
            Component.text("Status: ${if (isOnline) "Online" else "Offline"}")
                .color(if (isOnline) NamedTextColor.GREEN else NamedTextColor.RED)
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)
        ))
        headItem.itemMeta = headMeta
        setItem(1, 4, GUIItem(headItem))

        // Load player data and show snapshots
        val storageService = plugin.serviceManager.storageService
        val groupService = plugin.serviceManager.groupService
        val groups = groupService.getAllGroups()

        var slot = 19 // Start position for group items

        for (group in groups.take(7)) { // Show up to 7 groups in the middle rows
            val hasData = runBlocking {
                storageService.hasData(targetUUID, group.name)
            }

            val material = when {
                hasData -> Material.CHEST
                else -> Material.GRAY_SHULKER_BOX
            }

            val color = when {
                hasData -> NamedTextColor.GOLD
                else -> NamedTextColor.GRAY
            }

            setItem(slot, GUIItemBuilder()
                .material(material)
                .name(group.name, color)
                .lore(if (hasData) "Has inventory data" else "No data stored")
                .lore("")
                .lore(if (hasData) "Click to view inventory" else "")
                .onClick { event ->
                    if (hasData) {
                        val player = event.whoClicked as Player
                        InventoryViewGUI(plugin, targetUUID, targetName, group.name).open(player)
                    }
                }
                .build()
            )

            slot++
            if ((slot - 19) % 7 == 0) {
                slot += 2 // Skip to next row
            }
        }

        // Actions row
        if (isOnline) {
            setItem(5, 2, GUIItemBuilder()
                .material(Material.CHEST_MINECART)
                .name("Force Save", NamedTextColor.GREEN)
                .lore("Save player's current inventory")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    val target = Bukkit.getPlayer(targetUUID)
                    if (target != null) {
                        plugin.scope.launch(plugin.storageDispatcher) {
                            plugin.serviceManager.inventoryService.saveInventory(target)
                            kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                                player.sendMessage(Component.text("Saved ${targetName}'s inventory.", NamedTextColor.GREEN))
                            }
                        }
                    }
                }
                .build()
            )

            setItem(5, 4, GUIItemBuilder()
                .material(Material.HOPPER_MINECART)
                .name("Force Load", NamedTextColor.AQUA)
                .lore("Reload player's inventory from storage")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    val target = Bukkit.getPlayer(targetUUID)
                    if (target != null) {
                        plugin.scope.launch(plugin.storageDispatcher) {
                            val group = plugin.serviceManager.groupService.getGroupForWorld(target.world)
                            plugin.serviceManager.inventoryService.loadInventory(
                                target,
                                group.toApiModel(),
                                sh.pcx.xinventories.api.event.InventoryLoadEvent.LoadReason.COMMAND
                            )
                            kotlinx.coroutines.withContext(plugin.mainThreadDispatcher) {
                                player.sendMessage(Component.text("Reloaded ${targetName}'s inventory.", NamedTextColor.AQUA))
                            }
                        }
                    }
                }
                .build()
            )
        }

        // Back button - explicitly set after all other items to ensure it's not overwritten
        val backButton = GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to player list")
            .onClick { event ->
                val player = event.whoClicked as Player
                PlayerListGUI(plugin).open(player)
            }
            .build()
        setItem(45, backButton) // Row 5, Col 0 = slot 45

        // Close button
        setItem(5, 8, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )
    }

    override fun fillEmptySlots(inventory: Inventory) {
        // Fill any remaining empty slots both visually AND in the items map
        val filler = GUIComponents.filler()
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

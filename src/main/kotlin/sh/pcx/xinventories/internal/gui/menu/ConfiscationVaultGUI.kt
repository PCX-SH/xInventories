package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.ConfiscatedItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * GUI for viewing and claiming confiscated items from the player vault.
 * Items end up here when they violate restrictions with MOVE_TO_VAULT action.
 */
class ConfiscationVaultGUI(
    plugin: XInventories,
    private val targetUuid: UUID,
    private val targetName: String,
    private val isAdmin: Boolean = false,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text(if (isAdmin) "Vault: $targetName" else "Your Confiscated Items", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 36
    private val confiscatedItems: List<ConfiscatedItem>
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    init {
        confiscatedItems = runBlocking {
            plugin.serviceManager.restrictionService.getConfiscatedItems(targetUuid)
        }
        setupItems()
    }

    private fun setupItems() {
        // Fill bottom two rows with filler
        for (i in 36..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (confiscatedItems.isEmpty()) 0 else (confiscatedItems.size - 1) / itemsPerPage

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore(if (isAdmin) "Return to player detail" else "Close")
            .onClick { event ->
                val player = event.whoClicked as Player
                if (isAdmin) {
                    // Return to player detail GUI
                    player.closeInventory()
                } else {
                    player.closeInventory()
                }
            }
            .build()
        )

        // Claim all button
        if (confiscatedItems.isNotEmpty()) {
            setItem(46, GUIItemBuilder()
                .material(Material.HOPPER)
                .name("Claim All Items", NamedTextColor.GREEN)
                .lore("Retrieve all ${confiscatedItems.size} items")
                .lore("to your inventory")
                .lore("")
                .lore("Items that don't fit will")
                .lore("be dropped at your feet")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    if (!isAdmin && player.uniqueId != targetUuid) {
                        player.sendMessage(Component.text("You can only claim your own items!", NamedTextColor.RED))
                        return@onClick
                    }
                    val targetPlayer = if (isAdmin) plugin.server.getPlayer(targetUuid) else player
                    if (targetPlayer == null) {
                        player.sendMessage(Component.text("Target player is not online!", NamedTextColor.RED))
                        return@onClick
                    }

                    runBlocking {
                        val claimed = plugin.serviceManager.restrictionService.claimAllConfiscatedItems(targetPlayer)
                        player.sendMessage(Component.text("Claimed $claimed confiscated items", NamedTextColor.GREEN))
                    }
                    ConfiscationVaultGUI(plugin, targetUuid, targetName, isAdmin, 0).open(player)
                }
                .build()
            )
        }

        // Info
        setItem(47, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Confiscated Items", NamedTextColor.GOLD)
            .lore("These items were removed because")
            .lore("they violated item restrictions.")
            .lore("")
            .lore("Click an item to claim it back.")
            .lore("Left-click: Claim item")
            .apply {
                if (isAdmin) {
                    lore("Right-click: Delete item")
                }
            }
            .build()
        )

        // Previous page button
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .lore("Go to page $page")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    ConfiscationVaultGUI(plugin, targetUuid, targetName, isAdmin, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("${confiscatedItems.size} total items")
            .build()
        )

        // Next page button
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .lore("Go to page ${page + 2}")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    ConfiscationVaultGUI(plugin, targetUuid, targetName, isAdmin, page + 1).open(player)
                }
                .build()
            )
        }

        // Close button
        setItem(53, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )

        // Populate confiscated items
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, confiscatedItems.size)

        for (i in startIndex until endIndex) {
            val confiscatedItem = confiscatedItems[i]
            val slot = i - startIndex
            setItem(slot, createConfiscatedItemDisplay(confiscatedItem))
        }
    }

    private fun createConfiscatedItemDisplay(confiscatedItem: ConfiscatedItem): GUIItem {
        // Try to create a display item from the stored data
        val displayItem = confiscatedItem.toItemStack()?.clone() ?: ItemStack(Material.BARRIER)

        val meta = displayItem.itemMeta
        val existingLore = meta.lore() ?: mutableListOf()
        val newLore = mutableListOf<Component>()

        // Add separator if there's existing lore
        if (existingLore.isNotEmpty()) {
            newLore.addAll(existingLore)
            newLore.add(Component.empty())
        }

        // Add confiscation info
        newLore.add(Component.text("--- Confiscation Info ---", NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false))
        newLore.add(Component.text("Reason: ${confiscatedItem.reason}", NamedTextColor.RED)
            .decoration(TextDecoration.ITALIC, false))
        newLore.add(Component.text("Group: ${confiscatedItem.groupName}", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false))
        confiscatedItem.worldName?.let {
            newLore.add(Component.text("World: $it", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false))
        }
        newLore.add(Component.text("Date: ${dateFormatter.format(confiscatedItem.confiscatedAt)}", NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false))
        newLore.add(Component.empty())
        newLore.add(Component.text("Left-click to claim", NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false))
        if (isAdmin) {
            newLore.add(Component.text("Right-click to delete", NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false))
        }

        meta.lore(newLore)
        displayItem.itemMeta = meta

        return GUIItem(displayItem) { event ->
            val player = event.whoClicked as Player

            if (event.isRightClick && isAdmin) {
                // Delete the item
                runBlocking {
                    plugin.serviceManager.restrictionService.deleteConfiscatedItem(confiscatedItem.id)
                }
                player.sendMessage(Component.text("Deleted confiscated item", NamedTextColor.YELLOW))
                ConfiscationVaultGUI(plugin, targetUuid, targetName, isAdmin, page).open(player)
            } else {
                // Claim the item
                if (!isAdmin && player.uniqueId != targetUuid) {
                    player.sendMessage(Component.text("You can only claim your own items!", NamedTextColor.RED))
                    return@GUIItem
                }

                val targetPlayer = if (isAdmin) plugin.server.getPlayer(targetUuid) else player
                if (targetPlayer == null) {
                    player.sendMessage(Component.text("Target player is not online!", NamedTextColor.RED))
                    return@GUIItem
                }

                runBlocking {
                    val claimed = plugin.serviceManager.restrictionService.claimConfiscatedItem(targetPlayer, confiscatedItem.id)
                    if (claimed != null) {
                        player.sendMessage(Component.text("Claimed ${claimed.itemType} x${claimed.amount}", NamedTextColor.GREEN))
                    } else {
                        player.sendMessage(Component.text("Failed to claim item", NamedTextColor.RED))
                    }
                }
                ConfiscationVaultGUI(plugin, targetUuid, targetName, isAdmin, page).open(player)
            }
        }
    }

    override fun fillEmptySlots(inventory: Inventory) {
        val filler = GUIComponents.filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
        for (i in 0 until 36) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import java.util.*

/**
 * GUI for listing players with stored inventory data.
 */
class PlayerListGUI(
    plugin: XInventories,
    private val page: Int = 0,
    private val showOnlineOnly: Boolean = true
) : AbstractGUI(
    plugin,
    Component.text(if (showOnlineOnly) "Online Players" else "All Players", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 45
    private val players: List<PlayerEntry>

    init {
        players = loadPlayers()
        setupItems()
    }

    private fun loadPlayers(): List<PlayerEntry> {
        return if (showOnlineOnly) {
            Bukkit.getOnlinePlayers()
                .sortedBy { it.name }
                .map { PlayerEntry(it.uniqueId, it.name, true) }
        } else {
            // Load all players from storage
            val onlinePlayers = Bukkit.getOnlinePlayers().map { it.uniqueId }.toSet()
            val allUUIDs = runBlocking {
                plugin.serviceManager.storageService.getAllPlayerUUIDs()
            }

            allUUIDs.mapNotNull { uuid ->
                val online = uuid in onlinePlayers
                val name = Bukkit.getOfflinePlayer(uuid).name ?: return@mapNotNull null
                PlayerEntry(uuid, name, online)
            }.sortedWith(compareByDescending<PlayerEntry> { it.online }.thenBy { it.name })
        }
    }

    private fun setupItems() {
        // Fill bottom row
        for (i in 45..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (players.isEmpty()) 0 else (players.size - 1) / itemsPerPage

        // Back button - set explicitly with slot number
        val backButton = GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to main menu")
            .onClick { event ->
                val player = event.whoClicked as Player
                MainMenuGUI(plugin).open(player)
            }
            .build()
        items[45] = backButton // Ensure back button is in the items map

        // Toggle online/all
        setItem(46, GUIItemBuilder()
            .material(if (showOnlineOnly) Material.LIME_DYE else Material.GRAY_DYE)
            .name(if (showOnlineOnly) "Showing Online" else "Showing All", NamedTextColor.YELLOW)
            .lore("Click to toggle")
            .onClick { event ->
                val player = event.whoClicked as Player
                PlayerListGUI(plugin, 0, !showOnlineOnly).open(player)
            }
            .build()
        )

        // Previous page
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .lore("Go to page $page")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    PlayerListGUI(plugin, page - 1, showOnlineOnly).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("${players.size} total players")
            .build()
        )

        // Next page
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .lore("Go to page ${page + 2}")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    PlayerListGUI(plugin, page + 1, showOnlineOnly).open(player)
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

        // Populate players
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, players.size)

        for (i in startIndex until endIndex) {
            val playerEntry = players[i]
            val slot = i - startIndex

            setItem(slot, createPlayerItem(playerEntry))
        }
    }

    private fun createPlayerItem(entry: PlayerEntry): GUIItem {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as SkullMeta

        val offlinePlayer = Bukkit.getOfflinePlayer(entry.uuid)
        meta.owningPlayer = offlinePlayer

        val statusColor = if (entry.online) NamedTextColor.GREEN else NamedTextColor.GRAY
        val status = if (entry.online) "Online" else "Offline"

        meta.displayName(
            Component.text(entry.name)
                .color(statusColor)
                .decoration(TextDecoration.ITALIC, false)
        )

        meta.lore(listOf(
            Component.text("Status: $status")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false),
            Component.text("")
                .decoration(TextDecoration.ITALIC, false),
            Component.text("Click to view inventory data")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ))

        item.itemMeta = meta

        return GUIItem(item) { event ->
            val player = event.whoClicked as Player
            PlayerDetailGUI(plugin, entry.uuid, entry.name).open(player)
        }
    }

    private data class PlayerEntry(
        val uuid: UUID,
        val name: String,
        val online: Boolean
    )

    override fun fillEmptySlots(inventory: org.bukkit.inventory.Inventory) {
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

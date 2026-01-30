package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.util.Logging
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * GUI for searching and looking up players.
 * Provides search functionality, recent players, and online players sections.
 */
class PlayerLookupGUI(
    plugin: XInventories,
    private val searchQuery: String = "",
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Player Lookup", NamedTextColor.DARK_AQUA),
    54
) {

    companion object {
        // Track recent players viewed by each admin (last 10)
        private val recentPlayers = ConcurrentHashMap<UUID, MutableList<PlayerEntry>>()
        private const val MAX_RECENT_PLAYERS = 10

        /**
         * Records a player as recently viewed.
         */
        fun recordRecentPlayer(adminUuid: UUID, playerUuid: UUID, playerName: String) {
            val list = recentPlayers.getOrPut(adminUuid) { mutableListOf() }
            // Remove if already exists (to move to front)
            list.removeIf { it.uuid == playerUuid }
            // Add to front
            list.add(0, PlayerEntry(playerUuid, playerName, Bukkit.getPlayer(playerUuid) != null))
            // Trim to max size
            while (list.size > MAX_RECENT_PLAYERS) {
                list.removeAt(list.lastIndex)
            }
        }

        /**
         * Gets recent players for an admin.
         */
        fun getRecentPlayers(adminUuid: UUID): List<PlayerEntry> {
            return recentPlayers[adminUuid]?.toList() ?: emptyList()
        }
    }

    private val itemsPerPage = 21 // 3 rows x 7 columns for search results
    private var searchResults: List<PlayerEntry> = emptyList()

    init {
        if (searchQuery.isNotEmpty()) {
            searchResults = searchPlayers(searchQuery)
        }
        setupItems()
    }

    private fun searchPlayers(query: String): List<PlayerEntry> {
        val lowerQuery = query.lowercase()
        val results = mutableListOf<PlayerEntry>()

        // Search online players
        for (player in Bukkit.getOnlinePlayers()) {
            if (player.name.lowercase().startsWith(lowerQuery)) {
                results.add(PlayerEntry(player.uniqueId, player.name, true))
            }
        }

        // Search offline players with stored data
        val storedUUIDs = runBlocking {
            plugin.serviceManager.storageService.getAllPlayerUUIDs()
        }

        for (uuid in storedUUIDs) {
            // Skip if already added (online)
            if (results.any { it.uuid == uuid }) continue

            val offlinePlayer = Bukkit.getOfflinePlayer(uuid)
            val name = offlinePlayer.name ?: continue

            if (name.lowercase().startsWith(lowerQuery)) {
                results.add(PlayerEntry(uuid, name, false))
            }
        }

        return results.sortedWith(compareByDescending<PlayerEntry> { it.online }.thenBy { it.name })
    }

    private fun setupItems() {
        // Fill border
        fillBorder(GUIComponents.filler())

        // Search button (opens anvil GUI for input)
        setItem(4, GUIItemBuilder()
            .material(Material.NAME_TAG)
            .name("Search Players", NamedTextColor.GREEN)
            .lore(if (searchQuery.isEmpty()) "Click to search by name" else "Current search: $searchQuery")
            .lore("")
            .lore("Enter a player name prefix")
            .onClick { event ->
                val player = event.whoClicked as Player
                openSearchAnvil(player)
            }
            .build()
        )

        // Clear search button (if searching)
        if (searchQuery.isNotEmpty()) {
            setItem(5, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("Clear Search", NamedTextColor.RED)
                .lore("Click to clear search")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    PlayerLookupGUI(plugin, "", 0).open(player)
                }
                .build()
            )
        }

        // Populate content based on state
        if (searchQuery.isEmpty()) {
            setupRecentAndOnlinePlayers()
        } else {
            setupSearchResults()
        }

        // Navigation row
        setupNavigationRow()
    }

    private fun setupRecentAndOnlinePlayers() {
        // Get the admin viewing this GUI
        // Note: We'll use the first online player or stored recent list
        // In practice, the admin UUID is passed when opened

        // Left side: Recent Players (slots 10-12, 19-21, 28-30)
        setItem(1, 1, GUIItemBuilder()
            .material(Material.CLOCK)
            .name("Recent Players", NamedTextColor.GOLD)
            .lore("Recently viewed players")
            .build()
        )

        // Right side: Online Players (slots 14-16, 23-25, 32-34)
        setItem(1, 7, GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name("Online Players", NamedTextColor.GREEN)
            .lore("Currently online players")
            .build()
        )

        // Note: Recent players will be populated when we know the admin UUID
        // For now, show a placeholder

        // Online players section
        val onlinePlayers = Bukkit.getOnlinePlayers()
            .sortedBy { it.name }
            .take(9)

        val onlineSlots = listOf(14, 15, 16, 23, 24, 25, 32, 33, 34)
        for ((index, player) in onlinePlayers.withIndex()) {
            if (index >= onlineSlots.size) break
            setItem(onlineSlots[index], createPlayerItem(
                PlayerEntry(player.uniqueId, player.name, true)
            ))
        }
    }

    /**
     * Sets up the GUI with recent players for a specific admin.
     */
    fun setupForAdmin(adminUuid: UUID) {
        val recent = getRecentPlayers(adminUuid).take(9)
        val recentSlots = listOf(10, 11, 12, 19, 20, 21, 28, 29, 30)

        for ((index, entry) in recent.withIndex()) {
            if (index >= recentSlots.size) break
            // Update online status
            val isOnline = Bukkit.getPlayer(entry.uuid) != null
            val updatedEntry = entry.copy(online = isOnline)
            setItem(recentSlots[index], createPlayerItem(updatedEntry))
        }
    }

    private fun setupSearchResults() {
        // Search results header
        setItem(1, 4, GUIItemBuilder()
            .material(Material.WRITABLE_BOOK)
            .name("Search Results", NamedTextColor.AQUA)
            .lore("Found ${searchResults.size} players matching '$searchQuery'")
            .build()
        )

        // Calculate pagination
        val maxPage = if (searchResults.isEmpty()) 0 else (searchResults.size - 1) / itemsPerPage
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, searchResults.size)

        // Display results in a 3x7 grid (rows 2-4, columns 1-7)
        val resultSlots = mutableListOf<Int>()
        for (row in 2..4) {
            for (col in 1..7) {
                resultSlots.add(row * 9 + col)
            }
        }

        for (i in startIndex until endIndex) {
            val slotIndex = i - startIndex
            if (slotIndex >= resultSlots.size) break

            setItem(resultSlots[slotIndex], createPlayerItem(searchResults[i]))
        }

        // Page navigation
        if (page > 0) {
            setItem(48, GUIComponents.previousPageButton(page + 1) {
                // Note: This lambda won't work directly - need to handle in click
            })
            items[48] = GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .lore("Go to page $page")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    PlayerLookupGUI(plugin, searchQuery, page - 1).open(player)
                }
                .build()
        }

        if (page < maxPage) {
            items[50] = GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .lore("Go to page ${page + 2}")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    PlayerLookupGUI(plugin, searchQuery, page + 1).open(player)
                }
                .build()
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.WHITE)
            .lore("${searchResults.size} results")
            .build()
        )
    }

    private fun setupNavigationRow() {
        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to main menu")
            .onClick { event ->
                val player = event.whoClicked as Player
                MainMenuGUI(plugin).open(player)
            }
            .build()
        )

        // Player list button
        setItem(46, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Browse All Players", NamedTextColor.AQUA)
            .lore("View complete player list")
            .onClick { event ->
                val player = event.whoClicked as Player
                PlayerListGUI(plugin).open(player)
            }
            .build()
        )

        // Close button
        setItem(53, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )
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
            Component.text("Click to view details")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ))

        item.itemMeta = meta

        return GUIItem(item) { event ->
            val player = event.whoClicked as Player
            // Record this as a recently viewed player
            recordRecentPlayer(player.uniqueId, entry.uuid, entry.name)
            // Open player detail GUI
            PlayerDetailGUI(plugin, entry.uuid, entry.name).open(player)
        }
    }

    private fun openSearchAnvil(player: Player) {
        // Close current GUI and prompt for input
        player.closeInventory()

        // Send message prompting for search
        player.sendMessage(Component.text("Enter a player name to search (in chat):", NamedTextColor.GREEN))
        player.sendMessage(Component.text("Type 'cancel' to abort.", NamedTextColor.GRAY))

        // Register a temporary chat listener for this player
        // Note: In a real implementation, you'd use a proper chat listener system
        // For now, we'll use a simpler approach with a conversation API or anvil

        // Store pending search state
        pendingSearches[player.uniqueId] = System.currentTimeMillis()

        // The actual search input would be handled by a chat listener
        // For simplicity, we'll provide instructions and let them use the command
        player.sendMessage(Component.text("Or use: /xinv gui lookup <name>", NamedTextColor.YELLOW))
    }

    override fun fillEmptySlots(inventory: Inventory) {
        val filler = GUIComponents.filler()
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }

    /**
     * Represents a player entry for display.
     */
    data class PlayerEntry(
        val uuid: UUID,
        val name: String,
        val online: Boolean
    )
}

/**
 * Tracks pending search requests.
 */
private val pendingSearches = ConcurrentHashMap<UUID, Long>()

/**
 * Checks if a player has a pending search.
 */
fun hasPendingSearch(playerUuid: UUID): Boolean {
    val timestamp = pendingSearches[playerUuid] ?: return false
    // Expire after 30 seconds
    if (System.currentTimeMillis() - timestamp > 30000) {
        pendingSearches.remove(playerUuid)
        return false
    }
    return true
}

/**
 * Clears a pending search for a player.
 */
fun clearPendingSearch(playerUuid: UUID) {
    pendingSearches.remove(playerUuid)
}

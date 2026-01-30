package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.SkullMeta
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.Group
import java.io.File
import java.util.UUID

/**
 * GUI for searching player inventories.
 *
 * Features:
 * - Search by item type (material selector)
 * - Search by item name (anvil input)
 * - Search by enchantment (enchantment selector)
 * - Search scope: all players, specific group, online only
 * - Results list showing player name, item location, quantity
 * - Click result to view player's inventory
 * - Export results to file option
 * - Pagination for results
 */
class InventorySearchGUI(
    plugin: PluginContext
) : AbstractGUI(
    plugin,
    Component.text("Inventory Search", NamedTextColor.DARK_AQUA),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler())

        // Search by material
        setItem(1, 2, GUIItemBuilder()
            .material(Material.DIAMOND)
            .name("Search by Material", NamedTextColor.AQUA)
            .lore("Find items of a specific type")
            .lore("across player inventories")
            .lore("")
            .lore("Click to select material")
            .onClick { event ->
                val player = event.whoClicked as Player
                MaterialSelectorGUI(plugin).open(player)
            }
            .build()
        )

        // Search by enchantment
        setItem(1, 4, GUIItemBuilder()
            .material(Material.ENCHANTED_BOOK)
            .name("Search by Enchantment", NamedTextColor.LIGHT_PURPLE)
            .lore("Find items with a specific")
            .lore("enchantment")
            .lore("")
            .lore("Click to select enchantment")
            .onClick { event ->
                val player = event.whoClicked as Player
                EnchantmentSelectorGUI(plugin).open(player)
            }
            .build()
        )

        // Search by name
        setItem(1, 6, GUIItemBuilder()
            .material(Material.NAME_TAG)
            .name("Search by Name", NamedTextColor.YELLOW)
            .lore("Find items containing a")
            .lore("specific text in their name")
            .lore("")
            .lore("Use command:")
            .lore("/xinv gui search name <text>")
            .build()
        )

        // Back button
        setItem(2, 0, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to main menu")
            .onClick { event ->
                val player = event.whoClicked as Player
                MainMenuGUI(plugin).open(player)
            }
            .build()
        )

        // Close button
        setItem(2, 8, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )
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
}

/**
 * GUI for selecting a material to search for.
 */
class MaterialSelectorGUI(
    plugin: PluginContext,
    private val page: Int = 0,
    private val category: MaterialCategory = MaterialCategory.ALL
) : AbstractGUI(
    plugin,
    Component.text("Select Material", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 36
    private val materials: List<Material>

    init {
        materials = getMaterialsForCategory(category)
        setupItems()
    }

    private fun getMaterialsForCategory(category: MaterialCategory): List<Material> {
        return when (category) {
            MaterialCategory.ALL -> Material.entries.filter { !it.isAir && it.isItem }
            MaterialCategory.WEAPONS -> Material.entries.filter {
                it.name.contains("SWORD") || it.name.contains("BOW") ||
                        it.name.contains("CROSSBOW") || it.name.contains("TRIDENT") ||
                        it.name.contains("AXE")
            }
            MaterialCategory.ARMOR -> Material.entries.filter {
                it.name.contains("HELMET") || it.name.contains("CHESTPLATE") ||
                        it.name.contains("LEGGINGS") || it.name.contains("BOOTS") ||
                        it.name.contains("SHIELD") || it.name.contains("ELYTRA")
            }
            MaterialCategory.TOOLS -> Material.entries.filter {
                it.name.contains("PICKAXE") || it.name.contains("SHOVEL") ||
                        it.name.contains("HOE") || it.name.contains("SHEARS") ||
                        it.name.contains("FLINT_AND_STEEL") || it.name.contains("FISHING_ROD")
            }
            MaterialCategory.BLOCKS -> Material.entries.filter { it.isBlock && it.isItem }
            MaterialCategory.FOOD -> Material.entries.filter { it.isEdible }
            MaterialCategory.VALUABLE -> listOf(
                Material.DIAMOND, Material.DIAMOND_BLOCK, Material.EMERALD, Material.EMERALD_BLOCK,
                Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.IRON_INGOT, Material.IRON_BLOCK,
                Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, Material.ANCIENT_DEBRIS,
                Material.NETHER_STAR, Material.BEACON, Material.TOTEM_OF_UNDYING,
                Material.ENCHANTED_GOLDEN_APPLE, Material.ELYTRA, Material.TRIDENT
            ).filter { Material.entries.contains(it) }
        }.sortedBy { it.name }
    }

    private fun setupItems() {
        // Fill bottom two rows with filler
        for (i in 36..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (materials.isEmpty()) 0 else (materials.size - 1) / itemsPerPage

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to search menu")
            .onClick { event ->
                val player = event.whoClicked as Player
                InventorySearchGUI(plugin).open(player)
            }
            .build()
        )

        // Category buttons
        setItem(46, createCategoryButton(MaterialCategory.VALUABLE, Material.DIAMOND, "Valuable"))
        setItem(47, createCategoryButton(MaterialCategory.WEAPONS, Material.DIAMOND_SWORD, "Weapons"))

        // Previous page button
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    MaterialSelectorGUI(plugin, page - 1, category).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("Category: ${category.name}")
            .lore("${materials.size} materials")
            .build()
        )

        // Next page button
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    MaterialSelectorGUI(plugin, page + 1, category).open(player)
                }
                .build()
            )
        }

        // More category buttons
        setItem(51, createCategoryButton(MaterialCategory.ARMOR, Material.DIAMOND_CHESTPLATE, "Armor"))
        setItem(52, createCategoryButton(MaterialCategory.TOOLS, Material.DIAMOND_PICKAXE, "Tools"))

        // Close button
        setItem(53, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )

        // Populate materials
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, materials.size)

        for (i in startIndex until endIndex) {
            val material = materials[i]
            val slot = i - startIndex

            setItem(slot, GUIItemBuilder()
                .material(material)
                .name(formatMaterialName(material), NamedTextColor.WHITE)
                .lore("Click to search for this item")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    SearchScopeSelectGUI(plugin, SearchType.MATERIAL, material = material).open(player)
                }
                .build()
            )
        }
    }

    private fun createCategoryButton(cat: MaterialCategory, material: Material, name: String): GUIItem {
        val selected = cat == category
        return GUIItemBuilder()
            .material(material)
            .name(name, if (selected) NamedTextColor.GREEN else NamedTextColor.GRAY)
            .lore(if (selected) "Selected" else "Click to filter")
            .onClick { event ->
                val player = event.whoClicked as Player
                MaterialSelectorGUI(plugin, 0, cat).open(player)
            }
            .build()
    }

    private fun formatMaterialName(material: Material): String {
        return material.name.lowercase().replace('_', ' ')
            .split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
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
}

enum class MaterialCategory {
    ALL, WEAPONS, ARMOR, TOOLS, BLOCKS, FOOD, VALUABLE
}

/**
 * GUI for selecting an enchantment to search for.
 */
class EnchantmentSelectorGUI(
    plugin: PluginContext,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Select Enchantment", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 45
    private val enchantments: List<Enchantment>

    init {
        enchantments = Enchantment.values().toList().sortedBy { it.key.key }
        setupItems()
    }

    private fun setupItems() {
        // Fill bottom row with filler
        for (i in 45..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (enchantments.isEmpty()) 0 else (enchantments.size - 1) / itemsPerPage

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to search menu")
            .onClick { event ->
                val player = event.whoClicked as Player
                InventorySearchGUI(plugin).open(player)
            }
            .build()
        )

        // Previous page button
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    EnchantmentSelectorGUI(plugin, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("${enchantments.size} enchantments")
            .build()
        )

        // Next page button
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    EnchantmentSelectorGUI(plugin, page + 1).open(player)
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

        // Populate enchantments
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, enchantments.size)

        for (i in startIndex until endIndex) {
            val enchantment = enchantments[i]
            val slot = i - startIndex

            setItem(slot, GUIItemBuilder()
                .material(Material.ENCHANTED_BOOK)
                .name(formatEnchantmentName(enchantment), NamedTextColor.LIGHT_PURPLE)
                .lore("Max Level: ${enchantment.maxLevel}")
                .lore("")
                .lore("Click to search")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    SearchScopeSelectGUI(plugin, SearchType.ENCHANTMENT, enchantment = enchantment).open(player)
                }
                .build()
            )
        }
    }

    private fun formatEnchantmentName(enchantment: Enchantment): String {
        return enchantment.key.key.replace('_', ' ')
            .split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
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
}

enum class SearchType {
    MATERIAL, ENCHANTMENT, NAME
}

/**
 * GUI for selecting search scope.
 */
class SearchScopeSelectGUI(
    plugin: PluginContext,
    private val searchType: SearchType,
    private val material: Material? = null,
    private val enchantment: Enchantment? = null,
    private val nameQuery: String? = null
) : AbstractGUI(
    plugin,
    Component.text("Select Search Scope", NamedTextColor.DARK_AQUA),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler())

        // Search info
        val searchDesc = when (searchType) {
            SearchType.MATERIAL -> "Material: ${material?.name?.lowercase()?.replace('_', ' ')}"
            SearchType.ENCHANTMENT -> "Enchantment: ${enchantment?.key?.key?.replace('_', ' ')}"
            SearchType.NAME -> "Name contains: $nameQuery"
        }

        setItem(0, 4, GUIItemBuilder()
            .material(Material.COMPASS)
            .name("Search", NamedTextColor.GOLD)
            .lore(searchDesc)
            .build()
        )

        // All players
        setItem(1, 2, GUIItemBuilder()
            .material(Material.ENDER_CHEST)
            .name("All Players", NamedTextColor.AQUA)
            .lore("Search across all players")
            .lore("with stored inventory data")
            .onClick { event ->
                val player = event.whoClicked as Player
                executeSearch(player, SearchScope.ALL)
            }
            .build()
        )

        // Online only
        setItem(1, 4, GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name("Online Players Only", NamedTextColor.GREEN)
            .lore("Search only online players")
            .onClick { event ->
                val player = event.whoClicked as Player
                executeSearch(player, SearchScope.ONLINE)
            }
            .build()
        )

        // Specific group
        setItem(1, 6, GUIItemBuilder()
            .material(Material.CHEST)
            .name("Specific Group", NamedTextColor.YELLOW)
            .lore("Search players in a")
            .lore("specific group")
            .onClick { event ->
                val player = event.whoClicked as Player
                SearchGroupSelectGUI(plugin, searchType, material, enchantment, nameQuery).open(player)
            }
            .build()
        )

        // Back button
        setItem(2, 0, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .onClick { event ->
                val player = event.whoClicked as Player
                when (searchType) {
                    SearchType.MATERIAL -> MaterialSelectorGUI(plugin).open(player)
                    SearchType.ENCHANTMENT -> EnchantmentSelectorGUI(plugin).open(player)
                    SearchType.NAME -> InventorySearchGUI(plugin).open(player)
                }
            }
            .build()
        )

        // Close button
        setItem(2, 8, GUIItemBuilder()
            .material(Material.BARRIER)
            .name("Close", NamedTextColor.RED)
            .onClick { event ->
                event.whoClicked.closeInventory()
            }
            .build()
        )
    }

    private fun executeSearch(player: Player, scope: SearchScope) {
        player.sendMessage(Component.text("Searching inventories...", NamedTextColor.YELLOW))

        val results = runBlocking {
            performSearch(scope, null)
        }

        if (results.isEmpty()) {
            player.sendMessage(Component.text("No results found.", NamedTextColor.RED))
            return
        }

        SearchResultsGUI(plugin, results, searchType, material, enchantment, nameQuery).open(player)
    }

    private suspend fun performSearch(scope: SearchScope, groupName: String?): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val storageService = plugin.serviceManager.storageService

        val uuidsToSearch: List<UUID> = when (scope) {
            SearchScope.ALL -> storageService.getAllPlayerUUIDs().toList()
            SearchScope.ONLINE -> Bukkit.getOnlinePlayers().map { it.uniqueId }
            SearchScope.GROUP -> {
                if (groupName == null) return emptyList()
                storageService.getAllPlayerUUIDs().filter { uuid ->
                    groupName in storageService.getPlayerGroups(uuid)
                }.toList()
            }
        }

        for (uuid in uuidsToSearch) {
            val playerName = Bukkit.getOfflinePlayer(uuid).name ?: continue
            val allData = storageService.loadAllPlayerData(uuid)

            for ((key, data) in allData) {
                // Search main inventory
                for ((slot, item) in data.mainInventory) {
                    if (matchesSearch(item)) {
                        results.add(SearchResult(
                            playerUuid = uuid,
                            playerName = playerName,
                            group = data.group,
                            location = "Inventory slot $slot",
                            item = item.clone(),
                            quantity = item.amount
                        ))
                    }
                }

                // Search armor
                for ((slot, item) in data.armorInventory) {
                    if (matchesSearch(item)) {
                        val armorName = when (slot) {
                            0 -> "Boots"
                            1 -> "Leggings"
                            2 -> "Chestplate"
                            3 -> "Helmet"
                            else -> "Armor"
                        }
                        results.add(SearchResult(
                            playerUuid = uuid,
                            playerName = playerName,
                            group = data.group,
                            location = armorName,
                            item = item.clone(),
                            quantity = item.amount
                        ))
                    }
                }

                // Search offhand
                data.offhand?.let { item ->
                    if (matchesSearch(item)) {
                        results.add(SearchResult(
                            playerUuid = uuid,
                            playerName = playerName,
                            group = data.group,
                            location = "Offhand",
                            item = item.clone(),
                            quantity = item.amount
                        ))
                    }
                }

                // Search ender chest
                for ((slot, item) in data.enderChest) {
                    if (matchesSearch(item)) {
                        results.add(SearchResult(
                            playerUuid = uuid,
                            playerName = playerName,
                            group = data.group,
                            location = "Ender chest slot $slot",
                            item = item.clone(),
                            quantity = item.amount
                        ))
                    }
                }
            }
        }

        return results
    }

    private fun matchesSearch(item: ItemStack): Boolean {
        return when (searchType) {
            SearchType.MATERIAL -> item.type == material
            SearchType.ENCHANTMENT -> {
                val meta = item.itemMeta
                meta?.hasEnchant(enchantment!!) == true ||
                        (meta is org.bukkit.inventory.meta.EnchantmentStorageMeta &&
                                meta.hasStoredEnchant(enchantment!!))
            }
            SearchType.NAME -> {
                val meta = item.itemMeta
                meta?.displayName()?.toString()?.contains(nameQuery!!, ignoreCase = true) == true
            }
        }
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
}

enum class SearchScope {
    ALL, ONLINE, GROUP
}

data class SearchResult(
    val playerUuid: UUID,
    val playerName: String,
    val group: String,
    val location: String,
    val item: ItemStack,
    val quantity: Int
)

/**
 * GUI for selecting a group to search in.
 */
class SearchGroupSelectGUI(
    plugin: PluginContext,
    private val searchType: SearchType,
    private val material: Material?,
    private val enchantment: Enchantment?,
    private val nameQuery: String?,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Select Group", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 45
    private val groups = plugin.serviceManager.groupService.getAllGroups()
        .sortedBy { it.name.lowercase() }

    init {
        setupItems()
    }

    private fun setupItems() {
        // Fill bottom row with filler
        for (i in 45..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (groups.isEmpty()) 0 else (groups.size - 1) / itemsPerPage

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .onClick { event ->
                val player = event.whoClicked as Player
                SearchScopeSelectGUI(plugin, searchType, material, enchantment, nameQuery).open(player)
            }
            .build()
        )

        // Previous page button
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    SearchGroupSelectGUI(plugin, searchType, material, enchantment, nameQuery, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("${groups.size} groups")
            .build()
        )

        // Next page button
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    SearchGroupSelectGUI(plugin, searchType, material, enchantment, nameQuery, page + 1).open(player)
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

        // Populate groups
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, groups.size)

        for (i in startIndex until endIndex) {
            val group = groups[i]
            val slot = i - startIndex

            setItem(slot, GUIItemBuilder()
                .material(if (group.isDefault) Material.ENDER_CHEST else Material.CHEST)
                .name(group.name, if (group.isDefault) NamedTextColor.LIGHT_PURPLE else NamedTextColor.AQUA)
                .lore("Click to search in this group")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    executeGroupSearch(player, group.name)
                }
                .build()
            )
        }
    }

    private fun executeGroupSearch(player: Player, groupName: String) {
        player.sendMessage(Component.text("Searching inventories in '$groupName'...", NamedTextColor.YELLOW))

        val results = runBlocking {
            performSearch(groupName)
        }

        if (results.isEmpty()) {
            player.sendMessage(Component.text("No results found.", NamedTextColor.RED))
            return
        }

        SearchResultsGUI(plugin, results, searchType, material, enchantment, nameQuery).open(player)
    }

    private suspend fun performSearch(groupName: String): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        val storageService = plugin.serviceManager.storageService

        val uuidsToSearch = storageService.getAllPlayerUUIDs().filter { uuid ->
            groupName in storageService.getPlayerGroups(uuid)
        }.toList()

        for (uuid in uuidsToSearch) {
            val playerName = Bukkit.getOfflinePlayer(uuid).name ?: continue
            val allData = storageService.loadAllPlayerData(uuid)

            for ((key, data) in allData) {
                if (data.group != groupName) continue

                // Search main inventory
                for ((slot, item) in data.mainInventory) {
                    if (matchesSearch(item)) {
                        results.add(SearchResult(
                            playerUuid = uuid,
                            playerName = playerName,
                            group = data.group,
                            location = "Inventory slot $slot",
                            item = item.clone(),
                            quantity = item.amount
                        ))
                    }
                }

                // Search armor
                for ((slot, item) in data.armorInventory) {
                    if (matchesSearch(item)) {
                        val armorName = when (slot) {
                            0 -> "Boots"
                            1 -> "Leggings"
                            2 -> "Chestplate"
                            3 -> "Helmet"
                            else -> "Armor"
                        }
                        results.add(SearchResult(
                            playerUuid = uuid,
                            playerName = playerName,
                            group = data.group,
                            location = armorName,
                            item = item.clone(),
                            quantity = item.amount
                        ))
                    }
                }

                // Search offhand
                data.offhand?.let { item ->
                    if (matchesSearch(item)) {
                        results.add(SearchResult(
                            playerUuid = uuid,
                            playerName = playerName,
                            group = data.group,
                            location = "Offhand",
                            item = item.clone(),
                            quantity = item.amount
                        ))
                    }
                }

                // Search ender chest
                for ((slot, item) in data.enderChest) {
                    if (matchesSearch(item)) {
                        results.add(SearchResult(
                            playerUuid = uuid,
                            playerName = playerName,
                            group = data.group,
                            location = "Ender chest slot $slot",
                            item = item.clone(),
                            quantity = item.amount
                        ))
                    }
                }
            }
        }

        return results
    }

    private fun matchesSearch(item: ItemStack): Boolean {
        return when (searchType) {
            SearchType.MATERIAL -> item.type == material
            SearchType.ENCHANTMENT -> {
                val meta = item.itemMeta
                meta?.hasEnchant(enchantment!!) == true ||
                        (meta is org.bukkit.inventory.meta.EnchantmentStorageMeta &&
                                meta.hasStoredEnchant(enchantment!!))
            }
            SearchType.NAME -> {
                val meta = item.itemMeta
                meta?.displayName()?.toString()?.contains(nameQuery!!, ignoreCase = true) == true
            }
        }
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
}

/**
 * GUI for displaying search results.
 */
class SearchResultsGUI(
    plugin: PluginContext,
    private val results: List<SearchResult>,
    private val searchType: SearchType,
    private val material: Material?,
    private val enchantment: Enchantment?,
    private val nameQuery: String?,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Search Results (${results.size})", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 45

    init {
        setupItems()
    }

    private fun setupItems() {
        // Fill bottom row with filler
        for (i in 45..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (results.isEmpty()) 0 else (results.size - 1) / itemsPerPage

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to search")
            .onClick { event ->
                val player = event.whoClicked as Player
                InventorySearchGUI(plugin).open(player)
            }
            .build()
        )

        // Export results button
        setItem(46, GUIItemBuilder()
            .material(Material.WRITABLE_BOOK)
            .name("Export Results", NamedTextColor.AQUA)
            .lore("Export search results to file")
            .onClick { event ->
                val player = event.whoClicked as Player
                exportResults(player)
            }
            .build()
        )

        // Total count
        val totalQuantity = results.sumOf { it.quantity }
        setItem(47, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Statistics", NamedTextColor.GOLD)
            .lore("Results: ${results.size}")
            .lore("Total items: $totalQuantity")
            .lore("Unique players: ${results.map { it.playerUuid }.distinct().size}")
            .build()
        )

        // Previous page button
        if (page > 0) {
            setItem(48, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Previous Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    SearchResultsGUI(plugin, results, searchType, material, enchantment, nameQuery, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("${results.size} total results")
            .build()
        )

        // Next page button
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    SearchResultsGUI(plugin, results, searchType, material, enchantment, nameQuery, page + 1).open(player)
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

        // Populate results
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, results.size)

        for (i in startIndex until endIndex) {
            val result = results[i]
            val slot = i - startIndex

            setItem(slot, createResultItem(result))
        }
    }

    private fun createResultItem(result: SearchResult): GUIItem {
        val item = result.item.clone()
        val meta = item.itemMeta

        val lore = meta?.lore()?.toMutableList() ?: mutableListOf()
        lore.add(Component.empty())
        lore.add(Component.text("------- Search Result -------")
            .color(NamedTextColor.DARK_GRAY)
            .decoration(TextDecoration.ITALIC, false))
        lore.add(Component.text("Player: ${result.playerName}")
            .color(NamedTextColor.YELLOW)
            .decoration(TextDecoration.ITALIC, false))
        lore.add(Component.text("Group: ${result.group}")
            .color(NamedTextColor.AQUA)
            .decoration(TextDecoration.ITALIC, false))
        lore.add(Component.text("Location: ${result.location}")
            .color(NamedTextColor.GRAY)
            .decoration(TextDecoration.ITALIC, false))
        lore.add(Component.text("Quantity: ${result.quantity}")
            .color(NamedTextColor.WHITE)
            .decoration(TextDecoration.ITALIC, false))
        lore.add(Component.empty())
        lore.add(Component.text("Click to view player inventory")
            .color(NamedTextColor.GREEN)
            .decoration(TextDecoration.ITALIC, false))

        meta?.lore(lore)
        item.itemMeta = meta

        return GUIItem(item) { event ->
            val player = event.whoClicked as Player
            InventoryViewGUI(plugin, result.playerUuid, result.playerName, result.group).open(player)
        }
    }

    private fun exportResults(player: Player) {
        val exportFile = File(plugin.plugin.dataFolder, "exports/search-${System.currentTimeMillis()}.csv")
        exportFile.parentFile?.mkdirs()

        val csv = StringBuilder()
        csv.appendLine("Player,UUID,Group,Location,Item,Quantity")

        for (result in results) {
            csv.appendLine("${result.playerName},${result.playerUuid},${result.group},${result.location},${result.item.type.name},${result.quantity}")
        }

        exportFile.writeText(csv.toString())
        player.sendMessage(Component.text("Results exported to ${exportFile.name}", NamedTextColor.GREEN))
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
}

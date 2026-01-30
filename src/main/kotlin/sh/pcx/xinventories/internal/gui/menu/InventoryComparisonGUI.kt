package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.PlayerData
import java.util.UUID

/**
 * View mode for inventory comparison.
 */
enum class ComparisonViewMode {
    MAIN_INVENTORY,
    ARMOR,
    ENDER_CHEST
}

/**
 * GUI for comparing two players' inventories side-by-side.
 * Shows items from player 1 on the left and player 2 on the right,
 * highlighting differences between them.
 */
class InventoryComparisonGUI(
    plugin: PluginContext,
    private val player1Uuid: UUID,
    private val player1Name: String,
    private val player2Uuid: UUID,
    private val player2Name: String,
    private val groupName: String,
    private val gameMode: GameMode = GameMode.SURVIVAL,
    private val viewMode: ComparisonViewMode = ComparisonViewMode.MAIN_INVENTORY
) : AbstractGUI(
    plugin,
    Component.text("Compare: $player1Name vs $player2Name", NamedTextColor.DARK_AQUA),
    54
) {

    private var player1Data: PlayerData? = null
    private var player2Data: PlayerData? = null

    init {
        loadData()
        setupItems()
    }

    private fun loadData() {
        runBlocking {
            player1Data = plugin.serviceManager.storageService.load(player1Uuid, groupName, gameMode)
            player2Data = plugin.serviceManager.storageService.load(player2Uuid, groupName, gameMode)
        }
    }

    private fun setupItems() {
        // Fill background
        for (i in 0 until size) {
            setItem(i, GUIComponents.filler(Material.BLACK_STAINED_GLASS_PANE))
        }

        // Divider column (column 4)
        for (row in 0 until 5) {
            setItem(row, 4, GUIComponents.filler(Material.WHITE_STAINED_GLASS_PANE))
        }

        // Player info headers
        setItem(0, 1, createPlayerHeader(player1Name, player1Data != null))
        setItem(0, 7, createPlayerHeader(player2Name, player2Data != null))

        when (viewMode) {
            ComparisonViewMode.MAIN_INVENTORY -> setupMainInventoryComparison()
            ComparisonViewMode.ARMOR -> setupArmorComparison()
            ComparisonViewMode.ENDER_CHEST -> setupEnderChestComparison()
        }

        // Navigation buttons (bottom row)
        setupNavigationButtons()
    }

    private fun setupMainInventoryComparison() {
        val data1 = player1Data
        val data2 = player2Data

        // Compare main inventory (first 36 slots in two columns)
        // Left side: Player 1 (columns 0-3)
        // Right side: Player 2 (columns 5-8)

        for (i in 0..35) {
            val row = (i / 9) + 1 // Rows 1-4
            val col = i % 9

            if (col < 4) {
                // Player 1 side (columns 0-3)
                val item1 = data1?.mainInventory?.get(i)
                val item2 = data2?.mainInventory?.get(i)
                val guiRow = row
                val guiCol = col

                setItem(guiRow, guiCol, createComparisonItem(item1, item2, i, true))
            }
        }

        // Player 2 inventory display
        for (i in 0..35) {
            val row = (i / 9) + 1
            val col = i % 9

            if (col < 4) {
                val item1 = player1Data?.mainInventory?.get(i)
                val item2 = player2Data?.mainInventory?.get(i)
                val guiRow = row
                val guiCol = col + 5 // Offset to right side

                setItem(guiRow, guiCol, createComparisonItem(item2, item1, i, false))
            }
        }
    }

    private fun setupArmorComparison() {
        val data1 = player1Data
        val data2 = player2Data

        // Armor slots: 0=boots, 1=leggings, 2=chestplate, 3=helmet
        val armorNames = listOf("Boots", "Leggings", "Chestplate", "Helmet")

        for (i in 0..3) {
            val row = i + 1

            // Player 1 armor (column 1)
            val armor1 = data1?.armorInventory?.get(i)
            val armor2 = data2?.armorInventory?.get(i)

            setItem(row, 1, createArmorComparisonItem(armor1, armor2, armorNames[i], true))

            // Player 2 armor (column 7)
            setItem(row, 7, createArmorComparisonItem(armor2, armor1, armorNames[i], false))
        }

        // Offhand (row 5)
        val offhand1 = data1?.offhand
        val offhand2 = data2?.offhand

        setItem(5, 1, createOffhandComparisonItem(offhand1, offhand2, true))
        setItem(5, 7, createOffhandComparisonItem(offhand2, offhand1, false))
    }

    private fun setupEnderChestComparison() {
        val data1 = player1Data
        val data2 = player2Data

        // Ender chest has 27 slots (3 rows of 9)
        for (i in 0..26) {
            val row = (i / 9) + 1
            val col = i % 9

            if (col < 4) {
                // Player 1 (left side)
                val item1 = data1?.enderChest?.get(i)
                val item2 = data2?.enderChest?.get(i)
                setItem(row, col, createComparisonItem(item1, item2, i, true))
            }
        }

        for (i in 0..26) {
            val row = (i / 9) + 1
            val col = i % 9

            if (col < 4) {
                // Player 2 (right side)
                val item1 = player1Data?.enderChest?.get(i)
                val item2 = player2Data?.enderChest?.get(i)
                setItem(row, col + 5, createComparisonItem(item2, item1, i, false))
            }
        }
    }

    private fun setupNavigationButtons() {
        // Main Inventory button
        setItem(5, 0, GUIItemBuilder()
            .material(if (viewMode == ComparisonViewMode.MAIN_INVENTORY) Material.CHEST else Material.TRAPPED_CHEST)
            .name(Component.text("Main Inventory", if (viewMode == ComparisonViewMode.MAIN_INVENTORY) NamedTextColor.GREEN else NamedTextColor.GRAY))
            .lore("Click to view main inventory comparison")
            .onClick { event ->
                if (viewMode != ComparisonViewMode.MAIN_INVENTORY) {
                    val player = event.whoClicked as Player
                    InventoryComparisonGUI(plugin, player1Uuid, player1Name, player2Uuid, player2Name, groupName, gameMode, ComparisonViewMode.MAIN_INVENTORY).open(player)
                }
            }
            .build()
        )

        // Armor button
        setItem(5, 1, GUIItemBuilder()
            .material(if (viewMode == ComparisonViewMode.ARMOR) Material.DIAMOND_CHESTPLATE else Material.LEATHER_CHESTPLATE)
            .name(Component.text("Armor & Offhand", if (viewMode == ComparisonViewMode.ARMOR) NamedTextColor.GREEN else NamedTextColor.GRAY))
            .lore("Click to view armor comparison")
            .onClick { event ->
                if (viewMode != ComparisonViewMode.ARMOR) {
                    val player = event.whoClicked as Player
                    InventoryComparisonGUI(plugin, player1Uuid, player1Name, player2Uuid, player2Name, groupName, gameMode, ComparisonViewMode.ARMOR).open(player)
                }
            }
            .build()
        )

        // Ender Chest button
        setItem(5, 2, GUIItemBuilder()
            .material(if (viewMode == ComparisonViewMode.ENDER_CHEST) Material.ENDER_CHEST else Material.ENDER_EYE)
            .name(Component.text("Ender Chest", if (viewMode == ComparisonViewMode.ENDER_CHEST) NamedTextColor.GREEN else NamedTextColor.GRAY))
            .lore("Click to view ender chest comparison")
            .onClick { event ->
                if (viewMode != ComparisonViewMode.ENDER_CHEST) {
                    val player = event.whoClicked as Player
                    InventoryComparisonGUI(plugin, player1Uuid, player1Name, player2Uuid, player2Name, groupName, gameMode, ComparisonViewMode.ENDER_CHEST).open(player)
                }
            }
            .build()
        )

        // GameMode selector
        setItem(5, 4, GUIItemBuilder()
            .material(getMaterialForGameMode(gameMode))
            .name("GameMode: $gameMode", NamedTextColor.AQUA)
            .lore("Click to cycle game modes")
            .onClick { event ->
                val player = event.whoClicked as Player
                val nextMode = getNextGameMode(gameMode)
                InventoryComparisonGUI(plugin, player1Uuid, player1Name, player2Uuid, player2Name, groupName, nextMode, viewMode).open(player)
            }
            .build()
        )

        // Stats comparison
        setItem(5, 6, createStatsComparisonItem())

        // Close button
        setItem(5, 8, GUIComponents.closeButton())
    }

    private fun createPlayerHeader(name: String, hasData: Boolean): GUIItem {
        return GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name(Component.text(name, if (hasData) NamedTextColor.GREEN else NamedTextColor.RED))
            .lore(if (hasData) "Data loaded" else "No data found")
            .lore("Group: $groupName")
            .lore("GameMode: $gameMode")
            .build()
    }

    private fun createComparisonItem(thisItem: ItemStack?, otherItem: ItemStack?, slot: Int, isPlayer1: Boolean): GUIItem {
        if (thisItem == null || thisItem.type.isAir) {
            // Empty slot
            if (otherItem != null && !otherItem.type.isAir) {
                // Other player has item, highlight as missing
                return GUIItemBuilder()
                    .material(Material.RED_STAINED_GLASS_PANE)
                    .name(Component.text("Empty", NamedTextColor.RED))
                    .lore(Component.text("Other player has: ${otherItem.type.name}", NamedTextColor.YELLOW))
                    .lore(Component.text("Amount: ${otherItem.amount}", NamedTextColor.GRAY))
                    .build()
            } else {
                // Both empty
                return GUIComponents.filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
            }
        }

        // Has item
        val itemClone = thisItem.clone()
        val meta = itemClone.itemMeta

        val lore = meta.lore()?.toMutableList() ?: mutableListOf()
        lore.add(Component.empty())
        lore.add(Component.text("--- Comparison ---", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false))

        if (otherItem == null || otherItem.type.isAir) {
            // Only this player has the item
            lore.add(Component.text("UNIQUE - Other player missing", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
        } else if (thisItem.isSimilar(otherItem)) {
            if (thisItem.amount == otherItem.amount) {
                // Identical
                lore.add(Component.text("MATCH - Identical", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
            } else {
                // Same item, different amount
                val diff = thisItem.amount - otherItem.amount
                val diffStr = if (diff > 0) "+$diff" else "$diff"
                lore.add(Component.text("DIFFER - Amount: $diffStr", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
            }
        } else if (thisItem.type == otherItem.type) {
            // Same type, different metadata
            lore.add(Component.text("DIFFER - Different metadata", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
        } else {
            // Different item entirely
            lore.add(Component.text("DIFFER - Other has: ${otherItem.type.name}", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false))
        }

        meta.lore(lore)
        itemClone.itemMeta = meta

        return GUIItem(itemClone)
    }

    private fun createArmorComparisonItem(thisItem: ItemStack?, otherItem: ItemStack?, slotName: String, isPlayer1: Boolean): GUIItem {
        if (thisItem == null || thisItem.type.isAir) {
            if (otherItem != null && !otherItem.type.isAir) {
                return GUIItemBuilder()
                    .material(Material.RED_STAINED_GLASS_PANE)
                    .name(Component.text("Empty $slotName", NamedTextColor.RED))
                    .lore("Other player has: ${otherItem.type.name}")
                    .build()
            }
            return GUIItemBuilder()
                .material(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
                .name(Component.text("Empty $slotName", NamedTextColor.GRAY))
                .build()
        }

        val item = thisItem.clone()
        val meta = item.itemMeta
        val lore = meta.lore()?.toMutableList() ?: mutableListOf()
        lore.add(Component.empty())

        if (otherItem == null || otherItem.type.isAir) {
            lore.add(Component.text("UNIQUE", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false))
        } else if (thisItem.isSimilar(otherItem)) {
            lore.add(Component.text("MATCH", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false))
        } else {
            lore.add(Component.text("DIFFER", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false))
        }

        meta.lore(lore)
        item.itemMeta = meta
        return GUIItem(item)
    }

    private fun createOffhandComparisonItem(thisItem: ItemStack?, otherItem: ItemStack?, isPlayer1: Boolean): GUIItem {
        return createArmorComparisonItem(thisItem, otherItem, "Offhand", isPlayer1)
    }

    private fun createStatsComparisonItem(): GUIItem {
        val data1 = player1Data
        val data2 = player2Data

        return GUIItemBuilder()
            .material(Material.COMPARATOR)
            .name("Stats Comparison", NamedTextColor.GOLD)
            .lore("")
            .lore(Component.text("Health: ", NamedTextColor.GRAY)
                .append(Component.text("${data1?.health ?: 0}", NamedTextColor.GREEN))
                .append(Component.text(" vs ", NamedTextColor.WHITE))
                .append(Component.text("${data2?.health ?: 0}", NamedTextColor.RED)))
            .lore(Component.text("Food: ", NamedTextColor.GRAY)
                .append(Component.text("${data1?.foodLevel ?: 0}", NamedTextColor.GREEN))
                .append(Component.text(" vs ", NamedTextColor.WHITE))
                .append(Component.text("${data2?.foodLevel ?: 0}", NamedTextColor.RED)))
            .lore(Component.text("Level: ", NamedTextColor.GRAY)
                .append(Component.text("${data1?.level ?: 0}", NamedTextColor.GREEN))
                .append(Component.text(" vs ", NamedTextColor.WHITE))
                .append(Component.text("${data2?.level ?: 0}", NamedTextColor.RED)))
            .lore(Component.text("XP: ", NamedTextColor.GRAY)
                .append(Component.text("${String.format("%.1f", (data1?.experience ?: 0f) * 100)}%", NamedTextColor.GREEN))
                .append(Component.text(" vs ", NamedTextColor.WHITE))
                .append(Component.text("${String.format("%.1f", (data2?.experience ?: 0f) * 100)}%", NamedTextColor.RED)))
            .build()
    }

    private fun getMaterialForGameMode(mode: GameMode): Material = when (mode) {
        GameMode.SURVIVAL -> Material.IRON_SWORD
        GameMode.CREATIVE -> Material.COMMAND_BLOCK
        GameMode.ADVENTURE -> Material.MAP
        GameMode.SPECTATOR -> Material.ENDER_EYE
    }

    private fun getNextGameMode(current: GameMode): GameMode = when (current) {
        GameMode.SURVIVAL -> GameMode.CREATIVE
        GameMode.CREATIVE -> GameMode.ADVENTURE
        GameMode.ADVENTURE -> GameMode.SPECTATOR
        GameMode.SPECTATOR -> GameMode.SURVIVAL
    }

    override fun fillEmptySlots(inventory: Inventory) {
        // Already filled in setupItems
    }
}

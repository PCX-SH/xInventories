package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.PlayerData
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.*

/**
 * GUI for viewing a player's stored inventory contents.
 */
class InventoryViewGUI(
    plugin: XInventories,
    private val targetUUID: UUID,
    private val targetName: String,
    private val groupName: String,
    private val gameMode: GameMode = GameMode.SURVIVAL
) : AbstractGUI(
    plugin,
    Component.text("$targetName - $groupName", NamedTextColor.DARK_AQUA),
    54
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
            // No data
            setItem(22, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("No Data", NamedTextColor.RED)
                .lore("No inventory data found for this group")
                .build()
            )

            // Back button - even when no data exists
            setItem(49, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Back", NamedTextColor.GRAY)
                .lore("Return to player details")
                .onClick { event ->
                    val player = event.whoClicked as Player
                    PlayerDetailGUI(plugin, targetUUID, targetName).open(player)
                }
                .build()
            )
            return
        }

        // Display inventory contents
        // Main inventory (slots 0-35)
        for (i in 0..35) {
            val item = data.mainInventory[i] ?: ItemStack(Material.AIR)
            if (item.type != Material.AIR) {
                setItem(i, GUIItem(item.clone()))
            }
        }

        // Armor slots (displayed in column 8)
        val armorSlots = listOf(39, 38, 37, 36) // Helmet, chest, legs, boots
        for ((index, armorSlot) in armorSlots.withIndex()) {
            val item = data.armorInventory[3 - index] ?: ItemStack(Material.AIR)
            if (item.type != Material.AIR) {
                setItem(armorSlot, GUIItem(item.clone()))
            } else {
                val placeholder = when (index) {
                    0 -> createArmorPlaceholder("Helmet", Material.LEATHER_HELMET)
                    1 -> createArmorPlaceholder("Chestplate", Material.LEATHER_CHESTPLATE)
                    2 -> createArmorPlaceholder("Leggings", Material.LEATHER_LEGGINGS)
                    3 -> createArmorPlaceholder("Boots", Material.LEATHER_BOOTS)
                    else -> GUIComponents.filler()
                }
                setItem(armorSlot, placeholder)
            }
        }

        // Offhand
        val offhandItem = data.offhand ?: ItemStack(Material.AIR)
        if (offhandItem.type != Material.AIR) {
            setItem(40, GUIItem(offhandItem.clone()))
        } else {
            setItem(40, createArmorPlaceholder("Offhand", Material.SHIELD))
        }

        // Info panel (bottom row)
        for (i in 45..53) {
            setItem(i, GUIComponents.filler(Material.BLACK_STAINED_GLASS_PANE))
        }

        // Stats info
        setItem(45, GUIItemBuilder()
            .material(Material.EXPERIENCE_BOTTLE)
            .name("Experience", NamedTextColor.GREEN)
            .lore("Level: ${data.level}")
            .lore("Progress: ${String.format("%.1f", data.experience * 100)}%")
            .build()
        )

        setItem(46, GUIItemBuilder()
            .material(Material.GOLDEN_APPLE)
            .name("Health & Food", NamedTextColor.RED)
            .lore("Health: ${String.format("%.1f", data.health)}")
            .lore("Food: ${data.foodLevel}")
            .lore("Saturation: ${String.format("%.1f", data.saturation)}")
            .build()
        )

        // GameMode selector
        setItem(47, GUIItemBuilder()
            .material(getMaterialForGameMode(gameMode))
            .name("GameMode: $gameMode", NamedTextColor.AQUA)
            .lore("Click to cycle through modes")
            .onClick { event ->
                val player = event.whoClicked as Player
                val nextMode = getNextGameMode(gameMode)
                InventoryViewGUI(plugin, targetUUID, targetName, groupName, nextMode).open(player)
            }
            .build()
        )

        // Back button
        setItem(49, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to player details")
            .onClick { event ->
                val player = event.whoClicked as Player
                PlayerDetailGUI(plugin, targetUUID, targetName).open(player)
            }
            .build()
        )

        // Ender chest button
        setItem(51, GUIItemBuilder()
            .material(Material.ENDER_CHEST)
            .name("View Ender Chest", NamedTextColor.DARK_PURPLE)
            .lore("Click to view ender chest")
            .onClick { event ->
                val player = event.whoClicked as Player
                EnderChestViewGUI(plugin, targetUUID, targetName, groupName, gameMode).open(player)
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

    private fun createArmorPlaceholder(name: String, material: Material): GUIItem {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Empty $name Slot")
                .color(NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false)
        )
        item.itemMeta = meta
        return GUIItem(item)
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

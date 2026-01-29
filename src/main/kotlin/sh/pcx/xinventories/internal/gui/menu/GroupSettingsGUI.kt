package sh.pcx.xinventories.internal.gui.menu

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory

/**
 * GUI for editing group settings.
 */
class GroupSettingsGUI(
    plugin: XInventories,
    private val groupName: String
) : AbstractGUI(
    plugin,
    Component.text("Settings: $groupName", NamedTextColor.DARK_AQUA),
    54
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        val groupService = plugin.serviceManager.groupService
        val group = groupService.getGroup(groupName)

        if (group == null) {
            setItem(22, GUIItemBuilder()
                .material(Material.BARRIER)
                .name("Group Not Found", NamedTextColor.RED)
                .build()
            )
            setItem(49, createBackButton())
            return
        }

        fillBorder(GUIComponents.filler())

        val settings = group.settings

        // Row 1: Save settings
        setItem(10, createToggleItem(
            "Save Health",
            Material.RED_DYE,
            settings.saveHealth,
            "Save player health when switching"
        ) { newValue ->
            updateSetting { it.copy(saveHealth = newValue) }
        })

        setItem(11, createToggleItem(
            "Save Hunger",
            Material.COOKED_BEEF,
            settings.saveHunger,
            "Save food level when switching"
        ) { newValue ->
            updateSetting { it.copy(saveHunger = newValue) }
        })

        setItem(12, createToggleItem(
            "Save Saturation",
            Material.GOLDEN_CARROT,
            settings.saveSaturation,
            "Save saturation when switching"
        ) { newValue ->
            updateSetting { it.copy(saveSaturation = newValue) }
        })

        setItem(13, createToggleItem(
            "Save Exhaustion",
            Material.ROTTEN_FLESH,
            settings.saveExhaustion,
            "Save exhaustion when switching"
        ) { newValue ->
            updateSetting { it.copy(saveExhaustion = newValue) }
        })

        setItem(14, createToggleItem(
            "Save Experience",
            Material.EXPERIENCE_BOTTLE,
            settings.saveExperience,
            "Save XP level when switching"
        ) { newValue ->
            updateSetting { it.copy(saveExperience = newValue) }
        })

        setItem(15, createToggleItem(
            "Save Potion Effects",
            Material.POTION,
            settings.savePotionEffects,
            "Save active effects when switching"
        ) { newValue ->
            updateSetting { it.copy(savePotionEffects = newValue) }
        })

        setItem(16, createToggleItem(
            "Save Ender Chest",
            Material.ENDER_CHEST,
            settings.saveEnderChest,
            "Save ender chest contents"
        ) { newValue ->
            updateSetting { it.copy(saveEnderChest = newValue) }
        })

        // Row 2: GameMode settings
        setItem(28, createToggleItem(
            "Separate GameMode Inventories",
            Material.COMMAND_BLOCK,
            settings.separateGameModeInventories,
            "Keep different inventories per gamemode"
        ) { newValue ->
            updateSetting { it.copy(separateGameModeInventories = newValue) }
        })

        setItem(30, createToggleItem(
            "Save GameMode",
            Material.IRON_SWORD,
            settings.saveGameMode,
            "Restore gamemode when returning"
        ) { newValue ->
            updateSetting { it.copy(saveGameMode = newValue) }
        })

        // Row 3: Special settings
        setItem(32, createToggleItem(
            "Clear on Death",
            Material.SKELETON_SKULL,
            settings.clearOnDeath,
            "Clear inventory when player dies"
        ) { newValue ->
            updateSetting { it.copy(clearOnDeath = newValue) }
        })

        setItem(34, createToggleItem(
            "Clear on Join",
            Material.BARRIER,
            settings.clearOnJoin,
            "Clear inventory when entering group"
        ) { newValue ->
            updateSetting { it.copy(clearOnJoin = newValue) }
        })

        // Info
        setItem(4, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Group Settings", NamedTextColor.AQUA)
            .lore("Configure what data is saved")
            .lore("and restored for this group.")
            .lore("")
            .lore("Click a setting to toggle it.")
            .build()
        )

        // Back button
        setItem(45, createBackButton())

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

    private fun createToggleItem(
        name: String,
        material: Material,
        enabled: Boolean,
        description: String,
        onToggle: (Boolean) -> Unit
    ): GUIItem {
        val color = if (enabled) NamedTextColor.GREEN else NamedTextColor.RED
        val status = if (enabled) "ENABLED" else "DISABLED"

        return GUIItemBuilder()
            .material(material)
            .name(name, color)
            .lore(description)
            .lore("")
            .lore("Status: $status")
            .lore("Click to toggle")
            .onClick { event ->
                val player = event.whoClicked as Player
                onToggle(!enabled)
                player.sendMessage(Component.text("$name ${if (!enabled) "enabled" else "disabled"}", NamedTextColor.AQUA))
                GroupSettingsGUI(plugin, groupName).open(player)
            }
            .build()
    }

    private fun updateSetting(modifier: (GroupSettings) -> GroupSettings) {
        val groupService = plugin.serviceManager.groupService
        groupService.modifyGroup(groupName) {
            modifySettings(modifier)
        }
    }

    private fun createBackButton(): GUIItem {
        return GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to group editor")
            .onClick { event ->
                val player = event.whoClicked as Player
                GroupEditorGUI(plugin, groupName).open(player)
            }
            .build()
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

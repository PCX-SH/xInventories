package sh.pcx.xinventories.internal.gui.menu

import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.gui.AbstractGUI
import sh.pcx.xinventories.internal.gui.GUIComponents
import sh.pcx.xinventories.internal.gui.GUIItem
import sh.pcx.xinventories.internal.gui.GUIItemBuilder
import sh.pcx.xinventories.internal.model.InventoryTemplate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * GUI for managing inventory templates.
 *
 * Features:
 * - List all templates with icons
 * - Create new template from current inventory
 * - Edit template contents (opens editor)
 * - Apply template to player (with player selector)
 * - Delete templates (with confirmation)
 * - Show template metadata (created date, items count)
 * - Pagination
 */
class TemplateManagerGUI(
    plugin: PluginContext,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Template Manager", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 36
    private val templates: List<InventoryTemplate>
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    init {
        templates = plugin.serviceManager.templateService.getAllTemplates()
            .sortedBy { it.name.lowercase() }
        setupItems()
    }

    private fun setupItems() {
        // Fill bottom two rows with filler
        for (i in 36..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (templates.isEmpty()) 0 else (templates.size - 1) / itemsPerPage

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

        // Create template button
        setItem(46, GUIItemBuilder()
            .material(Material.EMERALD)
            .name("Create Template", NamedTextColor.GREEN)
            .lore("Create a new template from")
            .lore("your current inventory")
            .lore("")
            .lore("Click to create")
            .onClick { event ->
                val player = event.whoClicked as Player
                CreateTemplateGUI(plugin).open(player)
            }
            .build()
        )

        // Refresh button
        setItem(47, GUIItemBuilder()
            .material(Material.SUNFLOWER)
            .name("Refresh", NamedTextColor.YELLOW)
            .lore("Reload templates from disk")
            .onClick { event ->
                val player = event.whoClicked as Player
                runBlocking {
                    plugin.serviceManager.templateService.reload()
                }
                TemplateManagerGUI(plugin, page).open(player)
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
                    TemplateManagerGUI(plugin, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("${templates.size} total templates")
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
                    TemplateManagerGUI(plugin, page + 1).open(player)
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

        // Populate templates
        val startIndex = page * itemsPerPage
        val endIndex = minOf(startIndex + itemsPerPage, templates.size)

        for (i in startIndex until endIndex) {
            val template = templates[i]
            val slot = i - startIndex

            setItem(slot, createTemplateItem(template))
        }
    }

    private fun createTemplateItem(template: InventoryTemplate): GUIItem {
        val itemCount = template.inventory.mainInventory.size +
                template.inventory.armorInventory.size +
                (if (template.inventory.offhand != null) 1 else 0)

        val createdBy = template.createdBy?.let {
            Bukkit.getOfflinePlayer(it).name ?: "Unknown"
        } ?: "System"

        val createdDate = dateFormatter.format(template.createdAt)

        return GUIItemBuilder()
            .material(Material.CHEST)
            .name(template.getEffectiveDisplayName(), NamedTextColor.GOLD)
            .lore("ID: ${template.name}")
            .apply {
                template.description?.let { lore(it) }
            }
            .lore("")
            .lore("Items: $itemCount")
            .lore("Created: $createdDate")
            .lore("By: $createdBy")
            .lore("")
            .lore("Left-click: View/Edit")
            .lore("Right-click: Quick actions")
            .onClick { event ->
                val player = event.whoClicked as Player
                if (event.isRightClick) {
                    TemplateActionsGUI(plugin, template).open(player)
                } else {
                    TemplateEditorGUI(plugin, template).open(player)
                }
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

/**
 * GUI for creating a new template from the player's current inventory.
 */
class CreateTemplateGUI(
    plugin: PluginContext
) : AbstractGUI(
    plugin,
    Component.text("Create Template", NamedTextColor.DARK_AQUA),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler())

        // Info
        setItem(1, 4, GUIItemBuilder()
            .material(Material.BOOK)
            .name("Create New Template", NamedTextColor.GOLD)
            .lore("Creates a template from your")
            .lore("current inventory state.")
            .lore("")
            .lore("Use /xinv template create <name>")
            .lore("to create a template.")
            .build()
        )

        // Back button
        setItem(2, 0, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to template manager")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplateManagerGUI(plugin).open(player)
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
 * GUI for template quick actions (apply, delete, etc.).
 */
class TemplateActionsGUI(
    plugin: PluginContext,
    private val template: InventoryTemplate
) : AbstractGUI(
    plugin,
    Component.text("Template: ${template.name}", NamedTextColor.DARK_AQUA),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler())

        // Template info
        setItem(1, 1, GUIItemBuilder()
            .material(Material.CHEST)
            .name(template.getEffectiveDisplayName(), NamedTextColor.GOLD)
            .apply {
                template.description?.let { lore(it) }
            }
            .build()
        )

        // Apply to player button
        setItem(1, 3, GUIItemBuilder()
            .material(Material.PLAYER_HEAD)
            .name("Apply to Player", NamedTextColor.GREEN)
            .lore("Select a player to apply")
            .lore("this template to")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplatePlayerSelectorGUI(plugin, template).open(player)
            }
            .build()
        )

        // Edit template button
        setItem(1, 5, GUIItemBuilder()
            .material(Material.WRITABLE_BOOK)
            .name("Edit Template", NamedTextColor.YELLOW)
            .lore("View and modify template contents")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplateEditorGUI(plugin, template).open(player)
            }
            .build()
        )

        // Delete template button
        setItem(1, 7, GUIItemBuilder()
            .material(Material.TNT)
            .name("Delete Template", NamedTextColor.RED)
            .lore("Permanently delete this template")
            .lore("")
            .lore("This cannot be undone!")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplateDeleteConfirmGUI(plugin, template).open(player)
            }
            .build()
        )

        // Back button
        setItem(2, 0, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to template manager")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplateManagerGUI(plugin).open(player)
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
 * GUI for selecting a player to apply a template to.
 */
class TemplatePlayerSelectorGUI(
    plugin: PluginContext,
    private val template: InventoryTemplate,
    private val page: Int = 0
) : AbstractGUI(
    plugin,
    Component.text("Select Player - ${template.name}", NamedTextColor.DARK_AQUA),
    54
) {

    private val itemsPerPage = 45
    private val players = Bukkit.getOnlinePlayers().sortedBy { it.name }

    init {
        setupItems()
    }

    private fun setupItems() {
        // Fill bottom row with filler
        for (i in 45..53) {
            setItem(i, GUIComponents.filler())
        }

        val maxPage = if (players.isEmpty()) 0 else (players.size - 1) / itemsPerPage

        // Back button
        setItem(45, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to template actions")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplateActionsGUI(plugin, template).open(player)
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
                    TemplatePlayerSelectorGUI(plugin, template, page - 1).open(player)
                }
                .build()
            )
        }

        // Page indicator
        setItem(49, GUIItemBuilder()
            .material(Material.PAPER)
            .name("Page ${page + 1}/${maxPage + 1}", NamedTextColor.YELLOW)
            .lore("${players.size} online players")
            .build()
        )

        // Next page button
        if (page < maxPage) {
            setItem(50, GUIItemBuilder()
                .material(Material.ARROW)
                .name("Next Page", NamedTextColor.YELLOW)
                .onClick { event ->
                    val player = event.whoClicked as Player
                    TemplatePlayerSelectorGUI(plugin, template, page + 1).open(player)
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
            val targetPlayer = players[i]
            val slot = i - startIndex

            setItem(slot, createPlayerItem(targetPlayer))
        }
    }

    private fun createPlayerItem(targetPlayer: Player): GUIItem {
        val item = ItemStack(Material.PLAYER_HEAD)
        val meta = item.itemMeta as org.bukkit.inventory.meta.SkullMeta
        meta.owningPlayer = targetPlayer

        meta.displayName(
            Component.text(targetPlayer.name)
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        )

        meta.lore(listOf(
            Component.text("Click to apply template")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        ))

        item.itemMeta = meta

        return GUIItem(item) { event ->
            val player = event.whoClicked as Player
            TemplateApplyConfirmGUI(plugin, template, targetPlayer).open(player)
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
 * GUI for confirming template application to a player.
 */
class TemplateApplyConfirmGUI(
    plugin: PluginContext,
    private val template: InventoryTemplate,
    private val targetPlayer: Player
) : AbstractGUI(
    plugin,
    Component.text("Confirm Apply Template", NamedTextColor.DARK_AQUA),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler())

        // Warning info
        setItem(1, 4, GUIItemBuilder()
            .material(Material.NETHER_STAR)
            .name("Apply Template?", NamedTextColor.GOLD)
            .lore("Template: ${template.name}")
            .lore("Target: ${targetPlayer.name}")
            .lore("")
            .lore("This will replace the player's")
            .lore("current inventory!")
            .build()
        )

        // Confirm button
        setItem(1, 2, GUIItemBuilder()
            .material(Material.LIME_WOOL)
            .name("Confirm", NamedTextColor.GREEN)
            .lore("Apply the template")
            .onClick { event ->
                val player = event.whoClicked as Player
                val group = plugin.serviceManager.groupService.getGroupForPlayer(targetPlayer)

                runBlocking {
                    plugin.serviceManager.templateService.applyTemplate(
                        targetPlayer,
                        template,
                        group.toApiModel(),
                        sh.pcx.xinventories.internal.model.TemplateApplyTrigger.MANUAL,
                        true
                    )
                }

                player.sendMessage(Component.text("Template '${template.name}' applied to ${targetPlayer.name}", NamedTextColor.GREEN))
                player.closeInventory()
            }
            .build()
        )

        // Cancel button
        setItem(1, 6, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Cancel", NamedTextColor.RED)
            .lore("Go back")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplatePlayerSelectorGUI(plugin, template).open(player)
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
 * GUI for confirming template deletion.
 */
class TemplateDeleteConfirmGUI(
    plugin: PluginContext,
    private val template: InventoryTemplate
) : AbstractGUI(
    plugin,
    Component.text("Delete Template?", NamedTextColor.DARK_RED),
    27
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        fillBorder(GUIComponents.filler(Material.RED_STAINED_GLASS_PANE))

        // Warning info
        setItem(1, 4, GUIItemBuilder()
            .material(Material.TNT)
            .name("Delete Template?", NamedTextColor.RED)
            .lore("Template: ${template.name}")
            .lore("")
            .lore("This action cannot be undone!")
            .lore("The template will be permanently deleted.")
            .build()
        )

        // Confirm button
        setItem(1, 2, GUIItemBuilder()
            .material(Material.LIME_WOOL)
            .name("Confirm Delete", NamedTextColor.GREEN)
            .lore("Permanently delete this template")
            .onClick { event ->
                val player = event.whoClicked as Player

                runBlocking {
                    plugin.serviceManager.templateService.deleteTemplate(template.name)
                }

                player.sendMessage(Component.text("Template '${template.name}' deleted", NamedTextColor.RED))
                TemplateManagerGUI(plugin).open(player)
            }
            .build()
        )

        // Cancel button
        setItem(1, 6, GUIItemBuilder()
            .material(Material.RED_WOOL)
            .name("Cancel", NamedTextColor.RED)
            .lore("Keep the template")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplateActionsGUI(plugin, template).open(player)
            }
            .build()
        )
    }

    override fun fillEmptySlots(inventory: Inventory) {
        val filler = GUIComponents.filler(Material.RED_STAINED_GLASS_PANE)
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

/**
 * GUI for viewing/editing template contents.
 */
class TemplateEditorGUI(
    plugin: PluginContext,
    private val template: InventoryTemplate
) : AbstractGUI(
    plugin,
    Component.text("Template: ${template.name}", NamedTextColor.DARK_AQUA),
    54
) {

    init {
        setupItems()
    }

    private fun setupItems() {
        // Display template inventory contents (read-only view)
        val data = template.inventory

        // Main inventory (slots 0-35)
        for (i in 0..35) {
            val item = data.mainInventory[i] ?: ItemStack(Material.AIR)
            if (item.type != Material.AIR) {
                setItem(i, GUIItem(item.clone()))
            }
        }

        // Armor slots (slots 36-39)
        val armorSlots = listOf(39, 38, 37, 36)
        for ((index, armorSlot) in armorSlots.withIndex()) {
            val item = data.armorInventory[3 - index] ?: ItemStack(Material.AIR)
            if (item.type != Material.AIR) {
                setItem(armorSlot, GUIItem(item.clone()))
            } else {
                val placeholder = createArmorPlaceholder(index)
                setItem(armorSlot, placeholder)
            }
        }

        // Offhand (slot 40)
        val offhandItem = data.offhand ?: ItemStack(Material.AIR)
        if (offhandItem.type != Material.AIR) {
            setItem(40, GUIItem(offhandItem.clone()))
        } else {
            setItem(40, createArmorPlaceholder(4))
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

        // Effects info
        setItem(47, GUIItemBuilder()
            .material(Material.POTION)
            .name("Potion Effects", NamedTextColor.LIGHT_PURPLE)
            .apply {
                if (data.potionEffects.isEmpty()) {
                    lore("No effects")
                } else {
                    data.potionEffects.take(5).forEach { effect ->
                        lore("${effect.type.name} ${effect.amplifier + 1}")
                    }
                    if (data.potionEffects.size > 5) {
                        lore("... and ${data.potionEffects.size - 5} more")
                    }
                }
            }
            .build()
        )

        // Back button
        setItem(49, GUIItemBuilder()
            .material(Material.ARROW)
            .name("Back", NamedTextColor.GRAY)
            .lore("Return to template manager")
            .onClick { event ->
                val player = event.whoClicked as Player
                TemplateManagerGUI(plugin).open(player)
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

    private fun createArmorPlaceholder(index: Int): GUIItem {
        val (name, material) = when (index) {
            0 -> "Helmet" to Material.LEATHER_HELMET
            1 -> "Chestplate" to Material.LEATHER_CHESTPLATE
            2 -> "Leggings" to Material.LEATHER_LEGGINGS
            3 -> "Boots" to Material.LEATHER_BOOTS
            4 -> "Offhand" to Material.SHIELD
            else -> "Unknown" to Material.BARRIER
        }

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

    override fun fillEmptySlots(inventory: Inventory) {
        val filler = GUIComponents.filler(Material.LIGHT_GRAY_STAINED_GLASS_PANE)
        for (i in 0 until size) {
            if (!items.containsKey(i)) {
                items[i] = filler
                inventory.setItem(i, filler.itemStack)
            }
        }
    }
}

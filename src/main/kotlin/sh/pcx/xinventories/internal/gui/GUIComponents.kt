package sh.pcx.xinventories.internal.gui

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.inventory.ItemStack

/**
 * Common GUI components and item builders.
 */
object GUIComponents {

    /**
     * Creates a filler item (typically gray stained glass pane).
     */
    fun filler(material: Material = Material.GRAY_STAINED_GLASS_PANE): GUIItem {
        val item = ItemStack(material)
        val meta = item.itemMeta
        meta.displayName(Component.empty())
        item.itemMeta = meta
        return GUIItem(item)
    }

    /**
     * Creates a back button.
     */
    fun backButton(onClick: () -> Unit): GUIItem {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Back")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        )
        item.itemMeta = meta
        return GUIItem(item) { onClick() }
    }

    /**
     * Creates a close button.
     */
    fun closeButton(): GUIItem {
        val item = ItemStack(Material.BARRIER)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Close")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        )
        item.itemMeta = meta
        return GUIItem(item) { event ->
            event.whoClicked.closeInventory()
        }
    }

    /**
     * Creates a previous page button.
     */
    fun previousPageButton(currentPage: Int, onClick: () -> Unit): GUIItem {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Previous Page")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        )
        meta.lore(listOf(
            Component.text("Current page: $currentPage")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta
        return GUIItem(item) { onClick() }
    }

    /**
     * Creates a next page button.
     */
    fun nextPageButton(currentPage: Int, onClick: () -> Unit): GUIItem {
        val item = ItemStack(Material.ARROW)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Next Page")
                .color(NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)
        )
        meta.lore(listOf(
            Component.text("Current page: $currentPage")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)
        ))
        item.itemMeta = meta
        return GUIItem(item) { onClick() }
    }

    /**
     * Creates a confirmation button.
     */
    fun confirmButton(onClick: () -> Unit): GUIItem {
        val item = ItemStack(Material.LIME_WOOL)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Confirm")
                .color(NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false)
        )
        item.itemMeta = meta
        return GUIItem(item) { onClick() }
    }

    /**
     * Creates a cancel button.
     */
    fun cancelButton(onClick: () -> Unit): GUIItem {
        val item = ItemStack(Material.RED_WOOL)
        val meta = item.itemMeta
        meta.displayName(
            Component.text("Cancel")
                .color(NamedTextColor.RED)
                .decoration(TextDecoration.ITALIC, false)
        )
        item.itemMeta = meta
        return GUIItem(item) { onClick() }
    }

    /**
     * Creates an info item.
     */
    fun infoItem(name: Component, vararg lore: Component): GUIItem {
        val item = ItemStack(Material.PAPER)
        val meta = item.itemMeta
        meta.displayName(name.decoration(TextDecoration.ITALIC, false))
        meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
        item.itemMeta = meta
        return GUIItem(item)
    }
}

/**
 * Builder for creating custom GUI items.
 */
class GUIItemBuilder(private var material: Material = Material.STONE) {

    private var amount: Int = 1
    private var displayName: Component? = null
    private var lore: MutableList<Component> = mutableListOf()
    private var customModelData: Int? = null
    private var onClick: ((org.bukkit.event.inventory.InventoryClickEvent) -> Unit)? = null

    fun material(material: Material) = apply { this.material = material }
    fun amount(amount: Int) = apply { this.amount = amount }
    fun name(name: Component) = apply { this.displayName = name }
    fun name(name: String, color: NamedTextColor = NamedTextColor.WHITE) = apply {
        this.displayName = Component.text(name)
            .color(color)
            .decoration(TextDecoration.ITALIC, false)
    }
    fun lore(vararg lines: Component) = apply { this.lore.addAll(lines) }
    fun lore(vararg lines: String) = apply {
        lines.forEach { line ->
            this.lore.add(
                Component.text(line)
                    .color(NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)
            )
        }
    }
    fun customModelData(data: Int) = apply { this.customModelData = data }
    fun onClick(handler: (org.bukkit.event.inventory.InventoryClickEvent) -> Unit) = apply {
        this.onClick = handler
    }

    fun build(): GUIItem {
        val item = ItemStack(material, amount)
        val meta = item.itemMeta

        displayName?.let { meta.displayName(it.decoration(TextDecoration.ITALIC, false)) }

        if (lore.isNotEmpty()) {
            meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
        }

        customModelData?.let { meta.setCustomModelData(it) }

        item.itemMeta = meta
        return GUIItem(item, onClick)
    }
}

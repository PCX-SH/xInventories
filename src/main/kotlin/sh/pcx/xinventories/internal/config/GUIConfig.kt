package sh.pcx.xinventories.internal.config

import org.bukkit.Material
import org.bukkit.Sound

/**
 * Configuration for GUI customization.
 */
data class GUIConfig(
    /** The color theme for GUIs */
    val theme: GUITheme = GUITheme.DEFAULT,
    /** Sound settings for GUI interactions */
    val sounds: GUISoundConfig = GUISoundConfig(),
    /** Custom item configurations for common buttons */
    val customItems: GUICustomItems = GUICustomItems()
)

/**
 * Available GUI themes.
 */
enum class GUITheme {
    /** Default theme with gray glass panes */
    DEFAULT,
    /** Dark theme with black glass panes */
    DARK,
    /** Light theme with white glass panes */
    LIGHT
}

/**
 * Sound configuration for GUI interactions.
 */
data class GUISoundConfig(
    /** Sound played when clicking a button */
    val click: Sound = Sound.UI_BUTTON_CLICK,
    /** Sound played on successful action */
    val success: Sound = Sound.ENTITY_PLAYER_LEVELUP,
    /** Sound played on error */
    val error: Sound = Sound.ENTITY_VILLAGER_NO
)

/**
 * Custom item configuration for common GUI buttons.
 */
data class GUICustomItems(
    /** Back button configuration */
    val backButton: GUIItemConfig = GUIItemConfig(
        type = Material.ARROW,
        name = "<gray>Back"
    ),
    /** Close button configuration */
    val closeButton: GUIItemConfig = GUIItemConfig(
        type = Material.BARRIER,
        name = "<red>Close"
    ),
    /** Filler item configuration */
    val filler: GUIItemConfig = GUIItemConfig(
        type = Material.GRAY_STAINED_GLASS_PANE,
        name = ""
    )
)

/**
 * Configuration for a single GUI item.
 */
data class GUIItemConfig(
    /** The material type */
    val type: Material,
    /** Display name (MiniMessage format) */
    val name: String,
    /** Lore lines (MiniMessage format) */
    val lore: List<String> = emptyList()
)

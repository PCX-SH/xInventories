package sh.pcx.xinventories.unit.config

import org.bukkit.Material
import org.bukkit.Sound
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.config.GUIConfig
import sh.pcx.xinventories.internal.config.GUICustomItems
import sh.pcx.xinventories.internal.config.GUIItemConfig
import sh.pcx.xinventories.internal.config.GUISoundConfig
import sh.pcx.xinventories.internal.config.GUITheme

@DisplayName("GUIConfig")
class GUIConfigTest {

    @Nested
    @DisplayName("GUITheme")
    inner class GUIThemeTests {

        @Test
        @DisplayName("all themes are available")
        fun allThemesAreAvailable() {
            val themes = GUITheme.entries

            assertTrue(themes.contains(GUITheme.DEFAULT))
            assertTrue(themes.contains(GUITheme.DARK))
            assertTrue(themes.contains(GUITheme.LIGHT))
        }

        @Test
        @DisplayName("default theme is DEFAULT")
        fun defaultThemeIsDefault() {
            val config = GUIConfig()
            assertEquals(GUITheme.DEFAULT, config.theme)
        }
    }

    @Nested
    @DisplayName("GUISoundConfig")
    inner class GUISoundConfigTests {

        @Test
        @DisplayName("default click sound is UI_BUTTON_CLICK")
        fun defaultClickSoundIsUiButtonClick() {
            val config = GUISoundConfig()
            assertEquals(Sound.UI_BUTTON_CLICK, config.click)
        }

        @Test
        @DisplayName("default success sound is ENTITY_PLAYER_LEVELUP")
        fun defaultSuccessSoundIsEntityPlayerLevelup() {
            val config = GUISoundConfig()
            assertEquals(Sound.ENTITY_PLAYER_LEVELUP, config.success)
        }

        @Test
        @DisplayName("default error sound is ENTITY_VILLAGER_NO")
        fun defaultErrorSoundIsEntityVillagerNo() {
            val config = GUISoundConfig()
            assertEquals(Sound.ENTITY_VILLAGER_NO, config.error)
        }

        @Test
        @DisplayName("custom sounds can be set")
        fun customSoundsCanBeSet() {
            val config = GUISoundConfig(
                click = Sound.BLOCK_NOTE_BLOCK_PLING,
                success = Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                error = Sound.BLOCK_ANVIL_LAND
            )

            assertEquals(Sound.BLOCK_NOTE_BLOCK_PLING, config.click)
            assertEquals(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, config.success)
            assertEquals(Sound.BLOCK_ANVIL_LAND, config.error)
        }
    }

    @Nested
    @DisplayName("GUIItemConfig")
    inner class GUIItemConfigTests {

        @Test
        @DisplayName("creates item config with required fields")
        fun createsItemConfigWithRequiredFields() {
            val config = GUIItemConfig(
                type = Material.DIAMOND,
                name = "<gold>Diamond"
            )

            assertEquals(Material.DIAMOND, config.type)
            assertEquals("<gold>Diamond", config.name)
            assertTrue(config.lore.isEmpty())
        }

        @Test
        @DisplayName("creates item config with lore")
        fun createsItemConfigWithLore() {
            val config = GUIItemConfig(
                type = Material.EMERALD,
                name = "Emerald",
                lore = listOf("Line 1", "Line 2", "Line 3")
            )

            assertEquals(3, config.lore.size)
            assertEquals("Line 1", config.lore[0])
        }
    }

    @Nested
    @DisplayName("GUICustomItems")
    inner class GUICustomItemsTests {

        @Test
        @DisplayName("default back button is ARROW")
        fun defaultBackButtonIsArrow() {
            val items = GUICustomItems()
            assertEquals(Material.ARROW, items.backButton.type)
            assertEquals("<gray>Back", items.backButton.name)
        }

        @Test
        @DisplayName("default close button is BARRIER")
        fun defaultCloseButtonIsBarrier() {
            val items = GUICustomItems()
            assertEquals(Material.BARRIER, items.closeButton.type)
            assertEquals("<red>Close", items.closeButton.name)
        }

        @Test
        @DisplayName("default filler is GRAY_STAINED_GLASS_PANE")
        fun defaultFillerIsGrayStainedGlassPane() {
            val items = GUICustomItems()
            assertEquals(Material.GRAY_STAINED_GLASS_PANE, items.filler.type)
            assertEquals("", items.filler.name)
        }
    }

    @Nested
    @DisplayName("GUIConfig Integration")
    inner class GUIConfigIntegrationTests {

        @Test
        @DisplayName("default config has sensible defaults")
        fun defaultConfigHasSensibleDefaults() {
            val config = GUIConfig()

            assertEquals(GUITheme.DEFAULT, config.theme)
            assertEquals(Sound.UI_BUTTON_CLICK, config.sounds.click)
            assertEquals(Material.ARROW, config.customItems.backButton.type)
        }

        @Test
        @DisplayName("custom config can override all settings")
        fun customConfigCanOverrideAllSettings() {
            val config = GUIConfig(
                theme = GUITheme.DARK,
                sounds = GUISoundConfig(
                    click = Sound.BLOCK_NOTE_BLOCK_PLING,
                    success = Sound.ENTITY_FIREWORK_ROCKET_BLAST,
                    error = Sound.ENTITY_WITHER_HURT
                ),
                customItems = GUICustomItems(
                    backButton = GUIItemConfig(Material.SPECTRAL_ARROW, "<white>Go Back"),
                    closeButton = GUIItemConfig(Material.STRUCTURE_VOID, "<dark_red>Exit"),
                    filler = GUIItemConfig(Material.BLACK_STAINED_GLASS_PANE, "")
                )
            )

            assertEquals(GUITheme.DARK, config.theme)
            assertEquals(Sound.BLOCK_NOTE_BLOCK_PLING, config.sounds.click)
            assertEquals(Material.SPECTRAL_ARROW, config.customItems.backButton.type)
            assertEquals("<white>Go Back", config.customItems.backButton.name)
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, config.customItems.filler.type)
        }
    }

    @Nested
    @DisplayName("Theme Filler Materials")
    inner class ThemeFillerMaterialsTests {

        @Test
        @DisplayName("DEFAULT theme uses gray glass")
        fun defaultThemeUsesGrayGlass() {
            val theme = GUITheme.DEFAULT
            val fillerMaterial = getFillerForTheme(theme)
            assertEquals(Material.GRAY_STAINED_GLASS_PANE, fillerMaterial)
        }

        @Test
        @DisplayName("DARK theme uses black glass")
        fun darkThemeUsesBlackGlass() {
            val theme = GUITheme.DARK
            val fillerMaterial = getFillerForTheme(theme)
            assertEquals(Material.BLACK_STAINED_GLASS_PANE, fillerMaterial)
        }

        @Test
        @DisplayName("LIGHT theme uses white glass")
        fun lightThemeUsesWhiteGlass() {
            val theme = GUITheme.LIGHT
            val fillerMaterial = getFillerForTheme(theme)
            assertEquals(Material.WHITE_STAINED_GLASS_PANE, fillerMaterial)
        }

        private fun getFillerForTheme(theme: GUITheme): Material {
            return when (theme) {
                GUITheme.DEFAULT -> Material.GRAY_STAINED_GLASS_PANE
                GUITheme.DARK -> Material.BLACK_STAINED_GLASS_PANE
                GUITheme.LIGHT -> Material.WHITE_STAINED_GLASS_PANE
            }
        }
    }
}

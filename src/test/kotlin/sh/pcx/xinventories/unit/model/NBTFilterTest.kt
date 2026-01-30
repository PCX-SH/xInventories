package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.FilterAction
import sh.pcx.xinventories.internal.model.NBTFilter
import sh.pcx.xinventories.internal.model.NBTFilterConfig
import sh.pcx.xinventories.internal.model.NBTFilterResult
import sh.pcx.xinventories.internal.model.NBTFilterType

@DisplayName("NBTFilter")
class NBTFilterTest {

    @Nested
    @DisplayName("NBTFilterType")
    inner class NBTFilterTypeTests {

        @Test
        @DisplayName("all filter types are defined")
        fun allFilterTypesAreDefined() {
            assertEquals(5, NBTFilterType.entries.size)
            assertNotNull(NBTFilterType.ENCHANTMENT)
            assertNotNull(NBTFilterType.CUSTOM_MODEL_DATA)
            assertNotNull(NBTFilterType.DISPLAY_NAME)
            assertNotNull(NBTFilterType.LORE)
            assertNotNull(NBTFilterType.ATTRIBUTE)
        }
    }

    @Nested
    @DisplayName("FilterAction")
    inner class FilterActionTests {

        @Test
        @DisplayName("all filter actions are defined")
        fun allFilterActionsAreDefined() {
            assertEquals(3, FilterAction.entries.size)
            assertNotNull(FilterAction.ALLOW)
            assertNotNull(FilterAction.REMOVE)
            assertNotNull(FilterAction.DROP)
        }
    }

    @Nested
    @DisplayName("NBTFilter creation")
    inner class NBTFilterCreationTests {

        @Test
        @DisplayName("enchantment filter is created correctly")
        fun enchantmentFilterCreation() {
            val filter = NBTFilter.enchantment("SHARPNESS", FilterAction.REMOVE, minLevel = 1, maxLevel = 3)

            assertEquals(NBTFilterType.ENCHANTMENT, filter.type)
            assertEquals(FilterAction.REMOVE, filter.action)
            assertEquals("SHARPNESS", filter.enchantment)
            assertEquals(1, filter.minLevel)
            assertEquals(3, filter.maxLevel)
        }

        @Test
        @DisplayName("custom model data filter is created correctly")
        fun customModelDataFilterCreation() {
            val filter = NBTFilter.customModelData(setOf(1001, 1002, 1003), FilterAction.ALLOW)

            assertEquals(NBTFilterType.CUSTOM_MODEL_DATA, filter.type)
            assertEquals(FilterAction.ALLOW, filter.action)
            assertEquals(setOf(1001, 1002, 1003), filter.customModelData)
        }

        @Test
        @DisplayName("display name filter is created correctly")
        fun displayNameFilterCreation() {
            val filter = NBTFilter.displayName(".*\\[BANNED\\].*", FilterAction.REMOVE)

            assertEquals(NBTFilterType.DISPLAY_NAME, filter.type)
            assertEquals(FilterAction.REMOVE, filter.action)
            assertEquals(".*\\[BANNED\\].*", filter.namePattern)
        }

        @Test
        @DisplayName("lore filter is created correctly")
        fun loreFilterCreation() {
            val filter = NBTFilter.lore(".*admin.*", FilterAction.DROP)

            assertEquals(NBTFilterType.LORE, filter.type)
            assertEquals(FilterAction.DROP, filter.action)
            assertEquals(".*admin.*", filter.lorePattern)
        }

        @Test
        @DisplayName("attribute filter is created correctly")
        fun attributeFilterCreation() {
            val filter = NBTFilter.attribute("generic.attack_damage", FilterAction.REMOVE)

            assertEquals(NBTFilterType.ATTRIBUTE, filter.type)
            assertEquals(FilterAction.REMOVE, filter.action)
            assertEquals("generic.attack_damage", filter.attributeName)
        }
    }

    @Nested
    @DisplayName("NBTFilter validation")
    inner class NBTFilterValidationTests {

        @Test
        @DisplayName("valid enchantment filter has no errors")
        fun validEnchantmentFilterNoErrors() {
            val filter = NBTFilter.enchantment("SHARPNESS", FilterAction.REMOVE)
            val errors = filter.validate()

            assertTrue(errors.isEmpty())
        }

        @Test
        @DisplayName("enchantment filter without enchantment has errors")
        fun enchantmentFilterWithoutEnchantmentHasErrors() {
            val filter = NBTFilter(NBTFilterType.ENCHANTMENT, FilterAction.REMOVE)
            val errors = filter.validate()

            assertTrue(errors.isNotEmpty())
            assertTrue(errors.any { it.contains("enchantment") })
        }

        @Test
        @DisplayName("custom model data filter without values has errors")
        fun customModelDataFilterWithoutValuesHasErrors() {
            val filter = NBTFilter(NBTFilterType.CUSTOM_MODEL_DATA, FilterAction.REMOVE)
            val errors = filter.validate()

            assertTrue(errors.isNotEmpty())
            assertTrue(errors.any { it.contains("values") })
        }

        @Test
        @DisplayName("display name filter without pattern has errors")
        fun displayNameFilterWithoutPatternHasErrors() {
            val filter = NBTFilter(NBTFilterType.DISPLAY_NAME, FilterAction.REMOVE)
            val errors = filter.validate()

            assertTrue(errors.isNotEmpty())
            assertTrue(errors.any { it.contains("pattern") })
        }

        @Test
        @DisplayName("display name filter with invalid regex has errors")
        fun displayNameFilterWithInvalidRegexHasErrors() {
            val filter = NBTFilter(NBTFilterType.DISPLAY_NAME, FilterAction.REMOVE, namePattern = "[invalid")
            val errors = filter.validate()

            assertTrue(errors.isNotEmpty())
            assertTrue(errors.any { it.contains("Invalid regex") })
        }
    }

    @Nested
    @DisplayName("NBTFilter pattern compilation")
    inner class NBTFilterPatternTests {

        @Test
        @DisplayName("valid regex compiles successfully")
        fun validRegexCompilesSuccessfully() {
            val filter = NBTFilter.displayName(".*test.*", FilterAction.REMOVE)

            assertNotNull(filter.compiledNamePattern)
        }

        @Test
        @DisplayName("invalid regex returns null")
        fun invalidRegexReturnsNull() {
            val filter = NBTFilter(NBTFilterType.DISPLAY_NAME, FilterAction.REMOVE, namePattern = "[invalid")

            assertNull(filter.compiledNamePattern)
        }

        @Test
        @DisplayName("lore pattern compiles successfully")
        fun lorePatternCompilesSuccessfully() {
            val filter = NBTFilter.lore("^Admin Only$", FilterAction.REMOVE)

            assertNotNull(filter.compiledLorePattern)
        }
    }

    @Nested
    @DisplayName("NBTFilter display string")
    inner class NBTFilterDisplayStringTests {

        @Test
        @DisplayName("enchantment filter display string")
        fun enchantmentFilterDisplayString() {
            val filter = NBTFilter.enchantment("SHARPNESS", FilterAction.REMOVE, minLevel = 1, maxLevel = 3)
            val display = filter.toDisplayString()

            assertTrue(display.contains("Enchantment"))
            assertTrue(display.contains("SHARPNESS"))
            assertTrue(display.contains("1-3"))
            assertTrue(display.contains("REMOVE"))
        }

        @Test
        @DisplayName("custom model data filter display string")
        fun customModelDataFilterDisplayString() {
            val filter = NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW)
            val display = filter.toDisplayString()

            assertTrue(display.contains("Custom Model Data"))
            assertTrue(display.contains("1001"))
            assertTrue(display.contains("ALLOW"))
        }
    }

    @Nested
    @DisplayName("NBTFilterConfig")
    inner class NBTFilterConfigTests {

        @Test
        @DisplayName("disabled config returns empty filters")
        fun disabledConfigReturnsEmptyFilters() {
            val config = NBTFilterConfig.DISABLED

            assertFalse(config.enabled)
            assertTrue(config.filters.isEmpty())
        }

        @Test
        @DisplayName("config with filters validates all")
        fun configWithFiltersValidatesAll() {
            val config = NBTFilterConfig(
                enabled = true,
                filters = listOf(
                    NBTFilter.enchantment("SHARPNESS", FilterAction.REMOVE),
                    NBTFilter(NBTFilterType.DISPLAY_NAME, FilterAction.REMOVE) // Invalid
                )
            )

            val errors = config.validate()

            assertEquals(1, errors.size)
            assertTrue(errors.containsKey(1))
        }

        @Test
        @DisplayName("config categorizes filters by action")
        fun configCategorizesByAction() {
            val config = NBTFilterConfig(
                enabled = true,
                filters = listOf(
                    NBTFilter.enchantment("SHARPNESS", FilterAction.REMOVE),
                    NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW),
                    NBTFilter.displayName("test", FilterAction.DROP)
                )
            )

            assertEquals(1, config.getAllowFilters().size)
            assertEquals(1, config.getRemoveFilters().size)
            assertEquals(1, config.getDropFilters().size)
        }
    }

    @Nested
    @DisplayName("NBTFilterResult")
    inner class NBTFilterResultTests {

        @Test
        @DisplayName("NO_MATCH result is not matched")
        fun noMatchResultIsNotMatched() {
            val result = NBTFilterResult.NO_MATCH

            assertFalse(result.matched)
            assertNull(result.filter)
            assertNull(result.matchReason)
        }

        @Test
        @DisplayName("matched result has filter and reason")
        fun matchedResultHasFilterAndReason() {
            val filter = NBTFilter.enchantment("SHARPNESS", FilterAction.REMOVE)
            val result = NBTFilterResult.matched(filter, "Enchantment matched")

            assertTrue(result.matched)
            assertEquals(filter, result.filter)
            assertEquals("Enchantment matched", result.matchReason)
        }
    }
}

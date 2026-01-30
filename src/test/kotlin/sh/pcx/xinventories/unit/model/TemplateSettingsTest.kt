package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.internal.model.TemplateApplyTrigger
import sh.pcx.xinventories.internal.model.TemplateSettings

@DisplayName("TemplateSettings Tests")
class TemplateSettingsTest {

    @Nested
    @DisplayName("Default Values")
    inner class DefaultValuesTests {

        @Test
        @DisplayName("should have sensible defaults")
        fun defaults() {
            val settings = TemplateSettings()

            assertFalse(settings.enabled)
            Assertions.assertNull(settings.templateName)
            assertEquals(TemplateApplyTrigger.NONE, settings.applyOn)
            assertFalse(settings.allowReset)
            assertTrue(settings.clearInventoryFirst)
        }
    }

    @Nested
    @DisplayName("Configuration Options")
    inner class ConfigurationTests {

        @Test
        @DisplayName("should create enabled settings with JOIN trigger")
        fun enabledWithJoin() {
            val settings = TemplateSettings(
                enabled = true,
                templateName = "starter-kit",
                applyOn = TemplateApplyTrigger.JOIN,
                allowReset = true,
                clearInventoryFirst = true
            )

            assertTrue(settings.enabled)
            assertEquals("starter-kit", settings.templateName)
            assertEquals(TemplateApplyTrigger.JOIN, settings.applyOn)
            assertTrue(settings.allowReset)
        }

        @Test
        @DisplayName("should create settings with FIRST_JOIN trigger")
        fun firstJoinTrigger() {
            val settings = TemplateSettings(
                enabled = true,
                applyOn = TemplateApplyTrigger.FIRST_JOIN
            )

            assertEquals(TemplateApplyTrigger.FIRST_JOIN, settings.applyOn)
        }

        @Test
        @DisplayName("should create settings with MANUAL trigger")
        fun manualTrigger() {
            val settings = TemplateSettings(
                enabled = true,
                applyOn = TemplateApplyTrigger.MANUAL,
                allowReset = true
            )

            assertEquals(TemplateApplyTrigger.MANUAL, settings.applyOn)
            assertTrue(settings.allowReset)
        }

        @Test
        @DisplayName("should support not clearing inventory")
        fun noClearInventory() {
            val settings = TemplateSettings(
                enabled = true,
                applyOn = TemplateApplyTrigger.JOIN,
                clearInventoryFirst = false
            )

            assertFalse(settings.clearInventoryFirst)
        }
    }

    @Nested
    @DisplayName("Template Apply Trigger Enum")
    inner class TriggerEnumTests {

        @Test
        @DisplayName("should have all expected triggers")
        fun allTriggers() {
            val triggers = TemplateApplyTrigger.entries

            assertEquals(4, triggers.size)
            assertTrue(triggers.contains(TemplateApplyTrigger.JOIN))
            assertTrue(triggers.contains(TemplateApplyTrigger.FIRST_JOIN))
            assertTrue(triggers.contains(TemplateApplyTrigger.MANUAL))
            assertTrue(triggers.contains(TemplateApplyTrigger.NONE))
        }
    }

    @Nested
    @DisplayName("Data Class Behavior")
    inner class DataClassTests {

        @Test
        @DisplayName("should support copy")
        fun supportCopy() {
            val original = TemplateSettings(
                enabled = true,
                templateName = "original",
                applyOn = TemplateApplyTrigger.JOIN
            )

            val modified = original.copy(templateName = "modified")

            assertEquals("original", original.templateName)
            assertEquals("modified", modified.templateName)
            assertEquals(original.enabled, modified.enabled)
            assertEquals(original.applyOn, modified.applyOn)
        }

        @Test
        @DisplayName("should implement equals")
        fun implementsEquals() {
            val settings1 = TemplateSettings(enabled = true, templateName = "test")
            val settings2 = TemplateSettings(enabled = true, templateName = "test")
            val settings3 = TemplateSettings(enabled = true, templateName = "other")

            assertEquals(settings1, settings2)
            assertNotEquals(settings1, settings3)
        }

        @Test
        @DisplayName("should implement hashCode")
        fun implementsHashCode() {
            val settings1 = TemplateSettings(enabled = true, templateName = "test")
            val settings2 = TemplateSettings(enabled = true, templateName = "test")

            assertEquals(settings1.hashCode(), settings2.hashCode())
        }
    }
}

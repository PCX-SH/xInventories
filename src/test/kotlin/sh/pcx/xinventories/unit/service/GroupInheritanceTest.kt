package sh.pcx.xinventories.unit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.api.model.GroupSettings

@DisplayName("Group Settings Inheritance")
class GroupInheritanceTest {

    @Nested
    @DisplayName("GroupSettings.merge()")
    inner class GroupSettingsMergeTests {

        @Test
        @DisplayName("merge with null explicit returns child settings")
        fun mergeWithNullExplicitReturnsChild() {
            val parent = GroupSettings(
                saveHealth = true,
                saveHunger = true,
                saveExperience = true
            )

            val child = GroupSettings(
                saveHealth = false,
                saveHunger = false,
                saveExperience = false
            )

            val merged = parent.merge(child, null)

            // Child always wins when no explicit set is provided
            assertFalse(merged.saveHealth)
            assertFalse(merged.saveHunger)
            assertFalse(merged.saveExperience)
        }

        @Test
        @DisplayName("merge uses parent for non-explicit fields")
        fun mergeUsesParentForNonExplicit() {
            val parent = GroupSettings(
                saveHealth = true,
                saveHunger = true,
                saveExperience = true
            )

            val child = GroupSettings(
                saveHealth = false,
                saveHunger = true,
                saveExperience = false
            )

            // Only saveHealth was explicitly set in child
            val merged = parent.merge(child, setOf("saveHealth"))

            assertFalse(merged.saveHealth)  // Child explicit
            assertTrue(merged.saveHunger)    // Parent (not explicit in child)
            assertTrue(merged.saveExperience) // Parent (not explicit in child)
        }

        @Test
        @DisplayName("merge handles multiple explicit fields")
        fun mergeHandlesMultipleExplicitFields() {
            val parent = GroupSettings(
                saveHealth = true,
                saveHunger = true,
                savePotionEffects = true,
                saveEnderChest = true
            )

            val child = GroupSettings(
                saveHealth = false,
                saveHunger = false,
                savePotionEffects = true,
                saveEnderChest = false
            )

            val explicit = setOf("saveHealth", "saveHunger")
            val merged = parent.merge(child, explicit)

            assertFalse(merged.saveHealth)      // Child explicit
            assertFalse(merged.saveHunger)      // Child explicit
            assertTrue(merged.savePotionEffects) // Parent (not explicit)
            assertTrue(merged.saveEnderChest)   // Parent (not explicit)
        }

        @Test
        @DisplayName("mergeWith always returns child")
        fun mergeWithAlwaysReturnsChild() {
            val parent = GroupSettings(
                saveHealth = true,
                saveHunger = true,
                saveExperience = true
            )

            val child = GroupSettings(
                saveHealth = false,
                saveHunger = false,
                saveExperience = false
            )

            val merged = parent.mergeWith(child)

            assertEquals(child, merged)
        }
    }

    @Nested
    @DisplayName("GroupSettings constants")
    inner class GroupSettingsConstantsTests {

        @Test
        @DisplayName("DEFAULT has expected values")
        fun defaultHasExpectedValues() {
            val settings = GroupSettings.DEFAULT

            assertTrue(settings.saveHealth)
            assertTrue(settings.saveHunger)
            assertTrue(settings.saveExperience)
            assertTrue(settings.savePotionEffects)
            assertTrue(settings.saveEnderChest)
            assertTrue(settings.separateGameModeInventories)
            assertFalse(settings.clearOnDeath)
            assertFalse(settings.clearOnJoin)
            assertFalse(settings.saveStatistics)
            assertFalse(settings.saveAdvancements)
            assertFalse(settings.saveRecipes)
        }

        @Test
        @DisplayName("MINIMAL has all disabled")
        fun minimalHasAllDisabled() {
            val settings = GroupSettings.MINIMAL

            assertFalse(settings.saveHealth)
            assertFalse(settings.saveHunger)
            assertFalse(settings.saveExperience)
            assertFalse(settings.savePotionEffects)
            assertFalse(settings.saveEnderChest)
            assertFalse(settings.separateGameModeInventories)
            assertFalse(settings.clearOnDeath)
            assertFalse(settings.clearOnJoin)
            assertFalse(settings.saveStatistics)
            assertFalse(settings.saveAdvancements)
            assertFalse(settings.saveRecipes)
        }
    }

    @Nested
    @DisplayName("Inheritance chain scenarios")
    inner class InheritanceChainTests {

        @Test
        @DisplayName("two level inheritance works correctly")
        fun twoLevelInheritanceWorks() {
            // Base settings
            val base = GroupSettings(
                saveHealth = true,
                saveHunger = true,
                saveExperience = true,
                savePotionEffects = false
            )

            // Intermediate overrides savePotionEffects
            val intermediate = GroupSettings(
                saveHealth = true,
                saveHunger = true,
                saveExperience = true,
                savePotionEffects = true // Override
            )

            // Child overrides saveHealth
            val child = GroupSettings(
                saveHealth = false, // Override
                saveHunger = true,
                saveExperience = true,
                savePotionEffects = true
            )

            // Simulate resolution: base -> intermediate -> child
            val afterIntermediate = base.mergeWith(intermediate)
            val resolved = afterIntermediate.mergeWith(child)

            assertFalse(resolved.saveHealth)      // From child
            assertTrue(resolved.saveHunger)       // From base
            assertTrue(resolved.saveExperience)   // From base
            assertTrue(resolved.savePotionEffects) // From intermediate
        }

        @Test
        @DisplayName("child completely overrides parent")
        fun childCompletelyOverridesParent() {
            val parent = GroupSettings(
                saveHealth = true,
                saveHunger = true,
                saveExperience = true,
                savePotionEffects = true,
                saveEnderChest = true
            )

            val child = GroupSettings.MINIMAL

            val resolved = parent.mergeWith(child)

            assertEquals(child, resolved)
        }

        @Test
        @DisplayName("empty explicit set preserves all parent settings")
        fun emptyExplicitPreservesParent() {
            val parent = GroupSettings(
                saveHealth = true,
                saveHunger = true,
                saveExperience = true
            )

            val child = GroupSettings(
                saveHealth = false,
                saveHunger = false,
                saveExperience = false
            )

            // Empty set means nothing was explicitly set in child config
            val merged = parent.merge(child, emptySet())

            // All values should come from parent
            assertTrue(merged.saveHealth)
            assertTrue(merged.saveHunger)
            assertTrue(merged.saveExperience)
        }
    }
}

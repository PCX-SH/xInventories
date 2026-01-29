package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.WorldPattern

@DisplayName("Group")
class GroupTest {

    @Nested
    @DisplayName("Initialization")
    inner class Initialization {

        @Test
        @DisplayName("creates group with all properties")
        fun createsGroupWithAllProperties() {
            val settings = GroupSettings(
                saveHealth = false,
                saveHunger = true,
                clearOnDeath = true
            )
            val patterns = listOf(WorldPattern.fromStringOrThrow("survival_.*"))

            val group = Group(
                name = "survival",
                worlds = setOf("survival_world", "survival_nether"),
                patterns = patterns,
                priority = 10,
                parent = "default",
                settings = settings,
                isDefault = false
            )

            assertEquals("survival", group.name)
            assertEquals(setOf("survival_world", "survival_nether"), group.worlds)
            assertEquals(listOf("survival_.*"), group.patternStrings)
            assertEquals(10, group.priority)
            assertEquals("default", group.parent)
            assertEquals(settings, group.settings)
            assertFalse(group.isDefault)
        }

        @Test
        @DisplayName("creates group with default values")
        fun createsGroupWithDefaultValues() {
            val group = Group(name = "default")

            assertEquals("default", group.name)
            assertTrue(group.worlds.isEmpty())
            assertTrue(group.patterns.isEmpty())
            assertEquals(0, group.priority)
            assertNull(group.parent)
            assertEquals(GroupSettings(), group.settings)
            assertFalse(group.isDefault)
        }

        @Test
        @DisplayName("worlds collection is defensive copy")
        fun worldsCollectionIsDefensiveCopy() {
            val originalWorlds = mutableSetOf("world1", "world2")
            val group = Group(name = "test", worlds = originalWorlds)

            originalWorlds.add("world3")

            assertEquals(2, group.worlds.size)
            assertFalse(group.worlds.contains("world3"))
        }

        @Test
        @DisplayName("patterns list is defensive copy")
        fun patternsListIsDefensiveCopy() {
            val originalPatterns = mutableListOf(WorldPattern.fromStringOrThrow("world.*"))
            val group = Group(name = "test", patterns = originalPatterns)

            originalPatterns.add(WorldPattern.fromStringOrThrow("survival.*"))

            assertEquals(1, group.patterns.size)
        }
    }

    @Nested
    @DisplayName("Add/Remove Worlds")
    inner class AddRemoveWorlds {

        private lateinit var group: Group

        @BeforeEach
        fun setUp() {
            group = Group(name = "test", worlds = setOf("world"))
        }

        @Test
        @DisplayName("addWorld adds new world")
        fun addWorldAddsNewWorld() {
            val result = group.addWorld("survival")

            assertTrue(result)
            assertTrue(group.worlds.contains("survival"))
            assertEquals(2, group.worlds.size)
        }

        @Test
        @DisplayName("addWorld returns false for duplicate")
        fun addWorldReturnsFalseForDuplicate() {
            val result = group.addWorld("world")

            assertFalse(result)
            assertEquals(1, group.worlds.size)
        }

        @Test
        @DisplayName("removeWorld removes existing world")
        fun removeWorldRemovesExistingWorld() {
            val result = group.removeWorld("world")

            assertTrue(result)
            assertFalse(group.worlds.contains("world"))
            assertTrue(group.worlds.isEmpty())
        }

        @Test
        @DisplayName("removeWorld returns false for non-existent world")
        fun removeWorldReturnsFalseForNonExistentWorld() {
            val result = group.removeWorld("nonexistent")

            assertFalse(result)
            assertEquals(1, group.worlds.size)
        }

        @Test
        @DisplayName("setWorlds replaces all worlds")
        fun setWorldsReplacesAllWorlds() {
            group.setWorlds(setOf("new_world1", "new_world2"))

            assertEquals(2, group.worlds.size)
            assertTrue(group.worlds.contains("new_world1"))
            assertTrue(group.worlds.contains("new_world2"))
            assertFalse(group.worlds.contains("world"))
        }

        @Test
        @DisplayName("setWorlds with empty set clears worlds")
        fun setWorldsWithEmptySetClearsWorlds() {
            group.setWorlds(emptySet())

            assertTrue(group.worlds.isEmpty())
        }

        @Test
        @DisplayName("worlds property returns immutable copy")
        fun worldsPropertyReturnsImmutableCopy() {
            val worlds = group.worlds

            // The returned set should be a copy
            group.addWorld("new_world")

            assertFalse(worlds.contains("new_world"))
        }
    }

    @Nested
    @DisplayName("Pattern Management")
    inner class PatternManagement {

        private lateinit var group: Group

        @BeforeEach
        fun setUp() {
            group = Group(name = "test")
        }

        @Test
        @DisplayName("addPattern adds valid pattern")
        fun addPatternAddsValidPattern() {
            val result = group.addPattern("world.*")

            assertTrue(result)
            assertEquals(1, group.patterns.size)
            assertEquals("world.*", group.patternStrings[0])
        }

        @Test
        @DisplayName("addPattern returns false for invalid pattern")
        fun addPatternReturnsFalseForInvalidPattern() {
            val result = group.addPattern("[invalid")

            assertFalse(result)
            assertTrue(group.patterns.isEmpty())
        }

        @Test
        @DisplayName("addPattern returns false for duplicate pattern")
        fun addPatternReturnsFalseForDuplicatePattern() {
            group.addPattern("world.*")
            val result = group.addPattern("world.*")

            assertFalse(result)
            assertEquals(1, group.patterns.size)
        }

        @Test
        @DisplayName("removePattern removes existing pattern")
        fun removePatternRemovesExistingPattern() {
            group.addPattern("world.*")
            val result = group.removePattern("world.*")

            assertTrue(result)
            assertTrue(group.patterns.isEmpty())
        }

        @Test
        @DisplayName("removePattern returns false for non-existent pattern")
        fun removePatternReturnsFalseForNonExistentPattern() {
            group.addPattern("world.*")
            val result = group.removePattern("survival.*")

            assertFalse(result)
            assertEquals(1, group.patterns.size)
        }

        @Test
        @DisplayName("setPatterns replaces all patterns")
        fun setPatternsReplacesAllPatterns() {
            group.addPattern("world.*")
            val result = group.setPatterns(listOf("survival.*", "creative.*"))

            assertTrue(result)
            assertEquals(2, group.patterns.size)
            assertEquals(listOf("survival.*", "creative.*"), group.patternStrings)
        }

        @Test
        @DisplayName("setPatterns returns false if any pattern invalid")
        fun setPatternsReturnsFalseIfAnyPatternInvalid() {
            group.addPattern("world.*")
            val result = group.setPatterns(listOf("survival.*", "[invalid"))

            assertFalse(result)
            // Original patterns should remain
            assertEquals(listOf("world.*"), group.patternStrings)
        }

        @Test
        @DisplayName("setPatterns with empty list clears patterns")
        fun setPatternsWithEmptyListClearsPatterns() {
            group.addPattern("world.*")
            val result = group.setPatterns(emptyList())

            assertTrue(result)
            assertTrue(group.patterns.isEmpty())
        }

        @Test
        @DisplayName("patterns property returns immutable copy")
        fun patternsPropertyReturnsImmutableCopy() {
            group.addPattern("world.*")
            val patterns = group.patterns

            group.addPattern("survival.*")

            assertEquals(1, patterns.size)
        }

        @Test
        @DisplayName("patternStrings returns pattern string representations")
        fun patternStringsReturnsPatternStringRepresentations() {
            group.addPattern("^world$")
            group.addPattern("survival_.*")

            assertEquals(listOf("^world$", "survival_.*"), group.patternStrings)
        }
    }

    @Nested
    @DisplayName("World Containment Checks")
    inner class WorldContainmentChecks {

        @Test
        @DisplayName("containsWorld returns true for explicit world")
        fun containsWorldReturnsTrueForExplicitWorld() {
            val group = Group(name = "test", worlds = setOf("world", "survival"))

            assertTrue(group.containsWorld("world"))
            assertTrue(group.containsWorld("survival"))
        }

        @Test
        @DisplayName("containsWorld returns true for pattern match")
        fun containsWorldReturnsTrueForPatternMatch() {
            val group = Group(
                name = "test",
                patterns = listOf(WorldPattern.fromStringOrThrow("survival_.*"))
            )

            assertTrue(group.containsWorld("survival_world"))
            assertTrue(group.containsWorld("survival_nether"))
        }

        @Test
        @DisplayName("containsWorld returns false for non-matching world")
        fun containsWorldReturnsFalseForNonMatchingWorld() {
            val group = Group(
                name = "test",
                worlds = setOf("world"),
                patterns = listOf(WorldPattern.fromStringOrThrow("survival_.*"))
            )

            assertFalse(group.containsWorld("creative"))
            assertFalse(group.containsWorld("nonexistent"))
        }

        @Test
        @DisplayName("containsWorld prefers explicit over pattern")
        fun containsWorldPrefersExplicitOverPattern() {
            val group = Group(
                name = "test",
                worlds = setOf("survival_world"),
                patterns = listOf(WorldPattern.fromStringOrThrow("survival_.*"))
            )

            // Both should match, but the explicit check happens first
            assertTrue(group.containsWorld("survival_world"))
        }

        @Test
        @DisplayName("matchesPattern returns true only for pattern matches")
        fun matchesPatternReturnsTrueOnlyForPatternMatches() {
            val group = Group(
                name = "test",
                worlds = setOf("survival_world"),
                patterns = listOf(WorldPattern.fromStringOrThrow("survival_.*"))
            )

            // survival_world is explicit, not a pattern match
            assertTrue(group.matchesPattern("survival_world")) // Pattern also matches
            assertTrue(group.matchesPattern("survival_nether"))
        }

        @Test
        @DisplayName("matchesPattern returns false for explicit-only world")
        fun matchesPatternReturnsFalseForExplicitOnlyWorld() {
            val group = Group(
                name = "test",
                worlds = setOf("world"),
                patterns = listOf(WorldPattern.fromStringOrThrow("survival_.*"))
            )

            assertFalse(group.matchesPattern("world"))
        }

        @Test
        @DisplayName("matchesPattern returns false when no patterns match")
        fun matchesPatternReturnsFalseWhenNoPatternsMatch() {
            val group = Group(
                name = "test",
                patterns = listOf(WorldPattern.fromStringOrThrow("survival_.*"))
            )

            assertFalse(group.matchesPattern("creative_world"))
        }

        @Test
        @DisplayName("containsWorld with multiple patterns")
        fun containsWorldWithMultiplePatterns() {
            val group = Group(
                name = "test",
                patterns = listOf(
                    WorldPattern.fromStringOrThrow("survival_.*"),
                    WorldPattern.fromStringOrThrow("creative_.*")
                )
            )

            assertTrue(group.containsWorld("survival_world"))
            assertTrue(group.containsWorld("creative_world"))
            assertFalse(group.containsWorld("adventure_world"))
        }
    }

    @Nested
    @DisplayName("Priority Comparison")
    inner class PriorityComparison {

        @Test
        @DisplayName("priority can be read and modified")
        fun priorityCanBeReadAndModified() {
            val group = Group(name = "test", priority = 5)

            assertEquals(5, group.priority)

            group.priority = 10

            assertEquals(10, group.priority)
        }

        @Test
        @DisplayName("higher priority value means higher priority")
        fun higherPriorityValueMeansHigherPriority() {
            val lowPriority = Group(name = "low", priority = 1)
            val highPriority = Group(name = "high", priority = 10)

            assertTrue(highPriority.priority > lowPriority.priority)
        }

        @Test
        @DisplayName("groups can be sorted by priority")
        fun groupsCanBeSortedByPriority() {
            val groups = listOf(
                Group(name = "medium", priority = 5),
                Group(name = "low", priority = 1),
                Group(name = "high", priority = 10)
            )

            val sorted = groups.sortedByDescending { it.priority }

            assertEquals("high", sorted[0].name)
            assertEquals("medium", sorted[1].name)
            assertEquals("low", sorted[2].name)
        }

        @Test
        @DisplayName("negative priority is allowed")
        fun negativePriorityIsAllowed() {
            val group = Group(name = "test", priority = -5)

            assertEquals(-5, group.priority)
        }
    }

    @Nested
    @DisplayName("Group Settings Access")
    inner class GroupSettingsAccess {

        @Test
        @DisplayName("settings can be read")
        fun settingsCanBeRead() {
            val settings = GroupSettings(
                saveHealth = false,
                clearOnDeath = true
            )
            val group = Group(name = "test", settings = settings)

            assertEquals(settings, group.settings)
            assertFalse(group.settings.saveHealth)
            assertTrue(group.settings.clearOnDeath)
        }

        @Test
        @DisplayName("settings can be modified")
        fun settingsCanBeModified() {
            val group = Group(name = "test")

            val newSettings = GroupSettings(saveExperience = false)
            group.settings = newSettings

            assertEquals(newSettings, group.settings)
            assertFalse(group.settings.saveExperience)
        }

        @Test
        @DisplayName("default settings are applied")
        fun defaultSettingsAreApplied() {
            val group = Group(name = "test")

            val defaultSettings = GroupSettings()
            assertEquals(defaultSettings, group.settings)
            assertTrue(group.settings.saveHealth)
            assertTrue(group.settings.saveHunger)
            assertTrue(group.settings.saveExperience)
            assertFalse(group.settings.saveGameMode)
        }
    }

    @Nested
    @DisplayName("Default Group Flag")
    inner class DefaultGroupFlag {

        @Test
        @DisplayName("isDefault defaults to false")
        fun isDefaultDefaultsToFalse() {
            val group = Group(name = "test")

            assertFalse(group.isDefault)
        }

        @Test
        @DisplayName("isDefault can be set to true")
        fun isDefaultCanBeSetToTrue() {
            val group = Group(name = "default", isDefault = true)

            assertTrue(group.isDefault)
        }

        @Test
        @DisplayName("isDefault can be modified")
        fun isDefaultCanBeModified() {
            val group = Group(name = "test", isDefault = false)

            group.isDefault = true

            assertTrue(group.isDefault)
        }
    }

    @Nested
    @DisplayName("Parent Group Handling")
    inner class ParentGroupHandling {

        @Test
        @DisplayName("parent defaults to null")
        fun parentDefaultsToNull() {
            val group = Group(name = "test")

            assertNull(group.parent)
        }

        @Test
        @DisplayName("parent can be set")
        fun parentCanBeSet() {
            val group = Group(name = "child", parent = "parent")

            assertEquals("parent", group.parent)
        }

        @Test
        @DisplayName("parent can be modified")
        fun parentCanBeModified() {
            val group = Group(name = "test", parent = "old_parent")

            group.parent = "new_parent"

            assertEquals("new_parent", group.parent)
        }

        @Test
        @DisplayName("parent can be set to null")
        fun parentCanBeSetToNull() {
            val group = Group(name = "test", parent = "parent")

            group.parent = null

            assertNull(group.parent)
        }
    }

    @Nested
    @DisplayName("API Model Conversion")
    inner class ApiModelConversion {

        @Test
        @DisplayName("toApiModel creates correct InventoryGroup")
        fun toApiModelCreatesCorrectInventoryGroup() {
            val settings = GroupSettings(saveHealth = false)
            val group = Group(
                name = "survival",
                worlds = setOf("survival_world", "survival_nether"),
                patterns = listOf(WorldPattern.fromStringOrThrow("survival_.*")),
                priority = 10,
                parent = "default",
                settings = settings,
                isDefault = false
            )

            val apiModel = group.toApiModel()

            assertEquals("survival", apiModel.name)
            assertEquals(setOf("survival_world", "survival_nether"), apiModel.worlds)
            assertEquals(listOf("survival_.*"), apiModel.patterns)
            assertEquals(10, apiModel.priority)
            assertEquals("default", apiModel.parent)
            assertEquals(settings, apiModel.settings)
            assertFalse(apiModel.isDefault)
        }

        @Test
        @DisplayName("toApiModel with default values")
        fun toApiModelWithDefaultValues() {
            val group = Group(name = "default")

            val apiModel = group.toApiModel()

            assertEquals("default", apiModel.name)
            assertTrue(apiModel.worlds.isEmpty())
            assertTrue(apiModel.patterns.isEmpty())
            assertEquals(0, apiModel.priority)
            assertNull(apiModel.parent)
            assertEquals(GroupSettings(), apiModel.settings)
            assertFalse(apiModel.isDefault)
        }

        @Test
        @DisplayName("fromApiModel creates correct Group")
        fun fromApiModelCreatesCorrectGroup() {
            val settings = GroupSettings(saveHunger = false)
            val apiModel = InventoryGroup(
                name = "creative",
                worlds = setOf("creative_world"),
                patterns = listOf("creative_.*"),
                priority = 5,
                parent = "default",
                settings = settings,
                isDefault = true
            )

            val group = Group.fromApiModel(apiModel)

            assertEquals("creative", group.name)
            assertEquals(setOf("creative_world"), group.worlds)
            assertEquals(listOf("creative_.*"), group.patternStrings)
            assertEquals(5, group.priority)
            assertEquals("default", group.parent)
            assertEquals(settings, group.settings)
            assertTrue(group.isDefault)
        }

        @Test
        @DisplayName("fromApiModel skips invalid patterns")
        fun fromApiModelSkipsInvalidPatterns() {
            val apiModel = InventoryGroup(
                name = "test",
                worlds = emptySet(),
                patterns = listOf("valid.*", "[invalid", "another_valid"),
                priority = 0,
                parent = null,
                settings = GroupSettings(),
                isDefault = false
            )

            val group = Group.fromApiModel(apiModel)

            // Only valid patterns should be included
            assertEquals(2, group.patterns.size)
            assertEquals(listOf("valid.*", "another_valid"), group.patternStrings)
        }

        @Test
        @DisplayName("round trip conversion preserves data")
        fun roundTripConversionPreservesData() {
            val originalGroup = Group(
                name = "test",
                worlds = setOf("world1", "world2"),
                patterns = listOf(WorldPattern.fromStringOrThrow("pattern_.*")),
                priority = 7,
                parent = "parent_group",
                settings = GroupSettings(saveEnderChest = false),
                isDefault = true
            )

            val apiModel = originalGroup.toApiModel()
            val restoredGroup = Group.fromApiModel(apiModel)

            assertEquals(originalGroup.name, restoredGroup.name)
            assertEquals(originalGroup.worlds, restoredGroup.worlds)
            assertEquals(originalGroup.patternStrings, restoredGroup.patternStrings)
            assertEquals(originalGroup.priority, restoredGroup.priority)
            assertEquals(originalGroup.parent, restoredGroup.parent)
            assertEquals(originalGroup.settings, restoredGroup.settings)
            assertEquals(originalGroup.isDefault, restoredGroup.isDefault)
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("empty group name is allowed")
        fun emptyGroupNameIsAllowed() {
            val group = Group(name = "")

            assertEquals("", group.name)
        }

        @Test
        @DisplayName("special characters in world names")
        fun specialCharactersInWorldNames() {
            val group = Group(name = "test")

            group.addWorld("world-1")
            group.addWorld("world_2")
            group.addWorld("world.3")

            assertTrue(group.containsWorld("world-1"))
            assertTrue(group.containsWorld("world_2"))
            assertTrue(group.containsWorld("world.3"))
        }

        @Test
        @DisplayName("very long world names")
        fun veryLongWorldNames() {
            val longWorldName = "a".repeat(1000)
            val group = Group(name = "test")

            group.addWorld(longWorldName)

            assertTrue(group.containsWorld(longWorldName))
        }

        @Test
        @DisplayName("unicode in world names")
        fun unicodeInWorldNames() {
            val group = Group(name = "test")

            group.addWorld("world_\u4e16\u754c")

            assertTrue(group.containsWorld("world_\u4e16\u754c"))
        }

        @Test
        @DisplayName("concurrent modification behavior")
        fun concurrentModificationBehavior() {
            val group = Group(name = "test", worlds = setOf("world1", "world2", "world3"))

            // Get a snapshot of worlds
            val snapshot = group.worlds

            // Modify the group
            group.removeWorld("world2")

            // Snapshot should be unchanged
            assertEquals(3, snapshot.size)
            assertEquals(2, group.worlds.size)
        }
    }
}

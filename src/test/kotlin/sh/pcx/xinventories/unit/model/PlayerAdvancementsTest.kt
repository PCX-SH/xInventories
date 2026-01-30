package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.model.PlayerAdvancements

@DisplayName("PlayerAdvancements Tests")
class PlayerAdvancementsTest {

    // ============================================================
    // Creation Tests
    // ============================================================

    @Nested
    @DisplayName("Create PlayerAdvancements")
    inner class CreateTests {

        @Test
        @DisplayName("should create empty PlayerAdvancements with default constructor")
        fun createEmptyDefault() {
            val advancements = PlayerAdvancements()

            assertTrue(advancements.isEmpty())
            assertEquals(0, advancements.size)
        }

        @Test
        @DisplayName("should create empty PlayerAdvancements with empty() companion")
        fun createEmptyCompanion() {
            val advancements = PlayerAdvancements.empty()

            assertTrue(advancements.isEmpty())
            assertEquals(0, advancements.size)
        }

        @Test
        @DisplayName("should create PlayerAdvancements with initial data")
        fun createWithData() {
            val data = setOf(
                "minecraft:story/mine_stone",
                "minecraft:nether/return_to_sender"
            )
            val advancements = PlayerAdvancements(data)

            assertFalse(advancements.isEmpty())
            assertEquals(2, advancements.size)
            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
            assertTrue(advancements.isCompleted("minecraft:nether/return_to_sender"))
        }
    }

    // ============================================================
    // isCompleted Tests
    // ============================================================

    @Nested
    @DisplayName("isCompleted Operations")
    inner class IsCompletedTests {

        @Test
        @DisplayName("should return true for completed advancement")
        fun completedReturnsTrue() {
            val advancements = PlayerAdvancements(setOf("minecraft:story/mine_stone"))

            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
        }

        @Test
        @DisplayName("should return false for uncompleted advancement")
        fun uncompletedReturnsFalse() {
            val advancements = PlayerAdvancements(setOf("minecraft:story/mine_stone"))

            assertFalse(advancements.isCompleted("minecraft:story/smelt_iron"))
        }

        @Test
        @DisplayName("should return false for empty advancements")
        fun emptyReturnsFalse() {
            val advancements = PlayerAdvancements.empty()

            assertFalse(advancements.isCompleted("minecraft:story/mine_stone"))
        }
    }

    // ============================================================
    // withCompleted Tests
    // ============================================================

    @Nested
    @DisplayName("withCompleted Operations")
    inner class WithCompletedTests {

        @Test
        @DisplayName("should add new advancement")
        fun addNewAdvancement() {
            val original = PlayerAdvancements.empty()
            val updated = original.withCompleted("minecraft:story/mine_stone")

            assertEquals(0, original.size) // Original unchanged
            assertEquals(1, updated.size)
            assertTrue(updated.isCompleted("minecraft:story/mine_stone"))
        }

        @Test
        @DisplayName("should not duplicate existing advancement")
        fun noDuplicateAdvancement() {
            val original = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val updated = original.withCompleted("minecraft:story/mine_stone")

            assertEquals(1, original.size)
            assertEquals(1, updated.size)
        }

        @Test
        @DisplayName("should preserve other advancements when adding")
        fun preserveOtherAdvancements() {
            val original = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron"
            ))
            val updated = original.withCompleted("minecraft:nether/return_to_sender")

            assertEquals(3, updated.size)
            assertTrue(updated.isCompleted("minecraft:story/mine_stone"))
            assertTrue(updated.isCompleted("minecraft:story/smelt_iron"))
            assertTrue(updated.isCompleted("minecraft:nether/return_to_sender"))
        }
    }

    // ============================================================
    // withAllCompleted Tests
    // ============================================================

    @Nested
    @DisplayName("withAllCompleted Operations")
    inner class WithAllCompletedTests {

        @Test
        @DisplayName("should add multiple advancements")
        fun addMultipleAdvancements() {
            val original = PlayerAdvancements.empty()
            val toAdd = setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron",
                "minecraft:nether/return_to_sender"
            )
            val updated = original.withAllCompleted(toAdd)

            assertEquals(0, original.size) // Original unchanged
            assertEquals(3, updated.size)
            assertTrue(updated.isCompleted("minecraft:story/mine_stone"))
            assertTrue(updated.isCompleted("minecraft:story/smelt_iron"))
            assertTrue(updated.isCompleted("minecraft:nether/return_to_sender"))
        }

        @Test
        @DisplayName("should handle empty set")
        fun handleEmptySet() {
            val original = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val updated = original.withAllCompleted(emptySet())

            assertEquals(original, updated)
        }

        @Test
        @DisplayName("should merge without duplicates")
        fun mergeWithoutDuplicates() {
            val original = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val toAdd = setOf(
                "minecraft:story/mine_stone",  // Already exists
                "minecraft:story/smelt_iron"   // New
            )
            val updated = original.withAllCompleted(toAdd)

            assertEquals(2, updated.size)
        }
    }

    // ============================================================
    // withoutCompleted Tests
    // ============================================================

    @Nested
    @DisplayName("withoutCompleted Operations")
    inner class WithoutCompletedTests {

        @Test
        @DisplayName("should remove existing advancement")
        fun removeExistingAdvancement() {
            val original = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron"
            ))
            val updated = original.withoutCompleted("minecraft:story/mine_stone")

            assertEquals(2, original.size) // Original unchanged
            assertEquals(1, updated.size)
            assertFalse(updated.isCompleted("minecraft:story/mine_stone"))
            assertTrue(updated.isCompleted("minecraft:story/smelt_iron"))
        }

        @Test
        @DisplayName("should handle removing non-existent advancement")
        fun removeNonExistent() {
            val original = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val updated = original.withoutCompleted("minecraft:story/smelt_iron")

            assertEquals(original.size, updated.size)
        }

        @Test
        @DisplayName("should handle removing from empty")
        fun removeFromEmpty() {
            val original = PlayerAdvancements.empty()
            val updated = original.withoutCompleted("minecraft:story/mine_stone")

            assertTrue(updated.isEmpty())
        }
    }

    // ============================================================
    // isEmpty/isNotEmpty Tests
    // ============================================================

    @Nested
    @DisplayName("Empty Check Operations")
    inner class EmptyCheckTests {

        @Test
        @DisplayName("should return true for empty advancements")
        fun isEmptyTrue() {
            val advancements = PlayerAdvancements.empty()

            assertTrue(advancements.isEmpty())
            assertFalse(advancements.isNotEmpty())
        }

        @Test
        @DisplayName("should return false for non-empty advancements")
        fun isEmptyFalse() {
            val advancements = PlayerAdvancements(setOf("minecraft:story/mine_stone"))

            assertFalse(advancements.isEmpty())
            assertTrue(advancements.isNotEmpty())
        }
    }

    // ============================================================
    // Size Tests
    // ============================================================

    @Nested
    @DisplayName("Size Operations")
    inner class SizeTests {

        @Test
        @DisplayName("should return 0 for empty advancements")
        fun sizeZero() {
            val advancements = PlayerAdvancements.empty()

            assertEquals(0, advancements.size)
        }

        @Test
        @DisplayName("should return correct count")
        fun sizeCorrectCount() {
            val advancements = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron",
                "minecraft:nether/return_to_sender"
            ))

            assertEquals(3, advancements.size)
        }
    }

    // ============================================================
    // Advancement Key Format Tests
    // ============================================================

    @Nested
    @DisplayName("Advancement Key Format")
    inner class KeyFormatTests {

        @Test
        @DisplayName("should handle minecraft namespace keys")
        fun minecraftNamespaceKeys() {
            val advancements = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/upgrade_tools",
                "minecraft:nether/return_to_sender",
                "minecraft:adventure/kill_a_mob"
            ))

            assertEquals(4, advancements.size)
            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
            assertTrue(advancements.isCompleted("minecraft:adventure/kill_a_mob"))
        }

        @Test
        @DisplayName("should handle custom namespace keys")
        fun customNamespaceKeys() {
            val advancements = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "customplugin:custom_advancement",
                "myplugin:quests/first_quest"
            ))

            assertEquals(3, advancements.size)
            assertTrue(advancements.isCompleted("customplugin:custom_advancement"))
            assertTrue(advancements.isCompleted("myplugin:quests/first_quest"))
        }

        @Test
        @DisplayName("should handle deeply nested paths")
        fun deeplyNestedPaths() {
            val advancements = PlayerAdvancements(setOf(
                "minecraft:husbandry/breed_all_animals",
                "minecraft:adventure/adventuring_time"
            ))

            assertTrue(advancements.isCompleted("minecraft:husbandry/breed_all_animals"))
            assertTrue(advancements.isCompleted("minecraft:adventure/adventuring_time"))
        }
    }

    // ============================================================
    // Data Class Behavior Tests
    // ============================================================

    @Nested
    @DisplayName("Data Class Behavior")
    inner class DataClassTests {

        @Test
        @DisplayName("should be equal when contents match")
        fun equalsWhenMatching() {
            val adv1 = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val adv2 = PlayerAdvancements(setOf("minecraft:story/mine_stone"))

            assertEquals(adv1, adv2)
            assertEquals(adv1.hashCode(), adv2.hashCode())
        }

        @Test
        @DisplayName("should not be equal when contents differ")
        fun notEqualsWhenDifferent() {
            val adv1 = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val adv2 = PlayerAdvancements(setOf("minecraft:story/smelt_iron"))

            assertNotEquals(adv1, adv2)
        }

        @Test
        @DisplayName("should copy correctly")
        fun copyCorrectly() {
            val original = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val copied = original.copy(completedAdvancements = setOf("minecraft:story/smelt_iron"))

            assertTrue(original.isCompleted("minecraft:story/mine_stone"))
            assertFalse(original.isCompleted("minecraft:story/smelt_iron"))
            assertTrue(copied.isCompleted("minecraft:story/smelt_iron"))
            assertFalse(copied.isCompleted("minecraft:story/mine_stone"))
        }

        @Test
        @DisplayName("should provide meaningful toString")
        fun toStringMeaningful() {
            val advancements = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val string = advancements.toString()

            assertTrue(string.contains("minecraft:story/mine_stone"))
        }
    }

    // ============================================================
    // Immutability Tests
    // ============================================================

    @Nested
    @DisplayName("Immutability")
    inner class ImmutabilityTests {

        @Test
        @DisplayName("withCompleted() should return new instance")
        fun withCompletedReturnsNewInstance() {
            val original = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val updated = original.withCompleted("minecraft:story/smelt_iron")

            assertNotSame(original, updated)
            assertEquals(1, original.size)
            assertEquals(2, updated.size)
        }

        @Test
        @DisplayName("withAllCompleted() should return new instance")
        fun withAllCompletedReturnsNewInstance() {
            val original = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val updated = original.withAllCompleted(setOf("minecraft:story/smelt_iron"))

            assertNotSame(original, updated)
            assertEquals(1, original.size)
            assertEquals(2, updated.size)
        }

        @Test
        @DisplayName("withoutCompleted() should return new instance")
        fun withoutCompletedReturnsNewInstance() {
            val original = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron"
            ))
            val updated = original.withoutCompleted("minecraft:story/mine_stone")

            assertNotSame(original, updated)
            assertEquals(2, original.size)
            assertEquals(1, updated.size)
        }

        @Test
        @DisplayName("should not share state between instances created via withCompleted()")
        fun noSharedStateBetweenInstances() {
            val original = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val updated = original.withCompleted("minecraft:story/smelt_iron")

            // Modifying one should not affect the other
            val furtherUpdated = updated.withCompleted("minecraft:nether/return_to_sender")

            assertEquals(1, original.size)
            assertEquals(2, updated.size)
            assertEquals(3, furtherUpdated.size)
        }
    }
}

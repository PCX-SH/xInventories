package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.model.PlayerStatistics

@DisplayName("PlayerStatistics Tests")
class PlayerStatisticsTest {

    // ============================================================
    // Creation Tests
    // ============================================================

    @Nested
    @DisplayName("Create PlayerStatistics")
    inner class CreateTests {

        @Test
        @DisplayName("should create empty PlayerStatistics with default constructor")
        fun createEmptyDefault() {
            val stats = PlayerStatistics()

            assertTrue(stats.isEmpty())
            assertEquals(0, stats.size)
        }

        @Test
        @DisplayName("should create empty PlayerStatistics with empty() companion")
        fun createEmptyCompanion() {
            val stats = PlayerStatistics.empty()

            assertTrue(stats.isEmpty())
            assertEquals(0, stats.size)
        }

        @Test
        @DisplayName("should create PlayerStatistics with initial data")
        fun createWithData() {
            val data = mapOf(
                "DEATHS" to 5,
                "PLAY_ONE_MINUTE" to 72000
            )
            val stats = PlayerStatistics(data)

            assertFalse(stats.isEmpty())
            assertEquals(2, stats.size)
            assertEquals(5, stats.get("DEATHS"))
            assertEquals(72000, stats.get("PLAY_ONE_MINUTE"))
        }
    }

    // ============================================================
    // Get Operation Tests
    // ============================================================

    @Nested
    @DisplayName("Get Operations")
    inner class GetTests {

        @Test
        @DisplayName("should return value for existing statistic")
        fun getExisting() {
            val stats = PlayerStatistics(mapOf("DEATHS" to 10))

            assertEquals(10, stats.get("DEATHS"))
        }

        @Test
        @DisplayName("should return 0 for non-existing statistic")
        fun getNonExisting() {
            val stats = PlayerStatistics(mapOf("DEATHS" to 10))

            assertEquals(0, stats.get("NON_EXISTENT"))
        }

        @Test
        @DisplayName("should return 0 for empty statistics")
        fun getFromEmpty() {
            val stats = PlayerStatistics.empty()

            assertEquals(0, stats.get("DEATHS"))
        }
    }

    // ============================================================
    // With Operation Tests
    // ============================================================

    @Nested
    @DisplayName("With Operations")
    inner class WithTests {

        @Test
        @DisplayName("should add new statistic")
        fun addNewStatistic() {
            val original = PlayerStatistics.empty()
            val updated = original.with("DEATHS", 5)

            assertEquals(0, original.size) // Original unchanged
            assertEquals(1, updated.size)
            assertEquals(5, updated.get("DEATHS"))
        }

        @Test
        @DisplayName("should update existing statistic")
        fun updateExistingStatistic() {
            val original = PlayerStatistics(mapOf("DEATHS" to 5))
            val updated = original.with("DEATHS", 10)

            assertEquals(5, original.get("DEATHS")) // Original unchanged
            assertEquals(10, updated.get("DEATHS"))
        }

        @Test
        @DisplayName("should preserve other statistics when adding")
        fun preserveOtherStatistics() {
            val original = PlayerStatistics(mapOf(
                "DEATHS" to 5,
                "PLAY_ONE_MINUTE" to 72000
            ))
            val updated = original.with("JUMP", 100)

            assertEquals(3, updated.size)
            assertEquals(5, updated.get("DEATHS"))
            assertEquals(72000, updated.get("PLAY_ONE_MINUTE"))
            assertEquals(100, updated.get("JUMP"))
        }
    }

    // ============================================================
    // WithAll Operation Tests
    // ============================================================

    @Nested
    @DisplayName("WithAll Operations")
    inner class WithAllTests {

        @Test
        @DisplayName("should add multiple statistics")
        fun addMultipleStatistics() {
            val original = PlayerStatistics.empty()
            val updates = mapOf(
                "DEATHS" to 5,
                "JUMP" to 100,
                "WALK_ONE_CM" to 50000
            )
            val updated = original.withAll(updates)

            assertEquals(0, original.size) // Original unchanged
            assertEquals(3, updated.size)
            assertEquals(5, updated.get("DEATHS"))
            assertEquals(100, updated.get("JUMP"))
            assertEquals(50000, updated.get("WALK_ONE_CM"))
        }

        @Test
        @DisplayName("should update multiple existing statistics")
        fun updateMultipleStatistics() {
            val original = PlayerStatistics(mapOf(
                "DEATHS" to 5,
                "JUMP" to 50
            ))
            val updates = mapOf(
                "DEATHS" to 10,
                "JUMP" to 100
            )
            val updated = original.withAll(updates)

            assertEquals(5, original.get("DEATHS")) // Original unchanged
            assertEquals(10, updated.get("DEATHS"))
            assertEquals(100, updated.get("JUMP"))
        }

        @Test
        @DisplayName("should handle empty update map")
        fun handleEmptyUpdate() {
            val original = PlayerStatistics(mapOf("DEATHS" to 5))
            val updated = original.withAll(emptyMap())

            assertEquals(original, updated)
        }
    }

    // ============================================================
    // isEmpty/isNotEmpty Tests
    // ============================================================

    @Nested
    @DisplayName("Empty Check Operations")
    inner class EmptyCheckTests {

        @Test
        @DisplayName("should return true for empty statistics")
        fun isEmptyTrue() {
            val stats = PlayerStatistics.empty()

            assertTrue(stats.isEmpty())
            assertFalse(stats.isNotEmpty())
        }

        @Test
        @DisplayName("should return false for non-empty statistics")
        fun isEmptyFalse() {
            val stats = PlayerStatistics(mapOf("DEATHS" to 1))

            assertFalse(stats.isEmpty())
            assertTrue(stats.isNotEmpty())
        }
    }

    // ============================================================
    // Size Tests
    // ============================================================

    @Nested
    @DisplayName("Size Operations")
    inner class SizeTests {

        @Test
        @DisplayName("should return 0 for empty statistics")
        fun sizeZero() {
            val stats = PlayerStatistics.empty()

            assertEquals(0, stats.size)
        }

        @Test
        @DisplayName("should return correct count")
        fun sizeCorrectCount() {
            val stats = PlayerStatistics(mapOf(
                "DEATHS" to 5,
                "JUMP" to 100,
                "MINE_BLOCK:DIAMOND_ORE" to 10
            ))

            assertEquals(3, stats.size)
        }
    }

    // ============================================================
    // Statistic Key Format Tests
    // ============================================================

    @Nested
    @DisplayName("Statistic Key Format")
    inner class KeyFormatTests {

        @Test
        @DisplayName("should handle UNTYPED statistic keys")
        fun untypedKeys() {
            val stats = PlayerStatistics(mapOf(
                "DEATHS" to 5,
                "PLAY_ONE_MINUTE" to 72000,
                "JUMP" to 100
            ))

            assertEquals(5, stats.get("DEATHS"))
            assertEquals(72000, stats.get("PLAY_ONE_MINUTE"))
            assertEquals(100, stats.get("JUMP"))
        }

        @Test
        @DisplayName("should handle BLOCK statistic keys with material suffix")
        fun blockStatisticKeys() {
            val stats = PlayerStatistics(mapOf(
                "MINE_BLOCK:DIAMOND_ORE" to 100,
                "MINE_BLOCK:STONE" to 5000,
                "BREAK_ITEM:DIAMOND_PICKAXE" to 3
            ))

            assertEquals(100, stats.get("MINE_BLOCK:DIAMOND_ORE"))
            assertEquals(5000, stats.get("MINE_BLOCK:STONE"))
            assertEquals(3, stats.get("BREAK_ITEM:DIAMOND_PICKAXE"))
        }

        @Test
        @DisplayName("should handle ENTITY statistic keys with entity suffix")
        fun entityStatisticKeys() {
            val stats = PlayerStatistics(mapOf(
                "KILL_ENTITY:ZOMBIE" to 100,
                "KILL_ENTITY:CREEPER" to 50,
                "ENTITY_KILLED_BY:CREEPER" to 2
            ))

            assertEquals(100, stats.get("KILL_ENTITY:ZOMBIE"))
            assertEquals(50, stats.get("KILL_ENTITY:CREEPER"))
            assertEquals(2, stats.get("ENTITY_KILLED_BY:CREEPER"))
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
            val stats1 = PlayerStatistics(mapOf("DEATHS" to 5))
            val stats2 = PlayerStatistics(mapOf("DEATHS" to 5))

            assertEquals(stats1, stats2)
            assertEquals(stats1.hashCode(), stats2.hashCode())
        }

        @Test
        @DisplayName("should not be equal when contents differ")
        fun notEqualsWhenDifferent() {
            val stats1 = PlayerStatistics(mapOf("DEATHS" to 5))
            val stats2 = PlayerStatistics(mapOf("DEATHS" to 10))

            assertNotEquals(stats1, stats2)
        }

        @Test
        @DisplayName("should copy correctly")
        fun copyCorrectly() {
            val original = PlayerStatistics(mapOf("DEATHS" to 5))
            val copied = original.copy(statistics = mapOf("DEATHS" to 10))

            assertEquals(5, original.get("DEATHS"))
            assertEquals(10, copied.get("DEATHS"))
        }

        @Test
        @DisplayName("should provide meaningful toString")
        fun toStringMeaningful() {
            val stats = PlayerStatistics(mapOf("DEATHS" to 5))
            val string = stats.toString()

            assertTrue(string.contains("DEATHS"))
            assertTrue(string.contains("5"))
        }
    }

    // ============================================================
    // Immutability Tests
    // ============================================================

    @Nested
    @DisplayName("Immutability")
    inner class ImmutabilityTests {

        @Test
        @DisplayName("with() should return new instance")
        fun withReturnsNewInstance() {
            val original = PlayerStatistics(mapOf("DEATHS" to 5))
            val updated = original.with("JUMP", 100)

            assertNotSame(original, updated)
            assertEquals(1, original.size)
            assertEquals(2, updated.size)
        }

        @Test
        @DisplayName("withAll() should return new instance")
        fun withAllReturnsNewInstance() {
            val original = PlayerStatistics(mapOf("DEATHS" to 5))
            val updated = original.withAll(mapOf("JUMP" to 100))

            assertNotSame(original, updated)
            assertEquals(1, original.size)
            assertEquals(2, updated.size)
        }

        @Test
        @DisplayName("should not share state between instances created via with()")
        fun noSharedStateBetweenInstances() {
            val original = PlayerStatistics(mapOf("DEATHS" to 5))
            val updated = original.with("JUMP", 100)

            // Modifying one should not affect the other
            val furtherUpdated = updated.with("WALK_ONE_CM", 500)

            assertEquals(1, original.size)
            assertEquals(2, updated.size)
            assertEquals(3, furtherUpdated.size)
        }
    }
}

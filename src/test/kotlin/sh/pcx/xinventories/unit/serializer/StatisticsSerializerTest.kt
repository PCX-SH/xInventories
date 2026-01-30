package sh.pcx.xinventories.unit.serializer

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.model.PlayerStatistics
import sh.pcx.xinventories.internal.serializer.StatisticsSerializer

@DisplayName("StatisticsSerializer Tests")
class StatisticsSerializerTest {

    // ============================================================
    // Map Serialization Tests
    // ============================================================

    @Nested
    @DisplayName("Serialize To Map")
    inner class SerializeToMapTests {

        @Test
        @DisplayName("should serialize empty statistics to empty map")
        fun serializeEmpty() {
            val stats = PlayerStatistics.empty()
            val map = StatisticsSerializer.serializeToMap(stats)

            assertTrue(map.isEmpty())
        }

        @Test
        @DisplayName("should serialize single statistic")
        fun serializeSingle() {
            val stats = PlayerStatistics(mapOf("DEATHS" to 5))
            val map = StatisticsSerializer.serializeToMap(stats)

            assertEquals(1, map.size)
            assertEquals(5, map["DEATHS"])
        }

        @Test
        @DisplayName("should serialize multiple statistics")
        fun serializeMultiple() {
            val stats = PlayerStatistics(mapOf(
                "DEATHS" to 5,
                "PLAY_ONE_MINUTE" to 72000,
                "JUMP" to 100
            ))
            val map = StatisticsSerializer.serializeToMap(stats)

            assertEquals(3, map.size)
            assertEquals(5, map["DEATHS"])
            assertEquals(72000, map["PLAY_ONE_MINUTE"])
            assertEquals(100, map["JUMP"])
        }

        @Test
        @DisplayName("should serialize block statistics with material suffix")
        fun serializeBlockStats() {
            val stats = PlayerStatistics(mapOf(
                "MINE_BLOCK:DIAMOND_ORE" to 100,
                "MINE_BLOCK:STONE" to 5000
            ))
            val map = StatisticsSerializer.serializeToMap(stats)

            assertEquals(2, map.size)
            assertEquals(100, map["MINE_BLOCK:DIAMOND_ORE"])
            assertEquals(5000, map["MINE_BLOCK:STONE"])
        }

        @Test
        @DisplayName("should serialize entity statistics with entity suffix")
        fun serializeEntityStats() {
            val stats = PlayerStatistics(mapOf(
                "KILL_ENTITY:ZOMBIE" to 50,
                "ENTITY_KILLED_BY:CREEPER" to 2
            ))
            val map = StatisticsSerializer.serializeToMap(stats)

            assertEquals(2, map.size)
            assertEquals(50, map["KILL_ENTITY:ZOMBIE"])
            assertEquals(2, map["ENTITY_KILLED_BY:CREEPER"])
        }
    }

    // ============================================================
    // Map Deserialization Tests
    // ============================================================

    @Nested
    @DisplayName("Deserialize From Map")
    inner class DeserializeFromMapTests {

        @Test
        @DisplayName("should deserialize null to empty statistics")
        fun deserializeNull() {
            val stats = StatisticsSerializer.deserializeFromMap(null)

            assertTrue(stats.isEmpty())
        }

        @Test
        @DisplayName("should deserialize empty map to empty statistics")
        fun deserializeEmpty() {
            val stats = StatisticsSerializer.deserializeFromMap(emptyMap())

            assertTrue(stats.isEmpty())
        }

        @Test
        @DisplayName("should deserialize integer values")
        fun deserializeIntegers() {
            val data = mapOf<String, Any>(
                "DEATHS" to 5,
                "JUMP" to 100
            )
            val stats = StatisticsSerializer.deserializeFromMap(data)

            assertEquals(2, stats.size)
            assertEquals(5, stats.get("DEATHS"))
            assertEquals(100, stats.get("JUMP"))
        }

        @Test
        @DisplayName("should deserialize long values to int")
        fun deserializeLongs() {
            val data = mapOf<String, Any>(
                "PLAY_ONE_MINUTE" to 72000L
            )
            val stats = StatisticsSerializer.deserializeFromMap(data)

            assertEquals(72000, stats.get("PLAY_ONE_MINUTE"))
        }

        @Test
        @DisplayName("should deserialize string numbers")
        fun deserializeStringNumbers() {
            val data = mapOf<String, Any>(
                "DEATHS" to "5",
                "JUMP" to "100"
            )
            val stats = StatisticsSerializer.deserializeFromMap(data)

            assertEquals(5, stats.get("DEATHS"))
            assertEquals(100, stats.get("JUMP"))
        }

        @Test
        @DisplayName("should skip invalid values")
        fun skipInvalidValues() {
            val data = mapOf<String, Any>(
                "DEATHS" to 5,
                "INVALID" to "not_a_number",
                "ALSO_INVALID" to listOf(1, 2, 3)
            )
            val stats = StatisticsSerializer.deserializeFromMap(data)

            assertEquals(1, stats.size)
            assertEquals(5, stats.get("DEATHS"))
        }

        @Test
        @DisplayName("should deserialize mixed statistic types")
        fun deserializeMixedTypes() {
            val data = mapOf<String, Any>(
                "DEATHS" to 5,
                "MINE_BLOCK:DIAMOND_ORE" to 100,
                "KILL_ENTITY:ZOMBIE" to 50
            )
            val stats = StatisticsSerializer.deserializeFromMap(data)

            assertEquals(3, stats.size)
            assertEquals(5, stats.get("DEATHS"))
            assertEquals(100, stats.get("MINE_BLOCK:DIAMOND_ORE"))
            assertEquals(50, stats.get("KILL_ENTITY:ZOMBIE"))
        }
    }

    // ============================================================
    // String Serialization Tests
    // ============================================================

    @Nested
    @DisplayName("Serialize To String")
    inner class SerializeToStringTests {

        @Test
        @DisplayName("should serialize empty statistics to empty string")
        fun serializeEmpty() {
            val stats = PlayerStatistics.empty()
            val str = StatisticsSerializer.serializeToString(stats)

            assertEquals("", str)
        }

        @Test
        @DisplayName("should serialize single statistic")
        fun serializeSingle() {
            val stats = PlayerStatistics(mapOf("DEATHS" to 5))
            val str = StatisticsSerializer.serializeToString(stats)

            assertEquals("DEATHS=5", str)
        }

        @Test
        @DisplayName("should serialize multiple statistics with semicolon separator")
        fun serializeMultiple() {
            val stats = PlayerStatistics(mapOf(
                "DEATHS" to 5,
                "JUMP" to 100
            ))
            val str = StatisticsSerializer.serializeToString(stats)

            // Order may vary, so check both possibilities
            assertTrue(str.contains("DEATHS=5"))
            assertTrue(str.contains("JUMP=100"))
            assertTrue(str.contains(";"))
        }

        @Test
        @DisplayName("should serialize block statistics correctly")
        fun serializeBlockStats() {
            val stats = PlayerStatistics(mapOf("MINE_BLOCK:DIAMOND_ORE" to 100))
            val str = StatisticsSerializer.serializeToString(stats)

            assertEquals("MINE_BLOCK:DIAMOND_ORE=100", str)
        }

        @Test
        @DisplayName("should handle zero values")
        fun handleZeroValues() {
            val stats = PlayerStatistics(mapOf("DEATHS" to 0))
            val str = StatisticsSerializer.serializeToString(stats)

            assertEquals("DEATHS=0", str)
        }
    }

    // ============================================================
    // String Deserialization Tests
    // ============================================================

    @Nested
    @DisplayName("Deserialize From String")
    inner class DeserializeFromStringTests {

        @Test
        @DisplayName("should deserialize null to empty statistics")
        fun deserializeNull() {
            val stats = StatisticsSerializer.deserializeFromString(null)

            assertTrue(stats.isEmpty())
        }

        @Test
        @DisplayName("should deserialize empty string to empty statistics")
        fun deserializeEmpty() {
            val stats = StatisticsSerializer.deserializeFromString("")

            assertTrue(stats.isEmpty())
        }

        @Test
        @DisplayName("should deserialize blank string to empty statistics")
        fun deserializeBlank() {
            val stats = StatisticsSerializer.deserializeFromString("   ")

            assertTrue(stats.isEmpty())
        }

        @Test
        @DisplayName("should deserialize single statistic")
        fun deserializeSingle() {
            val stats = StatisticsSerializer.deserializeFromString("DEATHS=5")

            assertEquals(1, stats.size)
            assertEquals(5, stats.get("DEATHS"))
        }

        @Test
        @DisplayName("should deserialize multiple statistics")
        fun deserializeMultiple() {
            val stats = StatisticsSerializer.deserializeFromString("DEATHS=5;JUMP=100;PLAY_ONE_MINUTE=72000")

            assertEquals(3, stats.size)
            assertEquals(5, stats.get("DEATHS"))
            assertEquals(100, stats.get("JUMP"))
            assertEquals(72000, stats.get("PLAY_ONE_MINUTE"))
        }

        @Test
        @DisplayName("should deserialize block statistics")
        fun deserializeBlockStats() {
            val stats = StatisticsSerializer.deserializeFromString("MINE_BLOCK:DIAMOND_ORE=100")

            assertEquals(100, stats.get("MINE_BLOCK:DIAMOND_ORE"))
        }

        @Test
        @DisplayName("should deserialize entity statistics")
        fun deserializeEntityStats() {
            val stats = StatisticsSerializer.deserializeFromString("KILL_ENTITY:ZOMBIE=50;ENTITY_KILLED_BY:CREEPER=2")

            assertEquals(50, stats.get("KILL_ENTITY:ZOMBIE"))
            assertEquals(2, stats.get("ENTITY_KILLED_BY:CREEPER"))
        }

        @Test
        @DisplayName("should skip entries without equals sign")
        fun skipInvalidEntries() {
            val stats = StatisticsSerializer.deserializeFromString("DEATHS=5;INVALID;JUMP=100")

            assertEquals(2, stats.size)
            assertEquals(5, stats.get("DEATHS"))
            assertEquals(100, stats.get("JUMP"))
        }

        @Test
        @DisplayName("should skip entries with invalid values")
        fun skipInvalidValues() {
            val stats = StatisticsSerializer.deserializeFromString("DEATHS=5;INVALID=not_a_number;JUMP=100")

            assertEquals(2, stats.size)
            assertEquals(5, stats.get("DEATHS"))
            assertEquals(100, stats.get("JUMP"))
        }

        @Test
        @DisplayName("should handle trailing semicolon")
        fun handleTrailingSemicolon() {
            val stats = StatisticsSerializer.deserializeFromString("DEATHS=5;JUMP=100;")

            assertEquals(2, stats.size)
        }

        @Test
        @DisplayName("should handle leading semicolon")
        fun handleLeadingSemicolon() {
            val stats = StatisticsSerializer.deserializeFromString(";DEATHS=5;JUMP=100")

            assertEquals(2, stats.size)
        }
    }

    // ============================================================
    // Round Trip Tests
    // ============================================================

    @Nested
    @DisplayName("Round Trip Serialization")
    inner class RoundTripTests {

        @Test
        @DisplayName("should round-trip through map serialization")
        fun roundTripMap() {
            val original = PlayerStatistics(mapOf(
                "DEATHS" to 5,
                "MINE_BLOCK:DIAMOND_ORE" to 100,
                "KILL_ENTITY:ZOMBIE" to 50
            ))

            val map = StatisticsSerializer.serializeToMap(original)
            @Suppress("UNCHECKED_CAST")
            val restored = StatisticsSerializer.deserializeFromMap(map as Map<String, Any>)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("should round-trip through string serialization")
        fun roundTripString() {
            val original = PlayerStatistics(mapOf(
                "DEATHS" to 5,
                "MINE_BLOCK:DIAMOND_ORE" to 100,
                "KILL_ENTITY:ZOMBIE" to 50
            ))

            val str = StatisticsSerializer.serializeToString(original)
            val restored = StatisticsSerializer.deserializeFromString(str)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("should round-trip empty statistics")
        fun roundTripEmpty() {
            val original = PlayerStatistics.empty()

            val str = StatisticsSerializer.serializeToString(original)
            val restored = StatisticsSerializer.deserializeFromString(str)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("should round-trip large statistics")
        fun roundTripLarge() {
            val data = mutableMapOf<String, Int>()
            for (i in 1..100) {
                data["STAT_$i"] = i * 10
            }
            val original = PlayerStatistics(data)

            val str = StatisticsSerializer.serializeToString(original)
            val restored = StatisticsSerializer.deserializeFromString(str)

            assertEquals(original, restored)
        }
    }

    // ============================================================
    // Edge Case Tests
    // ============================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("should handle very large numbers")
        fun handleLargeNumbers() {
            val stats = PlayerStatistics(mapOf("PLAY_ONE_MINUTE" to Int.MAX_VALUE))

            val str = StatisticsSerializer.serializeToString(stats)
            val restored = StatisticsSerializer.deserializeFromString(str)

            assertEquals(Int.MAX_VALUE, restored.get("PLAY_ONE_MINUTE"))
        }

        @Test
        @DisplayName("should handle negative numbers")
        fun handleNegativeNumbers() {
            val stats = PlayerStatistics(mapOf("TEST_STAT" to -100))

            val str = StatisticsSerializer.serializeToString(stats)
            val restored = StatisticsSerializer.deserializeFromString(str)

            assertEquals(-100, restored.get("TEST_STAT"))
        }

        @Test
        @DisplayName("should handle statistic key with multiple colons")
        fun handleMultipleColons() {
            // Keys should only have one colon (STAT:SUBTYPE format)
            val str = "STAT:TYPE:EXTRA=100"
            val stats = StatisticsSerializer.deserializeFromString(str)

            // The key should include everything before the = sign
            assertEquals(100, stats.get("STAT:TYPE:EXTRA"))
        }
    }
}

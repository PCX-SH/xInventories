package sh.pcx.xinventories.unit.serializer

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.model.PlayerAdvancements
import sh.pcx.xinventories.internal.serializer.AdvancementSerializer

@DisplayName("AdvancementSerializer Tests")
class AdvancementSerializerTest {

    // ============================================================
    // List Serialization Tests
    // ============================================================

    @Nested
    @DisplayName("Serialize To List")
    inner class SerializeToListTests {

        @Test
        @DisplayName("should serialize empty advancements to empty list")
        fun serializeEmpty() {
            val advancements = PlayerAdvancements.empty()
            val list = AdvancementSerializer.serializeToList(advancements)

            assertTrue(list.isEmpty())
        }

        @Test
        @DisplayName("should serialize single advancement")
        fun serializeSingle() {
            val advancements = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val list = AdvancementSerializer.serializeToList(advancements)

            assertEquals(1, list.size)
            assertTrue(list.contains("minecraft:story/mine_stone"))
        }

        @Test
        @DisplayName("should serialize multiple advancements")
        fun serializeMultiple() {
            val advancements = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron",
                "minecraft:nether/return_to_sender"
            ))
            val list = AdvancementSerializer.serializeToList(advancements)

            assertEquals(3, list.size)
            assertTrue(list.contains("minecraft:story/mine_stone"))
            assertTrue(list.contains("minecraft:story/smelt_iron"))
            assertTrue(list.contains("minecraft:nether/return_to_sender"))
        }

        @Test
        @DisplayName("should serialize custom namespace advancements")
        fun serializeCustomNamespace() {
            val advancements = PlayerAdvancements(setOf(
                "customplugin:quests/first_quest",
                "myplugin:achievements/level_10"
            ))
            val list = AdvancementSerializer.serializeToList(advancements)

            assertEquals(2, list.size)
            assertTrue(list.contains("customplugin:quests/first_quest"))
            assertTrue(list.contains("myplugin:achievements/level_10"))
        }
    }

    // ============================================================
    // List Deserialization Tests
    // ============================================================

    @Nested
    @DisplayName("Deserialize From List")
    inner class DeserializeFromListTests {

        @Test
        @DisplayName("should deserialize null to empty advancements")
        fun deserializeNull() {
            val advancements = AdvancementSerializer.deserializeFromList(null)

            assertTrue(advancements.isEmpty())
        }

        @Test
        @DisplayName("should deserialize empty list to empty advancements")
        fun deserializeEmpty() {
            val advancements = AdvancementSerializer.deserializeFromList(emptyList())

            assertTrue(advancements.isEmpty())
        }

        @Test
        @DisplayName("should deserialize single advancement")
        fun deserializeSingle() {
            val advancements = AdvancementSerializer.deserializeFromList(listOf("minecraft:story/mine_stone"))

            assertEquals(1, advancements.size)
            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
        }

        @Test
        @DisplayName("should deserialize multiple advancements")
        fun deserializeMultiple() {
            val list = listOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron",
                "minecraft:nether/return_to_sender"
            )
            val advancements = AdvancementSerializer.deserializeFromList(list)

            assertEquals(3, advancements.size)
            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
            assertTrue(advancements.isCompleted("minecraft:story/smelt_iron"))
            assertTrue(advancements.isCompleted("minecraft:nether/return_to_sender"))
        }

        @Test
        @DisplayName("should handle duplicates in list")
        fun handleDuplicates() {
            val list = listOf(
                "minecraft:story/mine_stone",
                "minecraft:story/mine_stone"
            )
            val advancements = AdvancementSerializer.deserializeFromList(list)

            assertEquals(1, advancements.size)
            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
        }
    }

    // ============================================================
    // String Serialization Tests
    // ============================================================

    @Nested
    @DisplayName("Serialize To String")
    inner class SerializeToStringTests {

        @Test
        @DisplayName("should serialize empty advancements to empty string")
        fun serializeEmpty() {
            val advancements = PlayerAdvancements.empty()
            val str = AdvancementSerializer.serializeToString(advancements)

            assertEquals("", str)
        }

        @Test
        @DisplayName("should serialize single advancement")
        fun serializeSingle() {
            val advancements = PlayerAdvancements(setOf("minecraft:story/mine_stone"))
            val str = AdvancementSerializer.serializeToString(advancements)

            assertEquals("minecraft:story/mine_stone", str)
        }

        @Test
        @DisplayName("should serialize multiple advancements with semicolon separator")
        fun serializeMultiple() {
            val advancements = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron"
            ))
            val str = AdvancementSerializer.serializeToString(advancements)

            assertTrue(str.contains("minecraft:story/mine_stone"))
            assertTrue(str.contains("minecraft:story/smelt_iron"))
            assertTrue(str.contains(";"))
        }

        @Test
        @DisplayName("should handle advancement keys with slashes")
        fun handleSlashes() {
            val advancements = PlayerAdvancements(setOf("minecraft:husbandry/balanced_diet"))
            val str = AdvancementSerializer.serializeToString(advancements)

            assertEquals("minecraft:husbandry/balanced_diet", str)
        }
    }

    // ============================================================
    // String Deserialization Tests
    // ============================================================

    @Nested
    @DisplayName("Deserialize From String")
    inner class DeserializeFromStringTests {

        @Test
        @DisplayName("should deserialize null to empty advancements")
        fun deserializeNull() {
            val advancements = AdvancementSerializer.deserializeFromString(null)

            assertTrue(advancements.isEmpty())
        }

        @Test
        @DisplayName("should deserialize empty string to empty advancements")
        fun deserializeEmpty() {
            val advancements = AdvancementSerializer.deserializeFromString("")

            assertTrue(advancements.isEmpty())
        }

        @Test
        @DisplayName("should deserialize blank string to empty advancements")
        fun deserializeBlank() {
            val advancements = AdvancementSerializer.deserializeFromString("   ")

            assertTrue(advancements.isEmpty())
        }

        @Test
        @DisplayName("should deserialize single advancement")
        fun deserializeSingle() {
            val advancements = AdvancementSerializer.deserializeFromString("minecraft:story/mine_stone")

            assertEquals(1, advancements.size)
            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
        }

        @Test
        @DisplayName("should deserialize multiple advancements")
        fun deserializeMultiple() {
            val str = "minecraft:story/mine_stone;minecraft:story/smelt_iron;minecraft:nether/return_to_sender"
            val advancements = AdvancementSerializer.deserializeFromString(str)

            assertEquals(3, advancements.size)
            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
            assertTrue(advancements.isCompleted("minecraft:story/smelt_iron"))
            assertTrue(advancements.isCompleted("minecraft:nether/return_to_sender"))
        }

        @Test
        @DisplayName("should skip blank entries")
        fun skipBlankEntries() {
            val str = "minecraft:story/mine_stone;;minecraft:story/smelt_iron"
            val advancements = AdvancementSerializer.deserializeFromString(str)

            assertEquals(2, advancements.size)
        }

        @Test
        @DisplayName("should handle trailing semicolon")
        fun handleTrailingSemicolon() {
            val advancements = AdvancementSerializer.deserializeFromString("minecraft:story/mine_stone;")

            assertEquals(1, advancements.size)
            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
        }

        @Test
        @DisplayName("should handle leading semicolon")
        fun handleLeadingSemicolon() {
            val advancements = AdvancementSerializer.deserializeFromString(";minecraft:story/mine_stone")

            assertEquals(1, advancements.size)
            assertTrue(advancements.isCompleted("minecraft:story/mine_stone"))
        }
    }

    // ============================================================
    // Round Trip Tests
    // ============================================================

    @Nested
    @DisplayName("Round Trip Serialization")
    inner class RoundTripTests {

        @Test
        @DisplayName("should round-trip through list serialization")
        fun roundTripList() {
            val original = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron",
                "minecraft:nether/return_to_sender"
            ))

            val list = AdvancementSerializer.serializeToList(original)
            val restored = AdvancementSerializer.deserializeFromList(list)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("should round-trip through string serialization")
        fun roundTripString() {
            val original = PlayerAdvancements(setOf(
                "minecraft:story/mine_stone",
                "minecraft:story/smelt_iron",
                "minecraft:nether/return_to_sender"
            ))

            val str = AdvancementSerializer.serializeToString(original)
            val restored = AdvancementSerializer.deserializeFromString(str)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("should round-trip empty advancements")
        fun roundTripEmpty() {
            val original = PlayerAdvancements.empty()

            val str = AdvancementSerializer.serializeToString(original)
            val restored = AdvancementSerializer.deserializeFromString(str)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("should round-trip many advancements")
        fun roundTripMany() {
            val keys = (1..100).map { "minecraft:test/advancement_$it" }.toSet()
            val original = PlayerAdvancements(keys)

            val str = AdvancementSerializer.serializeToString(original)
            val restored = AdvancementSerializer.deserializeFromString(str)

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
        @DisplayName("should handle advancement keys with special characters")
        fun handleSpecialCharacters() {
            val advancements = PlayerAdvancements(setOf(
                "minecraft:adventure/kill_a_mob",
                "minecraft:husbandry/breed_all_animals"
            ))

            val str = AdvancementSerializer.serializeToString(advancements)
            val restored = AdvancementSerializer.deserializeFromString(str)

            assertEquals(advancements, restored)
        }

        @Test
        @DisplayName("should handle long advancement keys")
        fun handleLongKeys() {
            val longKey = "minecraft:" + "a".repeat(200) + "/test"
            val advancements = PlayerAdvancements(setOf(longKey))

            val str = AdvancementSerializer.serializeToString(advancements)
            val restored = AdvancementSerializer.deserializeFromString(str)

            assertTrue(restored.isCompleted(longKey))
        }

        @Test
        @DisplayName("should handle custom namespace with numbers")
        fun handleCustomNamespaceWithNumbers() {
            val advancements = PlayerAdvancements(setOf(
                "plugin123:quests/quest_1",
                "mod456:achievements/level_100"
            ))

            val str = AdvancementSerializer.serializeToString(advancements)
            val restored = AdvancementSerializer.deserializeFromString(str)

            assertEquals(advancements, restored)
        }
    }
}

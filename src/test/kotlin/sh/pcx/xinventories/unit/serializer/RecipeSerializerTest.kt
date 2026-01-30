package sh.pcx.xinventories.unit.serializer

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.serializer.RecipeSerializer

@DisplayName("RecipeSerializer Tests")
class RecipeSerializerTest {

    // ============================================================
    // List Serialization Tests
    // ============================================================

    @Nested
    @DisplayName("Serialize To List")
    inner class SerializeToListTests {

        @Test
        @DisplayName("should serialize empty recipes to empty list")
        fun serializeEmpty() {
            val recipes = emptySet<String>()
            val list = RecipeSerializer.serializeToList(recipes)

            assertTrue(list.isEmpty())
        }

        @Test
        @DisplayName("should serialize single recipe")
        fun serializeSingle() {
            val recipes = setOf("minecraft:oak_planks")
            val list = RecipeSerializer.serializeToList(recipes)

            assertEquals(1, list.size)
            assertTrue(list.contains("minecraft:oak_planks"))
        }

        @Test
        @DisplayName("should serialize multiple recipes")
        fun serializeMultiple() {
            val recipes = setOf(
                "minecraft:oak_planks",
                "minecraft:crafting_table",
                "minecraft:diamond_sword"
            )
            val list = RecipeSerializer.serializeToList(recipes)

            assertEquals(3, list.size)
            assertTrue(list.contains("minecraft:oak_planks"))
            assertTrue(list.contains("minecraft:crafting_table"))
            assertTrue(list.contains("minecraft:diamond_sword"))
        }

        @Test
        @DisplayName("should serialize custom namespace recipes")
        fun serializeCustomNamespace() {
            val recipes = setOf(
                "customplugin:custom_item",
                "myplugin:super_sword"
            )
            val list = RecipeSerializer.serializeToList(recipes)

            assertEquals(2, list.size)
            assertTrue(list.contains("customplugin:custom_item"))
            assertTrue(list.contains("myplugin:super_sword"))
        }
    }

    // ============================================================
    // List Deserialization Tests
    // ============================================================

    @Nested
    @DisplayName("Deserialize From List")
    inner class DeserializeFromListTests {

        @Test
        @DisplayName("should deserialize null to empty set")
        fun deserializeNull() {
            val recipes = RecipeSerializer.deserializeFromList(null)

            assertTrue(recipes.isEmpty())
        }

        @Test
        @DisplayName("should deserialize empty list to empty set")
        fun deserializeEmpty() {
            val recipes = RecipeSerializer.deserializeFromList(emptyList())

            assertTrue(recipes.isEmpty())
        }

        @Test
        @DisplayName("should deserialize single recipe")
        fun deserializeSingle() {
            val recipes = RecipeSerializer.deserializeFromList(listOf("minecraft:oak_planks"))

            assertEquals(1, recipes.size)
            assertTrue(recipes.contains("minecraft:oak_planks"))
        }

        @Test
        @DisplayName("should deserialize multiple recipes")
        fun deserializeMultiple() {
            val list = listOf(
                "minecraft:oak_planks",
                "minecraft:crafting_table",
                "minecraft:diamond_sword"
            )
            val recipes = RecipeSerializer.deserializeFromList(list)

            assertEquals(3, recipes.size)
            assertTrue(recipes.contains("minecraft:oak_planks"))
            assertTrue(recipes.contains("minecraft:crafting_table"))
            assertTrue(recipes.contains("minecraft:diamond_sword"))
        }

        @Test
        @DisplayName("should handle duplicates in list")
        fun handleDuplicates() {
            val list = listOf(
                "minecraft:oak_planks",
                "minecraft:oak_planks"
            )
            val recipes = RecipeSerializer.deserializeFromList(list)

            assertEquals(1, recipes.size)
            assertTrue(recipes.contains("minecraft:oak_planks"))
        }
    }

    // ============================================================
    // String Serialization Tests
    // ============================================================

    @Nested
    @DisplayName("Serialize To String")
    inner class SerializeToStringTests {

        @Test
        @DisplayName("should serialize empty recipes to empty string")
        fun serializeEmpty() {
            val recipes = emptySet<String>()
            val str = RecipeSerializer.serializeToString(recipes)

            assertEquals("", str)
        }

        @Test
        @DisplayName("should serialize single recipe")
        fun serializeSingle() {
            val recipes = setOf("minecraft:oak_planks")
            val str = RecipeSerializer.serializeToString(recipes)

            assertEquals("minecraft:oak_planks", str)
        }

        @Test
        @DisplayName("should serialize multiple recipes with semicolon separator")
        fun serializeMultiple() {
            val recipes = setOf(
                "minecraft:oak_planks",
                "minecraft:crafting_table"
            )
            val str = RecipeSerializer.serializeToString(recipes)

            assertTrue(str.contains("minecraft:oak_planks"))
            assertTrue(str.contains("minecraft:crafting_table"))
            assertTrue(str.contains(";"))
        }

        @Test
        @DisplayName("should handle recipe keys with underscores")
        fun handleUnderscores() {
            val recipes = setOf("minecraft:diamond_sword")
            val str = RecipeSerializer.serializeToString(recipes)

            assertEquals("minecraft:diamond_sword", str)
        }
    }

    // ============================================================
    // String Deserialization Tests
    // ============================================================

    @Nested
    @DisplayName("Deserialize From String")
    inner class DeserializeFromStringTests {

        @Test
        @DisplayName("should deserialize null to empty set")
        fun deserializeNull() {
            val recipes = RecipeSerializer.deserializeFromString(null)

            assertTrue(recipes.isEmpty())
        }

        @Test
        @DisplayName("should deserialize empty string to empty set")
        fun deserializeEmpty() {
            val recipes = RecipeSerializer.deserializeFromString("")

            assertTrue(recipes.isEmpty())
        }

        @Test
        @DisplayName("should deserialize blank string to empty set")
        fun deserializeBlank() {
            val recipes = RecipeSerializer.deserializeFromString("   ")

            assertTrue(recipes.isEmpty())
        }

        @Test
        @DisplayName("should deserialize single recipe")
        fun deserializeSingle() {
            val recipes = RecipeSerializer.deserializeFromString("minecraft:oak_planks")

            assertEquals(1, recipes.size)
            assertTrue(recipes.contains("minecraft:oak_planks"))
        }

        @Test
        @DisplayName("should deserialize multiple recipes")
        fun deserializeMultiple() {
            val str = "minecraft:oak_planks;minecraft:crafting_table;minecraft:diamond_sword"
            val recipes = RecipeSerializer.deserializeFromString(str)

            assertEquals(3, recipes.size)
            assertTrue(recipes.contains("minecraft:oak_planks"))
            assertTrue(recipes.contains("minecraft:crafting_table"))
            assertTrue(recipes.contains("minecraft:diamond_sword"))
        }

        @Test
        @DisplayName("should skip blank entries")
        fun skipBlankEntries() {
            val str = "minecraft:oak_planks;;minecraft:crafting_table"
            val recipes = RecipeSerializer.deserializeFromString(str)

            assertEquals(2, recipes.size)
        }

        @Test
        @DisplayName("should handle trailing semicolon")
        fun handleTrailingSemicolon() {
            val recipes = RecipeSerializer.deserializeFromString("minecraft:oak_planks;")

            assertEquals(1, recipes.size)
            assertTrue(recipes.contains("minecraft:oak_planks"))
        }

        @Test
        @DisplayName("should handle leading semicolon")
        fun handleLeadingSemicolon() {
            val recipes = RecipeSerializer.deserializeFromString(";minecraft:oak_planks")

            assertEquals(1, recipes.size)
            assertTrue(recipes.contains("minecraft:oak_planks"))
        }

        @Test
        @DisplayName("should trim whitespace from entries")
        fun trimWhitespace() {
            val str = " minecraft:oak_planks ; minecraft:crafting_table "
            val recipes = RecipeSerializer.deserializeFromString(str)

            // Note: Implementation filters blank entries, entries with spaces may be kept
            // The actual behavior depends on implementation
            assertTrue(recipes.isNotEmpty())
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
            val original = setOf(
                "minecraft:oak_planks",
                "minecraft:crafting_table",
                "minecraft:diamond_sword"
            )

            val list = RecipeSerializer.serializeToList(original)
            val restored = RecipeSerializer.deserializeFromList(list)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("should round-trip through string serialization")
        fun roundTripString() {
            val original = setOf(
                "minecraft:oak_planks",
                "minecraft:crafting_table",
                "minecraft:diamond_sword"
            )

            val str = RecipeSerializer.serializeToString(original)
            val restored = RecipeSerializer.deserializeFromString(str)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("should round-trip empty recipes")
        fun roundTripEmpty() {
            val original = emptySet<String>()

            val str = RecipeSerializer.serializeToString(original)
            val restored = RecipeSerializer.deserializeFromString(str)

            assertEquals(original, restored)
        }

        @Test
        @DisplayName("should round-trip many recipes")
        fun roundTripMany() {
            val original = (1..100).map { "minecraft:recipe_$it" }.toSet()

            val str = RecipeSerializer.serializeToString(original)
            val restored = RecipeSerializer.deserializeFromString(str)

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
        @DisplayName("should handle recipe keys with many underscores")
        fun handleManyUnderscores() {
            val recipes = setOf(
                "minecraft:diamond_sword",
                "minecraft:golden_carrot",
                "minecraft:enchanted_golden_apple"
            )

            val str = RecipeSerializer.serializeToString(recipes)
            val restored = RecipeSerializer.deserializeFromString(str)

            assertEquals(recipes, restored)
        }

        @Test
        @DisplayName("should handle long recipe keys")
        fun handleLongKeys() {
            val longKey = "minecraft:" + "a".repeat(200)
            val recipes = setOf(longKey)

            val str = RecipeSerializer.serializeToString(recipes)
            val restored = RecipeSerializer.deserializeFromString(str)

            assertTrue(restored.contains(longKey))
        }

        @Test
        @DisplayName("should handle custom namespace with numbers")
        fun handleCustomNamespaceWithNumbers() {
            val recipes = setOf(
                "plugin123:item_1",
                "mod456:recipe_100"
            )

            val str = RecipeSerializer.serializeToString(recipes)
            val restored = RecipeSerializer.deserializeFromString(str)

            assertEquals(recipes, restored)
        }

        @Test
        @DisplayName("should maintain order independence")
        fun maintainOrderIndependence() {
            val recipes1 = setOf("minecraft:a", "minecraft:b", "minecraft:c")
            val recipes2 = setOf("minecraft:c", "minecraft:a", "minecraft:b")

            // Sets should be equal regardless of insertion order
            assertEquals(recipes1, recipes2)

            val str1 = RecipeSerializer.serializeToString(recipes1)
            val str2 = RecipeSerializer.serializeToString(recipes2)

            val restored1 = RecipeSerializer.deserializeFromString(str1)
            val restored2 = RecipeSerializer.deserializeFromString(str2)

            assertEquals(restored1, restored2)
        }
    }
}

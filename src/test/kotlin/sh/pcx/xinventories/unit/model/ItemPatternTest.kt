package sh.pcx.xinventories.unit.model

import org.bukkit.Material
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.internal.model.ItemPattern

@DisplayName("ItemPattern Tests")
class ItemPatternTest {

    private lateinit var server: ServerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    // ============================================================
    // Exact Material Pattern Tests
    // ============================================================

    @Nested
    @DisplayName("Exact Material Pattern")
    inner class ExactMaterialTests {

        @Test
        @DisplayName("should match exact material")
        fun matchExactMaterial() {
            val pattern = ItemPattern.parse("DIAMOND")

            assertTrue(pattern is ItemPattern.ExactMaterial)
            assertTrue(pattern.matches(ItemStack(Material.DIAMOND)))
            assertFalse(pattern.matches(ItemStack(Material.GOLD_INGOT)))
        }

        @Test
        @DisplayName("should be case insensitive")
        fun caseInsensitive() {
            val pattern1 = ItemPattern.parse("diamond")
            val pattern2 = ItemPattern.parse("DIAMOND")
            val pattern3 = ItemPattern.parse("Diamond")

            val diamond = ItemStack(Material.DIAMOND)

            assertTrue(pattern1.matches(diamond))
            assertTrue(pattern2.matches(diamond))
            assertTrue(pattern3.matches(diamond))
        }

        @Test
        @DisplayName("should not match different materials")
        fun notMatchDifferentMaterial() {
            val pattern = ItemPattern.parse("DIAMOND")

            assertFalse(pattern.matches(ItemStack(Material.EMERALD)))
            assertFalse(pattern.matches(ItemStack(Material.DIAMOND_SWORD)))
        }

        @Test
        @DisplayName("should return Invalid for unknown material")
        fun invalidForUnknownMaterial() {
            val pattern = ItemPattern.parse("UNKNOWN_MATERIAL_XYZ")

            assertTrue(pattern is ItemPattern.Invalid)
            assertFalse(pattern.matches(ItemStack(Material.DIAMOND)))
        }
    }

    // ============================================================
    // Material Wildcard Pattern Tests
    // ============================================================

    @Nested
    @DisplayName("Material Wildcard Pattern")
    inner class MaterialWildcardTests {

        @Test
        @DisplayName("should parse material with wildcard")
        fun parseMaterialWildcard() {
            val pattern = ItemPattern.parse("DIAMOND:*")

            assertTrue(pattern is ItemPattern.MaterialWildcard)
            assertTrue(pattern.matches(ItemStack(Material.DIAMOND)))
        }

        @Test
        @DisplayName("should match material regardless of enchantments")
        fun matchWithEnchantments() {
            val pattern = ItemPattern.parse("DIAMOND_SWORD:*")

            val plainSword = ItemStack(Material.DIAMOND_SWORD)
            val enchantedSword = ItemStack(Material.DIAMOND_SWORD).apply {
                addEnchantment(Enchantment.SHARPNESS, 5)
            }

            assertTrue(pattern.matches(plainSword))
            assertTrue(pattern.matches(enchantedSword))
        }
    }

    // ============================================================
    // Spawn Egg Wildcard Pattern Tests
    // ============================================================

    @Nested
    @DisplayName("Spawn Egg Wildcard Pattern")
    inner class SpawnEggWildcardTests {

        @Test
        @DisplayName("should parse spawn egg wildcard")
        fun parseSpawnEggWildcard() {
            val pattern = ItemPattern.parse("SPAWN_EGG:*")

            assertTrue(pattern is ItemPattern.SpawnEggWildcard)
        }

        @Test
        @DisplayName("should match various spawn eggs")
        fun matchSpawnEggs() {
            val pattern = ItemPattern.parse("SPAWN_EGG:*")

            assertTrue(pattern.matches(ItemStack(Material.ZOMBIE_SPAWN_EGG)))
            assertTrue(pattern.matches(ItemStack(Material.CREEPER_SPAWN_EGG)))
            assertTrue(pattern.matches(ItemStack(Material.SKELETON_SPAWN_EGG)))
            assertTrue(pattern.matches(ItemStack(Material.PIG_SPAWN_EGG)))
        }

        @Test
        @DisplayName("should not match non-spawn-egg items")
        fun notMatchOtherItems() {
            val pattern = ItemPattern.parse("SPAWN_EGG:*")

            assertFalse(pattern.matches(ItemStack(Material.EGG)))
            assertFalse(pattern.matches(ItemStack(Material.DIAMOND)))
            assertFalse(pattern.matches(ItemStack(Material.TURTLE_EGG)))
        }
    }

    // ============================================================
    // Material With Enchantment Pattern Tests
    // ============================================================

    @Nested
    @DisplayName("Material With Enchantment Pattern")
    inner class MaterialWithEnchantTests {

        @Test
        @DisplayName("should parse material with exact enchant level")
        fun parseExactEnchantLevel() {
            val pattern = ItemPattern.parse("DIAMOND_SWORD:SHARPNESS:5")

            assertTrue(pattern is ItemPattern.MaterialWithEnchant)

            val sword5 = ItemStack(Material.DIAMOND_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 5)
            }
            val sword4 = ItemStack(Material.DIAMOND_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 4)
            }
            val sword6 = ItemStack(Material.DIAMOND_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 6)
            }

            assertTrue(pattern.matches(sword5))
            assertFalse(pattern.matches(sword4))
            assertFalse(pattern.matches(sword6))
        }

        @Test
        @DisplayName("should parse material with enchant level or higher")
        fun parseEnchantLevelOrHigher() {
            val pattern = ItemPattern.parse("DIAMOND_SWORD:SHARPNESS:5+")

            assertTrue(pattern is ItemPattern.MaterialWithEnchant)

            val sword5 = ItemStack(Material.DIAMOND_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 5)
            }
            val sword6 = ItemStack(Material.DIAMOND_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 6)
            }
            val sword10 = ItemStack(Material.DIAMOND_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 10)
            }
            val sword4 = ItemStack(Material.DIAMOND_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 4)
            }

            assertTrue(pattern.matches(sword5))
            assertTrue(pattern.matches(sword6))
            assertTrue(pattern.matches(sword10))
            assertFalse(pattern.matches(sword4))
        }

        @Test
        @DisplayName("should not match wrong material")
        fun notMatchWrongMaterial() {
            val pattern = ItemPattern.parse("DIAMOND_SWORD:SHARPNESS:5")

            val ironSword = ItemStack(Material.IRON_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 5)
            }

            assertFalse(pattern.matches(ironSword))
        }

        @Test
        @DisplayName("should not match without enchantment")
        fun notMatchWithoutEnchant() {
            val pattern = ItemPattern.parse("DIAMOND_SWORD:SHARPNESS:5")

            val plainSword = ItemStack(Material.DIAMOND_SWORD)

            assertFalse(pattern.matches(plainSword))
        }
    }

    // ============================================================
    // Any Item With Enchantment Pattern Tests
    // ============================================================

    @Nested
    @DisplayName("Any Item With Enchantment Pattern")
    inner class AnyWithEnchantTests {

        @Test
        @DisplayName("should parse any item with enchant")
        fun parseAnyWithEnchant() {
            val pattern = ItemPattern.parse("*:SHARPNESS:6+")

            assertTrue(pattern is ItemPattern.AnyWithEnchant)
        }

        @Test
        @DisplayName("should match any material with enchant")
        fun matchAnyMaterial() {
            val pattern = ItemPattern.parse("*:SHARPNESS:5+")

            val diamondSword = ItemStack(Material.DIAMOND_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 5)
            }
            val ironSword = ItemStack(Material.IRON_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 6)
            }
            val stick = ItemStack(Material.STICK).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 10)
            }

            assertTrue(pattern.matches(diamondSword))
            assertTrue(pattern.matches(ironSword))
            assertTrue(pattern.matches(stick))
        }

        @Test
        @DisplayName("should not match if enchant level too low")
        fun notMatchLowLevel() {
            val pattern = ItemPattern.parse("*:SHARPNESS:5+")

            val sword = ItemStack(Material.DIAMOND_SWORD).apply {
                addUnsafeEnchantment(Enchantment.SHARPNESS, 4)
            }

            assertFalse(pattern.matches(sword))
        }

        @Test
        @DisplayName("should not match if enchant missing")
        fun notMatchMissingEnchant() {
            val pattern = ItemPattern.parse("*:SHARPNESS:5+")

            val sword = ItemStack(Material.DIAMOND_SWORD)

            assertFalse(pattern.matches(sword))
        }
    }

    // ============================================================
    // Invalid Pattern Tests
    // ============================================================

    @Nested
    @DisplayName("Invalid Pattern Handling")
    inner class InvalidPatternTests {

        @Test
        @DisplayName("should return Invalid for unknown material")
        fun invalidUnknownMaterial() {
            val pattern = ItemPattern.parse("NOTAMATERIAL")

            assertTrue(pattern is ItemPattern.Invalid)
            assertEquals("NOTAMATERIAL", pattern.patternString)
        }

        @Test
        @DisplayName("should return Invalid for unknown enchantment")
        fun invalidUnknownEnchant() {
            val pattern = ItemPattern.parse("DIAMOND_SWORD:NOTANENCHANT:5")

            assertTrue(pattern is ItemPattern.Invalid)
        }

        @Test
        @DisplayName("should return Invalid for invalid level")
        fun invalidLevel() {
            val pattern = ItemPattern.parse("DIAMOND_SWORD:SHARPNESS:abc")

            assertTrue(pattern is ItemPattern.Invalid)
        }

        @Test
        @DisplayName("Invalid pattern should never match")
        fun invalidNeverMatches() {
            val pattern = ItemPattern.parse("INVALID_PATTERN_XYZ")

            assertTrue(pattern is ItemPattern.Invalid)
            assertFalse(pattern.matches(ItemStack(Material.DIAMOND)))
            assertFalse(pattern.matches(ItemStack(Material.AIR)))
            assertFalse(pattern.matches(ItemStack(Material.BARRIER)))
        }
    }

    // ============================================================
    // Edge Cases
    // ============================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("should handle whitespace in pattern")
        fun handleWhitespace() {
            val pattern = ItemPattern.parse("  DIAMOND  ")

            assertTrue(pattern.matches(ItemStack(Material.DIAMOND)))
        }

        @Test
        @DisplayName("should preserve original pattern string")
        fun preservePatternString() {
            val originalPattern = "DIAMOND_SWORD:SHARPNESS:5+"
            val pattern = ItemPattern.parse(originalPattern)

            assertEquals(originalPattern, pattern.patternString)
        }

        @Test
        @DisplayName("should handle enchantment aliases")
        fun handleEnchantAliases() {
            val pattern1 = ItemPattern.parse("DIAMOND_SWORD:SWEEPING:1+")
            val pattern2 = ItemPattern.parse("DIAMOND_SWORD:SWEEPING_EDGE:1+")

            val sword = ItemStack(Material.DIAMOND_SWORD).apply {
                addEnchantment(Enchantment.SWEEPING_EDGE, 1)
            }

            assertTrue(pattern1.matches(sword))
            assertTrue(pattern2.matches(sword))
        }
    }
}

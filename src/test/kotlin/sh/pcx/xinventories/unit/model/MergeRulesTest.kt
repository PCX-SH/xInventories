package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.MergeRule
import sh.pcx.xinventories.internal.model.MergeRules

/**
 * Unit tests for MergeRules and MergeRule.
 */
class MergeRulesTest {

    @Nested
    @DisplayName("MergeRule Enum")
    inner class MergeRuleEnumTests {

        @Test
        @DisplayName("should have all expected values")
        fun testAllValuesExist() {
            val expectedValues = listOf(
                "NEWER", "OLDER", "HIGHER", "LOWER",
                "CURRENT_SERVER", "UNION", "INTERSECTION"
            )

            val actualValues = MergeRule.entries.map { it.name }

            expectedValues.forEach { expected ->
                assertTrue(actualValues.contains(expected), "Missing MergeRule: $expected")
            }
        }

        @Test
        @DisplayName("should parse from string correctly")
        fun testParseFromString() {
            assertEquals(MergeRule.NEWER, MergeRule.valueOf("NEWER"))
            assertEquals(MergeRule.HIGHER, MergeRule.valueOf("HIGHER"))
            assertEquals(MergeRule.UNION, MergeRule.valueOf("UNION"))
        }
    }

    @Nested
    @DisplayName("MergeRules Defaults")
    inner class MergeRulesDefaultsTests {

        @Test
        @DisplayName("DEFAULT should use expected values")
        fun testDefaultValues() {
            val defaults = MergeRules.DEFAULT

            assertEquals(MergeRule.NEWER, defaults.inventory)
            assertEquals(MergeRule.HIGHER, defaults.experience)
            assertEquals(MergeRule.CURRENT_SERVER, defaults.health)
            assertEquals(MergeRule.UNION, defaults.potionEffects)
        }

        @Test
        @DisplayName("CONSERVATIVE should prioritize preservation")
        fun testConservativeValues() {
            val conservative = MergeRules.CONSERVATIVE

            assertEquals(MergeRule.OLDER, conservative.inventory)
            assertEquals(MergeRule.HIGHER, conservative.experience)
            assertEquals(MergeRule.HIGHER, conservative.health)
            assertEquals(MergeRule.UNION, conservative.potionEffects)
        }

        @Test
        @DisplayName("AGGRESSIVE should prioritize newer data")
        fun testAggressiveValues() {
            val aggressive = MergeRules.AGGRESSIVE

            assertEquals(MergeRule.NEWER, aggressive.inventory)
            assertEquals(MergeRule.NEWER, aggressive.experience)
            assertEquals(MergeRule.NEWER, aggressive.health)
            assertEquals(MergeRule.NEWER, aggressive.potionEffects)
        }
    }

    @Nested
    @DisplayName("MergeRules Construction")
    inner class MergeRulesConstructionTests {

        @Test
        @DisplayName("should use default values when constructed with no arguments")
        fun testNoArgConstructor() {
            val rules = MergeRules()

            assertEquals(MergeRule.NEWER, rules.inventory)
            assertEquals(MergeRule.HIGHER, rules.experience)
            assertEquals(MergeRule.CURRENT_SERVER, rules.health)
            assertEquals(MergeRule.UNION, rules.potionEffects)
        }

        @Test
        @DisplayName("should allow custom values")
        fun testCustomValues() {
            val rules = MergeRules(
                inventory = MergeRule.OLDER,
                experience = MergeRule.LOWER,
                health = MergeRule.NEWER,
                potionEffects = MergeRule.INTERSECTION
            )

            assertEquals(MergeRule.OLDER, rules.inventory)
            assertEquals(MergeRule.LOWER, rules.experience)
            assertEquals(MergeRule.NEWER, rules.health)
            assertEquals(MergeRule.INTERSECTION, rules.potionEffects)
        }

        @Test
        @DisplayName("should allow partial custom values")
        fun testPartialCustomValues() {
            val rules = MergeRules(
                experience = MergeRule.LOWER
            )

            // Should use defaults for unspecified values
            assertEquals(MergeRule.NEWER, rules.inventory)
            assertEquals(MergeRule.LOWER, rules.experience)
            assertEquals(MergeRule.CURRENT_SERVER, rules.health)
            assertEquals(MergeRule.UNION, rules.potionEffects)
        }
    }

    @Nested
    @DisplayName("MergeRules Equality")
    inner class MergeRulesEqualityTests {

        @Test
        @DisplayName("identical rules should be equal")
        fun testEquality() {
            val rules1 = MergeRules(MergeRule.NEWER, MergeRule.HIGHER, MergeRule.CURRENT_SERVER, MergeRule.UNION)
            val rules2 = MergeRules(MergeRule.NEWER, MergeRule.HIGHER, MergeRule.CURRENT_SERVER, MergeRule.UNION)

            assertEquals(rules1, rules2)
        }

        @Test
        @DisplayName("different rules should not be equal")
        fun testInequality() {
            val rules1 = MergeRules(inventory = MergeRule.NEWER)
            val rules2 = MergeRules(inventory = MergeRule.OLDER)

            assertNotEquals(rules1, rules2)
        }

        @Test
        @DisplayName("DEFAULT should equal a new instance with same values")
        fun testDefaultEquality() {
            val explicit = MergeRules(
                inventory = MergeRule.NEWER,
                experience = MergeRule.HIGHER,
                health = MergeRule.CURRENT_SERVER,
                potionEffects = MergeRule.UNION
            )

            assertEquals(MergeRules.DEFAULT, explicit)
        }
    }

    @Nested
    @DisplayName("MergeRules Copy")
    inner class MergeRulesCopyTests {

        @Test
        @DisplayName("copy should work correctly")
        fun testCopy() {
            val original = MergeRules.DEFAULT
            val modified = original.copy(health = MergeRule.NEWER)

            assertEquals(MergeRule.NEWER, original.inventory) // Unchanged
            assertEquals(MergeRule.CURRENT_SERVER, original.health) // Unchanged

            assertEquals(MergeRule.NEWER, modified.inventory) // Same as original
            assertEquals(MergeRule.NEWER, modified.health) // Changed
        }
    }
}

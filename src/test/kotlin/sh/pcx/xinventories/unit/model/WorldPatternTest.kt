package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import sh.pcx.xinventories.internal.model.WorldPattern

@DisplayName("WorldPattern")
class WorldPatternTest {

    @Nested
    @DisplayName("Pattern Compilation")
    inner class PatternCompilation {

        @Test
        @DisplayName("fromString creates WorldPattern from valid regex")
        fun fromStringCreatesPatternFromValidRegex() {
            val pattern = WorldPattern.fromString("world.*")

            assertNotNull(pattern)
            assertEquals("world.*", pattern!!.pattern)
        }

        @Test
        @DisplayName("fromString returns null for invalid regex")
        fun fromStringReturnsNullForInvalidRegex() {
            val pattern = WorldPattern.fromString("[invalid")

            assertNull(pattern)
        }

        @Test
        @DisplayName("fromStringOrThrow creates WorldPattern from valid regex")
        fun fromStringOrThrowCreatesPatternFromValidRegex() {
            val pattern = WorldPattern.fromStringOrThrow("world.*")

            assertEquals("world.*", pattern.pattern)
        }

        @Test
        @DisplayName("fromStringOrThrow throws for invalid regex")
        fun fromStringOrThrowThrowsForInvalidRegex() {
            val exception = assertThrows<IllegalArgumentException> {
                WorldPattern.fromStringOrThrow("[invalid")
            }

            assertTrue(exception.message!!.contains("Invalid regex pattern"))
            assertTrue(exception.message!!.contains("[invalid"))
        }

        @Test
        @DisplayName("fromString preserves exact pattern string")
        fun fromStringPreservesExactPatternString() {
            val patternString = "^(world|world_nether|world_the_end)$"
            val pattern = WorldPattern.fromString(patternString)

            assertNotNull(pattern)
            assertEquals(patternString, pattern!!.pattern)
        }
    }

    @Nested
    @DisplayName("World Name Matching - Positive Cases")
    inner class MatchingPositiveCases {

        @Test
        @DisplayName("matches exact world name")
        fun matchesExactWorldName() {
            val pattern = WorldPattern.fromStringOrThrow("^world$")

            assertTrue(pattern.matches("world"))
        }

        @Test
        @DisplayName("matches with wildcard pattern")
        fun matchesWithWildcardPattern() {
            val pattern = WorldPattern.fromStringOrThrow("world.*")

            assertTrue(pattern.matches("world"))
            assertTrue(pattern.matches("world_nether"))
            assertTrue(pattern.matches("world_the_end"))
            assertTrue(pattern.matches("world123"))
        }

        @Test
        @DisplayName("matches with prefix pattern")
        fun matchesWithPrefixPattern() {
            val pattern = WorldPattern.fromStringOrThrow("^survival_.*")

            assertTrue(pattern.matches("survival_world"))
            assertTrue(pattern.matches("survival_nether"))
            assertTrue(pattern.matches("survival_end"))
        }

        @Test
        @DisplayName("matches with suffix pattern")
        fun matchesWithSuffixPattern() {
            val pattern = WorldPattern.fromStringOrThrow(".*_nether$")

            assertTrue(pattern.matches("world_nether"))
            assertTrue(pattern.matches("survival_nether"))
            assertTrue(pattern.matches("creative_nether"))
        }

        @Test
        @DisplayName("matches with alternation pattern")
        fun matchesWithAlternationPattern() {
            val pattern = WorldPattern.fromStringOrThrow("^(world|survival|creative)$")

            assertTrue(pattern.matches("world"))
            assertTrue(pattern.matches("survival"))
            assertTrue(pattern.matches("creative"))
        }

        @ParameterizedTest
        @CsvSource(
            "world.*, world",
            "world.*, world_nether",
            ".*nether.*, world_nether",
            "^world$, world",
            "survival_.+, survival_world"
        )
        @DisplayName("matches various pattern combinations")
        fun matchesVariousPatternCombinations(patternString: String, worldName: String) {
            val pattern = WorldPattern.fromStringOrThrow(patternString)

            assertTrue(pattern.matches(worldName))
        }
    }

    @Nested
    @DisplayName("World Name Matching - Negative Cases")
    inner class MatchingNegativeCases {

        @Test
        @DisplayName("does not match different world name")
        fun doesNotMatchDifferentWorldName() {
            val pattern = WorldPattern.fromStringOrThrow("^world$")

            assertFalse(pattern.matches("survival"))
            assertFalse(pattern.matches("creative"))
        }

        @Test
        @DisplayName("does not match partial with anchored pattern")
        fun doesNotMatchPartialWithAnchoredPattern() {
            val pattern = WorldPattern.fromStringOrThrow("^world$")

            assertFalse(pattern.matches("world_nether"))
            assertFalse(pattern.matches("myworld"))
        }

        @Test
        @DisplayName("does not match when prefix is different")
        fun doesNotMatchWhenPrefixIsDifferent() {
            val pattern = WorldPattern.fromStringOrThrow("^survival_.*")

            assertFalse(pattern.matches("creative_world"))
            assertFalse(pattern.matches("world"))
            assertFalse(pattern.matches("my_survival_world"))
        }

        @Test
        @DisplayName("does not match empty string unless pattern allows")
        fun doesNotMatchEmptyStringUnlessPatternAllows() {
            val pattern = WorldPattern.fromStringOrThrow("world.+")

            assertFalse(pattern.matches(""))
            assertFalse(pattern.matches("world")) // .+ requires at least one char
        }

        @ParameterizedTest
        @CsvSource(
            "^world$, world_nether",
            "^survival_.+, survival_",
            ".*_nether$, world_end",
            "^(world|survival)$, creative"
        )
        @DisplayName("does not match various non-matching combinations")
        fun doesNotMatchVariousNonMatchingCombinations(patternString: String, worldName: String) {
            val pattern = WorldPattern.fromStringOrThrow(patternString)

            assertFalse(pattern.matches(worldName))
        }
    }

    @Nested
    @DisplayName("Special Regex Characters Handling")
    inner class SpecialCharactersHandling {

        @Test
        @DisplayName("handles dot character correctly")
        fun handlesDotCharacterCorrectly() {
            // Unescaped dot matches any character
            val pattern = WorldPattern.fromStringOrThrow("world.nether")

            assertTrue(pattern.matches("world_nether"))
            assertTrue(pattern.matches("world-nether"))
            assertTrue(pattern.matches("world.nether"))
        }

        @Test
        @DisplayName("handles escaped dot for literal match")
        fun handlesEscapedDotForLiteralMatch() {
            val pattern = WorldPattern.fromStringOrThrow("world\\.nether")

            assertTrue(pattern.matches("world.nether"))
            assertFalse(pattern.matches("world_nether"))
        }

        @Test
        @DisplayName("handles character classes")
        fun handlesCharacterClasses() {
            val pattern = WorldPattern.fromStringOrThrow("world_[0-9]+")

            assertTrue(pattern.matches("world_1"))
            assertTrue(pattern.matches("world_123"))
            assertFalse(pattern.matches("world_abc"))
        }

        @Test
        @DisplayName("handles question mark quantifier")
        fun handlesQuestionMarkQuantifier() {
            val pattern = WorldPattern.fromStringOrThrow("world_?nether")

            assertTrue(pattern.matches("worldnether"))
            assertTrue(pattern.matches("world_nether"))
            assertFalse(pattern.matches("world__nether"))
        }

        @Test
        @DisplayName("handles plus quantifier")
        fun handlesPlusQuantifier() {
            val pattern = WorldPattern.fromStringOrThrow("world_+nether")

            assertTrue(pattern.matches("world_nether"))
            assertTrue(pattern.matches("world__nether"))
            assertFalse(pattern.matches("worldnether"))
        }

        @Test
        @DisplayName("handles word boundaries")
        fun handlesWordBoundaries() {
            val pattern = WorldPattern.fromStringOrThrow("\\bworld\\b")

            assertTrue(pattern.matches("world"))
            assertFalse(pattern.matches("worldnether"))
        }

        @Test
        @DisplayName("handles pipe for alternation")
        fun handlesPipeForAlternation() {
            val pattern = WorldPattern.fromStringOrThrow("world|survival|creative")

            assertTrue(pattern.matches("world"))
            assertTrue(pattern.matches("survival"))
            assertTrue(pattern.matches("creative"))
        }
    }

    @Nested
    @DisplayName("Invalid Pattern Handling")
    inner class InvalidPatternHandling {

        @ParameterizedTest
        @ValueSource(strings = [
            "[invalid",
            "(unclosed",
            "*invalid",
            "+invalid",
            "?invalid",
            "[z-a]",
            "\\",
            "(?invalid)"
        ])
        @DisplayName("fromString returns null for various invalid patterns")
        fun fromStringReturnsNullForVariousInvalidPatterns(invalidPattern: String) {
            val pattern = WorldPattern.fromString(invalidPattern)

            assertNull(pattern)
        }

        @ParameterizedTest
        @ValueSource(strings = [
            "[invalid",
            "(unclosed",
            "*invalid"
        ])
        @DisplayName("fromStringOrThrow throws for various invalid patterns")
        fun fromStringOrThrowThrowsForVariousInvalidPatterns(invalidPattern: String) {
            assertThrows<IllegalArgumentException> {
                WorldPattern.fromStringOrThrow(invalidPattern)
            }
        }
    }

    @Nested
    @DisplayName("Case Sensitivity")
    inner class CaseSensitivity {

        @Test
        @DisplayName("matching is case sensitive by default")
        fun matchingIsCaseSensitiveByDefault() {
            val pattern = WorldPattern.fromStringOrThrow("^World$")

            assertTrue(pattern.matches("World"))
            assertFalse(pattern.matches("world"))
            assertFalse(pattern.matches("WORLD"))
        }

        @Test
        @DisplayName("case insensitive matching with regex flag")
        fun caseInsensitiveMatchingWithRegexFlag() {
            val pattern = WorldPattern.fromStringOrThrow("(?i)^world$")

            assertTrue(pattern.matches("world"))
            assertTrue(pattern.matches("World"))
            assertTrue(pattern.matches("WORLD"))
            assertTrue(pattern.matches("WoRlD"))
        }

        @Test
        @DisplayName("mixed case pattern matches exactly")
        fun mixedCasePatternMatchesExactly() {
            val pattern = WorldPattern.fromStringOrThrow("^SkyBlock_.*")

            assertTrue(pattern.matches("SkyBlock_island"))
            assertFalse(pattern.matches("skyblock_island"))
            assertFalse(pattern.matches("SKYBLOCK_island"))
        }

        @Test
        @DisplayName("character class with case")
        fun characterClassWithCase() {
            val pattern = WorldPattern.fromStringOrThrow("^[Ww]orld$")

            assertTrue(pattern.matches("World"))
            assertTrue(pattern.matches("world"))
            assertFalse(pattern.matches("WORLD"))
        }
    }

    @Nested
    @DisplayName("Data Class Behavior")
    inner class DataClassBehavior {

        @Test
        @DisplayName("equals compares pattern strings")
        fun equalsComparesPatternStrings() {
            val pattern1 = WorldPattern.fromStringOrThrow("world.*")
            val pattern2 = WorldPattern.fromStringOrThrow("world.*")
            val pattern3 = WorldPattern.fromStringOrThrow("survival.*")

            assertEquals(pattern1, pattern2)
            assertNotEquals(pattern1, pattern3)
        }

        @Test
        @DisplayName("hashCode is consistent")
        fun hashCodeIsConsistent() {
            val pattern1 = WorldPattern.fromStringOrThrow("world.*")
            val pattern2 = WorldPattern.fromStringOrThrow("world.*")

            assertEquals(pattern1.hashCode(), pattern2.hashCode())
        }

        @Test
        @DisplayName("toString includes pattern")
        fun toStringIncludesPattern() {
            val pattern = WorldPattern.fromStringOrThrow("world.*")

            assertTrue(pattern.toString().contains("world.*"))
        }
    }
}

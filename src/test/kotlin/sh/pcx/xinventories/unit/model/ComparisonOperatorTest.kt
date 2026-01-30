package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.ComparisonOperator

@DisplayName("ComparisonOperator")
class ComparisonOperatorTest {

    @Nested
    @DisplayName("Numeric Comparison")
    inner class NumericComparison {

        @Test
        @DisplayName("EQUALS compares numeric values")
        fun equalsComparesNumericValues() {
            assertTrue(ComparisonOperator.EQUALS.evaluate("50", "50"))
            assertTrue(ComparisonOperator.EQUALS.evaluate("50.0", "50"))
            assertFalse(ComparisonOperator.EQUALS.evaluate("50", "51"))
        }

        @Test
        @DisplayName("NOT_EQUALS compares numeric values")
        fun notEqualsComparesNumericValues() {
            assertTrue(ComparisonOperator.NOT_EQUALS.evaluate("50", "51"))
            assertFalse(ComparisonOperator.NOT_EQUALS.evaluate("50", "50"))
        }

        @Test
        @DisplayName("GREATER_THAN compares numeric values")
        fun greaterThanComparesNumericValues() {
            assertTrue(ComparisonOperator.GREATER_THAN.evaluate("51", "50"))
            assertFalse(ComparisonOperator.GREATER_THAN.evaluate("50", "50"))
            assertFalse(ComparisonOperator.GREATER_THAN.evaluate("49", "50"))
        }

        @Test
        @DisplayName("LESS_THAN compares numeric values")
        fun lessThanComparesNumericValues() {
            assertTrue(ComparisonOperator.LESS_THAN.evaluate("49", "50"))
            assertFalse(ComparisonOperator.LESS_THAN.evaluate("50", "50"))
            assertFalse(ComparisonOperator.LESS_THAN.evaluate("51", "50"))
        }

        @Test
        @DisplayName("GREATER_OR_EQUAL compares numeric values")
        fun greaterOrEqualComparesNumericValues() {
            assertTrue(ComparisonOperator.GREATER_OR_EQUAL.evaluate("51", "50"))
            assertTrue(ComparisonOperator.GREATER_OR_EQUAL.evaluate("50", "50"))
            assertFalse(ComparisonOperator.GREATER_OR_EQUAL.evaluate("49", "50"))
        }

        @Test
        @DisplayName("LESS_OR_EQUAL compares numeric values")
        fun lessOrEqualComparesNumericValues() {
            assertTrue(ComparisonOperator.LESS_OR_EQUAL.evaluate("49", "50"))
            assertTrue(ComparisonOperator.LESS_OR_EQUAL.evaluate("50", "50"))
            assertFalse(ComparisonOperator.LESS_OR_EQUAL.evaluate("51", "50"))
        }

        @Test
        @DisplayName("handles decimal numbers")
        fun handlesDecimalNumbers() {
            assertTrue(ComparisonOperator.GREATER_THAN.evaluate("50.5", "50.4"))
            assertTrue(ComparisonOperator.EQUALS.evaluate("3.14159", "3.14159"))
        }

        @Test
        @DisplayName("handles negative numbers")
        fun handlesNegativeNumbers() {
            assertTrue(ComparisonOperator.GREATER_THAN.evaluate("-5", "-10"))
            assertTrue(ComparisonOperator.LESS_THAN.evaluate("-10", "-5"))
        }
    }

    @Nested
    @DisplayName("String Comparison")
    inner class StringComparison {

        @Test
        @DisplayName("EQUALS compares strings case-insensitive")
        fun equalsComparesStringsCaseInsensitive() {
            assertTrue(ComparisonOperator.EQUALS.evaluate("Hello", "hello"))
            assertTrue(ComparisonOperator.EQUALS.evaluate("VIP", "vip"))
            assertFalse(ComparisonOperator.EQUALS.evaluate("Hello", "World"))
        }

        @Test
        @DisplayName("NOT_EQUALS compares strings case-insensitive")
        fun notEqualsComparesStringsCaseInsensitive() {
            assertTrue(ComparisonOperator.NOT_EQUALS.evaluate("Hello", "World"))
            assertFalse(ComparisonOperator.NOT_EQUALS.evaluate("Hello", "hello"))
        }

        @Test
        @DisplayName("CONTAINS checks substring")
        fun containsChecksSubstring() {
            assertTrue(ComparisonOperator.CONTAINS.evaluate("Hello World", "World"))
            assertTrue(ComparisonOperator.CONTAINS.evaluate("Hello World", "world"))
            assertFalse(ComparisonOperator.CONTAINS.evaluate("Hello", "World"))
        }

        @Test
        @DisplayName("GREATER_THAN compares strings lexicographically")
        fun greaterThanComparesStringsLexicographically() {
            assertTrue(ComparisonOperator.GREATER_THAN.evaluate("b", "a"))
            assertFalse(ComparisonOperator.GREATER_THAN.evaluate("a", "b"))
        }
    }

    @Nested
    @DisplayName("fromString Parsing")
    inner class FromStringParsing {

        @Test
        @DisplayName("parses enum names")
        fun parsesEnumNames() {
            assertEquals(ComparisonOperator.EQUALS, ComparisonOperator.fromString("EQUALS"))
            assertEquals(ComparisonOperator.NOT_EQUALS, ComparisonOperator.fromString("NOT_EQUALS"))
            assertEquals(ComparisonOperator.GREATER_THAN, ComparisonOperator.fromString("GREATER_THAN"))
            assertEquals(ComparisonOperator.LESS_THAN, ComparisonOperator.fromString("LESS_THAN"))
            assertEquals(ComparisonOperator.GREATER_OR_EQUAL, ComparisonOperator.fromString("GREATER_OR_EQUAL"))
            assertEquals(ComparisonOperator.LESS_OR_EQUAL, ComparisonOperator.fromString("LESS_OR_EQUAL"))
            assertEquals(ComparisonOperator.CONTAINS, ComparisonOperator.fromString("CONTAINS"))
        }

        @Test
        @DisplayName("parses symbols")
        fun parsesSymbols() {
            assertEquals(ComparisonOperator.EQUALS, ComparisonOperator.fromString("="))
            assertEquals(ComparisonOperator.EQUALS, ComparisonOperator.fromString("=="))
            assertEquals(ComparisonOperator.NOT_EQUALS, ComparisonOperator.fromString("!="))
            assertEquals(ComparisonOperator.NOT_EQUALS, ComparisonOperator.fromString("<>"))
            assertEquals(ComparisonOperator.GREATER_THAN, ComparisonOperator.fromString(">"))
            assertEquals(ComparisonOperator.LESS_THAN, ComparisonOperator.fromString("<"))
            assertEquals(ComparisonOperator.GREATER_OR_EQUAL, ComparisonOperator.fromString(">="))
            assertEquals(ComparisonOperator.LESS_OR_EQUAL, ComparisonOperator.fromString("<="))
        }

        @Test
        @DisplayName("is case-insensitive")
        fun isCaseInsensitive() {
            assertEquals(ComparisonOperator.EQUALS, ComparisonOperator.fromString("equals"))
            assertEquals(ComparisonOperator.GREATER_THAN, ComparisonOperator.fromString("Greater_Than"))
        }

        @Test
        @DisplayName("trims whitespace")
        fun trimsWhitespace() {
            assertEquals(ComparisonOperator.EQUALS, ComparisonOperator.fromString("  EQUALS  "))
            assertEquals(ComparisonOperator.GREATER_THAN, ComparisonOperator.fromString(" > "))
        }

        @Test
        @DisplayName("returns null for invalid input")
        fun returnsNullForInvalidInput() {
            Assertions.assertNull(ComparisonOperator.fromString("invalid"))
            Assertions.assertNull(ComparisonOperator.fromString(""))
            Assertions.assertNull(ComparisonOperator.fromString("==="))
        }
    }
}

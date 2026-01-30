package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.ComparisonOperator
import sh.pcx.xinventories.internal.model.PlaceholderCondition

@DisplayName("PlaceholderCondition")
class PlaceholderConditionTest {

    @Nested
    @DisplayName("Evaluation")
    inner class Evaluation {

        @Test
        @DisplayName("evaluates numeric condition correctly")
        fun evaluatesNumericConditionCorrectly() {
            val condition = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )

            assertTrue(condition.evaluate("50"))
            assertTrue(condition.evaluate("100"))
            assertFalse(condition.evaluate("49"))
        }

        @Test
        @DisplayName("evaluates string condition correctly")
        fun evaluatesStringConditionCorrectly() {
            val condition = PlaceholderCondition(
                placeholder = "%player_rank%",
                operator = ComparisonOperator.EQUALS,
                value = "VIP"
            )

            assertTrue(condition.evaluate("VIP"))
            assertTrue(condition.evaluate("vip"))
            assertFalse(condition.evaluate("Regular"))
        }

        @Test
        @DisplayName("evaluates contains condition correctly")
        fun evaluatesContainsConditionCorrectly() {
            val condition = PlaceholderCondition(
                placeholder = "%player_groups%",
                operator = ComparisonOperator.CONTAINS,
                value = "admin"
            )

            assertTrue(condition.evaluate("admin,moderator,vip"))
            assertTrue(condition.evaluate("Admin"))
            assertFalse(condition.evaluate("moderator,vip"))
        }
    }

    @Nested
    @DisplayName("toDisplayString")
    inner class ToDisplayString {

        @Test
        @DisplayName("formats condition as readable string")
        fun formatsConditionAsReadableString() {
            val condition = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )

            assertEquals("%player_level% >= 50", condition.toDisplayString())
        }

        @Test
        @DisplayName("uses correct symbols for each operator")
        fun usesCorrectSymbolsForEachOperator() {
            assertEquals(
                "%p% = v",
                PlaceholderCondition("%p%", ComparisonOperator.EQUALS, "v").toDisplayString()
            )
            assertEquals(
                "%p% != v",
                PlaceholderCondition("%p%", ComparisonOperator.NOT_EQUALS, "v").toDisplayString()
            )
            assertEquals(
                "%p% > v",
                PlaceholderCondition("%p%", ComparisonOperator.GREATER_THAN, "v").toDisplayString()
            )
            assertEquals(
                "%p% < v",
                PlaceholderCondition("%p%", ComparisonOperator.LESS_THAN, "v").toDisplayString()
            )
            assertEquals(
                "%p% >= v",
                PlaceholderCondition("%p%", ComparisonOperator.GREATER_OR_EQUAL, "v").toDisplayString()
            )
            assertEquals(
                "%p% <= v",
                PlaceholderCondition("%p%", ComparisonOperator.LESS_OR_EQUAL, "v").toDisplayString()
            )
            assertEquals(
                "%p% contains v",
                PlaceholderCondition("%p%", ComparisonOperator.CONTAINS, "v").toDisplayString()
            )
        }
    }

    @Nested
    @DisplayName("fromConfig")
    inner class FromConfig {

        @Test
        @DisplayName("creates condition from config values")
        fun createsConditionFromConfigValues() {
            val condition = PlaceholderCondition.fromConfig(
                placeholder = "%player_level%",
                operatorStr = "GREATER_OR_EQUAL",
                value = "50"
            )

            Assertions.assertNotNull(condition)
            assertEquals("%player_level%", condition!!.placeholder)
            assertEquals(ComparisonOperator.GREATER_OR_EQUAL, condition.operator)
            assertEquals("50", condition.value)
        }

        @Test
        @DisplayName("accepts operator symbols")
        fun acceptsOperatorSymbols() {
            val condition = PlaceholderCondition.fromConfig(
                placeholder = "%player_level%",
                operatorStr = ">=",
                value = "50"
            )

            Assertions.assertNotNull(condition)
            assertEquals(ComparisonOperator.GREATER_OR_EQUAL, condition!!.operator)
        }

        @Test
        @DisplayName("returns null for invalid operator")
        fun returnsNullForInvalidOperator() {
            val condition = PlaceholderCondition.fromConfig(
                placeholder = "%player_level%",
                operatorStr = "INVALID",
                value = "50"
            )

            Assertions.assertNull(condition)
        }
    }

    @Nested
    @DisplayName("parse")
    inner class Parse {

        @Test
        @DisplayName("parses expression with >= operator")
        fun parsesExpressionWithGreaterOrEqual() {
            val condition = PlaceholderCondition.parse("%player_level% >= 50")

            Assertions.assertNotNull(condition)
            assertEquals("%player_level%", condition!!.placeholder)
            assertEquals(ComparisonOperator.GREATER_OR_EQUAL, condition.operator)
            assertEquals("50", condition.value)
        }

        @Test
        @DisplayName("parses expression with = operator")
        fun parsesExpressionWithEquals() {
            val condition = PlaceholderCondition.parse("%player_rank% = VIP")

            Assertions.assertNotNull(condition)
            assertEquals("%player_rank%", condition!!.placeholder)
            assertEquals(ComparisonOperator.EQUALS, condition.operator)
            assertEquals("VIP", condition.value)
        }

        @Test
        @DisplayName("parses expression with contains operator")
        fun parsesExpressionWithContains() {
            val condition = PlaceholderCondition.parse("%player_groups% contains admin")

            Assertions.assertNotNull(condition)
            assertEquals("%player_groups%", condition!!.placeholder)
            assertEquals(ComparisonOperator.CONTAINS, condition.operator)
            assertEquals("admin", condition.value)
        }

        @Test
        @DisplayName("handles whitespace in expression")
        fun handlesWhitespaceInExpression() {
            val condition = PlaceholderCondition.parse("  %player_level%   >=   50  ")

            Assertions.assertNotNull(condition)
            assertEquals("%player_level%", condition!!.placeholder)
            assertEquals("50", condition.value)
        }

        @Test
        @DisplayName("returns null for invalid expression")
        fun returnsNullForInvalidExpression() {
            Assertions.assertNull(PlaceholderCondition.parse("invalid"))
            Assertions.assertNull(PlaceholderCondition.parse(""))
            Assertions.assertNull(PlaceholderCondition.parse("no operator here"))
        }

        @Test
        @DisplayName("prefers longer operators to avoid false matches")
        fun prefersLongerOperators() {
            // Should match >= not just >
            val condition = PlaceholderCondition.parse("%level% >= 50")

            Assertions.assertNotNull(condition)
            assertEquals(ComparisonOperator.GREATER_OR_EQUAL, condition!!.operator)
        }
    }
}

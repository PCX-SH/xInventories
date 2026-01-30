package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import sh.pcx.xinventories.internal.model.ComparisonOperator
import sh.pcx.xinventories.internal.model.GroupConditions
import sh.pcx.xinventories.internal.model.PlaceholderCondition
import sh.pcx.xinventories.internal.model.TimeRange
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Unit tests for ConditionalGroupsGUI logic.
 *
 * These tests verify the group conditions logic that powers the GUI,
 * without requiring full Bukkit/server infrastructure.
 */
@DisplayName("ConditionalGroupsGUI Logic Tests")
class ConditionalGroupsGUITest {

    // ============================================================
    // Permission Condition Tests
    // ============================================================

    @Nested
    @DisplayName("Permission Condition Logic")
    inner class PermissionConditionTests {

        @Test
        @DisplayName("should create condition with permission")
        fun createWithPermission() {
            val conditions = GroupConditions.withPermission("xinventories.group.vip")

            assertEquals("xinventories.group.vip", conditions.permission)
            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("should update permission")
        fun updatePermission() {
            val conditions = GroupConditions.withPermission("old.permission")

            val updated = conditions.copy(permission = "new.permission")

            assertEquals("new.permission", updated.permission)
        }

        @Test
        @DisplayName("should remove permission")
        fun removePermission() {
            val conditions = GroupConditions.withPermission("some.permission")

            val updated = conditions.copy(permission = null)

            assertNull(updated.permission)
        }
    }

    // ============================================================
    // Schedule Condition Tests
    // ============================================================

    @Nested
    @DisplayName("Schedule Condition Logic")
    inner class ScheduleConditionTests {

        @Test
        @DisplayName("should create time range")
        fun createTimeRange() {
            val start = Instant.now()
            val end = start.plus(Duration.ofHours(2))
            val range = TimeRange(start, end)

            assertNotNull(range)
            assertEquals(start, range.start)
            assertEquals(end, range.end)
        }

        @Test
        @DisplayName("should check if time range is active")
        fun checkTimeRangeActive() {
            val now = Instant.now()
            val activeRange = TimeRange(
                start = now.minus(Duration.ofHours(1)),
                end = now.plus(Duration.ofHours(1))
            )
            val inactiveRange = TimeRange(
                start = now.plus(Duration.ofHours(1)),
                end = now.plus(Duration.ofHours(2))
            )

            assertTrue(activeRange.isActive())
            assertFalse(inactiveRange.isActive())
        }

        @Test
        @DisplayName("should add schedule to conditions")
        fun addSchedule() {
            val range = TimeRange(
                start = Instant.now(),
                end = Instant.now().plus(Duration.ofHours(2))
            )
            val conditions = GroupConditions.withSchedule(range)

            assertNotNull(conditions.schedule)
            assertEquals(1, conditions.schedule!!.size)
            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("should add multiple schedules")
        fun addMultipleSchedules() {
            val range1 = TimeRange(
                start = Instant.now(),
                end = Instant.now().plus(Duration.ofHours(2))
            )
            val range2 = TimeRange(
                start = Instant.now().plus(Duration.ofDays(1)),
                end = Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(2))
            )
            val conditions = GroupConditions.withSchedule(range1, range2)

            assertEquals(2, conditions.schedule!!.size)
        }

        @Test
        @DisplayName("should clear all schedules")
        fun clearSchedules() {
            val range = TimeRange(
                start = Instant.now(),
                end = Instant.now().plus(Duration.ofHours(2))
            )
            val conditions = GroupConditions.withSchedule(range)

            val cleared = conditions.copy(schedule = null)

            assertNull(cleared.schedule)
        }

        @Test
        @DisplayName("should parse time range from strings")
        fun parseFromStrings() {
            val range = TimeRange.fromStrings(
                "2026-01-01T00:00:00",
                "2026-12-31T23:59:59",
                "UTC"
            )

            assertNotNull(range)
        }
    }

    // ============================================================
    // Cron Condition Tests
    // ============================================================

    @Nested
    @DisplayName("Cron Condition Logic")
    inner class CronConditionTests {

        @Test
        @DisplayName("should create condition with cron")
        fun createWithCron() {
            val conditions = GroupConditions.withCron("0 18-22 * * FRI,SAT")

            assertEquals("0 18-22 * * FRI,SAT", conditions.cron)
            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("should update cron expression")
        fun updateCron() {
            val conditions = GroupConditions.withCron("0 18-22 * * FRI,SAT")

            val updated = conditions.copy(cron = "0 * * * *")

            assertEquals("0 * * * *", updated.cron)
        }

        @Test
        @DisplayName("should remove cron")
        fun removeCron() {
            val conditions = GroupConditions.withCron("0 * * * *")

            val updated = conditions.copy(cron = null)

            assertNull(updated.cron)
        }
    }

    // ============================================================
    // Placeholder Condition Tests
    // ============================================================

    @Nested
    @DisplayName("Placeholder Condition Logic")
    inner class PlaceholderConditionTests {

        @Test
        @DisplayName("should create placeholder condition")
        fun createPlaceholderCondition() {
            val condition = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )

            assertEquals("%player_level%", condition.placeholder)
            assertEquals(ComparisonOperator.GREATER_OR_EQUAL, condition.operator)
            assertEquals("50", condition.value)
        }

        @Test
        @DisplayName("should parse placeholder condition from config")
        fun parseFromConfig() {
            val condition = PlaceholderCondition.fromConfig(
                "%player_level%",
                ">=",
                "50"
            )

            assertNotNull(condition)
            assertEquals(ComparisonOperator.GREATER_OR_EQUAL, condition!!.operator)
        }

        @Test
        @DisplayName("should parse placeholder condition from expression")
        fun parseFromExpression() {
            val condition = PlaceholderCondition.parse("%player_level% >= 50")

            assertNotNull(condition)
            assertEquals("%player_level%", condition!!.placeholder)
            assertEquals(ComparisonOperator.GREATER_OR_EQUAL, condition.operator)
            assertEquals("50", condition.value)
        }

        @Test
        @DisplayName("should handle all operators")
        fun handleAllOperators() {
            val operators = listOf("=", "!=", ">", "<", ">=", "<=", "contains")
            val expectedOperators = listOf(
                ComparisonOperator.EQUALS,
                ComparisonOperator.NOT_EQUALS,
                ComparisonOperator.GREATER_THAN,
                ComparisonOperator.LESS_THAN,
                ComparisonOperator.GREATER_OR_EQUAL,
                ComparisonOperator.LESS_OR_EQUAL,
                ComparisonOperator.CONTAINS
            )

            operators.forEachIndexed { index, opStr ->
                val condition = PlaceholderCondition.fromConfig("%test%", opStr, "value")
                assertNotNull(condition, "Failed to parse operator: $opStr")
                assertEquals(expectedOperators[index], condition!!.operator)
            }
        }

        @Test
        @DisplayName("should evaluate numeric conditions")
        fun evaluateNumericConditions() {
            val condition = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )

            assertTrue(condition.evaluate("60"))
            assertTrue(condition.evaluate("50"))
            assertFalse(condition.evaluate("40"))
        }

        @Test
        @DisplayName("should evaluate string conditions")
        fun evaluateStringConditions() {
            val condition = PlaceholderCondition(
                placeholder = "%player_rank%",
                operator = ComparisonOperator.EQUALS,
                value = "VIP"
            )

            assertTrue(condition.evaluate("VIP"))
            assertTrue(condition.evaluate("vip")) // Case insensitive
            assertFalse(condition.evaluate("MEMBER"))
        }

        @Test
        @DisplayName("should evaluate contains condition")
        fun evaluateContainsCondition() {
            val condition = PlaceholderCondition(
                placeholder = "%player_group%",
                operator = ComparisonOperator.CONTAINS,
                value = "admin"
            )

            assertTrue(condition.evaluate("super_admin_group"))
            assertTrue(condition.evaluate("admin"))
            assertFalse(condition.evaluate("member"))
        }

        @Test
        @DisplayName("should display condition string")
        fun displayConditionString() {
            val condition = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )

            val display = condition.toDisplayString()

            assertTrue(display.contains("%player_level%"))
            assertTrue(display.contains(">="))
            assertTrue(display.contains("50"))
        }
    }

    // ============================================================
    // Combined Conditions Tests
    // ============================================================

    @Nested
    @DisplayName("Combined Conditions Logic")
    inner class CombinedConditionsTests {

        @Test
        @DisplayName("should add single placeholder to conditions")
        fun addSinglePlaceholder() {
            val placeholder = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            val conditions = GroupConditions(placeholder = placeholder)

            assertEquals(1, conditions.getAllPlaceholderConditions().size)
        }

        @Test
        @DisplayName("should add multiple placeholders to conditions")
        fun addMultiplePlaceholders() {
            val placeholder1 = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            val placeholder2 = PlaceholderCondition(
                placeholder = "%player_rank%",
                operator = ComparisonOperator.EQUALS,
                value = "VIP"
            )
            val conditions = GroupConditions(placeholders = listOf(placeholder1, placeholder2))

            assertEquals(2, conditions.getAllPlaceholderConditions().size)
        }

        @Test
        @DisplayName("should combine single and multiple placeholders")
        fun combinePlaceholders() {
            val single = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            val multi = listOf(
                PlaceholderCondition(
                    placeholder = "%player_rank%",
                    operator = ComparisonOperator.EQUALS,
                    value = "VIP"
                )
            )
            val conditions = GroupConditions(placeholder = single, placeholders = multi)

            assertEquals(2, conditions.getAllPlaceholderConditions().size)
        }

        @Test
        @DisplayName("should remove placeholder from conditions")
        fun removePlaceholder() {
            val placeholder1 = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            val placeholder2 = PlaceholderCondition(
                placeholder = "%player_rank%",
                operator = ComparisonOperator.EQUALS,
                value = "VIP"
            )
            val conditions = GroupConditions(placeholders = listOf(placeholder1, placeholder2))

            val remaining = conditions.getAllPlaceholderConditions().toMutableList()
            remaining.remove(placeholder1)

            assertEquals(1, remaining.size)
            assertEquals(placeholder2, remaining[0])
        }
    }

    // ============================================================
    // Require All/Any Mode Tests
    // ============================================================

    @Nested
    @DisplayName("Require Mode Logic")
    inner class RequireModeTests {

        @Test
        @DisplayName("should default to require all")
        fun defaultRequireAll() {
            val conditions = GroupConditions.empty()

            assertTrue(conditions.requireAll)
        }

        @Test
        @DisplayName("should toggle to require any")
        fun toggleToRequireAny() {
            val conditions = GroupConditions(requireAll = true)

            val toggled = conditions.copy(requireAll = false)

            assertFalse(toggled.requireAll)
        }

        @Test
        @DisplayName("should toggle back to require all")
        fun toggleBackToRequireAll() {
            val conditions = GroupConditions(requireAll = false)

            val toggled = conditions.copy(requireAll = true)

            assertTrue(toggled.requireAll)
        }
    }

    // ============================================================
    // Has Conditions Tests
    // ============================================================

    @Nested
    @DisplayName("Has Conditions Check")
    inner class HasConditionsTests {

        @Test
        @DisplayName("should return false for empty conditions")
        fun emptyConditions() {
            val conditions = GroupConditions.empty()

            assertFalse(conditions.hasConditions())
        }

        @Test
        @DisplayName("should return true for permission condition")
        fun hasPermission() {
            val conditions = GroupConditions.withPermission("test")

            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("should return true for schedule condition")
        fun hasSchedule() {
            val range = TimeRange(
                start = Instant.now(),
                end = Instant.now().plus(Duration.ofHours(2))
            )
            val conditions = GroupConditions.withSchedule(range)

            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("should return true for cron condition")
        fun hasCron() {
            val conditions = GroupConditions.withCron("0 * * * *")

            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("should return true for placeholder condition")
        fun hasPlaceholder() {
            val placeholder = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            val conditions = GroupConditions(placeholder = placeholder)

            assertTrue(conditions.hasConditions())
        }
    }

    // ============================================================
    // Display String Tests
    // ============================================================

    @Nested
    @DisplayName("Display String Generation")
    inner class DisplayStringTests {

        @Test
        @DisplayName("should generate display string for no conditions")
        fun noConditionsDisplay() {
            val conditions = GroupConditions.empty()

            assertEquals("No conditions", conditions.toDisplayString())
        }

        @Test
        @DisplayName("should generate display string with permission")
        fun permissionDisplay() {
            val conditions = GroupConditions.withPermission("test.permission")

            val display = conditions.toDisplayString()

            assertTrue(display.contains("permission"))
            assertTrue(display.contains("test.permission"))
        }

        @Test
        @DisplayName("should show AND for require all")
        fun requireAllDisplay() {
            val conditions = GroupConditions(
                permission = "test",
                cron = "0 * * * *",
                requireAll = true
            )

            val display = conditions.toDisplayString()

            assertTrue(display.contains("AND"))
        }

        @Test
        @DisplayName("should show OR for require any")
        fun requireAnyDisplay() {
            val conditions = GroupConditions(
                permission = "test",
                cron = "0 * * * *",
                requireAll = false
            )

            val display = conditions.toDisplayString()

            assertTrue(display.contains("OR"))
        }
    }

    // ============================================================
    // Config Immutability Tests
    // ============================================================

    @Nested
    @DisplayName("Config Immutability")
    inner class ImmutabilityTests {

        @Test
        @DisplayName("should not modify original when copying")
        fun immutableOnCopy() {
            val original = GroupConditions.withPermission("original")

            val modified = original.copy(permission = "modified")

            assertEquals("original", original.permission)
            assertEquals("modified", modified.permission)
        }

        @Test
        @DisplayName("should not modify schedule list")
        fun immutableScheduleList() {
            val range = TimeRange(
                start = Instant.now(),
                end = Instant.now().plus(Duration.ofHours(2))
            )
            val original = GroupConditions.withSchedule(range)
            val originalSize = original.schedule!!.size

            // Adding to a copy
            val newRange = TimeRange(
                start = Instant.now().plus(Duration.ofDays(1)),
                end = Instant.now().plus(Duration.ofDays(1)).plus(Duration.ofHours(2))
            )
            val newSchedule = original.schedule!!.toMutableList()
            newSchedule.add(newRange)
            val modified = original.copy(schedule = newSchedule)

            assertEquals(originalSize, original.schedule!!.size)
            assertEquals(originalSize + 1, modified.schedule!!.size)
        }
    }
}

package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.ComparisonOperator
import sh.pcx.xinventories.internal.model.GroupConditions
import sh.pcx.xinventories.internal.model.PlaceholderCondition
import sh.pcx.xinventories.internal.model.TimeRange
import java.time.Instant
import java.time.temporal.ChronoUnit

@DisplayName("GroupConditions")
class GroupConditionsTest {

    @Nested
    @DisplayName("hasConditions")
    inner class HasConditions {

        @Test
        @DisplayName("returns false for empty conditions")
        fun returnsFalseForEmptyConditions() {
            val conditions = GroupConditions()

            assertFalse(conditions.hasConditions())
        }

        @Test
        @DisplayName("returns true when permission is set")
        fun returnsTrueWhenPermissionIsSet() {
            val conditions = GroupConditions(permission = "xinventories.group.vip")

            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("returns true when schedule is set")
        fun returnsTrueWhenScheduleIsSet() {
            val range = TimeRange(
                start = Instant.now(),
                end = Instant.now().plus(1, ChronoUnit.HOURS)
            )
            val conditions = GroupConditions(schedule = listOf(range))

            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("returns true when cron is set")
        fun returnsTrueWhenCronIsSet() {
            val conditions = GroupConditions(cron = "0 18-22 * * FRI,SAT")

            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("returns true when placeholder is set")
        fun returnsTrueWhenPlaceholderIsSet() {
            val placeholder = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            val conditions = GroupConditions(placeholder = placeholder)

            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("returns true when placeholders list is set")
        fun returnsTrueWhenPlaceholdersListIsSet() {
            val placeholder = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            val conditions = GroupConditions(placeholders = listOf(placeholder))

            assertTrue(conditions.hasConditions())
        }

        @Test
        @DisplayName("returns false when schedule is empty list")
        fun returnsFalseWhenScheduleIsEmptyList() {
            val conditions = GroupConditions(schedule = emptyList())

            assertFalse(conditions.hasConditions())
        }

        @Test
        @DisplayName("returns false when placeholders is empty list")
        fun returnsFalseWhenPlaceholdersIsEmptyList() {
            val conditions = GroupConditions(placeholders = emptyList())

            assertFalse(conditions.hasConditions())
        }
    }

    @Nested
    @DisplayName("getAllPlaceholderConditions")
    inner class GetAllPlaceholderConditions {

        @Test
        @DisplayName("returns empty list when no placeholders")
        fun returnsEmptyListWhenNoPlaceholders() {
            val conditions = GroupConditions()

            assertTrue(conditions.getAllPlaceholderConditions().isEmpty())
        }

        @Test
        @DisplayName("returns single placeholder from placeholder field")
        fun returnsSinglePlaceholderFromPlaceholderField() {
            val placeholder = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            val conditions = GroupConditions(placeholder = placeholder)

            val all = conditions.getAllPlaceholderConditions()

            assertEquals(1, all.size)
            assertEquals(placeholder, all[0])
        }

        @Test
        @DisplayName("returns all placeholders from placeholders list")
        fun returnsAllPlaceholdersFromPlaceholdersList() {
            val placeholder1 = PlaceholderCondition("%level%", ComparisonOperator.GREATER_OR_EQUAL, "50")
            val placeholder2 = PlaceholderCondition("%rank%", ComparisonOperator.EQUALS, "VIP")
            val conditions = GroupConditions(placeholders = listOf(placeholder1, placeholder2))

            val all = conditions.getAllPlaceholderConditions()

            assertEquals(2, all.size)
        }

        @Test
        @DisplayName("combines placeholder and placeholders list")
        fun combinesPlaceholderAndPlaceholdersList() {
            val single = PlaceholderCondition("%level%", ComparisonOperator.GREATER_OR_EQUAL, "50")
            val list1 = PlaceholderCondition("%rank%", ComparisonOperator.EQUALS, "VIP")
            val list2 = PlaceholderCondition("%balance%", ComparisonOperator.GREATER_THAN, "1000")
            val conditions = GroupConditions(
                placeholder = single,
                placeholders = listOf(list1, list2)
            )

            val all = conditions.getAllPlaceholderConditions()

            assertEquals(3, all.size)
            assertEquals(single, all[0]) // Single placeholder first
            assertEquals(list1, all[1])
            assertEquals(list2, all[2])
        }
    }

    @Nested
    @DisplayName("toDisplayString")
    inner class ToDisplayString {

        @Test
        @DisplayName("returns 'No conditions' for empty conditions")
        fun returnsNoConditionsForEmptyConditions() {
            val conditions = GroupConditions()

            assertEquals("No conditions", conditions.toDisplayString())
        }

        @Test
        @DisplayName("includes permission in display string")
        fun includesPermissionInDisplayString() {
            val conditions = GroupConditions(permission = "xinventories.vip")

            assertTrue(conditions.toDisplayString().contains("permission: xinventories.vip"))
        }

        @Test
        @DisplayName("includes cron in display string")
        fun includesCronInDisplayString() {
            val conditions = GroupConditions(cron = "0 18 * * *")

            assertTrue(conditions.toDisplayString().contains("cron: 0 18 * * *"))
        }

        @Test
        @DisplayName("shows AND logic when requireAll is true")
        fun showsAndLogicWhenRequireAllIsTrue() {
            val conditions = GroupConditions(
                permission = "vip",
                cron = "* * * * *",
                requireAll = true
            )

            assertTrue(conditions.toDisplayString().contains("AND"))
        }

        @Test
        @DisplayName("shows OR logic when requireAll is false")
        fun showsOrLogicWhenRequireAllIsFalse() {
            val conditions = GroupConditions(
                permission = "vip",
                cron = "* * * * *",
                requireAll = false
            )

            assertTrue(conditions.toDisplayString().contains("OR"))
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    inner class FactoryMethods {

        @Test
        @DisplayName("empty creates empty conditions")
        fun emptyCreatesEmptyConditions() {
            val conditions = GroupConditions.empty()

            assertFalse(conditions.hasConditions())
            Assertions.assertNull(conditions.permission)
            Assertions.assertNull(conditions.schedule)
            Assertions.assertNull(conditions.cron)
            Assertions.assertNull(conditions.placeholder)
        }

        @Test
        @DisplayName("withPermission creates permission-only conditions")
        fun withPermissionCreatesPermissionOnlyConditions() {
            val conditions = GroupConditions.withPermission("xinventories.vip")

            assertTrue(conditions.hasConditions())
            assertEquals("xinventories.vip", conditions.permission)
            Assertions.assertNull(conditions.schedule)
            Assertions.assertNull(conditions.cron)
        }

        @Test
        @DisplayName("withSchedule creates schedule-only conditions")
        fun withScheduleCreatesScheduleOnlyConditions() {
            val range = TimeRange(Instant.now(), Instant.now().plus(1, ChronoUnit.HOURS))
            val conditions = GroupConditions.withSchedule(range)

            assertTrue(conditions.hasConditions())
            Assertions.assertNotNull(conditions.schedule)
            assertEquals(1, conditions.schedule!!.size)
            Assertions.assertNull(conditions.permission)
        }

        @Test
        @DisplayName("withCron creates cron-only conditions")
        fun withCronCreatesCronOnlyConditions() {
            val conditions = GroupConditions.withCron("0 18-22 * * FRI,SAT")

            assertTrue(conditions.hasConditions())
            assertEquals("0 18-22 * * FRI,SAT", conditions.cron)
            Assertions.assertNull(conditions.permission)
            Assertions.assertNull(conditions.schedule)
        }
    }

    @Nested
    @DisplayName("requireAll Flag")
    inner class RequireAllFlag {

        @Test
        @DisplayName("defaults to true")
        fun defaultsToTrue() {
            val conditions = GroupConditions()

            assertTrue(conditions.requireAll)
        }

        @Test
        @DisplayName("can be set to false")
        fun canBeSetToFalse() {
            val conditions = GroupConditions(requireAll = false)

            assertFalse(conditions.requireAll)
        }
    }
}

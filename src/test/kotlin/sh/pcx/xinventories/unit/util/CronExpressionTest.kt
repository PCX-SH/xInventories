package sh.pcx.xinventories.unit.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.util.CronExpression
import java.time.ZonedDateTime

@DisplayName("CronExpression")
class CronExpressionTest {

    @Nested
    @DisplayName("Parsing")
    inner class Parsing {

        @Test
        @DisplayName("parses basic wildcard expression")
        fun parsesBasicWildcardExpression() {
            val cron = CronExpression.parse("* * * * *")

            Assertions.assertNotNull(cron)
            assertEquals("* * * * *", cron!!.expression)
        }

        @Test
        @DisplayName("parses specific values")
        fun parsesSpecificValues() {
            val cron = CronExpression.parse("30 18 25 10 5")

            Assertions.assertNotNull(cron)
        }

        @Test
        @DisplayName("parses ranges")
        fun parsesRanges() {
            val cron = CronExpression.parse("0-30 9-17 * * 1-5")

            Assertions.assertNotNull(cron)
        }

        @Test
        @DisplayName("parses lists")
        fun parsesLists() {
            val cron = CronExpression.parse("0,15,30,45 * * * *")

            Assertions.assertNotNull(cron)
        }

        @Test
        @DisplayName("parses step values")
        fun parsesStepValues() {
            val cron = CronExpression.parse("*/15 * * * *")

            Assertions.assertNotNull(cron)
        }

        @Test
        @DisplayName("parses day names")
        fun parsesDayNames() {
            val cron = CronExpression.parse("0 18 * * FRI,SAT")

            Assertions.assertNotNull(cron)
        }

        @Test
        @DisplayName("parses month names")
        fun parsesMonthNames() {
            val cron = CronExpression.parse("0 0 1 JAN,JUL *")

            Assertions.assertNotNull(cron)
        }

        @Test
        @DisplayName("parses range with day names")
        fun parsesRangeWithDayNames() {
            val cron = CronExpression.parse("0 9 * * MON-FRI")

            Assertions.assertNotNull(cron)
        }

        @Test
        @DisplayName("returns null for invalid expression")
        fun returnsNullForInvalidExpression() {
            Assertions.assertNull(CronExpression.parse("invalid"))
            Assertions.assertNull(CronExpression.parse("* * *")) // Too few fields
            Assertions.assertNull(CronExpression.parse("* * * * * *")) // Too many fields
        }

        @Test
        @DisplayName("returns null for out of range values")
        fun returnsNullForOutOfRangeValues() {
            Assertions.assertNull(CronExpression.parse("60 * * * *")) // Minute out of range
            Assertions.assertNull(CronExpression.parse("* 24 * * *")) // Hour out of range
            Assertions.assertNull(CronExpression.parse("* * 32 * *")) // Day out of range
            Assertions.assertNull(CronExpression.parse("* * * 13 *")) // Month out of range
            Assertions.assertNull(CronExpression.parse("* * * * 7")) // Day of week out of range
        }
    }

    @Nested
    @DisplayName("Matching")
    inner class Matching {

        @Test
        @DisplayName("wildcard matches any value")
        fun wildcardMatchesAnyValue() {
            val cron = CronExpression.parse("* * * * *")!!

            // Should match any time
            assertTrue(cron.matches(ZonedDateTime.now()))
        }

        @Test
        @DisplayName("specific minute matches")
        fun specificMinuteMatches() {
            val cron = CronExpression.parse("30 * * * *")!!
            val time = ZonedDateTime.of(2024, 1, 1, 12, 30, 0, 0, java.time.ZoneId.systemDefault())

            assertTrue(cron.matches(time))
        }

        @Test
        @DisplayName("specific minute does not match")
        fun specificMinuteDoesNotMatch() {
            val cron = CronExpression.parse("30 * * * *")!!
            val time = ZonedDateTime.of(2024, 1, 1, 12, 31, 0, 0, java.time.ZoneId.systemDefault())

            assertFalse(cron.matches(time))
        }

        @Test
        @DisplayName("range matches values within range")
        fun rangeMatchesValuesWithinRange() {
            val cron = CronExpression.parse("0 9-17 * * *")!!

            val morning = ZonedDateTime.of(2024, 1, 1, 9, 0, 0, 0, java.time.ZoneId.systemDefault())
            val afternoon = ZonedDateTime.of(2024, 1, 1, 14, 0, 0, 0, java.time.ZoneId.systemDefault())
            val evening = ZonedDateTime.of(2024, 1, 1, 20, 0, 0, 0, java.time.ZoneId.systemDefault())

            assertTrue(cron.matches(morning))
            assertTrue(cron.matches(afternoon))
            assertFalse(cron.matches(evening))
        }

        @Test
        @DisplayName("list matches specific values")
        fun listMatchesSpecificValues() {
            val cron = CronExpression.parse("0,15,30,45 * * * *")!!

            val min0 = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, java.time.ZoneId.systemDefault())
            val min15 = ZonedDateTime.of(2024, 1, 1, 12, 15, 0, 0, java.time.ZoneId.systemDefault())
            val min10 = ZonedDateTime.of(2024, 1, 1, 12, 10, 0, 0, java.time.ZoneId.systemDefault())

            assertTrue(cron.matches(min0))
            assertTrue(cron.matches(min15))
            assertFalse(cron.matches(min10))
        }

        @Test
        @DisplayName("step values match correct intervals")
        fun stepValuesMatchCorrectIntervals() {
            val cron = CronExpression.parse("*/15 * * * *")!!

            val min0 = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, java.time.ZoneId.systemDefault())
            val min15 = ZonedDateTime.of(2024, 1, 1, 12, 15, 0, 0, java.time.ZoneId.systemDefault())
            val min30 = ZonedDateTime.of(2024, 1, 1, 12, 30, 0, 0, java.time.ZoneId.systemDefault())
            val min45 = ZonedDateTime.of(2024, 1, 1, 12, 45, 0, 0, java.time.ZoneId.systemDefault())
            val min10 = ZonedDateTime.of(2024, 1, 1, 12, 10, 0, 0, java.time.ZoneId.systemDefault())

            assertTrue(cron.matches(min0))
            assertTrue(cron.matches(min15))
            assertTrue(cron.matches(min30))
            assertTrue(cron.matches(min45))
            assertFalse(cron.matches(min10))
        }

        @Test
        @DisplayName("day of week matches specific day")
        fun dayOfWeekMatchesSpecificDay() {
            val cron = CronExpression.parse("0 12 * * FRI")!!

            // Friday January 5, 2024 at 12:00
            val friday = ZonedDateTime.of(2024, 1, 5, 12, 0, 0, 0, java.time.ZoneId.systemDefault())
            // Saturday January 6, 2024 at 12:00
            val saturday = ZonedDateTime.of(2024, 1, 6, 12, 0, 0, 0, java.time.ZoneId.systemDefault())

            assertTrue(cron.matches(friday))
            assertFalse(cron.matches(saturday))
        }

        @Test
        @DisplayName("complex expression matches correctly")
        fun complexExpressionMatchesCorrectly() {
            // Every Friday and Saturday at 6-10 PM
            val cron = CronExpression.parse("0 18-22 * * FRI,SAT")!!

            // Friday at 6 PM
            val fridayEvening = ZonedDateTime.of(2024, 1, 5, 18, 0, 0, 0, java.time.ZoneId.systemDefault())
            // Saturday at 9 PM
            val saturdayNight = ZonedDateTime.of(2024, 1, 6, 21, 0, 0, 0, java.time.ZoneId.systemDefault())
            // Friday at 5 PM (outside hour range)
            val fridayAfternoon = ZonedDateTime.of(2024, 1, 5, 17, 0, 0, 0, java.time.ZoneId.systemDefault())
            // Monday at 6 PM (wrong day)
            val mondayEvening = ZonedDateTime.of(2024, 1, 8, 18, 0, 0, 0, java.time.ZoneId.systemDefault())

            assertTrue(cron.matches(fridayEvening))
            assertTrue(cron.matches(saturdayNight))
            assertFalse(cron.matches(fridayAfternoon))
            assertFalse(cron.matches(mondayEvening))
        }
    }

    @Nested
    @DisplayName("Validation")
    inner class Validation {

        @Test
        @DisplayName("isValid returns true for valid expression")
        fun isValidReturnsTrueForValidExpression() {
            assertTrue(CronExpression.isValid("* * * * *"))
            assertTrue(CronExpression.isValid("0 18-22 * * FRI,SAT"))
            assertTrue(CronExpression.isValid("*/15 9-17 * * MON-FRI"))
        }

        @Test
        @DisplayName("isValid returns false for invalid expression")
        fun isValidReturnsFalseForInvalidExpression() {
            assertFalse(CronExpression.isValid("invalid"))
            assertFalse(CronExpression.isValid("* * *"))
            assertFalse(CronExpression.isValid("60 * * * *"))
        }
    }

    @Nested
    @DisplayName("toString")
    inner class ToStringTest {

        @Test
        @DisplayName("returns original expression")
        fun returnsOriginalExpression() {
            val expression = "0 18-22 * * FRI,SAT"
            val cron = CronExpression.parse(expression)!!

            assertEquals(expression, cron.toString())
        }
    }
}

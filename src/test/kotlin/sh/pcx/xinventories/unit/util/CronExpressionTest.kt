package sh.pcx.xinventories.unit.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.util.CronExpression
import java.time.ZoneId
import java.time.ZonedDateTime

@DisplayName("CronExpression")
class CronExpressionTest {

    @Nested
    @DisplayName("Parsing")
    inner class Parsing {

        @Nested
        @DisplayName("Basic Expressions")
        inner class BasicExpressions {

            @Test
            @DisplayName("parses basic wildcard expression")
            fun parsesBasicWildcardExpression() {
                val cron = CronExpression.parse("* * * * *")

                Assertions.assertNotNull(cron)
                assertEquals("* * * * *", cron!!.expression)
            }

            @Test
            @DisplayName("parses specific values for all fields")
            fun parsesSpecificValues() {
                val cron = CronExpression.parse("30 18 25 10 5")

                Assertions.assertNotNull(cron)
                assertEquals("30 18 25 10 5", cron!!.expression)
            }

            @Test
            @DisplayName("parses expression with extra whitespace")
            fun parsesExpressionWithExtraWhitespace() {
                val cron = CronExpression.parse("  0   12   *   *   *  ")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses expression with tabs as separators")
            fun parsesExpressionWithTabs() {
                val cron = CronExpression.parse("0\t12\t*\t*\t*")

                Assertions.assertNotNull(cron)
            }
        }

        @Nested
        @DisplayName("Range Expressions")
        inner class RangeExpressions {

            @Test
            @DisplayName("parses minute range")
            fun parsesMinuteRange() {
                val cron = CronExpression.parse("0-30 * * * *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses hour range")
            fun parsesHourRange() {
                val cron = CronExpression.parse("0 9-17 * * *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses day of month range")
            fun parsesDayOfMonthRange() {
                val cron = CronExpression.parse("0 0 1-15 * *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses month range")
            fun parsesMonthRange() {
                val cron = CronExpression.parse("0 0 1 1-6 *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses day of week range")
            fun parsesDayOfWeekRange() {
                val cron = CronExpression.parse("0 0 * * 1-5")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses ranges in multiple fields")
            fun parsesRangesInMultipleFields() {
                val cron = CronExpression.parse("0-30 9-17 1-15 1-6 1-5")

                Assertions.assertNotNull(cron)
            }
        }

        @Nested
        @DisplayName("List Expressions")
        inner class ListExpressions {

            @Test
            @DisplayName("parses minute list")
            fun parsesMinuteList() {
                val cron = CronExpression.parse("0,15,30,45 * * * *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses hour list")
            fun parsesHourList() {
                val cron = CronExpression.parse("0 6,12,18 * * *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses day of month list")
            fun parsesDayOfMonthList() {
                val cron = CronExpression.parse("0 0 1,15,28 * *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses month list")
            fun parsesMonthList() {
                val cron = CronExpression.parse("0 0 1 1,4,7,10 *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses day of week list")
            fun parsesDayOfWeekList() {
                val cron = CronExpression.parse("0 0 * * 0,6")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses combined list and range")
            fun parsesCombinedListAndRange() {
                val cron = CronExpression.parse("0 9-12,14-17 * * *")

                Assertions.assertNotNull(cron)
            }
        }

        @Nested
        @DisplayName("Step Value Expressions")
        inner class StepValueExpressions {

            @Test
            @DisplayName("parses minute step")
            fun parsesMinuteStep() {
                val cron = CronExpression.parse("*/15 * * * *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses hour step")
            fun parsesHourStep() {
                val cron = CronExpression.parse("0 */2 * * *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses day of month step")
            fun parsesDayOfMonthStep() {
                val cron = CronExpression.parse("0 0 */5 * *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses month step")
            fun parsesMonthStep() {
                val cron = CronExpression.parse("0 0 1 */3 *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses day of week step")
            fun parsesDayOfWeekStep() {
                val cron = CronExpression.parse("0 0 * * */2")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses step value of 1")
            fun parsesStepValueOfOne() {
                val cron = CronExpression.parse("*/1 * * * *")

                Assertions.assertNotNull(cron)
            }
        }

        @Nested
        @DisplayName("Named Day Expressions")
        inner class NamedDayExpressions {

            @Test
            @DisplayName("parses SUN")
            fun parsesSun() {
                val cron = CronExpression.parse("0 0 * * SUN")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses MON")
            fun parsesMon() {
                val cron = CronExpression.parse("0 0 * * MON")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses TUE")
            fun parsesTue() {
                val cron = CronExpression.parse("0 0 * * TUE")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses WED")
            fun parsesWed() {
                val cron = CronExpression.parse("0 0 * * WED")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses THU")
            fun parsesThu() {
                val cron = CronExpression.parse("0 0 * * THU")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses FRI")
            fun parsesFri() {
                val cron = CronExpression.parse("0 0 * * FRI")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses SAT")
            fun parsesSat() {
                val cron = CronExpression.parse("0 0 * * SAT")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses lowercase day names")
            fun parsesLowercaseDayNames() {
                val cron = CronExpression.parse("0 0 * * mon")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses mixed case day names")
            fun parsesMixedCaseDayNames() {
                val cron = CronExpression.parse("0 0 * * Mon")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses day name range MON-FRI")
            fun parsesDayNameRange() {
                val cron = CronExpression.parse("0 9 * * MON-FRI")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses day name list")
            fun parsesDayNameList() {
                val cron = CronExpression.parse("0 18 * * FRI,SAT,SUN")

                Assertions.assertNotNull(cron)
            }
        }

        @Nested
        @DisplayName("Named Month Expressions")
        inner class NamedMonthExpressions {

            @Test
            @DisplayName("parses JAN")
            fun parsesJan() {
                val cron = CronExpression.parse("0 0 1 JAN *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses FEB")
            fun parsesFeb() {
                val cron = CronExpression.parse("0 0 1 FEB *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses MAR")
            fun parsesMar() {
                val cron = CronExpression.parse("0 0 1 MAR *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses APR")
            fun parsesApr() {
                val cron = CronExpression.parse("0 0 1 APR *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses MAY")
            fun parsesMay() {
                val cron = CronExpression.parse("0 0 1 MAY *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses JUN")
            fun parsesJun() {
                val cron = CronExpression.parse("0 0 1 JUN *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses JUL")
            fun parsesJul() {
                val cron = CronExpression.parse("0 0 1 JUL *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses AUG")
            fun parsesAug() {
                val cron = CronExpression.parse("0 0 1 AUG *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses SEP")
            fun parsesSep() {
                val cron = CronExpression.parse("0 0 1 SEP *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses OCT")
            fun parsesOct() {
                val cron = CronExpression.parse("0 0 1 OCT *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses NOV")
            fun parsesNov() {
                val cron = CronExpression.parse("0 0 1 NOV *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses DEC")
            fun parsesDec() {
                val cron = CronExpression.parse("0 0 1 DEC *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses lowercase month names")
            fun parsesLowercaseMonthNames() {
                val cron = CronExpression.parse("0 0 1 jan *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses mixed case month names")
            fun parsesMixedCaseMonthNames() {
                val cron = CronExpression.parse("0 0 1 Jan *")
                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses month name range JAN-JUN")
            fun parsesMonthNameRange() {
                val cron = CronExpression.parse("0 0 1 JAN-JUN *")

                Assertions.assertNotNull(cron)
            }

            @Test
            @DisplayName("parses month name list")
            fun parsesMonthNameList() {
                val cron = CronExpression.parse("0 0 1 JAN,APR,JUL,OCT *")

                Assertions.assertNotNull(cron)
            }
        }

        @Nested
        @DisplayName("Invalid Expressions")
        inner class InvalidExpressions {

            @Test
            @DisplayName("returns null for empty string")
            fun returnsNullForEmptyString() {
                Assertions.assertNull(CronExpression.parse(""))
            }

            @Test
            @DisplayName("returns null for whitespace only")
            fun returnsNullForWhitespaceOnly() {
                Assertions.assertNull(CronExpression.parse("   "))
            }

            @Test
            @DisplayName("returns null for invalid text")
            fun returnsNullForInvalidText() {
                Assertions.assertNull(CronExpression.parse("invalid"))
            }

            @Test
            @DisplayName("returns null for too few fields")
            fun returnsNullForTooFewFields() {
                Assertions.assertNull(CronExpression.parse("* * *"))
                Assertions.assertNull(CronExpression.parse("* * * *"))
            }

            @Test
            @DisplayName("returns null for too many fields")
            fun returnsNullForTooManyFields() {
                Assertions.assertNull(CronExpression.parse("* * * * * *"))
                Assertions.assertNull(CronExpression.parse("* * * * * * *"))
            }

            @Test
            @DisplayName("returns null for minute out of range")
            fun returnsNullForMinuteOutOfRange() {
                Assertions.assertNull(CronExpression.parse("60 * * * *"))
                Assertions.assertNull(CronExpression.parse("-1 * * * *"))
            }

            @Test
            @DisplayName("returns null for hour out of range")
            fun returnsNullForHourOutOfRange() {
                Assertions.assertNull(CronExpression.parse("* 24 * * *"))
                Assertions.assertNull(CronExpression.parse("* -1 * * *"))
            }

            @Test
            @DisplayName("returns null for day of month out of range")
            fun returnsNullForDayOfMonthOutOfRange() {
                Assertions.assertNull(CronExpression.parse("* * 32 * *"))
                Assertions.assertNull(CronExpression.parse("* * 0 * *"))
            }

            @Test
            @DisplayName("returns null for month out of range")
            fun returnsNullForMonthOutOfRange() {
                Assertions.assertNull(CronExpression.parse("* * * 13 *"))
                Assertions.assertNull(CronExpression.parse("* * * 0 *"))
            }

            @Test
            @DisplayName("returns null for day of week out of range")
            fun returnsNullForDayOfWeekOutOfRange() {
                Assertions.assertNull(CronExpression.parse("* * * * 7"))
                Assertions.assertNull(CronExpression.parse("* * * * -1"))
            }

            @Test
            @DisplayName("returns null for invalid range")
            fun returnsNullForInvalidRange() {
                Assertions.assertNull(CronExpression.parse("30-10 * * * *")) // start > end
                Assertions.assertNull(CronExpression.parse("* 17-9 * * *")) // start > end
            }

            @Test
            @DisplayName("returns null for invalid step value")
            fun returnsNullForInvalidStepValue() {
                Assertions.assertNull(CronExpression.parse("*/0 * * * *"))
                Assertions.assertNull(CronExpression.parse("*/-1 * * * *"))
                Assertions.assertNull(CronExpression.parse("*/abc * * * *"))
            }

            @Test
            @DisplayName("returns null for invalid month name")
            fun returnsNullForInvalidMonthName() {
                Assertions.assertNull(CronExpression.parse("0 0 1 XYZ *"))
                Assertions.assertNull(CronExpression.parse("0 0 1 JANUARY *"))
            }

            @Test
            @DisplayName("returns null for invalid day name")
            fun returnsNullForInvalidDayName() {
                Assertions.assertNull(CronExpression.parse("0 0 * * XYZ"))
                Assertions.assertNull(CronExpression.parse("0 0 * * MONDAY"))
            }

            @Test
            @DisplayName("returns null for malformed range")
            fun returnsNullForMalformedRange() {
                Assertions.assertNull(CronExpression.parse("1-2-3 * * * *"))
                Assertions.assertNull(CronExpression.parse("- * * * *"))
                Assertions.assertNull(CronExpression.parse("1- * * * *"))
            }
        }
    }

    @Nested
    @DisplayName("Matching")
    inner class Matching {

        @Nested
        @DisplayName("Wildcard Matching")
        inner class WildcardMatching {

            @Test
            @DisplayName("wildcard matches any minute")
            fun wildcardMatchesAnyMinute() {
                val cron = CronExpression.parse("* 12 1 1 *")!!

                for (minute in 0..59) {
                    val time = ZonedDateTime.of(2024, 1, 1, 12, minute, 0, 0, ZoneId.systemDefault())
                    assertTrue(cron.matches(time), "Should match minute $minute")
                }
            }

            @Test
            @DisplayName("wildcard matches any hour")
            fun wildcardMatchesAnyHour() {
                val cron = CronExpression.parse("0 * 1 1 *")!!

                for (hour in 0..23) {
                    val time = ZonedDateTime.of(2024, 1, 1, hour, 0, 0, 0, ZoneId.systemDefault())
                    assertTrue(cron.matches(time), "Should match hour $hour")
                }
            }

            @Test
            @DisplayName("wildcard matches any day of month")
            fun wildcardMatchesAnyDayOfMonth() {
                val cron = CronExpression.parse("0 12 * 1 *")!!

                for (day in 1..31) {
                    val time = ZonedDateTime.of(2024, 1, day, 12, 0, 0, 0, ZoneId.systemDefault())
                    assertTrue(cron.matches(time), "Should match day $day")
                }
            }

            @Test
            @DisplayName("wildcard matches any month")
            fun wildcardMatchesAnyMonth() {
                val cron = CronExpression.parse("0 12 15 * *")!!

                for (month in 1..12) {
                    val time = ZonedDateTime.of(2024, month, 15, 12, 0, 0, 0, ZoneId.systemDefault())
                    assertTrue(cron.matches(time), "Should match month $month")
                }
            }

            @Test
            @DisplayName("wildcard matches any day of week")
            fun wildcardMatchesAnyDayOfWeek() {
                val cron = CronExpression.parse("0 12 * 1 *")!!

                // Test all 7 days starting from Monday Jan 1, 2024
                for (day in 1..7) {
                    val time = ZonedDateTime.of(2024, 1, day, 12, 0, 0, 0, ZoneId.systemDefault())
                    assertTrue(cron.matches(time), "Should match day of week for Jan $day")
                }
            }
        }

        @Nested
        @DisplayName("Specific Value Matching")
        inner class SpecificValueMatching {

            @Test
            @DisplayName("specific minute matches exactly")
            fun specificMinuteMatchesExactly() {
                val cron = CronExpression.parse("30 * * * *")!!
                val matches = ZonedDateTime.of(2024, 1, 1, 12, 30, 0, 0, ZoneId.systemDefault())
                val notMatches = ZonedDateTime.of(2024, 1, 1, 12, 31, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(matches))
                assertFalse(cron.matches(notMatches))
            }

            @Test
            @DisplayName("specific hour matches exactly")
            fun specificHourMatchesExactly() {
                val cron = CronExpression.parse("0 18 * * *")!!
                val matches = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.systemDefault())
                val notMatches = ZonedDateTime.of(2024, 1, 1, 19, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(matches))
                assertFalse(cron.matches(notMatches))
            }

            @Test
            @DisplayName("specific day of month matches exactly")
            fun specificDayOfMonthMatchesExactly() {
                val cron = CronExpression.parse("0 12 15 * *")!!
                val matches = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneId.systemDefault())
                val notMatches = ZonedDateTime.of(2024, 1, 16, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(matches))
                assertFalse(cron.matches(notMatches))
            }

            @Test
            @DisplayName("specific month matches exactly")
            fun specificMonthMatchesExactly() {
                val cron = CronExpression.parse("0 12 15 7 *")!!
                val matches = ZonedDateTime.of(2024, 7, 15, 12, 0, 0, 0, ZoneId.systemDefault())
                val notMatches = ZonedDateTime.of(2024, 8, 15, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(matches))
                assertFalse(cron.matches(notMatches))
            }

            @Test
            @DisplayName("specific day of week matches exactly")
            fun specificDayOfWeekMatchesExactly() {
                // 0 = Sunday
                val cron = CronExpression.parse("0 12 * * 0")!!
                // Sunday January 7, 2024
                val sunday = ZonedDateTime.of(2024, 1, 7, 12, 0, 0, 0, ZoneId.systemDefault())
                // Monday January 8, 2024
                val monday = ZonedDateTime.of(2024, 1, 8, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(sunday))
                assertFalse(cron.matches(monday))
            }

            @Test
            @DisplayName("matches boundary minute values")
            fun matchesBoundaryMinuteValues() {
                val cronMin = CronExpression.parse("0 12 * * *")!!
                val cronMax = CronExpression.parse("59 12 * * *")!!

                val min0 = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val min59 = ZonedDateTime.of(2024, 1, 1, 12, 59, 0, 0, ZoneId.systemDefault())

                assertTrue(cronMin.matches(min0))
                assertTrue(cronMax.matches(min59))
            }

            @Test
            @DisplayName("matches boundary hour values")
            fun matchesBoundaryHourValues() {
                val cronMin = CronExpression.parse("0 0 * * *")!!
                val cronMax = CronExpression.parse("0 23 * * *")!!

                val hour0 = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
                val hour23 = ZonedDateTime.of(2024, 1, 1, 23, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cronMin.matches(hour0))
                assertTrue(cronMax.matches(hour23))
            }
        }

        @Nested
        @DisplayName("Range Matching")
        inner class RangeMatching {

            @Test
            @DisplayName("range matches values within range")
            fun rangeMatchesValuesWithinRange() {
                val cron = CronExpression.parse("0 9-17 * * *")!!

                val morning = ZonedDateTime.of(2024, 1, 1, 9, 0, 0, 0, ZoneId.systemDefault())
                val afternoon = ZonedDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneId.systemDefault())
                val evening = ZonedDateTime.of(2024, 1, 1, 17, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(morning))
                assertTrue(cron.matches(afternoon))
                assertTrue(cron.matches(evening))
            }

            @Test
            @DisplayName("range does not match values outside range")
            fun rangeDoesNotMatchValuesOutsideRange() {
                val cron = CronExpression.parse("0 9-17 * * *")!!

                val tooEarly = ZonedDateTime.of(2024, 1, 1, 8, 0, 0, 0, ZoneId.systemDefault())
                val tooLate = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.systemDefault())

                assertFalse(cron.matches(tooEarly))
                assertFalse(cron.matches(tooLate))
            }

            @Test
            @DisplayName("minute range matches all values")
            fun minuteRangeMatchesAllValues() {
                val cron = CronExpression.parse("15-45 12 1 1 *")!!

                for (minute in 15..45) {
                    val time = ZonedDateTime.of(2024, 1, 1, 12, minute, 0, 0, ZoneId.systemDefault())
                    assertTrue(cron.matches(time), "Should match minute $minute")
                }
            }
        }

        @Nested
        @DisplayName("List Matching")
        inner class ListMatching {

            @Test
            @DisplayName("list matches specific values")
            fun listMatchesSpecificValues() {
                val cron = CronExpression.parse("0,15,30,45 * * * *")!!

                val min0 = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val min15 = ZonedDateTime.of(2024, 1, 1, 12, 15, 0, 0, ZoneId.systemDefault())
                val min30 = ZonedDateTime.of(2024, 1, 1, 12, 30, 0, 0, ZoneId.systemDefault())
                val min45 = ZonedDateTime.of(2024, 1, 1, 12, 45, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(min0))
                assertTrue(cron.matches(min15))
                assertTrue(cron.matches(min30))
                assertTrue(cron.matches(min45))
            }

            @Test
            @DisplayName("list does not match other values")
            fun listDoesNotMatchOtherValues() {
                val cron = CronExpression.parse("0,15,30,45 * * * *")!!

                val min10 = ZonedDateTime.of(2024, 1, 1, 12, 10, 0, 0, ZoneId.systemDefault())
                val min20 = ZonedDateTime.of(2024, 1, 1, 12, 20, 0, 0, ZoneId.systemDefault())

                assertFalse(cron.matches(min10))
                assertFalse(cron.matches(min20))
            }

            @Test
            @DisplayName("combined list and range matches correctly")
            fun combinedListAndRangeMatchesCorrectly() {
                val cron = CronExpression.parse("0 9-12,14-17 * * *")!!

                // Should match
                val hour9 = ZonedDateTime.of(2024, 1, 1, 9, 0, 0, 0, ZoneId.systemDefault())
                val hour12 = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val hour14 = ZonedDateTime.of(2024, 1, 1, 14, 0, 0, 0, ZoneId.systemDefault())
                val hour17 = ZonedDateTime.of(2024, 1, 1, 17, 0, 0, 0, ZoneId.systemDefault())

                // Should not match (lunch break)
                val hour13 = ZonedDateTime.of(2024, 1, 1, 13, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(hour9))
                assertTrue(cron.matches(hour12))
                assertTrue(cron.matches(hour14))
                assertTrue(cron.matches(hour17))
                assertFalse(cron.matches(hour13))
            }
        }

        @Nested
        @DisplayName("Step Value Matching")
        inner class StepValueMatching {

            @Test
            @DisplayName("step values match correct intervals")
            fun stepValuesMatchCorrectIntervals() {
                val cron = CronExpression.parse("*/15 * * * *")!!

                val min0 = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val min15 = ZonedDateTime.of(2024, 1, 1, 12, 15, 0, 0, ZoneId.systemDefault())
                val min30 = ZonedDateTime.of(2024, 1, 1, 12, 30, 0, 0, ZoneId.systemDefault())
                val min45 = ZonedDateTime.of(2024, 1, 1, 12, 45, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(min0))
                assertTrue(cron.matches(min15))
                assertTrue(cron.matches(min30))
                assertTrue(cron.matches(min45))
            }

            @Test
            @DisplayName("step values do not match intermediate values")
            fun stepValuesDoNotMatchIntermediateValues() {
                val cron = CronExpression.parse("*/15 * * * *")!!

                val min10 = ZonedDateTime.of(2024, 1, 1, 12, 10, 0, 0, ZoneId.systemDefault())
                val min25 = ZonedDateTime.of(2024, 1, 1, 12, 25, 0, 0, ZoneId.systemDefault())

                assertFalse(cron.matches(min10))
                assertFalse(cron.matches(min25))
            }

            @Test
            @DisplayName("hour step matches every n hours")
            fun hourStepMatchesEveryNHours() {
                val cron = CronExpression.parse("0 */6 * * *")!!

                val hour0 = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
                val hour6 = ZonedDateTime.of(2024, 1, 1, 6, 0, 0, 0, ZoneId.systemDefault())
                val hour12 = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val hour18 = ZonedDateTime.of(2024, 1, 1, 18, 0, 0, 0, ZoneId.systemDefault())
                val hour3 = ZonedDateTime.of(2024, 1, 1, 3, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(hour0))
                assertTrue(cron.matches(hour6))
                assertTrue(cron.matches(hour12))
                assertTrue(cron.matches(hour18))
                assertFalse(cron.matches(hour3))
            }

            @Test
            @DisplayName("step of 1 matches all values")
            fun stepOfOneMatchesAllValues() {
                val cron = CronExpression.parse("*/1 12 1 1 *")!!

                for (minute in 0..59) {
                    val time = ZonedDateTime.of(2024, 1, 1, 12, minute, 0, 0, ZoneId.systemDefault())
                    assertTrue(cron.matches(time), "Should match minute $minute")
                }
            }
        }

        @Nested
        @DisplayName("Day Name Matching")
        inner class DayNameMatching {

            @Test
            @DisplayName("FRI matches Friday")
            fun friMatchesFriday() {
                val cron = CronExpression.parse("0 12 * * FRI")!!

                // Friday January 5, 2024
                val friday = ZonedDateTime.of(2024, 1, 5, 12, 0, 0, 0, ZoneId.systemDefault())
                assertTrue(cron.matches(friday))
            }

            @Test
            @DisplayName("SAT matches Saturday")
            fun satMatchesSaturday() {
                val cron = CronExpression.parse("0 12 * * SAT")!!

                // Saturday January 6, 2024
                val saturday = ZonedDateTime.of(2024, 1, 6, 12, 0, 0, 0, ZoneId.systemDefault())
                assertTrue(cron.matches(saturday))
            }

            @Test
            @DisplayName("SUN matches Sunday")
            fun sunMatchesSunday() {
                val cron = CronExpression.parse("0 12 * * SUN")!!

                // Sunday January 7, 2024
                val sunday = ZonedDateTime.of(2024, 1, 7, 12, 0, 0, 0, ZoneId.systemDefault())
                assertTrue(cron.matches(sunday))
            }

            @Test
            @DisplayName("MON-FRI matches weekdays")
            fun monFriMatchesWeekdays() {
                val cron = CronExpression.parse("0 12 * * MON-FRI")!!

                // Monday through Friday (Jan 1-5, 2024)
                val monday = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val tuesday = ZonedDateTime.of(2024, 1, 2, 12, 0, 0, 0, ZoneId.systemDefault())
                val wednesday = ZonedDateTime.of(2024, 1, 3, 12, 0, 0, 0, ZoneId.systemDefault())
                val thursday = ZonedDateTime.of(2024, 1, 4, 12, 0, 0, 0, ZoneId.systemDefault())
                val friday = ZonedDateTime.of(2024, 1, 5, 12, 0, 0, 0, ZoneId.systemDefault())
                // Weekend
                val saturday = ZonedDateTime.of(2024, 1, 6, 12, 0, 0, 0, ZoneId.systemDefault())
                val sunday = ZonedDateTime.of(2024, 1, 7, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(monday))
                assertTrue(cron.matches(tuesday))
                assertTrue(cron.matches(wednesday))
                assertTrue(cron.matches(thursday))
                assertTrue(cron.matches(friday))
                assertFalse(cron.matches(saturday))
                assertFalse(cron.matches(sunday))
            }

            @Test
            @DisplayName("SAT,SUN matches weekend")
            fun satSunMatchesWeekend() {
                val cron = CronExpression.parse("0 12 * * SAT,SUN")!!

                // Weekend
                val saturday = ZonedDateTime.of(2024, 1, 6, 12, 0, 0, 0, ZoneId.systemDefault())
                val sunday = ZonedDateTime.of(2024, 1, 7, 12, 0, 0, 0, ZoneId.systemDefault())
                // Weekday
                val monday = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(saturday))
                assertTrue(cron.matches(sunday))
                assertFalse(cron.matches(monday))
            }

            @Test
            @DisplayName("numeric 0 matches Sunday")
            fun numericZeroMatchesSunday() {
                val cron = CronExpression.parse("0 12 * * 0")!!

                // Sunday January 7, 2024
                val sunday = ZonedDateTime.of(2024, 1, 7, 12, 0, 0, 0, ZoneId.systemDefault())
                assertTrue(cron.matches(sunday))
            }

            @Test
            @DisplayName("numeric 6 matches Saturday")
            fun numericSixMatchesSaturday() {
                val cron = CronExpression.parse("0 12 * * 6")!!

                // Saturday January 6, 2024
                val saturday = ZonedDateTime.of(2024, 1, 6, 12, 0, 0, 0, ZoneId.systemDefault())
                assertTrue(cron.matches(saturday))
            }
        }

        @Nested
        @DisplayName("Month Name Matching")
        inner class MonthNameMatching {

            @Test
            @DisplayName("JAN matches January")
            fun janMatchesJanuary() {
                val cron = CronExpression.parse("0 12 15 JAN *")!!

                val january = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneId.systemDefault())
                val february = ZonedDateTime.of(2024, 2, 15, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(january))
                assertFalse(cron.matches(february))
            }

            @Test
            @DisplayName("DEC matches December")
            fun decMatchesDecember() {
                val cron = CronExpression.parse("0 12 15 DEC *")!!

                val december = ZonedDateTime.of(2024, 12, 15, 12, 0, 0, 0, ZoneId.systemDefault())
                assertTrue(cron.matches(december))
            }

            @Test
            @DisplayName("JAN-JUN matches first half of year")
            fun janJunMatchesFirstHalf() {
                val cron = CronExpression.parse("0 12 15 JAN-JUN *")!!

                for (month in 1..6) {
                    val time = ZonedDateTime.of(2024, month, 15, 12, 0, 0, 0, ZoneId.systemDefault())
                    assertTrue(cron.matches(time), "Should match month $month")
                }

                for (month in 7..12) {
                    val time = ZonedDateTime.of(2024, month, 15, 12, 0, 0, 0, ZoneId.systemDefault())
                    assertFalse(cron.matches(time), "Should not match month $month")
                }
            }

            @Test
            @DisplayName("quarterly months match correctly")
            fun quarterlyMonthsMatchCorrectly() {
                val cron = CronExpression.parse("0 12 1 JAN,APR,JUL,OCT *")!!

                val jan = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val apr = ZonedDateTime.of(2024, 4, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val jul = ZonedDateTime.of(2024, 7, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val oct = ZonedDateTime.of(2024, 10, 1, 12, 0, 0, 0, ZoneId.systemDefault())
                val feb = ZonedDateTime.of(2024, 2, 1, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(jan))
                assertTrue(cron.matches(apr))
                assertTrue(cron.matches(jul))
                assertTrue(cron.matches(oct))
                assertFalse(cron.matches(feb))
            }
        }

        @Nested
        @DisplayName("Day Matching Logic")
        inner class DayMatchingLogic {

            @Test
            @DisplayName("both day wildcards match any day")
            fun bothDayWildcardsMatchAnyDay() {
                val cron = CronExpression.parse("0 12 * * *")!!

                // Test multiple days across different weeks
                for (day in 1..28) {
                    val time = ZonedDateTime.of(2024, 1, day, 12, 0, 0, 0, ZoneId.systemDefault())
                    assertTrue(cron.matches(time), "Should match day $day")
                }
            }

            @Test
            @DisplayName("specific day of month with wildcard day of week checks only day of month")
            fun specificDayOfMonthWithWildcardDayOfWeek() {
                val cron = CronExpression.parse("0 12 15 * *")!!

                // 15th of January (Monday) should match
                val jan15 = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneId.systemDefault())
                // 14th of January should not match
                val jan14 = ZonedDateTime.of(2024, 1, 14, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(jan15))
                assertFalse(cron.matches(jan14))
            }

            @Test
            @DisplayName("wildcard day of month with specific day of week checks only day of week")
            fun wildcardDayOfMonthWithSpecificDayOfWeek() {
                val cron = CronExpression.parse("0 12 * * FRI")!!

                // All Fridays in January 2024: 5, 12, 19, 26
                val friday5 = ZonedDateTime.of(2024, 1, 5, 12, 0, 0, 0, ZoneId.systemDefault())
                val friday12 = ZonedDateTime.of(2024, 1, 12, 12, 0, 0, 0, ZoneId.systemDefault())
                // Monday January 1
                val monday1 = ZonedDateTime.of(2024, 1, 1, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(friday5))
                assertTrue(cron.matches(friday12))
                assertFalse(cron.matches(monday1))
            }

            @Test
            @DisplayName("both specific uses OR logic")
            fun bothSpecificUsesOrLogic() {
                // "On the 15th OR on Fridays"
                val cron = CronExpression.parse("0 12 15 * FRI")!!

                // Friday January 5 (not 15th, but Friday) - should match
                val friday5 = ZonedDateTime.of(2024, 1, 5, 12, 0, 0, 0, ZoneId.systemDefault())
                // Monday January 15 (15th, but not Friday) - should match
                val monday15 = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, ZoneId.systemDefault())
                // Monday January 8 (neither 15th nor Friday) - should not match
                val monday8 = ZonedDateTime.of(2024, 1, 8, 12, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(friday5))
                assertTrue(cron.matches(monday15))
                assertFalse(cron.matches(monday8))
            }
        }

        @Nested
        @DisplayName("Complex Expression Matching")
        inner class ComplexExpressionMatching {

            @Test
            @DisplayName("matches business hours on weekdays")
            fun matchesBusinessHoursOnWeekdays() {
                // 9 AM to 5 PM, Monday through Friday
                val cron = CronExpression.parse("0 9-17 * * MON-FRI")!!

                // Monday 10 AM
                val mondayMorning = ZonedDateTime.of(2024, 1, 1, 10, 0, 0, 0, ZoneId.systemDefault())
                // Saturday 10 AM
                val saturdayMorning = ZonedDateTime.of(2024, 1, 6, 10, 0, 0, 0, ZoneId.systemDefault())
                // Monday 8 PM
                val mondayEvening = ZonedDateTime.of(2024, 1, 1, 20, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(mondayMorning))
                assertFalse(cron.matches(saturdayMorning))
                assertFalse(cron.matches(mondayEvening))
            }

            @Test
            @DisplayName("matches every 15 minutes during work hours")
            fun matchesEvery15MinutesDuringWorkHours() {
                val cron = CronExpression.parse("*/15 9-17 * * MON-FRI")!!

                // Monday 9:00
                val monday9 = ZonedDateTime.of(2024, 1, 1, 9, 0, 0, 0, ZoneId.systemDefault())
                // Monday 9:15
                val monday915 = ZonedDateTime.of(2024, 1, 1, 9, 15, 0, 0, ZoneId.systemDefault())
                // Monday 9:10
                val monday910 = ZonedDateTime.of(2024, 1, 1, 9, 10, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(monday9))
                assertTrue(cron.matches(monday915))
                assertFalse(cron.matches(monday910))
            }

            @Test
            @DisplayName("matches weekend evening events")
            fun matchesWeekendEveningEvents() {
                // Every Friday and Saturday at 6-10 PM
                val cron = CronExpression.parse("0 18-22 * * FRI,SAT")!!

                // Friday at 6 PM
                val fridayEvening = ZonedDateTime.of(2024, 1, 5, 18, 0, 0, 0, ZoneId.systemDefault())
                // Saturday at 9 PM
                val saturdayNight = ZonedDateTime.of(2024, 1, 6, 21, 0, 0, 0, ZoneId.systemDefault())
                // Friday at 5 PM (outside hour range)
                val fridayAfternoon = ZonedDateTime.of(2024, 1, 5, 17, 0, 0, 0, ZoneId.systemDefault())
                // Monday at 6 PM (wrong day)
                val mondayEvening = ZonedDateTime.of(2024, 1, 8, 18, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(fridayEvening))
                assertTrue(cron.matches(saturdayNight))
                assertFalse(cron.matches(fridayAfternoon))
                assertFalse(cron.matches(mondayEvening))
            }

            @Test
            @DisplayName("matches first day of each quarter")
            fun matchesFirstDayOfEachQuarter() {
                val cron = CronExpression.parse("0 0 1 1,4,7,10 *")!!

                val jan1 = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
                val apr1 = ZonedDateTime.of(2024, 4, 1, 0, 0, 0, 0, ZoneId.systemDefault())
                val jul1 = ZonedDateTime.of(2024, 7, 1, 0, 0, 0, 0, ZoneId.systemDefault())
                val oct1 = ZonedDateTime.of(2024, 10, 1, 0, 0, 0, 0, ZoneId.systemDefault())
                val feb1 = ZonedDateTime.of(2024, 2, 1, 0, 0, 0, 0, ZoneId.systemDefault())

                assertTrue(cron.matches(jan1))
                assertTrue(cron.matches(apr1))
                assertTrue(cron.matches(jul1))
                assertTrue(cron.matches(oct1))
                assertFalse(cron.matches(feb1))
            }
        }
    }

    @Nested
    @DisplayName("Timezone Matching")
    inner class TimezoneMatching {

        @Test
        @DisplayName("matches with specified timezone")
        fun matchesWithSpecifiedTimezone() {
            val cron = CronExpression.parse("0 12 * * *")!!

            // Create a time that is 12:00 in New York
            val newYorkZone = ZoneId.of("America/New_York")
            val utcZone = ZoneId.of("UTC")

            // 12:00 in New York
            val noonNewYork = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, newYorkZone)

            // Should match when checking in New York timezone
            assertTrue(cron.matches(noonNewYork, newYorkZone))

            // The same instant in UTC is 17:00 (during standard time), so should not match
            assertFalse(cron.matches(noonNewYork, utcZone))
        }

        @Test
        @DisplayName("converts time to target timezone before matching")
        fun convertsTimeToTargetTimezoneBeforeMatching() {
            val cron = CronExpression.parse("0 9 * * *")!!

            val tokyoZone = ZoneId.of("Asia/Tokyo")
            val utcZone = ZoneId.of("UTC")

            // 9:00 AM UTC
            val utc9am = ZonedDateTime.of(2024, 1, 15, 9, 0, 0, 0, utcZone)

            // Should match 9 AM when checking in UTC
            assertTrue(cron.matches(utc9am, utcZone))

            // In Tokyo, 9 AM UTC is 6 PM JST, so should not match 9 AM Tokyo
            assertFalse(cron.matches(utc9am, tokyoZone))
        }

        @Test
        @DisplayName("handles daylight saving time")
        fun handlesDaylightSavingTime() {
            val cron = CronExpression.parse("0 12 * * *")!!

            val newYorkZone = ZoneId.of("America/New_York")

            // Summer time (EDT, UTC-4)
            val summerNoon = ZonedDateTime.of(2024, 7, 15, 12, 0, 0, 0, newYorkZone)
            // Winter time (EST, UTC-5)
            val winterNoon = ZonedDateTime.of(2024, 1, 15, 12, 0, 0, 0, newYorkZone)

            // Both should match noon in New York regardless of DST
            assertTrue(cron.matches(summerNoon, newYorkZone))
            assertTrue(cron.matches(winterNoon, newYorkZone))
        }

        @Test
        @DisplayName("cross-day timezone conversion")
        fun crossDayTimezoneConversion() {
            val cron = CronExpression.parse("0 2 15 * *")!! // 2 AM on the 15th

            val tokyoZone = ZoneId.of("Asia/Tokyo")
            val utcZone = ZoneId.of("UTC")

            // 2 AM on January 15 in Tokyo
            val tokyo2am = ZonedDateTime.of(2024, 1, 15, 2, 0, 0, 0, tokyoZone)

            // Should match 2 AM in Tokyo
            assertTrue(cron.matches(tokyo2am, tokyoZone))

            // Same instant in UTC is still January 14 at 17:00, so should not match 2 AM on 15th
            assertFalse(cron.matches(tokyo2am, utcZone))
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
            assertTrue(CronExpression.isValid("0 0 1 JAN *"))
            assertTrue(CronExpression.isValid("30 12 15 6 3"))
        }

        @Test
        @DisplayName("isValid returns false for invalid expression")
        fun isValidReturnsFalseForInvalidExpression() {
            assertFalse(CronExpression.isValid("invalid"))
            assertFalse(CronExpression.isValid("* * *"))
            assertFalse(CronExpression.isValid("60 * * * *"))
            assertFalse(CronExpression.isValid(""))
            assertFalse(CronExpression.isValid("* * * * * *"))
        }

        @Test
        @DisplayName("isValid handles edge cases")
        fun isValidHandlesEdgeCases() {
            assertFalse(CronExpression.isValid("   "))
            assertFalse(CronExpression.isValid("*/0 * * * *"))
            assertFalse(CronExpression.isValid("30-10 * * * *"))
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

        @Test
        @DisplayName("preserves expression format")
        fun preservesExpressionFormat() {
            val expressions = listOf(
                "* * * * *",
                "*/15 * * * *",
                "0 9-17 * * MON-FRI",
                "0,30 * * * *",
                "0 0 1 JAN,JUL *"
            )

            for (expr in expressions) {
                val cron = CronExpression.parse(expr)!!
                assertEquals(expr, cron.toString(), "Should preserve: $expr")
            }
        }
    }

    @Nested
    @DisplayName("expression Property")
    inner class ExpressionProperty {

        @Test
        @DisplayName("expression property returns original string")
        fun expressionPropertyReturnsOriginalString() {
            val expr = "*/5 8-20 * * 1-5"
            val cron = CronExpression.parse(expr)!!

            assertEquals(expr, cron.expression)
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("handles leap year February 29")
        fun handlesLeapYearFebruary29() {
            val cron = CronExpression.parse("0 12 29 2 *")!!

            // 2024 is a leap year
            val leapYearFeb29 = ZonedDateTime.of(2024, 2, 29, 12, 0, 0, 0, ZoneId.systemDefault())
            assertTrue(cron.matches(leapYearFeb29))
        }

        @Test
        @DisplayName("handles end of month days")
        fun handlesEndOfMonthDays() {
            val cron = CronExpression.parse("0 12 31 * *")!!

            // January has 31 days
            val jan31 = ZonedDateTime.of(2024, 1, 31, 12, 0, 0, 0, ZoneId.systemDefault())
            // March has 31 days
            val mar31 = ZonedDateTime.of(2024, 3, 31, 12, 0, 0, 0, ZoneId.systemDefault())

            assertTrue(cron.matches(jan31))
            assertTrue(cron.matches(mar31))
        }

        @Test
        @DisplayName("handles midnight correctly")
        fun handlesMidnightCorrectly() {
            val cron = CronExpression.parse("0 0 * * *")!!

            val midnight = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
            val oneAm = ZonedDateTime.of(2024, 1, 1, 1, 0, 0, 0, ZoneId.systemDefault())

            assertTrue(cron.matches(midnight))
            assertFalse(cron.matches(oneAm))
        }

        @Test
        @DisplayName("handles last minute of day")
        fun handlesLastMinuteOfDay() {
            val cron = CronExpression.parse("59 23 * * *")!!

            val lastMinute = ZonedDateTime.of(2024, 1, 1, 23, 59, 0, 0, ZoneId.systemDefault())
            assertTrue(cron.matches(lastMinute))
        }

        @Test
        @DisplayName("handles year transition")
        fun handlesYearTransition() {
            val cron = CronExpression.parse("0 0 1 1 *")!!

            val newYearsDay2024 = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
            val newYearsDay2025 = ZonedDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault())
            val dec31 = ZonedDateTime.of(2024, 12, 31, 0, 0, 0, 0, ZoneId.systemDefault())

            assertTrue(cron.matches(newYearsDay2024))
            assertTrue(cron.matches(newYearsDay2025))
            assertFalse(cron.matches(dec31))
        }

        @Test
        @DisplayName("no-arg matches uses current time")
        fun noArgMatchesUsesCurrentTime() {
            // This test verifies the no-arg matches() function works
            // We use a wildcard expression that always matches
            val alwaysMatch = CronExpression.parse("* * * * *")!!
            assertTrue(alwaysMatch.matches())
        }
    }
}

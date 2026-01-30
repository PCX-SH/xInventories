package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.TimeRange
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@DisplayName("TimeRange")
class TimeRangeTest {

    @Nested
    @DisplayName("Initialization")
    inner class Initialization {

        @Test
        @DisplayName("creates TimeRange with all properties")
        fun createsTimeRangeWithAllProperties() {
            val start = Instant.now()
            val end = start.plus(1, ChronoUnit.HOURS)
            val timezone = ZoneId.of("America/New_York")

            val range = TimeRange(start, end, timezone)

            assertEquals(start, range.start)
            assertEquals(end, range.end)
            assertEquals(timezone, range.timezone)
        }

        @Test
        @DisplayName("uses system default timezone when not specified")
        fun usesSystemDefaultTimezone() {
            val start = Instant.now()
            val end = start.plus(1, ChronoUnit.HOURS)

            val range = TimeRange(start, end)

            assertEquals(ZoneId.systemDefault(), range.timezone)
        }
    }

    @Nested
    @DisplayName("isActive")
    inner class IsActive {

        @Test
        @DisplayName("returns true when current time is within range")
        fun returnsTrueWhenWithinRange() {
            val start = Instant.now().minus(1, ChronoUnit.HOURS)
            val end = Instant.now().plus(1, ChronoUnit.HOURS)

            val range = TimeRange(start, end)

            assertTrue(range.isActive())
        }

        @Test
        @DisplayName("returns false when current time is before range")
        fun returnsFalseWhenBeforeRange() {
            val start = Instant.now().plus(1, ChronoUnit.HOURS)
            val end = Instant.now().plus(2, ChronoUnit.HOURS)

            val range = TimeRange(start, end)

            assertFalse(range.isActive())
        }

        @Test
        @DisplayName("returns false when current time is after range")
        fun returnsFalseWhenAfterRange() {
            val start = Instant.now().minus(2, ChronoUnit.HOURS)
            val end = Instant.now().minus(1, ChronoUnit.HOURS)

            val range = TimeRange(start, end)

            assertFalse(range.isActive())
        }

        @Test
        @DisplayName("returns true at exact start time")
        fun returnsTrueAtExactStartTime() {
            val now = Instant.now()
            val range = TimeRange(now, now.plus(1, ChronoUnit.HOURS))

            assertTrue(range.isActive(now))
        }

        @Test
        @DisplayName("returns true at exact end time")
        fun returnsTrueAtExactEndTime() {
            val now = Instant.now()
            val range = TimeRange(now.minus(1, ChronoUnit.HOURS), now)

            assertTrue(range.isActive(now))
        }
    }

    @Nested
    @DisplayName("fromStrings")
    inner class FromStrings {

        @Test
        @DisplayName("parses ISO-8601 datetime strings")
        fun parsesIsoDateTimeStrings() {
            val range = TimeRange.fromStrings(
                "2024-10-25T00:00:00",
                "2024-11-01T23:59:59",
                "America/New_York"
            )

            Assertions.assertNotNull(range)
            assertEquals(ZoneId.of("America/New_York"), range!!.timezone)
        }

        @Test
        @DisplayName("uses system timezone when not specified")
        fun usesSystemTimezoneWhenNotSpecified() {
            val range = TimeRange.fromStrings(
                "2024-10-25T00:00:00",
                "2024-11-01T23:59:59",
                null
            )

            Assertions.assertNotNull(range)
            assertEquals(ZoneId.systemDefault(), range!!.timezone)
        }

        @Test
        @DisplayName("returns null for invalid start date")
        fun returnsNullForInvalidStartDate() {
            val range = TimeRange.fromStrings(
                "invalid-date",
                "2024-11-01T23:59:59",
                null
            )

            Assertions.assertNull(range)
        }

        @Test
        @DisplayName("returns null for invalid end date")
        fun returnsNullForInvalidEndDate() {
            val range = TimeRange.fromStrings(
                "2024-10-25T00:00:00",
                "invalid-date",
                null
            )

            Assertions.assertNull(range)
        }

        @Test
        @DisplayName("returns null for invalid timezone")
        fun returnsNullForInvalidTimezone() {
            val range = TimeRange.fromStrings(
                "2024-10-25T00:00:00",
                "2024-11-01T23:59:59",
                "Invalid/Timezone"
            )

            Assertions.assertNull(range)
        }
    }

    @Nested
    @DisplayName("getStartZoned and getEndZoned")
    inner class ZonedAccessors {

        @Test
        @DisplayName("returns zoned datetime in configured timezone")
        fun returnsZonedDateTimeInConfiguredTimezone() {
            val range = TimeRange.fromStrings(
                "2024-10-25T12:00:00",
                "2024-10-25T18:00:00",
                "America/New_York"
            )

            Assertions.assertNotNull(range)
            assertEquals(ZoneId.of("America/New_York"), range!!.getStartZoned().zone)
            assertEquals(ZoneId.of("America/New_York"), range.getEndZoned().zone)
        }
    }
}

package sh.pcx.xinventories.internal.model

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Represents a time range for schedule-based conditions.
 */
data class TimeRange(
    val start: Instant,
    val end: Instant,
    val timezone: ZoneId = ZoneId.systemDefault()
) {
    /**
     * Checks if the current time falls within this range.
     */
    fun isActive(): Boolean = isActive(Instant.now())

    /**
     * Checks if a given instant falls within this range.
     */
    fun isActive(instant: Instant): Boolean {
        return !instant.isBefore(start) && !instant.isAfter(end)
    }

    /**
     * Gets the start time as a ZonedDateTime in the configured timezone.
     */
    fun getStartZoned(): ZonedDateTime = start.atZone(timezone)

    /**
     * Gets the end time as a ZonedDateTime in the configured timezone.
     */
    fun getEndZoned(): ZonedDateTime = end.atZone(timezone)

    companion object {
        private val ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME
        private val LOCAL_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        /**
         * Creates a TimeRange from string values.
         *
         * @param startStr Start time in ISO-8601 format (e.g., "2024-10-25T00:00:00")
         * @param endStr End time in ISO-8601 format
         * @param timezoneStr Timezone ID (e.g., "America/New_York") or null for system default
         * @return TimeRange or null if parsing fails
         */
        fun fromStrings(startStr: String, endStr: String, timezoneStr: String? = null): TimeRange? {
            return try {
                val timezone = timezoneStr?.let { ZoneId.of(it) } ?: ZoneId.systemDefault()

                val start = parseDateTime(startStr, timezone)
                val end = parseDateTime(endStr, timezone)

                if (start != null && end != null) {
                    TimeRange(start, end, timezone)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }

        private fun parseDateTime(str: String, timezone: ZoneId): Instant? {
            return try {
                // Try parsing as ZonedDateTime first
                ZonedDateTime.parse(str, ISO_FORMATTER).toInstant()
            } catch (e: DateTimeParseException) {
                try {
                    // Try parsing as LocalDateTime and apply timezone
                    java.time.LocalDateTime.parse(str, LOCAL_FORMATTER)
                        .atZone(timezone)
                        .toInstant()
                } catch (e2: DateTimeParseException) {
                    null
                }
            }
        }
    }
}

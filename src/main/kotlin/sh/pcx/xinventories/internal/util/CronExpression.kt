package sh.pcx.xinventories.internal.util

import java.time.DayOfWeek
import java.time.ZonedDateTime
import java.time.ZoneId

/**
 * Simple cron expression parser for schedule-based conditions.
 * Supports: minute hour day-of-month month day-of-week
 *
 * Field values:
 * - minute: 0-59
 * - hour: 0-23
 * - day-of-month: 1-31
 * - month: 1-12 or JAN-DEC
 * - day-of-week: 0-6 (0=Sunday) or SUN-SAT
 *
 * Supported syntax:
 * - * (any value)
 * - n (specific value)
 * - n-m (range)
 * - n,m,... (list)
 * - n-m,p-q (combined ranges)
 * - * /n (step values, e.g., * /15 for every 15 minutes)
 */
class CronExpression private constructor(
    private val minutes: Set<Int>,
    private val hours: Set<Int>,
    private val daysOfMonth: Set<Int>,
    private val months: Set<Int>,
    private val daysOfWeek: Set<Int>,
    private val dayOfMonthWildcard: Boolean,
    private val dayOfWeekWildcard: Boolean,
    val expression: String
) {
    /**
     * Checks if the cron expression matches the current time.
     */
    fun matches(): Boolean = matches(ZonedDateTime.now())

    /**
     * Checks if the cron expression matches the given time.
     */
    fun matches(dateTime: ZonedDateTime): Boolean {
        val minute = dateTime.minute
        val hour = dateTime.hour
        val dayOfMonth = dateTime.dayOfMonth
        val month = dateTime.monthValue
        val dayOfWeek = dateTime.dayOfWeek.value % 7 // Convert Monday=1..Sunday=7 to Sunday=0..Saturday=6

        // Standard cron behavior for day matching:
        // - If both day-of-month and day-of-week are wildcards: any day matches
        // - If only day-of-month is specific: check only day-of-month
        // - If only day-of-week is specific: check only day-of-week
        // - If both are specific: OR them (either can match)
        val dayMatches = when {
            dayOfMonthWildcard && dayOfWeekWildcard -> true
            dayOfMonthWildcard -> daysOfWeek.contains(dayOfWeek)
            dayOfWeekWildcard -> daysOfMonth.contains(dayOfMonth)
            else -> daysOfMonth.contains(dayOfMonth) || daysOfWeek.contains(dayOfWeek)
        }

        return minutes.contains(minute) &&
            hours.contains(hour) &&
            months.contains(month) &&
            dayMatches
    }

    /**
     * Checks if the cron expression matches the given time in a specific timezone.
     */
    fun matches(dateTime: ZonedDateTime, timezone: ZoneId): Boolean {
        return matches(dateTime.withZoneSameInstant(timezone))
    }

    override fun toString(): String = expression

    companion object {
        private val DAY_NAMES = mapOf(
            "SUN" to 0, "MON" to 1, "TUE" to 2, "WED" to 3,
            "THU" to 4, "FRI" to 5, "SAT" to 6
        )

        private val MONTH_NAMES = mapOf(
            "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4,
            "MAY" to 5, "JUN" to 6, "JUL" to 7, "AUG" to 8,
            "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
        )

        /**
         * Parses a cron expression string.
         *
         * @param expression The cron expression (5 fields: minute hour day-of-month month day-of-week)
         * @return CronExpression or null if parsing fails
         */
        fun parse(expression: String): CronExpression? {
            val parts = expression.trim().split(Regex("\\s+"))
            if (parts.size != 5) return null

            return try {
                val minutes = parseField(parts[0], 0, 59, emptyMap())
                val hours = parseField(parts[1], 0, 23, emptyMap())
                val daysOfMonth = parseField(parts[2], 1, 31, emptyMap())
                val months = parseField(parts[3], 1, 12, MONTH_NAMES)
                val daysOfWeek = parseField(parts[4], 0, 6, DAY_NAMES)

                if (minutes == null || hours == null || daysOfMonth == null ||
                    months == null || daysOfWeek == null) {
                    return null
                }

                val dayOfMonthWildcard = parts[2] == "*"
                val dayOfWeekWildcard = parts[4] == "*"

                CronExpression(minutes, hours, daysOfMonth, months, daysOfWeek,
                    dayOfMonthWildcard, dayOfWeekWildcard, expression)
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Checks if a cron expression string is valid.
         */
        fun isValid(expression: String): Boolean = parse(expression) != null

        private fun parseField(
            field: String,
            min: Int,
            max: Int,
            names: Map<String, Int>
        ): Set<Int>? {
            val result = mutableSetOf<Int>()

            // Handle wildcard with step (e.g., */15)
            if (field.startsWith("*/")) {
                val step = field.substring(2).toIntOrNull() ?: return null
                if (step <= 0) return null
                for (i in min..max step step) {
                    result.add(i)
                }
                return result
            }

            // Handle simple wildcard
            if (field == "*") {
                return (min..max).toSet()
            }

            // Handle comma-separated values
            for (part in field.split(",")) {
                val trimmed = part.trim().uppercase()

                // Handle range (e.g., 1-5 or MON-FRI)
                if (trimmed.contains("-")) {
                    val rangeParts = trimmed.split("-")
                    if (rangeParts.size != 2) return null

                    val rangeStart = parseValue(rangeParts[0], names) ?: return null
                    val rangeEnd = parseValue(rangeParts[1], names) ?: return null

                    if (rangeStart < min || rangeEnd > max || rangeStart > rangeEnd) {
                        return null
                    }

                    result.addAll(rangeStart..rangeEnd)
                } else {
                    // Single value
                    val value = parseValue(trimmed, names) ?: return null
                    if (value < min || value > max) return null
                    result.add(value)
                }
            }

            return if (result.isEmpty()) null else result
        }

        private fun parseValue(value: String, names: Map<String, Int>): Int? {
            // Try named value first
            names[value.uppercase()]?.let { return it }

            // Try numeric value
            return value.toIntOrNull()
        }
    }
}

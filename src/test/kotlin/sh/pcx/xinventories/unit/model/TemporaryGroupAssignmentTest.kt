package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.TemporaryGroupAssignment
import java.time.Duration
import java.time.Instant
import java.util.UUID

@DisplayName("TemporaryGroupAssignment")
class TemporaryGroupAssignmentTest {

    private val testUuid = UUID.randomUUID()

    @Nested
    @DisplayName("Creation")
    inner class CreationTests {

        @Test
        @DisplayName("create sets correct values")
        fun createSetsCorrectValues() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(2),
                assignedBy = "Admin"
            )

            assertEquals(testUuid, assignment.playerUuid)
            assertEquals("event", assignment.temporaryGroup)
            assertEquals("survival", assignment.originalGroup)
            assertEquals("Admin", assignment.assignedBy)
            assertNotNull(assignment.assignedAt)
            assertNotNull(assignment.expiresAt)
        }

        @Test
        @DisplayName("expiration is set correctly based on duration")
        fun expirationIsSetCorrectly() {
            val before = Instant.now()
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofMinutes(30),
                assignedBy = "Admin"
            )
            val after = Instant.now()

            val expectedMin = before.plus(Duration.ofMinutes(30))
            val expectedMax = after.plus(Duration.ofMinutes(30))

            assertTrue(assignment.expiresAt.isAfter(expectedMin.minusSeconds(1)))
            assertTrue(assignment.expiresAt.isBefore(expectedMax.plusSeconds(1)))
        }

        @Test
        @DisplayName("reason is optional")
        fun reasonIsOptional() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(1),
                assignedBy = "Admin"
            )

            assertNull(assignment.reason)

            val withReason = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(1),
                assignedBy = "Admin",
                reason = "Special event"
            )

            assertEquals("Special event", withReason.reason)
        }
    }

    @Nested
    @DisplayName("Expiration")
    inner class ExpirationTests {

        @Test
        @DisplayName("isExpired returns false for future expiration")
        fun isExpiredReturnsFalseForFuture() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(1),
                assignedBy = "Admin"
            )

            assertFalse(assignment.isExpired)
        }

        @Test
        @DisplayName("isExpired returns true for past expiration")
        fun isExpiredReturnsTrueForPast() {
            val assignment = TemporaryGroupAssignment(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                expiresAt = Instant.now().minus(Duration.ofHours(1)),
                assignedBy = "Admin"
            )

            assertTrue(assignment.isExpired)
        }

        @Test
        @DisplayName("getRemainingTime returns positive duration for future")
        fun getRemainingTimePositiveForFuture() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(2),
                assignedBy = "Admin"
            )

            val remaining = assignment.getRemainingTime()
            assertTrue(remaining.toMinutes() > 100) // At least 100 minutes
            assertTrue(remaining.toMinutes() < 121) // Less than 121 minutes
        }

        @Test
        @DisplayName("getRemainingTime returns zero for expired")
        fun getRemainingTimeZeroForExpired() {
            val assignment = TemporaryGroupAssignment(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                expiresAt = Instant.now().minus(Duration.ofHours(1)),
                assignedBy = "Admin"
            )

            assertEquals(Duration.ZERO, assignment.getRemainingTime())
        }
    }

    @Nested
    @DisplayName("Time formatting")
    inner class TimeFormattingTests {

        @Test
        @DisplayName("getRemainingTimeString formats correctly")
        fun getRemainingTimeStringFormatsCorrectly() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(2).plusMinutes(30),
                assignedBy = "Admin"
            )

            val remaining = assignment.getRemainingTimeString()
            assertTrue(remaining.contains("h"))
            assertTrue(remaining.contains("m"))
        }

        @Test
        @DisplayName("expired assignment shows Expired")
        fun expiredShowsExpired() {
            val assignment = TemporaryGroupAssignment(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                expiresAt = Instant.now().minus(Duration.ofHours(1)),
                assignedBy = "Admin"
            )

            assertEquals("Expired", assignment.getRemainingTimeString())
        }
    }

    @Nested
    @DisplayName("Extension")
    inner class ExtensionTests {

        @Test
        @DisplayName("extend adds duration to expiration")
        fun extendAddsToExpiration() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(1),
                assignedBy = "Admin"
            )

            val originalExpires = assignment.expiresAt
            val extended = assignment.extend(Duration.ofMinutes(30))

            assertEquals(originalExpires.plus(Duration.ofMinutes(30)), extended.expiresAt)
            // Other fields should be unchanged
            assertEquals(assignment.playerUuid, extended.playerUuid)
            assertEquals(assignment.temporaryGroup, extended.temporaryGroup)
            assertEquals(assignment.assignedAt, extended.assignedAt)
        }
    }

    @Nested
    @DisplayName("Duration parsing")
    inner class DurationParsingTests {

        @Test
        @DisplayName("parses simple hour duration")
        fun parsesSimpleHour() {
            val duration = TemporaryGroupAssignment.parseDuration("2h")

            assertNotNull(duration)
            assertEquals(2, duration!!.toHours())
        }

        @Test
        @DisplayName("parses simple minute duration")
        fun parsesSimpleMinute() {
            val duration = TemporaryGroupAssignment.parseDuration("30m")

            assertNotNull(duration)
            assertEquals(30, duration!!.toMinutes())
        }

        @Test
        @DisplayName("parses combined duration")
        fun parsesCombinedDuration() {
            val duration = TemporaryGroupAssignment.parseDuration("1h30m")

            assertNotNull(duration)
            assertEquals(90, duration!!.toMinutes())
        }

        @Test
        @DisplayName("parses days")
        fun parsesDays() {
            val duration = TemporaryGroupAssignment.parseDuration("2d")

            assertNotNull(duration)
            assertEquals(48, duration!!.toHours())
        }

        @Test
        @DisplayName("parses weeks")
        fun parsesWeeks() {
            val duration = TemporaryGroupAssignment.parseDuration("1w")

            assertNotNull(duration)
            assertEquals(7, duration!!.toDays())
        }

        @Test
        @DisplayName("parses complex duration")
        fun parsesComplexDuration() {
            val duration = TemporaryGroupAssignment.parseDuration("1w2d3h30m")

            assertNotNull(duration)
            val totalMinutes = (7 * 24 * 60) + (2 * 24 * 60) + (3 * 60) + 30
            assertEquals(totalMinutes.toLong(), duration!!.toMinutes())
        }

        @Test
        @DisplayName("parses seconds")
        fun parsesSeconds() {
            val duration = TemporaryGroupAssignment.parseDuration("90s")

            assertNotNull(duration)
            assertEquals(90, duration!!.toSeconds())
        }

        @Test
        @DisplayName("returns null for empty input")
        fun returnsNullForEmpty() {
            assertNull(TemporaryGroupAssignment.parseDuration(""))
            assertNull(TemporaryGroupAssignment.parseDuration("  "))
        }

        @Test
        @DisplayName("handles case insensitivity")
        fun handlesCaseInsensitivity() {
            val duration1 = TemporaryGroupAssignment.parseDuration("2H")
            val duration2 = TemporaryGroupAssignment.parseDuration("2h")

            assertNotNull(duration1)
            assertNotNull(duration2)
            assertEquals(duration1, duration2)
        }
    }

    @Nested
    @DisplayName("Duration formatting")
    inner class DurationFormattingTests {

        @Test
        @DisplayName("formats simple duration")
        fun formatsSimpleDuration() {
            val formatted = TemporaryGroupAssignment.formatDuration(Duration.ofHours(2))

            assertEquals("2h", formatted)
        }

        @Test
        @DisplayName("formats complex duration")
        fun formatsComplexDuration() {
            val duration = Duration.ofDays(1).plusHours(2).plusMinutes(30)
            val formatted = TemporaryGroupAssignment.formatDuration(duration)

            assertTrue(formatted.contains("1d"))
            assertTrue(formatted.contains("2h"))
            assertTrue(formatted.contains("30m"))
        }

        @Test
        @DisplayName("formats weeks correctly")
        fun formatsWeeks() {
            val formatted = TemporaryGroupAssignment.formatDuration(Duration.ofDays(14))

            assertTrue(formatted.contains("2w"))
        }

        @Test
        @DisplayName("formats zero as 0s")
        fun formatsZero() {
            assertEquals("0s", TemporaryGroupAssignment.formatDuration(Duration.ZERO))
        }
    }

    @Nested
    @DisplayName("Storage conversion")
    inner class StorageConversionTests {

        @Test
        @DisplayName("toStorageMap contains all fields")
        fun toStorageMapContainsAllFields() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(1),
                assignedBy = "Admin",
                reason = "Test reason"
            )

            val map = assignment.toStorageMap()

            assertEquals(testUuid.toString(), map["player_uuid"])
            assertEquals("event", map["temp_group"])
            assertEquals("survival", map["original_group"])
            assertEquals("Admin", map["assigned_by"])
            assertEquals("Test reason", map["reason"])
            assertNotNull(map["expires_at"])
            assertNotNull(map["assigned_at"])
        }

        @Test
        @DisplayName("fromStorageMap reconstructs assignment")
        fun fromStorageMapReconstructs() {
            val original = TemporaryGroupAssignment.create(
                playerUuid = testUuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(1),
                assignedBy = "Admin",
                reason = "Test"
            )

            val map = original.toStorageMap()
            val reconstructed = TemporaryGroupAssignment.fromStorageMap(map)

            assertNotNull(reconstructed)
            assertEquals(original.playerUuid, reconstructed!!.playerUuid)
            assertEquals(original.temporaryGroup, reconstructed.temporaryGroup)
            assertEquals(original.originalGroup, reconstructed.originalGroup)
            assertEquals(original.assignedBy, reconstructed.assignedBy)
            assertEquals(original.reason, reconstructed.reason)
        }

        @Test
        @DisplayName("fromStorageMap returns null for invalid data")
        fun fromStorageMapReturnsNullForInvalid() {
            val invalidMap = mapOf("invalid" to "data")

            assertNull(TemporaryGroupAssignment.fromStorageMap(invalidMap))
        }
    }
}

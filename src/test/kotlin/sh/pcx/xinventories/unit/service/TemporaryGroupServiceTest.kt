package sh.pcx.xinventories.unit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.api.event.TemporaryGroupRemoveEvent
import sh.pcx.xinventories.internal.model.TemporaryGroupAssignment
import java.time.Duration
import java.time.Instant
import java.util.UUID

@DisplayName("Temporary Group Components")
class TemporaryGroupServiceTest {

    @Nested
    @DisplayName("TemporaryGroupAssignment data class")
    inner class AssignmentDataClassTests {

        @Test
        @DisplayName("assignment stores all required fields")
        fun assignmentStoresAllFields() {
            val uuid = UUID.randomUUID()
            val now = Instant.now()

            val assignment = TemporaryGroupAssignment(
                playerUuid = uuid,
                temporaryGroup = "event",
                originalGroup = "survival",
                expiresAt = now.plus(Duration.ofHours(2)),
                assignedBy = "Admin",
                assignedAt = now,
                reason = "Special event"
            )

            assertEquals(uuid, assignment.playerUuid)
            assertEquals("event", assignment.temporaryGroup)
            assertEquals("survival", assignment.originalGroup)
            assertEquals("Admin", assignment.assignedBy)
            assertEquals("Special event", assignment.reason)
        }

        @Test
        @DisplayName("total duration is calculated correctly")
        fun totalDurationIsCorrect() {
            val now = Instant.now()
            val assignment = TemporaryGroupAssignment(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                expiresAt = now.plus(Duration.ofHours(3)),
                assignedBy = "Admin",
                assignedAt = now
            )

            val total = assignment.getTotalDuration()
            assertEquals(3, total.toHours())
        }

        @Test
        @DisplayName("elapsed time increases over time")
        fun elapsedTimeIncreasesOverTime() {
            val pastTime = Instant.now().minus(Duration.ofMinutes(10))
            val assignment = TemporaryGroupAssignment(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                expiresAt = Instant.now().plus(Duration.ofHours(1)),
                assignedBy = "Admin",
                assignedAt = pastTime
            )

            val elapsed = assignment.getElapsedTime()
            assertTrue(elapsed.toMinutes() >= 9) // At least 9 minutes
        }

        @Test
        @DisplayName("copy preserves immutability")
        fun copyPreservesImmutability() {
            val original = TemporaryGroupAssignment.create(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(1),
                assignedBy = "Admin"
            )

            val copy = original.copy(temporaryGroup = "different")

            assertEquals("event", original.temporaryGroup)
            assertEquals("different", copy.temporaryGroup)
            assertEquals(original.playerUuid, copy.playerUuid)
        }
    }

    @Nested
    @DisplayName("RemovalReason enum")
    inner class RemovalReasonTests {

        @Test
        @DisplayName("all removal reasons are defined")
        fun allReasonsAreDefined() {
            assertEquals(5, TemporaryGroupRemoveEvent.RemovalReason.entries.size)
            assertNotNull(TemporaryGroupRemoveEvent.RemovalReason.EXPIRED)
            assertNotNull(TemporaryGroupRemoveEvent.RemovalReason.MANUAL)
            assertNotNull(TemporaryGroupRemoveEvent.RemovalReason.PLUGIN_DISABLE)
            assertNotNull(TemporaryGroupRemoveEvent.RemovalReason.DATA_DELETED)
            assertNotNull(TemporaryGroupRemoveEvent.RemovalReason.API)
        }

        @Test
        @DisplayName("reasons have correct ordinals")
        fun reasonsHaveCorrectOrdinals() {
            assertEquals(0, TemporaryGroupRemoveEvent.RemovalReason.EXPIRED.ordinal)
            assertEquals(1, TemporaryGroupRemoveEvent.RemovalReason.MANUAL.ordinal)
        }
    }

    @Nested
    @DisplayName("Assignment lifecycle")
    inner class AssignmentLifecycleTests {

        @Test
        @DisplayName("newly created assignment is not expired")
        fun newAssignmentNotExpired() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(1),
                assignedBy = "Admin"
            )

            assertFalse(assignment.isExpired)
            assertTrue(assignment.getRemainingTime().toMinutes() > 50)
        }

        @Test
        @DisplayName("past expiration assignment is expired")
        fun pastExpirationIsExpired() {
            val assignment = TemporaryGroupAssignment(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                expiresAt = Instant.now().minus(Duration.ofSeconds(1)),
                assignedBy = "Admin"
            )

            assertTrue(assignment.isExpired)
            assertEquals(Duration.ZERO, assignment.getRemainingTime())
        }

        @Test
        @DisplayName("extended assignment has longer expiration")
        fun extendedAssignmentHasLongerExpiration() {
            val original = TemporaryGroupAssignment.create(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(1),
                assignedBy = "Admin"
            )

            val extended = original.extend(Duration.ofHours(1))

            assertTrue(extended.getRemainingTime().toMinutes() > original.getRemainingTime().toMinutes())
            assertTrue(extended.getRemainingTime().toMinutes() > 110) // ~2 hours
        }
    }

    @Nested
    @DisplayName("Duration handling edge cases")
    inner class DurationEdgeCasesTests {

        @Test
        @DisplayName("very short duration works")
        fun veryShortDurationWorks() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofSeconds(5),
                assignedBy = "Admin"
            )

            assertFalse(assignment.isExpired)
            assertTrue(assignment.getRemainingTime().toSeconds() <= 5)
        }

        @Test
        @DisplayName("very long duration works")
        fun veryLongDurationWorks() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofDays(365),
                assignedBy = "Admin"
            )

            assertFalse(assignment.isExpired)
            assertTrue(assignment.getRemainingTime().toDays() > 360)
        }

        @Test
        @DisplayName("zero duration is immediately expired")
        fun zeroDurationIsExpired() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ZERO,
                assignedBy = "Admin"
            )

            assertTrue(assignment.isExpired)
        }

        @Test
        @DisplayName("negative extension shortens expiration")
        fun negativeExtensionShortensExpiration() {
            val original = TemporaryGroupAssignment.create(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofHours(2),
                assignedBy = "Admin"
            )

            val shortened = original.extend(Duration.ofHours(-1))

            assertTrue(shortened.getRemainingTime().toMinutes() < original.getRemainingTime().toMinutes())
        }
    }

    @Nested
    @DisplayName("String formatting")
    inner class StringFormattingTests {

        @Test
        @DisplayName("remaining time string contains appropriate units")
        fun remainingTimeStringContainsUnits() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofDays(1).plusHours(2).plusMinutes(30),
                assignedBy = "Admin"
            )

            val remaining = assignment.getRemainingTimeString()

            assertTrue(remaining.contains("d") || remaining.contains("h"))
        }

        @Test
        @DisplayName("expired assignment shows 'Expired'")
        fun expiredAssignmentShowsExpired() {
            val assignment = TemporaryGroupAssignment(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                expiresAt = Instant.now().minus(Duration.ofHours(1)),
                assignedBy = "Admin"
            )

            assertEquals("Expired", assignment.getRemainingTimeString())
        }

        @Test
        @DisplayName("seconds only shows correctly")
        fun secondsOnlyShowsCorrectly() {
            val assignment = TemporaryGroupAssignment.create(
                playerUuid = UUID.randomUUID(),
                temporaryGroup = "event",
                originalGroup = "survival",
                duration = Duration.ofSeconds(30),
                assignedBy = "Admin"
            )

            val remaining = assignment.getRemainingTimeString()
            assertTrue(remaining.contains("s"))
            assertFalse(remaining.contains("m"))
            assertFalse(remaining.contains("h"))
        }
    }
}

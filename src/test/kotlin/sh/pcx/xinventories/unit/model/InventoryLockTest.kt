package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.InventoryLock
import sh.pcx.xinventories.internal.model.LockScope
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@DisplayName("InventoryLock")
class InventoryLockTest {

    private val playerUuid = UUID.randomUUID()
    private val lockedBy = UUID.randomUUID()

    @Nested
    @DisplayName("Creation")
    inner class Creation {

        @Test
        @DisplayName("createFullLock creates lock with ALL scope")
        fun createFullLockCreatesLockWithAllScope() {
            val lock = InventoryLock.createFullLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                duration = Duration.ofMinutes(30),
                reason = "Test reason"
            )

            assertEquals(playerUuid, lock.playerUuid)
            assertEquals(lockedBy, lock.lockedBy)
            assertEquals("Test reason", lock.reason)
            assertEquals(LockScope.ALL, lock.scope)
            Assertions.assertNotNull(lock.expiresAt)
        }

        @Test
        @DisplayName("createGroupLock creates lock with GROUP scope")
        fun createGroupLockCreatesLockWithGroupScope() {
            val lock = InventoryLock.createGroupLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                group = "survival",
                duration = Duration.ofHours(1),
                reason = "Group specific lock"
            )

            assertEquals(LockScope.GROUP, lock.scope)
            assertEquals("survival", lock.lockedGroup)
        }

        @Test
        @DisplayName("createSlotLock creates lock with SLOTS scope")
        fun createSlotLockCreatesLockWithSlotsScope() {
            val slots = setOf(0, 1, 2, 3)
            val lock = InventoryLock.createSlotLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                slots = slots,
                duration = Duration.ofMinutes(5),
                reason = "Hotbar lock"
            )

            assertEquals(LockScope.SLOTS, lock.scope)
            assertEquals(slots, lock.lockedSlots)
        }

        @Test
        @DisplayName("creates permanent lock when duration is null")
        fun createsPermanentLockWhenDurationIsNull() {
            val lock = InventoryLock.createFullLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                duration = null,
                reason = "Permanent lock"
            )

            Assertions.assertNull(lock.expiresAt)
        }
    }

    @Nested
    @DisplayName("Expiration")
    inner class Expiration {

        @Test
        @DisplayName("isExpired returns false for non-expired lock")
        fun isExpiredReturnsFalseForNonExpiredLock() {
            val lock = InventoryLock.createFullLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                duration = Duration.ofHours(1),
                reason = null
            )

            assertFalse(lock.isExpired())
            assertTrue(lock.isActive())
        }

        @Test
        @DisplayName("isExpired returns true for expired lock")
        fun isExpiredReturnsTrueForExpiredLock() {
            val lock = InventoryLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                lockedAt = Instant.now().minus(2, ChronoUnit.HOURS),
                expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
                reason = null,
                scope = LockScope.ALL
            )

            assertTrue(lock.isExpired())
            assertFalse(lock.isActive())
        }

        @Test
        @DisplayName("permanent lock is never expired")
        fun permanentLockIsNeverExpired() {
            val lock = InventoryLock.createFullLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                duration = null,
                reason = "Permanent"
            )

            assertFalse(lock.isExpired())
            assertTrue(lock.isActive())
        }

        @Test
        @DisplayName("getRemainingDuration returns correct duration")
        fun getRemainingDurationReturnsCorrectDuration() {
            val lock = InventoryLock.createFullLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                duration = Duration.ofMinutes(30),
                reason = null
            )

            val remaining = lock.getRemainingDuration()

            Assertions.assertNotNull(remaining)
            assertTrue(remaining!!.toMinutes() in 29..30)
        }

        @Test
        @DisplayName("getRemainingDuration returns null for permanent lock")
        fun getRemainingDurationReturnsNullForPermanentLock() {
            val lock = InventoryLock.createFullLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                duration = null,
                reason = null
            )

            Assertions.assertNull(lock.getRemainingDuration())
        }

        @Test
        @DisplayName("getRemainingDuration returns zero for expired lock")
        fun getRemainingDurationReturnsZeroForExpiredLock() {
            val lock = InventoryLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                lockedAt = Instant.now().minus(2, ChronoUnit.HOURS),
                expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
                reason = null,
                scope = LockScope.ALL
            )

            val remaining = lock.getRemainingDuration()

            Assertions.assertNotNull(remaining)
            assertEquals(Duration.ZERO, remaining)
        }
    }

    @Nested
    @DisplayName("getRemainingTimeString")
    inner class GetRemainingTimeString {

        @Test
        @DisplayName("returns 'permanent' for permanent lock")
        fun returnsPermanentForPermanentLock() {
            val lock = InventoryLock.createFullLock(playerUuid, lockedBy, null, null)

            assertEquals("permanent", lock.getRemainingTimeString())
        }

        @Test
        @DisplayName("returns 'expired' for expired lock")
        fun returnsExpiredForExpiredLock() {
            val lock = InventoryLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                lockedAt = Instant.now().minus(2, ChronoUnit.HOURS),
                expiresAt = Instant.now().minus(1, ChronoUnit.HOURS),
                reason = null,
                scope = LockScope.ALL
            )

            assertEquals("expired", lock.getRemainingTimeString())
        }

        @Test
        @DisplayName("formats seconds correctly")
        fun formatsSecondsCorrectly() {
            val lock = InventoryLock.createFullLock(playerUuid, lockedBy, Duration.ofSeconds(30), null)

            assertTrue(lock.getRemainingTimeString().matches(Regex("\\d+s")))
        }

        @Test
        @DisplayName("formats minutes correctly")
        fun formatsMinutesCorrectly() {
            val lock = InventoryLock.createFullLock(playerUuid, lockedBy, Duration.ofMinutes(5), null)

            assertTrue(lock.getRemainingTimeString().matches(Regex("\\d+m \\d+s")))
        }

        @Test
        @DisplayName("formats hours correctly")
        fun formatsHoursCorrectly() {
            val lock = InventoryLock.createFullLock(playerUuid, lockedBy, Duration.ofHours(2), null)

            assertTrue(lock.getRemainingTimeString().matches(Regex("\\d+h \\d+m")))
        }

        @Test
        @DisplayName("formats days correctly")
        fun formatsDaysCorrectly() {
            val lock = InventoryLock.createFullLock(playerUuid, lockedBy, Duration.ofDays(2), null)

            assertTrue(lock.getRemainingTimeString().matches(Regex("\\d+d \\d+h")))
        }
    }

    @Nested
    @DisplayName("Scope Checks")
    inner class ScopeChecks {

        @Test
        @DisplayName("ALL scope applies to any group")
        fun allScopeAppliesToAnyGroup() {
            val lock = InventoryLock.createFullLock(playerUuid, lockedBy, null, null)

            assertTrue(lock.appliesToGroup("survival"))
            assertTrue(lock.appliesToGroup("creative"))
            assertTrue(lock.appliesToGroup("any_group"))
        }

        @Test
        @DisplayName("GROUP scope applies only to specific group")
        fun groupScopeAppliesOnlyToSpecificGroup() {
            val lock = InventoryLock.createGroupLock(playerUuid, lockedBy, "survival", null, null)

            assertTrue(lock.appliesToGroup("survival"))
            assertFalse(lock.appliesToGroup("creative"))
        }

        @Test
        @DisplayName("ALL scope applies to any slot")
        fun allScopeAppliesToAnySlot() {
            val lock = InventoryLock.createFullLock(playerUuid, lockedBy, null, null)

            assertTrue(lock.appliesToSlot(0))
            assertTrue(lock.appliesToSlot(35))
            assertTrue(lock.appliesToSlot(40))
        }

        @Test
        @DisplayName("SLOTS scope applies only to specific slots")
        fun slotsScopeAppliesOnlyToSpecificSlots() {
            val lock = InventoryLock.createSlotLock(playerUuid, lockedBy, setOf(0, 1, 2), null, null)

            assertTrue(lock.appliesToSlot(0))
            assertTrue(lock.appliesToSlot(1))
            assertTrue(lock.appliesToSlot(2))
            assertFalse(lock.appliesToSlot(3))
            assertFalse(lock.appliesToSlot(35))
        }
    }

    @Nested
    @DisplayName("parseDuration")
    inner class ParseDuration {

        @Test
        @DisplayName("parses seconds")
        fun parsesSeconds() {
            val duration = InventoryLock.parseDuration("30s")

            Assertions.assertNotNull(duration)
            assertEquals(30, duration!!.seconds)
        }

        @Test
        @DisplayName("parses minutes")
        fun parsesMinutes() {
            val duration = InventoryLock.parseDuration("5m")

            Assertions.assertNotNull(duration)
            assertEquals(5, duration!!.toMinutes())
        }

        @Test
        @DisplayName("parses hours")
        fun parsesHours() {
            val duration = InventoryLock.parseDuration("2h")

            Assertions.assertNotNull(duration)
            assertEquals(2, duration!!.toHours())
        }

        @Test
        @DisplayName("parses days")
        fun parsesDays() {
            val duration = InventoryLock.parseDuration("7d")

            Assertions.assertNotNull(duration)
            assertEquals(7, duration!!.toDays())
        }

        @Test
        @DisplayName("parses weeks")
        fun parsesWeeks() {
            val duration = InventoryLock.parseDuration("2w")

            Assertions.assertNotNull(duration)
            assertEquals(14, duration!!.toDays())
        }

        @Test
        @DisplayName("is case insensitive")
        fun isCaseInsensitive() {
            assertEquals(Duration.ofMinutes(5), InventoryLock.parseDuration("5M"))
            assertEquals(Duration.ofHours(1), InventoryLock.parseDuration("1H"))
        }

        @Test
        @DisplayName("trims whitespace")
        fun trimsWhitespace() {
            assertEquals(Duration.ofMinutes(10), InventoryLock.parseDuration("  10m  "))
        }

        @Test
        @DisplayName("returns null for invalid format")
        fun returnsNullForInvalidFormat() {
            Assertions.assertNull(InventoryLock.parseDuration("invalid"))
            Assertions.assertNull(InventoryLock.parseDuration(""))
            Assertions.assertNull(InventoryLock.parseDuration("5"))
            Assertions.assertNull(InventoryLock.parseDuration("5x"))
        }

        @Test
        @DisplayName("returns null for zero or negative values")
        fun returnsNullForZeroOrNegativeValues() {
            Assertions.assertNull(InventoryLock.parseDuration("0s"))
            Assertions.assertNull(InventoryLock.parseDuration("-5m"))
        }
    }
}

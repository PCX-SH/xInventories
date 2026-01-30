package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import sh.pcx.xinventories.internal.model.InventoryLock
import sh.pcx.xinventories.internal.model.LockScope
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Unit tests for LockManagerGUI logic.
 *
 * These tests verify the inventory lock logic that powers the GUI,
 * without requiring full Bukkit/server infrastructure.
 */
@DisplayName("LockManagerGUI Logic Tests")
class LockManagerGUITest {

    // ============================================================
    // Lock Creation Tests
    // ============================================================

    @Nested
    @DisplayName("Lock Creation Logic")
    inner class LockCreationTests {

        @Test
        @DisplayName("should create permanent lock")
        fun createPermanentLock() {
            val playerUuid = UUID.randomUUID()
            val adminUuid = UUID.randomUUID()

            val lock = InventoryLock.createFullLock(
                playerUuid = playerUuid,
                lockedBy = adminUuid,
                duration = null,
                reason = "Test reason"
            )

            assertEquals(playerUuid, lock.playerUuid)
            assertEquals(adminUuid, lock.lockedBy)
            assertNull(lock.expiresAt)
            assertEquals("Test reason", lock.reason)
            assertEquals(LockScope.ALL, lock.scope)
        }

        @Test
        @DisplayName("should create timed lock")
        fun createTimedLock() {
            val playerUuid = UUID.randomUUID()
            val duration = Duration.ofHours(1)

            val lock = InventoryLock.createFullLock(
                playerUuid = playerUuid,
                lockedBy = null,
                duration = duration,
                reason = null
            )

            assertNotNull(lock.expiresAt)
            assertTrue(lock.expiresAt!!.isAfter(Instant.now()))
        }

        @Test
        @DisplayName("should create group-specific lock")
        fun createGroupLock() {
            val playerUuid = UUID.randomUUID()

            val lock = InventoryLock.createGroupLock(
                playerUuid = playerUuid,
                lockedBy = null,
                group = "survival",
                reason = "Group restriction"
            )

            assertEquals(LockScope.GROUP, lock.scope)
            assertEquals("survival", lock.lockedGroup)
        }

        @Test
        @DisplayName("should create slot-specific lock")
        fun createSlotLock() {
            val playerUuid = UUID.randomUUID()
            val slots = setOf(0, 1, 2, 3, 4)

            val lock = InventoryLock.createSlotLock(
                playerUuid = playerUuid,
                lockedBy = null,
                slots = slots,
                reason = "Hotbar lock"
            )

            assertEquals(LockScope.SLOTS, lock.scope)
            assertEquals(slots, lock.lockedSlots)
        }
    }

    // ============================================================
    // Lock Expiration Tests
    // ============================================================

    @Nested
    @DisplayName("Lock Expiration Logic")
    inner class ExpirationTests {

        @Test
        @DisplayName("should detect expired lock")
        fun detectExpiredLock() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now().minus(Duration.ofHours(2)),
                expiresAt = Instant.now().minus(Duration.ofHours(1)),
                reason = null,
                scope = LockScope.ALL
            )

            assertTrue(lock.isExpired())
            assertFalse(lock.isActive())
        }

        @Test
        @DisplayName("should detect active lock")
        fun detectActiveLock() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now(),
                expiresAt = Instant.now().plus(Duration.ofHours(1)),
                reason = null,
                scope = LockScope.ALL
            )

            assertFalse(lock.isExpired())
            assertTrue(lock.isActive())
        }

        @Test
        @DisplayName("should handle permanent lock as never expired")
        fun permanentLockNeverExpires() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now().minus(Duration.ofDays(365)),
                expiresAt = null,
                reason = null,
                scope = LockScope.ALL
            )

            assertFalse(lock.isExpired())
            assertTrue(lock.isActive())
        }
    }

    // ============================================================
    // Remaining Duration Tests
    // ============================================================

    @Nested
    @DisplayName("Remaining Duration Logic")
    inner class RemainingDurationTests {

        @Test
        @DisplayName("should calculate remaining duration")
        fun calculateRemainingDuration() {
            val expiresAt = Instant.now().plus(Duration.ofHours(2))
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now(),
                expiresAt = expiresAt,
                reason = null,
                scope = LockScope.ALL
            )

            val remaining = lock.getRemainingDuration()

            assertNotNull(remaining)
            assertTrue(remaining!!.toMinutes() > 100) // Roughly 2 hours
            assertTrue(remaining.toMinutes() < 130)
        }

        @Test
        @DisplayName("should return null for permanent lock")
        fun nullForPermanentLock() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now(),
                expiresAt = null,
                reason = null,
                scope = LockScope.ALL
            )

            assertNull(lock.getRemainingDuration())
        }

        @Test
        @DisplayName("should return zero for expired lock")
        fun zeroForExpiredLock() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now().minus(Duration.ofHours(2)),
                expiresAt = Instant.now().minus(Duration.ofHours(1)),
                reason = null,
                scope = LockScope.ALL
            )

            val remaining = lock.getRemainingDuration()

            assertEquals(Duration.ZERO, remaining)
        }
    }

    // ============================================================
    // Time String Formatting Tests
    // ============================================================

    @Nested
    @DisplayName("Time String Formatting")
    inner class TimeStringTests {

        @Test
        @DisplayName("should format permanent as 'permanent'")
        fun formatPermanent() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now(),
                expiresAt = null,
                reason = null,
                scope = LockScope.ALL
            )

            assertEquals("permanent", lock.getRemainingTimeString())
        }

        @Test
        @DisplayName("should format expired as 'expired'")
        fun formatExpired() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now().minus(Duration.ofHours(2)),
                expiresAt = Instant.now().minus(Duration.ofHours(1)),
                reason = null,
                scope = LockScope.ALL
            )

            assertEquals("expired", lock.getRemainingTimeString())
        }

        @Test
        @DisplayName("should format seconds")
        fun formatSeconds() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now(),
                expiresAt = Instant.now().plusSeconds(45),
                reason = null,
                scope = LockScope.ALL
            )

            val timeString = lock.getRemainingTimeString()

            assertTrue(timeString.endsWith("s"))
        }

        @Test
        @DisplayName("should format minutes")
        fun formatMinutes() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now(),
                expiresAt = Instant.now().plus(Duration.ofMinutes(30)),
                reason = null,
                scope = LockScope.ALL
            )

            val timeString = lock.getRemainingTimeString()

            assertTrue(timeString.contains("m"))
        }

        @Test
        @DisplayName("should format hours")
        fun formatHours() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now(),
                expiresAt = Instant.now().plus(Duration.ofHours(5)),
                reason = null,
                scope = LockScope.ALL
            )

            val timeString = lock.getRemainingTimeString()

            assertTrue(timeString.contains("h"))
        }

        @Test
        @DisplayName("should format days")
        fun formatDays() {
            val lock = InventoryLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                lockedAt = Instant.now(),
                expiresAt = Instant.now().plus(Duration.ofDays(3)),
                reason = null,
                scope = LockScope.ALL
            )

            val timeString = lock.getRemainingTimeString()

            assertTrue(timeString.contains("d"))
        }
    }

    // ============================================================
    // Duration Parsing Tests
    // ============================================================

    @Nested
    @DisplayName("Duration Parsing")
    inner class DurationParsingTests {

        @Test
        @DisplayName("should parse seconds")
        fun parseSeconds() {
            val duration = InventoryLock.parseDuration("30s")

            assertNotNull(duration)
            assertEquals(30, duration!!.toSeconds())
        }

        @Test
        @DisplayName("should parse minutes")
        fun parseMinutes() {
            val duration = InventoryLock.parseDuration("5m")

            assertNotNull(duration)
            assertEquals(5, duration!!.toMinutes())
        }

        @Test
        @DisplayName("should parse hours")
        fun parseHours() {
            val duration = InventoryLock.parseDuration("2h")

            assertNotNull(duration)
            assertEquals(2, duration!!.toHours())
        }

        @Test
        @DisplayName("should parse days")
        fun parseDays() {
            val duration = InventoryLock.parseDuration("7d")

            assertNotNull(duration)
            assertEquals(7, duration!!.toDays())
        }

        @Test
        @DisplayName("should parse weeks")
        fun parseWeeks() {
            val duration = InventoryLock.parseDuration("2w")

            assertNotNull(duration)
            assertEquals(14, duration!!.toDays())
        }

        @Test
        @DisplayName("should return null for invalid format")
        fun invalidFormat() {
            assertNull(InventoryLock.parseDuration("invalid"))
            assertNull(InventoryLock.parseDuration(""))
            assertNull(InventoryLock.parseDuration("5x"))
            assertNull(InventoryLock.parseDuration("-5m"))
        }

        @Test
        @DisplayName("should handle case insensitivity")
        fun caseInsensitive() {
            assertNotNull(InventoryLock.parseDuration("5M"))
            assertNotNull(InventoryLock.parseDuration("5H"))
            assertNotNull(InventoryLock.parseDuration("5D"))
        }
    }

    // ============================================================
    // Scope Application Tests
    // ============================================================

    @Nested
    @DisplayName("Scope Application Logic")
    inner class ScopeApplicationTests {

        @Test
        @DisplayName("should apply ALL scope to any group")
        fun allScopeAppliesToAllGroups() {
            val lock = InventoryLock.createFullLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null
            )

            assertTrue(lock.appliesToGroup("survival"))
            assertTrue(lock.appliesToGroup("creative"))
            assertTrue(lock.appliesToGroup("anything"))
        }

        @Test
        @DisplayName("should apply GROUP scope only to specific group")
        fun groupScopeOnlyApplies() {
            val lock = InventoryLock.createGroupLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                group = "survival"
            )

            assertTrue(lock.appliesToGroup("survival"))
            assertFalse(lock.appliesToGroup("creative"))
        }

        @Test
        @DisplayName("should apply ALL scope to any slot")
        fun allScopeAppliesToAllSlots() {
            val lock = InventoryLock.createFullLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null
            )

            assertTrue(lock.appliesToSlot(0))
            assertTrue(lock.appliesToSlot(20))
            assertTrue(lock.appliesToSlot(40))
        }

        @Test
        @DisplayName("should apply SLOTS scope only to specific slots")
        fun slotsScopeOnlyApplies() {
            val lock = InventoryLock.createSlotLock(
                playerUuid = UUID.randomUUID(),
                lockedBy = null,
                slots = setOf(0, 1, 2)
            )

            assertTrue(lock.appliesToSlot(0))
            assertTrue(lock.appliesToSlot(1))
            assertTrue(lock.appliesToSlot(2))
            assertFalse(lock.appliesToSlot(3))
            assertFalse(lock.appliesToSlot(40))
        }
    }

    // ============================================================
    // Filter Mode Tests (GUI Logic)
    // ============================================================

    @Nested
    @DisplayName("Filter Mode Logic")
    inner class FilterModeTests {

        private fun createLocks(): List<InventoryLock> {
            val now = Instant.now()
            return listOf(
                // Permanent lock
                InventoryLock(
                    playerUuid = UUID.randomUUID(),
                    lockedBy = null,
                    lockedAt = now,
                    expiresAt = null,
                    reason = null,
                    scope = LockScope.ALL
                ),
                // Expiring soon (30 minutes)
                InventoryLock(
                    playerUuid = UUID.randomUUID(),
                    lockedBy = null,
                    lockedAt = now,
                    expiresAt = now.plus(Duration.ofMinutes(30)),
                    reason = null,
                    scope = LockScope.ALL
                ),
                // Expiring later (5 hours)
                InventoryLock(
                    playerUuid = UUID.randomUUID(),
                    lockedBy = null,
                    lockedAt = now,
                    expiresAt = now.plus(Duration.ofHours(5)),
                    reason = null,
                    scope = LockScope.ALL
                )
            )
        }

        @Test
        @DisplayName("should filter ALL locks")
        fun filterAll() {
            val locks = createLocks()

            assertEquals(3, locks.size)
        }

        @Test
        @DisplayName("should filter permanent locks")
        fun filterPermanent() {
            val locks = createLocks()
            val permanent = locks.filter { it.expiresAt == null }

            assertEquals(1, permanent.size)
        }

        @Test
        @DisplayName("should filter expiring soon locks")
        fun filterExpiringSoon() {
            val locks = createLocks()
            val oneHourFromNow = Instant.now().plus(Duration.ofHours(1))
            val expiringSoon = locks.filter { lock ->
                lock.expiresAt != null && lock.expiresAt.isBefore(oneHourFromNow)
            }

            assertEquals(1, expiringSoon.size)
        }

        @Test
        @DisplayName("should sort locks by locked time")
        fun sortByLockedTime() {
            val now = Instant.now()
            val locks = listOf(
                InventoryLock(
                    playerUuid = UUID.randomUUID(),
                    lockedBy = null,
                    lockedAt = now.minus(Duration.ofHours(2)),
                    expiresAt = null,
                    reason = null,
                    scope = LockScope.ALL
                ),
                InventoryLock(
                    playerUuid = UUID.randomUUID(),
                    lockedBy = null,
                    lockedAt = now,
                    expiresAt = null,
                    reason = null,
                    scope = LockScope.ALL
                ),
                InventoryLock(
                    playerUuid = UUID.randomUUID(),
                    lockedBy = null,
                    lockedAt = now.minus(Duration.ofHours(1)),
                    expiresAt = null,
                    reason = null,
                    scope = LockScope.ALL
                )
            )

            val sorted = locks.sortedBy { it.lockedAt }

            assertTrue(sorted[0].lockedAt.isBefore(sorted[1].lockedAt))
            assertTrue(sorted[1].lockedAt.isBefore(sorted[2].lockedAt))
        }
    }

    // ============================================================
    // Pagination Tests
    // ============================================================

    @Nested
    @DisplayName("Pagination Logic")
    inner class PaginationTests {

        @Test
        @DisplayName("should calculate page count")
        fun calculatePageCount() {
            val lockCount = 50
            val locksPerPage = 21

            val totalPages = (lockCount + locksPerPage - 1) / locksPerPage

            assertEquals(3, totalPages)
        }

        @Test
        @DisplayName("should get locks for page")
        fun getLocksForPage() {
            val locks = (1..50).map { i ->
                InventoryLock(
                    playerUuid = UUID.randomUUID(),
                    lockedBy = null,
                    lockedAt = Instant.now().plusSeconds(i.toLong()),
                    expiresAt = null,
                    reason = "Lock $i",
                    scope = LockScope.ALL
                )
            }
            val locksPerPage = 21
            val page = 1

            val startIndex = page * locksPerPage
            val endIndex = minOf(startIndex + locksPerPage, locks.size)
            val locksOnPage = locks.subList(startIndex, endIndex)

            assertEquals(21, locksOnPage.size)
        }
    }
}

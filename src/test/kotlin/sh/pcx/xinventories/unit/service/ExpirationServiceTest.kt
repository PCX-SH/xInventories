package sh.pcx.xinventories.unit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.service.ExpirationConfig
import sh.pcx.xinventories.internal.service.ExpiredPlayerInfo
import sh.pcx.xinventories.internal.service.ExpirationResult
import sh.pcx.xinventories.internal.service.ExpirationStatus
import java.time.Duration
import java.time.Instant
import java.util.UUID

@DisplayName("Expiration Components")
class ExpirationServiceTest {

    @Nested
    @DisplayName("ExpirationConfig")
    inner class ExpirationConfigTests {

        @Test
        @DisplayName("default config has sensible values")
        fun defaultConfigHasSensibleValues() {
            val config = ExpirationConfig()

            assertFalse(config.enabled)
            assertEquals(90, config.inactivityDays)
            assertEquals("xinventories.expiration.exempt", config.excludePermission)
            assertTrue(config.backupBeforeDelete)
            assertEquals("0 4 * * 0", config.schedule)
        }

        @Test
        @DisplayName("custom config preserves values")
        fun customConfigPreservesValues() {
            val config = ExpirationConfig(
                enabled = true,
                inactivityDays = 30,
                excludePermission = "custom.permission",
                backupBeforeDelete = false,
                schedule = "0 0 * * *"
            )

            assertTrue(config.enabled)
            assertEquals(30, config.inactivityDays)
            assertEquals("custom.permission", config.excludePermission)
            assertFalse(config.backupBeforeDelete)
            assertEquals("0 0 * * *", config.schedule)
        }
    }

    @Nested
    @DisplayName("ExpiredPlayerInfo")
    inner class ExpiredPlayerInfoTests {

        @Test
        @DisplayName("stores player information correctly")
        fun storesPlayerInfoCorrectly() {
            val uuid = UUID.randomUUID()
            val lastActivity = Instant.now().minus(Duration.ofDays(100))

            val info = ExpiredPlayerInfo(
                uuid = uuid,
                name = "TestPlayer",
                lastActivity = lastActivity,
                daysSinceActivity = 100,
                groupCount = 3
            )

            assertEquals(uuid, info.uuid)
            assertEquals("TestPlayer", info.name)
            assertEquals(lastActivity, info.lastActivity)
            assertEquals(100, info.daysSinceActivity)
            assertEquals(3, info.groupCount)
        }
    }

    @Nested
    @DisplayName("ExpirationResult")
    inner class ExpirationResultTests {

        @Test
        @DisplayName("successful result has correct properties")
        fun successfulResultHasCorrectProperties() {
            val startTime = Instant.now().minusSeconds(10)
            val endTime = Instant.now()

            val result = ExpirationResult(
                success = true,
                dryRun = false,
                playersProcessed = 5,
                dataEntriesDeleted = 15,
                backupCreated = "backup-123",
                startTime = startTime,
                endTime = endTime,
                errors = emptyList()
            )

            assertTrue(result.success)
            assertFalse(result.dryRun)
            assertEquals(5, result.playersProcessed)
            assertEquals(15, result.dataEntriesDeleted)
            assertEquals("backup-123", result.backupCreated)
            assertTrue(result.errors.isEmpty())
        }

        @Test
        @DisplayName("duration is calculated correctly")
        fun durationIsCalculatedCorrectly() {
            val startTime = Instant.now().minusSeconds(30)
            val endTime = Instant.now()

            val result = ExpirationResult(
                success = true,
                dryRun = false,
                playersProcessed = 0,
                dataEntriesDeleted = 0,
                backupCreated = null,
                startTime = startTime,
                endTime = endTime,
                errors = emptyList()
            )

            assertTrue(result.duration.toSeconds() >= 29)
            assertTrue(result.duration.toSeconds() <= 31)
        }

        @Test
        @DisplayName("dry run result indicates no actual changes")
        fun dryRunResultIndicatesNoChanges() {
            val result = ExpirationResult(
                success = true,
                dryRun = true,
                playersProcessed = 10,
                dataEntriesDeleted = 30,
                backupCreated = null,
                startTime = Instant.now(),
                endTime = Instant.now(),
                errors = emptyList()
            )

            assertTrue(result.dryRun)
            assertEquals(10, result.playersProcessed)
        }

        @Test
        @DisplayName("failed result contains errors")
        fun failedResultContainsErrors() {
            val result = ExpirationResult(
                success = false,
                dryRun = false,
                playersProcessed = 5,
                dataEntriesDeleted = 10,
                backupCreated = null,
                startTime = Instant.now(),
                endTime = Instant.now(),
                errors = listOf("Error 1", "Error 2")
            )

            assertFalse(result.success)
            assertEquals(2, result.errors.size)
            assertTrue(result.errors.contains("Error 1"))
        }
    }

    @Nested
    @DisplayName("ExpirationStatus")
    inner class ExpirationStatusTests {

        @Test
        @DisplayName("status contains all configuration info")
        fun statusContainsAllConfigInfo() {
            val nextRun = Instant.now().plusSeconds(3600)

            val status = ExpirationStatus(
                enabled = true,
                inactivityDays = 90,
                excludePermission = "xinventories.expiration.exempt",
                backupBeforeDelete = true,
                schedule = "0 4 * * 0",
                excludedPlayerCount = 5,
                cachedActivityCount = 100,
                nextScheduledRun = nextRun
            )

            assertTrue(status.enabled)
            assertEquals(90, status.inactivityDays)
            assertEquals("xinventories.expiration.exempt", status.excludePermission)
            assertTrue(status.backupBeforeDelete)
            assertEquals("0 4 * * 0", status.schedule)
            assertEquals(5, status.excludedPlayerCount)
            assertEquals(100, status.cachedActivityCount)
            assertEquals(nextRun, status.nextScheduledRun)
        }

        @Test
        @DisplayName("disabled status has no next run")
        fun disabledStatusHasNoNextRun() {
            val status = ExpirationStatus(
                enabled = false,
                inactivityDays = 90,
                excludePermission = "perm",
                backupBeforeDelete = true,
                schedule = "",
                excludedPlayerCount = 0,
                cachedActivityCount = 0,
                nextScheduledRun = null
            )

            assertFalse(status.enabled)
            assertNull(status.nextScheduledRun)
        }
    }
}

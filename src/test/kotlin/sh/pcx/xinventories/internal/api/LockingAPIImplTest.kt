package sh.pcx.xinventories.internal.api

import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.LockingConfig
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.model.InventoryLock
import sh.pcx.xinventories.internal.model.LockScope
import sh.pcx.xinventories.internal.service.LockingService
import sh.pcx.xinventories.internal.service.ServiceManager
import java.time.Duration
import java.time.Instant
import java.util.*

@DisplayName("LockingAPIImpl")
class LockingAPIImplTest {

    private lateinit var plugin: XInventories
    private lateinit var api: LockingAPIImpl
    private lateinit var serviceManager: ServiceManager
    private lateinit var lockingService: LockingService
    private lateinit var configManager: ConfigManager

    private val testUuid = UUID.randomUUID()
    private val adminUuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        lockingService = mockk(relaxed = true)
        configManager = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { plugin.configManager } returns configManager
        every { serviceManager.lockingService } returns lockingService

        val mainConfig = mockk<MainConfig>()
        val lockingConfig = LockingConfig(enabled = true)
        every { configManager.mainConfig } returns mainConfig
        every { mainConfig.locking } returns lockingConfig

        api = LockingAPIImpl(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("lock with Duration")
    inner class LockWithDuration {

        @Test
        @DisplayName("creates lock with duration")
        fun createsLockWithDuration() {
            val lock = createTestLock()
            every { lockingService.lockInventory(testUuid, adminUuid, Duration.ofMinutes(30), "Test reason", LockScope.ALL) } returns lock

            val result = api.lock(testUuid, adminUuid, Duration.ofMinutes(30), "Test reason", LockScope.ALL)

            Assertions.assertNotNull(result)
            assertEquals(testUuid, result?.playerUuid)
        }

        @Test
        @DisplayName("creates permanent lock when duration is null")
        fun createsPermanentLockWhenDurationIsNull() {
            val lock = createTestLock(expiresAt = null)
            every { lockingService.lockInventory(testUuid, adminUuid, null, "Permanent lock", LockScope.ALL) } returns lock

            val result = api.lock(testUuid, adminUuid, null as Duration?, "Permanent lock")

            Assertions.assertNotNull(result)
            Assertions.assertNull(result?.expiresAt)
        }

        @Test
        @DisplayName("returns null when locking is disabled")
        fun returnsNullWhenLockingIsDisabled() {
            every { lockingService.lockInventory(any(), any(), any(), any(), any()) } returns null

            val result = api.lock(testUuid, adminUuid, Duration.ofMinutes(5))

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("lock with duration string")
    inner class LockWithDurationString {

        @Test
        @DisplayName("creates lock with 30 seconds duration string")
        fun createsLockWith30SecondsDurationString() {
            val duration = Duration.ofSeconds(30)
            val lock = createTestLock()
            every { lockingService.lockInventory(testUuid, adminUuid, duration, null, LockScope.ALL) } returns lock

            val result = api.lock(testUuid, adminUuid, "30s")

            Assertions.assertNotNull(result)
        }

        @Test
        @DisplayName("creates lock with 5 minutes duration string")
        fun createsLockWith5MinutesDurationString() {
            val duration = Duration.ofMinutes(5)
            val lock = createTestLock()
            every { lockingService.lockInventory(testUuid, adminUuid, duration, null, LockScope.ALL) } returns lock

            val result = api.lock(testUuid, adminUuid, "5m")

            Assertions.assertNotNull(result)
        }

        @Test
        @DisplayName("creates lock with 2 hours duration string")
        fun createsLockWith2HoursDurationString() {
            val duration = Duration.ofHours(2)
            val lock = createTestLock()
            every { lockingService.lockInventory(testUuid, adminUuid, duration, null, LockScope.ALL) } returns lock

            val result = api.lock(testUuid, adminUuid, "2h")

            Assertions.assertNotNull(result)
        }

        @Test
        @DisplayName("creates lock with 1 day duration string")
        fun createsLockWith1DayDurationString() {
            val duration = Duration.ofDays(1)
            val lock = createTestLock()
            every { lockingService.lockInventory(testUuid, adminUuid, duration, null, LockScope.ALL) } returns lock

            val result = api.lock(testUuid, adminUuid, "1d")

            Assertions.assertNotNull(result)
        }

        @Test
        @DisplayName("creates lock with 1 week duration string")
        fun createsLockWith1WeekDurationString() {
            val duration = Duration.ofDays(7)
            val lock = createTestLock()
            every { lockingService.lockInventory(testUuid, adminUuid, duration, null, LockScope.ALL) } returns lock

            val result = api.lock(testUuid, adminUuid, "1w")

            Assertions.assertNotNull(result)
        }

        @Test
        @DisplayName("creates permanent lock with null duration string")
        fun createsPermanentLockWithNullDurationString() {
            val lock = createTestLock(expiresAt = null)
            every { lockingService.lockInventory(testUuid, adminUuid, null, "Permanent", LockScope.ALL) } returns lock

            val result = api.lock(testUuid, adminUuid, null as String?, "Permanent")

            Assertions.assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("unlock")
    inner class Unlock {

        @Test
        @DisplayName("unlocks player successfully")
        fun unlocksPlayerSuccessfully() {
            every { lockingService.unlockInventory(testUuid) } returns true

            val result = api.unlock(testUuid)

            assertTrue(result)
            verify { lockingService.unlockInventory(testUuid) }
        }

        @Test
        @DisplayName("returns false when player not locked")
        fun returnsFalseWhenPlayerNotLocked() {
            every { lockingService.unlockInventory(testUuid) } returns false

            val result = api.unlock(testUuid)

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("isLocked")
    inner class IsLocked {

        @Test
        @DisplayName("returns true when player is locked")
        fun returnsTrueWhenPlayerIsLocked() {
            every { lockingService.isLocked(testUuid) } returns true

            val result = api.isLocked(testUuid)

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when player is not locked")
        fun returnsFalseWhenPlayerIsNotLocked() {
            every { lockingService.isLocked(testUuid) } returns false

            val result = api.isLocked(testUuid)

            assertFalse(result)
        }

        @Test
        @DisplayName("checks lock for specific group")
        fun checksLockForSpecificGroup() {
            every { lockingService.isLocked(testUuid, "survival") } returns true

            val result = api.isLocked(testUuid, "survival")

            assertTrue(result)
            verify { lockingService.isLocked(testUuid, "survival") }
        }
    }

    @Nested
    @DisplayName("getLock")
    inner class GetLock {

        @Test
        @DisplayName("returns lock when player is locked")
        fun returnsLockWhenPlayerIsLocked() {
            val lock = createTestLock()
            every { lockingService.getLock(testUuid) } returns lock

            val result = api.getLock(testUuid)

            Assertions.assertNotNull(result)
            assertEquals(testUuid, result?.playerUuid)
        }

        @Test
        @DisplayName("returns null when player is not locked")
        fun returnsNullWhenPlayerIsNotLocked() {
            every { lockingService.getLock(testUuid) } returns null

            val result = api.getLock(testUuid)

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("getLockedPlayers")
    inner class GetLockedPlayers {

        @Test
        @DisplayName("returns all locked players")
        fun returnsAllLockedPlayers() {
            val locks = listOf(
                createTestLock(playerUuid = UUID.randomUUID()),
                createTestLock(playerUuid = UUID.randomUUID()),
                createTestLock(playerUuid = UUID.randomUUID())
            )
            every { lockingService.getLockedPlayers() } returns locks

            val result = api.getLockedPlayers()

            assertEquals(3, result.size)
        }

        @Test
        @DisplayName("returns empty list when no players locked")
        fun returnsEmptyListWhenNoPlayersLocked() {
            every { lockingService.getLockedPlayers() } returns emptyList()

            val result = api.getLockedPlayers()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("isEnabled")
    inner class IsEnabled {

        @Test
        @DisplayName("returns true when locking is enabled")
        fun returnsTrueWhenLockingIsEnabled() {
            val mainConfig = mockk<MainConfig>()
            val lockingConfig = LockingConfig(enabled = true)
            every { configManager.mainConfig } returns mainConfig
            every { mainConfig.locking } returns lockingConfig

            val result = api.isEnabled()

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when locking is disabled")
        fun returnsFalseWhenLockingIsDisabled() {
            val mainConfig = mockk<MainConfig>()
            val lockingConfig = LockingConfig(enabled = false)
            every { configManager.mainConfig } returns mainConfig
            every { mainConfig.locking } returns lockingConfig

            val result = api.isEnabled()

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("parseDuration")
    inner class ParseDuration {

        @Test
        @DisplayName("parses seconds")
        fun parsesSeconds() {
            val result = api.parseDuration("30s")

            Assertions.assertNotNull(result)
            assertEquals(30, result?.seconds)
        }

        @Test
        @DisplayName("parses minutes")
        fun parsesMinutes() {
            val result = api.parseDuration("5m")

            Assertions.assertNotNull(result)
            assertEquals(5 * 60, result?.seconds)
        }

        @Test
        @DisplayName("parses hours")
        fun parsesHours() {
            val result = api.parseDuration("2h")

            Assertions.assertNotNull(result)
            assertEquals(2 * 60 * 60, result?.seconds)
        }

        @Test
        @DisplayName("parses days")
        fun parsesDays() {
            val result = api.parseDuration("1d")

            Assertions.assertNotNull(result)
            assertEquals(24 * 60 * 60, result?.seconds)
        }

        @Test
        @DisplayName("parses weeks")
        fun parsesWeeks() {
            val result = api.parseDuration("1w")

            Assertions.assertNotNull(result)
            assertEquals(7 * 24 * 60 * 60, result?.seconds)
        }

        @Test
        @DisplayName("returns null for invalid format")
        fun returnsNullForInvalidFormat() {
            val result = api.parseDuration("invalid")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("returns null for invalid unit")
        fun returnsNullForInvalidUnit() {
            val result = api.parseDuration("30x")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("returns null for empty string")
        fun returnsNullForEmptyString() {
            val result = api.parseDuration("")

            Assertions.assertNull(result)
        }
    }

    private fun createTestLock(
        playerUuid: UUID = testUuid,
        lockedBy: UUID? = adminUuid,
        expiresAt: Instant? = Instant.now().plusSeconds(1800)
    ): InventoryLock {
        return InventoryLock(
            playerUuid = playerUuid,
            lockedBy = lockedBy,
            lockedAt = Instant.now(),
            expiresAt = expiresAt,
            reason = "Test lock",
            scope = LockScope.ALL
        )
    }
}

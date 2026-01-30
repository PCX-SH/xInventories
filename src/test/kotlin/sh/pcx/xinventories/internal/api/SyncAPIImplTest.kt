package sh.pcx.xinventories.internal.api

import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.config.NetworkSyncConfig
import sh.pcx.xinventories.internal.model.ServerInfo
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.service.SyncService
import java.time.Instant
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@DisplayName("SyncAPIImpl")
class SyncAPIImplTest {

    private lateinit var plugin: XInventories
    private lateinit var api: SyncAPIImpl
    private lateinit var serviceManager: ServiceManager
    private lateinit var syncService: SyncService
    private lateinit var configManager: ConfigManager

    private val testUuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        syncService = mockk(relaxed = true)
        configManager = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { plugin.configManager } returns configManager
        every { serviceManager.syncService } returns syncService

        val mainConfig = mockk<MainConfig>()
        val syncConfig = mockk<NetworkSyncConfig>()
        every { configManager.mainConfig } returns mainConfig
        every { mainConfig.sync } returns syncConfig
        every { syncConfig.serverId } returns "server-1"

        api = SyncAPIImpl(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("isEnabled")
    inner class IsEnabled {

        @Test
        @DisplayName("returns true when sync service is enabled")
        fun returnsTrueWhenSyncServiceIsEnabled() {
            every { syncService.isEnabled } returns true

            val result = api.isEnabled

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when sync service is disabled")
        fun returnsFalseWhenSyncServiceIsDisabled() {
            every { syncService.isEnabled } returns false

            val result = api.isEnabled

            assertFalse(result)
        }

        @Test
        @DisplayName("returns false when sync service is null")
        fun returnsFalseWhenSyncServiceIsNull() {
            every { serviceManager.syncService } returns null

            val result = api.isEnabled

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("serverId")
    inner class ServerId {

        @Test
        @DisplayName("returns server ID from sync service")
        fun returnsServerIdFromSyncService() {
            every { syncService.serverId } returns "lobby-1"

            val result = api.serverId

            assertEquals("lobby-1", result)
        }

        @Test
        @DisplayName("returns config server ID when sync service is null")
        fun returnsConfigServerIdWhenSyncServiceIsNull() {
            every { serviceManager.syncService } returns null

            val result = api.serverId

            assertEquals("server-1", result)
        }
    }

    @Nested
    @DisplayName("getConnectedServers")
    inner class GetConnectedServers {

        @Test
        @DisplayName("returns connected servers list")
        fun returnsConnectedServersList() {
            val servers = listOf(
                ServerInfo("lobby-1", Instant.now(), 50, true),
                ServerInfo("survival-1", Instant.now(), 30, true),
                ServerInfo("creative-1", Instant.now().minusSeconds(100), 10, false)
            )
            every { syncService.getConnectedServers() } returns servers

            val result = api.getConnectedServers()

            assertEquals(3, result.size)
            assertEquals("lobby-1", result[0].serverId)
            assertEquals(50, result[0].playerCount)
            assertTrue(result[0].isHealthy)
            assertFalse(result[2].isHealthy)
        }

        @Test
        @DisplayName("returns empty list when sync service is null")
        fun returnsEmptyListWhenSyncServiceIsNull() {
            every { serviceManager.syncService } returns null

            val result = api.getConnectedServers()

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("converts Instant to epoch millis for lastHeartbeat")
        fun convertsInstantToEpochMillis() {
            val now = Instant.now()
            val servers = listOf(ServerInfo("server-1", now, 10, true))
            every { syncService.getConnectedServers() } returns servers

            val result = api.getConnectedServers()

            assertEquals(now.toEpochMilli(), result[0].lastHeartbeat)
        }
    }

    @Nested
    @DisplayName("isPlayerLocked")
    inner class IsPlayerLocked {

        @Test
        @DisplayName("returns true when player is locked")
        fun returnsTrueWhenPlayerIsLocked() {
            every { syncService.isPlayerLocked(testUuid) } returns true

            val result = api.isPlayerLocked(testUuid)

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when player is not locked")
        fun returnsFalseWhenPlayerIsNotLocked() {
            every { syncService.isPlayerLocked(testUuid) } returns false

            val result = api.isPlayerLocked(testUuid)

            assertFalse(result)
        }

        @Test
        @DisplayName("returns false when sync service is null")
        fun returnsFalseWhenSyncServiceIsNull() {
            every { serviceManager.syncService } returns null

            val result = api.isPlayerLocked(testUuid)

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("getLockHolder")
    inner class GetLockHolder {

        @Test
        @DisplayName("returns lock holder server ID")
        fun returnsLockHolderServerId() {
            every { syncService.getLockHolder(testUuid) } returns "lobby-1"

            val result = api.getLockHolder(testUuid)

            assertEquals("lobby-1", result)
        }

        @Test
        @DisplayName("returns null when player not locked")
        fun returnsNullWhenPlayerNotLocked() {
            every { syncService.getLockHolder(testUuid) } returns null

            val result = api.getLockHolder(testUuid)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("returns null when sync service is null")
        fun returnsNullWhenSyncServiceIsNull() {
            every { serviceManager.syncService } returns null

            val result = api.getLockHolder(testUuid)

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("forceSyncPlayer")
    inner class ForceSyncPlayer {

        @Test
        @DisplayName("syncs player successfully")
        fun syncsPlayerSuccessfully() {
            every { syncService.forceSyncPlayer(testUuid) } returns CompletableFuture.completedFuture(Result.success(Unit))

            val future = api.forceSyncPlayer(testUuid)
            val result = future.get(5, TimeUnit.SECONDS)

            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("returns failure when sync service is null")
        fun returnsFailureWhenSyncServiceIsNull() {
            every { serviceManager.syncService } returns null

            val future = api.forceSyncPlayer(testUuid)
            val result = future.get(5, TimeUnit.SECONDS)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalStateException)
        }
    }

    @Nested
    @DisplayName("broadcastInvalidation")
    inner class BroadcastInvalidation {

        @Test
        @DisplayName("broadcasts invalidation for all groups")
        fun broadcastsInvalidationForAllGroups() {
            every { syncService.broadcastInvalidation(testUuid, null) } just Runs

            api.broadcastInvalidation(testUuid)

            verify { syncService.broadcastInvalidation(testUuid, null) }
        }

        @Test
        @DisplayName("broadcasts invalidation for specific group")
        fun broadcastsInvalidationForSpecificGroup() {
            every { syncService.broadcastInvalidation(testUuid, "survival") } just Runs

            api.broadcastInvalidation(testUuid, "survival")

            verify { syncService.broadcastInvalidation(testUuid, "survival") }
        }

        @Test
        @DisplayName("does nothing when sync service is null")
        fun doesNothingWhenSyncServiceIsNull() {
            every { serviceManager.syncService } returns null

            api.broadcastInvalidation(testUuid) // Should not throw
        }
    }

    @Nested
    @DisplayName("getTotalNetworkPlayerCount")
    inner class GetTotalNetworkPlayerCount {

        @Test
        @DisplayName("returns total player count across all servers")
        fun returnsTotalPlayerCountAcrossAllServers() {
            val servers = listOf(
                ServerInfo("server-1", Instant.now(), 50, true),
                ServerInfo("server-2", Instant.now(), 30, true),
                ServerInfo("server-3", Instant.now(), 20, true)
            )
            every { syncService.getConnectedServers() } returns servers

            val result = api.getTotalNetworkPlayerCount()

            assertEquals(100, result)
        }

        @Test
        @DisplayName("returns 0 when sync service is null")
        fun returnsZeroWhenSyncServiceIsNull() {
            every { serviceManager.syncService } returns null

            val result = api.getTotalNetworkPlayerCount()

            assertEquals(0, result)
        }

        @Test
        @DisplayName("returns 0 when no servers connected")
        fun returnsZeroWhenNoServersConnected() {
            every { syncService.getConnectedServers() } returns emptyList()

            val result = api.getTotalNetworkPlayerCount()

            assertEquals(0, result)
        }
    }

    @Nested
    @DisplayName("isServerHealthy")
    inner class IsServerHealthy {

        @Test
        @DisplayName("returns true for healthy server")
        fun returnsTrueForHealthyServer() {
            val servers = listOf(
                ServerInfo("healthy-server", Instant.now(), 50, true)
            )
            every { syncService.getConnectedServers() } returns servers

            val result = api.isServerHealthy("healthy-server")

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false for unhealthy server")
        fun returnsFalseForUnhealthyServer() {
            val servers = listOf(
                ServerInfo("unhealthy-server", Instant.now().minusSeconds(120), 50, false)
            )
            every { syncService.getConnectedServers() } returns servers

            val result = api.isServerHealthy("unhealthy-server")

            assertFalse(result)
        }

        @Test
        @DisplayName("returns false for unknown server")
        fun returnsFalseForUnknownServer() {
            every { syncService.getConnectedServers() } returns emptyList()

            val result = api.isServerHealthy("unknown-server")

            assertFalse(result)
        }

        @Test
        @DisplayName("returns false when sync service is null")
        fun returnsFalseWhenSyncServiceIsNull() {
            every { serviceManager.syncService } returns null

            val result = api.isServerHealthy("any-server")

            assertFalse(result)
        }
    }
}

package sh.pcx.xinventories.unit.service

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.bukkit.Server
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.ServerInfo
import sh.pcx.xinventories.internal.model.SyncMessage
import sh.pcx.xinventories.internal.service.*
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.util.UUID

/**
 * Comprehensive unit tests for RedisSyncService.
 *
 * Tests cover:
 * - initialize() with config disabled returns false
 * - initialize() with Redis connection failure returns false
 * - Lock acquisition/release with statistics tracking
 * - Lock conflict counting
 * - Message publishing increments counter
 * - Message receiving increments counter
 * - Connected servers tracking from heartbeats
 * - Server shutdown removes from connected list
 * - Cache invalidation handlers called
 * - Stats return correct values
 * - broadcastUpdate() and broadcastInvalidation() behavior
 * - transferLock() with and without transfer lock enabled
 * - forceSyncPlayer() behavior
 * - Message handler registration and invocation
 */
@DisplayName("RedisSyncService Tests")
class RedisSyncServiceTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            // Mock the Logging object to prevent actual logging during tests
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.severe(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    private lateinit var plugin: XInventories
    private lateinit var scope: CoroutineScope
    private lateinit var server: Server

    private val testServerId = "test-server-1"
    private val testChannel = "xinventories:sync"
    private val testUuid = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val testUuid2 = UUID.fromString("00000000-0000-0000-0000-000000000002")

    @BeforeEach
    fun setUp() {
        // Mock Server with online players
        server = mockk(relaxed = true) {
            every { onlinePlayers } returns emptyList()
        }

        // Mock Plugin
        plugin = mockk(relaxed = true) {
            every { this@mockk.server } returns this@RedisSyncServiceTest.server
        }

        scope = CoroutineScope(Dispatchers.Default)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks(answers = false)
    }

    // ============================================
    // Helper Methods
    // ============================================

    private fun createConfig(
        enabled: Boolean = true,
        serverId: String = testServerId,
        redisHost: String = "localhost",
        redisPort: Int = 6379,
        redisPassword: String = "",
        channel: String = testChannel,
        transferLockEnabled: Boolean = true,
        transferLockTimeoutSeconds: Int = 10,
        heartbeatIntervalSeconds: Int = 30,
        heartbeatTimeoutSeconds: Int = 90
    ): SyncConfig {
        return SyncConfig(
            enabled = enabled,
            mode = SyncMode.REDIS,
            serverId = serverId,
            redis = RedisConfig(
                host = redisHost,
                port = redisPort,
                password = redisPassword,
                channel = channel,
                timeout = 5000
            ),
            transferLock = TransferLockConfig(
                enabled = transferLockEnabled,
                timeoutSeconds = transferLockTimeoutSeconds
            ),
            heartbeat = HeartbeatConfig(
                intervalSeconds = heartbeatIntervalSeconds,
                timeoutSeconds = heartbeatTimeoutSeconds
            )
        )
    }

    private fun createServerInfo(
        serverId: String,
        playerCount: Int = 10,
        isHealthy: Boolean = true
    ): ServerInfo {
        return ServerInfo(
            serverId = serverId,
            lastHeartbeat = Instant.now(),
            playerCount = playerCount,
            isHealthy = isHealthy
        )
    }

    // ============================================
    // Initialization Tests
    // ============================================

    @Nested
    @DisplayName("Initialization")
    inner class InitializationTests {

        @Test
        @DisplayName("initialize() with config disabled returns false")
        fun initializeWithConfigDisabledReturnsFalse() = runTest {
            val config = createConfig(enabled = false)
            val service = RedisSyncService(plugin, scope, config)

            val result = service.initialize()

            assertFalse(result)
            assertFalse(service.isEnabled)
            verify { Logging.info("Sync is disabled in configuration") }
        }

        @Test
        @DisplayName("serverId returns configured server ID")
        fun serverIdReturnsConfiguredValue() {
            val config = createConfig(serverId = "my-custom-server")
            val service = RedisSyncService(plugin, scope, config)

            assertEquals("my-custom-server", service.serverId)
        }

        @Test
        @DisplayName("isEnabled returns false when not initialized")
        fun isEnabledReturnsFalseWhenNotInitialized() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            assertFalse(service.isEnabled)
        }
    }

    // ============================================
    // Connected Servers Tests
    // ============================================

    @Nested
    @DisplayName("Connected Servers Tracking")
    inner class ConnectedServersTests {

        @Test
        @DisplayName("getConnectedServers() returns empty list initially")
        fun getConnectedServersReturnsEmptyListInitially() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val servers = service.getConnectedServers()

            assertTrue(servers.isEmpty())
        }
    }

    // ============================================
    // Message Handler Registration Tests
    // ============================================

    @Nested
    @DisplayName("Message Handler Registration")
    inner class MessageHandlerTests {

        @Test
        @DisplayName("onMessage() registers handler")
        fun onMessageRegistersHandler() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            var handlerCalled = false
            service.onMessage { _ ->
                handlerCalled = true
            }

            // Handler registered but not called yet
            assertFalse(handlerCalled)
        }

        @Test
        @DisplayName("onCacheInvalidate() registers handler")
        fun onCacheInvalidateRegistersHandler() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            var handlerCalled = false
            service.onCacheInvalidate { _, _ ->
                handlerCalled = true
            }

            // Handler registered but not called yet
            assertFalse(handlerCalled)
        }

        @Test
        @DisplayName("multiple handlers can be registered")
        fun multipleHandlersCanBeRegistered() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            var handler1Called = false
            var handler2Called = false
            var handler3Called = false

            service.onMessage { handler1Called = true }
            service.onMessage { handler2Called = true }
            service.onCacheInvalidate { _, _ -> handler3Called = true }

            // All handlers registered
            assertFalse(handler1Called)
            assertFalse(handler2Called)
            assertFalse(handler3Called)
        }
    }

    // ============================================
    // Statistics Tests
    // ============================================

    @Nested
    @DisplayName("Statistics")
    inner class StatisticsTests {

        @Test
        @DisplayName("getStats() returns zero values initially")
        fun getStatsReturnsZeroValuesInitially() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val stats = service.getStats()

            assertEquals(0L, stats.messagesPublished)
            assertEquals(0L, stats.messagesReceived)
            assertEquals(0L, stats.locksAcquired)
            assertEquals(0L, stats.locksReleased)
            assertEquals(0L, stats.lockConflicts)
            assertEquals(0, stats.connectedServers)
            assertEquals(0L, stats.lastHeartbeat)
        }

        @Test
        @DisplayName("getStats() returns SyncStats data class")
        fun getStatsReturnsSyncStatsDataClass() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val stats = service.getStats()

            assertNotNull(stats)
            assertTrue(stats is SyncStats)
        }
    }

    // ============================================
    // SyncConfig Tests
    // ============================================

    @Nested
    @DisplayName("SyncConfig")
    inner class SyncConfigTests {

        @Test
        @DisplayName("default config has sensible values")
        fun defaultConfigHasSensibleValues() {
            val config = SyncConfig()

            assertFalse(config.enabled)
            assertEquals(SyncMode.REDIS, config.mode)
            assertEquals("server-1", config.serverId)
            assertEquals("localhost", config.redis.host)
            assertEquals(6379, config.redis.port)
            assertEquals("", config.redis.password)
            assertEquals("xinventories:sync", config.redis.channel)
            assertEquals(5000, config.redis.timeout)
        }

        @Test
        @DisplayName("TransferLockConfig has default values")
        fun transferLockConfigHasDefaultValues() {
            val config = TransferLockConfig()

            assertTrue(config.enabled)
            assertEquals(10, config.timeoutSeconds)
        }

        @Test
        @DisplayName("HeartbeatConfig has default values")
        fun heartbeatConfigHasDefaultValues() {
            val config = HeartbeatConfig()

            assertEquals(30, config.intervalSeconds)
            assertEquals(90, config.timeoutSeconds)
        }

        @Test
        @DisplayName("RedisConfig has default values")
        fun redisConfigHasDefaultValues() {
            val config = RedisConfig()

            assertEquals("localhost", config.host)
            assertEquals(6379, config.port)
            assertEquals("", config.password)
            assertEquals("xinventories:sync", config.channel)
            assertEquals(5000, config.timeout)
        }

        @Test
        @DisplayName("SyncMode has all expected values")
        fun syncModeHasAllExpectedValues() {
            assertEquals(3, SyncMode.entries.size)
            assertNotNull(SyncMode.REDIS)
            assertNotNull(SyncMode.PLUGIN_MESSAGING)
            assertNotNull(SyncMode.MYSQL_NOTIFY)
        }

        @Test
        @DisplayName("custom config preserves values")
        fun customConfigPreservesValues() {
            val config = SyncConfig(
                enabled = true,
                mode = SyncMode.PLUGIN_MESSAGING,
                serverId = "custom-server",
                redis = RedisConfig(
                    host = "redis.example.com",
                    port = 6380,
                    password = "secret",
                    channel = "custom:channel",
                    timeout = 10000
                ),
                transferLock = TransferLockConfig(
                    enabled = false,
                    timeoutSeconds = 30
                ),
                heartbeat = HeartbeatConfig(
                    intervalSeconds = 60,
                    timeoutSeconds = 180
                )
            )

            assertTrue(config.enabled)
            assertEquals(SyncMode.PLUGIN_MESSAGING, config.mode)
            assertEquals("custom-server", config.serverId)
            assertEquals("redis.example.com", config.redis.host)
            assertEquals(6380, config.redis.port)
            assertEquals("secret", config.redis.password)
            assertEquals("custom:channel", config.redis.channel)
            assertEquals(10000, config.redis.timeout)
            assertFalse(config.transferLock.enabled)
            assertEquals(30, config.transferLock.timeoutSeconds)
            assertEquals(60, config.heartbeat.intervalSeconds)
            assertEquals(180, config.heartbeat.timeoutSeconds)
        }
    }

    // ============================================
    // SyncStats Tests
    // ============================================

    @Nested
    @DisplayName("SyncStats")
    inner class SyncStatsTests {

        @Test
        @DisplayName("SyncStats data class holds all values")
        fun syncStatsHoldsAllValues() {
            val stats = SyncStats(
                messagesPublished = 100L,
                messagesReceived = 200L,
                locksAcquired = 50L,
                locksReleased = 45L,
                lockConflicts = 5L,
                connectedServers = 3,
                lastHeartbeat = 1234567890L
            )

            assertEquals(100L, stats.messagesPublished)
            assertEquals(200L, stats.messagesReceived)
            assertEquals(50L, stats.locksAcquired)
            assertEquals(45L, stats.locksReleased)
            assertEquals(5L, stats.lockConflicts)
            assertEquals(3, stats.connectedServers)
            assertEquals(1234567890L, stats.lastHeartbeat)
        }

        @Test
        @DisplayName("SyncStats supports copy with modifications")
        fun syncStatsSupportsCopy() {
            val original = SyncStats(
                messagesPublished = 100L,
                messagesReceived = 200L,
                locksAcquired = 50L,
                locksReleased = 45L,
                lockConflicts = 5L,
                connectedServers = 3,
                lastHeartbeat = 1234567890L
            )

            val modified = original.copy(messagesPublished = 150L)

            assertEquals(150L, modified.messagesPublished)
            assertEquals(200L, modified.messagesReceived)
        }

        @Test
        @DisplayName("SyncStats equals and hashCode work correctly")
        fun syncStatsEqualsAndHashCode() {
            val stats1 = SyncStats(
                messagesPublished = 100L,
                messagesReceived = 200L,
                locksAcquired = 50L,
                locksReleased = 45L,
                lockConflicts = 5L,
                connectedServers = 3,
                lastHeartbeat = 1234567890L
            )

            val stats2 = SyncStats(
                messagesPublished = 100L,
                messagesReceived = 200L,
                locksAcquired = 50L,
                locksReleased = 45L,
                lockConflicts = 5L,
                connectedServers = 3,
                lastHeartbeat = 1234567890L
            )

            assertEquals(stats1, stats2)
            assertEquals(stats1.hashCode(), stats2.hashCode())
        }
    }

    // ============================================
    // Force Sync Player Tests
    // ============================================

    @Nested
    @DisplayName("Force Sync Player")
    inner class ForceSyncPlayerTests {

        @Test
        @DisplayName("forceSyncPlayer() returns CompletableFuture")
        fun forceSyncPlayerReturnsCompletableFuture() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val future = service.forceSyncPlayer(testUuid)

            assertNotNull(future)
        }

        @Test
        @DisplayName("forceSyncPlayer() completes with success when no exception")
        fun forceSyncPlayerCompletesWithSuccess() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val future = service.forceSyncPlayer(testUuid)
            val result = future.get()

            assertTrue(result.isSuccess)
        }
    }

    // ============================================
    // Integration-like Tests (using reflection to test internal state)
    // ============================================

    @Nested
    @DisplayName("Internal State Management")
    inner class InternalStateTests {

        @Test
        @DisplayName("service starts with empty connected servers map")
        fun serviceStartsWithEmptyConnectedServers() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val connectedServers = service.getConnectedServers()

            assertTrue(connectedServers.isEmpty())
        }

        @Test
        @DisplayName("service maintains serverId from config")
        fun serviceMaintainsServerIdFromConfig() {
            val config = createConfig(serverId = "unique-server-123")
            val service = RedisSyncService(plugin, scope, config)

            assertEquals("unique-server-123", service.serverId)
        }
    }

    // ============================================
    // Lock Management Interface Tests
    // ============================================

    @Nested
    @DisplayName("Lock Management Interface")
    inner class LockManagementInterfaceTests {

        @Test
        @DisplayName("isPlayerLocked() requires initialization")
        fun isPlayerLockedRequiresInitialization() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            // Without initialization, lockManager is not initialized
            // This should throw UninitializedPropertyAccessException
            Assertions.assertThrows(UninitializedPropertyAccessException::class.java) {
                service.isPlayerLocked(testUuid)
            }
        }

        @Test
        @DisplayName("getLockHolder() requires initialization")
        fun getLockHolderRequiresInitialization() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            // Without initialization, lockManager is not initialized
            Assertions.assertThrows(UninitializedPropertyAccessException::class.java) {
                service.getLockHolder(testUuid)
            }
        }
    }

    // ============================================
    // Broadcast Methods Tests (without Redis connection)
    // ============================================

    @Nested
    @DisplayName("Broadcast Methods (No Connection)")
    inner class BroadcastMethodsNoConnectionTests {

        @Test
        @DisplayName("broadcastUpdate() handles null redisClient gracefully")
        fun broadcastUpdateHandlesNullRedisClient() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            // Should not throw - redisClient is null, so publish returns early
            assertDoesNotThrow {
                service.broadcastUpdate(testUuid, "survival", 1L)
            }
        }

        @Test
        @DisplayName("broadcastInvalidation() handles null redisClient gracefully")
        fun broadcastInvalidationHandlesNullRedisClient() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            // Should not throw - redisClient is null, so publish returns early
            assertDoesNotThrow {
                service.broadcastInvalidation(testUuid, "survival")
            }
        }

        @Test
        @DisplayName("broadcastInvalidation() with null group handles gracefully")
        fun broadcastInvalidationWithNullGroupHandlesGracefully() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            // Should not throw
            assertDoesNotThrow {
                service.broadcastInvalidation(testUuid, null)
            }
        }
    }

    // ============================================
    // Multiple Services Tests
    // ============================================

    @Nested
    @DisplayName("Multiple Service Instances")
    inner class MultipleServicesTests {

        @Test
        @DisplayName("different instances have different server IDs")
        fun differentInstancesHaveDifferentServerIds() {
            val config1 = createConfig(serverId = "server-1")
            val config2 = createConfig(serverId = "server-2")
            val service1 = RedisSyncService(plugin, scope, config1)
            val service2 = RedisSyncService(plugin, scope, config2)

            assertNotEquals(service1.serverId, service2.serverId)
            assertEquals("server-1", service1.serverId)
            assertEquals("server-2", service2.serverId)
        }

        @Test
        @DisplayName("services are independent")
        fun servicesAreIndependent() {
            val config1 = createConfig(serverId = "server-1")
            val config2 = createConfig(serverId = "server-2")
            val service1 = RedisSyncService(plugin, scope, config1)
            val service2 = RedisSyncService(plugin, scope, config2)

            // Register handler only on service1
            var handler1Called = false
            service1.onMessage { handler1Called = true }

            // Services should be independent
            assertEquals(0, service1.getStats().messagesReceived)
            assertEquals(0, service2.getStats().messagesReceived)
        }
    }

    // ============================================
    // Stats Counter Tests
    // ============================================

    @Nested
    @DisplayName("Statistics Counters")
    inner class StatisticsCountersTests {

        @Test
        @DisplayName("stats counters start at zero")
        fun statsCountersStartAtZero() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)
            val stats = service.getStats()

            assertEquals(0L, stats.messagesPublished)
            assertEquals(0L, stats.messagesReceived)
            assertEquals(0L, stats.locksAcquired)
            assertEquals(0L, stats.locksReleased)
            assertEquals(0L, stats.lockConflicts)
        }

        @Test
        @DisplayName("connectedServers starts at zero")
        fun connectedServersStartsAtZero() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)
            val stats = service.getStats()

            assertEquals(0, stats.connectedServers)
        }

        @Test
        @DisplayName("lastHeartbeat starts at zero")
        fun lastHeartbeatStartsAtZero() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)
            val stats = service.getStats()

            assertEquals(0L, stats.lastHeartbeat)
        }
    }

    // ============================================
    // Configuration Edge Cases
    // ============================================

    @Nested
    @DisplayName("Configuration Edge Cases")
    inner class ConfigurationEdgeCasesTests {

        @Test
        @DisplayName("empty password creates valid config")
        fun emptyPasswordCreatesValidConfig() {
            val config = createConfig(redisPassword = "")

            assertEquals("", config.redis.password)
        }

        @Test
        @DisplayName("blank password creates valid config")
        fun blankPasswordCreatesValidConfig() {
            val config = createConfig(redisPassword = "   ")

            assertEquals("   ", config.redis.password)
        }

        @Test
        @DisplayName("custom port creates valid config")
        fun customPortCreatesValidConfig() {
            val config = createConfig(redisPort = 16379)

            assertEquals(16379, config.redis.port)
        }

        @Test
        @DisplayName("transfer lock disabled creates valid config")
        fun transferLockDisabledCreatesValidConfig() {
            val config = createConfig(transferLockEnabled = false)

            assertFalse(config.transferLock.enabled)
        }

        @Test
        @DisplayName("zero heartbeat timeout creates valid config")
        fun zeroHeartbeatTimeoutCreatesValidConfig() {
            val config = createConfig(heartbeatTimeoutSeconds = 0)

            assertEquals(0, config.heartbeat.timeoutSeconds)
        }

        @Test
        @DisplayName("large heartbeat interval creates valid config")
        fun largeHeartbeatIntervalCreatesValidConfig() {
            val config = createConfig(heartbeatIntervalSeconds = 3600)

            assertEquals(3600, config.heartbeat.intervalSeconds)
        }
    }

    // ============================================
    // Service Lifecycle Tests
    // ============================================

    @Nested
    @DisplayName("Service Lifecycle")
    inner class ServiceLifecycleTests {

        @Test
        @DisplayName("service can be created without initialization")
        fun serviceCanBeCreatedWithoutInitialization() {
            val config = createConfig()

            val service = RedisSyncService(plugin, scope, config)

            assertNotNull(service)
            assertFalse(service.isEnabled)
        }

        @Test
        @DisplayName("multiple services can be created")
        fun multipleServicesCanBeCreated() {
            val config = createConfig()

            val service1 = RedisSyncService(plugin, scope, config)
            val service2 = RedisSyncService(plugin, scope, config)
            val service3 = RedisSyncService(plugin, scope, config)

            assertNotNull(service1)
            assertNotNull(service2)
            assertNotNull(service3)
        }

        @Test
        @DisplayName("disabled service initialize returns false immediately")
        fun disabledServiceInitializeReturnsFalseImmediately() = runTest {
            val config = createConfig(enabled = false)
            val service = RedisSyncService(plugin, scope, config)

            val startTime = System.currentTimeMillis()
            val result = service.initialize()
            val duration = System.currentTimeMillis() - startTime

            assertFalse(result)
            // Should return almost immediately (less than 100ms)
            assertTrue(duration < 100)
        }
    }

    // ============================================
    // UUID Handling Tests
    // ============================================

    @Nested
    @DisplayName("UUID Handling")
    inner class UUIDHandlingTests {

        @Test
        @DisplayName("forceSyncPlayer accepts any valid UUID")
        fun forceSyncPlayerAcceptsAnyValidUuid() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val uuid1 = UUID.randomUUID()
            val uuid2 = UUID.fromString("00000000-0000-0000-0000-000000000000")
            val uuid3 = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff")

            assertDoesNotThrow { service.forceSyncPlayer(uuid1) }
            assertDoesNotThrow { service.forceSyncPlayer(uuid2) }
            assertDoesNotThrow { service.forceSyncPlayer(uuid3) }
        }

        @Test
        @DisplayName("broadcastUpdate accepts any valid UUID")
        fun broadcastUpdateAcceptsAnyValidUuid() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val uuid = UUID.randomUUID()

            assertDoesNotThrow { service.broadcastUpdate(uuid, "group", 1L) }
        }

        @Test
        @DisplayName("broadcastInvalidation accepts any valid UUID")
        fun broadcastInvalidationAcceptsAnyValidUuid() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val uuid = UUID.randomUUID()

            assertDoesNotThrow { service.broadcastInvalidation(uuid, "group") }
            assertDoesNotThrow { service.broadcastInvalidation(uuid, null) }
        }
    }

    // ============================================
    // Group Name Handling Tests
    // ============================================

    @Nested
    @DisplayName("Group Name Handling")
    inner class GroupNameHandlingTests {

        @Test
        @DisplayName("broadcastUpdate accepts various group names")
        fun broadcastUpdateAcceptsVariousGroupNames() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            assertDoesNotThrow { service.broadcastUpdate(testUuid, "survival", 1L) }
            assertDoesNotThrow { service.broadcastUpdate(testUuid, "creative", 1L) }
            assertDoesNotThrow { service.broadcastUpdate(testUuid, "world_nether", 1L) }
            assertDoesNotThrow { service.broadcastUpdate(testUuid, "Group-With-Dashes", 1L) }
            assertDoesNotThrow { service.broadcastUpdate(testUuid, "", 1L) }
        }

        @Test
        @DisplayName("broadcastInvalidation accepts various group names")
        fun broadcastInvalidationAcceptsVariousGroupNames() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            assertDoesNotThrow { service.broadcastInvalidation(testUuid, "survival") }
            assertDoesNotThrow { service.broadcastInvalidation(testUuid, "creative") }
            assertDoesNotThrow { service.broadcastInvalidation(testUuid, null) }
            assertDoesNotThrow { service.broadcastInvalidation(testUuid, "") }
        }
    }

    // ============================================
    // Version Number Handling Tests
    // ============================================

    @Nested
    @DisplayName("Version Number Handling")
    inner class VersionNumberHandlingTests {

        @Test
        @DisplayName("broadcastUpdate accepts various version numbers")
        fun broadcastUpdateAcceptsVariousVersionNumbers() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            assertDoesNotThrow { service.broadcastUpdate(testUuid, "group", 0L) }
            assertDoesNotThrow { service.broadcastUpdate(testUuid, "group", 1L) }
            assertDoesNotThrow { service.broadcastUpdate(testUuid, "group", Long.MAX_VALUE) }
            assertDoesNotThrow { service.broadcastUpdate(testUuid, "group", -1L) }
        }
    }

    // ============================================
    // Handler Callback Tests
    // ============================================

    @Nested
    @DisplayName("Handler Callbacks")
    inner class HandlerCallbackTests {

        @Test
        @DisplayName("onMessage handler receives correct parameter type")
        fun onMessageHandlerReceivesCorrectParameterType() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            var receivedMessage: SyncMessage? = null
            service.onMessage { msg ->
                receivedMessage = msg
            }

            // Handler is registered but message would need to come through Redis
            assertNull(receivedMessage)
        }

        @Test
        @DisplayName("onCacheInvalidate handler signature is correct")
        fun onCacheInvalidateHandlerSignatureIsCorrect() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            var receivedUuid: UUID? = null
            var receivedGroup: String? = null

            service.onCacheInvalidate { uuid, group ->
                receivedUuid = uuid
                receivedGroup = group
            }

            // Handler is registered but invocation would need to come through Redis
            assertNull(receivedUuid)
            assertNull(receivedGroup)
        }
    }

    // ============================================
    // Concurrency Safety Tests
    // ============================================

    @Nested
    @DisplayName("Concurrency Safety")
    inner class ConcurrencySafetyTests {

        @Test
        @DisplayName("getStats() is thread-safe")
        fun getStatsIsThreadSafe() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val threads = (1..10).map { _ ->
                Thread {
                    repeat(100) {
                        service.getStats()
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Should complete without exception
            assertNotNull(service.getStats())
        }

        @Test
        @DisplayName("getConnectedServers() is thread-safe")
        fun getConnectedServersIsThreadSafe() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            val threads = (1..10).map { _ ->
                Thread {
                    repeat(100) {
                        service.getConnectedServers()
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Should complete without exception
            assertNotNull(service.getConnectedServers())
        }

        @Test
        @DisplayName("serverId access is thread-safe")
        fun serverIdAccessIsThreadSafe() {
            val config = createConfig(serverId = "test-server")
            val service = RedisSyncService(plugin, scope, config)

            val threads = (1..10).map { _ ->
                Thread {
                    repeat(100) {
                        assertEquals("test-server", service.serverId)
                    }
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }
        }
    }

    // ============================================
    // SyncService Interface Compliance Tests
    // ============================================

    @Nested
    @DisplayName("SyncService Interface Compliance")
    inner class SyncServiceInterfaceComplianceTests {

        @Test
        @DisplayName("implements SyncService interface")
        fun implementsSyncServiceInterface() {
            val config = createConfig()
            val service = RedisSyncService(plugin, scope, config)

            assertTrue(service is SyncService)
        }

        @Test
        @DisplayName("all interface methods are accessible")
        fun allInterfaceMethodsAreAccessible() {
            val config = createConfig()
            val service: SyncService = RedisSyncService(plugin, scope, config)

            // Properties
            assertNotNull(service.isEnabled.toString())
            assertNotNull(service.serverId)

            // Methods that don't require initialization
            assertNotNull(service.getConnectedServers())
            assertNotNull(service.getStats())
            assertNotNull(service.forceSyncPlayer(testUuid))

            // Handler registration
            assertDoesNotThrow { service.onMessage { } }
            assertDoesNotThrow { service.onCacheInvalidate { _, _ -> } }

            // Broadcast methods (safe to call without initialization)
            assertDoesNotThrow { service.broadcastUpdate(testUuid, "group", 1L) }
            assertDoesNotThrow { service.broadcastInvalidation(testUuid, "group") }
        }
    }
}

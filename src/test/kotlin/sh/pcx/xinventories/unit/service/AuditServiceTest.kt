package sh.pcx.xinventories.unit.service

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.AuditConfig
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.model.AuditAction
import sh.pcx.xinventories.internal.model.AuditEntry
import sh.pcx.xinventories.internal.service.AuditService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.service.SyncConfig
import sh.pcx.xinventories.internal.storage.AuditStorage
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for AuditService.
 *
 * Tests cover:
 * - All logging methods when enabled/disabled
 * - Query methods returning results
 * - CSV export functionality
 * - isEnabled property based on config
 * - Actor types (Player vs CommandSender for console)
 * - Server ID handling
 */
@DisplayName("AuditService Unit Tests")
class AuditServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var mainConfig: MainConfig
    private lateinit var serviceManager: ServiceManager
    private lateinit var auditService: AuditService
    private lateinit var auditStorage: AuditStorage
    private lateinit var scope: CoroutineScope
    private lateinit var dataFolder: File

    private val playerUUID = UUID.randomUUID()
    private val playerName = "TestPlayer"
    private val adminUUID = UUID.randomUUID()
    private val adminName = "AdminPlayer"
    private val targetUUID = UUID.randomUUID()
    private val targetName = "TargetPlayer"

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("AuditServiceTest"), false)
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    @BeforeEach
    fun setUp() {
        dataFolder = tempDir.resolve("plugin").toFile()
        dataFolder.mkdirs()

        plugin = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        mainConfig = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        auditStorage = mockk(relaxed = true)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        every { plugin.dataFolder } returns dataFolder
        every { plugin.configManager } returns configManager
        every { plugin.serviceManager } returns serviceManager
        every { configManager.mainConfig } returns mainConfig

        // Default to enabled config
        every { mainConfig.audit } returns AuditConfig(
            enabled = true,
            retentionDays = 30,
            logViews = true,
            logSaves = true
        )

        // No sync config by default
        every { serviceManager.syncConfig } returns null

        auditService = AuditService(plugin, scope)
    }

    @AfterEach
    fun tearDown() = runTest {
        auditService.shutdown()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockPlayer(uuid: UUID = playerUUID, name: String = playerName): Player {
        return mockk<Player>(relaxed = true).apply {
            every { uniqueId } returns uuid
            every { this@apply.name } returns name
        }
    }

    private fun createMockCommandSender(): CommandSender {
        return mockk<CommandSender>(relaxed = true).apply {
            // Not a Player, so it's console
            every { this@apply.name } returns "CONSOLE"
        }
    }

    private fun createMockAdminPlayer(uuid: UUID = adminUUID, name: String = adminName): Player {
        return mockk<Player>(relaxed = true).apply {
            every { uniqueId } returns uuid
            every { this@apply.name } returns name
        }
    }

    private suspend fun initializeServiceWithStorage() {
        // Mock the storage initialization
        mockkConstructor(AuditStorage::class)
        coEvery { anyConstructed<AuditStorage>().initialize() } just Runs
        coEvery { anyConstructed<AuditStorage>().shutdown() } just Runs
        coEvery { anyConstructed<AuditStorage>().flushBuffer() } returns 0
        coEvery { anyConstructed<AuditStorage>().record(any()) } just Runs

        auditService.initialize()
    }

    // =========================================================================
    // Initialization Tests
    // =========================================================================

    @Nested
    @DisplayName("Initialization")
    inner class InitializationTests {

        @Test
        @DisplayName("Should initialize with enabled config")
        fun initializeWithEnabledConfig() = runTest {
            initializeServiceWithStorage()

            assertTrue(auditService.isEnabled)
            verify { Logging.info(match { it.contains("AuditService initialized") }) }
        }

        @Test
        @DisplayName("Should initialize with disabled config")
        fun initializeWithDisabledConfig() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)

            auditService.initialize()

            assertFalse(auditService.isEnabled)
            verify { Logging.info("Audit logging is disabled") }
        }

        @Test
        @DisplayName("Should not re-initialize if already initialized")
        fun noReInitialization() = runTest {
            initializeServiceWithStorage()
            auditService.initialize() // Second call should be no-op

            coVerify(exactly = 1) { anyConstructed<AuditStorage>().initialize() }
        }
    }

    // =========================================================================
    // isEnabled Property Tests
    // =========================================================================

    @Nested
    @DisplayName("isEnabled Property")
    inner class IsEnabledTests {

        @Test
        @DisplayName("Should return false when not initialized")
        fun returnFalseWhenNotInitialized() {
            assertFalse(auditService.isEnabled)
        }

        @Test
        @DisplayName("Should return false when config is disabled")
        fun returnFalseWhenConfigDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
        }

        @Test
        @DisplayName("Should return true when enabled and initialized")
        fun returnTrueWhenEnabledAndInitialized() = runTest {
            initializeServiceWithStorage()

            assertTrue(auditService.isEnabled)
        }
    }

    // =========================================================================
    // Config Property Tests
    // =========================================================================

    @Nested
    @DisplayName("Config Property")
    inner class ConfigPropertyTests {

        @Test
        @DisplayName("Should have default config before initialization")
        fun defaultConfigBeforeInitialization() {
            val config = auditService.config
            // Default AuditConfig values
            assertTrue(config.enabled)
            assertEquals(30, config.retentionDays)
        }

        @Test
        @DisplayName("Should update config after reload")
        fun updateConfigAfterReload() = runTest {
            initializeServiceWithStorage()

            val newConfig = AuditConfig(
                enabled = true,
                retentionDays = 60,
                logViews = false,
                logSaves = false
            )
            every { mainConfig.audit } returns newConfig

            auditService.reload()

            assertEquals(60, auditService.config.retentionDays)
            assertFalse(auditService.config.logViews)
            assertFalse(auditService.config.logSaves)
        }
    }

    // =========================================================================
    // logSave Tests
    // =========================================================================

    @Nested
    @DisplayName("logSave")
    inner class LogSaveTests {

        @Test
        @DisplayName("Should log save when enabled and logSaves is true")
        fun logSaveWhenEnabled() = runTest {
            initializeServiceWithStorage()

            val player = createMockPlayer()
            auditService.logSave(player, "survival")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.INVENTORY_SAVE &&
                    entry.target == playerUUID &&
                    entry.targetName == playerName &&
                    entry.group == "survival" &&
                    entry.actorName == "SYSTEM"
                })
            }
        }

        @Test
        @DisplayName("Should not log save when disabled")
        fun noLogSaveWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val player = createMockPlayer()
            // Should complete without error when disabled
            auditService.logSave(player, "survival")
        }

        @Test
        @DisplayName("Should not log save when logSaves is false")
        fun noLogSaveWhenLogSavesFalse() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = true, logSaves = false)
            initializeServiceWithStorage()

            val player = createMockPlayer()
            auditService.logSave(player, "survival")

            coVerify(exactly = 0) { anyConstructed<AuditStorage>().record(any()) }
        }
    }

    // =========================================================================
    // logLoad Tests
    // =========================================================================

    @Nested
    @DisplayName("logLoad")
    inner class LogLoadTests {

        @Test
        @DisplayName("Should log load when enabled and logSaves is true")
        fun logLoadWhenEnabled() = runTest {
            initializeServiceWithStorage()

            val player = createMockPlayer()
            auditService.logLoad(player, "survival")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.INVENTORY_LOAD &&
                    entry.target == playerUUID &&
                    entry.targetName == playerName &&
                    entry.group == "survival" &&
                    entry.actorName == "SYSTEM"
                })
            }
        }

        @Test
        @DisplayName("Should not log load when disabled")
        fun noLogLoadWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val player = createMockPlayer()
            // Should complete without error when disabled
            auditService.logLoad(player, "survival")
        }

        @Test
        @DisplayName("Should not log load when logSaves is false")
        fun noLogLoadWhenLogSavesFalse() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = true, logSaves = false)
            initializeServiceWithStorage()

            val player = createMockPlayer()
            auditService.logLoad(player, "creative")

            coVerify(exactly = 0) { anyConstructed<AuditStorage>().record(any()) }
        }
    }

    // =========================================================================
    // logAdminView Tests
    // =========================================================================

    @Nested
    @DisplayName("logAdminView")
    inner class LogAdminViewTests {

        @Test
        @DisplayName("Should log view with player actor")
        fun logViewWithPlayerActor() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logAdminView(admin, targetUUID, targetName, "survival")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.ADMIN_VIEW &&
                    entry.actor == adminUUID &&
                    entry.actorName == adminName &&
                    entry.target == targetUUID &&
                    entry.targetName == targetName &&
                    entry.group == "survival"
                })
            }
        }

        @Test
        @DisplayName("Should log view with console actor")
        fun logViewWithConsoleActor() = runTest {
            initializeServiceWithStorage()

            val console = createMockCommandSender()
            auditService.logAdminView(console, targetUUID, targetName, "survival")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.ADMIN_VIEW &&
                    entry.actor == null &&
                    entry.actorName == "CONSOLE" &&
                    entry.target == targetUUID &&
                    entry.targetName == targetName
                })
            }
        }

        @Test
        @DisplayName("Should not log view when disabled")
        fun noLogViewWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val admin = createMockAdminPlayer()
            // Should complete without error when disabled
            auditService.logAdminView(admin, targetUUID, targetName, "survival")
        }

        @Test
        @DisplayName("Should not log view when logViews is false")
        fun noLogViewWhenLogViewsFalse() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = true, logViews = false)
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logAdminView(admin, targetUUID, targetName, "survival")

            coVerify(exactly = 0) { anyConstructed<AuditStorage>().record(any()) }
        }

        @Test
        @DisplayName("Should handle null group")
        fun handleNullGroup() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logAdminView(admin, targetUUID, targetName, null)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.group == null
                })
            }
        }
    }

    // =========================================================================
    // logAdminEdit Tests
    // =========================================================================

    @Nested
    @DisplayName("logAdminEdit")
    inner class LogAdminEditTests {

        @Test
        @DisplayName("Should log edit with player actor")
        fun logEditWithPlayerActor() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logAdminEdit(admin, targetUUID, targetName, "survival", "Modified armor")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.ADMIN_EDIT &&
                    entry.actor == adminUUID &&
                    entry.actorName == adminName &&
                    entry.target == targetUUID &&
                    entry.targetName == targetName &&
                    entry.group == "survival" &&
                    entry.details == "Modified armor"
                })
            }
        }

        @Test
        @DisplayName("Should log edit with console actor")
        fun logEditWithConsoleActor() = runTest {
            initializeServiceWithStorage()

            val console = createMockCommandSender()
            auditService.logAdminEdit(console, targetUUID, targetName, "creative", "Added items")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.ADMIN_EDIT &&
                    entry.actor == null &&
                    entry.actorName == "CONSOLE" &&
                    entry.details == "Added items"
                })
            }
        }

        @Test
        @DisplayName("Should not log edit when disabled")
        fun noLogEditWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val admin = createMockAdminPlayer()
            // Should complete without error when disabled
            auditService.logAdminEdit(admin, targetUUID, targetName, "survival")
        }

        @Test
        @DisplayName("Should log edit regardless of logViews setting")
        fun logEditRegardlessOfLogViews() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = true, logViews = false)
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logAdminEdit(admin, targetUUID, targetName, "survival")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.ADMIN_EDIT
                })
            }
        }
    }

    // =========================================================================
    // logClear Tests
    // =========================================================================

    @Nested
    @DisplayName("logClear")
    inner class LogClearTests {

        @Test
        @DisplayName("Should log clear with player actor")
        fun logClearWithPlayerActor() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logClear(admin, targetUUID, targetName, "survival")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.INVENTORY_CLEAR &&
                    entry.actor == adminUUID &&
                    entry.actorName == adminName &&
                    entry.target == targetUUID &&
                    entry.group == "survival"
                })
            }
        }

        @Test
        @DisplayName("Should log clear with console actor")
        fun logClearWithConsoleActor() = runTest {
            initializeServiceWithStorage()

            val console = createMockCommandSender()
            auditService.logClear(console, targetUUID, targetName, "creative")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.INVENTORY_CLEAR &&
                    entry.actor == null &&
                    entry.actorName == "CONSOLE"
                })
            }
        }

        @Test
        @DisplayName("Should not log clear when disabled")
        fun noLogClearWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val admin = createMockAdminPlayer()
            // Should complete without error when disabled
            auditService.logClear(admin, targetUUID, targetName, "survival")
        }
    }

    // =========================================================================
    // logVersionRestore Tests
    // =========================================================================

    @Nested
    @DisplayName("logVersionRestore")
    inner class LogVersionRestoreTests {

        @Test
        @DisplayName("Should log version restore with player actor")
        fun logVersionRestoreWithPlayerActor() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logVersionRestore(admin, targetUUID, targetName, "survival", 5L)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.VERSION_RESTORE &&
                    entry.actor == adminUUID &&
                    entry.actorName == adminName &&
                    entry.target == targetUUID &&
                    entry.group == "survival" &&
                    entry.details == "Restored version #5"
                })
            }
        }

        @Test
        @DisplayName("Should log version restore with console actor")
        fun logVersionRestoreWithConsoleActor() = runTest {
            initializeServiceWithStorage()

            val console = createMockCommandSender()
            auditService.logVersionRestore(console, targetUUID, targetName, "creative", 10L)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.VERSION_RESTORE &&
                    entry.actor == null &&
                    entry.actorName == "CONSOLE" &&
                    entry.details == "Restored version #10"
                })
            }
        }

        @Test
        @DisplayName("Should not log version restore when disabled")
        fun noLogVersionRestoreWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val admin = createMockAdminPlayer()
            // Should complete without error when disabled
            auditService.logVersionRestore(admin, targetUUID, targetName, "survival", 1L)
        }
    }

    // =========================================================================
    // logDeathRestore Tests
    // =========================================================================

    @Nested
    @DisplayName("logDeathRestore")
    inner class LogDeathRestoreTests {

        @Test
        @DisplayName("Should log death restore with player actor")
        fun logDeathRestoreWithPlayerActor() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logDeathRestore(admin, targetUUID, targetName, "survival", 3L)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.DEATH_RESTORE &&
                    entry.actor == adminUUID &&
                    entry.actorName == adminName &&
                    entry.target == targetUUID &&
                    entry.group == "survival" &&
                    entry.details == "Restored death #3"
                })
            }
        }

        @Test
        @DisplayName("Should log death restore with console actor")
        fun logDeathRestoreWithConsoleActor() = runTest {
            initializeServiceWithStorage()

            val console = createMockCommandSender()
            auditService.logDeathRestore(console, targetUUID, targetName, "creative", 7L)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.DEATH_RESTORE &&
                    entry.actor == null &&
                    entry.actorName == "CONSOLE" &&
                    entry.details == "Restored death #7"
                })
            }
        }

        @Test
        @DisplayName("Should not log death restore when disabled")
        fun noLogDeathRestoreWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val admin = createMockAdminPlayer()
            // Should complete without error when disabled
            auditService.logDeathRestore(admin, targetUUID, targetName, "survival", 1L)
        }
    }

    // =========================================================================
    // logTemplateApply Tests
    // =========================================================================

    @Nested
    @DisplayName("logTemplateApply")
    inner class LogTemplateApplyTests {

        @Test
        @DisplayName("Should log template apply with player actor")
        fun logTemplateApplyWithPlayerActor() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logTemplateApply(admin, targetUUID, targetName, "survival", "starter_kit")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.TEMPLATE_APPLY &&
                    entry.actor == adminUUID &&
                    entry.actorName == adminName &&
                    entry.target == targetUUID &&
                    entry.group == "survival" &&
                    entry.details == "Applied template 'starter_kit'"
                })
            }
        }

        @Test
        @DisplayName("Should log template apply with console actor")
        fun logTemplateApplyWithConsoleActor() = runTest {
            initializeServiceWithStorage()

            val console = createMockCommandSender()
            auditService.logTemplateApply(console, targetUUID, targetName, "creative", "vip_kit")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.TEMPLATE_APPLY &&
                    entry.actor == null &&
                    entry.actorName == "CONSOLE" &&
                    entry.details == "Applied template 'vip_kit'"
                })
            }
        }

        @Test
        @DisplayName("Should log template apply with null actor as SYSTEM")
        fun logTemplateApplyWithNullActor() = runTest {
            initializeServiceWithStorage()

            auditService.logTemplateApply(null, targetUUID, targetName, "survival", "auto_template")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.TEMPLATE_APPLY &&
                    entry.actor == null &&
                    entry.actorName == "SYSTEM" &&
                    entry.details == "Applied template 'auto_template'"
                })
            }
        }

        @Test
        @DisplayName("Should not log template apply when disabled")
        fun noLogTemplateApplyWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val admin = createMockAdminPlayer()
            // Should complete without error when disabled
            auditService.logTemplateApply(admin, targetUUID, targetName, "survival", "kit")
        }
    }

    // =========================================================================
    // logLock Tests
    // =========================================================================

    @Nested
    @DisplayName("logLock")
    inner class LogLockTests {

        @Test
        @DisplayName("Should log lock with player actor and reason")
        fun logLockWithPlayerActorAndReason() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logLock(admin, targetUUID, targetName, "Suspected duping")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.LOCK_APPLY &&
                    entry.actor == adminUUID &&
                    entry.actorName == adminName &&
                    entry.target == targetUUID &&
                    entry.details == "Suspected duping" &&
                    entry.group == null
                })
            }
        }

        @Test
        @DisplayName("Should log lock with console actor")
        fun logLockWithConsoleActor() = runTest {
            initializeServiceWithStorage()

            val console = createMockCommandSender()
            auditService.logLock(console, targetUUID, targetName, "Admin request")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.LOCK_APPLY &&
                    entry.actor == null &&
                    entry.actorName == "CONSOLE"
                })
            }
        }

        @Test
        @DisplayName("Should log lock with null reason")
        fun logLockWithNullReason() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logLock(admin, targetUUID, targetName, null)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.LOCK_APPLY &&
                    entry.details == null
                })
            }
        }

        @Test
        @DisplayName("Should not log lock when disabled")
        fun noLogLockWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val admin = createMockAdminPlayer()
            // Should complete without error when disabled
            auditService.logLock(admin, targetUUID, targetName, "reason")
        }
    }

    // =========================================================================
    // logUnlock Tests
    // =========================================================================

    @Nested
    @DisplayName("logUnlock")
    inner class LogUnlockTests {

        @Test
        @DisplayName("Should log unlock with player actor")
        fun logUnlockWithPlayerActor() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logUnlock(admin, targetUUID, targetName)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.LOCK_REMOVE &&
                    entry.actor == adminUUID &&
                    entry.actorName == adminName &&
                    entry.target == targetUUID &&
                    entry.targetName == targetName
                })
            }
        }

        @Test
        @DisplayName("Should log unlock with console actor")
        fun logUnlockWithConsoleActor() = runTest {
            initializeServiceWithStorage()

            val console = createMockCommandSender()
            auditService.logUnlock(console, targetUUID, targetName)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.LOCK_REMOVE &&
                    entry.actor == null &&
                    entry.actorName == "CONSOLE"
                })
            }
        }

        @Test
        @DisplayName("Should not log unlock when disabled")
        fun noLogUnlockWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val admin = createMockAdminPlayer()
            // Should complete without error when disabled
            auditService.logUnlock(admin, targetUUID, targetName)
        }
    }

    // =========================================================================
    // logBulkOperation Tests
    // =========================================================================

    @Nested
    @DisplayName("logBulkOperation")
    inner class LogBulkOperationTests {

        @Test
        @DisplayName("Should log bulk operation with player actor")
        fun logBulkOperationWithPlayerActor() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logBulkOperation(admin, AuditAction.BULK_CLEAR, "survival", 50, "Cleared all")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.BULK_CLEAR &&
                    entry.actor == adminUUID &&
                    entry.actorName == adminName &&
                    entry.target == UUID(0, 0) &&
                    entry.targetName == "BULK" &&
                    entry.group == "survival" &&
                    entry.details == "Affected 50 players - Cleared all"
                })
            }
        }

        @Test
        @DisplayName("Should log bulk operation with console actor")
        fun logBulkOperationWithConsoleActor() = runTest {
            initializeServiceWithStorage()

            val console = createMockCommandSender()
            auditService.logBulkOperation(console, AuditAction.BULK_EXPORT, "creative", 25)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.BULK_EXPORT &&
                    entry.actor == null &&
                    entry.actorName == "CONSOLE" &&
                    entry.targetName == "BULK" &&
                    entry.details == "Affected 25 players"
                })
            }
        }

        @Test
        @DisplayName("Should handle null details")
        fun handleNullDetails() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logBulkOperation(admin, AuditAction.BULK_RESET_STATS, "survival", 10, null)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.details == "Affected 10 players"
                })
            }
        }

        @Test
        @DisplayName("Should not log bulk operation when disabled")
        fun noLogBulkOperationWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val admin = createMockAdminPlayer()
            // Should complete without error when disabled
            auditService.logBulkOperation(admin, AuditAction.BULK_CLEAR, "survival", 10)
        }
    }

    // =========================================================================
    // logGroupChange Tests
    // =========================================================================

    @Nested
    @DisplayName("logGroupChange")
    inner class LogGroupChangeTests {

        @Test
        @DisplayName("Should log group change")
        fun logGroupChange() = runTest {
            initializeServiceWithStorage()

            val player = createMockPlayer()
            auditService.logGroupChange(player, "survival", "creative")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.action == AuditAction.GROUP_CHANGE &&
                    entry.target == playerUUID &&
                    entry.targetName == playerName &&
                    entry.group == "creative" &&
                    entry.details == "Changed from 'survival' to 'creative'" &&
                    entry.actorName == "SYSTEM"
                })
            }
        }

        @Test
        @DisplayName("Should not log group change when disabled")
        fun noLogGroupChangeWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            val player = createMockPlayer()
            // Should complete without error when disabled
            auditService.logGroupChange(player, "survival", "creative")
        }
    }

    // =========================================================================
    // Server ID Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Server ID Handling")
    inner class ServerIdHandlingTests {

        @Test
        @DisplayName("Should include server ID when sync config is set")
        fun includeServerIdWhenSyncConfigSet() = runTest {
            val syncConfig = SyncConfig(
                enabled = true,
                serverId = "server-1"
            )
            every { serviceManager.syncConfig } returns syncConfig
            initializeServiceWithStorage()

            val player = createMockPlayer()
            auditService.logSave(player, "survival")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.serverId == "server-1"
                })
            }
        }

        @Test
        @DisplayName("Should have null server ID when sync config is null")
        fun nullServerIdWhenSyncConfigNull() = runTest {
            every { serviceManager.syncConfig } returns null
            initializeServiceWithStorage()

            val player = createMockPlayer()
            auditService.logSave(player, "survival")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.serverId == null
                })
            }
        }

        @Test
        @DisplayName("Should include server ID in all logging methods")
        fun includeServerIdInAllLoggingMethods() = runTest {
            val syncConfig = SyncConfig(
                enabled = true,
                serverId = "lobby-2"
            )
            every { serviceManager.syncConfig } returns syncConfig
            initializeServiceWithStorage()

            val player = createMockPlayer()
            val admin = createMockAdminPlayer()

            auditService.logSave(player, "survival")
            auditService.logLoad(player, "survival")
            auditService.logAdminView(admin, targetUUID, targetName, "survival")
            auditService.logAdminEdit(admin, targetUUID, targetName, "survival")
            auditService.logClear(admin, targetUUID, targetName, "survival")
            auditService.logLock(admin, targetUUID, targetName, "reason")
            auditService.logUnlock(admin, targetUUID, targetName)
            auditService.logGroupChange(player, "survival", "creative")

            coVerify(atLeast = 8) {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.serverId == "lobby-2"
                })
            }
        }
    }

    // =========================================================================
    // Query Methods Tests
    // =========================================================================

    @Nested
    @DisplayName("Query Methods")
    inner class QueryMethodsTests {

        @Test
        @DisplayName("getEntriesForPlayer should return results when enabled")
        fun getEntriesForPlayerReturnsResultsWhenEnabled() = runTest {
            initializeServiceWithStorage()

            val expectedEntries = listOf(
                AuditEntry.system(playerUUID, playerName, AuditAction.INVENTORY_SAVE, "survival"),
                AuditEntry.system(playerUUID, playerName, AuditAction.INVENTORY_LOAD, "survival")
            )
            coEvery { anyConstructed<AuditStorage>().getEntriesForPlayer(playerUUID, 50) } returns expectedEntries

            val result = auditService.getEntriesForPlayer(playerUUID)

            assertEquals(2, result.size)
            assertEquals(AuditAction.INVENTORY_SAVE, result[0].action)
            assertEquals(AuditAction.INVENTORY_LOAD, result[1].action)
        }

        @Test
        @DisplayName("getEntriesForPlayer should return empty list when disabled")
        fun getEntriesForPlayerReturnsEmptyWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            val result = auditService.getEntriesForPlayer(playerUUID)

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("getEntriesForPlayer should use custom limit")
        fun getEntriesForPlayerUsesCustomLimit() = runTest {
            initializeServiceWithStorage()

            coEvery { anyConstructed<AuditStorage>().getEntriesForPlayer(playerUUID, 100) } returns emptyList()

            auditService.getEntriesForPlayer(playerUUID, 100)

            coVerify { anyConstructed<AuditStorage>().getEntriesForPlayer(playerUUID, 100) }
        }

        @Test
        @DisplayName("searchByAction should return results when enabled")
        fun searchByActionReturnsResultsWhenEnabled() = runTest {
            initializeServiceWithStorage()

            val expectedEntries = listOf(
                AuditEntry.player(adminUUID, adminName, targetUUID, targetName, AuditAction.ADMIN_EDIT)
            )
            coEvery {
                anyConstructed<AuditStorage>().searchByAction(AuditAction.ADMIN_EDIT, any(), any(), 100)
            } returns expectedEntries

            val result = auditService.searchByAction(AuditAction.ADMIN_EDIT)

            assertEquals(1, result.size)
            assertEquals(AuditAction.ADMIN_EDIT, result[0].action)
        }

        @Test
        @DisplayName("searchByAction should return empty list when disabled")
        fun searchByActionReturnsEmptyWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            val result = auditService.searchByAction(AuditAction.ADMIN_EDIT)

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("searchByAction should support date range filters")
        fun searchByActionSupportsDateRangeFilters() = runTest {
            initializeServiceWithStorage()

            val from = Instant.now().minus(1, ChronoUnit.DAYS)
            val to = Instant.now()

            coEvery {
                anyConstructed<AuditStorage>().searchByAction(AuditAction.INVENTORY_CLEAR, from, to, 50)
            } returns emptyList()

            auditService.searchByAction(AuditAction.INVENTORY_CLEAR, from, to, 50)

            coVerify { anyConstructed<AuditStorage>().searchByAction(AuditAction.INVENTORY_CLEAR, from, to, 50) }
        }

        @Test
        @DisplayName("getEntriesInRange should return results when enabled")
        fun getEntriesInRangeReturnsResultsWhenEnabled() = runTest {
            initializeServiceWithStorage()

            val from = Instant.now().minus(7, ChronoUnit.DAYS)
            val to = Instant.now()
            val expectedEntries = listOf(
                AuditEntry.system(playerUUID, playerName, AuditAction.INVENTORY_SAVE, "survival")
            )
            coEvery { anyConstructed<AuditStorage>().getEntriesInRange(from, to, 1000) } returns expectedEntries

            val result = auditService.getEntriesInRange(from, to)

            assertEquals(1, result.size)
        }

        @Test
        @DisplayName("getEntriesInRange should return empty list when disabled")
        fun getEntriesInRangeReturnsEmptyWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            val from = Instant.now().minus(7, ChronoUnit.DAYS)
            val to = Instant.now()

            val result = auditService.getEntriesInRange(from, to)

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("getEntriesInRange should use custom limit")
        fun getEntriesInRangeUsesCustomLimit() = runTest {
            initializeServiceWithStorage()

            val from = Instant.now().minus(7, ChronoUnit.DAYS)
            val to = Instant.now()

            coEvery { anyConstructed<AuditStorage>().getEntriesInRange(from, to, 500) } returns emptyList()

            auditService.getEntriesInRange(from, to, 500)

            coVerify { anyConstructed<AuditStorage>().getEntriesInRange(from, to, 500) }
        }
    }

    // =========================================================================
    // CSV Export Tests
    // =========================================================================

    @Nested
    @DisplayName("CSV Export")
    inner class CsvExportTests {

        @Test
        @DisplayName("Should export entries to CSV file successfully")
        fun exportEntriesToCsvSuccessfully() = runTest {
            initializeServiceWithStorage()

            val entries = listOf(
                AuditEntry(
                    id = 1,
                    timestamp = Instant.now(),
                    actor = adminUUID,
                    actorName = adminName,
                    target = targetUUID,
                    targetName = targetName,
                    action = AuditAction.ADMIN_EDIT,
                    group = "survival",
                    details = "Modified items"
                )
            )

            val csvContent = "id,timestamp,actor_uuid,actor_name,target_uuid,target_name,action,group,details,server_id\n" +
                "1,${entries[0].timestamp},$adminUUID,$adminName,$targetUUID,$targetName,ADMIN_EDIT,survival,Modified items,\n"

            coEvery { anyConstructed<AuditStorage>().exportToCsv(entries) } returns csvContent

            val outputFile = File(tempDir.toFile(), "audit_export.csv")
            val result = auditService.exportToCsv(entries, outputFile)

            assertTrue(result)
            assertTrue(outputFile.exists())
            assertTrue(outputFile.readText().contains("ADMIN_EDIT"))
            assertTrue(outputFile.readText().contains(adminName))
        }

        @Test
        @DisplayName("Should return false when disabled")
        fun returnFalseWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            val outputFile = File(tempDir.toFile(), "audit_export.csv")
            val result = auditService.exportToCsv(emptyList(), outputFile)

            assertFalse(result)
            assertFalse(outputFile.exists())
        }

        @Test
        @DisplayName("Should create parent directories if needed")
        fun createParentDirectoriesIfNeeded() = runTest {
            initializeServiceWithStorage()

            val entries = listOf(
                AuditEntry.system(playerUUID, playerName, AuditAction.INVENTORY_SAVE, "survival")
            )

            coEvery { anyConstructed<AuditStorage>().exportToCsv(entries) } returns "header\ndata\n"

            val outputFile = File(tempDir.toFile(), "subdir/nested/audit_export.csv")
            val result = auditService.exportToCsv(entries, outputFile)

            assertTrue(result)
            assertTrue(outputFile.exists())
            assertTrue(outputFile.parentFile.exists())
        }

        @Test
        @DisplayName("Should handle export errors gracefully")
        fun handleExportErrorsGracefully() = runTest {
            initializeServiceWithStorage()

            val entries = listOf(
                AuditEntry.system(playerUUID, playerName, AuditAction.INVENTORY_SAVE, "survival")
            )

            coEvery { anyConstructed<AuditStorage>().exportToCsv(entries) } throws RuntimeException("Export failed")

            val outputFile = File(tempDir.toFile(), "audit_export.csv")
            val result = auditService.exportToCsv(entries, outputFile)

            assertFalse(result)
            verify { Logging.error(match<String> { it.contains("Failed to export") }, any()) }
        }
    }

    // =========================================================================
    // Entry Count and Storage Size Tests
    // =========================================================================

    @Nested
    @DisplayName("Entry Count and Storage Size")
    inner class EntryCountAndStorageSizeTests {

        @Test
        @DisplayName("getEntryCount should return count when enabled")
        fun getEntryCountReturnsCountWhenEnabled() = runTest {
            initializeServiceWithStorage()

            coEvery { anyConstructed<AuditStorage>().getEntryCount() } returns 150

            val count = auditService.getEntryCount()

            assertEquals(150, count)
        }

        @Test
        @DisplayName("getEntryCount should return 0 when disabled")
        fun getEntryCountReturnsZeroWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            val count = auditService.getEntryCount()

            assertEquals(0, count)
        }

        @Test
        @DisplayName("getStorageSize should return size when enabled")
        fun getStorageSizeReturnsSizeWhenEnabled() = runTest {
            initializeServiceWithStorage()

            every { anyConstructed<AuditStorage>().getStorageSize() } returns 1024000L

            val size = auditService.getStorageSize()

            assertEquals(1024000L, size)
        }

        @Test
        @DisplayName("getStorageSize should return 0 when disabled")
        fun getStorageSizeReturnsZeroWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            val size = auditService.getStorageSize()

            assertEquals(0L, size)
        }
    }

    // =========================================================================
    // Shutdown Tests
    // =========================================================================

    @Nested
    @DisplayName("Shutdown")
    inner class ShutdownTests {

        @Test
        @DisplayName("Should flush buffer and close storage on shutdown")
        fun flushBufferAndCloseStorageOnShutdown() = runTest {
            initializeServiceWithStorage()

            auditService.shutdown()

            coVerify { anyConstructed<AuditStorage>().flushBuffer() }
            coVerify { anyConstructed<AuditStorage>().shutdown() }
        }

        @Test
        @DisplayName("Should handle shutdown when not initialized")
        fun handleShutdownWhenNotInitialized() = runTest {
            // Don't initialize - just call shutdown directly
            // Should not throw when not initialized
            auditService.shutdown()
            // Test passes if no exception is thrown
        }

        @Test
        @DisplayName("Should handle shutdown when disabled")
        fun handleShutdownWhenDisabled() = runTest {
            every { mainConfig.audit } returns AuditConfig(enabled = false)
            auditService.initialize()

            assertFalse(auditService.isEnabled)
            // Should not throw when disabled
            auditService.shutdown()
            // Test passes if no exception is thrown
        }
    }

    // =========================================================================
    // Reload Tests
    // =========================================================================

    @Nested
    @DisplayName("Reload")
    inner class ReloadTests {

        @Test
        @DisplayName("Should reload config")
        fun reloadConfig() = runTest {
            initializeServiceWithStorage()

            val newConfig = AuditConfig(
                enabled = true,
                retentionDays = 90,
                logViews = false,
                logSaves = true
            )
            every { mainConfig.audit } returns newConfig

            auditService.reload()

            assertEquals(90, auditService.config.retentionDays)
            assertFalse(auditService.config.logViews)
            verify { Logging.debug(any<() -> String>()) }
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle special characters in player names")
        fun handleSpecialCharactersInPlayerNames() = runTest {
            initializeServiceWithStorage()

            val specialPlayer = mockk<Player>(relaxed = true).apply {
                every { uniqueId } returns playerUUID
                every { name } returns "Player\"With,Special'Chars"
            }

            auditService.logSave(specialPlayer, "survival")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.targetName == "Player\"With,Special'Chars"
                })
            }
        }

        @Test
        @DisplayName("Should handle empty group name")
        fun handleEmptyGroupName() = runTest {
            initializeServiceWithStorage()

            val player = createMockPlayer()
            auditService.logSave(player, "")

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.group == ""
                })
            }
        }

        @Test
        @DisplayName("Should handle very long details string")
        fun handleVeryLongDetailsString() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            val longDetails = "A".repeat(10000)

            auditService.logAdminEdit(admin, targetUUID, targetName, "survival", longDetails)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.details?.length == 10000
                })
            }
        }

        @Test
        @DisplayName("Should handle zero affected count in bulk operation")
        fun handleZeroAffectedCountInBulkOperation() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logBulkOperation(admin, AuditAction.BULK_CLEAR, "survival", 0)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.details == "Affected 0 players"
                })
            }
        }

        @Test
        @DisplayName("Should handle negative version ID")
        fun handleNegativeVersionId() = runTest {
            initializeServiceWithStorage()

            val admin = createMockAdminPlayer()
            auditService.logVersionRestore(admin, targetUUID, targetName, "survival", -1L)

            coVerify {
                anyConstructed<AuditStorage>().record(match { entry ->
                    entry.details == "Restored version #-1"
                })
            }
        }
    }
}

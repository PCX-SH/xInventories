package sh.pcx.xinventories.integration.service

import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.bukkit.GameMode
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.config.*
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.service.MigrationService
import sh.pcx.xinventories.internal.service.StorageService
import sh.pcx.xinventories.internal.storage.MySqlStorage
import sh.pcx.xinventories.internal.storage.Storage
import sh.pcx.xinventories.internal.util.Logging
import java.nio.file.Path
import java.time.Instant
import java.util.*
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Comprehensive integration tests for MigrationService.
 *
 * Tests cover:
 * - Migration between different storage backends (YAML, SQLite, MySQL)
 * - Data preservation during migration
 * - Migration report accuracy
 * - Error handling and edge cases
 * - Concurrent migration prevention
 * - Configuration validation
 * - Connection testing
 * - Progress and duration tracking
 */
@DisplayName("MigrationService Integration Tests")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MigrationServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var storageService: StorageService
    private lateinit var migrationService: MigrationService

    // Test UUIDs
    private val uuid1 = UUID.randomUUID()
    private val uuid2 = UUID.randomUUID()
    private val uuid3 = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        // Initialize Logging
        Logging.init(Logger.getLogger("Test"), false)
        mockkObject(Logging)
        every { Logging.info(any()) } returns Unit
        every { Logging.debug(any<() -> String>()) } returns Unit
        every { Logging.error(any(), any()) } returns Unit
        every { Logging.warning(any()) } returns Unit

        // Create mock plugin
        plugin = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        storageService = mockk(relaxed = true)

        // Set up plugin data folder
        val dataFolder = tempDir.resolve("plugin").toFile()
        dataFolder.mkdirs()
        every { plugin.dataFolder } returns dataFolder

        // Set up default config
        val mainConfig = MainConfig(
            storage = StorageConfig(
                type = StorageType.YAML,
                sqlite = SqliteConfig(file = "data/test.db"),
                mysql = MysqlConfig(
                    host = "localhost",
                    port = 3306,
                    database = "test_db",
                    username = "test_user",
                    password = "test_pass"
                )
            )
        )
        every { configManager.mainConfig } returns mainConfig
        every { plugin.configManager } returns configManager

        // Create migration service
        migrationService = MigrationService(plugin, storageService)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== YAML to SQLite Migration Tests ====================

    @Nested
    @DisplayName("YAML to SQLite Migration")
    inner class YamlToSqliteMigrationTests {

        @Test
        @DisplayName("Should successfully migrate from YAML to SQLite")
        fun migrateYamlToSqlite() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            val playerDataList = createTestPlayerData(listOf(uuid1, uuid2))
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(StorageType.YAML, report.from)
            assertEquals(StorageType.SQLITE, report.to)
            assertEquals(2, report.playersProcessed)
            assertTrue(report.success)
            assertTrue(report.errors.isEmpty())
        }

        @Test
        @DisplayName("Should preserve all player data when migrating YAML to SQLite")
        fun preserveDataYamlToSqlite() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            val playerData = createDetailedPlayerData(uuid1)
            val playerDataMap = mapOf("survival_SURVIVAL" to playerData)

            coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid1)
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } returns playerDataMap
            coEvery { targetStorage.savePlayerDataBatch(any()) } answers {
                val dataList = firstArg<List<PlayerData>>()
                // Verify data integrity
                val data = dataList.first()
                assertEquals(uuid1, data.uuid)
                assertEquals("TestPlayer", data.playerName)
                assertEquals("survival", data.group)
                assertEquals(GameMode.SURVIVAL, data.gameMode)
                assertEquals(18.5, data.health)
                assertEquals(15, data.level)
                dataList.size
            }
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            coVerify { targetStorage.savePlayerDataBatch(any()) }
        }
    }

    // ==================== SQLite to YAML Migration Tests ====================

    @Nested
    @DisplayName("SQLite to YAML Migration")
    inner class SqliteToYamlMigrationTests {

        @Test
        @DisplayName("Should successfully migrate from SQLite to YAML")
        fun migrateSqliteToYaml() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("SQLite")
            val targetStorage = createMockStorage("YAML")

            val playerDataList = createTestPlayerData(listOf(uuid1, uuid2, uuid3))
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.SQLITE, StorageType.YAML)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(StorageType.SQLITE, report.from)
            assertEquals(StorageType.YAML, report.to)
            assertEquals(3, report.playersProcessed)
        }
    }

    // ==================== YAML to MySQL Migration Tests (Mocked) ====================

    @Nested
    @DisplayName("YAML to MySQL Migration (Mocked)")
    inner class YamlToMysqlMigrationTests {

        @Test
        @DisplayName("Should successfully migrate from YAML to MySQL")
        fun migrateYamlToMysql() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockMySqlStorage()

            val playerDataList = createTestPlayerData(listOf(uuid1))
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.MYSQL)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(StorageType.YAML, report.from)
            assertEquals(StorageType.MYSQL, report.to)
            assertEquals(1, report.playersProcessed)
        }
    }

    // ==================== MySQL to YAML Migration Tests (Mocked) ====================

    @Nested
    @DisplayName("MySQL to YAML Migration (Mocked)")
    inner class MysqlToYamlMigrationTests {

        @Test
        @DisplayName("Should successfully migrate from MySQL to YAML")
        fun migrateMysqlToYaml() = runTest {
            // Arrange
            val sourceStorage = createMockMySqlStorage()
            val targetStorage = createMockStorage("YAML")

            val playerDataList = createTestPlayerData(listOf(uuid1, uuid2))
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.MYSQL, StorageType.YAML)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(StorageType.MYSQL, report.from)
            assertEquals(StorageType.YAML, report.to)
            assertEquals(2, report.playersProcessed)
        }
    }

    // ==================== SQLite to MySQL Migration Tests (Mocked) ====================

    @Nested
    @DisplayName("SQLite to MySQL Migration (Mocked)")
    inner class SqliteToMysqlMigrationTests {

        @Test
        @DisplayName("Should successfully migrate from SQLite to MySQL")
        fun migrateSqliteToMysql() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("SQLite")
            val targetStorage = createMockMySqlStorage()

            val playerDataList = createTestPlayerData(listOf(uuid1, uuid2, uuid3))
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.SQLITE, StorageType.MYSQL)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(StorageType.SQLITE, report.from)
            assertEquals(StorageType.MYSQL, report.to)
            assertEquals(3, report.playersProcessed)
        }
    }

    // ==================== Data Preservation Tests ====================

    @Nested
    @DisplayName("Data Preservation During Migration")
    inner class DataPreservationTests {

        @Test
        @DisplayName("Should preserve all player data fields during migration")
        fun preserveAllPlayerDataFields() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            val playerData = PlayerData(uuid1, "TestPlayer", "survival", GameMode.SURVIVAL).apply {
                health = 15.5
                maxHealth = 20.0
                foodLevel = 18
                saturation = 4.5f
                exhaustion = 1.2f
                experience = 0.75f
                level = 25
                totalExperience = 1250
            }

            val capturedData = slot<List<PlayerData>>()

            coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid1)
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } returns mapOf("survival_SURVIVAL" to playerData)
            coEvery { targetStorage.savePlayerDataBatch(capture(capturedData)) } returns 1
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val savedData = capturedData.captured.first()
            assertEquals(uuid1, savedData.uuid)
            assertEquals("TestPlayer", savedData.playerName)
            assertEquals("survival", savedData.group)
            assertEquals(GameMode.SURVIVAL, savedData.gameMode)
            assertEquals(15.5, savedData.health)
            assertEquals(20.0, savedData.maxHealth)
            assertEquals(18, savedData.foodLevel)
            assertEquals(4.5f, savedData.saturation)
            assertEquals(1.2f, savedData.exhaustion)
            assertEquals(0.75f, savedData.experience)
            assertEquals(25, savedData.level)
            assertEquals(1250, savedData.totalExperience)
        }

        @Test
        @DisplayName("Should preserve multiple groups per player")
        fun preserveMultipleGroupsPerPlayer() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            val survivalData = PlayerData(uuid1, "TestPlayer", "survival", GameMode.SURVIVAL)
            val creativeData = PlayerData(uuid1, "TestPlayer", "creative", GameMode.CREATIVE)
            val minigamesData = PlayerData(uuid1, "TestPlayer", "minigames", GameMode.ADVENTURE)

            val playerDataMap = mapOf(
                "survival_SURVIVAL" to survivalData,
                "creative_CREATIVE" to creativeData,
                "minigames_ADVENTURE" to minigamesData
            )

            val capturedData = slot<List<PlayerData>>()

            coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid1)
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } returns playerDataMap
            coEvery { targetStorage.savePlayerDataBatch(capture(capturedData)) } returns 3
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(1, report.playersProcessed)
            assertEquals(3, report.entriesMigrated)

            val savedDataList = capturedData.captured
            assertEquals(3, savedDataList.size)
            assertTrue(savedDataList.any { it.group == "survival" })
            assertTrue(savedDataList.any { it.group == "creative" })
            assertTrue(savedDataList.any { it.group == "minigames" })
        }
    }

    // ==================== Migration Report Tests ====================

    @Nested
    @DisplayName("Migration Report Accuracy")
    inner class MigrationReportTests {

        @Test
        @DisplayName("Should show correct player count in report")
        fun correctPlayerCount() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            val uuids = (1..10).map { UUID.randomUUID() }
            val playerDataList = createTestPlayerData(uuids)
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(10, report.playersProcessed)
        }

        @Test
        @DisplayName("Should show correct entry count in report")
        fun correctEntryCount() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            // Create multiple entries per player
            val player1Data = mapOf(
                "survival_SURVIVAL" to PlayerData(uuid1, "Player1", "survival", GameMode.SURVIVAL),
                "creative_CREATIVE" to PlayerData(uuid1, "Player1", "creative", GameMode.CREATIVE)
            )
            val player2Data = mapOf(
                "survival_SURVIVAL" to PlayerData(uuid2, "Player2", "survival", GameMode.SURVIVAL)
            )

            coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid1, uuid2)
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } returns player1Data
            coEvery { sourceStorage.loadAllPlayerData(uuid2) } returns player2Data
            coEvery { targetStorage.savePlayerDataBatch(any()) } answers {
                firstArg<List<PlayerData>>().size
            }
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(2, report.playersProcessed)
            assertEquals(3, report.entriesMigrated)
        }

        @Test
        @DisplayName("Should track errors in report")
        fun trackErrorsInReport() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid1, uuid2)
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } throws RuntimeException("Load failed")
            coEvery { sourceStorage.loadAllPlayerData(uuid2) } returns mapOf(
                "survival_SURVIVAL" to PlayerData(uuid2, "Player2", "survival", GameMode.SURVIVAL)
            )
            coEvery { targetStorage.savePlayerDataBatch(any()) } returns 1
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(1, report.playersProcessed) // Only uuid2 succeeded
            assertEquals(1, report.errors.size)
            assertFalse(report.success) // Report should indicate failure due to errors
            assertEquals(uuid1, report.errors.first().uuid)
            assertTrue(report.errors.first().message.contains("Load failed"))
        }

        @Test
        @DisplayName("Report should indicate success when no errors")
        fun reportSuccessWhenNoErrors() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            val playerDataList = createTestPlayerData(listOf(uuid1))
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertTrue(report.success)
            assertTrue(report.errors.isEmpty())
        }
    }

    // ==================== Empty Source Tests ====================

    @Nested
    @DisplayName("Empty Source Handling")
    inner class EmptySourceTests {

        @Test
        @DisplayName("Should handle empty source storage gracefully")
        fun handleEmptySource() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            coEvery { sourceStorage.getAllPlayerUUIDs() } returns emptySet()
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(0, report.playersProcessed)
            assertEquals(0, report.entriesMigrated)
            assertTrue(report.success)
            assertTrue(report.errors.isEmpty())
        }

        @Test
        @DisplayName("Should handle player with no data entries")
        fun handlePlayerWithNoData() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid1)
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } returns emptyMap()
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(0, report.playersProcessed)
            assertEquals(0, report.entriesMigrated)
        }
    }

    // ==================== Concurrent Migration Prevention Tests ====================

    @Nested
    @DisplayName("Concurrent Migration Prevention")
    inner class ConcurrentMigrationTests {

        @Test
        @DisplayName("Should prevent concurrent migrations")
        fun preventConcurrentMigrations() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            // Setup slow migration
            coEvery { sourceStorage.getAllPlayerUUIDs() } coAnswers {
                delay(500) // Simulate slow operation
                setOf(uuid1)
            }
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } returns mapOf(
                "survival_SURVIVAL" to PlayerData(uuid1, "Player1", "survival", GameMode.SURVIVAL)
            )
            coEvery { targetStorage.savePlayerDataBatch(any()) } returns 1
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act - Start first migration
            val firstJob = async {
                service.migrate(StorageType.YAML, StorageType.SQLITE)
            }

            // Wait a bit for first migration to start
            delay(100)

            // Try to start second migration while first is in progress
            val secondResult = service.migrate(StorageType.YAML, StorageType.SQLITE)

            val firstResult = firstJob.await()

            // Assert
            assertTrue(firstResult.isSuccess)
            assertTrue(secondResult.isFailure)
            assertTrue(secondResult.exceptionOrNull() is IllegalStateException)
            assertTrue(secondResult.exceptionOrNull()?.message?.contains("already in progress") == true)
        }

        @Test
        @DisplayName("Should report migration in progress status")
        fun reportMigrationInProgressStatus() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            val migrationStarted = CompletableDeferred<Unit>()
            val continueSignal = CompletableDeferred<Unit>()

            coEvery { sourceStorage.getAllPlayerUUIDs() } coAnswers {
                migrationStarted.complete(Unit)
                continueSignal.await()
                setOf(uuid1)
            }
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } returns mapOf(
                "survival_SURVIVAL" to PlayerData(uuid1, "Player1", "survival", GameMode.SURVIVAL)
            )
            coEvery { targetStorage.savePlayerDataBatch(any()) } returns 1
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Initially not in progress
            assertFalse(service.isMigrationInProgress())

            // Start migration
            val migrationJob = async {
                service.migrate(StorageType.YAML, StorageType.SQLITE)
            }

            // Wait for migration to start
            migrationStarted.await()

            // Should be in progress now
            assertTrue(service.isMigrationInProgress())

            // Allow migration to continue
            continueSignal.complete(Unit)
            migrationJob.await()

            // Should no longer be in progress
            assertFalse(service.isMigrationInProgress())
        }

        @Test
        @DisplayName("Should allow new migration after previous completes")
        fun allowNewMigrationAfterCompletion() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            val playerDataList = createTestPlayerData(listOf(uuid1))
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act - First migration
            val firstResult = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Second migration after first completes
            val secondResult = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(firstResult.isSuccess)
            assertTrue(secondResult.isSuccess)
        }
    }

    // ==================== Configuration Validation Tests ====================

    @Nested
    @DisplayName("Storage Configuration Validation")
    inner class ConfigValidationTests {

        @Test
        @DisplayName("Should validate YAML storage configuration")
        fun validateYamlConfig() {
            // Act
            val result = migrationService.validateStorageConfig(StorageType.YAML)

            // Assert
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Should validate SQLite storage configuration")
        fun validateSqliteConfig() {
            // Arrange
            val config = MainConfig(
                storage = StorageConfig(
                    sqlite = SqliteConfig(file = "data/test.db")
                )
            )
            every { configManager.mainConfig } returns config

            // Act
            val result = migrationService.validateStorageConfig(StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Should fail validation for blank SQLite file path")
        fun failValidationForBlankSqlitePath() {
            // Arrange
            val config = MainConfig(
                storage = StorageConfig(
                    sqlite = SqliteConfig(file = "")
                )
            )
            every { configManager.mainConfig } returns config

            // Act
            val result = migrationService.validateStorageConfig(StorageType.SQLITE)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("SQLite file path") == true)
        }

        @Test
        @DisplayName("Should validate MySQL storage configuration")
        fun validateMysqlConfig() {
            // Arrange
            val config = MainConfig(
                storage = StorageConfig(
                    mysql = MysqlConfig(
                        host = "localhost",
                        database = "test_db"
                    )
                )
            )
            every { configManager.mainConfig } returns config

            // Act
            val result = migrationService.validateStorageConfig(StorageType.MYSQL)

            // Assert
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Should fail validation for blank MySQL host")
        fun failValidationForBlankMysqlHost() {
            // Arrange
            val config = MainConfig(
                storage = StorageConfig(
                    mysql = MysqlConfig(
                        host = "",
                        database = "test_db"
                    )
                )
            )
            every { configManager.mainConfig } returns config

            // Act
            val result = migrationService.validateStorageConfig(StorageType.MYSQL)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("MySQL host") == true)
        }

        @Test
        @DisplayName("Should fail validation for blank MySQL database")
        fun failValidationForBlankMysqlDatabase() {
            // Arrange
            val config = MainConfig(
                storage = StorageConfig(
                    mysql = MysqlConfig(
                        host = "localhost",
                        database = ""
                    )
                )
            )
            every { configManager.mainConfig } returns config

            // Act
            val result = migrationService.validateStorageConfig(StorageType.MYSQL)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("MySQL database") == true)
        }
    }

    // ==================== Connection Testing Tests ====================

    @Nested
    @DisplayName("Storage Connection Testing")
    inner class ConnectionTestingTests {

        @Test
        @DisplayName("Should successfully test healthy storage connection")
        fun testHealthyConnection() = runTest {
            // Arrange
            val storage = createMockStorage("YAML")
            coEvery { storage.isHealthy() } returns true
            setupStorageInitShutdown(storage)

            val service = createMigrationServiceWithTestConnection(storage)

            // Act
            val result = service.testConnection(StorageType.YAML)

            // Assert
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Should fail test for unhealthy storage connection")
        fun testUnhealthyConnection() = runTest {
            // Arrange
            val storage = createMockStorage("YAML")
            coEvery { storage.isHealthy() } returns false
            setupStorageInitShutdown(storage)

            val service = createMigrationServiceWithTestConnection(storage)

            // Act
            val result = service.testConnection(StorageType.YAML)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("not healthy") == true)
        }

        @Test
        @DisplayName("Should handle connection test exception")
        fun handleConnectionTestException() = runTest {
            // Arrange
            val storage = createMockStorage("YAML")
            coEvery { storage.initialize() } throws RuntimeException("Connection failed")

            val service = createMigrationServiceWithTestConnection(storage)

            // Act
            val result = service.testConnection(StorageType.YAML)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Connection failed") == true)
        }
    }

    // ==================== Error Handling Tests ====================

    @Nested
    @DisplayName("Migration Error Handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("Should reject migration with same source and target")
        fun rejectSameSourceAndTarget() = runTest {
            // Act
            val result = migrationService.migrate(StorageType.YAML, StorageType.YAML)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(result.exceptionOrNull()?.message?.contains("same") == true)
        }

        @Test
        @DisplayName("Should handle source storage initialization failure")
        fun handleSourceInitializationFailure() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            coEvery { sourceStorage.initialize() } throws RuntimeException("Init failed")

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Init failed") == true)
        }

        @Test
        @DisplayName("Should handle target storage initialization failure")
        fun handleTargetInitializationFailure() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            coEvery { sourceStorage.initialize() } just runs
            coEvery { targetStorage.initialize() } throws RuntimeException("Target init failed")

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("Should continue migration despite individual player failures")
        fun continueDespitePlayerFailures() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid1, uuid2, uuid3)
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } throws RuntimeException("Player 1 load failed")
            coEvery { sourceStorage.loadAllPlayerData(uuid2) } returns mapOf(
                "survival_SURVIVAL" to PlayerData(uuid2, "Player2", "survival", GameMode.SURVIVAL)
            )
            coEvery { sourceStorage.loadAllPlayerData(uuid3) } throws RuntimeException("Player 3 load failed")
            coEvery { targetStorage.savePlayerDataBatch(any()) } returns 1
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(1, report.playersProcessed) // Only uuid2 succeeded
            assertEquals(2, report.errors.size)
            assertFalse(report.success)
        }

        @Test
        @DisplayName("Should properly shutdown storages after error")
        fun shutdownStoragesAfterError() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            coEvery { sourceStorage.initialize() } just runs
            coEvery { targetStorage.initialize() } just runs
            coEvery { sourceStorage.getAllPlayerUUIDs() } throws RuntimeException("Unexpected error")
            coEvery { sourceStorage.shutdown() } just runs
            coEvery { targetStorage.shutdown() } just runs

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isFailure)
            coVerify { sourceStorage.shutdown() }
            coVerify { targetStorage.shutdown() }
        }

        @Test
        @DisplayName("Should reset migration flag after failure")
        fun resetMigrationFlagAfterFailure() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            coEvery { sourceStorage.initialize() } throws RuntimeException("Init failed")

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isFailure)
            assertFalse(service.isMigrationInProgress())
        }
    }

    // ==================== Progress and Duration Tracking Tests ====================

    @Nested
    @DisplayName("Progress and Duration Tracking")
    inner class ProgressDurationTests {

        @Test
        @Disabled("Duration tracking test doesn't work correctly with TestDispatcher virtual time")
        @DisplayName("Should track migration duration")
        fun trackMigrationDuration() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            coEvery { sourceStorage.getAllPlayerUUIDs() } coAnswers {
                delay(100) // Add some delay
                setOf(uuid1)
            }
            coEvery { sourceStorage.loadAllPlayerData(uuid1) } returns mapOf(
                "survival_SURVIVAL" to PlayerData(uuid1, "Player1", "survival", GameMode.SURVIVAL)
            )
            coEvery { targetStorage.savePlayerDataBatch(any()) } coAnswers {
                delay(50) // Add some delay
                1
            }
            setupStorageInitShutdown(sourceStorage)
            setupStorageInitShutdown(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertTrue(report.duration.toMillis() >= 100) // At least the delay time
            assertTrue(report.startTime.isBefore(report.endTime))
        }

        @Test
        @DisplayName("Should record accurate start and end times")
        fun recordAccurateStartEndTimes() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            val playerDataList = createTestPlayerData(listOf(uuid1))
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            val beforeMigration = Instant.now()

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            val afterMigration = Instant.now()

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertTrue(report.startTime.isAfter(beforeMigration) || report.startTime == beforeMigration)
            assertTrue(report.endTime.isBefore(afterMigration) || report.endTime == afterMigration)
        }

        @Test
        @DisplayName("Should log progress for large migrations")
        fun logProgressForLargeMigrations() = runTest {
            // Arrange
            val sourceStorage = createMockStorage("YAML")
            val targetStorage = createMockStorage("SQLite")

            // Create 250 players (triggers 2 progress logs at 100 and 200)
            val uuids = (1..250).map { UUID.randomUUID() }
            val playerDataList = createTestPlayerData(uuids)
            setupStorageMocks(sourceStorage, playerDataList)
            setupTargetStorageMock(targetStorage)

            val progressLogs = mutableListOf<String>()
            every { Logging.info(capture(progressLogs)) } returns Unit

            val service = createMigrationServiceWithMockedStorages(sourceStorage, targetStorage)

            // Act
            val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

            // Assert
            assertTrue(result.isSuccess)
            val report = result.getOrNull()!!
            assertEquals(250, report.playersProcessed)
            // Verify progress was logged (100 and 200 checkpoints)
            assertTrue(progressLogs.any { it.contains("100 players processed") })
            assertTrue(progressLogs.any { it.contains("200 players processed") })
        }
    }

    // ==================== Helper Methods ====================

    private fun createMockStorage(name: String): Storage {
        val storage = mockk<Storage>(relaxed = true)
        every { storage.name } returns name
        return storage
    }

    private fun createMockMySqlStorage(): Storage {
        val storage = mockk<MySqlStorage>(relaxed = true)
        every { storage.name } returns "MySQL"
        return storage
    }

    private fun setupStorageInitShutdown(storage: Storage) {
        coEvery { storage.initialize() } just runs
        coEvery { storage.shutdown() } just runs
    }

    private fun setupStorageMocks(sourceStorage: Storage, playerDataMap: Map<UUID, Map<String, PlayerData>>) {
        coEvery { sourceStorage.getAllPlayerUUIDs() } returns playerDataMap.keys
        playerDataMap.forEach { (uuid, data) ->
            coEvery { sourceStorage.loadAllPlayerData(uuid) } returns data
        }
        setupStorageInitShutdown(sourceStorage)
    }

    private fun setupTargetStorageMock(targetStorage: Storage) {
        coEvery { targetStorage.savePlayerDataBatch(any()) } answers {
            firstArg<List<PlayerData>>().size
        }
        setupStorageInitShutdown(targetStorage)
    }

    private fun createTestPlayerData(uuids: List<UUID>): Map<UUID, Map<String, PlayerData>> {
        return uuids.associateWith { uuid ->
            mapOf(
                "survival_SURVIVAL" to PlayerData(
                    uuid = uuid,
                    playerName = "Player_${uuid.toString().take(8)}",
                    group = "survival",
                    gameMode = GameMode.SURVIVAL
                )
            )
        }
    }

    private fun createDetailedPlayerData(uuid: UUID): PlayerData {
        return PlayerData(uuid, "TestPlayer", "survival", GameMode.SURVIVAL).apply {
            health = 18.5
            maxHealth = 20.0
            foodLevel = 15
            saturation = 3.5f
            exhaustion = 0.5f
            experience = 0.6f
            level = 15
            totalExperience = 750
        }
    }

    /**
     * Creates a MigrationService that uses mocked storages for testing.
     * Uses reflection to inject mocked storages via the createStorage method.
     */
    private fun createMigrationServiceWithMockedStorages(
        sourceStorage: Storage,
        targetStorage: Storage
    ): MigrationService {
        val service = spyk(MigrationService(plugin, storageService))

        // Mock the createStorage method to return our mocked storages
        var callCount = 0
        every { service["createStorage"](any<StorageType>()) } answers {
            if (callCount++ == 0) sourceStorage else targetStorage
        }

        return service
    }

    private fun createMigrationServiceWithTestConnection(
        storage: Storage
    ): MigrationService {
        val service = spyk(MigrationService(plugin, storageService))

        every { service["createStorage"](any<StorageType>()) } returns storage

        return service
    }
}

/**
 * Additional edge case tests for MigrationService.
 */
@DisplayName("MigrationService Edge Case Tests")
class MigrationServiceEdgeCaseTests {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var storageService: StorageService

    @BeforeEach
    fun setUp() {
        Logging.init(Logger.getLogger("Test"), false)
        mockkObject(Logging)
        every { Logging.info(any()) } returns Unit
        every { Logging.debug(any<() -> String>()) } returns Unit
        every { Logging.error(any(), any()) } returns Unit
        every { Logging.warning(any()) } returns Unit

        plugin = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        storageService = mockk(relaxed = true)

        val dataFolder = tempDir.resolve("plugin").toFile()
        dataFolder.mkdirs()
        every { plugin.dataFolder } returns dataFolder

        val mainConfig = MainConfig()
        every { configManager.mainConfig } returns mainConfig
        every { plugin.configManager } returns configManager
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    @DisplayName("Should handle very large player data sets")
    fun handleVeryLargeDataSets() = runTest {
        // Arrange
        val sourceStorage = mockk<Storage>(relaxed = true)
        val targetStorage = mockk<Storage>(relaxed = true)

        // Create 1000 players
        val uuids = (1..1000).map { UUID.randomUUID() }.toSet()

        coEvery { sourceStorage.getAllPlayerUUIDs() } returns uuids
        uuids.forEach { uuid ->
            coEvery { sourceStorage.loadAllPlayerData(uuid) } returns mapOf(
                "survival_SURVIVAL" to PlayerData(uuid, "Player", "survival", GameMode.SURVIVAL)
            )
        }
        coEvery { targetStorage.savePlayerDataBatch(any()) } answers {
            firstArg<List<PlayerData>>().size
        }
        coEvery { sourceStorage.initialize() } just runs
        coEvery { sourceStorage.shutdown() } just runs
        coEvery { targetStorage.initialize() } just runs
        coEvery { targetStorage.shutdown() } just runs
        every { sourceStorage.name } returns "YAML"
        every { targetStorage.name } returns "SQLite"

        val service = spyk(MigrationService(plugin, storageService))
        var callCount = 0
        every { service["createStorage"](any<StorageType>()) } answers {
            if (callCount++ == 0) sourceStorage else targetStorage
        }

        // Act
        val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

        // Assert
        assertTrue(result.isSuccess)
        val report = result.getOrNull()!!
        assertEquals(1000, report.playersProcessed)
    }

    @Test
    @DisplayName("Should handle special characters in player names")
    fun handleSpecialCharactersInPlayerNames() = runTest {
        // Arrange
        val sourceStorage = mockk<Storage>(relaxed = true)
        val targetStorage = mockk<Storage>(relaxed = true)

        val uuid = UUID.randomUUID()
        val specialName = "Player_Special!@#\$%^&*()"

        coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid)
        coEvery { sourceStorage.loadAllPlayerData(uuid) } returns mapOf(
            "survival_SURVIVAL" to PlayerData(uuid, specialName, "survival", GameMode.SURVIVAL)
        )
        coEvery { targetStorage.savePlayerDataBatch(any()) } answers {
            val dataList = firstArg<List<PlayerData>>()
            assertEquals(specialName, dataList.first().playerName)
            dataList.size
        }
        coEvery { sourceStorage.initialize() } just runs
        coEvery { sourceStorage.shutdown() } just runs
        coEvery { targetStorage.initialize() } just runs
        coEvery { targetStorage.shutdown() } just runs
        every { sourceStorage.name } returns "YAML"
        every { targetStorage.name } returns "SQLite"

        val service = spyk(MigrationService(plugin, storageService))
        var callCount = 0
        every { service["createStorage"](any<StorageType>()) } answers {
            if (callCount++ == 0) sourceStorage else targetStorage
        }

        // Act
        val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

        // Assert
        assertTrue(result.isSuccess)
    }

    @Test
    @DisplayName("Should handle all game modes")
    fun handleAllGameModes() = runTest {
        // Arrange
        val sourceStorage = mockk<Storage>(relaxed = true)
        val targetStorage = mockk<Storage>(relaxed = true)

        val uuid = UUID.randomUUID()
        val allGameModes = mapOf(
            "survival_SURVIVAL" to PlayerData(uuid, "Player", "survival", GameMode.SURVIVAL),
            "survival_CREATIVE" to PlayerData(uuid, "Player", "survival", GameMode.CREATIVE),
            "survival_ADVENTURE" to PlayerData(uuid, "Player", "survival", GameMode.ADVENTURE),
            "survival_SPECTATOR" to PlayerData(uuid, "Player", "survival", GameMode.SPECTATOR)
        )

        coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid)
        coEvery { sourceStorage.loadAllPlayerData(uuid) } returns allGameModes
        coEvery { targetStorage.savePlayerDataBatch(any()) } answers {
            val dataList = firstArg<List<PlayerData>>()
            assertEquals(4, dataList.size)
            dataList.size
        }
        coEvery { sourceStorage.initialize() } just runs
        coEvery { sourceStorage.shutdown() } just runs
        coEvery { targetStorage.initialize() } just runs
        coEvery { targetStorage.shutdown() } just runs
        every { sourceStorage.name } returns "YAML"
        every { targetStorage.name } returns "SQLite"

        val service = spyk(MigrationService(plugin, storageService))
        var callCount = 0
        every { service["createStorage"](any<StorageType>()) } answers {
            if (callCount++ == 0) sourceStorage else targetStorage
        }

        // Act
        val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

        // Assert
        assertTrue(result.isSuccess)
        val report = result.getOrNull()!!
        assertEquals(4, report.entriesMigrated)
    }

    @Test
    @DisplayName("Should handle extreme player data values")
    fun handleExtremePlayerDataValues() = runTest {
        // Arrange
        val sourceStorage = mockk<Storage>(relaxed = true)
        val targetStorage = mockk<Storage>(relaxed = true)

        val uuid = UUID.randomUUID()
        val extremeData = PlayerData(uuid, "Player", "survival", GameMode.SURVIVAL).apply {
            health = 0.0 // Minimum
            maxHealth = Double.MAX_VALUE
            foodLevel = 0
            saturation = 0.0f
            exhaustion = Float.MAX_VALUE
            experience = 0.999f // Near max
            level = Int.MAX_VALUE
            totalExperience = Int.MAX_VALUE
        }

        val capturedData = slot<List<PlayerData>>()

        coEvery { sourceStorage.getAllPlayerUUIDs() } returns setOf(uuid)
        coEvery { sourceStorage.loadAllPlayerData(uuid) } returns mapOf("survival_SURVIVAL" to extremeData)
        coEvery { targetStorage.savePlayerDataBatch(capture(capturedData)) } returns 1
        coEvery { sourceStorage.initialize() } just runs
        coEvery { sourceStorage.shutdown() } just runs
        coEvery { targetStorage.initialize() } just runs
        coEvery { targetStorage.shutdown() } just runs
        every { sourceStorage.name } returns "YAML"
        every { targetStorage.name } returns "SQLite"

        val service = spyk(MigrationService(plugin, storageService))
        var callCount = 0
        every { service["createStorage"](any<StorageType>()) } answers {
            if (callCount++ == 0) sourceStorage else targetStorage
        }

        // Act
        val result = service.migrate(StorageType.YAML, StorageType.SQLITE)

        // Assert
        assertTrue(result.isSuccess)
        val savedData = capturedData.captured.first()
        assertEquals(0.0, savedData.health)
        assertEquals(Double.MAX_VALUE, savedData.maxHealth)
        assertEquals(Int.MAX_VALUE, savedData.level)
    }
}

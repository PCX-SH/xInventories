package sh.pcx.xinventories.integration.service

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.bukkit.GameMode
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.io.TempDir
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.BackupMetadata
import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.config.BackupConfig
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.service.BackupService
import sh.pcx.xinventories.internal.service.StorageService
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.io.FileInputStream
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import java.util.zip.GZIPInputStream

/**
 * Comprehensive integration tests for BackupService.
 *
 * Tests cover:
 * - Create backup with auto-generated name
 * - Create backup with custom name
 * - Create compressed backup (if enabled)
 * - Create uncompressed backup
 * - List all backups
 * - List backups returns empty when none exist
 * - Get backup by ID
 * - Restore backup
 * - Restore backup that doesn't exist fails
 * - Delete backup
 * - Delete backup that doesn't exist fails
 * - Auto-cleanup removes old backups (max backups setting)
 * - Backup metadata parsing (timestamp, player count, size)
 * - Backup directory creation
 * - Handle backup directory not existing
 * - Large backup handling (many players)
 */
@DisplayName("BackupService Integration Tests")
class BackupServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var storageService: StorageService
    private lateinit var backupService: BackupService
    private lateinit var scope: CoroutineScope
    private lateinit var dataFolder: File

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            // Initialize Logging to avoid lateinit issues
            Logging.init(Logger.getLogger("BackupServiceTest"), false)
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

    @BeforeEach
    fun setUp() {
        // Create data folder
        dataFolder = tempDir.resolve("plugin").toFile()
        dataFolder.mkdirs()

        // Create mock plugin
        plugin = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        storageService = mockk(relaxed = true)

        every { plugin.plugin } returns plugin
        every { plugin.dataFolder } returns dataFolder
        every { plugin.configManager } returns configManager
        every { storageService.storageType } returns StorageType.YAML

        // Create coroutine scope for testing
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }

    @AfterEach
    fun tearDown() {
        // Clean up backup directory
        val backupDir = File(dataFolder, "backups")
        if (backupDir.exists()) {
            backupDir.deleteRecursively()
        }
    }

    // ============================================
    // Helper Methods
    // ============================================

    private fun createBackupConfig(
        autoBackup: Boolean = false,
        intervalHours: Int = 24,
        maxBackups: Int = 7,
        compression: Boolean = true,
        directory: String = "backups"
    ): BackupConfig {
        return BackupConfig(
            autoBackup = autoBackup,
            intervalHours = intervalHours,
            maxBackups = maxBackups,
            compression = compression,
            directory = directory
        )
    }

    private fun setupBackupService(backupConfig: BackupConfig): BackupService {
        val mainConfig = MainConfig(backup = backupConfig)
        every { configManager.mainConfig } returns mainConfig

        val service = BackupService(plugin, scope, storageService)
        service.initialize()
        return service
    }

    private fun createTestPlayerData(uuid: UUID, group: String, gameMode: GameMode): PlayerData {
        return PlayerData(
            uuid = uuid,
            playerName = "TestPlayer_${uuid.toString().substring(0, 8)}",
            group = group,
            gameMode = gameMode
        ).apply {
            level = 10
            experience = 0.5f
            health = 20.0
            foodLevel = 20
        }
    }

    // ============================================
    // Create Backup with Auto-generated Name Tests
    // ============================================

    @Nested
    @DisplayName("Create Backup with Auto-generated Name")
    inner class CreateBackupAutoNameTests {

        @Test
        @DisplayName("Should create backup with auto-generated name containing timestamp")
        fun createBackupWithAutoGeneratedName() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup()

            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()
            assertTrue(metadata.name.startsWith("backup_"))
            assertTrue(metadata.name.contains("_")) // Contains date/time separator
        }

        @Test
        @DisplayName("Auto-generated backup name should follow date format")
        fun autoGeneratedNameFollowsDateFormat() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup()

            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()
            // Format: backup_yyyy-MM-dd_HH-mm-ss
            val datePattern = Regex("""backup_\d{4}-\d{2}-\d{2}_\d{2}-\d{2}-\d{2}""")
            assertTrue(datePattern.containsMatchIn(metadata.name))
        }
    }

    // ============================================
    // Create Backup with Custom Name Tests
    // ============================================

    @Nested
    @DisplayName("Create Backup with Custom Name")
    inner class CreateBackupCustomNameTests {

        @Test
        @DisplayName("Should create backup with custom name")
        fun createBackupWithCustomName() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val customName = "my_custom_backup"
            val result = backupService.createBackup(customName)

            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()
            assertEquals(customName, metadata.name)
        }

        @Test
        @DisplayName("Custom name backup should create file with correct name")
        fun customNameBackupCreatesCorrectFile() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val customName = "before_reset"
            val result = backupService.createBackup(customName)

            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()

            val backupDir = File(dataFolder, "backups")
            val files = backupDir.listFiles { f -> f.name.startsWith("before_reset_") }
            Assertions.assertNotNull(files)
            assertEquals(1, files!!.size)
            assertTrue(files[0].name.endsWith(".yml"))
        }

        @Test
        @DisplayName("Should allow special characters in custom name")
        fun allowSpecialCharactersInCustomName() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val customName = "backup-v1.2.3"
            val result = backupService.createBackup(customName)

            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()
            assertEquals(customName, metadata.name)
        }
    }

    // ============================================
    // Create Compressed Backup Tests
    // ============================================

    @Nested
    @DisplayName("Create Compressed Backup")
    inner class CreateCompressedBackupTests {

        @Test
        @DisplayName("Should create compressed backup when compression enabled")
        fun createCompressedBackupWhenEnabled() = runTest {
            val backupConfig = createBackupConfig(compression = true)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("compressed_test")

            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()
            assertTrue(metadata.compressed)
        }

        @Test
        @DisplayName("Compressed backup should create .gz file")
        fun compressedBackupCreatesGzFile() = runTest {
            val backupConfig = createBackupConfig(compression = true)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("gz_test")

            assertTrue(result.isSuccess)

            val backupDir = File(dataFolder, "backups")
            val files = backupDir.listFiles { f -> f.name.startsWith("gz_test_") }
            Assertions.assertNotNull(files)
            assertEquals(1, files!!.size)
            assertTrue(files[0].extension == "gz")
        }

        @Test
        @DisplayName("Compressed backup should be readable with GZIP")
        fun compressedBackupIsReadableWithGzip() = runTest {
            val backupConfig = createBackupConfig(compression = true)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("gzip_readable")

            assertTrue(result.isSuccess)

            val backupDir = File(dataFolder, "backups")
            val files = backupDir.listFiles { f -> f.name.startsWith("gzip_readable_") }
            val backupFile = files!![0]

            // Should be able to read as GZIP without exception
            val content = GZIPInputStream(FileInputStream(backupFile)).use { gzip ->
                gzip.bufferedReader().readText()
            }
            assertTrue(content.contains("xInventories Backup"))
        }
    }

    // ============================================
    // Create Uncompressed Backup Tests
    // ============================================

    @Nested
    @DisplayName("Create Uncompressed Backup")
    inner class CreateUncompressedBackupTests {

        @Test
        @DisplayName("Should create uncompressed backup when compression disabled")
        fun createUncompressedBackupWhenDisabled() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("uncompressed_test")

            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()
            assertFalse(metadata.compressed)
        }

        @Test
        @DisplayName("Uncompressed backup should create .yml file")
        fun uncompressedBackupCreatesYmlFile() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("yml_test")

            assertTrue(result.isSuccess)

            val backupDir = File(dataFolder, "backups")
            val files = backupDir.listFiles { f -> f.name.startsWith("yml_test_") }
            Assertions.assertNotNull(files)
            assertEquals(1, files!!.size)
            assertTrue(files[0].extension == "yml")
        }

        @Test
        @DisplayName("Uncompressed backup should be plain text readable")
        fun uncompressedBackupIsPlainTextReadable() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("plain_text")

            assertTrue(result.isSuccess)

            val backupDir = File(dataFolder, "backups")
            val files = backupDir.listFiles { f -> f.name.startsWith("plain_text_") }
            val backupFile = files!![0]

            val content = backupFile.readText()
            assertTrue(content.contains("# xInventories Backup"))
            assertTrue(content.contains("backup-info:"))
        }
    }

    // ============================================
    // List All Backups Tests
    // ============================================

    @Nested
    @DisplayName("List All Backups")
    inner class ListBackupsTests {

        @Test
        @DisplayName("Should list all existing backups")
        fun listAllBackups() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            // Create multiple backups
            backupService.createBackup("backup_one")
            backupService.createBackup("backup_two")
            backupService.createBackup("backup_three")

            val backups = backupService.listBackups()

            assertEquals(3, backups.size)
        }

        @Test
        @DisplayName("Listed backups should be sorted by timestamp descending")
        fun listedBackupsSortedByTimestampDescending() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            // Create backups with small delays
            backupService.createBackup("first")
            Thread.sleep(50) // Small delay to ensure different timestamps
            backupService.createBackup("second")
            Thread.sleep(50)
            backupService.createBackup("third")

            val backups = backupService.listBackups()

            assertEquals(3, backups.size)
            // Most recent first
            assertTrue(backups[0].timestamp >= backups[1].timestamp)
            assertTrue(backups[1].timestamp >= backups[2].timestamp)
        }

        @Test
        @DisplayName("Should list both compressed and uncompressed backups")
        fun listBothCompressedAndUncompressedBackups() = runTest {
            // Create compressed backup
            val compressedConfig = createBackupConfig(compression = true)
            backupService = setupBackupService(compressedConfig)
            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()
            backupService.createBackup("compressed")

            // Create uncompressed backup
            val uncompressedConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(uncompressedConfig)
            backupService.createBackup("uncompressed")

            val backups = backupService.listBackups()

            assertEquals(2, backups.size)
            assertTrue(backups.any { it.compressed })
            assertTrue(backups.any { !it.compressed })
        }
    }

    // ============================================
    // List Backups Returns Empty Tests
    // ============================================

    @Nested
    @DisplayName("List Backups Returns Empty When None Exist")
    inner class ListBackupsEmptyTests {

        @Test
        @DisplayName("Should return empty list when no backups exist")
        fun returnEmptyListWhenNoBackups() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            val backups = backupService.listBackups()

            assertTrue(backups.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list when backup directory is empty")
        fun returnEmptyListWhenDirectoryEmpty() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            // Ensure directory exists but is empty
            val backupDir = File(dataFolder, "backups")
            backupDir.mkdirs()

            val backups = backupService.listBackups()

            assertTrue(backups.isEmpty())
        }

        @Test
        @DisplayName("Should ignore non-backup files in directory")
        fun ignoreNonBackupFiles() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            // Create non-backup files
            val backupDir = File(dataFolder, "backups")
            backupDir.mkdirs()
            File(backupDir, "readme.txt").writeText("Not a backup")
            File(backupDir, "data.json").writeText("{}")
            File(backupDir, "config.properties").writeText("key=value")

            val backups = backupService.listBackups()

            assertTrue(backups.isEmpty())
        }
    }

    // ============================================
    // Get Backup by ID Tests
    // ============================================

    @Nested
    @DisplayName("Get Backup by ID")
    inner class GetBackupByIdTests {

        @Test
        @DisplayName("Should get backup by ID")
        fun getBackupById() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val createResult = backupService.createBackup("test_get")
            val createdBackup = createResult.getOrThrow()

            val retrievedBackup = backupService.getBackup(createdBackup.id)

            Assertions.assertNotNull(retrievedBackup)
            assertEquals(createdBackup.id, retrievedBackup!!.id)
        }

        @Test
        @DisplayName("Should return null for non-existent backup ID")
        fun returnNullForNonExistentId() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            val backup = backupService.getBackup("nonexistent123")

            Assertions.assertNull(backup)
        }

        @Test
        @DisplayName("Retrieved backup should have correct metadata")
        fun retrievedBackupHasCorrectMetadata() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val createResult = backupService.createBackup("metadata_test")
            val createdBackup = createResult.getOrThrow()

            val retrievedBackup = backupService.getBackup(createdBackup.id)

            Assertions.assertNotNull(retrievedBackup)
            assertEquals(createdBackup.name, retrievedBackup!!.name)
            assertEquals(createdBackup.compressed, retrievedBackup.compressed)
            assertTrue(retrievedBackup.sizeBytes > 0)
        }
    }

    // ============================================
    // Restore Backup Tests
    // ============================================

    @Nested
    @DisplayName("Restore Backup")
    inner class RestoreBackupTests {

        @Test
        @DisplayName("Should restore backup successfully")
        fun restoreBackupSuccessfully() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val createResult = backupService.createBackup("restore_test")
            val createdBackup = createResult.getOrThrow()

            val restoreResult = backupService.restoreBackup(createdBackup.id)

            assertTrue(restoreResult.isSuccess)
        }

        @Test
        @DisplayName("Should restore compressed backup")
        fun restoreCompressedBackup() = runTest {
            val backupConfig = createBackupConfig(compression = true)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val createResult = backupService.createBackup("compressed_restore")
            val createdBackup = createResult.getOrThrow()

            val restoreResult = backupService.restoreBackup(createdBackup.id)

            assertTrue(restoreResult.isSuccess)
        }

        @Test
        @DisplayName("Should restore uncompressed backup")
        fun restoreUncompressedBackup() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val createResult = backupService.createBackup("uncompressed_restore")
            val createdBackup = createResult.getOrThrow()

            val restoreResult = backupService.restoreBackup(createdBackup.id)

            assertTrue(restoreResult.isSuccess)
        }
    }

    // ============================================
    // Restore Backup That Doesn't Exist Tests
    // ============================================

    @Nested
    @DisplayName("Restore Backup That Doesn't Exist Fails")
    inner class RestoreNonExistentBackupTests {

        @Test
        @DisplayName("Should fail when restoring non-existent backup")
        fun failWhenRestoringNonExistentBackup() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            val result = backupService.restoreBackup("nonexistent123")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("Should return IllegalArgumentException for non-existent backup")
        fun returnIllegalArgumentExceptionForNonExistentBackup() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            val result = backupService.restoreBackup("invalid_id")

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception is IllegalArgumentException)
            assertTrue(exception!!.message!!.contains("Backup not found"))
        }

        @Test
        @DisplayName("Should fail with meaningful error message")
        fun failWithMeaningfulErrorMessage() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            val backupId = "test_missing_id"
            val result = backupService.restoreBackup(backupId)

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception!!.message!!.contains(backupId))
        }
    }

    // ============================================
    // Delete Backup Tests
    // ============================================

    @Nested
    @DisplayName("Delete Backup")
    inner class DeleteBackupTests {

        @Test
        @DisplayName("Should delete backup successfully")
        fun deleteBackupSuccessfully() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val createResult = backupService.createBackup("delete_test")
            val createdBackup = createResult.getOrThrow()

            val deleteResult = backupService.deleteBackup(createdBackup.id)

            assertTrue(deleteResult.isSuccess)
        }

        @Test
        @DisplayName("Deleted backup should no longer be retrievable")
        fun deletedBackupNotRetrievable() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val createResult = backupService.createBackup("delete_verify")
            val createdBackup = createResult.getOrThrow()

            backupService.deleteBackup(createdBackup.id)
            val retrievedBackup = backupService.getBackup(createdBackup.id)

            Assertions.assertNull(retrievedBackup)
        }

        @Test
        @DisplayName("Deleted backup should not appear in list")
        fun deletedBackupNotInList() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            backupService.createBackup("keep_one")
            val deleteResult = backupService.createBackup("delete_me")
            val toDelete = deleteResult.getOrThrow()
            backupService.createBackup("keep_two")

            backupService.deleteBackup(toDelete.id)
            val backups = backupService.listBackups()

            assertEquals(2, backups.size)
            assertFalse(backups.any { it.id == toDelete.id })
        }

        @Test
        @DisplayName("Should delete backup file from disk")
        fun deleteBackupFileFromDisk() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val createResult = backupService.createBackup("disk_delete")
            val createdBackup = createResult.getOrThrow()

            val backupDir = File(dataFolder, "backups")
            val filesBefore = backupDir.listFiles()?.size ?: 0
            assertEquals(1, filesBefore)

            backupService.deleteBackup(createdBackup.id)

            val filesAfter = backupDir.listFiles { f -> f.name.contains(createdBackup.id) }?.size ?: 0
            assertEquals(0, filesAfter)
        }
    }

    // ============================================
    // Delete Backup That Doesn't Exist Tests
    // ============================================

    @Nested
    @DisplayName("Delete Backup That Doesn't Exist Fails")
    inner class DeleteNonExistentBackupTests {

        @Test
        @DisplayName("Should fail when deleting non-existent backup")
        fun failWhenDeletingNonExistentBackup() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            val result = backupService.deleteBackup("nonexistent123")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("Should return IllegalArgumentException for non-existent backup")
        fun returnIllegalArgumentExceptionForNonExistentBackup() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            val result = backupService.deleteBackup("invalid_delete_id")

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception is IllegalArgumentException)
        }

        @Test
        @DisplayName("Should fail with meaningful error message")
        fun failWithMeaningfulErrorMessage() = runTest {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            val backupId = "missing_backup_id"
            val result = backupService.deleteBackup(backupId)

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception!!.message!!.contains("Backup not found") ||
                       exception.message!!.contains(backupId))
        }
    }

    // ============================================
    // Auto-cleanup Removes Old Backups Tests
    // ============================================

    @Nested
    @DisplayName("Auto-cleanup Removes Old Backups (max backups setting)")
    inner class AutoCleanupTests {

        @Test
        @DisplayName("Should remove oldest backups when exceeding max")
        fun removeOldestBackupsWhenExceedingMax() = runTest {
            val backupConfig = createBackupConfig(compression = false, maxBackups = 3)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            // Create more backups than max
            backupService.createBackup("backup_1")
            Thread.sleep(50)
            backupService.createBackup("backup_2")
            Thread.sleep(50)
            backupService.createBackup("backup_3")
            Thread.sleep(50)
            backupService.createBackup("backup_4")
            Thread.sleep(50)
            backupService.createBackup("backup_5")

            val backups = backupService.listBackups()

            // Should have exactly maxBackups
            assertEquals(3, backups.size)
        }

        @Test
        @DisplayName("Should keep most recent backups when cleaning up")
        fun keepMostRecentBackupsWhenCleaningUp() = runTest {
            val backupConfig = createBackupConfig(compression = false, maxBackups = 2)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            backupService.createBackup("old_backup")
            Thread.sleep(50)
            backupService.createBackup("newer_backup")
            Thread.sleep(50)
            val newestResult = backupService.createBackup("newest_backup")
            val newest = newestResult.getOrThrow()

            val backups = backupService.listBackups()

            assertEquals(2, backups.size)
            // Most recent should be kept
            assertTrue(backups.any { it.name == newest.name })
        }

        @Test
        @DisplayName("Should not cleanup when maxBackups is 0")
        fun noCleanupWhenMaxBackupsIsZero() = runTest {
            val backupConfig = createBackupConfig(compression = false, maxBackups = 0)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            // Create many backups
            repeat(5) { i ->
                backupService.createBackup("backup_$i")
                Thread.sleep(10)
            }

            val backups = backupService.listBackups()

            // All backups should be kept
            assertEquals(5, backups.size)
        }

        @Test
        @DisplayName("Should not cleanup when maxBackups is negative")
        fun noCleanupWhenMaxBackupsIsNegative() = runTest {
            val backupConfig = createBackupConfig(compression = false, maxBackups = -1)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            repeat(3) { i ->
                backupService.createBackup("backup_$i")
                Thread.sleep(10)
            }

            val backups = backupService.listBackups()

            assertEquals(3, backups.size)
        }
    }

    // ============================================
    // Backup Metadata Parsing Tests
    // ============================================

    @Nested
    @DisplayName("Backup Metadata Parsing")
    inner class BackupMetadataParsingTests {

        @Test
        @DisplayName("Should parse timestamp from backup metadata")
        fun parseTimestampFromBackupMetadata() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val beforeCreate = Instant.now()
            val result = backupService.createBackup("timestamp_test")
            val afterCreate = Instant.now()

            val metadata = result.getOrThrow()

            assertTrue(metadata.timestamp >= beforeCreate)
            assertTrue(metadata.timestamp <= afterCreate)
        }

        @Test
        @DisplayName("Should parse player count from backup metadata")
        fun parsePlayerCountFromBackupMetadata() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            val uuid1 = UUID.randomUUID()
            val uuid2 = UUID.randomUUID()

            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(uuid1, uuid2)
            coEvery { storageService.loadAllPlayerData(uuid1) } returns mapOf(
                "survival_SURVIVAL" to createTestPlayerData(uuid1, "survival", GameMode.SURVIVAL)
            )
            coEvery { storageService.loadAllPlayerData(uuid2) } returns mapOf(
                "survival_SURVIVAL" to createTestPlayerData(uuid2, "survival", GameMode.SURVIVAL)
            )

            val result = backupService.createBackup("player_count_test")
            val metadata = result.getOrThrow()

            assertEquals(2, metadata.playerCount)
        }

        @Test
        @DisplayName("Should parse size from backup metadata")
        fun parseSizeFromBackupMetadata() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("size_test")
            val metadata = result.getOrThrow()

            assertTrue(metadata.sizeBytes > 0)
        }

        @Test
        @DisplayName("Should parse storage type from backup metadata")
        fun parseStorageTypeFromBackupMetadata() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("storage_type_test")
            val metadata = result.getOrThrow()

            assertEquals(StorageType.YAML, metadata.storageType)
        }

        @Test
        @DisplayName("Should parse compression status from backup metadata")
        fun parseCompressionStatusFromBackupMetadata() = runTest {
            val compressedConfig = createBackupConfig(compression = true)
            backupService = setupBackupService(compressedConfig)
            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()
            val compressedResult = backupService.createBackup("compressed")
            assertTrue(compressedResult.getOrThrow().compressed)

            val uncompressedConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(uncompressedConfig)
            val uncompressedResult = backupService.createBackup("uncompressed")
            assertFalse(uncompressedResult.getOrThrow().compressed)
        }

        @Test
        @DisplayName("Should generate unique backup ID")
        fun generateUniqueBackupId() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result1 = backupService.createBackup("unique_1")
            val result2 = backupService.createBackup("unique_2")
            val result3 = backupService.createBackup("unique_3")

            val ids = setOf(
                result1.getOrThrow().id,
                result2.getOrThrow().id,
                result3.getOrThrow().id
            )

            assertEquals(3, ids.size) // All IDs should be unique
        }
    }

    // ============================================
    // Backup Directory Creation Tests
    // ============================================

    @Nested
    @DisplayName("Backup Directory Creation")
    inner class BackupDirectoryCreationTests {

        @Test
        @DisplayName("Should create backup directory on initialization")
        fun createBackupDirectoryOnInitialization() {
            val backupConfig = createBackupConfig(directory = "custom_backups")
            backupService = setupBackupService(backupConfig)

            val backupDir = File(dataFolder, "custom_backups")

            assertTrue(backupDir.exists())
            assertTrue(backupDir.isDirectory)
        }

        @Test
        @DisplayName("Should create nested backup directory")
        fun createNestedBackupDirectory() {
            val backupConfig = createBackupConfig(directory = "data/backups/daily")
            backupService = setupBackupService(backupConfig)

            val backupDir = File(dataFolder, "data/backups/daily")

            assertTrue(backupDir.exists())
            assertTrue(backupDir.isDirectory)
        }

        @Test
        @DisplayName("Should not fail if directory already exists")
        fun notFailIfDirectoryAlreadyExists() {
            val backupDir = File(dataFolder, "backups")
            backupDir.mkdirs()
            assertTrue(backupDir.exists())

            val backupConfig = createBackupConfig(directory = "backups")

            // Should not throw
            assertDoesNotThrow {
                backupService = setupBackupService(backupConfig)
            }
        }
    }

    // ============================================
    // Handle Backup Directory Not Existing Tests
    // ============================================

    @Nested
    @DisplayName("Handle Backup Directory Not Existing")
    inner class HandleMissingDirectoryTests {

        @Test
        @DisplayName("Should handle listing when directory doesn't exist")
        fun handleListingWhenDirectoryDoesNotExist() = runTest {
            val backupConfig = createBackupConfig(directory = "nonexistent_dir")
            backupService = setupBackupService(backupConfig)

            // Delete the directory after initialization to simulate it not existing
            val backupDir = File(dataFolder, "nonexistent_dir")
            backupDir.deleteRecursively()

            val backups = backupService.listBackups()

            assertTrue(backups.isEmpty())
        }

        @Test
        @DisplayName("Should create directory when creating backup")
        fun createDirectoryWhenCreatingBackup() = runTest {
            val backupConfig = createBackupConfig(compression = false, directory = "auto_created")
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("test")

            assertTrue(result.isSuccess)
            val backupDir = File(dataFolder, "auto_created")
            assertTrue(backupDir.exists())
        }
    }

    // ============================================
    // Large Backup Handling Tests
    // ============================================

    @Nested
    @DisplayName("Large Backup Handling (Many Players)")
    inner class LargeBackupHandlingTests {

        @Test
        @DisplayName("Should handle backup with many players")
        fun handleBackupWithManyPlayers() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            // Create 100 player UUIDs
            val playerUuids = (1..100).map { UUID.randomUUID() }.toSet()

            coEvery { storageService.getAllPlayerUUIDs() } returns playerUuids
            playerUuids.forEach { uuid ->
                coEvery { storageService.loadAllPlayerData(uuid) } returns mapOf(
                    "survival_SURVIVAL" to createTestPlayerData(uuid, "survival", GameMode.SURVIVAL)
                )
            }

            val result = backupService.createBackup("large_backup")

            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()
            assertEquals(100, metadata.playerCount)
        }

        @Test
        @DisplayName("Should handle backup with many entries per player")
        fun handleBackupWithManyEntriesPerPlayer() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            val uuid = UUID.randomUUID()

            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(uuid)

            // Create multiple inventory entries for one player
            val entries = mutableMapOf<String, PlayerData>()
            GameMode.entries.forEach { mode ->
                listOf("survival", "creative", "minigames", "skyblock", "factions").forEach { group ->
                    val key = "${group}_${mode.name}"
                    entries[key] = createTestPlayerData(uuid, group, mode)
                }
            }

            coEvery { storageService.loadAllPlayerData(uuid) } returns entries

            val result = backupService.createBackup("multi_entry_backup")

            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("Large compressed backup should be smaller than uncompressed")
        fun largeCompressedBackupSmallerThanUncompressed() = runTest {
            val playerUuids = (1..50).map { UUID.randomUUID() }.toSet()

            coEvery { storageService.getAllPlayerUUIDs() } returns playerUuids
            playerUuids.forEach { uuid ->
                coEvery { storageService.loadAllPlayerData(uuid) } returns mapOf(
                    "survival_SURVIVAL" to createTestPlayerData(uuid, "survival", GameMode.SURVIVAL),
                    "creative_CREATIVE" to createTestPlayerData(uuid, "creative", GameMode.CREATIVE)
                )
            }

            // Create uncompressed backup
            val uncompressedConfig = createBackupConfig(compression = false, maxBackups = 100)
            backupService = setupBackupService(uncompressedConfig)
            val uncompressedResult = backupService.createBackup("uncompressed_large")
            val uncompressedSize = uncompressedResult.getOrThrow().sizeBytes

            // Create compressed backup
            val compressedConfig = createBackupConfig(compression = true, maxBackups = 100)
            backupService = setupBackupService(compressedConfig)
            val compressedResult = backupService.createBackup("compressed_large")
            val compressedSize = compressedResult.getOrThrow().sizeBytes

            // Compressed should be smaller
            assertTrue(compressedSize < uncompressedSize,
                "Compressed size ($compressedSize) should be less than uncompressed ($uncompressedSize)")
        }

        @Test
        @DisplayName("Should handle players with empty data")
        fun handlePlayersWithEmptyData() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            val uuids = (1..10).map { UUID.randomUUID() }.toSet()

            coEvery { storageService.getAllPlayerUUIDs() } returns uuids
            // Some players have data, some don't
            uuids.forEachIndexed { index, uuid ->
                if (index % 2 == 0) {
                    coEvery { storageService.loadAllPlayerData(uuid) } returns mapOf(
                        "survival_SURVIVAL" to createTestPlayerData(uuid, "survival", GameMode.SURVIVAL)
                    )
                } else {
                    coEvery { storageService.loadAllPlayerData(uuid) } returns emptyMap()
                }
            }

            val result = backupService.createBackup("mixed_data")

            assertTrue(result.isSuccess)
            val metadata = result.getOrThrow()
            assertEquals(5, metadata.playerCount) // Only half have data
        }
    }

    // ============================================
    // Backup Service Lifecycle Tests
    // ============================================

    @Nested
    @DisplayName("Backup Service Lifecycle")
    inner class BackupServiceLifecycleTests {

        @Test
        @DisplayName("Should initialize without auto-backup when disabled")
        fun initializeWithoutAutoBackupWhenDisabled() {
            val backupConfig = createBackupConfig(autoBackup = false)

            assertDoesNotThrow {
                backupService = setupBackupService(backupConfig)
            }
        }

        @Test
        @DisplayName("Should shutdown gracefully")
        fun shutdownGracefully() {
            val backupConfig = createBackupConfig()
            backupService = setupBackupService(backupConfig)

            assertDoesNotThrow {
                backupService.shutdown()
            }
        }

        @Test
        @DisplayName("Should be able to create backup after initialization")
        fun createBackupAfterInitialization() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup()

            assertTrue(result.isSuccess)
        }
    }

    // ============================================
    // Backup Content Verification Tests
    // ============================================

    @Nested
    @DisplayName("Backup Content Verification")
    inner class BackupContentVerificationTests {

        @Test
        @DisplayName("Backup file should contain header comment")
        fun backupFileContainsHeaderComment() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("header_test")
            val metadata = result.getOrThrow()

            val backupDir = File(dataFolder, "backups")
            val backupFile = backupDir.listFiles { f -> f.name.contains(metadata.id) }!![0]
            val content = backupFile.readText()

            assertTrue(content.startsWith("# xInventories Backup"))
            assertTrue(content.contains("# Created:"))
            assertTrue(content.contains("# Storage Type:"))
        }

        @Test
        @DisplayName("Backup file should contain backup info section")
        fun backupFileContainsBackupInfoSection() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("info_test")
            val metadata = result.getOrThrow()

            val backupDir = File(dataFolder, "backups")
            val backupFile = backupDir.listFiles { f -> f.name.contains(metadata.id) }!![0]
            val content = backupFile.readText()

            assertTrue(content.contains("backup-info:"))
            assertTrue(content.contains("id: ${metadata.id}"))
            assertTrue(content.contains("name: info_test"))
            assertTrue(content.contains("timestamp:"))
            assertTrue(content.contains("storage-type:"))
        }

        @Test
        @DisplayName("Backup file should contain players section")
        fun backupFileContainsPlayersSection() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("players_section_test")
            val metadata = result.getOrThrow()

            val backupDir = File(dataFolder, "backups")
            val backupFile = backupDir.listFiles { f -> f.name.contains(metadata.id) }!![0]
            val content = backupFile.readText()

            assertTrue(content.contains("players:"))
        }

        @Test
        @DisplayName("Backup file should contain player data when players exist")
        fun backupFileContainsPlayerData() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            val uuid = UUID.randomUUID()

            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(uuid)
            coEvery { storageService.loadAllPlayerData(uuid) } returns mapOf(
                "survival_SURVIVAL" to createTestPlayerData(uuid, "survival", GameMode.SURVIVAL)
            )

            val result = backupService.createBackup("player_data_test")
            val metadata = result.getOrThrow()

            val backupDir = File(dataFolder, "backups")
            val backupFile = backupDir.listFiles { f -> f.name.contains(metadata.id) }!![0]
            val content = backupFile.readText()

            assertTrue(content.contains(uuid.toString()))
            assertTrue(content.contains("survival_SURVIVAL:"))
        }
    }

    // ============================================
    // Edge Cases Tests
    // ============================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle empty player UUID set")
        fun handleEmptyPlayerUuidSet() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("empty_players")

            assertTrue(result.isSuccess)
            assertEquals(0, result.getOrThrow().playerCount)
        }

        @Test
        @DisplayName("Should handle special characters in backup name")
        fun handleSpecialCharactersInBackupName() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = backupService.createBackup("test-backup_v1.0")

            assertTrue(result.isSuccess)
            assertEquals("test-backup_v1.0", result.getOrThrow().name)
        }

        @Test
        @DisplayName("Should handle very long backup name")
        fun handleVeryLongBackupName() = runTest {
            val backupConfig = createBackupConfig(compression = false)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val longName = "a".repeat(100)
            val result = backupService.createBackup(longName)

            assertTrue(result.isSuccess)
            assertEquals(longName, result.getOrThrow().name)
        }

        @Test
        @DisplayName("Should handle multiple rapid backup creations")
        fun handleMultipleRapidBackupCreations() = runTest {
            val backupConfig = createBackupConfig(compression = false, maxBackups = 100)
            backupService = setupBackupService(backupConfig)

            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val results = (1..10).map { i ->
                backupService.createBackup("rapid_$i")
            }

            assertTrue(results.all { it.isSuccess })
            val ids = results.map { it.getOrThrow().id }.toSet()
            assertEquals(10, ids.size) // All IDs should be unique
        }
    }
}

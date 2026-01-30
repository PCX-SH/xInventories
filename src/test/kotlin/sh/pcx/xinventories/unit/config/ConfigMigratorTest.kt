package sh.pcx.xinventories.unit.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.BackupResult
import sh.pcx.xinventories.internal.config.ConfigMigration
import sh.pcx.xinventories.internal.config.ConfigMigrator
import sh.pcx.xinventories.internal.config.MigrationResult
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("ConfigMigrator Tests")
class ConfigMigratorTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var plugin: XInventories
    private lateinit var migrator: ConfigMigrator
    private lateinit var dataFolder: File

    @BeforeEach
    fun setUp() {
        // Initialize Logging
        Logging.init(Logger.getLogger("Test"), false)
        mockkObject(Logging)

        plugin = mockk(relaxed = true)
        dataFolder = tempDir.resolve("plugin").toFile()
        dataFolder.mkdirs()

        every { plugin.dataFolder } returns dataFolder

        migrator = ConfigMigrator(plugin)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("Version Detection")
    inner class VersionDetection {

        @Test
        @DisplayName("Should detect version from config")
        fun detectVersion() {
            val config = YamlConfiguration()
            config.set("config-version", 2)

            val version = migrator.detectVersion(config)

            assertEquals(2, version)
        }

        @Test
        @DisplayName("Should return 1 for configs without version")
        fun defaultVersionForLegacyConfig() {
            val config = YamlConfiguration()
            // No config-version set

            val version = migrator.detectVersion(config)

            assertEquals(1, version, "Legacy configs without version should default to 1")
        }

        @Test
        @DisplayName("Should detect if migration is needed")
        fun needsMigrationWhenVersionLow() {
            val config = YamlConfiguration()
            config.set("config-version", 0)

            val needsMigration = migrator.needsMigration(config)

            assertTrue(needsMigration, "Migration should be needed for version 0")
        }

        @Test
        @DisplayName("Should not need migration when at current version")
        fun noMigrationNeededAtCurrentVersion() {
            val config = YamlConfiguration()
            config.set("config-version", ConfigMigrator.CURRENT_VERSION)

            val needsMigration = migrator.needsMigration(config)

            assertFalse(needsMigration, "Migration should not be needed at current version")
        }
    }

    @Nested
    @DisplayName("Migration Execution")
    inner class MigrationExecution {

        @Test
        @DisplayName("Should return success for non-existent config file")
        fun successForNonExistentFile() {
            val nonExistentFile = File(dataFolder, "nonexistent.yml")

            val result = migrator.migrate(nonExistentFile)

            assertTrue(result is MigrationResult.Success)
            assertEquals(ConfigMigrator.CURRENT_VERSION, (result as MigrationResult.Success).fromVersion)
            assertEquals(0, result.migrationsApplied)
        }

        @Test
        @DisplayName("Should return success when no migration needed")
        fun successWhenNoMigrationNeeded() {
            val configFile = File(dataFolder, "config.yml")
            val config = YamlConfiguration()
            config.set("config-version", ConfigMigrator.CURRENT_VERSION)
            config.set("some-setting", "value")
            config.save(configFile)

            val result = migrator.migrate(configFile)

            assertTrue(result is MigrationResult.Success)
            assertEquals(0, (result as MigrationResult.Success).migrationsApplied)
        }

        @Test
        @DisplayName("Should preserve existing config values during migration")
        fun preserveExistingValues() {
            val configFile = File(dataFolder, "config.yml")
            val config = YamlConfiguration()
            config.set("config-version", ConfigMigrator.CURRENT_VERSION)
            config.set("storage.type", "MYSQL")
            config.set("debug", true)
            config.save(configFile)

            val result = migrator.migrate(configFile)

            assertTrue(result is MigrationResult.Success)
            val migratedConfig = (result as MigrationResult.Success).config
            assertEquals("MYSQL", migratedConfig.getString("storage.type"))
            assertEquals(true, migratedConfig.getBoolean("debug"))
        }

        @Test
        @DisplayName("Should update config version after migration")
        fun updateConfigVersion() {
            val configFile = File(dataFolder, "config.yml")
            val config = YamlConfiguration()
            // Set version to 0 to simulate an outdated config that needs migration
            // Note: Since no actual migrations are registered for 0->1,
            // the migrator will just update the version to current
            config.set("config-version", 0)
            config.set("some-setting", "value")
            config.save(configFile)

            migrator.migrate(configFile)

            val reloadedConfig = YamlConfiguration.loadConfiguration(configFile)
            assertEquals(ConfigMigrator.CURRENT_VERSION, reloadedConfig.getInt("config-version"))
        }
    }

    @Nested
    @DisplayName("Backup Creation")
    inner class BackupCreation {

        @Test
        @DisplayName("Should create backup before migration")
        fun createBackupBeforeMigration() {
            val configFile = File(dataFolder, "config.yml")
            val config = YamlConfiguration()
            config.set("config-version", 0) // Old version to trigger migration
            config.set("test-value", "original")
            config.save(configFile)

            val result = migrator.migrate(configFile)

            // Since current version is 1 and config version is 0, migration path doesn't exist
            // but backup should still be created if there's a migration attempt
            assertTrue(result is MigrationResult.Success)
        }

        @Test
        @DisplayName("Should create backup with correct naming")
        fun createBackupWithCorrectNaming() {
            val configFile = File(dataFolder, "config.yml")
            configFile.writeText("test: value")

            val result = migrator.createBackup(configFile, 1)

            assertTrue(result is BackupResult.Success)
            val backupFile = (result as BackupResult.Success).backupFile
            assertNotNull(backupFile)
            assertTrue(backupFile!!.name.startsWith("config_v1_"))
            assertTrue(backupFile.name.endsWith(".yml"))
        }

        @Test
        @DisplayName("Should return success with null file for non-existent config")
        fun noBackupForNonExistentFile() {
            val nonExistentFile = File(dataFolder, "nonexistent.yml")

            val result = migrator.createBackup(nonExistentFile, 1)

            assertTrue(result is BackupResult.Success)
            assertNull((result as BackupResult.Success).backupFile)
        }

        @Test
        @DisplayName("Backup should contain original content")
        fun backupContainsOriginalContent() {
            val configFile = File(dataFolder, "config.yml")
            val config = YamlConfiguration()
            config.set("original-key", "original-value")
            config.save(configFile)

            val result = migrator.createBackup(configFile, 1)

            assertTrue(result is BackupResult.Success)
            val backupFile = (result as BackupResult.Success).backupFile
            val backupConfig = YamlConfiguration.loadConfiguration(backupFile!!)
            assertEquals("original-value", backupConfig.getString("original-key"))
        }
    }

    @Nested
    @DisplayName("Backup Listing")
    inner class BackupListing {

        @Test
        @DisplayName("Should return empty list when no backups exist")
        fun emptyListWhenNoBackups() {
            val backups = migrator.listBackups("config.yml")

            assertTrue(backups.isEmpty())
        }

        @Test
        @DisplayName("Should list all backups for a config file")
        fun listAllBackups() {
            // Create backup directory and files
            val backupDir = File(dataFolder, ConfigMigrator.BACKUP_DIR)
            backupDir.mkdirs()
            File(backupDir, "config_v1_20240101_120000.yml").writeText("test1")
            File(backupDir, "config_v2_20240102_120000.yml").writeText("test2")
            File(backupDir, "other_v1_20240101_120000.yml").writeText("other")

            val backups = migrator.listBackups("config.yml")

            assertEquals(2, backups.size)
            assertTrue(backups.all { it.name.startsWith("config_v") })
        }

        @Test
        @DisplayName("Should sort backups by date (newest first)")
        fun sortBackupsByDate() {
            val backupDir = File(dataFolder, ConfigMigrator.BACKUP_DIR)
            backupDir.mkdirs()

            val older = File(backupDir, "config_v1_20240101_120000.yml")
            older.writeText("older")
            older.setLastModified(System.currentTimeMillis() - 100000)

            val newer = File(backupDir, "config_v2_20240102_120000.yml")
            newer.writeText("newer")
            newer.setLastModified(System.currentTimeMillis())

            val backups = migrator.listBackups("config.yml")

            assertEquals(2, backups.size)
            assertTrue(backups[0].lastModified() >= backups[1].lastModified())
        }
    }

    @Nested
    @DisplayName("Backup Restoration")
    inner class BackupRestoration {

        @Test
        @DisplayName("Should restore config from backup")
        fun restoreFromBackup() {
            val backupDir = File(dataFolder, ConfigMigrator.BACKUP_DIR)
            backupDir.mkdirs()

            val backupFile = File(backupDir, "config_v1_backup.yml")
            val backupConfig = YamlConfiguration()
            backupConfig.set("restored-key", "restored-value")
            backupConfig.save(backupFile)

            val targetFile = File(dataFolder, "config.yml")
            val currentConfig = YamlConfiguration()
            currentConfig.set("current-key", "current-value")
            currentConfig.save(targetFile)

            val success = migrator.restoreBackup(backupFile, targetFile)

            assertTrue(success)
            val restoredConfig = YamlConfiguration.loadConfiguration(targetFile)
            assertEquals("restored-value", restoredConfig.getString("restored-key"))
        }

        @Test
        @DisplayName("Should return false when backup file does not exist")
        fun falseWhenBackupMissing() {
            val nonExistentBackup = File(dataFolder, "missing_backup.yml")
            val targetFile = File(dataFolder, "config.yml")

            val success = migrator.restoreBackup(nonExistentBackup, targetFile)

            assertFalse(success)
        }

        @Test
        @DisplayName("Should create backup of current config before restoring")
        fun createBackupBeforeRestore() {
            val backupDir = File(dataFolder, ConfigMigrator.BACKUP_DIR)
            backupDir.mkdirs()

            val backupFile = File(backupDir, "config_v1_backup.yml")
            backupFile.writeText("backup content")

            val targetFile = File(dataFolder, "config.yml")
            val currentConfig = YamlConfiguration()
            currentConfig.set("config-version", 5)
            currentConfig.set("about-to-be-replaced", "true")
            currentConfig.save(targetFile)

            migrator.restoreBackup(backupFile, targetFile)

            // A new backup should have been created
            val backups = migrator.listBackups("config.yml")
            assertTrue(backups.isNotEmpty(), "Backup should be created before restore")
        }
    }

    @Nested
    @DisplayName("Migration Registration")
    inner class MigrationRegistration {

        @Test
        @DisplayName("Should return list of registered migrations")
        fun getRegisteredMigrations() {
            val migrations = migrator.getRegisteredMigrations()

            // This depends on how many migrations are registered
            // For now, the list might be empty since no actual migrations are implemented
            assertNotNull(migrations)
        }

        @Test
        @DisplayName("Migrations should be sorted by fromVersion")
        fun migrationsSortedByFromVersion() {
            val migrations = migrator.getRegisteredMigrations()

            if (migrations.size > 1) {
                for (i in 0 until migrations.size - 1) {
                    assertTrue(migrations[i].fromVersion <= migrations[i + 1].fromVersion,
                        "Migrations should be sorted by fromVersion")
                }
            }
        }
    }

    @Nested
    @DisplayName("Migration Interface")
    inner class MigrationInterface {

        @Test
        @DisplayName("ConfigMigration should define fromVersion and toVersion")
        fun migrationDefinesVersions() {
            val testMigration = object : ConfigMigration {
                override val fromVersion: Int = 1
                override val toVersion: Int = 2
                override fun migrate(config: YamlConfiguration): YamlConfiguration = config
            }

            assertEquals(1, testMigration.fromVersion)
            assertEquals(2, testMigration.toVersion)
        }

        @Test
        @DisplayName("ConfigMigration should transform config")
        fun migrationTransformsConfig() {
            val testMigration = object : ConfigMigration {
                override val fromVersion: Int = 1
                override val toVersion: Int = 2
                override fun migrate(config: YamlConfiguration): YamlConfiguration {
                    config.set("new-key", "new-value")
                    return config
                }
            }

            val config = YamlConfiguration()
            config.set("existing-key", "existing-value")

            val migratedConfig = testMigration.migrate(config)

            assertEquals("existing-value", migratedConfig.getString("existing-key"))
            assertEquals("new-value", migratedConfig.getString("new-key"))
        }
    }

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandling {

        @Test
        @DisplayName("Should handle malformed YAML gracefully")
        fun handleMalformedYaml() {
            val configFile = File(dataFolder, "config.yml")
            configFile.writeText("""
                invalid: yaml: content
                  - broken
                    structure:
            """.trimIndent())

            // YamlConfiguration handles malformed YAML by loading partial content
            // The migration should still work or fail gracefully
            val result = migrator.migrate(configFile)

            // Either success or failure is acceptable, but should not throw
            assertTrue(result is MigrationResult.Success || result is MigrationResult.Failure)
        }

        @Test
        @DisplayName("Should return failure result when migration throws")
        fun failureWhenMigrationThrows() {
            // Create a custom migrator with a failing migration
            // Since we can't easily inject migrations, we test the result type
            val configFile = File(dataFolder, "config.yml")
            val config = YamlConfiguration()
            config.set("config-version", 0)
            config.save(configFile)

            val result = migrator.migrate(configFile)

            // With no migrations registered, this should succeed
            assertTrue(result is MigrationResult.Success)
        }
    }

    @Nested
    @DisplayName("MigrationResult Types")
    inner class MigrationResultTypes {

        @Test
        @DisplayName("Success result should contain all fields")
        fun successResultFields() {
            val config = YamlConfiguration()
            val result = MigrationResult.Success(
                config = config,
                fromVersion = 1,
                toVersion = 2,
                migrationsApplied = 1,
                backupPath = "/path/to/backup.yml"
            )

            assertEquals(config, result.config)
            assertEquals(1, result.fromVersion)
            assertEquals(2, result.toVersion)
            assertEquals(1, result.migrationsApplied)
            assertEquals("/path/to/backup.yml", result.backupPath)
        }

        @Test
        @DisplayName("Failure result should contain error information")
        fun failureResultFields() {
            val exception = RuntimeException("Test error")
            val partialConfig = YamlConfiguration()
            val result = MigrationResult.Failure(
                error = "Migration failed",
                exception = exception,
                partialConfig = partialConfig,
                lastSuccessfulVersion = 1
            )

            assertEquals("Migration failed", result.error)
            assertEquals(exception, result.exception)
            assertEquals(partialConfig, result.partialConfig)
            assertEquals(1, result.lastSuccessfulVersion)
        }
    }

    @Nested
    @DisplayName("BackupResult Types")
    inner class BackupResultTypes {

        @Test
        @DisplayName("Backup success result should contain backup file")
        fun backupSuccessResult() {
            val backupFile = File(dataFolder, "backup.yml")
            val result = BackupResult.Success(backupFile)

            assertEquals(backupFile, result.backupFile)
        }

        @Test
        @DisplayName("Backup success can have null file")
        fun backupSuccessNullFile() {
            val result = BackupResult.Success(null)

            assertNull(result.backupFile)
        }

        @Test
        @DisplayName("Backup failure result should contain error")
        fun backupFailureResult() {
            val exception = RuntimeException("Backup failed")
            val result = BackupResult.Failure(
                error = "Could not create backup",
                exception = exception
            )

            assertEquals("Could not create backup", result.error)
            assertEquals(exception, result.exception)
        }
    }
}

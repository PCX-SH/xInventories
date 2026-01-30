package sh.pcx.xinventories.internal.config

import org.bukkit.configuration.file.YamlConfiguration
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.migrations.Migration_1_to_2
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Interface for configuration migrations.
 *
 * Implementations define how to migrate from one config version to another.
 */
interface ConfigMigration {
    /**
     * The config version this migration starts from.
     */
    val fromVersion: Int

    /**
     * The config version this migration results in.
     */
    val toVersion: Int

    /**
     * Performs the migration on the given configuration.
     *
     * @param config The configuration to migrate
     * @return The migrated configuration
     */
    fun migrate(config: YamlConfiguration): YamlConfiguration
}

/**
 * Handles automatic configuration migrations when the plugin updates.
 *
 * This class detects the current config version, backs up the old config,
 * and applies sequential migrations to bring the config up to date.
 */
class ConfigMigrator(private val plugin: XInventories) {

    companion object {
        /**
         * The current configuration version.
         * Increment this when making breaking config changes.
         */
        const val CURRENT_VERSION = 1

        /**
         * The config key that stores the version number.
         */
        const val VERSION_KEY = "config-version"

        /**
         * Directory name for config backups.
         */
        const val BACKUP_DIR = "config-backups"
    }

    /**
     * Registry of all available migrations, sorted by fromVersion.
     */
    private val migrations: List<ConfigMigration> = listOf<ConfigMigration>(
        // Add migrations here as they are created
        // Migration_1_to_2
    ).sortedBy { it.fromVersion }

    /**
     * Detects the version of a configuration file.
     *
     * @param config The configuration to check
     * @return The config version, or 1 if not specified (legacy configs)
     */
    fun detectVersion(config: YamlConfiguration): Int {
        return config.getInt(VERSION_KEY, 1)
    }

    /**
     * Checks if the config needs migration.
     *
     * @param config The configuration to check
     * @return true if migration is needed
     */
    fun needsMigration(config: YamlConfiguration): Boolean {
        val currentVersion = detectVersion(config)
        return currentVersion < CURRENT_VERSION
    }

    /**
     * Migrates a configuration file to the current version.
     *
     * @param configFile The configuration file to migrate
     * @return MigrationResult containing the migrated config or error information
     */
    fun migrate(configFile: File): MigrationResult {
        if (!configFile.exists()) {
            return MigrationResult.Success(
                YamlConfiguration(),
                fromVersion = CURRENT_VERSION,
                toVersion = CURRENT_VERSION,
                migrationsApplied = 0
            )
        }

        val config = try {
            YamlConfiguration.loadConfiguration(configFile)
        } catch (e: Exception) {
            Logging.error("Failed to load config for migration: ${configFile.name}", e)
            return MigrationResult.Failure(
                error = "Failed to load configuration: ${e.message}",
                exception = e
            )
        }

        val startVersion = detectVersion(config)

        if (startVersion >= CURRENT_VERSION) {
            Logging.debug { "Config ${configFile.name} is already at version $startVersion, no migration needed" }
            return MigrationResult.Success(
                config = config,
                fromVersion = startVersion,
                toVersion = startVersion,
                migrationsApplied = 0
            )
        }

        // Create backup before migration
        val backupResult = createBackup(configFile, startVersion)
        if (backupResult is BackupResult.Failure) {
            return MigrationResult.Failure(
                error = "Failed to create backup: ${backupResult.error}",
                exception = backupResult.exception
            )
        }

        Logging.info("Migrating ${configFile.name} from version $startVersion to $CURRENT_VERSION")

        // Apply migrations sequentially
        var currentConfig = config
        var currentVersion = startVersion
        var migrationsApplied = 0

        val applicableMigrations = migrations.filter {
            it.fromVersion >= startVersion && it.toVersion <= CURRENT_VERSION
        }.sortedBy { it.fromVersion }

        for (migration in applicableMigrations) {
            if (migration.fromVersion != currentVersion) {
                continue
            }

            try {
                Logging.debug { "Applying migration from version ${migration.fromVersion} to ${migration.toVersion}" }
                currentConfig = migration.migrate(currentConfig)
                currentVersion = migration.toVersion
                migrationsApplied++
                Logging.info("Applied migration: ${migration.fromVersion} -> ${migration.toVersion}")
            } catch (e: Exception) {
                Logging.error("Migration failed at version ${migration.fromVersion} -> ${migration.toVersion}", e)
                return MigrationResult.Failure(
                    error = "Migration failed at version ${migration.fromVersion}: ${e.message}",
                    exception = e,
                    partialConfig = currentConfig,
                    lastSuccessfulVersion = currentVersion
                )
            }
        }

        // Update version in config
        currentConfig.set(VERSION_KEY, CURRENT_VERSION)

        // Save migrated config
        try {
            currentConfig.save(configFile)
            Logging.info("Successfully migrated ${configFile.name} to version $CURRENT_VERSION ($migrationsApplied migrations applied)")
        } catch (e: Exception) {
            Logging.error("Failed to save migrated config", e)
            return MigrationResult.Failure(
                error = "Failed to save migrated configuration: ${e.message}",
                exception = e,
                partialConfig = currentConfig,
                lastSuccessfulVersion = currentVersion
            )
        }

        return MigrationResult.Success(
            config = currentConfig,
            fromVersion = startVersion,
            toVersion = CURRENT_VERSION,
            migrationsApplied = migrationsApplied,
            backupPath = (backupResult as? BackupResult.Success)?.backupFile?.absolutePath
        )
    }

    /**
     * Creates a backup of the configuration file before migration.
     *
     * @param configFile The file to backup
     * @param version The current version being backed up
     * @return BackupResult indicating success or failure
     */
    fun createBackup(configFile: File, version: Int): BackupResult {
        if (!configFile.exists()) {
            return BackupResult.Success(null)
        }

        val backupDir = File(plugin.dataFolder, BACKUP_DIR)
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            return BackupResult.Failure(
                error = "Failed to create backup directory: ${backupDir.absolutePath}"
            )
        }

        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupName = "${configFile.nameWithoutExtension}_v${version}_$timestamp.yml"
        val backupFile = File(backupDir, backupName)

        return try {
            configFile.copyTo(backupFile, overwrite = false)
            Logging.info("Created config backup: ${backupFile.name}")
            BackupResult.Success(backupFile)
        } catch (e: Exception) {
            Logging.error("Failed to create config backup", e)
            BackupResult.Failure(
                error = "Failed to create backup: ${e.message}",
                exception = e
            )
        }
    }

    /**
     * Lists all available backups for a config file.
     *
     * @param configFileName The name of the config file (e.g., "config.yml")
     * @return List of backup files, sorted by date (newest first)
     */
    fun listBackups(configFileName: String): List<File> {
        val backupDir = File(plugin.dataFolder, BACKUP_DIR)
        if (!backupDir.exists()) {
            return emptyList()
        }

        val baseName = configFileName.substringBeforeLast(".")
        return backupDir.listFiles { file ->
            file.name.startsWith("${baseName}_v") && file.name.endsWith(".yml")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Restores a configuration from a backup file.
     *
     * @param backupFile The backup file to restore from
     * @param targetFile The target config file to restore to
     * @return true if restoration was successful
     */
    fun restoreBackup(backupFile: File, targetFile: File): Boolean {
        if (!backupFile.exists()) {
            Logging.warning("Backup file does not exist: ${backupFile.absolutePath}")
            return false
        }

        return try {
            // Create a backup of the current config before restoring
            if (targetFile.exists()) {
                val currentVersion = detectVersion(YamlConfiguration.loadConfiguration(targetFile))
                createBackup(targetFile, currentVersion)
            }

            backupFile.copyTo(targetFile, overwrite = true)
            Logging.info("Restored config from backup: ${backupFile.name}")
            true
        } catch (e: Exception) {
            Logging.error("Failed to restore backup", e)
            false
        }
    }

    /**
     * Gets the list of registered migrations.
     *
     * @return List of available migrations
     */
    fun getRegisteredMigrations(): List<ConfigMigration> = migrations.toList()
}

/**
 * Result of a migration operation.
 */
sealed class MigrationResult {
    /**
     * Migration completed successfully.
     */
    data class Success(
        val config: YamlConfiguration,
        val fromVersion: Int,
        val toVersion: Int,
        val migrationsApplied: Int,
        val backupPath: String? = null
    ) : MigrationResult()

    /**
     * Migration failed.
     */
    data class Failure(
        val error: String,
        val exception: Exception? = null,
        val partialConfig: YamlConfiguration? = null,
        val lastSuccessfulVersion: Int? = null
    ) : MigrationResult()
}

/**
 * Result of a backup operation.
 */
sealed class BackupResult {
    /**
     * Backup created successfully.
     */
    data class Success(val backupFile: File?) : BackupResult()

    /**
     * Backup failed.
     */
    data class Failure(
        val error: String,
        val exception: Exception? = null
    ) : BackupResult()
}

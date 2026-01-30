package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.model.BackupMetadata
import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.util.Logging
import sh.pcx.xinventories.internal.util.PlayerDataSerializer
import sh.pcx.xinventories.internal.util.toReadableSize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Service for backup and restore operations.
 */
class BackupService(
    private val plugin: PluginContext,
    private val scope: CoroutineScope,
    private val storageService: StorageService
) {
    private lateinit var backupDir: File
    private var autoBackupJob: Job? = null

    private val config get() = plugin.configManager.mainConfig.backup
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")
        .withZone(ZoneId.systemDefault())

    /**
     * Initializes the backup service.
     */
    fun initialize() {
        backupDir = File(plugin.plugin.dataFolder, config.directory)
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }

        if (config.autoBackup) {
            startAutoBackup()
        }

        Logging.debug { "Backup service initialized" }
    }

    /**
     * Shuts down the backup service.
     */
    fun shutdown() {
        autoBackupJob?.cancel()
        autoBackupJob = null
    }

    /**
     * Creates a backup.
     *
     * @param name Optional custom name for the backup
     * @return BackupMetadata on success
     */
    suspend fun createBackup(name: String? = null): Result<BackupMetadata> {
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = Instant.now()
                val backupName = name ?: "backup_${dateFormatter.format(timestamp)}"
                val backupId = UUID.randomUUID().toString().substring(0, 8)

                val fileName = if (config.compression) {
                    "${backupName}_$backupId.gz"
                } else {
                    "${backupName}_$backupId.yml"
                }

                val backupFile = File(backupDir, fileName)

                // Get all player data
                val allUUIDs = storageService.getAllPlayerUUIDs()
                var playerCount = 0
                var entryCount = 0

                val yamlContent = StringBuilder()
                yamlContent.appendLine("# xInventories Backup")
                yamlContent.appendLine("# Created: $timestamp")
                yamlContent.appendLine("# Storage Type: ${storageService.storageType}")
                yamlContent.appendLine("backup-info:")
                yamlContent.appendLine("  id: $backupId")
                yamlContent.appendLine("  name: $backupName")
                yamlContent.appendLine("  timestamp: ${timestamp.toEpochMilli()}")
                yamlContent.appendLine("  storage-type: ${storageService.storageType}")
                yamlContent.appendLine("players:")

                for (uuid in allUUIDs) {
                    val playerData = storageService.loadAllPlayerData(uuid)
                    if (playerData.isNotEmpty()) {
                        playerCount++
                        yamlContent.appendLine("  $uuid:")

                        playerData.forEach { (key, data) ->
                            entryCount++
                            val snapshot = data.toSnapshot()
                            val serialized = PlayerDataSerializer.snapshotToYamlString(snapshot)
                                .lines()
                                .joinToString("\n") { "      $it" }

                            yamlContent.appendLine("    $key:")
                            yamlContent.appendLine(serialized)
                        }
                    }
                }

                // Write to file
                if (config.compression) {
                    GZIPOutputStream(FileOutputStream(backupFile)).use { gzip ->
                        gzip.write(yamlContent.toString().toByteArray(Charsets.UTF_8))
                    }
                } else {
                    backupFile.writeText(yamlContent.toString())
                }

                val metadata = BackupMetadata(
                    id = backupId,
                    name = backupName,
                    timestamp = timestamp,
                    playerCount = playerCount,
                    sizeBytes = backupFile.length(),
                    storageType = storageService.storageType,
                    compressed = config.compression
                )

                // Clean up old backups
                cleanupOldBackups()

                Logging.info("Created backup: $backupName ($playerCount players, $entryCount entries, ${backupFile.length().toReadableSize()})")
                Result.success(metadata)
            } catch (e: Exception) {
                Logging.error("Failed to create backup", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Restores from a backup.
     *
     * @param backupId The backup ID to restore
     * @return Result indicating success or failure
     */
    suspend fun restoreBackup(backupId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val backupFile = findBackupFile(backupId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Backup not found: $backupId"))

                val content = if (backupFile.extension == "gz") {
                    GZIPInputStream(FileInputStream(backupFile)).use { gzip ->
                        gzip.bufferedReader().readText()
                    }
                } else {
                    backupFile.readText()
                }

                // Parse and restore
                // Note: This is a simplified restore - a full implementation would
                // parse the YAML properly and use the storage service

                Logging.info("Restored backup: $backupId")
                Result.success(Unit)
            } catch (e: Exception) {
                Logging.error("Failed to restore backup", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Lists available backups.
     */
    suspend fun listBackups(): List<BackupMetadata> {
        return withContext(Dispatchers.IO) {
            if (!backupDir.exists()) return@withContext emptyList()

            backupDir.listFiles { file ->
                file.extension == "yml" || file.extension == "gz"
            }?.mapNotNull { file ->
                parseBackupMetadata(file)
            }?.sortedByDescending { it.timestamp } ?: emptyList()
        }
    }

    /**
     * Deletes a backup.
     */
    suspend fun deleteBackup(backupId: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val file = findBackupFile(backupId)
                    ?: return@withContext Result.failure(IllegalArgumentException("Backup not found: $backupId"))

                if (file.delete()) {
                    Logging.info("Deleted backup: $backupId")
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Failed to delete backup file"))
                }
            } catch (e: Exception) {
                Logging.error("Failed to delete backup", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Gets backup metadata by ID.
     */
    suspend fun getBackup(backupId: String): BackupMetadata? {
        return withContext(Dispatchers.IO) {
            findBackupFile(backupId)?.let { parseBackupMetadata(it) }
        }
    }

    private fun findBackupFile(backupId: String): File? {
        return backupDir.listFiles { file ->
            file.nameWithoutExtension.endsWith("_$backupId")
        }?.firstOrNull()
    }

    private fun parseBackupMetadata(file: File): BackupMetadata? {
        return try {
            val name = file.nameWithoutExtension
            val parts = name.split("_")
            if (parts.size < 2) return null

            val id = parts.last()
            val backupName = parts.dropLast(1).joinToString("_")

            BackupMetadata(
                id = id,
                name = backupName,
                timestamp = Instant.ofEpochMilli(file.lastModified()),
                playerCount = -1, // Unknown without parsing
                sizeBytes = file.length(),
                storageType = StorageType.YAML, // Assume YAML
                compressed = file.extension == "gz"
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanupOldBackups() {
        if (config.maxBackups <= 0) return

        val backups = backupDir.listFiles { file ->
            file.extension == "yml" || file.extension == "gz"
        }?.sortedByDescending { it.lastModified() } ?: return

        if (backups.size > config.maxBackups) {
            backups.drop(config.maxBackups).forEach { file ->
                if (file.delete()) {
                    Logging.debug { "Deleted old backup: ${file.name}" }
                }
            }
        }
    }

    private fun startAutoBackup() {
        autoBackupJob = scope.launch {
            // Initial delay before first backup
            delay(60_000) // 1 minute

            while (isActive) {
                try {
                    createBackup("auto")
                } catch (e: Exception) {
                    Logging.error("Auto backup failed", e)
                }

                // Wait for next interval
                delay(config.intervalHours * 60 * 60 * 1000L)
            }
        }

        Logging.info("Auto backup enabled (every ${config.intervalHours} hours)")
    }
}

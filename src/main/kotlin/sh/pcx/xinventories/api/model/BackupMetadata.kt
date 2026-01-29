package sh.pcx.xinventories.api.model

import java.time.Instant

/**
 * Backup metadata.
 */
data class BackupMetadata(
    val id: String,
    val name: String,
    val timestamp: Instant,
    val playerCount: Int,
    val sizeBytes: Long,
    val storageType: StorageType,
    val compressed: Boolean
)

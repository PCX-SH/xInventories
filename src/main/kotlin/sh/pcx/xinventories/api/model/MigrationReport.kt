package sh.pcx.xinventories.api.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Migration report.
 */
data class MigrationReport(
    val from: StorageType,
    val to: StorageType,
    val playersProcessed: Int,
    val entriesMigrated: Int,
    val errors: List<MigrationError>,
    val startTime: Instant,
    val endTime: Instant
) {
    val duration: Duration get() = Duration.between(startTime, endTime)
    val success: Boolean get() = errors.isEmpty()
}

data class MigrationError(
    val uuid: UUID,
    val group: String?,
    val message: String,
    val exception: Throwable?
)

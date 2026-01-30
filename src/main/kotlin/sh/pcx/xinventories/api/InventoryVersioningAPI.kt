package sh.pcx.xinventories.api

import org.bukkit.entity.Player
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Trigger types for inventory version creation.
 */
enum class VersionTrigger {
    WORLD_CHANGE,
    DISCONNECT,
    MANUAL,
    DEATH,
    SCHEDULED
}

/**
 * Represents a snapshot version of a player's inventory.
 */
data class InventoryVersionInfo(
    val id: String,
    val playerUuid: UUID,
    val group: String,
    val timestamp: Instant,
    val trigger: VersionTrigger,
    val snapshot: PlayerInventorySnapshot,
    val metadata: Map<String, String>
)

/**
 * API for inventory versioning and history.
 * Allows creating, retrieving, and restoring inventory versions.
 */
interface InventoryVersioningAPI {

    /**
     * Checks if versioning is enabled.
     */
    fun isEnabled(): Boolean

    /**
     * Gets versions for a player.
     *
     * @param playerUuid The player's UUID
     * @param group Optional group filter (null = all groups)
     * @param limit Maximum number of versions to return (default 10)
     * @return CompletableFuture containing list of versions, ordered by timestamp descending
     */
    fun getVersions(
        playerUuid: UUID,
        group: String? = null,
        limit: Int = 10
    ): CompletableFuture<List<InventoryVersionInfo>>

    /**
     * Gets a specific version by ID.
     *
     * @param versionId The version ID
     * @return CompletableFuture containing the version, or null if not found
     */
    fun getVersion(versionId: String): CompletableFuture<InventoryVersionInfo?>

    /**
     * Creates a manual version of a player's current inventory.
     *
     * @param player The player to snapshot
     * @param metadata Optional metadata to include
     * @return CompletableFuture containing the created version, or null if creation failed
     */
    fun createVersion(
        player: Player,
        metadata: Map<String, String> = emptyMap()
    ): CompletableFuture<InventoryVersionInfo?>

    /**
     * Restores a player's inventory from a version.
     *
     * @param player The player to restore
     * @param versionId The version ID to restore from
     * @param createBackup If true, creates a backup of current inventory before restoring
     * @return CompletableFuture containing the result
     */
    fun restoreVersion(
        player: Player,
        versionId: String,
        createBackup: Boolean = true
    ): CompletableFuture<Result<Unit>>

    /**
     * Deletes a specific version.
     *
     * @param versionId The version ID to delete
     * @return CompletableFuture containing true if deleted
     */
    fun deleteVersion(versionId: String): CompletableFuture<Boolean>

    /**
     * Prunes versions older than the specified time.
     *
     * @param olderThan Delete versions before this timestamp
     * @return CompletableFuture containing number of versions deleted
     */
    fun pruneVersions(olderThan: Instant): CompletableFuture<Int>

    /**
     * Gets the version count for a player.
     *
     * @param playerUuid The player's UUID
     * @param group Optional group filter
     * @return CompletableFuture containing the count
     */
    fun getVersionCount(playerUuid: UUID, group: String? = null): CompletableFuture<Int>
}

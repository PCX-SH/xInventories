package sh.pcx.xinventories.api

import org.bukkit.entity.Player
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Represents a death record for a player.
 */
data class DeathRecordInfo(
    val id: String,
    val playerUuid: UUID,
    val timestamp: Instant,
    val world: String,
    val x: Double,
    val y: Double,
    val z: Double,
    val deathCause: String?,
    val killerName: String?,
    val killerUuid: UUID?,
    val group: String,
    val snapshot: PlayerInventorySnapshot
) {
    /**
     * Gets the location as a formatted string.
     */
    fun getLocationString(): String = "$world (${x.toInt()}, ${y.toInt()}, ${z.toInt()})"

    /**
     * Gets a human-readable description of the death cause.
     */
    fun getDeathDescription(): String = when {
        killerName != null && killerUuid != null -> "Killed by player $killerName"
        killerName != null -> "Killed by $killerName"
        deathCause != null -> deathCause.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() }
        else -> "Unknown cause"
    }
}

/**
 * API for death inventory recovery.
 * Allows retrieving and restoring inventories from player deaths.
 */
interface DeathRecoveryAPI {

    /**
     * Checks if death recovery is enabled.
     */
    fun isEnabled(): Boolean

    /**
     * Gets death records for a player.
     *
     * @param playerUuid The player's UUID
     * @param limit Maximum number of records to return (default 10)
     * @return CompletableFuture containing list of death records, ordered by timestamp descending
     */
    fun getDeathRecords(playerUuid: UUID, limit: Int = 10): CompletableFuture<List<DeathRecordInfo>>

    /**
     * Gets a specific death record by ID.
     *
     * @param deathId The death record ID
     * @return CompletableFuture containing the death record, or null if not found
     */
    fun getDeathRecord(deathId: String): CompletableFuture<DeathRecordInfo?>

    /**
     * Gets the most recent death record for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture containing the most recent death record, or null if none
     */
    fun getMostRecentDeath(playerUuid: UUID): CompletableFuture<DeathRecordInfo?>

    /**
     * Restores a player's inventory from a death record.
     *
     * @param player The player to restore (must be online)
     * @param deathId The death record ID to restore from
     * @return CompletableFuture containing the result
     */
    fun restoreDeathInventory(player: Player, deathId: String): CompletableFuture<Result<Unit>>

    /**
     * Deletes a specific death record.
     *
     * @param deathId The death record ID to delete
     * @return CompletableFuture containing true if deleted
     */
    fun deleteDeathRecord(deathId: String): CompletableFuture<Boolean>

    /**
     * Prunes death records older than the specified time.
     *
     * @param olderThan Delete records before this timestamp
     * @return CompletableFuture containing number of records deleted
     */
    fun pruneDeathRecords(olderThan: Instant): CompletableFuture<Int>

    /**
     * Gets the death record count for a player.
     *
     * @param playerUuid The player's UUID
     * @return CompletableFuture containing the count
     */
    fun getDeathRecordCount(playerUuid: UUID): CompletableFuture<Int>
}

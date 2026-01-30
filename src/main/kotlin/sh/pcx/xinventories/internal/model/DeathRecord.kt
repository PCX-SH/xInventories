package sh.pcx.xinventories.internal.model

import org.bukkit.GameMode
import org.bukkit.Location
import java.time.Instant
import java.util.UUID

/**
 * Represents a record of a player's death, including their inventory at the time.
 * Used for death recovery functionality.
 *
 * @property id Unique death record identifier (UUID-based)
 * @property playerUuid The UUID of the player who died
 * @property timestamp When the death occurred
 * @property world The world where the death occurred
 * @property x X coordinate of death location
 * @property y Y coordinate of death location
 * @property z Z coordinate of death location
 * @property deathCause The cause of death (DamageCause name)
 * @property killerName Name of the killer (player or entity)
 * @property killerUuid UUID of the killer if killed by a player
 * @property group The inventory group at time of death
 * @property gameMode The player's game mode at time of death
 * @property inventoryData The player's inventory data before death
 */
data class DeathRecord(
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
    val gameMode: GameMode,
    val inventoryData: PlayerData
) {
    companion object {
        /**
         * Creates a new death record.
         *
         * @param playerData The player's inventory data at time of death
         * @param location The location where death occurred
         * @param deathCause The cause of death
         * @param killerName Name of the killer (if any)
         * @param killerUuid UUID of the killer if killed by a player
         * @return A new DeathRecord
         */
        fun create(
            playerData: PlayerData,
            location: Location,
            deathCause: String?,
            killerName: String?,
            killerUuid: UUID?
        ): DeathRecord {
            return DeathRecord(
                id = UUID.randomUUID().toString(),
                playerUuid = playerData.uuid,
                timestamp = Instant.now(),
                world = location.world?.name ?: "unknown",
                x = location.x,
                y = location.y,
                z = location.z,
                deathCause = deathCause,
                killerName = killerName,
                killerUuid = killerUuid,
                group = playerData.group,
                gameMode = playerData.gameMode,
                inventoryData = playerData
            )
        }

        /**
         * Creates a death record from storage data.
         */
        fun fromStorage(
            id: String,
            playerUuid: UUID,
            timestamp: Instant,
            world: String,
            x: Double,
            y: Double,
            z: Double,
            deathCause: String?,
            killerName: String?,
            killerUuid: UUID?,
            group: String,
            gameMode: GameMode,
            inventoryData: PlayerData
        ): DeathRecord {
            return DeathRecord(
                id = id,
                playerUuid = playerUuid,
                timestamp = timestamp,
                world = world,
                x = x,
                y = y,
                z = z,
                deathCause = deathCause,
                killerName = killerName,
                killerUuid = killerUuid,
                group = group,
                gameMode = gameMode,
                inventoryData = inventoryData
            )
        }
    }

    /**
     * Gets the location as a string for display.
     */
    fun getLocationString(): String {
        return "$world (${x.toInt()}, ${y.toInt()}, ${z.toInt()})"
    }

    /**
     * Gets a human-readable description of when this death occurred.
     */
    fun getRelativeTimeDescription(): String {
        val now = Instant.now()
        val diffSeconds = now.epochSecond - timestamp.epochSecond

        return when {
            diffSeconds < 60 -> "just now"
            diffSeconds < 3600 -> "${diffSeconds / 60} minutes ago"
            diffSeconds < 86400 -> "${diffSeconds / 3600} hours ago"
            diffSeconds < 604800 -> "${diffSeconds / 86400} days ago"
            else -> "${diffSeconds / 604800} weeks ago"
        }
    }

    /**
     * Gets a human-readable description of the death cause.
     */
    fun getDeathDescription(): String {
        return when {
            killerName != null && killerUuid != null -> "Killed by player $killerName"
            killerName != null -> "Killed by $killerName"
            deathCause != null -> formatDeathCause(deathCause)
            else -> "Unknown cause"
        }
    }

    /**
     * Gets a summary of items in this death record.
     */
    fun getItemSummary(): String {
        val mainCount = inventoryData.mainInventory.size
        val armorCount = inventoryData.armorInventory.size
        val hasOffhand = inventoryData.offhand != null
        val enderCount = inventoryData.enderChest.size

        val parts = mutableListOf<String>()
        if (mainCount > 0) parts.add("$mainCount items")
        if (armorCount > 0) parts.add("$armorCount armor")
        if (hasOffhand) parts.add("offhand")
        if (enderCount > 0) parts.add("$enderCount ender chest")

        return if (parts.isEmpty()) "empty inventory" else parts.joinToString(", ")
    }

    private fun formatDeathCause(cause: String): String {
        return cause.lowercase()
            .replace("_", " ")
            .replaceFirstChar { it.uppercase() }
    }
}

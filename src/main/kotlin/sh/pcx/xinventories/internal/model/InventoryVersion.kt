package sh.pcx.xinventories.internal.model

import org.bukkit.GameMode
import java.time.Instant
import java.util.UUID

/**
 * Represents a historical version/snapshot of a player's inventory.
 * Used for rollback and history tracking.
 *
 * @property id Unique version identifier (UUID-based)
 * @property playerUuid The UUID of the player this version belongs to
 * @property group The inventory group name at the time of capture
 * @property gameMode The player's game mode at the time of capture (null if not separating by gamemode)
 * @property timestamp When this version was created
 * @property trigger What caused this version to be created
 * @property data The actual inventory data snapshot
 * @property metadata Optional additional context (world name, reason, etc.)
 */
data class InventoryVersion(
    val id: String,
    val playerUuid: UUID,
    val group: String,
    val gameMode: GameMode?,
    val timestamp: Instant,
    val trigger: VersionTrigger,
    val data: PlayerData,
    val metadata: Map<String, String> = emptyMap()
) {
    companion object {
        /**
         * Creates a new inventory version from a player's current data.
         *
         * @param player The player data to snapshot
         * @param trigger The trigger that caused this version
         * @param metadata Optional metadata to include
         * @return A new InventoryVersion
         */
        fun create(
            player: PlayerData,
            trigger: VersionTrigger,
            metadata: Map<String, String> = emptyMap()
        ): InventoryVersion {
            return InventoryVersion(
                id = UUID.randomUUID().toString(),
                playerUuid = player.uuid,
                group = player.group,
                gameMode = player.gameMode,
                timestamp = Instant.now(),
                trigger = trigger,
                data = player,
                metadata = metadata
            )
        }

        /**
         * Creates an inventory version with a specific ID.
         * Used when reconstructing from storage.
         */
        fun fromStorage(
            id: String,
            playerUuid: UUID,
            group: String,
            gameMode: GameMode?,
            timestamp: Instant,
            trigger: VersionTrigger,
            data: PlayerData,
            metadata: Map<String, String>
        ): InventoryVersion {
            return InventoryVersion(
                id = id,
                playerUuid = playerUuid,
                group = group,
                gameMode = gameMode,
                timestamp = timestamp,
                trigger = trigger,
                data = data,
                metadata = metadata
            )
        }
    }

    /**
     * Gets a human-readable description of when this version was created.
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
     * Gets a summary of items in this version.
     */
    fun getItemSummary(): String {
        val mainCount = data.mainInventory.size
        val armorCount = data.armorInventory.size
        val hasOffhand = data.offhand != null
        val enderCount = data.enderChest.size

        val parts = mutableListOf<String>()
        if (mainCount > 0) parts.add("$mainCount items")
        if (armorCount > 0) parts.add("$armorCount armor")
        if (hasOffhand) parts.add("offhand")
        if (enderCount > 0) parts.add("$enderCount ender chest")

        return if (parts.isEmpty()) "empty" else parts.joinToString(", ")
    }
}

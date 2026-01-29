package sh.pcx.xinventories.api.model

import org.bukkit.GameMode
import java.util.UUID

/**
 * Represents a player's current state in the inventory system.
 */
data class PlayerState(
    val uuid: UUID,
    val currentGroup: String,
    val currentGameMode: GameMode,
    val hasLoadedData: Boolean
)

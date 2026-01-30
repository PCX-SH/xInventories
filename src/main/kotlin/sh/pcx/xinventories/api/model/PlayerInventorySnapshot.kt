package sh.pcx.xinventories.api.model

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import java.time.Instant
import java.util.UUID

/**
 * Immutable snapshot of a player's inventory and state.
 */
data class PlayerInventorySnapshot(
    val uuid: UUID,
    val playerName: String,
    val group: String,
    val gameMode: GameMode,
    val contents: InventoryContents,
    val health: Double,
    val maxHealth: Double,
    val foodLevel: Int,
    val saturation: Float,
    val exhaustion: Float,
    val experience: Float,
    val level: Int,
    val totalExperience: Int,
    val potionEffects: List<PotionEffect>,
    val timestamp: Instant
) {
    /**
     * Creates a mutable builder from this snapshot.
     */
    fun toBuilder(): SnapshotBuilder = SnapshotBuilder(this)

    companion object {
        /**
         * Creates an empty snapshot for a player.
         */
        fun empty(uuid: UUID, playerName: String, group: String, gameMode: GameMode): PlayerInventorySnapshot =
            PlayerInventorySnapshot(
                uuid = uuid,
                playerName = playerName,
                group = group,
                gameMode = gameMode,
                contents = InventoryContents.empty(),
                health = 20.0,
                maxHealth = 20.0,
                foodLevel = 20,
                saturation = 5.0f,
                exhaustion = 0.0f,
                experience = 0.0f,
                level = 0,
                totalExperience = 0,
                potionEffects = emptyList(),
                timestamp = Instant.now()
            )

        /**
         * Creates a snapshot from a player's current state.
         */
        @Suppress("UnstableApiUsage")
        fun fromPlayer(player: Player, group: String): PlayerInventorySnapshot {
            val maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: 20.0

            return PlayerInventorySnapshot(
                uuid = player.uniqueId,
                playerName = player.name,
                group = group,
                gameMode = player.gameMode,
                contents = InventoryContents.fromPlayer(player),
                health = player.health,
                maxHealth = maxHealth,
                foodLevel = player.foodLevel,
                saturation = player.saturation,
                exhaustion = player.exhaustion,
                experience = player.exp,
                level = player.level,
                totalExperience = player.totalExperience,
                potionEffects = player.activePotionEffects.toList(),
                timestamp = Instant.now()
            )
        }
    }
}

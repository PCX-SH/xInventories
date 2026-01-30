package sh.pcx.xinventories.internal.model

import java.time.Instant
import java.util.UUID

/**
 * Represents a preset inventory template that can be applied to players.
 *
 * Templates store complete inventory state including items, armor, experience,
 * health, hunger, and potion effects. They can be applied automatically when
 * players enter groups or manually via commands.
 */
data class InventoryTemplate(
    /**
     * Unique identifier for the template.
     */
    val name: String,

    /**
     * Human-readable display name for the template.
     */
    val displayName: String? = null,

    /**
     * Description of what this template contains/is for.
     */
    val description: String? = null,

    /**
     * The inventory data to apply.
     */
    val inventory: PlayerData,

    /**
     * When this template was created.
     */
    val createdAt: Instant = Instant.now(),

    /**
     * UUID of the player who created this template, or null if system-created.
     */
    val createdBy: UUID? = null
) {
    /**
     * Creates a copy of this template with a new name.
     */
    fun copyWithName(newName: String): InventoryTemplate {
        val newData = PlayerData(
            uuid = inventory.uuid,
            playerName = inventory.playerName,
            group = inventory.group,
            gameMode = inventory.gameMode
        ).also { data ->
            data.mainInventory.clear()
            data.mainInventory.putAll(inventory.mainInventory.mapValues { it.value.clone() })
            data.armorInventory.clear()
            data.armorInventory.putAll(inventory.armorInventory.mapValues { it.value.clone() })
            data.offhand = inventory.offhand?.clone()
            data.enderChest.clear()
            data.enderChest.putAll(inventory.enderChest.mapValues { it.value.clone() })
            data.health = inventory.health
            data.maxHealth = inventory.maxHealth
            data.foodLevel = inventory.foodLevel
            data.saturation = inventory.saturation
            data.exhaustion = inventory.exhaustion
            data.experience = inventory.experience
            data.level = inventory.level
            data.totalExperience = inventory.totalExperience
            data.potionEffects.clear()
            data.potionEffects.addAll(inventory.potionEffects)
        }

        return copy(
            name = newName,
            inventory = newData,
            createdAt = Instant.now()
        )
    }

    /**
     * Gets the effective display name (displayName or name).
     */
    fun getEffectiveDisplayName(): String = displayName ?: name

    companion object {
        /**
         * Creates an empty template with default values.
         */
        fun empty(name: String): InventoryTemplate {
            return InventoryTemplate(
                name = name,
                inventory = PlayerData.empty(
                    uuid = UUID.fromString("00000000-0000-0000-0000-000000000000"),
                    playerName = "template",
                    group = "template",
                    gameMode = org.bukkit.GameMode.SURVIVAL
                )
            )
        }

        /**
         * Creates a template from a player's current inventory state.
         */
        fun fromPlayer(
            name: String,
            player: org.bukkit.entity.Player,
            createdBy: UUID? = null,
            displayName: String? = null,
            description: String? = null
        ): InventoryTemplate {
            return InventoryTemplate(
                name = name,
                displayName = displayName,
                description = description,
                inventory = PlayerData.fromPlayer(player, "template"),
                createdAt = Instant.now(),
                createdBy = createdBy
            )
        }
    }
}

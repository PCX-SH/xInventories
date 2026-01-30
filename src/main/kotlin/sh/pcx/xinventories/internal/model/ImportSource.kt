package sh.pcx.xinventories.internal.model

import org.bukkit.GameMode
import java.util.UUID

/**
 * Interface for import sources from other inventory management plugins.
 * Implementations provide access to plugin data via API or file-based methods.
 */
interface ImportSource {

    /**
     * The display name of this import source (e.g., "PerWorldInventory").
     */
    val name: String

    /**
     * The internal identifier for this source (e.g., "pwi", "mvi", "myworlds").
     */
    val id: String

    /**
     * Whether this import source is available (plugin files/data exist).
     */
    val isAvailable: Boolean

    /**
     * Whether the source plugin is currently loaded and API access is available.
     */
    val hasApiAccess: Boolean

    /**
     * Gets all groups defined in the source plugin.
     * @return List of import groups with their world mappings
     */
    fun getGroups(): List<ImportGroup>

    /**
     * Gets all players that have data in this source.
     * @return List of player UUIDs with stored data
     */
    fun getPlayers(): List<UUID>

    /**
     * Gets player data for a specific group and game mode.
     *
     * @param uuid The player's UUID
     * @param group The source group name
     * @param gameMode Optional game mode filter
     * @return Imported player data, or null if not found
     */
    fun getPlayerData(uuid: UUID, group: String, gameMode: GameMode? = null): ImportedPlayerData?

    /**
     * Gets all available player data across all groups.
     *
     * @param uuid The player's UUID
     * @return Map of group name to imported player data
     */
    fun getAllPlayerData(uuid: UUID): Map<String, ImportedPlayerData>

    /**
     * Validates the source data integrity.
     * @return Validation result with any issues found
     */
    fun validate(): ImportValidationResult
}

/**
 * Result of validating import source data.
 */
data class ImportValidationResult(
    val isValid: Boolean,
    val issues: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val playerCount: Int = 0,
    val groupCount: Int = 0
)

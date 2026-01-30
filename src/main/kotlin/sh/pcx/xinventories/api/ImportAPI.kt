package sh.pcx.xinventories.api

import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * API for importing data from other inventory plugins.
 */
interface ImportAPI {

    /**
     * Information about an import source.
     */
    data class ImportSourceInfo(
        val id: String,
        val name: String,
        val isAvailable: Boolean,
        val hasApiAccess: Boolean,
        val playerCount: Int,
        val groupCount: Int
    )

    /**
     * Preview of an import operation.
     */
    data class ImportPreviewResult(
        val source: String,
        val totalPlayers: Int,
        val playersToImport: Int,
        val playersToSkip: Int,
        val groups: List<String>,
        val warnings: List<String>
    )

    /**
     * Result of an import operation.
     */
    data class ImportOperationResult(
        val success: Boolean,
        val playersImported: Int,
        val playersSkipped: Int,
        val playersFailed: Int,
        val groupsProcessed: Int,
        val durationMs: Long,
        val errors: List<String>,
        val warnings: List<String>
    )

    /**
     * Detects available import sources.
     * @return List of available import source information
     */
    fun detectSources(): List<ImportSourceInfo>

    /**
     * Gets information about a specific import source.
     *
     * @param sourceId The source ID (e.g., "pwi", "mvi", "myworlds")
     * @return Source information, or null if not found
     */
    fun getSourceInfo(sourceId: String): ImportSourceInfo?

    /**
     * Generates a preview of what an import would do.
     *
     * @param sourceId The source ID
     * @param groupMappings Map of source group to target group (null for identity mapping)
     * @return CompletableFuture containing the preview result
     */
    fun previewImport(
        sourceId: String,
        groupMappings: Map<String, String>? = null
    ): CompletableFuture<ImportPreviewResult?>

    /**
     * Executes an import operation.
     *
     * @param sourceId The source ID
     * @param groupMappings Map of source group to target group
     * @param options Import options
     * @return CompletableFuture containing the import result
     */
    fun executeImport(
        sourceId: String,
        groupMappings: Map<String, String>,
        options: ImportOptions = ImportOptions()
    ): CompletableFuture<ImportOperationResult>

    /**
     * Gets the last import result.
     * @return The last import result, or null if no import has been run
     */
    fun getLastImportResult(): ImportOperationResult?

    /**
     * Gets available groups from an import source.
     *
     * @param sourceId The source ID
     * @return List of group names and their worlds
     */
    fun getSourceGroups(sourceId: String): Map<String, Set<String>>

    /**
     * Gets players with data in an import source.
     *
     * @param sourceId The source ID
     * @return List of player UUIDs
     */
    fun getSourcePlayers(sourceId: String): List<UUID>

    /**
     * Options for import operations.
     */
    data class ImportOptions(
        /**
         * Whether to overwrite existing xInventories data.
         */
        val overwriteExisting: Boolean = false,

        /**
         * Whether to import economy balances.
         */
        val importBalances: Boolean = true,

        /**
         * Whether to create target groups if they don't exist.
         */
        val createMissingGroups: Boolean = true,

        /**
         * Whether to import data for offline players.
         */
        val importOfflinePlayers: Boolean = true,

        /**
         * Whether to run in dry-run mode (no actual changes).
         */
        val dryRun: Boolean = false
    )
}

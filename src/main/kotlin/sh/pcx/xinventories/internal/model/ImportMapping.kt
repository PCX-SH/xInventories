package sh.pcx.xinventories.internal.model

/**
 * Configuration for mapping source plugin groups to xInventories groups.
 * Used during the import process to control how data is migrated.
 */
data class ImportMapping(
    /**
     * The source plugin identifier (e.g., "pwi", "mvi", "myworlds").
     */
    val source: String,

    /**
     * Map of source group name to xInventories group name.
     */
    val groupMappings: Map<String, String>,

    /**
     * Import options.
     */
    val options: ImportOptions = ImportOptions()
) {
    /**
     * Gets the target xInventories group for a source group.
     * Returns the source group name if no mapping is defined.
     */
    fun getTargetGroup(sourceGroup: String): String {
        return groupMappings[sourceGroup] ?: sourceGroup
    }

    /**
     * Checks if a source group has an explicit mapping.
     */
    fun hasMappingFor(sourceGroup: String): Boolean {
        return groupMappings.containsKey(sourceGroup)
    }

    /**
     * Gets all source groups that are mapped.
     */
    fun getMappedSourceGroups(): Set<String> {
        return groupMappings.keys
    }

    /**
     * Gets all target groups.
     */
    fun getTargetGroups(): Set<String> {
        return groupMappings.values.toSet()
    }

    companion object {
        /**
         * Creates a default mapping that maps each source group to itself.
         */
        fun identity(source: String, groups: List<ImportGroup>): ImportMapping {
            return ImportMapping(
                source = source,
                groupMappings = groups.associate { it.name to it.name }
            )
        }

        /**
         * Creates an empty mapping.
         */
        fun empty(source: String): ImportMapping {
            return ImportMapping(
                source = source,
                groupMappings = emptyMap()
            )
        }
    }
}

/**
 * Options controlling the import behavior.
 */
data class ImportOptions(
    /**
     * Whether to overwrite existing xInventories data for players.
     * If false, players with existing data will be skipped.
     */
    val overwriteExisting: Boolean = false,

    /**
     * Whether to import economy balances.
     * Only applicable if the source has balance data.
     */
    val importBalances: Boolean = true,

    /**
     * Whether to delete source data after successful import.
     * WARNING: This is destructive and cannot be undone.
     */
    val deleteSourceAfter: Boolean = false,

    /**
     * Whether to prefer API access when the source plugin is loaded.
     * If false, always uses file-based import.
     */
    val useApiWhenAvailable: Boolean = true,

    /**
     * Whether to create target groups if they don't exist.
     */
    val createMissingGroups: Boolean = true,

    /**
     * Whether to import data for offline players.
     * If false, only imports data for players who are currently online.
     */
    val importOfflinePlayers: Boolean = true,

    /**
     * Maximum number of players to import in a single batch.
     * Used to prevent memory issues with large player bases.
     */
    val batchSize: Int = 100,

    /**
     * Whether to run the import in dry-run mode (no actual changes).
     */
    val dryRun: Boolean = false
)

/**
 * Result of an import operation.
 */
data class ImportResult(
    /**
     * Whether the import completed successfully.
     */
    val success: Boolean,

    /**
     * Number of players successfully imported.
     */
    val playersImported: Int,

    /**
     * Number of players skipped (due to existing data or errors).
     */
    val playersSkipped: Int,

    /**
     * Number of players that failed to import.
     */
    val playersFailed: Int,

    /**
     * Number of groups processed.
     */
    val groupsProcessed: Int,

    /**
     * Time taken for the import in milliseconds.
     */
    val durationMs: Long,

    /**
     * List of errors encountered during import.
     */
    val errors: List<ImportError> = emptyList(),

    /**
     * List of warnings encountered during import.
     */
    val warnings: List<String> = emptyList(),

    /**
     * Whether this was a dry run.
     */
    val isDryRun: Boolean = false,

    /**
     * Additional details about the import.
     */
    val details: Map<String, Any> = emptyMap()
) {
    /**
     * Gets a human-readable summary of the import.
     */
    fun getSummary(): String = buildString {
        if (isDryRun) appendLine("[DRY RUN] No changes were made.")
        appendLine("Import ${if (success) "completed successfully" else "completed with errors"}")
        appendLine("- Players imported: $playersImported")
        appendLine("- Players skipped: $playersSkipped")
        appendLine("- Players failed: $playersFailed")
        appendLine("- Groups processed: $groupsProcessed")
        appendLine("- Duration: ${durationMs}ms")
        if (errors.isNotEmpty()) {
            appendLine("Errors (${errors.size}):")
            errors.take(10).forEach { error ->
                appendLine("  - ${error.message}")
            }
            if (errors.size > 10) {
                appendLine("  ... and ${errors.size - 10} more errors")
            }
        }
        if (warnings.isNotEmpty()) {
            appendLine("Warnings (${warnings.size}):")
            warnings.take(5).forEach { warning ->
                appendLine("  - $warning")
            }
            if (warnings.size > 5) {
                appendLine("  ... and ${warnings.size - 5} more warnings")
            }
        }
    }
}

/**
 * Represents an error during import.
 */
data class ImportError(
    /**
     * The error message.
     */
    val message: String,

    /**
     * The player UUID associated with this error, if applicable.
     */
    val playerUuid: java.util.UUID? = null,

    /**
     * The group associated with this error, if applicable.
     */
    val group: String? = null,

    /**
     * The underlying exception, if any.
     */
    val exception: Throwable? = null
) {
    override fun toString(): String = buildString {
        append(message)
        if (playerUuid != null) append(" [player: $playerUuid]")
        if (group != null) append(" [group: $group]")
    }
}

/**
 * Preview of what an import would do.
 */
data class ImportPreview(
    /**
     * The source plugin being imported from.
     */
    val source: String,

    /**
     * Number of players that would be imported.
     */
    val totalPlayers: Int,

    /**
     * Number of players that would be skipped (already have data).
     */
    val playersToSkip: Int,

    /**
     * Groups that would be imported.
     */
    val groups: List<ImportGroup>,

    /**
     * The mapping configuration.
     */
    val mapping: ImportMapping,

    /**
     * Estimated data size in bytes.
     */
    val estimatedDataSize: Long,

    /**
     * Any warnings about the import.
     */
    val warnings: List<String> = emptyList(),

    /**
     * Sample player data for preview.
     */
    val samplePlayers: List<ImportedPlayerData> = emptyList()
) {
    /**
     * Gets a human-readable summary of the preview.
     */
    fun getSummary(): String = buildString {
        appendLine("Import Preview from $source")
        appendLine("─".repeat(40))
        appendLine("Players to import: ${totalPlayers - playersToSkip}")
        appendLine("Players to skip (existing data): $playersToSkip")
        appendLine("Total players in source: $totalPlayers")
        appendLine()
        appendLine("Group mappings:")
        mapping.groupMappings.forEach { (source, target) ->
            appendLine("  $source -> $target")
        }
        if (warnings.isNotEmpty()) {
            appendLine()
            appendLine("Warnings:")
            warnings.forEach { warning ->
                appendLine("  - $warning")
            }
        }
    }
}

package sh.pcx.xinventories.internal.api

import kotlinx.coroutines.runBlocking
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.ImportAPI
import sh.pcx.xinventories.internal.model.ImportMapping
import sh.pcx.xinventories.internal.model.ImportOptions
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the ImportAPI.
 * Adapts internal ImportService to the public API interface.
 */
class ImportAPIImpl(private val plugin: XInventories) : ImportAPI {

    private val importService get() = plugin.serviceManager.importService

    override fun detectSources(): List<ImportAPI.ImportSourceInfo> {
        return importService.detectSources().map { source ->
            ImportAPI.ImportSourceInfo(
                id = source.id,
                name = source.name,
                isAvailable = source.isAvailable,
                hasApiAccess = source.hasApiAccess,
                playerCount = source.getPlayers().size,
                groupCount = source.getGroups().size
            )
        }
    }

    override fun getSourceInfo(sourceId: String): ImportAPI.ImportSourceInfo? {
        val source = importService.getSource(sourceId) ?: return null
        return ImportAPI.ImportSourceInfo(
            id = source.id,
            name = source.name,
            isAvailable = source.isAvailable,
            hasApiAccess = source.hasApiAccess,
            playerCount = source.getPlayers().size,
            groupCount = source.getGroups().size
        )
    }

    override fun previewImport(
        sourceId: String,
        groupMappings: Map<String, String>?
    ): CompletableFuture<ImportAPI.ImportPreviewResult?> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                val mapping = groupMappings?.let { mappings ->
                    ImportMapping(
                        source = sourceId,
                        groupMappings = mappings,
                        options = ImportOptions()
                    )
                }

                val preview = importService.previewImport(sourceId, mapping) ?: return@runBlocking null

                ImportAPI.ImportPreviewResult(
                    source = preview.source,
                    totalPlayers = preview.totalPlayers,
                    playersToImport = preview.totalPlayers - preview.playersToSkip,
                    playersToSkip = preview.playersToSkip,
                    groups = preview.groups.map { it.name },
                    warnings = preview.warnings
                )
            }
        }
    }

    override fun executeImport(
        sourceId: String,
        groupMappings: Map<String, String>,
        options: ImportAPI.ImportOptions
    ): CompletableFuture<ImportAPI.ImportOperationResult> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                val internalOptions = ImportOptions(
                    overwriteExisting = options.overwriteExisting,
                    importBalances = options.importBalances,
                    createMissingGroups = options.createMissingGroups,
                    importOfflinePlayers = options.importOfflinePlayers,
                    dryRun = options.dryRun
                )

                val mapping = ImportMapping(
                    source = sourceId,
                    groupMappings = groupMappings,
                    options = internalOptions
                )

                val result = importService.executeImport(sourceId, mapping)

                ImportAPI.ImportOperationResult(
                    success = result.success,
                    playersImported = result.playersImported,
                    playersSkipped = result.playersSkipped,
                    playersFailed = result.playersFailed,
                    groupsProcessed = result.groupsProcessed,
                    durationMs = result.durationMs,
                    errors = result.errors.map { it.message },
                    warnings = result.warnings
                )
            }
        }
    }

    override fun getLastImportResult(): ImportAPI.ImportOperationResult? {
        val result = importService.getLastImportResult() ?: return null
        return ImportAPI.ImportOperationResult(
            success = result.success,
            playersImported = result.playersImported,
            playersSkipped = result.playersSkipped,
            playersFailed = result.playersFailed,
            groupsProcessed = result.groupsProcessed,
            durationMs = result.durationMs,
            errors = result.errors.map { it.message },
            warnings = result.warnings
        )
    }

    override fun getSourceGroups(sourceId: String): Map<String, Set<String>> {
        val source = importService.getSource(sourceId) ?: return emptyMap()
        return source.getGroups().associate { group ->
            group.name to group.worlds
        }
    }

    override fun getSourcePlayers(sourceId: String): List<UUID> {
        val source = importService.getSource(sourceId) ?: return emptyList()
        return source.getPlayers()
    }
}

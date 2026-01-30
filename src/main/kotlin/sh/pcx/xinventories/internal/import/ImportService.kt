package sh.pcx.xinventories.internal.import

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.*
import sh.pcx.xinventories.internal.service.EconomyService
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.StorageService
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service that orchestrates the import process from other inventory plugins.
 * Handles source detection, mapping configuration, and data conversion.
 */
class ImportService(
    private val plugin: XInventories,
    private val scope: CoroutineScope,
    private val storageService: StorageService,
    private val groupService: GroupService,
    private val economyService: EconomyService
) {

    // Available import sources
    private val sources = ConcurrentHashMap<String, ImportSource>()

    // Last import report
    private var lastImportResult: ImportResult? = null

    /**
     * Initializes the import service and registers available sources.
     */
    fun initialize() {
        // Register import sources
        registerSource(PwiImportSource(plugin))
        registerSource(MviImportSource(plugin))
        registerSource(MyWorldsImportSource(plugin))

        Logging.debug { "ImportService initialized with ${sources.size} sources" }
    }

    /**
     * Registers an import source.
     */
    fun registerSource(source: ImportSource) {
        sources[source.id] = source
        Logging.debug { "Registered import source: ${source.name} (${source.id})" }
    }

    /**
     * Gets all registered import sources.
     */
    fun getSources(): List<ImportSource> = sources.values.toList()

    /**
     * Gets an import source by ID.
     */
    fun getSource(id: String): ImportSource? = sources[id]

    /**
     * Detects available import sources.
     * Returns only sources that have data available.
     */
    fun detectSources(): List<ImportSource> {
        return sources.values.filter { it.isAvailable }
    }

    /**
     * Gets a preview of what an import would do.
     *
     * @param sourceId The import source ID
     * @param mapping The group mapping configuration
     * @return Import preview
     */
    suspend fun previewImport(
        sourceId: String,
        mapping: ImportMapping? = null
    ): ImportPreview? = withContext(Dispatchers.IO) {
        val source = sources[sourceId] ?: return@withContext null

        if (!source.isAvailable) {
            Logging.warning("Import source $sourceId is not available")
            return@withContext null
        }

        val groups = source.getGroups()
        val players = source.getPlayers()

        // Create identity mapping if none provided
        val effectiveMapping = mapping ?: ImportMapping.identity(sourceId, groups)

        // Check which players already have data
        var playersToSkip = 0
        if (!effectiveMapping.options.overwriteExisting) {
            players.forEach { uuid ->
                effectiveMapping.groupMappings.values.forEach { targetGroup ->
                    if (storageService.hasPlayerData(uuid, targetGroup)) {
                        playersToSkip++
                        return@forEach
                    }
                }
            }
        }

        // Get sample data for preview
        val samplePlayers = players.take(3).mapNotNull { uuid ->
            groups.firstOrNull()?.let { group ->
                source.getPlayerData(uuid, group.name, GameMode.SURVIVAL)
            }
        }

        // Collect warnings
        val warnings = mutableListOf<String>()
        val validation = source.validate()
        warnings.addAll(validation.warnings)

        if (!source.hasApiAccess) {
            warnings.add("Source plugin not loaded - using file-based import")
        }

        effectiveMapping.groupMappings.values.forEach { targetGroup ->
            if (groupService.getGroup(targetGroup) == null && !effectiveMapping.options.createMissingGroups) {
                warnings.add("Target group '$targetGroup' does not exist")
            }
        }

        ImportPreview(
            source = source.name,
            totalPlayers = players.size,
            playersToSkip = playersToSkip,
            groups = groups,
            mapping = effectiveMapping,
            estimatedDataSize = estimateDataSize(players.size, groups.size),
            warnings = warnings,
            samplePlayers = samplePlayers
        )
    }

    /**
     * Executes an import operation.
     *
     * @param sourceId The import source ID
     * @param mapping The group mapping configuration
     * @return Import result
     */
    suspend fun executeImport(
        sourceId: String,
        mapping: ImportMapping
    ): ImportResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<ImportError>()
        val warnings = mutableListOf<String>()

        val source = sources[sourceId]
        if (source == null) {
            return@withContext ImportResult(
                success = false,
                playersImported = 0,
                playersSkipped = 0,
                playersFailed = 0,
                groupsProcessed = 0,
                durationMs = System.currentTimeMillis() - startTime,
                errors = listOf(ImportError("Import source '$sourceId' not found")),
                isDryRun = mapping.options.dryRun
            )
        }

        if (!source.isAvailable) {
            return@withContext ImportResult(
                success = false,
                playersImported = 0,
                playersSkipped = 0,
                playersFailed = 0,
                groupsProcessed = 0,
                durationMs = System.currentTimeMillis() - startTime,
                errors = listOf(ImportError("Import source '$sourceId' is not available")),
                isDryRun = mapping.options.dryRun
            )
        }

        Logging.info("Starting import from ${source.name}...")

        val groups = source.getGroups()
        val players = source.getPlayers()

        var playersImported = 0
        var playersSkipped = 0
        var playersFailed = 0
        var groupsProcessed = 0

        // Create missing target groups if configured
        if (mapping.options.createMissingGroups) {
            mapping.groupMappings.values.toSet().forEach { targetGroup ->
                if (groupService.getGroup(targetGroup) == null) {
                    if (!mapping.options.dryRun) {
                        groupService.createGroup(targetGroup)
                        Logging.info("Created group: $targetGroup")
                    }
                    warnings.add("Created group: $targetGroup")
                }
            }
        }

        // Process players in batches
        val batches = players.chunked(mapping.options.batchSize)

        batches.forEachIndexed { batchIndex, batch ->
            Logging.debug { "Processing batch ${batchIndex + 1}/${batches.size} (${batch.size} players)" }

            batch.forEach { uuid ->
                try {
                    val imported = importPlayer(source, uuid, mapping, errors, warnings)
                    when (imported) {
                        ImportPlayerResult.SUCCESS -> playersImported++
                        ImportPlayerResult.SKIPPED -> playersSkipped++
                        ImportPlayerResult.FAILED -> playersFailed++
                    }
                } catch (e: Exception) {
                    errors.add(ImportError(
                        message = "Failed to import player $uuid: ${e.message}",
                        playerUuid = uuid,
                        exception = e
                    ))
                    playersFailed++
                }
            }
        }

        groupsProcessed = groups.size

        val result = ImportResult(
            success = errors.isEmpty() || playersImported > 0,
            playersImported = playersImported,
            playersSkipped = playersSkipped,
            playersFailed = playersFailed,
            groupsProcessed = groupsProcessed,
            durationMs = System.currentTimeMillis() - startTime,
            errors = errors,
            warnings = warnings,
            isDryRun = mapping.options.dryRun,
            details = mapOf(
                "source" to source.name,
                "sourceId" to sourceId,
                "totalPlayers" to players.size,
                "apiAccess" to source.hasApiAccess
            )
        )

        lastImportResult = result

        Logging.info(result.getSummary())

        result
    }

    private enum class ImportPlayerResult {
        SUCCESS, SKIPPED, FAILED
    }

    private suspend fun importPlayer(
        source: ImportSource,
        uuid: UUID,
        mapping: ImportMapping,
        errors: MutableList<ImportError>,
        warnings: MutableList<String>
    ): ImportPlayerResult {
        var anyImported = false
        var anyFailed = false

        // Import data for each group mapping
        mapping.groupMappings.forEach { (sourceGroup, targetGroup) ->
            // Import for each game mode if the source supports it
            val gameModes = if (mapping.options.useApiWhenAvailable && source.hasApiAccess) {
                GameMode.entries
            } else {
                listOf(GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR)
            }

            gameModes.forEach gameModeLoop@{ gameMode ->
                // Check if target data already exists
                if (!mapping.options.overwriteExisting) {
                    if (storageService.hasPlayerData(uuid, targetGroup, gameMode)) {
                        return@gameModeLoop
                    }
                }

                // Get source data
                val importedData = source.getPlayerData(uuid, sourceGroup, gameMode)
                if (importedData == null) {
                    return@gameModeLoop
                }

                // Convert to PlayerData
                val playerData = importedData.toPlayerData(targetGroup)
                playerData.gameMode = gameMode

                // Save the data (unless dry run)
                if (!mapping.options.dryRun) {
                    try {
                        storageService.savePlayerData(playerData)

                        // Import balance if configured
                        if (mapping.options.importBalances && importedData.balance != null) {
                            economyService.setBalance(uuid, targetGroup, importedData.balance)
                        }

                        anyImported = true
                    } catch (e: Exception) {
                        errors.add(ImportError(
                            message = "Failed to save data for player $uuid in $targetGroup: ${e.message}",
                            playerUuid = uuid,
                            group = targetGroup,
                            exception = e
                        ))
                        anyFailed = true
                    }
                } else {
                    anyImported = true
                }
            }
        }

        return when {
            anyImported && !anyFailed -> ImportPlayerResult.SUCCESS
            anyFailed -> ImportPlayerResult.FAILED
            else -> ImportPlayerResult.SKIPPED
        }
    }

    /**
     * Loads import mapping configuration from a file.
     */
    fun loadMapping(file: File): ImportMapping? {
        if (!file.exists()) return null

        return try {
            val yaml = YamlConfiguration.loadConfiguration(file)

            val source = yaml.getString("source") ?: return null
            val mappings = mutableMapOf<String, String>()

            yaml.getConfigurationSection("mappings")?.let { mappingsSection ->
                mappingsSection.getKeys(false).forEach { sourceGroup ->
                    val targetGroup = mappingsSection.getString(sourceGroup)
                    if (targetGroup != null) {
                        mappings[sourceGroup] = targetGroup
                    }
                }
            }

            val optionsSection = yaml.getConfigurationSection("options")
            val options = ImportOptions(
                overwriteExisting = optionsSection?.getBoolean("overwriteExisting", false) ?: false,
                importBalances = optionsSection?.getBoolean("importBalances", true) ?: true,
                deleteSourceAfter = optionsSection?.getBoolean("deleteSourceAfter", false) ?: false,
                useApiWhenAvailable = optionsSection?.getBoolean("useApiWhenAvailable", true) ?: true,
                createMissingGroups = optionsSection?.getBoolean("createMissingGroups", true) ?: true,
                importOfflinePlayers = optionsSection?.getBoolean("importOfflinePlayers", true) ?: true,
                batchSize = optionsSection?.getInt("batchSize", 100) ?: 100,
                dryRun = optionsSection?.getBoolean("dryRun", false) ?: false
            )

            ImportMapping(
                source = source,
                groupMappings = mappings,
                options = options
            )
        } catch (e: Exception) {
            Logging.error("Failed to load import mapping from ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Saves import mapping configuration to a file.
     */
    fun saveMapping(mapping: ImportMapping, file: File): Boolean {
        return try {
            val yaml = YamlConfiguration()

            yaml.set("source", mapping.source)

            mapping.groupMappings.forEach { (source, target) ->
                yaml.set("mappings.$source", target)
            }

            yaml.set("options.overwriteExisting", mapping.options.overwriteExisting)
            yaml.set("options.importBalances", mapping.options.importBalances)
            yaml.set("options.deleteSourceAfter", mapping.options.deleteSourceAfter)
            yaml.set("options.useApiWhenAvailable", mapping.options.useApiWhenAvailable)
            yaml.set("options.createMissingGroups", mapping.options.createMissingGroups)
            yaml.set("options.importOfflinePlayers", mapping.options.importOfflinePlayers)
            yaml.set("options.batchSize", mapping.options.batchSize)
            yaml.set("options.dryRun", mapping.options.dryRun)

            file.parentFile?.mkdirs()
            yaml.save(file)
            true
        } catch (e: Exception) {
            Logging.error("Failed to save import mapping to ${file.absolutePath}", e)
            false
        }
    }

    /**
     * Gets the default mapping file location.
     */
    fun getMappingFile(): File = File(plugin.dataFolder, "import-mapping.yml")

    /**
     * Gets the last import result.
     */
    fun getLastImportResult(): ImportResult? = lastImportResult

    /**
     * Generates an import report.
     */
    fun generateReport(result: ImportResult): String = buildString {
        appendLine("═".repeat(50))
        appendLine("           xInventories Import Report")
        appendLine("═".repeat(50))
        appendLine()
        appendLine("Source: ${result.details["source"] ?: "Unknown"}")
        appendLine("API Access: ${result.details["apiAccess"] ?: "N/A"}")
        appendLine("Dry Run: ${if (result.isDryRun) "Yes" else "No"}")
        appendLine()
        appendLine("─".repeat(50))
        appendLine("Results:")
        appendLine("─".repeat(50))
        appendLine("  Players imported:  ${result.playersImported}")
        appendLine("  Players skipped:   ${result.playersSkipped}")
        appendLine("  Players failed:    ${result.playersFailed}")
        appendLine("  Groups processed:  ${result.groupsProcessed}")
        appendLine("  Duration:          ${result.durationMs}ms")
        appendLine()

        if (result.errors.isNotEmpty()) {
            appendLine("─".repeat(50))
            appendLine("Errors (${result.errors.size}):")
            appendLine("─".repeat(50))
            result.errors.forEach { error ->
                appendLine("  - $error")
            }
            appendLine()
        }

        if (result.warnings.isNotEmpty()) {
            appendLine("─".repeat(50))
            appendLine("Warnings (${result.warnings.size}):")
            appendLine("─".repeat(50))
            result.warnings.forEach { warning ->
                appendLine("  - $warning")
            }
            appendLine()
        }

        appendLine("═".repeat(50))
        appendLine("Status: ${if (result.success) "SUCCESS" else "FAILED"}")
        appendLine("═".repeat(50))
    }

    private fun estimateDataSize(playerCount: Int, groupCount: Int): Long {
        // Rough estimate: ~10KB per player per group
        return (playerCount.toLong() * groupCount.toLong() * 10 * 1024)
    }
}

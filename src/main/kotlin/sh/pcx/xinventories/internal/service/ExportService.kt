package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.InventorySerializer
import sh.pcx.xinventories.internal.util.Logging
import sh.pcx.xinventories.internal.util.PotionSerializer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.bukkit.GameMode
import org.bukkit.inventory.ItemStack
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Result of an export operation.
 */
data class ExportResult(
    val success: Boolean,
    val filePath: String?,
    val playerCount: Int,
    val message: String
)

/**
 * Result of an import operation.
 */
data class ImportResult(
    val success: Boolean,
    val playersImported: Int,
    val playersSkipped: Int,
    val playersFailed: Int,
    val errors: List<String>,
    val message: String
)

/**
 * Result of a validation operation.
 */
data class ValidationResult(
    val valid: Boolean,
    val version: String?,
    val playerCount: Int,
    val groupName: String?,
    val errors: List<String>,
    val warnings: List<String>
)

/**
 * JSON-serializable player export data.
 */
@Serializable
data class PlayerExportData(
    val version: String = "1.1.0",
    val exported: String,
    val player: PlayerInfo,
    val group: String,
    val data: InventoryData
)

@Serializable
data class PlayerInfo(
    val uuid: String,
    val name: String
)

@Serializable
data class InventoryData(
    val inventory: Map<String, SerializedItem>,
    val armor: Map<String, SerializedItem>,
    val offhand: SerializedItem?,
    val enderChest: Map<String, SerializedItem>,
    val experience: Int,
    val level: Int,
    val health: Double,
    val maxHealth: Double,
    val hunger: Int,
    val saturation: Float,
    val exhaustion: Float,
    val effects: List<SerializedEffect>,
    val gameMode: String
)

@Serializable
data class SerializedItem(
    val type: String,
    val amount: Int,
    val data: String // Base64 encoded full item data
)

@Serializable
data class SerializedEffect(
    val type: String,
    val duration: Int,
    val amplifier: Int,
    val ambient: Boolean,
    val particles: Boolean,
    val icon: Boolean
)

/**
 * Bulk export data for multiple players.
 */
@Serializable
data class BulkExportData(
    val version: String = "1.1.0",
    val exported: String,
    val group: String,
    val playerCount: Int,
    val players: List<PlayerExportData>
)

/**
 * Service for exporting and importing player inventory data as JSON.
 *
 * Supports:
 * - Export single player inventory to JSON
 * - Export all players in a group
 * - Import from JSON with validation
 * - Partial imports
 */
class ExportService(
    private val plugin: XInventories,
    private val scope: CoroutineScope,
    private val storageService: StorageService
) {
    private val exportDir: File by lazy {
        File(plugin.dataFolder, "exports").also { it.mkdirs() }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dateFormatter = DateTimeFormatter.ISO_INSTANT
        .withZone(ZoneId.of("UTC"))

    /**
     * Initializes the export service.
     */
    fun initialize() {
        Logging.debug { "ExportService initialized" }
    }

    /**
     * Shuts down the export service.
     */
    fun shutdown() {
        // Nothing to clean up
    }

    /**
     * Exports a single player's inventory to JSON.
     *
     * @param playerUUID The player's UUID
     * @param group The group to export (or current group if null)
     * @param fileName Custom file name (auto-generated if null)
     * @param gameMode GameMode for data lookup (null for non-separated)
     * @return ExportResult with file path on success
     */
    suspend fun exportPlayer(
        playerUUID: UUID,
        group: String,
        fileName: String? = null,
        gameMode: GameMode? = null
    ): ExportResult {
        return withContext(Dispatchers.IO) {
            try {
                val playerData = storageService.loadPlayerData(playerUUID, group, gameMode)

                if (playerData == null) {
                    return@withContext ExportResult(
                        success = false,
                        filePath = null,
                        playerCount = 0,
                        message = "No data found for player in group '$group'"
                    )
                }

                val exportData = playerDataToExport(playerData)
                val jsonContent = json.encodeToString(exportData)

                val actualFileName = fileName
                    ?: "${playerData.playerName}_${group}_${System.currentTimeMillis()}.json"
                val outputFile = File(exportDir, actualFileName)

                outputFile.writeText(jsonContent)

                Logging.info("Exported player ${playerData.playerName} (group: $group) to ${outputFile.name}")

                ExportResult(
                    success = true,
                    filePath = outputFile.absolutePath,
                    playerCount = 1,
                    message = "Exported to ${outputFile.name}"
                )
            } catch (e: Exception) {
                Logging.error("Failed to export player $playerUUID", e)
                ExportResult(
                    success = false,
                    filePath = null,
                    playerCount = 0,
                    message = "Export failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Exports all players in a group to a single JSON file.
     *
     * @param group The group to export
     * @param fileName Custom file name (auto-generated if null)
     * @return ExportResult with file path on success
     */
    suspend fun exportGroup(
        group: String,
        fileName: String? = null
    ): ExportResult {
        return withContext(Dispatchers.IO) {
            try {
                val allUUIDs = storageService.getAllPlayerUUIDs()
                val exportedPlayers = mutableListOf<PlayerExportData>()

                for (uuid in allUUIDs) {
                    val playerData = storageService.loadPlayerData(uuid, group, null)
                        ?: continue

                    if (playerData.group == group) {
                        exportedPlayers.add(playerDataToExport(playerData))
                    }
                }

                if (exportedPlayers.isEmpty()) {
                    return@withContext ExportResult(
                        success = false,
                        filePath = null,
                        playerCount = 0,
                        message = "No players found in group '$group'"
                    )
                }

                val bulkExport = BulkExportData(
                    version = "1.1.0",
                    exported = dateFormatter.format(Instant.now()),
                    group = group,
                    playerCount = exportedPlayers.size,
                    players = exportedPlayers
                )

                val jsonContent = json.encodeToString(bulkExport)

                val actualFileName = fileName
                    ?: "group_${group}_${System.currentTimeMillis()}.json"
                val outputFile = File(exportDir, actualFileName)

                outputFile.writeText(jsonContent)

                Logging.info("Exported ${exportedPlayers.size} players from group $group to ${outputFile.name}")

                ExportResult(
                    success = true,
                    filePath = outputFile.absolutePath,
                    playerCount = exportedPlayers.size,
                    message = "Exported ${exportedPlayers.size} players to ${outputFile.name}"
                )
            } catch (e: Exception) {
                Logging.error("Failed to export group $group", e)
                ExportResult(
                    success = false,
                    filePath = null,
                    playerCount = 0,
                    message = "Export failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Validates a JSON import file without importing.
     *
     * @param filePath Path to the JSON file
     * @return ValidationResult with details about the file
     */
    suspend fun validateImport(filePath: String): ValidationResult {
        return withContext(Dispatchers.IO) {
            try {
                val file = resolveFile(filePath)
                    ?: return@withContext ValidationResult(
                        valid = false,
                        version = null,
                        playerCount = 0,
                        groupName = null,
                        errors = listOf("File not found: $filePath"),
                        warnings = emptyList()
                    )

                val content = file.readText()
                val errors = mutableListOf<String>()
                val warnings = mutableListOf<String>()

                // Try parsing as single player export first
                try {
                    val singleExport = json.decodeFromString<PlayerExportData>(content)
                    return@withContext validateSingleExport(singleExport, errors, warnings)
                } catch (e: Exception) {
                    // Try as bulk export
                    try {
                        val bulkExport = json.decodeFromString<BulkExportData>(content)
                        return@withContext validateBulkExport(bulkExport, errors, warnings)
                    } catch (e2: Exception) {
                        return@withContext ValidationResult(
                            valid = false,
                            version = null,
                            playerCount = 0,
                            groupName = null,
                            errors = listOf("Invalid JSON format: ${e2.message}"),
                            warnings = emptyList()
                        )
                    }
                }
            } catch (e: Exception) {
                ValidationResult(
                    valid = false,
                    version = null,
                    playerCount = 0,
                    groupName = null,
                    errors = listOf("Validation failed: ${e.message}"),
                    warnings = emptyList()
                )
            }
        }
    }

    /**
     * Imports player data from a JSON file.
     *
     * @param filePath Path to the JSON file
     * @param targetPlayerUUID Override player UUID (for single imports)
     * @param targetGroup Override target group
     * @param overwrite Whether to overwrite existing data
     * @return ImportResult with import statistics
     */
    suspend fun importFromFile(
        filePath: String,
        targetPlayerUUID: UUID? = null,
        targetGroup: String? = null,
        overwrite: Boolean = false
    ): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val file = resolveFile(filePath)
                    ?: return@withContext ImportResult(
                        success = false,
                        playersImported = 0,
                        playersSkipped = 0,
                        playersFailed = 0,
                        errors = listOf("File not found: $filePath"),
                        message = "Import failed: File not found"
                    )

                val content = file.readText()

                // Try parsing as single player export first
                try {
                    val singleExport = json.decodeFromString<PlayerExportData>(content)
                    return@withContext importSinglePlayer(singleExport, targetPlayerUUID, targetGroup, overwrite)
                } catch (e: Exception) {
                    // Try as bulk export
                    try {
                        val bulkExport = json.decodeFromString<BulkExportData>(content)
                        return@withContext importBulkExport(bulkExport, targetGroup, overwrite)
                    } catch (e2: Exception) {
                        return@withContext ImportResult(
                            success = false,
                            playersImported = 0,
                            playersSkipped = 0,
                            playersFailed = 0,
                            errors = listOf("Invalid JSON format: ${e2.message}"),
                            message = "Import failed: Invalid JSON format"
                        )
                    }
                }
            } catch (e: Exception) {
                Logging.error("Failed to import from $filePath", e)
                ImportResult(
                    success = false,
                    playersImported = 0,
                    playersSkipped = 0,
                    playersFailed = 0,
                    errors = listOf("Import failed: ${e.message}"),
                    message = "Import failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Lists available export files.
     */
    fun listExportFiles(): List<File> {
        return exportDir.listFiles { file ->
            file.extension.lowercase() == "json"
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Gets the export directory path.
     */
    fun getExportDirectory(): File = exportDir

    // =========================================================================
    // Private Helper Methods
    // =========================================================================

    private fun playerDataToExport(data: PlayerData): PlayerExportData {
        val inventoryMap = data.mainInventory.mapKeys { it.key.toString() }
            .mapValues { serializeItem(it.value) }

        val armorMap = data.armorInventory.mapKeys { it.key.toString() }
            .mapValues { serializeItem(it.value) }

        val enderChestMap = data.enderChest.mapKeys { it.key.toString() }
            .mapValues { serializeItem(it.value) }

        val effects = data.potionEffects.map { effect ->
            SerializedEffect(
                type = effect.type.key.toString(),
                duration = effect.duration,
                amplifier = effect.amplifier,
                ambient = effect.isAmbient,
                particles = effect.hasParticles(),
                icon = effect.hasIcon()
            )
        }

        return PlayerExportData(
            version = "1.1.0",
            exported = dateFormatter.format(data.timestamp),
            player = PlayerInfo(
                uuid = data.uuid.toString(),
                name = data.playerName
            ),
            group = data.group,
            data = InventoryData(
                inventory = inventoryMap,
                armor = armorMap,
                offhand = data.offhand?.let { serializeItem(it) },
                enderChest = enderChestMap,
                experience = data.totalExperience,
                level = data.level,
                health = data.health,
                maxHealth = data.maxHealth,
                hunger = data.foodLevel,
                saturation = data.saturation,
                exhaustion = data.exhaustion,
                effects = effects,
                gameMode = data.gameMode.name
            )
        )
    }

    private fun serializeItem(item: ItemStack): SerializedItem {
        return SerializedItem(
            type = item.type.name,
            amount = item.amount,
            data = InventorySerializer.serializeItemStack(item)
        )
    }

    private fun deserializeItem(item: SerializedItem): ItemStack? {
        return InventorySerializer.deserializeItemStack(item.data)
    }

    private fun exportToPlayerData(export: PlayerExportData, targetGroup: String? = null): PlayerData {
        val uuid = UUID.fromString(export.player.uuid)
        val group = targetGroup ?: export.group
        val gameMode = try {
            GameMode.valueOf(export.data.gameMode)
        } catch (e: Exception) {
            GameMode.SURVIVAL
        }

        val data = PlayerData(uuid, export.player.name, group, gameMode)

        // Deserialize inventory
        for ((slot, item) in export.data.inventory) {
            val slotNum = slot.toIntOrNull() ?: continue
            deserializeItem(item)?.let { data.mainInventory[slotNum] = it }
        }

        // Deserialize armor
        for ((slot, item) in export.data.armor) {
            val slotNum = slot.toIntOrNull() ?: continue
            deserializeItem(item)?.let { data.armorInventory[slotNum] = it }
        }

        // Deserialize offhand
        export.data.offhand?.let { deserializeItem(it) }?.let { data.offhand = it }

        // Deserialize ender chest
        for ((slot, item) in export.data.enderChest) {
            val slotNum = slot.toIntOrNull() ?: continue
            deserializeItem(item)?.let { data.enderChest[slotNum] = it }
        }

        // Set stats
        data.totalExperience = export.data.experience
        data.level = export.data.level
        data.health = export.data.health
        data.maxHealth = export.data.maxHealth
        data.foodLevel = export.data.hunger
        data.saturation = export.data.saturation
        data.exhaustion = export.data.exhaustion

        // Deserialize effects
        for (effect in export.data.effects) {
            try {
                val potionEffect = PotionSerializer.deserializeEffect(effect.type, effect.duration, effect.amplifier, effect.ambient, effect.particles, effect.icon)
                potionEffect?.let { data.potionEffects.add(it) }
            } catch (e: Exception) {
                Logging.debug { "Failed to deserialize effect ${effect.type}: ${e.message}" }
            }
        }

        return data
    }

    private fun validateSingleExport(
        export: PlayerExportData,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ): ValidationResult {
        // Validate UUID
        try {
            UUID.fromString(export.player.uuid)
        } catch (e: Exception) {
            errors.add("Invalid player UUID: ${export.player.uuid}")
        }

        // Check version
        if (export.version != "1.1.0") {
            warnings.add("Export version ${export.version} may not be fully compatible")
        }

        // Validate inventory data exists
        val itemCount = export.data.inventory.size + export.data.armor.size + export.data.enderChest.size
        if (itemCount == 0 && export.data.offhand == null) {
            warnings.add("Export contains no items")
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            version = export.version,
            playerCount = 1,
            groupName = export.group,
            errors = errors,
            warnings = warnings
        )
    }

    private fun validateBulkExport(
        export: BulkExportData,
        errors: MutableList<String>,
        warnings: MutableList<String>
    ): ValidationResult {
        if (export.players.isEmpty()) {
            errors.add("Export contains no players")
        }

        // Check version
        if (export.version != "1.1.0") {
            warnings.add("Export version ${export.version} may not be fully compatible")
        }

        // Validate each player
        var invalidPlayers = 0
        for (player in export.players) {
            try {
                UUID.fromString(player.player.uuid)
            } catch (e: Exception) {
                invalidPlayers++
            }
        }

        if (invalidPlayers > 0) {
            warnings.add("$invalidPlayers player(s) have invalid UUIDs")
        }

        return ValidationResult(
            valid = errors.isEmpty(),
            version = export.version,
            playerCount = export.playerCount,
            groupName = export.group,
            errors = errors,
            warnings = warnings
        )
    }

    private suspend fun importSinglePlayer(
        export: PlayerExportData,
        targetPlayerUUID: UUID?,
        targetGroup: String?,
        overwrite: Boolean
    ): ImportResult {
        val uuid = targetPlayerUUID ?: try {
            UUID.fromString(export.player.uuid)
        } catch (e: Exception) {
            return ImportResult(
                success = false,
                playersImported = 0,
                playersSkipped = 0,
                playersFailed = 1,
                errors = listOf("Invalid player UUID: ${export.player.uuid}"),
                message = "Import failed: Invalid player UUID"
            )
        }

        val group = targetGroup ?: export.group

        // Check if data exists
        val existingData = storageService.loadPlayerData(uuid, group, null)
        if (existingData != null && !overwrite) {
            return ImportResult(
                success = true,
                playersImported = 0,
                playersSkipped = 1,
                playersFailed = 0,
                errors = emptyList(),
                message = "Skipped: Data already exists for player (use --overwrite to replace)"
            )
        }

        val playerData = exportToPlayerData(export, group)
        val success = storageService.savePlayerData(playerData)

        if (success) {
            Logging.info("Imported player ${export.player.name} to group $group")
            return ImportResult(
                success = true,
                playersImported = 1,
                playersSkipped = 0,
                playersFailed = 0,
                errors = emptyList(),
                message = "Successfully imported player ${export.player.name}"
            )
        } else {
            return ImportResult(
                success = false,
                playersImported = 0,
                playersSkipped = 0,
                playersFailed = 1,
                errors = listOf("Failed to save player data"),
                message = "Import failed: Could not save player data"
            )
        }
    }

    private suspend fun importBulkExport(
        export: BulkExportData,
        targetGroup: String?,
        overwrite: Boolean
    ): ImportResult {
        val group = targetGroup ?: export.group
        var imported = 0
        var skipped = 0
        var failed = 0
        val errors = mutableListOf<String>()

        for (playerExport in export.players) {
            try {
                val uuid = UUID.fromString(playerExport.player.uuid)

                // Check if data exists
                val existingData = storageService.loadPlayerData(uuid, group, null)
                if (existingData != null && !overwrite) {
                    skipped++
                    continue
                }

                val playerData = exportToPlayerData(playerExport, group)
                val success = storageService.savePlayerData(playerData)

                if (success) {
                    imported++
                } else {
                    failed++
                    errors.add("Failed to save: ${playerExport.player.name}")
                }
            } catch (e: Exception) {
                failed++
                errors.add("Error importing ${playerExport.player.name}: ${e.message}")
            }
        }

        val message = StringBuilder()
        message.append("Imported $imported player(s)")
        if (skipped > 0) message.append(", skipped $skipped")
        if (failed > 0) message.append(", failed $failed")

        Logging.info("Bulk import to group $group: imported=$imported, skipped=$skipped, failed=$failed")

        return ImportResult(
            success = failed == 0,
            playersImported = imported,
            playersSkipped = skipped,
            playersFailed = failed,
            errors = errors,
            message = message.toString()
        )
    }

    private fun resolveFile(filePath: String): File? {
        // Try as absolute path first
        val absoluteFile = File(filePath)
        if (absoluteFile.exists()) return absoluteFile

        // Try in exports directory
        val exportFile = File(exportDir, filePath)
        if (exportFile.exists()) return exportFile

        // Try with .json extension
        val withExtension = File(exportDir, "$filePath.json")
        if (withExtension.exists()) return withExtension

        return null
    }
}

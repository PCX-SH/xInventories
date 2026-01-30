package sh.pcx.xinventories.internal.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.model.AuditAction
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Represents the progress of a bulk operation.
 */
data class BulkOperationProgress(
    val operationId: String,
    val operationType: BulkOperationType,
    val group: String,
    val totalPlayers: Int,
    val processedPlayers: AtomicInteger = AtomicInteger(0),
    val successCount: AtomicInteger = AtomicInteger(0),
    val failCount: AtomicInteger = AtomicInteger(0),
    val startTime: Instant = Instant.now(),
    var endTime: Instant? = null,
    var status: BulkOperationStatus = BulkOperationStatus.RUNNING,
    var error: String? = null
) {
    val processed: Int get() = processedPlayers.get()
    val successful: Int get() = successCount.get()
    val failed: Int get() = failCount.get()
    val percentComplete: Int get() = if (totalPlayers > 0) (processed * 100) / totalPlayers else 0
    val isComplete: Boolean get() = status != BulkOperationStatus.RUNNING

    fun markSuccess() {
        processedPlayers.incrementAndGet()
        successCount.incrementAndGet()
    }

    fun markFailure() {
        processedPlayers.incrementAndGet()
        failCount.incrementAndGet()
    }

    fun complete(newStatus: BulkOperationStatus = BulkOperationStatus.COMPLETED) {
        status = newStatus
        endTime = Instant.now()
    }
}

/**
 * Types of bulk operations.
 */
enum class BulkOperationType {
    CLEAR,
    APPLY_TEMPLATE,
    RESET_STATS,
    EXPORT
}

/**
 * Status of a bulk operation.
 */
enum class BulkOperationStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Result of a bulk operation.
 */
data class BulkOperationResult(
    val operationId: String,
    val operationType: BulkOperationType,
    val group: String,
    val totalPlayers: Int,
    val successCount: Int,
    val failCount: Int,
    val durationMs: Long,
    val status: BulkOperationStatus,
    val error: String? = null
)

/**
 * Service for performing bulk operations on player inventories.
 * Operations are executed asynchronously with progress reporting.
 */
class BulkOperationService(
    private val plugin: PluginContext,
    private val scope: CoroutineScope
) {
    private val activeOperations = ConcurrentHashMap<String, Pair<BulkOperationProgress, Job>>()
    private var operationCounter = 0

    /**
     * Gets all active bulk operations.
     */
    fun getActiveOperations(): List<BulkOperationProgress> {
        return activeOperations.values.map { it.first }
    }

    /**
     * Gets the progress of a specific operation.
     */
    fun getProgress(operationId: String): BulkOperationProgress? {
        return activeOperations[operationId]?.first
    }

    /**
     * Cancels an active operation.
     */
    suspend fun cancelOperation(operationId: String): Boolean {
        val operation = activeOperations[operationId] ?: return false
        operation.second.cancelAndJoin()
        operation.first.complete(BulkOperationStatus.CANCELLED)
        activeOperations.remove(operationId)
        return true
    }

    /**
     * Clears all inventories in a group.
     */
    suspend fun clearGroup(
        sender: CommandSender,
        groupName: String,
        onProgress: ((BulkOperationProgress) -> Unit)? = null
    ): BulkOperationResult {
        val operationId = generateOperationId()
        val storageService = plugin.serviceManager.storageService
        val auditService = plugin.serviceManager.auditService

        // Get all players with data in this group
        val allUuids = storageService.getAllPlayerUUIDs()
        val playersInGroup = mutableListOf<UUID>()

        for (uuid in allUuids) {
            val groups = storageService.getPlayerGroups(uuid)
            if (groupName in groups) {
                playersInGroup.add(uuid)
            }
        }

        val progress = BulkOperationProgress(
            operationId = operationId,
            operationType = BulkOperationType.CLEAR,
            group = groupName,
            totalPlayers = playersInGroup.size
        )

        val job = scope.launch {
            try {
                for (uuid in playersInGroup) {
                    if (!isActive) {
                        progress.complete(BulkOperationStatus.CANCELLED)
                        break
                    }

                    try {
                        // Delete data for all game modes
                        for (gameMode in GameMode.entries) {
                            storageService.deletePlayerData(uuid, groupName, gameMode)
                        }
                        storageService.deletePlayerData(uuid, groupName, null)
                        progress.markSuccess()
                    } catch (e: Exception) {
                        Logging.error("Failed to clear inventory for $uuid in $groupName", e)
                        progress.markFailure()
                    }

                    onProgress?.invoke(progress)

                    // Yield to prevent blocking
                    if (progress.processed % 10 == 0) {
                        delay(1)
                    }
                }

                if (progress.status == BulkOperationStatus.RUNNING) {
                    progress.complete()
                }

                // Log audit entry
                auditService?.logBulkOperation(
                    sender,
                    AuditAction.BULK_CLEAR,
                    groupName,
                    progress.successful
                )
            } catch (e: Exception) {
                progress.error = e.message
                progress.complete(BulkOperationStatus.FAILED)
                Logging.error("Bulk clear operation failed", e)
            } finally {
                activeOperations.remove(operationId)
            }
        }

        activeOperations[operationId] = progress to job

        // Wait for completion
        job.join()

        return BulkOperationResult(
            operationId = operationId,
            operationType = BulkOperationType.CLEAR,
            group = groupName,
            totalPlayers = progress.totalPlayers,
            successCount = progress.successful,
            failCount = progress.failed,
            durationMs = java.time.Duration.between(progress.startTime, progress.endTime ?: Instant.now()).toMillis(),
            status = progress.status,
            error = progress.error
        )
    }

    /**
     * Applies a template to all players in a group.
     */
    suspend fun applyTemplateToGroup(
        sender: CommandSender,
        groupName: String,
        templateName: String,
        onProgress: ((BulkOperationProgress) -> Unit)? = null
    ): BulkOperationResult {
        val operationId = generateOperationId()
        val storageService = plugin.serviceManager.storageService
        val templateService = plugin.serviceManager.templateService
        val auditService = plugin.serviceManager.auditService

        // Verify template exists
        val template = templateService.getTemplate(templateName)
            ?: return BulkOperationResult(
                operationId = operationId,
                operationType = BulkOperationType.APPLY_TEMPLATE,
                group = groupName,
                totalPlayers = 0,
                successCount = 0,
                failCount = 0,
                durationMs = 0,
                status = BulkOperationStatus.FAILED,
                error = "Template '$templateName' not found"
            )

        // Get all players with data in this group
        val allUuids = storageService.getAllPlayerUUIDs()
        val playersInGroup = mutableListOf<UUID>()

        for (uuid in allUuids) {
            val groups = storageService.getPlayerGroups(uuid)
            if (groupName in groups) {
                playersInGroup.add(uuid)
            }
        }

        val progress = BulkOperationProgress(
            operationId = operationId,
            operationType = BulkOperationType.APPLY_TEMPLATE,
            group = groupName,
            totalPlayers = playersInGroup.size
        )

        val job = scope.launch {
            try {
                for (uuid in playersInGroup) {
                    if (!isActive) {
                        progress.complete(BulkOperationStatus.CANCELLED)
                        break
                    }

                    try {
                        // Load existing data or create new
                        var data = storageService.loadPlayerData(uuid, groupName, GameMode.SURVIVAL)
                        if (data == null) {
                            val playerName = Bukkit.getOfflinePlayer(uuid).name ?: "Unknown"
                            data = PlayerData(uuid, playerName, groupName, GameMode.SURVIVAL)
                        }

                        // Apply template data to PlayerData
                        val templateData = template.inventory
                        data.mainInventory.clear()
                        templateData.mainInventory.forEach { (slot, item) ->
                            data.mainInventory[slot] = item.clone()
                        }
                        data.armorInventory.clear()
                        templateData.armorInventory.forEach { (slot, item) ->
                            data.armorInventory[slot] = item.clone()
                        }
                        data.offhand = templateData.offhand?.clone()
                        data.enderChest.clear()
                        templateData.enderChest.forEach { (slot, item) ->
                            data.enderChest[slot] = item.clone()
                        }
                        data.health = templateData.health
                        data.maxHealth = templateData.maxHealth
                        data.foodLevel = templateData.foodLevel
                        data.saturation = templateData.saturation
                        data.experience = templateData.experience
                        data.level = templateData.level
                        data.potionEffects.clear()
                        data.potionEffects.addAll(templateData.potionEffects)
                        data.dirty = true

                        // Save
                        storageService.savePlayerDataImmediate(data)
                        progress.markSuccess()
                    } catch (e: Exception) {
                        Logging.error("Failed to apply template to $uuid in $groupName", e)
                        progress.markFailure()
                    }

                    onProgress?.invoke(progress)

                    // Yield to prevent blocking
                    if (progress.processed % 10 == 0) {
                        delay(1)
                    }
                }

                if (progress.status == BulkOperationStatus.RUNNING) {
                    progress.complete()
                }

                // Log audit entry
                auditService?.logBulkOperation(
                    sender,
                    AuditAction.TEMPLATE_APPLY,
                    groupName,
                    progress.successful,
                    "Template: $templateName"
                )
            } catch (e: Exception) {
                progress.error = e.message
                progress.complete(BulkOperationStatus.FAILED)
                Logging.error("Bulk template apply operation failed", e)
            } finally {
                activeOperations.remove(operationId)
            }
        }

        activeOperations[operationId] = progress to job

        // Wait for completion
        job.join()

        return BulkOperationResult(
            operationId = operationId,
            operationType = BulkOperationType.APPLY_TEMPLATE,
            group = groupName,
            totalPlayers = progress.totalPlayers,
            successCount = progress.successful,
            failCount = progress.failed,
            durationMs = java.time.Duration.between(progress.startTime, progress.endTime ?: Instant.now()).toMillis(),
            status = progress.status,
            error = progress.error
        )
    }

    /**
     * Resets statistics for all players in a group.
     */
    suspend fun resetStatsForGroup(
        sender: CommandSender,
        groupName: String,
        onProgress: ((BulkOperationProgress) -> Unit)? = null
    ): BulkOperationResult {
        val operationId = generateOperationId()
        val storageService = plugin.serviceManager.storageService
        val auditService = plugin.serviceManager.auditService

        // Get all players with data in this group
        val allUuids = storageService.getAllPlayerUUIDs()
        val playersInGroup = mutableListOf<UUID>()

        for (uuid in allUuids) {
            val groups = storageService.getPlayerGroups(uuid)
            if (groupName in groups) {
                playersInGroup.add(uuid)
            }
        }

        val progress = BulkOperationProgress(
            operationId = operationId,
            operationType = BulkOperationType.RESET_STATS,
            group = groupName,
            totalPlayers = playersInGroup.size
        )

        val job = scope.launch {
            try {
                for (uuid in playersInGroup) {
                    if (!isActive) {
                        progress.complete(BulkOperationStatus.CANCELLED)
                        break
                    }

                    try {
                        // Load and reset stats for all game modes
                        for (gameMode in listOf(GameMode.SURVIVAL, GameMode.CREATIVE, GameMode.ADVENTURE, GameMode.SPECTATOR, null)) {
                            val data = storageService.loadPlayerData(uuid, groupName, gameMode)
                            if (data != null) {
                                data.resetState()
                                storageService.savePlayerDataImmediate(data)
                            }
                        }
                        progress.markSuccess()
                    } catch (e: Exception) {
                        Logging.error("Failed to reset stats for $uuid in $groupName", e)
                        progress.markFailure()
                    }

                    onProgress?.invoke(progress)

                    // Yield to prevent blocking
                    if (progress.processed % 10 == 0) {
                        delay(1)
                    }
                }

                if (progress.status == BulkOperationStatus.RUNNING) {
                    progress.complete()
                }

                // Log audit entry
                auditService?.logBulkOperation(
                    sender,
                    AuditAction.BULK_RESET_STATS,
                    groupName,
                    progress.successful
                )
            } catch (e: Exception) {
                progress.error = e.message
                progress.complete(BulkOperationStatus.FAILED)
                Logging.error("Bulk reset stats operation failed", e)
            } finally {
                activeOperations.remove(operationId)
            }
        }

        activeOperations[operationId] = progress to job

        // Wait for completion
        job.join()

        return BulkOperationResult(
            operationId = operationId,
            operationType = BulkOperationType.RESET_STATS,
            group = groupName,
            totalPlayers = progress.totalPlayers,
            successCount = progress.successful,
            failCount = progress.failed,
            durationMs = java.time.Duration.between(progress.startTime, progress.endTime ?: Instant.now()).toMillis(),
            status = progress.status,
            error = progress.error
        )
    }

    /**
     * Exports all player data in a group to a file.
     */
    suspend fun exportGroup(
        sender: CommandSender,
        groupName: String,
        outputFile: File,
        onProgress: ((BulkOperationProgress) -> Unit)? = null
    ): BulkOperationResult {
        val operationId = generateOperationId()
        val storageService = plugin.serviceManager.storageService
        val auditService = plugin.serviceManager.auditService

        // Get all players with data in this group
        val allUuids = storageService.getAllPlayerUUIDs()
        val playersInGroup = mutableListOf<UUID>()

        for (uuid in allUuids) {
            val groups = storageService.getPlayerGroups(uuid)
            if (groupName in groups) {
                playersInGroup.add(uuid)
            }
        }

        val progress = BulkOperationProgress(
            operationId = operationId,
            operationType = BulkOperationType.EXPORT,
            group = groupName,
            totalPlayers = playersInGroup.size
        )

        val job = scope.launch {
            try {
                val exportData = mutableListOf<Map<String, Any?>>()

                for (uuid in playersInGroup) {
                    if (!isActive) {
                        progress.complete(BulkOperationStatus.CANCELLED)
                        break
                    }

                    try {
                        val allData = storageService.loadAllPlayerData(uuid)
                        val groupData = allData.filter { it.value.group == groupName }

                        for ((key, data) in groupData) {
                            val snapshot = data.toSnapshot()
                            exportData.add(mapOf(
                                "uuid" to uuid.toString(),
                                "playerName" to data.playerName,
                                "group" to groupName,
                                "gameMode" to data.gameMode.name,
                                "health" to data.health,
                                "foodLevel" to data.foodLevel,
                                "level" to data.level,
                                "experience" to data.experience,
                                "timestamp" to data.timestamp.toString()
                            ))
                        }
                        progress.markSuccess()
                    } catch (e: Exception) {
                        Logging.error("Failed to export data for $uuid in $groupName", e)
                        progress.markFailure()
                    }

                    onProgress?.invoke(progress)

                    // Yield to prevent blocking
                    if (progress.processed % 10 == 0) {
                        delay(1)
                    }
                }

                if (progress.status == BulkOperationStatus.RUNNING) {
                    // Write to file
                    withContext(Dispatchers.IO) {
                        outputFile.parentFile?.mkdirs()
                        outputFile.writeText(com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(exportData))
                    }
                    progress.complete()
                }

                // Log audit entry
                auditService?.logBulkOperation(
                    sender,
                    AuditAction.BULK_EXPORT,
                    groupName,
                    progress.successful,
                    "File: ${outputFile.name}"
                )
            } catch (e: Exception) {
                progress.error = e.message
                progress.complete(BulkOperationStatus.FAILED)
                Logging.error("Bulk export operation failed", e)
            } finally {
                activeOperations.remove(operationId)
            }
        }

        activeOperations[operationId] = progress to job

        // Wait for completion
        job.join()

        return BulkOperationResult(
            operationId = operationId,
            operationType = BulkOperationType.EXPORT,
            group = groupName,
            totalPlayers = progress.totalPlayers,
            successCount = progress.successful,
            failCount = progress.failed,
            durationMs = java.time.Duration.between(progress.startTime, progress.endTime ?: Instant.now()).toMillis(),
            status = progress.status,
            error = progress.error
        )
    }

    @Synchronized
    private fun generateOperationId(): String {
        return "bulk-${++operationCounter}-${System.currentTimeMillis()}"
    }
}

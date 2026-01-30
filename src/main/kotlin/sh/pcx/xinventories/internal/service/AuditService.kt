package sh.pcx.xinventories.internal.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.AuditConfig
import sh.pcx.xinventories.internal.model.AuditAction
import sh.pcx.xinventories.internal.model.AuditEntry
import sh.pcx.xinventories.internal.storage.AuditStorage
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Service for tracking and querying audit log entries.
 * Provides comprehensive logging of all inventory operations for security and debugging.
 */
class AuditService(
    private val plugin: XInventories,
    private val scope: CoroutineScope
) {
    private lateinit var storage: AuditStorage
    private var cleanupJob: Job? = null
    private var flushJob: Job? = null
    private var initialized = false

    /**
     * Current audit configuration.
     */
    var config: AuditConfig = AuditConfig()
        private set

    /**
     * Whether audit logging is enabled.
     */
    val isEnabled: Boolean
        get() = config.enabled && initialized

    /**
     * Initializes the audit service.
     */
    suspend fun initialize() {
        if (initialized) return

        // Load config
        loadConfig()

        if (!config.enabled) {
            Logging.info("Audit logging is disabled")
            initialized = true
            return
        }

        // Initialize storage
        storage = AuditStorage(plugin)
        storage.initialize()

        // Start periodic jobs
        startCleanupJob()
        startFlushJob()

        initialized = true
        Logging.info("AuditService initialized (retention: ${config.retentionDays} days)")
    }

    /**
     * Shuts down the audit service.
     */
    suspend fun shutdown() {
        if (!initialized) return

        cleanupJob?.cancel()
        flushJob?.cancel()

        if (config.enabled) {
            storage.flushBuffer()
            storage.shutdown()
        }

        initialized = false
        Logging.debug { "AuditService shut down" }
    }

    /**
     * Reloads the audit service configuration.
     */
    fun reload() {
        loadConfig()
        Logging.debug { "AuditService config reloaded" }
    }

    private fun loadConfig() {
        config = plugin.configManager.mainConfig.audit
    }

    // =========================================================================
    // Logging Methods
    // =========================================================================

    /**
     * Logs an inventory save action.
     */
    fun logSave(player: Player, group: String) {
        if (!isEnabled || !config.logSaves) return

        record(AuditEntry.system(
            target = player.uniqueId,
            targetName = player.name,
            action = AuditAction.INVENTORY_SAVE,
            group = group,
            serverId = getServerId()
        ))
    }

    /**
     * Logs an inventory load action.
     */
    fun logLoad(player: Player, group: String) {
        if (!isEnabled || !config.logSaves) return

        record(AuditEntry.system(
            target = player.uniqueId,
            targetName = player.name,
            action = AuditAction.INVENTORY_LOAD,
            group = group,
            serverId = getServerId()
        ))
    }

    /**
     * Logs an admin view action.
     */
    fun logAdminView(admin: CommandSender, targetUuid: UUID, targetName: String, group: String?) {
        if (!isEnabled || !config.logViews) return

        val entry = if (admin is Player) {
            AuditEntry.player(
                actor = admin.uniqueId,
                actorName = admin.name,
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.ADMIN_VIEW,
                group = group,
                serverId = getServerId()
            )
        } else {
            AuditEntry.console(
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.ADMIN_VIEW,
                group = group,
                serverId = getServerId()
            )
        }

        record(entry)
    }

    /**
     * Logs an admin edit action.
     */
    fun logAdminEdit(admin: CommandSender, targetUuid: UUID, targetName: String, group: String?, details: String? = null) {
        if (!isEnabled) return

        val entry = if (admin is Player) {
            AuditEntry.player(
                actor = admin.uniqueId,
                actorName = admin.name,
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.ADMIN_EDIT,
                group = group,
                details = details,
                serverId = getServerId()
            )
        } else {
            AuditEntry.console(
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.ADMIN_EDIT,
                group = group,
                details = details,
                serverId = getServerId()
            )
        }

        record(entry)
    }

    /**
     * Logs an inventory clear action.
     */
    fun logClear(admin: CommandSender, targetUuid: UUID, targetName: String, group: String?) {
        if (!isEnabled) return

        val entry = if (admin is Player) {
            AuditEntry.player(
                actor = admin.uniqueId,
                actorName = admin.name,
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.INVENTORY_CLEAR,
                group = group,
                serverId = getServerId()
            )
        } else {
            AuditEntry.console(
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.INVENTORY_CLEAR,
                group = group,
                serverId = getServerId()
            )
        }

        record(entry)
    }

    /**
     * Logs a version restore action.
     */
    fun logVersionRestore(admin: CommandSender, targetUuid: UUID, targetName: String, group: String, versionId: Long) {
        if (!isEnabled) return

        val entry = if (admin is Player) {
            AuditEntry.player(
                actor = admin.uniqueId,
                actorName = admin.name,
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.VERSION_RESTORE,
                group = group,
                details = "Restored version #$versionId",
                serverId = getServerId()
            )
        } else {
            AuditEntry.console(
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.VERSION_RESTORE,
                group = group,
                details = "Restored version #$versionId",
                serverId = getServerId()
            )
        }

        record(entry)
    }

    /**
     * Logs a death restore action.
     */
    fun logDeathRestore(admin: CommandSender, targetUuid: UUID, targetName: String, group: String, deathId: Long) {
        if (!isEnabled) return

        val entry = if (admin is Player) {
            AuditEntry.player(
                actor = admin.uniqueId,
                actorName = admin.name,
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.DEATH_RESTORE,
                group = group,
                details = "Restored death #$deathId",
                serverId = getServerId()
            )
        } else {
            AuditEntry.console(
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.DEATH_RESTORE,
                group = group,
                details = "Restored death #$deathId",
                serverId = getServerId()
            )
        }

        record(entry)
    }

    /**
     * Logs a template apply action.
     */
    fun logTemplateApply(admin: CommandSender?, targetUuid: UUID, targetName: String, group: String, templateName: String) {
        if (!isEnabled) return

        val entry = when {
            admin is Player -> AuditEntry.player(
                actor = admin.uniqueId,
                actorName = admin.name,
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.TEMPLATE_APPLY,
                group = group,
                details = "Applied template '$templateName'",
                serverId = getServerId()
            )
            admin != null -> AuditEntry.console(
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.TEMPLATE_APPLY,
                group = group,
                details = "Applied template '$templateName'",
                serverId = getServerId()
            )
            else -> AuditEntry.system(
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.TEMPLATE_APPLY,
                group = group,
                details = "Applied template '$templateName'",
                serverId = getServerId()
            )
        }

        record(entry)
    }

    /**
     * Logs a lock action.
     */
    fun logLock(admin: CommandSender, targetUuid: UUID, targetName: String, reason: String?) {
        if (!isEnabled) return

        val entry = if (admin is Player) {
            AuditEntry.player(
                actor = admin.uniqueId,
                actorName = admin.name,
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.LOCK_APPLY,
                details = reason,
                serverId = getServerId()
            )
        } else {
            AuditEntry.console(
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.LOCK_APPLY,
                details = reason,
                serverId = getServerId()
            )
        }

        record(entry)
    }

    /**
     * Logs an unlock action.
     */
    fun logUnlock(admin: CommandSender, targetUuid: UUID, targetName: String) {
        if (!isEnabled) return

        val entry = if (admin is Player) {
            AuditEntry.player(
                actor = admin.uniqueId,
                actorName = admin.name,
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.LOCK_REMOVE,
                serverId = getServerId()
            )
        } else {
            AuditEntry.console(
                target = targetUuid,
                targetName = targetName,
                action = AuditAction.LOCK_REMOVE,
                serverId = getServerId()
            )
        }

        record(entry)
    }

    /**
     * Logs a bulk operation.
     */
    fun logBulkOperation(admin: CommandSender, action: AuditAction, group: String, affectedCount: Int, details: String? = null) {
        if (!isEnabled) return

        // Create a "bulk" target entry
        val bulkUuid = UUID(0, 0) // Placeholder UUID for bulk operations
        val fullDetails = "Affected $affectedCount players" + (if (details != null) " - $details" else "")

        val entry = if (admin is Player) {
            AuditEntry.player(
                actor = admin.uniqueId,
                actorName = admin.name,
                target = bulkUuid,
                targetName = "BULK",
                action = action,
                group = group,
                details = fullDetails,
                serverId = getServerId()
            )
        } else {
            AuditEntry.console(
                target = bulkUuid,
                targetName = "BULK",
                action = action,
                group = group,
                details = fullDetails,
                serverId = getServerId()
            )
        }

        record(entry)
    }

    /**
     * Logs a group change.
     */
    fun logGroupChange(player: Player, fromGroup: String, toGroup: String) {
        if (!isEnabled) return

        record(AuditEntry.system(
            target = player.uniqueId,
            targetName = player.name,
            action = AuditAction.GROUP_CHANGE,
            group = toGroup,
            details = "Changed from '$fromGroup' to '$toGroup'",
            serverId = getServerId()
        ))
    }

    // =========================================================================
    // Query Methods
    // =========================================================================

    /**
     * Gets audit entries for a player.
     */
    suspend fun getEntriesForPlayer(uuid: UUID, limit: Int = 50): List<AuditEntry> {
        if (!isEnabled) return emptyList()
        return storage.getEntriesForPlayer(uuid, limit)
    }

    /**
     * Searches audit entries by action type.
     */
    suspend fun searchByAction(
        action: AuditAction,
        from: Instant? = null,
        to: Instant? = null,
        limit: Int = 100
    ): List<AuditEntry> {
        if (!isEnabled) return emptyList()
        return storage.searchByAction(action, from, to, limit)
    }

    /**
     * Gets audit entries within a date range.
     */
    suspend fun getEntriesInRange(from: Instant, to: Instant, limit: Int = 1000): List<AuditEntry> {
        if (!isEnabled) return emptyList()
        return storage.getEntriesInRange(from, to, limit)
    }

    /**
     * Exports audit entries to a CSV file.
     */
    suspend fun exportToCsv(entries: List<AuditEntry>, file: File): Boolean {
        if (!isEnabled) return false

        try {
            val csv = storage.exportToCsv(entries)
            file.parentFile?.mkdirs()
            file.writeText(csv)
            return true
        } catch (e: Exception) {
            Logging.error("Failed to export audit log to ${file.path}", e)
            return false
        }
    }

    /**
     * Gets the total number of audit entries.
     */
    suspend fun getEntryCount(): Int {
        if (!isEnabled) return 0
        return storage.getEntryCount()
    }

    /**
     * Gets the audit storage size in bytes.
     */
    fun getStorageSize(): Long {
        if (!isEnabled) return 0
        return storage.getStorageSize()
    }

    // =========================================================================
    // Internal Methods
    // =========================================================================

    private fun record(entry: AuditEntry) {
        if (!isEnabled) return
        storage.record(entry)
        Logging.debug { "Audit: ${entry.toDisplayString()}" }
    }

    private fun getServerId(): String? {
        val syncConfig = plugin.serviceManager.syncConfig
        return syncConfig?.serverId
    }

    private fun startCleanupJob() {
        cleanupJob = scope.launch {
            // Run cleanup daily
            while (isActive) {
                delay(24 * 60 * 60 * 1000L) // 24 hours

                try {
                    storage.cleanup(config.retentionDays)
                } catch (e: Exception) {
                    Logging.error("Audit cleanup failed", e)
                }
            }
        }
    }

    private fun startFlushJob() {
        flushJob = scope.launch {
            // Flush buffer every 30 seconds
            while (isActive) {
                delay(30_000L)

                try {
                    storage.flushBuffer()
                } catch (e: Exception) {
                    Logging.error("Audit flush failed", e)
                }
            }
        }
    }
}

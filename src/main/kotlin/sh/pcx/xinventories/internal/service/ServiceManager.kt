package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope

/**
 * Dependency injection container for all services.
 */
class ServiceManager(
    private val plugin: XInventories,
    private val scope: CoroutineScope
) {
    // Services
    lateinit var messageService: MessageService
        private set

    lateinit var groupService: GroupService
        private set

    lateinit var storageService: StorageService
        private set

    lateinit var inventoryService: InventoryService
        private set

    lateinit var backupService: BackupService
        private set

    lateinit var migrationService: MigrationService
        private set

    private var initialized = false

    /**
     * Initializes all services.
     */
    suspend fun initialize() {
        if (initialized) {
            Logging.warning("ServiceManager already initialized")
            return
        }

        Logging.debug { "Initializing services..." }

        // Initialize services in dependency order

        // Message service (no dependencies)
        messageService = MessageService(plugin)
        Logging.debug { "MessageService initialized" }

        // Group service (no dependencies)
        groupService = GroupService(plugin)
        groupService.initialize()
        Logging.debug { "GroupService initialized" }

        // Storage service (no service dependencies)
        storageService = StorageService(plugin, scope)
        storageService.initialize()
        Logging.debug { "StorageService initialized" }

        // Inventory service (depends on storage, group, message)
        inventoryService = InventoryService(
            plugin, scope, storageService, groupService, messageService
        )
        Logging.debug { "InventoryService initialized" }

        // Backup service (depends on storage)
        backupService = BackupService(plugin, scope, storageService)
        backupService.initialize()
        Logging.debug { "BackupService initialized" }

        // Migration service (depends on storage)
        migrationService = MigrationService(plugin, storageService)
        Logging.debug { "MigrationService initialized" }

        initialized = true
        Logging.info("All services initialized")
    }

    /**
     * Shuts down all services.
     */
    suspend fun shutdown() {
        if (!initialized) return

        Logging.debug { "Shutting down services..." }

        // Shutdown in reverse order

        // Backup service
        try {
            backupService.shutdown()
            Logging.debug { "BackupService shut down" }
        } catch (e: Exception) {
            Logging.error("Error shutting down BackupService", e)
        }

        // Storage service (flushes cache, closes connections)
        try {
            storageService.shutdown()
            Logging.debug { "StorageService shut down" }
        } catch (e: Exception) {
            Logging.error("Error shutting down StorageService", e)
        }

        initialized = false
        Logging.info("All services shut down")
    }

    /**
     * Reloads services that support hot-reloading.
     */
    fun reload() {
        Logging.debug { "Reloading services..." }

        // Reload group service
        groupService.reload()

        // Note: Storage service doesn't support hot-reload
        // (would need to restart plugin to change storage type)

        Logging.info("Services reloaded")
    }

    /**
     * Checks if all services are initialized.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Gets service health status.
     */
    suspend fun getHealthStatus(): Map<String, Boolean> {
        return mapOf(
            "storage" to storageService.isHealthy(),
            "cache" to storageService.cache.isEnabled(),
            "groups" to (groupService.getAllGroups().isNotEmpty())
        )
    }
}

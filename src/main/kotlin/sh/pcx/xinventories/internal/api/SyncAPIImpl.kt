package sh.pcx.xinventories.internal.api

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.SyncAPI
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the SyncAPI.
 * Adapts internal SyncService to the public API interface.
 */
class SyncAPIImpl(private val plugin: PluginContext) : SyncAPI {

    private val syncService get() = plugin.serviceManager.syncService

    override val isEnabled: Boolean
        get() = syncService?.isEnabled == true

    override val serverId: String
        get() = syncService?.serverId ?: plugin.configManager.mainConfig.sync.serverId

    override fun getConnectedServers(): List<SyncAPI.ServerInfo> {
        return syncService?.getConnectedServers()?.map { server ->
            SyncAPI.ServerInfo(
                serverId = server.serverId,
                playerCount = server.playerCount,
                lastHeartbeat = server.lastHeartbeat.toEpochMilli(),
                isHealthy = server.isHealthy
            )
        } ?: emptyList()
    }

    override fun isPlayerLocked(uuid: UUID): Boolean {
        return syncService?.isPlayerLocked(uuid) == true
    }

    override fun getLockHolder(uuid: UUID): String? {
        return syncService?.getLockHolder(uuid)
    }

    override fun forceSyncPlayer(uuid: UUID): CompletableFuture<Result<Unit>> {
        val service = syncService
        return if (service != null) {
            service.forceSyncPlayer(uuid)
        } else {
            CompletableFuture.completedFuture(
                Result.failure(IllegalStateException("Sync service is not enabled"))
            )
        }
    }

    override fun broadcastInvalidation(uuid: UUID, group: String?) {
        syncService?.broadcastInvalidation(uuid, group)
    }

    override fun getTotalNetworkPlayerCount(): Int {
        return syncService?.getConnectedServers()?.sumOf { it.playerCount } ?: 0
    }

    override fun isServerHealthy(serverId: String): Boolean {
        return syncService?.getConnectedServers()?.find { it.serverId == serverId }?.isHealthy == true
    }
}

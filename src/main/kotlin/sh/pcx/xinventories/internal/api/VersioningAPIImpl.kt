package sh.pcx.xinventories.internal.api

import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.InventoryVersionInfo
import sh.pcx.xinventories.api.InventoryVersioningAPI
import sh.pcx.xinventories.api.VersionTrigger
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.VersionTrigger as InternalVersionTrigger
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the InventoryVersioningAPI.
 * Adapts internal VersioningService to the public API interface.
 */
class VersioningAPIImpl(private val plugin: XInventories) : InventoryVersioningAPI {

    private val versioningService get() = plugin.serviceManager.versioningService

    override fun isEnabled(): Boolean {
        return plugin.configManager.mainConfig.versioning.enabled
    }

    override fun getVersions(
        playerUuid: UUID,
        group: String?,
        limit: Int
    ): CompletableFuture<List<InventoryVersionInfo>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                versioningService.getVersions(playerUuid, group, limit)
                    .map { it.toApiModel() }
            }
        }
    }

    override fun getVersion(versionId: String): CompletableFuture<InventoryVersionInfo?> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                versioningService.getVersion(versionId)?.toApiModel()
            }
        }
    }

    override fun createVersion(
        player: Player,
        metadata: Map<String, String>
    ): CompletableFuture<InventoryVersionInfo?> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                versioningService.createVersion(
                    player,
                    InternalVersionTrigger.MANUAL,
                    metadata,
                    force = true
                )?.toApiModel()
            }
        }
    }

    override fun restoreVersion(
        player: Player,
        versionId: String,
        createBackup: Boolean
    ): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                versioningService.restoreVersion(player, versionId, createBackup)
            }
        }
    }

    override fun deleteVersion(versionId: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                versioningService.deleteVersion(versionId)
            }
        }
    }

    override fun pruneVersions(olderThan: Instant): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                plugin.serviceManager.storageService.storage.pruneVersions(olderThan)
            }
        }
    }

    override fun getVersionCount(playerUuid: UUID, group: String?): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                versioningService.getVersionCount(playerUuid, group)
            }
        }
    }

    /**
     * Converts internal InventoryVersion to API InventoryVersionInfo.
     */
    private fun InventoryVersion.toApiModel(): InventoryVersionInfo {
        return InventoryVersionInfo(
            id = id,
            playerUuid = playerUuid,
            group = group,
            timestamp = timestamp,
            trigger = when (trigger) {
                InternalVersionTrigger.WORLD_CHANGE -> VersionTrigger.WORLD_CHANGE
                InternalVersionTrigger.DISCONNECT -> VersionTrigger.DISCONNECT
                InternalVersionTrigger.MANUAL -> VersionTrigger.MANUAL
                InternalVersionTrigger.DEATH -> VersionTrigger.DEATH
                InternalVersionTrigger.SCHEDULED -> VersionTrigger.SCHEDULED
            },
            snapshot = data.toSnapshot(),
            metadata = metadata
        )
    }
}

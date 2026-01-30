package sh.pcx.xinventories.internal.api

import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.DeathRecordInfo
import sh.pcx.xinventories.api.DeathRecoveryAPI
import sh.pcx.xinventories.internal.model.DeathRecord
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the DeathRecoveryAPI.
 * Adapts internal DeathRecoveryService to the public API interface.
 */
class DeathRecoveryAPIImpl(private val plugin: PluginContext) : DeathRecoveryAPI {

    private val deathRecoveryService get() = plugin.serviceManager.deathRecoveryService

    override fun isEnabled(): Boolean {
        return plugin.configManager.mainConfig.deathRecovery.enabled
    }

    override fun getDeathRecords(playerUuid: UUID, limit: Int): CompletableFuture<List<DeathRecordInfo>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                deathRecoveryService.getDeathRecords(playerUuid, limit)
                    .map { it.toApiModel() }
            }
        }
    }

    override fun getDeathRecord(deathId: String): CompletableFuture<DeathRecordInfo?> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                deathRecoveryService.getDeathRecord(deathId)?.toApiModel()
            }
        }
    }

    override fun getMostRecentDeath(playerUuid: UUID): CompletableFuture<DeathRecordInfo?> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                deathRecoveryService.getMostRecentDeath(playerUuid)?.toApiModel()
            }
        }
    }

    override fun restoreDeathInventory(player: Player, deathId: String): CompletableFuture<Result<Unit>> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                deathRecoveryService.restoreDeathInventory(player, deathId)
            }
        }
    }

    override fun deleteDeathRecord(deathId: String): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                deathRecoveryService.deleteDeathRecord(deathId)
            }
        }
    }

    override fun pruneDeathRecords(olderThan: Instant): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                plugin.serviceManager.storageService.storage.pruneDeathRecords(olderThan)
            }
        }
    }

    override fun getDeathRecordCount(playerUuid: UUID): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            runBlocking {
                deathRecoveryService.getDeathRecordCount(playerUuid)
            }
        }
    }

    /**
     * Converts internal DeathRecord to API DeathRecordInfo.
     */
    private fun DeathRecord.toApiModel(): DeathRecordInfo {
        return DeathRecordInfo(
            id = id,
            playerUuid = playerUuid,
            timestamp = timestamp,
            world = world,
            x = x,
            y = y,
            z = z,
            deathCause = deathCause,
            killerName = killerName,
            killerUuid = killerUuid,
            group = group,
            snapshot = inventoryData.toSnapshot()
        )
    }
}

package sh.pcx.xinventories.internal.api

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.InventoryLockingAPI
import sh.pcx.xinventories.internal.model.InventoryLock
import sh.pcx.xinventories.internal.model.LockScope
import java.time.Duration
import java.util.UUID

/**
 * Implementation of the InventoryLockingAPI.
 * Adapts internal LockingService to the public API interface.
 */
class LockingAPIImpl(private val plugin: XInventories) : InventoryLockingAPI {

    private val lockingService get() = plugin.serviceManager.lockingService

    override fun lock(
        playerUuid: UUID,
        lockedBy: UUID?,
        duration: Duration?,
        reason: String?,
        scope: LockScope
    ): InventoryLock? {
        return lockingService.lockInventory(playerUuid, lockedBy, duration, reason, scope)
    }

    override fun lock(
        playerUuid: UUID,
        lockedBy: UUID?,
        durationStr: String?,
        reason: String?
    ): InventoryLock? {
        val duration = durationStr?.let { parseDuration(it) }
        return lockingService.lockInventory(playerUuid, lockedBy, duration, reason)
    }

    override fun unlock(playerUuid: UUID): Boolean {
        return lockingService.unlockInventory(playerUuid)
    }

    override fun isLocked(playerUuid: UUID): Boolean {
        return lockingService.isLocked(playerUuid)
    }

    override fun isLocked(playerUuid: UUID, groupName: String): Boolean {
        return lockingService.isLocked(playerUuid, groupName)
    }

    override fun getLock(playerUuid: UUID): InventoryLock? {
        return lockingService.getLock(playerUuid)
    }

    override fun getLockedPlayers(): List<InventoryLock> {
        return lockingService.getLockedPlayers()
    }

    override fun isEnabled(): Boolean {
        return plugin.configManager.mainConfig.locking.enabled
    }

    override fun parseDuration(durationStr: String): Duration? {
        return try {
            val value = durationStr.dropLast(1).toLongOrNull() ?: return null
            val unit = durationStr.lastOrNull() ?: return null

            when (unit.lowercaseChar()) {
                's' -> Duration.ofSeconds(value)
                'm' -> Duration.ofMinutes(value)
                'h' -> Duration.ofHours(value)
                'd' -> Duration.ofDays(value)
                'w' -> Duration.ofDays(value * 7)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}

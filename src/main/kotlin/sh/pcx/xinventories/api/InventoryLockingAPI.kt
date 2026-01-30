package sh.pcx.xinventories.api

import sh.pcx.xinventories.internal.model.InventoryLock
import sh.pcx.xinventories.internal.model.LockScope
import java.time.Duration
import java.util.UUID

/**
 * API for managing inventory locks.
 */
interface InventoryLockingAPI {

    /**
     * Locks a player's inventory.
     *
     * @param playerUuid UUID of the player to lock
     * @param lockedBy UUID of who is creating the lock (null for system locks)
     * @param duration Duration of the lock (null for permanent)
     * @param reason Optional reason for the lock
     * @param scope The scope of the lock (default: ALL)
     * @return The created lock, or null if locking is disabled or cancelled by event
     */
    fun lock(
        playerUuid: UUID,
        lockedBy: UUID? = null,
        duration: Duration? = null,
        reason: String? = null,
        scope: LockScope = LockScope.ALL
    ): InventoryLock?

    /**
     * Locks a player's inventory with a duration string.
     *
     * @param playerUuid UUID of the player to lock
     * @param lockedBy UUID of who is creating the lock
     * @param durationStr Duration string (e.g., "30s", "5m", "1h", "1d")
     * @param reason Optional reason for the lock
     * @return The created lock, or null if locking is disabled, cancelled, or duration invalid
     */
    fun lock(
        playerUuid: UUID,
        lockedBy: UUID?,
        durationStr: String?,
        reason: String? = null
    ): InventoryLock?

    /**
     * Unlocks a player's inventory.
     *
     * @param playerUuid UUID of the player to unlock
     * @return true if the player was unlocked, false if they weren't locked
     */
    fun unlock(playerUuid: UUID): Boolean

    /**
     * Checks if a player's inventory is locked.
     *
     * @param playerUuid UUID of the player to check
     * @return true if the player is locked
     */
    fun isLocked(playerUuid: UUID): Boolean

    /**
     * Checks if a player's inventory is locked for a specific group.
     *
     * @param playerUuid UUID of the player to check
     * @param groupName The group to check
     * @return true if the player is locked for this group
     */
    fun isLocked(playerUuid: UUID, groupName: String): Boolean

    /**
     * Gets the lock for a player.
     *
     * @param playerUuid UUID of the player
     * @return The lock, or null if not locked
     */
    fun getLock(playerUuid: UUID): InventoryLock?

    /**
     * Gets all currently locked players.
     *
     * @return List of all active locks
     */
    fun getLockedPlayers(): List<InventoryLock>

    /**
     * Checks if inventory locking is enabled.
     *
     * @return true if locking is enabled in config
     */
    fun isEnabled(): Boolean

    /**
     * Parses a duration string to a Duration object.
     *
     * @param durationStr Duration string (e.g., "30s", "5m", "1h", "1d")
     * @return Duration or null if parsing fails
     */
    fun parseDuration(durationStr: String): Duration?
}

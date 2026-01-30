package sh.pcx.xinventories.internal.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Represents an inventory lock on a player.
 */
data class InventoryLock(
    /**
     * UUID of the player whose inventory is locked.
     */
    val playerUuid: UUID,

    /**
     * UUID of the admin/player who created the lock.
     * May be null for system-generated locks.
     */
    val lockedBy: UUID?,

    /**
     * When the lock was created.
     */
    val lockedAt: Instant,

    /**
     * When the lock expires, or null for permanent locks.
     */
    val expiresAt: Instant?,

    /**
     * Optional reason for the lock.
     */
    val reason: String?,

    /**
     * The scope of the lock.
     */
    val scope: LockScope,

    /**
     * For GROUP scope, the specific group that is locked.
     * Null for ALL scope.
     */
    val lockedGroup: String? = null,

    /**
     * For SLOTS scope, the specific slots that are locked.
     * Null for other scopes.
     */
    val lockedSlots: Set<Int>? = null
) {
    /**
     * Checks if this lock has expired.
     */
    fun isExpired(): Boolean {
        return expiresAt?.isBefore(Instant.now()) == true
    }

    /**
     * Checks if this lock is currently active (not expired).
     */
    fun isActive(): Boolean = !isExpired()

    /**
     * Gets the remaining duration until expiration.
     * Returns null if the lock is permanent or already expired.
     */
    fun getRemainingDuration(): Duration? {
        val expires = expiresAt ?: return null
        val now = Instant.now()
        if (expires.isBefore(now)) return Duration.ZERO
        return Duration.between(now, expires)
    }

    /**
     * Gets a human-readable description of the remaining time.
     */
    fun getRemainingTimeString(): String {
        if (expiresAt == null) return "permanent"

        val remaining = getRemainingDuration() ?: return "expired"
        if (remaining.isZero || remaining.isNegative) return "expired"

        val seconds = remaining.seconds
        return when {
            seconds < 60 -> "${seconds}s"
            seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s"
            seconds < 86400 -> "${seconds / 3600}h ${(seconds % 3600) / 60}m"
            else -> "${seconds / 86400}d ${(seconds % 86400) / 3600}h"
        }
    }

    /**
     * Checks if this lock applies to a specific group.
     */
    fun appliesToGroup(groupName: String): Boolean {
        return when (scope) {
            LockScope.ALL -> true
            LockScope.GROUP -> lockedGroup == groupName
            LockScope.SLOTS -> true // Slot locks apply regardless of group
        }
    }

    /**
     * Checks if this lock applies to a specific slot.
     */
    fun appliesToSlot(slot: Int): Boolean {
        return when (scope) {
            LockScope.ALL -> true
            LockScope.GROUP -> true // Group locks affect all slots in that group
            LockScope.SLOTS -> lockedSlots?.contains(slot) == true
        }
    }

    companion object {
        /**
         * Creates a full inventory lock.
         */
        fun createFullLock(
            playerUuid: UUID,
            lockedBy: UUID?,
            duration: Duration? = null,
            reason: String? = null
        ): InventoryLock {
            val now = Instant.now()
            return InventoryLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                lockedAt = now,
                expiresAt = duration?.let { now.plus(it) },
                reason = reason,
                scope = LockScope.ALL
            )
        }

        /**
         * Creates a group-specific lock.
         */
        fun createGroupLock(
            playerUuid: UUID,
            lockedBy: UUID?,
            group: String,
            duration: Duration? = null,
            reason: String? = null
        ): InventoryLock {
            val now = Instant.now()
            return InventoryLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                lockedAt = now,
                expiresAt = duration?.let { now.plus(it) },
                reason = reason,
                scope = LockScope.GROUP,
                lockedGroup = group
            )
        }

        /**
         * Creates a slot-specific lock.
         */
        fun createSlotLock(
            playerUuid: UUID,
            lockedBy: UUID?,
            slots: Set<Int>,
            duration: Duration? = null,
            reason: String? = null
        ): InventoryLock {
            val now = Instant.now()
            return InventoryLock(
                playerUuid = playerUuid,
                lockedBy = lockedBy,
                lockedAt = now,
                expiresAt = duration?.let { now.plus(it) },
                reason = reason,
                scope = LockScope.SLOTS,
                lockedSlots = slots
            )
        }

        /**
         * Parses a duration string like "30s", "5m", "1h", "1d".
         */
        fun parseDuration(durationStr: String): Duration? {
            val str = durationStr.trim().lowercase()
            if (str.isEmpty()) return null

            val number = str.dropLast(1).toLongOrNull() ?: return null
            if (number <= 0) return null

            return when (str.last()) {
                's' -> Duration.ofSeconds(number)
                'm' -> Duration.ofMinutes(number)
                'h' -> Duration.ofHours(number)
                'd' -> Duration.ofDays(number)
                'w' -> Duration.ofDays(number * 7)
                else -> null
            }
        }
    }
}

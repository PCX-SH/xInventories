package sh.pcx.xinventories.internal.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Represents a temporary group assignment for a player.
 *
 * Temporary groups allow players to be assigned to a different inventory
 * group for a limited time, such as during events or minigames. When the
 * assignment expires, the player is automatically returned to their
 * original group.
 *
 * @property playerUuid The UUID of the assigned player
 * @property temporaryGroup The name of the temporary group
 * @property originalGroup The name of the player's original group (for restoration)
 * @property expiresAt When this assignment expires
 * @property assignedBy Who assigned the temporary group (player name, "CONSOLE", or "API")
 * @property assignedAt When this assignment was created
 * @property reason Optional reason for the assignment
 */
data class TemporaryGroupAssignment(
    val playerUuid: UUID,
    val temporaryGroup: String,
    val originalGroup: String,
    val expiresAt: Instant,
    val assignedBy: String,
    val assignedAt: Instant = Instant.now(),
    val reason: String? = null
) {
    /**
     * Checks if this assignment has expired.
     * Returns true if the current time is at or after the expiration time.
     */
    val isExpired: Boolean
        get() = !Instant.now().isBefore(expiresAt)

    /**
     * Gets the remaining time until expiration.
     *
     * @return The remaining duration, or Duration.ZERO if expired
     */
    fun getRemainingTime(): Duration {
        val now = Instant.now()
        return if (now.isAfter(expiresAt)) {
            Duration.ZERO
        } else {
            Duration.between(now, expiresAt)
        }
    }

    /**
     * Gets the total duration of the assignment.
     */
    fun getTotalDuration(): Duration = Duration.between(assignedAt, expiresAt)

    /**
     * Gets the elapsed time since assignment.
     */
    fun getElapsedTime(): Duration = Duration.between(assignedAt, Instant.now())

    /**
     * Gets a human-readable remaining time string.
     */
    fun getRemainingTimeString(): String {
        val remaining = getRemainingTime()

        if (remaining.isZero) return "Expired"

        val days = remaining.toDays()
        val hours = remaining.toHours() % 24
        val minutes = remaining.toMinutes() % 60
        val seconds = remaining.seconds % 60

        return buildString {
            if (days > 0) append("${days}d ")
            if (hours > 0) append("${hours}h ")
            if (minutes > 0) append("${minutes}m ")
            if (seconds > 0 || isEmpty()) append("${seconds}s")
        }.trim()
    }

    /**
     * Creates a copy with an extended expiration time.
     *
     * @param extension The duration to extend by
     * @return A new assignment with extended expiration
     */
    fun extend(extension: Duration): TemporaryGroupAssignment {
        return copy(expiresAt = expiresAt.plus(extension))
    }

    /**
     * Converts to a map for storage.
     */
    fun toStorageMap(): Map<String, Any?> = mapOf(
        "player_uuid" to playerUuid.toString(),
        "temp_group" to temporaryGroup,
        "original_group" to originalGroup,
        "expires_at" to expiresAt.toEpochMilli(),
        "assigned_by" to assignedBy,
        "assigned_at" to assignedAt.toEpochMilli(),
        "reason" to reason
    )

    companion object {
        /**
         * Creates a new temporary group assignment.
         *
         * @param playerUuid The player's UUID
         * @param temporaryGroup The temporary group name
         * @param originalGroup The original group name
         * @param duration How long the assignment should last
         * @param assignedBy Who is making the assignment
         * @param reason Optional reason
         */
        fun create(
            playerUuid: UUID,
            temporaryGroup: String,
            originalGroup: String,
            duration: Duration,
            assignedBy: String,
            reason: String? = null
        ): TemporaryGroupAssignment {
            val now = Instant.now()
            return TemporaryGroupAssignment(
                playerUuid = playerUuid,
                temporaryGroup = temporaryGroup,
                originalGroup = originalGroup,
                expiresAt = now.plus(duration),
                assignedBy = assignedBy,
                assignedAt = now,
                reason = reason
            )
        }

        /**
         * Creates from a storage map.
         */
        fun fromStorageMap(map: Map<String, Any?>): TemporaryGroupAssignment? {
            return try {
                TemporaryGroupAssignment(
                    playerUuid = UUID.fromString(map["player_uuid"] as String),
                    temporaryGroup = map["temp_group"] as String,
                    originalGroup = map["original_group"] as String,
                    expiresAt = Instant.ofEpochMilli(map["expires_at"] as Long),
                    assignedBy = map["assigned_by"] as String,
                    assignedAt = Instant.ofEpochMilli(map["assigned_at"] as Long),
                    reason = map["reason"] as? String
                )
            } catch (e: Exception) {
                null
            }
        }

        /**
         * Parses a duration string like "1h30m", "2d", "1w".
         *
         * @param input The duration string
         * @return The parsed duration, or null if invalid
         */
        fun parseDuration(input: String): Duration? {
            if (input.isBlank()) return null

            var remaining = input.lowercase().trim()
            var totalMillis = 0L

            val patterns = listOf(
                "w" to (7 * 24 * 60 * 60 * 1000L),
                "d" to (24 * 60 * 60 * 1000L),
                "h" to (60 * 60 * 1000L),
                "m" to (60 * 1000L),
                "s" to 1000L
            )

            for ((suffix, multiplier) in patterns) {
                val regex = Regex("(\\d+)$suffix")
                val match = regex.find(remaining)
                if (match != null) {
                    val value = match.groupValues[1].toLongOrNull() ?: return null
                    totalMillis += value * multiplier
                    remaining = remaining.replace(match.value, "")
                }
            }

            // If nothing was parsed and there's leftover text, try parsing as pure number (seconds)
            if (totalMillis == 0L && remaining.isNotBlank()) {
                val seconds = remaining.toLongOrNull() ?: return null
                totalMillis = seconds * 1000
            }

            return if (totalMillis > 0) Duration.ofMillis(totalMillis) else null
        }

        /**
         * Formats a duration as a human-readable string.
         *
         * @param duration The duration to format
         * @return A string like "1w 2d 3h 30m"
         */
        fun formatDuration(duration: Duration): String {
            if (duration.isZero || duration.isNegative) return "0s"

            val weeks = duration.toDays() / 7
            val days = duration.toDays() % 7
            val hours = duration.toHours() % 24
            val minutes = duration.toMinutes() % 60
            val seconds = duration.seconds % 60

            return buildString {
                if (weeks > 0) append("${weeks}w ")
                if (days > 0) append("${days}d ")
                if (hours > 0) append("${hours}h ")
                if (minutes > 0) append("${minutes}m ")
                if (seconds > 0 || isEmpty()) append("${seconds}s")
            }.trim()
        }
    }
}

package sh.pcx.xinventories.internal.model

import java.time.Instant
import java.util.UUID

/**
 * Represents an audit log entry tracking inventory modifications.
 *
 * @property id Unique identifier for this entry
 * @property timestamp When the action occurred
 * @property actor UUID of the player/admin who performed the action, null for system actions
 * @property actorName Name of the actor ("SYSTEM", "CONSOLE", or player name)
 * @property target UUID of the affected player
 * @property targetName Name of the affected player
 * @property action The type of action performed
 * @property group The inventory group involved, if applicable
 * @property details Additional details about the action
 * @property serverId The server ID where this action occurred (for multi-server setups)
 */
data class AuditEntry(
    val id: Long = 0,
    val timestamp: Instant = Instant.now(),
    val actor: UUID? = null,
    val actorName: String,
    val target: UUID,
    val targetName: String,
    val action: AuditAction,
    val group: String? = null,
    val details: String? = null,
    val serverId: String? = null
) {
    /**
     * Returns a human-readable description of this entry.
     */
    fun toDisplayString(): String {
        val actorStr = if (actor != null) actorName else "[$actorName]"
        val groupStr = if (group != null) " in group '$group'" else ""
        val detailStr = if (details != null) " - $details" else ""
        return "[$timestamp] $actorStr ${action.displayName} $targetName$groupStr$detailStr"
    }

    companion object {
        /**
         * Creates an audit entry for a system action.
         */
        fun system(
            target: UUID,
            targetName: String,
            action: AuditAction,
            group: String? = null,
            details: String? = null,
            serverId: String? = null
        ): AuditEntry = AuditEntry(
            actorName = "SYSTEM",
            target = target,
            targetName = targetName,
            action = action,
            group = group,
            details = details,
            serverId = serverId
        )

        /**
         * Creates an audit entry for a console action.
         */
        fun console(
            target: UUID,
            targetName: String,
            action: AuditAction,
            group: String? = null,
            details: String? = null,
            serverId: String? = null
        ): AuditEntry = AuditEntry(
            actorName = "CONSOLE",
            target = target,
            targetName = targetName,
            action = action,
            group = group,
            details = details,
            serverId = serverId
        )

        /**
         * Creates an audit entry for a player action.
         */
        fun player(
            actor: UUID,
            actorName: String,
            target: UUID,
            targetName: String,
            action: AuditAction,
            group: String? = null,
            details: String? = null,
            serverId: String? = null
        ): AuditEntry = AuditEntry(
            actor = actor,
            actorName = actorName,
            target = target,
            targetName = targetName,
            action = action,
            group = group,
            details = details,
            serverId = serverId
        )
    }
}

/**
 * Enumeration of auditable actions.
 */
enum class AuditAction(val displayName: String, val isDestructive: Boolean = false) {
    // Inventory operations
    INVENTORY_SAVE("saved inventory for", false),
    INVENTORY_LOAD("loaded inventory for", false),
    INVENTORY_CLEAR("cleared inventory for", true),

    // Item modifications
    ITEM_ADD("added items to", false),
    ITEM_REMOVE("removed items from", true),
    ITEM_MODIFY("modified items for", false),

    // Recovery operations
    VERSION_RESTORE("restored version for", false),
    DEATH_RESTORE("restored death inventory for", false),

    // Template operations
    TEMPLATE_APPLY("applied template to", false),

    // Bulk operations
    BULK_OPERATION("performed bulk operation on", true),
    BULK_CLEAR("bulk cleared inventories for", true),
    BULK_EXPORT("bulk exported data for", false),
    BULK_RESET_STATS("bulk reset stats for", true),

    // Lock operations
    LOCK_APPLY("locked inventory for", false),
    LOCK_REMOVE("unlocked inventory for", false),

    // Admin operations
    ADMIN_EDIT("edited inventory for", false),
    ADMIN_VIEW("viewed inventory for", false),

    // Group operations
    GROUP_CHANGE("changed group for", false);

    companion object {
        /**
         * Gets an action by name, case-insensitive.
         */
        fun fromName(name: String): AuditAction? {
            return entries.find { it.name.equals(name, ignoreCase = true) }
        }
    }
}

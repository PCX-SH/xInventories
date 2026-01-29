package sh.pcx.xinventories.internal.config

/**
 * Messages configuration model (messages.yml).
 */
data class MessagesConfig(
    val prefix: String = "<gradient:#5e4fa2:#9e7bb5><bold>xInventories</bold></gradient> <dark_gray>\u00bb</dark_gray> ",
    val messages: Map<String, String> = defaultMessages()
) {
    companion object {
        fun defaultMessages(): Map<String, String> = mapOf(
            // General
            "no-permission" to "<red>You don't have permission to do this.",
            "player-not-found" to "<red>Player <aqua>{player}</aqua> not found.",
            "player-not-online" to "<red>Player <aqua>{player}</aqua> is not online.",
            "invalid-syntax" to "<red>Invalid syntax. Usage: <aqua>{usage}</aqua>",
            "console-only" to "<red>This command can only be run from console.",
            "player-only" to "<red>This command can only be run by players.",
            "world-not-found" to "<red>World <aqua>{world}</aqua> not found.",
            "reloaded" to "<green>Configuration reloaded successfully!",
            "reload-failed" to "<red>Failed to reload configuration. Check console for errors.",
            "unknown-command" to "<red>Unknown command: <aqua>{command}</aqua>. Use <aqua>/xinv</aqua> for help.",
            "command-error" to "<red>An error occurred while executing the command.",

            // Help
            "help-header" to "<gold><bold>xInventories Commands</bold></gold>",
            "help-entry" to "<gray>\u2022 <aqua>{command}</aqua> <gray>- {description}</gray>",
            "help-footer" to "<gray>Use <aqua>/xinv <command></aqua> for more info.",

            // Inventory Operations
            "inventory-saved" to "<green>Inventory saved for <aqua>{player}</aqua>.",
            "inventory-saved-self" to "<green>Your inventory has been saved.",
            "inventory-loaded" to "<green>Inventory loaded for <aqua>{player}</aqua>.",
            "inventory-loaded-self" to "<green>Your inventory has been loaded.",
            "inventory-save-failed" to "<red>Failed to save inventory for <aqua>{player}</aqua>.",
            "inventory-load-failed" to "<red>Failed to load inventory for <aqua>{player}</aqua>.",
            "inventory-cleared" to "<gray>Your inventory has been cleared for this group.",
            "inventory-switched" to "<gray>Inventory switched: <aqua>{from_group}</aqua> \u2192 <green>{to_group}</green>",
            "inventory-switch-same" to "<gray>Staying in group <aqua>{group}</aqua>.",

            // Group Management
            "group-created" to "<green>Group <aqua>{group}</aqua> created successfully.",
            "group-deleted" to "<green>Group <aqua>{group}</aqua> deleted.",
            "group-not-found" to "<red>Group <aqua>{group}</aqua> not found.",
            "group-already-exists" to "<red>Group <aqua>{group}</aqua> already exists.",
            "group-cannot-delete-default" to "<red>Cannot delete the default group.",
            "group-list-header" to "<gold><bold>Inventory Groups</bold></gold>",
            "group-list-entry" to "<gray>\u2022 <aqua>{group}</aqua> <gray>({count} worlds)</gray>",
            "group-list-empty" to "<gray>No groups configured.",
            "group-info-header" to "<gold><bold>Group: {group}</bold></gold>",
            "group-info-worlds" to "<gray>Worlds: <white>{worlds}</white>",
            "group-info-patterns" to "<gray>Patterns: <white>{patterns}</white>",
            "group-info-priority" to "<gray>Priority: <white>{priority}</white>",
            "group-info-parent" to "<gray>Parent: <white>{parent}</white>",
            "group-modified" to "<green>Group <aqua>{group}</aqua> updated.",

            // World Management
            "world-assigned" to "<green>World <aqua>{world}</aqua> assigned to group <aqua>{group}</aqua>.",
            "world-unassigned" to "<green>World <aqua>{world}</aqua> unassigned from group <aqua>{group}</aqua>.",
            "world-not-assigned" to "<gray>World <aqua>{world}</aqua> is not assigned to any group.",
            "world-already-assigned" to "<gray>World <aqua>{world}</aqua> is already assigned to <aqua>{group}</aqua>.",
            "world-list-header" to "<gold><bold>World Assignments</bold></gold>",
            "world-list-entry" to "<gray>\u2022 <aqua>{world}</aqua> \u2192 <green>{group}</green>",
            "world-list-pattern" to "<gray>\u2022 <aqua>{world}</aqua> \u2192 <green>{group}</green> <gray>(pattern)</gray>",
            "world-list-default" to "<gray>\u2022 <aqua>{world}</aqua> \u2192 <green>{group}</green> <gray>(default)</gray>",

            // Pattern Management
            "pattern-added" to "<green>Pattern <aqua>{pattern}</aqua> added to group <aqua>{group}</aqua>.",
            "pattern-removed" to "<green>Pattern <aqua>{pattern}</aqua> removed from group <aqua>{group}</aqua>.",
            "pattern-not-found" to "<red>Pattern <aqua>{pattern}</aqua> not found in group <aqua>{group}</aqua>.",
            "pattern-invalid" to "<red>Invalid regex pattern: <aqua>{pattern}</aqua>",
            "pattern-already-exists" to "<red>Pattern <aqua>{pattern}</aqua> already exists in group <aqua>{group}</aqua>.",
            "pattern-list-header" to "<gold><bold>Patterns for {group}</bold></gold>",
            "pattern-list-entry" to "<gray>\u2022 <aqua>{pattern}</aqua>",
            "pattern-list-empty" to "<gray>No patterns configured for this group.",

            // Cache Management
            "cache-cleared" to "<green>Cache cleared. <aqua>{count}</aqua> entries removed.",
            "cache-player-cleared" to "<green>Cache cleared for player <aqua>{player}</aqua>.",
            "cache-stats-header" to "<gold><bold>Cache Statistics</bold></gold>",
            "cache-stats-size" to "<gray>Entries: <white>{size}/{max}</white>",
            "cache-stats-hits" to "<gray>Hits: <green>{hits}</green> <gray>({rate}%)</gray>",
            "cache-stats-misses" to "<gray>Misses: <red>{misses}</red>",
            "cache-stats-evictions" to "<gray>Evictions: <aqua>{evictions}</aqua>",

            // Backup Management
            "backup-created" to "<green>Backup created: <aqua>{name}</aqua>",
            "backup-create-failed" to "<red>Failed to create backup. Check console for errors.",
            "backup-restored" to "<green>Backup <aqua>{name}</aqua> restored successfully.",
            "backup-restore-failed" to "<red>Failed to restore backup. Check console for errors.",
            "backup-deleted" to "<green>Backup <aqua>{name}</aqua> deleted.",
            "backup-not-found" to "<red>Backup <aqua>{name}</aqua> not found.",
            "backup-list-header" to "<gold><bold>Available Backups</bold></gold>",
            "backup-list-entry" to "<gray>\u2022 <aqua>{name}</aqua> <gray>({time}, {size})</gray>",
            "backup-list-empty" to "<gray>No backups available.",
            "backup-in-progress" to "<gray>Backup in progress... Please wait.",
            "backup-restore-confirm" to "<red>Warning: This will overwrite current data! Use <aqua>/xinv backup restore {name} confirm</aqua> to proceed.",

            // Conversion
            "convert-started" to "<gray>Starting conversion from <white>{source}</white>...",
            "convert-complete" to "<green>Conversion complete! <aqua>{count}</aqua> players migrated.",
            "convert-failed" to "<red>Conversion failed. Check console for errors.",
            "convert-not-found" to "<red>No data found for <aqua>{source}</aqua>.",
            "convert-in-progress" to "<gray>Conversion already in progress.",

            // GUI
            "gui-title-main" to "<gradient:#5e4fa2:#9e7bb5>xInventories Admin</gradient>",
            "gui-title-groups" to "<gold>Group Management</gold>",
            "gui-title-group-editor" to "<gold>Edit: {group}</gold>",
            "gui-title-worlds" to "<gold>World Assignments</gold>",
            "gui-title-patterns" to "<gold>Pattern Editor</gold>",
            "gui-title-player-select" to "<gold>Select Player</gold>",
            "gui-title-player-view" to "<gold>{player}'s Inventory</gold>",
            "gui-title-player-edit" to "<red>Editing: {player}</red>",
            "gui-title-cache" to "<gold>Cache Statistics</gold>",
            "gui-title-backups" to "<gold>Backup Management</gold>",
            "gui-title-confirm" to "<red>Confirm Action</red>",
            "gui-item-back" to "<gray>\u2190 Back",
            "gui-item-close" to "<red>Close",
            "gui-item-next-page" to "<aqua>Next Page \u2192",
            "gui-item-prev-page" to "<aqua>\u2190 Previous Page",
            "gui-item-confirm" to "<green>Confirm",
            "gui-item-cancel" to "<red>Cancel",
            "gui-item-refresh" to "<aqua>Refresh",

            // Error Messages
            "error-generic" to "<red>An error occurred. Please check console for details.",
            "error-storage" to "<red>Storage error. Data may not have been saved.",
            "error-storage-connect" to "<red>Failed to connect to database.",
            "error-migration" to "<red>Migration failed. Original data preserved.",
            "error-serialization" to "<red>Failed to serialize inventory data.",
            "error-deserialization" to "<red>Failed to load inventory data. It may be corrupted.",
            "admin-error" to "<dark_red>[xInventories Error]</dark_red> <red>{message}</red>",
            "admin-warning" to "<gold>[xInventories Warning]</gold> <gray>{message}</gray>"
        )
    }

    fun getMessage(key: String): String = messages[key] ?: "<red>Missing message: $key"
}

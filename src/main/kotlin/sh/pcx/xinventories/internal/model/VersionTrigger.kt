package sh.pcx.xinventories.internal.model

/**
 * Enum representing the trigger that caused an inventory version to be created.
 */
enum class VersionTrigger {
    /**
     * Version created when player changed worlds/groups.
     */
    WORLD_CHANGE,

    /**
     * Version created when player disconnected.
     */
    DISCONNECT,

    /**
     * Version created manually via command.
     */
    MANUAL,

    /**
     * Version created when player died.
     */
    DEATH,

    /**
     * Version created by scheduled automatic backup.
     */
    SCHEDULED
}

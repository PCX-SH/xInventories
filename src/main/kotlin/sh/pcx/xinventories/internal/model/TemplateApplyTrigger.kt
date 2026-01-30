package sh.pcx.xinventories.internal.model

/**
 * Trigger conditions for when an inventory template should be applied.
 */
enum class TemplateApplyTrigger {
    /**
     * Apply template every time the player enters the group.
     */
    JOIN,

    /**
     * Apply template only the first time the player enters the group.
     */
    FIRST_JOIN,

    /**
     * Apply template only via manual command.
     */
    MANUAL,

    /**
     * Template exists but is never auto-applied.
     */
    NONE
}

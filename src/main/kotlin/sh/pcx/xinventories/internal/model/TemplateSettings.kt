package sh.pcx.xinventories.internal.model

/**
 * Settings for how a template should be applied to a group.
 */
data class TemplateSettings(
    /**
     * Whether template functionality is enabled for this group.
     */
    val enabled: Boolean = false,

    /**
     * The name of the template to use. If null, uses the group name.
     */
    val templateName: String? = null,

    /**
     * When to apply the template.
     */
    val applyOn: TemplateApplyTrigger = TemplateApplyTrigger.NONE,

    /**
     * Whether players can reset their inventory to the template via command.
     */
    val allowReset: Boolean = false,

    /**
     * Whether to clear the player's inventory before applying the template.
     */
    val clearInventoryFirst: Boolean = true
)

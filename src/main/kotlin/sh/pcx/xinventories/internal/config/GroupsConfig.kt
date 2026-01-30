package sh.pcx.xinventories.internal.config

import sh.pcx.xinventories.api.model.GroupSettings

/**
 * Groups configuration model (groups.yml).
 */
data class GroupsConfig(
    val groups: Map<String, GroupConfig> = mapOf(
        "survival" to GroupConfig(
            worlds = listOf("world", "world_nether", "world_the_end"),
            patterns = listOf("^survival_.*", "^smp_.*"),
            priority = 10,
            parent = null,
            settings = GroupSettings()
        )
    ),
    val defaultGroup: String = "survival",
    val globalSettings: GlobalSettings = GlobalSettings()
)

data class GroupConfig(
    val worlds: List<String> = emptyList(),
    val patterns: List<String> = emptyList(),
    val priority: Int = 0,
    val parent: String? = null,
    val settings: GroupSettings = GroupSettings(),
    val conditions: ConditionsConfig? = null,
    val template: TemplateConfigSection? = null,
    val restrictions: RestrictionConfigSection? = null
)

/**
 * Template configuration section for a group.
 */
data class TemplateConfigSection(
    val enabled: Boolean = false,
    val templateName: String? = null,
    val applyOn: String = "NONE",
    val allowReset: Boolean = false,
    val clearInventoryFirst: Boolean = true
)

/**
 * Restriction configuration section for a group.
 */
data class RestrictionConfigSection(
    val mode: String = "NONE",
    val onViolation: String = "REMOVE",
    val notifyPlayer: Boolean = true,
    val notifyAdmins: Boolean = true,
    val blacklist: List<String> = emptyList(),
    val whitelist: List<String> = emptyList(),
    val stripOnExit: List<String> = emptyList()
)

/**
 * Conditions configuration for conditional groups.
 */
data class ConditionsConfig(
    val permission: String? = null,
    val schedule: List<ScheduleConfig>? = null,
    val cron: String? = null,
    val placeholder: PlaceholderConfig? = null,
    val placeholders: List<PlaceholderConfig>? = null,
    val requireAll: Boolean = true
)

/**
 * Schedule time range configuration.
 */
data class ScheduleConfig(
    val start: String,
    val end: String,
    val timezone: String? = null
)

/**
 * PlaceholderAPI condition configuration.
 */
data class PlaceholderConfig(
    val placeholder: String,
    val operator: String,
    val value: String
)

data class GlobalSettings(
    val notifyOnSwitch: Boolean = true,
    val switchSound: String? = "ENTITY_ENDERMAN_TELEPORT",
    val switchSoundVolume: Float = 0.5f,
    val switchSoundPitch: Float = 1.2f
)

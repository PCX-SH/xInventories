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
    val settings: GroupSettings = GroupSettings()
)

data class GlobalSettings(
    val notifyOnSwitch: Boolean = true,
    val switchSound: String? = "ENTITY_ENDERMAN_TELEPORT",
    val switchSoundVolume: Float = 0.5f,
    val switchSoundPitch: Float = 1.2f
)

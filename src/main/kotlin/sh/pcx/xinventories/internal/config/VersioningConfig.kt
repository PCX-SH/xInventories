package sh.pcx.xinventories.internal.config

/**
 * Configuration for inventory versioning/history feature.
 */
data class VersioningConfig(
    /**
     * Whether versioning is enabled.
     */
    val enabled: Boolean = true,

    /**
     * Maximum number of versions to keep per player per group.
     */
    val maxVersionsPerPlayer: Int = 10,

    /**
     * Minimum interval between automatic versions in seconds.
     * Prevents version spam during rapid world changes.
     */
    val minIntervalSeconds: Int = 300,

    /**
     * Number of days to retain versions before automatic pruning.
     */
    val retentionDays: Int = 7,

    /**
     * Configuration for what triggers version creation.
     */
    val triggerOn: TriggerConfig = TriggerConfig()
)

/**
 * Configuration for version creation triggers.
 */
data class TriggerConfig(
    /**
     * Create version when player changes worlds/groups.
     */
    val worldChange: Boolean = true,

    /**
     * Create version when player disconnects.
     */
    val disconnect: Boolean = true,

    /**
     * Allow manual version creation via command.
     */
    val manual: Boolean = true
)

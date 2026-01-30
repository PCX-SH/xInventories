package sh.pcx.xinventories.internal.config

/**
 * Configuration for death recovery feature.
 */
data class DeathRecoveryConfig(
    /**
     * Whether death recovery is enabled.
     */
    val enabled: Boolean = true,

    /**
     * Maximum number of deaths to store per player.
     */
    val maxDeathsPerPlayer: Int = 5,

    /**
     * Number of days to retain death records before automatic pruning.
     */
    val retentionDays: Int = 3,

    /**
     * Whether to store the death location coordinates.
     */
    val storeLocation: Boolean = true,

    /**
     * Whether to store the cause of death.
     */
    val storeDeathCause: Boolean = true,

    /**
     * Whether to store killer information (player or entity).
     */
    val storeKiller: Boolean = true
)

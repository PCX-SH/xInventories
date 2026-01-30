package sh.pcx.xinventories.internal.config

/**
 * Configuration for startup banner and statistics display.
 */
data class StartupConfig(
    /**
     * Whether to show the ASCII art banner on startup.
     */
    val showBanner: Boolean = true,

    /**
     * Whether to show plugin statistics (storage type, groups, sync status) on startup.
     */
    val showStats: Boolean = true
)

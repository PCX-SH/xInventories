package sh.pcx.xinventories.internal.util

import sh.pcx.xinventories.XInventories

/**
 * Displays a colorful startup banner and plugin information on enable.
 *
 * Uses ANSI color codes for console output to display an ASCII art logo
 * along with version information and quick status summary.
 */
object StartupBanner {
    // ANSI color codes
    private const val RESET = "\u001B[0m"
    private const val CYAN = "\u001B[36m"
    private const val GREEN = "\u001B[32m"
    private const val YELLOW = "\u001B[33m"
    private const val WHITE = "\u001B[37m"
    private const val BOLD = "\u001B[1m"

    // ASCII art logo lines (without color codes - applied dynamically)
    private val LOGO_LINES = listOf(
        " __  ___                      __             _",
        " \\ \\/ (_)___ _   _____  ____  / /_____  _____(_)__  _____",
        "  \\  /| | __ \\ | / / _ \\/ __ \\/ __/ __ \\/ ___/ / _ \\/ ___/",
        "  /  \\| | / / | V /  __/ / / / /_/ /_/ / /  / /  __(__  )",
        " /_/\\_\\_/_/ /_/|___/\\___/_/ /_/\\__/\\____/_/  /_/\\___/____/"
    )

    /**
     * Displays the startup banner with version info and plugin status.
     *
     * @param plugin The main plugin instance
     */
    fun display(plugin: XInventories) {
        val config = plugin.configManager.mainConfig
        val startupConfig = config.startup

        // Check if banner display is enabled
        if (!startupConfig.showBanner) {
            return
        }

        val logger = plugin.logger

        // Print empty line for separation
        logger.info("")

        // Print logo with colors
        LOGO_LINES.forEach { line ->
            logger.info("$CYAN$BOLD$line$RESET")
        }

        logger.info("")

        // Print version and author info
        logger.info("${GREEN}Version:$RESET ${plugin.description.version}")
        logger.info("${GREEN}Author:$RESET ${plugin.description.authors.joinToString(", ")}")

        // Print stats if enabled
        if (startupConfig.showStats) {
            displayStats(plugin)
        }

        logger.info("")
    }

    /**
     * Displays plugin statistics including storage type, groups, and sync status.
     *
     * @param plugin The main plugin instance
     */
    private fun displayStats(plugin: XInventories) {
        val config = plugin.configManager.mainConfig
        val logger = plugin.logger

        // Storage type
        logger.info("${GREEN}Storage:$RESET ${config.storage.type.name}")

        // Groups loaded count
        val groupCount = try {
            plugin.serviceManager.groupService.getAllGroups().size
        } catch (e: Exception) {
            // ServiceManager might not be initialized yet
            plugin.configManager.groupsConfig.groups.size
        }
        logger.info("${GREEN}Groups:$RESET $groupCount loaded")

        // Sync status
        if (config.sync.enabled) {
            logger.info("${GREEN}Sync:$RESET ${YELLOW}Enabled$RESET (${config.sync.mode})")
        }
    }

    /**
     * Gets the banner as a list of strings without ANSI codes (for testing).
     *
     * @return List of banner lines
     */
    fun getBannerLines(): List<String> = LOGO_LINES

    /**
     * Gets the colored banner as a list of strings.
     *
     * @return List of colored banner lines
     */
    fun getColoredBannerLines(): List<String> = LOGO_LINES.map { "$CYAN$BOLD$it$RESET" }
}

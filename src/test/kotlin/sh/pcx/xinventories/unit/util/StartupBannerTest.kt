package sh.pcx.xinventories.unit.util

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.bukkit.plugin.PluginDescriptionFile
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.config.*
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import sh.pcx.xinventories.internal.util.StartupBanner
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DisplayName("StartupBanner Tests")
class StartupBannerTest {

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var serviceManager: ServiceManager
    private lateinit var groupService: GroupService
    private lateinit var logger: Logger
    private val loggedMessages = mutableListOf<String>()

    @BeforeEach
    fun setUp() {
        // Initialize Logging to avoid lateinit issues
        val testLogger = Logger.getLogger("Test")
        Logging.init(testLogger, false)
        mockkObject(Logging)

        plugin = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        groupService = mockk(relaxed = true)

        // Create a capturing logger
        logger = mockk(relaxed = true)
        val messageSlot = slot<String>()
        every { logger.info(capture(messageSlot)) } answers {
            loggedMessages.add(messageSlot.captured)
            Unit
        }

        // Set up plugin description
        val description = mockk<PluginDescriptionFile>()
        every { description.version } returns "1.0.0"
        every { description.authors } returns listOf("TestAuthor")

        // Wire up mocks - plugin.plugin returns plugin itself for PluginContext compatibility
        every { plugin.plugin } returns plugin
        every { plugin.logger } returns logger
        every { plugin.description } returns description
        every { plugin.configManager } returns configManager
        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.groupService } returns groupService
        every { groupService.getAllGroups() } returns listOf(
            mockk<Group>(),
            mockk<Group>()
        )

        // Default config setup
        val mainConfig = MainConfig(
            storage = StorageConfig(type = StorageType.YAML),
            sync = NetworkSyncConfig(enabled = false),
            startup = StartupConfig(showBanner = true, showStats = true)
        )
        every { configManager.mainConfig } returns mainConfig
        every { configManager.groupsConfig } returns GroupsConfig()
    }

    @AfterEach
    fun tearDown() {
        loggedMessages.clear()
        unmockkAll()
    }

    @Nested
    @DisplayName("Banner Display")
    inner class BannerDisplay {

        @Test
        @DisplayName("Should display ASCII logo when banner is enabled")
        fun displayLogoWhenEnabled() {
            StartupBanner.display(plugin)

            // Verify that logo lines are logged
            val bannerLines = StartupBanner.getBannerLines()
            assertTrue(loggedMessages.any { msg ->
                bannerLines.any { line -> msg.contains(line.trim()) }
            }, "Banner should contain logo lines")
        }

        @Test
        @DisplayName("Should display version when banner is enabled")
        fun displayVersionWhenEnabled() {
            StartupBanner.display(plugin)

            assertTrue(loggedMessages.any { it.contains("Version:") && it.contains("1.0.0") },
                "Banner should display version")
        }

        @Test
        @DisplayName("Should display author when banner is enabled")
        fun displayAuthorWhenEnabled() {
            StartupBanner.display(plugin)

            assertTrue(loggedMessages.any { it.contains("Author:") && it.contains("TestAuthor") },
                "Banner should display author")
        }

        @Test
        @DisplayName("Should not display banner when disabled")
        fun noDisplayWhenDisabled() {
            val mainConfig = MainConfig(
                startup = StartupConfig(showBanner = false, showStats = true)
            )
            every { configManager.mainConfig } returns mainConfig

            StartupBanner.display(plugin)

            // Clear the logged messages check - when banner is disabled, only empty line might be logged
            val bannerLines = StartupBanner.getBannerLines()
            assertFalse(loggedMessages.any { msg ->
                bannerLines.any { line -> msg.contains(line.trim()) && line.isNotBlank() }
            }, "Banner should not be displayed when disabled")
        }
    }

    @Nested
    @DisplayName("Stats Display")
    inner class StatsDisplay {

        @Test
        @DisplayName("Should display storage type when stats enabled")
        fun displayStorageType() {
            StartupBanner.display(plugin)

            assertTrue(loggedMessages.any { it.contains("Storage:") && it.contains("YAML") },
                "Banner should display storage type")
        }

        @Test
        @DisplayName("Should display groups count when stats enabled")
        fun displayGroupsCount() {
            StartupBanner.display(plugin)

            assertTrue(loggedMessages.any { it.contains("Groups:") && it.contains("2 loaded") },
                "Banner should display groups count")
        }

        @Test
        @DisplayName("Should display sync status when enabled")
        fun displaySyncStatusWhenEnabled() {
            val mainConfig = MainConfig(
                sync = NetworkSyncConfig(enabled = true, mode = SyncMode.REDIS),
                startup = StartupConfig(showBanner = true, showStats = true)
            )
            every { configManager.mainConfig } returns mainConfig

            StartupBanner.display(plugin)

            assertTrue(loggedMessages.any { it.contains("Sync:") && it.contains("Enabled") },
                "Banner should display sync status")
        }

        @Test
        @DisplayName("Should not display sync status when disabled")
        fun noSyncStatusWhenDisabled() {
            val mainConfig = MainConfig(
                sync = NetworkSyncConfig(enabled = false),
                startup = StartupConfig(showBanner = true, showStats = true)
            )
            every { configManager.mainConfig } returns mainConfig

            StartupBanner.display(plugin)

            assertFalse(loggedMessages.any { it.contains("Sync:") },
                "Banner should not display sync status when disabled")
        }

        @Test
        @DisplayName("Should not display stats when showStats is false")
        fun noStatsWhenDisabled() {
            val mainConfig = MainConfig(
                startup = StartupConfig(showBanner = true, showStats = false)
            )
            every { configManager.mainConfig } returns mainConfig

            StartupBanner.display(plugin)

            assertFalse(loggedMessages.any { it.contains("Storage:") },
                "Stats should not be displayed when disabled")
            assertFalse(loggedMessages.any { it.contains("Groups:") },
                "Stats should not be displayed when disabled")
        }
    }

    @Nested
    @DisplayName("ANSI Color Codes")
    inner class AnsiColorCodes {

        @Test
        @DisplayName("Colored banner lines should contain ANSI codes")
        fun coloredLinesContainAnsiCodes() {
            val coloredLines = StartupBanner.getColoredBannerLines()

            assertTrue(coloredLines.all { it.contains("\u001B[") },
                "Colored lines should contain ANSI escape codes")
        }

        @Test
        @DisplayName("Colored banner should contain RESET code")
        fun coloredLinesContainResetCode() {
            val coloredLines = StartupBanner.getColoredBannerLines()

            assertTrue(coloredLines.all { it.contains("\u001B[0m") },
                "Colored lines should end with RESET code")
        }

        @Test
        @DisplayName("Colored banner should contain CYAN code")
        fun coloredLinesContainCyanCode() {
            val coloredLines = StartupBanner.getColoredBannerLines()

            assertTrue(coloredLines.all { it.contains("\u001B[36m") },
                "Colored lines should contain CYAN code")
        }
    }

    @Nested
    @DisplayName("Banner Content")
    inner class BannerContent {

        @Test
        @DisplayName("Banner should have 6 logo lines")
        fun bannerHasSixLines() {
            val bannerLines = StartupBanner.getBannerLines()
            assertEquals(6, bannerLines.size, "Banner should have 6 logo lines")
        }

        @Test
        @DisplayName("Banner lines should not be empty")
        fun bannerLinesNotEmpty() {
            val bannerLines = StartupBanner.getBannerLines()
            assertTrue(bannerLines.all { it.isNotBlank() },
                "Banner lines should not be empty")
        }

        @Test
        @DisplayName("Banner should contain 'Xinventories' pattern")
        fun bannerContainsXinventoriesPattern() {
            val bannerLines = StartupBanner.getBannerLines()
            // The ASCII art should spell out something resembling Xinventories using box-drawing characters
            val fullBanner = bannerLines.joinToString("")
            assertTrue(fullBanner.contains("██") && fullBanner.contains("╔") && fullBanner.contains("╗"),
                "Banner should contain ASCII art box-drawing characters")
        }
    }

    @Nested
    @DisplayName("Fallback Handling")
    inner class FallbackHandling {

        @Test
        @DisplayName("Should use config groups count if serviceManager throws")
        fun useConfigGroupsOnServiceError() {
            every { serviceManager.groupService } throws RuntimeException("Not initialized")
            every { configManager.groupsConfig } returns GroupsConfig(
                groups = mapOf(
                    "survival" to GroupConfig(),
                    "creative" to GroupConfig(),
                    "minigames" to GroupConfig()
                )
            )

            StartupBanner.display(plugin)

            assertTrue(loggedMessages.any { it.contains("Groups:") && it.contains("3 loaded") },
                "Should fall back to config group count")
        }
    }
}

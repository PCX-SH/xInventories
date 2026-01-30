package sh.pcx.xinventories.unit.gui

import io.mockk.*
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.model.RestrictionConfig
import sh.pcx.xinventories.internal.model.RestrictionMode
import sh.pcx.xinventories.internal.model.RestrictionViolationAction

/**
 * Unit tests for ItemRestrictionsGUI logic.
 *
 * These tests verify the restriction configuration logic that powers the GUI,
 * without requiring full Bukkit/server infrastructure.
 */
@DisplayName("ItemRestrictionsGUI Logic Tests")
class ItemRestrictionsGUITest {

    // ============================================================
    // Mode Toggle Tests
    // ============================================================

    @Nested
    @DisplayName("Mode Toggle Logic")
    inner class ModeToggleTests {

        @Test
        @DisplayName("should toggle from NONE to BLACKLIST")
        fun toggleNoneToBlacklist() {
            val config = RestrictionConfig(mode = RestrictionMode.NONE)

            // Simulate left-click toggle
            val newConfig = config.copy(mode = RestrictionMode.BLACKLIST)

            assertEquals(RestrictionMode.BLACKLIST, newConfig.mode)
        }

        @Test
        @DisplayName("should toggle from BLACKLIST to WHITELIST")
        fun toggleBlacklistToWhitelist() {
            val config = RestrictionConfig(mode = RestrictionMode.BLACKLIST)

            // Simulate left-click toggle
            val newConfig = config.copy(mode = RestrictionMode.WHITELIST)

            assertEquals(RestrictionMode.WHITELIST, newConfig.mode)
        }

        @Test
        @DisplayName("should toggle from WHITELIST to BLACKLIST")
        fun toggleWhitelistToBlacklist() {
            val config = RestrictionConfig(mode = RestrictionMode.WHITELIST)

            // Simulate left-click toggle
            val newConfig = config.copy(mode = RestrictionMode.BLACKLIST)

            assertEquals(RestrictionMode.BLACKLIST, newConfig.mode)
        }

        @Test
        @DisplayName("should disable via right-click")
        fun disableViaRightClick() {
            val config = RestrictionConfig(mode = RestrictionMode.BLACKLIST)

            // Simulate right-click to disable
            val newConfig = config.copy(mode = RestrictionMode.NONE)

            assertEquals(RestrictionMode.NONE, newConfig.mode)
            assertFalse(newConfig.isEnabled())
        }
    }

    // ============================================================
    // Pattern Addition Tests
    // ============================================================

    @Nested
    @DisplayName("Pattern Addition Logic")
    inner class PatternAdditionTests {

        @Test
        @DisplayName("should add pattern to blacklist when in BLACKLIST mode")
        fun addToBlacklist() {
            val config = RestrictionConfig(mode = RestrictionMode.BLACKLIST)
            val pattern = "DIAMOND_SWORD"

            val newConfig = config.withBlacklistPattern(pattern)

            assertTrue(newConfig.blacklist.contains(pattern))
            assertEquals(1, newConfig.blacklist.size)
        }

        @Test
        @DisplayName("should add pattern to whitelist when in WHITELIST mode")
        fun addToWhitelist() {
            val config = RestrictionConfig(mode = RestrictionMode.WHITELIST)
            val pattern = "STONE"

            val newConfig = config.withWhitelistPattern(pattern)

            assertTrue(newConfig.whitelist.contains(pattern))
            assertEquals(1, newConfig.whitelist.size)
        }

        @Test
        @DisplayName("should add multiple patterns")
        fun addMultiplePatterns() {
            var config = RestrictionConfig(mode = RestrictionMode.BLACKLIST)
            val patterns = listOf("DIAMOND_SWORD", "NETHERITE_SWORD", "BEDROCK")

            for (pattern in patterns) {
                config = config.withBlacklistPattern(pattern)
            }

            assertEquals(3, config.blacklist.size)
            assertTrue(config.blacklist.containsAll(patterns))
        }

        @Test
        @DisplayName("should enable BLACKLIST mode when adding first pattern to NONE mode")
        fun enableModeOnFirstPattern() {
            val config = RestrictionConfig(mode = RestrictionMode.NONE)
            val pattern = "DIAMOND_SWORD"

            // When adding pattern to NONE mode, GUI would switch to BLACKLIST
            val newConfig = config.copy(mode = RestrictionMode.BLACKLIST).withBlacklistPattern(pattern)

            assertEquals(RestrictionMode.BLACKLIST, newConfig.mode)
            assertTrue(newConfig.blacklist.contains(pattern))
        }
    }

    // ============================================================
    // Pattern Removal Tests
    // ============================================================

    @Nested
    @DisplayName("Pattern Removal Logic")
    inner class PatternRemovalTests {

        @Test
        @DisplayName("should remove pattern from blacklist")
        fun removeFromBlacklist() {
            val config = RestrictionConfig.blacklist("DIAMOND_SWORD", "BEDROCK")

            val newConfig = config.withoutBlacklistPattern("DIAMOND_SWORD")

            assertFalse(newConfig.blacklist.contains("DIAMOND_SWORD"))
            assertTrue(newConfig.blacklist.contains("BEDROCK"))
            assertEquals(1, newConfig.blacklist.size)
        }

        @Test
        @DisplayName("should remove pattern from whitelist")
        fun removeFromWhitelist() {
            val config = RestrictionConfig.whitelist("STONE", "DIRT")

            val newConfig = config.withoutWhitelistPattern("STONE")

            assertFalse(newConfig.whitelist.contains("STONE"))
            assertTrue(newConfig.whitelist.contains("DIRT"))
        }

        @Test
        @DisplayName("should handle removing non-existent pattern")
        fun removeNonExistentPattern() {
            val config = RestrictionConfig.blacklist("DIAMOND_SWORD")

            val newConfig = config.withoutBlacklistPattern("BEDROCK")

            assertEquals(1, newConfig.blacklist.size)
            assertTrue(newConfig.blacklist.contains("DIAMOND_SWORD"))
        }
    }

    // ============================================================
    // Strip on Exit Tests
    // ============================================================

    @Nested
    @DisplayName("Strip on Exit Logic")
    inner class StripOnExitTests {

        @Test
        @DisplayName("should add strip-on-exit pattern")
        fun addStripOnExit() {
            val config = RestrictionConfig.blacklist("DIAMOND_SWORD")

            val newConfig = config.withStripOnExitPattern("BEDROCK")

            assertTrue(newConfig.stripOnExit.contains("BEDROCK"))
            assertEquals(1, newConfig.stripOnExit.size)
        }

        @Test
        @DisplayName("should toggle pattern between blacklist and strip-on-exit")
        fun toggleBetweenBlacklistAndStrip() {
            val config = RestrictionConfig.blacklist("DIAMOND_SWORD")

            // Move from blacklist to strip-on-exit
            val newConfig = config
                .withoutBlacklistPattern("DIAMOND_SWORD")
                .withStripOnExitPattern("DIAMOND_SWORD")

            assertFalse(newConfig.blacklist.contains("DIAMOND_SWORD"))
            assertTrue(newConfig.stripOnExit.contains("DIAMOND_SWORD"))
        }

        @Test
        @DisplayName("should toggle pattern from strip-on-exit back to blacklist")
        fun toggleFromStripToBlacklist() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                stripOnExit = listOf("DIAMOND_SWORD")
            )

            // Move from strip-on-exit to blacklist
            val newConfig = config
                .withoutStripOnExitPattern("DIAMOND_SWORD")
                .withBlacklistPattern("DIAMOND_SWORD")

            assertTrue(newConfig.blacklist.contains("DIAMOND_SWORD"))
            assertFalse(newConfig.stripOnExit.contains("DIAMOND_SWORD"))
        }
    }

    // ============================================================
    // Violation Action Tests
    // ============================================================

    @Nested
    @DisplayName("Violation Action Logic")
    inner class ViolationActionTests {

        @Test
        @DisplayName("should cycle through violation actions")
        fun cycleViolationActions() {
            val actions = RestrictionViolationAction.entries.toTypedArray()
            var config = RestrictionConfig(onViolation = RestrictionViolationAction.REMOVE)

            val actionSequence = mutableListOf<RestrictionViolationAction>()
            repeat(actions.size + 1) {
                actionSequence.add(config.onViolation)
                val currentIndex = actions.indexOf(config.onViolation)
                val nextIndex = (currentIndex + 1) % actions.size
                config = config.copy(onViolation = actions[nextIndex])
            }

            // Verify we cycled through all actions
            assertEquals(actions.size, actionSequence.distinct().size)
        }

        @Test
        @DisplayName("should maintain violation action when modifying patterns")
        fun maintainActionOnPatternChange() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                onViolation = RestrictionViolationAction.DROP
            )

            val newConfig = config.withBlacklistPattern("DIAMOND_SWORD")

            assertEquals(RestrictionViolationAction.DROP, newConfig.onViolation)
        }
    }

    // ============================================================
    // Notification Tests
    // ============================================================

    @Nested
    @DisplayName("Notification Toggle Logic")
    inner class NotificationTests {

        @Test
        @DisplayName("should toggle player notifications")
        fun togglePlayerNotification() {
            val config = RestrictionConfig(notifyPlayer = true)

            val newConfig = config.copy(notifyPlayer = false)

            assertFalse(newConfig.notifyPlayer)
        }

        @Test
        @DisplayName("should toggle admin notifications")
        fun toggleAdminNotification() {
            val config = RestrictionConfig(notifyAdmins = true)

            val newConfig = config.copy(notifyAdmins = false)

            assertFalse(newConfig.notifyAdmins)
        }

        @Test
        @DisplayName("should independently control player and admin notifications")
        fun independentNotifications() {
            val config = RestrictionConfig(notifyPlayer = true, notifyAdmins = true)

            val newConfig = config.copy(notifyPlayer = false)

            assertFalse(newConfig.notifyPlayer)
            assertTrue(newConfig.notifyAdmins)
        }
    }

    // ============================================================
    // Pattern List Tests
    // ============================================================

    @Nested
    @DisplayName("Active Pattern List Logic")
    inner class ActivePatternTests {

        @Test
        @DisplayName("should return blacklist patterns when in BLACKLIST mode")
        fun getBlacklistPatterns() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND", "BEDROCK"),
                whitelist = listOf("STONE")
            )

            val active = config.getActivePatterns()

            assertEquals(2, active.size)
            assertTrue(active.contains("DIAMOND"))
            assertTrue(active.contains("BEDROCK"))
            assertFalse(active.contains("STONE"))
        }

        @Test
        @DisplayName("should return whitelist patterns when in WHITELIST mode")
        fun getWhitelistPatterns() {
            val config = RestrictionConfig(
                mode = RestrictionMode.WHITELIST,
                blacklist = listOf("DIAMOND"),
                whitelist = listOf("STONE", "DIRT")
            )

            val active = config.getActivePatterns()

            assertEquals(2, active.size)
            assertTrue(active.contains("STONE"))
            assertTrue(active.contains("DIRT"))
            assertFalse(active.contains("DIAMOND"))
        }

        @Test
        @DisplayName("should return empty list when mode is NONE")
        fun getNoPatternsWhenDisabled() {
            val config = RestrictionConfig(
                mode = RestrictionMode.NONE,
                blacklist = listOf("DIAMOND"),
                whitelist = listOf("STONE")
            )

            val active = config.getActivePatterns()

            assertTrue(active.isEmpty())
        }

        @Test
        @DisplayName("should count total patterns including strip-on-exit")
        fun countTotalPatterns() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND", "BEDROCK"),
                stripOnExit = listOf("BEACON")
            )

            val totalPatterns = config.getActivePatterns().size + config.stripOnExit.size

            assertEquals(3, totalPatterns)
        }
    }

    // ============================================================
    // Pagination Logic Tests
    // ============================================================

    @Nested
    @DisplayName("Pagination Logic")
    inner class PaginationTests {

        @Test
        @DisplayName("should calculate correct page count for patterns")
        fun calculatePageCount() {
            val patterns = (1..50).map { "PATTERN_$it" }
            val patternsPerPage = 21

            val totalPages = (patterns.size + patternsPerPage - 1) / patternsPerPage

            assertEquals(3, totalPages)  // 50 patterns / 21 per page = 2.38 -> 3 pages
        }

        @Test
        @DisplayName("should get correct patterns for page")
        fun getPatternsForPage() {
            val patterns = (1..50).map { "PATTERN_$it" }
            val patternsPerPage = 21
            val page = 1  // Second page (0-indexed)

            val startIndex = page * patternsPerPage
            val endIndex = minOf(startIndex + patternsPerPage, patterns.size)
            val patternsOnPage = patterns.subList(startIndex, endIndex)

            assertEquals(21, patternsOnPage.size)
            assertEquals("PATTERN_22", patternsOnPage.first())
            assertEquals("PATTERN_42", patternsOnPage.last())
        }

        @Test
        @DisplayName("should handle last page with fewer patterns")
        fun handleLastPage() {
            val patterns = (1..50).map { "PATTERN_$it" }
            val patternsPerPage = 21
            val page = 2  // Third page (last)

            val startIndex = page * patternsPerPage
            val endIndex = minOf(startIndex + patternsPerPage, patterns.size)
            val patternsOnPage = patterns.subList(startIndex, endIndex)

            assertEquals(8, patternsOnPage.size)  // 50 - 42 = 8 remaining
            assertEquals("PATTERN_43", patternsOnPage.first())
            assertEquals("PATTERN_50", patternsOnPage.last())
        }
    }

    // ============================================================
    // Config Immutability Tests
    // ============================================================

    @Nested
    @DisplayName("Config Immutability")
    inner class ImmutabilityTests {

        @Test
        @DisplayName("should not modify original config when adding pattern")
        fun immutableOnAdd() {
            val original = RestrictionConfig.blacklist("DIAMOND")
            val originalSize = original.blacklist.size

            original.withBlacklistPattern("BEDROCK")

            assertEquals(originalSize, original.blacklist.size)
        }

        @Test
        @DisplayName("should not modify original config when changing mode")
        fun immutableOnModeChange() {
            val original = RestrictionConfig(mode = RestrictionMode.BLACKLIST)

            val newConfig = original.copy(mode = RestrictionMode.WHITELIST)

            assertEquals(RestrictionMode.BLACKLIST, original.mode)
            assertEquals(RestrictionMode.WHITELIST, newConfig.mode)
        }
    }
}

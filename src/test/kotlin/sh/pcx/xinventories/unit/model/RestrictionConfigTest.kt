package sh.pcx.xinventories.unit.model

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.model.RestrictionConfig
import sh.pcx.xinventories.internal.model.RestrictionMode
import sh.pcx.xinventories.internal.model.RestrictionViolationAction

@DisplayName("RestrictionConfig Tests")
class RestrictionConfigTest {

    // ============================================================
    // Creation Tests
    // ============================================================

    @Nested
    @DisplayName("Configuration Creation")
    inner class CreationTests {

        @Test
        @DisplayName("should create disabled config by default")
        fun defaultDisabled() {
            val config = RestrictionConfig()

            assertEquals(RestrictionMode.NONE, config.mode)
            assertFalse(config.isEnabled())
        }

        @Test
        @DisplayName("should create disabled config via factory")
        fun disabledFactory() {
            val config = RestrictionConfig.disabled()

            assertEquals(RestrictionMode.NONE, config.mode)
            assertFalse(config.isEnabled())
        }

        @Test
        @DisplayName("should create blacklist config via factory")
        fun blacklistFactory() {
            val config = RestrictionConfig.blacklist("DIAMOND", "BEDROCK", "BARRIER")

            assertEquals(RestrictionMode.BLACKLIST, config.mode)
            assertTrue(config.isEnabled())
            assertEquals(3, config.blacklist.size)
            assertTrue(config.blacklist.contains("DIAMOND"))
            assertTrue(config.blacklist.contains("BEDROCK"))
            assertTrue(config.blacklist.contains("BARRIER"))
        }

        @Test
        @DisplayName("should create whitelist config via factory")
        fun whitelistFactory() {
            val config = RestrictionConfig.whitelist("STONE", "DIRT", "COBBLESTONE")

            assertEquals(RestrictionMode.WHITELIST, config.mode)
            assertTrue(config.isEnabled())
            assertEquals(3, config.whitelist.size)
        }
    }

    // ============================================================
    // Active Patterns Tests
    // ============================================================

    @Nested
    @DisplayName("Active Patterns")
    inner class ActivePatternsTests {

        @Test
        @DisplayName("should return blacklist when mode is BLACKLIST")
        fun activeBlacklist() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND", "BEDROCK"),
                whitelist = listOf("STONE", "DIRT")
            )

            val active = config.getActivePatterns()

            assertEquals(2, active.size)
            assertTrue(active.contains("DIAMOND"))
            assertTrue(active.contains("BEDROCK"))
            assertFalse(active.contains("STONE"))
        }

        @Test
        @DisplayName("should return whitelist when mode is WHITELIST")
        fun activeWhitelist() {
            val config = RestrictionConfig(
                mode = RestrictionMode.WHITELIST,
                blacklist = listOf("DIAMOND", "BEDROCK"),
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
        fun activeNone() {
            val config = RestrictionConfig(
                mode = RestrictionMode.NONE,
                blacklist = listOf("DIAMOND"),
                whitelist = listOf("STONE")
            )

            val active = config.getActivePatterns()

            assertTrue(active.isEmpty())
        }
    }

    // ============================================================
    // Pattern Modification Tests
    // ============================================================

    @Nested
    @DisplayName("Pattern Modification")
    inner class PatternModificationTests {

        @Test
        @DisplayName("should add blacklist pattern")
        fun addBlacklistPattern() {
            val original = RestrictionConfig.blacklist("DIAMOND")
            val modified = original.withBlacklistPattern("BEDROCK")

            assertEquals(1, original.blacklist.size)
            assertEquals(2, modified.blacklist.size)
            assertTrue(modified.blacklist.contains("DIAMOND"))
            assertTrue(modified.blacklist.contains("BEDROCK"))
        }

        @Test
        @DisplayName("should remove blacklist pattern")
        fun removeBlacklistPattern() {
            val original = RestrictionConfig.blacklist("DIAMOND", "BEDROCK")
            val modified = original.withoutBlacklistPattern("BEDROCK")

            assertEquals(2, original.blacklist.size)
            assertEquals(1, modified.blacklist.size)
            assertTrue(modified.blacklist.contains("DIAMOND"))
            assertFalse(modified.blacklist.contains("BEDROCK"))
        }

        @Test
        @DisplayName("should add whitelist pattern")
        fun addWhitelistPattern() {
            val original = RestrictionConfig.whitelist("STONE")
            val modified = original.withWhitelistPattern("DIRT")

            assertEquals(1, original.whitelist.size)
            assertEquals(2, modified.whitelist.size)
        }

        @Test
        @DisplayName("should remove whitelist pattern")
        fun removeWhitelistPattern() {
            val original = RestrictionConfig.whitelist("STONE", "DIRT")
            val modified = original.withoutWhitelistPattern("DIRT")

            assertEquals(2, original.whitelist.size)
            assertEquals(1, modified.whitelist.size)
        }

        @Test
        @DisplayName("should add strip on exit pattern")
        fun addStripOnExitPattern() {
            val original = RestrictionConfig.blacklist("DIAMOND")
            val modified = original.withStripOnExitPattern("BEDROCK")

            assertTrue(original.stripOnExit.isEmpty())
            assertEquals(1, modified.stripOnExit.size)
            assertTrue(modified.stripOnExit.contains("BEDROCK"))
        }

        @Test
        @DisplayName("should remove strip on exit pattern")
        fun removeStripOnExitPattern() {
            val original = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                stripOnExit = listOf("BEDROCK", "BARRIER")
            )
            val modified = original.withoutStripOnExitPattern("BARRIER")

            assertEquals(2, original.stripOnExit.size)
            assertEquals(1, modified.stripOnExit.size)
            assertTrue(modified.stripOnExit.contains("BEDROCK"))
        }
    }

    // ============================================================
    // Immutability Tests
    // ============================================================

    @Nested
    @DisplayName("Immutability")
    inner class ImmutabilityTests {

        @Test
        @DisplayName("should not modify original when adding pattern")
        fun addDoesNotModify() {
            val original = RestrictionConfig.blacklist("DIAMOND")
            val originalSize = original.blacklist.size

            original.withBlacklistPattern("BEDROCK")

            assertEquals(originalSize, original.blacklist.size)
        }

        @Test
        @DisplayName("should not modify original when removing pattern")
        fun removeDoesNotModify() {
            val original = RestrictionConfig.blacklist("DIAMOND", "BEDROCK")
            val originalSize = original.blacklist.size

            original.withoutBlacklistPattern("BEDROCK")

            assertEquals(originalSize, original.blacklist.size)
        }
    }

    // ============================================================
    // Violation Action Tests
    // ============================================================

    @Nested
    @DisplayName("Violation Action")
    inner class ViolationActionTests {

        @Test
        @DisplayName("should default to REMOVE action")
        fun defaultRemoveAction() {
            val config = RestrictionConfig()

            assertEquals(RestrictionViolationAction.REMOVE, config.onViolation)
        }

        @Test
        @DisplayName("should support PREVENT action")
        fun preventAction() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                onViolation = RestrictionViolationAction.PREVENT
            )

            assertEquals(RestrictionViolationAction.PREVENT, config.onViolation)
        }

        @Test
        @DisplayName("should support DROP action")
        fun dropAction() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                onViolation = RestrictionViolationAction.DROP
            )

            assertEquals(RestrictionViolationAction.DROP, config.onViolation)
        }
    }

    // ============================================================
    // Notification Settings Tests
    // ============================================================

    @Nested
    @DisplayName("Notification Settings")
    inner class NotificationTests {

        @Test
        @DisplayName("should default to notifying player and admins")
        fun defaultNotifications() {
            val config = RestrictionConfig()

            assertTrue(config.notifyPlayer)
            assertTrue(config.notifyAdmins)
        }

        @Test
        @DisplayName("should allow disabling notifications")
        fun disableNotifications() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                notifyPlayer = false,
                notifyAdmins = false
            )

            assertFalse(config.notifyPlayer)
            assertFalse(config.notifyAdmins)
        }
    }
}

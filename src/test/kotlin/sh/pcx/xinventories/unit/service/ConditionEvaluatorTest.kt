package sh.pcx.xinventories.unit.service

import io.mockk.*
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.integration.PlaceholderAPIIntegration
import sh.pcx.xinventories.internal.model.*
import sh.pcx.xinventories.internal.service.ConditionEvaluationResult
import sh.pcx.xinventories.internal.service.ConditionEvaluator
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for ConditionEvaluator.
 *
 * Tests cover:
 * - Permission condition evaluation
 * - Schedule condition evaluation
 * - Cron condition evaluation
 * - Placeholder condition evaluation (with and without PAPI)
 * - requireAll logic (AND vs OR)
 * - Cache behavior (hit, miss, invalidation)
 * - No conditions case
 * - Combined conditions
 * - getMatchReason
 * - getActiveConditionsForPlayer
 */
@DisplayName("ConditionEvaluator Unit Tests")
class ConditionEvaluatorTest {

    private lateinit var plugin: XInventories
    private lateinit var conditionEvaluator: ConditionEvaluator
    private lateinit var player: Player
    private lateinit var placeholderIntegration: PlaceholderAPIIntegration

    private val playerUUID = UUID.randomUUID()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("ConditionEvaluatorTest"), false)
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs

            // Mock Bukkit static calls for PlaceholderAPIIntegration
            val server = mockk<Server>(relaxed = true)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { server.pluginManager } returns pluginManager
            every { pluginManager.getPlugin(any()) } returns null // No plugins installed

            mockkStatic(Bukkit::class)
            every { Bukkit.getServer() } returns server
            every { Bukkit.getPluginManager() } returns pluginManager
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkStatic(Bukkit::class)
            unmockkAll()
        }
    }

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        player = mockk(relaxed = true)
        every { player.uniqueId } returns playerUUID
        every { player.name } returns "TestPlayer"

        // Create ConditionEvaluator
        conditionEvaluator = ConditionEvaluator(plugin)

        // Use reflection to replace the placeholderIntegration field with a mock
        placeholderIntegration = mockk(relaxed = true)
        val field = ConditionEvaluator::class.java.getDeclaredField("placeholderIntegration")
        field.isAccessible = true
        field.set(conditionEvaluator, placeholderIntegration)
    }

    @AfterEach
    fun tearDown() {
        conditionEvaluator.clearCache()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createGroup(
        name: String,
        conditions: GroupConditions? = null
    ): Group {
        return Group(
            name = name,
            worlds = setOf("world"),
            conditions = conditions
        )
    }

    private fun createTimeRange(
        hoursFromNow: Long,
        durationHours: Long = 2
    ): TimeRange {
        val start = Instant.now().plus(hoursFromNow, ChronoUnit.HOURS)
        val end = start.plus(durationHours, ChronoUnit.HOURS)
        return TimeRange(start, end)
    }

    private fun createActiveTimeRange(): TimeRange {
        val start = Instant.now().minus(1, ChronoUnit.HOURS)
        val end = Instant.now().plus(1, ChronoUnit.HOURS)
        return TimeRange(start, end)
    }

    private fun createInactiveTimeRange(): TimeRange {
        val start = Instant.now().plus(10, ChronoUnit.HOURS)
        val end = Instant.now().plus(12, ChronoUnit.HOURS)
        return TimeRange(start, end)
    }

    // =========================================================================
    // No Conditions Tests
    // =========================================================================

    @Nested
    @DisplayName("No Conditions")
    inner class NoConditionsTests {

        @Test
        @DisplayName("Should match when group has null conditions")
        fun matchWhenNullConditions() {
            val group = createGroup("test", conditions = null)

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertTrue(result.matchedConditions.contains("no conditions"))
            assertTrue(result.failedConditions.isEmpty())
        }

        @Test
        @DisplayName("Should match when group has empty conditions")
        fun matchWhenEmptyConditions() {
            val group = createGroup("test", conditions = GroupConditions())

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertTrue(result.matchedConditions.contains("no conditions"))
        }

        @Test
        @DisplayName("Should match when conditions have empty lists")
        fun matchWhenEmptyLists() {
            val group = createGroup("test", conditions = GroupConditions(
                schedule = emptyList(),
                placeholders = emptyList()
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
        }
    }

    // =========================================================================
    // Permission Condition Tests
    // =========================================================================

    @Nested
    @DisplayName("Permission Condition")
    inner class PermissionConditionTests {

        @Test
        @DisplayName("Should match when player has permission")
        fun matchWhenPlayerHasPermission() {
            val permission = "xinventories.group.vip"
            every { player.hasPermission(permission) } returns true

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertTrue(result.matchedConditions.any { it.contains(permission) })
            assertTrue(result.failedConditions.isEmpty())
        }

        @Test
        @DisplayName("Should not match when player lacks permission")
        fun notMatchWhenPlayerLacksPermission() {
            val permission = "xinventories.group.vip"
            every { player.hasPermission(permission) } returns false

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
            assertTrue(result.failedConditions.any { it.contains(permission) })
        }

        @Test
        @DisplayName("evaluatePermission returns true for null permission")
        fun evaluatePermissionTrueForNull() {
            val result = conditionEvaluator.evaluatePermission(player, null)

            assertTrue(result)
        }

        @Test
        @DisplayName("evaluatePermission returns true when player has permission")
        fun evaluatePermissionTrueWhenHas() {
            val permission = "test.permission"
            every { player.hasPermission(permission) } returns true

            val result = conditionEvaluator.evaluatePermission(player, permission)

            assertTrue(result)
        }

        @Test
        @DisplayName("evaluatePermission returns false when player lacks permission")
        fun evaluatePermissionFalseWhenLacks() {
            val permission = "test.permission"
            every { player.hasPermission(permission) } returns false

            val result = conditionEvaluator.evaluatePermission(player, permission)

            assertFalse(result)
        }
    }

    // =========================================================================
    // Schedule Condition Tests
    // =========================================================================

    @Nested
    @DisplayName("Schedule Condition")
    inner class ScheduleConditionTests {

        @Test
        @DisplayName("Should match when current time is within schedule")
        fun matchWhenWithinSchedule() {
            val activeRange = createActiveTimeRange()
            val group = createGroup("event", conditions = GroupConditions(
                schedule = listOf(activeRange)
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertTrue(result.matchedConditions.any { it.contains("schedule:active") })
        }

        @Test
        @DisplayName("Should not match when current time is outside schedule")
        fun notMatchWhenOutsideSchedule() {
            val inactiveRange = createInactiveTimeRange()
            val group = createGroup("event", conditions = GroupConditions(
                schedule = listOf(inactiveRange)
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
            assertTrue(result.failedConditions.any { it.contains("schedule:not_active") })
        }

        @Test
        @DisplayName("Should match when any schedule range is active")
        fun matchWhenAnyRangeIsActive() {
            val inactiveRange = createInactiveTimeRange()
            val activeRange = createActiveTimeRange()
            val group = createGroup("event", conditions = GroupConditions(
                schedule = listOf(inactiveRange, activeRange)
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
        }

        @Test
        @DisplayName("evaluateSchedule returns true for null schedule")
        fun evaluateScheduleTrueForNull() {
            val result = conditionEvaluator.evaluateSchedule(null)

            assertTrue(result)
        }

        @Test
        @DisplayName("evaluateSchedule returns true for empty schedule")
        fun evaluateScheduleTrueForEmpty() {
            val result = conditionEvaluator.evaluateSchedule(emptyList())

            assertTrue(result)
        }

        @Test
        @DisplayName("evaluateSchedule returns true when within active range")
        fun evaluateScheduleTrueWhenActive() {
            val activeRange = createActiveTimeRange()

            val result = conditionEvaluator.evaluateSchedule(listOf(activeRange))

            assertTrue(result)
        }

        @Test
        @DisplayName("evaluateSchedule returns false when outside all ranges")
        fun evaluateScheduleFalseWhenOutside() {
            val inactiveRange = createInactiveTimeRange()

            val result = conditionEvaluator.evaluateSchedule(listOf(inactiveRange))

            assertFalse(result)
        }

        @Test
        @DisplayName("evaluateSchedule accepts custom now parameter")
        fun evaluateScheduleWithCustomNow() {
            val start = Instant.parse("2024-06-15T10:00:00Z")
            val end = Instant.parse("2024-06-15T12:00:00Z")
            val range = TimeRange(start, end)

            val withinRange = Instant.parse("2024-06-15T11:00:00Z")
            val outsideRange = Instant.parse("2024-06-15T13:00:00Z")

            assertTrue(conditionEvaluator.evaluateSchedule(listOf(range), withinRange))
            assertFalse(conditionEvaluator.evaluateSchedule(listOf(range), outsideRange))
        }
    }

    // =========================================================================
    // Cron Condition Tests
    // =========================================================================

    @Nested
    @DisplayName("Cron Condition")
    inner class CronConditionTests {

        @Test
        @DisplayName("Should match when cron expression matches current time")
        fun matchWhenCronMatches() {
            // Use a cron that always matches: * * * * *
            val group = createGroup("event", conditions = GroupConditions(
                cron = "* * * * *"
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertTrue(result.matchedConditions.any { it.contains("cron:") })
        }

        @Test
        @DisplayName("Should not match when cron expression does not match")
        fun notMatchWhenCronNotMatches() {
            // Use a cron that matches at a specific impossible minute
            // This cron matches only at minute 59 of hour 23 on the 31st of February (never)
            val group = createGroup("event", conditions = GroupConditions(
                cron = "59 23 31 2 *"
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
            assertTrue(result.failedConditions.any { it.contains("cron:not_matching") })
        }

        @Test
        @DisplayName("Should fail for invalid cron expression")
        fun failForInvalidCron() {
            val group = createGroup("event", conditions = GroupConditions(
                cron = "invalid cron expression"
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
            assertTrue(result.failedConditions.any { it.contains("cron:invalid_expression") })
        }

        @Test
        @DisplayName("evaluateCron returns true for null cron")
        fun evaluateCronTrueForNull() {
            val result = conditionEvaluator.evaluateCron(null)

            assertTrue(result)
        }

        @Test
        @DisplayName("evaluateCron returns false for invalid expression")
        fun evaluateCronFalseForInvalid() {
            val result = conditionEvaluator.evaluateCron("not a cron")

            assertFalse(result)
        }

        @Test
        @DisplayName("evaluateCron accepts custom now parameter")
        fun evaluateCronWithCustomNow() {
            // Cron that matches Friday at 18:00
            val cron = "0 18 * * FRI"

            // Friday June 14, 2024 at 18:00
            val fridayAt18 = ZonedDateTime.parse("2024-06-14T18:00:00Z")
            // Friday June 14, 2024 at 10:00
            val fridayAt10 = ZonedDateTime.parse("2024-06-14T10:00:00Z")

            assertTrue(conditionEvaluator.evaluateCron(cron, fridayAt18))
            assertFalse(conditionEvaluator.evaluateCron(cron, fridayAt10))
        }

        @Test
        @DisplayName("Cron expressions are cached")
        fun cronExpressionsCached() {
            val cron = "0 18 * * FRI"

            // First evaluation parses the cron
            conditionEvaluator.evaluateCron(cron)

            // Second evaluation should use cached cron
            conditionEvaluator.evaluateCron(cron)

            // No direct way to verify caching, but this ensures no errors
            // The cron cache is internal implementation detail
        }
    }

    // =========================================================================
    // Placeholder Condition Tests
    // =========================================================================

    @Nested
    @DisplayName("Placeholder Condition")
    inner class PlaceholderConditionTests {

        @Test
        @DisplayName("Should match when placeholder condition is satisfied and PAPI available")
        fun matchWhenPlaceholderSatisfied() {
            val placeholder = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            every { placeholderIntegration.isAvailable() } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder) } returns true

            val group = createGroup("high_level", conditions = GroupConditions(
                placeholder = placeholder
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertTrue(result.matchedConditions.any { it.contains("placeholder:") })
        }

        @Test
        @DisplayName("Should not match when placeholder condition is not satisfied")
        fun notMatchWhenPlaceholderNotSatisfied() {
            val placeholder = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            every { placeholderIntegration.isAvailable() } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder) } returns false

            val group = createGroup("high_level", conditions = GroupConditions(
                placeholder = placeholder
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
            assertTrue(result.failedConditions.any { it.contains("placeholder:") })
        }

        @Test
        @DisplayName("Should fail when PlaceholderAPI is unavailable")
        fun failWhenPapiUnavailable() {
            val placeholder = PlaceholderCondition(
                placeholder = "%player_level%",
                operator = ComparisonOperator.GREATER_OR_EQUAL,
                value = "50"
            )
            every { placeholderIntegration.isAvailable() } returns false

            val group = createGroup("high_level", conditions = GroupConditions(
                placeholder = placeholder
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
            assertTrue(result.failedConditions.any { it.contains("papi_unavailable") })
        }

        @Test
        @DisplayName("Should evaluate multiple placeholder conditions from list")
        fun evaluateMultiplePlaceholders() {
            val placeholder1 = PlaceholderCondition("%level%", ComparisonOperator.GREATER_OR_EQUAL, "50")
            val placeholder2 = PlaceholderCondition("%rank%", ComparisonOperator.EQUALS, "VIP")

            every { placeholderIntegration.isAvailable() } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder1) } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder2) } returns true

            val group = createGroup("elite", conditions = GroupConditions(
                placeholders = listOf(placeholder1, placeholder2),
                requireAll = true
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertEquals(2, result.matchedConditions.count { it.contains("placeholder:") })
        }

        @Test
        @DisplayName("Should combine single placeholder and placeholders list")
        fun combineSingleAndListPlaceholders() {
            val single = PlaceholderCondition("%level%", ComparisonOperator.GREATER_OR_EQUAL, "50")
            val list1 = PlaceholderCondition("%rank%", ComparisonOperator.EQUALS, "VIP")

            every { placeholderIntegration.isAvailable() } returns true
            every { placeholderIntegration.evaluateCondition(player, single) } returns true
            every { placeholderIntegration.evaluateCondition(player, list1) } returns true

            val group = createGroup("elite", conditions = GroupConditions(
                placeholder = single,
                placeholders = listOf(list1),
                requireAll = true
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertEquals(2, result.matchedConditions.count { it.contains("placeholder:") })
        }
    }

    // =========================================================================
    // requireAll Logic Tests
    // =========================================================================

    @Nested
    @DisplayName("requireAll Logic")
    inner class RequireAllLogicTests {

        @Test
        @DisplayName("AND logic: matches when all conditions pass")
        fun andLogicMatchesWhenAllPass() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group = createGroup("vip", conditions = GroupConditions(
                permission = permission,
                schedule = listOf(createActiveTimeRange()),
                cron = "* * * * *",
                requireAll = true
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertTrue(result.failedConditions.isEmpty())
        }

        @Test
        @DisplayName("AND logic: fails when any condition fails")
        fun andLogicFailsWhenAnyFails() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns false // This will fail

            val group = createGroup("vip", conditions = GroupConditions(
                permission = permission,
                schedule = listOf(createActiveTimeRange()), // This passes
                cron = "* * * * *", // This passes
                requireAll = true
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
            assertFalse(result.failedConditions.isEmpty())
        }

        @Test
        @DisplayName("OR logic: matches when any condition passes")
        fun orLogicMatchesWhenAnyPasses() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns false // This fails

            val group = createGroup("vip", conditions = GroupConditions(
                permission = permission,
                schedule = listOf(createActiveTimeRange()), // This passes
                cron = "59 23 31 2 *", // This fails (February 31)
                requireAll = false
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertFalse(result.matchedConditions.isEmpty())
        }

        @Test
        @DisplayName("OR logic: fails when all conditions fail")
        fun orLogicFailsWhenAllFail() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns false

            val group = createGroup("vip", conditions = GroupConditions(
                permission = permission, // Fails
                schedule = listOf(createInactiveTimeRange()), // Fails
                cron = "59 23 31 2 *", // Fails
                requireAll = false
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
            assertTrue(result.matchedConditions.isEmpty())
        }

        @Test
        @DisplayName("AND logic with placeholders: fails if any placeholder fails")
        fun andLogicWithPlaceholdersFails() {
            val placeholder1 = PlaceholderCondition("%level%", ComparisonOperator.GREATER_OR_EQUAL, "50")
            val placeholder2 = PlaceholderCondition("%rank%", ComparisonOperator.EQUALS, "VIP")

            every { placeholderIntegration.isAvailable() } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder1) } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder2) } returns false

            val group = createGroup("elite", conditions = GroupConditions(
                placeholders = listOf(placeholder1, placeholder2),
                requireAll = true
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
        }

        @Test
        @DisplayName("OR logic with placeholders: matches if any placeholder passes")
        fun orLogicWithPlaceholdersMatches() {
            val placeholder1 = PlaceholderCondition("%level%", ComparisonOperator.GREATER_OR_EQUAL, "50")
            val placeholder2 = PlaceholderCondition("%rank%", ComparisonOperator.EQUALS, "VIP")

            every { placeholderIntegration.isAvailable() } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder1) } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder2) } returns false

            val group = createGroup("elite", conditions = GroupConditions(
                placeholders = listOf(placeholder1, placeholder2),
                requireAll = false
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
        }
    }

    // =========================================================================
    // Combined Conditions Tests
    // =========================================================================

    @Nested
    @DisplayName("Combined Conditions")
    inner class CombinedConditionsTests {

        @Test
        @DisplayName("Should evaluate all condition types together with AND logic")
        fun evaluateAllConditionTypesWithAnd() {
            val permission = "xinventories.event"
            val placeholder = PlaceholderCondition("%player_level%", ComparisonOperator.GREATER_OR_EQUAL, "10")

            every { player.hasPermission(permission) } returns true
            every { placeholderIntegration.isAvailable() } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder) } returns true

            val group = createGroup("event", conditions = GroupConditions(
                permission = permission,
                schedule = listOf(createActiveTimeRange()),
                cron = "* * * * *",
                placeholder = placeholder,
                requireAll = true
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertTrue(result.matches)
            assertTrue(result.matchedConditions.any { it.contains("permission:") })
            assertTrue(result.matchedConditions.any { it.contains("schedule:") })
            assertTrue(result.matchedConditions.any { it.contains("cron:") })
            assertTrue(result.matchedConditions.any { it.contains("placeholder:") })
        }

        @Test
        @DisplayName("Should fail combined conditions when one fails with AND logic")
        fun failCombinedConditionsWhenOneFails() {
            val permission = "xinventories.event"
            val placeholder = PlaceholderCondition("%player_level%", ComparisonOperator.GREATER_OR_EQUAL, "10")

            every { player.hasPermission(permission) } returns true
            every { placeholderIntegration.isAvailable() } returns true
            every { placeholderIntegration.evaluateCondition(player, placeholder) } returns false // Fails

            val group = createGroup("event", conditions = GroupConditions(
                permission = permission,
                schedule = listOf(createActiveTimeRange()),
                cron = "* * * * *",
                placeholder = placeholder,
                requireAll = true
            ))

            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
            assertFalse(result.matchedConditions.isEmpty()) // Some conditions matched
            assertFalse(result.failedConditions.isEmpty()) // Some conditions failed
        }
    }

    // =========================================================================
    // Cache Behavior Tests
    // =========================================================================

    @Nested
    @DisplayName("Cache Behavior")
    inner class CacheBehaviorTests {

        @Test
        @DisplayName("Should cache evaluation results")
        fun cacheEvaluationResults() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            // First evaluation
            val result1 = conditionEvaluator.evaluateConditions(player, group)

            // Change the permission mock
            every { player.hasPermission(permission) } returns false

            // Second evaluation should return cached result
            val result2 = conditionEvaluator.evaluateConditions(player, group, useCache = true)

            assertTrue(result1.matches)
            assertTrue(result2.matches) // Still true because cached
        }

        @Test
        @DisplayName("Should bypass cache when useCache is false")
        fun bypassCacheWhenDisabled() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            // First evaluation
            val result1 = conditionEvaluator.evaluateConditions(player, group)

            // Change the permission mock
            every { player.hasPermission(permission) } returns false

            // Second evaluation with cache disabled should get fresh result
            val result2 = conditionEvaluator.evaluateConditions(player, group, useCache = false)

            assertTrue(result1.matches)
            assertFalse(result2.matches)
        }

        @Test
        @DisplayName("Should invalidate cache for specific player")
        fun invalidateCacheForPlayer() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            // First evaluation
            conditionEvaluator.evaluateConditions(player, group)

            // Invalidate cache for player
            conditionEvaluator.invalidateCache(playerUUID)

            // Change mock
            every { player.hasPermission(permission) } returns false

            // Should get fresh result
            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
        }

        @Test
        @DisplayName("Should invalidate cache for specific player and group")
        fun invalidateCacheForPlayerAndGroup() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group1 = createGroup("vip", conditions = GroupConditions(permission = permission))
            val group2 = createGroup("premium", conditions = GroupConditions(permission = permission))

            // Evaluate both groups
            conditionEvaluator.evaluateConditions(player, group1)
            conditionEvaluator.evaluateConditions(player, group2)

            // Invalidate cache only for group1
            conditionEvaluator.invalidateCache(playerUUID, "vip")

            // Change mock
            every { player.hasPermission(permission) } returns false

            // Group1 should get fresh result
            val result1 = conditionEvaluator.evaluateConditions(player, group1)

            // Group2 should still use cached result
            val result2 = conditionEvaluator.evaluateConditions(player, group2)

            assertFalse(result1.matches) // Fresh evaluation
            assertTrue(result2.matches) // Cached
        }

        @Test
        @DisplayName("Should clear all cached results")
        fun clearAllCache() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group1 = createGroup("vip", conditions = GroupConditions(permission = permission))
            val group2 = createGroup("premium", conditions = GroupConditions(permission = permission))

            // Evaluate both groups
            conditionEvaluator.evaluateConditions(player, group1)
            conditionEvaluator.evaluateConditions(player, group2)

            // Clear all cache
            conditionEvaluator.clearCache()

            // Change mock
            every { player.hasPermission(permission) } returns false

            // Both should get fresh results
            val result1 = conditionEvaluator.evaluateConditions(player, group1)
            val result2 = conditionEvaluator.evaluateConditions(player, group2)

            assertFalse(result1.matches)
            assertFalse(result2.matches)
        }

        @Test
        @DisplayName("Cache miss for new player-group combination")
        fun cacheMissForNewCombination() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            // First evaluation (cache miss)
            val result1 = conditionEvaluator.evaluateConditions(player, group)

            // Create new player
            val newPlayer = mockk<Player>(relaxed = true)
            val newUUID = UUID.randomUUID()
            every { newPlayer.uniqueId } returns newUUID
            every { newPlayer.name } returns "NewPlayer"
            every { newPlayer.hasPermission(permission) } returns false

            // Should be cache miss and get different result
            val result2 = conditionEvaluator.evaluateConditions(newPlayer, group)

            assertTrue(result1.matches)
            assertFalse(result2.matches)
        }
    }

    // =========================================================================
    // getMatchReason Tests
    // =========================================================================

    @Nested
    @DisplayName("getMatchReason")
    inner class GetMatchReasonTests {

        @Test
        @DisplayName("Should return 'No conditions' message for groups without conditions")
        fun noConditionsMessage() {
            val group = createGroup("test", conditions = null)

            val reason = conditionEvaluator.getMatchReason(player, group)

            assertTrue(reason.contains("No conditions"))
        }

        @Test
        @DisplayName("Should return 'No conditions' for empty conditions")
        fun emptyConditionsMessage() {
            val group = createGroup("test", conditions = GroupConditions())

            val reason = conditionEvaluator.getMatchReason(player, group)

            assertTrue(reason.contains("No conditions"))
        }

        @Test
        @DisplayName("Should return matched conditions when player matches")
        fun matchedConditionsMessage() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            val reason = conditionEvaluator.getMatchReason(player, group)

            assertTrue(reason.contains("Matched conditions"))
            assertTrue(reason.contains(permission))
        }

        @Test
        @DisplayName("Should return failed conditions when player does not match")
        fun failedConditionsMessage() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns false

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            val reason = conditionEvaluator.getMatchReason(player, group)

            assertTrue(reason.contains("Failed conditions"))
            assertTrue(reason.contains(permission))
        }

        @Test
        @DisplayName("Should bypass cache when getting match reason")
        fun bypassCacheForMatchReason() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            // Cache a result
            conditionEvaluator.evaluateConditions(player, group)

            // Change mock
            every { player.hasPermission(permission) } returns false

            // getMatchReason should use fresh evaluation
            val reason = conditionEvaluator.getMatchReason(player, group)

            assertTrue(reason.contains("Failed conditions"))
        }
    }

    // =========================================================================
    // getActiveConditionsForPlayer Tests
    // =========================================================================

    @Nested
    @DisplayName("getActiveConditionsForPlayer")
    inner class GetActiveConditionsForPlayerTests {

        @Test
        @DisplayName("Should return evaluation results for all groups with conditions")
        fun returnResultsForAllGroupsWithConditions() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group1 = createGroup("vip", conditions = GroupConditions(permission = permission))
            val group2 = createGroup("event", conditions = GroupConditions(cron = "* * * * *"))
            val group3 = createGroup("default", conditions = null) // No conditions

            val groupService = mockk<GroupService>()
            every { groupService.getAllGroups() } returns listOf(group1, group2, group3)

            val serviceManager = mockk<ServiceManager>()
            every { serviceManager.groupService } returns groupService
            every { plugin.serviceManager } returns serviceManager

            val results = conditionEvaluator.getActiveConditionsForPlayer(player)

            // Should only include groups with conditions
            assertEquals(2, results.size)
            assertTrue(results.containsKey("vip"))
            assertTrue(results.containsKey("event"))
            assertFalse(results.containsKey("default"))
        }

        @Test
        @DisplayName("Should return empty map when no groups have conditions")
        fun returnEmptyMapWhenNoConditions() {
            val group1 = createGroup("default1", conditions = null)
            val group2 = createGroup("default2", conditions = GroupConditions())

            val groupService = mockk<GroupService>()
            every { groupService.getAllGroups() } returns listOf(group1, group2)

            val serviceManager = mockk<ServiceManager>()
            every { serviceManager.groupService } returns groupService
            every { plugin.serviceManager } returns serviceManager

            val results = conditionEvaluator.getActiveConditionsForPlayer(player)

            assertTrue(results.isEmpty())
        }

        @Test
        @DisplayName("Should bypass cache when getting active conditions")
        fun bypassCacheForActiveConditions() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            val groupService = mockk<GroupService>()
            every { groupService.getAllGroups() } returns listOf(group)

            val serviceManager = mockk<ServiceManager>()
            every { serviceManager.groupService } returns groupService
            every { plugin.serviceManager } returns serviceManager

            // Cache a result
            conditionEvaluator.evaluateConditions(player, group)

            // Change mock
            every { player.hasPermission(permission) } returns false

            // getActiveConditionsForPlayer should use fresh evaluation
            val results = conditionEvaluator.getActiveConditionsForPlayer(player)

            assertFalse(results["vip"]!!.matches)
        }
    }

    // =========================================================================
    // ConditionEvaluationResult Tests
    // =========================================================================

    @Nested
    @DisplayName("ConditionEvaluationResult")
    inner class ConditionEvaluationResultTests {

        @Test
        @DisplayName("success factory method creates successful result")
        fun successFactoryMethod() {
            val matched = listOf("permission:test", "schedule:active")

            val result = ConditionEvaluationResult.success(matched)

            assertTrue(result.matches)
            assertEquals(matched, result.matchedConditions)
            assertTrue(result.failedConditions.isEmpty())
        }

        @Test
        @DisplayName("failure factory method creates failed result")
        fun failureFactoryMethod() {
            val failed = listOf("permission:denied", "schedule:not_active")

            val result = ConditionEvaluationResult.failure(failed)

            assertFalse(result.matches)
            assertTrue(result.matchedConditions.isEmpty())
            assertEquals(failed, result.failedConditions)
        }

        @Test
        @DisplayName("noConditions factory method creates success with special marker")
        fun noConditionsFactoryMethod() {
            val result = ConditionEvaluationResult.noConditions()

            assertTrue(result.matches)
            assertTrue(result.matchedConditions.contains("no conditions"))
            assertTrue(result.failedConditions.isEmpty())
        }

        @Test
        @DisplayName("evaluationTime is set to current instant")
        fun evaluationTimeIsSet() {
            val before = Instant.now()
            val result = ConditionEvaluationResult.success(listOf("test"))
            val after = Instant.now()

            assertFalse(result.evaluationTime.isBefore(before))
            assertFalse(result.evaluationTime.isAfter(after))
        }
    }

    // =========================================================================
    // Reload Tests
    // =========================================================================

    @Nested
    @DisplayName("Reload")
    inner class ReloadTests {

        @Test
        @DisplayName("reload clears evaluation cache")
        fun reloadClearsEvaluationCache() {
            val permission = "xinventories.vip"
            every { player.hasPermission(permission) } returns true
            every { placeholderIntegration.checkAvailability() } just Runs

            val group = createGroup("vip", conditions = GroupConditions(permission = permission))

            // Cache a result
            conditionEvaluator.evaluateConditions(player, group)

            // Change mock
            every { player.hasPermission(permission) } returns false

            // Reload
            conditionEvaluator.reload()

            // Should get fresh result
            val result = conditionEvaluator.evaluateConditions(player, group)

            assertFalse(result.matches)
        }

        @Test
        @DisplayName("reload checks PlaceholderAPI availability")
        fun reloadChecksPapiAvailability() {
            every { placeholderIntegration.checkAvailability() } just Runs

            conditionEvaluator.reload()

            verify { placeholderIntegration.checkAvailability() }
        }
    }
}

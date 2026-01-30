package sh.pcx.xinventories.internal.api

import io.mockk.*
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.integration.PlaceholderAPIIntegration
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.ConditionEvaluationResult
import sh.pcx.xinventories.internal.service.ConditionEvaluator
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.ServiceManager
import java.util.*

@DisplayName("ConditionAPIImpl")
class ConditionAPIImplTest {

    private lateinit var plugin: XInventories
    private lateinit var api: ConditionAPIImpl
    private lateinit var serviceManager: ServiceManager
    private lateinit var conditionEvaluator: ConditionEvaluator
    private lateinit var groupService: GroupService
    private lateinit var placeholderIntegration: PlaceholderAPIIntegration

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        conditionEvaluator = mockk(relaxed = true)
        groupService = mockk(relaxed = true)
        placeholderIntegration = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.conditionEvaluator } returns conditionEvaluator
        every { serviceManager.groupService } returns groupService
        every { conditionEvaluator.placeholderIntegration } returns placeholderIntegration

        api = ConditionAPIImpl(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("evaluateConditions")
    inner class EvaluateConditions {

        @Test
        @DisplayName("returns result when group exists")
        fun returnsResultWhenGroupExists() {
            val player = mockk<Player>()
            val group = mockk<Group>()
            val result = ConditionEvaluationResult.success(listOf("permission:test.perm"))

            every { groupService.getGroup("vip") } returns group
            every { conditionEvaluator.evaluateConditions(player, group) } returns result

            val actualResult = api.evaluateConditions(player, "vip")

            Assertions.assertNotNull(actualResult)
            assertTrue(actualResult!!.matches)
            assertTrue(actualResult.matchedConditions.contains("permission:test.perm"))
        }

        @Test
        @DisplayName("returns null when group not found")
        fun returnsNullWhenGroupNotFound() {
            val player = mockk<Player>()
            every { groupService.getGroup("nonexistent") } returns null

            val result = api.evaluateConditions(player, "nonexistent")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("returns failure result when conditions not met")
        fun returnsFailureResultWhenConditionsNotMet() {
            val player = mockk<Player>()
            val group = mockk<Group>()
            val result = ConditionEvaluationResult.failure(listOf("permission:admin.perm"))

            every { groupService.getGroup("admin") } returns group
            every { conditionEvaluator.evaluateConditions(player, group) } returns result

            val actualResult = api.evaluateConditions(player, "admin")

            Assertions.assertNotNull(actualResult)
            assertFalse(actualResult!!.matches)
            assertTrue(actualResult.failedConditions.contains("permission:admin.perm"))
        }
    }

    @Nested
    @DisplayName("getActiveConditionsForPlayer")
    inner class GetActiveConditionsForPlayer {

        @Test
        @DisplayName("returns conditions for all groups")
        fun returnsConditionsForAllGroups() {
            val player = mockk<Player>()
            val results = mapOf(
                "vip" to ConditionEvaluationResult.success(listOf("permission:vip")),
                "admin" to ConditionEvaluationResult.failure(listOf("permission:admin"))
            )
            every { conditionEvaluator.getActiveConditionsForPlayer(player) } returns results

            val actualResults = api.getActiveConditionsForPlayer(player)

            assertEquals(2, actualResults.size)
            assertTrue(actualResults["vip"]!!.matches)
            assertFalse(actualResults["admin"]!!.matches)
        }

        @Test
        @DisplayName("returns empty map when no conditional groups")
        fun returnsEmptyMapWhenNoConditionalGroups() {
            val player = mockk<Player>()
            every { conditionEvaluator.getActiveConditionsForPlayer(player) } returns emptyMap()

            val results = api.getActiveConditionsForPlayer(player)

            assertTrue(results.isEmpty())
        }
    }

    @Nested
    @DisplayName("getMatchReason")
    inner class GetMatchReason {

        @Test
        @DisplayName("returns match reason when group exists")
        fun returnsMatchReasonWhenGroupExists() {
            val player = mockk<Player>()
            val group = mockk<Group>()
            every { groupService.getGroup("vip") } returns group
            every { conditionEvaluator.getMatchReason(player, group) } returns "Matched conditions: permission:vip"

            val reason = api.getMatchReason(player, "vip")

            Assertions.assertNotNull(reason)
            assertTrue(reason!!.contains("Matched conditions"))
        }

        @Test
        @DisplayName("returns null when group not found")
        fun returnsNullWhenGroupNotFound() {
            val player = mockk<Player>()
            every { groupService.getGroup("nonexistent") } returns null

            val reason = api.getMatchReason(player, "nonexistent")

            Assertions.assertNull(reason)
        }

        @Test
        @DisplayName("returns failure reason when conditions not met")
        fun returnsFailureReasonWhenConditionsNotMet() {
            val player = mockk<Player>()
            val group = mockk<Group>()
            every { groupService.getGroup("admin") } returns group
            every { conditionEvaluator.getMatchReason(player, group) } returns "Failed conditions: permission:admin"

            val reason = api.getMatchReason(player, "admin")

            Assertions.assertNotNull(reason)
            assertTrue(reason!!.contains("Failed conditions"))
        }
    }

    @Nested
    @DisplayName("invalidateCache")
    inner class InvalidateCache {

        @Test
        @DisplayName("invalidates cache for player")
        fun invalidatesCacheForPlayer() {
            val player = mockk<Player>()
            val uuid = UUID.randomUUID()
            every { player.uniqueId } returns uuid
            every { conditionEvaluator.invalidateCache(uuid) } just Runs

            api.invalidateCache(player)

            verify { conditionEvaluator.invalidateCache(uuid) }
        }
    }

    @Nested
    @DisplayName("isPlaceholderAPIAvailable")
    inner class IsPlaceholderAPIAvailable {

        @Test
        @DisplayName("returns true when PAPI is available")
        fun returnsTrueWhenPapiIsAvailable() {
            every { placeholderIntegration.isAvailable() } returns true

            val result = api.isPlaceholderAPIAvailable()

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when PAPI is not available")
        fun returnsFalseWhenPapiIsNotAvailable() {
            every { placeholderIntegration.isAvailable() } returns false

            val result = api.isPlaceholderAPIAvailable()

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("parsePlaceholder")
    inner class ParsePlaceholder {

        @Test
        @DisplayName("parses placeholder when PAPI available")
        fun parsesPlaceholderWhenPapiAvailable() {
            val player = mockk<Player>()
            every { placeholderIntegration.isAvailable() } returns true
            every { placeholderIntegration.parsePlaceholders(player, "%player_level%") } returns "25"

            val result = api.parsePlaceholder(player, "%player_level%")

            assertEquals("25", result)
        }

        @Test
        @DisplayName("returns original when PAPI not available")
        fun returnsOriginalWhenPapiNotAvailable() {
            val player = mockk<Player>()
            every { placeholderIntegration.isAvailable() } returns false

            val result = api.parsePlaceholder(player, "%player_level%")

            assertEquals("%player_level%", result)
        }
    }
}

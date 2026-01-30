package sh.pcx.xinventories.internal.api

import io.mockk.*
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.*
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.RestrictionService
import sh.pcx.xinventories.internal.service.ServiceManager

@DisplayName("RestrictionAPIImpl")
class RestrictionAPIImplTest {

    private lateinit var plugin: XInventories
    private lateinit var api: RestrictionAPIImpl
    private lateinit var serviceManager: ServiceManager
    private lateinit var restrictionService: RestrictionService
    private lateinit var groupService: GroupService

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        restrictionService = mockk(relaxed = true)
        groupService = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.restrictionService } returns restrictionService
        every { serviceManager.groupService } returns groupService

        api = RestrictionAPIImpl(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("getRestrictionConfig")
    inner class GetRestrictionConfig {

        @Test
        @DisplayName("returns config when group exists")
        fun returnsConfigWhenGroupExists() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND_SWORD", "NETHERITE_*"),
                whitelist = emptyList()
            )
            val group = mockk<Group>()
            every { group.restrictions } returns config
            every { groupService.getGroup("pvp") } returns group

            val result = api.getRestrictionConfig("pvp")

            Assertions.assertNotNull(result)
            assertEquals(RestrictionMode.BLACKLIST, result?.mode)
            assertEquals(2, result?.blacklist?.size)
        }

        @Test
        @DisplayName("returns null when group not found")
        fun returnsNullWhenGroupNotFound() {
            every { groupService.getGroup("nonexistent") } returns null

            val result = api.getRestrictionConfig("nonexistent")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("returns null when group has no restrictions")
        fun returnsNullWhenGroupHasNoRestrictions() {
            val group = mockk<Group>()
            every { group.restrictions } returns null
            every { groupService.getGroup("survival") } returns group

            val result = api.getRestrictionConfig("survival")

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("testItem")
    inner class TestItem {

        @Test
        @DisplayName("returns true when item is restricted")
        fun returnsTrueWhenItemIsRestricted() {
            val item = mockk<ItemStack>()
            val config = RestrictionConfig(mode = RestrictionMode.BLACKLIST, blacklist = listOf("DIAMOND_SWORD"))
            val group = mockk<Group>()
            every { group.restrictions } returns config
            every { groupService.getGroup("pvp") } returns group
            every { restrictionService.testItem(item, config) } returns (true to "DIAMOND_SWORD")

            val result = api.testItem(item, "pvp")

            assertTrue(result.first)
            assertEquals("DIAMOND_SWORD", result.second)
        }

        @Test
        @DisplayName("returns false when item is not restricted")
        fun returnsFalseWhenItemIsNotRestricted() {
            val item = mockk<ItemStack>()
            val config = RestrictionConfig(mode = RestrictionMode.BLACKLIST, blacklist = listOf("DIAMOND_SWORD"))
            val group = mockk<Group>()
            every { group.restrictions } returns config
            every { groupService.getGroup("survival") } returns group
            every { restrictionService.testItem(item, config) } returns (false to null)

            val result = api.testItem(item, "survival")

            assertFalse(result.first)
            Assertions.assertNull(result.second)
        }

        @Test
        @DisplayName("returns false when group not found")
        fun returnsFalseWhenGroupNotFound() {
            val item = mockk<ItemStack>()
            every { groupService.getGroup("nonexistent") } returns null

            val result = api.testItem(item, "nonexistent")

            assertFalse(result.first)
            Assertions.assertNull(result.second)
        }
    }

    @Nested
    @DisplayName("testStripOnExit")
    inner class TestStripOnExit {

        @Test
        @DisplayName("returns pattern when item should be stripped")
        fun returnsPatternWhenItemShouldBeStripped() {
            val item = mockk<ItemStack>()
            val config = RestrictionConfig(
                mode = RestrictionMode.NONE,
                stripOnExit = listOf("ELYTRA")
            )
            val group = mockk<Group>()
            every { group.restrictions } returns config
            every { groupService.getGroup("hub") } returns group
            every { restrictionService.shouldStripOnExit(item, config) } returns "ELYTRA"

            val result = api.testStripOnExit(item, "hub")

            assertEquals("ELYTRA", result)
        }

        @Test
        @DisplayName("returns null when item should not be stripped")
        fun returnsNullWhenItemShouldNotBeStripped() {
            val item = mockk<ItemStack>()
            val config = RestrictionConfig(mode = RestrictionMode.NONE, stripOnExit = listOf("ELYTRA"))
            val group = mockk<Group>()
            every { group.restrictions } returns config
            every { groupService.getGroup("survival") } returns group
            every { restrictionService.shouldStripOnExit(item, config) } returns null

            val result = api.testStripOnExit(item, "survival")

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("checkPlayerInventory")
    inner class CheckPlayerInventory {

        @Test
        @DisplayName("returns violations map")
        fun returnsViolationsMap() {
            val player = mockk<Player>()
            val item1 = mockk<ItemStack>()
            val item2 = mockk<ItemStack>()
            val config = RestrictionConfig(mode = RestrictionMode.BLACKLIST, blacklist = listOf("DIAMOND_SWORD"))
            val group = mockk<Group>()
            every { group.restrictions } returns config
            every { groupService.getGroup("pvp") } returns group

            val violations = mapOf(
                0 to (item1 to "DIAMOND_SWORD"),
                8 to (item2 to "DIAMOND_SWORD")
            )
            every { restrictionService.checkPlayerInventory(player, config, RestrictionAction.ENTERING) } returns violations

            val result = api.checkPlayerInventory(player, "pvp")

            assertEquals(2, result.size)
            assertTrue(result.containsKey(0))
            assertTrue(result.containsKey(8))
        }

        @Test
        @DisplayName("returns empty map when no violations")
        fun returnsEmptyMapWhenNoViolations() {
            val player = mockk<Player>()
            val config = RestrictionConfig(mode = RestrictionMode.BLACKLIST, blacklist = listOf("DIAMOND_SWORD"))
            val group = mockk<Group>()
            every { group.restrictions } returns config
            every { groupService.getGroup("survival") } returns group
            every { restrictionService.checkPlayerInventory(player, config, RestrictionAction.ENTERING) } returns emptyMap()

            val result = api.checkPlayerInventory(player, "survival")

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("returns empty map when group not found")
        fun returnsEmptyMapWhenGroupNotFound() {
            val player = mockk<Player>()
            every { groupService.getGroup("nonexistent") } returns null

            val result = api.checkPlayerInventory(player, "nonexistent")

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("parsePattern")
    inner class ParsePattern {

        @Test
        @DisplayName("parses pattern via service")
        fun parsesPatternViaService() {
            val pattern = mockk<ItemPattern>()
            every { restrictionService.parsePattern("DIAMOND_*") } returns pattern

            val result = api.parsePattern("DIAMOND_*")

            assertEquals(pattern, result)
            verify { restrictionService.parsePattern("DIAMOND_*") }
        }
    }

    @Nested
    @DisplayName("validatePattern")
    inner class ValidatePattern {

        @Test
        @DisplayName("returns success for valid pattern")
        fun returnsSuccessForValidPattern() {
            val pattern = mockk<ItemPattern>()
            every { restrictionService.validatePattern("DIAMOND_SWORD") } returns Result.success(pattern)

            val result = api.validatePattern("DIAMOND_SWORD")

            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("returns failure for invalid pattern")
        fun returnsFailureForInvalidPattern() {
            every { restrictionService.validatePattern("[invalid") } returns
                Result.failure(IllegalArgumentException("Invalid pattern"))

            val result = api.validatePattern("[invalid")

            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("getAllPatterns")
    inner class GetAllPatterns {

        @Test
        @DisplayName("returns all patterns for group")
        fun returnsAllPatternsForGroup() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND_SWORD", "NETHERITE_SWORD"),
                stripOnExit = listOf("ELYTRA")
            )
            val group = mockk<Group>()
            every { group.restrictions } returns config
            every { groupService.getGroup("pvp") } returns group

            val patterns = listOf(
                "DIAMOND_SWORD" to true,
                "NETHERITE_SWORD" to true,
                "STRIP:ELYTRA" to true
            )
            every { restrictionService.getAllPatterns(config) } returns patterns

            val result = api.getAllPatterns("pvp")

            assertEquals(3, result.size)
        }

        @Test
        @DisplayName("returns empty list when group not found")
        fun returnsEmptyListWhenGroupNotFound() {
            every { groupService.getGroup("nonexistent") } returns null

            val result = api.getAllPatterns("nonexistent")

            assertTrue(result.isEmpty())
        }

        @Test
        @DisplayName("returns empty list when no restrictions")
        fun returnsEmptyListWhenNoRestrictions() {
            val group = mockk<Group>()
            every { group.restrictions } returns null
            every { groupService.getGroup("survival") } returns group

            val result = api.getAllPatterns("survival")

            assertTrue(result.isEmpty())
        }
    }
}

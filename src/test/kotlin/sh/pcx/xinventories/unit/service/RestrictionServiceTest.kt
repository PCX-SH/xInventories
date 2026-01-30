package sh.pcx.xinventories.unit.service

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.plugin.PluginManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.event.ItemRestrictionEvent
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.model.*
import sh.pcx.xinventories.internal.service.MessageService
import sh.pcx.xinventories.internal.service.RestrictionService
import sh.pcx.xinventories.internal.storage.ConfiscationStorage
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for RestrictionService.
 *
 * Tests cover:
 * - Pattern parsing and matching
 * - checkItem with BLACKLIST, WHITELIST, and NONE modes
 * - shouldStripOnExit
 * - checkPlayerInventory for all slot types
 * - handleViolations with all action types
 * - testItem
 * - getAllPatterns
 * - validatePattern
 * - Confiscation vault operations
 */
@DisplayName("RestrictionService Unit Tests")
class RestrictionServiceTest {

    private lateinit var server: ServerMock
    private lateinit var plugin: XInventories
    private lateinit var messageService: MessageService
    private lateinit var restrictionService: RestrictionService
    private lateinit var confiscationStorage: ConfiscationStorage
    private lateinit var scope: CoroutineScope

    private val playerUUID = UUID.randomUUID()

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("RestrictionServiceTest"), false)
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs
            every { Logging.notifyAdmins(any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        plugin = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        confiscationStorage = mockk(relaxed = true)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        restrictionService = RestrictionService(plugin, scope, messageService)
        // Note: We don't call initialize() since it would try to create actual storage
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createItem(material: Material, amount: Int = 1): ItemStack {
        return ItemStack(material, amount)
    }

    private fun createGroup(name: String = "test_group"): InventoryGroup {
        return InventoryGroup(
            name = name,
            worlds = setOf("world"),
            patterns = listOf("world"),
            priority = 0,
            parent = null,
            settings = GroupSettings.DEFAULT,
            isDefault = false
        )
    }

    private fun createMockPlayer(
        uuid: UUID = playerUUID,
        mainItems: Map<Int, ItemStack?> = emptyMap(),
        armorItems: Array<ItemStack?> = arrayOfNulls(4),
        offhandItem: ItemStack? = null
    ): Player {
        val player = mockk<Player>(relaxed = true)
        val inventory = mockk<PlayerInventory>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val location = mockk<Location>(relaxed = true)

        every { player.uniqueId } returns uuid
        every { player.name } returns "TestPlayer"
        every { player.inventory } returns inventory
        every { player.world } returns world
        every { player.location } returns location
        every { world.name } returns "world"

        // Set up main inventory slots (0-35)
        for (i in 0..35) {
            val item = mainItems[i]
            every { inventory.getItem(i) } returns item
        }

        // Set up armor contents (slots 36-39)
        every { inventory.armorContents } returns armorItems

        // Set up offhand (slot 40)
        every { inventory.itemInOffHand } returns (offhandItem ?: ItemStack(Material.AIR))

        return player
    }

    // =========================================================================
    // Pattern Parsing Tests
    // =========================================================================

    @Nested
    @DisplayName("Pattern Parsing")
    inner class PatternParsingTests {

        @Test
        @DisplayName("Should parse exact material pattern")
        fun parseExactMaterial() {
            val pattern = restrictionService.parsePattern("DIAMOND")

            assertTrue(pattern is ItemPattern.ExactMaterial)
            assertEquals("DIAMOND", pattern.patternString)
        }

        @Test
        @DisplayName("Should parse material wildcard pattern")
        fun parseMaterialWildcard() {
            val pattern = restrictionService.parsePattern("DIAMOND_SWORD:*")

            assertTrue(pattern is ItemPattern.MaterialWildcard)
        }

        @Test
        @DisplayName("Should parse spawn egg wildcard pattern")
        fun parseSpawnEggWildcard() {
            val pattern = restrictionService.parsePattern("SPAWN_EGG:*")

            assertTrue(pattern is ItemPattern.SpawnEggWildcard)
        }

        @Test
        @DisplayName("Should return Invalid for unknown material")
        fun parseInvalidMaterial() {
            val pattern = restrictionService.parsePattern("UNKNOWN_MATERIAL")

            assertTrue(pattern is ItemPattern.Invalid)
        }

        @Test
        @DisplayName("Should cache parsed patterns")
        fun cachePatterns() {
            val pattern1 = restrictionService.parsePattern("DIAMOND")
            val pattern2 = restrictionService.parsePattern("DIAMOND")

            assertSame(pattern1, pattern2, "Patterns should be same cached instance")
        }
    }

    // =========================================================================
    // Pattern Matching Tests
    // =========================================================================

    @Nested
    @DisplayName("Pattern Matching")
    inner class PatternMatchingTests {

        @Test
        @DisplayName("Should match item against patterns")
        fun matchItemAgainstPatterns() {
            val diamond = createItem(Material.DIAMOND)
            val patterns = listOf("DIAMOND", "EMERALD")

            val result = restrictionService.matchesAnyPattern(diamond, patterns)

            assertEquals("DIAMOND", result)
        }

        @Test
        @DisplayName("Should return null when no pattern matches")
        fun noPatternMatches() {
            val dirt = createItem(Material.DIRT)
            val patterns = listOf("DIAMOND", "EMERALD")

            val result = restrictionService.matchesAnyPattern(dirt, patterns)

            assertTrue(result == null, "Result should be null")
        }

        @Test
        @DisplayName("Should match spawn egg wildcard")
        fun matchSpawnEggWildcard() {
            val zombieEgg = createItem(Material.ZOMBIE_SPAWN_EGG)
            val patterns = listOf("SPAWN_EGG:*")

            val result = restrictionService.matchesAnyPattern(zombieEgg, patterns)

            assertEquals("SPAWN_EGG:*", result)
        }

        @Test
        @DisplayName("Should return first matching pattern")
        fun returnFirstMatchingPattern() {
            val diamond = createItem(Material.DIAMOND)
            val patterns = listOf("EMERALD", "DIAMOND", "DIAMOND")

            val result = restrictionService.matchesAnyPattern(diamond, patterns)

            assertEquals("DIAMOND", result)
        }
    }

    // =========================================================================
    // checkItem Tests - BLACKLIST Mode
    // =========================================================================

    @Nested
    @DisplayName("checkItem - BLACKLIST Mode")
    inner class CheckItemBlacklistTests {

        @Test
        @DisplayName("Should return pattern when item matches blacklist")
        fun itemMatchesBlacklist() {
            val diamond = createItem(Material.DIAMOND)
            val config = RestrictionConfig.blacklist("DIAMOND", "EMERALD")

            val result = restrictionService.checkItem(diamond, config)

            assertEquals("DIAMOND", result)
        }

        @Test
        @DisplayName("Should return null when item not in blacklist")
        fun itemNotInBlacklist() {
            val dirt = createItem(Material.DIRT)
            val config = RestrictionConfig.blacklist("DIAMOND", "EMERALD")

            val result = restrictionService.checkItem(dirt, config)

            assertTrue(result == null, "Result should be null")
        }

        @Test
        @DisplayName("Should match wildcard patterns in blacklist")
        fun wildcardInBlacklist() {
            val zombieEgg = createItem(Material.ZOMBIE_SPAWN_EGG)
            val config = RestrictionConfig.blacklist("SPAWN_EGG:*")

            val result = restrictionService.checkItem(zombieEgg, config)

            assertEquals("SPAWN_EGG:*", result)
        }
    }

    // =========================================================================
    // checkItem Tests - WHITELIST Mode
    // =========================================================================

    @Nested
    @DisplayName("checkItem - WHITELIST Mode")
    inner class CheckItemWhitelistTests {

        @Test
        @DisplayName("Should return null when item matches whitelist")
        fun itemMatchesWhitelist() {
            val stone = createItem(Material.STONE)
            val config = RestrictionConfig.whitelist("STONE", "DIRT")

            val result = restrictionService.checkItem(stone, config)

            assertTrue(result == null, "Result should be null")
        }

        @Test
        @DisplayName("Should return pattern when item not in whitelist")
        fun itemNotInWhitelist() {
            val diamond = createItem(Material.DIAMOND)
            val config = RestrictionConfig.whitelist("STONE", "DIRT")

            val result = restrictionService.checkItem(diamond, config)

            assertTrue(result != null, "Result should not be null")
            assertTrue(result!!.startsWith("WHITELIST:"))
            assertTrue(result.contains("DIAMOND"))
        }

        @Test
        @DisplayName("Should allow wildcard patterns in whitelist")
        fun wildcardInWhitelist() {
            val zombieEgg = createItem(Material.ZOMBIE_SPAWN_EGG)
            val config = RestrictionConfig.whitelist("SPAWN_EGG:*")

            val result = restrictionService.checkItem(zombieEgg, config)

            assertTrue(result == null, "Spawn egg should be allowed by whitelist wildcard")
        }
    }

    // =========================================================================
    // checkItem Tests - NONE Mode
    // =========================================================================

    @Nested
    @DisplayName("checkItem - NONE Mode")
    inner class CheckItemNoneModeTests {

        @Test
        @DisplayName("Should return null for any item when mode is NONE")
        fun noneModeAllowsAll() {
            val diamond = createItem(Material.DIAMOND)
            val config = RestrictionConfig.disabled()

            val result = restrictionService.checkItem(diamond, config)

            assertTrue(result == null, "Result should be null")
        }

        @Test
        @DisplayName("Should return null even with blacklist patterns when mode is NONE")
        fun noneModeIgnoresBlacklist() {
            val diamond = createItem(Material.DIAMOND)
            val config = RestrictionConfig(
                mode = RestrictionMode.NONE,
                blacklist = listOf("DIAMOND")
            )

            val result = restrictionService.checkItem(diamond, config)

            assertTrue(result == null, "Result should be null")
        }
    }

    // =========================================================================
    // shouldStripOnExit Tests
    // =========================================================================

    @Nested
    @DisplayName("shouldStripOnExit")
    inner class ShouldStripOnExitTests {

        @Test
        @DisplayName("Should return pattern when item matches strip on exit")
        fun matchesStripOnExit() {
            val diamond = createItem(Material.DIAMOND)
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                stripOnExit = listOf("DIAMOND")
            )

            val result = restrictionService.shouldStripOnExit(diamond, config)

            assertEquals("DIAMOND", result)
        }

        @Test
        @DisplayName("Should return null when item not in strip on exit")
        fun notInStripOnExit() {
            val dirt = createItem(Material.DIRT)
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                stripOnExit = listOf("DIAMOND")
            )

            val result = restrictionService.shouldStripOnExit(dirt, config)

            assertTrue(result == null, "Result should be null")
        }

        @Test
        @DisplayName("Should return null when strip on exit is empty")
        fun emptyStripOnExit() {
            val diamond = createItem(Material.DIAMOND)
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                stripOnExit = emptyList()
            )

            val result = restrictionService.shouldStripOnExit(diamond, config)

            assertTrue(result == null, "Result should be null")
        }

        @Test
        @DisplayName("Should work independently of restriction mode")
        fun independentOfMode() {
            val diamond = createItem(Material.DIAMOND)
            val config = RestrictionConfig(
                mode = RestrictionMode.NONE,
                stripOnExit = listOf("DIAMOND")
            )

            val result = restrictionService.shouldStripOnExit(diamond, config)

            assertEquals("DIAMOND", result)
        }
    }

    // =========================================================================
    // checkPlayerInventory Tests
    // =========================================================================

    @Nested
    @DisplayName("checkPlayerInventory")
    inner class CheckPlayerInventoryTests {

        @Test
        @DisplayName("Should check main inventory slots")
        fun checkMainInventory() {
            val mainItems = mapOf(
                0 to createItem(Material.DIAMOND),
                5 to createItem(Material.DIRT),
                10 to createItem(Material.EMERALD)
            )
            val player = createMockPlayer(mainItems = mainItems)
            val config = RestrictionConfig.blacklist("DIAMOND", "EMERALD")

            val violations = restrictionService.checkPlayerInventory(
                player, config, RestrictionAction.ENTERING
            )

            assertEquals(2, violations.size)
            assertTrue(violations.containsKey(0))
            assertTrue(violations.containsKey(10))
            assertFalse(violations.containsKey(5))
        }

        @Test
        @DisplayName("Should check armor slots")
        fun checkArmorSlots() {
            val armorItems: Array<ItemStack?> = arrayOf(
                createItem(Material.DIAMOND_BOOTS),
                createItem(Material.IRON_LEGGINGS),
                createItem(Material.DIAMOND_CHESTPLATE),
                createItem(Material.IRON_HELMET)
            )
            val player = createMockPlayer(armorItems = armorItems)
            val config = RestrictionConfig.blacklist("DIAMOND_BOOTS", "DIAMOND_CHESTPLATE")

            val violations = restrictionService.checkPlayerInventory(
                player, config, RestrictionAction.ENTERING
            )

            assertEquals(2, violations.size)
            assertTrue(violations.containsKey(36), "Should have violation at slot 36 (boots)")
            assertTrue(violations.containsKey(38), "Should have violation at slot 38 (chestplate)")
        }

        @Test
        @DisplayName("Should check offhand slot")
        fun checkOffhandSlot() {
            val offhand = createItem(Material.DIAMOND)
            val player = createMockPlayer(offhandItem = offhand)
            val config = RestrictionConfig.blacklist("DIAMOND")

            val violations = restrictionService.checkPlayerInventory(
                player, config, RestrictionAction.ENTERING
            )

            assertEquals(1, violations.size)
            assertTrue(violations.containsKey(40), "Should have violation at slot 40 (offhand)")
        }

        @Test
        @DisplayName("Should return empty map when no violations")
        fun noViolations() {
            val mainItems = mapOf(0 to createItem(Material.DIRT))
            val player = createMockPlayer(mainItems = mainItems)
            val config = RestrictionConfig.blacklist("DIAMOND")

            val violations = restrictionService.checkPlayerInventory(
                player, config, RestrictionAction.ENTERING
            )

            assertTrue(violations.isEmpty())
        }

        @Test
        @DisplayName("Should check stripOnExit when exiting")
        fun checkStripOnExitWhenExiting() {
            val mainItems = mapOf(0 to createItem(Material.DIAMOND))
            val player = createMockPlayer(mainItems = mainItems)
            val config = RestrictionConfig(
                mode = RestrictionMode.NONE,
                stripOnExit = listOf("DIAMOND")
            )

            val violations = restrictionService.checkPlayerInventory(
                player, config, RestrictionAction.EXITING
            )

            assertEquals(1, violations.size)
            assertTrue(violations.containsKey(0))
        }

        @Test
        @DisplayName("Should skip air items")
        fun skipAirItems() {
            val mainItems = mapOf(
                0 to createItem(Material.AIR),
                1 to createItem(Material.DIAMOND)
            )
            val player = createMockPlayer(mainItems = mainItems)
            val config = RestrictionConfig.blacklist("DIAMOND", "AIR")

            val violations = restrictionService.checkPlayerInventory(
                player, config, RestrictionAction.ENTERING
            )

            assertEquals(1, violations.size)
            assertTrue(violations.containsKey(1))
            assertFalse(violations.containsKey(0), "Should not report AIR as violation")
        }
    }

    // =========================================================================
    // handleViolations Tests
    // =========================================================================

    @Nested
    @DisplayName("handleViolations")
    inner class HandleViolationsTests {

        @Test
        @DisplayName("Should return true when no violations")
        fun noViolationsReturnsTrue() {
            val player = createMockPlayer()
            val group = createGroup()
            val config = RestrictionConfig.blacklist("DIAMOND")

            val result = restrictionService.handleViolations(
                player, group, config, emptyMap(), RestrictionAction.ENTERING
            )

            assertTrue(result)
        }

        @Test
        @DisplayName("Should return false when PREVENT action and violations exist")
        fun preventActionBlocksEntry() {
            val player = createMockPlayer()
            val group = createGroup()
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND"),
                onViolation = RestrictionViolationAction.PREVENT
            )

            val violations = mapOf(0 to (createItem(Material.DIAMOND) to "DIAMOND"))

            mockkStatic(Bukkit::class)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { Bukkit.getPluginManager() } returns pluginManager

            // Don't cancel the event, so PREVENT result stands
            every { pluginManager.callEvent(any<ItemRestrictionEvent>()) } just Runs

            val result = restrictionService.handleViolations(
                player, group, config, violations, RestrictionAction.ENTERING
            )

            assertFalse(result, "Should block entry when PREVENT action")

            unmockkStatic(Bukkit::class)
        }

        @Test
        @DisplayName("Should remove items when REMOVE action")
        fun removeActionClearsItems() {
            val mainItems = mutableMapOf(0 to createItem(Material.DIAMOND))
            val inventory = mockk<PlayerInventory>(relaxed = true)
            val player = mockk<Player>(relaxed = true)
            val world = mockk<World>(relaxed = true)

            every { player.inventory } returns inventory
            every { player.world } returns world
            every { player.uniqueId } returns playerUUID
            every { player.name } returns "TestPlayer"
            every { player.location } returns mockk(relaxed = true)
            every { inventory.getItem(0) } returns mainItems[0]

            val group = createGroup()
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND"),
                onViolation = RestrictionViolationAction.REMOVE,
                notifyPlayer = false,
                notifyAdmins = false
            )

            val violations = mapOf(0 to (createItem(Material.DIAMOND) to "DIAMOND"))

            mockkStatic(Bukkit::class)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { Bukkit.getPluginManager() } returns pluginManager
            every { pluginManager.callEvent(any<ItemRestrictionEvent>()) } just Runs

            restrictionService.handleViolations(
                player, group, config, violations, RestrictionAction.ENTERING
            )

            verify { inventory.setItem(0, null) }

            unmockkStatic(Bukkit::class)
        }

        @Test
        @DisplayName("Should drop items when DROP action")
        fun dropActionDropsItems() {
            val item = createItem(Material.DIAMOND)
            val inventory = mockk<PlayerInventory>(relaxed = true)
            val player = mockk<Player>(relaxed = true)
            val world = mockk<World>(relaxed = true)
            val location = mockk<Location>(relaxed = true)
            val droppedItem = mockk<org.bukkit.entity.Item>(relaxed = true)

            every { player.inventory } returns inventory
            every { player.world } returns world
            every { player.uniqueId } returns playerUUID
            every { player.name } returns "TestPlayer"
            every { player.location } returns location
            every { inventory.getItem(0) } returns item
            every { world.dropItemNaturally(any(), any()) } returns droppedItem

            val group = createGroup()
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND"),
                onViolation = RestrictionViolationAction.DROP,
                notifyPlayer = false,
                notifyAdmins = false
            )

            val violations = mapOf(0 to (item to "DIAMOND"))

            mockkStatic(Bukkit::class)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { Bukkit.getPluginManager() } returns pluginManager
            every { pluginManager.callEvent(any<ItemRestrictionEvent>()) } just Runs

            restrictionService.handleViolations(
                player, group, config, violations, RestrictionAction.ENTERING
            )

            verify { world.dropItemNaturally(any(), any()) }
            verify { inventory.setItem(0, null) }

            unmockkStatic(Bukkit::class)
        }

        @Test
        @DisplayName("Should notify player when notifyPlayer is true")
        fun notifyPlayer() {
            val player = createMockPlayer()
            val group = createGroup()
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND"),
                onViolation = RestrictionViolationAction.REMOVE,
                notifyPlayer = true,
                notifyAdmins = false
            )

            val violations = mapOf(0 to (createItem(Material.DIAMOND) to "DIAMOND"))

            mockkStatic(Bukkit::class)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { Bukkit.getPluginManager() } returns pluginManager
            every { pluginManager.callEvent(any<ItemRestrictionEvent>()) } just Runs

            restrictionService.handleViolations(
                player, group, config, violations, RestrictionAction.ENTERING
            )

            verify { messageService.send(player, any(), *anyVararg()) }

            unmockkStatic(Bukkit::class)
        }

        @Test
        @DisplayName("Should notify admins when notifyAdmins is true")
        fun notifyAdmins() {
            val player = createMockPlayer()
            val group = createGroup()
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND"),
                onViolation = RestrictionViolationAction.REMOVE,
                notifyPlayer = false,
                notifyAdmins = true
            )

            val violations = mapOf(0 to (createItem(Material.DIAMOND) to "DIAMOND"))

            mockkStatic(Bukkit::class)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { Bukkit.getPluginManager() } returns pluginManager
            every { pluginManager.callEvent(any<ItemRestrictionEvent>()) } just Runs

            restrictionService.handleViolations(
                player, group, config, violations, RestrictionAction.ENTERING
            )

            verify { Logging.notifyAdmins(any()) }

            unmockkStatic(Bukkit::class)
        }

        @Test
        @DisplayName("Should handle armor slot removal")
        fun handleArmorSlotRemoval() {
            val armorItem = createItem(Material.DIAMOND_CHESTPLATE)
            val armorContents = arrayOf<ItemStack?>(null, null, armorItem, null)
            val inventory = mockk<PlayerInventory>(relaxed = true)
            val player = mockk<Player>(relaxed = true)
            val world = mockk<World>(relaxed = true)

            every { player.inventory } returns inventory
            every { player.world } returns world
            every { player.uniqueId } returns playerUUID
            every { player.name } returns "TestPlayer"
            every { player.location } returns mockk(relaxed = true)
            every { inventory.armorContents } returns armorContents

            val group = createGroup()
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND_CHESTPLATE"),
                onViolation = RestrictionViolationAction.REMOVE,
                notifyPlayer = false,
                notifyAdmins = false
            )

            // Slot 38 is chestplate (36 + 2)
            val violations = mapOf(38 to (armorItem to "DIAMOND_CHESTPLATE"))

            mockkStatic(Bukkit::class)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { Bukkit.getPluginManager() } returns pluginManager
            every { pluginManager.callEvent(any<ItemRestrictionEvent>()) } just Runs

            restrictionService.handleViolations(
                player, group, config, violations, RestrictionAction.ENTERING
            )

            verify { inventory.armorContents = any() }

            unmockkStatic(Bukkit::class)
        }

        @Test
        @DisplayName("Should handle offhand slot removal")
        fun handleOffhandSlotRemoval() {
            val offhandItem = createItem(Material.DIAMOND)
            val inventory = mockk<PlayerInventory>(relaxed = true)
            val player = mockk<Player>(relaxed = true)
            val world = mockk<World>(relaxed = true)

            every { player.inventory } returns inventory
            every { player.world } returns world
            every { player.uniqueId } returns playerUUID
            every { player.name } returns "TestPlayer"
            every { player.location } returns mockk(relaxed = true)
            every { inventory.itemInOffHand } returns offhandItem

            val group = createGroup()
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND"),
                onViolation = RestrictionViolationAction.REMOVE,
                notifyPlayer = false,
                notifyAdmins = false
            )

            val violations = mapOf(40 to (offhandItem to "DIAMOND"))

            mockkStatic(Bukkit::class)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { Bukkit.getPluginManager() } returns pluginManager
            every { pluginManager.callEvent(any<ItemRestrictionEvent>()) } just Runs

            restrictionService.handleViolations(
                player, group, config, violations, RestrictionAction.ENTERING
            )

            verify { inventory.setItemInOffHand(null) }

            unmockkStatic(Bukkit::class)
        }
    }

    // =========================================================================
    // testItem Tests
    // =========================================================================

    @Nested
    @DisplayName("testItem")
    inner class TestItemTests {

        @Test
        @DisplayName("Should return restricted=true with pattern when item is restricted")
        fun itemIsRestricted() {
            val diamond = createItem(Material.DIAMOND)
            val config = RestrictionConfig.blacklist("DIAMOND")

            val (restricted, pattern) = restrictionService.testItem(diamond, config)

            assertTrue(restricted)
            assertEquals("DIAMOND", pattern)
        }

        @Test
        @DisplayName("Should return restricted=false with null pattern when item is allowed")
        fun itemIsAllowed() {
            val dirt = createItem(Material.DIRT)
            val config = RestrictionConfig.blacklist("DIAMOND")

            val (restricted, pattern) = restrictionService.testItem(dirt, config)

            assertFalse(restricted)
            assertTrue(pattern == null, "Pattern should be null")
        }

        @Test
        @DisplayName("Should work with whitelist mode")
        fun whitelistMode() {
            val diamond = createItem(Material.DIAMOND)
            val config = RestrictionConfig.whitelist("STONE", "DIRT")

            val (restricted, pattern) = restrictionService.testItem(diamond, config)

            assertTrue(restricted)
            assertTrue(pattern != null, "Pattern should not be null")
        }
    }

    // =========================================================================
    // getAllPatterns Tests
    // =========================================================================

    @Nested
    @DisplayName("getAllPatterns")
    inner class GetAllPatternsTests {

        @Test
        @DisplayName("Should return blacklist patterns in BLACKLIST mode")
        fun blacklistModePatterns() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND", "EMERALD"),
                whitelist = listOf("STONE")
            )

            val patterns = restrictionService.getAllPatterns(config)

            assertEquals(2, patterns.size)
            assertTrue(patterns.any { it.first == "DIAMOND" && it.second })
            assertTrue(patterns.any { it.first == "EMERALD" && it.second })
        }

        @Test
        @DisplayName("Should return whitelist patterns in WHITELIST mode")
        fun whitelistModePatterns() {
            val config = RestrictionConfig(
                mode = RestrictionMode.WHITELIST,
                blacklist = listOf("DIAMOND"),
                whitelist = listOf("STONE", "DIRT")
            )

            val patterns = restrictionService.getAllPatterns(config)

            assertEquals(2, patterns.size)
            assertTrue(patterns.any { it.first == "STONE" && !it.second })
            assertTrue(patterns.any { it.first == "DIRT" && !it.second })
        }

        @Test
        @DisplayName("Should return empty list in NONE mode")
        fun noneModePatterns() {
            val config = RestrictionConfig(
                mode = RestrictionMode.NONE,
                blacklist = listOf("DIAMOND"),
                whitelist = listOf("STONE")
            )

            val patterns = restrictionService.getAllPatterns(config)

            assertTrue(patterns.none { !it.first.startsWith("STRIP:") })
        }

        @Test
        @DisplayName("Should include stripOnExit patterns with STRIP prefix")
        fun includeStripOnExitPatterns() {
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND"),
                stripOnExit = listOf("EMERALD", "GOLD_INGOT")
            )

            val patterns = restrictionService.getAllPatterns(config)

            assertEquals(3, patterns.size)
            assertTrue(patterns.any { it.first == "STRIP:EMERALD" && it.second })
            assertTrue(patterns.any { it.first == "STRIP:GOLD_INGOT" && it.second })
        }
    }

    // =========================================================================
    // validatePattern Tests
    // =========================================================================

    @Nested
    @DisplayName("validatePattern")
    inner class ValidatePatternTests {

        @Test
        @DisplayName("Should return success for valid material pattern")
        fun validMaterialPattern() {
            val result = restrictionService.validatePattern("DIAMOND")

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull() is ItemPattern.ExactMaterial)
        }

        @Test
        @DisplayName("Should return success for valid wildcard pattern")
        fun validWildcardPattern() {
            val result = restrictionService.validatePattern("SPAWN_EGG:*")

            assertTrue(result.isSuccess)
            assertTrue(result.getOrNull() is ItemPattern.SpawnEggWildcard)
        }

        @Test
        @DisplayName("Should return failure for invalid pattern")
        fun invalidPattern() {
            val result = restrictionService.validatePattern("UNKNOWN_MATERIAL_XYZ")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
        }

        @Test
        @DisplayName("Should return failure for invalid enchantment pattern")
        fun invalidEnchantmentPattern() {
            val result = restrictionService.validatePattern("DIAMOND_SWORD:INVALID_ENCHANT:5")

            assertTrue(result.isFailure)
        }
    }

    // =========================================================================
    // Confiscation Vault Tests
    // =========================================================================

    @Nested
    @DisplayName("Confiscation Vault Operations")
    inner class ConfiscationVaultTests {

        private lateinit var serviceWithStorage: RestrictionService

        @BeforeEach
        fun setUpStorage() {
            // Create a service with mocked confiscation storage using reflection
            serviceWithStorage = RestrictionService(plugin, scope, messageService)

            // Use reflection to set the confiscationStorage field
            val field = RestrictionService::class.java.getDeclaredField("confiscationStorage")
            field.isAccessible = true
            field.set(serviceWithStorage, confiscationStorage)
        }

        @Test
        @DisplayName("Should get confiscated items for player")
        fun getConfiscatedItems() = runTest {
            val items = listOf(
                ConfiscatedItem(
                    id = 1L,
                    playerUuid = playerUUID,
                    itemData = "data",
                    itemType = "DIAMOND",
                    itemName = "Diamond",
                    amount = 1,
                    confiscatedAt = Instant.now(),
                    reason = "Restricted item",
                    groupName = "survival",
                    worldName = "world"
                )
            )
            coEvery { confiscationStorage.getItemsForPlayer(playerUUID, any()) } returns items

            val result = serviceWithStorage.getConfiscatedItems(playerUUID)

            assertEquals(1, result.size)
            assertEquals("DIAMOND", result[0].itemType)
        }

        @Test
        @DisplayName("Should get confiscated item by ID")
        fun getConfiscatedItemById() = runTest {
            val item = ConfiscatedItem(
                id = 1L,
                playerUuid = playerUUID,
                itemData = "data",
                itemType = "DIAMOND",
                itemName = "Diamond",
                amount = 1,
                confiscatedAt = Instant.now(),
                reason = "Restricted item",
                groupName = "survival",
                worldName = "world"
            )
            coEvery { confiscationStorage.getItemById(1L) } returns item

            val result = serviceWithStorage.getConfiscatedItem(1L)

            assertTrue(result != null, "Result should not be null")
            assertEquals(1L, result!!.id)
        }

        @Test
        @DisplayName("Should return null for non-existent confiscated item")
        fun getNonExistentConfiscatedItem() = runTest {
            coEvery { confiscationStorage.getItemById(999L) } returns null

            val result = serviceWithStorage.getConfiscatedItem(999L)

            assertTrue(result == null, "Result should be null")
        }

        @Test
        @DisplayName("Should get confiscated item count for player")
        fun getConfiscatedItemCount() = runTest {
            coEvery { confiscationStorage.getCountForPlayer(playerUUID) } returns 5

            val count = serviceWithStorage.getConfiscatedItemCount(playerUUID)

            assertEquals(5, count)
        }

        @Test
        @DisplayName("Should delete confiscated item")
        fun deleteConfiscatedItem() = runTest {
            coEvery { confiscationStorage.deleteItem(1L) } returns true

            val result = serviceWithStorage.deleteConfiscatedItem(1L)

            assertTrue(result)
            coVerify { confiscationStorage.deleteItem(1L) }
        }

        @Test
        @DisplayName("Should delete all confiscated items for player")
        fun deleteAllConfiscatedItems() = runTest {
            coEvery { confiscationStorage.deleteAllForPlayer(playerUUID) } returns 3

            val count = serviceWithStorage.deleteAllConfiscatedItems(playerUUID)

            assertEquals(3, count)
        }

        @Test
        @DisplayName("Should cleanup old confiscations")
        fun cleanupConfiscations() = runTest {
            coEvery { confiscationStorage.cleanup(30) } returns 10

            val count = serviceWithStorage.cleanupConfiscations(30)

            assertEquals(10, count)
        }

        @Test
        @DisplayName("Should claim confiscated item and return it to player")
        fun claimConfiscatedItem() = runTest {
            val player = mockk<Player>(relaxed = true)
            val inventory = mockk<PlayerInventory>(relaxed = true)
            val item = ConfiscatedItem(
                id = 1L,
                playerUuid = playerUUID,
                itemData = "data",
                itemType = "DIAMOND",
                itemName = "Diamond",
                amount = 1,
                confiscatedAt = Instant.now(),
                reason = "Restricted item",
                groupName = "survival",
                worldName = "world"
            )

            every { player.uniqueId } returns playerUUID
            every { player.name } returns "TestPlayer"
            every { player.inventory } returns inventory
            every { inventory.addItem(any()) } returns HashMap<Int, ItemStack>()
            coEvery { confiscationStorage.getItemById(1L) } returns item
            coEvery { confiscationStorage.deleteItem(1L) } returns true

            // Mock item deserialization
            mockkObject(sh.pcx.xinventories.internal.util.InventorySerializer)
            every { sh.pcx.xinventories.internal.util.InventorySerializer.deserializeItemStack(any()) } returns createItem(Material.DIAMOND)

            val result = serviceWithStorage.claimConfiscatedItem(player, 1L)

            assertTrue(result != null, "Result should not be null")
            assertEquals(1L, result!!.id)
            verify { inventory.addItem(any()) }
            coVerify { confiscationStorage.deleteItem(1L) }

            unmockkObject(sh.pcx.xinventories.internal.util.InventorySerializer)
        }

        @Test
        @DisplayName("Should return null when claiming item owned by different player")
        fun claimItemWrongOwner() = runTest {
            val otherPlayerUUID = UUID.randomUUID()
            val player = mockk<Player>(relaxed = true)
            val item = ConfiscatedItem(
                id = 1L,
                playerUuid = otherPlayerUUID,
                itemData = "data",
                itemType = "DIAMOND",
                itemName = "Diamond",
                amount = 1,
                confiscatedAt = Instant.now(),
                reason = "Restricted item",
                groupName = "survival",
                worldName = "world"
            )

            every { player.uniqueId } returns playerUUID
            every { player.name } returns "TestPlayer"
            coEvery { confiscationStorage.getItemById(1L) } returns item

            val result = serviceWithStorage.claimConfiscatedItem(player, 1L)

            assertTrue(result == null, "Should not claim item owned by different player")
        }

        @Test
        @DisplayName("Should claim all confiscated items")
        fun claimAllConfiscatedItems() = runTest {
            val player = mockk<Player>(relaxed = true)
            val inventory = mockk<PlayerInventory>(relaxed = true)
            val world = mockk<World>(relaxed = true)
            val location = mockk<Location>(relaxed = true)

            val items = listOf(
                ConfiscatedItem(
                    id = 1L,
                    playerUuid = playerUUID,
                    itemData = "data1",
                    itemType = "DIAMOND",
                    itemName = "Diamond",
                    amount = 1,
                    confiscatedAt = Instant.now(),
                    reason = "Restricted item",
                    groupName = "survival",
                    worldName = "world"
                ),
                ConfiscatedItem(
                    id = 2L,
                    playerUuid = playerUUID,
                    itemData = "data2",
                    itemType = "EMERALD",
                    itemName = "Emerald",
                    amount = 1,
                    confiscatedAt = Instant.now(),
                    reason = "Restricted item",
                    groupName = "survival",
                    worldName = "world"
                )
            )

            every { player.uniqueId } returns playerUUID
            every { player.name } returns "TestPlayer"
            every { player.inventory } returns inventory
            every { player.world } returns world
            every { player.location } returns location
            every { inventory.addItem(any()) } returns HashMap<Int, ItemStack>()
            coEvery { confiscationStorage.getItemsForPlayer(playerUUID) } returns items
            coEvery { confiscationStorage.deleteItem(any()) } returns true

            mockkObject(sh.pcx.xinventories.internal.util.InventorySerializer)
            every { sh.pcx.xinventories.internal.util.InventorySerializer.deserializeItemStack(any()) } returns createItem(Material.DIAMOND)

            val count = serviceWithStorage.claimAllConfiscatedItems(player)

            assertEquals(2, count)
            coVerify(exactly = 2) { confiscationStorage.deleteItem(any()) }

            unmockkObject(sh.pcx.xinventories.internal.util.InventorySerializer)
        }
    }

    // =========================================================================
    // Cache Management Tests
    // =========================================================================

    @Nested
    @DisplayName("Cache Management")
    inner class CacheManagementTests {

        @Test
        @DisplayName("Should clear pattern cache")
        fun clearCache() {
            // Parse some patterns to populate cache
            restrictionService.parsePattern("DIAMOND")
            restrictionService.parsePattern("EMERALD")

            // Clear cache
            restrictionService.clearCache()

            // Patterns should still work (just repopulate cache)
            val pattern = restrictionService.parsePattern("DIAMOND")
            assertTrue(pattern is ItemPattern.ExactMaterial)
        }
    }

    // =========================================================================
    // Event Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Event Integration")
    inner class EventIntegrationTests {

        @Test
        @DisplayName("Should fire ItemRestrictionEvent for each violation")
        fun firesEventForEachViolation() {
            val mainItems = mapOf(
                0 to createItem(Material.DIAMOND),
                1 to createItem(Material.EMERALD)
            )
            val player = createMockPlayer(mainItems = mainItems)
            val group = createGroup()
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND", "EMERALD"),
                onViolation = RestrictionViolationAction.REMOVE,
                notifyPlayer = false,
                notifyAdmins = false
            )

            val violations = mapOf(
                0 to (createItem(Material.DIAMOND) to "DIAMOND"),
                1 to (createItem(Material.EMERALD) to "EMERALD")
            )

            mockkStatic(Bukkit::class)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { Bukkit.getPluginManager() } returns pluginManager

            val firedEvents = mutableListOf<ItemRestrictionEvent>()
            every { pluginManager.callEvent(any<ItemRestrictionEvent>()) } answers {
                firedEvents.add(firstArg())
            }

            restrictionService.handleViolations(
                player, group, config, violations, RestrictionAction.ENTERING
            )

            assertEquals(2, firedEvents.size)

            unmockkStatic(Bukkit::class)
        }

        @Test
        @DisplayName("Should respect cancelled events")
        fun respectCancelledEvents() {
            val item = createItem(Material.DIAMOND)
            val inventory = mockk<PlayerInventory>(relaxed = true)
            val player = mockk<Player>(relaxed = true)
            val world = mockk<World>(relaxed = true)

            every { player.inventory } returns inventory
            every { player.world } returns world
            every { player.uniqueId } returns playerUUID
            every { player.name } returns "TestPlayer"
            every { player.location } returns mockk(relaxed = true)
            every { inventory.getItem(0) } returns item

            val group = createGroup()
            val config = RestrictionConfig(
                mode = RestrictionMode.BLACKLIST,
                blacklist = listOf("DIAMOND"),
                onViolation = RestrictionViolationAction.REMOVE,
                notifyPlayer = false,
                notifyAdmins = false
            )

            val violations = mapOf(0 to (item to "DIAMOND"))

            mockkStatic(Bukkit::class)
            val pluginManager = mockk<PluginManager>(relaxed = true)
            every { Bukkit.getPluginManager() } returns pluginManager

            // Cancel the event
            every { pluginManager.callEvent(any<ItemRestrictionEvent>()) } answers {
                val event = firstArg<ItemRestrictionEvent>()
                event.isCancelled = true
            }

            restrictionService.handleViolations(
                player, group, config, violations, RestrictionAction.ENTERING
            )

            // Item should NOT be removed because event was cancelled
            verify(exactly = 0) { inventory.setItem(0, null) }

            unmockkStatic(Bukkit::class)
        }
    }
}

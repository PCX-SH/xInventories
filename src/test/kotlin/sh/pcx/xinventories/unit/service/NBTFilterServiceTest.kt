package sh.pcx.xinventories.unit.service

import com.google.common.collect.ArrayListMultimap
import com.google.common.collect.Multimap
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kyori.adventure.text.Component
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.inventory.meta.ItemMeta
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.internal.model.FilterAction
import sh.pcx.xinventories.internal.model.NBTFilter
import sh.pcx.xinventories.internal.model.NBTFilterConfig
import sh.pcx.xinventories.internal.model.NBTFilterResult
import sh.pcx.xinventories.internal.model.NBTFilterType
import sh.pcx.xinventories.internal.service.MessageService
import sh.pcx.xinventories.internal.service.NBTFilterService
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for NBTFilterService.
 *
 * Tests cover:
 * - checkItem() - checking items against NBT filters
 * - Filter types: ENCHANTMENT, CUSTOM_MODEL_DATA, DISPLAY_NAME, LORE, ATTRIBUTE
 * - Enchantment filtering with level bounds
 * - findEnchantment() - finding enchantments by name
 * - checkPlayerInventory() - checking all player inventory slots
 * - applyFilterActions() - applying filter actions (ALLOW, REMOVE, DROP)
 * - testItem() - testing if item would be filtered
 * - validateConfig() - validating NBT filter configuration
 */
@DisplayName("NBTFilterService Unit Tests")
class NBTFilterServiceTest {

    private lateinit var plugin: XInventories
    private lateinit var scope: CoroutineScope
    private lateinit var messageService: MessageService
    private lateinit var service: NBTFilterService

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("NBTFilterServiceTest"), false)
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        service = NBTFilterService(plugin, scope, messageService)
        service.initialize()
    }

    @AfterEach
    fun tearDown() {
        service.shutdown()
        clearAllMocks(answers = false)
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockItemStack(
        material: Material = Material.DIAMOND_SWORD,
        hasItemMeta: Boolean = true,
        configureItemMeta: (ItemMeta) -> Unit = {}
    ): ItemStack {
        val item = mockk<ItemStack>(relaxed = true)
        every { item.type } returns material

        if (hasItemMeta) {
            val meta = mockk<ItemMeta>(relaxed = true)
            configureItemMeta(meta)
            every { item.itemMeta } returns meta
        } else {
            every { item.itemMeta } returns null
        }

        return item
    }

    private fun createMockEnchantment(name: String, namespace: String = "minecraft"): Enchantment {
        val enchantment = mockk<Enchantment>(relaxed = true)
        val key = NamespacedKey(namespace, name.lowercase())
        every { enchantment.key } returns key
        return enchantment
    }

    private fun createMockPlayer(): Player {
        val player = mockk<Player>(relaxed = true)
        val inventory = mockk<PlayerInventory>(relaxed = true)
        val world = mockk<World>(relaxed = true)
        val location = mockk<Location>(relaxed = true)

        every { player.uniqueId } returns UUID.randomUUID()
        every { player.name } returns "TestPlayer"
        every { player.inventory } returns inventory
        every { player.world } returns world
        every { player.location } returns location

        return player
    }

    private fun createInventoryGroup(name: String = "test_group"): InventoryGroup {
        return InventoryGroup(
            name = name,
            worlds = setOf("world"),
            patterns = emptyList(),
            priority = 0,
            parent = null,
            settings = GroupSettings(),
            isDefault = false
        )
    }

    // =========================================================================
    // Disabled Config Tests
    // =========================================================================

    @Nested
    @DisplayName("Disabled Config")
    inner class DisabledConfigTests {

        @Test
        @DisplayName("should return NO_MATCH when config is disabled")
        fun shouldReturnNoMatchWhenDisabled() {
            val item = createMockItemStack()
            val config = NBTFilterConfig(enabled = false, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
            assertEquals(NBTFilterResult.NO_MATCH, result)
        }

        @Test
        @DisplayName("should return NO_MATCH when filters list is empty")
        fun shouldReturnNoMatchWhenFiltersEmpty() {
            val item = createMockItemStack()
            val config = NBTFilterConfig(enabled = true, filters = emptyList())

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
            assertEquals(NBTFilterResult.NO_MATCH, result)
        }
    }

    // =========================================================================
    // Item Without Meta Tests
    // =========================================================================

    @Nested
    @DisplayName("Item Without Meta")
    inner class ItemWithoutMetaTests {

        @Test
        @DisplayName("should return NO_MATCH when item has no meta")
        fun shouldReturnNoMatchWhenNoMeta() {
            val item = createMockItemStack(hasItemMeta = false)
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
            assertEquals(NBTFilterResult.NO_MATCH, result)
        }
    }

    // =========================================================================
    // Enchantment Filter Tests
    // =========================================================================

    @Nested
    @DisplayName("Enchantment Filter")
    inner class EnchantmentFilterTests {

        @Test
        @DisplayName("should match item with enchantment")
        fun shouldMatchItemWithEnchantment() {
            val sharpness = createMockEnchantment("sharpness")
            val item = createMockItemStack()
            every { item.enchantments } returns mapOf(sharpness to 5)

            mockkStatic(Enchantment::class)
            every { Enchantment.getByKey(any()) } returns sharpness

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.REMOVE, result.filter?.action)

            unmockkStatic(Enchantment::class)
        }

        @Test
        @DisplayName("should not match item without enchantment")
        fun shouldNotMatchItemWithoutEnchantment() {
            val item = createMockItemStack()
            every { item.enchantments } returns emptyMap()

            mockkStatic(Enchantment::class)
            every { Enchantment.getByKey(any()) } returns null
            @Suppress("DEPRECATION")
            every { Enchantment.getByName(any()) } returns null

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)

            unmockkStatic(Enchantment::class)
        }

        @Test
        @DisplayName("should match enchantment with level in min/max range")
        fun shouldMatchEnchantmentWithLevelInRange() {
            val sharpness = createMockEnchantment("sharpness")
            val item = createMockItemStack()
            every { item.enchantments } returns mapOf(sharpness to 3)

            mockkStatic(Enchantment::class)
            every { Enchantment.getByKey(any()) } returns sharpness

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE, minLevel = 2, maxLevel = 5)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)

            unmockkStatic(Enchantment::class)
        }

        @Test
        @DisplayName("should not match enchantment with level below minLevel")
        fun shouldNotMatchEnchantmentBelowMinLevel() {
            val sharpness = createMockEnchantment("sharpness")
            val item = createMockItemStack()
            every { item.enchantments } returns mapOf(sharpness to 1)

            mockkStatic(Enchantment::class)
            every { Enchantment.getByKey(any()) } returns sharpness

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE, minLevel = 3)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)

            unmockkStatic(Enchantment::class)
        }

        @Test
        @DisplayName("should not match enchantment with level above maxLevel")
        fun shouldNotMatchEnchantmentAboveMaxLevel() {
            val sharpness = createMockEnchantment("sharpness")
            val item = createMockItemStack()
            every { item.enchantments } returns mapOf(sharpness to 10)

            mockkStatic(Enchantment::class)
            every { Enchantment.getByKey(any()) } returns sharpness

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE, maxLevel = 5)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)

            unmockkStatic(Enchantment::class)
        }

        @Test
        @DisplayName("should match enchantment at exact minLevel")
        fun shouldMatchEnchantmentAtExactMinLevel() {
            val sharpness = createMockEnchantment("sharpness")
            val item = createMockItemStack()
            every { item.enchantments } returns mapOf(sharpness to 3)

            mockkStatic(Enchantment::class)
            every { Enchantment.getByKey(any()) } returns sharpness

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE, minLevel = 3)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)

            unmockkStatic(Enchantment::class)
        }

        @Test
        @DisplayName("should match enchantment at exact maxLevel")
        fun shouldMatchEnchantmentAtExactMaxLevel() {
            val sharpness = createMockEnchantment("sharpness")
            val item = createMockItemStack()
            every { item.enchantments } returns mapOf(sharpness to 5)

            mockkStatic(Enchantment::class)
            every { Enchantment.getByKey(any()) } returns sharpness

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE, maxLevel = 5)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)

            unmockkStatic(Enchantment::class)
        }

        @Test
        @DisplayName("should return NO_MATCH when filter has no enchantment name")
        fun shouldReturnNoMatchWhenNoEnchantmentName() {
            val item = createMockItemStack()
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.ENCHANTMENT, action = FilterAction.REMOVE, enchantment = null)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should find enchantment with namespace:key format")
        fun shouldFindEnchantmentWithNamespaceKey() {
            val sharpness = createMockEnchantment("sharpness")
            val item = createMockItemStack()
            every { item.enchantments } returns mapOf(sharpness to 5)

            mockkStatic(Enchantment::class)
            every { Enchantment.getByKey(NamespacedKey.fromString("minecraft:sharpness")) } returns sharpness

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("minecraft:sharpness", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)

            unmockkStatic(Enchantment::class)
        }

        @Test
        @DisplayName("should find enchantment with legacy name")
        fun shouldFindEnchantmentWithLegacyName() {
            val sharpness = createMockEnchantment("sharpness")
            val item = createMockItemStack()
            every { item.enchantments } returns mapOf(sharpness to 5)

            mockkStatic(Enchantment::class)
            every { Enchantment.getByKey(any()) } returns null
            @Suppress("DEPRECATION")
            every { Enchantment.getByName("SHARPNESS") } returns sharpness

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("SHARPNESS", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)

            unmockkStatic(Enchantment::class)
        }
    }

    // =========================================================================
    // Custom Model Data Filter Tests
    // =========================================================================

    @Nested
    @DisplayName("Custom Model Data Filter")
    inner class CustomModelDataFilterTests {

        @Test
        @DisplayName("should match item with matching custom model data")
        fun shouldMatchItemWithMatchingCustomModelData() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001, 1002, 1003), FilterAction.ALLOW)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.ALLOW, result.filter?.action)
        }

        @Test
        @DisplayName("should not match item with non-matching custom model data")
        fun shouldNotMatchItemWithNonMatchingCustomModelData() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 9999
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001, 1002, 1003), FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should not match item without custom model data")
        fun shouldNotMatchItemWithoutCustomModelData() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns false
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should return NO_MATCH when filter has no custom model data values")
        fun shouldReturnNoMatchWhenNoCustomModelDataValues() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.CUSTOM_MODEL_DATA, action = FilterAction.REMOVE, customModelData = null)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }
    }

    // =========================================================================
    // Display Name Filter Tests
    // =========================================================================

    @Nested
    @DisplayName("Display Name Filter")
    inner class DisplayNameFilterTests {

        @Test
        @DisplayName("should match item with matching display name pattern")
        fun shouldMatchItemWithMatchingDisplayNamePattern() {
            val item = createMockItemStack { meta ->
                every { meta.hasDisplayName() } returns true
                every { meta.displayName() } returns Component.text("Legendary Sword [BANNED]")
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.displayName(".*\\[BANNED\\].*", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.REMOVE, result.filter?.action)
        }

        @Test
        @DisplayName("should not match item with non-matching display name")
        fun shouldNotMatchItemWithNonMatchingDisplayName() {
            val item = createMockItemStack { meta ->
                every { meta.hasDisplayName() } returns true
                every { meta.displayName() } returns Component.text("Regular Sword")
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.displayName(".*\\[BANNED\\].*", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should not match item without display name")
        fun shouldNotMatchItemWithoutDisplayName() {
            val item = createMockItemStack { meta ->
                every { meta.hasDisplayName() } returns false
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.displayName(".*test.*", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should match display name case-insensitively")
        fun shouldMatchDisplayNameCaseInsensitively() {
            val item = createMockItemStack { meta ->
                every { meta.hasDisplayName() } returns true
                every { meta.displayName() } returns Component.text("ADMIN WEAPON")
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.displayName("admin", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
        }

        @Test
        @DisplayName("should return NO_MATCH when filter has no name pattern")
        fun shouldReturnNoMatchWhenNoNamePattern() {
            val item = createMockItemStack { meta ->
                every { meta.hasDisplayName() } returns true
                every { meta.displayName() } returns Component.text("Test")
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.DISPLAY_NAME, action = FilterAction.REMOVE, namePattern = null)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should return NO_MATCH when display name returns null")
        fun shouldReturnNoMatchWhenDisplayNameNull() {
            val item = createMockItemStack { meta ->
                every { meta.hasDisplayName() } returns true
                every { meta.displayName() } returns null
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.displayName(".*test.*", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }
    }

    // =========================================================================
    // Lore Filter Tests
    // =========================================================================

    @Nested
    @DisplayName("Lore Filter")
    inner class LoreFilterTests {

        @Test
        @DisplayName("should match item with matching lore pattern")
        fun shouldMatchItemWithMatchingLorePattern() {
            val item = createMockItemStack { meta ->
                every { meta.hasLore() } returns true
                every { meta.lore() } returns listOf(
                    Component.text("A powerful weapon"),
                    Component.text("Admin Only Item")
                )
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.lore(".*admin.*", FilterAction.DROP)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.DROP, result.filter?.action)
        }

        @Test
        @DisplayName("should not match item with non-matching lore")
        fun shouldNotMatchItemWithNonMatchingLore() {
            val item = createMockItemStack { meta ->
                every { meta.hasLore() } returns true
                every { meta.lore() } returns listOf(
                    Component.text("A regular sword"),
                    Component.text("Found in a dungeon")
                )
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.lore(".*admin.*", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should not match item without lore")
        fun shouldNotMatchItemWithoutLore() {
            val item = createMockItemStack { meta ->
                every { meta.hasLore() } returns false
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.lore(".*test.*", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should match lore case-insensitively")
        fun shouldMatchLoreCaseInsensitively() {
            val item = createMockItemStack { meta ->
                every { meta.hasLore() } returns true
                every { meta.lore() } returns listOf(Component.text("ADMIN ITEM"))
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.lore("admin", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
        }

        @Test
        @DisplayName("should return NO_MATCH when filter has no lore pattern")
        fun shouldReturnNoMatchWhenNoLorePattern() {
            val item = createMockItemStack { meta ->
                every { meta.hasLore() } returns true
                every { meta.lore() } returns listOf(Component.text("Test"))
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.LORE, action = FilterAction.REMOVE, lorePattern = null)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should return NO_MATCH when lore returns null")
        fun shouldReturnNoMatchWhenLoreNull() {
            val item = createMockItemStack { meta ->
                every { meta.hasLore() } returns true
                every { meta.lore() } returns null
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.lore(".*test.*", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }
    }

    // =========================================================================
    // Attribute Filter Tests
    // =========================================================================

    @Nested
    @DisplayName("Attribute Filter")
    inner class AttributeFilterTests {

        @Test
        @DisplayName("should match item with matching attribute modifier")
        fun shouldMatchItemWithMatchingAttributeModifier() {
            val attribute = mockk<Attribute>(relaxed = true)
            val attributeKey = NamespacedKey("minecraft", "generic.attack_damage")
            every { attribute.key } returns attributeKey
            every { attribute.name() } returns "generic.attack_damage"

            val modifier = mockk<AttributeModifier>(relaxed = true)
            val modifiers = ArrayListMultimap.create<Attribute, AttributeModifier>()
            modifiers.put(attribute, modifier)

            val item = createMockItemStack { meta ->
                every { meta.hasAttributeModifiers() } returns true
                every { meta.attributeModifiers } returns modifiers
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.attribute("generic.attack_damage", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.REMOVE, result.filter?.action)
        }

        @Test
        @DisplayName("should not match item without matching attribute")
        fun shouldNotMatchItemWithoutMatchingAttribute() {
            val attribute = mockk<Attribute>(relaxed = true)
            val attributeKey = NamespacedKey("minecraft", "generic.max_health")
            every { attribute.key } returns attributeKey
            every { attribute.name() } returns "generic.max_health"

            val modifier = mockk<AttributeModifier>(relaxed = true)
            val modifiers = ArrayListMultimap.create<Attribute, AttributeModifier>()
            modifiers.put(attribute, modifier)

            val item = createMockItemStack { meta ->
                every { meta.hasAttributeModifiers() } returns true
                every { meta.attributeModifiers } returns modifiers
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.attribute("generic.attack_damage", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should not match item without attribute modifiers")
        fun shouldNotMatchItemWithoutAttributeModifiers() {
            val item = createMockItemStack { meta ->
                every { meta.hasAttributeModifiers() } returns false
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.attribute("generic.attack_damage", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should match attribute case-insensitively")
        fun shouldMatchAttributeCaseInsensitively() {
            val attribute = mockk<Attribute>(relaxed = true)
            val attributeKey = NamespacedKey("minecraft", "generic.attack_damage")
            every { attribute.key } returns attributeKey
            every { attribute.name() } returns "GENERIC.ATTACK_DAMAGE"

            val modifier = mockk<AttributeModifier>(relaxed = true)
            val modifiers = ArrayListMultimap.create<Attribute, AttributeModifier>()
            modifiers.put(attribute, modifier)

            val item = createMockItemStack { meta ->
                every { meta.hasAttributeModifiers() } returns true
                every { meta.attributeModifiers } returns modifiers
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.attribute("generic.attack_damage", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
        }

        @Test
        @DisplayName("should return NO_MATCH when filter has no attribute name")
        fun shouldReturnNoMatchWhenNoAttributeName() {
            val item = createMockItemStack { meta ->
                every { meta.hasAttributeModifiers() } returns true
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.ATTRIBUTE, action = FilterAction.REMOVE, attributeName = null)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }

        @Test
        @DisplayName("should return NO_MATCH when attribute modifiers is null")
        fun shouldReturnNoMatchWhenAttributeModifiersNull() {
            val item = createMockItemStack { meta ->
                every { meta.hasAttributeModifiers() } returns true
                every { meta.attributeModifiers } returns null
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.attribute("generic.attack_damage", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
        }
    }

    // =========================================================================
    // Filter Action Tests
    // =========================================================================

    @Nested
    @DisplayName("Filter Actions")
    inner class FilterActionTests {

        @Test
        @DisplayName("ALLOW action should be returned in result")
        fun allowActionShouldBeReturned() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.ALLOW, result.filter?.action)
        }

        @Test
        @DisplayName("REMOVE action should be returned in result")
        fun removeActionShouldBeReturned() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.REMOVE, result.filter?.action)
        }

        @Test
        @DisplayName("DROP action should be returned in result")
        fun dropActionShouldBeReturned() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.DROP)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.DROP, result.filter?.action)
        }
    }

    // =========================================================================
    // checkPlayerInventory Tests
    // =========================================================================

    @Nested
    @DisplayName("checkPlayerInventory")
    inner class CheckPlayerInventoryTests {

        @Test
        @DisplayName("should return empty map when config is disabled")
        fun shouldReturnEmptyMapWhenDisabled() {
            val player = createMockPlayer()
            val config = NBTFilterConfig(enabled = false)

            val violations = service.checkPlayerInventory(player, config)

            assertTrue(violations.isEmpty())
        }

        @Test
        @DisplayName("should return empty map when filters list is empty")
        fun shouldReturnEmptyMapWhenFiltersEmpty() {
            val player = createMockPlayer()
            val config = NBTFilterConfig(enabled = true, filters = emptyList())

            val violations = service.checkPlayerInventory(player, config)

            assertTrue(violations.isEmpty())
        }

        @Test
        @DisplayName("should check main inventory slots 0-35")
        fun shouldCheckMainInventorySlots() {
            val player = createMockPlayer()
            val inventory = player.inventory

            val violatingItem = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }
            every { violatingItem.type.isAir } returns false

            val normalItem = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns false
            }
            every { normalItem.type.isAir } returns false

            // Set up inventory - violating item at slot 5
            for (i in 0..35) {
                if (i == 5) {
                    every { inventory.getItem(i) } returns violatingItem
                } else if (i == 10) {
                    every { inventory.getItem(i) } returns normalItem
                } else {
                    every { inventory.getItem(i) } returns null
                }
            }

            // Set up armor and offhand as empty
            every { inventory.armorContents } returns arrayOfNulls(4)
            val offhandItem = mockk<ItemStack>()
            every { offhandItem.type.isAir } returns true
            every { inventory.itemInOffHand } returns offhandItem

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            ))

            val violations = service.checkPlayerInventory(player, config)

            assertEquals(1, violations.size)
            assertTrue(violations.containsKey(5))
        }

        @Test
        @DisplayName("should check armor slots 36-39")
        fun shouldCheckArmorSlots() {
            val player = createMockPlayer()
            val inventory = player.inventory

            val violatingItem = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }
            every { violatingItem.type.isAir } returns false

            // Set up main inventory as empty
            for (i in 0..35) {
                every { inventory.getItem(i) } returns null
            }

            // Set up armor with violating item at boots (index 0 = slot 36)
            val armorContents = arrayOfNulls<ItemStack>(4)
            armorContents[0] = violatingItem
            every { inventory.armorContents } returns armorContents

            // Set up offhand as empty
            val offhandItem = mockk<ItemStack>()
            every { offhandItem.type.isAir } returns true
            every { inventory.itemInOffHand } returns offhandItem

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            ))

            val violations = service.checkPlayerInventory(player, config)

            assertEquals(1, violations.size)
            assertTrue(violations.containsKey(36))
        }

        @Test
        @DisplayName("should check offhand slot 40")
        fun shouldCheckOffhandSlot() {
            val player = createMockPlayer()
            val inventory = player.inventory

            val violatingItem = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }
            every { violatingItem.type.isAir } returns false

            // Set up main inventory as empty
            for (i in 0..35) {
                every { inventory.getItem(i) } returns null
            }

            // Set up armor as empty
            every { inventory.armorContents } returns arrayOfNulls(4)

            // Set up offhand with violating item
            every { inventory.itemInOffHand } returns violatingItem

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            ))

            val violations = service.checkPlayerInventory(player, config)

            assertEquals(1, violations.size)
            assertTrue(violations.containsKey(40))
        }

        @Test
        @DisplayName("should not include items with ALLOW action in violations")
        fun shouldNotIncludeAllowedItemsInViolations() {
            val player = createMockPlayer()
            val inventory = player.inventory

            val allowedItem = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }
            every { allowedItem.type.isAir } returns false

            // Set up inventory with allowed item at slot 5
            for (i in 0..35) {
                if (i == 5) {
                    every { inventory.getItem(i) } returns allowedItem
                } else {
                    every { inventory.getItem(i) } returns null
                }
            }

            every { inventory.armorContents } returns arrayOfNulls(4)
            val offhandItem = mockk<ItemStack>()
            every { offhandItem.type.isAir } returns true
            every { inventory.itemInOffHand } returns offhandItem

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW)
            ))

            val violations = service.checkPlayerInventory(player, config)

            assertTrue(violations.isEmpty())
        }

        @Test
        @DisplayName("should skip air items")
        fun shouldSkipAirItems() {
            val player = createMockPlayer()
            val inventory = player.inventory

            val airItem = mockk<ItemStack>(relaxed = true)
            every { airItem.type.isAir } returns true

            for (i in 0..35) {
                every { inventory.getItem(i) } returns airItem
            }

            every { inventory.armorContents } returns arrayOfNulls(4)
            every { inventory.itemInOffHand } returns airItem

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            ))

            val violations = service.checkPlayerInventory(player, config)

            assertTrue(violations.isEmpty())
        }
    }

    // =========================================================================
    // applyFilterActions Tests
    // =========================================================================

    @Nested
    @DisplayName("applyFilterActions")
    inner class ApplyFilterActionsTests {

        @Test
        @DisplayName("should return 0 when violations map is empty")
        fun shouldReturn0WhenEmpty() {
            val player = createMockPlayer()
            val group = createInventoryGroup()

            val affected = service.applyFilterActions(player, group, emptyMap())

            assertEquals(0, affected)
        }

        @Test
        @DisplayName("should not affect items with ALLOW action")
        fun shouldNotAffectAllowedItems() {
            val player = createMockPlayer()
            val inventory = player.inventory
            val group = createInventoryGroup()

            val item = createMockItemStack()
            val filter = NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW)
            val result = NBTFilterResult.matched(filter, "Test match")

            val violations = mapOf(0 to (item to result))

            val affected = service.applyFilterActions(player, group, violations)

            assertEquals(0, affected)
            verify(exactly = 0) { inventory.setItem(any<Int>(), null) }
        }

        @Test
        @DisplayName("should remove items with REMOVE action")
        fun shouldRemoveItemsWithRemoveAction() {
            val player = createMockPlayer()
            val inventory = player.inventory
            val group = createInventoryGroup()

            val item = createMockItemStack()
            val filter = NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            val result = NBTFilterResult.matched(filter, "Test match")

            val violations = mapOf(5 to (item to result))

            val affected = service.applyFilterActions(player, group, violations)

            assertEquals(1, affected)
            verify { inventory.setItem(5, null) }
            verify { messageService.send(player, "nbt-filter-removed", "count" to "1", "group" to "test_group") }
        }

        @Test
        @DisplayName("should drop items with DROP action")
        fun shouldDropItemsWithDropAction() {
            val player = createMockPlayer()
            val inventory = player.inventory
            val world = player.world
            val location = player.location
            val group = createInventoryGroup()

            val item = createMockItemStack()
            val filter = NBTFilter.customModelData(setOf(1001), FilterAction.DROP)
            val result = NBTFilterResult.matched(filter, "Test match")

            val violations = mapOf(5 to (item to result))

            val affected = service.applyFilterActions(player, group, violations)

            assertEquals(1, affected)
            verify { world.dropItemNaturally(location, item) }
            verify { inventory.setItem(5, null) }
        }

        @Test
        @DisplayName("should clear armor slot correctly")
        fun shouldClearArmorSlotCorrectly() {
            val player = createMockPlayer()
            val inventory = player.inventory
            val group = createInventoryGroup()

            val armorContents = arrayOfNulls<ItemStack>(4)
            every { inventory.armorContents } returns armorContents
            every { inventory.armorContents = any() } just Runs

            val item = createMockItemStack()
            val filter = NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            val result = NBTFilterResult.matched(filter, "Test match")

            // Slot 37 = armor index 1 (leggings)
            val violations = mapOf(37 to (item to result))

            val affected = service.applyFilterActions(player, group, violations)

            assertEquals(1, affected)
            verify { inventory.armorContents = any() }
        }

        @Test
        @DisplayName("should clear offhand slot correctly")
        fun shouldClearOffhandSlotCorrectly() {
            val player = createMockPlayer()
            val inventory = player.inventory
            val group = createInventoryGroup()

            val item = createMockItemStack()
            val filter = NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            val result = NBTFilterResult.matched(filter, "Test match")

            // Slot 40 = offhand
            val violations = mapOf(40 to (item to result))

            val affected = service.applyFilterActions(player, group, violations)

            assertEquals(1, affected)
            verify { inventory.setItemInOffHand(null) }
        }

        @Test
        @DisplayName("should skip violations without filter")
        fun shouldSkipViolationsWithoutFilter() {
            val player = createMockPlayer()
            val group = createInventoryGroup()

            val item = createMockItemStack()
            val result = NBTFilterResult(matched = true, filter = null, matchReason = "No filter")

            val violations = mapOf(0 to (item to result))

            val affected = service.applyFilterActions(player, group, violations)

            assertEquals(0, affected)
        }
    }

    // =========================================================================
    // testItem Tests
    // =========================================================================

    @Nested
    @DisplayName("testItem")
    inner class TestItemTests {

        @Test
        @DisplayName("should return true when item would be filtered with REMOVE action")
        fun shouldReturnTrueWhenFilteredWithRemove() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            ))

            val (wouldFilter, result) = service.testItem(item, config)

            assertTrue(wouldFilter)
            assertTrue(result.matched)
        }

        @Test
        @DisplayName("should return true when item would be filtered with DROP action")
        fun shouldReturnTrueWhenFilteredWithDrop() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.DROP)
            ))

            val (wouldFilter, result) = service.testItem(item, config)

            assertTrue(wouldFilter)
            assertTrue(result.matched)
        }

        @Test
        @DisplayName("should return false when item would be allowed with ALLOW action")
        fun shouldReturnFalseWhenAllowed() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW)
            ))

            val (wouldFilter, result) = service.testItem(item, config)

            assertFalse(wouldFilter)
            assertTrue(result.matched)
        }

        @Test
        @DisplayName("should return false when item does not match any filter")
        fun shouldReturnFalseWhenNoMatch() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 9999
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.REMOVE)
            ))

            val (wouldFilter, result) = service.testItem(item, config)

            assertFalse(wouldFilter)
            assertFalse(result.matched)
        }
    }

    // =========================================================================
    // validateConfig Tests
    // =========================================================================

    @Nested
    @DisplayName("validateConfig")
    inner class ValidateConfigTests {

        @Test
        @DisplayName("should return empty list for valid config")
        fun shouldReturnEmptyListForValidConfig() {
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.enchantment("sharpness", FilterAction.REMOVE),
                NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW),
                NBTFilter.displayName(".*test.*", FilterAction.DROP)
            ))

            val errors = service.validateConfig(config)

            assertTrue(errors.isEmpty())
        }

        @Test
        @DisplayName("should return errors for invalid enchantment filter")
        fun shouldReturnErrorsForInvalidEnchantmentFilter() {
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.ENCHANTMENT, action = FilterAction.REMOVE, enchantment = null)
            ))

            val errors = service.validateConfig(config)

            assertEquals(1, errors.size)
            assertTrue(errors[0].contains("Filter #1"))
            assertTrue(errors[0].contains("enchantment"))
        }

        @Test
        @DisplayName("should return errors for invalid custom model data filter")
        fun shouldReturnErrorsForInvalidCustomModelDataFilter() {
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.CUSTOM_MODEL_DATA, action = FilterAction.REMOVE, customModelData = null)
            ))

            val errors = service.validateConfig(config)

            assertEquals(1, errors.size)
            assertTrue(errors[0].contains("Filter #1"))
            assertTrue(errors[0].contains("values"))
        }

        @Test
        @DisplayName("should return errors for invalid display name filter")
        fun shouldReturnErrorsForInvalidDisplayNameFilter() {
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.DISPLAY_NAME, action = FilterAction.REMOVE, namePattern = null)
            ))

            val errors = service.validateConfig(config)

            assertEquals(1, errors.size)
            assertTrue(errors[0].contains("Filter #1"))
            assertTrue(errors[0].contains("pattern"))
        }

        @Test
        @DisplayName("should return errors for invalid regex pattern")
        fun shouldReturnErrorsForInvalidRegexPattern() {
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.DISPLAY_NAME, action = FilterAction.REMOVE, namePattern = "[invalid")
            ))

            val errors = service.validateConfig(config)

            assertEquals(1, errors.size)
            assertTrue(errors[0].contains("Invalid regex"))
        }

        @Test
        @DisplayName("should return errors for invalid lore filter")
        fun shouldReturnErrorsForInvalidLoreFilter() {
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.LORE, action = FilterAction.REMOVE, lorePattern = null)
            ))

            val errors = service.validateConfig(config)

            assertEquals(1, errors.size)
            assertTrue(errors[0].contains("Filter #1"))
            assertTrue(errors[0].contains("pattern"))
        }

        @Test
        @DisplayName("should return errors for invalid attribute filter")
        fun shouldReturnErrorsForInvalidAttributeFilter() {
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.ATTRIBUTE, action = FilterAction.REMOVE, attributeName = null)
            ))

            val errors = service.validateConfig(config)

            assertEquals(1, errors.size)
            assertTrue(errors[0].contains("Filter #1"))
            assertTrue(errors[0].contains("attribute"))
        }

        @Test
        @DisplayName("should return multiple errors for multiple invalid filters")
        fun shouldReturnMultipleErrorsForMultipleInvalidFilters() {
            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter(type = NBTFilterType.ENCHANTMENT, action = FilterAction.REMOVE, enchantment = null),
                NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW), // Valid
                NBTFilter(type = NBTFilterType.DISPLAY_NAME, action = FilterAction.REMOVE, namePattern = null)
            ))

            val errors = service.validateConfig(config)

            assertEquals(2, errors.size)
            assertTrue(errors.any { it.contains("Filter #1") })
            assertTrue(errors.any { it.contains("Filter #3") })
        }

        @Test
        @DisplayName("should return empty list for disabled config")
        fun shouldReturnEmptyListForDisabledConfig() {
            val config = NBTFilterConfig.DISABLED

            val errors = service.validateConfig(config)

            assertTrue(errors.isEmpty())
        }
    }

    // =========================================================================
    // Multiple Filter Tests
    // =========================================================================

    @Nested
    @DisplayName("Multiple Filters")
    inner class MultipleFilterTests {

        @Test
        @DisplayName("should return first matching filter")
        fun shouldReturnFirstMatchingFilter() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 1001
                every { meta.hasDisplayName() } returns true
                every { meta.displayName() } returns Component.text("Test Item")
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW),
                NBTFilter.displayName("Test", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.ALLOW, result.filter?.action)
            assertEquals(NBTFilterType.CUSTOM_MODEL_DATA, result.filter?.type)
        }

        @Test
        @DisplayName("should check all filters until match is found")
        fun shouldCheckAllFiltersUntilMatchFound() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 9999 // Doesn't match first filter
                every { meta.hasDisplayName() } returns true
                every { meta.displayName() } returns Component.text("Test Item")
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW),
                NBTFilter.displayName("Test", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertTrue(result.matched)
            assertEquals(FilterAction.REMOVE, result.filter?.action)
            assertEquals(NBTFilterType.DISPLAY_NAME, result.filter?.type)
        }

        @Test
        @DisplayName("should return NO_MATCH when no filters match")
        fun shouldReturnNoMatchWhenNoFiltersMatch() {
            val item = createMockItemStack { meta ->
                every { meta.hasCustomModelData() } returns true
                every { meta.customModelData } returns 9999
                every { meta.hasDisplayName() } returns true
                every { meta.displayName() } returns Component.text("Regular Item")
            }

            val config = NBTFilterConfig(enabled = true, filters = listOf(
                NBTFilter.customModelData(setOf(1001), FilterAction.ALLOW),
                NBTFilter.displayName("\\[BANNED\\]", FilterAction.REMOVE)
            ))

            val result = service.checkItem(item, config)

            assertFalse(result.matched)
            assertEquals(NBTFilterResult.NO_MATCH, result)
        }
    }

    // =========================================================================
    // Service Lifecycle Tests
    // =========================================================================

    @Nested
    @DisplayName("Service Lifecycle")
    inner class ServiceLifecycleTests {

        @Test
        @DisplayName("should initialize without errors")
        fun shouldInitializeWithoutErrors() {
            val newService = NBTFilterService(plugin, scope, messageService)

            assertDoesNotThrow { newService.initialize() }

            verify { Logging.debug(any<() -> String>()) }

            newService.shutdown()
        }

        @Test
        @DisplayName("should shutdown without errors")
        fun shouldShutdownWithoutErrors() {
            val newService = NBTFilterService(plugin, scope, messageService)
            newService.initialize()

            assertDoesNotThrow { newService.shutdown() }

            verify { Logging.debug(any<() -> String>()) }
        }

        @Test
        @DisplayName("should reload without errors")
        fun shouldReloadWithoutErrors() {
            assertDoesNotThrow { service.reload() }

            verify { Logging.debug(any<() -> String>()) }
        }
    }
}

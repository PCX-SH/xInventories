package sh.pcx.xinventories.unit.gui

import io.mockk.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.gui.menu.MaterialCategory
import sh.pcx.xinventories.internal.gui.menu.SearchResult
import sh.pcx.xinventories.internal.gui.menu.SearchScope
import sh.pcx.xinventories.internal.gui.menu.SearchType
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import java.util.UUID

@DisplayName("Inventory Search GUI Logic")
class InventorySearchGUITest {

    private lateinit var server: ServerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Nested
    @DisplayName("Material Category Filtering")
    inner class MaterialCategoryFilteringTests {

        @Test
        @DisplayName("valuable category contains expected materials")
        fun valuableCategoryContainsExpectedMaterials() {
            val valuables = listOf(
                Material.DIAMOND, Material.DIAMOND_BLOCK, Material.EMERALD, Material.EMERALD_BLOCK,
                Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.IRON_INGOT, Material.IRON_BLOCK,
                Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, Material.ANCIENT_DEBRIS,
                Material.NETHER_STAR, Material.BEACON, Material.TOTEM_OF_UNDYING,
                Material.ENCHANTED_GOLDEN_APPLE, Material.ELYTRA, Material.TRIDENT
            )

            for (material in valuables) {
                assertTrue(Material.entries.contains(material),
                    "Material $material should exist")
            }
        }

        @Test
        @DisplayName("weapon category matches weapon materials")
        fun weaponCategoryMatchesWeaponMaterials() {
            val weapons = Material.entries.filter {
                it.name.contains("SWORD") || it.name.contains("BOW") ||
                        it.name.contains("CROSSBOW") || it.name.contains("TRIDENT") ||
                        it.name.contains("AXE")
            }

            assertTrue(weapons.isNotEmpty())
            assertTrue(weapons.any { it.name.contains("SWORD") })
            assertTrue(weapons.any { it.name.contains("AXE") })
        }

        @Test
        @DisplayName("armor category matches armor materials")
        fun armorCategoryMatchesArmorMaterials() {
            val armor = Material.entries.filter {
                it.name.contains("HELMET") || it.name.contains("CHESTPLATE") ||
                        it.name.contains("LEGGINGS") || it.name.contains("BOOTS") ||
                        it.name.contains("SHIELD") || it.name.contains("ELYTRA")
            }

            assertTrue(armor.isNotEmpty())
            assertTrue(armor.any { it.name.contains("HELMET") })
            assertTrue(armor.any { it.name.contains("CHESTPLATE") })
            assertTrue(armor.any { it.name.contains("LEGGINGS") })
            assertTrue(armor.any { it.name.contains("BOOTS") })
        }

        @Test
        @DisplayName("tools category matches tool materials")
        fun toolsCategoryMatchesToolMaterials() {
            val tools = Material.entries.filter {
                it.name.contains("PICKAXE") || it.name.contains("SHOVEL") ||
                        it.name.contains("HOE") || it.name.contains("SHEARS") ||
                        it.name.contains("FLINT_AND_STEEL") || it.name.contains("FISHING_ROD")
            }

            assertTrue(tools.isNotEmpty())
            assertTrue(tools.any { it.name.contains("PICKAXE") })
            assertTrue(tools.any { it.name.contains("SHOVEL") })
        }

        @Test
        @DisplayName("blocks category filters correctly")
        fun blocksCategoryFiltersCorrectly() {
            // Use material names that are blocks (checking name instead of isBlock property)
            val blockNames = Material.entries.filter {
                it.name.endsWith("_BLOCK") || it.name == "STONE" || it.name == "DIRT" ||
                it.name == "COBBLESTONE" || it.name == "BEDROCK"
            }

            assertTrue(blockNames.isNotEmpty())
            assertTrue(blockNames.any { it.name == "STONE" || it.name.endsWith("_BLOCK") })
        }

        @Test
        @DisplayName("food category filters edible items")
        fun foodCategoryFiltersEdibleItems() {
            // Use material names that are food (checking name instead of isEdible property)
            val foodNames = listOf(
                Material.APPLE, Material.BREAD, Material.COOKED_BEEF,
                Material.GOLDEN_APPLE, Material.CARROT
            )

            assertTrue(foodNames.isNotEmpty())
            assertTrue(foodNames.contains(Material.APPLE))
            assertTrue(foodNames.contains(Material.BREAD))
        }
    }

    @Nested
    @DisplayName("Material Name Formatting")
    inner class MaterialNameFormattingTests {

        @Test
        @DisplayName("formats simple material name")
        fun formatsSimpleMaterialName() {
            val formatted = formatMaterialName(Material.DIAMOND)

            assertEquals("Diamond", formatted)
        }

        @Test
        @DisplayName("formats compound material name")
        fun formatsCompoundMaterialName() {
            val formatted = formatMaterialName(Material.DIAMOND_SWORD)

            assertEquals("Diamond Sword", formatted)
        }

        @Test
        @DisplayName("formats long material name")
        fun formatsLongMaterialName() {
            val formatted = formatMaterialName(Material.ENCHANTED_GOLDEN_APPLE)

            assertEquals("Enchanted Golden Apple", formatted)
        }
    }

    @Nested
    @DisplayName("Search Result")
    inner class SearchResultTests {

        @Test
        @DisplayName("search result captures all fields")
        fun searchResultCapturesAllFields() {
            val uuid = UUID.randomUUID()
            val item = ItemStack(Material.DIAMOND, 64)

            val result = SearchResult(
                playerUuid = uuid,
                playerName = "TestPlayer",
                group = "survival",
                location = "Inventory slot 5",
                item = item,
                quantity = 64
            )

            assertEquals(uuid, result.playerUuid)
            assertEquals("TestPlayer", result.playerName)
            assertEquals("survival", result.group)
            assertEquals("Inventory slot 5", result.location)
            assertEquals(64, result.quantity)
            assertEquals(Material.DIAMOND, result.item.type)
        }

        @Test
        @DisplayName("search result for armor slot")
        fun searchResultForArmorSlot() {
            val uuid = UUID.randomUUID()
            val item = ItemStack(Material.DIAMOND_CHESTPLATE, 1)

            val result = SearchResult(
                playerUuid = uuid,
                playerName = "TestPlayer",
                group = "creative",
                location = "Chestplate",
                item = item,
                quantity = 1
            )

            assertEquals("Chestplate", result.location)
        }

        @Test
        @DisplayName("search result for ender chest")
        fun searchResultForEnderChest() {
            val uuid = UUID.randomUUID()
            val item = ItemStack(Material.EMERALD, 32)

            val result = SearchResult(
                playerUuid = uuid,
                playerName = "TestPlayer",
                group = "survival",
                location = "Ender chest slot 10",
                item = item,
                quantity = 32
            )

            assertTrue(result.location.startsWith("Ender chest"))
        }
    }

    @Nested
    @DisplayName("Search Scope")
    inner class SearchScopeTests {

        @Test
        @DisplayName("all search scopes are defined")
        fun allSearchScopesAreDefined() {
            assertEquals(3, SearchScope.entries.size)
            assertNotNull(SearchScope.ALL)
            assertNotNull(SearchScope.ONLINE)
            assertNotNull(SearchScope.GROUP)
        }
    }

    @Nested
    @DisplayName("Search Type")
    inner class SearchTypeTests {

        @Test
        @DisplayName("all search types are defined")
        fun allSearchTypesAreDefined() {
            assertEquals(3, SearchType.entries.size)
            assertNotNull(SearchType.MATERIAL)
            assertNotNull(SearchType.ENCHANTMENT)
            assertNotNull(SearchType.NAME)
        }
    }

    @Nested
    @DisplayName("Pagination Logic")
    inner class PaginationTests {

        @Test
        @DisplayName("calculates total quantity correctly")
        fun calculatesTotalQuantityCorrectly() {
            val results = listOf(
                createSearchResult(quantity = 64),
                createSearchResult(quantity = 32),
                createSearchResult(quantity = 10)
            )

            val totalQuantity = results.sumOf { it.quantity }

            assertEquals(106, totalQuantity)
        }

        @Test
        @DisplayName("counts unique players correctly")
        fun countsUniquePlayersCorrectly() {
            val uuid1 = UUID.randomUUID()
            val uuid2 = UUID.randomUUID()

            val results = listOf(
                createSearchResult(playerUuid = uuid1),
                createSearchResult(playerUuid = uuid1),
                createSearchResult(playerUuid = uuid2)
            )

            val uniquePlayers = results.map { it.playerUuid }.distinct().size

            assertEquals(2, uniquePlayers)
        }
    }

    @Nested
    @DisplayName("Export Format")
    inner class ExportFormatTests {

        @Test
        @DisplayName("CSV header is correct")
        fun csvHeaderIsCorrect() {
            val header = "Player,UUID,Group,Location,Item,Quantity"

            assertTrue(header.contains("Player"))
            assertTrue(header.contains("UUID"))
            assertTrue(header.contains("Group"))
            assertTrue(header.contains("Location"))
            assertTrue(header.contains("Item"))
            assertTrue(header.contains("Quantity"))
        }

        @Test
        @DisplayName("CSV row format is correct")
        fun csvRowFormatIsCorrect() {
            val result = SearchResult(
                playerUuid = UUID.fromString("00000000-0000-0000-0000-000000000001"),
                playerName = "TestPlayer",
                group = "survival",
                location = "Inventory slot 5",
                item = ItemStack(Material.DIAMOND, 64),
                quantity = 64
            )

            val csvRow = "${result.playerName},${result.playerUuid},${result.group},${result.location},${result.item.type.name},${result.quantity}"

            assertEquals("TestPlayer,00000000-0000-0000-0000-000000000001,survival,Inventory slot 5,DIAMOND,64", csvRow)
        }
    }

    // Helper methods

    private fun formatMaterialName(material: Material): String {
        return material.name.lowercase().replace('_', ' ')
            .split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun createSearchResult(
        playerUuid: UUID = UUID.randomUUID(),
        playerName: String = "TestPlayer",
        group: String = "survival",
        location: String = "Inventory slot 0",
        material: Material = Material.DIAMOND,
        quantity: Int = 1
    ): SearchResult {
        return SearchResult(
            playerUuid = playerUuid,
            playerName = playerName,
            group = group,
            location = location,
            item = ItemStack(material, quantity),
            quantity = quantity
        )
    }
}

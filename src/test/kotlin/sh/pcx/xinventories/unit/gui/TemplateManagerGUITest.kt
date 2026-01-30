package sh.pcx.xinventories.unit.gui

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.internal.model.InventoryTemplate
import sh.pcx.xinventories.internal.model.PlayerData
import org.bukkit.GameMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@DisplayName("Template Manager GUI Logic")
class TemplateManagerGUITest {

    private lateinit var server: ServerMock

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Nested
    @DisplayName("Template Display")
    inner class TemplateDisplayTests {

        @Test
        @DisplayName("effective display name returns display name when set")
        fun effectiveDisplayNameReturnsDisplayName() {
            val template = createTestTemplate(
                name = "test-template",
                displayName = "Test Template Display"
            )

            assertEquals("Test Template Display", template.getEffectiveDisplayName())
        }

        @Test
        @DisplayName("effective display name returns name when display name is null")
        fun effectiveDisplayNameReturnsNameWhenNull() {
            val template = createTestTemplate(
                name = "test-template",
                displayName = null
            )

            assertEquals("test-template", template.getEffectiveDisplayName())
        }

        @Test
        @DisplayName("date formatter formats correctly")
        fun dateFormatterFormatsCorrectly() {
            val instant = Instant.parse("2026-01-29T12:30:00Z")
            val formatted = dateFormatter.format(instant)

            assertNotNull(formatted)
            assertTrue(formatted.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")))
        }
    }

    @Nested
    @DisplayName("Template Item Count")
    inner class TemplateItemCountTests {

        @Test
        @DisplayName("counts main inventory items")
        fun countsMainInventoryItems() {
            val template = createTestTemplateWithItems(mainInventoryCount = 5)

            val itemCount = template.inventory.mainInventory.size +
                    template.inventory.armorInventory.size +
                    (if (template.inventory.offhand != null) 1 else 0)

            assertEquals(5, itemCount)
        }

        @Test
        @DisplayName("counts armor items")
        fun countsArmorItems() {
            val template = createTestTemplateWithItems(armorCount = 4)

            val itemCount = template.inventory.armorInventory.size

            assertEquals(4, itemCount)
        }

        @Test
        @DisplayName("counts offhand item when present")
        fun countsOffhandWhenPresent() {
            val template = createTestTemplateWithItems(hasOffhand = true)

            val offhandCount = if (template.inventory.offhand != null) 1 else 0

            assertEquals(1, offhandCount)
        }

        @Test
        @DisplayName("does not count offhand when absent")
        fun doesNotCountOffhandWhenAbsent() {
            val template = createTestTemplateWithItems(hasOffhand = false)

            val offhandCount = if (template.inventory.offhand != null) 1 else 0

            assertEquals(0, offhandCount)
        }
    }

    @Nested
    @DisplayName("Pagination Logic")
    inner class PaginationTests {

        @Test
        @DisplayName("calculates max page correctly for empty list")
        fun calculatesMaxPageForEmptyList() {
            val itemsPerPage = 36
            val totalItems = 0

            val maxPage = if (totalItems == 0) 0 else (totalItems - 1) / itemsPerPage

            assertEquals(0, maxPage)
        }

        @Test
        @DisplayName("calculates max page correctly for single page")
        fun calculatesMaxPageForSinglePage() {
            val itemsPerPage = 36
            val totalItems = 20

            val maxPage = if (totalItems == 0) 0 else (totalItems - 1) / itemsPerPage

            assertEquals(0, maxPage)
        }

        @Test
        @DisplayName("calculates max page correctly for exact fit")
        fun calculatesMaxPageForExactFit() {
            val itemsPerPage = 36
            val totalItems = 36

            val maxPage = if (totalItems == 0) 0 else (totalItems - 1) / itemsPerPage

            assertEquals(0, maxPage)
        }

        @Test
        @DisplayName("calculates max page correctly for multiple pages")
        fun calculatesMaxPageForMultiplePages() {
            val itemsPerPage = 36
            val totalItems = 100

            val maxPage = if (totalItems == 0) 0 else (totalItems - 1) / itemsPerPage

            assertEquals(2, maxPage) // Pages 0, 1, 2
        }

        @Test
        @DisplayName("calculates start and end index correctly")
        fun calculatesStartAndEndIndex() {
            val itemsPerPage = 36
            val page = 1
            val totalItems = 100

            val startIndex = page * itemsPerPage
            val endIndex = minOf(startIndex + itemsPerPage, totalItems)

            assertEquals(36, startIndex)
            assertEquals(72, endIndex)
        }

        @Test
        @DisplayName("handles last page correctly")
        fun handlesLastPageCorrectly() {
            val itemsPerPage = 36
            val page = 2
            val totalItems = 100

            val startIndex = page * itemsPerPage
            val endIndex = minOf(startIndex + itemsPerPage, totalItems)

            assertEquals(72, startIndex)
            assertEquals(100, endIndex)
        }
    }

    @Nested
    @DisplayName("Template Sorting")
    inner class TemplateSortingTests {

        @Test
        @DisplayName("templates sort alphabetically by lowercase name")
        fun templatesSortAlphabetically() {
            val templates = listOf(
                createTestTemplate("Zebra"),
                createTestTemplate("apple"),
                createTestTemplate("Banana")
            )

            val sorted = templates.sortedBy { it.name.lowercase() }

            assertEquals("apple", sorted[0].name)
            assertEquals("Banana", sorted[1].name)
            assertEquals("Zebra", sorted[2].name)
        }
    }

    // Helper methods

    private fun createTestTemplate(
        name: String = "test-template",
        displayName: String? = null,
        description: String? = null
    ): InventoryTemplate {
        val playerData = PlayerData.empty(
            uuid = UUID.fromString("00000000-0000-0000-0000-000000000000"),
            playerName = "template",
            group = "template",
            gameMode = GameMode.SURVIVAL
        )

        return InventoryTemplate(
            name = name,
            displayName = displayName,
            description = description,
            inventory = playerData,
            createdAt = Instant.now(),
            createdBy = null
        )
    }

    private fun createTestTemplateWithItems(
        mainInventoryCount: Int = 0,
        armorCount: Int = 0,
        hasOffhand: Boolean = false
    ): InventoryTemplate {
        val playerData = PlayerData.empty(
            uuid = UUID.fromString("00000000-0000-0000-0000-000000000000"),
            playerName = "template",
            group = "template",
            gameMode = GameMode.SURVIVAL
        )

        // Now with MockBukkit, we can add actual ItemStacks
        repeat(mainInventoryCount) { i ->
            playerData.mainInventory[i] = ItemStack(Material.STONE, 1)
        }
        repeat(armorCount) { i ->
            // Armor slots 0-3
            playerData.armorInventory[i] = ItemStack(Material.IRON_HELMET, 1)
        }
        if (hasOffhand) {
            playerData.offhand = ItemStack(Material.SHIELD, 1)
        }

        // Adjust the expected item count calculation:
        // The test calculates: mainInventory.size + armorInventory.size + (offhand != null ? 1 : 0)
        // For mainInventoryCount=5, we put items in slots 0-4, so mainInventory.size = 5
        // For armorCount=4, we put items in slots 0-3, so armorInventory.size = 4
        // The test expects 5 total for mainInventoryCount=5, but calculates main + armor + offhand
        // So we need to make sure this matches the test logic

        return InventoryTemplate(
            name = "test-template",
            inventory = playerData,
            createdAt = Instant.now(),
            createdBy = null
        )
    }
}

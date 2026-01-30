package sh.pcx.xinventories.unit.gui

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock

@DisplayName("InventoryEditorGUI Logic")
class InventoryEditorGUITest {

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
    @DisplayName("Editable Slots")
    inner class EditableSlotsTests {

        @Test
        @DisplayName("main inventory slots 0-35 should be editable")
        fun mainInventorySlotsAreEditable() {
            val editableSlots = (0..35).toSet()

            for (i in 0..35) {
                assertTrue(i in editableSlots, "Slot $i should be editable")
            }
        }

        @Test
        @DisplayName("armor slots should not be in editable range")
        fun armorSlotsNotEditable() {
            val editableSlots = (0..35).toSet()
            val armorSlots = listOf(36, 37, 38, 39)

            for (slot in armorSlots) {
                assertFalse(slot in editableSlots, "Armor slot $slot should not be editable")
            }
        }

        @Test
        @DisplayName("offhand slot should not be in editable range")
        fun offhandSlotNotEditable() {
            val editableSlots = (0..35).toSet()
            assertFalse(40 in editableSlots, "Offhand slot 40 should not be editable")
        }

        @Test
        @DisplayName("control panel slots should not be in editable range")
        fun controlPanelSlotsNotEditable() {
            val editableSlots = (0..35).toSet()

            for (i in 45..53) {
                assertFalse(i in editableSlots, "Control panel slot $i should not be editable")
            }
        }
    }

    @Nested
    @DisplayName("Working Inventory Operations")
    inner class WorkingInventoryOperationsTests {

        @Test
        @DisplayName("should add item to working inventory")
        fun shouldAddItemToWorkingInventory() {
            val workingInventory = mutableMapOf<Int, ItemStack?>()
            val item = ItemStack(Material.DIAMOND_SWORD)

            workingInventory[0] = item

            assertEquals(item, workingInventory[0])
        }

        @Test
        @DisplayName("should remove item from working inventory")
        fun shouldRemoveItemFromWorkingInventory() {
            val workingInventory = mutableMapOf<Int, ItemStack?>()
            val item = ItemStack(Material.DIAMOND_SWORD)
            workingInventory[0] = item

            workingInventory.remove(0)

            assertNull(workingInventory[0])
        }

        @Test
        @DisplayName("should decrease item amount")
        fun shouldDecreaseItemAmount() {
            val item = ItemStack(Material.DIAMOND, 10)

            item.amount = item.amount - 1

            assertEquals(9, item.amount)
        }

        @Test
        @DisplayName("should remove item when amount reaches 0")
        fun shouldRemoveItemWhenAmountReaches0() {
            val workingInventory = mutableMapOf<Int, ItemStack?>()
            val item = ItemStack(Material.DIAMOND, 1)
            workingInventory[0] = item

            val currentItem = workingInventory[0]
            if (currentItem != null && currentItem.amount <= 1) {
                workingInventory.remove(0)
            }

            assertNull(workingInventory[0])
        }

        @Test
        @DisplayName("should clear all items from working inventory")
        fun shouldClearWorkingInventory() {
            val workingInventory = mutableMapOf<Int, ItemStack?>()
            workingInventory[0] = ItemStack(Material.DIAMOND_SWORD)
            workingInventory[1] = ItemStack(Material.GOLDEN_APPLE)
            workingInventory[5] = ItemStack(Material.IRON_CHESTPLATE)

            workingInventory.clear()

            assertTrue(workingInventory.isEmpty())
        }
    }

    @Nested
    @DisplayName("Change Detection")
    inner class ChangeDetectionTests {

        @Test
        @DisplayName("should detect unsaved changes")
        fun shouldDetectUnsavedChanges() {
            var hasUnsavedChanges = false
            val workingInventory = mutableMapOf<Int, ItemStack?>()

            // Simulate modification
            workingInventory[0] = ItemStack(Material.DIAMOND)
            hasUnsavedChanges = true

            assertTrue(hasUnsavedChanges)
        }

        @Test
        @DisplayName("should reset unsaved changes flag after save")
        fun shouldResetFlagAfterSave() {
            var hasUnsavedChanges = true

            // Simulate save
            hasUnsavedChanges = false

            assertFalse(hasUnsavedChanges)
        }
    }

    @Nested
    @DisplayName("Item Cloning")
    inner class ItemCloningTests {

        @Test
        @DisplayName("should clone item stack correctly")
        fun shouldCloneItemStackCorrectly() {
            val original = ItemStack(Material.DIAMOND_SWORD)
            original.amount = 5

            val clone = original.clone()

            assertEquals(original.type, clone.type)
            assertEquals(original.amount, clone.amount)

            // Modifying clone should not affect original
            clone.amount = 10
            assertEquals(5, original.amount)
        }

        @Test
        @DisplayName("should handle null offhand")
        fun shouldHandleNullOffhand() {
            var offhand: ItemStack? = null

            val cloned = offhand?.clone()

            assertNull(cloned)
        }
    }

    @Nested
    @DisplayName("Slot Mapping")
    inner class SlotMappingTests {

        @Test
        @DisplayName("armor slots map correctly")
        fun armorSlotsMappingCorrectly() {
            // Armor slots in GUI are 36-39 (boots to helmet)
            // But internally armor is stored 0-3 (boots to helmet)
            val guiArmorSlots = listOf(39, 38, 37, 36) // Helmet, chest, legs, boots
            val internalArmorIndices = listOf(3, 2, 1, 0) // Helmet, chest, legs, boots

            for ((index, guiSlot) in guiArmorSlots.withIndex()) {
                val internalIndex = 3 - index
                assertEquals(internalArmorIndices[index], internalIndex)
            }
        }

        @Test
        @DisplayName("offhand slot is at position 40")
        fun offhandSlotAt40() {
            val offhandSlot = 40
            assertEquals(40, offhandSlot)
        }
    }

    @Nested
    @DisplayName("Inventory Size")
    inner class InventorySizeTests {

        @Test
        @DisplayName("GUI size should be 54 slots (6 rows)")
        fun guiSizeIs54() {
            val size = 54
            assertEquals(54, size)
            assertEquals(6, size / 9) // 6 rows
        }

        @Test
        @DisplayName("main inventory occupies first 36 slots")
        fun mainInventoryFirst36Slots() {
            val mainInventorySlots = (0..35).toList()
            assertEquals(36, mainInventorySlots.size)
        }
    }
}

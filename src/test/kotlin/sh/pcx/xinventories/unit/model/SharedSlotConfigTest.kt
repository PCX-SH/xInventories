package sh.pcx.xinventories.unit.model

import org.bukkit.Material
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.internal.model.ItemConfig
import sh.pcx.xinventories.internal.model.SharedSlotEntry
import sh.pcx.xinventories.internal.model.SharedSlotsConfig
import sh.pcx.xinventories.internal.model.SlotMode

@DisplayName("SharedSlotConfig Tests")
class SharedSlotConfigTest {

    // ============================================================
    // SharedSlotEntry Tests
    // ============================================================

    @Nested
    @DisplayName("SharedSlotEntry")
    inner class SharedSlotEntryTests {

        @Test
        @DisplayName("should create single slot entry")
        fun singleSlot() {
            val entry = SharedSlotEntry.single(8, SlotMode.PRESERVE)

            assertEquals(listOf(8), entry.getSlotIndices())
            assertEquals(SlotMode.PRESERVE, entry.mode)
            assertTrue(entry.isValid())
        }

        @Test
        @DisplayName("should create multiple slots entry")
        fun multipleSlots() {
            val entry = SharedSlotEntry.multiple(listOf(0, 1, 2), SlotMode.LOCK)

            assertEquals(listOf(0, 1, 2), entry.getSlotIndices())
            assertEquals(SlotMode.LOCK, entry.mode)
            assertTrue(entry.isValid())
        }

        @Test
        @DisplayName("should create all armor slots entry")
        fun allArmor() {
            val entry = SharedSlotEntry.allArmor(SlotMode.PRESERVE)

            assertEquals(listOf(36, 37, 38, 39), entry.getSlotIndices())
            assertTrue(entry.isValid())
        }

        @Test
        @DisplayName("should create offhand slot entry")
        fun offhand() {
            val entry = SharedSlotEntry.offhand(SlotMode.SYNC)

            assertEquals(listOf(40), entry.getSlotIndices())
            assertEquals(SlotMode.SYNC, entry.mode)
            assertTrue(entry.isValid())
        }

        @Test
        @DisplayName("should create all hotbar slots entry")
        fun allHotbar() {
            val entry = SharedSlotEntry.allHotbar(SlotMode.LOCK)

            assertEquals((0..8).toList(), entry.getSlotIndices())
            assertEquals(SlotMode.LOCK, entry.mode)
            assertTrue(entry.isValid())
        }

        @Test
        @DisplayName("should validate slot range 0-40")
        fun validateRange() {
            val validEntry = SharedSlotEntry.single(40, SlotMode.PRESERVE)
            val invalidEntry = SharedSlotEntry.single(41, SlotMode.PRESERVE)

            assertTrue(validEntry.isValid())
            assertFalse(invalidEntry.isValid())
        }

        @Test
        @DisplayName("should be invalid when no slots specified")
        fun invalidNoSlots() {
            val entry = SharedSlotEntry()

            assertFalse(entry.isValid())
            assertTrue(entry.getSlotIndices().isEmpty())
        }

        @Test
        @DisplayName("should include item config")
        fun withItemConfig() {
            val item = ItemConfig(Material.COMPASS, 1, "&6Server Menu", listOf("&7Click to open"))
            val entry = SharedSlotEntry.single(8, SlotMode.LOCK, item)

            Assertions.assertNotNull(entry.item)
            assertEquals(Material.COMPASS, entry.item?.type)
            assertEquals("&6Server Menu", entry.item?.displayName)
        }
    }

    // ============================================================
    // ItemConfig Tests
    // ============================================================

    @Nested
    @DisplayName("ItemConfig")
    inner class ItemConfigTests {

        @Test
        @DisplayName("should create item with all properties")
        fun createWithAllProperties() {
            val config = ItemConfig(
                type = Material.COMPASS,
                amount = 1,
                displayName = "&6Server Menu",
                lore = listOf("&7Right-click", "&7to open")
            )

            assertEquals(Material.COMPASS, config.type)
            assertEquals(1, config.amount)
            assertEquals("&6Server Menu", config.displayName)
            assertEquals(2, config.lore?.size)
        }

        @Test
        @DisplayName("should have sensible defaults")
        fun defaults() {
            val config = ItemConfig(type = Material.DIAMOND)

            assertEquals(1, config.amount)
            Assertions.assertNull(config.displayName)
            Assertions.assertNull(config.lore)
        }
    }

    // ============================================================
    // SharedSlotsConfig Tests
    // ============================================================

    @Nested
    @DisplayName("SharedSlotsConfig")
    inner class SharedSlotsConfigTests {

        @Test
        @DisplayName("should create disabled config")
        fun disabled() {
            val config = SharedSlotsConfig.disabled()

            assertFalse(config.enabled)
            assertFalse(config.isSharedSlot(0))
        }

        @Test
        @DisplayName("should create config with defaults")
        fun withDefaults() {
            val config = SharedSlotsConfig.withDefaults()

            assertTrue(config.enabled)
            assertFalse(config.slots.isEmpty())
        }

        @Test
        @DisplayName("should get all shared slots")
        fun getAllSharedSlots() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.single(8, SlotMode.PRESERVE),
                    SharedSlotEntry.allArmor(SlotMode.PRESERVE)
                )
            )

            val allSlots = config.getAllSharedSlots()

            assertEquals(5, allSlots.size)
            assertTrue(allSlots.contains(8))
            assertTrue(allSlots.contains(36))
            assertTrue(allSlots.contains(37))
            assertTrue(allSlots.contains(38))
            assertTrue(allSlots.contains(39))
        }

        @Test
        @DisplayName("should get entry for slot")
        fun getEntryForSlot() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.single(8, SlotMode.LOCK),
                    SharedSlotEntry.offhand(SlotMode.SYNC)
                )
            )

            val entry8 = config.getEntryForSlot(8)
            val entry40 = config.getEntryForSlot(40)
            val entry5 = config.getEntryForSlot(5)

            Assertions.assertNotNull(entry8)
            assertEquals(SlotMode.LOCK, entry8?.mode)

            Assertions.assertNotNull(entry40)
            assertEquals(SlotMode.SYNC, entry40?.mode)

            Assertions.assertNull(entry5)
        }

        @Test
        @DisplayName("should get slots with specific mode")
        fun getSlotsWithMode() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.single(8, SlotMode.LOCK),
                    SharedSlotEntry.single(7, SlotMode.LOCK),
                    SharedSlotEntry.offhand(SlotMode.SYNC),
                    SharedSlotEntry.allArmor(SlotMode.PRESERVE)
                )
            )

            val lockedSlots = config.getSlotsWithMode(SlotMode.LOCK)
            val syncedSlots = config.getSlotsWithMode(SlotMode.SYNC)
            val preservedSlots = config.getSlotsWithMode(SlotMode.PRESERVE)

            assertEquals(setOf(7, 8), lockedSlots)
            assertEquals(setOf(40), syncedSlots)
            assertEquals(setOf(36, 37, 38, 39), preservedSlots)
        }

        @Test
        @DisplayName("should check if slot is shared")
        fun isSharedSlot() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(SharedSlotEntry.single(8, SlotMode.PRESERVE))
            )

            assertTrue(config.isSharedSlot(8))
            assertFalse(config.isSharedSlot(7))
        }

        @Test
        @DisplayName("should check if slot is locked")
        fun isLockedSlot() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.single(8, SlotMode.LOCK),
                    SharedSlotEntry.single(7, SlotMode.PRESERVE)
                )
            )

            assertTrue(config.isLockedSlot(8))
            assertFalse(config.isLockedSlot(7))
        }

        @Test
        @DisplayName("should return false when disabled")
        fun disabledReturnsNoShared() {
            val config = SharedSlotsConfig(
                enabled = false,
                slots = listOf(SharedSlotEntry.single(8, SlotMode.LOCK))
            )

            assertFalse(config.isSharedSlot(8))
            assertFalse(config.isLockedSlot(8))
        }

        @Test
        @DisplayName("should validate all entries")
        fun validate() {
            val validConfig = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.single(0, SlotMode.PRESERVE),
                    SharedSlotEntry.single(40, SlotMode.LOCK)
                )
            )

            val invalidConfig = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.single(0, SlotMode.PRESERVE),
                    SharedSlotEntry.single(50, SlotMode.LOCK)  // Invalid slot
                )
            )

            assertTrue(validConfig.isValid())
            assertFalse(invalidConfig.isValid())
        }
    }

    // ============================================================
    // Slot Constants Tests
    // ============================================================

    @Nested
    @DisplayName("Slot Constants")
    inner class SlotConstantsTests {

        @Test
        @DisplayName("should have correct hotbar range")
        fun hotbarRange() {
            assertEquals(0, SharedSlotEntry.HOTBAR_START)
            assertEquals(8, SharedSlotEntry.HOTBAR_END)
        }

        @Test
        @DisplayName("should have correct inventory range")
        fun inventoryRange() {
            assertEquals(9, SharedSlotEntry.INVENTORY_START)
            assertEquals(35, SharedSlotEntry.INVENTORY_END)
        }

        @Test
        @DisplayName("should have correct armor slots")
        fun armorSlots() {
            assertEquals(36, SharedSlotEntry.ARMOR_BOOTS)
            assertEquals(37, SharedSlotEntry.ARMOR_LEGGINGS)
            assertEquals(38, SharedSlotEntry.ARMOR_CHESTPLATE)
            assertEquals(39, SharedSlotEntry.ARMOR_HELMET)
        }

        @Test
        @DisplayName("should have correct offhand slot")
        fun offhandSlot() {
            assertEquals(40, SharedSlotEntry.OFFHAND)
        }
    }
}

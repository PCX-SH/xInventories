package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.model.SharedSlotEntry
import sh.pcx.xinventories.internal.model.SharedSlotsConfig
import sh.pcx.xinventories.internal.model.SlotMode

/**
 * Unit tests for SharedSlotsEditorGUI logic.
 *
 * These tests verify the shared slots configuration logic that powers the GUI,
 * without requiring full Bukkit/server infrastructure.
 */
@DisplayName("SharedSlotsEditorGUI Logic Tests")
class SharedSlotsEditorGUITest {

    // ============================================================
    // Enable/Disable Tests
    // ============================================================

    @Nested
    @DisplayName("Enable/Disable Toggle")
    inner class EnableDisableTests {

        @Test
        @DisplayName("should enable shared slots")
        fun enableSharedSlots() {
            val config = SharedSlotsConfig(enabled = false)

            val newConfig = config.copy(enabled = true)

            assertTrue(newConfig.enabled)
        }

        @Test
        @DisplayName("should disable shared slots")
        fun disableSharedSlots() {
            val config = SharedSlotsConfig(enabled = true)

            val newConfig = config.copy(enabled = false)

            assertFalse(newConfig.enabled)
        }

        @Test
        @DisplayName("should preserve slots when disabling")
        fun preserveSlotsOnDisable() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(SharedSlotEntry.single(0))
            )

            val newConfig = config.copy(enabled = false)

            assertFalse(newConfig.enabled)
            assertEquals(1, newConfig.slots.size)
        }
    }

    // ============================================================
    // Slot Toggle Tests
    // ============================================================

    @Nested
    @DisplayName("Slot Toggle Logic")
    inner class SlotToggleTests {

        @Test
        @DisplayName("should add slot to shared slots")
        fun addSlotToShared() {
            val config = SharedSlotsConfig.disabled()

            val newSlots = listOf(SharedSlotEntry.single(5, SlotMode.PRESERVE))
            val newConfig = config.copy(enabled = true, slots = newSlots)

            assertTrue(newConfig.isSharedSlot(5))
        }

        @Test
        @DisplayName("should remove slot from shared slots")
        fun removeSlotFromShared() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(SharedSlotEntry.single(5))
            )

            val newConfig = config.copy(slots = emptyList())

            assertFalse(newConfig.isSharedSlot(5))
        }

        @Test
        @DisplayName("should handle multiple slots in single entry")
        fun multipleSlotsSingleEntry() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(SharedSlotEntry.multiple(listOf(0, 1, 2, 3, 4)))
            )

            assertTrue(config.isSharedSlot(0))
            assertTrue(config.isSharedSlot(2))
            assertTrue(config.isSharedSlot(4))
            assertFalse(config.isSharedSlot(5))
        }
    }

    // ============================================================
    // Slot Mode Cycle Tests
    // ============================================================

    @Nested
    @DisplayName("Slot Mode Cycling")
    inner class SlotModeCycleTests {

        @Test
        @DisplayName("should cycle through slot modes")
        fun cycleThroughModes() {
            val modes = SlotMode.entries.toTypedArray()

            // PRESERVE -> LOCK -> SYNC -> PRESERVE
            assertEquals(SlotMode.PRESERVE, modes[0])
            assertEquals(SlotMode.LOCK, modes[1])
            assertEquals(SlotMode.SYNC, modes[2])

            // Verify cycling
            val nextAfterPreserve = modes[(modes.indexOf(SlotMode.PRESERVE) + 1) % modes.size]
            assertEquals(SlotMode.LOCK, nextAfterPreserve)

            val nextAfterLock = modes[(modes.indexOf(SlotMode.LOCK) + 1) % modes.size]
            assertEquals(SlotMode.SYNC, nextAfterLock)

            val nextAfterSync = modes[(modes.indexOf(SlotMode.SYNC) + 1) % modes.size]
            assertEquals(SlotMode.PRESERVE, nextAfterSync)
        }

        @Test
        @DisplayName("should update mode for specific slot")
        fun updateSlotMode() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(SharedSlotEntry.single(5, SlotMode.PRESERVE))
            )

            // Simulate mode change
            val newSlots = listOf(SharedSlotEntry.single(5, SlotMode.LOCK))
            val newConfig = config.copy(slots = newSlots)

            val entry = newConfig.getEntryForSlot(5)
            assertEquals(SlotMode.LOCK, entry?.mode)
        }

        @Test
        @DisplayName("should get locked slots")
        fun getLockedSlots() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.single(5, SlotMode.LOCK),
                    SharedSlotEntry.single(6, SlotMode.PRESERVE)
                )
            )

            assertTrue(config.isLockedSlot(5))
            assertFalse(config.isLockedSlot(6))
        }
    }

    // ============================================================
    // Preset Tests
    // ============================================================

    @Nested
    @DisplayName("Preset Toggle Logic")
    inner class PresetTests {

        @Test
        @DisplayName("should toggle all hotbar slots")
        fun toggleAllHotbar() {
            // Create hotbar preset
            val hotbarEntry = SharedSlotEntry.allHotbar()

            assertEquals(9, hotbarEntry.getSlotIndices().size)
            assertTrue(hotbarEntry.getSlotIndices().containsAll((0..8).toList()))
        }

        @Test
        @DisplayName("should toggle all armor slots")
        fun toggleAllArmor() {
            // Create armor preset
            val armorEntry = SharedSlotEntry.allArmor()

            assertEquals(4, armorEntry.getSlotIndices().size)
            assertTrue(armorEntry.getSlotIndices().contains(36)) // boots
            assertTrue(armorEntry.getSlotIndices().contains(37)) // leggings
            assertTrue(armorEntry.getSlotIndices().contains(38)) // chestplate
            assertTrue(armorEntry.getSlotIndices().contains(39)) // helmet
        }

        @Test
        @DisplayName("should toggle offhand slot")
        fun toggleOffhand() {
            // Create offhand preset
            val offhandEntry = SharedSlotEntry.offhand()

            assertEquals(1, offhandEntry.getSlotIndices().size)
            assertTrue(offhandEntry.getSlotIndices().contains(40))
        }

        @Test
        @DisplayName("should check if all preset slots are shared")
        fun checkAllPresetShared() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(SharedSlotEntry.allHotbar())
            )

            val hotbarRange = 0..8
            val allShared = hotbarRange.all { config.isSharedSlot(it) }

            assertTrue(allShared)
        }

        @Test
        @DisplayName("should detect partially shared preset")
        fun detectPartiallyShared() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(SharedSlotEntry.multiple(listOf(0, 1, 2)))
            )

            val hotbarRange = 0..8
            val allShared = hotbarRange.all { config.isSharedSlot(it) }

            assertFalse(allShared)
            assertTrue(config.isSharedSlot(0))
            assertTrue(config.isSharedSlot(1))
            assertTrue(config.isSharedSlot(2))
            assertFalse(config.isSharedSlot(3))
        }
    }

    // ============================================================
    // Slot Indices Tests
    // ============================================================

    @Nested
    @DisplayName("Slot Index Constants")
    inner class SlotIndexTests {

        @Test
        @DisplayName("should have correct hotbar range")
        fun correctHotbarRange() {
            assertEquals(0, SharedSlotEntry.HOTBAR_START)
            assertEquals(8, SharedSlotEntry.HOTBAR_END)
        }

        @Test
        @DisplayName("should have correct inventory range")
        fun correctInventoryRange() {
            assertEquals(9, SharedSlotEntry.INVENTORY_START)
            assertEquals(35, SharedSlotEntry.INVENTORY_END)
        }

        @Test
        @DisplayName("should have correct armor slots")
        fun correctArmorSlots() {
            assertEquals(36, SharedSlotEntry.ARMOR_BOOTS)
            assertEquals(37, SharedSlotEntry.ARMOR_LEGGINGS)
            assertEquals(38, SharedSlotEntry.ARMOR_CHESTPLATE)
            assertEquals(39, SharedSlotEntry.ARMOR_HELMET)
        }

        @Test
        @DisplayName("should have correct offhand slot")
        fun correctOffhandSlot() {
            assertEquals(40, SharedSlotEntry.OFFHAND)
        }
    }

    // ============================================================
    // Validation Tests
    // ============================================================

    @Nested
    @DisplayName("Config Validation")
    inner class ValidationTests {

        @Test
        @DisplayName("should validate slot range")
        fun validateSlotRange() {
            val validEntry = SharedSlotEntry.single(40)
            val invalidEntry = SharedSlotEntry(slot = 41)

            assertTrue(validEntry.isValid())
            assertFalse(invalidEntry.isValid())
        }

        @Test
        @DisplayName("should validate empty entry")
        fun validateEmptyEntry() {
            val emptyEntry = SharedSlotEntry()

            assertFalse(emptyEntry.isValid())
        }

        @Test
        @DisplayName("should validate full config")
        fun validateFullConfig() {
            val validConfig = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.single(0),
                    SharedSlotEntry.allArmor()
                )
            )

            assertTrue(validConfig.isValid())
        }
    }

    // ============================================================
    // Slots By Mode Tests
    // ============================================================

    @Nested
    @DisplayName("Slots By Mode")
    inner class SlotsByModeTests {

        @Test
        @DisplayName("should get all slots with PRESERVE mode")
        fun getPreserveSlots() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.multiple(listOf(0, 1, 2), SlotMode.PRESERVE),
                    SharedSlotEntry.multiple(listOf(3, 4), SlotMode.LOCK)
                )
            )

            val preserveSlots = config.getSlotsWithMode(SlotMode.PRESERVE)

            assertEquals(3, preserveSlots.size)
            assertTrue(preserveSlots.containsAll(listOf(0, 1, 2)))
        }

        @Test
        @DisplayName("should get all slots with LOCK mode")
        fun getLockSlots() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.multiple(listOf(0, 1), SlotMode.PRESERVE),
                    SharedSlotEntry.multiple(listOf(2, 3), SlotMode.LOCK),
                    SharedSlotEntry.single(4, SlotMode.SYNC)
                )
            )

            val lockSlots = config.getSlotsWithMode(SlotMode.LOCK)

            assertEquals(2, lockSlots.size)
            assertTrue(lockSlots.containsAll(listOf(2, 3)))
        }

        @Test
        @DisplayName("should get all slots with SYNC mode")
        fun getSyncSlots() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.multiple(listOf(0, 1, 2), SlotMode.SYNC)
                )
            )

            val syncSlots = config.getSlotsWithMode(SlotMode.SYNC)

            assertEquals(3, syncSlots.size)
        }
    }

    // ============================================================
    // Config Building Tests
    // ============================================================

    @Nested
    @DisplayName("Config Building")
    inner class ConfigBuildingTests {

        @Test
        @DisplayName("should build config from slot map")
        fun buildConfigFromSlotMap() {
            // Simulate how the GUI builds config from slot modes
            val slotModes = mapOf(
                0 to SlotMode.PRESERVE,
                1 to SlotMode.PRESERVE,
                5 to SlotMode.LOCK,
                40 to SlotMode.SYNC
            )

            // Group slots by mode
            val slotsByMode = slotModes.entries.groupBy({ it.value }, { it.key })

            val entries = slotsByMode.map { (mode, slots) ->
                SharedSlotEntry(slots = slots.sorted(), mode = mode)
            }

            val config = SharedSlotsConfig(enabled = true, slots = entries)

            assertEquals(3, config.slots.size)
            assertTrue(config.isSharedSlot(0))
            assertTrue(config.isSharedSlot(1))
            assertTrue(config.isSharedSlot(5))
            assertTrue(config.isSharedSlot(40))
            assertFalse(config.isSharedSlot(2))
        }

        @Test
        @DisplayName("should get all shared slots")
        fun getAllSharedSlots() {
            val config = SharedSlotsConfig(
                enabled = true,
                slots = listOf(
                    SharedSlotEntry.multiple(listOf(0, 1, 2)),
                    SharedSlotEntry.allArmor()
                )
            )

            val allShared = config.getAllSharedSlots()

            assertEquals(7, allShared.size) // 3 + 4
            assertTrue(allShared.contains(0))
            assertTrue(allShared.contains(36))
        }
    }

    // ============================================================
    // Default Config Tests
    // ============================================================

    @Nested
    @DisplayName("Default Configurations")
    inner class DefaultConfigTests {

        @Test
        @DisplayName("should create disabled config")
        fun createDisabledConfig() {
            val config = SharedSlotsConfig.disabled()

            assertFalse(config.enabled)
            assertTrue(config.slots.isEmpty())
        }

        @Test
        @DisplayName("should create default config with compass")
        fun createDefaultConfig() {
            val config = SharedSlotsConfig.withDefaults()

            assertTrue(config.enabled)
            assertEquals(1, config.slots.size)
            assertTrue(config.isSharedSlot(8)) // Hotbar slot 9 (index 8)
        }
    }
}

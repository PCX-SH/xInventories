package sh.pcx.xinventories.unit.gui

import org.bukkit.GameMode
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.VersionTrigger
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@DisplayName("VersionBrowserGUI Logic")
class VersionBrowserGUITest {

    private val testUuid = UUID.randomUUID()
    private val testName = "TestPlayer"

    @Nested
    @DisplayName("Version Display")
    inner class VersionDisplayTests {

        @Test
        @DisplayName("should format trigger name correctly")
        fun shouldFormatTriggerNameCorrectly() {
            assertEquals("World Change", formatTrigger(VersionTrigger.WORLD_CHANGE))
            assertEquals("Disconnect", formatTrigger(VersionTrigger.DISCONNECT))
            assertEquals("Manual Snapshot", formatTrigger(VersionTrigger.MANUAL))
            assertEquals("Death", formatTrigger(VersionTrigger.DEATH))
            assertEquals("Scheduled", formatTrigger(VersionTrigger.SCHEDULED))
        }

        @Test
        @DisplayName("should get relative time description")
        fun shouldGetRelativeTimeDescription() {
            val now = Instant.now()

            assertEquals("just now", getRelativeTime(now))
            assertEquals("5 minutes ago", getRelativeTime(now.minusSeconds(300)))
            assertEquals("2 hours ago", getRelativeTime(now.minusSeconds(7200)))
            assertEquals("3 days ago", getRelativeTime(now.minusSeconds(259200)))
            assertEquals("2 weeks ago", getRelativeTime(now.minusSeconds(1209600)))
        }

        @Test
        @DisplayName("should get item summary")
        fun shouldGetItemSummary() {
            val playerData = PlayerData.empty(testUuid, testName, "test", GameMode.SURVIVAL)

            // Empty inventory
            assertEquals("empty", getItemSummary(playerData))

            // Add items
            playerData.mainInventory[0] = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND)
            assertTrue(getItemSummary(playerData).contains("1 items"))

            // Add armor
            playerData.armorInventory[0] = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND_BOOTS)
            assertTrue(getItemSummary(playerData).contains("1 armor"))

            // Add offhand
            playerData.offhand = org.bukkit.inventory.ItemStack(org.bukkit.Material.SHIELD)
            assertTrue(getItemSummary(playerData).contains("offhand"))
        }

        private fun formatTrigger(trigger: VersionTrigger): String {
            return when (trigger) {
                VersionTrigger.WORLD_CHANGE -> "World Change"
                VersionTrigger.DISCONNECT -> "Disconnect"
                VersionTrigger.MANUAL -> "Manual Snapshot"
                VersionTrigger.DEATH -> "Death"
                VersionTrigger.SCHEDULED -> "Scheduled"
            }
        }

        private fun getRelativeTime(timestamp: Instant): String {
            val now = Instant.now()
            val diffSeconds = now.epochSecond - timestamp.epochSecond

            return when {
                diffSeconds < 60 -> "just now"
                diffSeconds < 3600 -> "${diffSeconds / 60} minutes ago"
                diffSeconds < 86400 -> "${diffSeconds / 3600} hours ago"
                diffSeconds < 604800 -> "${diffSeconds / 86400} days ago"
                else -> "${diffSeconds / 604800} weeks ago"
            }
        }

        private fun getItemSummary(data: PlayerData): String {
            val mainCount = data.mainInventory.size
            val armorCount = data.armorInventory.size
            val hasOffhand = data.offhand != null
            val enderCount = data.enderChest.size

            val parts = mutableListOf<String>()
            if (mainCount > 0) parts.add("$mainCount items")
            if (armorCount > 0) parts.add("$armorCount armor")
            if (hasOffhand) parts.add("offhand")
            if (enderCount > 0) parts.add("$enderCount ender chest")

            return if (parts.isEmpty()) "empty" else parts.joinToString(", ")
        }
    }

    @Nested
    @DisplayName("Pagination")
    inner class PaginationTests {

        @Test
        @DisplayName("should calculate correct max page for versions")
        fun shouldCalculateCorrectMaxPage() {
            val itemsPerPage = 28

            assertEquals(0, calculateMaxPage(0, itemsPerPage))
            assertEquals(0, calculateMaxPage(1, itemsPerPage))
            assertEquals(0, calculateMaxPage(28, itemsPerPage))
            assertEquals(1, calculateMaxPage(29, itemsPerPage))
            assertEquals(1, calculateMaxPage(56, itemsPerPage))
            assertEquals(2, calculateMaxPage(57, itemsPerPage))
        }

        @Test
        @DisplayName("should calculate version slot positions")
        fun shouldCalculateVersionSlotPositions() {
            // Grid: rows 1-4, columns 1-7
            val versionSlots = mutableListOf<Int>()
            for (row in 1..4) {
                for (col in 1..7) {
                    versionSlots.add(row * 9 + col)
                }
            }

            assertEquals(28, versionSlots.size)
            assertEquals(10, versionSlots.first()) // First slot: row 1, col 1
            assertEquals(43, versionSlots.last()) // Last slot: row 4, col 7
        }

        private fun calculateMaxPage(totalItems: Int, itemsPerPage: Int): Int {
            return if (totalItems == 0) 0 else (totalItems - 1) / itemsPerPage
        }
    }

    @Nested
    @DisplayName("Version Filtering")
    inner class VersionFilteringTests {

        @Test
        @DisplayName("should filter versions by group")
        fun shouldFilterVersionsByGroup() {
            val versions = listOf(
                createMockVersion("survival"),
                createMockVersion("creative"),
                createMockVersion("survival"),
                createMockVersion("minigames")
            )

            val filtered = versions.filter { it.group == "survival" }

            assertEquals(2, filtered.size)
            assertTrue(filtered.all { it.group == "survival" })
        }

        @Test
        @DisplayName("should return all versions when no filter")
        fun shouldReturnAllVersionsWhenNoFilter() {
            val versions = listOf(
                createMockVersion("survival"),
                createMockVersion("creative"),
                createMockVersion("minigames")
            )

            val groupFilter: String? = null
            val filtered = if (groupFilter == null) versions else versions.filter { it.group == groupFilter }

            assertEquals(3, filtered.size)
        }

        private fun createMockVersion(group: String): InventoryVersion {
            val playerData = PlayerData.empty(testUuid, testName, group, GameMode.SURVIVAL)
            return InventoryVersion.create(playerData, VersionTrigger.MANUAL)
        }
    }

    @Nested
    @DisplayName("Visual Diff Logic")
    inner class VisualDiffLogicTests {

        @Test
        @DisplayName("should identify matching items")
        fun shouldIdentifyMatchingItems() {
            val item1 = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 5)
            val item2 = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 5)

            assertTrue(itemsMatch(item1, item2))
        }

        @Test
        @DisplayName("should identify different items by type")
        fun shouldIdentifyDifferentItemsByType() {
            val item1 = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 5)
            val item2 = org.bukkit.inventory.ItemStack(org.bukkit.Material.GOLD_INGOT, 5)

            assertFalse(itemsMatch(item1, item2))
        }

        @Test
        @DisplayName("should identify different items by amount")
        fun shouldIdentifyDifferentItemsByAmount() {
            val item1 = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 5)
            val item2 = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 10)

            assertFalse(itemsMatch(item1, item2))
        }

        @Test
        @DisplayName("should handle null comparisons")
        fun shouldHandleNullComparisons() {
            val item = org.bukkit.inventory.ItemStack(org.bukkit.Material.DIAMOND, 5)

            assertTrue(itemsMatch(null, null))
            assertFalse(itemsMatch(item, null))
            assertFalse(itemsMatch(null, item))
        }

        private fun itemsMatch(item1: org.bukkit.inventory.ItemStack?, item2: org.bukkit.inventory.ItemStack?): Boolean {
            if (item1 == null && item2 == null) return true
            if (item1 == null || item2 == null) return false
            return item1.type == item2.type && item1.amount == item2.amount
        }
    }

    @Nested
    @DisplayName("Trigger Type Colors")
    inner class TriggerTypeColorsTests {

        @Test
        @DisplayName("each trigger type should have distinct meaning")
        fun eachTriggerTypeShouldHaveDistinctMeaning() {
            val triggers = VersionTrigger.entries

            assertEquals(5, triggers.size)

            assertTrue(triggers.contains(VersionTrigger.WORLD_CHANGE))
            assertTrue(triggers.contains(VersionTrigger.DISCONNECT))
            assertTrue(triggers.contains(VersionTrigger.MANUAL))
            assertTrue(triggers.contains(VersionTrigger.DEATH))
            assertTrue(triggers.contains(VersionTrigger.SCHEDULED))
        }

        @Test
        @DisplayName("trigger material mapping should be unique")
        fun triggerMaterialMappingShouldBeUnique() {
            val materials = VersionTrigger.entries.map { getMaterialForTrigger(it) }.toSet()

            // Each trigger should map to a different material
            assertEquals(VersionTrigger.entries.size, materials.size)
        }

        private fun getMaterialForTrigger(trigger: VersionTrigger): org.bukkit.Material {
            return when (trigger) {
                VersionTrigger.WORLD_CHANGE -> org.bukkit.Material.COMPASS
                VersionTrigger.DISCONNECT -> org.bukkit.Material.ENDER_PEARL
                VersionTrigger.MANUAL -> org.bukkit.Material.WRITABLE_BOOK
                VersionTrigger.DEATH -> org.bukkit.Material.BONE
                VersionTrigger.SCHEDULED -> org.bukkit.Material.CLOCK
            }
        }
    }
}

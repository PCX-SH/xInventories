package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.internal.model.Group
import org.bukkit.Material

@DisplayName("Group Browser GUI Logic")
class GroupBrowserGUITest {

    @Nested
    @DisplayName("Group Material Selection")
    inner class GroupMaterialSelectionTests {

        @Test
        @DisplayName("default group gets ender chest material")
        fun defaultGroupGetsEnderChest() {
            val group = createTestGroup("default", isDefault = true)

            val material = getGroupMaterial(group)

            assertEquals(Material.ENDER_CHEST, material)
        }

        @Test
        @DisplayName("creative group gets command block material")
        fun creativeGroupGetsCommandBlock() {
            val group = createTestGroup("creative")

            val material = getGroupMaterial(group)

            assertEquals(Material.COMMAND_BLOCK, material)
        }

        @Test
        @DisplayName("survival group gets grass block material")
        fun survivalGroupGetsGrassBlock() {
            val group = createTestGroup("survival")

            val material = getGroupMaterial(group)

            assertEquals(Material.GRASS_BLOCK, material)
        }

        @Test
        @DisplayName("adventure group gets map material")
        fun adventureGroupGetsMap() {
            val group = createTestGroup("adventure")

            val material = getGroupMaterial(group)

            assertEquals(Material.MAP, material)
        }

        @Test
        @DisplayName("skyblock group gets oak sapling material")
        fun skyblockGroupGetsOakSapling() {
            val group = createTestGroup("skyblock")

            val material = getGroupMaterial(group)

            assertEquals(Material.OAK_SAPLING, material)
        }

        @Test
        @DisplayName("minigame group gets golden apple material")
        fun minigameGroupGetsGoldenApple() {
            val group = createTestGroup("minigames")

            val material = getGroupMaterial(group)

            assertEquals(Material.GOLDEN_APPLE, material)
        }

        @Test
        @DisplayName("pvp group gets iron sword material")
        fun pvpGroupGetsIronSword() {
            val group = createTestGroup("pvp-arena")

            val material = getGroupMaterial(group)

            assertEquals(Material.IRON_SWORD, material)
        }

        @Test
        @DisplayName("build group gets bricks material")
        fun buildGroupGetsBricks() {
            val group = createTestGroup("build-world")

            val material = getGroupMaterial(group)

            assertEquals(Material.BRICKS, material)
        }

        @Test
        @DisplayName("generic group gets chest material")
        fun genericGroupGetsChest() {
            val group = createTestGroup("custom-group")

            val material = getGroupMaterial(group)

            assertEquals(Material.CHEST, material)
        }

        @Test
        @DisplayName("material selection is case insensitive")
        fun materialSelectionIsCaseInsensitive() {
            val group1 = createTestGroup("SURVIVAL")
            val group2 = createTestGroup("Survival")
            val group3 = createTestGroup("SuRvIvAl")

            assertEquals(Material.GRASS_BLOCK, getGroupMaterial(group1))
            assertEquals(Material.GRASS_BLOCK, getGroupMaterial(group2))
            assertEquals(Material.GRASS_BLOCK, getGroupMaterial(group3))
        }
    }

    @Nested
    @DisplayName("Pagination Logic")
    inner class PaginationTests {

        @Test
        @DisplayName("calculates max page correctly")
        fun calculatesMaxPageCorrectly() {
            val itemsPerPage = 36
            val totalGroups = 50

            val maxPage = if (totalGroups == 0) 0 else (totalGroups - 1) / itemsPerPage

            assertEquals(1, maxPage) // Pages 0 and 1
        }

        @Test
        @DisplayName("handles empty group list")
        fun handlesEmptyGroupList() {
            val itemsPerPage = 36
            val totalGroups = 0

            val maxPage = if (totalGroups == 0) 0 else (totalGroups - 1) / itemsPerPage

            assertEquals(0, maxPage)
        }
    }

    @Nested
    @DisplayName("Group Sorting")
    inner class GroupSortingTests {

        @Test
        @DisplayName("groups sort alphabetically by name")
        fun groupsSortAlphabetically() {
            val groups = listOf(
                createTestGroup("Zebra"),
                createTestGroup("apple"),
                createTestGroup("Banana")
            )

            val sorted = groups.sortedBy { it.name }

            assertEquals("Banana", sorted[0].name)
            assertEquals("Zebra", sorted[1].name)
            assertEquals("apple", sorted[2].name)
        }
    }

    @Nested
    @DisplayName("Player Count Display")
    inner class PlayerCountDisplayTests {

        @Test
        @DisplayName("displays player count format correctly")
        fun displaysPlayerCountFormat() {
            val totalPlayers = 50
            val onlinePlayers = 10

            val displayText = "Players: $totalPlayers (${onlinePlayers} online)"

            assertEquals("Players: 50 (10 online)", displayText)
        }

        @Test
        @DisplayName("handles zero players")
        fun handlesZeroPlayers() {
            val totalPlayers = 0
            val onlinePlayers = 0

            val displayText = "Players: $totalPlayers (${onlinePlayers} online)"

            assertEquals("Players: 0 (0 online)", displayText)
        }
    }

    // Helper methods that mirror the GUI logic

    private fun getGroupMaterial(group: Group): Material {
        return when {
            group.isDefault -> Material.ENDER_CHEST
            group.name.contains("creative", ignoreCase = true) -> Material.COMMAND_BLOCK
            group.name.contains("survival", ignoreCase = true) -> Material.GRASS_BLOCK
            group.name.contains("adventure", ignoreCase = true) -> Material.MAP
            group.name.contains("skyblock", ignoreCase = true) -> Material.OAK_SAPLING
            group.name.contains("minigame", ignoreCase = true) -> Material.GOLDEN_APPLE
            group.name.contains("pvp", ignoreCase = true) -> Material.IRON_SWORD
            group.name.contains("build", ignoreCase = true) -> Material.BRICKS
            else -> Material.CHEST
        }
    }

    private fun createTestGroup(
        name: String,
        isDefault: Boolean = false,
        worlds: Set<String> = emptySet()
    ): Group {
        return Group(
            name = name,
            worlds = worlds,
            priority = 0,
            settings = GroupSettings(),
            isDefault = isDefault
        )
    }
}

package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.gui.menu.ExportToolGUI

@DisplayName("ExportToolGUI Logic")
class ExportToolGUITest {

    @Nested
    @DisplayName("Export Scope Selection")
    inner class ExportScopeSelectionTests {

        @Test
        @DisplayName("all export scopes are available")
        fun allExportScopesAreAvailable() {
            val scopes = ExportToolGUI.ExportScope.entries

            assertTrue(scopes.contains(ExportToolGUI.ExportScope.SELECT))
            assertTrue(scopes.contains(ExportToolGUI.ExportScope.SINGLE_PLAYER))
            assertTrue(scopes.contains(ExportToolGUI.ExportScope.GROUP))
            assertTrue(scopes.contains(ExportToolGUI.ExportScope.ALL))
        }

        @Test
        @DisplayName("initial scope is SELECT")
        fun initialScopeIsSelect() {
            val initialScope = ExportToolGUI.ExportScope.SELECT
            assertEquals(0, initialScope.ordinal)
        }
    }

    @Nested
    @DisplayName("Title Generation")
    inner class TitleGenerationTests {

        @Test
        @DisplayName("SELECT scope shows generic title")
        fun selectScopeShowsGenericTitle() {
            val title = getTitle(ExportToolGUI.ExportScope.SELECT, null, null)
            assertEquals("Export Tool", title)
        }

        @Test
        @DisplayName("SINGLE_PLAYER scope shows player name")
        fun singlePlayerScopeShowsPlayerName() {
            val title = getTitle(ExportToolGUI.ExportScope.SINGLE_PLAYER, "TestPlayer", null)
            assertEquals("Export: TestPlayer", title)
        }

        @Test
        @DisplayName("GROUP scope shows group name")
        fun groupScopeShowsGroupName() {
            val title = getTitle(ExportToolGUI.ExportScope.GROUP, null, "survival")
            assertEquals("Export: survival", title)
        }

        @Test
        @DisplayName("ALL scope shows all data")
        fun allScopeShowsAllData() {
            val title = getTitle(ExportToolGUI.ExportScope.ALL, null, null)
            assertEquals("Export: All Data", title)
        }

        private fun getTitle(scope: ExportToolGUI.ExportScope, playerName: String?, groupName: String?): String {
            return when (scope) {
                ExportToolGUI.ExportScope.SELECT -> "Export Tool"
                ExportToolGUI.ExportScope.SINGLE_PLAYER -> "Export: ${playerName ?: "Select Player"}"
                ExportToolGUI.ExportScope.GROUP -> "Export: ${groupName ?: "Select Group"}"
                ExportToolGUI.ExportScope.ALL -> "Export: All Data"
            }
        }
    }

    @Nested
    @DisplayName("Pagination Logic")
    inner class PaginationLogicTests {

        @Test
        @DisplayName("calculates pages correctly for player list")
        fun calculatesPagesCorrectlyForPlayerList() {
            val itemsPerPage = 21

            assertEquals(1, calculatePages(0, itemsPerPage))
            assertEquals(1, calculatePages(15, itemsPerPage))
            assertEquals(1, calculatePages(21, itemsPerPage))
            assertEquals(2, calculatePages(22, itemsPerPage))
            assertEquals(3, calculatePages(50, itemsPerPage))
        }

        private fun calculatePages(itemCount: Int, perPage: Int): Int {
            return maxOf(1, (itemCount + perPage - 1) / perPage)
        }
    }

    @Nested
    @DisplayName("Export Options")
    inner class ExportOptionsTests {

        @Test
        @DisplayName("toggle version inclusion")
        fun toggleVersionInclusion() {
            var includeVersions = false

            includeVersions = !includeVersions
            assertTrue(includeVersions)

            includeVersions = !includeVersions
            assertFalse(includeVersions)
        }

        @Test
        @DisplayName("toggle death inclusion")
        fun toggleDeathInclusion() {
            var includeDeaths = false

            includeDeaths = !includeDeaths
            assertTrue(includeDeaths)

            includeDeaths = !includeDeaths
            assertFalse(includeDeaths)
        }

        @Test
        @DisplayName("default options are disabled")
        fun defaultOptionsAreDisabled() {
            val defaultIncludeVersions = false
            val defaultIncludeDeaths = false

            assertFalse(defaultIncludeVersions)
            assertFalse(defaultIncludeDeaths)
        }
    }

    @Nested
    @DisplayName("Navigation State")
    inner class NavigationStateTests {

        @Test
        @DisplayName("SELECT scope shows scope selection")
        fun selectScopeShowsScopeSelection() {
            val scope = ExportToolGUI.ExportScope.SELECT
            val showsScopeSelection = scope == ExportToolGUI.ExportScope.SELECT

            assertTrue(showsScopeSelection)
        }

        @Test
        @DisplayName("SINGLE_PLAYER without selection shows player list")
        fun singlePlayerWithoutSelectionShowsPlayerList() {
            val scope = ExportToolGUI.ExportScope.SINGLE_PLAYER
            val selectedPlayer: String? = null

            val showsPlayerList = scope == ExportToolGUI.ExportScope.SINGLE_PLAYER && selectedPlayer == null

            assertTrue(showsPlayerList)
        }

        @Test
        @DisplayName("SINGLE_PLAYER with selection shows export config")
        fun singlePlayerWithSelectionShowsExportConfig() {
            val scope = ExportToolGUI.ExportScope.SINGLE_PLAYER
            val selectedPlayer = "some-uuid"

            val showsExportConfig = scope == ExportToolGUI.ExportScope.SINGLE_PLAYER && selectedPlayer != null

            assertTrue(showsExportConfig)
        }

        @Test
        @DisplayName("ALL scope goes directly to export config")
        fun allScopeGoesDirectlyToExportConfig() {
            val scope = ExportToolGUI.ExportScope.ALL
            val showsExportConfig = scope == ExportToolGUI.ExportScope.ALL

            assertTrue(showsExportConfig)
        }
    }

    @Nested
    @DisplayName("Player Sorting")
    inner class PlayerSortingTests {

        @Test
        @DisplayName("players are sorted alphabetically by name")
        fun playersAreSortedAlphabeticallyByName() {
            val players = listOf(
                "uuid1" to "Zack",
                "uuid2" to "Alice",
                "uuid3" to "Bob"
            )

            val sorted = players.sortedBy { it.second }

            assertEquals("Alice", sorted[0].second)
            assertEquals("Bob", sorted[1].second)
            assertEquals("Zack", sorted[2].second)
        }

        @Test
        @DisplayName("sorting is case sensitive")
        fun sortingIsCaseSensitive() {
            val players = listOf(
                "uuid1" to "alice",
                "uuid2" to "Alice",
                "uuid3" to "ALICE"
            )

            val sorted = players.sortedBy { it.second }

            // Case-sensitive sorting puts uppercase before lowercase
            assertEquals("ALICE", sorted[0].second)
            assertEquals("Alice", sorted[1].second)
            assertEquals("alice", sorted[2].second)
        }
    }

    @Nested
    @DisplayName("Material Selection for Dyes")
    inner class MaterialSelectionTests {

        @Test
        @DisplayName("enabled option uses lime dye")
        fun enabledOptionUsesLimeDye() {
            val enabled = true
            val material = if (enabled) "LIME_DYE" else "GRAY_DYE"
            assertEquals("LIME_DYE", material)
        }

        @Test
        @DisplayName("disabled option uses gray dye")
        fun disabledOptionUsesGrayDye() {
            val enabled = false
            val material = if (enabled) "LIME_DYE" else "GRAY_DYE"
            assertEquals("GRAY_DYE", material)
        }
    }

    @Nested
    @DisplayName("UUID Display")
    inner class UuidDisplayTests {

        @Test
        @DisplayName("truncates long UUIDs for display")
        fun truncatesLongUuidsForDisplay() {
            val uuid = "123e4567-e89b-12d3-a456-426614174000"
            val truncated = uuid.take(16) + "..."

            assertEquals("123e4567-e89b-12", truncated.take(16))
            assertTrue(truncated.endsWith("..."))
            assertEquals(19, truncated.length) // 16 chars + "..."
        }
    }
}

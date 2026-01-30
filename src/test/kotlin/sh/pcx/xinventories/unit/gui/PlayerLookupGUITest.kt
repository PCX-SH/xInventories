package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.internal.gui.menu.PlayerLookupGUI
import java.util.UUID

@DisplayName("PlayerLookupGUI Logic")
class PlayerLookupGUITest {

    private lateinit var server: ServerMock

    @BeforeEach
    fun setUpServer() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDownServer() {
        MockBukkit.unmock()
    }

    @Nested
    @DisplayName("Recent Players Tracking")
    inner class RecentPlayersTests {

        private val adminUuid = UUID.randomUUID()

        @BeforeEach
        fun clearRecentPlayers() {
            // Clear recent players by recording 10+ different ones
            repeat(15) {
                PlayerLookupGUI.recordRecentPlayer(
                    adminUuid,
                    UUID.randomUUID(),
                    "ClearPlayer$it"
                )
            }
        }

        @Test
        @DisplayName("should record recent player")
        fun shouldRecordRecentPlayer() {
            val playerUuid = UUID.randomUUID()
            val playerName = "TestPlayer"

            PlayerLookupGUI.recordRecentPlayer(adminUuid, playerUuid, playerName)

            val recent = PlayerLookupGUI.getRecentPlayers(adminUuid)
            assertTrue(recent.any { it.uuid == playerUuid })
            assertTrue(recent.any { it.name == playerName })
        }

        @Test
        @DisplayName("should maintain max of 10 recent players")
        fun shouldMaintainMaxRecentPlayers() {
            // Record 15 players
            repeat(15) { i ->
                PlayerLookupGUI.recordRecentPlayer(
                    adminUuid,
                    UUID.randomUUID(),
                    "Player$i"
                )
            }

            val recent = PlayerLookupGUI.getRecentPlayers(adminUuid)
            assertTrue(recent.size <= 10)
        }

        @Test
        @DisplayName("should move player to front when re-recorded")
        fun shouldMovePlayerToFrontWhenReRecorded() {
            val firstPlayerUuid = UUID.randomUUID()
            val firstPlayerName = "FirstPlayer"

            // Record first player
            PlayerLookupGUI.recordRecentPlayer(adminUuid, firstPlayerUuid, firstPlayerName)

            // Record other players
            repeat(5) { i ->
                PlayerLookupGUI.recordRecentPlayer(
                    adminUuid,
                    UUID.randomUUID(),
                    "OtherPlayer$i"
                )
            }

            // Re-record first player
            PlayerLookupGUI.recordRecentPlayer(adminUuid, firstPlayerUuid, firstPlayerName)

            val recent = PlayerLookupGUI.getRecentPlayers(adminUuid)
            assertEquals(firstPlayerUuid, recent.first().uuid)
        }

        @Test
        @DisplayName("should return empty list for unknown admin")
        fun shouldReturnEmptyListForUnknownAdmin() {
            val unknownAdmin = UUID.randomUUID()
            val recent = PlayerLookupGUI.getRecentPlayers(unknownAdmin)
            assertTrue(recent.isEmpty())
        }

        @Test
        @DisplayName("should track recent players per admin independently")
        fun shouldTrackPerAdminIndependently() {
            val admin1 = UUID.randomUUID()
            val admin2 = UUID.randomUUID()

            val player1 = UUID.randomUUID()
            val player2 = UUID.randomUUID()

            PlayerLookupGUI.recordRecentPlayer(admin1, player1, "Player1")
            PlayerLookupGUI.recordRecentPlayer(admin2, player2, "Player2")

            val recent1 = PlayerLookupGUI.getRecentPlayers(admin1)
            val recent2 = PlayerLookupGUI.getRecentPlayers(admin2)

            assertTrue(recent1.any { it.uuid == player1 })
            assertFalse(recent1.any { it.uuid == player2 })

            assertTrue(recent2.any { it.uuid == player2 })
            assertFalse(recent2.any { it.uuid == player1 })
        }
    }

    @Nested
    @DisplayName("Player Entry")
    inner class PlayerEntryTests {

        @Test
        @DisplayName("should create player entry correctly")
        fun shouldCreatePlayerEntryCorrectly() {
            val uuid = UUID.randomUUID()
            val name = "TestPlayer"
            val online = true

            val entry = PlayerLookupGUI.PlayerEntry(uuid, name, online)

            assertEquals(uuid, entry.uuid)
            assertEquals(name, entry.name)
            assertTrue(entry.online)
        }

        @Test
        @DisplayName("should copy player entry with updated online status")
        fun shouldCopyPlayerEntryWithUpdatedStatus() {
            val entry = PlayerLookupGUI.PlayerEntry(
                UUID.randomUUID(),
                "TestPlayer",
                false
            )

            val updatedEntry = entry.copy(online = true)

            assertEquals(entry.uuid, updatedEntry.uuid)
            assertEquals(entry.name, updatedEntry.name)
            assertTrue(updatedEntry.online)
        }
    }

    @Nested
    @DisplayName("Search Logic")
    inner class SearchLogicTests {

        @Test
        @DisplayName("should match name prefix case insensitively")
        fun shouldMatchNamePrefixCaseInsensitively() {
            val query = "test"
            val names = listOf("TestPlayer", "TESTING", "testuser", "OtherPlayer", "ATest")

            val matches = names.filter { it.lowercase().startsWith(query.lowercase()) }

            assertEquals(3, matches.size)
            assertTrue(matches.contains("TestPlayer"))
            assertTrue(matches.contains("TESTING"))
            assertTrue(matches.contains("testuser"))
            assertFalse(matches.contains("ATest")) // Doesn't START with "test"
        }

        @Test
        @DisplayName("should sort by online status then name")
        fun shouldSortByOnlineThenName() {
            val entries = listOf(
                PlayerLookupGUI.PlayerEntry(UUID.randomUUID(), "Zach", false),
                PlayerLookupGUI.PlayerEntry(UUID.randomUUID(), "Adam", true),
                PlayerLookupGUI.PlayerEntry(UUID.randomUUID(), "Bob", false),
                PlayerLookupGUI.PlayerEntry(UUID.randomUUID(), "Charlie", true)
            )

            val sorted = entries.sortedWith(
                compareByDescending<PlayerLookupGUI.PlayerEntry> { it.online }
                    .thenBy { it.name }
            )

            // Online players first, then alphabetically
            assertEquals("Adam", sorted[0].name)
            assertEquals("Charlie", sorted[1].name)
            assertEquals("Bob", sorted[2].name)
            assertEquals("Zach", sorted[3].name)
        }
    }

    @Nested
    @DisplayName("Pagination")
    inner class PaginationTests {

        @Test
        @DisplayName("should calculate correct max page")
        fun shouldCalculateCorrectMaxPage() {
            val itemsPerPage = 21

            assertEquals(0, calculateMaxPage(0, itemsPerPage))
            assertEquals(0, calculateMaxPage(1, itemsPerPage))
            assertEquals(0, calculateMaxPage(21, itemsPerPage))
            assertEquals(1, calculateMaxPage(22, itemsPerPage))
            assertEquals(1, calculateMaxPage(42, itemsPerPage))
            assertEquals(2, calculateMaxPage(43, itemsPerPage))
        }

        @Test
        @DisplayName("should calculate correct page bounds")
        fun shouldCalculateCorrectPageBounds() {
            val itemsPerPage = 21
            val totalItems = 50
            val page = 1

            val startIndex = page * itemsPerPage
            val endIndex = minOf(startIndex + itemsPerPage, totalItems)

            assertEquals(21, startIndex)
            assertEquals(42, endIndex)
        }

        private fun calculateMaxPage(totalItems: Int, itemsPerPage: Int): Int {
            return if (totalItems == 0) 0 else (totalItems - 1) / itemsPerPage
        }
    }
}

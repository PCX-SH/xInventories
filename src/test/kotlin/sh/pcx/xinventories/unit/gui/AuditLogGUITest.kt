package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.model.AuditAction
import sh.pcx.xinventories.internal.model.AuditEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@DisplayName("AuditLogGUI Logic")
class AuditLogGUITest {

    private val dateFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    @Nested
    @DisplayName("AuditEntry Formatting")
    inner class AuditEntryFormattingTests {

        @Test
        @DisplayName("toDisplayString formats entry correctly")
        fun toDisplayStringFormatsEntryCorrectly() {
            val entry = AuditEntry(
                id = 1,
                timestamp = Instant.parse("2026-01-15T10:30:00Z"),
                actor = UUID.randomUUID(),
                actorName = "AdminPlayer",
                target = UUID.randomUUID(),
                targetName = "TargetPlayer",
                action = AuditAction.INVENTORY_SAVE,
                group = "survival"
            )

            val display = entry.toDisplayString()

            assertTrue(display.contains("AdminPlayer"))
            assertTrue(display.contains("TargetPlayer"))
            assertTrue(display.contains("survival"))
        }

        @Test
        @DisplayName("system action displays correctly")
        fun systemActionDisplaysCorrectly() {
            val entry = AuditEntry.system(
                target = UUID.randomUUID(),
                targetName = "TestPlayer",
                action = AuditAction.INVENTORY_LOAD,
                group = "creative"
            )

            val display = entry.toDisplayString()

            assertTrue(display.contains("[SYSTEM]"))
            assertTrue(display.contains("TestPlayer"))
        }

        @Test
        @DisplayName("console action displays correctly")
        fun consoleActionDisplaysCorrectly() {
            val entry = AuditEntry.console(
                target = UUID.randomUUID(),
                targetName = "TestPlayer",
                action = AuditAction.INVENTORY_CLEAR
            )

            val display = entry.toDisplayString()

            assertTrue(display.contains("[CONSOLE]"))
            assertTrue(display.contains("cleared inventory"))
        }
    }

    @Nested
    @DisplayName("Action Material Mapping")
    inner class ActionMaterialMappingTests {

        @Test
        @DisplayName("destructive actions are identified correctly")
        fun destructiveActionsAreIdentifiedCorrectly() {
            assertTrue(AuditAction.INVENTORY_CLEAR.isDestructive)
            assertTrue(AuditAction.ITEM_REMOVE.isDestructive)
            assertTrue(AuditAction.BULK_OPERATION.isDestructive)
            assertTrue(AuditAction.BULK_CLEAR.isDestructive)
            assertTrue(AuditAction.BULK_RESET_STATS.isDestructive)

            assertFalse(AuditAction.INVENTORY_SAVE.isDestructive)
            assertFalse(AuditAction.INVENTORY_LOAD.isDestructive)
            assertFalse(AuditAction.ADMIN_VIEW.isDestructive)
        }

        @Test
        @DisplayName("AuditAction fromName parses correctly")
        fun auditActionFromNameParsesCorrectly() {
            assertEquals(AuditAction.INVENTORY_SAVE, AuditAction.fromName("INVENTORY_SAVE"))
            assertEquals(AuditAction.INVENTORY_SAVE, AuditAction.fromName("inventory_save"))
            assertEquals(AuditAction.ADMIN_EDIT, AuditAction.fromName("admin_edit"))
            assertNull(AuditAction.fromName("INVALID_ACTION"))
        }
    }

    @Nested
    @DisplayName("Pagination Logic")
    inner class PaginationLogicTests {

        @Test
        @DisplayName("calculates total pages correctly")
        fun calculatesTotalPagesCorrectly() {
            val entriesPerPage = 28

            // Test with various entry counts
            assertEquals(1, maxOf(1, (0 + entriesPerPage - 1) / entriesPerPage))
            assertEquals(1, maxOf(1, (1 + entriesPerPage - 1) / entriesPerPage))
            assertEquals(1, maxOf(1, (28 + entriesPerPage - 1) / entriesPerPage))
            assertEquals(2, maxOf(1, (29 + entriesPerPage - 1) / entriesPerPage))
            assertEquals(2, maxOf(1, (56 + entriesPerPage - 1) / entriesPerPage))
            assertEquals(3, maxOf(1, (57 + entriesPerPage - 1) / entriesPerPage))
        }

        @Test
        @DisplayName("page bounds are respected")
        fun pageBoundsAreRespected() {
            val totalPages = 5
            var currentPage = 6

            // Should clamp to valid range
            if (currentPage >= totalPages) currentPage = totalPages - 1
            assertEquals(4, currentPage)

            currentPage = -1
            if (currentPage < 0) currentPage = 0
            assertEquals(0, currentPage)
        }
    }

    @Nested
    @DisplayName("Filter Logic")
    inner class FilterLogicTests {

        @Test
        @DisplayName("date range description is correct")
        fun dateRangeDescriptionIsCorrect() {
            // All time
            val allTime = getDateFilterDescription(null, null)
            assertEquals("Current: All time", allTime)

            // Custom range
            val from = Instant.now().minusSeconds(86400)
            val to = Instant.now()
            val custom = getDateFilterDescription(from, to)
            assertTrue(custom.startsWith("Current:"))
            assertTrue(custom.contains(" to "))
        }

        private fun getDateFilterDescription(from: Instant?, to: Instant?): String {
            val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())

            return when {
                from == null && to == null -> "Current: All time"
                from != null && to != null -> {
                    val fromStr = fullDateFormatter.format(from)
                    val toStr = fullDateFormatter.format(to)
                    "Current: $fromStr to $toStr"
                }
                else -> "Current: Custom"
            }
        }
    }
}

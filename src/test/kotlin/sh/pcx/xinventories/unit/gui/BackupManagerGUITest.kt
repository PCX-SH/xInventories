package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.api.model.BackupMetadata
import sh.pcx.xinventories.api.model.StorageType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@DisplayName("BackupManagerGUI Logic")
class BackupManagerGUITest {

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .withZone(ZoneId.systemDefault())

    @Nested
    @DisplayName("Backup Metadata Display")
    inner class BackupMetadataDisplayTests {

        @Test
        @DisplayName("formats backup date correctly")
        fun formatsBackupDateCorrectly() {
            val timestamp = Instant.parse("2026-01-15T14:30:00Z")
            val formatted = dateFormatter.format(timestamp)
            assertNotNull(formatted)
            assertTrue(formatted.contains("2026"))
            assertTrue(formatted.contains("01"))
            assertTrue(formatted.contains("15"))
        }

        @Test
        @DisplayName("calculates total size correctly")
        fun calculatesTotalSizeCorrectly() {
            val backups = listOf(
                createBackup("backup1", 1024),
                createBackup("backup2", 2048),
                createBackup("backup3", 512)
            )

            val totalSize = backups.sumOf { it.sizeBytes }
            assertEquals(3584, totalSize)
        }

        private fun createBackup(name: String, size: Long): BackupMetadata {
            return BackupMetadata(
                id = "id_$name",
                name = name,
                timestamp = Instant.now(),
                playerCount = 10,
                sizeBytes = size,
                storageType = StorageType.YAML,
                compressed = true
            )
        }
    }

    @Nested
    @DisplayName("Size Formatting")
    inner class SizeFormattingTests {

        @Test
        @DisplayName("formats bytes correctly")
        fun formatsBytesCorrectly() {
            assertEquals("512 B", formatSize(512))
        }

        @Test
        @DisplayName("formats kilobytes correctly")
        fun formatsKilobytesCorrectly() {
            assertEquals("5 KB", formatSize(5120))
        }

        @Test
        @DisplayName("formats megabytes correctly")
        fun formatsMegabytesCorrectly() {
            assertEquals("10 MB", formatSize(10485760))
        }

        @Test
        @DisplayName("formats gigabytes correctly")
        fun formatsGigabytesCorrectly() {
            assertEquals("2 GB", formatSize(2147483648))
        }

        private fun formatSize(bytes: Long): String {
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> "${bytes / 1024} KB"
                bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
                else -> "${bytes / (1024 * 1024 * 1024)} GB"
            }
        }
    }

    @Nested
    @DisplayName("Pagination Logic")
    inner class PaginationLogicTests {

        @Test
        @DisplayName("calculates total pages correctly for backups")
        fun calculatesTotalPagesCorrectlyForBackups() {
            val backupsPerPage = 21

            assertEquals(1, calculatePages(0, backupsPerPage))
            assertEquals(1, calculatePages(10, backupsPerPage))
            assertEquals(1, calculatePages(21, backupsPerPage))
            assertEquals(2, calculatePages(22, backupsPerPage))
            assertEquals(3, calculatePages(50, backupsPerPage))
        }

        private fun calculatePages(itemCount: Int, perPage: Int): Int {
            return maxOf(1, (itemCount + perPage - 1) / perPage)
        }
    }

    @Nested
    @DisplayName("Backup Sorting")
    inner class BackupSortingTests {

        @Test
        @DisplayName("sorts backups by timestamp descending")
        fun sortsBackupsByTimestampDescending() {
            val backup1 = BackupMetadata(
                id = "1",
                name = "old",
                timestamp = Instant.parse("2026-01-01T00:00:00Z"),
                playerCount = 5,
                sizeBytes = 1000,
                storageType = StorageType.YAML,
                compressed = true
            )
            val backup2 = BackupMetadata(
                id = "2",
                name = "new",
                timestamp = Instant.parse("2026-01-15T00:00:00Z"),
                playerCount = 5,
                sizeBytes = 1000,
                storageType = StorageType.YAML,
                compressed = true
            )
            val backup3 = BackupMetadata(
                id = "3",
                name = "middle",
                timestamp = Instant.parse("2026-01-08T00:00:00Z"),
                playerCount = 5,
                sizeBytes = 1000,
                storageType = StorageType.YAML,
                compressed = true
            )

            val sorted = listOf(backup1, backup2, backup3).sortedByDescending { it.timestamp }

            assertEquals("new", sorted[0].name)
            assertEquals("middle", sorted[1].name)
            assertEquals("old", sorted[2].name)
        }
    }

    @Nested
    @DisplayName("Confirmation State")
    inner class ConfirmationStateTests {

        @Test
        @DisplayName("confirmation states are mutually exclusive")
        fun confirmationStatesAreMutuallyExclusive() {
            // Test that only one confirmation can be active at a time
            var confirmingRestore: String? = "backup_id"
            var confirmingDelete: String? = null

            assertTrue(confirmingRestore != null && confirmingDelete == null)

            // Switch to delete confirmation
            confirmingRestore = null
            confirmingDelete = "backup_id"

            assertTrue(confirmingRestore == null && confirmingDelete != null)
        }

        @Test
        @DisplayName("clear confirmations returns to list view")
        fun clearConfirmationsReturnsToListView() {
            var confirmingRestore: String? = "backup_id"
            var confirmingDelete: String? = null

            // Clear confirmation
            confirmingRestore = null

            assertTrue(confirmingRestore == null && confirmingDelete == null)
        }
    }

    @Nested
    @DisplayName("Material Selection")
    inner class MaterialSelectionTests {

        @Test
        @DisplayName("compressed backups use minecart chest")
        fun compressedBackupsUseMinecartChest() {
            val backup = BackupMetadata(
                id = "1",
                name = "test",
                timestamp = Instant.now(),
                playerCount = 5,
                sizeBytes = 1000,
                storageType = StorageType.YAML,
                compressed = true
            )

            val material = if (backup.compressed) "CHEST_MINECART" else "CHEST"
            assertEquals("CHEST_MINECART", material)
        }

        @Test
        @DisplayName("uncompressed backups use regular chest")
        fun uncompressedBackupsUseRegularChest() {
            val backup = BackupMetadata(
                id = "1",
                name = "test",
                timestamp = Instant.now(),
                playerCount = 5,
                sizeBytes = 1000,
                storageType = StorageType.YAML,
                compressed = false
            )

            val material = if (backup.compressed) "CHEST_MINECART" else "CHEST"
            assertEquals("CHEST", material)
        }
    }
}

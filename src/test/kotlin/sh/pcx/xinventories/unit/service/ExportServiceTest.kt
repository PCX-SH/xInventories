package sh.pcx.xinventories.unit.service

import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.service.*
import sh.pcx.xinventories.internal.util.InventorySerializer
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.nio.file.Path
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for ExportService.
 *
 * Tests cover:
 * - Export single player to JSON
 * - Export all players in a group
 * - Validate JSON files
 * - Import from JSON
 * - Partial imports
 * - Error handling
 */
@DisplayName("ExportService Unit Tests")
class ExportServiceTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var plugin: XInventories
    private lateinit var storageService: StorageService
    private lateinit var exportService: ExportService
    private lateinit var scope: CoroutineScope
    private lateinit var dataFolder: File

    private val playerUUID = UUID.randomUUID()
    private val playerName = "TestPlayer"

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("ExportServiceTest"), false)
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs

            // Mock InventorySerializer for serialization
            mockkObject(InventorySerializer)
            every { InventorySerializer.serializeItemStack(any()) } returns "mock_base64_data"
            every { InventorySerializer.deserializeItemStack(any()) } returns null
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    @BeforeEach
    fun setUp() {
        dataFolder = tempDir.resolve("plugin").toFile()
        dataFolder.mkdirs()

        plugin = mockk(relaxed = true)
        storageService = mockk(relaxed = true)
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        every { plugin.dataFolder } returns dataFolder

        exportService = ExportService(plugin, scope, storageService)
        exportService.initialize()
    }

    @AfterEach
    fun tearDown() {
        exportService.shutdown()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createPlayerData(
        uuid: UUID = playerUUID,
        name: String = playerName,
        group: String = "survival",
        items: Map<Int, ItemStack> = emptyMap(),
        level: Int = 30,
        totalExp: Int = 1500
    ): PlayerData {
        return PlayerData(uuid, name, group, GameMode.SURVIVAL).apply {
            mainInventory.putAll(items)
            this.level = level
            this.totalExperience = totalExp
            health = 20.0
            maxHealth = 20.0
            foodLevel = 20
            saturation = 5.0f
        }
    }

    private fun createItem(material: Material, amount: Int = 1): ItemStack {
        return mockk<ItemStack>(relaxed = true).apply {
            every { this@apply.type } returns material
            every { this@apply.amount } returns amount
            every { maxStackSize } returns 64
            every { clone() } returns this
        }
    }

    // =========================================================================
    // Export Single Player Tests
    // =========================================================================

    @Nested
    @DisplayName("Export Single Player")
    inner class ExportSinglePlayerTests {

        @Test
        @DisplayName("Should export player data to JSON file")
        fun exportPlayerToJson() = runTest {
            val playerData = createPlayerData()
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData

            val result = exportService.exportPlayer(playerUUID, "survival")

            assertTrue(result.success)
            assertNotNull(result.filePath, "filePath should not be null")
            assertEquals(1, result.playerCount)

            val file = File(result.filePath!!)
            assertTrue(file.exists())
            assertTrue(file.readText().contains("\"version\""))
            assertTrue(file.readText().contains("\"player\""))
        }

        @Test
        @DisplayName("Should use custom file name when provided")
        fun useCustomFileName() = runTest {
            val playerData = createPlayerData()
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData

            val result = exportService.exportPlayer(playerUUID, "survival", "custom_export.json")

            assertTrue(result.success)
            assertTrue(result.filePath!!.endsWith("custom_export.json"))
        }

        @Test
        @DisplayName("Should fail when no data exists")
        fun failWhenNoData() = runTest {
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns null

            val result = exportService.exportPlayer(playerUUID, "survival")

            assertFalse(result.success)
            assertTrue(result.message.contains("No data found"))
        }

        @Test
        @DisplayName("Should include all player state in export")
        fun includeAllPlayerState() = runTest {
            val playerData = createPlayerData(level = 50, totalExp = 5000)
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData

            val result = exportService.exportPlayer(playerUUID, "survival")

            assertTrue(result.success)
            val content = File(result.filePath!!).readText()
            assertTrue(content.contains("\"level\""))
            assertTrue(content.contains("\"health\""))
            assertTrue(content.contains("\"hunger\""))
        }
    }

    // =========================================================================
    // Export Group Tests
    // =========================================================================

    @Nested
    @DisplayName("Export Group")
    inner class ExportGroupTests {

        @Test
        @DisplayName("Should export all players in group")
        fun exportAllPlayersInGroup() = runTest {
            val uuid1 = UUID.randomUUID()
            val uuid2 = UUID.randomUUID()
            val player1 = createPlayerData(uuid = uuid1, name = "Player1", group = "survival")
            val player2 = createPlayerData(uuid = uuid2, name = "Player2", group = "survival")

            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(uuid1, uuid2)
            coEvery { storageService.loadPlayerData(uuid1, "survival", null) } returns player1
            coEvery { storageService.loadPlayerData(uuid2, "survival", null) } returns player2

            val result = exportService.exportGroup("survival")

            assertTrue(result.success)
            assertEquals(2, result.playerCount)
        }

        @Test
        @DisplayName("Should fail when no players in group")
        fun failWhenNoPlayersInGroup() = runTest {
            coEvery { storageService.getAllPlayerUUIDs() } returns emptySet()

            val result = exportService.exportGroup("empty_group")

            assertFalse(result.success)
            assertTrue(result.message.contains("No players found"))
        }

        @Test
        @DisplayName("Should skip players not in target group")
        fun skipPlayersNotInGroup() = runTest {
            val uuid1 = UUID.randomUUID()
            val uuid2 = UUID.randomUUID()
            val player1 = createPlayerData(uuid = uuid1, name = "Player1", group = "survival")
            val player2 = createPlayerData(uuid = uuid2, name = "Player2", group = "creative")

            coEvery { storageService.getAllPlayerUUIDs() } returns setOf(uuid1, uuid2)
            coEvery { storageService.loadPlayerData(uuid1, "survival", null) } returns player1
            coEvery { storageService.loadPlayerData(uuid2, "survival", null) } returns null

            val result = exportService.exportGroup("survival")

            assertTrue(result.success)
            assertEquals(1, result.playerCount)
        }
    }

    // =========================================================================
    // Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("Validate Import")
    inner class ValidationTests {

        @Test
        @DisplayName("Should validate single player export")
        fun validateSinglePlayerExport() = runTest {
            val playerData = createPlayerData()
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData

            // First export
            val exportResult = exportService.exportPlayer(playerUUID, "survival", "validate_test.json")
            assertTrue(exportResult.success)

            // Then validate
            val validationResult = exportService.validateImport("validate_test.json")

            assertTrue(validationResult.valid)
            assertEquals("1.1.0", validationResult.version)
            assertEquals(1, validationResult.playerCount)
            assertEquals("survival", validationResult.groupName)
        }

        @Test
        @DisplayName("Should fail validation for non-existent file")
        fun failValidationForNonExistentFile() = runTest {
            val result = exportService.validateImport("non_existent_file.json")

            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("not found") })
        }

        @Test
        @DisplayName("Should fail validation for invalid JSON")
        fun failValidationForInvalidJson() = runTest {
            val invalidFile = File(exportService.getExportDirectory(), "invalid.json")
            invalidFile.writeText("not valid json {")

            val result = exportService.validateImport("invalid.json")

            assertFalse(result.valid)
            assertTrue(result.errors.any { it.contains("Invalid JSON") })
        }
    }

    // =========================================================================
    // Import Tests
    // =========================================================================

    @Nested
    @DisplayName("Import from JSON")
    inner class ImportTests {

        @Test
        @DisplayName("Should import player from JSON file")
        fun importPlayerFromJson() = runTest {
            val playerData = createPlayerData()
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData andThen null
            coEvery { storageService.savePlayerData(any()) } returns true

            // First export
            val exportResult = exportService.exportPlayer(playerUUID, "survival", "import_test.json")
            assertTrue(exportResult.success)

            // Then import (with no existing data)
            val importResult = exportService.importFromFile("import_test.json")

            assertTrue(importResult.success)
            assertEquals(1, importResult.playersImported)
        }

        @Test
        @DisplayName("Should skip import when data exists and overwrite is false")
        fun skipWhenDataExistsNoOverwrite() = runTest {
            val playerData = createPlayerData()
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData

            // First export
            val exportResult = exportService.exportPlayer(playerUUID, "survival", "skip_test.json")
            assertTrue(exportResult.success)

            // Then import (data exists)
            val importResult = exportService.importFromFile("skip_test.json", overwrite = false)

            assertTrue(importResult.success)
            assertEquals(0, importResult.playersImported)
            assertEquals(1, importResult.playersSkipped)
        }

        @Test
        @DisplayName("Should overwrite when overwrite flag is true")
        fun overwriteWhenFlagIsTrue() = runTest {
            val playerData = createPlayerData()
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData
            coEvery { storageService.savePlayerData(any()) } returns true

            // First export
            val exportResult = exportService.exportPlayer(playerUUID, "survival", "overwrite_test.json")
            assertTrue(exportResult.success)

            // Then import with overwrite
            val importResult = exportService.importFromFile("overwrite_test.json", overwrite = true)

            assertTrue(importResult.success)
            assertEquals(1, importResult.playersImported)
        }

        @Test
        @DisplayName("Should allow targeting different group on import")
        fun targetDifferentGroupOnImport() = runTest {
            val playerData = createPlayerData(group = "survival")
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData
            coEvery { storageService.loadPlayerData(playerUUID, "creative", null) } returns null
            coEvery { storageService.savePlayerData(any()) } returns true

            // Export from survival
            val exportResult = exportService.exportPlayer(playerUUID, "survival", "group_test.json")
            assertTrue(exportResult.success)

            // Import to creative
            val importResult = exportService.importFromFile("group_test.json", targetGroup = "creative")

            assertTrue(importResult.success)
            coVerify { storageService.savePlayerData(match { it.group == "creative" }) }
        }
    }

    // =========================================================================
    // List Export Files Tests
    // =========================================================================

    @Nested
    @DisplayName("List Export Files")
    inner class ListExportFilesTests {

        @Test
        @DisplayName("Should list export files sorted by modification time")
        fun listExportFilesSorted() = runTest {
            val playerData = createPlayerData()
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData

            // Create multiple exports
            exportService.exportPlayer(playerUUID, "survival", "export1.json")
            Thread.sleep(50)
            exportService.exportPlayer(playerUUID, "survival", "export2.json")
            Thread.sleep(50)
            exportService.exportPlayer(playerUUID, "survival", "export3.json")

            val files = exportService.listExportFiles()

            assertEquals(3, files.size)
            // Most recent should be first
            assertEquals("export3.json", files[0].name)
        }

        @Test
        @DisplayName("Should return empty list when no exports exist")
        fun returnEmptyWhenNoExports() {
            val files = exportService.listExportFiles()
            assertTrue(files.isEmpty())
        }
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle storage errors gracefully")
        fun handleStorageErrors() = runTest {
            coEvery { storageService.loadPlayerData(any(), any(), any()) } throws RuntimeException("Storage error")

            val result = exportService.exportPlayer(playerUUID, "survival")

            assertFalse(result.success)
            assertTrue(result.message.contains("Export failed"))
        }

        @Test
        @DisplayName("Should handle import errors gracefully")
        fun handleImportErrors() = runTest {
            val playerData = createPlayerData()
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns playerData
            coEvery { storageService.savePlayerData(any()) } throws RuntimeException("Save error")

            // First export
            val exportResult = exportService.exportPlayer(playerUUID, "survival", "error_test.json")
            assertTrue(exportResult.success)

            // Reset mock to return null for data check, then throw on save
            coEvery { storageService.loadPlayerData(playerUUID, "survival", null) } returns null
            coEvery { storageService.savePlayerData(any()) } throws RuntimeException("Save error")

            val importResult = exportService.importFromFile("error_test.json")

            assertFalse(importResult.success)
        }
    }
}

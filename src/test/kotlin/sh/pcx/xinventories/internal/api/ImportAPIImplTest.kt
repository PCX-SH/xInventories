package sh.pcx.xinventories.internal.api

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.ImportAPI
import sh.pcx.xinventories.internal.import.ImportService
import sh.pcx.xinventories.internal.model.*
import sh.pcx.xinventories.internal.service.ServiceManager
import java.util.*
import java.util.concurrent.TimeUnit

@DisplayName("ImportAPIImpl")
class ImportAPIImplTest {

    private lateinit var plugin: XInventories
    private lateinit var api: ImportAPIImpl
    private lateinit var serviceManager: ServiceManager
    private lateinit var importService: ImportService

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        importService = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.importService } returns importService

        api = ImportAPIImpl(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("detectSources")
    inner class DetectSources {

        @Test
        @DisplayName("returns available import sources")
        fun returnsAvailableImportSources() {
            val sources = listOf(
                createMockSource("pwi", "PerWorldInventory", true, true, 100, 5),
                createMockSource("mvi", "MultiVerse-Inventories", true, false, 50, 3)
            )
            every { importService.detectSources() } returns sources

            val result = api.detectSources()

            assertEquals(2, result.size)
            assertEquals("pwi", result[0].id)
            assertEquals("PerWorldInventory", result[0].name)
            assertTrue(result[0].isAvailable)
            assertTrue(result[0].hasApiAccess)
            assertEquals(100, result[0].playerCount)
            assertEquals(5, result[0].groupCount)
        }

        @Test
        @DisplayName("returns empty list when no sources available")
        fun returnsEmptyListWhenNoSourcesAvailable() {
            every { importService.detectSources() } returns emptyList()

            val result = api.detectSources()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("getSourceInfo")
    inner class GetSourceInfo {

        @Test
        @DisplayName("returns source info when found")
        fun returnsSourceInfoWhenFound() {
            val source = createMockSource("pwi", "PerWorldInventory", true, true, 100, 5)
            every { importService.getSource("pwi") } returns source

            val result = api.getSourceInfo("pwi")

            Assertions.assertNotNull(result)
            assertEquals("pwi", result?.id)
            assertEquals("PerWorldInventory", result?.name)
        }

        @Test
        @DisplayName("returns null when source not found")
        fun returnsNullWhenSourceNotFound() {
            every { importService.getSource("nonexistent") } returns null

            val result = api.getSourceInfo("nonexistent")

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("previewImport")
    inner class PreviewImport {

        @Test
        @DisplayName("returns preview result")
        fun returnsPreviewResult() {
            val preview = ImportPreview(
                source = "PerWorldInventory",
                totalPlayers = 100,
                playersToSkip = 20,
                groups = listOf(
                    ImportGroup("survival", setOf("world", "world_nether")),
                    ImportGroup("creative", setOf("creative"))
                ),
                mapping = ImportMapping("pwi", mapOf("survival" to "survival")),
                estimatedDataSize = 1024000,
                warnings = listOf("Some warning")
            )
            coEvery { importService.previewImport("pwi", any()) } returns preview

            val future = api.previewImport("pwi", mapOf("survival" to "survival"))
            val result = future.get(5, TimeUnit.SECONDS)

            Assertions.assertNotNull(result)
            assertEquals("PerWorldInventory", result?.source)
            assertEquals(100, result?.totalPlayers)
            assertEquals(80, result?.playersToImport)
            assertEquals(20, result?.playersToSkip)
            assertEquals(2, result?.groups?.size)
        }

        @Test
        @DisplayName("returns null when source not available")
        fun returnsNullWhenSourceNotAvailable() {
            coEvery { importService.previewImport("nonexistent", any()) } returns null

            val future = api.previewImport("nonexistent")
            val result = future.get(5, TimeUnit.SECONDS)

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("executeImport")
    inner class ExecuteImport {

        @Test
        @DisplayName("executes import successfully")
        fun executesImportSuccessfully() {
            val importResult = ImportResult(
                success = true,
                playersImported = 80,
                playersSkipped = 20,
                playersFailed = 0,
                groupsProcessed = 3,
                durationMs = 5000,
                errors = emptyList(),
                warnings = listOf("Some warning"),
                isDryRun = false
            )
            coEvery { importService.executeImport("pwi", any()) } returns importResult

            val options = ImportAPI.ImportOptions(
                overwriteExisting = false,
                importBalances = true,
                createMissingGroups = true
            )
            val future = api.executeImport("pwi", mapOf("survival" to "survival"), options)
            val result = future.get(5, TimeUnit.SECONDS)

            assertTrue(result.success)
            assertEquals(80, result.playersImported)
            assertEquals(20, result.playersSkipped)
            assertEquals(0, result.playersFailed)
            assertEquals(3, result.groupsProcessed)
            assertEquals(5000, result.durationMs)
        }

        @Test
        @DisplayName("returns failure result on error")
        fun returnsFailureResultOnError() {
            val importResult = ImportResult(
                success = false,
                playersImported = 0,
                playersSkipped = 0,
                playersFailed = 100,
                groupsProcessed = 0,
                durationMs = 1000,
                errors = listOf(ImportError("Source not available")),
                warnings = emptyList(),
                isDryRun = false
            )
            coEvery { importService.executeImport(any(), any()) } returns importResult

            val future = api.executeImport("invalid", mapOf())
            val result = future.get(5, TimeUnit.SECONDS)

            assertFalse(result.success)
            assertEquals(100, result.playersFailed)
            assertTrue(result.errors.isNotEmpty())
        }

        @Test
        @DisplayName("supports dry run mode")
        fun supportsDryRunMode() {
            val importResult = ImportResult(
                success = true,
                playersImported = 50,
                playersSkipped = 0,
                playersFailed = 0,
                groupsProcessed = 2,
                durationMs = 500,
                errors = emptyList(),
                warnings = emptyList(),
                isDryRun = true
            )
            coEvery { importService.executeImport(any(), match { it.options.dryRun }) } returns importResult

            val options = ImportAPI.ImportOptions(dryRun = true)
            val future = api.executeImport("pwi", mapOf("default" to "default"), options)
            val result = future.get(5, TimeUnit.SECONDS)

            assertTrue(result.success)
        }
    }

    @Nested
    @DisplayName("getLastImportResult")
    inner class GetLastImportResult {

        @Test
        @DisplayName("returns last import result")
        fun returnsLastImportResult() {
            val importResult = ImportResult(
                success = true,
                playersImported = 50,
                playersSkipped = 10,
                playersFailed = 0,
                groupsProcessed = 3,
                durationMs = 3000,
                errors = emptyList(),
                warnings = emptyList(),
                isDryRun = false
            )
            every { importService.getLastImportResult() } returns importResult

            val result = api.getLastImportResult()

            Assertions.assertNotNull(result)
            assertTrue(result!!.success)
            assertEquals(50, result.playersImported)
        }

        @Test
        @DisplayName("returns null when no import has been run")
        fun returnsNullWhenNoImportHasBeenRun() {
            every { importService.getLastImportResult() } returns null

            val result = api.getLastImportResult()

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("getSourceGroups")
    inner class GetSourceGroups {

        @Test
        @DisplayName("returns groups from source")
        fun returnsGroupsFromSource() {
            val source = createMockSource("pwi", "PerWorldInventory", true, true, 100, 3)
            every { importService.getSource("pwi") } returns source

            val result = api.getSourceGroups("pwi")

            assertEquals(3, result.size)
        }

        @Test
        @DisplayName("returns empty map when source not found")
        fun returnsEmptyMapWhenSourceNotFound() {
            every { importService.getSource("nonexistent") } returns null

            val result = api.getSourceGroups("nonexistent")

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("getSourcePlayers")
    inner class GetSourcePlayers {

        @Test
        @DisplayName("returns players from source")
        fun returnsPlayersFromSource() {
            val players = listOf(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
            val source = mockk<ImportSource>()
            every { source.getPlayers() } returns players
            every { importService.getSource("pwi") } returns source

            val result = api.getSourcePlayers("pwi")

            assertEquals(3, result.size)
        }

        @Test
        @DisplayName("returns empty list when source not found")
        fun returnsEmptyListWhenSourceNotFound() {
            every { importService.getSource("nonexistent") } returns null

            val result = api.getSourcePlayers("nonexistent")

            assertTrue(result.isEmpty())
        }
    }

    private fun createMockSource(
        id: String,
        name: String,
        isAvailable: Boolean,
        hasApiAccess: Boolean,
        playerCount: Int,
        groupCount: Int
    ): ImportSource {
        val players = (1..playerCount).map { UUID.randomUUID() }
        val groups = (1..groupCount).map { ImportGroup("group$it", setOf("world$it")) }

        return mockk {
            every { this@mockk.id } returns id
            every { this@mockk.name } returns name
            every { this@mockk.isAvailable } returns isAvailable
            every { this@mockk.hasApiAccess } returns hasApiAccess
            every { getPlayers() } returns players
            every { getGroups() } returns groups
        }
    }
}

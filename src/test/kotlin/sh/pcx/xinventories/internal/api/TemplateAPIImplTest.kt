package sh.pcx.xinventories.internal.api

import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.InventoryTemplate
import sh.pcx.xinventories.internal.model.TemplateApplyTrigger
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.service.TemplateService
import java.util.*
import java.util.concurrent.TimeUnit

@DisplayName("TemplateAPIImpl")
class TemplateAPIImplTest {

    private lateinit var plugin: XInventories
    private lateinit var api: TemplateAPIImpl
    private lateinit var serviceManager: ServiceManager
    private lateinit var templateService: TemplateService
    private lateinit var groupService: GroupService

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        // Don't use relaxed mock for templateService - it interferes with Result<T> return types
        templateService = mockk()
        groupService = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.templateService } returns templateService
        every { serviceManager.groupService } returns groupService

        api = TemplateAPIImpl(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("getTemplate")
    inner class GetTemplate {

        @Test
        @DisplayName("returns template when found")
        fun returnsTemplateWhenFound() {
            val template = mockk<InventoryTemplate>()
            every { templateService.getTemplate("starter") } returns template

            val result = api.getTemplate("starter")

            Assertions.assertNotNull(result)
            assertEquals(template, result)
        }

        @Test
        @DisplayName("returns null when not found")
        fun returnsNullWhenNotFound() {
            every { templateService.getTemplate("nonexistent") } returns null

            val result = api.getTemplate("nonexistent")

            Assertions.assertNull(result)
        }
    }

    @Nested
    @DisplayName("getAllTemplates")
    inner class GetAllTemplates {

        @Test
        @DisplayName("returns all templates")
        fun returnsAllTemplates() {
            val templates = listOf(
                mockk<InventoryTemplate>(),
                mockk<InventoryTemplate>()
            )
            every { templateService.getAllTemplates() } returns templates

            val result = api.getAllTemplates()

            assertEquals(2, result.size)
        }

        @Test
        @DisplayName("returns empty list when no templates")
        fun returnsEmptyListWhenNoTemplates() {
            every { templateService.getAllTemplates() } returns emptyList()

            val result = api.getAllTemplates()

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("createTemplate")
    inner class CreateTemplate {

        @Test
        @DisplayName("creates template from player inventory")
        fun createsTemplateFromPlayerInventory() {
            val player = mockk<Player>()
            val template = mockk<InventoryTemplate>()

            // Use coAnswers for proper suspend function handling
            coEvery { templateService.createTemplate(any(), any(), any(), any()) } coAnswers {
                Result.success(template)
            }

            val future = api.createTemplate("newkit", player, "New Kit", "A starter kit")
            val result = future.get(5, TimeUnit.SECONDS)

            assertTrue(result.isSuccess, "Result should be success")
            Assertions.assertNotNull(result.getOrNull(), "Result should contain a template")

            coVerify { templateService.createTemplate("newkit", player, "New Kit", "A starter kit") }
        }

        @Test
        @DisplayName("returns failure when template already exists")
        fun returnsFailureWhenTemplateAlreadyExists() {
            val player = mockk<Player>()

            // Clear any existing stubs before setting up failure case
            clearMocks(templateService)
            // Have the mock throw an exception - the API catches it and wraps in Result.failure
            coEvery { templateService.createTemplate(any(), any(), any(), any()) } throws
                IllegalArgumentException("Template already exists")

            val future = api.createTemplate("existing", player)

            // When the service throws, the CompletableFuture completes exceptionally
            val exception = assertThrows<java.util.concurrent.ExecutionException> {
                future.get(5, TimeUnit.SECONDS)
            }
            assertTrue(exception.cause is IllegalArgumentException)
            assertEquals("Template already exists", exception.cause?.message)

            coVerify { templateService.createTemplate("existing", player, null, null) }
        }
    }

    @Nested
    @DisplayName("createEmptyTemplate")
    inner class CreateEmptyTemplate {

        @Test
        @DisplayName("creates empty template successfully")
        fun createsEmptyTemplateSuccessfully() {
            coEvery { templateService.saveTemplate(any()) } returns true

            val future = api.createEmptyTemplate("emptykit")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
            Assertions.assertNotNull(result.getOrNull())
        }

        @Test
        @DisplayName("returns failure when save fails")
        fun returnsFailureWhenSaveFails() {
            coEvery { templateService.saveTemplate(any()) } returns false

            val future = api.createEmptyTemplate("failedkit")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isFailure)
        }
    }

    @Nested
    @DisplayName("deleteTemplate")
    inner class DeleteTemplate {

        @Test
        @DisplayName("deletes template successfully")
        fun deletesTemplateSuccessfully() {
            coEvery { templateService.deleteTemplate("oldkit") } returns true

            val future = api.deleteTemplate("oldkit")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when template not found")
        fun returnsFalseWhenTemplateNotFound() {
            coEvery { templateService.deleteTemplate("nonexistent") } returns false

            val future = api.deleteTemplate("nonexistent")

            val result = future.get(5, TimeUnit.SECONDS)
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("applyTemplate")
    inner class ApplyTemplate {

        @Test
        @DisplayName("applies template by name")
        fun appliesTemplateByName() {
            val player = mockk<Player>()
            val world = mockk<org.bukkit.World>()
            every { player.world } returns world

            val template = mockk<InventoryTemplate>()
            every { templateService.getTemplate("starter") } returns template

            val group = mockk<Group>()
            every { group.toApiModel() } returns InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
            every { groupService.getGroupForWorld(world) } returns group

            coEvery { templateService.applyTemplate(player, template, any(), TemplateApplyTrigger.MANUAL, true) } returns true

            val future = api.applyTemplate(player, "starter")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
        }

        @Test
        @DisplayName("returns failure when template not found")
        fun returnsFailureWhenTemplateNotFound() {
            val player = mockk<Player>()
            every { templateService.getTemplate("nonexistent") } returns null

            val future = api.applyTemplate(player, "nonexistent")

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Template not found") == true)
        }

        @Test
        @DisplayName("applies template with custom trigger")
        fun appliesTemplateWithCustomTrigger() {
            val player = mockk<Player>()
            val world = mockk<org.bukkit.World>()
            every { player.world } returns world

            val template = mockk<InventoryTemplate>()
            val group = mockk<Group>()
            every { group.toApiModel() } returns InventoryGroup("survival", emptySet(), emptyList(), 0, null, GroupSettings(), false)
            every { groupService.getGroupForWorld(world) } returns group

            coEvery { templateService.applyTemplate(player, template, any(), TemplateApplyTrigger.FIRST_JOIN, false) } returns true

            val future = api.applyTemplate(player, template, TemplateApplyTrigger.FIRST_JOIN, false)

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result.isSuccess)
        }
    }

    @Nested
    @DisplayName("isFirstJoin")
    inner class IsFirstJoin {

        @Test
        @DisplayName("returns true for first join")
        fun returnsTrueForFirstJoin() {
            val uuid = UUID.randomUUID()
            every { templateService.isFirstJoin(uuid, "survival") } returns true

            val result = api.isFirstJoin(uuid, "survival")

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false for returning player")
        fun returnsFalseForReturningPlayer() {
            val uuid = UUID.randomUUID()
            every { templateService.isFirstJoin(uuid, "survival") } returns false

            val result = api.isFirstJoin(uuid, "survival")

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("markJoined")
    inner class MarkJoined {

        @Test
        @DisplayName("marks player as joined")
        fun marksPlayerAsJoined() {
            val uuid = UUID.randomUUID()
            every { templateService.markJoined(uuid, "survival") } just Runs

            api.markJoined(uuid, "survival")

            verify { templateService.markJoined(uuid, "survival") }
        }
    }

    @Nested
    @DisplayName("saveTemplate")
    inner class SaveTemplate {

        @Test
        @DisplayName("saves template successfully")
        fun savesTemplateSuccessfully() {
            val template = mockk<InventoryTemplate>()
            coEvery { templateService.saveTemplate(template) } returns true

            val future = api.saveTemplate(template)

            val result = future.get(5, TimeUnit.SECONDS)
            assertTrue(result)
        }

        @Test
        @DisplayName("returns false on save failure")
        fun returnsFalseOnSaveFailure() {
            val template = mockk<InventoryTemplate>()
            coEvery { templateService.saveTemplate(template) } returns false

            val future = api.saveTemplate(template)

            val result = future.get(5, TimeUnit.SECONDS)
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("reloadTemplates")
    inner class ReloadTemplates {

        @Test
        @DisplayName("reloads all templates")
        fun reloadsAllTemplates() {
            coEvery { templateService.reload() } just Runs

            val future = api.reloadTemplates()

            future.get(5, TimeUnit.SECONDS)
            coVerify { templateService.reload() }
        }
    }
}

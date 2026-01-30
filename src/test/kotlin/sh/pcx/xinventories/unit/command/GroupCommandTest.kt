package sh.pcx.xinventories.unit.command

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.command.subcommand.GroupCommand
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.MessageService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.logging.Logger

/**
 * Unit tests for GroupCommand.
 *
 * Tests cover:
 * - Command properties (name, aliases, permission, usage, description)
 * - execute() with "create" action - Creating new groups
 * - execute() with "delete" action - Deleting groups (with error handling for default group)
 * - execute() with "list" action - Listing all groups
 * - execute() with "info" action - Showing group details
 * - execute() with invalid/unknown actions
 * - Syntax validation for all actions
 * - tabComplete() - Providing tab completions for actions and group names
 */
@DisplayName("GroupCommand Unit Tests")
class GroupCommandTest {

    private lateinit var plugin: XInventories
    private lateinit var serviceManager: ServiceManager
    private lateinit var groupService: GroupService
    private lateinit var messageService: MessageService
    private lateinit var sender: CommandSender
    private lateinit var groupCommand: GroupCommand

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("GroupCommandTest"), false)
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        groupService = mockk(relaxed = true)
        messageService = mockk(relaxed = true)
        sender = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.groupService } returns groupService
        every { serviceManager.messageService } returns messageService

        groupCommand = GroupCommand()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockGroup(
        name: String,
        worlds: Set<String> = emptySet(),
        patterns: List<String> = emptyList(),
        priority: Int = 0,
        parent: String? = null,
        isDefault: Boolean = false
    ): Group {
        return mockk<Group>(relaxed = true).apply {
            every { this@apply.name } returns name
            every { this@apply.worlds } returns worlds
            every { patternStrings } returns patterns
            every { this@apply.priority } returns priority
            every { this@apply.parent } returns parent
            every { this@apply.isDefault } returns isDefault
        }
    }

    private fun createMockInventoryGroup(
        name: String,
        worlds: Set<String> = emptySet(),
        patterns: List<String> = emptyList(),
        priority: Int = 0,
        parent: String? = null,
        isDefault: Boolean = false
    ): InventoryGroup {
        return InventoryGroup(
            name = name,
            worlds = worlds,
            patterns = patterns,
            priority = priority,
            parent = parent,
            settings = GroupSettings(),
            isDefault = isDefault
        )
    }

    // =========================================================================
    // Command Properties Tests
    // =========================================================================

    @Nested
    @DisplayName("Command Properties")
    inner class CommandPropertiesTests {

        @Test
        @DisplayName("Should have correct name")
        fun hasCorrectName() {
            assertEquals("group", groupCommand.name)
        }

        @Test
        @DisplayName("Should have correct aliases")
        fun hasCorrectAliases() {
            val aliases = groupCommand.aliases
            assertTrue(aliases.contains("groups"))
            assertTrue(aliases.contains("g"))
            assertEquals(2, aliases.size)
        }

        @Test
        @DisplayName("Should have correct permission")
        fun hasCorrectPermission() {
            assertEquals("xinventories.command.group", groupCommand.permission)
        }

        @Test
        @DisplayName("Should have correct usage string")
        fun hasCorrectUsage() {
            assertEquals("/xinv group <create|delete|list|info> [name]", groupCommand.usage)
        }

        @Test
        @DisplayName("Should have correct description")
        fun hasCorrectDescription() {
            assertEquals("Manage inventory groups", groupCommand.description)
        }

        @Test
        @DisplayName("Should not be player-only command")
        fun isNotPlayerOnly() {
            assertFalse(groupCommand.playerOnly)
        }
    }

    // =========================================================================
    // Execute with No Arguments Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute with No Arguments")
    inner class ExecuteNoArgsTests {

        @Test
        @DisplayName("Should show usage when no arguments provided")
        fun showUsageWhenNoArgs() = runTest {
            val result = groupCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
            verify {
                messageService.send(
                    sender,
                    "invalid-syntax",
                    "usage" to "/xinv group <create|delete|list|info> [name]"
                )
            }
        }
    }

    // =========================================================================
    // Execute "create" Action Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute 'create' Action")
    inner class ExecuteCreateTests {

        @Test
        @DisplayName("Should show syntax error when group name is missing")
        fun showSyntaxErrorWhenNameMissing() = runTest {
            val result = groupCommand.execute(plugin, sender, arrayOf("create"))

            assertTrue(result)
            verify {
                messageService.send(
                    sender,
                    "invalid-syntax",
                    "usage" to "/xinv group create <name>"
                )
            }
        }

        @Test
        @DisplayName("Should create group successfully")
        fun createGroupSuccessfully() = runTest {
            every { groupService.createGroup("survival") } returns Result.success(
                createMockInventoryGroup("survival")
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("create", "survival"))

            assertTrue(result)
            verify { groupService.createGroup("survival") }
            verify { messageService.send(sender, "group-created", "group" to "survival") }
        }

        @Test
        @DisplayName("Should handle group already exists error")
        fun handleGroupAlreadyExistsError() = runTest {
            every { groupService.createGroup("existing") } returns Result.failure(
                IllegalArgumentException("Group already exists")
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("create", "existing"))

            assertTrue(result)
            verify { groupService.createGroup("existing") }
            verify { messageService.send(sender, "group-already-exists", "group" to "existing") }
        }

        @Test
        @DisplayName("Should handle CREATE action case-insensitively")
        fun handleCreateCaseInsensitively() = runTest {
            every { groupService.createGroup("newgroup") } returns Result.success(
                createMockInventoryGroup("newgroup")
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("CREATE", "newgroup"))

            assertTrue(result)
            verify { groupService.createGroup("newgroup") }
            verify { messageService.send(sender, "group-created", "group" to "newgroup") }
        }

        @Test
        @DisplayName("Should handle Create action with mixed case")
        fun handleCreateMixedCase() = runTest {
            every { groupService.createGroup("testgroup") } returns Result.success(
                createMockInventoryGroup("testgroup")
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("Create", "testgroup"))

            assertTrue(result)
            verify { groupService.createGroup("testgroup") }
        }

        @Test
        @DisplayName("Should preserve group name case")
        fun preserveGroupNameCase() = runTest {
            every { groupService.createGroup("MyGroup") } returns Result.success(
                createMockInventoryGroup("MyGroup")
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("create", "MyGroup"))

            assertTrue(result)
            verify { groupService.createGroup("MyGroup") }
            verify { messageService.send(sender, "group-created", "group" to "MyGroup") }
        }
    }

    // =========================================================================
    // Execute "delete" Action Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute 'delete' Action")
    inner class ExecuteDeleteTests {

        @Test
        @DisplayName("Should show syntax error when group name is missing")
        fun showSyntaxErrorWhenNameMissing() = runTest {
            val result = groupCommand.execute(plugin, sender, arrayOf("delete"))

            assertTrue(result)
            verify {
                messageService.send(
                    sender,
                    "invalid-syntax",
                    "usage" to "/xinv group delete <name>"
                )
            }
        }

        @Test
        @DisplayName("Should delete group successfully")
        fun deleteGroupSuccessfully() = runTest {
            every { groupService.deleteGroup("oldgroup") } returns Result.success(Unit)

            val result = groupCommand.execute(plugin, sender, arrayOf("delete", "oldgroup"))

            assertTrue(result)
            verify { groupService.deleteGroup("oldgroup") }
            verify { messageService.send(sender, "group-deleted", "group" to "oldgroup") }
        }

        @Test
        @DisplayName("Should handle cannot delete default group error")
        fun handleCannotDeleteDefaultGroupError() = runTest {
            every { groupService.deleteGroup("default") } returns Result.failure(
                IllegalArgumentException("Cannot delete the default group")
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("delete", "default"))

            assertTrue(result)
            verify { groupService.deleteGroup("default") }
            verify { messageService.send(sender, "group-cannot-delete-default") }
        }

        @Test
        @DisplayName("Should handle group not found error")
        fun handleGroupNotFoundError() = runTest {
            every { groupService.deleteGroup("nonexistent") } returns Result.failure(
                IllegalArgumentException("Group not found")
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("delete", "nonexistent"))

            assertTrue(result)
            verify { groupService.deleteGroup("nonexistent") }
            verify { messageService.send(sender, "group-not-found", "group" to "nonexistent") }
        }

        @Test
        @DisplayName("Should handle 'remove' alias for delete action")
        fun handleRemoveAlias() = runTest {
            every { groupService.deleteGroup("testgroup") } returns Result.success(Unit)

            val result = groupCommand.execute(plugin, sender, arrayOf("remove", "testgroup"))

            assertTrue(result)
            verify { groupService.deleteGroup("testgroup") }
            verify { messageService.send(sender, "group-deleted", "group" to "testgroup") }
        }

        @Test
        @DisplayName("Should handle DELETE action case-insensitively")
        fun handleDeleteCaseInsensitively() = runTest {
            every { groupService.deleteGroup("mygroup") } returns Result.success(Unit)

            val result = groupCommand.execute(plugin, sender, arrayOf("DELETE", "mygroup"))

            assertTrue(result)
            verify { groupService.deleteGroup("mygroup") }
        }

        @Test
        @DisplayName("Should handle REMOVE alias case-insensitively")
        fun handleRemoveAliaseCaseInsensitively() = runTest {
            every { groupService.deleteGroup("mygroup") } returns Result.success(Unit)

            val result = groupCommand.execute(plugin, sender, arrayOf("REMOVE", "mygroup"))

            assertTrue(result)
            verify { groupService.deleteGroup("mygroup") }
        }
    }

    // =========================================================================
    // Execute "list" Action Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute 'list' Action")
    inner class ExecuteListTests {

        @Test
        @DisplayName("Should list all groups")
        fun listAllGroups() = runTest {
            val groups = listOf(
                createMockInventoryGroup("survival", setOf("world", "world_nether"), priority = 0),
                createMockInventoryGroup("creative", setOf("creative_world"), priority = 10)
            )
            every { groupService.getAllGroupsApi() } returns groups

            val result = groupCommand.execute(plugin, sender, arrayOf("list"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-list-header") }
            verify {
                messageService.sendRaw(
                    sender,
                    "group-list-entry",
                    "group" to "survival",
                    "count" to "2"
                )
            }
            verify {
                messageService.sendRaw(
                    sender,
                    "group-list-entry",
                    "group" to "creative",
                    "count" to "1"
                )
            }
        }

        @Test
        @DisplayName("Should show empty list message when no groups")
        fun showEmptyListMessage() = runTest {
            every { groupService.getAllGroupsApi() } returns emptyList()

            val result = groupCommand.execute(plugin, sender, arrayOf("list"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-list-header") }
            verify { messageService.sendRaw(sender, "group-list-empty") }
        }

        @Test
        @DisplayName("Should handle LIST action case-insensitively")
        fun handleListCaseInsensitively() = runTest {
            every { groupService.getAllGroupsApi() } returns emptyList()

            val result = groupCommand.execute(plugin, sender, arrayOf("LIST"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-list-header") }
        }

        @Test
        @DisplayName("Should display groups with zero worlds")
        fun displayGroupsWithZeroWorlds() = runTest {
            val groups = listOf(
                createMockInventoryGroup("empty_group", emptySet())
            )
            every { groupService.getAllGroupsApi() } returns groups

            val result = groupCommand.execute(plugin, sender, arrayOf("list"))

            assertTrue(result)
            verify {
                messageService.sendRaw(
                    sender,
                    "group-list-entry",
                    "group" to "empty_group",
                    "count" to "0"
                )
            }
        }

        @Test
        @DisplayName("Should handle many groups in list")
        fun handleManyGroupsInList() = runTest {
            val groups = (1..10).map { i ->
                createMockInventoryGroup("group$i", setOf("world$i"))
            }
            every { groupService.getAllGroupsApi() } returns groups

            val result = groupCommand.execute(plugin, sender, arrayOf("list"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-list-header") }
            groups.forEach { group ->
                verify {
                    messageService.sendRaw(
                        sender,
                        "group-list-entry",
                        "group" to group.name,
                        "count" to "1"
                    )
                }
            }
        }
    }

    // =========================================================================
    // Execute "info" Action Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute 'info' Action")
    inner class ExecuteInfoTests {

        @Test
        @DisplayName("Should show syntax error when group name is missing")
        fun showSyntaxErrorWhenNameMissing() = runTest {
            val result = groupCommand.execute(plugin, sender, arrayOf("info"))

            assertTrue(result)
            verify {
                messageService.send(
                    sender,
                    "invalid-syntax",
                    "usage" to "/xinv group info <name>"
                )
            }
        }

        @Test
        @DisplayName("Should show group info with worlds and patterns")
        fun showGroupInfoWithWorldsAndPatterns() = runTest {
            val group = createMockInventoryGroup(
                name = "survival",
                worlds = setOf("world", "world_nether"),
                patterns = listOf("skyblock_.*", "adventure_.*"),
                priority = 5,
                parent = "base"
            )
            every { groupService.getGroupApi("survival") } returns group

            val result = groupCommand.execute(plugin, sender, arrayOf("info", "survival"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-info-header", "group" to "survival") }
            verify {
                messageService.sendRaw(
                    sender,
                    "group-info-worlds",
                    "worlds" to "world, world_nether"
                )
            }
            verify {
                messageService.sendRaw(
                    sender,
                    "group-info-patterns",
                    "patterns" to "skyblock_.*, adventure_.*"
                )
            }
            verify { messageService.sendRaw(sender, "group-info-priority", "priority" to "5") }
            verify { messageService.sendRaw(sender, "group-info-parent", "parent" to "base") }
        }

        @Test
        @DisplayName("Should show 'none' for empty worlds")
        fun showNoneForEmptyWorlds() = runTest {
            val group = createMockInventoryGroup(
                name = "empty_worlds",
                worlds = emptySet(),
                patterns = listOf("test_.*")
            )
            every { groupService.getGroupApi("empty_worlds") } returns group

            val result = groupCommand.execute(plugin, sender, arrayOf("info", "empty_worlds"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-info-worlds", "worlds" to "none") }
        }

        @Test
        @DisplayName("Should show 'none' for empty patterns")
        fun showNoneForEmptyPatterns() = runTest {
            val group = createMockInventoryGroup(
                name = "empty_patterns",
                worlds = setOf("world"),
                patterns = emptyList()
            )
            every { groupService.getGroupApi("empty_patterns") } returns group

            val result = groupCommand.execute(plugin, sender, arrayOf("info", "empty_patterns"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-info-patterns", "patterns" to "none") }
        }

        @Test
        @DisplayName("Should show 'none' for null parent")
        fun showNoneForNullParent() = runTest {
            val group = createMockInventoryGroup(
                name = "no_parent",
                worlds = setOf("world"),
                parent = null
            )
            every { groupService.getGroupApi("no_parent") } returns group

            val result = groupCommand.execute(plugin, sender, arrayOf("info", "no_parent"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-info-parent", "parent" to "none") }
        }

        @Test
        @DisplayName("Should handle group not found")
        fun handleGroupNotFound() = runTest {
            every { groupService.getGroupApi("nonexistent") } returns null

            val result = groupCommand.execute(plugin, sender, arrayOf("info", "nonexistent"))

            assertTrue(result)
            verify { messageService.send(sender, "group-not-found", "group" to "nonexistent") }
        }

        @Test
        @DisplayName("Should handle INFO action case-insensitively")
        fun handleInfoCaseInsensitively() = runTest {
            every { groupService.getGroupApi("test") } returns null

            val result = groupCommand.execute(plugin, sender, arrayOf("INFO", "test"))

            assertTrue(result)
            verify { messageService.send(sender, "group-not-found", "group" to "test") }
        }

        @Test
        @DisplayName("Should display priority correctly")
        fun displayPriorityCorrectly() = runTest {
            val group = createMockInventoryGroup(
                name = "high_priority",
                priority = 100
            )
            every { groupService.getGroupApi("high_priority") } returns group

            val result = groupCommand.execute(plugin, sender, arrayOf("info", "high_priority"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-info-priority", "priority" to "100") }
        }

        @Test
        @DisplayName("Should display negative priority correctly")
        fun displayNegativePriorityCorrectly() = runTest {
            val group = createMockInventoryGroup(
                name = "low_priority",
                priority = -5
            )
            every { groupService.getGroupApi("low_priority") } returns group

            val result = groupCommand.execute(plugin, sender, arrayOf("info", "low_priority"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "group-info-priority", "priority" to "-5") }
        }
    }

    // =========================================================================
    // Execute with Unknown Action Tests
    // =========================================================================

    @Nested
    @DisplayName("Execute with Unknown Action")
    inner class ExecuteUnknownActionTests {

        @Test
        @DisplayName("Should show usage for unknown action")
        fun showUsageForUnknownAction() = runTest {
            val result = groupCommand.execute(plugin, sender, arrayOf("unknown"))

            assertTrue(result)
            verify {
                messageService.send(
                    sender,
                    "invalid-syntax",
                    "usage" to "/xinv group <create|delete|list|info> [name]"
                )
            }
        }

        @Test
        @DisplayName("Should show usage for misspelled action")
        fun showUsageForMisspelledAction() = runTest {
            val result = groupCommand.execute(plugin, sender, arrayOf("crete"))

            assertTrue(result)
            verify {
                messageService.send(
                    sender,
                    "invalid-syntax",
                    "usage" to "/xinv group <create|delete|list|info> [name]"
                )
            }
        }

        @Test
        @DisplayName("Should show usage for empty string action")
        fun showUsageForEmptyStringAction() = runTest {
            val result = groupCommand.execute(plugin, sender, arrayOf(""))

            assertTrue(result)
            verify {
                messageService.send(
                    sender,
                    "invalid-syntax",
                    "usage" to "/xinv group <create|delete|list|info> [name]"
                )
            }
        }
    }

    // =========================================================================
    // Tab Complete Tests
    // =========================================================================

    @Nested
    @DisplayName("Tab Complete")
    inner class TabCompleteTests {

        @Test
        @DisplayName("Should return all actions when args is empty string")
        fun returnAllActionsWhenArgsEmpty() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf(""))

            assertEquals(4, completions.size)
            assertTrue(completions.contains("create"))
            assertTrue(completions.contains("delete"))
            assertTrue(completions.contains("list"))
            assertTrue(completions.contains("info"))
        }

        @Test
        @DisplayName("Should filter actions by prefix")
        fun filterActionsByPrefix() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("c"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("create"))
        }

        @Test
        @DisplayName("Should filter actions with 'd' prefix")
        fun filterActionsWithDPrefix() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("d"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("delete"))
        }

        @Test
        @DisplayName("Should filter actions with 'l' prefix")
        fun filterActionsWithLPrefix() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("l"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("list"))
        }

        @Test
        @DisplayName("Should filter actions with 'i' prefix")
        fun filterActionsWithIPrefix() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("i"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("info"))
        }

        @Test
        @DisplayName("Should return empty list when no actions match")
        fun returnEmptyWhenNoActionsMatch() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("x"))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should be case-insensitive for action filtering")
        fun beCaseInsensitiveForActionFiltering() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("C"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("create"))
        }

        @Test
        @DisplayName("Should return group names for 'delete' second argument")
        fun returnGroupNamesForDeleteSecondArg() {
            val groups = listOf(
                createMockGroup("survival"),
                createMockGroup("creative"),
                createMockGroup("adventure")
            )
            every { groupService.getAllGroups() } returns groups

            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("delete", ""))

            assertEquals(3, completions.size)
            assertTrue(completions.contains("survival"))
            assertTrue(completions.contains("creative"))
            assertTrue(completions.contains("adventure"))
        }

        @Test
        @DisplayName("Should filter group names for 'delete' by prefix")
        fun filterGroupNamesForDeleteByPrefix() {
            val groups = listOf(
                createMockGroup("survival"),
                createMockGroup("creative"),
                createMockGroup("skyblock")
            )
            every { groupService.getAllGroups() } returns groups

            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("delete", "s"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("survival"))
            assertTrue(completions.contains("skyblock"))
        }

        @Test
        @DisplayName("Should return group names for 'info' second argument")
        fun returnGroupNamesForInfoSecondArg() {
            val groups = listOf(
                createMockGroup("survival"),
                createMockGroup("creative")
            )
            every { groupService.getAllGroups() } returns groups

            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("info", ""))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("survival"))
            assertTrue(completions.contains("creative"))
        }

        @Test
        @DisplayName("Should filter group names for 'info' by prefix")
        fun filterGroupNamesForInfoByPrefix() {
            val groups = listOf(
                createMockGroup("survival"),
                createMockGroup("creative"),
                createMockGroup("skyblock")
            )
            every { groupService.getAllGroups() } returns groups

            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("info", "c"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("creative"))
        }

        @Test
        @DisplayName("Should return empty list for 'create' second argument")
        fun returnEmptyForCreateSecondArg() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("create", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list for 'list' second argument")
        fun returnEmptyForListSecondArg() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("list", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list for third argument and beyond")
        fun returnEmptyForThirdArg() {
            val completions = groupCommand.tabComplete(
                plugin, sender, arrayOf("delete", "group", "extra")
            )

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should be case-insensitive for action in second arg completion")
        fun beCaseInsensitiveForActionInSecondArgCompletion() {
            val groups = listOf(createMockGroup("test"))
            every { groupService.getAllGroups() } returns groups

            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("DELETE", ""))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("test"))
        }

        @Test
        @DisplayName("Should be case-insensitive for group name filtering")
        fun beCaseInsensitiveForGroupNameFiltering() {
            val groups = listOf(
                createMockGroup("Survival"),
                createMockGroup("Creative")
            )
            every { groupService.getAllGroups() } returns groups

            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("info", "S"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("Survival"))
        }

        @Test
        @DisplayName("Should handle empty groups list")
        fun handleEmptyGroupsList() {
            every { groupService.getAllGroups() } returns emptyList()

            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("delete", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list for unknown action with second arg")
        fun returnEmptyForUnknownActionWithSecondArg() {
            val completions = groupCommand.tabComplete(plugin, sender, arrayOf("unknown", ""))

            assertTrue(completions.isEmpty())
        }
    }

    // =========================================================================
    // Permission Check Tests
    // =========================================================================

    @Nested
    @DisplayName("Permission Check")
    inner class PermissionCheckTests {

        @Test
        @DisplayName("Should check correct permission via hasPermission")
        fun checkCorrectPermission() {
            every { sender.hasPermission("xinventories.command.group") } returns true

            val result = groupCommand.hasPermission(sender)

            assertTrue(result)
            verify { sender.hasPermission("xinventories.command.group") }
        }

        @Test
        @DisplayName("Should return false when permission denied")
        fun returnFalseWhenPermissionDenied() {
            every { sender.hasPermission("xinventories.command.group") } returns false

            val result = groupCommand.hasPermission(sender)

            assertFalse(result)
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle group name with special characters")
        fun handleGroupNameWithSpecialCharacters() = runTest {
            every { groupService.createGroup("my-group_v2") } returns Result.success(
                createMockInventoryGroup("my-group_v2")
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("create", "my-group_v2"))

            assertTrue(result)
            verify { groupService.createGroup("my-group_v2") }
            verify { messageService.send(sender, "group-created", "group" to "my-group_v2") }
        }

        @Test
        @DisplayName("Should handle numeric group name")
        fun handleNumericGroupName() = runTest {
            every { groupService.createGroup("123") } returns Result.success(
                createMockInventoryGroup("123")
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("create", "123"))

            assertTrue(result)
            verify { groupService.createGroup("123") }
        }

        @Test
        @DisplayName("Should handle very long group name")
        fun handleVeryLongGroupName() = runTest {
            val longName = "a".repeat(100)
            every { groupService.createGroup(longName) } returns Result.success(
                createMockInventoryGroup(longName)
            )

            val result = groupCommand.execute(plugin, sender, arrayOf("create", longName))

            assertTrue(result)
            verify { groupService.createGroup(longName) }
        }

        @Test
        @DisplayName("Should handle extra arguments after group name for create")
        fun handleExtraArgumentsAfterCreate() = runTest {
            every { groupService.createGroup("test") } returns Result.success(
                createMockInventoryGroup("test")
            )

            // Extra arguments after name should be ignored
            val result = groupCommand.execute(
                plugin, sender, arrayOf("create", "test", "extra", "args")
            )

            assertTrue(result)
            verify { groupService.createGroup("test") }
        }

        @Test
        @DisplayName("Should handle extra arguments after group name for delete")
        fun handleExtraArgumentsAfterDelete() = runTest {
            every { groupService.deleteGroup("test") } returns Result.success(Unit)

            // Extra arguments after name should be ignored
            val result = groupCommand.execute(
                plugin, sender, arrayOf("delete", "test", "extra", "args")
            )

            assertTrue(result)
            verify { groupService.deleteGroup("test") }
        }

        @Test
        @DisplayName("Should handle group with many worlds in list")
        fun handleGroupWithManyWorldsInList() = runTest {
            val manyWorlds = (1..50).map { "world_$it" }.toSet()
            val groups = listOf(
                createMockInventoryGroup("big_group", manyWorlds)
            )
            every { groupService.getAllGroupsApi() } returns groups

            val result = groupCommand.execute(plugin, sender, arrayOf("list"))

            assertTrue(result)
            verify {
                messageService.sendRaw(
                    sender,
                    "group-list-entry",
                    "group" to "big_group",
                    "count" to "50"
                )
            }
        }

        @Test
        @DisplayName("Should handle group info with many patterns")
        fun handleGroupInfoWithManyPatterns() = runTest {
            val manyPatterns = (1..10).map { "pattern_$it.*" }
            val group = createMockInventoryGroup(
                name = "pattern_heavy",
                patterns = manyPatterns
            )
            every { groupService.getGroupApi("pattern_heavy") } returns group

            val result = groupCommand.execute(plugin, sender, arrayOf("info", "pattern_heavy"))

            assertTrue(result)
            verify {
                messageService.sendRaw(
                    sender,
                    "group-info-patterns",
                    "patterns" to manyPatterns.joinToString(", ")
                )
            }
        }

        @Test
        @DisplayName("Should always return true from execute")
        fun alwaysReturnTrueFromExecute() = runTest {
            // Test various scenarios all return true
            assertTrue(groupCommand.execute(plugin, sender, emptyArray()))
            assertTrue(groupCommand.execute(plugin, sender, arrayOf("unknown")))
            assertTrue(groupCommand.execute(plugin, sender, arrayOf("list")))
            assertTrue(groupCommand.execute(plugin, sender, arrayOf("create")))

            every { groupService.getGroupApi("test") } returns null
            assertTrue(groupCommand.execute(plugin, sender, arrayOf("info", "test")))
        }
    }

    // =========================================================================
    // Integration-like Tests
    // =========================================================================

    @Nested
    @DisplayName("Full Workflow Tests")
    inner class FullWorkflowTests {

        @Test
        @DisplayName("Should handle full create and info workflow")
        fun handleFullCreateAndInfoWorkflow() = runTest {
            val newGroup = createMockInventoryGroup(
                name = "newgroup",
                worlds = emptySet(),
                patterns = emptyList(),
                priority = 0,
                parent = null
            )

            // Create
            every { groupService.createGroup("newgroup") } returns Result.success(newGroup)
            val createResult = groupCommand.execute(plugin, sender, arrayOf("create", "newgroup"))
            assertTrue(createResult)
            verify { messageService.send(sender, "group-created", "group" to "newgroup") }

            // Info
            every { groupService.getGroupApi("newgroup") } returns newGroup
            val infoResult = groupCommand.execute(plugin, sender, arrayOf("info", "newgroup"))
            assertTrue(infoResult)
            verify { messageService.sendRaw(sender, "group-info-header", "group" to "newgroup") }
        }

        @Test
        @DisplayName("Should handle create duplicate and then delete workflow")
        fun handleCreateDuplicateAndDeleteWorkflow() = runTest {
            // First create succeeds
            every { groupService.createGroup("mygroup") } returns Result.success(
                createMockInventoryGroup("mygroup")
            )
            groupCommand.execute(plugin, sender, arrayOf("create", "mygroup"))
            verify { messageService.send(sender, "group-created", "group" to "mygroup") }

            // Second create fails (duplicate)
            every { groupService.createGroup("mygroup") } returns Result.failure(
                IllegalArgumentException("already exists")
            )
            groupCommand.execute(plugin, sender, arrayOf("create", "mygroup"))
            verify { messageService.send(sender, "group-already-exists", "group" to "mygroup") }

            // Delete succeeds
            every { groupService.deleteGroup("mygroup") } returns Result.success(Unit)
            groupCommand.execute(plugin, sender, arrayOf("delete", "mygroup"))
            verify { messageService.send(sender, "group-deleted", "group" to "mygroup") }
        }
    }
}

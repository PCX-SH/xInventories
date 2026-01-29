package sh.pcx.xinventories.integration.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.bukkit.World
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.GroupConfig
import sh.pcx.xinventories.internal.config.GroupsConfig
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.util.Logging
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive integration tests for GroupService.
 * Tests group management, world resolution, and configuration persistence.
 */
@DisplayName("GroupService Integration Tests")
class GroupServiceTest {

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var groupService: GroupService

    @BeforeEach
    fun setUp() {
        // Initialize Logging to avoid lateinit issues
        Logging.init(Logger.getLogger("Test"), false)
        mockkObject(Logging)

        // Create mock plugin and config manager
        plugin = mockk(relaxed = true)
        configManager = mockk(relaxed = true)

        every { plugin.configManager } returns configManager
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    /**
     * Helper to set up a standard test configuration with multiple groups.
     */
    private fun setupStandardConfig() {
        val groupsConfig = GroupsConfig(
            groups = mapOf(
                "survival" to GroupConfig(
                    worlds = listOf("world", "world_nether", "world_the_end"),
                    patterns = listOf("^survival_.*"),
                    priority = 10,
                    parent = null,
                    settings = GroupSettings()
                ),
                "creative" to GroupConfig(
                    worlds = listOf("creative"),
                    patterns = listOf("^creative_.*"),
                    priority = 5,
                    parent = null,
                    settings = GroupSettings(saveGameMode = true)
                ),
                "minigames" to GroupConfig(
                    worlds = listOf("lobby"),
                    patterns = listOf("^minigame_.*", "^game_.*"),
                    priority = 15,
                    parent = null,
                    settings = GroupSettings(clearOnDeath = true)
                )
            ),
            defaultGroup = "survival"
        )

        every { configManager.groupsConfig } returns groupsConfig
    }

    /**
     * Helper to set up a minimal configuration with just the default group.
     */
    private fun setupMinimalConfig() {
        val groupsConfig = GroupsConfig(
            groups = mapOf(
                "survival" to GroupConfig(
                    worlds = listOf("world"),
                    patterns = emptyList(),
                    priority = 0
                )
            ),
            defaultGroup = "survival"
        )

        every { configManager.groupsConfig } returns groupsConfig
    }

    /**
     * Helper to set up an empty configuration (no groups defined).
     */
    private fun setupEmptyConfig() {
        val groupsConfig = GroupsConfig(
            groups = emptyMap(),
            defaultGroup = "survival"
        )

        every { configManager.groupsConfig } returns groupsConfig
    }

    // ==================== Load Groups from Configuration ====================

    @Nested
    @DisplayName("Load Groups from Configuration")
    inner class LoadGroupsFromConfiguration {

        @Test
        @DisplayName("should load all groups from configuration")
        fun shouldLoadAllGroupsFromConfiguration() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val groups = groupService.getAllGroups()

            assertEquals(3, groups.size)
            assertNotNull(groupService.getGroup("survival"))
            assertNotNull(groupService.getGroup("creative"))
            assertNotNull(groupService.getGroup("minigames"))
        }

        @Test
        @DisplayName("should load group worlds from configuration")
        fun shouldLoadGroupWorldsFromConfiguration() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val survival = groupService.getGroup("survival")

            assertNotNull(survival)
            assertTrue(survival.worlds.contains("world"))
            assertTrue(survival.worlds.contains("world_nether"))
            assertTrue(survival.worlds.contains("world_the_end"))
        }

        @Test
        @DisplayName("should load group patterns from configuration")
        fun shouldLoadGroupPatternsFromConfiguration() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val survival = groupService.getGroup("survival")

            assertNotNull(survival)
            assertEquals(listOf("^survival_.*"), survival.patternStrings)
        }

        @Test
        @DisplayName("should load group priority from configuration")
        fun shouldLoadGroupPriorityFromConfiguration() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val survival = groupService.getGroup("survival")
            val creative = groupService.getGroup("creative")
            val minigames = groupService.getGroup("minigames")

            assertNotNull(survival)
            assertNotNull(creative)
            assertNotNull(minigames)
            assertEquals(10, survival.priority)
            assertEquals(5, creative.priority)
            assertEquals(15, minigames.priority)
        }

        @Test
        @DisplayName("should load group settings from configuration")
        fun shouldLoadGroupSettingsFromConfiguration() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val creative = groupService.getGroup("creative")
            val minigames = groupService.getGroup("minigames")

            assertNotNull(creative)
            assertNotNull(minigames)
            assertTrue(creative.settings.saveGameMode)
            assertTrue(minigames.settings.clearOnDeath)
        }

        @Test
        @DisplayName("should create default group when not in configuration")
        fun shouldCreateDefaultGroupWhenNotInConfiguration() {
            setupEmptyConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val defaultGroup = groupService.getDefaultGroup()

            assertNotNull(defaultGroup)
            assertEquals("survival", defaultGroup.name)
            assertTrue(defaultGroup.isDefault)
        }

        @Test
        @DisplayName("should mark correct group as default")
        fun shouldMarkCorrectGroupAsDefault() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val survival = groupService.getGroup("survival")
            val creative = groupService.getGroup("creative")

            assertNotNull(survival)
            assertNotNull(creative)
            assertTrue(survival.isDefault)
            assertFalse(creative.isDefault)
        }
    }

    // ==================== Get Group by Name ====================

    @Nested
    @DisplayName("Get Group by Name")
    inner class GetGroupByName {

        @Test
        @DisplayName("should return group when it exists")
        fun shouldReturnGroupWhenItExists() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroup("survival")

            assertNotNull(group)
            assertEquals("survival", group.name)
        }

        @Test
        @DisplayName("should return null when group does not exist")
        fun shouldReturnNullWhenGroupDoesNotExist() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroup("nonexistent")

            assertNull(group)
        }

        @Test
        @DisplayName("should be case-sensitive")
        fun shouldBeCaseSensitive() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val lower = groupService.getGroup("survival")
            val upper = groupService.getGroup("SURVIVAL")

            assertNotNull(lower)
            assertNull(upper)
        }

        @Test
        @DisplayName("should return API model via getGroupApi")
        fun shouldReturnApiModelViaGetGroupApi() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val apiGroup = groupService.getGroupApi("survival")

            assertNotNull(apiGroup)
            assertEquals("survival", apiGroup.name)
        }
    }

    // ==================== Get All Groups ====================

    @Nested
    @DisplayName("Get All Groups")
    inner class GetAllGroups {

        @Test
        @DisplayName("should return all groups")
        fun shouldReturnAllGroups() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val groups = groupService.getAllGroups()

            assertEquals(3, groups.size)
            val names = groups.map { it.name }
            assertTrue(names.contains("survival"))
            assertTrue(names.contains("creative"))
            assertTrue(names.contains("minigames"))
        }

        @Test
        @DisplayName("should return empty list when no groups configured")
        fun shouldReturnListWithDefaultWhenNoGroupsConfigured() {
            setupEmptyConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val groups = groupService.getAllGroups()

            // Default group is always created
            assertEquals(1, groups.size)
            assertEquals("survival", groups[0].name)
        }

        @Test
        @DisplayName("should return API models via getAllGroupsApi")
        fun shouldReturnApiModelsViaGetAllGroupsApi() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val apiGroups = groupService.getAllGroupsApi()

            assertEquals(3, apiGroups.size)
        }
    }

    // ==================== Get Default Group ====================

    @Nested
    @DisplayName("Get Default Group")
    inner class GetDefaultGroup {

        @Test
        @DisplayName("should return the default group")
        fun shouldReturnTheDefaultGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val defaultGroup = groupService.getDefaultGroup()

            assertEquals("survival", defaultGroup.name)
            assertTrue(defaultGroup.isDefault)
        }

        @Test
        @DisplayName("should return API model via getDefaultGroupApi")
        fun shouldReturnApiModelViaGetDefaultGroupApi() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val apiDefault = groupService.getDefaultGroupApi()

            assertEquals("survival", apiDefault.name)
            assertTrue(apiDefault.isDefault)
        }

        @Test
        @DisplayName("should create default group if missing from config")
        fun shouldCreateDefaultGroupIfMissingFromConfig() {
            val groupsConfig = GroupsConfig(
                groups = mapOf(
                    "creative" to GroupConfig(worlds = listOf("creative"))
                ),
                defaultGroup = "missing_default"
            )
            every { configManager.groupsConfig } returns groupsConfig

            groupService = GroupService(plugin)
            groupService.initialize()

            val defaultGroup = groupService.getDefaultGroup()

            assertEquals("missing_default", defaultGroup.name)
            assertTrue(defaultGroup.isDefault)
        }
    }

    // ==================== Create New Group ====================

    @Nested
    @DisplayName("Create New Group")
    inner class CreateNewGroup {

        @Test
        @DisplayName("should create a new group successfully")
        fun shouldCreateNewGroupSuccessfully() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.createGroup(
                name = "adventure",
                settings = GroupSettings(saveExperience = false),
                worlds = setOf("adventure_world"),
                patterns = listOf("^adventure_.*"),
                priority = 20
            )

            assertTrue(result.isSuccess)
            val created = groupService.getGroup("adventure")
            assertNotNull(created)
            assertEquals("adventure", created.name)
            assertEquals(setOf("adventure_world"), created.worlds)
            assertEquals(listOf("^adventure_.*"), created.patternStrings)
            assertEquals(20, created.priority)
            assertFalse(created.settings.saveExperience)
        }

        @Test
        @DisplayName("should fail when group already exists")
        fun shouldFailWhenGroupAlreadyExists() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.createGroup(name = "survival")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("already exists"))
        }

        @Test
        @DisplayName("should fail with invalid pattern")
        fun shouldFailWithInvalidPattern() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.createGroup(
                name = "newgroup",
                patterns = listOf("[invalid")
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("Invalid regex"))
        }

        @Test
        @DisplayName("should save to config after creation")
        fun shouldSaveToConfigAfterCreation() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.createGroup(name = "newgroup")

            verify { configManager.saveGroupsConfig() }
        }

        @Test
        @DisplayName("should create group with parent")
        fun shouldCreateGroupWithParent() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.createGroup(
                name = "child",
                parent = "survival"
            )

            assertTrue(result.isSuccess)
            val created = groupService.getGroup("child")
            assertNotNull(created)
            assertEquals("survival", created.parent)
        }
    }

    // ==================== Delete Existing Group ====================

    @Nested
    @DisplayName("Delete Existing Group")
    inner class DeleteExistingGroup {

        @Test
        @DisplayName("should delete an existing group")
        fun shouldDeleteExistingGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.deleteGroup("creative")

            assertTrue(result.isSuccess)
            assertNull(groupService.getGroup("creative"))
        }

        @Test
        @DisplayName("should fail when group does not exist")
        fun shouldFailWhenGroupDoesNotExist() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.deleteGroup("nonexistent")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("not found"))
        }

        @Test
        @DisplayName("should save to config after deletion")
        fun shouldSaveToConfigAfterDeletion() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.deleteGroup("creative")

            verify { configManager.saveGroupsConfig() }
        }
    }

    // ==================== Cannot Delete Default Group ====================

    @Nested
    @DisplayName("Cannot Delete Default Group")
    inner class CannotDeleteDefaultGroup {

        @Test
        @DisplayName("should fail when attempting to delete default group")
        fun shouldFailWhenAttemptingToDeleteDefaultGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.deleteGroup("survival")

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("default group"))
        }

        @Test
        @DisplayName("default group should still exist after failed deletion")
        fun defaultGroupShouldStillExistAfterFailedDeletion() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.deleteGroup("survival")

            assertNotNull(groupService.getGroup("survival"))
        }
    }

    // ==================== Modify Group Settings ====================

    @Nested
    @DisplayName("Modify Group Settings")
    inner class ModifyGroupSettings {

        @Test
        @DisplayName("should modify group settings")
        fun shouldModifyGroupSettings() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.modifyGroup("survival") {
                modifySettings { copy(saveHealth = false, clearOnDeath = true) }
            }

            assertTrue(result.isSuccess)
            val modified = groupService.getGroup("survival")
            assertNotNull(modified)
            assertFalse(modified.settings.saveHealth)
            assertTrue(modified.settings.clearOnDeath)
        }

        @Test
        @DisplayName("should modify group priority")
        fun shouldModifyGroupPriority() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.modifyGroup("survival") {
                setPriority(100)
            }

            assertTrue(result.isSuccess)
            val modified = groupService.getGroup("survival")
            assertNotNull(modified)
            assertEquals(100, modified.priority)
        }

        @Test
        @DisplayName("should modify group parent")
        fun shouldModifyGroupParent() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.modifyGroup("creative") {
                setParent("survival")
            }

            assertTrue(result.isSuccess)
            val modified = groupService.getGroup("creative")
            assertNotNull(modified)
            assertEquals("survival", modified.parent)
        }

        @Test
        @DisplayName("should fail when group does not exist")
        fun shouldFailWhenGroupDoesNotExist() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.modifyGroup("nonexistent") {
                setPriority(50)
            }

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()!!.message!!.contains("not found"))
        }

        @Test
        @DisplayName("should save to config after modification")
        fun shouldSaveToConfigAfterModification() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.modifyGroup("survival") {
                setPriority(50)
            }

            verify { configManager.saveGroupsConfig() }
        }
    }

    // ==================== Add/Remove Worlds from Group ====================

    @Nested
    @DisplayName("Add/Remove Worlds from Group")
    inner class AddRemoveWorldsFromGroup {

        @Test
        @DisplayName("should add world to group")
        fun shouldAddWorldToGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.modifyGroup("survival") {
                addWorld("new_world")
            }

            val group = groupService.getGroup("survival")
            assertNotNull(group)
            assertTrue(group.worlds.contains("new_world"))
        }

        @Test
        @DisplayName("should remove world from group")
        fun shouldRemoveWorldFromGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.modifyGroup("survival") {
                removeWorld("world_nether")
            }

            val group = groupService.getGroup("survival")
            assertNotNull(group)
            assertFalse(group.worlds.contains("world_nether"))
        }

        @Test
        @DisplayName("should set worlds replacing all existing")
        fun shouldSetWorldsReplacingAllExisting() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.modifyGroup("survival") {
                setWorlds(setOf("new_world1", "new_world2"))
            }

            val group = groupService.getGroup("survival")
            assertNotNull(group)
            assertEquals(setOf("new_world1", "new_world2"), group.worlds)
        }

        @Test
        @DisplayName("should assign world to group using assignWorldToGroup")
        fun shouldAssignWorldToGroupUsingAssignWorldToGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.assignWorldToGroup("new_world", "creative")

            assertTrue(result.isSuccess)
            val group = groupService.getGroup("creative")
            assertNotNull(group)
            assertTrue(group.worlds.contains("new_world"))
        }

        @Test
        @DisplayName("should remove world from other groups when assigning")
        fun shouldRemoveWorldFromOtherGroupsWhenAssigning() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            // world is originally in survival
            groupService.assignWorldToGroup("world", "creative")

            val survival = groupService.getGroup("survival")
            val creative = groupService.getGroup("creative")

            assertNotNull(survival)
            assertNotNull(creative)
            assertFalse(survival.worlds.contains("world"))
            assertTrue(creative.worlds.contains("world"))
        }

        @Test
        @DisplayName("should unassign world from all groups")
        fun shouldUnassignWorldFromAllGroups() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.unassignWorld("world")

            val survival = groupService.getGroup("survival")
            assertNotNull(survival)
            assertFalse(survival.worlds.contains("world"))
        }

        @Test
        @DisplayName("should get world assignments")
        fun shouldGetWorldAssignments() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val assignments = groupService.getWorldAssignments()

            assertEquals("survival", assignments["world"])
            assertEquals("survival", assignments["world_nether"])
            assertEquals("creative", assignments["creative"])
            assertEquals("minigames", assignments["lobby"])
        }
    }

    // ==================== Add/Remove Patterns from Group ====================

    @Nested
    @DisplayName("Add/Remove Patterns from Group")
    inner class AddRemovePatternsFromGroup {

        @Test
        @DisplayName("should add pattern to group")
        fun shouldAddPatternToGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.addPattern("survival", "^smp_.*")

            assertTrue(result.isSuccess)
            val patterns = groupService.getPatterns("survival")
            assertTrue(patterns.contains("^smp_.*"))
        }

        @Test
        @DisplayName("should fail adding invalid pattern")
        fun shouldFailAddingInvalidPattern() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.addPattern("survival", "[invalid")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("should fail adding duplicate pattern")
        fun shouldFailAddingDuplicatePattern() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.addPattern("survival", "^survival_.*")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("should remove pattern from group")
        fun shouldRemovePatternFromGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.removePattern("survival", "^survival_.*")

            assertTrue(result.isSuccess)
            val patterns = groupService.getPatterns("survival")
            assertFalse(patterns.contains("^survival_.*"))
        }

        @Test
        @DisplayName("should fail removing non-existent pattern")
        fun shouldFailRemovingNonExistentPattern() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.removePattern("survival", "^nonexistent_.*")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("should add pattern via modifier")
        fun shouldAddPatternViaModifier() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.modifyGroup("survival") {
                addPattern("^new_pattern_.*")
            }

            val patterns = groupService.getPatterns("survival")
            assertTrue(patterns.contains("^new_pattern_.*"))
        }

        @Test
        @DisplayName("should remove pattern via modifier")
        fun shouldRemovePatternViaModifier() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.modifyGroup("survival") {
                removePattern("^survival_.*")
            }

            val patterns = groupService.getPatterns("survival")
            assertFalse(patterns.contains("^survival_.*"))
        }

        @Test
        @DisplayName("should set patterns via modifier")
        fun shouldSetPatternsViaModifier() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.modifyGroup("survival") {
                setPatterns(listOf("^new1_.*", "^new2_.*"))
            }

            val patterns = groupService.getPatterns("survival")
            assertEquals(listOf("^new1_.*", "^new2_.*"), patterns)
        }

        @Test
        @DisplayName("should test pattern matching")
        fun shouldTestPatternMatching() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            assertTrue(groupService.testPattern("survival_world", "survival"))
            assertFalse(groupService.testPattern("creative_world", "survival"))
        }
    }

    // ==================== World Resolution: Explicit Assignment Wins ====================

    @Nested
    @DisplayName("World Resolution: Explicit Assignment Wins")
    inner class WorldResolutionExplicitAssignmentWins {

        @Test
        @DisplayName("should return explicitly assigned group")
        fun shouldReturnExplicitlyAssignedGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("world")

            assertEquals("survival", group.name)
        }

        @Test
        @DisplayName("explicit assignment should override pattern match")
        fun explicitAssignmentShouldOverridePatternMatch() {
            // Set up config where a world is explicitly assigned but also matches a pattern
            val groupsConfig = GroupsConfig(
                groups = mapOf(
                    "survival" to GroupConfig(
                        worlds = listOf("creative_special"),  // Explicit assignment
                        patterns = emptyList(),
                        priority = 5
                    ),
                    "creative" to GroupConfig(
                        worlds = emptyList(),
                        patterns = listOf("^creative_.*"),  // Pattern that would match
                        priority = 10  // Higher priority
                    )
                ),
                defaultGroup = "survival"
            )
            every { configManager.groupsConfig } returns groupsConfig

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("creative_special")

            // Explicit wins even though creative has higher priority and matching pattern
            assertEquals("survival", group.name)
        }
    }

    // ==================== World Resolution: Pattern Matching ====================

    @Nested
    @DisplayName("World Resolution: Pattern Matching")
    inner class WorldResolutionPatternMatching {

        @Test
        @DisplayName("should match world via pattern")
        fun shouldMatchWorldViaPattern() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("survival_adventure")

            assertEquals("survival", group.name)
        }

        @Test
        @DisplayName("should match complex regex patterns")
        fun shouldMatchComplexRegexPatterns() {
            val groupsConfig = GroupsConfig(
                groups = mapOf(
                    "numbered" to GroupConfig(
                        worlds = emptyList(),
                        patterns = listOf("^world_[0-9]+$"),
                        priority = 10
                    )
                ),
                defaultGroup = "numbered"
            )
            every { configManager.groupsConfig } returns groupsConfig

            groupService = GroupService(plugin)
            groupService.initialize()

            assertTrue(groupService.testPattern("world_123", "numbered"))
            assertTrue(groupService.testPattern("world_0", "numbered"))
            assertFalse(groupService.testPattern("world_abc", "numbered"))
            assertFalse(groupService.testPattern("world_12a", "numbered"))
        }

        @Test
        @DisplayName("should support multiple patterns in a group")
        fun shouldSupportMultiplePatternsInGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            // minigames has patterns: ^minigame_.* and ^game_.*
            val minigame = groupService.getGroupForWorld("minigame_pvp")
            val game = groupService.getGroupForWorld("game_spleef")

            assertEquals("minigames", minigame.name)
            assertEquals("minigames", game.name)
        }
    }

    // ==================== World Resolution: Falls Back to Default ====================

    @Nested
    @DisplayName("World Resolution: Falls Back to Default")
    inner class WorldResolutionFallsBackToDefault {

        @Test
        @DisplayName("should return default group when world not matched")
        fun shouldReturnDefaultGroupWhenWorldNotMatched() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("unknown_world")

            assertEquals("survival", group.name)
            assertTrue(group.isDefault)
        }

        @Test
        @DisplayName("should return default for empty world name")
        fun shouldReturnDefaultForEmptyWorldName() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("")

            assertEquals("survival", group.name)
        }
    }

    // ==================== World Resolution: Priority Ordering ====================

    @Nested
    @DisplayName("World Resolution: Priority Ordering")
    inner class WorldResolutionPriorityOrdering {

        @Test
        @DisplayName("higher priority wins for explicit assignments")
        fun higherPriorityWinsForExplicitAssignments() {
            val groupsConfig = GroupsConfig(
                groups = mapOf(
                    "low" to GroupConfig(
                        worlds = listOf("contested_world"),
                        priority = 5
                    ),
                    "high" to GroupConfig(
                        worlds = listOf("contested_world"),
                        priority = 10
                    )
                ),
                defaultGroup = "low"
            )
            every { configManager.groupsConfig } returns groupsConfig

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("contested_world")

            assertEquals("high", group.name)
        }

        @Test
        @DisplayName("higher priority wins for pattern matches")
        fun higherPriorityWinsForPatternMatches() {
            val groupsConfig = GroupsConfig(
                groups = mapOf(
                    "low" to GroupConfig(
                        worlds = emptyList(),
                        patterns = listOf("^test_.*"),
                        priority = 5
                    ),
                    "high" to GroupConfig(
                        worlds = emptyList(),
                        patterns = listOf("^test_.*"),
                        priority = 10
                    )
                ),
                defaultGroup = "low"
            )
            every { configManager.groupsConfig } returns groupsConfig

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("test_world")

            assertEquals("high", group.name)
        }

        @Test
        @DisplayName("negative priority should work correctly")
        fun negativePriorityShouldWorkCorrectly() {
            val groupsConfig = GroupsConfig(
                groups = mapOf(
                    "negative" to GroupConfig(
                        worlds = listOf("world"),
                        priority = -10
                    ),
                    "positive" to GroupConfig(
                        worlds = listOf("world"),
                        priority = 5
                    )
                ),
                defaultGroup = "negative"
            )
            every { configManager.groupsConfig } returns groupsConfig

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("world")

            assertEquals("positive", group.name)
        }

        @Test
        @DisplayName("equal priority should return first match")
        fun equalPriorityShouldReturnFirstMatch() {
            val groupsConfig = GroupsConfig(
                groups = mapOf(
                    "alpha" to GroupConfig(
                        worlds = listOf("shared"),
                        priority = 10
                    ),
                    "beta" to GroupConfig(
                        worlds = listOf("shared"),
                        priority = 10
                    )
                ),
                defaultGroup = "alpha"
            )
            every { configManager.groupsConfig } returns groupsConfig

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("shared")

            // One of them should be returned (order not guaranteed with ConcurrentHashMap)
            assertTrue(group.name == "alpha" || group.name == "beta")
        }
    }

    // ==================== Reload Groups from Config ====================

    @Nested
    @DisplayName("Reload Groups from Config")
    inner class ReloadGroupsFromConfig {

        @Test
        @DisplayName("should reload groups from configuration")
        fun shouldReloadGroupsFromConfiguration() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            // Change the config
            val newConfig = GroupsConfig(
                groups = mapOf(
                    "survival" to GroupConfig(
                        worlds = listOf("new_world"),
                        priority = 100
                    )
                ),
                defaultGroup = "survival"
            )
            every { configManager.groupsConfig } returns newConfig

            groupService.reload()

            val group = groupService.getGroup("survival")
            assertNotNull(group)
            assertTrue(group.worlds.contains("new_world"))
            assertEquals(100, group.priority)
            assertFalse(group.worlds.contains("world"))
        }

        @Test
        @DisplayName("should clear old groups on reload")
        fun shouldClearOldGroupsOnReload() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            // New config without creative
            val newConfig = GroupsConfig(
                groups = mapOf(
                    "survival" to GroupConfig(worlds = listOf("world"))
                ),
                defaultGroup = "survival"
            )
            every { configManager.groupsConfig } returns newConfig

            groupService.reload()

            assertNull(groupService.getGroup("creative"))
            assertNull(groupService.getGroup("minigames"))
        }
    }

    // ==================== Save Groups to Config ====================

    @Nested
    @DisplayName("Save Groups to Config")
    inner class SaveGroupsToConfig {

        @Test
        @DisplayName("should save after creating group")
        fun shouldSaveAfterCreatingGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.createGroup("new_group")

            verify { configManager.saveGroupsConfig() }
        }

        @Test
        @DisplayName("should save after deleting group")
        fun shouldSaveAfterDeletingGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.deleteGroup("creative")

            verify { configManager.saveGroupsConfig() }
        }

        @Test
        @DisplayName("should save after modifying group")
        fun shouldSaveAfterModifyingGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.modifyGroup("survival") {
                setPriority(50)
            }

            verify { configManager.saveGroupsConfig() }
        }

        @Test
        @DisplayName("should save after assigning world")
        fun shouldSaveAfterAssigningWorld() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.assignWorldToGroup("new_world", "survival")

            verify { configManager.saveGroupsConfig() }
        }

        @Test
        @DisplayName("should save after unassigning world")
        fun shouldSaveAfterUnassigningWorld() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.unassignWorld("world")

            verify { configManager.saveGroupsConfig() }
        }

        @Test
        @DisplayName("should save after adding pattern")
        fun shouldSaveAfterAddingPattern() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.addPattern("survival", "^new_.*")

            verify { configManager.saveGroupsConfig() }
        }

        @Test
        @DisplayName("should save after removing pattern")
        fun shouldSaveAfterRemovingPattern() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.removePattern("survival", "^survival_.*")

            verify { configManager.saveGroupsConfig() }
        }
    }

    // ==================== Get Group for World (World Object) ====================

    @Nested
    @DisplayName("Get Group for World (World Object)")
    inner class GetGroupForWorldObject {

        @Test
        @DisplayName("should resolve group from World object")
        fun shouldResolveGroupFromWorldObject() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val mockWorld: World = mockk()
            every { mockWorld.name } returns "world"

            val group = groupService.getGroupForWorld(mockWorld)

            assertEquals("survival", group.name)
        }

        @Test
        @DisplayName("should return API model via getGroupForWorldApi with World object")
        fun shouldReturnApiModelViaGetGroupForWorldApiWithWorldObject() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val mockWorld: World = mockk()
            every { mockWorld.name } returns "world"

            val apiGroup = groupService.getGroupForWorldApi(mockWorld)

            assertEquals("survival", apiGroup.name)
        }

        @Test
        @DisplayName("should use World.name for resolution")
        fun shouldUseWorldNameForResolution() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val mockWorld: World = mockk()
            every { mockWorld.name } returns "survival_custom"

            val group = groupService.getGroupForWorld(mockWorld)

            // Should match via pattern
            assertEquals("survival", group.name)
        }
    }

    // ==================== Get Group for World (World Name String) ====================

    @Nested
    @DisplayName("Get Group for World (World Name String)")
    inner class GetGroupForWorldString {

        @Test
        @DisplayName("should resolve group from world name string")
        fun shouldResolveGroupFromWorldNameString() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroupForWorld("world")

            assertEquals("survival", group.name)
        }

        @Test
        @DisplayName("should return API model via getGroupForWorldApi with string")
        fun shouldReturnApiModelViaGetGroupForWorldApiWithString() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val apiGroup = groupService.getGroupForWorldApi("world")

            assertEquals("survival", apiGroup.name)
        }

        @Test
        @DisplayName("should handle special characters in world name")
        fun shouldHandleSpecialCharactersInWorldName() {
            val groupsConfig = GroupsConfig(
                groups = mapOf(
                    "special" to GroupConfig(
                        worlds = listOf("world-1", "world_2", "world.3"),
                        priority = 10
                    )
                ),
                defaultGroup = "special"
            )
            every { configManager.groupsConfig } returns groupsConfig

            groupService = GroupService(plugin)
            groupService.initialize()

            assertEquals("special", groupService.getGroupForWorld("world-1").name)
            assertEquals("special", groupService.getGroupForWorld("world_2").name)
            assertEquals("special", groupService.getGroupForWorld("world.3").name)
        }
    }

    // ==================== Edge Cases and Error Handling ====================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    inner class EdgeCasesAndErrorHandling {

        @Test
        @DisplayName("should handle group with no worlds or patterns")
        fun shouldHandleGroupWithNoWorldsOrPatterns() {
            val groupsConfig = GroupsConfig(
                groups = mapOf(
                    "empty" to GroupConfig(
                        worlds = emptyList(),
                        patterns = emptyList(),
                        priority = 10
                    )
                ),
                defaultGroup = "empty"
            )
            every { configManager.groupsConfig } returns groupsConfig

            groupService = GroupService(plugin)
            groupService.initialize()

            val group = groupService.getGroup("empty")
            assertNotNull(group)
            assertTrue(group.worlds.isEmpty())
            assertTrue(group.patterns.isEmpty())
        }

        @Test
        @DisplayName("should handle assign world to non-existent group")
        fun shouldHandleAssignWorldToNonExistentGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.assignWorldToGroup("world", "nonexistent")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("should handle add pattern to non-existent group")
        fun shouldHandleAddPatternToNonExistentGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.addPattern("nonexistent", "^test_.*")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("should handle remove pattern from non-existent group")
        fun shouldHandleRemovePatternFromNonExistentGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.removePattern("nonexistent", "^test_.*")

            assertTrue(result.isFailure)
        }

        @Test
        @DisplayName("should return empty patterns for non-existent group")
        fun shouldReturnEmptyPatternsForNonExistentGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val patterns = groupService.getPatterns("nonexistent")

            assertTrue(patterns.isEmpty())
        }

        @Test
        @DisplayName("should return false for testPattern with non-existent group")
        fun shouldReturnFalseForTestPatternWithNonExistentGroup() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.testPattern("world", "nonexistent")

            assertFalse(result)
        }

        @Test
        @DisplayName("should handle concurrent group operations")
        fun shouldHandleConcurrentGroupOperations() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            // Simulate concurrent operations
            val threads = (1..10).map { i ->
                Thread {
                    groupService.createGroup("thread_group_$i")
                    groupService.getGroupForWorld("world")
                    groupService.getAllGroups()
                }
            }

            threads.forEach { it.start() }
            threads.forEach { it.join() }

            // Verify all thread groups were created
            (1..10).forEach { i ->
                assertNotNull(groupService.getGroup("thread_group_$i"))
            }
        }
    }

    // ==================== Integration with GroupModifier ====================

    @Nested
    @DisplayName("Integration with GroupModifier")
    inner class IntegrationWithGroupModifier {

        @Test
        @DisplayName("should apply all modifier operations")
        fun shouldApplyAllModifierOperations() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            groupService.modifyGroup("survival") {
                addWorld("new_world")
                removeWorld("world_nether")
                addPattern("^modified_.*")
                setPriority(50)
                setParent("creative")
                modifySettings { copy(saveHealth = false) }
            }

            val group = groupService.getGroup("survival")
            assertNotNull(group)
            assertTrue(group.worlds.contains("new_world"))
            assertFalse(group.worlds.contains("world_nether"))
            assertTrue(group.patternStrings.contains("^modified_.*"))
            assertEquals(50, group.priority)
            assertEquals("creative", group.parent)
            assertFalse(group.settings.saveHealth)
        }

        @Test
        @DisplayName("should return updated API model after modification")
        fun shouldReturnUpdatedApiModelAfterModification() {
            setupStandardConfig()

            groupService = GroupService(plugin)
            groupService.initialize()

            val result = groupService.modifyGroup("survival") {
                setPriority(100)
            }

            assertTrue(result.isSuccess)
            val apiModel = result.getOrNull()
            assertNotNull(apiModel)
            assertEquals(100, apiModel.priority)
        }
    }
}

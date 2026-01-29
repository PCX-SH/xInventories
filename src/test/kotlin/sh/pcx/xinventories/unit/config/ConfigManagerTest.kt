package sh.pcx.xinventories.unit.config

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.config.*
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Comprehensive unit tests for ConfigManager using MockK for mocking.
 * These tests focus on ConfigManager logic without full plugin initialization.
 */
@DisplayName("ConfigManager Tests")
class ConfigManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var plugin: XInventories
    private lateinit var configManager: ConfigManager
    private lateinit var mockConfig: FileConfiguration

    @BeforeEach
    fun setUp() {
        // Initialize Logging to avoid lateinit issues
        Logging.init(Logger.getLogger("Test"), false)
        mockkObject(Logging)

        // Create mock plugin
        plugin = mockk(relaxed = true)
        mockConfig = YamlConfiguration()

        // Set up plugin data folder
        val dataFolder = tempDir.resolve("plugin").toFile()
        dataFolder.mkdirs()

        every { plugin.dataFolder } returns dataFolder
        every { plugin.config } returns mockConfig
        every { plugin.reloadConfig() } returns Unit
        every { plugin.saveResource(any(), any()) } answers {
            // Simulate saving default resource
            val resourceName = firstArg<String>()
            val targetFile = File(dataFolder, resourceName)
            if (!targetFile.exists()) {
                targetFile.createNewFile()
            }
        }
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Main Config Tests ====================

    @Test
    @DisplayName("Should load main configuration with default values")
    fun loadMainConfigWithDefaults() {
        configManager = ConfigManager(plugin)
        val result = configManager.loadAll()

        assertTrue(result)
        assertNotNull(configManager.mainConfig)
        assertEquals(StorageType.YAML, configManager.mainConfig.storage.type)
    }

    @Test
    @DisplayName("Should parse YAML storage type")
    fun parseYamlStorageType() {
        mockConfig.set("storage.type", "YAML")

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        assertEquals(StorageType.YAML, configManager.mainConfig.storage.type)
    }

    @Test
    @DisplayName("Should parse SQLITE storage type")
    fun parseSqliteStorageType() {
        mockConfig.set("storage.type", "SQLITE")

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        assertEquals(StorageType.SQLITE, configManager.mainConfig.storage.type)
    }

    @Test
    @DisplayName("Should parse MYSQL storage type")
    fun parseMysqlStorageType() {
        mockConfig.set("storage.type", "MYSQL")

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        assertEquals(StorageType.MYSQL, configManager.mainConfig.storage.type)
    }

    @Test
    @DisplayName("Should default to YAML for invalid storage type")
    fun defaultToYamlForInvalidStorageType() {
        mockConfig.set("storage.type", "INVALID")

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        assertEquals(StorageType.YAML, configManager.mainConfig.storage.type)
    }

    @Test
    @DisplayName("Should parse SQLite configuration")
    fun parseSqliteConfig() {
        mockConfig.set("storage.sqlite.file", "custom/path.db")

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        assertEquals("custom/path.db", configManager.mainConfig.storage.sqlite.file)
    }

    @Test
    @DisplayName("Should parse MySQL configuration")
    fun parseMysqlConfig() {
        mockConfig.set("storage.mysql.host", "db.example.com")
        mockConfig.set("storage.mysql.port", 3307)
        mockConfig.set("storage.mysql.database", "mydb")
        mockConfig.set("storage.mysql.username", "admin")
        mockConfig.set("storage.mysql.password", "secret")

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val mysql = configManager.mainConfig.storage.mysql
        assertEquals("db.example.com", mysql.host)
        assertEquals(3307, mysql.port)
        assertEquals("mydb", mysql.database)
        assertEquals("admin", mysql.username)
        assertEquals("secret", mysql.password)
    }

    @Test
    @DisplayName("Should parse MySQL pool configuration")
    fun parseMysqlPoolConfig() {
        mockConfig.set("storage.mysql.pool.maximum-pool-size", 20)
        mockConfig.set("storage.mysql.pool.minimum-idle", 5)
        mockConfig.set("storage.mysql.pool.connection-timeout", 60000L)
        mockConfig.set("storage.mysql.pool.idle-timeout", 300000L)
        mockConfig.set("storage.mysql.pool.max-lifetime", 900000L)

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val pool = configManager.mainConfig.storage.mysql.pool
        assertEquals(20, pool.maximumPoolSize)
        assertEquals(5, pool.minimumIdle)
        assertEquals(60000L, pool.connectionTimeout)
        assertEquals(300000L, pool.idleTimeout)
        assertEquals(900000L, pool.maxLifetime)
    }

    @Test
    @DisplayName("Should parse cache configuration")
    fun parseCacheConfig() {
        mockConfig.set("cache.enabled", false)
        mockConfig.set("cache.max-size", 500)
        mockConfig.set("cache.ttl-minutes", 60)
        mockConfig.set("cache.write-behind-seconds", 10)

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val cache = configManager.mainConfig.cache
        assertFalse(cache.enabled)
        assertEquals(500, cache.maxSize)
        assertEquals(60, cache.ttlMinutes)
        assertEquals(10, cache.writeBehindSeconds)
    }

    @Test
    @DisplayName("Should parse features configuration")
    fun parseFeaturesConfig() {
        mockConfig.set("features.separate-gamemode-inventories", false)
        mockConfig.set("features.save-on-world-change", false)
        mockConfig.set("features.save-on-gamemode-change", false)
        mockConfig.set("features.async-saving", false)
        mockConfig.set("features.clear-on-death", true)
        mockConfig.set("features.admin-notifications", false)

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val features = configManager.mainConfig.features
        assertFalse(features.separateGamemodeInventories)
        assertFalse(features.saveOnWorldChange)
        assertFalse(features.saveOnGamemodeChange)
        assertFalse(features.asyncSaving)
        assertTrue(features.clearOnDeath)
        assertFalse(features.adminNotifications)
    }

    @Test
    @DisplayName("Should parse backup configuration")
    fun parseBackupConfig() {
        mockConfig.set("backup.auto-backup", false)
        mockConfig.set("backup.interval-hours", 12)
        mockConfig.set("backup.max-backups", 14)
        mockConfig.set("backup.compression", false)
        mockConfig.set("backup.directory", "custom-backups")

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val backup = configManager.mainConfig.backup
        assertFalse(backup.autoBackup)
        assertEquals(12, backup.intervalHours)
        assertEquals(14, backup.maxBackups)
        assertFalse(backup.compression)
        assertEquals("custom-backups", backup.directory)
    }

    @Test
    @DisplayName("Should parse performance configuration")
    fun parsePerformanceConfig() {
        mockConfig.set("performance.batch-size", 200)
        mockConfig.set("performance.thread-pool-size", 8)
        mockConfig.set("performance.save-delay-ticks", 5)

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val performance = configManager.mainConfig.performance
        assertEquals(200, performance.batchSize)
        assertEquals(8, performance.threadPoolSize)
        assertEquals(5, performance.saveDelayTicks)
    }

    @Test
    @DisplayName("Should parse debug setting")
    fun parseDebugSetting() {
        mockConfig.set("debug", true)

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        assertTrue(configManager.mainConfig.debug)
    }

    // ==================== Groups Config Tests ====================

    @Test
    @DisplayName("Should load groups configuration with defaults when file missing")
    fun loadGroupsConfigWithDefaultsWhenMissing() {
        configManager = ConfigManager(plugin)
        configManager.loadAll()

        assertNotNull(configManager.groupsConfig)
        assertTrue(configManager.groupsConfig.groups.isNotEmpty())
    }

    @Test
    @DisplayName("Should load groups from YAML file")
    fun loadGroupsFromYamlFile() {
        // Create groups.yml file
        val groupsFile = tempDir.resolve("plugin/groups.yml").toFile()
        groupsFile.parentFile.mkdirs()
        groupsFile.writeText("""
            groups:
              test-group:
                worlds:
                  - "test_world"
                  - "test_world_nether"
                patterns:
                  - "^test_.*"
                priority: 50
                settings:
                  save-health: true
                  save-hunger: false
            default-group: test-group
            global-settings:
              notify-on-switch: false
              switch-sound: "BLOCK_NOTE_BLOCK_PLING"
              switch-sound-volume: 0.8
              switch-sound-pitch: 1.5
        """.trimIndent())

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val group = configManager.groupsConfig.groups["test-group"]
        assertNotNull(group)
        assertEquals(listOf("test_world", "test_world_nether"), group.worlds)
        assertEquals(listOf("^test_.*"), group.patterns)
        assertEquals(50, group.priority)
        assertTrue(group.settings.saveHealth)
        assertFalse(group.settings.saveHunger)
        assertEquals("test-group", configManager.groupsConfig.defaultGroup)
        assertFalse(configManager.groupsConfig.globalSettings.notifyOnSwitch)
    }

    @Test
    @DisplayName("Should use default survival group when groups empty")
    fun useDefaultSurvivalGroupWhenEmpty() {
        // Create empty groups.yml
        val groupsFile = tempDir.resolve("plugin/groups.yml").toFile()
        groupsFile.parentFile.mkdirs()
        groupsFile.writeText("""
            groups: {}
            default-group: survival
        """.trimIndent())

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        // When groups are empty, a default survival group should be created
        assertTrue(configManager.groupsConfig.groups.containsKey("survival"))
    }

    @Test
    @DisplayName("Should parse group parent")
    fun parseGroupParent() {
        val groupsFile = tempDir.resolve("plugin/groups.yml").toFile()
        groupsFile.parentFile.mkdirs()
        groupsFile.writeText("""
            groups:
              parent-group:
                worlds:
                  - "world"
              child-group:
                worlds:
                  - "child_world"
                parent: parent-group
            default-group: parent-group
        """.trimIndent())

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val childGroup = configManager.groupsConfig.groups["child-group"]
        assertNotNull(childGroup)
        assertEquals("parent-group", childGroup.parent)
    }

    @Test
    @DisplayName("Should parse all group settings")
    fun parseAllGroupSettings() {
        val groupsFile = tempDir.resolve("plugin/groups.yml").toFile()
        groupsFile.parentFile.mkdirs()
        groupsFile.writeText("""
            groups:
              full-settings:
                worlds:
                  - "world"
                settings:
                  save-health: false
                  save-hunger: false
                  save-saturation: false
                  save-exhaustion: false
                  save-experience: false
                  save-potion-effects: false
                  save-ender-chest: false
                  save-gamemode: true
                  separate-gamemode-inventories: false
                  clear-on-death: true
                  clear-on-join: true
            default-group: full-settings
        """.trimIndent())

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val settings = configManager.groupsConfig.groups["full-settings"]?.settings
        assertNotNull(settings)
        assertFalse(settings.saveHealth)
        assertFalse(settings.saveHunger)
        assertFalse(settings.saveSaturation)
        assertFalse(settings.saveExhaustion)
        assertFalse(settings.saveExperience)
        assertFalse(settings.savePotionEffects)
        assertFalse(settings.saveEnderChest)
        assertTrue(settings.saveGameMode)
        assertFalse(settings.separateGameModeInventories)
        assertTrue(settings.clearOnDeath)
        assertTrue(settings.clearOnJoin)
    }

    // ==================== Messages Config Tests ====================

    @Test
    @DisplayName("Should load messages configuration with defaults")
    fun loadMessagesConfigWithDefaults() {
        configManager = ConfigManager(plugin)
        configManager.loadAll()

        assertNotNull(configManager.messagesConfig)
        assertTrue(configManager.messagesConfig.messages.isNotEmpty())
    }

    @Test
    @DisplayName("Should load custom messages from file")
    fun loadCustomMessagesFromFile() {
        val messagesFile = tempDir.resolve("plugin/messages.yml").toFile()
        messagesFile.parentFile.mkdirs()
        messagesFile.writeText("""
            prefix: "[CustomPrefix] "
            messages:
              custom-key: "Custom message value"
              no-permission: "Custom no permission message"
        """.trimIndent())

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        assertEquals("[CustomPrefix] ", configManager.messagesConfig.prefix)
        assertEquals("Custom message value", configManager.messagesConfig.getMessage("custom-key"))
        assertEquals("Custom no permission message", configManager.messagesConfig.getMessage("no-permission"))
    }

    @Test
    @DisplayName("Should merge custom messages with defaults")
    fun mergeCustomMessagesWithDefaults() {
        val messagesFile = tempDir.resolve("plugin/messages.yml").toFile()
        messagesFile.parentFile.mkdirs()
        messagesFile.writeText("""
            messages:
              no-permission: "Custom permission message"
        """.trimIndent())

        configManager = ConfigManager(plugin)
        configManager.loadAll()

        // Custom message should override default
        assertEquals("Custom permission message", configManager.messagesConfig.getMessage("no-permission"))
        // Other default messages should still exist
        assertTrue(configManager.messagesConfig.messages.containsKey("reloaded"))
    }

    // ==================== Reload Config Tests ====================

    @Test
    @DisplayName("Should reload all configurations")
    fun reloadAllConfigurations() {
        configManager = ConfigManager(plugin)
        configManager.loadAll()

        val result = configManager.reloadAll()

        assertTrue(result)
        verify { plugin.reloadConfig() }
    }

    @Test
    @DisplayName("Should update configuration after reload")
    fun updateConfigAfterReload() {
        configManager = ConfigManager(plugin)
        configManager.loadAll()

        // Change config
        mockConfig.set("debug", true)

        configManager.reloadAll()

        assertTrue(configManager.mainConfig.debug)
    }

    // ==================== Save Groups Config Tests ====================

    @Test
    @DisplayName("Should save groups configuration to file")
    fun saveGroupsConfigToFile() {
        configManager = ConfigManager(plugin)
        configManager.loadAll()

        configManager.saveGroupsConfig()

        val groupsFile = tempDir.resolve("plugin/groups.yml").toFile()
        assertTrue(groupsFile.exists())
    }

    @Test
    @DisplayName("Should save group settings correctly")
    fun saveGroupSettingsCorrectly() {
        val groupsFile = tempDir.resolve("plugin/groups.yml").toFile()
        groupsFile.parentFile.mkdirs()
        groupsFile.writeText("""
            groups:
              survival:
                worlds:
                  - "world"
                settings:
                  save-health: true
            default-group: survival
        """.trimIndent())

        configManager = ConfigManager(plugin)
        configManager.loadAll()
        configManager.saveGroupsConfig()

        // Verify saved file
        val yaml = YamlConfiguration.loadConfiguration(groupsFile)
        assertTrue(yaml.getBoolean("groups.survival.settings.save-health"))
    }

    @Test
    @DisplayName("Should save global settings correctly")
    fun saveGlobalSettingsCorrectly() {
        val groupsFile = tempDir.resolve("plugin/groups.yml").toFile()
        groupsFile.parentFile.mkdirs()
        groupsFile.writeText("""
            groups:
              survival:
                worlds:
                  - "world"
            default-group: survival
            global-settings:
              notify-on-switch: true
              switch-sound: "ENTITY_ENDERMAN_TELEPORT"
              switch-sound-volume: 0.5
              switch-sound-pitch: 1.2
        """.trimIndent())

        configManager = ConfigManager(plugin)
        configManager.loadAll()
        configManager.saveGroupsConfig()

        // Verify saved file
        val yaml = YamlConfiguration.loadConfiguration(groupsFile)
        assertTrue(yaml.getBoolean("global-settings.notify-on-switch"))
        assertEquals(0.5, yaml.getDouble("global-settings.switch-sound-volume"), 0.01)
    }

    // ==================== Handle Missing Config Files Tests ====================

    @Test
    @DisplayName("Should create default groups when file missing")
    fun createDefaultGroupsWhenFileMissing() {
        configManager = ConfigManager(plugin)
        configManager.loadAll()

        // Should have default survival group
        assertTrue(configManager.groupsConfig.groups.containsKey("survival"))
        verify { plugin.saveResource("groups.yml", false) }
    }

    @Test
    @DisplayName("Should create default messages when file missing")
    fun createDefaultMessagesWhenFileMissing() {
        configManager = ConfigManager(plugin)
        configManager.loadAll()

        // Should have default messages
        assertTrue(configManager.messagesConfig.messages.isNotEmpty())
        verify { plugin.saveResource("messages.yml", false) }
    }
}

/**
 * Unit tests for configuration model data classes.
 * These tests focus on data class behavior and default values.
 */
@DisplayName("Configuration Model Tests")
class ConfigModelTest {

    @Nested
    @DisplayName("MainConfig Model")
    inner class MainConfigModelTests {

        @Test
        @DisplayName("MainConfig should have sensible defaults")
        fun mainConfigDefaults() {
            val config = MainConfig()

            assertEquals(StorageType.YAML, config.storage.type)
            assertTrue(config.cache.enabled)
            assertEquals(1000, config.cache.maxSize)
            assertFalse(config.debug)
        }

        @Test
        @DisplayName("Should create MainConfig with all parameters")
        fun createMainConfigWithAllParams() {
            val config = MainConfig(
                storage = StorageConfig(type = StorageType.MYSQL),
                cache = CacheConfig(enabled = false),
                features = FeaturesConfig(asyncSaving = false),
                backup = BackupConfig(autoBackup = false),
                performance = PerformanceConfig(threadPoolSize = 8),
                debug = true
            )

            assertEquals(StorageType.MYSQL, config.storage.type)
            assertFalse(config.cache.enabled)
            assertFalse(config.features.asyncSaving)
            assertFalse(config.backup.autoBackup)
            assertEquals(8, config.performance.threadPoolSize)
            assertTrue(config.debug)
        }

        @Test
        @DisplayName("Should copy MainConfig with modifications")
        fun copyMainConfig() {
            val original = MainConfig(debug = false)
            val modified = original.copy(debug = true)

            assertFalse(original.debug)
            assertTrue(modified.debug)
        }
    }

    @Nested
    @DisplayName("StorageConfig Model")
    inner class StorageConfigModelTests {

        @Test
        @DisplayName("StorageConfig should have sensible defaults")
        fun storageConfigDefaults() {
            val config = StorageConfig()

            assertEquals(StorageType.YAML, config.type)
            assertEquals("data/inventories.db", config.sqlite.file)
            assertEquals("localhost", config.mysql.host)
            assertEquals(3306, config.mysql.port)
        }

        @Test
        @DisplayName("SqliteConfig should be a data class")
        fun sqliteConfigIsDataClass() {
            val config1 = SqliteConfig(file = "test.db")
            val config2 = SqliteConfig(file = "test.db")

            assertEquals(config1, config2)
            assertEquals(config1.hashCode(), config2.hashCode())
        }

        @Test
        @DisplayName("MysqlConfig should be a data class")
        fun mysqlConfigIsDataClass() {
            val config1 = MysqlConfig(host = "localhost", port = 3306, database = "test")
            val config2 = MysqlConfig(host = "localhost", port = 3306, database = "test")

            assertEquals(config1, config2)
        }
    }

    @Nested
    @DisplayName("PoolConfig Model")
    inner class PoolConfigModelTests {

        @Test
        @DisplayName("PoolConfig should have sensible defaults")
        fun poolConfigDefaults() {
            val config = PoolConfig()

            assertEquals(10, config.maximumPoolSize)
            assertEquals(2, config.minimumIdle)
            assertEquals(30000L, config.connectionTimeout)
            assertEquals(600000L, config.idleTimeout)
            assertEquals(1800000L, config.maxLifetime)
        }

        @Test
        @DisplayName("Zero values should be valid for pool config")
        fun zeroPoolValues() {
            val pool = PoolConfig(
                maximumPoolSize = 1,
                minimumIdle = 0,
                connectionTimeout = 1000,
                idleTimeout = 0,
                maxLifetime = 0
            )

            assertEquals(1, pool.maximumPoolSize)
            assertEquals(0, pool.minimumIdle)
            assertEquals(0L, pool.idleTimeout)
        }
    }

    @Nested
    @DisplayName("CacheConfig Model")
    inner class CacheConfigModelTests {

        @Test
        @DisplayName("CacheConfig should have sensible defaults")
        fun cacheConfigDefaults() {
            val config = CacheConfig()

            assertTrue(config.enabled)
            assertEquals(1000, config.maxSize)
            assertEquals(30, config.ttlMinutes)
            assertEquals(5, config.writeBehindSeconds)
        }

        @Test
        @DisplayName("CacheConfig copy should work")
        fun cacheConfigCopy() {
            val original = CacheConfig(enabled = true, maxSize = 500)
            val copy = original.copy(maxSize = 1000)

            assertTrue(copy.enabled)
            assertEquals(1000, copy.maxSize)
            assertEquals(500, original.maxSize)
        }
    }

    @Nested
    @DisplayName("FeaturesConfig Model")
    inner class FeaturesConfigModelTests {

        @Test
        @DisplayName("FeaturesConfig should have sensible defaults")
        fun featuresConfigDefaults() {
            val config = FeaturesConfig()

            assertTrue(config.separateGamemodeInventories)
            assertTrue(config.saveOnWorldChange)
            assertTrue(config.saveOnGamemodeChange)
            assertTrue(config.asyncSaving)
            assertFalse(config.clearOnDeath)
            assertTrue(config.adminNotifications)
        }
    }

    @Nested
    @DisplayName("BackupConfig Model")
    inner class BackupConfigModelTests {

        @Test
        @DisplayName("BackupConfig should have sensible defaults")
        fun backupConfigDefaults() {
            val config = BackupConfig()

            assertTrue(config.autoBackup)
            assertEquals(24, config.intervalHours)
            assertEquals(7, config.maxBackups)
            assertTrue(config.compression)
            assertEquals("backups", config.directory)
        }
    }

    @Nested
    @DisplayName("PerformanceConfig Model")
    inner class PerformanceConfigModelTests {

        @Test
        @DisplayName("PerformanceConfig should have sensible defaults")
        fun performanceConfigDefaults() {
            val config = PerformanceConfig()

            assertEquals(100, config.batchSize)
            assertEquals(4, config.threadPoolSize)
            assertEquals(1, config.saveDelayTicks)
        }
    }

    @Nested
    @DisplayName("GroupsConfig Model")
    inner class GroupsConfigModelTests {

        @Test
        @DisplayName("GroupsConfig should have default survival group")
        fun groupsConfigDefaults() {
            val config = GroupsConfig()

            assertTrue(config.groups.containsKey("survival"))
            assertEquals("survival", config.defaultGroup)
        }

        @Test
        @DisplayName("Should create GroupsConfig with custom groups")
        fun createGroupsConfigWithCustomGroups() {
            val groups = mapOf(
                "test" to GroupConfig(worlds = listOf("test_world"))
            )
            val config = GroupsConfig(groups = groups, defaultGroup = "test")

            assertEquals(1, config.groups.size)
            assertTrue(config.groups.containsKey("test"))
            assertEquals("test", config.defaultGroup)
        }

        @Test
        @DisplayName("Should have default survival group in defaults")
        fun defaultSurvivalGroup() {
            val config = GroupsConfig()

            assertTrue(config.groups.containsKey("survival"))
            val survival = config.groups["survival"]!!
            assertTrue(survival.worlds.contains("world"))
        }

        @Test
        @DisplayName("Empty groups map should be valid")
        fun emptyGroupsValid() {
            val config = GroupsConfig(groups = emptyMap())
            assertTrue(config.groups.isEmpty())
        }
    }

    @Nested
    @DisplayName("GroupConfig Model")
    inner class GroupConfigModelTests {

        @Test
        @DisplayName("GroupConfig should have sensible defaults")
        fun groupConfigDefaults() {
            val config = GroupConfig()

            assertTrue(config.worlds.isEmpty())
            assertTrue(config.patterns.isEmpty())
            assertEquals(0, config.priority)
            assertNull(config.parent)
        }

        @Test
        @DisplayName("Should create GroupConfig with all parameters")
        fun createGroupConfigWithAllParams() {
            val settings = GroupSettings(saveHealth = false)
            val config = GroupConfig(
                worlds = listOf("world1", "world2"),
                patterns = listOf("^test_.*"),
                priority = 100,
                parent = "parent_group",
                settings = settings
            )

            assertEquals(listOf("world1", "world2"), config.worlds)
            assertEquals(listOf("^test_.*"), config.patterns)
            assertEquals(100, config.priority)
            assertEquals("parent_group", config.parent)
            assertFalse(config.settings.saveHealth)
        }

        @Test
        @DisplayName("GroupConfig copy should work")
        fun groupConfigCopy() {
            val original = GroupConfig(
                worlds = listOf("world"),
                priority = 5
            )
            val copy = original.copy(priority = 10)

            assertEquals(listOf("world"), copy.worlds)
            assertEquals(10, copy.priority)
            assertEquals(5, original.priority)
        }

        @Test
        @DisplayName("Null parent should be handled")
        fun nullParentHandled() {
            val config = GroupConfig(parent = null)
            assertNull(config.parent)
        }

        @Test
        @DisplayName("Negative priority should be valid")
        fun negativePriorityValid() {
            val config = GroupConfig(priority = -10)
            assertEquals(-10, config.priority)
        }
    }

    @Nested
    @DisplayName("GlobalSettings Model")
    inner class GlobalSettingsModelTests {

        @Test
        @DisplayName("GlobalSettings should have sensible defaults")
        fun globalSettingsDefaults() {
            val config = GlobalSettings()

            assertTrue(config.notifyOnSwitch)
            assertEquals("ENTITY_ENDERMAN_TELEPORT", config.switchSound)
            assertEquals(0.5f, config.switchSoundVolume)
            assertEquals(1.2f, config.switchSoundPitch)
        }

        @Test
        @DisplayName("Should create GlobalSettings with custom values")
        fun createGlobalSettingsWithCustomValues() {
            val settings = GlobalSettings(
                notifyOnSwitch = false,
                switchSound = "BLOCK_NOTE_BLOCK_PLING",
                switchSoundVolume = 1.0f,
                switchSoundPitch = 0.5f
            )

            assertFalse(settings.notifyOnSwitch)
            assertEquals("BLOCK_NOTE_BLOCK_PLING", settings.switchSound)
            assertEquals(1.0f, settings.switchSoundVolume)
            assertEquals(0.5f, settings.switchSoundPitch)
        }

        @Test
        @DisplayName("Null switch sound should be valid")
        fun nullSwitchSoundValid() {
            val settings = GlobalSettings(switchSound = null)
            assertNull(settings.switchSound)
        }
    }

    @Nested
    @DisplayName("GroupSettings Model")
    inner class GroupSettingsModelTests {

        @Test
        @DisplayName("GroupSettings should have sensible defaults")
        fun groupSettingsDefaults() {
            val settings = GroupSettings()

            assertTrue(settings.saveHealth)
            assertTrue(settings.saveHunger)
            assertTrue(settings.saveSaturation)
            assertTrue(settings.saveExhaustion)
            assertTrue(settings.saveExperience)
            assertTrue(settings.savePotionEffects)
            assertTrue(settings.saveEnderChest)
            assertFalse(settings.saveGameMode)
            assertTrue(settings.separateGameModeInventories)
            assertFalse(settings.clearOnDeath)
            assertFalse(settings.clearOnJoin)
        }

        @Test
        @DisplayName("Should create GroupSettings with all false values")
        fun createGroupSettingsAllFalse() {
            val settings = GroupSettings(
                saveHealth = false,
                saveHunger = false,
                saveSaturation = false,
                saveExhaustion = false,
                saveExperience = false,
                savePotionEffects = false,
                saveEnderChest = false,
                saveGameMode = false,
                separateGameModeInventories = false,
                clearOnDeath = false,
                clearOnJoin = false
            )

            assertFalse(settings.saveHealth)
            assertFalse(settings.saveHunger)
            assertFalse(settings.saveSaturation)
            assertFalse(settings.saveExhaustion)
            assertFalse(settings.saveExperience)
            assertFalse(settings.savePotionEffects)
            assertFalse(settings.saveEnderChest)
            assertFalse(settings.saveGameMode)
            assertFalse(settings.separateGameModeInventories)
            assertFalse(settings.clearOnDeath)
            assertFalse(settings.clearOnJoin)
        }

        @Test
        @DisplayName("GroupSettings copy should work")
        fun groupSettingsCopy() {
            val original = GroupSettings(saveHealth = true, saveHunger = false)
            val copy = original.copy(saveHunger = true)

            assertTrue(copy.saveHealth)
            assertTrue(copy.saveHunger)
            assertFalse(original.saveHunger)
        }
    }
}

/**
 * Unit tests for MessagesConfig.
 */
@DisplayName("MessagesConfig Tests")
class MessagesConfigTest {

    @Nested
    @DisplayName("MessagesConfig Model")
    inner class MessagesConfigModelTests {

        @Test
        @DisplayName("MessagesConfig should have default messages")
        fun messagesConfigDefaults() {
            val config = MessagesConfig()

            assertTrue(config.messages.isNotEmpty())
            assertTrue(config.messages.containsKey("no-permission"))
            assertTrue(config.messages.containsKey("reloaded"))
        }

        @Test
        @DisplayName("Should create MessagesConfig with custom prefix")
        fun createMessagesConfigWithCustomPrefix() {
            val config = MessagesConfig(prefix = "[Test] ")

            assertEquals("[Test] ", config.prefix)
        }

        @Test
        @DisplayName("Should create MessagesConfig with custom messages")
        fun createMessagesConfigWithCustomMessages() {
            val customMessages = mapOf("test-key" to "Test value")
            val config = MessagesConfig(messages = customMessages)

            assertEquals("Test value", config.getMessage("test-key"))
        }

        @Test
        @DisplayName("Should return missing message for unknown key")
        fun getMissingMessage() {
            val config = MessagesConfig()
            val message = config.getMessage("non-existent-key")

            assertTrue(message.contains("Missing message"))
            assertTrue(message.contains("non-existent-key"))
        }

        @Test
        @DisplayName("Default messages should not be empty")
        fun defaultMessagesNotEmpty() {
            val defaults = MessagesConfig.defaultMessages()
            assertTrue(defaults.isNotEmpty())
            assertTrue(defaults.size > 50) // Should have many messages
        }
    }

    @Nested
    @DisplayName("Message Placeholder Tests")
    inner class MessagePlaceholderTests {

        @Test
        @DisplayName("Message should contain player placeholder")
        fun messageContainsPlayerPlaceholder() {
            val defaults = MessagesConfig.defaultMessages()
            val message = defaults["player-not-found"]!!

            assertTrue(message.contains("{player}"))
        }

        @Test
        @DisplayName("Message should contain group placeholder")
        fun messageContainsGroupPlaceholder() {
            val defaults = MessagesConfig.defaultMessages()
            val message = defaults["group-not-found"]!!

            assertTrue(message.contains("{group}"))
        }

        @Test
        @DisplayName("Message should contain world placeholder")
        fun messageContainsWorldPlaceholder() {
            val defaults = MessagesConfig.defaultMessages()
            val message = defaults["world-not-found"]!!

            assertTrue(message.contains("{world}"))
        }

        @Test
        @DisplayName("Inventory switch message should contain from_group and to_group placeholders")
        fun inventorySwitchMessagePlaceholders() {
            val defaults = MessagesConfig.defaultMessages()
            val message = defaults["inventory-switched"]!!

            assertTrue(message.contains("{from_group}"))
            assertTrue(message.contains("{to_group}"))
        }
    }

    @Nested
    @DisplayName("All Message Keys Present")
    inner class MessageKeysTests {

        @Test
        @DisplayName("Should have all general messages")
        fun hasGeneralMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("no-permission"))
            assertTrue(defaults.containsKey("player-not-found"))
            assertTrue(defaults.containsKey("player-not-online"))
            assertTrue(defaults.containsKey("invalid-syntax"))
            assertTrue(defaults.containsKey("console-only"))
            assertTrue(defaults.containsKey("player-only"))
            assertTrue(defaults.containsKey("world-not-found"))
            assertTrue(defaults.containsKey("reloaded"))
            assertTrue(defaults.containsKey("reload-failed"))
            assertTrue(defaults.containsKey("unknown-command"))
            assertTrue(defaults.containsKey("command-error"))
        }

        @Test
        @DisplayName("Should have all help messages")
        fun hasHelpMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("help-header"))
            assertTrue(defaults.containsKey("help-entry"))
            assertTrue(defaults.containsKey("help-footer"))
        }

        @Test
        @DisplayName("Should have all inventory messages")
        fun hasInventoryMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("inventory-saved"))
            assertTrue(defaults.containsKey("inventory-saved-self"))
            assertTrue(defaults.containsKey("inventory-loaded"))
            assertTrue(defaults.containsKey("inventory-loaded-self"))
            assertTrue(defaults.containsKey("inventory-save-failed"))
            assertTrue(defaults.containsKey("inventory-load-failed"))
            assertTrue(defaults.containsKey("inventory-cleared"))
            assertTrue(defaults.containsKey("inventory-switched"))
            assertTrue(defaults.containsKey("inventory-switch-same"))
        }

        @Test
        @DisplayName("Should have all group messages")
        fun hasGroupMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("group-created"))
            assertTrue(defaults.containsKey("group-deleted"))
            assertTrue(defaults.containsKey("group-not-found"))
            assertTrue(defaults.containsKey("group-already-exists"))
            assertTrue(defaults.containsKey("group-cannot-delete-default"))
            assertTrue(defaults.containsKey("group-list-header"))
            assertTrue(defaults.containsKey("group-list-entry"))
            assertTrue(defaults.containsKey("group-list-empty"))
            assertTrue(defaults.containsKey("group-info-header"))
            assertTrue(defaults.containsKey("group-modified"))
        }

        @Test
        @DisplayName("Should have all world messages")
        fun hasWorldMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("world-assigned"))
            assertTrue(defaults.containsKey("world-unassigned"))
            assertTrue(defaults.containsKey("world-not-assigned"))
            assertTrue(defaults.containsKey("world-already-assigned"))
            assertTrue(defaults.containsKey("world-list-header"))
            assertTrue(defaults.containsKey("world-list-entry"))
        }

        @Test
        @DisplayName("Should have all pattern messages")
        fun hasPatternMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("pattern-added"))
            assertTrue(defaults.containsKey("pattern-removed"))
            assertTrue(defaults.containsKey("pattern-not-found"))
            assertTrue(defaults.containsKey("pattern-invalid"))
            assertTrue(defaults.containsKey("pattern-already-exists"))
        }

        @Test
        @DisplayName("Should have all cache messages")
        fun hasCacheMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("cache-cleared"))
            assertTrue(defaults.containsKey("cache-player-cleared"))
            assertTrue(defaults.containsKey("cache-stats-header"))
            assertTrue(defaults.containsKey("cache-stats-size"))
            assertTrue(defaults.containsKey("cache-stats-hits"))
        }

        @Test
        @DisplayName("Should have all backup messages")
        fun hasBackupMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("backup-created"))
            assertTrue(defaults.containsKey("backup-create-failed"))
            assertTrue(defaults.containsKey("backup-restored"))
            assertTrue(defaults.containsKey("backup-restore-failed"))
            assertTrue(defaults.containsKey("backup-deleted"))
            assertTrue(defaults.containsKey("backup-not-found"))
            assertTrue(defaults.containsKey("backup-list-header"))
        }

        @Test
        @DisplayName("Should have all GUI messages")
        fun hasGuiMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("gui-title-main"))
            assertTrue(defaults.containsKey("gui-title-groups"))
            assertTrue(defaults.containsKey("gui-item-back"))
            assertTrue(defaults.containsKey("gui-item-close"))
            assertTrue(defaults.containsKey("gui-item-confirm"))
            assertTrue(defaults.containsKey("gui-item-cancel"))
        }

        @Test
        @DisplayName("Should have all error messages")
        fun hasErrorMessages() {
            val defaults = MessagesConfig.defaultMessages()

            assertTrue(defaults.containsKey("error-generic"))
            assertTrue(defaults.containsKey("error-storage"))
            assertTrue(defaults.containsKey("error-storage-connect"))
            assertTrue(defaults.containsKey("error-migration"))
            assertTrue(defaults.containsKey("error-serialization"))
            assertTrue(defaults.containsKey("error-deserialization"))
            assertTrue(defaults.containsKey("admin-error"))
            assertTrue(defaults.containsKey("admin-warning"))
        }
    }
}

/**
 * Unit tests for StorageType enum.
 */
@DisplayName("StorageType Tests")
class StorageTypeTest {

    @Test
    @DisplayName("StorageType YAML should be valid")
    fun storageTypeYaml() {
        val type = StorageType.valueOf("YAML")
        assertEquals(StorageType.YAML, type)
    }

    @Test
    @DisplayName("StorageType SQLITE should be valid")
    fun storageTypeSqlite() {
        val type = StorageType.valueOf("SQLITE")
        assertEquals(StorageType.SQLITE, type)
    }

    @Test
    @DisplayName("StorageType MYSQL should be valid")
    fun storageTypeMysql() {
        val type = StorageType.valueOf("MYSQL")
        assertEquals(StorageType.MYSQL, type)
    }

    @Test
    @DisplayName("Invalid storage type should throw exception")
    fun invalidStorageType() {
        try {
            StorageType.valueOf("INVALID")
            assertTrue(false, "Should have thrown exception")
        } catch (e: IllegalArgumentException) {
            // Expected
            assertTrue(true)
        }
    }

    @Test
    @DisplayName("StorageType should have exactly 3 values")
    fun storageTypeValues() {
        val values = StorageType.entries
        assertEquals(3, values.size)
        assertTrue(values.contains(StorageType.YAML))
        assertTrue(values.contains(StorageType.SQLITE))
        assertTrue(values.contains(StorageType.MYSQL))
    }
}

/**
 * Tests for isolated configuration file handling.
 * Uses temp directory for file operations.
 */
@DisplayName("Configuration File Handling Tests")
class ConfigFileHandlingTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setUp() {
        Logging.init(Logger.getLogger("Test"), false)
    }

    @Test
    @DisplayName("YamlConfiguration should load from file")
    fun yamlConfigurationLoadsFromFile() {
        val configFile = tempDir.resolve("test-config.yml").toFile()
        configFile.writeText("""
            storage:
              type: sqlite
            debug: true
        """.trimIndent())

        val yaml = YamlConfiguration.loadConfiguration(configFile)

        assertEquals("sqlite", yaml.getString("storage.type"))
        assertTrue(yaml.getBoolean("debug"))
    }

    @Test
    @DisplayName("YamlConfiguration should save to file")
    fun yamlConfigurationSavesToFile() {
        val configFile = tempDir.resolve("test-save.yml").toFile()
        val yaml = YamlConfiguration()
        yaml.set("test.key", "value")
        yaml.set("test.number", 42)
        yaml.save(configFile)

        val loaded = YamlConfiguration.loadConfiguration(configFile)

        assertEquals("value", loaded.getString("test.key"))
        assertEquals(42, loaded.getInt("test.number"))
    }

    @Test
    @DisplayName("Should handle missing configuration file gracefully")
    fun handleMissingConfigFile() {
        val nonExistentFile = tempDir.resolve("non-existent.yml").toFile()
        val yaml = YamlConfiguration.loadConfiguration(nonExistentFile)

        // Should return empty configuration, not throw
        assertNull(yaml.getString("any-key"))
        assertEquals(0, yaml.getKeys(false).size)
    }

    @Test
    @DisplayName("Should handle malformed YAML gracefully")
    fun handleMalformedYaml() {
        val malformedFile = tempDir.resolve("malformed.yml").toFile()
        malformedFile.writeText("""
            invalid yaml content
            : not: proper: format
            [[[
        """.trimIndent())

        // YamlConfiguration handles malformed YAML by returning partial/empty config
        // It may log warnings but should not throw an exception
        try {
            val yaml = YamlConfiguration.loadConfiguration(malformedFile)
            // Should not throw, may have partial content or empty
            assertNotNull(yaml)
        } catch (e: Exception) {
            // Some YAML parsers may throw, which is also acceptable behavior
            assertTrue(true)
        }
    }

    @Test
    @DisplayName("Should parse nested configuration sections")
    fun parseNestedConfigSections() {
        val configFile = tempDir.resolve("nested.yml").toFile()
        configFile.writeText("""
            level1:
              level2:
                level3:
                  value: "deep value"
                  number: 123
        """.trimIndent())

        val yaml = YamlConfiguration.loadConfiguration(configFile)

        assertEquals("deep value", yaml.getString("level1.level2.level3.value"))
        assertEquals(123, yaml.getInt("level1.level2.level3.number"))
    }

    @Test
    @DisplayName("Should parse list values")
    fun parseListValues() {
        val configFile = tempDir.resolve("lists.yml").toFile()
        configFile.writeText("""
            worlds:
              - "world"
              - "world_nether"
              - "world_the_end"
            numbers:
              - 1
              - 2
              - 3
        """.trimIndent())

        val yaml = YamlConfiguration.loadConfiguration(configFile)
        val worlds = yaml.getStringList("worlds")
        val numbers = yaml.getIntegerList("numbers")

        assertEquals(3, worlds.size)
        assertTrue(worlds.contains("world"))
        assertTrue(worlds.contains("world_nether"))
        assertEquals(listOf(1, 2, 3), numbers)
    }

    @Test
    @DisplayName("Should handle boolean values correctly")
    fun handleBooleanValues() {
        val configFile = tempDir.resolve("booleans.yml").toFile()
        configFile.writeText("""
            enabled: true
            disabled: false
            yes-value: yes
            no-value: no
        """.trimIndent())

        val yaml = YamlConfiguration.loadConfiguration(configFile)

        assertTrue(yaml.getBoolean("enabled"))
        assertFalse(yaml.getBoolean("disabled"))
        assertTrue(yaml.getBoolean("yes-value"))
        assertFalse(yaml.getBoolean("no-value"))
    }

    @Test
    @DisplayName("Should handle default values for missing keys")
    fun handleDefaultValues() {
        val configFile = tempDir.resolve("defaults.yml").toFile()
        configFile.writeText("existing-key: value")

        val yaml = YamlConfiguration.loadConfiguration(configFile)

        assertEquals("value", yaml.getString("existing-key"))
        assertEquals("default", yaml.getString("missing-key", "default"))
        assertEquals(42, yaml.getInt("missing-int", 42))
        assertTrue(yaml.getBoolean("missing-bool", true))
    }

    @Test
    @DisplayName("Should handle float and double values")
    fun handleFloatDoubleValues() {
        val configFile = tempDir.resolve("numbers.yml").toFile()
        configFile.writeText("""
            volume: 0.5
            pitch: 1.2
            large: 1000000.123456
        """.trimIndent())

        val yaml = YamlConfiguration.loadConfiguration(configFile)

        assertEquals(0.5, yaml.getDouble("volume"), 0.001)
        assertEquals(1.2f, yaml.getDouble("pitch").toFloat(), 0.001f)
        assertEquals(1000000.123456, yaml.getDouble("large"), 0.000001)
    }

    @Test
    @DisplayName("Should handle long values")
    fun handleLongValues() {
        val configFile = tempDir.resolve("longs.yml").toFile()
        configFile.writeText("""
            timeout: 30000
            lifetime: 1800000
        """.trimIndent())

        val yaml = YamlConfiguration.loadConfiguration(configFile)

        assertEquals(30000L, yaml.getLong("timeout"))
        assertEquals(1800000L, yaml.getLong("lifetime"))
    }

    @Test
    @DisplayName("Should enumerate configuration section keys")
    fun enumerateConfigSectionKeys() {
        val configFile = tempDir.resolve("sections.yml").toFile()
        configFile.writeText("""
            groups:
              survival:
                priority: 10
              creative:
                priority: 20
              minigames:
                priority: 30
        """.trimIndent())

        val yaml = YamlConfiguration.loadConfiguration(configFile)
        val groupKeys = yaml.getConfigurationSection("groups")?.getKeys(false)

        assertNotNull(groupKeys)
        assertEquals(3, groupKeys.size)
        assertTrue(groupKeys.contains("survival"))
        assertTrue(groupKeys.contains("creative"))
        assertTrue(groupKeys.contains("minigames"))
    }

    @Test
    @DisplayName("Should create configuration sections")
    fun createConfigSections() {
        val yaml = YamlConfiguration()
        val section = yaml.createSection("new.section.path")
        section.set("key", "value")

        assertEquals("value", yaml.getString("new.section.path.key"))
    }

    @Test
    @DisplayName("Groups file should parse all group properties")
    fun parseGroupsFileProperties() {
        val groupsFile = tempDir.resolve("groups.yml").toFile()
        groupsFile.writeText("""
            groups:
              test:
                worlds:
                  - "test_world"
                patterns:
                  - "^test_.*"
                priority: 50
                parent: null
                settings:
                  save-health: true
                  save-hunger: false
                  clear-on-death: true
            default-group: test
            global-settings:
              notify-on-switch: true
              switch-sound: "BLOCK_NOTE_BLOCK_PLING"
              switch-sound-volume: 0.8
              switch-sound-pitch: 1.5
        """.trimIndent())

        val yaml = YamlConfiguration.loadConfiguration(groupsFile)

        // Group properties
        assertEquals(listOf("test_world"), yaml.getStringList("groups.test.worlds"))
        assertEquals(listOf("^test_.*"), yaml.getStringList("groups.test.patterns"))
        assertEquals(50, yaml.getInt("groups.test.priority"))

        // Group settings
        assertTrue(yaml.getBoolean("groups.test.settings.save-health"))
        assertFalse(yaml.getBoolean("groups.test.settings.save-hunger"))
        assertTrue(yaml.getBoolean("groups.test.settings.clear-on-death"))

        // Default group
        assertEquals("test", yaml.getString("default-group"))

        // Global settings
        assertTrue(yaml.getBoolean("global-settings.notify-on-switch"))
        assertEquals("BLOCK_NOTE_BLOCK_PLING", yaml.getString("global-settings.switch-sound"))
        assertEquals(0.8, yaml.getDouble("global-settings.switch-sound-volume"), 0.001)
        assertEquals(1.5, yaml.getDouble("global-settings.switch-sound-pitch"), 0.001)
    }

    @Test
    @DisplayName("Main config file should parse all properties")
    fun parseMainConfigFileProperties() {
        val configFile = tempDir.resolve("config.yml").toFile()
        configFile.writeText("""
            storage:
              type: mysql
              sqlite:
                file: "custom.db"
              mysql:
                host: "db.example.com"
                port: 3307
                database: "xinv"
                username: "user"
                password: "pass"
                pool:
                  maximum-pool-size: 20
                  minimum-idle: 5
                  connection-timeout: 60000
                  idle-timeout: 300000
                  max-lifetime: 900000
            cache:
              enabled: false
              max-size: 500
              ttl-minutes: 60
              write-behind-seconds: 10
            features:
              separate-gamemode-inventories: false
              save-on-world-change: false
              async-saving: false
            backup:
              auto-backup: false
              interval-hours: 12
              max-backups: 14
              compression: false
              directory: "custom-backups"
            performance:
              batch-size: 200
              thread-pool-size: 8
              save-delay-ticks: 5
            debug: true
        """.trimIndent())

        val yaml = YamlConfiguration.loadConfiguration(configFile)

        // Storage
        assertEquals("mysql", yaml.getString("storage.type"))
        assertEquals("custom.db", yaml.getString("storage.sqlite.file"))
        assertEquals("db.example.com", yaml.getString("storage.mysql.host"))
        assertEquals(3307, yaml.getInt("storage.mysql.port"))
        assertEquals(20, yaml.getInt("storage.mysql.pool.maximum-pool-size"))

        // Cache
        assertFalse(yaml.getBoolean("cache.enabled"))
        assertEquals(500, yaml.getInt("cache.max-size"))

        // Features
        assertFalse(yaml.getBoolean("features.separate-gamemode-inventories"))
        assertFalse(yaml.getBoolean("features.async-saving"))

        // Backup
        assertFalse(yaml.getBoolean("backup.auto-backup"))
        assertEquals(12, yaml.getInt("backup.interval-hours"))

        // Performance
        assertEquals(200, yaml.getInt("performance.batch-size"))
        assertEquals(8, yaml.getInt("performance.thread-pool-size"))

        // Debug
        assertTrue(yaml.getBoolean("debug"))
    }
}

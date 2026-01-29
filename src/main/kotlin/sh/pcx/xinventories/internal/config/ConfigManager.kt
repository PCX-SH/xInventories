package sh.pcx.xinventories.internal.config

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File

/**
 * Manages loading and saving of all configuration files.
 */
class ConfigManager(private val plugin: XInventories) {

    var mainConfig: MainConfig = MainConfig()
        private set

    var groupsConfig: GroupsConfig = GroupsConfig()
        private set

    var messagesConfig: MessagesConfig = MessagesConfig()
        private set

    private val groupsFile: File = File(plugin.dataFolder, "groups.yml")
    private val messagesFile: File = File(plugin.dataFolder, "messages.yml")

    /**
     * Loads all configuration files.
     */
    fun loadAll(): Boolean {
        return try {
            loadMainConfig()
            loadGroupsConfig()
            loadMessagesConfig()
            true
        } catch (e: Exception) {
            Logging.error("Failed to load configuration", e)
            false
        }
    }

    /**
     * Reloads all configuration files.
     */
    fun reloadAll(): Boolean {
        plugin.reloadConfig()
        return loadAll()
    }

    /**
     * Saves the groups configuration.
     */
    fun saveGroupsConfig() {
        try {
            val yaml = YamlConfiguration()

            // Save groups
            groupsConfig.groups.forEach { (name, group) ->
                val section = yaml.createSection("groups.$name")
                section.set("worlds", group.worlds)
                section.set("patterns", group.patterns)
                section.set("priority", group.priority)
                section.set("parent", group.parent)
                saveGroupSettings(section.createSection("settings"), group.settings)
            }

            yaml.set("default-group", groupsConfig.defaultGroup)

            // Save global settings
            val globalSection = yaml.createSection("global-settings")
            globalSection.set("notify-on-switch", groupsConfig.globalSettings.notifyOnSwitch)
            globalSection.set("switch-sound", groupsConfig.globalSettings.switchSound)
            globalSection.set("switch-sound-volume", groupsConfig.globalSettings.switchSoundVolume)
            globalSection.set("switch-sound-pitch", groupsConfig.globalSettings.switchSoundPitch)

            yaml.save(groupsFile)
            Logging.debug { "Saved groups configuration" }
        } catch (e: Exception) {
            Logging.error("Failed to save groups configuration", e)
        }
    }

    private fun loadMainConfig() {
        val config = plugin.config

        val storageType = try {
            StorageType.valueOf(config.getString("storage.type", "YAML")!!.uppercase())
        } catch (e: Exception) {
            Logging.warning("Invalid storage type, defaulting to YAML")
            StorageType.YAML
        }

        mainConfig = MainConfig(
            storage = StorageConfig(
                type = storageType,
                sqlite = SqliteConfig(
                    file = config.getString("storage.sqlite.file", "data/inventories.db")!!
                ),
                mysql = MysqlConfig(
                    host = config.getString("storage.mysql.host", "localhost")!!,
                    port = config.getInt("storage.mysql.port", 3306),
                    database = config.getString("storage.mysql.database", "xinventories")!!,
                    username = config.getString("storage.mysql.username", "root")!!,
                    password = config.getString("storage.mysql.password", "")!!,
                    pool = PoolConfig(
                        maximumPoolSize = config.getInt("storage.mysql.pool.maximum-pool-size", 10),
                        minimumIdle = config.getInt("storage.mysql.pool.minimum-idle", 2),
                        connectionTimeout = config.getLong("storage.mysql.pool.connection-timeout", 30000),
                        idleTimeout = config.getLong("storage.mysql.pool.idle-timeout", 600000),
                        maxLifetime = config.getLong("storage.mysql.pool.max-lifetime", 1800000)
                    )
                )
            ),
            cache = CacheConfig(
                enabled = config.getBoolean("cache.enabled", true),
                maxSize = config.getInt("cache.max-size", 1000),
                ttlMinutes = config.getInt("cache.ttl-minutes", 30),
                writeBehindSeconds = config.getInt("cache.write-behind-seconds", 5)
            ),
            features = FeaturesConfig(
                separateGamemodeInventories = config.getBoolean("features.separate-gamemode-inventories", true),
                saveOnWorldChange = config.getBoolean("features.save-on-world-change", true),
                saveOnGamemodeChange = config.getBoolean("features.save-on-gamemode-change", true),
                asyncSaving = config.getBoolean("features.async-saving", true),
                clearOnDeath = config.getBoolean("features.clear-on-death", false),
                adminNotifications = config.getBoolean("features.admin-notifications", true)
            ),
            backup = BackupConfig(
                autoBackup = config.getBoolean("backup.auto-backup", true),
                intervalHours = config.getInt("backup.interval-hours", 24),
                maxBackups = config.getInt("backup.max-backups", 7),
                compression = config.getBoolean("backup.compression", true),
                directory = config.getString("backup.directory", "backups")!!
            ),
            performance = PerformanceConfig(
                batchSize = config.getInt("performance.batch-size", 100),
                threadPoolSize = config.getInt("performance.thread-pool-size", 4),
                saveDelayTicks = config.getInt("performance.save-delay-ticks", 1)
            ),
            debug = config.getBoolean("debug", false)
        )

        Logging.setDebug(mainConfig.debug)
        Logging.debug { "Loaded main configuration" }
    }

    private fun loadGroupsConfig() {
        if (!groupsFile.exists()) {
            plugin.saveResource("groups.yml", false)
        }

        val yaml = YamlConfiguration.loadConfiguration(groupsFile)
        val groups = mutableMapOf<String, GroupConfig>()

        yaml.getConfigurationSection("groups")?.getKeys(false)?.forEach { name ->
            val section = yaml.getConfigurationSection("groups.$name")
            if (section != null) {
                groups[name] = loadGroupConfig(section)
            }
        }

        groupsConfig = GroupsConfig(
            groups = groups.ifEmpty {
                mapOf("survival" to GroupConfig(
                    worlds = listOf("world", "world_nether", "world_the_end")
                ))
            },
            defaultGroup = yaml.getString("default-group", "survival")!!,
            globalSettings = GlobalSettings(
                notifyOnSwitch = yaml.getBoolean("global-settings.notify-on-switch", true),
                switchSound = yaml.getString("global-settings.switch-sound"),
                switchSoundVolume = yaml.getDouble("global-settings.switch-sound-volume", 0.5).toFloat(),
                switchSoundPitch = yaml.getDouble("global-settings.switch-sound-pitch", 1.2).toFloat()
            )
        )

        Logging.debug { "Loaded ${groups.size} groups from configuration" }
    }

    private fun loadGroupConfig(section: ConfigurationSection): GroupConfig {
        return GroupConfig(
            worlds = section.getStringList("worlds"),
            patterns = section.getStringList("patterns"),
            priority = section.getInt("priority", 0),
            parent = section.getString("parent"),
            settings = loadGroupSettings(section.getConfigurationSection("settings"))
        )
    }

    private fun loadGroupSettings(section: ConfigurationSection?): GroupSettings {
        if (section == null) return GroupSettings()

        return GroupSettings(
            saveHealth = section.getBoolean("save-health", true),
            saveHunger = section.getBoolean("save-hunger", true),
            saveSaturation = section.getBoolean("save-saturation", true),
            saveExhaustion = section.getBoolean("save-exhaustion", true),
            saveExperience = section.getBoolean("save-experience", true),
            savePotionEffects = section.getBoolean("save-potion-effects", true),
            saveEnderChest = section.getBoolean("save-ender-chest", true),
            saveGameMode = section.getBoolean("save-gamemode", false),
            separateGameModeInventories = section.getBoolean("separate-gamemode-inventories", true),
            clearOnDeath = section.getBoolean("clear-on-death", false),
            clearOnJoin = section.getBoolean("clear-on-join", false)
        )
    }

    private fun saveGroupSettings(section: ConfigurationSection, settings: GroupSettings) {
        section.set("save-health", settings.saveHealth)
        section.set("save-hunger", settings.saveHunger)
        section.set("save-saturation", settings.saveSaturation)
        section.set("save-exhaustion", settings.saveExhaustion)
        section.set("save-experience", settings.saveExperience)
        section.set("save-potion-effects", settings.savePotionEffects)
        section.set("save-ender-chest", settings.saveEnderChest)
        section.set("save-gamemode", settings.saveGameMode)
        section.set("separate-gamemode-inventories", settings.separateGameModeInventories)
        section.set("clear-on-death", settings.clearOnDeath)
        section.set("clear-on-join", settings.clearOnJoin)
    }

    private fun loadMessagesConfig() {
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false)
        }

        val yaml = YamlConfiguration.loadConfiguration(messagesFile)
        val messages = mutableMapOf<String, String>()

        // Load all messages from the file
        yaml.getConfigurationSection("messages")?.getKeys(true)?.forEach { key ->
            val value = yaml.getString("messages.$key")
            if (value != null) {
                messages[key] = value
            }
        }

        // Merge with defaults (defaults take precedence for missing keys)
        val finalMessages = MessagesConfig.defaultMessages().toMutableMap()
        messages.forEach { (key, value) ->
            finalMessages[key] = value
        }

        messagesConfig = MessagesConfig(
            prefix = yaml.getString("prefix", MessagesConfig().prefix)!!,
            messages = finalMessages
        )

        Logging.debug { "Loaded ${messages.size} custom messages" }
    }
}

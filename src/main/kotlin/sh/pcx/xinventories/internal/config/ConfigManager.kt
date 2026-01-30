package sh.pcx.xinventories.internal.config

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.model.ConflictStrategy
import sh.pcx.xinventories.internal.model.MergeRule
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
    private val configFile: File = File(plugin.dataFolder, "config.yml")

    /**
     * The config migrator instance for handling version migrations.
     */
    val configMigrator: ConfigMigrator = ConfigMigrator(plugin)

    /**
     * Loads all configuration files.
     * Performs config migration if needed before loading.
     */
    fun loadAll(): Boolean {
        return try {
            // Check and apply migrations for main config
            migrateConfigIfNeeded()

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
     * Checks if the main config needs migration and applies migrations if necessary.
     */
    private fun migrateConfigIfNeeded() {
        if (!configFile.exists()) {
            return
        }

        val result = configMigrator.migrate(configFile)
        when (result) {
            is MigrationResult.Success -> {
                if (result.migrationsApplied > 0) {
                    Logging.info("Config migration completed: v${result.fromVersion} -> v${result.toVersion}")
                    // Reload the Bukkit config to pick up migrated values
                    plugin.reloadConfig()
                }
            }
            is MigrationResult.Failure -> {
                Logging.error("Config migration failed: ${result.error}")
                if (result.lastSuccessfulVersion != null) {
                    Logging.warning("Last successful migration version: ${result.lastSuccessfulVersion}")
                }
            }
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

                // Save conditions if present
                group.conditions?.let { conditions ->
                    saveConditionsConfig(section.createSection("conditions"), conditions)
                }

                // Save template settings if present
                group.template?.let { template ->
                    saveTemplateConfig(section.createSection("template"), template)
                }

                // Save restrictions if present
                group.restrictions?.let { restrictions ->
                    saveRestrictionsConfig(section.createSection("restrictions"), restrictions)
                }
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

    private fun saveConditionsConfig(section: ConfigurationSection, conditions: ConditionsConfig) {
        conditions.permission?.let { section.set("permission", it) }
        conditions.cron?.let { section.set("cron", it) }
        section.set("require-all", conditions.requireAll)

        conditions.schedule?.let { schedules ->
            section.set("schedule", schedules.map { schedule ->
                mapOf(
                    "start" to schedule.start,
                    "end" to schedule.end,
                    "timezone" to schedule.timezone
                ).filterValues { it != null }
            })
        }

        conditions.placeholder?.let { placeholder ->
            val placeholderSection = section.createSection("placeholder")
            placeholderSection.set("placeholder", placeholder.placeholder)
            placeholderSection.set("operator", placeholder.operator)
            placeholderSection.set("value", placeholder.value)
        }

        conditions.placeholders?.let { placeholders ->
            section.set("placeholders", placeholders.map { placeholder ->
                mapOf(
                    "placeholder" to placeholder.placeholder,
                    "operator" to placeholder.operator,
                    "value" to placeholder.value
                )
            })
        }
    }

    private fun saveTemplateConfig(section: ConfigurationSection, template: TemplateConfigSection) {
        section.set("enabled", template.enabled)
        template.templateName?.let { section.set("template-name", it) }
        section.set("apply-on", template.applyOn)
        section.set("allow-reset", template.allowReset)
        section.set("clear-inventory-first", template.clearInventoryFirst)
    }

    private fun saveRestrictionsConfig(section: ConfigurationSection, restrictions: RestrictionConfigSection) {
        section.set("mode", restrictions.mode)
        section.set("on-violation", restrictions.onViolation)
        section.set("notify-player", restrictions.notifyPlayer)
        section.set("notify-admins", restrictions.notifyAdmins)
        if (restrictions.blacklist.isNotEmpty()) {
            section.set("blacklist", restrictions.blacklist)
        }
        if (restrictions.whitelist.isNotEmpty()) {
            section.set("whitelist", restrictions.whitelist)
        }
        if (restrictions.stripOnExit.isNotEmpty()) {
            section.set("strip-on-exit", restrictions.stripOnExit)
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
            player = loadPlayerConfig(config),
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
            versioning = VersioningConfig(
                enabled = config.getBoolean("versioning.enabled", true),
                maxVersionsPerPlayer = config.getInt("versioning.max-versions-per-player", 10),
                minIntervalSeconds = config.getInt("versioning.min-interval-seconds", 300),
                retentionDays = config.getInt("versioning.retention-days", 7),
                triggerOn = TriggerConfig(
                    worldChange = config.getBoolean("versioning.trigger-on.world-change", true),
                    disconnect = config.getBoolean("versioning.trigger-on.disconnect", true),
                    manual = config.getBoolean("versioning.trigger-on.manual", true)
                )
            ),
            deathRecovery = DeathRecoveryConfig(
                enabled = config.getBoolean("death-recovery.enabled", true),
                maxDeathsPerPlayer = config.getInt("death-recovery.max-deaths-per-player", 5),
                retentionDays = config.getInt("death-recovery.retention-days", 3),
                storeLocation = config.getBoolean("death-recovery.store-location", true),
                storeDeathCause = config.getBoolean("death-recovery.store-death-cause", true),
                storeKiller = config.getBoolean("death-recovery.store-killer", true)
            ),
            locking = LockingConfig(
                enabled = config.getBoolean("locking.enabled", true),
                defaultMessage = config.getString("locking.default-message", "<red>Your inventory is currently locked.")!!,
                allowAdminBypass = config.getBoolean("locking.allow-admin-bypass", true)
            ),
            sharedSlots = loadSharedSlotsConfig(config),
            economy = EconomyConfig(
                enabled = config.getBoolean("economy.enabled", true),
                provider = config.getString("economy.provider", "VAULT")!!,
                separateByGroup = config.getBoolean("economy.separate-by-group", true)
            ),
            sync = loadSyncConfig(config),
            startup = StartupConfig(
                showBanner = config.getBoolean("startup.show-banner", true),
                showStats = config.getBoolean("startup.show-stats", true)
            ),
            gui = loadGUIConfig(config),
            audit = loadAuditConfig(config),
            antiDupe = loadAntiDupeConfig(config),
            configVersion = config.getInt("config-version", 1),
            metrics = config.getBoolean("metrics", true),
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
        val (settings, explicit) = loadGroupSettingsWithExplicit(section.getConfigurationSection("settings"))
        return GroupConfig(
            worlds = section.getStringList("worlds"),
            patterns = section.getStringList("patterns"),
            priority = section.getInt("priority", 0),
            parent = section.getString("parent"),
            settings = settings,
            conditions = loadConditionsConfig(section.getConfigurationSection("conditions")),
            template = loadTemplateConfig(section.getConfigurationSection("template")),
            restrictions = loadRestrictionsConfig(section.getConfigurationSection("restrictions")),
            explicitSettings = explicit
        )
    }

    private fun loadConditionsConfig(section: ConfigurationSection?): ConditionsConfig? {
        if (section == null) return null

        val scheduleConfigs = section.getMapList("schedule").mapNotNull { map ->
            val start = map["start"] as? String ?: return@mapNotNull null
            val end = map["end"] as? String ?: return@mapNotNull null
            val timezone = map["timezone"] as? String
            ScheduleConfig(start, end, timezone)
        }.takeIf { it.isNotEmpty() }

        val placeholderConfig = section.getConfigurationSection("placeholder")?.let {
            PlaceholderConfig(
                placeholder = it.getString("placeholder") ?: return@let null,
                operator = it.getString("operator") ?: return@let null,
                value = it.getString("value") ?: return@let null
            )
        }

        val placeholderConfigs = section.getMapList("placeholders").mapNotNull { map ->
            val placeholder = map["placeholder"] as? String ?: return@mapNotNull null
            val operator = map["operator"] as? String ?: return@mapNotNull null
            val value = map["value"] as? String ?: return@mapNotNull null
            PlaceholderConfig(placeholder, operator, value)
        }.takeIf { it.isNotEmpty() }

        return ConditionsConfig(
            permission = section.getString("permission"),
            schedule = scheduleConfigs,
            cron = section.getString("cron"),
            placeholder = placeholderConfig,
            placeholders = placeholderConfigs,
            requireAll = section.getBoolean("require-all", true)
        )
    }

    private fun loadTemplateConfig(section: ConfigurationSection?): TemplateConfigSection? {
        if (section == null) return null

        return TemplateConfigSection(
            enabled = section.getBoolean("enabled", false),
            templateName = section.getString("template-name"),
            applyOn = section.getString("apply-on", "NONE")!!,
            allowReset = section.getBoolean("allow-reset", false),
            clearInventoryFirst = section.getBoolean("clear-inventory-first", true)
        )
    }

    private fun loadRestrictionsConfig(section: ConfigurationSection?): RestrictionConfigSection? {
        if (section == null) return null

        return RestrictionConfigSection(
            mode = section.getString("mode", "NONE")!!,
            onViolation = section.getString("on-violation", "REMOVE")!!,
            notifyPlayer = section.getBoolean("notify-player", true),
            notifyAdmins = section.getBoolean("notify-admins", true),
            blacklist = section.getStringList("blacklist"),
            whitelist = section.getStringList("whitelist"),
            stripOnExit = section.getStringList("strip-on-exit")
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
            clearOnJoin = section.getBoolean("clear-on-join", false),
            saveStatistics = section.getBoolean("save-statistics", false),
            saveAdvancements = section.getBoolean("save-advancements", false),
            saveRecipes = section.getBoolean("save-recipes", false),
            saveInventory = section.getBoolean("save-inventory", true),
            saveFlying = section.getBoolean("save-flying", true),
            saveAllowFlight = section.getBoolean("save-allow-flight", true),
            saveDisplayName = section.getBoolean("save-display-name", false),
            saveFallDistance = section.getBoolean("save-fall-distance", true),
            saveFireTicks = section.getBoolean("save-fire-ticks", true),
            saveMaximumAir = section.getBoolean("save-maximum-air", true),
            saveRemainingAir = section.getBoolean("save-remaining-air", true)
        )
    }

    /**
     * Loads group settings and returns both the settings and which fields were explicitly set.
     * This enables proper inheritance merging where only explicitly set values override parent.
     */
    fun loadGroupSettingsWithExplicit(section: ConfigurationSection?): Pair<GroupSettings, Set<String>> {
        if (section == null) return GroupSettings() to emptySet()

        val explicit = mutableSetOf<String>()
        val keys = section.getKeys(false)

        // Map of config key to property name
        val keyMapping = mapOf(
            "save-health" to "saveHealth",
            "save-hunger" to "saveHunger",
            "save-saturation" to "saveSaturation",
            "save-exhaustion" to "saveExhaustion",
            "save-experience" to "saveExperience",
            "save-potion-effects" to "savePotionEffects",
            "save-ender-chest" to "saveEnderChest",
            "save-gamemode" to "saveGameMode",
            "separate-gamemode-inventories" to "separateGameModeInventories",
            "clear-on-death" to "clearOnDeath",
            "clear-on-join" to "clearOnJoin",
            "separate-economy" to "separateEconomy",
            "save-statistics" to "saveStatistics",
            "save-advancements" to "saveAdvancements",
            "save-recipes" to "saveRecipes",
            "save-inventory" to "saveInventory",
            "save-flying" to "saveFlying",
            "save-allow-flight" to "saveAllowFlight",
            "save-display-name" to "saveDisplayName",
            "save-fall-distance" to "saveFallDistance",
            "save-fire-ticks" to "saveFireTicks",
            "save-maximum-air" to "saveMaximumAir",
            "save-remaining-air" to "saveRemainingAir"
        )

        keyMapping.forEach { (configKey, propName) ->
            if (keys.contains(configKey)) {
                explicit.add(propName)
            }
        }

        return loadGroupSettings(section) to explicit
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
        section.set("save-statistics", settings.saveStatistics)
        section.set("save-advancements", settings.saveAdvancements)
        section.set("save-recipes", settings.saveRecipes)
        section.set("save-inventory", settings.saveInventory)
        section.set("save-flying", settings.saveFlying)
        section.set("save-allow-flight", settings.saveAllowFlight)
        section.set("save-display-name", settings.saveDisplayName)
        section.set("save-fall-distance", settings.saveFallDistance)
        section.set("save-fire-ticks", settings.saveFireTicks)
        section.set("save-maximum-air", settings.saveMaximumAir)
        section.set("save-remaining-air", settings.saveRemainingAir)
    }

    /**
     * Loads player configuration (global defaults) from the main config.
     */
    private fun loadPlayerConfig(config: org.bukkit.configuration.file.FileConfiguration): PlayerConfig {
        return PlayerConfig(
            saveHealth = config.getBoolean("player.save-health", true),
            saveHunger = config.getBoolean("player.save-hunger", true),
            saveSaturation = config.getBoolean("player.save-saturation", true),
            saveExhaustion = config.getBoolean("player.save-exhaustion", true),
            saveExperience = config.getBoolean("player.save-experience", true),
            savePotionEffects = config.getBoolean("player.save-potion-effects", true),
            saveEnderChest = config.getBoolean("player.save-ender-chest", true),
            saveInventory = config.getBoolean("player.save-inventory", true),
            saveGameMode = config.getBoolean("player.save-gamemode", false),
            saveFlying = config.getBoolean("player.save-flying", true),
            saveAllowFlight = config.getBoolean("player.save-allow-flight", true),
            saveFallDistance = config.getBoolean("player.save-fall-distance", true),
            saveFireTicks = config.getBoolean("player.save-fire-ticks", true),
            saveMaximumAir = config.getBoolean("player.save-maximum-air", true),
            saveRemainingAir = config.getBoolean("player.save-remaining-air", true),
            saveDisplayName = config.getBoolean("player.save-display-name", false),
            saveStatistics = config.getBoolean("player.save-statistics", false),
            saveAdvancements = config.getBoolean("player.save-advancements", false),
            saveRecipes = config.getBoolean("player.save-recipes", false)
        )
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

    /**
     * Loads sync configuration from the main config.
     */
    private fun loadSyncConfig(config: org.bukkit.configuration.file.FileConfiguration): NetworkSyncConfig {
        val syncMode = try {
            SyncMode.valueOf(config.getString("sync.mode", "REDIS")!!.uppercase())
        } catch (e: Exception) {
            Logging.warning("Invalid sync mode, defaulting to REDIS")
            SyncMode.REDIS
        }

        val conflictStrategy = try {
            ConflictStrategy.valueOf(config.getString("sync.conflicts.strategy", "LAST_WRITE_WINS")!!.uppercase())
        } catch (e: Exception) {
            Logging.warning("Invalid conflict strategy, defaulting to LAST_WRITE_WINS")
            ConflictStrategy.LAST_WRITE_WINS
        }

        return NetworkSyncConfig(
            enabled = config.getBoolean("sync.enabled", false),
            mode = syncMode,
            serverId = config.getString("sync.server-id", "server-1")!!,
            redis = RedisSyncConfig(
                host = config.getString("sync.redis.host", "localhost")!!,
                port = config.getInt("sync.redis.port", 6379),
                password = config.getString("sync.redis.password", "")!!,
                channel = config.getString("sync.redis.channel", "xinventories:sync")!!,
                timeout = config.getInt("sync.redis.timeout", 5000)
            ),
            conflicts = ConflictSyncConfig(
                strategy = conflictStrategy,
                mergeRules = MergeRulesSyncConfig(
                    inventory = parseMergeRule(config.getString("sync.conflicts.merge-rules.inventory", "NEWER")),
                    experience = parseMergeRule(config.getString("sync.conflicts.merge-rules.experience", "HIGHER")),
                    health = parseMergeRule(config.getString("sync.conflicts.merge-rules.health", "CURRENT_SERVER")),
                    potionEffects = parseMergeRule(config.getString("sync.conflicts.merge-rules.potion-effects", "UNION"))
                )
            ),
            transferLock = TransferLockSyncConfig(
                enabled = config.getBoolean("sync.transfer-lock.enabled", true),
                timeoutSeconds = config.getInt("sync.transfer-lock.timeout-seconds", 10)
            ),
            heartbeat = HeartbeatSyncConfig(
                intervalSeconds = config.getInt("sync.heartbeat.interval-seconds", 30),
                timeoutSeconds = config.getInt("sync.heartbeat.timeout-seconds", 90)
            )
        )
    }

    private fun parseMergeRule(value: String?): MergeRule {
        return try {
            MergeRule.valueOf(value?.uppercase() ?: "NEWER")
        } catch (e: Exception) {
            MergeRule.NEWER
        }
    }

    /**
     * Loads GUI configuration from the main config.
     */
    private fun loadGUIConfig(config: org.bukkit.configuration.file.FileConfiguration): GUIConfigSection {
        return GUIConfigSection(
            theme = config.getString("gui.theme", "DEFAULT") ?: "DEFAULT",
            sounds = GUISoundsConfigSection(
                click = config.getString("gui.sounds.click", "UI_BUTTON_CLICK") ?: "UI_BUTTON_CLICK",
                success = config.getString("gui.sounds.success", "ENTITY_PLAYER_LEVELUP") ?: "ENTITY_PLAYER_LEVELUP",
                error = config.getString("gui.sounds.error", "ENTITY_VILLAGER_NO") ?: "ENTITY_VILLAGER_NO"
            )
        )
    }

    /**
     * Loads audit configuration from the main config.
     */
    private fun loadAuditConfig(config: org.bukkit.configuration.file.FileConfiguration): AuditConfig {
        return AuditConfig(
            enabled = config.getBoolean("audit.enabled", true),
            retentionDays = config.getInt("audit.retention-days", 30),
            logViews = config.getBoolean("audit.log-views", false),
            logSaves = config.getBoolean("audit.log-saves", true)
        )
    }

    /**
     * Loads anti-dupe configuration from the main config.
     */
    private fun loadAntiDupeConfig(config: org.bukkit.configuration.file.FileConfiguration): AntiDupeConfig {
        val sensitivityStr = config.getString("anti-dupe.sensitivity", "MEDIUM") ?: "MEDIUM"
        val sensitivity = try {
            DupeSensitivity.valueOf(sensitivityStr.uppercase())
        } catch (e: Exception) {
            Logging.warning("Invalid anti-dupe sensitivity '$sensitivityStr', defaulting to MEDIUM")
            DupeSensitivity.MEDIUM
        }

        return AntiDupeConfig(
            enabled = config.getBoolean("anti-dupe.enabled", true),
            sensitivity = sensitivity,
            minSwitchIntervalMs = config.getLong("anti-dupe.min-switch-interval-ms", sensitivity.minSwitchIntervalMs),
            freezeOnDetection = config.getBoolean("anti-dupe.freeze-on-detection", false),
            notifyAdmins = config.getBoolean("anti-dupe.notify-admins", true),
            logDetections = config.getBoolean("anti-dupe.log-detections", true)
        )
    }

    /**
     * Saves the main configuration to config.yml.
     * Note: This only saves certain runtime-modifiable settings.
     */
    fun saveMainConfig(updatedConfig: MainConfig) {
        try {
            val config = plugin.config

            // Update shared slots configuration
            config.set("shared-slots.enabled", updatedConfig.sharedSlots.enabled)
            val slotsList = updatedConfig.sharedSlots.slots.map { entry ->
                val map = mutableMapOf<String, Any?>()
                entry.slot?.let { map["slot"] = it }
                entry.slots?.let { map["slots"] = it }
                map["mode"] = entry.mode
                entry.item?.let { item ->
                    val itemMap = mutableMapOf<String, Any?>(
                        "type" to item.type,
                        "amount" to item.amount
                    )
                    item.displayName?.let { itemMap["display-name"] = it }
                    item.lore?.let { itemMap["lore"] = it }
                    map["item"] = itemMap
                }
                map
            }
            config.set("shared-slots.slots", slotsList)

            // Save the config file
            plugin.saveConfig()

            // Update the internal config reference
            mainConfig = updatedConfig

            Logging.debug { "Saved main configuration" }
        } catch (e: Exception) {
            Logging.error("Failed to save main configuration", e)
        }
    }

    /**
     * Loads shared slots configuration from the main config.
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadSharedSlotsConfig(config: org.bukkit.configuration.file.FileConfiguration): SharedSlotsConfigSection {
        val enabled = config.getBoolean("shared-slots.enabled", true)
        val slotConfigs = mutableListOf<SharedSlotConfigEntry>()

        val slotsList = config.getList("shared-slots.slots") as? List<Map<String, Any?>>
        slotsList?.forEach { slotMap ->
            try {
                val slot = (slotMap["slot"] as? Number)?.toInt()
                val slots = (slotMap["slots"] as? List<*>)?.mapNotNull { (it as? Number)?.toInt() }
                val mode = slotMap["mode"] as? String ?: "PRESERVE"

                val itemConfig = (slotMap["item"] as? Map<String, Any?>)?.let { itemMap ->
                    ItemConfigEntry(
                        type = itemMap["type"] as? String ?: return@let null,
                        amount = (itemMap["amount"] as? Number)?.toInt() ?: 1,
                        displayName = itemMap["display-name"] as? String,
                        lore = (itemMap["lore"] as? List<*>)?.mapNotNull { it as? String }
                    )
                }

                slotConfigs.add(SharedSlotConfigEntry(
                    slot = slot,
                    slots = slots,
                    mode = mode,
                    item = itemConfig
                ))
            } catch (e: Exception) {
                Logging.warning("Failed to parse shared slot config entry: ${e.message}")
            }
        }

        return SharedSlotsConfigSection(
            enabled = enabled,
            slots = slotConfigs
        )
    }
}

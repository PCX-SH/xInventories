package sh.pcx.xinventories.internal.config

import sh.pcx.xinventories.api.model.StorageType
import sh.pcx.xinventories.internal.model.ConflictStrategy
import sh.pcx.xinventories.internal.model.ItemConfig
import sh.pcx.xinventories.internal.model.MergeRule
import sh.pcx.xinventories.internal.model.MergeRules
import sh.pcx.xinventories.internal.model.SharedSlotEntry
import sh.pcx.xinventories.internal.model.SharedSlotsConfig
import sh.pcx.xinventories.internal.model.SlotMode

/**
 * Main configuration model (config.yml).
 */
data class MainConfig(
    val storage: StorageConfig = StorageConfig(),
    val cache: CacheConfig = CacheConfig(),
    val features: FeaturesConfig = FeaturesConfig(),
    val player: PlayerConfig = PlayerConfig(),
    val backup: BackupConfig = BackupConfig(),
    val performance: PerformanceConfig = PerformanceConfig(),
    val versioning: VersioningConfig = VersioningConfig(),
    val deathRecovery: DeathRecoveryConfig = DeathRecoveryConfig(),
    val locking: LockingConfig = LockingConfig(),
    val sharedSlots: SharedSlotsConfigSection = SharedSlotsConfigSection(),
    val economy: EconomyConfig = EconomyConfig(),
    val sync: NetworkSyncConfig = NetworkSyncConfig(),
    val startup: StartupConfig = StartupConfig(),
    val gui: GUIConfigSection = GUIConfigSection(),
    val audit: AuditConfig = AuditConfig(),
    val antiDupe: AntiDupeConfig = AntiDupeConfig(),
    val configVersion: Int = 1,
    val metrics: Boolean = true,
    val debug: Boolean = false
)

/**
 * Global player settings defaults.
 * These settings are used as defaults for all groups and can be overridden per-group.
 */
data class PlayerConfig(
    val saveHealth: Boolean = true,
    val saveHunger: Boolean = true,
    val saveSaturation: Boolean = true,
    val saveExhaustion: Boolean = true,
    val saveExperience: Boolean = true,
    val savePotionEffects: Boolean = true,
    val saveEnderChest: Boolean = true,
    val saveInventory: Boolean = true,
    val saveGameMode: Boolean = false,
    val saveFlying: Boolean = true,
    val saveAllowFlight: Boolean = true,
    val saveFallDistance: Boolean = true,
    val saveFireTicks: Boolean = true,
    val saveMaximumAir: Boolean = true,
    val saveRemainingAir: Boolean = true,
    val saveDisplayName: Boolean = false,
    val saveStatistics: Boolean = false,
    val saveAdvancements: Boolean = false,
    val saveRecipes: Boolean = false
) {
    /**
     * Converts to GroupSettings with all values set.
     */
    fun toGroupSettings(): sh.pcx.xinventories.api.model.GroupSettings {
        return sh.pcx.xinventories.api.model.GroupSettings(
            saveHealth = saveHealth,
            saveHunger = saveHunger,
            saveSaturation = saveSaturation,
            saveExhaustion = saveExhaustion,
            saveExperience = saveExperience,
            savePotionEffects = savePotionEffects,
            saveEnderChest = saveEnderChest,
            saveInventory = saveInventory,
            saveGameMode = saveGameMode,
            saveFlying = saveFlying,
            saveAllowFlight = saveAllowFlight,
            saveFallDistance = saveFallDistance,
            saveFireTicks = saveFireTicks,
            saveMaximumAir = saveMaximumAir,
            saveRemainingAir = saveRemainingAir,
            saveDisplayName = saveDisplayName,
            saveStatistics = saveStatistics,
            saveAdvancements = saveAdvancements,
            saveRecipes = saveRecipes
        )
    }
}

/**
 * Economy integration configuration for per-group economy balances.
 */
data class EconomyConfig(
    /** Whether economy integration is enabled */
    val enabled: Boolean = true,
    /** Economy provider to use (VAULT) */
    val provider: String = "VAULT",
    /** Whether to separate balances by group */
    val separateByGroup: Boolean = true
)

data class StorageConfig(
    val type: StorageType = StorageType.YAML,
    val sqlite: SqliteConfig = SqliteConfig(),
    val mysql: MysqlConfig = MysqlConfig()
)

data class SqliteConfig(
    val file: String = "data/inventories.db"
)

data class MysqlConfig(
    val host: String = "localhost",
    val port: Int = 3306,
    val database: String = "xinventories",
    val username: String = "root",
    val password: String = "",
    val pool: PoolConfig = PoolConfig()
)

data class PoolConfig(
    val maximumPoolSize: Int = 10,
    val minimumIdle: Int = 2,
    val connectionTimeout: Long = 30000,
    val idleTimeout: Long = 600000,
    val maxLifetime: Long = 1800000
)

data class CacheConfig(
    val enabled: Boolean = true,
    val maxSize: Int = 1000,
    val ttlMinutes: Int = 30,
    val writeBehindSeconds: Int = 5
)

data class FeaturesConfig(
    val separateGamemodeInventories: Boolean = true,
    val saveOnWorldChange: Boolean = true,
    val saveOnGamemodeChange: Boolean = true,
    val asyncSaving: Boolean = true,
    val clearOnDeath: Boolean = false,
    val adminNotifications: Boolean = true
)

data class BackupConfig(
    val autoBackup: Boolean = true,
    val intervalHours: Int = 24,
    val maxBackups: Int = 7,
    val compression: Boolean = true,
    val directory: String = "backups"
)

data class PerformanceConfig(
    val batchSize: Int = 100,
    val threadPoolSize: Int = 4,
    val saveDelayTicks: Int = 1
)

/**
 * Configuration for inventory locking feature.
 */
data class LockingConfig(
    val enabled: Boolean = true,
    val defaultMessage: String = "<red>Your inventory is currently locked.",
    val allowAdminBypass: Boolean = true
)

// ============================================================
// Shared Slots Configuration
// ============================================================

/**
 * Configuration section for shared slots in config.yml.
 */
data class SharedSlotsConfigSection(
    val enabled: Boolean = true,
    val slots: List<SharedSlotConfigEntry> = emptyList()
) {
    /**
     * Converts to the internal SharedSlotsConfig model.
     */
    fun toSharedSlotsConfig(): SharedSlotsConfig {
        return SharedSlotsConfig(
            enabled = enabled,
            slots = slots.map { it.toSharedSlotEntry() }
        )
    }
}

/**
 * Configuration entry for a single shared slot or slot group.
 */
data class SharedSlotConfigEntry(
    val slot: Int? = null,
    val slots: List<Int>? = null,
    val mode: String = "PRESERVE",
    val item: ItemConfigEntry? = null
) {
    /**
     * Converts to the internal SharedSlotEntry model.
     */
    fun toSharedSlotEntry(): SharedSlotEntry {
        val slotMode = try {
            SlotMode.valueOf(mode.uppercase())
        } catch (e: IllegalArgumentException) {
            SlotMode.PRESERVE
        }

        return SharedSlotEntry(
            slot = slot,
            slots = slots,
            mode = slotMode,
            item = item?.toItemConfig()
        )
    }
}

/**
 * Configuration for an enforced item in a slot.
 */
data class ItemConfigEntry(
    val type: String,
    val amount: Int = 1,
    val displayName: String? = null,
    val lore: List<String>? = null
) {
    /**
     * Converts to the internal ItemConfig model.
     */
    fun toItemConfig(): ItemConfig? {
        val material = org.bukkit.Material.matchMaterial(type) ?: return null
        return ItemConfig(
            type = material,
            amount = amount,
            displayName = displayName,
            lore = lore
        )
    }
}

// ============================================================
// Network Sync Configuration
// ============================================================

/**
 * Configuration for cross-server synchronization.
 */
data class NetworkSyncConfig(
    val enabled: Boolean = false,
    val mode: SyncMode = SyncMode.REDIS,
    val serverId: String = "server-1",
    val redis: RedisSyncConfig = RedisSyncConfig(),
    val conflicts: ConflictSyncConfig = ConflictSyncConfig(),
    val transferLock: TransferLockSyncConfig = TransferLockSyncConfig(),
    val heartbeat: HeartbeatSyncConfig = HeartbeatSyncConfig()
)

/**
 * Sync mode options.
 */
enum class SyncMode {
    REDIS,
    PLUGIN_MESSAGING,
    MYSQL_NOTIFY
}

/**
 * Redis connection configuration for sync.
 */
data class RedisSyncConfig(
    val host: String = "localhost",
    val port: Int = 6379,
    val password: String = "",
    val channel: String = "xinventories:sync",
    val timeout: Int = 5000
)

/**
 * Conflict resolution configuration.
 */
data class ConflictSyncConfig(
    val strategy: ConflictStrategy = ConflictStrategy.LAST_WRITE_WINS,
    val mergeRules: MergeRulesSyncConfig = MergeRulesSyncConfig()
)

/**
 * Merge rules configuration (maps to MergeRules model).
 */
data class MergeRulesSyncConfig(
    val inventory: MergeRule = MergeRule.NEWER,
    val experience: MergeRule = MergeRule.HIGHER,
    val health: MergeRule = MergeRule.CURRENT_SERVER,
    val potionEffects: MergeRule = MergeRule.UNION
) {
    fun toMergeRules(): MergeRules = MergeRules(
        inventory = inventory,
        experience = experience,
        health = health,
        potionEffects = potionEffects
    )
}

/**
 * Lock transfer configuration for server switches.
 */
data class TransferLockSyncConfig(
    val enabled: Boolean = true,
    val timeoutSeconds: Int = 10
)

/**
 * Heartbeat configuration for server health monitoring.
 */
data class HeartbeatSyncConfig(
    val intervalSeconds: Int = 30,
    val timeoutSeconds: Int = 90
)

// ============================================================
// GUI Configuration
// ============================================================

/**
 * Configuration section for GUI customization in config.yml.
 */
data class GUIConfigSection(
    /** The color theme for GUIs */
    val theme: String = "DEFAULT",
    /** Sound settings for GUI interactions */
    val sounds: GUISoundsConfigSection = GUISoundsConfigSection()
) {
    /**
     * Converts to the internal GUIConfig model.
     */
    fun toGUIConfig(): GUIConfig {
        val guiTheme = try {
            GUITheme.valueOf(theme.uppercase())
        } catch (e: IllegalArgumentException) {
            GUITheme.DEFAULT
        }

        return GUIConfig(
            theme = guiTheme,
            sounds = sounds.toGUISoundConfig()
        )
    }
}

/**
 * Sound configuration section for GUI interactions.
 */
data class GUISoundsConfigSection(
    /** Sound played when clicking a button */
    val click: String = "UI_BUTTON_CLICK",
    /** Sound played on successful action */
    val success: String = "ENTITY_PLAYER_LEVELUP",
    /** Sound played on error */
    val error: String = "ENTITY_VILLAGER_NO"
) {
    /**
     * Converts to the internal GUISoundConfig model.
     */
    fun toGUISoundConfig(): GUISoundConfig {
        val clickSound = try {
            org.bukkit.Sound.valueOf(click)
        } catch (e: IllegalArgumentException) {
            org.bukkit.Sound.UI_BUTTON_CLICK
        }

        val successSound = try {
            org.bukkit.Sound.valueOf(success)
        } catch (e: IllegalArgumentException) {
            org.bukkit.Sound.ENTITY_PLAYER_LEVELUP
        }

        val errorSound = try {
            org.bukkit.Sound.valueOf(error)
        } catch (e: IllegalArgumentException) {
            org.bukkit.Sound.ENTITY_VILLAGER_NO
        }

        return GUISoundConfig(
            click = clickSound,
            success = successSound,
            error = errorSound
        )
    }
}

// ============================================================
// Audit Configuration
// ============================================================

/**
 * Configuration for audit logging.
 */
data class AuditConfig(
    /** Whether audit logging is enabled */
    val enabled: Boolean = true,
    /** Number of days to keep audit entries */
    val retentionDays: Int = 30,
    /** Log when admins view player inventories */
    val logViews: Boolean = false,
    /** Log automatic inventory saves */
    val logSaves: Boolean = true
)

// ============================================================
// Anti-Dupe Configuration
// ============================================================

/**
 * Sensitivity levels for dupe detection.
 */
enum class DupeSensitivity(val minSwitchIntervalMs: Long, val anomalyThreshold: Double) {
    LOW(200, 3.0),      // Very lenient, only obvious dupes
    MEDIUM(500, 2.0),   // Balanced detection
    HIGH(1000, 1.5)     // Aggressive detection, may have false positives
}

/**
 * Configuration for anti-dupe detection.
 */
data class AntiDupeConfig(
    /** Enable anti-dupe detection */
    val enabled: Boolean = true,
    /** Sensitivity level for detection */
    val sensitivity: DupeSensitivity = DupeSensitivity.MEDIUM,
    /** Minimum milliseconds between group switches */
    val minSwitchIntervalMs: Long = 500,
    /** Freeze player inventory when high-severity dupe is detected */
    val freezeOnDetection: Boolean = false,
    /** Notify online admins when dupe is detected */
    val notifyAdmins: Boolean = true,
    /** Log all detections to console */
    val logDetections: Boolean = true
)

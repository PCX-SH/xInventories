package sh.pcx.xinventories.internal.config

import sh.pcx.xinventories.api.model.StorageType

/**
 * Main configuration model (config.yml).
 */
data class MainConfig(
    val storage: StorageConfig = StorageConfig(),
    val cache: CacheConfig = CacheConfig(),
    val features: FeaturesConfig = FeaturesConfig(),
    val backup: BackupConfig = BackupConfig(),
    val performance: PerformanceConfig = PerformanceConfig(),
    val debug: Boolean = false
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

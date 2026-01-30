package sh.pcx.xinventories.internal.storage.query

/**
 * SQL table definitions for xInventories.
 */
object Tables {

    const val PLAYER_DATA = "xinventories_player_data"

    /**
     * SQLite table creation statement.
     */
    val CREATE_PLAYER_DATA_SQLITE = """
        CREATE TABLE IF NOT EXISTS $PLAYER_DATA (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            uuid TEXT NOT NULL,
            player_name TEXT NOT NULL,
            group_name TEXT NOT NULL,
            gamemode TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            health REAL NOT NULL DEFAULT 20.0,
            max_health REAL NOT NULL DEFAULT 20.0,
            food_level INTEGER NOT NULL DEFAULT 20,
            saturation REAL NOT NULL DEFAULT 5.0,
            exhaustion REAL NOT NULL DEFAULT 0.0,
            experience REAL NOT NULL DEFAULT 0.0,
            level INTEGER NOT NULL DEFAULT 0,
            total_experience INTEGER NOT NULL DEFAULT 0,
            main_inventory TEXT,
            armor_inventory TEXT,
            offhand TEXT,
            ender_chest TEXT,
            potion_effects TEXT,
            balances TEXT,
            version INTEGER NOT NULL DEFAULT 0,
            statistics TEXT,
            advancements TEXT,
            recipes TEXT,
            is_flying INTEGER NOT NULL DEFAULT 0,
            allow_flight INTEGER NOT NULL DEFAULT 0,
            display_name TEXT,
            fall_distance REAL NOT NULL DEFAULT 0.0,
            fire_ticks INTEGER NOT NULL DEFAULT 0,
            maximum_air INTEGER NOT NULL DEFAULT 300,
            remaining_air INTEGER NOT NULL DEFAULT 300,
            UNIQUE(uuid, group_name, gamemode)
        )
    """.trimIndent()

    /**
     * MySQL table creation statement.
     */
    val CREATE_PLAYER_DATA_MYSQL = """
        CREATE TABLE IF NOT EXISTS $PLAYER_DATA (
            id BIGINT PRIMARY KEY AUTO_INCREMENT,
            uuid VARCHAR(36) NOT NULL,
            player_name VARCHAR(16) NOT NULL,
            group_name VARCHAR(64) NOT NULL,
            gamemode VARCHAR(16) NOT NULL,
            timestamp BIGINT NOT NULL,
            health DOUBLE NOT NULL DEFAULT 20.0,
            max_health DOUBLE NOT NULL DEFAULT 20.0,
            food_level INT NOT NULL DEFAULT 20,
            saturation FLOAT NOT NULL DEFAULT 5.0,
            exhaustion FLOAT NOT NULL DEFAULT 0.0,
            experience FLOAT NOT NULL DEFAULT 0.0,
            level INT NOT NULL DEFAULT 0,
            total_experience INT NOT NULL DEFAULT 0,
            main_inventory MEDIUMTEXT,
            armor_inventory TEXT,
            offhand TEXT,
            ender_chest MEDIUMTEXT,
            potion_effects TEXT,
            balances TEXT,
            version BIGINT NOT NULL DEFAULT 0,
            statistics MEDIUMTEXT,
            advancements MEDIUMTEXT,
            recipes MEDIUMTEXT,
            is_flying TINYINT(1) NOT NULL DEFAULT 0,
            allow_flight TINYINT(1) NOT NULL DEFAULT 0,
            display_name VARCHAR(256),
            fall_distance FLOAT NOT NULL DEFAULT 0.0,
            fire_ticks INT NOT NULL DEFAULT 0,
            maximum_air INT NOT NULL DEFAULT 300,
            remaining_air INT NOT NULL DEFAULT 300,
            UNIQUE KEY unique_player_group (uuid, group_name, gamemode),
            INDEX idx_uuid (uuid),
            INDEX idx_group (group_name)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    /**
     * Index creation for SQLite.
     */
    val CREATE_INDEXES_SQLITE = listOf(
        "CREATE INDEX IF NOT EXISTS idx_uuid ON $PLAYER_DATA (uuid)",
        "CREATE INDEX IF NOT EXISTS idx_group ON $PLAYER_DATA (group_name)",
        "CREATE INDEX IF NOT EXISTS idx_uuid_group ON $PLAYER_DATA (uuid, group_name)"
    )

    // ═══════════════════════════════════════════════════════════════════
    // Inventory Version Tables
    // ═══════════════════════════════════════════════════════════════════

    const val INVENTORY_VERSIONS = "xinventories_versions"

    /**
     * SQLite table for inventory versions.
     */
    val CREATE_VERSIONS_SQLITE = """
        CREATE TABLE IF NOT EXISTS $INVENTORY_VERSIONS (
            id TEXT PRIMARY KEY,
            player_uuid TEXT NOT NULL,
            group_name TEXT NOT NULL,
            gamemode TEXT,
            timestamp INTEGER NOT NULL,
            trigger_type TEXT NOT NULL,
            data TEXT NOT NULL,
            metadata TEXT
        )
    """.trimIndent()

    /**
     * MySQL table for inventory versions.
     */
    val CREATE_VERSIONS_MYSQL = """
        CREATE TABLE IF NOT EXISTS $INVENTORY_VERSIONS (
            id VARCHAR(36) PRIMARY KEY,
            player_uuid VARCHAR(36) NOT NULL,
            group_name VARCHAR(64) NOT NULL,
            gamemode VARCHAR(16),
            timestamp BIGINT NOT NULL,
            trigger_type VARCHAR(32) NOT NULL,
            data MEDIUMTEXT NOT NULL,
            metadata TEXT,
            INDEX idx_player_uuid (player_uuid),
            INDEX idx_player_group (player_uuid, group_name),
            INDEX idx_timestamp (timestamp)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    /**
     * Index creation for versions (SQLite).
     */
    val CREATE_VERSIONS_INDEXES_SQLITE = listOf(
        "CREATE INDEX IF NOT EXISTS idx_versions_player ON $INVENTORY_VERSIONS (player_uuid)",
        "CREATE INDEX IF NOT EXISTS idx_versions_player_group ON $INVENTORY_VERSIONS (player_uuid, group_name)",
        "CREATE INDEX IF NOT EXISTS idx_versions_timestamp ON $INVENTORY_VERSIONS (timestamp)"
    )

    // ═══════════════════════════════════════════════════════════════════
    // Death Record Tables
    // ═══════════════════════════════════════════════════════════════════

    const val DEATH_RECORDS = "xinventories_deaths"

    /**
     * SQLite table for death records.
     */
    val CREATE_DEATHS_SQLITE = """
        CREATE TABLE IF NOT EXISTS $DEATH_RECORDS (
            id TEXT PRIMARY KEY,
            player_uuid TEXT NOT NULL,
            timestamp INTEGER NOT NULL,
            world TEXT NOT NULL,
            x REAL NOT NULL,
            y REAL NOT NULL,
            z REAL NOT NULL,
            death_cause TEXT,
            killer_name TEXT,
            killer_uuid TEXT,
            group_name TEXT NOT NULL,
            gamemode TEXT NOT NULL,
            inventory_data TEXT NOT NULL
        )
    """.trimIndent()

    /**
     * MySQL table for death records.
     */
    val CREATE_DEATHS_MYSQL = """
        CREATE TABLE IF NOT EXISTS $DEATH_RECORDS (
            id VARCHAR(36) PRIMARY KEY,
            player_uuid VARCHAR(36) NOT NULL,
            timestamp BIGINT NOT NULL,
            world VARCHAR(64) NOT NULL,
            x DOUBLE NOT NULL,
            y DOUBLE NOT NULL,
            z DOUBLE NOT NULL,
            death_cause VARCHAR(64),
            killer_name VARCHAR(64),
            killer_uuid VARCHAR(36),
            group_name VARCHAR(64) NOT NULL,
            gamemode VARCHAR(16) NOT NULL,
            inventory_data MEDIUMTEXT NOT NULL,
            INDEX idx_deaths_player (player_uuid),
            INDEX idx_deaths_timestamp (timestamp)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    /**
     * Index creation for death records (SQLite).
     */
    val CREATE_DEATHS_INDEXES_SQLITE = listOf(
        "CREATE INDEX IF NOT EXISTS idx_deaths_player ON $DEATH_RECORDS (player_uuid)",
        "CREATE INDEX IF NOT EXISTS idx_deaths_timestamp ON $DEATH_RECORDS (timestamp)"
    )

    // ═══════════════════════════════════════════════════════════════════
    // Temporary Group Assignment Tables
    // ═══════════════════════════════════════════════════════════════════

    const val TEMP_GROUPS = "xinventories_temp_groups"

    /**
     * SQLite table for temporary group assignments.
     */
    val CREATE_TEMP_GROUPS_SQLITE = """
        CREATE TABLE IF NOT EXISTS $TEMP_GROUPS (
            player_uuid TEXT PRIMARY KEY,
            temp_group TEXT NOT NULL,
            original_group TEXT NOT NULL,
            expires_at INTEGER NOT NULL,
            assigned_by TEXT,
            assigned_at INTEGER NOT NULL,
            reason TEXT
        )
    """.trimIndent()

    /**
     * MySQL table for temporary group assignments.
     */
    val CREATE_TEMP_GROUPS_MYSQL = """
        CREATE TABLE IF NOT EXISTS $TEMP_GROUPS (
            player_uuid VARCHAR(36) PRIMARY KEY,
            temp_group VARCHAR(64) NOT NULL,
            original_group VARCHAR(64) NOT NULL,
            expires_at BIGINT NOT NULL,
            assigned_by VARCHAR(64),
            assigned_at BIGINT NOT NULL,
            reason VARCHAR(255),
            INDEX idx_temp_expires (expires_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    """.trimIndent()

    /**
     * Index creation for temporary groups (SQLite).
     */
    val CREATE_TEMP_GROUPS_INDEXES_SQLITE = listOf(
        "CREATE INDEX IF NOT EXISTS idx_temp_expires ON $TEMP_GROUPS (expires_at)"
    )

    // ═══════════════════════════════════════════════════════════════════
    // Schema Migration Queries
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Migration: Add PWI-style player state columns to player_data table (SQLite).
     * These columns are added with default values for backwards compatibility.
     */
    val MIGRATE_ADD_PLAYER_STATE_COLUMNS_SQLITE = listOf(
        "ALTER TABLE $PLAYER_DATA ADD COLUMN is_flying INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN allow_flight INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN display_name TEXT",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN fall_distance REAL NOT NULL DEFAULT 0.0",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN fire_ticks INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN maximum_air INTEGER NOT NULL DEFAULT 300",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN remaining_air INTEGER NOT NULL DEFAULT 300"
    )

    /**
     * Migration: Add PWI-style player state columns to player_data table (MySQL).
     * These columns are added with default values for backwards compatibility.
     */
    val MIGRATE_ADD_PLAYER_STATE_COLUMNS_MYSQL = listOf(
        "ALTER TABLE $PLAYER_DATA ADD COLUMN is_flying TINYINT(1) NOT NULL DEFAULT 0",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN allow_flight TINYINT(1) NOT NULL DEFAULT 0",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN display_name VARCHAR(256)",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN fall_distance FLOAT NOT NULL DEFAULT 0.0",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN fire_ticks INT NOT NULL DEFAULT 0",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN maximum_air INT NOT NULL DEFAULT 300",
        "ALTER TABLE $PLAYER_DATA ADD COLUMN remaining_air INT NOT NULL DEFAULT 300"
    )
}

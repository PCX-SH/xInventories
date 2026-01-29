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
}

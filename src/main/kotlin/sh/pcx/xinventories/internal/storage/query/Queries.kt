package sh.pcx.xinventories.internal.storage.query

/**
 * SQL query constants for xInventories.
 */
object Queries {

    private const val TABLE = Tables.PLAYER_DATA

    /**
     * Insert or update player data (SQLite).
     */
    val UPSERT_PLAYER_DATA_SQLITE = """
        INSERT INTO $TABLE (
            uuid, player_name, group_name, gamemode, timestamp,
            health, max_health, food_level, saturation, exhaustion,
            experience, level, total_experience,
            main_inventory, armor_inventory, offhand, ender_chest, potion_effects
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(uuid, group_name, gamemode) DO UPDATE SET
            player_name = excluded.player_name,
            timestamp = excluded.timestamp,
            health = excluded.health,
            max_health = excluded.max_health,
            food_level = excluded.food_level,
            saturation = excluded.saturation,
            exhaustion = excluded.exhaustion,
            experience = excluded.experience,
            level = excluded.level,
            total_experience = excluded.total_experience,
            main_inventory = excluded.main_inventory,
            armor_inventory = excluded.armor_inventory,
            offhand = excluded.offhand,
            ender_chest = excluded.ender_chest,
            potion_effects = excluded.potion_effects
    """.trimIndent()

    /**
     * Insert or update player data (MySQL).
     */
    val UPSERT_PLAYER_DATA_MYSQL = """
        INSERT INTO $TABLE (
            uuid, player_name, group_name, gamemode, timestamp,
            health, max_health, food_level, saturation, exhaustion,
            experience, level, total_experience,
            main_inventory, armor_inventory, offhand, ender_chest, potion_effects
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            player_name = VALUES(player_name),
            timestamp = VALUES(timestamp),
            health = VALUES(health),
            max_health = VALUES(max_health),
            food_level = VALUES(food_level),
            saturation = VALUES(saturation),
            exhaustion = VALUES(exhaustion),
            experience = VALUES(experience),
            level = VALUES(level),
            total_experience = VALUES(total_experience),
            main_inventory = VALUES(main_inventory),
            armor_inventory = VALUES(armor_inventory),
            offhand = VALUES(offhand),
            ender_chest = VALUES(ender_chest),
            potion_effects = VALUES(potion_effects)
    """.trimIndent()

    /**
     * Select player data by UUID, group, and gamemode.
     */
    val SELECT_PLAYER_DATA = """
        SELECT * FROM $TABLE
        WHERE uuid = ? AND group_name = ? AND gamemode = ?
    """.trimIndent()

    /**
     * Select player data by UUID and group (any gamemode).
     */
    val SELECT_PLAYER_DATA_BY_GROUP = """
        SELECT * FROM $TABLE
        WHERE uuid = ? AND group_name = ?
    """.trimIndent()

    /**
     * Select all player data for a UUID.
     */
    val SELECT_ALL_PLAYER_DATA = """
        SELECT * FROM $TABLE
        WHERE uuid = ?
    """.trimIndent()

    /**
     * Delete player data by UUID, group, and gamemode.
     */
    val DELETE_PLAYER_DATA = """
        DELETE FROM $TABLE
        WHERE uuid = ? AND group_name = ? AND gamemode = ?
    """.trimIndent()

    /**
     * Delete all player data for a group.
     */
    val DELETE_PLAYER_DATA_BY_GROUP = """
        DELETE FROM $TABLE
        WHERE uuid = ? AND group_name = ?
    """.trimIndent()

    /**
     * Delete all player data for a UUID.
     */
    val DELETE_ALL_PLAYER_DATA = """
        DELETE FROM $TABLE
        WHERE uuid = ?
    """.trimIndent()

    /**
     * Check if player data exists.
     */
    val EXISTS_PLAYER_DATA = """
        SELECT 1 FROM $TABLE
        WHERE uuid = ? AND group_name = ? AND gamemode = ?
        LIMIT 1
    """.trimIndent()

    /**
     * Check if any player data exists for a group.
     */
    val EXISTS_PLAYER_DATA_BY_GROUP = """
        SELECT 1 FROM $TABLE
        WHERE uuid = ? AND group_name = ?
        LIMIT 1
    """.trimIndent()

    /**
     * Get all unique UUIDs.
     */
    val SELECT_ALL_UUIDS = """
        SELECT DISTINCT uuid FROM $TABLE
    """.trimIndent()

    /**
     * Get all groups for a player.
     */
    val SELECT_PLAYER_GROUPS = """
        SELECT DISTINCT group_name FROM $TABLE
        WHERE uuid = ?
    """.trimIndent()

    /**
     * Count total entries.
     */
    val COUNT_ENTRIES = """
        SELECT COUNT(*) FROM $TABLE
    """.trimIndent()

    /**
     * Count entries for a player.
     */
    val COUNT_PLAYER_ENTRIES = """
        SELECT COUNT(*) FROM $TABLE
        WHERE uuid = ?
    """.trimIndent()
}

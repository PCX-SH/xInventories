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
            main_inventory, armor_inventory, offhand, ender_chest, potion_effects,
            balances, version, statistics, advancements, recipes,
            is_flying, allow_flight, display_name, fall_distance, fire_ticks, maximum_air, remaining_air
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            potion_effects = excluded.potion_effects,
            balances = excluded.balances,
            version = excluded.version,
            statistics = excluded.statistics,
            advancements = excluded.advancements,
            recipes = excluded.recipes,
            is_flying = excluded.is_flying,
            allow_flight = excluded.allow_flight,
            display_name = excluded.display_name,
            fall_distance = excluded.fall_distance,
            fire_ticks = excluded.fire_ticks,
            maximum_air = excluded.maximum_air,
            remaining_air = excluded.remaining_air
    """.trimIndent()

    /**
     * Insert or update player data (MySQL).
     */
    val UPSERT_PLAYER_DATA_MYSQL = """
        INSERT INTO $TABLE (
            uuid, player_name, group_name, gamemode, timestamp,
            health, max_health, food_level, saturation, exhaustion,
            experience, level, total_experience,
            main_inventory, armor_inventory, offhand, ender_chest, potion_effects,
            balances, version, statistics, advancements, recipes,
            is_flying, allow_flight, display_name, fall_distance, fire_ticks, maximum_air, remaining_air
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
            potion_effects = VALUES(potion_effects),
            balances = VALUES(balances),
            version = VALUES(version),
            statistics = VALUES(statistics),
            advancements = VALUES(advancements),
            recipes = VALUES(recipes),
            is_flying = VALUES(is_flying),
            allow_flight = VALUES(allow_flight),
            display_name = VALUES(display_name),
            fall_distance = VALUES(fall_distance),
            fire_ticks = VALUES(fire_ticks),
            maximum_air = VALUES(maximum_air),
            remaining_air = VALUES(remaining_air)
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

    // ═══════════════════════════════════════════════════════════════════
    // Inventory Version Queries
    // ═══════════════════════════════════════════════════════════════════

    private const val VERSIONS_TABLE = Tables.INVENTORY_VERSIONS

    /**
     * Insert version (works for both SQLite and MySQL).
     */
    val INSERT_VERSION = """
        INSERT INTO $VERSIONS_TABLE (
            id, player_uuid, group_name, gamemode, timestamp, trigger_type, data, metadata
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    /**
     * Select versions for a player, ordered by timestamp descending.
     */
    val SELECT_VERSIONS_BY_PLAYER = """
        SELECT * FROM $VERSIONS_TABLE
        WHERE player_uuid = ?
        ORDER BY timestamp DESC
        LIMIT ?
    """.trimIndent()

    /**
     * Select versions for a player and group, ordered by timestamp descending.
     */
    val SELECT_VERSIONS_BY_PLAYER_GROUP = """
        SELECT * FROM $VERSIONS_TABLE
        WHERE player_uuid = ? AND group_name = ?
        ORDER BY timestamp DESC
        LIMIT ?
    """.trimIndent()

    /**
     * Select a specific version by ID.
     */
    val SELECT_VERSION_BY_ID = """
        SELECT * FROM $VERSIONS_TABLE
        WHERE id = ?
    """.trimIndent()

    /**
     * Delete a specific version by ID.
     */
    val DELETE_VERSION_BY_ID = """
        DELETE FROM $VERSIONS_TABLE
        WHERE id = ?
    """.trimIndent()

    /**
     * Delete versions older than a timestamp.
     */
    val DELETE_VERSIONS_OLDER_THAN = """
        DELETE FROM $VERSIONS_TABLE
        WHERE timestamp < ?
    """.trimIndent()

    // ═══════════════════════════════════════════════════════════════════
    // Death Record Queries
    // ═══════════════════════════════════════════════════════════════════

    private const val DEATHS_TABLE = Tables.DEATH_RECORDS

    /**
     * Insert death record (works for both SQLite and MySQL).
     */
    val INSERT_DEATH_RECORD = """
        INSERT INTO $DEATHS_TABLE (
            id, player_uuid, timestamp, world, x, y, z,
            death_cause, killer_name, killer_uuid,
            group_name, gamemode, inventory_data
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    /**
     * Select death records for a player, ordered by timestamp descending.
     */
    val SELECT_DEATHS_BY_PLAYER = """
        SELECT * FROM $DEATHS_TABLE
        WHERE player_uuid = ?
        ORDER BY timestamp DESC
        LIMIT ?
    """.trimIndent()

    /**
     * Select a specific death record by ID.
     */
    val SELECT_DEATH_BY_ID = """
        SELECT * FROM $DEATHS_TABLE
        WHERE id = ?
    """.trimIndent()

    /**
     * Delete a specific death record by ID.
     */
    val DELETE_DEATH_BY_ID = """
        DELETE FROM $DEATHS_TABLE
        WHERE id = ?
    """.trimIndent()

    /**
     * Delete death records older than a timestamp.
     */
    val DELETE_DEATHS_OLDER_THAN = """
        DELETE FROM $DEATHS_TABLE
        WHERE timestamp < ?
    """.trimIndent()

    // ═══════════════════════════════════════════════════════════════════
    // Temporary Group Queries
    // ═══════════════════════════════════════════════════════════════════

    private const val TEMP_GROUPS_TABLE = Tables.TEMP_GROUPS

    /**
     * Insert or update temporary group assignment (SQLite).
     */
    val UPSERT_TEMP_GROUP_SQLITE = """
        INSERT INTO $TEMP_GROUPS_TABLE (
            player_uuid, temp_group, original_group, expires_at, assigned_by, assigned_at, reason
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(player_uuid) DO UPDATE SET
            temp_group = excluded.temp_group,
            original_group = excluded.original_group,
            expires_at = excluded.expires_at,
            assigned_by = excluded.assigned_by,
            assigned_at = excluded.assigned_at,
            reason = excluded.reason
    """.trimIndent()

    /**
     * Insert or update temporary group assignment (MySQL).
     */
    val UPSERT_TEMP_GROUP_MYSQL = """
        INSERT INTO $TEMP_GROUPS_TABLE (
            player_uuid, temp_group, original_group, expires_at, assigned_by, assigned_at, reason
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        ON DUPLICATE KEY UPDATE
            temp_group = VALUES(temp_group),
            original_group = VALUES(original_group),
            expires_at = VALUES(expires_at),
            assigned_by = VALUES(assigned_by),
            assigned_at = VALUES(assigned_at),
            reason = VALUES(reason)
    """.trimIndent()

    /**
     * Select temporary group assignment by player UUID.
     */
    val SELECT_TEMP_GROUP = """
        SELECT * FROM $TEMP_GROUPS_TABLE
        WHERE player_uuid = ?
    """.trimIndent()

    /**
     * Select all temporary group assignments.
     */
    val SELECT_ALL_TEMP_GROUPS = """
        SELECT * FROM $TEMP_GROUPS_TABLE
    """.trimIndent()

    /**
     * Delete temporary group assignment by player UUID.
     */
    val DELETE_TEMP_GROUP = """
        DELETE FROM $TEMP_GROUPS_TABLE
        WHERE player_uuid = ?
    """.trimIndent()

    /**
     * Delete expired temporary group assignments.
     */
    val DELETE_EXPIRED_TEMP_GROUPS = """
        DELETE FROM $TEMP_GROUPS_TABLE
        WHERE expires_at < ?
    """.trimIndent()
}

package sh.pcx.xinventories.internal.storage

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.config.MysqlConfig
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.TemporaryGroupAssignment
import sh.pcx.xinventories.internal.model.VersionTrigger
import sh.pcx.xinventories.internal.storage.query.Queries
import sh.pcx.xinventories.internal.storage.query.Tables
import sh.pcx.xinventories.internal.util.Logging
import sh.pcx.xinventories.internal.util.PlayerDataSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * MySQL storage implementation with HikariCP connection pooling.
 */
class MySqlStorage(
    plugin: PluginContext,
    private val config: MysqlConfig
) : AbstractStorage(plugin) {

    override val name = "MySQL"

    private var dataSource: HikariDataSource? = null

    override suspend fun doInitialize() {
        withContext(Dispatchers.IO) {
            val hikariConfig = HikariConfig().apply {
                jdbcUrl = "jdbc:mysql://${config.host}:${config.port}/${config.database}?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=UTF-8"
                username = config.username
                password = config.password

                maximumPoolSize = config.pool.maximumPoolSize
                minimumIdle = config.pool.minimumIdle
                connectionTimeout = config.pool.connectionTimeout
                idleTimeout = config.pool.idleTimeout
                maxLifetime = config.pool.maxLifetime

                poolName = "xInventories-MySQL"

                // Performance settings
                addDataSourceProperty("cachePrepStmts", "true")
                addDataSourceProperty("prepStmtCacheSize", "250")
                addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
                addDataSourceProperty("useServerPrepStmts", "true")
                addDataSourceProperty("useLocalSessionState", "true")
                addDataSourceProperty("rewriteBatchedStatements", "true")
                addDataSourceProperty("cacheResultSetMetadata", "true")
                addDataSourceProperty("cacheServerConfiguration", "true")
                addDataSourceProperty("elideSetAutoCommits", "true")
                addDataSourceProperty("maintainTimeStats", "false")
            }

            dataSource = HikariDataSource(hikariConfig)

            // Create tables
            dataSource?.connection?.use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.execute(Tables.CREATE_PLAYER_DATA_MYSQL)
                    stmt.execute(Tables.CREATE_VERSIONS_MYSQL)
                    stmt.execute(Tables.CREATE_DEATHS_MYSQL)
                    stmt.execute(Tables.CREATE_TEMP_GROUPS_MYSQL)
                }
            }

            // Run migrations for existing databases
            runMigrations()

            Logging.debug { "MySQL connection pool initialized" }
        }
    }

    override suspend fun doShutdown() {
        withContext(Dispatchers.IO) {
            dataSource?.close()
            dataSource = null
        }
    }

    private fun getConnection(): Connection {
        return dataSource?.connection ?: throw IllegalStateException("Database not connected")
    }

    /**
     * Runs schema migrations for existing databases.
     * Adds new columns if they don't exist.
     */
    private fun runMigrations() {
        // Check if new columns exist by trying to select them
        val columnsExist = try {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery("SELECT is_flying FROM ${Tables.PLAYER_DATA} LIMIT 1")
                    true
                }
            }
        } catch (e: Exception) {
            false
        }

        if (!columnsExist) {
            Logging.info("Running database migration: Adding PWI-style player state columns...")
            getConnection().use { conn ->
                Tables.MIGRATE_ADD_PLAYER_STATE_COLUMNS_MYSQL.forEach { query ->
                    try {
                        conn.createStatement().use { stmt ->
                            stmt.execute(query)
                        }
                    } catch (e: Exception) {
                        // Column might already exist, ignore
                        Logging.debug { "Migration query skipped (column may exist): ${e.message}" }
                    }
                }
            }
            Logging.info("Database migration completed.")
        }
    }

    override suspend fun doSavePlayerData(data: PlayerData) {
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                val sqlData = PlayerDataSerializer.toSqlMap(data)

                conn.prepareStatement(Queries.UPSERT_PLAYER_DATA_MYSQL).use { stmt ->
                    stmt.setString(1, sqlData["uuid"] as String)
                    stmt.setString(2, sqlData["player_name"] as String)
                    stmt.setString(3, sqlData["group_name"] as String)
                    stmt.setString(4, sqlData["gamemode"] as String)
                    stmt.setLong(5, sqlData["timestamp"] as Long)
                    stmt.setDouble(6, sqlData["health"] as Double)
                    stmt.setDouble(7, sqlData["max_health"] as Double)
                    stmt.setInt(8, sqlData["food_level"] as Int)
                    stmt.setFloat(9, sqlData["saturation"] as Float)
                    stmt.setFloat(10, sqlData["exhaustion"] as Float)
                    stmt.setFloat(11, sqlData["experience"] as Float)
                    stmt.setInt(12, sqlData["level"] as Int)
                    stmt.setInt(13, sqlData["total_experience"] as Int)
                    stmt.setString(14, sqlData["main_inventory"] as String)
                    stmt.setString(15, sqlData["armor_inventory"] as String)
                    stmt.setString(16, sqlData["offhand"] as String)
                    stmt.setString(17, sqlData["ender_chest"] as String)
                    stmt.setString(18, sqlData["potion_effects"] as String)
                    stmt.setString(19, sqlData["balances"] as? String ?: "")
                    stmt.setLong(20, sqlData["version"] as? Long ?: 0)
                    stmt.setString(21, sqlData["statistics"] as? String ?: "")
                    stmt.setString(22, sqlData["advancements"] as? String ?: "")
                    stmt.setString(23, sqlData["recipes"] as? String ?: "")
                    // PWI-style player state
                    stmt.setBoolean(24, sqlData["is_flying"] as Boolean)
                    stmt.setBoolean(25, sqlData["allow_flight"] as Boolean)
                    stmt.setString(26, sqlData["display_name"] as? String ?: "")
                    stmt.setFloat(27, sqlData["fall_distance"] as Float)
                    stmt.setInt(28, sqlData["fire_ticks"] as Int)
                    stmt.setInt(29, sqlData["maximum_air"] as Int)
                    stmt.setInt(30, sqlData["remaining_air"] as Int)

                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun doLoadPlayerData(uuid: UUID, group: String, gameMode: GameMode?): PlayerData? {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                if (gameMode != null) {
                    conn.prepareStatement(Queries.SELECT_PLAYER_DATA).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.setString(2, group)
                        stmt.setString(3, gameMode.name)

                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                resultSetToPlayerData(rs)
                            } else {
                                null
                            }
                        }
                    }
                } else {
                    conn.prepareStatement(Queries.SELECT_PLAYER_DATA_BY_GROUP).use { stmt ->
                        stmt.setString(1, uuid.toString())
                        stmt.setString(2, group)

                        stmt.executeQuery().use { rs ->
                            if (rs.next()) {
                                resultSetToPlayerData(rs)
                            } else {
                                null
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun doLoadAllPlayerData(uuid: UUID): Map<String, PlayerData> {
        return withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, PlayerData>()

            getConnection().use { conn ->
                conn.prepareStatement(Queries.SELECT_ALL_PLAYER_DATA).use { stmt ->
                    stmt.setString(1, uuid.toString())

                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val data = resultSetToPlayerData(rs)
                            if (data != null) {
                                val key = "${data.group}_${data.gameMode.name}"
                                result[key] = data
                            }
                        }
                    }
                }
            }

            result
        }
    }

    override suspend fun doDeletePlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                val query = if (gameMode != null) {
                    Queries.DELETE_PLAYER_DATA
                } else {
                    Queries.DELETE_PLAYER_DATA_BY_GROUP
                }

                conn.prepareStatement(query).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, group)
                    if (gameMode != null) {
                        stmt.setString(3, gameMode.name)
                    }

                    stmt.executeUpdate() > 0
                }
            }
        }
    }

    override suspend fun doDeleteAllPlayerData(uuid: UUID): Int {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.DELETE_ALL_PLAYER_DATA).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun doHasPlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                val query = if (gameMode != null) {
                    Queries.EXISTS_PLAYER_DATA
                } else {
                    Queries.EXISTS_PLAYER_DATA_BY_GROUP
                }

                conn.prepareStatement(query).use { stmt ->
                    stmt.setString(1, uuid.toString())
                    stmt.setString(2, group)
                    if (gameMode != null) {
                        stmt.setString(3, gameMode.name)
                    }

                    stmt.executeQuery().use { rs ->
                        rs.next()
                    }
                }
            }
        }
    }

    override suspend fun doGetAllPlayerUUIDs(): Set<UUID> {
        return withContext(Dispatchers.IO) {
            val result = mutableSetOf<UUID>()

            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(Queries.SELECT_ALL_UUIDS).use { rs ->
                        while (rs.next()) {
                            try {
                                result.add(UUID.fromString(rs.getString("uuid")))
                            } catch (e: Exception) {
                                Logging.debug { "Invalid UUID in database: ${rs.getString("uuid")}" }
                            }
                        }
                    }
                }
            }

            result
        }
    }

    override suspend fun doGetPlayerGroups(uuid: UUID): Set<String> {
        return withContext(Dispatchers.IO) {
            val result = mutableSetOf<String>()

            getConnection().use { conn ->
                conn.prepareStatement(Queries.SELECT_PLAYER_GROUPS).use { stmt ->
                    stmt.setString(1, uuid.toString())

                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            result.add(rs.getString("group_name"))
                        }
                    }
                }
            }

            result
        }
    }

    override suspend fun doGetEntryCount(): Int {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    stmt.executeQuery(Queries.COUNT_ENTRIES).use { rs ->
                        if (rs.next()) rs.getInt(1) else 0
                    }
                }
            }
        }
    }

    override suspend fun doGetStorageSize(): Long {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.createStatement().use { stmt ->
                    // MySQL-specific query to get table size
                    val query = """
                        SELECT data_length + index_length AS size
                        FROM information_schema.tables
                        WHERE table_schema = DATABASE()
                        AND table_name = '${Tables.PLAYER_DATA}'
                    """.trimIndent()

                    stmt.executeQuery(query).use { rs ->
                        if (rs.next()) rs.getLong("size") else -1L
                    }
                }
            }
        }
    }

    override suspend fun doIsHealthy(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                getConnection().use { conn ->
                    conn.isValid(5)
                }
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun doSavePlayerDataBatch(dataList: List<PlayerData>): Int {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.autoCommit = false
                var count = 0

                try {
                    conn.prepareStatement(Queries.UPSERT_PLAYER_DATA_MYSQL).use { stmt ->
                        for (data in dataList) {
                            val sqlData = PlayerDataSerializer.toSqlMap(data)

                            stmt.setString(1, sqlData["uuid"] as String)
                            stmt.setString(2, sqlData["player_name"] as String)
                            stmt.setString(3, sqlData["group_name"] as String)
                            stmt.setString(4, sqlData["gamemode"] as String)
                            stmt.setLong(5, sqlData["timestamp"] as Long)
                            stmt.setDouble(6, sqlData["health"] as Double)
                            stmt.setDouble(7, sqlData["max_health"] as Double)
                            stmt.setInt(8, sqlData["food_level"] as Int)
                            stmt.setFloat(9, sqlData["saturation"] as Float)
                            stmt.setFloat(10, sqlData["exhaustion"] as Float)
                            stmt.setFloat(11, sqlData["experience"] as Float)
                            stmt.setInt(12, sqlData["level"] as Int)
                            stmt.setInt(13, sqlData["total_experience"] as Int)
                            stmt.setString(14, sqlData["main_inventory"] as String)
                            stmt.setString(15, sqlData["armor_inventory"] as String)
                            stmt.setString(16, sqlData["offhand"] as String)
                            stmt.setString(17, sqlData["ender_chest"] as String)
                            stmt.setString(18, sqlData["potion_effects"] as String)
                            stmt.setString(19, sqlData["balances"] as? String ?: "")
                            stmt.setLong(20, sqlData["version"] as? Long ?: 0)
                            stmt.setString(21, sqlData["statistics"] as? String ?: "")
                            stmt.setString(22, sqlData["advancements"] as? String ?: "")
                            stmt.setString(23, sqlData["recipes"] as? String ?: "")
                            // PWI-style player state
                            stmt.setBoolean(24, sqlData["is_flying"] as Boolean)
                            stmt.setBoolean(25, sqlData["allow_flight"] as Boolean)
                            stmt.setString(26, sqlData["display_name"] as? String ?: "")
                            stmt.setFloat(27, sqlData["fall_distance"] as Float)
                            stmt.setInt(28, sqlData["fire_ticks"] as Int)
                            stmt.setInt(29, sqlData["maximum_air"] as Int)
                            stmt.setInt(30, sqlData["remaining_air"] as Int)

                            stmt.addBatch()
                            count++
                        }

                        stmt.executeBatch()
                    }

                    conn.commit()
                    count
                } catch (e: Exception) {
                    conn.rollback()
                    throw e
                } finally {
                    conn.autoCommit = true
                }
            }
        }
    }

    private fun resultSetToPlayerData(rs: ResultSet): PlayerData? {
        return try {
            val row = mapOf(
                "uuid" to rs.getString("uuid"),
                "player_name" to rs.getString("player_name"),
                "group_name" to rs.getString("group_name"),
                "gamemode" to rs.getString("gamemode"),
                "timestamp" to rs.getLong("timestamp"),
                "health" to rs.getDouble("health"),
                "max_health" to rs.getDouble("max_health"),
                "food_level" to rs.getInt("food_level"),
                "saturation" to rs.getFloat("saturation"),
                "exhaustion" to rs.getFloat("exhaustion"),
                "experience" to rs.getFloat("experience"),
                "level" to rs.getInt("level"),
                "total_experience" to rs.getInt("total_experience"),
                "main_inventory" to rs.getString("main_inventory"),
                "armor_inventory" to rs.getString("armor_inventory"),
                "offhand" to rs.getString("offhand"),
                "ender_chest" to rs.getString("ender_chest"),
                "potion_effects" to rs.getString("potion_effects"),
                "balances" to rs.getString("balances"),
                "version" to rs.getLong("version"),
                "statistics" to getStringOrNull(rs, "statistics"),
                "advancements" to getStringOrNull(rs, "advancements"),
                "recipes" to getStringOrNull(rs, "recipes"),
                // PWI-style player state (optional columns - backwards compatible)
                "is_flying" to getBooleanOrNull(rs, "is_flying"),
                "allow_flight" to getBooleanOrNull(rs, "allow_flight"),
                "display_name" to getStringOrNull(rs, "display_name"),
                "fall_distance" to getFloatOrNull(rs, "fall_distance"),
                "fire_ticks" to getIntOrNull(rs, "fire_ticks"),
                "maximum_air" to getIntOrNull(rs, "maximum_air"),
                "remaining_air" to getIntOrNull(rs, "remaining_air")
            )

            PlayerDataSerializer.fromSqlMap(row)
        } catch (e: Exception) {
            Logging.error("Failed to parse player data from result set", e)
            null
        }
    }

    /**
     * Safely gets a string column that may not exist in older databases.
     */
    private fun getStringOrNull(rs: ResultSet, columnName: String): String? {
        return try {
            rs.getString(columnName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Safely gets a boolean column that may not exist in older databases.
     */
    private fun getBooleanOrNull(rs: ResultSet, columnName: String): Boolean? {
        return try {
            rs.getBoolean(columnName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Safely gets an int column that may not exist in older databases.
     */
    private fun getIntOrNull(rs: ResultSet, columnName: String): Int? {
        return try {
            rs.getInt(columnName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Safely gets a float column that may not exist in older databases.
     */
    private fun getFloatOrNull(rs: ResultSet, columnName: String): Float? {
        return try {
            rs.getFloat(columnName)
        } catch (e: Exception) {
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Inventory Version Storage
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun doSaveVersion(version: InventoryVersion) {
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                // Serialize PlayerData to YAML string for storage
                val dataYaml = YamlConfiguration()
                PlayerDataSerializer.toYaml(version.data, dataYaml)
                val dataString = dataYaml.saveToString()

                // Serialize metadata to JSON-like string
                val metadataString = version.metadata.entries.joinToString(";") { "${it.key}=${it.value}" }

                conn.prepareStatement(Queries.INSERT_VERSION).use { stmt ->
                    stmt.setString(1, version.id)
                    stmt.setString(2, version.playerUuid.toString())
                    stmt.setString(3, version.group)
                    stmt.setString(4, version.gameMode?.name)
                    stmt.setLong(5, version.timestamp.toEpochMilli())
                    stmt.setString(6, version.trigger.name)
                    stmt.setString(7, dataString)
                    stmt.setString(8, metadataString)

                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun doLoadVersions(playerUuid: UUID, group: String?, limit: Int): List<InventoryVersion> {
        return withContext(Dispatchers.IO) {
            val versions = mutableListOf<InventoryVersion>()

            getConnection().use { conn ->
                val query = if (group != null) {
                    Queries.SELECT_VERSIONS_BY_PLAYER_GROUP
                } else {
                    Queries.SELECT_VERSIONS_BY_PLAYER
                }

                conn.prepareStatement(query).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    if (group != null) {
                        stmt.setString(2, group)
                        stmt.setInt(3, limit)
                    } else {
                        stmt.setInt(2, limit)
                    }

                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val version = resultSetToVersion(rs)
                            if (version != null) {
                                versions.add(version)
                            }
                        }
                    }
                }
            }

            versions
        }
    }

    override suspend fun doLoadVersion(versionId: String): InventoryVersion? {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.SELECT_VERSION_BY_ID).use { stmt ->
                    stmt.setString(1, versionId)

                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            resultSetToVersion(rs)
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    override suspend fun doDeleteVersion(versionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.DELETE_VERSION_BY_ID).use { stmt ->
                    stmt.setString(1, versionId)
                    stmt.executeUpdate() > 0
                }
            }
        }
    }

    override suspend fun doPruneVersions(olderThan: Instant): Int {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.DELETE_VERSIONS_OLDER_THAN).use { stmt ->
                    stmt.setLong(1, olderThan.toEpochMilli())
                    return@withContext stmt.executeUpdate()
                }
            }
        }
    }

    private fun resultSetToVersion(rs: ResultSet): InventoryVersion? {
        return try {
            val id = rs.getString("id")
            val playerUuid = UUID.fromString(rs.getString("player_uuid"))
            val group = rs.getString("group_name")
            val gameMode = rs.getString("gamemode")?.let {
                try { GameMode.valueOf(it) } catch (e: IllegalArgumentException) { null }
            }
            val timestamp = Instant.ofEpochMilli(rs.getLong("timestamp"))
            val trigger = try {
                VersionTrigger.valueOf(rs.getString("trigger_type"))
            } catch (e: IllegalArgumentException) {
                Logging.warning("Invalid trigger type in database, defaulting to MANUAL")
                VersionTrigger.MANUAL
            }
            val dataString = rs.getString("data")
            val metadataString = rs.getString("metadata") ?: ""

            // Parse data from YAML string
            val dataYaml = YamlConfiguration()
            dataYaml.loadFromString(dataString)
            val data = PlayerDataSerializer.fromYaml(dataYaml)
                ?: return null

            // Parse metadata
            val metadata = if (metadataString.isNotEmpty()) {
                metadataString.split(";")
                    .filter { it.contains("=") }
                    .associate {
                        val parts = it.split("=", limit = 2)
                        parts[0] to parts.getOrElse(1) { "" }
                    }
            } else {
                emptyMap()
            }

            InventoryVersion.fromStorage(id, playerUuid, group, gameMode, timestamp, trigger, data, metadata)
        } catch (e: Exception) {
            Logging.error("Failed to parse version from result set", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Death Record Storage
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun doSaveDeathRecord(record: DeathRecord) {
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                // Serialize PlayerData to YAML string for storage
                val dataYaml = YamlConfiguration()
                PlayerDataSerializer.toYaml(record.inventoryData, dataYaml)
                val dataString = dataYaml.saveToString()

                conn.prepareStatement(Queries.INSERT_DEATH_RECORD).use { stmt ->
                    stmt.setString(1, record.id)
                    stmt.setString(2, record.playerUuid.toString())
                    stmt.setLong(3, record.timestamp.toEpochMilli())
                    stmt.setString(4, record.world)
                    stmt.setDouble(5, record.x)
                    stmt.setDouble(6, record.y)
                    stmt.setDouble(7, record.z)
                    stmt.setString(8, record.deathCause)
                    stmt.setString(9, record.killerName)
                    stmt.setString(10, record.killerUuid?.toString())
                    stmt.setString(11, record.group)
                    stmt.setString(12, record.gameMode.name)
                    stmt.setString(13, dataString)

                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun doLoadDeathRecords(playerUuid: UUID, limit: Int): List<DeathRecord> {
        return withContext(Dispatchers.IO) {
            val records = mutableListOf<DeathRecord>()

            getConnection().use { conn ->
                conn.prepareStatement(Queries.SELECT_DEATHS_BY_PLAYER).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    stmt.setInt(2, limit)

                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            val record = resultSetToDeathRecord(rs)
                            if (record != null) {
                                records.add(record)
                            }
                        }
                    }
                }
            }

            records
        }
    }

    override suspend fun doLoadDeathRecord(deathId: String): DeathRecord? {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.SELECT_DEATH_BY_ID).use { stmt ->
                    stmt.setString(1, deathId)

                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            resultSetToDeathRecord(rs)
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    override suspend fun doDeleteDeathRecord(deathId: String): Boolean {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.DELETE_DEATH_BY_ID).use { stmt ->
                    stmt.setString(1, deathId)
                    stmt.executeUpdate() > 0
                }
            }
        }
    }

    override suspend fun doPruneDeathRecords(olderThan: Instant): Int {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.DELETE_DEATHS_OLDER_THAN).use { stmt ->
                    stmt.setLong(1, olderThan.toEpochMilli())
                    return@withContext stmt.executeUpdate()
                }
            }
        }
    }

    private fun resultSetToDeathRecord(rs: ResultSet): DeathRecord? {
        return try {
            val id = rs.getString("id")
            val playerUuid = UUID.fromString(rs.getString("player_uuid"))
            val timestamp = Instant.ofEpochMilli(rs.getLong("timestamp"))
            val world = rs.getString("world")
            val x = rs.getDouble("x")
            val y = rs.getDouble("y")
            val z = rs.getDouble("z")
            val deathCause = rs.getString("death_cause")
            val killerName = rs.getString("killer_name")
            val killerUuid = rs.getString("killer_uuid")?.let { UUID.fromString(it) }
            val group = rs.getString("group_name")
            val gameMode = GameMode.valueOf(rs.getString("gamemode"))
            val dataString = rs.getString("inventory_data")

            // Parse data from YAML string
            val dataYaml = YamlConfiguration()
            dataYaml.loadFromString(dataString)
            val inventoryData = PlayerDataSerializer.fromYaml(dataYaml)
                ?: return null

            DeathRecord.fromStorage(
                id, playerUuid, timestamp, world, x, y, z,
                deathCause, killerName, killerUuid, group, gameMode, inventoryData
            )
        } catch (e: Exception) {
            Logging.error("Failed to parse death record from result set", e)
            null
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Temporary Group Assignment Storage
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun doSaveTempGroupAssignment(assignment: TemporaryGroupAssignment) {
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.UPSERT_TEMP_GROUP_MYSQL).use { stmt ->
                    stmt.setString(1, assignment.playerUuid.toString())
                    stmt.setString(2, assignment.temporaryGroup)
                    stmt.setString(3, assignment.originalGroup)
                    stmt.setLong(4, assignment.expiresAt.toEpochMilli())
                    stmt.setString(5, assignment.assignedBy)
                    stmt.setLong(6, assignment.assignedAt.toEpochMilli())
                    stmt.setString(7, assignment.reason)
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun doLoadTempGroupAssignment(playerUuid: UUID): TemporaryGroupAssignment? {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.SELECT_TEMP_GROUP).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) {
                            resultSetToTempGroupAssignment(rs)
                        } else {
                            null
                        }
                    }
                }
            }
        }
    }

    override suspend fun doLoadAllTempGroupAssignments(): List<TemporaryGroupAssignment> {
        return withContext(Dispatchers.IO) {
            val assignments = mutableListOf<TemporaryGroupAssignment>()

            getConnection().use { conn ->
                conn.prepareStatement(Queries.SELECT_ALL_TEMP_GROUPS).use { stmt ->
                    stmt.executeQuery().use { rs ->
                        while (rs.next()) {
                            resultSetToTempGroupAssignment(rs)?.let { assignments.add(it) }
                        }
                    }
                }
            }

            assignments
        }
    }

    override suspend fun doDeleteTempGroupAssignment(playerUuid: UUID) {
        withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.DELETE_TEMP_GROUP).use { stmt ->
                    stmt.setString(1, playerUuid.toString())
                    stmt.executeUpdate()
                }
            }
        }
    }

    override suspend fun doPruneExpiredTempGroups(): Int {
        return withContext(Dispatchers.IO) {
            getConnection().use { conn ->
                conn.prepareStatement(Queries.DELETE_EXPIRED_TEMP_GROUPS).use { stmt ->
                    stmt.setLong(1, Instant.now().toEpochMilli())
                    return@withContext stmt.executeUpdate()
                }
            }
        }
    }

    private fun resultSetToTempGroupAssignment(rs: ResultSet): TemporaryGroupAssignment? {
        return try {
            TemporaryGroupAssignment(
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                temporaryGroup = rs.getString("temp_group"),
                originalGroup = rs.getString("original_group"),
                expiresAt = Instant.ofEpochMilli(rs.getLong("expires_at")),
                assignedBy = rs.getString("assigned_by"),
                assignedAt = Instant.ofEpochMilli(rs.getLong("assigned_at")),
                reason = rs.getString("reason")
            )
        } catch (e: Exception) {
            Logging.error("Failed to parse temp group assignment from result set", e)
            null
        }
    }
}

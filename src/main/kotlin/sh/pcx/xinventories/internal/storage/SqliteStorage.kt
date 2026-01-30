package sh.pcx.xinventories.internal.storage

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.VersionTrigger
import sh.pcx.xinventories.internal.storage.query.Queries
import sh.pcx.xinventories.internal.storage.query.Tables
import sh.pcx.xinventories.internal.util.Logging
import sh.pcx.xinventories.internal.util.PlayerDataSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

/**
 * SQLite storage implementation.
 */
class SqliteStorage(
    plugin: XInventories,
    private val dbFile: String
) : AbstractStorage(plugin) {

    override val name = "SQLite"

    private var connection: Connection? = null
    private lateinit var databaseFile: File

    override suspend fun doInitialize() {
        withContext(Dispatchers.IO) {
            // Create database file
            databaseFile = File(plugin.dataFolder, dbFile)
            databaseFile.parentFile?.mkdirs()

            // Load SQLite driver
            Class.forName("org.sqlite.JDBC")

            // Create connection
            connection = DriverManager.getConnection("jdbc:sqlite:${databaseFile.absolutePath}")

            // Create tables
            connection?.createStatement()?.use { stmt ->
                stmt.execute(Tables.CREATE_PLAYER_DATA_SQLITE)
                Tables.CREATE_INDEXES_SQLITE.forEach { index ->
                    stmt.execute(index)
                }

                // Create version tables
                stmt.execute(Tables.CREATE_VERSIONS_SQLITE)
                Tables.CREATE_VERSIONS_INDEXES_SQLITE.forEach { index ->
                    stmt.execute(index)
                }

                // Create death tables
                stmt.execute(Tables.CREATE_DEATHS_SQLITE)
                Tables.CREATE_DEATHS_INDEXES_SQLITE.forEach { index ->
                    stmt.execute(index)
                }
            }

            Logging.debug { "SQLite database initialized at ${databaseFile.absolutePath}" }
        }
    }

    override suspend fun doShutdown() {
        withContext(Dispatchers.IO) {
            connection?.close()
            connection = null
        }
    }

    override suspend fun doSavePlayerData(data: PlayerData) {
        withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")
            val sqlData = PlayerDataSerializer.toSqlMap(data)

            conn.prepareStatement(Queries.UPSERT_PLAYER_DATA_SQLITE).use { stmt ->
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

                stmt.executeUpdate()
            }
        }
    }

    override suspend fun doLoadPlayerData(uuid: UUID, group: String, gameMode: GameMode?): PlayerData? {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

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
                // Load first match for group (any gamemode)
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

    override suspend fun doLoadAllPlayerData(uuid: UUID): Map<String, PlayerData> {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")
            val result = mutableMapOf<String, PlayerData>()

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

            result
        }
    }

    override suspend fun doDeletePlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

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

    override suspend fun doDeleteAllPlayerData(uuid: UUID): Int {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

            conn.prepareStatement(Queries.DELETE_ALL_PLAYER_DATA).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.executeUpdate()
            }
        }
    }

    override suspend fun doHasPlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

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

    override suspend fun doGetAllPlayerUUIDs(): Set<UUID> {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")
            val result = mutableSetOf<UUID>()

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

            result
        }
    }

    override suspend fun doGetPlayerGroups(uuid: UUID): Set<String> {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")
            val result = mutableSetOf<String>()

            conn.prepareStatement(Queries.SELECT_PLAYER_GROUPS).use { stmt ->
                stmt.setString(1, uuid.toString())

                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        result.add(rs.getString("group_name"))
                    }
                }
            }

            result
        }
    }

    override suspend fun doGetEntryCount(): Int {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

            conn.createStatement().use { stmt ->
                stmt.executeQuery(Queries.COUNT_ENTRIES).use { rs ->
                    if (rs.next()) rs.getInt(1) else 0
                }
            }
        }
    }

    override suspend fun doGetStorageSize(): Long {
        return withContext(Dispatchers.IO) {
            if (databaseFile.exists()) {
                databaseFile.length()
            } else {
                0L
            }
        }
    }

    override suspend fun doIsHealthy(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                connection?.isValid(5) == true
            } catch (e: Exception) {
                false
            }
        }
    }

    override suspend fun doSavePlayerDataBatch(dataList: List<PlayerData>): Int {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

            conn.autoCommit = false
            var count = 0

            try {
                conn.prepareStatement(Queries.UPSERT_PLAYER_DATA_SQLITE).use { stmt ->
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
                "recipes" to getStringOrNull(rs, "recipes")
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

    // ═══════════════════════════════════════════════════════════════════
    // Inventory Version Storage
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun doSaveVersion(version: InventoryVersion) {
        withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

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

    override suspend fun doLoadVersions(playerUuid: UUID, group: String?, limit: Int): List<InventoryVersion> {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")
            val versions = mutableListOf<InventoryVersion>()

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

            versions
        }
    }

    override suspend fun doLoadVersion(versionId: String): InventoryVersion? {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

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

    override suspend fun doDeleteVersion(versionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

            conn.prepareStatement(Queries.DELETE_VERSION_BY_ID).use { stmt ->
                stmt.setString(1, versionId)
                stmt.executeUpdate() > 0
            }
        }
    }

    override suspend fun doPruneVersions(olderThan: Instant): Int {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

            conn.prepareStatement(Queries.DELETE_VERSIONS_OLDER_THAN).use { stmt ->
                stmt.setLong(1, olderThan.toEpochMilli())
                stmt.executeUpdate()
            }
        }
    }

    private fun resultSetToVersion(rs: ResultSet): InventoryVersion? {
        return try {
            val id = rs.getString("id")
            val playerUuid = UUID.fromString(rs.getString("player_uuid"))
            val group = rs.getString("group_name")
            val gameMode = rs.getString("gamemode")?.let { GameMode.valueOf(it) }
            val timestamp = Instant.ofEpochMilli(rs.getLong("timestamp"))
            val trigger = VersionTrigger.valueOf(rs.getString("trigger_type"))
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
            val conn = connection ?: throw IllegalStateException("Database not connected")

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

    override suspend fun doLoadDeathRecords(playerUuid: UUID, limit: Int): List<DeathRecord> {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")
            val records = mutableListOf<DeathRecord>()

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

            records
        }
    }

    override suspend fun doLoadDeathRecord(deathId: String): DeathRecord? {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

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

    override suspend fun doDeleteDeathRecord(deathId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

            conn.prepareStatement(Queries.DELETE_DEATH_BY_ID).use { stmt ->
                stmt.setString(1, deathId)
                stmt.executeUpdate() > 0
            }
        }
    }

    override suspend fun doPruneDeathRecords(olderThan: Instant): Int {
        return withContext(Dispatchers.IO) {
            val conn = connection ?: throw IllegalStateException("Database not connected")

            conn.prepareStatement(Queries.DELETE_DEATHS_OLDER_THAN).use { stmt ->
                stmt.setLong(1, olderThan.toEpochMilli())
                stmt.executeUpdate()
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
}

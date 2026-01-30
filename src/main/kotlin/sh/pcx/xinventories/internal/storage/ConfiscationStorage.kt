package sh.pcx.xinventories.internal.storage

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.ConfiscatedItem
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Storage backend for confiscated items.
 * Items are stored here when they violate restrictions with MOVE_TO_VAULT action.
 * Players can later retrieve their confiscated items through a GUI or command.
 */
class ConfiscationStorage(private val plugin: XInventories) {

    private var connection: Connection? = null
    private val dbFile: File = File(plugin.dataFolder, "data/confiscations.db")

    /**
     * Initializes the confiscation storage.
     */
    suspend fun initialize() {
        try {
            // Ensure data directory exists
            dbFile.parentFile?.mkdirs()

            // Initialize SQLite connection
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}")

            // Create table
            connection?.createStatement()?.use { stmt ->
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS xinv_confiscations (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        player_uuid TEXT NOT NULL,
                        item_data TEXT NOT NULL,
                        item_type TEXT NOT NULL,
                        item_name TEXT NOT NULL,
                        amount INTEGER NOT NULL,
                        confiscated_at INTEGER NOT NULL,
                        reason TEXT NOT NULL,
                        group_name TEXT NOT NULL,
                        world_name TEXT
                    )
                """.trimIndent())

                // Create indexes
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_confiscations_player ON xinv_confiscations(player_uuid)")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_confiscations_timestamp ON xinv_confiscations(confiscated_at)")
            }

            Logging.debug { "ConfiscationStorage initialized at ${dbFile.absolutePath}" }
        } catch (e: Exception) {
            Logging.error("Failed to initialize ConfiscationStorage", e)
            throw e
        }
    }

    /**
     * Shuts down the confiscation storage.
     */
    suspend fun shutdown() {
        try {
            connection?.close()
            connection = null
            Logging.debug { "ConfiscationStorage shut down" }
        } catch (e: Exception) {
            Logging.error("Error shutting down ConfiscationStorage", e)
        }
    }

    /**
     * Stores a confiscated item.
     */
    suspend fun storeItem(item: ConfiscatedItem): Long {
        val conn = connection ?: return -1

        try {
            val sql = """
                INSERT INTO xinv_confiscations
                (player_uuid, item_data, item_type, item_name, amount, confiscated_at, reason, group_name, world_name)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setString(1, item.playerUuid.toString())
                stmt.setString(2, item.itemData)
                stmt.setString(3, item.itemType)
                stmt.setString(4, item.itemName)
                stmt.setInt(5, item.amount)
                stmt.setLong(6, item.confiscatedAt.toEpochMilli())
                stmt.setString(7, item.reason)
                stmt.setString(8, item.groupName)
                stmt.setString(9, item.worldName)
                stmt.executeUpdate()

                val rs = stmt.generatedKeys
                return if (rs.next()) rs.getLong(1) else -1
            }
        } catch (e: Exception) {
            Logging.error("Failed to store confiscated item", e)
            return -1
        }
    }

    /**
     * Gets all confiscated items for a player.
     */
    suspend fun getItemsForPlayer(uuid: UUID, limit: Int = 100): List<ConfiscatedItem> {
        val conn = connection ?: return emptyList()

        try {
            val sql = """
                SELECT * FROM xinv_confiscations
                WHERE player_uuid = ?
                ORDER BY confiscated_at DESC
                LIMIT ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setInt(2, limit)
                return resultSetToItems(stmt.executeQuery())
            }
        } catch (e: Exception) {
            Logging.error("Failed to get confiscated items for player $uuid", e)
            return emptyList()
        }
    }

    /**
     * Gets a specific confiscated item by ID.
     */
    suspend fun getItemById(id: Long): ConfiscatedItem? {
        val conn = connection ?: return null

        try {
            val sql = "SELECT * FROM xinv_confiscations WHERE id = ?"

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, id)
                val results = resultSetToItems(stmt.executeQuery())
                return results.firstOrNull()
            }
        } catch (e: Exception) {
            Logging.error("Failed to get confiscated item $id", e)
            return null
        }
    }

    /**
     * Deletes a confiscated item (when player claims it).
     */
    suspend fun deleteItem(id: Long): Boolean {
        val conn = connection ?: return false

        try {
            conn.prepareStatement("DELETE FROM xinv_confiscations WHERE id = ?").use { stmt ->
                stmt.setLong(1, id)
                return stmt.executeUpdate() > 0
            }
        } catch (e: Exception) {
            Logging.error("Failed to delete confiscated item $id", e)
            return false
        }
    }

    /**
     * Deletes all confiscated items for a player.
     */
    suspend fun deleteAllForPlayer(uuid: UUID): Int {
        val conn = connection ?: return 0

        try {
            conn.prepareStatement("DELETE FROM xinv_confiscations WHERE player_uuid = ?").use { stmt ->
                stmt.setString(1, uuid.toString())
                return stmt.executeUpdate()
            }
        } catch (e: Exception) {
            Logging.error("Failed to delete confiscated items for player $uuid", e)
            return 0
        }
    }

    /**
     * Gets the count of confiscated items for a player.
     */
    suspend fun getCountForPlayer(uuid: UUID): Int {
        val conn = connection ?: return 0

        try {
            conn.prepareStatement("SELECT COUNT(*) FROM xinv_confiscations WHERE player_uuid = ?").use { stmt ->
                stmt.setString(1, uuid.toString())
                val rs = stmt.executeQuery()
                return if (rs.next()) rs.getInt(1) else 0
            }
        } catch (e: Exception) {
            Logging.error("Failed to get confiscation count for player $uuid", e)
            return 0
        }
    }

    /**
     * Cleans up confiscated items older than the specified number of days.
     */
    suspend fun cleanup(retentionDays: Int): Int {
        val conn = connection ?: return 0

        try {
            val cutoff = Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)

            conn.prepareStatement("DELETE FROM xinv_confiscations WHERE confiscated_at < ?").use { stmt ->
                stmt.setLong(1, cutoff.toEpochMilli())
                val deleted = stmt.executeUpdate()

                if (deleted > 0) {
                    Logging.info("Cleaned up $deleted confiscated items older than $retentionDays days")
                }

                return deleted
            }
        } catch (e: Exception) {
            Logging.error("Failed to cleanup confiscated items", e)
            return 0
        }
    }

    /**
     * Gets total count of confiscated items.
     */
    suspend fun getTotalCount(): Int {
        val conn = connection ?: return 0

        try {
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM xinv_confiscations")
                return if (rs.next()) rs.getInt(1) else 0
            }
        } catch (e: Exception) {
            Logging.error("Failed to get total confiscation count", e)
            return 0
        }
    }

    private fun resultSetToItems(rs: java.sql.ResultSet): List<ConfiscatedItem> {
        val items = mutableListOf<ConfiscatedItem>()

        while (rs.next()) {
            items.add(ConfiscatedItem(
                id = rs.getLong("id"),
                playerUuid = UUID.fromString(rs.getString("player_uuid")),
                itemData = rs.getString("item_data"),
                itemType = rs.getString("item_type"),
                itemName = rs.getString("item_name"),
                amount = rs.getInt("amount"),
                confiscatedAt = Instant.ofEpochMilli(rs.getLong("confiscated_at")),
                reason = rs.getString("reason"),
                groupName = rs.getString("group_name"),
                worldName = rs.getString("world_name")
            ))
        }

        return items
    }
}

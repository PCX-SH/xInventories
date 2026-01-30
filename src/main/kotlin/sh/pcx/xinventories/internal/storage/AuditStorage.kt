package sh.pcx.xinventories.internal.storage

import kotlinx.coroutines.launch
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.model.AuditAction
import sh.pcx.xinventories.internal.model.AuditEntry
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Storage backend for audit log entries.
 * Uses SQLite for persistent storage with in-memory buffering for performance.
 */
class AuditStorage(private val plugin: PluginContext) {

    private var connection: Connection? = null
    private val writeBuffer = ConcurrentLinkedQueue<AuditEntry>()
    private val dbFile: File = File(plugin.plugin.dataFolder, "data/audit.db")

    /**
     * Initializes the audit storage.
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
                    CREATE TABLE IF NOT EXISTS xinv_audit (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        timestamp INTEGER NOT NULL,
                        actor_uuid TEXT,
                        actor_name TEXT NOT NULL,
                        target_uuid TEXT NOT NULL,
                        target_name TEXT NOT NULL,
                        action TEXT NOT NULL,
                        group_name TEXT,
                        details TEXT,
                        server_id TEXT
                    )
                """.trimIndent())

                // Create indexes for common queries
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_timestamp ON xinv_audit(timestamp)")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_target ON xinv_audit(target_uuid)")
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_action ON xinv_audit(action)")
            }

            Logging.debug { "AuditStorage initialized at ${dbFile.absolutePath}" }
        } catch (e: Exception) {
            Logging.error("Failed to initialize AuditStorage", e)
            throw e
        }
    }

    /**
     * Shuts down the audit storage, flushing any buffered entries.
     */
    suspend fun shutdown() {
        try {
            flushBuffer()
            connection?.close()
            connection = null
            Logging.debug { "AuditStorage shut down" }
        } catch (e: Exception) {
            Logging.error("Error shutting down AuditStorage", e)
        }
    }

    /**
     * Records an audit entry.
     * Entries are buffered and written in batches for performance.
     */
    fun record(entry: AuditEntry) {
        writeBuffer.offer(entry)

        // Flush if buffer exceeds threshold
        if (writeBuffer.size >= 100) {
            plugin.scope.launch {
                flushBuffer()
            }
        }
    }

    /**
     * Records an audit entry immediately without buffering.
     */
    suspend fun recordImmediate(entry: AuditEntry): Long {
        val conn = connection ?: return -1

        try {
            val sql = """
                INSERT INTO xinv_audit (timestamp, actor_uuid, actor_name, target_uuid, target_name, action, group_name, details, server_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS).use { stmt ->
                stmt.setLong(1, entry.timestamp.toEpochMilli())
                stmt.setString(2, entry.actor?.toString())
                stmt.setString(3, entry.actorName)
                stmt.setString(4, entry.target.toString())
                stmt.setString(5, entry.targetName)
                stmt.setString(6, entry.action.name)
                stmt.setString(7, entry.group)
                stmt.setString(8, entry.details)
                stmt.setString(9, entry.serverId)
                stmt.executeUpdate()

                val rs = stmt.generatedKeys
                return if (rs.next()) rs.getLong(1) else -1
            }
        } catch (e: Exception) {
            Logging.error("Failed to record audit entry", e)
            return -1
        }
    }

    /**
     * Flushes the write buffer to storage.
     */
    suspend fun flushBuffer(): Int {
        if (writeBuffer.isEmpty()) return 0

        val conn = connection ?: return 0
        val entries = mutableListOf<AuditEntry>()

        // Drain buffer
        while (true) {
            val entry = writeBuffer.poll() ?: break
            entries.add(entry)
        }

        if (entries.isEmpty()) return 0

        try {
            val sql = """
                INSERT INTO xinv_audit (timestamp, actor_uuid, actor_name, target_uuid, target_name, action, group_name, details, server_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                for (entry in entries) {
                    stmt.setLong(1, entry.timestamp.toEpochMilli())
                    stmt.setString(2, entry.actor?.toString())
                    stmt.setString(3, entry.actorName)
                    stmt.setString(4, entry.target.toString())
                    stmt.setString(5, entry.targetName)
                    stmt.setString(6, entry.action.name)
                    stmt.setString(7, entry.group)
                    stmt.setString(8, entry.details)
                    stmt.setString(9, entry.serverId)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }

            Logging.debug { "Flushed ${entries.size} audit entries to storage" }
            return entries.size
        } catch (e: Exception) {
            Logging.error("Failed to flush audit buffer", e)
            // Re-queue failed entries
            entries.forEach { writeBuffer.offer(it) }
            return 0
        }
    }

    /**
     * Gets audit entries for a player.
     */
    suspend fun getEntriesForPlayer(uuid: UUID, limit: Int = 50): List<AuditEntry> {
        val conn = connection ?: return emptyList()

        try {
            val sql = """
                SELECT * FROM xinv_audit
                WHERE target_uuid = ?
                ORDER BY timestamp DESC
                LIMIT ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, uuid.toString())
                stmt.setInt(2, limit)
                return resultSetToEntries(stmt.executeQuery())
            }
        } catch (e: Exception) {
            Logging.error("Failed to get audit entries for player $uuid", e)
            return emptyList()
        }
    }

    /**
     * Searches audit entries by action.
     */
    suspend fun searchByAction(
        action: AuditAction,
        from: Instant? = null,
        to: Instant? = null,
        limit: Int = 100
    ): List<AuditEntry> {
        val conn = connection ?: return emptyList()

        try {
            val conditions = mutableListOf("action = ?")
            if (from != null) conditions.add("timestamp >= ?")
            if (to != null) conditions.add("timestamp <= ?")

            val sql = """
                SELECT * FROM xinv_audit
                WHERE ${conditions.joinToString(" AND ")}
                ORDER BY timestamp DESC
                LIMIT ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                var paramIndex = 1
                stmt.setString(paramIndex++, action.name)
                if (from != null) stmt.setLong(paramIndex++, from.toEpochMilli())
                if (to != null) stmt.setLong(paramIndex++, to.toEpochMilli())
                stmt.setInt(paramIndex, limit)

                return resultSetToEntries(stmt.executeQuery())
            }
        } catch (e: Exception) {
            Logging.error("Failed to search audit entries by action $action", e)
            return emptyList()
        }
    }

    /**
     * Gets all audit entries within a date range.
     */
    suspend fun getEntriesInRange(from: Instant, to: Instant, limit: Int = 1000): List<AuditEntry> {
        val conn = connection ?: return emptyList()

        try {
            val sql = """
                SELECT * FROM xinv_audit
                WHERE timestamp >= ? AND timestamp <= ?
                ORDER BY timestamp DESC
                LIMIT ?
            """.trimIndent()

            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, from.toEpochMilli())
                stmt.setLong(2, to.toEpochMilli())
                stmt.setInt(3, limit)
                return resultSetToEntries(stmt.executeQuery())
            }
        } catch (e: Exception) {
            Logging.error("Failed to get audit entries in range", e)
            return emptyList()
        }
    }

    /**
     * Deletes audit entries older than the specified number of days.
     */
    suspend fun cleanup(retentionDays: Int): Int {
        val conn = connection ?: return 0

        try {
            val cutoff = Instant.now().minus(retentionDays.toLong(), ChronoUnit.DAYS)

            conn.prepareStatement("DELETE FROM xinv_audit WHERE timestamp < ?").use { stmt ->
                stmt.setLong(1, cutoff.toEpochMilli())
                val deleted = stmt.executeUpdate()

                if (deleted > 0) {
                    Logging.info("Cleaned up $deleted audit entries older than $retentionDays days")
                }

                return deleted
            }
        } catch (e: Exception) {
            Logging.error("Failed to cleanup audit entries", e)
            return 0
        }
    }

    /**
     * Gets the total number of audit entries.
     */
    suspend fun getEntryCount(): Int {
        val conn = connection ?: return 0

        try {
            conn.createStatement().use { stmt ->
                val rs = stmt.executeQuery("SELECT COUNT(*) FROM xinv_audit")
                return if (rs.next()) rs.getInt(1) else 0
            }
        } catch (e: Exception) {
            Logging.error("Failed to get audit entry count", e)
            return 0
        }
    }

    /**
     * Gets the database file size in bytes.
     */
    fun getStorageSize(): Long {
        return if (dbFile.exists()) dbFile.length() else 0
    }

    /**
     * Exports audit entries to a CSV string.
     */
    suspend fun exportToCsv(entries: List<AuditEntry>): String {
        val sb = StringBuilder()
        sb.appendLine("id,timestamp,actor_uuid,actor_name,target_uuid,target_name,action,group,details,server_id")

        for (entry in entries) {
            sb.appendLine("${entry.id},${entry.timestamp},${entry.actor ?: ""},${escapeCsv(entry.actorName)},${entry.target},${escapeCsv(entry.targetName)},${entry.action.name},${entry.group ?: ""},${escapeCsv(entry.details ?: "")},${entry.serverId ?: ""}")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun resultSetToEntries(rs: java.sql.ResultSet): List<AuditEntry> {
        val entries = mutableListOf<AuditEntry>()

        while (rs.next()) {
            val actorUuidStr = rs.getString("actor_uuid")
            val action = AuditAction.fromName(rs.getString("action")) ?: continue

            entries.add(AuditEntry(
                id = rs.getLong("id"),
                timestamp = Instant.ofEpochMilli(rs.getLong("timestamp")),
                actor = if (actorUuidStr != null) UUID.fromString(actorUuidStr) else null,
                actorName = rs.getString("actor_name"),
                target = UUID.fromString(rs.getString("target_uuid")),
                targetName = rs.getString("target_name"),
                action = action,
                group = rs.getString("group_name"),
                details = rs.getString("details"),
                serverId = rs.getString("server_id")
            ))
        }

        return entries
    }
}

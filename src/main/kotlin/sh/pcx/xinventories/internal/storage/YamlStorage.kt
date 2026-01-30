package sh.pcx.xinventories.internal.storage

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.TemporaryGroupAssignment
import sh.pcx.xinventories.internal.model.VersionTrigger
import sh.pcx.xinventories.internal.util.Logging
import sh.pcx.xinventories.internal.util.PlayerDataSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * YAML file-based storage implementation.
 * Stores player data in individual files per player.
 *
 * File structure:
 * plugins/xInventories/data/
 *   players/
 *     <uuid>/
 *       <group>_<gamemode>.yml
 */
class YamlStorage(plugin: XInventories) : AbstractStorage(plugin) {

    override val name = "YAML"

    private lateinit var dataDir: File
    private lateinit var playersDir: File
    private val fileMutex = Mutex()

    private lateinit var versionsDir: File
    private lateinit var deathsDir: File
    private lateinit var tempGroupsDir: File

    override suspend fun doInitialize() {
        dataDir = File(plugin.dataFolder, "data")
        playersDir = File(dataDir, "players")
        versionsDir = File(dataDir, "versions")
        deathsDir = File(dataDir, "deaths")
        tempGroupsDir = File(dataDir, "temp_groups")

        withContext(Dispatchers.IO) {
            if (!playersDir.exists()) {
                playersDir.mkdirs()
            }
            if (!versionsDir.exists()) {
                versionsDir.mkdirs()
            }
            if (!deathsDir.exists()) {
                deathsDir.mkdirs()
            }
            if (!tempGroupsDir.exists()) {
                tempGroupsDir.mkdirs()
            }
        }
    }

    override suspend fun doShutdown() {
        // Nothing to clean up for YAML storage
    }

    override suspend fun doSavePlayerData(data: PlayerData) {
        val file = getPlayerFile(data.uuid, data.group, data.gameMode)

        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                val yaml = YamlConfiguration()
                PlayerDataSerializer.toYaml(data, yaml)
                file.parentFile?.mkdirs()
                yaml.save(file)
            }
        }
    }

    override suspend fun doLoadPlayerData(uuid: UUID, group: String, gameMode: GameMode?): PlayerData? {
        val file = getPlayerFile(uuid, group, gameMode)

        return withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext null

            fileMutex.withLock {
                val yaml = YamlConfiguration.loadConfiguration(file)
                PlayerDataSerializer.fromYaml(yaml)
            }
        }
    }

    override suspend fun doLoadAllPlayerData(uuid: UUID): Map<String, PlayerData> {
        val playerDir = getPlayerDirectory(uuid)

        return withContext(Dispatchers.IO) {
            if (!playerDir.exists()) return@withContext emptyMap()

            val result = mutableMapOf<String, PlayerData>()

            fileMutex.withLock {
                playerDir.listFiles { file -> file.extension == "yml" }?.forEach { file ->
                    try {
                        val yaml = YamlConfiguration.loadConfiguration(file)
                        val data = PlayerDataSerializer.fromYaml(yaml)
                        if (data != null) {
                            val key = "${data.group}_${data.gameMode.name}"
                            result[key] = data
                        }
                    } catch (e: Exception) {
                        Logging.error("Failed to load player data from ${file.name}", e)
                    }
                }
            }

            result
        }
    }

    override suspend fun doDeletePlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                if (gameMode != null) {
                    // Delete specific file
                    val file = getPlayerFile(uuid, group, gameMode)
                    if (file.exists()) {
                        file.delete()
                    } else {
                        false
                    }
                } else {
                    // Delete all files for this group
                    val playerDir = getPlayerDirectory(uuid)
                    if (!playerDir.exists()) return@withLock false

                    var deleted = false
                    playerDir.listFiles { file ->
                        file.name.startsWith("${group}_") && file.extension == "yml"
                    }?.forEach { file ->
                        if (file.delete()) deleted = true
                    }
                    deleted
                }
            }
        }
    }

    override suspend fun doDeleteAllPlayerData(uuid: UUID): Int {
        val playerDir = getPlayerDirectory(uuid)

        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                if (!playerDir.exists()) return@withLock 0

                var count = 0
                playerDir.listFiles()?.forEach { file ->
                    if (file.delete()) count++
                }

                // Remove empty directory
                if (playerDir.listFiles()?.isEmpty() == true) {
                    playerDir.delete()
                }

                count
            }
        }
    }

    override suspend fun doHasPlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        return withContext(Dispatchers.IO) {
            if (gameMode != null) {
                getPlayerFile(uuid, group, gameMode).exists()
            } else {
                val playerDir = getPlayerDirectory(uuid)
                if (!playerDir.exists()) return@withContext false

                playerDir.listFiles { file ->
                    file.name.startsWith("${group}_") && file.extension == "yml"
                }?.isNotEmpty() == true
            }
        }
    }

    override suspend fun doGetAllPlayerUUIDs(): Set<UUID> {
        return withContext(Dispatchers.IO) {
            if (!playersDir.exists()) return@withContext emptySet()

            playersDir.listFiles { file -> file.isDirectory }
                ?.mapNotNull { dir ->
                    try {
                        UUID.fromString(dir.name)
                    } catch (e: Exception) {
                        null
                    }
                }
                ?.toSet() ?: emptySet()
        }
    }

    override suspend fun doGetPlayerGroups(uuid: UUID): Set<String> {
        val playerDir = getPlayerDirectory(uuid)

        return withContext(Dispatchers.IO) {
            if (!playerDir.exists()) return@withContext emptySet()

            playerDir.listFiles { file -> file.extension == "yml" }
                ?.mapNotNull { file ->
                    // Extract group name from filename (group_GAMEMODE.yml)
                    val name = file.nameWithoutExtension
                    val lastUnderscore = name.lastIndexOf('_')
                    if (lastUnderscore > 0) {
                        name.substring(0, lastUnderscore)
                    } else {
                        null
                    }
                }
                ?.toSet() ?: emptySet()
        }
    }

    override suspend fun doGetEntryCount(): Int {
        return withContext(Dispatchers.IO) {
            if (!playersDir.exists()) return@withContext 0

            var count = 0
            playersDir.listFiles { file -> file.isDirectory }?.forEach { playerDir ->
                count += playerDir.listFiles { file -> file.extension == "yml" }?.size ?: 0
            }
            count
        }
    }

    override suspend fun doGetStorageSize(): Long {
        return withContext(Dispatchers.IO) {
            if (!dataDir.exists()) return@withContext 0L

            calculateDirectorySize(dataDir)
        }
    }

    override suspend fun doIsHealthy(): Boolean {
        return withContext(Dispatchers.IO) {
            playersDir.exists() && playersDir.canWrite()
        }
    }

    override suspend fun doSavePlayerDataBatch(dataList: List<PlayerData>): Int {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                var count = 0
                for (data in dataList) {
                    try {
                        val file = getPlayerFile(data.uuid, data.group, data.gameMode)
                        val yaml = YamlConfiguration()
                        PlayerDataSerializer.toYaml(data, yaml)
                        file.parentFile?.mkdirs()
                        yaml.save(file)
                        count++
                    } catch (e: Exception) {
                        Logging.error("Failed to save data in batch for ${data.uuid}", e)
                    }
                }
                count
            }
        }
    }

    private fun getPlayerDirectory(uuid: UUID): File {
        return File(playersDir, uuid.toString())
    }

    private fun getPlayerFile(uuid: UUID, group: String, gameMode: GameMode?): File {
        val playerDir = getPlayerDirectory(uuid)
        val filename = "${sanitizeFilename(group)}_${gameMode?.name ?: "DEFAULT"}.yml"
        return File(playerDir, filename)
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
    }

    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    // ═══════════════════════════════════════════════════════════════════
    // Inventory Version Storage
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun doSaveVersion(version: InventoryVersion) {
        val playerVersionsDir = File(versionsDir, version.playerUuid.toString())
        val file = File(playerVersionsDir, "${version.id}.yml")

        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                playerVersionsDir.mkdirs()
                val yaml = YamlConfiguration()

                yaml.set("id", version.id)
                yaml.set("playerUuid", version.playerUuid.toString())
                yaml.set("group", version.group)
                yaml.set("gameMode", version.gameMode?.name)
                yaml.set("timestamp", version.timestamp.toEpochMilli())
                yaml.set("trigger", version.trigger.name)
                yaml.set("metadata", version.metadata)

                // Save player data inline
                PlayerDataSerializer.toYaml(version.data, yaml.createSection("data"))

                yaml.save(file)
            }
        }
    }

    override suspend fun doLoadVersions(playerUuid: UUID, group: String?, limit: Int): List<InventoryVersion> {
        val playerVersionsDir = File(versionsDir, playerUuid.toString())

        return withContext(Dispatchers.IO) {
            if (!playerVersionsDir.exists()) return@withContext emptyList()

            fileMutex.withLock {
                val versions = mutableListOf<InventoryVersion>()

                playerVersionsDir.listFiles { file -> file.extension == "yml" }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { file ->
                        if (versions.size >= limit) return@forEach

                        try {
                            val yaml = YamlConfiguration.loadConfiguration(file)
                            val version = parseVersionFromYaml(yaml)

                            if (version != null) {
                                // Filter by group if specified
                                if (group == null || version.group == group) {
                                    versions.add(version)
                                }
                            }
                        } catch (e: Exception) {
                            Logging.error("Failed to load version from ${file.name}", e)
                        }
                    }

                versions.sortedByDescending { it.timestamp }
            }
        }
    }

    override suspend fun doLoadVersion(versionId: String): InventoryVersion? {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                // Search through all player version directories
                versionsDir.listFiles { file -> file.isDirectory }?.forEach { playerDir ->
                    val versionFile = File(playerDir, "$versionId.yml")
                    if (versionFile.exists()) {
                        try {
                            val yaml = YamlConfiguration.loadConfiguration(versionFile)
                            return@withLock parseVersionFromYaml(yaml)
                        } catch (e: Exception) {
                            Logging.error("Failed to load version $versionId", e)
                        }
                    }
                }
                null
            }
        }
    }

    override suspend fun doDeleteVersion(versionId: String): Boolean {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                versionsDir.listFiles { file -> file.isDirectory }?.forEach { playerDir ->
                    val versionFile = File(playerDir, "$versionId.yml")
                    if (versionFile.exists()) {
                        return@withLock versionFile.delete()
                    }
                }
                false
            }
        }
    }

    override suspend fun doPruneVersions(olderThan: Instant): Int {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                var deleted = 0
                val cutoffMillis = olderThan.toEpochMilli()

                versionsDir.listFiles { file -> file.isDirectory }?.forEach { playerDir ->
                    playerDir.listFiles { file -> file.extension == "yml" }?.forEach { file ->
                        try {
                            val yaml = YamlConfiguration.loadConfiguration(file)
                            val timestamp = yaml.getLong("timestamp")
                            if (timestamp < cutoffMillis) {
                                if (file.delete()) deleted++
                            }
                        } catch (e: Exception) {
                            Logging.error("Failed to check version timestamp in ${file.name}", e)
                        }
                    }

                    // Clean up empty player directories
                    if (playerDir.listFiles()?.isEmpty() == true) {
                        playerDir.delete()
                    }
                }

                deleted
            }
        }
    }

    private fun parseVersionFromYaml(yaml: YamlConfiguration): InventoryVersion? {
        val id = yaml.getString("id") ?: return null
        val playerUuid = yaml.getString("playerUuid")?.let { UUID.fromString(it) } ?: return null
        val group = yaml.getString("group") ?: return null
        val gameMode = yaml.getString("gameMode")?.let {
            try { GameMode.valueOf(it) } catch (e: IllegalArgumentException) { null }
        }
        val timestamp = Instant.ofEpochMilli(yaml.getLong("timestamp"))
        val trigger = yaml.getString("trigger")?.let {
            try { VersionTrigger.valueOf(it) } catch (e: IllegalArgumentException) { VersionTrigger.MANUAL }
        } ?: VersionTrigger.MANUAL

        @Suppress("UNCHECKED_CAST")
        val metadata = yaml.getConfigurationSection("metadata")?.getValues(false)
            ?.mapValues { it.value.toString() } ?: emptyMap()

        val dataSection = yaml.getConfigurationSection("data")
        val data = if (dataSection != null) {
            PlayerDataSerializer.fromYamlSection(dataSection, playerUuid, group, gameMode ?: GameMode.SURVIVAL)
        } else {
            null
        } ?: return null

        return InventoryVersion.fromStorage(id, playerUuid, group, gameMode, timestamp, trigger, data, metadata)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Death Record Storage
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun doSaveDeathRecord(record: DeathRecord) {
        val playerDeathsDir = File(deathsDir, record.playerUuid.toString())
        val file = File(playerDeathsDir, "${record.id}.yml")

        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                playerDeathsDir.mkdirs()
                val yaml = YamlConfiguration()

                yaml.set("id", record.id)
                yaml.set("playerUuid", record.playerUuid.toString())
                yaml.set("timestamp", record.timestamp.toEpochMilli())
                yaml.set("world", record.world)
                yaml.set("x", record.x)
                yaml.set("y", record.y)
                yaml.set("z", record.z)
                yaml.set("deathCause", record.deathCause)
                yaml.set("killerName", record.killerName)
                yaml.set("killerUuid", record.killerUuid?.toString())
                yaml.set("group", record.group)
                yaml.set("gameMode", record.gameMode.name)

                // Save inventory data inline
                PlayerDataSerializer.toYaml(record.inventoryData, yaml.createSection("inventoryData"))

                yaml.save(file)
            }
        }
    }

    override suspend fun doLoadDeathRecords(playerUuid: UUID, limit: Int): List<DeathRecord> {
        val playerDeathsDir = File(deathsDir, playerUuid.toString())

        return withContext(Dispatchers.IO) {
            if (!playerDeathsDir.exists()) return@withContext emptyList()

            fileMutex.withLock {
                val records = mutableListOf<DeathRecord>()

                playerDeathsDir.listFiles { file -> file.extension == "yml" }
                    ?.sortedByDescending { it.lastModified() }
                    ?.forEach { file ->
                        if (records.size >= limit) return@forEach

                        try {
                            val yaml = YamlConfiguration.loadConfiguration(file)
                            val record = parseDeathRecordFromYaml(yaml)
                            if (record != null) {
                                records.add(record)
                            }
                        } catch (e: Exception) {
                            Logging.error("Failed to load death record from ${file.name}", e)
                        }
                    }

                records.sortedByDescending { it.timestamp }
            }
        }
    }

    override suspend fun doLoadDeathRecord(deathId: String): DeathRecord? {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                deathsDir.listFiles { file -> file.isDirectory }?.forEach { playerDir ->
                    val deathFile = File(playerDir, "$deathId.yml")
                    if (deathFile.exists()) {
                        try {
                            val yaml = YamlConfiguration.loadConfiguration(deathFile)
                            return@withLock parseDeathRecordFromYaml(yaml)
                        } catch (e: Exception) {
                            Logging.error("Failed to load death record $deathId", e)
                        }
                    }
                }
                null
            }
        }
    }

    override suspend fun doDeleteDeathRecord(deathId: String): Boolean {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                deathsDir.listFiles { file -> file.isDirectory }?.forEach { playerDir ->
                    val deathFile = File(playerDir, "$deathId.yml")
                    if (deathFile.exists()) {
                        return@withLock deathFile.delete()
                    }
                }
                false
            }
        }
    }

    override suspend fun doPruneDeathRecords(olderThan: Instant): Int {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                var deleted = 0
                val cutoffMillis = olderThan.toEpochMilli()

                deathsDir.listFiles { file -> file.isDirectory }?.forEach { playerDir ->
                    playerDir.listFiles { file -> file.extension == "yml" }?.forEach { file ->
                        try {
                            val yaml = YamlConfiguration.loadConfiguration(file)
                            val timestamp = yaml.getLong("timestamp")
                            if (timestamp < cutoffMillis) {
                                if (file.delete()) deleted++
                            }
                        } catch (e: Exception) {
                            Logging.error("Failed to check death record timestamp in ${file.name}", e)
                        }
                    }

                    // Clean up empty player directories
                    if (playerDir.listFiles()?.isEmpty() == true) {
                        playerDir.delete()
                    }
                }

                deleted
            }
        }
    }

    private fun parseDeathRecordFromYaml(yaml: YamlConfiguration): DeathRecord? {
        val id = yaml.getString("id") ?: return null
        val playerUuid = yaml.getString("playerUuid")?.let { UUID.fromString(it) } ?: return null
        val timestamp = Instant.ofEpochMilli(yaml.getLong("timestamp"))
        val world = yaml.getString("world") ?: "unknown"
        val x = yaml.getDouble("x")
        val y = yaml.getDouble("y")
        val z = yaml.getDouble("z")
        val deathCause = yaml.getString("deathCause")
        val killerName = yaml.getString("killerName")
        val killerUuid = yaml.getString("killerUuid")?.let { UUID.fromString(it) }
        val group = yaml.getString("group") ?: "default"
        val gameMode = yaml.getString("gameMode")?.let { GameMode.valueOf(it) } ?: GameMode.SURVIVAL

        val dataSection = yaml.getConfigurationSection("inventoryData")
        val inventoryData = if (dataSection != null) {
            PlayerDataSerializer.fromYamlSection(dataSection, playerUuid, group, gameMode)
        } else {
            null
        } ?: return null

        return DeathRecord.fromStorage(
            id, playerUuid, timestamp, world, x, y, z,
            deathCause, killerName, killerUuid, group, gameMode, inventoryData
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    // Temporary Group Assignment Storage
    // ═══════════════════════════════════════════════════════════════════

    override suspend fun doSaveTempGroupAssignment(assignment: TemporaryGroupAssignment) {
        val file = File(tempGroupsDir, "${assignment.playerUuid}.yml")

        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                val yaml = YamlConfiguration()
                yaml.set("playerUuid", assignment.playerUuid.toString())
                yaml.set("temporaryGroup", assignment.temporaryGroup)
                yaml.set("originalGroup", assignment.originalGroup)
                yaml.set("expiresAt", assignment.expiresAt.toEpochMilli())
                yaml.set("assignedBy", assignment.assignedBy)
                yaml.set("assignedAt", assignment.assignedAt.toEpochMilli())
                yaml.set("reason", assignment.reason)
                yaml.save(file)
            }
        }
    }

    override suspend fun doLoadTempGroupAssignment(playerUuid: UUID): TemporaryGroupAssignment? {
        val file = File(tempGroupsDir, "$playerUuid.yml")
        if (!file.exists()) return null

        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                try {
                    val yaml = YamlConfiguration.loadConfiguration(file)
                    parseTempGroupAssignmentFromYaml(yaml)
                } catch (e: Exception) {
                    Logging.error("Failed to load temp group assignment for $playerUuid", e)
                    null
                }
            }
        }
    }

    override suspend fun doLoadAllTempGroupAssignments(): List<TemporaryGroupAssignment> {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                val assignments = mutableListOf<TemporaryGroupAssignment>()

                tempGroupsDir.listFiles { file -> file.extension == "yml" }?.forEach { file ->
                    try {
                        val yaml = YamlConfiguration.loadConfiguration(file)
                        parseTempGroupAssignmentFromYaml(yaml)?.let { assignments.add(it) }
                    } catch (e: Exception) {
                        Logging.error("Failed to parse temp group assignment from ${file.name}", e)
                    }
                }

                assignments
            }
        }
    }

    override suspend fun doDeleteTempGroupAssignment(playerUuid: UUID) {
        val file = File(tempGroupsDir, "$playerUuid.yml")

        withContext(Dispatchers.IO) {
            fileMutex.withLock {
                if (file.exists()) {
                    file.delete()
                }
            }
        }
    }

    override suspend fun doPruneExpiredTempGroups(): Int {
        return withContext(Dispatchers.IO) {
            fileMutex.withLock {
                var deleted = 0
                val now = Instant.now().toEpochMilli()

                tempGroupsDir.listFiles { file -> file.extension == "yml" }?.forEach { file ->
                    try {
                        val yaml = YamlConfiguration.loadConfiguration(file)
                        val expiresAt = yaml.getLong("expiresAt")
                        if (expiresAt < now) {
                            if (file.delete()) deleted++
                        }
                    } catch (e: Exception) {
                        Logging.error("Failed to check temp group expiration in ${file.name}", e)
                    }
                }

                deleted
            }
        }
    }

    private fun parseTempGroupAssignmentFromYaml(yaml: YamlConfiguration): TemporaryGroupAssignment? {
        return try {
            TemporaryGroupAssignment(
                playerUuid = UUID.fromString(yaml.getString("playerUuid")),
                temporaryGroup = yaml.getString("temporaryGroup") ?: return null,
                originalGroup = yaml.getString("originalGroup") ?: return null,
                expiresAt = Instant.ofEpochMilli(yaml.getLong("expiresAt")),
                assignedBy = yaml.getString("assignedBy") ?: "UNKNOWN",
                assignedAt = Instant.ofEpochMilli(yaml.getLong("assignedAt")),
                reason = yaml.getString("reason")
            )
        } catch (e: Exception) {
            null
        }
    }
}

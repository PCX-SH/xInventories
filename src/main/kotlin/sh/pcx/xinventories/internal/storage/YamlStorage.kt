package sh.pcx.xinventories.internal.storage

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import sh.pcx.xinventories.internal.util.PlayerDataSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
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

    override suspend fun doInitialize() {
        dataDir = File(plugin.dataFolder, "data")
        playersDir = File(dataDir, "players")

        withContext(Dispatchers.IO) {
            if (!playersDir.exists()) {
                playersDir.mkdirs()
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
}

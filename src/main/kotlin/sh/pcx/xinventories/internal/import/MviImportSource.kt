package sh.pcx.xinventories.internal.import

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.*
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import sh.pcx.xinventories.internal.compat.PotionEffectCompat
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Import source for Multiverse-Inventories (MVI).
 * Supports both API access when the plugin is loaded and file-based import.
 *
 * Repository: https://github.com/Multiverse/Multiverse-Inventories
 */
class MviImportSource(private val plugin: XInventories) : ImportSource {

    override val name: String = "Multiverse-Inventories"
    override val id: String = "mvi"

    private val dataFolder: File = File(plugin.dataFolder.parentFile, "Multiverse-Inventories")
    private val groupsFile: File = File(dataFolder, "groups.yml")
    private val worldsFolder: File = File(dataFolder, "worlds")
    private val groupDataFolder: File = File(dataFolder, "groups")

    override val isAvailable: Boolean
        get() = dataFolder.exists() && dataFolder.isDirectory

    override val hasApiAccess: Boolean
        get() {
            return try {
                val mviPlugin = Bukkit.getPluginManager().getPlugin("Multiverse-Inventories")
                if (mviPlugin == null || !mviPlugin.isEnabled) return false

                // Check if API is available through ServiceManager
                val registration = Bukkit.getServicesManager()
                    .getRegistration(Class.forName("com.onarandombox.multiverseinventories.MultiverseInventoriesApi"))
                registration != null
            } catch (e: Exception) {
                false
            }
        }

    override fun getGroups(): List<ImportGroup> {
        if (hasApiAccess) {
            return getGroupsFromApi()
        }
        return getGroupsFromFiles()
    }

    private fun getGroupsFromApi(): List<ImportGroup> {
        return try {
            val apiClass = Class.forName("com.onarandombox.multiverseinventories.MultiverseInventoriesApi")
            val registration = Bukkit.getServicesManager().getRegistration(apiClass) ?: return getGroupsFromFiles()
            val api = registration.provider

            val groups = mutableListOf<ImportGroup>()

            // Get group manager
            val groupManagerMethod = api.javaClass.getMethod("getGroupManager")
            val groupManager = groupManagerMethod.invoke(api)

            // Get all groups
            val getGroupsMethod = groupManager.javaClass.getMethod("getGroups")
            @Suppress("UNCHECKED_CAST")
            val mviGroups = getGroupsMethod.invoke(groupManager) as? Collection<Any> ?: return getGroupsFromFiles()

            mviGroups.forEach { groupObj ->
                try {
                    val getNameMethod = groupObj.javaClass.getMethod("getName")
                    val name = getNameMethod.invoke(groupObj) as? String ?: return@forEach

                    val getWorldsMethod = groupObj.javaClass.getMethod("getWorlds")
                    @Suppress("UNCHECKED_CAST")
                    val worlds = getWorldsMethod.invoke(groupObj) as? Set<String> ?: emptySet()

                    groups.add(ImportGroup(
                        name = name,
                        worlds = worlds,
                        isDefault = false
                    ))
                } catch (e: Exception) {
                    Logging.warning("Failed to parse MVI group: ${e.message}")
                }
            }

            groups
        } catch (e: Exception) {
            Logging.warning("Failed to get groups from MVI API, falling back to files: ${e.message}")
            getGroupsFromFiles()
        }
    }

    private fun getGroupsFromFiles(): List<ImportGroup> {
        if (!groupsFile.exists()) {
            Logging.warning("MVI groups.yml not found at ${groupsFile.absolutePath}")
            return emptyList()
        }

        val groups = mutableListOf<ImportGroup>()

        try {
            val yaml = YamlConfiguration.loadConfiguration(groupsFile)

            yaml.getKeys(false).forEach { groupName ->
                val groupSection = yaml.getConfigurationSection(groupName) ?: return@forEach

                val worlds = groupSection.getStringList("worlds").toSet()

                groups.add(ImportGroup(
                    name = groupName,
                    worlds = worlds,
                    isDefault = false
                ))
            }
        } catch (e: Exception) {
            Logging.error("Failed to parse MVI groups.yml", e)
        }

        return groups
    }

    override fun getPlayers(): List<UUID> {
        val players = mutableSetOf<UUID>()

        // Check group data folder
        if (groupDataFolder.exists()) {
            groupDataFolder.listFiles()?.filter { it.isDirectory }?.forEach { groupDir ->
                groupDir.listFiles()?.filter { it.extension == "yml" || it.extension == "json" }?.forEach { file ->
                    try {
                        val uuidString = file.nameWithoutExtension
                        val uuid = UUID.fromString(uuidString)
                        players.add(uuid)
                    } catch (e: Exception) {
                        // Not a valid UUID file
                    }
                }
            }
        }

        // Check worlds folder for per-world data
        if (worldsFolder.exists()) {
            worldsFolder.listFiles()?.filter { it.isDirectory }?.forEach { worldDir ->
                worldDir.listFiles()?.filter { it.extension == "yml" || it.extension == "json" }?.forEach { file ->
                    try {
                        val uuidString = file.nameWithoutExtension
                        val uuid = UUID.fromString(uuidString)
                        players.add(uuid)
                    } catch (e: Exception) {
                        // Not a valid UUID file
                    }
                }
            }
        }

        return players.toList()
    }

    override fun getPlayerData(uuid: UUID, group: String, gameMode: GameMode?): ImportedPlayerData? {
        if (hasApiAccess) {
            return getPlayerDataFromApi(uuid, group, gameMode)
        }
        return getPlayerDataFromFiles(uuid, group, gameMode)
    }

    private fun getPlayerDataFromApi(uuid: UUID, group: String, gameMode: GameMode?): ImportedPlayerData? {
        return try {
            val apiClass = Class.forName("com.onarandombox.multiverseinventories.MultiverseInventoriesApi")
            val registration = Bukkit.getServicesManager().getRegistration(apiClass) ?: return null
            val api = registration.provider

            val player = Bukkit.getOfflinePlayer(uuid)

            // Get group manager and find the group
            val groupManagerMethod = api.javaClass.getMethod("getGroupManager")
            val groupManager = groupManagerMethod.invoke(api)

            val getGroupMethod = groupManager.javaClass.getMethod("getGroup", String::class.java)
            val groupObj = getGroupMethod.invoke(groupManager, group) ?: return null

            // Get player profile
            val getProfileMethod = groupObj.javaClass.methods.find {
                it.name == "getProfile" && it.parameterCount == 1
            } ?: return null

            val profile = getProfileMethod.invoke(groupObj, player) ?: return null

            parseProfileObject(uuid, player.name ?: uuid.toString(), group, profile, gameMode ?: GameMode.SURVIVAL)
        } catch (e: Exception) {
            Logging.debug { "Failed to get player data from MVI API: ${e.message}" }
            null
        }
    }

    private fun parseProfileObject(
        uuid: UUID,
        playerName: String,
        group: String,
        profile: Any,
        gameMode: GameMode
    ): ImportedPlayerData? {
        return try {
            val profileClass = profile.javaClass

            // MVI uses a "Sharable" system - need to get each sharable value
            val mainInventory = mutableMapOf<Int, ItemStack>()
            val armorInventory = mutableMapOf<Int, ItemStack>()
            var offhand: ItemStack? = null
            val enderChest = mutableMapOf<Int, ItemStack>()
            var health = 20.0
            var foodLevel = 20
            var exp = 0.0f
            var level = 0
            var saturation = 5.0f

            // Try to get inventory contents
            try {
                val getMethod = profileClass.getMethod("get", Class.forName("com.onarandombox.multiverseinventories.share.Sharable"))
                val sharableClass = Class.forName("com.onarandombox.multiverseinventories.share.Sharables")

                // Get inventory
                val inventorySharable = sharableClass.getField("INVENTORY").get(null)
                @Suppress("UNCHECKED_CAST")
                val inventory = getMethod.invoke(profile, inventorySharable) as? Array<ItemStack?>
                inventory?.forEachIndexed { index, item ->
                    if (item != null && item.type != Material.AIR) {
                        mainInventory[index] = item.clone()
                    }
                }

                // Get armor
                val armorSharable = sharableClass.getField("ARMOR").get(null)
                @Suppress("UNCHECKED_CAST")
                val armor = getMethod.invoke(profile, armorSharable) as? Array<ItemStack?>
                armor?.forEachIndexed { index, item ->
                    if (item != null && item.type != Material.AIR) {
                        armorInventory[index] = item.clone()
                    }
                }

                // Get ender chest
                val enderSharable = sharableClass.getField("ENDER_CHEST").get(null)
                @Suppress("UNCHECKED_CAST")
                val ender = getMethod.invoke(profile, enderSharable) as? Array<ItemStack?>
                ender?.forEachIndexed { index, item ->
                    if (item != null && item.type != Material.AIR) {
                        enderChest[index] = item.clone()
                    }
                }

                // Get stats
                val healthSharable = sharableClass.getField("HEALTH").get(null)
                health = getMethod.invoke(profile, healthSharable) as? Double ?: 20.0

                val foodSharable = sharableClass.getField("FOOD_LEVEL").get(null)
                foodLevel = getMethod.invoke(profile, foodSharable) as? Int ?: 20

                val expSharable = sharableClass.getField("EXPERIENCE").get(null)
                exp = getMethod.invoke(profile, expSharable) as? Float ?: 0.0f

                val levelSharable = sharableClass.getField("LEVEL").get(null)
                level = getMethod.invoke(profile, levelSharable) as? Int ?: 0

            } catch (e: Exception) {
                Logging.debug { "Failed to parse MVI sharables: ${e.message}" }
            }

            ImportedPlayerData(
                uuid = uuid,
                playerName = playerName,
                sourceGroup = group,
                gameMode = gameMode,
                mainInventory = mainInventory,
                armorInventory = armorInventory,
                offhand = offhand,
                enderChest = enderChest,
                health = health,
                maxHealth = 20.0,
                foodLevel = foodLevel,
                saturation = saturation,
                exhaustion = 0.0f,
                experience = exp,
                level = level,
                totalExperience = 0,
                potionEffects = emptyList(),
                balance = null,
                sourceTimestamp = null,
                sourceId = id
            )
        } catch (e: Exception) {
            Logging.debug { "Failed to parse MVI profile object: ${e.message}" }
            null
        }
    }

    private fun getPlayerDataFromFiles(uuid: UUID, group: String, gameMode: GameMode?): ImportedPlayerData? {
        // Try group data folder first
        val groupPlayerFile = File(groupDataFolder, "$group/$uuid.yml")
        if (groupPlayerFile.exists()) {
            return parsePlayerFile(groupPlayerFile, uuid, group, gameMode)
        }

        // Try JSON format
        val groupPlayerJsonFile = File(groupDataFolder, "$group/$uuid.json")
        if (groupPlayerJsonFile.exists()) {
            return parsePlayerJsonFile(groupPlayerJsonFile, uuid, group, gameMode)
        }

        return null
    }

    private fun parsePlayerFile(file: File, uuid: UUID, group: String, gameMode: GameMode?): ImportedPlayerData? {
        return try {
            val yaml = YamlConfiguration.loadConfiguration(file)
            val targetGameMode = gameMode ?: GameMode.SURVIVAL
            val gameModeKey = targetGameMode.name.lowercase()

            // Check if game mode data exists
            val section = yaml.getConfigurationSection(gameModeKey) ?: yaml

            // Parse inventory
            val mainInventory = mutableMapOf<Int, ItemStack>()
            section.getConfigurationSection("inventoryContents")?.let { invSection ->
                invSection.getKeys(false).forEach { slotStr ->
                    try {
                        val slot = slotStr.toInt()
                        val item = invSection.getItemStack(slotStr)
                        if (item != null && item.type != Material.AIR) {
                            mainInventory[slot] = item
                        }
                    } catch (e: Exception) {
                        // Invalid slot
                    }
                }
            }

            // Parse armor
            val armorInventory = mutableMapOf<Int, ItemStack>()
            section.getConfigurationSection("armorContents")?.let { armorSection ->
                armorSection.getKeys(false).forEach { slotStr ->
                    try {
                        val slot = slotStr.toInt()
                        val item = armorSection.getItemStack(slotStr)
                        if (item != null && item.type != Material.AIR) {
                            armorInventory[slot] = item
                        }
                    } catch (e: Exception) {
                        // Invalid slot
                    }
                }
            }

            // Parse ender chest
            val enderChest = mutableMapOf<Int, ItemStack>()
            section.getConfigurationSection("enderChestContents")?.let { ecSection ->
                ecSection.getKeys(false).forEach { slotStr ->
                    try {
                        val slot = slotStr.toInt()
                        val item = ecSection.getItemStack(slotStr)
                        if (item != null && item.type != Material.AIR) {
                            enderChest[slot] = item
                        }
                    } catch (e: Exception) {
                        // Invalid slot
                    }
                }
            }

            val playerName = yaml.getString("playerName") ?: uuid.toString()

            ImportedPlayerData(
                uuid = uuid,
                playerName = playerName,
                sourceGroup = group,
                gameMode = targetGameMode,
                mainInventory = mainInventory,
                armorInventory = armorInventory,
                offhand = section.getItemStack("offHandItem"),
                enderChest = enderChest,
                health = section.getDouble("health", 20.0),
                maxHealth = section.getDouble("maxHealth", 20.0),
                foodLevel = section.getInt("foodLevel", 20),
                saturation = section.getDouble("saturation", 5.0).toFloat(),
                exhaustion = section.getDouble("exhaustion", 0.0).toFloat(),
                experience = section.getDouble("exp", 0.0).toFloat(),
                level = section.getInt("level", 0),
                totalExperience = section.getInt("totalExperience", 0),
                potionEffects = parsePotionEffects(section.getConfigurationSection("potionEffects")),
                balance = if (section.contains("economy")) section.getDouble("economy") else null,
                sourceTimestamp = try {
                    val timestamp = yaml.getLong("lastModified", 0L)
                    if (timestamp > 0) Instant.ofEpochMilli(timestamp) else null
                } catch (e: Exception) {
                    null
                },
                sourceId = id
            )
        } catch (e: Exception) {
            Logging.error("Failed to parse MVI player file: ${file.absolutePath}", e)
            null
        }
    }

    private fun parsePlayerJsonFile(file: File, uuid: UUID, group: String, gameMode: GameMode?): ImportedPlayerData? {
        // JSON parsing would require additional library
        // For now, log a warning and return null
        Logging.warning("JSON format MVI files are not yet supported: ${file.absolutePath}")
        return null
    }

    private fun parsePotionEffects(section: org.bukkit.configuration.ConfigurationSection?): List<PotionEffect> {
        if (section == null) return emptyList()

        val effects = mutableListOf<PotionEffect>()
        section.getKeys(false).forEach { effectName ->
            try {
                val effectType = PotionEffectCompat.getByName(effectName)
                if (effectType != null) {
                    val effectData = section.getConfigurationSection(effectName)
                    val duration = effectData?.getInt("duration", 600) ?: 600
                    val amplifier = effectData?.getInt("amplifier", 0) ?: 0
                    effects.add(PotionEffect(effectType, duration, amplifier))
                }
            } catch (e: Exception) {
                // Invalid effect
            }
        }
        return effects
    }

    override fun getAllPlayerData(uuid: UUID): Map<String, ImportedPlayerData> {
        val result = mutableMapOf<String, ImportedPlayerData>()

        getGroups().forEach { group ->
            GameMode.entries.forEach { gameMode ->
                getPlayerData(uuid, group.name, gameMode)?.let { data ->
                    val key = "${group.name}:${gameMode.name}"
                    result[key] = data
                }
            }
        }

        return result
    }

    override fun validate(): ImportValidationResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!dataFolder.exists()) {
            issues.add("MVI data folder not found: ${dataFolder.absolutePath}")
            return ImportValidationResult(
                isValid = false,
                issues = issues
            )
        }

        if (!groupsFile.exists()) {
            warnings.add("MVI groups.yml not found - group detection may be limited")
        }

        val groups = getGroups()
        val players = getPlayers()

        if (groups.isEmpty()) {
            warnings.add("No groups detected in MVI configuration")
        }

        if (players.isEmpty()) {
            warnings.add("No player data files found")
        }

        return ImportValidationResult(
            isValid = issues.isEmpty(),
            issues = issues,
            warnings = warnings,
            playerCount = players.size,
            groupCount = groups.size
        )
    }
}

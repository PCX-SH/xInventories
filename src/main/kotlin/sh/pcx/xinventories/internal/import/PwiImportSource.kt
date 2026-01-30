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
 * Import source for PerWorldInventory-kt (PWI).
 * Supports both API access when the plugin is loaded and file-based import.
 *
 * Repository: https://github.com/EbonJaeger/perworldinventory-kt
 * Note: PWI is archived as of Dec 2021 but API is still functional.
 */
class PwiImportSource(private val plugin: XInventories) : ImportSource {

    override val name: String = "PerWorldInventory"
    override val id: String = "pwi"

    private val dataFolder: File = File(plugin.dataFolder.parentFile, "PerWorldInventory/data")
    private val configFolder: File = File(plugin.dataFolder.parentFile, "PerWorldInventory")
    private val worldsFile: File = File(configFolder, "worlds.yml")

    override val isAvailable: Boolean
        get() = dataFolder.exists() && dataFolder.isDirectory

    override val hasApiAccess: Boolean
        get() {
            return try {
                val pwiPlugin = Bukkit.getPluginManager().getPlugin("PerWorldInventory")
                pwiPlugin != null && pwiPlugin.isEnabled
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
            // Use reflection to avoid compile-time dependency
            val pwiPlugin = Bukkit.getPluginManager().getPlugin("PerWorldInventory") ?: return emptyList()
            val apiMethod = pwiPlugin.javaClass.getMethod("getPerWorldInventoryAPI")
            val api = apiMethod.invoke(pwiPlugin) ?: return emptyList()

            val groups = mutableListOf<ImportGroup>()

            // Get all worlds and their groups
            val worldManager = api.javaClass.getMethod("getGroupManager").invoke(api)
            val getGroupsMethod = worldManager.javaClass.getMethod("getGroups")
            @Suppress("UNCHECKED_CAST")
            val pwiGroups = getGroupsMethod.invoke(worldManager) as? Map<String, Any> ?: return emptyList()

            pwiGroups.forEach { (name, groupObj) ->
                try {
                    val getWorldsMethod = groupObj.javaClass.getMethod("getWorlds")
                    @Suppress("UNCHECKED_CAST")
                    val worlds = getWorldsMethod.invoke(groupObj) as? Set<String> ?: emptySet()

                    val isDefaultMethod = groupObj.javaClass.getMethod("isDefault")
                    val isDefault = isDefaultMethod.invoke(groupObj) as? Boolean ?: false

                    groups.add(ImportGroup(
                        name = name,
                        worlds = worlds,
                        isDefault = isDefault
                    ))
                } catch (e: Exception) {
                    Logging.warning("Failed to parse PWI group $name: ${e.message}")
                }
            }

            groups
        } catch (e: Exception) {
            Logging.warning("Failed to get groups from PWI API, falling back to files: ${e.message}")
            getGroupsFromFiles()
        }
    }

    private fun getGroupsFromFiles(): List<ImportGroup> {
        if (!worldsFile.exists()) {
            Logging.warning("PWI worlds.yml not found at ${worldsFile.absolutePath}")
            return emptyList()
        }

        val groups = mutableListOf<ImportGroup>()

        try {
            val yaml = YamlConfiguration.loadConfiguration(worldsFile)
            val groupsSection = yaml.getConfigurationSection("groups") ?: return emptyList()

            groupsSection.getKeys(false).forEach { groupName ->
                val groupSection = groupsSection.getConfigurationSection(groupName) ?: return@forEach

                val worlds = groupSection.getStringList("worlds").toSet()
                val isDefault = groupSection.getBoolean("default", false)

                groups.add(ImportGroup(
                    name = groupName,
                    worlds = worlds,
                    isDefault = isDefault
                ))
            }
        } catch (e: Exception) {
            Logging.error("Failed to parse PWI worlds.yml", e)
        }

        return groups
    }

    override fun getPlayers(): List<UUID> {
        if (!dataFolder.exists()) return emptyList()

        val players = mutableSetOf<UUID>()

        // PWI stores data in: data/<group>/<uuid>.yml
        dataFolder.listFiles()?.filter { it.isDirectory }?.forEach { groupDir ->
            groupDir.listFiles()?.filter { it.extension == "yml" }?.forEach { playerFile ->
                try {
                    val uuidString = playerFile.nameWithoutExtension
                    val uuid = UUID.fromString(uuidString)
                    players.add(uuid)
                } catch (e: Exception) {
                    // Not a valid UUID file
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
            // Use reflection to access PWI API
            val pwiPlugin = Bukkit.getPluginManager().getPlugin("PerWorldInventory") ?: return null
            val apiMethod = pwiPlugin.javaClass.getMethod("getPerWorldInventoryAPI")
            val api = apiMethod.invoke(pwiPlugin) ?: return null

            val player = Bukkit.getOfflinePlayer(uuid)
            val targetGameMode = gameMode ?: GameMode.SURVIVAL

            // Get profile manager
            val profileManager = api.javaClass.getMethod("getProfileManager").invoke(api)
            val getProfileMethod = profileManager.javaClass.methods.find {
                it.name == "getPlayerProfile" && it.parameterCount == 3
            } ?: return null

            // Get the group object
            val groupManager = api.javaClass.getMethod("getGroupManager").invoke(api)
            val getGroupMethod = groupManager.javaClass.getMethod("getGroup", String::class.java)
            val groupObj = getGroupMethod.invoke(groupManager, group) ?: return null

            // Get profile
            val profile = getProfileMethod.invoke(profileManager, player, groupObj, targetGameMode) ?: return null

            // Extract data from profile
            parseProfileObject(uuid, player.name ?: uuid.toString(), group, profile, targetGameMode)
        } catch (e: Exception) {
            Logging.debug { "Failed to get player data from PWI API: ${e.message}" }
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

            // Get inventory
            @Suppress("UNCHECKED_CAST")
            val inventory = profileClass.getMethod("getInventory").invoke(profile) as? Array<ItemStack?>
            val mainInventory = mutableMapOf<Int, ItemStack>()
            inventory?.forEachIndexed { index, item ->
                if (item != null && item.type != Material.AIR) {
                    mainInventory[index] = item.clone()
                }
            }

            // Get armor
            @Suppress("UNCHECKED_CAST")
            val armor = profileClass.getMethod("getArmor").invoke(profile) as? Array<ItemStack?>
            val armorInventory = mutableMapOf<Int, ItemStack>()
            armor?.forEachIndexed { index, item ->
                if (item != null && item.type != Material.AIR) {
                    armorInventory[index] = item.clone()
                }
            }

            // Get other stats
            val health = profileClass.getMethod("getHealth").invoke(profile) as? Double ?: 20.0
            val foodLevel = profileClass.getMethod("getFoodLevel").invoke(profile) as? Int ?: 20
            val exp = profileClass.getMethod("getExperience").invoke(profile) as? Float ?: 0.0f
            val level = profileClass.getMethod("getLevel").invoke(profile) as? Int ?: 0
            val balance = try {
                profileClass.getMethod("getBalance").invoke(profile) as? Double
            } catch (e: Exception) {
                null
            }

            ImportedPlayerData(
                uuid = uuid,
                playerName = playerName,
                sourceGroup = group,
                gameMode = gameMode,
                mainInventory = mainInventory,
                armorInventory = armorInventory,
                offhand = null, // PWI may not have separate offhand
                enderChest = emptyMap(),
                health = health,
                maxHealth = 20.0,
                foodLevel = foodLevel,
                saturation = 5.0f,
                exhaustion = 0.0f,
                experience = exp,
                level = level,
                totalExperience = 0,
                potionEffects = emptyList(),
                balance = balance,
                sourceTimestamp = null,
                sourceId = id
            )
        } catch (e: Exception) {
            Logging.debug { "Failed to parse PWI profile object: ${e.message}" }
            null
        }
    }

    private fun getPlayerDataFromFiles(uuid: UUID, group: String, gameMode: GameMode?): ImportedPlayerData? {
        val targetGameMode = gameMode ?: GameMode.SURVIVAL
        val gameModeSuffix = targetGameMode.name.lowercase()

        // PWI file structure: data/<group>/<uuid>.yml
        // Inside the file, data is separated by game mode
        val playerFile = File(dataFolder, "$group/$uuid.yml")

        if (!playerFile.exists()) {
            return null
        }

        return try {
            val yaml = YamlConfiguration.loadConfiguration(playerFile)
            val section = yaml.getConfigurationSection(gameModeSuffix) ?: return null

            // Parse inventory
            val mainInventory = mutableMapOf<Int, ItemStack>()
            section.getConfigurationSection("inventory")?.let { invSection ->
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
            section.getConfigurationSection("armor")?.let { armorSection ->
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
            section.getConfigurationSection("ender-chest")?.let { ecSection ->
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

            // Parse potion effects
            val potionEffects = mutableListOf<PotionEffect>()
            section.getConfigurationSection("potion-effects")?.let { effectsSection ->
                effectsSection.getKeys(false).forEach { effectName ->
                    try {
                        val effectType = PotionEffectCompat.getByName(effectName)
                        if (effectType != null) {
                            val effectData = effectsSection.getConfigurationSection(effectName)
                            val duration = effectData?.getInt("duration", 600) ?: 600
                            val amplifier = effectData?.getInt("amplifier", 0) ?: 0
                            potionEffects.add(PotionEffect(effectType, duration, amplifier))
                        }
                    } catch (e: Exception) {
                        // Invalid effect
                    }
                }
            }

            // Get player name from file or use UUID
            val playerName = yaml.getString("player-name") ?: uuid.toString()

            ImportedPlayerData(
                uuid = uuid,
                playerName = playerName,
                sourceGroup = group,
                gameMode = targetGameMode,
                mainInventory = mainInventory,
                armorInventory = armorInventory,
                offhand = section.getItemStack("offhand"),
                enderChest = enderChest,
                health = section.getDouble("health", 20.0),
                maxHealth = section.getDouble("max-health", 20.0),
                foodLevel = section.getInt("food-level", 20),
                saturation = section.getDouble("saturation", 5.0).toFloat(),
                exhaustion = section.getDouble("exhaustion", 0.0).toFloat(),
                experience = section.getDouble("exp", 0.0).toFloat(),
                level = section.getInt("level", 0),
                totalExperience = section.getInt("total-experience", 0),
                potionEffects = potionEffects,
                balance = if (section.contains("balance")) section.getDouble("balance") else null,
                sourceTimestamp = try {
                    val timestamp = yaml.getLong("last-modified", 0L)
                    if (timestamp > 0) Instant.ofEpochMilli(timestamp) else null
                } catch (e: Exception) {
                    null
                },
                sourceId = id,
                // PWI-style player state fields
                isFlying = section.getBoolean("flying", false),
                allowFlight = section.getBoolean("allow-flight", false),
                displayName = section.getString("display-name"),
                fallDistance = section.getDouble("fall-distance", 0.0).toFloat(),
                fireTicks = section.getInt("fire-ticks", 0),
                maximumAir = section.getInt("max-air", 300),
                remainingAir = section.getInt("remaining-air", 300)
            )
        } catch (e: Exception) {
            Logging.error("Failed to parse PWI player file: ${playerFile.absolutePath}", e)
            null
        }
    }

    override fun getAllPlayerData(uuid: UUID): Map<String, ImportedPlayerData> {
        val result = mutableMapOf<String, ImportedPlayerData>()

        getGroups().forEach { group ->
            // Try each game mode
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
            issues.add("PWI data folder not found: ${dataFolder.absolutePath}")
            return ImportValidationResult(
                isValid = false,
                issues = issues
            )
        }

        if (!worldsFile.exists()) {
            warnings.add("PWI worlds.yml not found - group detection may be limited")
        }

        val groups = getGroups()
        val players = getPlayers()

        if (groups.isEmpty()) {
            warnings.add("No groups detected in PWI configuration")
        }

        if (players.isEmpty()) {
            warnings.add("No player data files found")
        }

        // Check for corrupted data files
        var corruptedFiles = 0
        dataFolder.listFiles()?.filter { it.isDirectory }?.forEach { groupDir ->
            groupDir.listFiles()?.filter { it.extension == "yml" }?.forEach { playerFile ->
                try {
                    YamlConfiguration.loadConfiguration(playerFile)
                } catch (e: Exception) {
                    corruptedFiles++
                }
            }
        }

        if (corruptedFiles > 0) {
            warnings.add("$corruptedFiles corrupted player data files detected")
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

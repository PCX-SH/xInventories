package sh.pcx.xinventories.internal.import

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.*
import sh.pcx.xinventories.internal.util.Logging
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.io.File
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.time.Instant
import java.util.UUID
import java.util.zip.GZIPInputStream

/**
 * Import source for MyWorlds (MW).
 * Supports file-based import using NBT parsing.
 *
 * Repository: https://github.com/bergerhealer/MyWorlds
 * Note: MyWorlds stores player data in vanilla Minecraft NBT format.
 *
 * This implementation provides basic NBT reading capability.
 * For full NBT support, consider using BKCommonLib when available.
 */
class MyWorldsImportSource(private val plugin: XInventories) : ImportSource {

    override val name: String = "MyWorlds"
    override val id: String = "myworlds"

    private val worldsFolder: File = Bukkit.getWorldContainer()

    override val isAvailable: Boolean
        get() {
            // Check if any world has playerdata folder
            return worldsFolder.listFiles()?.any { worldDir ->
                worldDir.isDirectory && File(worldDir, "playerdata").exists()
            } ?: false
        }

    override val hasApiAccess: Boolean
        get() {
            return try {
                val myWorldsPlugin = Bukkit.getPluginManager().getPlugin("My_Worlds")
                myWorldsPlugin != null && myWorldsPlugin.isEnabled
            } catch (e: Exception) {
                false
            }
        }

    override fun getGroups(): List<ImportGroup> {
        if (hasApiAccess) {
            return getGroupsFromApi()
        }
        return getGroupsFromWorldFolders()
    }

    private fun getGroupsFromApi(): List<ImportGroup> {
        return try {
            // Use reflection to access MyWorlds API
            val worldInventoryClass = Class.forName("com.bergerkiller.bukkit.mw.WorldInventory")
            val getAllMethod = worldInventoryClass.getMethod("getAll")

            @Suppress("UNCHECKED_CAST")
            val inventories = getAllMethod.invoke(null) as? Collection<Any> ?: return getGroupsFromWorldFolders()

            val groups = mutableListOf<ImportGroup>()

            inventories.forEach { inventory ->
                try {
                    val getWorldsMethod = inventory.javaClass.getMethod("getWorlds")
                    @Suppress("UNCHECKED_CAST")
                    val worlds = getWorldsMethod.invoke(inventory) as? Set<String> ?: emptySet()

                    val getSharedWorldNameMethod = inventory.javaClass.getMethod("getSharedWorldName")
                    val sharedWorldName = getSharedWorldNameMethod.invoke(inventory) as? String ?: "default"

                    groups.add(ImportGroup(
                        name = sharedWorldName,
                        worlds = worlds,
                        isDefault = false,
                        metadata = mapOf("type" to "worldinventory")
                    ))
                } catch (e: Exception) {
                    Logging.debug { "Failed to parse MyWorlds inventory group: ${e.message}" }
                }
            }

            groups
        } catch (e: Exception) {
            Logging.debug { "Failed to get groups from MyWorlds API: ${e.message}" }
            getGroupsFromWorldFolders()
        }
    }

    private fun getGroupsFromWorldFolders(): List<ImportGroup> {
        val groups = mutableListOf<ImportGroup>()

        // Create a group for each world with player data
        worldsFolder.listFiles()?.filter { it.isDirectory }?.forEach { worldDir ->
            val playerDataDir = File(worldDir, "playerdata")
            if (playerDataDir.exists() && playerDataDir.listFiles()?.isNotEmpty() == true) {
                groups.add(ImportGroup(
                    name = worldDir.name,
                    worlds = setOf(worldDir.name),
                    isDefault = worldDir.name == "world",
                    metadata = mapOf("type" to "world")
                ))
            }
        }

        return groups
    }

    override fun getPlayers(): List<UUID> {
        val players = mutableSetOf<UUID>()

        worldsFolder.listFiles()?.filter { it.isDirectory }?.forEach { worldDir ->
            val playerDataDir = File(worldDir, "playerdata")
            if (playerDataDir.exists()) {
                playerDataDir.listFiles()?.filter { it.extension == "dat" }?.forEach { datFile ->
                    try {
                        val uuidString = datFile.nameWithoutExtension
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
        // Find the player data file in the world folder
        val worldDir = File(worldsFolder, group)
        val playerDataFile = File(worldDir, "playerdata/$uuid.dat")

        if (!playerDataFile.exists()) {
            return null
        }

        return parseNbtPlayerData(playerDataFile, uuid, group, gameMode)
    }

    /**
     * Parses NBT player data file.
     * This is a simplified NBT parser - for full support, BKCommonLib should be used.
     */
    private fun parseNbtPlayerData(file: File, uuid: UUID, group: String, gameMode: GameMode?): ImportedPlayerData? {
        return try {
            // Try to use BKCommonLib if available
            if (hasApiAccess) {
                return parseNbtWithBkCommonLib(file, uuid, group, gameMode)
            }

            // Fallback to basic NBT parsing
            parseNbtBasic(file, uuid, group, gameMode)
        } catch (e: Exception) {
            Logging.error("Failed to parse MyWorlds player data: ${file.absolutePath}", e)
            null
        }
    }

    private fun parseNbtWithBkCommonLib(file: File, uuid: UUID, group: String, gameMode: GameMode?): ImportedPlayerData? {
        return try {
            val commonTagCompoundClass = Class.forName("com.bergerkiller.bukkit.common.nbt.CommonTagCompound")
            val readFromFileMethod = commonTagCompoundClass.getMethod("readFromFile", File::class.java)

            val nbt = readFromFileMethod.invoke(null, file) ?: return null

            // Extract inventory using BKCommonLib
            val mainInventory = mutableMapOf<Int, ItemStack>()
            val armorInventory = mutableMapOf<Int, ItemStack>()
            val enderChest = mutableMapOf<Int, ItemStack>()

            // Get inventory list
            try {
                val getListMethod = nbt.javaClass.getMethod("getList", String::class.java)
                val inventoryList = getListMethod.invoke(nbt, "Inventory")

                if (inventoryList != null) {
                    val sizeMethod = inventoryList.javaClass.getMethod("size")
                    val size = sizeMethod.invoke(inventoryList) as Int

                    for (i in 0 until size) {
                        try {
                            val getMethod = inventoryList.javaClass.getMethod("get", Int::class.javaPrimitiveType)
                            val itemTag = getMethod.invoke(inventoryList, i) ?: continue

                            val getByteMethod = itemTag.javaClass.getMethod("getByte", String::class.java)
                            val slot = (getByteMethod.invoke(itemTag, "Slot") as? Byte)?.toInt() ?: continue

                            // Use BKCommonLib's ItemStack serialization
                            val itemUtilClass = Class.forName("com.bergerkiller.bukkit.common.utils.ItemUtil")
                            val deserializeMethod = itemUtilClass.getMethod("deserialize", itemTag.javaClass)
                            val item = deserializeMethod.invoke(null, itemTag) as? ItemStack ?: continue

                            if (item.type != Material.AIR) {
                                when {
                                    slot < 36 -> mainInventory[slot] = item
                                    slot < 40 -> armorInventory[slot - 36] = item // armor
                                    slot == 40 -> { /* offhand - handled separately */ }
                                }
                            }
                        } catch (e: Exception) {
                            // Skip this item
                        }
                    }
                }
            } catch (e: Exception) {
                Logging.debug { "Failed to parse inventory from NBT: ${e.message}" }
            }

            // Get player stats
            val getIntMethod = nbt.javaClass.getMethod("getInt", String::class.java)
            val getFloatMethod = nbt.javaClass.getMethod("getFloat", String::class.java)
            val getShortMethod = nbt.javaClass.getMethod("getShort", String::class.java)

            val health = try {
                (getFloatMethod.invoke(nbt, "Health") as? Float)?.toDouble() ?: 20.0
            } catch (e: Exception) { 20.0 }

            val foodLevel = try {
                getIntMethod.invoke(nbt, "foodLevel") as? Int ?: 20
            } catch (e: Exception) { 20 }

            val xpLevel = try {
                getIntMethod.invoke(nbt, "XpLevel") as? Int ?: 0
            } catch (e: Exception) { 0 }

            val xpP = try {
                (getFloatMethod.invoke(nbt, "XpP") as? Float) ?: 0.0f
            } catch (e: Exception) { 0.0f }

            val xpTotal = try {
                getIntMethod.invoke(nbt, "XpTotal") as? Int ?: 0
            } catch (e: Exception) { 0 }

            val playerGameMode = try {
                val gm = getIntMethod.invoke(nbt, "playerGameType") as? Int ?: 0
                GameMode.entries.getOrNull(gm) ?: GameMode.SURVIVAL
            } catch (e: Exception) { gameMode ?: GameMode.SURVIVAL }

            val playerName = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()

            ImportedPlayerData(
                uuid = uuid,
                playerName = playerName,
                sourceGroup = group,
                gameMode = playerGameMode,
                mainInventory = mainInventory,
                armorInventory = armorInventory,
                offhand = null,
                enderChest = enderChest,
                health = health,
                maxHealth = 20.0,
                foodLevel = foodLevel,
                saturation = 5.0f,
                exhaustion = 0.0f,
                experience = xpP,
                level = xpLevel,
                totalExperience = xpTotal,
                potionEffects = emptyList(),
                balance = null,
                sourceTimestamp = try {
                    Instant.ofEpochMilli(file.lastModified())
                } catch (e: Exception) { null },
                sourceId = id
            )
        } catch (e: Exception) {
            Logging.debug { "Failed to parse NBT with BKCommonLib: ${e.message}" }
            null
        }
    }

    /**
     * Basic NBT parsing without external libraries.
     * This is a simplified implementation that handles common cases.
     */
    private fun parseNbtBasic(file: File, uuid: UUID, group: String, gameMode: GameMode?): ImportedPlayerData? {
        return try {
            val nbtData = FileInputStream(file).use { fis ->
                GZIPInputStream(fis).use { gis ->
                    gis.readBytes()
                }
            }

            // Parse NBT structure
            val buffer = ByteBuffer.wrap(nbtData).order(ByteOrder.BIG_ENDIAN)

            // Skip root compound tag header
            if (buffer.get() != 10.toByte()) {
                Logging.debug { "Invalid NBT format - expected compound tag" }
                return null
            }

            // Skip root name
            val nameLength = buffer.short.toInt()
            buffer.position(buffer.position() + nameLength)

            // Parse the compound
            val nbt = parseNbtCompound(buffer)

            // Extract data from parsed NBT
            val health = (nbt["Health"] as? Float)?.toDouble() ?: 20.0
            val foodLevel = (nbt["foodLevel"] as? Int) ?: 20
            val xpLevel = (nbt["XpLevel"] as? Int) ?: 0
            val xpP = (nbt["XpP"] as? Float) ?: 0.0f
            val xpTotal = (nbt["XpTotal"] as? Int) ?: 0

            val playerName = Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString()

            // Note: Full inventory parsing requires complete NBT support
            // This basic implementation returns empty inventories
            ImportedPlayerData(
                uuid = uuid,
                playerName = playerName,
                sourceGroup = group,
                gameMode = gameMode ?: GameMode.SURVIVAL,
                mainInventory = emptyMap(),
                armorInventory = emptyMap(),
                offhand = null,
                enderChest = emptyMap(),
                health = health,
                maxHealth = 20.0,
                foodLevel = foodLevel,
                saturation = 5.0f,
                exhaustion = 0.0f,
                experience = xpP,
                level = xpLevel,
                totalExperience = xpTotal,
                potionEffects = emptyList(),
                balance = null,
                sourceTimestamp = Instant.ofEpochMilli(file.lastModified()),
                sourceId = id,
                metadata = mapOf("warning" to "Basic NBT parsing - inventory may be incomplete")
            )
        } catch (e: Exception) {
            Logging.debug { "Failed to parse NBT data: ${e.message}" }
            null
        }
    }

    /**
     * Parses an NBT compound tag from a ByteBuffer.
     */
    private fun parseNbtCompound(buffer: ByteBuffer): Map<String, Any> {
        val result = mutableMapOf<String, Any>()

        while (buffer.hasRemaining()) {
            val tagType = buffer.get().toInt()
            if (tagType == 0) break // End tag

            // Read name
            val nameLength = buffer.short.toInt()
            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8)

            // Parse value based on tag type
            val value = parseNbtTag(buffer, tagType)
            if (value != null) {
                result[name] = value
            }
        }

        return result
    }

    /**
     * Parses an NBT tag value based on its type.
     */
    private fun parseNbtTag(buffer: ByteBuffer, tagType: Int): Any? {
        return try {
            when (tagType) {
                1 -> buffer.get() // Byte
                2 -> buffer.short // Short
                3 -> buffer.int // Int
                4 -> buffer.long // Long
                5 -> buffer.float // Float
                6 -> buffer.double // Double
                7 -> { // Byte array
                    val length = buffer.int
                    val bytes = ByteArray(length)
                    buffer.get(bytes)
                    bytes
                }
                8 -> { // String
                    val length = buffer.short.toInt()
                    val bytes = ByteArray(length)
                    buffer.get(bytes)
                    String(bytes, Charsets.UTF_8)
                }
                9 -> { // List
                    val itemType = buffer.get().toInt()
                    val length = buffer.int
                    val list = mutableListOf<Any?>()
                    repeat(length) {
                        list.add(parseNbtTag(buffer, itemType))
                    }
                    list
                }
                10 -> parseNbtCompound(buffer) // Compound
                11 -> { // Int array
                    val length = buffer.int
                    IntArray(length) { buffer.int }
                }
                12 -> { // Long array
                    val length = buffer.int
                    LongArray(length) { buffer.long }
                }
                else -> {
                    Logging.debug { "Unknown NBT tag type: $tagType" }
                    null
                }
            }
        } catch (e: Exception) {
            Logging.debug { "Failed to parse NBT tag type $tagType: ${e.message}" }
            null
        }
    }

    override fun getAllPlayerData(uuid: UUID): Map<String, ImportedPlayerData> {
        val result = mutableMapOf<String, ImportedPlayerData>()

        getGroups().forEach { group ->
            getPlayerData(uuid, group.name, null)?.let { data ->
                result[group.name] = data
            }
        }

        return result
    }

    override fun validate(): ImportValidationResult {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        if (!isAvailable) {
            issues.add("No world folders with player data found")
            return ImportValidationResult(
                isValid = false,
                issues = issues
            )
        }

        val groups = getGroups()
        val players = getPlayers()

        if (groups.isEmpty()) {
            warnings.add("No world inventory groups detected")
        }

        if (players.isEmpty()) {
            warnings.add("No player data files found")
        }

        if (!hasApiAccess) {
            warnings.add("MyWorlds plugin not loaded - using basic NBT parsing (inventory import may be incomplete)")
        }

        // Check for corrupted files
        var corruptedFiles = 0
        worldsFolder.listFiles()?.filter { it.isDirectory }?.forEach { worldDir ->
            val playerDataDir = File(worldDir, "playerdata")
            if (playerDataDir.exists()) {
                playerDataDir.listFiles()?.filter { it.extension == "dat" }?.forEach { datFile ->
                    try {
                        FileInputStream(datFile).use { fis ->
                            GZIPInputStream(fis).use { it.readBytes() }
                        }
                    } catch (e: Exception) {
                        corruptedFiles++
                    }
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

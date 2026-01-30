package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.event.TemplateApplyEvent
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.internal.model.InventoryTemplate
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.TemplateApplyTrigger
import sh.pcx.xinventories.internal.model.TemplateSettings
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import sh.pcx.xinventories.internal.compat.PotionEffectCompat
import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing inventory templates.
 *
 * Templates are stored as YAML files in plugins/xInventories/templates/
 */
class TemplateService(
    private val plugin: PluginContext,
    private val scope: CoroutineScope
) {
    private val templates = ConcurrentHashMap<String, InventoryTemplate>()
    private val templatesDir: File = File(plugin.plugin.dataFolder, "templates")

    // Track first-join per player per group
    private val firstJoinTracker = ConcurrentHashMap<String, MutableSet<String>>()
    private val firstJoinFile: File = File(plugin.plugin.dataFolder, "first-joins.yml")

    /**
     * Initializes the template service.
     */
    suspend fun initialize() {
        // Create templates directory if it doesn't exist
        if (!templatesDir.exists()) {
            templatesDir.mkdirs()
        }

        // Load all templates
        loadAllTemplates()

        // Load first-join data
        loadFirstJoinData()

        Logging.info("TemplateService initialized with ${templates.size} templates")
    }

    /**
     * Shuts down the template service.
     */
    suspend fun shutdown() {
        saveFirstJoinData()
        Logging.debug { "TemplateService shut down" }
    }

    /**
     * Loads all templates from the templates directory.
     */
    private suspend fun loadAllTemplates() {
        withContext(Dispatchers.IO) {
            templates.clear()

            val files = templatesDir.listFiles { file -> file.extension == "yml" } ?: return@withContext

            for (file in files) {
                try {
                    val template = loadTemplateFromFile(file)
                    if (template != null) {
                        templates[template.name] = template
                        Logging.debug { "Loaded template: ${template.name}" }
                    }
                } catch (e: Exception) {
                    Logging.error("Failed to load template from ${file.name}", e)
                }
            }
        }
    }

    /**
     * Loads a single template from a file.
     */
    private fun loadTemplateFromFile(file: File): InventoryTemplate? {
        val config = YamlConfiguration.loadConfiguration(file)
        val name = file.nameWithoutExtension

        val displayName = config.getString("displayName")
        val description = config.getString("description")
        val createdAt = config.getLong("createdAt", Instant.now().toEpochMilli())
        val createdByStr = config.getString("createdBy")
        val createdBy = createdByStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        // Create empty PlayerData for the template
        val playerData = PlayerData.empty(
            uuid = UUID.fromString("00000000-0000-0000-0000-000000000000"),
            playerName = "template",
            group = "template",
            gameMode = GameMode.SURVIVAL
        )

        // Load inventory
        val inventorySection = config.getConfigurationSection("inventory")
        if (inventorySection != null) {
            for (slotStr in inventorySection.getKeys(false)) {
                val slot = slotStr.toIntOrNull() ?: continue
                val itemSection = inventorySection.getConfigurationSection(slotStr) ?: continue
                val item = loadItemFromConfig(itemSection)
                if (item != null && slot in 0..35) {
                    playerData.mainInventory[slot] = item
                }
            }
        }

        // Load armor
        val armorSection = config.getConfigurationSection("armor")
        if (armorSection != null) {
            loadArmorSlot(armorSection, "helmet", 3, playerData)
            loadArmorSlot(armorSection, "chestplate", 2, playerData)
            loadArmorSlot(armorSection, "leggings", 1, playerData)
            loadArmorSlot(armorSection, "boots", 0, playerData)
        }

        // Load offhand
        val offhandSection = config.getConfigurationSection("offhand")
        if (offhandSection != null) {
            playerData.offhand = loadItemFromConfig(offhandSection)
        }

        // Load experience
        val expSection = config.getConfigurationSection("experience")
        if (expSection != null) {
            playerData.level = expSection.getInt("level", 0)
            playerData.experience = expSection.getDouble("exp", 0.0).toFloat()
        }

        // Load health and hunger
        playerData.health = config.getDouble("health", 20.0)
        playerData.maxHealth = config.getDouble("maxHealth", 20.0)
        playerData.foodLevel = config.getInt("hunger", 20)
        playerData.saturation = config.getDouble("saturation", 5.0).toFloat()

        // Load effects
        val effectsList = config.getStringList("effects")
        for (effectStr in effectsList) {
            val effect = parseEffect(effectStr)
            if (effect != null) {
                playerData.potionEffects.add(effect)
            }
        }

        return InventoryTemplate(
            name = name,
            displayName = displayName,
            description = description,
            inventory = playerData,
            createdAt = Instant.ofEpochMilli(createdAt),
            createdBy = createdBy
        )
    }

    private fun loadArmorSlot(section: org.bukkit.configuration.ConfigurationSection, key: String, slot: Int, playerData: PlayerData) {
        val itemSection = section.getConfigurationSection(key)
        if (itemSection != null) {
            val item = loadItemFromConfig(itemSection)
            if (item != null) {
                playerData.armorInventory[slot] = item
            }
        }
    }

    private fun loadItemFromConfig(section: org.bukkit.configuration.ConfigurationSection): ItemStack? {
        val typeStr = section.getString("type") ?: return null
        val material = org.bukkit.Material.matchMaterial(typeStr) ?: return null
        val amount = section.getInt("amount", 1)

        val item = ItemStack(material, amount)
        val meta = item.itemMeta ?: return item

        // Display name
        section.getString("displayName")?.let {
            meta.setDisplayName(it.replace('&', '\u00A7'))
        }

        // Lore
        section.getStringList("lore").takeIf { it.isNotEmpty() }?.let { loreList ->
            meta.lore = loreList.map { it.replace('&', '\u00A7') }
        }

        // Enchantments
        val enchSection = section.getConfigurationSection("enchantments")
        if (enchSection != null) {
            for (enchName in enchSection.getKeys(false)) {
                val level = enchSection.getInt(enchName, 1)
                val key = org.bukkit.NamespacedKey.minecraft(enchName.lowercase())
                val enchantment = org.bukkit.enchantments.Enchantment.getByKey(key)
                if (enchantment != null) {
                    meta.addEnchant(enchantment, level, true)
                }
            }
        }

        item.itemMeta = meta
        return item
    }

    private fun parseEffect(effectStr: String): PotionEffect? {
        // Format: "EFFECT_NAME:DURATION:AMPLIFIER" or "EFFECT_NAME:DURATION" or "EFFECT_NAME"
        val parts = effectStr.split(":")
        if (parts.isEmpty()) return null

        val effectType = PotionEffectCompat.getByName(parts[0]) ?: return null
        val duration = parts.getOrNull(1)?.toIntOrNull() ?: 600 // Default 30 seconds
        val amplifier = parts.getOrNull(2)?.toIntOrNull() ?: 0

        return PotionEffect(effectType, duration, amplifier)
    }

    /**
     * Saves a template to file.
     */
    suspend fun saveTemplate(template: InventoryTemplate): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val file = File(templatesDir, "${template.name}.yml")
                val config = YamlConfiguration()

                // Metadata
                template.displayName?.let { config.set("displayName", it) }
                template.description?.let { config.set("description", it) }
                config.set("createdAt", template.createdAt.toEpochMilli())
                template.createdBy?.let { config.set("createdBy", it.toString()) }

                // Inventory
                for ((slot, item) in template.inventory.mainInventory) {
                    saveItemToConfig(config, "inventory.$slot", item)
                }

                // Armor
                template.inventory.armorInventory[3]?.let { saveItemToConfig(config, "armor.helmet", it) }
                template.inventory.armorInventory[2]?.let { saveItemToConfig(config, "armor.chestplate", it) }
                template.inventory.armorInventory[1]?.let { saveItemToConfig(config, "armor.leggings", it) }
                template.inventory.armorInventory[0]?.let { saveItemToConfig(config, "armor.boots", it) }

                // Offhand
                template.inventory.offhand?.let { saveItemToConfig(config, "offhand", it) }

                // Experience
                config.set("experience.level", template.inventory.level)
                config.set("experience.exp", template.inventory.experience)

                // Health and hunger
                config.set("health", template.inventory.health)
                config.set("maxHealth", template.inventory.maxHealth)
                config.set("hunger", template.inventory.foodLevel)
                config.set("saturation", template.inventory.saturation)

                // Effects
                val effectsList = template.inventory.potionEffects.map { effect ->
                    "${effect.type.name}:${effect.duration}:${effect.amplifier}"
                }
                config.set("effects", effectsList)

                config.save(file)
                templates[template.name] = template

                Logging.debug { "Saved template: ${template.name}" }
                true
            } catch (e: Exception) {
                Logging.error("Failed to save template: ${template.name}", e)
                false
            }
        }
    }

    private fun saveItemToConfig(config: YamlConfiguration, path: String, item: ItemStack) {
        config.set("$path.type", item.type.name)
        config.set("$path.amount", item.amount)

        val meta = item.itemMeta
        if (meta != null) {
            meta.displayName?.let { config.set("$path.displayName", it) }
            meta.lore?.let { config.set("$path.lore", it) }

            if (meta.hasEnchants()) {
                for ((enchant, level) in meta.enchants) {
                    config.set("$path.enchantments.${enchant.key.key.uppercase()}", level)
                }
            }
        }
    }

    /**
     * Gets a template by name.
     */
    fun getTemplate(name: String): InventoryTemplate? = templates[name]

    /**
     * Gets all templates.
     */
    fun getAllTemplates(): List<InventoryTemplate> = templates.values.toList()

    /**
     * Creates a new template from a player's inventory.
     */
    suspend fun createTemplate(
        name: String,
        player: Player,
        displayName: String? = null,
        description: String? = null
    ): Result<InventoryTemplate> {
        if (templates.containsKey(name)) {
            return Result.failure(IllegalArgumentException("Template '$name' already exists"))
        }

        val template = InventoryTemplate.fromPlayer(
            name = name,
            player = player,
            createdBy = player.uniqueId,
            displayName = displayName,
            description = description
        )

        return if (saveTemplate(template)) {
            Result.success(template)
        } else {
            Result.failure(Exception("Failed to save template"))
        }
    }

    /**
     * Deletes a template.
     */
    suspend fun deleteTemplate(name: String): Boolean {
        return withContext(Dispatchers.IO) {
            val file = File(templatesDir, "$name.yml")
            if (file.exists()) {
                val deleted = file.delete()
                if (deleted) {
                    templates.remove(name)
                    Logging.info("Deleted template: $name")
                }
                deleted
            } else {
                false
            }
        }
    }

    /**
     * Applies a template to a player.
     */
    suspend fun applyTemplate(
        player: Player,
        template: InventoryTemplate,
        group: InventoryGroup,
        trigger: TemplateApplyTrigger,
        clearFirst: Boolean = true
    ): Boolean {
        // Fire event
        val event = TemplateApplyEvent(player, group, template, trigger)
        event.clearInventoryFirst = clearFirst

        Bukkit.getPluginManager().callEvent(event)

        if (event.isCancelled) {
            Logging.debug { "Template apply cancelled for ${player.name}" }
            return false
        }

        // Apply the template
        return withContext(Dispatchers.Default) {
            try {
                // Run on main thread
                Bukkit.getScheduler().callSyncMethod(plugin.plugin) {
                    if (event.clearInventoryFirst) {
                        player.inventory.clear()
                        player.enderChest.clear()
                    }

                    // Apply template inventory to player
                    template.inventory.applyToPlayer(player, group.settings)
                    true
                }.get()

                Logging.debug { "Applied template '${template.name}' to ${player.name}" }
                true
            } catch (e: Exception) {
                Logging.error("Failed to apply template '${template.name}' to ${player.name}", e)
                false
            }
        }
    }

    /**
     * Checks if this is the player's first join to a group.
     */
    fun isFirstJoin(playerUuid: UUID, groupName: String): Boolean {
        val key = playerUuid.toString()
        val joinedGroups = firstJoinTracker[key] ?: return true
        return !joinedGroups.contains(groupName)
    }

    /**
     * Marks a player as having joined a group.
     */
    fun markJoined(playerUuid: UUID, groupName: String) {
        val key = playerUuid.toString()
        firstJoinTracker.computeIfAbsent(key) { ConcurrentHashMap.newKeySet() }.add(groupName)
    }

    /**
     * Loads first-join tracking data.
     */
    private suspend fun loadFirstJoinData() {
        withContext(Dispatchers.IO) {
            if (!firstJoinFile.exists()) return@withContext

            try {
                val config = YamlConfiguration.loadConfiguration(firstJoinFile)
                for (uuidStr in config.getKeys(false)) {
                    val groups = config.getStringList(uuidStr)
                    if (groups.isNotEmpty()) {
                        firstJoinTracker[uuidStr] = ConcurrentHashMap.newKeySet<String>().apply {
                            addAll(groups)
                        }
                    }
                }
                Logging.debug { "Loaded first-join data for ${firstJoinTracker.size} players" }
            } catch (e: Exception) {
                Logging.error("Failed to load first-join data", e)
            }
        }
    }

    /**
     * Saves first-join tracking data.
     */
    private suspend fun saveFirstJoinData() {
        withContext(Dispatchers.IO) {
            try {
                val config = YamlConfiguration()
                for ((uuidStr, groups) in firstJoinTracker) {
                    config.set(uuidStr, groups.toList())
                }
                config.save(firstJoinFile)
                Logging.debug { "Saved first-join data for ${firstJoinTracker.size} players" }
            } catch (e: Exception) {
                Logging.error("Failed to save first-join data", e)
            }
        }
    }

    /**
     * Handles group entry for template application.
     */
    suspend fun handleGroupEntry(player: Player, group: InventoryGroup, settings: TemplateSettings?) {
        if (settings == null || !settings.enabled) return

        val templateName = settings.templateName ?: group.name
        val template = getTemplate(templateName)

        if (template == null) {
            Logging.debug { "Template '$templateName' not found for group '${group.name}'" }
            return
        }

        when (settings.applyOn) {
            TemplateApplyTrigger.JOIN -> {
                applyTemplate(player, template, group, TemplateApplyTrigger.JOIN, settings.clearInventoryFirst)
            }
            TemplateApplyTrigger.FIRST_JOIN -> {
                if (isFirstJoin(player.uniqueId, group.name)) {
                    applyTemplate(player, template, group, TemplateApplyTrigger.FIRST_JOIN, settings.clearInventoryFirst)
                    markJoined(player.uniqueId, group.name)
                }
            }
            TemplateApplyTrigger.MANUAL, TemplateApplyTrigger.NONE -> {
                // Do nothing on entry
            }
        }
    }

    /**
     * Reloads all templates from disk.
     */
    suspend fun reload() {
        loadAllTemplates()
        Logging.info("Reloaded ${templates.size} templates")
    }
}

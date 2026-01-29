package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.event.GroupChangeEvent
import sh.pcx.xinventories.api.event.InventoryLoadEvent
import sh.pcx.xinventories.api.event.InventorySaveEvent
import sh.pcx.xinventories.api.event.InventorySwitchEvent
import sh.pcx.xinventories.api.model.GroupSettings
import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bukkit.GameMode
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Core service for inventory operations.
 */
class InventoryService(
    private val plugin: XInventories,
    private val scope: CoroutineScope,
    private val storageService: StorageService,
    private val groupService: GroupService,
    private val messageService: MessageService
) {
    // Track current group for online players
    private val playerGroups = ConcurrentHashMap<UUID, String>()

    // Track active snapshots for online players
    private val activeSnapshots = ConcurrentHashMap<UUID, PlayerInventorySnapshot>()

    // Runtime bypasses (in addition to permission-based)
    private val bypasses = ConcurrentHashMap<UUID, MutableSet<String?>>()

    private val config get() = plugin.configManager.mainConfig

    /**
     * Handles player join - loads their inventory for the current world.
     */
    suspend fun handlePlayerJoin(player: Player) {
        if (hasBypass(player)) {
            Logging.debug { "Player ${player.name} has bypass, skipping inventory load" }
            return
        }

        val group = groupService.getGroupForWorld(player.world)
        playerGroups[player.uniqueId] = group.name

        // Load inventory
        loadInventory(player, group.toApiModel(), InventoryLoadEvent.LoadReason.JOIN)
    }

    /**
     * Handles player quit - saves their inventory.
     */
    suspend fun handlePlayerQuit(player: Player) {
        if (hasBypass(player)) {
            Logging.debug { "Player ${player.name} has bypass, skipping inventory save" }
            playerGroups.remove(player.uniqueId)
            activeSnapshots.remove(player.uniqueId)
            return
        }

        val groupName = playerGroups[player.uniqueId] ?: groupService.getGroupForWorld(player.world).name

        // Save current inventory
        saveInventory(player, groupName)

        // Clean up
        playerGroups.remove(player.uniqueId)
        activeSnapshots.remove(player.uniqueId)
    }

    /**
     * Handles world change.
     */
    suspend fun handleWorldChange(player: Player, fromWorldName: String, toWorldName: String) {
        if (hasBypass(player)) return

        val fromGroup = groupService.getGroupForWorld(fromWorldName)
        val toGroup = groupService.getGroupForWorld(toWorldName)

        // Same group - no switch needed
        if (fromGroup.name == toGroup.name) {
            if (config.features.saveOnWorldChange) {
                saveInventory(player, fromGroup.name)
            }
            return
        }

        // Switch inventories
        switchInventory(
            player,
            fromGroup.toApiModel(),
            toGroup.toApiModel(),
            InventorySwitchEvent.SwitchReason.WORLD_CHANGE
        )
    }

    /**
     * Handles gamemode change.
     */
    suspend fun handleGameModeChange(player: Player, oldGameMode: GameMode, newGameMode: GameMode) {
        if (hasBypass(player)) return

        val groupName = playerGroups[player.uniqueId] ?: return
        val group = groupService.getGroup(groupName) ?: return

        // Check if this group separates gamemode inventories
        if (!group.settings.separateGameModeInventories) return

        if (config.features.saveOnGamemodeChange) {
            // Save current inventory with old gamemode
            val data = PlayerData.fromPlayer(player, groupName)
            data.gameMode = oldGameMode
            storageService.savePlayerData(data)
        }

        // Load inventory for new gamemode
        val newData = storageService.loadPlayerData(player.uniqueId, groupName, newGameMode)

        if (newData != null) {
            applyInventory(player, newData, group.settings)
            activeSnapshots[player.uniqueId] = newData.toSnapshot()
        } else {
            // Clear inventory for new gamemode
            clearPlayerInventory(player, group.settings)
        }
    }

    /**
     * Saves a player's current inventory.
     */
    suspend fun saveInventory(player: Player, groupName: String? = null): Boolean {
        val group = groupName ?: playerGroups[player.uniqueId]
            ?: groupService.getGroupForWorld(player.world).name

        val groupObj = groupService.getGroup(group) ?: return false
        val data = PlayerData.fromPlayer(player, group)

        // Fire event - always sync since we may be on main thread
        // Paper requires async events to be called from async threads
        val isAsync = !plugin.server.isPrimaryThread
        val event = InventorySaveEvent(
            player,
            groupObj.toApiModel(),
            data.toSnapshot(),
            isAsync
        )
        plugin.server.pluginManager.callEvent(event)

        val success = storageService.savePlayerData(data)

        if (success) {
            activeSnapshots[player.uniqueId] = data.toSnapshot()
            Logging.debug { "Saved inventory for ${player.name} in group $group" }
        }

        return success
    }

    /**
     * Loads inventory for a player.
     */
    suspend fun loadInventory(
        player: Player,
        group: InventoryGroup,
        reason: InventoryLoadEvent.LoadReason
    ): Boolean {
        val gameMode = if (group.settings.separateGameModeInventories) {
            player.gameMode
        } else {
            null
        }

        val data = storageService.loadPlayerData(player.uniqueId, group.name, gameMode)

        // Create empty data if none exists
        val snapshot = data?.toSnapshot()
            ?: PlayerInventorySnapshot.empty(player.uniqueId, player.name, group.name, player.gameMode)

        // Fire load event on main thread (Paper requires sync events on main thread)
        val eventResult = withContext(plugin.mainThreadDispatcher) {
            val event = InventoryLoadEvent(player, group, snapshot, reason)
            plugin.server.pluginManager.callEvent(event)
            if (event.isCancelled) null else event.snapshot
        }

        if (eventResult == null) {
            Logging.debug { "Inventory load cancelled for ${player.name}" }
            return false
        }

        // Apply to player (applyInventory already schedules on main thread)
        if (data != null) {
            applyInventory(player, data, group.settings)
        } else if (group.settings.clearOnJoin) {
            clearPlayerInventory(player, group.settings)
        }

        activeSnapshots[player.uniqueId] = eventResult
        playerGroups[player.uniqueId] = group.name

        Logging.debug { "Loaded inventory for ${player.name} in group ${group.name}" }
        return true
    }

    /**
     * Switches inventory between groups.
     */
    suspend fun switchInventory(
        player: Player,
        fromGroup: InventoryGroup,
        toGroup: InventoryGroup,
        reason: InventorySwitchEvent.SwitchReason
    ): Boolean {
        val currentSnapshot = activeSnapshots[player.uniqueId]
            ?: PlayerInventorySnapshot.fromPlayer(player, fromGroup.name)

        // Fire switch event on main thread (Paper requires sync events on main thread)
        val switchEventResult = withContext(plugin.mainThreadDispatcher) {
            val event = InventorySwitchEvent(
                player,
                fromGroup,
                toGroup,
                player.world,
                player.world,
                currentSnapshot,
                reason
            )
            plugin.server.pluginManager.callEvent(event)
            if (event.isCancelled) null else event
        }

        if (switchEventResult == null) {
            Logging.debug { "Inventory switch cancelled for ${player.name}" }
            return false
        }

        // Save current inventory
        if (config.features.saveOnWorldChange) {
            val data = PlayerData.fromPlayer(player, fromGroup.name)
            storageService.savePlayerData(data)
        }

        // Fire group change event on main thread
        withContext(plugin.mainThreadDispatcher) {
            val groupChangeEvent = GroupChangeEvent(
                player,
                fromGroup,
                toGroup,
                GroupChangeEvent.ChangeReason.WORLD_CHANGE
            )
            plugin.server.pluginManager.callEvent(groupChangeEvent)
        }

        // Load new inventory (use override if provided)
        val loadedData = if (switchEventResult.overrideSnapshot != null) {
            PlayerData.fromSnapshot(switchEventResult.overrideSnapshot!!)
        } else {
            val gameMode = if (toGroup.settings.separateGameModeInventories) player.gameMode else null
            storageService.loadPlayerData(player.uniqueId, toGroup.name, gameMode)
        }

        // Apply new inventory
        if (loadedData != null) {
            applyInventory(player, loadedData, toGroup.settings)
            activeSnapshots[player.uniqueId] = loadedData.toSnapshot()
        } else if (toGroup.settings.clearOnJoin) {
            clearPlayerInventory(player, toGroup.settings)
            activeSnapshots[player.uniqueId] = PlayerInventorySnapshot.empty(
                player.uniqueId, player.name, toGroup.name, player.gameMode
            )
        }

        playerGroups[player.uniqueId] = toGroup.name

        // Notify player
        notifySwitch(player, fromGroup.name, toGroup.name)

        Logging.debug { "Switched inventory for ${player.name}: ${fromGroup.name} -> ${toGroup.name}" }
        return true
    }

    /**
     * Gets the active snapshot for a player.
     */
    fun getActiveSnapshot(player: Player): PlayerInventorySnapshot? {
        return activeSnapshots[player.uniqueId]
    }

    /**
     * Gets the current group for a player.
     */
    fun getCurrentGroup(player: Player): String? {
        return playerGroups[player.uniqueId]
    }

    /**
     * Clears player data for a specific group.
     */
    suspend fun clearPlayerData(uuid: UUID, group: String, gameMode: GameMode?): Boolean {
        return storageService.deletePlayerData(uuid, group, gameMode)
    }

    // Bypass management

    fun addBypass(uuid: UUID, group: String? = null) {
        bypasses.getOrPut(uuid) { ConcurrentHashMap.newKeySet() }.add(group)
    }

    fun removeBypass(uuid: UUID, group: String? = null) {
        bypasses[uuid]?.remove(group)
        if (bypasses[uuid]?.isEmpty() == true) {
            bypasses.remove(uuid)
        }
    }

    fun hasBypass(player: Player, group: String? = null): Boolean {
        // Check permission
        if (player.hasPermission("xinventories.bypass")) return true
        if (group != null && player.hasPermission("xinventories.bypass.$group")) return true

        // Check runtime bypass
        val playerBypasses = bypasses[player.uniqueId] ?: return false
        return playerBypasses.contains(null) || (group != null && playerBypasses.contains(group))
    }

    fun hasBypass(uuid: UUID, group: String? = null): Boolean {
        val playerBypasses = bypasses[uuid] ?: return false
        return playerBypasses.contains(null) || (group != null && playerBypasses.contains(group))
    }

    fun getBypasses(): Map<UUID, Set<String?>> {
        return bypasses.mapValues { it.value.toSet() }
    }

    // Private helpers

    private fun applyInventory(player: Player, data: PlayerData, settings: GroupSettings) {
        // Run on main thread
        plugin.server.scheduler.runTask(plugin, Runnable {
            data.applyToPlayer(player, settings)
        })
    }

    private fun clearPlayerInventory(player: Player, settings: GroupSettings) {
        plugin.server.scheduler.runTask(plugin, Runnable {
            player.inventory.clear()
            player.inventory.armorContents = arrayOfNulls(4)
            player.inventory.setItemInOffHand(null)

            if (settings.saveEnderChest) {
                player.enderChest.clear()
            }

            if (settings.saveHealth) {
                player.health = 20.0
            }

            if (settings.saveHunger) {
                player.foodLevel = 20
            }

            if (settings.saveSaturation) {
                player.saturation = 5.0f
            }

            if (settings.saveExperience) {
                player.exp = 0f
                player.level = 0
                player.totalExperience = 0
            }

            if (settings.savePotionEffects) {
                player.activePotionEffects.forEach { effect ->
                    player.removePotionEffect(effect.type)
                }
            }
        })
    }

    private fun notifySwitch(player: Player, fromGroup: String, toGroup: String) {
        val globalSettings = plugin.configManager.groupsConfig.globalSettings

        if (globalSettings.notifyOnSwitch) {
            messageService.send(player, "inventory-switched",
                "from_group" to fromGroup,
                "to_group" to toGroup
            )
        }

        // Play sound
        globalSettings.switchSound?.let { soundName ->
            try {
                val sound = Sound.valueOf(soundName)
                player.playSound(
                    player.location,
                    sound,
                    globalSettings.switchSoundVolume,
                    globalSettings.switchSoundPitch
                )
            } catch (e: Exception) {
                Logging.debug { "Invalid switch sound: $soundName" }
            }
        }
    }
}

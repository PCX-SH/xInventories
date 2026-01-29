package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.util.toReadableSize
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Debug and diagnostic commands.
 */
class DebugCommand : Subcommand {

    override val name = "debug"
    override val permission = "xinventories.command.debug"
    override val usage = "/xinv debug [info|player|storage]"
    override val description = "Debug information"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val action = args.getOrNull(0)?.lowercase() ?: "info"

        when (action) {
            "info" -> showInfo(plugin, sender)
            "player" -> showPlayerInfo(plugin, sender, args.getOrNull(1))
            "storage" -> showStorageInfo(plugin, sender)
            else -> showInfo(plugin, sender)
        }

        return true
    }

    private suspend fun showInfo(plugin: XInventories, sender: CommandSender) {
        val config = plugin.configManager.mainConfig
        val groupService = plugin.serviceManager.groupService
        val storageService = plugin.serviceManager.storageService

        sender.sendMessage("§6§l=== xInventories Debug Info ===")
        sender.sendMessage("§7Version: §f${plugin.description.version}")
        sender.sendMessage("§7Storage Type: §f${config.storage.type}")
        sender.sendMessage("§7Cache Enabled: §f${config.cache.enabled}")
        sender.sendMessage("§7Groups: §f${groupService.getAllGroups().size}")
        sender.sendMessage("§7Default Group: §f${groupService.getDefaultGroup().name}")
        sender.sendMessage("§7Online Players: §f${Bukkit.getOnlinePlayers().size}")

        // Cache stats
        val cacheStats = storageService.getCacheStats()
        sender.sendMessage("§7Cache Size: §f${cacheStats.size}/${cacheStats.maxSize}")
        sender.sendMessage("§7Cache Hit Rate: §f${String.format("%.1f", cacheStats.hitRate * 100)}%")

        // Storage stats
        val entryCount = storageService.getEntryCount()
        val storageSize = storageService.getStorageSize()
        sender.sendMessage("§7Storage Entries: §f$entryCount")
        if (storageSize >= 0) {
            sender.sendMessage("§7Storage Size: §f${storageSize.toReadableSize()}")
        }

        // Health
        val healthy = storageService.isHealthy()
        sender.sendMessage("§7Storage Health: ${if (healthy) "§aHealthy" else "§cUnhealthy"}")
    }

    private fun showPlayerInfo(plugin: XInventories, sender: CommandSender, playerName: String?) {
        val target = if (playerName != null) {
            Bukkit.getPlayer(playerName)
        } else if (sender is Player) {
            sender
        } else {
            sender.sendMessage("§cSpecify a player name.")
            return
        }

        if (target == null) {
            sender.sendMessage("§cPlayer not found.")
            return
        }

        val inventoryService = plugin.serviceManager.inventoryService
        val groupService = plugin.serviceManager.groupService

        val currentGroup = inventoryService.getCurrentGroup(target)
            ?: groupService.getGroupForWorld(target.world).name
        val activeSnapshot = inventoryService.getActiveSnapshot(target)
        val hasBypass = inventoryService.hasBypass(target)

        sender.sendMessage("§6§l=== Player Debug: ${target.name} ===")
        sender.sendMessage("§7UUID: §f${target.uniqueId}")
        sender.sendMessage("§7World: §f${target.world.name}")
        sender.sendMessage("§7GameMode: §f${target.gameMode}")
        sender.sendMessage("§7Current Group: §f$currentGroup")
        sender.sendMessage("§7Has Bypass: §f$hasBypass")

        if (activeSnapshot != null) {
            sender.sendMessage("§7Snapshot Group: §f${activeSnapshot.group}")
            sender.sendMessage("§7Snapshot GameMode: §f${activeSnapshot.gameMode}")
            sender.sendMessage("§7Snapshot Items: §f${activeSnapshot.contents.totalItems()}")
            sender.sendMessage("§7Snapshot Time: §f${activeSnapshot.timestamp}")
        } else {
            sender.sendMessage("§7Active Snapshot: §cnone")
        }

        // Group info
        val group = groupService.getGroupForWorld(target.world)
        sender.sendMessage("§7World Group: §f${group.name}")
        sender.sendMessage("§7Separate GM Inventories: §f${group.settings.separateGameModeInventories}")
    }

    private suspend fun showStorageInfo(plugin: XInventories, sender: CommandSender) {
        val storageService = plugin.serviceManager.storageService
        val config = plugin.configManager.mainConfig

        sender.sendMessage("§6§l=== Storage Debug Info ===")
        sender.sendMessage("§7Type: §f${config.storage.type}")

        when (config.storage.type) {
            sh.pcx.xinventories.api.model.StorageType.YAML -> {
                sender.sendMessage("§7Location: §fplugins/xInventories/data/players/")
            }
            sh.pcx.xinventories.api.model.StorageType.SQLITE -> {
                sender.sendMessage("§7File: §f${config.storage.sqlite.file}")
            }
            sh.pcx.xinventories.api.model.StorageType.MYSQL -> {
                sender.sendMessage("§7Host: §f${config.storage.mysql.host}:${config.storage.mysql.port}")
                sender.sendMessage("§7Database: §f${config.storage.mysql.database}")
                sender.sendMessage("§7Pool Size: §f${config.storage.mysql.pool.maximumPoolSize}")
            }
        }

        val entryCount = storageService.getEntryCount()
        val storageSize = storageService.getStorageSize()
        val playerCount = storageService.getAllPlayerUUIDs().size
        val healthy = storageService.isHealthy()

        sender.sendMessage("§7Total Entries: §f$entryCount")
        sender.sendMessage("§7Unique Players: §f$playerCount")
        if (storageSize >= 0) {
            sender.sendMessage("§7Storage Size: §f${storageSize.toReadableSize()}")
        }
        sender.sendMessage("§7Health: ${if (healthy) "§aHealthy" else "§cUnhealthy"}")

        // Cache info
        sender.sendMessage("§7Cache Dirty Entries: §f${storageService.cache.getDirtyCount()}")
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("info", "player", "storage")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "player" -> Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

package sh.pcx.xinventories.internal.command.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import sh.pcx.xinventories.PluginContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Command for viewing sync status and managing network synchronization.
 *
 * Usage:
 *   /xinv sync status - Show sync status and connected servers
 *   /xinv sync servers - List all connected servers
 *   /xinv sync locks - Show active locks
 *   /xinv sync lock <player> - Check lock status for a player
 *   /xinv sync unlock <player> - Force unlock a player (admin)
 *   /xinv sync invalidate <player> - Invalidate cache for a player
 *   /xinv sync stats - Show sync statistics
 */
class SyncCommand : Subcommand {

    override val name = "sync"
    override val aliases = listOf("network")
    override val permission = "xinventories.command.sync"
    override val usage = "/xinv sync <status|servers|locks|lock|unlock|invalidate|stats>"
    override val description = "Network synchronization commands"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val syncService = plugin.serviceManager.syncService

        if (syncService == null || !syncService.isEnabled) {
            sender.sendMessage("§cNetwork sync is not enabled or not connected.")
            sender.sendMessage("§7Enable sync in config.yml under the 'sync' section.")
            return true
        }

        val action = args.getOrNull(0)?.lowercase() ?: "status"

        when (action) {
            "status" -> showStatus(plugin, sender, syncService)
            "servers" -> showServers(sender, syncService)
            "locks" -> showLocks(sender, syncService)
            "lock" -> checkLock(sender, syncService, args.getOrNull(1))
            "unlock" -> forceUnlock(sender, plugin, args.getOrNull(1))
            "invalidate" -> invalidateCache(sender, plugin, args.getOrNull(1))
            "stats" -> showStats(sender, syncService)
            else -> {
                sender.sendMessage("§cUnknown action: $action")
                sender.sendMessage("§7Usage: $usage")
            }
        }

        return true
    }

    private fun showStatus(plugin: PluginContext, sender: CommandSender, syncService: sh.pcx.xinventories.internal.service.SyncService) {
        sender.sendMessage("§6§l=== xInventories Sync Status ===")
        sender.sendMessage("")
        sender.sendMessage("§7Server ID: §f${syncService.serverId}")
        sender.sendMessage("§7Status: ${if (syncService.isEnabled) "§aConnected" else "§cDisconnected"}")

        val connectedServers = syncService.getConnectedServers()
        sender.sendMessage("§7Connected Servers: §f${connectedServers.size}")

        val stats = syncService.getStats()
        sender.sendMessage("§7Messages Sent: §f${stats.messagesPublished}")
        sender.sendMessage("§7Messages Received: §f${stats.messagesReceived}")
        sender.sendMessage("§7Lock Conflicts: §f${stats.lockConflicts}")

        if (stats.lastHeartbeat > 0) {
            val lastHeartbeat = Instant.ofEpochMilli(stats.lastHeartbeat)
            sender.sendMessage("§7Last Heartbeat: §f${timeFormatter.format(lastHeartbeat)}")
        }

        // Show config summary
        val config = plugin.configManager.mainConfig
        val syncConfig = plugin.serviceManager.syncConfig
        if (syncConfig != null) {
            sender.sendMessage("")
            sender.sendMessage("§7Redis: §f${syncConfig.redis.host}:${syncConfig.redis.port}")
            sender.sendMessage("§7Channel: §f${syncConfig.redis.channel}")
            sender.sendMessage("§7Heartbeat Interval: §f${syncConfig.heartbeat.intervalSeconds}s")
        }
    }

    private fun showServers(sender: CommandSender, syncService: sh.pcx.xinventories.internal.service.SyncService) {
        val servers = syncService.getConnectedServers()

        sender.sendMessage("§6§l=== Connected Servers (${servers.size}) ===")
        sender.sendMessage("")

        if (servers.isEmpty()) {
            sender.sendMessage("§7No other servers detected.")
            sender.sendMessage("§7(This server: ${syncService.serverId})")
            return
        }

        // Add this server first
        sender.sendMessage("§a• ${syncService.serverId} §7(this server)")

        servers.sortedBy { it.serverId }.forEach { server ->
            val healthColor = if (server.isHealthy) "§a" else "§c"
            val healthIcon = if (server.isHealthy) "•" else "✗"
            val lastSeen = formatTimeSince(server.lastHeartbeat.toEpochMilli())

            sender.sendMessage("$healthColor$healthIcon ${server.serverId} §7- ${server.playerCount} players, last seen $lastSeen")
        }
    }

    private fun showLocks(sender: CommandSender, syncService: sh.pcx.xinventories.internal.service.SyncService) {
        // Get locks from distributed lock manager if available
        sender.sendMessage("§6§l=== Active Player Locks ===")
        sender.sendMessage("")

        // Check online players for locks
        val lockedPlayers = Bukkit.getOnlinePlayers()
            .filter { syncService.isPlayerLocked(it.uniqueId) }

        if (lockedPlayers.isEmpty()) {
            sender.sendMessage("§7No players are currently locked.")
            return
        }

        lockedPlayers.forEach { player ->
            val holder = syncService.getLockHolder(player.uniqueId) ?: "unknown"
            val isOurs = holder == syncService.serverId
            val holderDisplay = if (isOurs) "§a$holder (this server)" else "§e$holder"
            sender.sendMessage("§7• ${player.name} §7- locked by $holderDisplay")
        }
    }

    private fun checkLock(sender: CommandSender, syncService: sh.pcx.xinventories.internal.service.SyncService, playerName: String?) {
        if (playerName == null) {
            sender.sendMessage("§cUsage: /xinv sync lock <player>")
            return
        }

        // Try to find player by name or UUID
        val uuid = try {
            UUID.fromString(playerName)
        } catch (e: Exception) {
            Bukkit.getOfflinePlayer(playerName).uniqueId
        }

        val isLocked = syncService.isPlayerLocked(uuid)
        val holder = syncService.getLockHolder(uuid)

        sender.sendMessage("§6§l=== Lock Status: $playerName ===")
        sender.sendMessage("")
        sender.sendMessage("§7UUID: §f$uuid")
        sender.sendMessage("§7Locked: ${if (isLocked) "§aYes" else "§cNo"}")

        if (holder != null) {
            val isOurs = holder == syncService.serverId
            sender.sendMessage("§7Held by: ${if (isOurs) "§a$holder (this server)" else "§e$holder"}")
        }
    }

    private suspend fun forceUnlock(sender: CommandSender, plugin: PluginContext, playerName: String?) {
        if (!sender.hasPermission("xinventories.command.sync.unlock")) {
            sender.sendMessage("§cYou don't have permission to force unlock players.")
            return
        }

        if (playerName == null) {
            sender.sendMessage("§cUsage: /xinv sync unlock <player>")
            return
        }

        val syncService = plugin.serviceManager.syncService
        if (syncService == null || !syncService.isEnabled) {
            sender.sendMessage("§cSync service is not available.")
            return
        }

        val uuid = try {
            UUID.fromString(playerName)
        } catch (e: Exception) {
            Bukkit.getOfflinePlayer(playerName).uniqueId
        }

        if (!syncService.isPlayerLocked(uuid)) {
            sender.sendMessage("§7Player $playerName is not locked.")
            return
        }

        syncService.releaseLock(uuid)
        sender.sendMessage("§aForce released lock for $playerName ($uuid)")
    }

    private fun invalidateCache(sender: CommandSender, plugin: PluginContext, playerName: String?) {
        if (playerName == null) {
            sender.sendMessage("§cUsage: /xinv sync invalidate <player>")
            return
        }

        val syncService = plugin.serviceManager.syncService
        if (syncService == null || !syncService.isEnabled) {
            sender.sendMessage("§cSync service is not available.")
            return
        }

        val uuid = try {
            UUID.fromString(playerName)
        } catch (e: Exception) {
            Bukkit.getOfflinePlayer(playerName).uniqueId
        }

        // Invalidate locally
        plugin.serviceManager.storageService.invalidateCache(uuid)

        // Broadcast to other servers
        syncService.broadcastInvalidation(uuid)

        sender.sendMessage("§aInvalidated cache for $playerName ($uuid) across all servers")
    }

    private fun showStats(sender: CommandSender, syncService: sh.pcx.xinventories.internal.service.SyncService) {
        val stats = syncService.getStats()

        sender.sendMessage("§6§l=== Sync Statistics ===")
        sender.sendMessage("")
        sender.sendMessage("§7Messages Published: §f${stats.messagesPublished}")
        sender.sendMessage("§7Messages Received: §f${stats.messagesReceived}")
        sender.sendMessage("§7Locks Acquired: §f${stats.locksAcquired}")
        sender.sendMessage("§7Locks Released: §f${stats.locksReleased}")
        sender.sendMessage("§7Lock Conflicts: §f${stats.lockConflicts}")
        sender.sendMessage("§7Connected Servers: §f${stats.connectedServers}")

        if (stats.lastHeartbeat > 0) {
            val lastHeartbeat = Instant.ofEpochMilli(stats.lastHeartbeat)
            sender.sendMessage("§7Last Heartbeat: §f${timeFormatter.format(lastHeartbeat)}")
        }
    }

    private fun formatTimeSince(timestamp: Long): String {
        val seconds = (System.currentTimeMillis() - timestamp) / 1000
        return when {
            seconds < 60 -> "${seconds}s ago"
            seconds < 3600 -> "${seconds / 60}m ago"
            else -> "${seconds / 3600}h ago"
        }
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("status", "servers", "locks", "lock", "unlock", "invalidate", "stats")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "lock", "unlock", "invalidate" -> Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

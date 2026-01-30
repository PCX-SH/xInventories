package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.PluginContext
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender

/**
 * Manages the inventory cache.
 */
class CacheCommand : Subcommand {

    override val name = "cache"
    override val permission = "xinventories.command.cache"
    override val usage = "/xinv cache <stats|clear> [player]"
    override val description = "Manage inventory cache"

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val storageService = plugin.serviceManager.storageService

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        when (args[0].lowercase()) {
            "stats" -> {
                val stats = storageService.getCacheStats()

                messages.sendRaw(sender, "cache-stats-header")
                messages.sendRaw(sender, "cache-stats-size",
                    "size" to stats.size.toString(),
                    "max" to stats.maxSize.toString()
                )
                messages.sendRaw(sender, "cache-stats-hits",
                    "hits" to stats.hitCount.toString(),
                    "rate" to String.format("%.1f", stats.hitRate * 100)
                )
                messages.sendRaw(sender, "cache-stats-misses",
                    "misses" to stats.missCount.toString()
                )
                messages.sendRaw(sender, "cache-stats-evictions",
                    "evictions" to stats.evictionCount.toString()
                )
            }

            "clear" -> {
                if (args.size > 1) {
                    // Clear specific player
                    val playerName = args[1]
                    val player = Bukkit.getPlayer(playerName)
                        ?: Bukkit.getOfflinePlayer(playerName).takeIf { it.hasPlayedBefore() }

                    if (player == null) {
                        messages.send(sender, "player-not-found", "player" to playerName)
                        return true
                    }

                    storageService.invalidateCache(player.uniqueId)
                    messages.send(sender, "cache-player-cleared", "player" to playerName)
                } else {
                    // Clear all
                    val count = storageService.clearCache()
                    messages.send(sender, "cache-cleared", "count" to count.toString())
                }
            }

            else -> {
                messages.send(sender, "invalid-syntax", "usage" to usage)
            }
        }

        return true
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> listOf("stats", "clear")
                .filter { it.startsWith(args[0].lowercase()) }
            2 -> when (args[0].lowercase()) {
                "clear" -> Bukkit.getOnlinePlayers()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

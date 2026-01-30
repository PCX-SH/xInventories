package sh.pcx.xinventories.internal.command.subcommand

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import sh.pcx.xinventories.XInventories
import java.time.format.DateTimeFormatter
import java.time.ZoneId

/**
 * Command to view and restore death inventories.
 * Usage: /xinv deaths <player> [restore|view] [death-id]
 */
class DeathsCommand : Subcommand {

    override val name = "deaths"
    override val aliases = listOf("death")
    override val permission = "xinventories.command.deaths"
    override val usage = "/xinv deaths <player> [restore|view] [death-id]"
    override val description = "View and restore death inventories"

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val deathRecoveryService = plugin.serviceManager.deathRecoveryService

        if (!plugin.configManager.mainConfig.deathRecovery.enabled) {
            messages.send(sender, "death-recovery-disabled")
            return true
        }

        if (args.isEmpty()) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        val playerName = args[0]
        val targetPlayer = Bukkit.getPlayer(playerName)
        val offlinePlayer = Bukkit.getOfflinePlayer(playerName)

        val playerUuid = targetPlayer?.uniqueId ?: offlinePlayer.uniqueId
        val displayName = targetPlayer?.name ?: offlinePlayer.name ?: playerName

        if (!offlinePlayer.hasPlayedBefore() && targetPlayer == null) {
            messages.send(sender, "player-not-found", "player" to playerName)
            return true
        }

        val action = args.getOrNull(1)?.lowercase()
        val deathId = args.getOrNull(2)

        when (action) {
            "restore" -> {
                if (!sender.hasPermission("xinventories.command.deaths.restore")) {
                    messages.send(sender, "no-permission")
                    return true
                }

                if (targetPlayer == null) {
                    messages.send(sender, "player-must-be-online", "player" to playerName)
                    return true
                }

                if (deathId == null) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv deaths $playerName restore <death-id>")
                    return true
                }

                // Expand short death IDs
                val fullDeathId = if (deathId.length < 36) {
                    val deaths = deathRecoveryService.getDeathRecords(playerUuid, 100)
                    deaths.find { it.id.startsWith(deathId) }?.id
                } else {
                    deathId
                }

                if (fullDeathId == null) {
                    messages.send(sender, "death-record-not-found", "death_id" to deathId)
                    return true
                }

                val result = deathRecoveryService.restoreDeathInventory(targetPlayer, fullDeathId)

                result.fold(
                    onSuccess = {
                        messages.send(sender, "death-inventory-restored",
                            "player" to targetPlayer.name,
                            "death_id" to fullDeathId.substring(0, 8)
                        )
                        messages.send(targetPlayer, "your-death-inventory-restored")
                    },
                    onFailure = { error ->
                        messages.send(sender, "death-restore-failed",
                            "player" to targetPlayer.name,
                            "error" to (error.message ?: "Unknown error")
                        )
                    }
                )
            }

            "view" -> {
                if (deathId == null) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv deaths $playerName view <death-id>")
                    return true
                }

                // Expand short death IDs
                val fullDeathId = if (deathId.length < 36) {
                    val deaths = deathRecoveryService.getDeathRecords(playerUuid, 100)
                    deaths.find { it.id.startsWith(deathId) }?.id
                } else {
                    deathId
                }

                if (fullDeathId == null) {
                    messages.send(sender, "death-record-not-found", "death_id" to deathId)
                    return true
                }

                val deathRecord = deathRecoveryService.getDeathRecord(fullDeathId)
                if (deathRecord == null) {
                    messages.send(sender, "death-record-not-found", "death_id" to deathId)
                    return true
                }

                // Display detailed death info
                messages.send(sender, "death-record-detail-header", "player" to displayName)
                messages.send(sender, "death-record-detail-id", "id" to deathRecord.id)
                messages.send(sender, "death-record-detail-time",
                    "time" to dateFormatter.format(deathRecord.timestamp),
                    "relative_time" to deathRecord.getRelativeTimeDescription()
                )
                messages.send(sender, "death-record-detail-location", "location" to deathRecord.getLocationString())
                messages.send(sender, "death-record-detail-cause", "cause" to deathRecord.getDeathDescription())
                messages.send(sender, "death-record-detail-items", "items" to deathRecord.getItemSummary())
            }

            else -> {
                // List deaths
                val deaths = deathRecoveryService.getDeathRecords(playerUuid, 10)

                if (deaths.isEmpty()) {
                    messages.send(sender, "no-deaths-found", "player" to displayName)
                    return true
                }

                messages.send(sender, "death-history-header",
                    "player" to displayName,
                    "count" to deaths.size.toString()
                )

                deaths.forEachIndexed { index, death ->
                    val formattedTime = dateFormatter.format(death.timestamp)
                    messages.send(sender, "death-history-entry",
                        "index" to (index + 1).toString(),
                        "id" to death.id.substring(0, 8),
                        "full_id" to death.id,
                        "location" to death.getLocationString(),
                        "cause" to death.getDeathDescription(),
                        "time" to formattedTime,
                        "relative_time" to death.getRelativeTimeDescription(),
                        "items" to death.getItemSummary()
                    )
                }

                messages.send(sender, "death-history-footer",
                    "restore_usage" to "/xinv deaths $displayName restore <death-id>",
                    "view_usage" to "/xinv deaths $displayName view <death-id>"
                )
            }
        }

        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> Bukkit.getOnlinePlayers()
                .map { it.name }
                .filter { it.lowercase().startsWith(args[0].lowercase()) }
            2 -> listOf("restore", "view")
                .filter { it.startsWith(args[1].lowercase()) }
            3 -> {
                // Could potentially load recent death IDs here
                listOf("<death-id>")
            }
            else -> emptyList()
        }
    }
}

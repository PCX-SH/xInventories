package sh.pcx.xinventories.internal.command.subcommand

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.GameMode
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.menu.BulkOperationsGUI
import sh.pcx.xinventories.internal.gui.menu.DeathRecoveryGUI
import sh.pcx.xinventories.internal.gui.menu.GroupBrowserGUI
import sh.pcx.xinventories.internal.gui.menu.InventoryComparisonGUI
import sh.pcx.xinventories.internal.gui.menu.InventoryEditorGUI
import sh.pcx.xinventories.internal.gui.menu.InventorySearchGUI
import sh.pcx.xinventories.internal.gui.menu.MainMenuGUI
import sh.pcx.xinventories.internal.gui.menu.PlayerLookupGUI
import sh.pcx.xinventories.internal.gui.menu.SearchResultsGUI
import sh.pcx.xinventories.internal.gui.menu.SearchScopeSelectGUI
import sh.pcx.xinventories.internal.gui.menu.SearchType
import sh.pcx.xinventories.internal.gui.menu.TemplateManagerGUI
import sh.pcx.xinventories.internal.gui.menu.VersionBrowserGUI

/**
 * Opens the admin GUI.
 *
 * Usage:
 * - /xinv gui - Opens main admin menu
 * - /xinv gui compare <player1> <player2> - Opens inventory comparison GUI
 * - /xinv gui templates - Opens template manager GUI
 * - /xinv gui groups - Opens group browser GUI
 * - /xinv gui bulk - Opens bulk operations GUI
 * - /xinv gui search - Opens inventory search GUI
 * - /xinv gui search name <text> - Search by item name
 * - /xinv gui lookup [search] - Opens player lookup GUI
 * - /xinv gui editor <player> [group] - Opens inventory editor GUI
 * - /xinv gui versions <player> [group] - Opens version browser GUI
 * - /xinv gui deaths <player> - Opens death recovery GUI
 */
class GUICommand : Subcommand {

    override val name = "gui"
    override val aliases = listOf("menu", "admin")
    override val permission = "xinventories.command.gui"
    override val usage = "/xinv gui [compare|templates|groups|bulk|search|lookup|editor|versions|deaths]"
    override val description = "Open admin menu"

    private val subcommands = listOf("compare", "templates", "groups", "bulk", "search", "lookup", "editor", "versions", "deaths")

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be used by players.")
            return true
        }

        if (args.isEmpty()) {
            MainMenuGUI(plugin).open(sender)
            return true
        }

        when (args[0].lowercase()) {
            "compare" -> handleCompare(plugin, sender, args.drop(1).toTypedArray())
            "templates" -> handleTemplates(plugin, sender)
            "groups" -> handleGroups(plugin, sender)
            "bulk" -> handleBulk(plugin, sender)
            "search" -> handleSearch(plugin, sender, args.drop(1).toTypedArray())
            "lookup" -> handleLookup(plugin, sender, args.drop(1).toTypedArray())
            "editor" -> handleEditor(plugin, sender, args.drop(1).toTypedArray())
            "versions" -> handleVersions(plugin, sender, args.drop(1).toTypedArray())
            "deaths" -> handleDeaths(plugin, sender, args.drop(1).toTypedArray())
            else -> {
                sender.sendMessage(Component.text("Unknown subcommand: ${args[0]}", NamedTextColor.RED))
                sender.sendMessage(Component.text("Usage: $usage", NamedTextColor.GRAY))
                sender.sendMessage(Component.text("Available: ${subcommands.joinToString(", ")}", NamedTextColor.GRAY))
            }
        }

        return true
    }

    private fun handleTemplates(plugin: XInventories, sender: Player) {
        if (!sender.hasPermission("xinventories.admin.templates")) {
            sender.sendMessage(Component.text("You don't have permission to manage templates.", NamedTextColor.RED))
            return
        }
        TemplateManagerGUI(plugin).open(sender)
    }

    private fun handleGroups(plugin: XInventories, sender: Player) {
        if (!sender.hasPermission("xinventories.admin.groups")) {
            sender.sendMessage(Component.text("You don't have permission to browse groups.", NamedTextColor.RED))
            return
        }
        GroupBrowserGUI(plugin).open(sender)
    }

    private fun handleBulk(plugin: XInventories, sender: Player) {
        if (!sender.hasPermission("xinventories.admin.bulk")) {
            sender.sendMessage(Component.text("You don't have permission to use bulk operations.", NamedTextColor.RED))
            return
        }
        BulkOperationsGUI(plugin).open(sender)
    }

    private fun handleSearch(plugin: XInventories, sender: Player, args: Array<String>) {
        if (!sender.hasPermission("xinventories.admin.search")) {
            sender.sendMessage(Component.text("You don't have permission to search inventories.", NamedTextColor.RED))
            return
        }

        if (args.isEmpty()) {
            InventorySearchGUI(plugin).open(sender)
            return
        }

        when (args[0].lowercase()) {
            "name" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("Usage: /xinv gui search name <text>", NamedTextColor.RED))
                    return
                }
                val query = args.drop(1).joinToString(" ")
                SearchScopeSelectGUI(plugin, SearchType.NAME, nameQuery = query).open(sender)
            }
            "material" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("Usage: /xinv gui search material <material>", NamedTextColor.RED))
                    return
                }
                val materialName = args[1].uppercase()
                val material = Material.matchMaterial(materialName)
                if (material == null) {
                    sender.sendMessage(Component.text("Unknown material: $materialName", NamedTextColor.RED))
                    return
                }
                SearchScopeSelectGUI(plugin, SearchType.MATERIAL, material = material).open(sender)
            }
            "enchantment" -> {
                if (args.size < 2) {
                    sender.sendMessage(Component.text("Usage: /xinv gui search enchantment <enchantment>", NamedTextColor.RED))
                    return
                }
                val enchantName = args[1].lowercase()
                val enchantment = Enchantment.values().find {
                    it.key.key.equals(enchantName, ignoreCase = true)
                }
                if (enchantment == null) {
                    sender.sendMessage(Component.text("Unknown enchantment: $enchantName", NamedTextColor.RED))
                    return
                }
                SearchScopeSelectGUI(plugin, SearchType.ENCHANTMENT, enchantment = enchantment).open(sender)
            }
            else -> {
                sender.sendMessage(Component.text("Unknown search type: ${args[0]}", NamedTextColor.RED))
                sender.sendMessage(Component.text("Available: name, material, enchantment", NamedTextColor.GRAY))
            }
        }
    }

    private fun handleLookup(plugin: XInventories, sender: Player, args: Array<String>) {
        val searchQuery = if (args.isNotEmpty()) args.joinToString(" ") else ""
        val gui = PlayerLookupGUI(plugin, searchQuery)
        gui.setupForAdmin(sender.uniqueId)
        gui.open(sender)
    }

    private fun handleEditor(plugin: XInventories, sender: Player, args: Array<String>) {
        if (!sender.hasPermission("xinventories.admin.edit")) {
            sender.sendMessage(Component.text("You don't have permission to edit inventories.", NamedTextColor.RED))
            return
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /xinv gui editor <player> [group]", NamedTextColor.RED))
            return
        }

        val playerName = args[0]
        val player = Bukkit.getOfflinePlayer(playerName)

        if (!player.hasPlayedBefore() && !player.isOnline) {
            sender.sendMessage(Component.text("Player '$playerName' not found.", NamedTextColor.RED))
            return
        }

        val groupName = if (args.size >= 2) {
            args[1]
        } else {
            plugin.serviceManager.groupService.getGroupForPlayer(sender).name
        }

        if (plugin.serviceManager.groupService.getGroup(groupName) == null) {
            sender.sendMessage(Component.text("Group '$groupName' not found.", NamedTextColor.RED))
            return
        }

        InventoryEditorGUI(
            plugin,
            player.uniqueId,
            player.name ?: playerName,
            groupName,
            GameMode.SURVIVAL
        ).open(sender)
    }

    private fun handleVersions(plugin: XInventories, sender: Player, args: Array<String>) {
        if (!sender.hasPermission("xinventories.admin.versions")) {
            sender.sendMessage(Component.text("You don't have permission to view versions.", NamedTextColor.RED))
            return
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /xinv gui versions <player> [group]", NamedTextColor.RED))
            return
        }

        val playerName = args[0]
        val player = Bukkit.getOfflinePlayer(playerName)

        if (!player.hasPlayedBefore() && !player.isOnline) {
            sender.sendMessage(Component.text("Player '$playerName' not found.", NamedTextColor.RED))
            return
        }

        val groupFilter = if (args.size >= 2) args[1] else null

        VersionBrowserGUI(
            plugin,
            player.uniqueId,
            player.name ?: playerName,
            groupFilter
        ).open(sender)
    }

    private fun handleDeaths(plugin: XInventories, sender: Player, args: Array<String>) {
        if (!sender.hasPermission("xinventories.admin.deaths")) {
            sender.sendMessage(Component.text("You don't have permission to view death records.", NamedTextColor.RED))
            return
        }

        if (args.isEmpty()) {
            sender.sendMessage(Component.text("Usage: /xinv gui deaths <player>", NamedTextColor.RED))
            return
        }

        val playerName = args[0]
        val player = Bukkit.getOfflinePlayer(playerName)

        if (!player.hasPlayedBefore() && !player.isOnline) {
            sender.sendMessage(Component.text("Player '$playerName' not found.", NamedTextColor.RED))
            return
        }

        DeathRecoveryGUI(
            plugin,
            player.uniqueId,
            player.name ?: playerName
        ).open(sender)
    }

    private suspend fun handleCompare(plugin: XInventories, sender: Player, args: Array<String>) {
        if (!sender.hasPermission("xinventories.admin.compare")) {
            sender.sendMessage(Component.text("You don't have permission to compare inventories.", NamedTextColor.RED))
            return
        }

        if (args.size < 2) {
            sender.sendMessage(Component.text("Usage: /xinv gui compare <player1> <player2>", NamedTextColor.RED))
            return
        }

        val player1Name = args[0]
        val player2Name = args[1]

        // Resolve players
        val player1 = Bukkit.getOfflinePlayer(player1Name)
        val player2 = Bukkit.getOfflinePlayer(player2Name)

        if (!player1.hasPlayedBefore() && !player1.isOnline) {
            sender.sendMessage(Component.text("Player '$player1Name' not found.", NamedTextColor.RED))
            return
        }

        if (!player2.hasPlayedBefore() && !player2.isOnline) {
            sender.sendMessage(Component.text("Player '$player2Name' not found.", NamedTextColor.RED))
            return
        }

        // Get group - use sender's current group or specified group
        val groupName = if (args.size >= 3) {
            args[2]
        } else {
            plugin.serviceManager.groupService.getGroupForPlayer(sender).name
        }

        // Verify group exists
        if (plugin.serviceManager.groupService.getGroup(groupName) == null) {
            sender.sendMessage(Component.text("Group '$groupName' not found.", NamedTextColor.RED))
            return
        }

        // Open comparison GUI
        InventoryComparisonGUI(
            plugin,
            player1.uniqueId,
            player1.name ?: player1Name,
            player2.uniqueId,
            player2.name ?: player2Name,
            groupName
        ).open(sender)
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return when (args.size) {
            1 -> subcommands.filter { it.startsWith(args[0], ignoreCase = true) }
            2 -> when (args[0].lowercase()) {
                "compare", "editor", "versions", "deaths" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                "search" -> listOf("name", "material", "enchantment")
                    .filter { it.startsWith(args[1], ignoreCase = true) }
                else -> emptyList()
            }
            3 -> when (args[0].lowercase()) {
                "compare" -> Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                "editor", "versions" -> plugin.serviceManager.groupService.getAllGroups()
                    .map { it.name }
                    .filter { it.startsWith(args[2], ignoreCase = true) }
                "search" -> when (args[1].lowercase()) {
                    "material" -> Material.entries
                        .filter { it.isItem && !it.isAir }
                        .map { it.name.lowercase() }
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                        .take(20)
                    "enchantment" -> Enchantment.values()
                        .map { it.key.key }
                        .filter { it.startsWith(args[2], ignoreCase = true) }
                    else -> emptyList()
                }
                else -> emptyList()
            }
            4 -> if (args[0].equals("compare", ignoreCase = true)) {
                plugin.serviceManager.groupService.getAllGroups()
                    .map { it.name }
                    .filter { it.startsWith(args[3], ignoreCase = true) }
            } else emptyList()
            else -> emptyList()
        }
    }
}

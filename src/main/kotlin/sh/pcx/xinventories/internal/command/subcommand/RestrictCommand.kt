package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.model.RestrictionConfig
import sh.pcx.xinventories.internal.model.RestrictionMode
import sh.pcx.xinventories.internal.model.ItemPattern
import org.bukkit.Material
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Command for managing item restrictions.
 *
 * Usage:
 * - /xinv restrict <group> add <pattern> - Add pattern to blacklist/whitelist
 * - /xinv restrict <group> remove <pattern> - Remove pattern from list
 * - /xinv restrict <group> list - Show current restrictions
 * - /xinv restrict <group> mode <blacklist|whitelist|none> - Set restriction mode
 * - /xinv restrict <group> test - Test if held item is restricted
 * - /xinv restrict <group> strip <add|remove> <pattern> - Manage strip-on-exit patterns
 */
class RestrictCommand : Subcommand {

    override val name = "restrict"
    override val aliases = listOf("restriction", "restrictions")
    override val permission = "xinventories.command.restrict"
    override val usage = "/xinv restrict <group> <add|remove|list|mode|test|strip> [args...]"
    override val description = "Manage item restrictions for groups"

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService
        val groupService = plugin.serviceManager.groupService
        val restrictionService = plugin.serviceManager.restrictionService

        if (args.size < 2) {
            messages.send(sender, "invalid-syntax", "usage" to usage)
            return true
        }

        val groupName = args[0]
        val action = args[1].lowercase()

        // Verify group exists
        val group = groupService.getGroup(groupName)
        if (group == null) {
            messages.send(sender, "group-not-found", "group" to groupName)
            return true
        }

        // Get current restriction config (or default)
        val currentConfig = group.restrictions ?: RestrictionConfig.disabled()

        when (action) {
            "add" -> {
                if (args.size < 3) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv restrict <group> add <pattern>")
                    return true
                }

                val pattern = args.drop(2).joinToString(" ")

                // Validate the pattern
                val validationResult = restrictionService.validatePattern(pattern)
                if (validationResult.isFailure) {
                    messages.send(sender, "invalid-pattern",
                        "pattern" to pattern,
                        "error" to (validationResult.exceptionOrNull()?.message ?: "Unknown error")
                    )
                    return true
                }

                // Add to appropriate list based on mode
                val updatedConfig = when (currentConfig.mode) {
                    RestrictionMode.BLACKLIST -> currentConfig.withBlacklistPattern(pattern)
                    RestrictionMode.WHITELIST -> currentConfig.withWhitelistPattern(pattern)
                    RestrictionMode.NONE -> {
                        messages.send(sender, "restriction-mode-none")
                        return true
                    }
                }

                // Update group config
                group.restrictions = updatedConfig
                groupService.saveToConfig()

                messages.send(sender, "restriction-added",
                    "pattern" to pattern,
                    "group" to groupName,
                    "mode" to currentConfig.mode.name.lowercase()
                )
            }

            "remove" -> {
                if (args.size < 3) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv restrict <group> remove <pattern>")
                    return true
                }

                val pattern = args.drop(2).joinToString(" ")

                val updatedConfig = when (currentConfig.mode) {
                    RestrictionMode.BLACKLIST -> currentConfig.withoutBlacklistPattern(pattern)
                    RestrictionMode.WHITELIST -> currentConfig.withoutWhitelistPattern(pattern)
                    RestrictionMode.NONE -> currentConfig
                }

                val wasRemoved = updatedConfig != currentConfig

                if (!wasRemoved) {
                    messages.send(sender, "restriction-not-found", "pattern" to pattern)
                    return true
                }

                group.restrictions = updatedConfig
                groupService.saveToConfig()

                messages.send(sender, "restriction-removed",
                    "pattern" to pattern,
                    "group" to groupName
                )
            }

            "list" -> {
                messages.sendRaw(sender, "restriction-list-header", "group" to groupName)
                messages.sendRaw(sender, "restriction-list-mode", "mode" to currentConfig.mode.name)
                messages.sendRaw(sender, "restriction-list-action", "action" to currentConfig.onViolation.name)

                val patterns = currentConfig.getActivePatterns()
                if (patterns.isEmpty()) {
                    messages.sendRaw(sender, "restriction-list-empty")
                } else {
                    messages.sendRaw(sender, "restriction-list-patterns-header")
                    patterns.forEach { pattern ->
                        val parsed = restrictionService.parsePattern(pattern)
                        val status = if (parsed is ItemPattern.Invalid) " (invalid)" else ""
                        messages.sendRaw(sender, "restriction-list-pattern-entry",
                            "pattern" to pattern,
                            "status" to status
                        )
                    }
                }

                if (currentConfig.stripOnExit.isNotEmpty()) {
                    messages.sendRaw(sender, "restriction-list-strip-header")
                    currentConfig.stripOnExit.forEach { pattern ->
                        messages.sendRaw(sender, "restriction-list-pattern-entry",
                            "pattern" to pattern,
                            "status" to ""
                        )
                    }
                }
            }

            "mode" -> {
                if (args.size < 3) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv restrict <group> mode <blacklist|whitelist|none>")
                    return true
                }

                val newMode = when (args[2].lowercase()) {
                    "blacklist", "black" -> RestrictionMode.BLACKLIST
                    "whitelist", "white" -> RestrictionMode.WHITELIST
                    "none", "off", "disable" -> RestrictionMode.NONE
                    else -> {
                        messages.send(sender, "invalid-mode", "mode" to args[2])
                        return true
                    }
                }

                group.restrictions = currentConfig.copy(mode = newMode)
                groupService.saveToConfig()

                messages.send(sender, "restriction-mode-set",
                    "group" to groupName,
                    "mode" to newMode.name
                )
            }

            "test" -> {
                if (sender !is Player) {
                    messages.send(sender, "player-only")
                    return true
                }

                val heldItem = sender.inventory.itemInMainHand
                if (heldItem.type == Material.AIR) {
                    messages.send(sender, "hold-item-to-test")
                    return true
                }

                val (isRestricted, pattern) = restrictionService.testItem(heldItem, currentConfig)

                if (isRestricted) {
                    messages.send(sender, "item-is-restricted",
                        "item" to heldItem.type.name,
                        "pattern" to (pattern ?: "unknown"),
                        "group" to groupName
                    )
                } else {
                    messages.send(sender, "item-not-restricted",
                        "item" to heldItem.type.name,
                        "group" to groupName
                    )
                }
            }

            "strip" -> {
                if (args.size < 4) {
                    messages.send(sender, "invalid-syntax", "usage" to "/xinv restrict <group> strip <add|remove> <pattern>")
                    return true
                }

                val stripAction = args[2].lowercase()
                val pattern = args.drop(3).joinToString(" ")

                when (stripAction) {
                    "add" -> {
                        val validationResult = restrictionService.validatePattern(pattern)
                        if (validationResult.isFailure) {
                            messages.send(sender, "invalid-pattern",
                                "pattern" to pattern,
                                "error" to (validationResult.exceptionOrNull()?.message ?: "Unknown error")
                            )
                            return true
                        }

                        group.restrictions = currentConfig.withStripOnExitPattern(pattern)
                        groupService.saveToConfig()

                        messages.send(sender, "strip-pattern-added",
                            "pattern" to pattern,
                            "group" to groupName
                        )
                    }
                    "remove" -> {
                        group.restrictions = currentConfig.withoutStripOnExitPattern(pattern)
                        groupService.saveToConfig()

                        messages.send(sender, "strip-pattern-removed",
                            "pattern" to pattern,
                            "group" to groupName
                        )
                    }
                    else -> {
                        messages.send(sender, "invalid-syntax", "usage" to "/xinv restrict <group> strip <add|remove> <pattern>")
                    }
                }
            }

            else -> {
                messages.send(sender, "invalid-syntax", "usage" to usage)
            }
        }

        return true
    }

    override fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> {
        val groupService = plugin.serviceManager.groupService

        return when (args.size) {
            1 -> {
                // Group names
                groupService.getAllGroups()
                    .map { it.name }
                    .filter { it.lowercase().startsWith(args[0].lowercase()) }
            }
            2 -> {
                // Actions
                listOf("add", "remove", "list", "mode", "test", "strip")
                    .filter { it.startsWith(args[1].lowercase()) }
            }
            3 -> when (args[1].lowercase()) {
                "mode" -> listOf("blacklist", "whitelist", "none")
                    .filter { it.startsWith(args[2].lowercase()) }
                "strip" -> listOf("add", "remove")
                    .filter { it.startsWith(args[2].lowercase()) }
                "add", "remove" -> {
                    // Suggest common materials
                    Material.entries
                        .take(50)
                        .map { it.name }
                        .filter { it.lowercase().startsWith(args[2].lowercase()) }
                }
                else -> emptyList()
            }
            4 -> when (args[1].lowercase()) {
                "strip" -> {
                    if (args[2].lowercase() in listOf("add", "remove")) {
                        Material.entries
                            .take(50)
                            .map { it.name }
                            .filter { it.lowercase().startsWith(args[3].lowercase()) }
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }
            else -> emptyList()
        }
    }
}

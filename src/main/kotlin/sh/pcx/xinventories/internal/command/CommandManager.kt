package sh.pcx.xinventories.internal.command

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.command.subcommand.*
import org.bukkit.command.PluginCommand

/**
 * Manages command registration and subcommands.
 */
class CommandManager(private val plugin: PluginContext) {

    private val subcommands = mutableMapOf<String, Subcommand>()
    private val aliases = mutableMapOf<String, String>()

    fun initialize() {
        // Register all subcommands
        registerSubcommand(ReloadCommand())
        registerSubcommand(SaveCommand())
        registerSubcommand(LoadCommand())
        registerSubcommand(GroupCommand())
        registerSubcommand(WorldCommand())
        registerSubcommand(PatternCommand())
        registerSubcommand(CacheCommand())
        registerSubcommand(BackupCommand())
        registerSubcommand(ConvertCommand())
        registerSubcommand(DebugCommand())
        registerSubcommand(GUICommand())
        registerSubcommand(SyncCommand())

        // Data Foundation commands
        registerSubcommand(HistoryCommand())
        registerSubcommand(RestoreCommand())
        registerSubcommand(SnapshotCommand())
        registerSubcommand(DeathsCommand())

        // Content Control commands
        registerSubcommand(TemplateCommand())
        registerSubcommand(RestrictCommand())
        registerSubcommand(VaultCommand())
        registerSubcommand(ResetCommand())

        // Advanced Groups commands
        registerSubcommand(ConditionsCommand())
        registerSubcommand(WhoamiCommand())
        registerSubcommand(LockCommand())
        registerSubcommand(UnlockCommand())

        // External Integrations commands
        registerSubcommand(BalanceCommand(
            plugin.serviceManager.economyService,
            plugin.serviceManager.groupService
        ))
        registerSubcommand(ImportCommand(plugin.serviceManager.importService))

        // Quality of Life commands
        registerSubcommand(MergeCommand())
        registerSubcommand(ExportCommand())
        registerSubcommand(ImportJsonCommand())

        // Admin Tools commands
        registerSubcommand(BulkCommand())
        registerSubcommand(AuditCommand())
        registerSubcommand(StatsCommand())

        // Advanced Features commands
        registerSubcommand(ExpirationCommand())
        registerSubcommand(TempGroupCommand())

        // Register main command executor
        val command: PluginCommand? = plugin.plugin.getCommand("xinventories")
        if (command != null) {
            val executor = XInventoriesCommand(plugin, this)
            command.setExecutor(executor)
            command.tabCompleter = executor
        } else {
            plugin.plugin.logger.severe("Failed to register /xinventories command!")
        }
    }

    private fun registerSubcommand(subcommand: Subcommand) {
        subcommands[subcommand.name.lowercase()] = subcommand

        // Register aliases
        subcommand.aliases.forEach { alias ->
            aliases[alias.lowercase()] = subcommand.name.lowercase()
        }
    }

    fun getSubcommand(name: String): Subcommand? {
        val key = name.lowercase()
        return subcommands[key] ?: aliases[key]?.let { subcommands[it] }
    }

    fun getAllSubcommands(): Collection<Subcommand> = subcommands.values

    fun getSubcommandNames(): Set<String> = subcommands.keys

    fun shutdown() {
        subcommands.clear()
        aliases.clear()
    }
}

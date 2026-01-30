package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.PluginContext
import org.bukkit.command.CommandSender

/**
 * Base interface for all subcommands.
 */
interface Subcommand {

    /**
     * The name of the subcommand.
     */
    val name: String

    /**
     * Aliases for this subcommand.
     */
    val aliases: List<String> get() = emptyList()

    /**
     * Permission required to use this subcommand.
     */
    val permission: String

    /**
     * Usage string for this subcommand.
     */
    val usage: String

    /**
     * Description of this subcommand.
     */
    val description: String

    /**
     * Whether this command can only be run by players.
     */
    val playerOnly: Boolean get() = false

    /**
     * Executes the subcommand.
     *
     * @param plugin The plugin instance
     * @param sender The command sender
     * @param args The arguments (excluding the subcommand name)
     * @return true if the command was handled
     */
    suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean

    /**
     * Provides tab completions for this subcommand.
     *
     * @param plugin The plugin instance
     * @param sender The command sender
     * @param args The arguments (excluding the subcommand name)
     * @return List of completions
     */
    fun tabComplete(plugin: PluginContext, sender: CommandSender, args: Array<String>): List<String> = emptyList()

    /**
     * Checks if the sender has permission to use this subcommand.
     */
    fun hasPermission(sender: CommandSender): Boolean = sender.hasPermission(permission)
}

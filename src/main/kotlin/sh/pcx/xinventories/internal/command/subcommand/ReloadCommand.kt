package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.PluginContext
import org.bukkit.command.CommandSender

/**
 * Reloads the plugin configuration.
 */
class ReloadCommand : Subcommand {

    override val name = "reload"
    override val permission = "xinventories.command.reload"
    override val usage = "/xinv reload"
    override val description = "Reload configuration files"

    override suspend fun execute(plugin: PluginContext, sender: CommandSender, args: Array<String>): Boolean {
        val messages = plugin.serviceManager.messageService

        val success = plugin.reload()

        if (success) {
            messages.send(sender, "reloaded")
        } else {
            messages.send(sender, "reload-failed")
        }

        return true
    }
}

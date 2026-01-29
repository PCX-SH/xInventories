package sh.pcx.xinventories.internal.command.subcommand

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.gui.menu.MainMenuGUI
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Opens the admin GUI.
 */
class GUICommand : Subcommand {

    override val name = "gui"
    override val aliases = listOf("menu", "admin")
    override val permission = "xinventories.command.gui"
    override val usage = "/xinv gui"
    override val description = "Open admin menu"

    override suspend fun execute(plugin: XInventories, sender: CommandSender, args: Array<String>): Boolean {
        if (sender !is Player) {
            sender.sendMessage("This command can only be used by players.")
            return true
        }

        MainMenuGUI(plugin).open(sender)
        return true
    }

    override fun tabComplete(plugin: XInventories, sender: CommandSender, args: Array<String>): List<String> {
        return emptyList()
    }
}

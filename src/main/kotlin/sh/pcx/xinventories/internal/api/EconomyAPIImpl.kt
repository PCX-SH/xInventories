package sh.pcx.xinventories.internal.api

import org.bukkit.entity.Player
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.EconomyAPI
import java.util.UUID

/**
 * Implementation of the EconomyAPI.
 * Adapts internal EconomyService to the public API interface.
 */
class EconomyAPIImpl(private val plugin: PluginContext) : EconomyAPI {

    private val economyService get() = plugin.serviceManager.economyService

    override fun isEnabled(): Boolean {
        return economyService.isEnabled()
    }

    override fun isSeparateByGroup(): Boolean {
        return economyService.isSeparateByGroup()
    }

    override fun getBalance(player: Player, group: String?): Double {
        return economyService.getBalance(player, group)
    }

    override fun getBalance(uuid: UUID, group: String): Double {
        return economyService.getBalance(uuid, group)
    }

    override fun getAllBalances(uuid: UUID): Map<String, Double> {
        return economyService.getAllBalances(uuid)
    }

    override fun setBalance(player: Player, amount: Double, group: String?): Boolean {
        return economyService.setBalance(player, amount, group)
    }

    override fun setBalance(uuid: UUID, group: String, amount: Double): Boolean {
        return economyService.setBalance(uuid, group, amount)
    }

    override fun deposit(player: Player, amount: Double, group: String?): Boolean {
        return economyService.deposit(player, amount, group)
    }

    override fun withdraw(player: Player, amount: Double, group: String?): Boolean {
        return economyService.withdraw(player, amount, group)
    }

    override fun transfer(player: Player, fromGroup: String, toGroup: String, amount: Double): Boolean {
        return economyService.transfer(player, fromGroup, toGroup, amount)
    }

    override fun transfer(uuid: UUID, fromGroup: String, toGroup: String, amount: Double): Boolean {
        return economyService.transfer(uuid, fromGroup, toGroup, amount)
    }

    override fun has(player: Player, amount: Double, group: String?): Boolean {
        return economyService.has(player, amount, group)
    }

    override fun hasGroupEconomy(groupName: String): Boolean {
        return economyService.hasGroupEconomy(groupName)
    }
}

package sh.pcx.xinventories.internal.api

import org.bukkit.entity.Player
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.ConditionAPI
import sh.pcx.xinventories.internal.service.ConditionEvaluationResult

/**
 * Implementation of the ConditionAPI.
 * Adapts internal ConditionEvaluator to the public API interface.
 */
class ConditionAPIImpl(private val plugin: PluginContext) : ConditionAPI {

    private val conditionEvaluator get() = plugin.serviceManager.conditionEvaluator
    private val groupService get() = plugin.serviceManager.groupService

    override fun evaluateConditions(player: Player, groupName: String): ConditionEvaluationResult? {
        val group = groupService.getGroup(groupName) ?: return null
        return conditionEvaluator.evaluateConditions(player, group)
    }

    override fun getActiveConditionsForPlayer(player: Player): Map<String, ConditionEvaluationResult> {
        return conditionEvaluator.getActiveConditionsForPlayer(player)
    }

    override fun getMatchReason(player: Player, groupName: String): String? {
        val group = groupService.getGroup(groupName) ?: return null
        return conditionEvaluator.getMatchReason(player, group)
    }

    override fun invalidateCache(player: Player) {
        conditionEvaluator.invalidateCache(player.uniqueId)
    }

    override fun isPlaceholderAPIAvailable(): Boolean {
        return conditionEvaluator.placeholderIntegration.isAvailable()
    }

    override fun parsePlaceholder(player: Player, placeholder: String): String {
        return if (isPlaceholderAPIAvailable()) {
            conditionEvaluator.placeholderIntegration.parsePlaceholders(player, placeholder)
        } else {
            placeholder
        }
    }
}

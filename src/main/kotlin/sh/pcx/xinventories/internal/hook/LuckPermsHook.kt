package sh.pcx.xinventories.internal.hook

import net.luckperms.api.LuckPerms
import net.luckperms.api.context.ContextCalculator
import net.luckperms.api.context.ContextConsumer
import net.luckperms.api.context.ContextSet
import net.luckperms.api.context.ImmutableContextSet
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.util.Logging

/**
 * Integration with LuckPerms for context-based permissions.
 *
 * Exposes the current xInventories group as a LuckPerms context,
 * allowing permission nodes to be applied conditionally based on
 * which inventory group a player is currently in.
 *
 * Context key: `xinventories:group`
 * Context value: current group name (e.g., "survival", "creative")
 *
 * Usage in LuckPerms:
 * ```
 * /lp user <player> permission set some.permission true xinventories:group=creative
 * ```
 */
class LuckPermsHook(private val plugin: PluginContext) {

    private var luckPerms: LuckPerms? = null
    private var contextCalculator: XInventoriesContextCalculator? = null
    private var initialized = false

    companion object {
        /**
         * The context key used for the xInventories group.
         */
        const val CONTEXT_KEY = "xinventories:group"
    }

    /**
     * Initializes the LuckPerms hook.
     *
     * @return true if initialization was successful
     */
    fun initialize(): Boolean {
        if (initialized) {
            return true
        }

        try {
            // Get LuckPerms API from the service provider
            val provider = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)
            if (provider == null) {
                Logging.debug { "LuckPerms service provider not found" }
                return false
            }

            luckPerms = provider.provider

            // Register our context calculator
            contextCalculator = XInventoriesContextCalculator(plugin)
            contextCalculator?.let { calculator ->
                luckPerms?.contextManager?.registerCalculator(calculator)
            }

            initialized = true
            Logging.info("LuckPerms context hook registered (context key: $CONTEXT_KEY)")
            return true
        } catch (e: NoClassDefFoundError) {
            // LuckPerms API classes not available
            Logging.debug { "LuckPerms API not available: ${e.message}" }
            return false
        } catch (e: Exception) {
            Logging.warning("Failed to initialize LuckPerms hook: ${e.message}")
            return false
        }
    }

    /**
     * Unregisters the LuckPerms hook.
     */
    fun unregister() {
        if (!initialized) return

        try {
            contextCalculator?.let { calculator ->
                luckPerms?.contextManager?.unregisterCalculator(calculator)
            }
            Logging.debug { "LuckPerms context hook unregistered" }
        } catch (e: Exception) {
            Logging.debug { "Error unregistering LuckPerms hook: ${e.message}" }
        }

        contextCalculator = null
        luckPerms = null
        initialized = false
    }

    /**
     * Checks if the hook is initialized and active.
     */
    fun isInitialized(): Boolean = initialized

    /**
     * Signals that a player's context has changed (e.g., group change).
     * This triggers LuckPerms to recalculate the player's permissions.
     *
     * @param player The player whose context changed
     */
    fun signalContextUpdate(player: Player) {
        if (!initialized) return

        try {
            luckPerms?.contextManager?.signalContextUpdate(player)
            Logging.debug { "Signaled LuckPerms context update for ${player.name}" }
        } catch (e: Exception) {
            Logging.debug { "Error signaling context update: ${e.message}" }
        }
    }

    /**
     * Gets the LuckPerms API instance, if available.
     */
    fun getLuckPerms(): LuckPerms? = luckPerms

    /**
     * Context calculator that provides the current xInventories group as a context.
     */
    private class XInventoriesContextCalculator(
        private val plugin: PluginContext
    ) : ContextCalculator<Player> {

        override fun calculate(target: Player, consumer: ContextConsumer) {
            try {
                // Get the player's current group
                val currentGroup = plugin.serviceManager.inventoryService.getCurrentGroup(target)
                    ?: plugin.serviceManager.groupService.getGroupForWorld(target.world).name

                consumer.accept(CONTEXT_KEY, currentGroup)
            } catch (e: Exception) {
                Logging.debug { "Error calculating context for ${target.name}: ${e.message}" }
            }
        }

        override fun estimatePotentialContexts(): ContextSet {
            // Return all possible group names as potential context values
            return try {
                val builder = ImmutableContextSet.builder()
                plugin.serviceManager.groupService.getAllGroups().forEach { group ->
                    builder.add(CONTEXT_KEY, group.name)
                }
                builder.build()
            } catch (e: Exception) {
                ImmutableContextSet.empty()
            }
        }
    }
}

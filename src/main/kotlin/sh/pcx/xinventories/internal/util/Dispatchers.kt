package sh.pcx.xinventories.internal.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.bukkit.plugin.Plugin
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * Custom coroutine dispatcher that runs tasks on the Bukkit main thread.
 *
 * This dispatcher handles plugin shutdown gracefully by checking if the plugin
 * is still enabled before scheduling tasks. During shutdown, tasks are either
 * executed directly (if on main thread) or silently dropped (if not).
 */
class BukkitMainThreadDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (plugin.server.isPrimaryThread) {
            // Already on main thread, run directly
            block.run()
        } else if (plugin.isEnabled) {
            // Plugin is enabled, schedule on main thread
            plugin.server.scheduler.runTask(plugin, block)
        }
        // If plugin is disabled and not on main thread, silently drop the task
        // This prevents IllegalPluginAccessException during shutdown
    }
}

/**
 * Custom coroutine dispatcher for async storage operations.
 */
class AsyncStorageDispatcher(poolSize: Int) : CoroutineDispatcher() {
    private val executor = Executors.newFixedThreadPool(poolSize) { runnable ->
        Thread(runnable, "xInventories-Storage").apply {
            isDaemon = true
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        executor.execute(block)
    }

    fun shutdown() {
        executor.shutdown()
    }
}

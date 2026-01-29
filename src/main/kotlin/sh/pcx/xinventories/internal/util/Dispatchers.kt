package sh.pcx.xinventories.internal.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import org.bukkit.plugin.Plugin
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

/**
 * Custom coroutine dispatcher that runs tasks on the Bukkit main thread.
 */
class BukkitMainThreadDispatcher(private val plugin: Plugin) : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (plugin.server.isPrimaryThread) {
            block.run()
        } else {
            plugin.server.scheduler.runTask(plugin, block)
        }
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

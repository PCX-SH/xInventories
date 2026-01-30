package sh.pcx.xinventories.internal.util

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.TimeUnit

/**
 * Compatibility layer for scheduling tasks that works on both
 * traditional Paper/Spigot servers and Folia.
 *
 * On Folia, player and entity operations must be scheduled on the
 * correct region thread. This class provides a unified API that
 * automatically uses the appropriate scheduling method.
 *
 * Usage:
 * ```kotlin
 * // Instead of: Bukkit.getScheduler().runTask(plugin) { ... }
 * SchedulerCompat.runTask(plugin, player) { player ->
 *     player.sendMessage("Hello!")
 * }
 * ```
 */
object SchedulerCompat {

    /**
     * Runs a task for a player on the appropriate thread.
     *
     * On Folia: Scheduled on the player's region thread.
     * On Paper/Spigot: Scheduled on the main thread.
     *
     * @param plugin The plugin scheduling the task
     * @param player The player context for the task
     * @param task The task to run, receives the player as parameter
     */
    fun runTask(plugin: Plugin, player: Player, task: (Player) -> Unit) {
        if (FoliaCompat.isFolia) {
            runEntityTask(plugin, player, task)
        } else {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                // Re-check player is still online
                if (player.isOnline) {
                    task(player)
                }
            })
        }
    }

    /**
     * Runs a task for a player after a delay.
     *
     * On Folia: Scheduled on the player's region thread.
     * On Paper/Spigot: Scheduled on the main thread.
     *
     * @param plugin The plugin scheduling the task
     * @param player The player context for the task
     * @param delayTicks The delay in ticks before execution
     * @param task The task to run, receives the player as parameter
     */
    fun runTaskLater(plugin: Plugin, player: Player, delayTicks: Long, task: (Player) -> Unit) {
        if (FoliaCompat.isFolia) {
            runEntityTaskLater(plugin, player, delayTicks, task)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (player.isOnline) {
                    task(player)
                }
            }, delayTicks)
        }
    }

    /**
     * Runs a task on the global region (main thread equivalent).
     *
     * Use this for operations that don't involve a specific entity or location,
     * such as global state management.
     *
     * @param plugin The plugin scheduling the task
     * @param task The task to run
     */
    fun runGlobalTask(plugin: Plugin, task: Runnable) {
        if (FoliaCompat.isFolia) {
            runFoliaGlobalTask(plugin, task)
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    /**
     * Runs a task on the global region after a delay.
     *
     * @param plugin The plugin scheduling the task
     * @param delayTicks The delay in ticks before execution
     * @param task The task to run
     */
    fun runGlobalTaskLater(plugin: Plugin, delayTicks: Long, task: Runnable) {
        if (FoliaCompat.isFolia) {
            runFoliaGlobalTaskLater(plugin, delayTicks, task)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
        }
    }

    /**
     * Runs a task asynchronously.
     *
     * On both Folia and Paper, this schedules on an async thread pool.
     *
     * @param plugin The plugin scheduling the task
     * @param task The task to run
     */
    fun runAsync(plugin: Plugin, task: Runnable) {
        if (FoliaCompat.isFolia) {
            runFoliaAsync(plugin, task)
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
        }
    }

    /**
     * Runs a task asynchronously after a delay.
     *
     * @param plugin The plugin scheduling the task
     * @param delayTicks The delay in ticks before execution
     * @param task The task to run
     */
    fun runAsyncLater(plugin: Plugin, delayTicks: Long, task: Runnable) {
        if (FoliaCompat.isFolia) {
            runFoliaAsyncLater(plugin, delayTicks, task)
        } else {
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks)
        }
    }

    /**
     * Runs a task at a specific location's region.
     *
     * On Folia: Scheduled on the region thread for the location.
     * On Paper/Spigot: Scheduled on the main thread.
     *
     * @param plugin The plugin scheduling the task
     * @param location The location context for the task
     * @param task The task to run
     */
    fun runAtLocation(plugin: Plugin, location: Location, task: Runnable) {
        if (FoliaCompat.isFolia) {
            runFoliaLocationTask(plugin, location, task)
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    // =========================================================================
    // Folia-specific implementations using reflection
    // =========================================================================

    /**
     * Runs a task for an entity on Folia's region scheduler.
     */
    private fun <T : Entity> runEntityTask(plugin: Plugin, entity: T, task: (T) -> Unit) {
        try {
            // Get the entity's scheduler: entity.getScheduler()
            val schedulerMethod = entity.javaClass.getMethod("getScheduler")
            val scheduler = schedulerMethod.invoke(entity)

            // Call scheduler.run(plugin, Consumer<ScheduledTask>, Runnable retired)
            val runMethod = scheduler.javaClass.getMethod(
                "run",
                Plugin::class.java,
                java.util.function.Consumer::class.java,
                Runnable::class.java
            )

            @Suppress("UNCHECKED_CAST")
            val consumer = java.util.function.Consumer<Any> { _ ->
                task(entity)
            }

            runMethod.invoke(scheduler, plugin, consumer, null)
        } catch (e: Exception) {
            // Fallback to traditional scheduling
            Logging.debug { "Folia entity scheduling failed, falling back: ${e.message}" }
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (entity.isValid) {
                    task(entity)
                }
            })
        }
    }

    /**
     * Runs a delayed task for an entity on Folia's region scheduler.
     */
    private fun <T : Entity> runEntityTaskLater(
        plugin: Plugin,
        entity: T,
        delayTicks: Long,
        task: (T) -> Unit
    ) {
        try {
            val schedulerMethod = entity.javaClass.getMethod("getScheduler")
            val scheduler = schedulerMethod.invoke(entity)

            val runDelayedMethod = scheduler.javaClass.getMethod(
                "runDelayed",
                Plugin::class.java,
                java.util.function.Consumer::class.java,
                Runnable::class.java,
                Long::class.javaPrimitiveType
            )

            @Suppress("UNCHECKED_CAST")
            val consumer = java.util.function.Consumer<Any> { _ ->
                task(entity)
            }

            runDelayedMethod.invoke(scheduler, plugin, consumer, null, delayTicks)
        } catch (e: Exception) {
            Logging.debug { "Folia entity delayed scheduling failed, falling back: ${e.message}" }
            Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                if (entity.isValid) {
                    task(entity)
                }
            }, delayTicks)
        }
    }

    /**
     * Runs a task on Folia's global region.
     */
    private fun runFoliaGlobalTask(plugin: Plugin, task: Runnable) {
        try {
            // Get the global region scheduler: Bukkit.getGlobalRegionScheduler()
            val getSchedulerMethod = Bukkit::class.java.getMethod("getGlobalRegionScheduler")
            val scheduler = getSchedulerMethod.invoke(null)

            val runMethod = scheduler.javaClass.getMethod(
                "run",
                Plugin::class.java,
                java.util.function.Consumer::class.java
            )

            val consumer = java.util.function.Consumer<Any> { _ ->
                task.run()
            }

            runMethod.invoke(scheduler, plugin, consumer)
        } catch (e: Exception) {
            Logging.debug { "Folia global scheduling failed, falling back: ${e.message}" }
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    /**
     * Runs a delayed task on Folia's global region.
     */
    private fun runFoliaGlobalTaskLater(plugin: Plugin, delayTicks: Long, task: Runnable) {
        try {
            val getSchedulerMethod = Bukkit::class.java.getMethod("getGlobalRegionScheduler")
            val scheduler = getSchedulerMethod.invoke(null)

            val runDelayedMethod = scheduler.javaClass.getMethod(
                "runDelayed",
                Plugin::class.java,
                java.util.function.Consumer::class.java,
                Long::class.javaPrimitiveType
            )

            val consumer = java.util.function.Consumer<Any> { _ ->
                task.run()
            }

            runDelayedMethod.invoke(scheduler, plugin, consumer, delayTicks)
        } catch (e: Exception) {
            Logging.debug { "Folia global delayed scheduling failed, falling back: ${e.message}" }
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
        }
    }

    /**
     * Runs an async task on Folia.
     */
    private fun runFoliaAsync(plugin: Plugin, task: Runnable) {
        try {
            val getSchedulerMethod = Bukkit::class.java.getMethod("getAsyncScheduler")
            val scheduler = getSchedulerMethod.invoke(null)

            val runNowMethod = scheduler.javaClass.getMethod(
                "runNow",
                Plugin::class.java,
                java.util.function.Consumer::class.java
            )

            val consumer = java.util.function.Consumer<Any> { _ ->
                task.run()
            }

            runNowMethod.invoke(scheduler, plugin, consumer)
        } catch (e: Exception) {
            Logging.debug { "Folia async scheduling failed, falling back: ${e.message}" }
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
        }
    }

    /**
     * Runs a delayed async task on Folia.
     */
    private fun runFoliaAsyncLater(plugin: Plugin, delayTicks: Long, task: Runnable) {
        try {
            val getSchedulerMethod = Bukkit::class.java.getMethod("getAsyncScheduler")
            val scheduler = getSchedulerMethod.invoke(null)

            // Convert ticks to milliseconds (1 tick = 50ms)
            val delayMillis = delayTicks * 50L

            val runDelayedMethod = scheduler.javaClass.getMethod(
                "runDelayed",
                Plugin::class.java,
                java.util.function.Consumer::class.java,
                Long::class.javaPrimitiveType,
                TimeUnit::class.java
            )

            val consumer = java.util.function.Consumer<Any> { _ ->
                task.run()
            }

            runDelayedMethod.invoke(scheduler, plugin, consumer, delayMillis, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            Logging.debug { "Folia async delayed scheduling failed, falling back: ${e.message}" }
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, task, delayTicks)
        }
    }

    /**
     * Runs a task at a location on Folia's region scheduler.
     */
    private fun runFoliaLocationTask(plugin: Plugin, location: Location, task: Runnable) {
        try {
            val getSchedulerMethod = Bukkit::class.java.getMethod("getRegionScheduler")
            val scheduler = getSchedulerMethod.invoke(null)

            val runMethod = scheduler.javaClass.getMethod(
                "run",
                Plugin::class.java,
                Location::class.java,
                java.util.function.Consumer::class.java
            )

            val consumer = java.util.function.Consumer<Any> { _ ->
                task.run()
            }

            runMethod.invoke(scheduler, plugin, location, consumer)
        } catch (e: Exception) {
            Logging.debug { "Folia location scheduling failed, falling back: ${e.message}" }
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }
}

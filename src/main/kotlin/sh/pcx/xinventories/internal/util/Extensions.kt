package sh.pcx.xinventories.internal.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * MiniMessage instance for text parsing.
 */
val miniMessage: MiniMessage = MiniMessage.miniMessage()

/**
 * Parses a MiniMessage string into a Component.
 */
fun String.toComponent(): Component = miniMessage.deserialize(this)

/**
 * Parses a MiniMessage string with placeholders.
 */
fun String.toComponent(vararg placeholders: Pair<String, String>): Component {
    var result = this
    placeholders.forEach { (key, value) ->
        result = result.replace("{$key}", value)
    }
    return miniMessage.deserialize(result)
}

/**
 * Sends a MiniMessage formatted message to a CommandSender.
 */
fun CommandSender.sendMiniMessage(message: String) {
    sendMessage(message.toComponent())
}

/**
 * Sends a MiniMessage formatted message with placeholders.
 */
fun CommandSender.sendMiniMessage(message: String, vararg placeholders: Pair<String, String>) {
    sendMessage(message.toComponent(*placeholders))
}

/**
 * Gets the item in a player's main hand, or null if empty.
 */
fun Player.mainHandItemOrNull(): ItemStack? {
    val item = inventory.itemInMainHand
    return if (item.type.isAir) null else item
}

/**
 * Converts a CoroutineScope launch to a CompletableFuture for Java interop.
 */
fun <T> CoroutineScope.toFuture(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> T
): CompletableFuture<T> = future(context, start, block)

/**
 * Launches a coroutine and ignores any exceptions (logs them).
 */
fun CoroutineScope.launchSafe(
    context: CoroutineContext = EmptyCoroutineContext,
    start: CoroutineStart = CoroutineStart.DEFAULT,
    block: suspend CoroutineScope.() -> Unit
): Job = launch(context, start) {
    try {
        block()
    } catch (e: Exception) {
        Logging.error("Coroutine error", e)
    }
}

/**
 * Converts bytes to a human-readable size string.
 */
fun Long.toReadableSize(): String {
    val units = arrayOf("B", "KB", "MB", "GB")
    var size = this.toDouble()
    var unitIndex = 0

    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }

    return "%.2f %s".format(size, units[unitIndex])
}

/**
 * Clamps a value between min and max.
 */
fun <T : Comparable<T>> T.clamp(min: T, max: T): T = when {
    this < min -> min
    this > max -> max
    else -> this
}

/**
 * Returns true if the string is a valid regex pattern.
 */
fun String.isValidRegex(): Boolean = try {
    Regex(this)
    true
} catch (e: Exception) {
    false
}

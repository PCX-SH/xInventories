package sh.pcx.xinventories.internal.util

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import java.util.logging.Level
import java.util.logging.Logger

/**
 * Centralized logging utility for xInventories.
 */
object Logging {
    private lateinit var logger: Logger
    private var debug: Boolean = false

    fun init(logger: Logger, debug: Boolean = false) {
        this.logger = logger
        this.debug = debug
    }

    fun setDebug(enabled: Boolean) {
        this.debug = enabled
    }

    fun info(message: String) {
        logger.info(message)
    }

    fun warning(message: String) {
        logger.warning(message)
    }

    fun severe(message: String) {
        logger.severe(message)
    }

    fun debug(message: String) {
        if (debug) {
            logger.info("[DEBUG] $message")
        }
    }

    fun debug(message: () -> String) {
        if (debug) {
            logger.info("[DEBUG] ${message()}")
        }
    }

    fun error(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            logger.log(Level.SEVERE, message, throwable)
        } else {
            logger.severe(message)
        }
    }

    /**
     * Notifies online admins of a critical error.
     */
    fun notifyAdmins(message: String) {
        val component = Component.text()
            .append(Component.text("[xInventories] ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
            .append(Component.text(message, NamedTextColor.RED))
            .build()

        Bukkit.getOnlinePlayers()
            .filter { it.hasPermission("xinventories.admin") }
            .forEach { it.sendMessage(component) }
    }
}

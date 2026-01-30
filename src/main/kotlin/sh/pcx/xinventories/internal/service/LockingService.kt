package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.event.InventoryLockEvent
import sh.pcx.xinventories.api.event.InventoryUnlockEvent
import sh.pcx.xinventories.internal.config.LockingConfig
import sh.pcx.xinventories.internal.model.InventoryLock
import sh.pcx.xinventories.internal.model.LockScope
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml

/**
 * Service for managing inventory locks.
 * Provides lock/unlock operations, expiration handling, and persistence.
 */
class LockingService(
    private val plugin: XInventories,
    private val scope: CoroutineScope
) {
    // Active locks stored in memory
    private val locks = ConcurrentHashMap<UUID, InventoryLock>()

    // Task for checking lock expiration
    private var expirationTask: BukkitTask? = null

    // MiniMessage for parsing formatted messages
    private val miniMessage = MiniMessage.miniMessage()

    // Configuration
    private val config: LockingConfig
        get() = plugin.configManager.mainConfig.locking

    /**
     * Initializes the locking service.
     */
    fun initialize() {
        if (!config.enabled) {
            Logging.info("Inventory locking is disabled")
            return
        }

        // Load persisted locks
        loadLocks()

        // Start expiration checker (runs every second)
        expirationTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            checkExpiredLocks()
        }, 20L, 20L) // Every second

        Logging.info("LockingService initialized with ${locks.size} active locks")
    }

    /**
     * Shuts down the locking service.
     */
    fun shutdown() {
        expirationTask?.cancel()
        expirationTask = null

        // Save locks before shutdown
        saveLocks()

        Logging.debug { "LockingService shut down" }
    }

    /**
     * Locks a player's inventory.
     *
     * @param playerUuid UUID of the player to lock
     * @param lockedBy UUID of the admin who created the lock (null for system locks)
     * @param duration Duration of the lock (null for permanent)
     * @param reason Reason for the lock
     * @param scope The scope of the lock
     * @return The created lock, or null if locking failed (e.g., cancelled by event)
     */
    fun lockInventory(
        playerUuid: UUID,
        lockedBy: UUID?,
        duration: Duration? = null,
        reason: String? = null,
        scope: LockScope = LockScope.ALL
    ): InventoryLock? {
        if (!config.enabled) {
            Logging.debug { "Locking disabled, cannot lock inventory for $playerUuid" }
            return null
        }

        // Create the lock
        val lock = InventoryLock.createFullLock(playerUuid, lockedBy, duration, reason)

        // Fire event
        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            val event = InventoryLockEvent(player, lock)
            Bukkit.getPluginManager().callEvent(event)

            if (event.isCancelled) {
                Logging.debug { "Lock cancelled by event for ${player.name}" }
                return null
            }
        }

        // Store the lock
        locks[playerUuid] = lock

        // Save to disk
        saveLocks()

        Logging.info("Locked inventory for $playerUuid (duration: ${lock.getRemainingTimeString()}, reason: ${reason ?: "none"})")

        return lock
    }

    /**
     * Unlocks a player's inventory.
     *
     * @param playerUuid UUID of the player to unlock
     * @param reason Reason for the unlock (MANUAL, EXPIRED, PLUGIN)
     * @return true if the player was unlocked, false if they weren't locked
     */
    fun unlockInventory(
        playerUuid: UUID,
        reason: InventoryUnlockEvent.UnlockReason = InventoryUnlockEvent.UnlockReason.MANUAL
    ): Boolean {
        val lock = locks.remove(playerUuid) ?: return false

        // Fire event
        val player = Bukkit.getPlayer(playerUuid)
        if (player != null) {
            val event = InventoryUnlockEvent(player, lock, reason)
            Bukkit.getPluginManager().callEvent(event)
        }

        // Save to disk
        saveLocks()

        Logging.info("Unlocked inventory for $playerUuid (reason: $reason)")

        return true
    }

    /**
     * Checks if a player's inventory is locked.
     */
    fun isLocked(playerUuid: UUID): Boolean {
        val lock = locks[playerUuid] ?: return false
        if (lock.isExpired()) {
            // Clean up expired lock
            unlockInventory(playerUuid, InventoryUnlockEvent.UnlockReason.EXPIRED)
            return false
        }
        return true
    }

    /**
     * Checks if a player's inventory is locked for a specific group.
     */
    fun isLocked(playerUuid: UUID, groupName: String): Boolean {
        val lock = locks[playerUuid] ?: return false
        if (lock.isExpired()) {
            unlockInventory(playerUuid, InventoryUnlockEvent.UnlockReason.EXPIRED)
            return false
        }
        return lock.appliesToGroup(groupName)
    }

    /**
     * Gets the lock for a player, or null if not locked.
     */
    fun getLock(playerUuid: UUID): InventoryLock? {
        val lock = locks[playerUuid] ?: return null
        if (lock.isExpired()) {
            unlockInventory(playerUuid, InventoryUnlockEvent.UnlockReason.EXPIRED)
            return null
        }
        return lock
    }

    /**
     * Gets all currently locked players.
     */
    fun getLockedPlayers(): List<InventoryLock> {
        // Clean up expired locks first
        val expired = locks.values.filter { it.isExpired() }
        expired.forEach { unlockInventory(it.playerUuid, InventoryUnlockEvent.UnlockReason.EXPIRED) }

        return locks.values.toList()
    }

    /**
     * Checks if a player can bypass inventory locks.
     */
    fun canBypass(player: Player): Boolean {
        if (!config.allowAdminBypass) return false
        return player.hasPermission("xinventories.lock.bypass")
    }

    /**
     * Shows the lock message to a player.
     */
    fun showLockMessage(player: Player) {
        val lock = getLock(player.uniqueId) ?: return

        // Build message
        var message = config.defaultMessage
        lock.reason?.let {
            message += " <gray>Reason: <white>$it"
        }
        if (lock.expiresAt != null) {
            message += " <gray>Expires in: <white>${lock.getRemainingTimeString()}"
        }

        // Send action bar message
        try {
            val component = miniMessage.deserialize(message)
            player.sendActionBar(component)
        } catch (e: Exception) {
            // Fallback to plain message
            player.sendActionBar(net.kyori.adventure.text.Component.text(message))
        }
    }

    /**
     * Checks and handles expired locks.
     */
    private fun checkExpiredLocks() {
        val expired = locks.values.filter { it.isExpired() }
        for (lock in expired) {
            unlockInventory(lock.playerUuid, InventoryUnlockEvent.UnlockReason.EXPIRED)
        }
    }

    /**
     * Gets the data file for persisting locks.
     */
    private fun getLocksFile(): File {
        return File(plugin.dataFolder, "data/locks.yml")
    }

    /**
     * Saves locks to disk for persistence.
     */
    private fun saveLocks() {
        scope.launch {
            try {
                val file = getLocksFile()
                file.parentFile?.mkdirs()

                val data = locks.values.map { lock ->
                    mapOf(
                        "playerUuid" to lock.playerUuid.toString(),
                        "lockedBy" to lock.lockedBy?.toString(),
                        "lockedAt" to lock.lockedAt.toEpochMilli(),
                        "expiresAt" to lock.expiresAt?.toEpochMilli(),
                        "reason" to lock.reason,
                        "scope" to lock.scope.name,
                        "lockedGroup" to lock.lockedGroup,
                        "lockedSlots" to lock.lockedSlots?.toList()
                    )
                }

                val options = DumperOptions().apply {
                    defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                    isPrettyFlow = true
                }
                val yaml = Yaml(options)
                file.writeText(yaml.dump(mapOf("locks" to data)))

                Logging.debug { "Saved ${locks.size} locks to disk" }
            } catch (e: Exception) {
                Logging.error("Failed to save locks", e)
            }
        }
    }

    /**
     * Loads locks from disk.
     */
    @Suppress("UNCHECKED_CAST")
    private fun loadLocks() {
        try {
            val file = getLocksFile()
            if (!file.exists()) return

            val yaml = Yaml()
            val data = yaml.load<Map<String, Any>>(file.readText()) ?: return
            val locksList = data["locks"] as? List<Map<String, Any?>> ?: return

            for (lockData in locksList) {
                try {
                    val playerUuid = UUID.fromString(lockData["playerUuid"] as String)
                    val lockedBy = (lockData["lockedBy"] as? String)?.let { UUID.fromString(it) }
                    val lockedAt = Instant.ofEpochMilli(lockData["lockedAt"] as Long)
                    val expiresAt = (lockData["expiresAt"] as? Long)?.let { Instant.ofEpochMilli(it) }
                    val reason = lockData["reason"] as? String
                    val scope = LockScope.valueOf(lockData["scope"] as String)
                    val lockedGroup = lockData["lockedGroup"] as? String
                    val lockedSlots = (lockData["lockedSlots"] as? List<Int>)?.toSet()

                    val lock = InventoryLock(
                        playerUuid = playerUuid,
                        lockedBy = lockedBy,
                        lockedAt = lockedAt,
                        expiresAt = expiresAt,
                        reason = reason,
                        scope = scope,
                        lockedGroup = lockedGroup,
                        lockedSlots = lockedSlots
                    )

                    // Only load if not expired
                    if (!lock.isExpired()) {
                        locks[playerUuid] = lock
                    }
                } catch (e: Exception) {
                    Logging.warning("Failed to load lock entry: ${e.message}")
                }
            }

            Logging.debug { "Loaded ${locks.size} locks from disk" }
        } catch (e: Exception) {
            Logging.error("Failed to load locks", e)
        }
    }

    /**
     * Reloads the service configuration.
     */
    fun reload() {
        // Restart expiration task if needed
        expirationTask?.cancel()

        if (config.enabled) {
            expirationTask = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
                checkExpiredLocks()
            }, 20L, 20L)
        }
    }
}

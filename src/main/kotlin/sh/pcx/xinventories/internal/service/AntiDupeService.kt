package sh.pcx.xinventories.internal.service

import kotlinx.coroutines.CoroutineScope
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.event.DupeDetectionEvent
import sh.pcx.xinventories.internal.util.Logging
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Sensitivity levels for dupe detection.
 */
enum class DupeSensitivity(val minSwitchIntervalMs: Long, val anomalyThreshold: Double) {
    LOW(200, 3.0),      // Very lenient, only obvious dupes
    MEDIUM(500, 2.0),   // Balanced detection
    HIGH(1000, 1.5)     // Aggressive detection, may have false positives
}

/**
 * Configuration for anti-dupe detection.
 */
data class AntiDupeConfig(
    val enabled: Boolean = true,
    val sensitivity: DupeSensitivity = DupeSensitivity.MEDIUM,
    val minSwitchIntervalMs: Long = 500,
    val freezeOnDetection: Boolean = false,
    val notifyAdmins: Boolean = true,
    val logDetections: Boolean = true
)

/**
 * Information about a potential dupe detection.
 */
data class DupeDetection(
    val player: UUID,
    val playerName: String,
    val type: DupeDetectionType,
    val timestamp: Instant,
    val details: String,
    val severity: DupeSeverity
)

/**
 * Types of dupe detection.
 */
enum class DupeDetectionType {
    RAPID_SWITCH,       // Player switching groups too fast
    ITEM_ANOMALY,       // Sudden large increase in items
    TIMING_ANOMALY      // Suspicious timing patterns
}

/**
 * Severity levels for detections.
 */
enum class DupeSeverity {
    LOW,        // Likely false positive
    MEDIUM,     // Suspicious, worth investigating
    HIGH        // Almost certainly a dupe attempt
}

/**
 * Service for detecting potential item duplication exploits.
 * Monitors player inventory patterns and group switching behavior.
 */
class AntiDupeService(
    private val plugin: XInventories,
    private val scope: CoroutineScope
) {
    private val lastSwitchTimes = ConcurrentHashMap<UUID, Long>()
    private val itemCounts = ConcurrentHashMap<UUID, Int>()
    private val recentDetections = ConcurrentHashMap<UUID, MutableList<DupeDetection>>()
    private val frozenPlayers = ConcurrentHashMap.newKeySet<UUID>()

    /**
     * Current anti-dupe configuration.
     */
    var config: AntiDupeConfig = AntiDupeConfig()
        private set

    /**
     * Whether anti-dupe detection is enabled.
     */
    val isEnabled: Boolean
        get() = config.enabled

    /**
     * Initializes the anti-dupe service.
     */
    fun initialize() {
        loadConfig()
        Logging.info("AntiDupeService initialized (sensitivity: ${config.sensitivity})")
    }

    /**
     * Shuts down the anti-dupe service.
     */
    fun shutdown() {
        lastSwitchTimes.clear()
        itemCounts.clear()
        recentDetections.clear()
        frozenPlayers.clear()
        Logging.debug { "AntiDupeService shut down" }
    }

    /**
     * Reloads the anti-dupe configuration.
     */
    fun reload() {
        loadConfig()
        Logging.debug { "AntiDupeService config reloaded" }
    }

    private fun loadConfig() {
        val configFile = plugin.config

        val sensitivityStr = configFile.getString("anti-dupe.sensitivity", "MEDIUM") ?: "MEDIUM"
        val sensitivity = try {
            DupeSensitivity.valueOf(sensitivityStr.uppercase())
        } catch (e: Exception) {
            DupeSensitivity.MEDIUM
        }

        config = AntiDupeConfig(
            enabled = configFile.getBoolean("anti-dupe.enabled", true),
            sensitivity = sensitivity,
            minSwitchIntervalMs = configFile.getLong("anti-dupe.min-switch-interval-ms", sensitivity.minSwitchIntervalMs),
            freezeOnDetection = configFile.getBoolean("anti-dupe.freeze-on-detection", false),
            notifyAdmins = configFile.getBoolean("anti-dupe.notify-admins", true),
            logDetections = configFile.getBoolean("anti-dupe.log-detections", true)
        )
    }

    // =========================================================================
    // Detection Methods
    // =========================================================================

    /**
     * Checks for rapid group switching.
     * Called before a group switch is processed.
     */
    fun checkRapidSwitch(player: Player, fromGroup: String, toGroup: String): DupeDetection? {
        if (!isEnabled) return null

        val now = System.currentTimeMillis()
        val lastSwitch = lastSwitchTimes[player.uniqueId]

        if (lastSwitch != null) {
            val interval = now - lastSwitch

            if (interval < config.minSwitchIntervalMs) {
                val detection = DupeDetection(
                    player = player.uniqueId,
                    playerName = player.name,
                    type = DupeDetectionType.RAPID_SWITCH,
                    timestamp = Instant.now(),
                    details = "Switched from '$fromGroup' to '$toGroup' in ${interval}ms (min: ${config.minSwitchIntervalMs}ms)",
                    severity = when {
                        interval < config.minSwitchIntervalMs / 4 -> DupeSeverity.HIGH
                        interval < config.minSwitchIntervalMs / 2 -> DupeSeverity.MEDIUM
                        else -> DupeSeverity.LOW
                    }
                )

                handleDetection(player, detection)
                return detection
            }
        }

        lastSwitchTimes[player.uniqueId] = now
        return null
    }

    /**
     * Checks for item count anomalies.
     * Called after inventory is loaded.
     */
    fun checkItemAnomaly(player: Player, previousCount: Int, currentCount: Int): DupeDetection? {
        if (!isEnabled) return null

        val difference = currentCount - previousCount
        val threshold = (previousCount * config.sensitivity.anomalyThreshold).toInt()

        if (difference > 0 && difference > threshold && previousCount > 10) {
            val detection = DupeDetection(
                player = player.uniqueId,
                playerName = player.name,
                type = DupeDetectionType.ITEM_ANOMALY,
                timestamp = Instant.now(),
                details = "Item count increased from $previousCount to $currentCount (+$difference, threshold: $threshold)",
                severity = when {
                    difference > threshold * 3 -> DupeSeverity.HIGH
                    difference > threshold * 2 -> DupeSeverity.MEDIUM
                    else -> DupeSeverity.LOW
                }
            )

            handleDetection(player, detection)
            return detection
        }

        return null
    }

    /**
     * Records inventory snapshot for comparison.
     */
    fun recordInventorySnapshot(player: Player) {
        if (!isEnabled) return

        val itemCount = countItems(player)
        itemCounts[player.uniqueId] = itemCount
    }

    /**
     * Checks inventory after load and compares with snapshot.
     */
    fun checkAfterLoad(player: Player): DupeDetection? {
        if (!isEnabled) return null

        val previousCount = itemCounts[player.uniqueId] ?: return null
        val currentCount = countItems(player)

        return checkItemAnomaly(player, previousCount, currentCount)
    }

    /**
     * Manually triggers a dupe check on a player.
     */
    fun manualCheck(player: Player): List<DupeDetection> {
        if (!isEnabled) return emptyList()

        val detections = mutableListOf<DupeDetection>()

        // Check for rapid switches
        val lastSwitch = lastSwitchTimes[player.uniqueId]
        if (lastSwitch != null && System.currentTimeMillis() - lastSwitch < 5000) {
            detections.add(DupeDetection(
                player = player.uniqueId,
                playerName = player.name,
                type = DupeDetectionType.TIMING_ANOMALY,
                timestamp = Instant.now(),
                details = "Recent group switch detected within last 5 seconds",
                severity = DupeSeverity.LOW
            ))
        }

        return detections
    }

    // =========================================================================
    // Player State Methods
    // =========================================================================

    /**
     * Freezes a player's inventory.
     */
    fun freezePlayer(player: Player) {
        frozenPlayers.add(player.uniqueId)
        Logging.info("Frozen inventory for ${player.name} due to dupe detection")
    }

    /**
     * Unfreezes a player's inventory.
     */
    fun unfreezePlayer(player: Player) {
        frozenPlayers.remove(player.uniqueId)
        Logging.info("Unfrozen inventory for ${player.name}")
    }

    /**
     * Checks if a player is frozen.
     */
    fun isFrozen(player: Player): Boolean = frozenPlayers.contains(player.uniqueId)

    /**
     * Gets all frozen players.
     */
    fun getFrozenPlayers(): Set<UUID> = frozenPlayers.toSet()

    /**
     * Gets recent detections for a player.
     */
    fun getRecentDetections(uuid: UUID): List<DupeDetection> {
        return recentDetections[uuid]?.toList() ?: emptyList()
    }

    /**
     * Gets all recent detections.
     */
    fun getAllRecentDetections(): Map<UUID, List<DupeDetection>> {
        return recentDetections.mapValues { it.value.toList() }
    }

    /**
     * Clears detections for a player.
     */
    fun clearDetections(uuid: UUID) {
        recentDetections.remove(uuid)
    }

    /**
     * Clears player tracking data on quit.
     */
    fun onPlayerQuit(player: Player) {
        lastSwitchTimes.remove(player.uniqueId)
        itemCounts.remove(player.uniqueId)
        // Don't clear detections - keep for admin review
    }

    // =========================================================================
    // Internal Methods
    // =========================================================================

    private fun handleDetection(player: Player, detection: DupeDetection) {
        // Store detection
        recentDetections.computeIfAbsent(player.uniqueId) { mutableListOf() }.apply {
            add(detection)
            // Keep only last 10 detections per player
            while (size > 10) removeAt(0)
        }

        // Fire event
        val event = DupeDetectionEvent(player, detection)
        Bukkit.getPluginManager().callEvent(event)

        if (event.isCancelled) return

        // Log detection
        if (config.logDetections) {
            val level = when (detection.severity) {
                DupeSeverity.HIGH -> "WARNING"
                DupeSeverity.MEDIUM -> "INFO"
                DupeSeverity.LOW -> "DEBUG"
            }
            Logging.warning("[DUPE-$level] ${player.name}: ${detection.type} - ${detection.details}")
        }

        // Notify admins
        if (config.notifyAdmins && detection.severity != DupeSeverity.LOW) {
            notifyAdmins(detection)
        }

        // Freeze if configured
        if (config.freezeOnDetection && detection.severity == DupeSeverity.HIGH) {
            freezePlayer(player)
        }
    }

    private fun notifyAdmins(detection: DupeDetection) {
        val message = buildString {
            append("[xInventories] Dupe Detection: ")
            append(detection.playerName)
            append(" - ")
            append(detection.type)
            append(" (")
            append(detection.severity)
            append(")")
        }

        Bukkit.getOnlinePlayers()
            .filter { it.hasPermission("xinventories.admin") }
            .forEach { it.sendMessage(message) }
    }

    private fun countItems(player: Player): Int {
        var count = 0

        // Main inventory
        for (item in player.inventory.contents) {
            if (item != null && !item.type.isAir) {
                count += item.amount
            }
        }

        // Armor
        for (item in player.inventory.armorContents) {
            if (item != null && !item.type.isAir) {
                count += item.amount
            }
        }

        // Offhand
        val offhand = player.inventory.itemInOffHand
        if (!offhand.type.isAir) {
            count += offhand.amount
        }

        return count
    }
}

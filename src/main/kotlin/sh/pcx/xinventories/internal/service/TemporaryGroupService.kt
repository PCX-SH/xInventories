package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.api.event.InventorySwitchEvent
import sh.pcx.xinventories.api.event.TemporaryGroupAssignEvent
import sh.pcx.xinventories.api.event.TemporaryGroupExpireEvent
import sh.pcx.xinventories.api.event.TemporaryGroupRemoveEvent
import sh.pcx.xinventories.internal.model.TemporaryGroupAssignment
import sh.pcx.xinventories.internal.util.Logging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Service for managing temporary group assignments.
 *
 * Temporary groups allow players to be placed in a different inventory
 * group for a limited time, such as during events or minigames. When
 * the assignment expires, the player is automatically restored to their
 * original group.
 */
class TemporaryGroupService(
    private val plugin: PluginContext,
    private val scope: CoroutineScope,
    private val storageService: StorageService,
    private val groupService: GroupService,
    private val inventoryService: InventoryService,
    private val messageService: MessageService
) {
    // Active assignments indexed by player UUID
    private val assignments = ConcurrentHashMap<UUID, TemporaryGroupAssignment>()

    // Background job for checking expirations
    private var expirationJob: Job? = null

    // Check interval (1 second)
    private val checkIntervalMs = 1000L

    /**
     * Initializes the temporary group service.
     */
    suspend fun initialize() {
        // Load persisted assignments from storage
        loadAssignments()

        // Start expiration checker
        startExpirationChecker()

        Logging.info("TemporaryGroupService initialized with ${assignments.size} active assignments")
    }

    /**
     * Shuts down the temporary group service.
     */
    suspend fun shutdown() {
        expirationJob?.cancel()
        expirationJob = null

        // Save all assignments before shutdown
        saveAllAssignments()

        assignments.clear()
        Logging.debug { "TemporaryGroupService shut down" }
    }

    /**
     * Assigns a player to a temporary group.
     *
     * @param player The player to assign
     * @param temporaryGroup The temporary group name
     * @param duration How long the assignment should last
     * @param assignedBy Who is making the assignment
     * @param reason Optional reason for the assignment
     * @return Result containing the assignment or an error
     */
    suspend fun assignTemporaryGroup(
        player: Player,
        temporaryGroup: String,
        duration: Duration,
        assignedBy: String,
        reason: String? = null
    ): Result<TemporaryGroupAssignment> {
        // Validate the temporary group exists
        val tempGroup = groupService.getGroup(temporaryGroup)
            ?: return Result.failure(IllegalArgumentException("Group '$temporaryGroup' not found"))

        // Get the player's current group
        val currentGroup = groupService.getGroupForPlayer(player)
        val originalGroup = assignments[player.uniqueId]?.originalGroup ?: currentGroup.name

        // Check if already in this temp group
        val existing = assignments[player.uniqueId]
        if (existing != null && existing.temporaryGroup == temporaryGroup) {
            return Result.failure(IllegalStateException("Player is already in temporary group '$temporaryGroup'"))
        }

        // Create the assignment
        val assignment = TemporaryGroupAssignment.create(
            playerUuid = player.uniqueId,
            temporaryGroup = temporaryGroup,
            originalGroup = originalGroup,
            duration = duration,
            assignedBy = assignedBy,
            reason = reason
        )

        // Fire the assign event
        val event = TemporaryGroupAssignEvent(player, assignment)
        Bukkit.getPluginManager().callEvent(event)

        if (event.isCancelled) {
            return Result.failure(IllegalStateException("Temporary group assignment was cancelled by another plugin"))
        }

        // If replacing an existing assignment, handle the transition
        if (existing != null) {
            assignments.remove(player.uniqueId)
        }

        // Store the assignment
        assignments[player.uniqueId] = assignment

        // Save to persistent storage
        saveAssignment(assignment)

        // Switch the player to the temporary group
        try {
            val fromGroup = groupService.getGroupApi(currentGroup.name)
            val toGroup = groupService.getGroupApi(temporaryGroup)
            if (fromGroup == null || toGroup == null) {
                throw IllegalStateException("Failed to resolve groups for switch")
            }
            inventoryService.switchInventory(player, fromGroup, toGroup, InventorySwitchEvent.SwitchReason.API)
            Logging.info("Assigned ${player.name} to temporary group '$temporaryGroup' for ${assignment.getRemainingTimeString()}")
        } catch (e: Exception) {
            // Rollback on failure
            assignments.remove(player.uniqueId)
            deleteAssignment(player.uniqueId)
            return Result.failure(e)
        }

        // Notify the player
        messageService.send(player, "tempgroup-assigned",
            "group" to temporaryGroup,
            "duration" to assignment.getRemainingTimeString()
        )

        return Result.success(assignment)
    }

    /**
     * Removes a player's temporary group assignment.
     *
     * @param playerUuid The player's UUID
     * @param reason The reason for removal
     * @param switchBack Whether to switch the player back to their original group
     * @return true if an assignment was removed
     */
    suspend fun removeTemporaryGroup(
        playerUuid: UUID,
        reason: TemporaryGroupRemoveEvent.RemovalReason = TemporaryGroupRemoveEvent.RemovalReason.MANUAL,
        switchBack: Boolean = true
    ): Boolean {
        val assignment = assignments.remove(playerUuid) ?: return false

        // Delete from persistent storage
        deleteAssignment(playerUuid)

        // Fire the remove event
        val removeEvent = TemporaryGroupRemoveEvent(playerUuid, assignment, reason)
        Bukkit.getPluginManager().callEvent(removeEvent)

        // Switch the player back if online
        if (switchBack) {
            val player = Bukkit.getPlayer(playerUuid)
            if (player != null) {
                try {
                    val fromGroup = groupService.getGroupApi(assignment.temporaryGroup)
                    val toGroup = groupService.getGroupApi(assignment.originalGroup)
                    if (fromGroup != null && toGroup != null) {
                        inventoryService.switchInventory(player, fromGroup, toGroup, InventorySwitchEvent.SwitchReason.API)
                    }
                    messageService.send(player, "tempgroup-removed",
                        "group" to assignment.temporaryGroup
                    )
                } catch (e: Exception) {
                    Logging.error("Failed to restore ${player.name} to original group", e)
                }
            }
        }

        Logging.info("Removed temporary group assignment for $playerUuid (reason: ${reason.name})")
        return true
    }

    /**
     * Extends a player's temporary group assignment.
     *
     * @param playerUuid The player's UUID
     * @param extension The duration to extend by
     * @return Result containing the updated assignment or an error
     */
    suspend fun extendTemporaryGroup(playerUuid: UUID, extension: Duration): Result<TemporaryGroupAssignment> {
        val current = assignments[playerUuid]
            ?: return Result.failure(IllegalStateException("Player has no temporary group assignment"))

        val extended = current.extend(extension)
        assignments[playerUuid] = extended

        // Update persistent storage
        saveAssignment(extended)

        // Notify player if online
        Bukkit.getPlayer(playerUuid)?.let { player ->
            messageService.send(player, "tempgroup-extended",
                "group" to extended.temporaryGroup,
                "duration" to extended.getRemainingTimeString()
            )
        }

        Logging.info("Extended temporary group for $playerUuid by ${TemporaryGroupAssignment.formatDuration(extension)}")
        return Result.success(extended)
    }

    /**
     * Gets a player's current temporary group assignment.
     *
     * @param playerUuid The player's UUID
     * @return The assignment, or null if none
     */
    fun getAssignment(playerUuid: UUID): TemporaryGroupAssignment? = assignments[playerUuid]

    /**
     * Checks if a player has a temporary group assignment.
     *
     * @param playerUuid The player's UUID
     * @return true if the player has a temporary assignment
     */
    fun hasTemporaryGroup(playerUuid: UUID): Boolean = assignments.containsKey(playerUuid)

    /**
     * Gets all active temporary group assignments.
     */
    fun getAllAssignments(): Map<UUID, TemporaryGroupAssignment> = assignments.toMap()

    /**
     * Gets assignments for a specific temporary group.
     *
     * @param groupName The group name
     * @return List of assignments for that group
     */
    fun getAssignmentsForGroup(groupName: String): List<TemporaryGroupAssignment> {
        return assignments.values.filter { it.temporaryGroup.equals(groupName, ignoreCase = true) }
    }

    /**
     * Gets the number of active assignments.
     */
    fun getActiveCount(): Int = assignments.size

    /**
     * Starts the background expiration checker.
     */
    private fun startExpirationChecker() {
        expirationJob = scope.launch {
            while (isActive) {
                try {
                    checkExpirations()
                } catch (e: Exception) {
                    Logging.error("Error checking temporary group expirations", e)
                }
                delay(checkIntervalMs)
            }
        }
    }

    /**
     * Checks all assignments for expiration.
     */
    private suspend fun checkExpirations() {
        val now = Instant.now()
        val expired = assignments.filter { (_, assignment) -> now.isAfter(assignment.expiresAt) }

        for ((playerUuid, assignment) in expired) {
            handleExpiration(playerUuid, assignment)
        }
    }

    /**
     * Handles an expired assignment.
     */
    private suspend fun handleExpiration(playerUuid: UUID, assignment: TemporaryGroupAssignment) {
        val player = Bukkit.getPlayer(playerUuid)

        if (player != null) {
            // Fire the expire event
            val event = TemporaryGroupExpireEvent(player, assignment)
            Bukkit.getPluginManager().callEvent(event)

            if (event.isCancelled) {
                // Extend by 1 minute if cancelled
                val extended = assignment.extend(Duration.ofMinutes(1))
                assignments[playerUuid] = extended
                saveAssignment(extended)
                Logging.debug { "Temporary group expiration cancelled for ${player.name}, extended by 1 minute" }
                return
            }
        }

        // Remove the assignment
        removeTemporaryGroup(playerUuid, TemporaryGroupRemoveEvent.RemovalReason.EXPIRED)
    }

    /**
     * Loads assignments from persistent storage.
     */
    private suspend fun loadAssignments() {
        try {
            val loaded = loadAssignmentsFromStorage()
            loaded.forEach { assignment ->
                if (!assignment.isExpired) {
                    assignments[assignment.playerUuid] = assignment
                } else {
                    // Clean up expired assignments
                    deleteAssignment(assignment.playerUuid)
                }
            }
            Logging.debug { "Loaded ${assignments.size} temporary group assignments" }
        } catch (e: Exception) {
            Logging.error("Failed to load temporary group assignments", e)
        }
    }

    /**
     * Loads assignments from the database.
     */
    private suspend fun loadAssignmentsFromStorage(): List<TemporaryGroupAssignment> {
        return storageService.storage.loadAllTempGroupAssignments()
    }

    /**
     * Saves an assignment to persistent storage.
     */
    private suspend fun saveAssignment(assignment: TemporaryGroupAssignment) {
        storageService.storage.saveTempGroupAssignment(assignment)
        Logging.debug { "Saved temporary group assignment for ${assignment.playerUuid}" }
    }

    /**
     * Deletes an assignment from persistent storage.
     */
    private suspend fun deleteAssignment(playerUuid: UUID) {
        storageService.storage.deleteTempGroupAssignment(playerUuid)
        Logging.debug { "Deleted temporary group assignment for $playerUuid" }
    }

    /**
     * Saves all assignments to persistent storage.
     */
    private suspend fun saveAllAssignments() {
        for ((_, assignment) in assignments) {
            saveAssignment(assignment)
        }
        Logging.debug { "Saved ${assignments.size} temporary group assignments" }
    }

    /**
     * Handles a player joining - check if they have a temp assignment.
     */
    suspend fun onPlayerJoin(player: Player) {
        val assignment = assignments[player.uniqueId] ?: return

        if (assignment.isExpired) {
            // Assignment expired while offline
            removeTemporaryGroup(player.uniqueId, TemporaryGroupRemoveEvent.RemovalReason.EXPIRED)
            return
        }

        // Player is still in temp group, notify them
        messageService.send(player, "tempgroup-active",
            "group" to assignment.temporaryGroup,
            "remaining" to assignment.getRemainingTimeString()
        )

        // Ensure they're in the temp group
        val currentGroup = groupService.getGroupForPlayer(player)
        if (currentGroup.name != assignment.temporaryGroup) {
            try {
                val fromGroup = currentGroup.toApiModel()
                val toGroup = groupService.getGroupApi(assignment.temporaryGroup)
                if (toGroup != null) {
                    inventoryService.switchInventory(player, fromGroup, toGroup, InventorySwitchEvent.SwitchReason.API)
                }
            } catch (e: Exception) {
                Logging.error("Failed to restore temp group for ${player.name} on join", e)
            }
        }
    }

    /**
     * Handles a player quitting - ensure assignment is persisted.
     */
    suspend fun onPlayerQuit(player: Player) {
        val assignment = assignments[player.uniqueId] ?: return
        saveAssignment(assignment)
    }
}

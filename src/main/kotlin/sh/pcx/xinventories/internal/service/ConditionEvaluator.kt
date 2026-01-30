package sh.pcx.xinventories.internal.service

import sh.pcx.xinventories.PluginContext
import sh.pcx.xinventories.internal.integration.PlaceholderAPIIntegration
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.model.GroupConditions
import sh.pcx.xinventories.internal.model.TimeRange
import sh.pcx.xinventories.internal.util.CronExpression
import sh.pcx.xinventories.internal.util.Logging
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.bukkit.entity.Player
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Result of a condition evaluation with details about what matched.
 */
data class ConditionEvaluationResult(
    val matches: Boolean,
    val matchedConditions: List<String>,
    val failedConditions: List<String>,
    val evaluationTime: Instant = Instant.now()
) {
    companion object {
        fun success(matched: List<String>): ConditionEvaluationResult {
            return ConditionEvaluationResult(true, matched, emptyList())
        }

        fun failure(failed: List<String>): ConditionEvaluationResult {
            return ConditionEvaluationResult(false, emptyList(), failed)
        }

        fun noConditions(): ConditionEvaluationResult {
            return ConditionEvaluationResult(true, listOf("no conditions"), emptyList())
        }
    }
}

/**
 * Cache key for condition evaluation results.
 */
private data class ConditionCacheKey(
    val playerUuid: UUID,
    val groupName: String
)

/**
 * Service for evaluating group conditions.
 * Handles permission, schedule, cron, and placeholder conditions with caching.
 */
class ConditionEvaluator(private val plugin: PluginContext) {

    // PlaceholderAPI integration
    val placeholderIntegration = PlaceholderAPIIntegration(plugin)

    // Cache for parsed cron expressions
    private val cronCache = ConcurrentHashMap<String, CronExpression?>()

    // Cache for condition evaluation results (short TTL since conditions can change)
    private val evaluationCache: Cache<ConditionCacheKey, ConditionEvaluationResult> = Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(Duration.ofSeconds(30))
        .build()

    /**
     * Evaluates all conditions for a group against a player.
     *
     * @param player The player to evaluate conditions for
     * @param group The group with conditions to evaluate
     * @param useCache Whether to use cached results (default: true)
     * @return ConditionEvaluationResult with match status and details
     */
    fun evaluateConditions(
        player: Player,
        group: Group,
        useCache: Boolean = true
    ): ConditionEvaluationResult {
        val conditions = group.conditions ?: return ConditionEvaluationResult.noConditions()

        if (!conditions.hasConditions()) {
            return ConditionEvaluationResult.noConditions()
        }

        // Check cache
        val cacheKey = ConditionCacheKey(player.uniqueId, group.name)
        if (useCache) {
            evaluationCache.getIfPresent(cacheKey)?.let { return it }
        }

        // Evaluate conditions
        val result = evaluateConditionsInternal(player, conditions)

        // Cache result
        evaluationCache.put(cacheKey, result)

        Logging.debug {
            "Condition evaluation for ${player.name} -> ${group.name}: " +
            "matches=${result.matches}, matched=${result.matchedConditions}, failed=${result.failedConditions}"
        }

        return result
    }

    /**
     * Internal condition evaluation logic.
     */
    private fun evaluateConditionsInternal(
        player: Player,
        conditions: GroupConditions
    ): ConditionEvaluationResult {
        val matchedConditions = mutableListOf<String>()
        val failedConditions = mutableListOf<String>()

        // Evaluate permission condition
        conditions.permission?.let { permission ->
            if (player.hasPermission(permission)) {
                matchedConditions.add("permission:$permission")
            } else {
                failedConditions.add("permission:$permission")
            }
        }

        // Evaluate schedule conditions (any matching range counts as success)
        conditions.schedule?.takeIf { it.isNotEmpty() }?.let { ranges ->
            val now = Instant.now()
            val activeRange = ranges.find { it.isActive(now) }
            if (activeRange != null) {
                matchedConditions.add("schedule:active")
            } else {
                failedConditions.add("schedule:not_active")
            }
        }

        // Evaluate cron condition
        conditions.cron?.let { cronStr ->
            val cronExpr = getCronExpression(cronStr)
            if (cronExpr != null && cronExpr.matches()) {
                matchedConditions.add("cron:$cronStr")
            } else if (cronExpr == null) {
                failedConditions.add("cron:invalid_expression")
            } else {
                failedConditions.add("cron:not_matching")
            }
        }

        // Evaluate placeholder conditions
        val allPlaceholderConditions = conditions.getAllPlaceholderConditions()
        if (allPlaceholderConditions.isNotEmpty()) {
            if (placeholderIntegration.isAvailable()) {
                for (condition in allPlaceholderConditions) {
                    if (placeholderIntegration.evaluateCondition(player, condition)) {
                        matchedConditions.add("placeholder:${condition.toDisplayString()}")
                    } else {
                        failedConditions.add("placeholder:${condition.toDisplayString()}")
                    }
                }
            } else {
                failedConditions.add("placeholder:papi_unavailable")
            }
        }

        // Determine final result based on requireAll setting
        val hasMatches = matchedConditions.isNotEmpty()
        val hasFailures = failedConditions.isNotEmpty()

        val matches = if (conditions.requireAll) {
            // AND logic: all conditions must pass (no failures)
            hasMatches && !hasFailures
        } else {
            // OR logic: any condition passing is sufficient
            hasMatches
        }

        return ConditionEvaluationResult(matches, matchedConditions, failedConditions)
    }

    /**
     * Evaluates only the permission condition.
     */
    fun evaluatePermission(player: Player, permission: String?): Boolean {
        if (permission == null) return true
        return player.hasPermission(permission)
    }

    /**
     * Evaluates only the schedule condition.
     */
    fun evaluateSchedule(schedule: List<TimeRange>?, now: Instant = Instant.now()): Boolean {
        if (schedule.isNullOrEmpty()) return true
        return schedule.any { it.isActive(now) }
    }

    /**
     * Evaluates only the cron condition.
     */
    fun evaluateCron(cronStr: String?, now: ZonedDateTime = ZonedDateTime.now()): Boolean {
        if (cronStr == null) return true
        val cronExpr = getCronExpression(cronStr) ?: return false
        return cronExpr.matches(now)
    }

    /**
     * Gets a cached or newly parsed cron expression.
     */
    private fun getCronExpression(cronStr: String): CronExpression? {
        return cronCache.computeIfAbsent(cronStr) {
            CronExpression.parse(it).also { result ->
                if (result == null) {
                    Logging.warning("Invalid cron expression: $cronStr")
                }
            }
        }
    }

    /**
     * Invalidates the cache for a specific player.
     */
    fun invalidateCache(playerUuid: UUID) {
        evaluationCache.asMap().keys.removeIf { it.playerUuid == playerUuid }
    }

    /**
     * Invalidates the cache for a specific player and group.
     */
    fun invalidateCache(playerUuid: UUID, groupName: String) {
        evaluationCache.invalidate(ConditionCacheKey(playerUuid, groupName))
    }

    /**
     * Clears all cached evaluation results.
     */
    fun clearCache() {
        evaluationCache.invalidateAll()
    }

    /**
     * Gets a description of why a player matched (or didn't match) a group.
     */
    fun getMatchReason(player: Player, group: Group): String {
        val conditions = group.conditions

        if (conditions == null || !conditions.hasConditions()) {
            return "No conditions defined - matches by world/pattern"
        }

        val result = evaluateConditions(player, group, useCache = false)

        return if (result.matches) {
            "Matched conditions: ${result.matchedConditions.joinToString(", ")}"
        } else {
            "Failed conditions: ${result.failedConditions.joinToString(", ")}"
        }
    }

    /**
     * Gets all active conditions for a player across all groups.
     */
    fun getActiveConditionsForPlayer(player: Player): Map<String, ConditionEvaluationResult> {
        val groupService = plugin.serviceManager.groupService
        val results = mutableMapOf<String, ConditionEvaluationResult>()

        for (group in groupService.getAllGroups()) {
            if (group.conditions?.hasConditions() == true) {
                results[group.name] = evaluateConditions(player, group, useCache = false)
            }
        }

        return results
    }

    /**
     * Reloads the evaluator, clearing caches and re-checking PlaceholderAPI.
     */
    fun reload() {
        clearCache()
        cronCache.clear()
        placeholderIntegration.checkAvailability()
    }
}

package sh.pcx.xinventories.internal.serializer

import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import sh.pcx.xinventories.internal.model.PlayerAdvancements
import sh.pcx.xinventories.internal.util.Logging

/**
 * Serializes and deserializes player advancements for storage.
 *
 * Advancements are stored as a set of NamespacedKey strings representing
 * completed advancements.
 */
object AdvancementSerializer {

    /**
     * Collects all completed advancements from a player.
     *
     * @param player The player to collect advancements from
     * @return PlayerAdvancements containing all completed advancement keys
     */
    fun collectFromPlayer(player: Player): PlayerAdvancements {
        val completed = mutableSetOf<String>()

        val iterator = Bukkit.advancementIterator()
        while (iterator.hasNext()) {
            val advancement = iterator.next()
            val progress = player.getAdvancementProgress(advancement)

            if (progress.isDone) {
                completed.add(advancement.key.toString())
            }
        }

        return PlayerAdvancements(completed)
    }

    /**
     * Applies advancements to a player, granting completed ones and revoking others.
     *
     * @param player The player to apply advancements to
     * @param advancements The advancements to apply
     */
    fun applyToPlayer(player: Player, advancements: PlayerAdvancements) {
        val iterator = Bukkit.advancementIterator()
        while (iterator.hasNext()) {
            val advancement = iterator.next()
            val advancementKey = advancement.key.toString()
            val progress = player.getAdvancementProgress(advancement)

            val shouldBeCompleted = advancements.isCompleted(advancementKey)
            val isCurrentlyCompleted = progress.isDone

            if (shouldBeCompleted && !isCurrentlyCompleted) {
                // Grant all criteria to complete the advancement
                try {
                    advancement.criteria.forEach { criterion ->
                        progress.awardCriteria(criterion)
                    }
                } catch (e: Exception) {
                    Logging.debug { "Failed to grant advancement $advancementKey: ${e.message}" }
                }
            } else if (!shouldBeCompleted && isCurrentlyCompleted) {
                // Revoke all criteria to reset the advancement
                try {
                    advancement.criteria.forEach { criterion ->
                        progress.revokeCriteria(criterion)
                    }
                } catch (e: Exception) {
                    Logging.debug { "Failed to revoke advancement $advancementKey: ${e.message}" }
                }
            }
        }
    }

    /**
     * Clears all advancements for a player.
     *
     * @param player The player to clear advancements for
     */
    fun clearPlayerAdvancements(player: Player) {
        val iterator = Bukkit.advancementIterator()
        while (iterator.hasNext()) {
            val advancement = iterator.next()
            val progress = player.getAdvancementProgress(advancement)

            if (progress.isDone || progress.awardedCriteria.isNotEmpty()) {
                try {
                    advancement.criteria.forEach { criterion ->
                        progress.revokeCriteria(criterion)
                    }
                } catch (e: Exception) {
                    Logging.debug { "Failed to clear advancement ${advancement.key}: ${e.message}" }
                }
            }
        }
    }

    /**
     * Serializes advancements to a list format for YAML storage.
     *
     * @param advancements The advancements to serialize
     * @return List of advancement key strings
     */
    fun serializeToList(advancements: PlayerAdvancements): List<String> {
        return advancements.completedAdvancements.toList()
    }

    /**
     * Deserializes advancements from a list format.
     *
     * @param data The list data from YAML
     * @return PlayerAdvancements object
     */
    fun deserializeFromList(data: List<String>?): PlayerAdvancements {
        if (data == null) return PlayerAdvancements.empty()
        return PlayerAdvancements(data.toSet())
    }

    /**
     * Serializes advancements to a compact string format for SQL storage.
     *
     * Format: key1;key2;key3;...
     *
     * @param advancements The advancements to serialize
     * @return Compact string representation
     */
    fun serializeToString(advancements: PlayerAdvancements): String {
        if (advancements.isEmpty()) return ""
        return advancements.completedAdvancements.joinToString(";")
    }

    /**
     * Deserializes advancements from a compact string format.
     *
     * @param data The string data from SQL
     * @return PlayerAdvancements object
     */
    fun deserializeFromString(data: String?): PlayerAdvancements {
        if (data.isNullOrBlank()) return PlayerAdvancements.empty()

        val keys = data.split(";")
            .filter { it.isNotBlank() }
            .toSet()

        return PlayerAdvancements(keys)
    }

    /**
     * Validates an advancement key by checking if the advancement exists.
     *
     * @param key The advancement key to validate
     * @return True if the advancement exists
     */
    fun isValidAdvancementKey(key: String): Boolean {
        return try {
            val namespacedKey = NamespacedKey.fromString(key)
            namespacedKey != null && Bukkit.getAdvancement(namespacedKey) != null
        } catch (e: Exception) {
            false
        }
    }
}

package sh.pcx.xinventories.internal.serializer

import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import sh.pcx.xinventories.internal.model.PlayerStatistics
import sh.pcx.xinventories.internal.util.Logging

/**
 * Serializes and deserializes player statistics for storage.
 *
 * Statistics are stored in the format: STATISTIC_TYPE or STATISTIC_TYPE:SUBTYPE
 * where SUBTYPE can be a Material or EntityType name depending on the statistic type.
 */
object StatisticsSerializer {

    /**
     * Collects all statistics from a player into a PlayerStatistics object.
     *
     * @param player The player to collect statistics from
     * @return PlayerStatistics containing all the player's statistics
     */
    fun collectFromPlayer(player: Player): PlayerStatistics {
        val stats = mutableMapOf<String, Int>()

        for (statistic in Statistic.entries) {
            try {
                when (statistic.type) {
                    Statistic.Type.UNTYPED -> {
                        val value = player.getStatistic(statistic)
                        if (value > 0) {
                            stats[statistic.name] = value
                        }
                    }
                    Statistic.Type.BLOCK, Statistic.Type.ITEM -> {
                        // Iterate through all materials for block/item statistics
                        for (material in Material.entries) {
                            if (material.isLegacy) continue
                            if (statistic.type == Statistic.Type.BLOCK && !material.isBlock) continue
                            if (statistic.type == Statistic.Type.ITEM && !material.isItem) continue

                            try {
                                val value = player.getStatistic(statistic, material)
                                if (value > 0) {
                                    stats["${statistic.name}:${material.name}"] = value
                                }
                            } catch (e: IllegalArgumentException) {
                                // Material not applicable for this statistic, skip
                            }
                        }
                    }
                    Statistic.Type.ENTITY -> {
                        // Iterate through all entity types for entity statistics
                        for (entityType in EntityType.entries) {
                            if (!entityType.isAlive) continue
                            try {
                                val value = player.getStatistic(statistic, entityType)
                                if (value > 0) {
                                    stats["${statistic.name}:${entityType.name}"] = value
                                }
                            } catch (e: IllegalArgumentException) {
                                // EntityType not applicable for this statistic, skip
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logging.debug { "Failed to collect statistic ${statistic.name}: ${e.message}" }
            }
        }

        return PlayerStatistics(stats)
    }

    /**
     * Applies statistics from a PlayerStatistics object to a player.
     *
     * @param player The player to apply statistics to
     * @param statistics The statistics to apply
     */
    fun applyToPlayer(player: Player, statistics: PlayerStatistics) {
        // First, clear all existing statistics
        clearPlayerStatistics(player)

        // Then apply the saved statistics
        for ((key, value) in statistics.statistics) {
            try {
                applyStatistic(player, key, value)
            } catch (e: Exception) {
                Logging.debug { "Failed to apply statistic $key=$value: ${e.message}" }
            }
        }
    }

    /**
     * Clears all statistics for a player.
     *
     * @param player The player to clear statistics for
     */
    fun clearPlayerStatistics(player: Player) {
        for (statistic in Statistic.entries) {
            try {
                when (statistic.type) {
                    Statistic.Type.UNTYPED -> {
                        player.setStatistic(statistic, 0)
                    }
                    Statistic.Type.BLOCK, Statistic.Type.ITEM -> {
                        for (material in Material.entries) {
                            if (material.isLegacy) continue
                            if (statistic.type == Statistic.Type.BLOCK && !material.isBlock) continue
                            if (statistic.type == Statistic.Type.ITEM && !material.isItem) continue
                            try {
                                player.setStatistic(statistic, material, 0)
                            } catch (e: IllegalArgumentException) {
                                // Material not applicable, skip
                            }
                        }
                    }
                    Statistic.Type.ENTITY -> {
                        for (entityType in EntityType.entries) {
                            if (!entityType.isAlive) continue
                            try {
                                player.setStatistic(statistic, entityType, 0)
                            } catch (e: IllegalArgumentException) {
                                // EntityType not applicable, skip
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logging.debug { "Failed to clear statistic ${statistic.name}: ${e.message}" }
            }
        }
    }

    /**
     * Applies a single statistic to a player.
     *
     * @param player The player
     * @param key The statistic key (format: STATISTIC or STATISTIC:SUBTYPE)
     * @param value The value to set
     */
    private fun applyStatistic(player: Player, key: String, value: Int) {
        val parts = key.split(":", limit = 2)
        val statisticName = parts[0]

        val statistic = try {
            Statistic.valueOf(statisticName)
        } catch (e: IllegalArgumentException) {
            Logging.debug { "Unknown statistic: $statisticName" }
            return
        }

        when (statistic.type) {
            Statistic.Type.UNTYPED -> {
                player.setStatistic(statistic, value)
            }
            Statistic.Type.BLOCK, Statistic.Type.ITEM -> {
                if (parts.size < 2) {
                    Logging.debug { "Missing material for block/item statistic: $key" }
                    return
                }
                val material = try {
                    Material.valueOf(parts[1])
                } catch (e: IllegalArgumentException) {
                    Logging.debug { "Unknown material: ${parts[1]}" }
                    return
                }
                player.setStatistic(statistic, material, value)
            }
            Statistic.Type.ENTITY -> {
                if (parts.size < 2) {
                    Logging.debug { "Missing entity type for entity statistic: $key" }
                    return
                }
                val entityType = try {
                    EntityType.valueOf(parts[1])
                } catch (e: IllegalArgumentException) {
                    Logging.debug { "Unknown entity type: ${parts[1]}" }
                    return
                }
                player.setStatistic(statistic, entityType, value)
            }
        }
    }

    /**
     * Serializes statistics to a map format for YAML storage.
     *
     * @param statistics The statistics to serialize
     * @return Map suitable for YAML serialization
     */
    fun serializeToMap(statistics: PlayerStatistics): Map<String, Int> {
        return statistics.statistics
    }

    /**
     * Deserializes statistics from a map format.
     *
     * @param data The map data from YAML
     * @return PlayerStatistics object
     */
    @Suppress("UNCHECKED_CAST")
    fun deserializeFromMap(data: Map<String, Any>?): PlayerStatistics {
        if (data == null) return PlayerStatistics.empty()

        val stats = data.mapNotNull { (key, value) ->
            val intValue = when (value) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull()
                else -> null
            }
            if (intValue != null) key to intValue else null
        }.toMap()

        return PlayerStatistics(stats)
    }

    /**
     * Serializes statistics to a compact string format for SQL storage.
     *
     * Format: key1=value1;key2=value2;...
     *
     * @param statistics The statistics to serialize
     * @return Compact string representation
     */
    fun serializeToString(statistics: PlayerStatistics): String {
        if (statistics.isEmpty()) return ""

        return statistics.statistics.entries.joinToString(";") { (key, value) ->
            "$key=$value"
        }
    }

    /**
     * Deserializes statistics from a compact string format.
     *
     * @param data The string data from SQL
     * @return PlayerStatistics object
     */
    fun deserializeFromString(data: String?): PlayerStatistics {
        if (data.isNullOrBlank()) return PlayerStatistics.empty()

        val stats = data.split(";")
            .filter { it.contains("=") }
            .mapNotNull { entry ->
                val parts = entry.split("=", limit = 2)
                val key = parts[0]
                val value = parts.getOrNull(1)?.toIntOrNull()
                if (value != null) key to value else null
            }
            .toMap()

        return PlayerStatistics(stats)
    }
}

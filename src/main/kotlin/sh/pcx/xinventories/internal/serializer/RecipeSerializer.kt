package sh.pcx.xinventories.internal.serializer

import org.bukkit.Bukkit
import org.bukkit.Keyed
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import sh.pcx.xinventories.internal.util.Logging

/**
 * Serializes and deserializes player discovered recipes for storage.
 *
 * Recipes are stored as a set of NamespacedKey strings representing
 * recipes the player has discovered/unlocked.
 */
object RecipeSerializer {

    /**
     * Collects all discovered recipes from a player.
     *
     * @param player The player to collect recipes from
     * @return Set of discovered recipe keys (NamespacedKey format)
     */
    fun collectFromPlayer(player: Player): Set<String> {
        val discovered = mutableSetOf<String>()

        // Iterate through all server recipes
        val recipeIterator = Bukkit.recipeIterator()
        while (recipeIterator.hasNext()) {
            val recipe = recipeIterator.next()

            // Only Keyed recipes have NamespacedKeys
            if (recipe is Keyed) {
                val key = recipe.key
                // Check if player has discovered this recipe
                if (player.hasDiscoveredRecipe(key)) {
                    discovered.add(key.toString())
                }
            }
        }

        return discovered
    }

    /**
     * Applies discovered recipes to a player.
     *
     * This will:
     * 1. Undiscover all recipes the player currently knows
     * 2. Discover all recipes in the provided set
     *
     * @param player The player to apply recipes to
     * @param recipes The set of recipe keys to apply
     */
    fun applyToPlayer(player: Player, recipes: Set<String>) {
        // First, undiscover all current recipes
        clearPlayerRecipes(player)

        // Then discover the saved recipes
        for (keyString in recipes) {
            try {
                val key = NamespacedKey.fromString(keyString) ?: continue
                player.discoverRecipe(key)
            } catch (e: Exception) {
                Logging.debug { "Failed to discover recipe $keyString: ${e.message}" }
            }
        }
    }

    /**
     * Clears all discovered recipes for a player.
     *
     * @param player The player to clear recipes for
     */
    fun clearPlayerRecipes(player: Player) {
        val recipeIterator = Bukkit.recipeIterator()
        while (recipeIterator.hasNext()) {
            val recipe = recipeIterator.next()

            if (recipe is Keyed) {
                val key = recipe.key
                try {
                    if (player.hasDiscoveredRecipe(key)) {
                        player.undiscoverRecipe(key)
                    }
                } catch (e: Exception) {
                    Logging.debug { "Failed to undiscover recipe ${key}: ${e.message}" }
                }
            }
        }
    }

    /**
     * Serializes recipes to a list format for YAML storage.
     *
     * @param recipes The recipe keys to serialize
     * @return List of recipe key strings
     */
    fun serializeToList(recipes: Set<String>): List<String> {
        return recipes.toList()
    }

    /**
     * Deserializes recipes from a list format.
     *
     * @param data The list data from YAML
     * @return Set of recipe key strings
     */
    fun deserializeFromList(data: List<String>?): Set<String> {
        if (data == null) return emptySet()
        return data.toSet()
    }

    /**
     * Serializes recipes to a compact string format for SQL storage.
     *
     * Format: key1;key2;key3;...
     *
     * @param recipes The recipe keys to serialize
     * @return Compact string representation
     */
    fun serializeToString(recipes: Set<String>): String {
        if (recipes.isEmpty()) return ""
        return recipes.joinToString(";")
    }

    /**
     * Deserializes recipes from a compact string format.
     *
     * @param data The string data from SQL
     * @return Set of recipe key strings
     */
    fun deserializeFromString(data: String?): Set<String> {
        if (data.isNullOrBlank()) return emptySet()

        return data.split(";")
            .filter { it.isNotBlank() }
            .toSet()
    }

    /**
     * Validates a recipe key by checking if the recipe exists.
     *
     * @param key The recipe key to validate
     * @return True if the recipe exists
     */
    fun isValidRecipeKey(key: String): Boolean {
        return try {
            val namespacedKey = NamespacedKey.fromString(key)
            if (namespacedKey == null) return false

            // Check if recipe exists by iterating
            val recipeIterator = Bukkit.recipeIterator()
            while (recipeIterator.hasNext()) {
                val recipe = recipeIterator.next()
                if (recipe is Keyed && recipe.key == namespacedKey) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}

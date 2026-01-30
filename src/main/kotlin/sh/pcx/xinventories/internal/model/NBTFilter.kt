package sh.pcx.xinventories.internal.model

/**
 * Represents an NBT-based item filter for advanced item restriction.
 *
 * NBT filters allow restricting items based on their metadata properties
 * such as enchantments, custom model data, display names, and lore.
 *
 * @property type The type of NBT filter (enchantment, custom model data, etc.)
 * @property action The action to take when the filter matches
 * @property enchantment The enchantment to filter (for ENCHANTMENT type)
 * @property minLevel Minimum enchantment level (inclusive)
 * @property maxLevel Maximum enchantment level (inclusive)
 * @property customModelData Set of custom model data values to match (for CUSTOM_MODEL_DATA type)
 * @property namePattern Regex pattern to match display name (for DISPLAY_NAME type)
 * @property lorePattern Regex pattern to match lore lines (for LORE type)
 * @property attributeName Attribute modifier name to filter (for ATTRIBUTE type)
 */
data class NBTFilter(
    val type: NBTFilterType,
    val action: FilterAction,
    val enchantment: String? = null,
    val minLevel: Int? = null,
    val maxLevel: Int? = null,
    val customModelData: Set<Int>? = null,
    val namePattern: String? = null,
    val lorePattern: String? = null,
    val attributeName: String? = null
) {
    /**
     * Compiled regex pattern for name matching.
     */
    val compiledNamePattern: Regex? by lazy {
        namePattern?.let {
            try {
                Regex(it, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Compiled regex pattern for lore matching.
     */
    val compiledLorePattern: Regex? by lazy {
        lorePattern?.let {
            try {
                Regex(it, RegexOption.IGNORE_CASE)
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Validates this filter configuration.
     *
     * @return A list of validation errors, empty if valid
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()

        when (type) {
            NBTFilterType.ENCHANTMENT -> {
                if (enchantment.isNullOrBlank()) {
                    errors.add("Enchantment filter requires 'enchantment' field")
                }
            }
            NBTFilterType.CUSTOM_MODEL_DATA -> {
                if (customModelData.isNullOrEmpty()) {
                    errors.add("Custom model data filter requires 'values' field")
                }
            }
            NBTFilterType.DISPLAY_NAME -> {
                if (namePattern.isNullOrBlank()) {
                    errors.add("Display name filter requires 'pattern' field")
                } else if (compiledNamePattern == null) {
                    errors.add("Invalid regex pattern for display name: $namePattern")
                }
            }
            NBTFilterType.LORE -> {
                if (lorePattern.isNullOrBlank()) {
                    errors.add("Lore filter requires 'pattern' field")
                } else if (compiledLorePattern == null) {
                    errors.add("Invalid regex pattern for lore: $lorePattern")
                }
            }
            NBTFilterType.ATTRIBUTE -> {
                if (attributeName.isNullOrBlank()) {
                    errors.add("Attribute filter requires 'attribute' field")
                }
            }
        }

        return errors
    }

    /**
     * Returns a human-readable description of this filter.
     */
    fun toDisplayString(): String {
        return when (type) {
            NBTFilterType.ENCHANTMENT -> {
                val levelRange = when {
                    minLevel != null && maxLevel != null -> "level $minLevel-$maxLevel"
                    minLevel != null -> "level >= $minLevel"
                    maxLevel != null -> "level <= $maxLevel"
                    else -> "any level"
                }
                "Enchantment: $enchantment ($levelRange) -> ${action.name}"
            }
            NBTFilterType.CUSTOM_MODEL_DATA -> {
                "Custom Model Data: ${customModelData?.joinToString(", ")} -> ${action.name}"
            }
            NBTFilterType.DISPLAY_NAME -> {
                "Display Name: /$namePattern/ -> ${action.name}"
            }
            NBTFilterType.LORE -> {
                "Lore: /$lorePattern/ -> ${action.name}"
            }
            NBTFilterType.ATTRIBUTE -> {
                "Attribute: $attributeName -> ${action.name}"
            }
        }
    }

    companion object {
        /**
         * Creates an enchantment filter.
         */
        fun enchantment(
            enchantment: String,
            action: FilterAction,
            minLevel: Int? = null,
            maxLevel: Int? = null
        ): NBTFilter = NBTFilter(
            type = NBTFilterType.ENCHANTMENT,
            action = action,
            enchantment = enchantment,
            minLevel = minLevel,
            maxLevel = maxLevel
        )

        /**
         * Creates a custom model data filter.
         */
        fun customModelData(
            values: Set<Int>,
            action: FilterAction
        ): NBTFilter = NBTFilter(
            type = NBTFilterType.CUSTOM_MODEL_DATA,
            action = action,
            customModelData = values
        )

        /**
         * Creates a display name pattern filter.
         */
        fun displayName(
            pattern: String,
            action: FilterAction
        ): NBTFilter = NBTFilter(
            type = NBTFilterType.DISPLAY_NAME,
            action = action,
            namePattern = pattern
        )

        /**
         * Creates a lore pattern filter.
         */
        fun lore(
            pattern: String,
            action: FilterAction
        ): NBTFilter = NBTFilter(
            type = NBTFilterType.LORE,
            action = action,
            lorePattern = pattern
        )

        /**
         * Creates an attribute filter.
         */
        fun attribute(
            attributeName: String,
            action: FilterAction
        ): NBTFilter = NBTFilter(
            type = NBTFilterType.ATTRIBUTE,
            action = action,
            attributeName = attributeName
        )
    }
}

/**
 * Types of NBT filters.
 */
enum class NBTFilterType {
    /** Filter by enchantment and level */
    ENCHANTMENT,

    /** Filter by custom model data value */
    CUSTOM_MODEL_DATA,

    /** Filter by display name pattern */
    DISPLAY_NAME,

    /** Filter by lore content pattern */
    LORE,

    /** Filter by attribute modifier */
    ATTRIBUTE
}

/**
 * Actions that can be taken when a filter matches.
 */
enum class FilterAction {
    /** Allow the item (used in whitelist mode) */
    ALLOW,

    /** Remove the item silently */
    REMOVE,

    /** Drop the item on the ground */
    DROP
}

/**
 * Result of checking an item against NBT filters.
 */
data class NBTFilterResult(
    val matched: Boolean,
    val filter: NBTFilter?,
    val matchReason: String?
) {
    companion object {
        /** No filter matched - item is allowed */
        val NO_MATCH = NBTFilterResult(false, null, null)

        /** Create a matched result */
        fun matched(filter: NBTFilter, reason: String) = NBTFilterResult(true, filter, reason)
    }
}

/**
 * Configuration for NBT filters in a group.
 */
data class NBTFilterConfig(
    /** Whether NBT filtering is enabled */
    val enabled: Boolean = false,

    /** List of NBT filters to apply */
    val filters: List<NBTFilter> = emptyList()
) {
    /**
     * Validates all filters in this configuration.
     *
     * @return Map of filter index to validation errors
     */
    fun validate(): Map<Int, List<String>> {
        val errors = mutableMapOf<Int, List<String>>()
        filters.forEachIndexed { index, filter ->
            val filterErrors = filter.validate()
            if (filterErrors.isNotEmpty()) {
                errors[index] = filterErrors
            }
        }
        return errors
    }

    /**
     * Gets all filters that would ALLOW items.
     */
    fun getAllowFilters(): List<NBTFilter> = filters.filter { it.action == FilterAction.ALLOW }

    /**
     * Gets all filters that would REMOVE items.
     */
    fun getRemoveFilters(): List<NBTFilter> = filters.filter { it.action == FilterAction.REMOVE }

    /**
     * Gets all filters that would DROP items.
     */
    fun getDropFilters(): List<NBTFilter> = filters.filter { it.action == FilterAction.DROP }

    companion object {
        /** Disabled NBT filter config */
        val DISABLED = NBTFilterConfig(enabled = false)
    }
}

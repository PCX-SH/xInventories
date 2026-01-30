package sh.pcx.xinventories.api.model

/**
 * Settings for a group.
 *
 * These settings control what player data is saved and loaded
 * when switching between inventory groups.
 */
data class GroupSettings(
        val saveHealth: Boolean = true,
        val saveHunger: Boolean = true,
        val saveSaturation: Boolean = true,
        val saveExhaustion: Boolean = true,
        val saveExperience: Boolean = true,
        val savePotionEffects: Boolean = true,
        val saveEnderChest: Boolean = true,
        val saveGameMode: Boolean = false,
        val separateGameModeInventories: Boolean = true,
        val clearOnDeath: Boolean = false,
        val clearOnJoin: Boolean = false,
        val separateEconomy: Boolean = true,
        /**
         * Whether to save and restore player statistics per group.
         * Default is false (opt-in) because statistics can be large and may impact performance.
         */
        val saveStatistics: Boolean = false,
        /**
         * Whether to save and restore player advancements per group.
         * Default is false (opt-in) because this changes advancement progress on world switch.
         */
        val saveAdvancements: Boolean = false,
        /**
         * Whether to save and restore discovered recipes per group.
         * Default is false (opt-in) because this changes recipe book on world switch.
         */
        val saveRecipes: Boolean = false
) {
    /**
     * Merges this settings with child settings.
     *
     * Child settings override parent settings. This method creates a new
     * GroupSettings where explicitly set child values take precedence.
     *
     * @param child The child settings to merge on top of these settings
     * @param childExplicit Optional set of field names that were explicitly set in the child
     * @return A new GroupSettings with merged values
     */
    fun merge(child: GroupSettings, childExplicit: Set<String>? = null): GroupSettings {
        // If we don't know which fields were explicitly set, child always wins
        if (childExplicit == null) {
            return child
        }

        // Merge each field, using child value if explicitly set, parent otherwise
        return GroupSettings(
            saveHealth = if ("saveHealth" in childExplicit) child.saveHealth else this.saveHealth,
            saveHunger = if ("saveHunger" in childExplicit) child.saveHunger else this.saveHunger,
            saveSaturation = if ("saveSaturation" in childExplicit) child.saveSaturation else this.saveSaturation,
            saveExhaustion = if ("saveExhaustion" in childExplicit) child.saveExhaustion else this.saveExhaustion,
            saveExperience = if ("saveExperience" in childExplicit) child.saveExperience else this.saveExperience,
            savePotionEffects = if ("savePotionEffects" in childExplicit) child.savePotionEffects else this.savePotionEffects,
            saveEnderChest = if ("saveEnderChest" in childExplicit) child.saveEnderChest else this.saveEnderChest,
            saveGameMode = if ("saveGameMode" in childExplicit) child.saveGameMode else this.saveGameMode,
            separateGameModeInventories = if ("separateGameModeInventories" in childExplicit) child.separateGameModeInventories else this.separateGameModeInventories,
            clearOnDeath = if ("clearOnDeath" in childExplicit) child.clearOnDeath else this.clearOnDeath,
            clearOnJoin = if ("clearOnJoin" in childExplicit) child.clearOnJoin else this.clearOnJoin,
            separateEconomy = if ("separateEconomy" in childExplicit) child.separateEconomy else this.separateEconomy,
            saveStatistics = if ("saveStatistics" in childExplicit) child.saveStatistics else this.saveStatistics,
            saveAdvancements = if ("saveAdvancements" in childExplicit) child.saveAdvancements else this.saveAdvancements,
            saveRecipes = if ("saveRecipes" in childExplicit) child.saveRecipes else this.saveRecipes
        )
    }

    /**
     * Simple merge where child settings always override parent.
     *
     * This is equivalent to just returning the child settings, but is provided
     * for consistency with the inheritance pattern.
     *
     * @param child The child settings
     * @return The child settings (child always wins)
     */
    fun mergeWith(child: GroupSettings): GroupSettings = child

    companion object {
        /**
         * Default settings with all features enabled.
         */
        val DEFAULT = GroupSettings()

        /**
         * Minimal settings for a lightweight group.
         */
        val MINIMAL = GroupSettings(
            saveHealth = false,
            saveHunger = false,
            saveSaturation = false,
            saveExhaustion = false,
            saveExperience = false,
            savePotionEffects = false,
            saveEnderChest = false,
            saveGameMode = false,
            separateGameModeInventories = false,
            clearOnDeath = false,
            clearOnJoin = false,
            separateEconomy = false,
            saveStatistics = false,
            saveAdvancements = false,
            saveRecipes = false
        )
    }
}

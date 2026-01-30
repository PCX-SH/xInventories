package sh.pcx.xinventories.api.model

/** Settings for a group. */
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
        val separateEconomy: Boolean = true
)

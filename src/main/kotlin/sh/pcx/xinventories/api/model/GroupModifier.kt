package sh.pcx.xinventories.api.model

/**
 * Modifier for group editing.
 */
interface GroupModifier {
    fun addWorld(world: String)
    fun removeWorld(world: String)
    fun setWorlds(worlds: Set<String>)
    fun addPattern(pattern: String)
    fun removePattern(pattern: String)
    fun setPatterns(patterns: List<String>)
    fun setPriority(priority: Int)
    fun setParent(parent: String?)
    fun modifySettings(modifier: GroupSettings.() -> GroupSettings)
}

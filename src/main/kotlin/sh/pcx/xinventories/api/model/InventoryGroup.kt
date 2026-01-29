package sh.pcx.xinventories.api.model

/**
 * Represents an inventory group.
 */
data class InventoryGroup(
    val name: String,
    val worlds: Set<String>,
    val patterns: List<String>,
    val priority: Int,
    val parent: String?,
    val settings: GroupSettings,
    val isDefault: Boolean
)

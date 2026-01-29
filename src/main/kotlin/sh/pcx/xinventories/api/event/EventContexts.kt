package sh.pcx.xinventories.api.event

import sh.pcx.xinventories.api.model.InventoryGroup
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import org.bukkit.World
import org.bukkit.entity.Player

data class InventorySwitchContext(
    val player: Player,
    val fromGroup: InventoryGroup,
    val toGroup: InventoryGroup,
    val fromWorld: World,
    val toWorld: World,
    val snapshot: PlayerInventorySnapshot
)

data class InventorySaveContext(
    val player: Player,
    val group: InventoryGroup,
    val snapshot: PlayerInventorySnapshot,
    val async: Boolean
)

data class InventoryLoadContext(
    val player: Player,
    val group: InventoryGroup,
    val snapshot: PlayerInventorySnapshot,
    val reason: InventoryLoadEvent.LoadReason
)

data class GroupChangeContext(
    val player: Player,
    val oldGroup: InventoryGroup?,
    val newGroup: InventoryGroup
)

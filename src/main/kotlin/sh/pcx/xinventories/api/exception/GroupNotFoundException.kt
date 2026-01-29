package sh.pcx.xinventories.api.exception

/**
 * Exception thrown when a group is not found.
 */
class GroupNotFoundException(
    val groupName: String
) : RuntimeException("Group '$groupName' not found")

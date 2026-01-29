package sh.pcx.xinventories.api.exception

/**
 * Exception thrown when a migration operation fails.
 */
class MigrationException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

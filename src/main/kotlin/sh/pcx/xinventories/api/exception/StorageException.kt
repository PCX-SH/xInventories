package sh.pcx.xinventories.api.exception

/**
 * Exception thrown when a storage operation fails.
 */
class StorageException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

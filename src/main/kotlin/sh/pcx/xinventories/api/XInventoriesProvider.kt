package sh.pcx.xinventories.api

/**
 * Static accessor for the xInventories API.
 *
 * Usage:
 * ```kotlin
 * val api = XInventoriesProvider.get()
 * api.getPlayerData(player, "survival")
 * ```
 *
 * Java usage:
 * ```java
 * XInventoriesAPI api = XInventoriesProvider.get();
 * api.getPlayerData(player, "survival");
 * ```
 */
object XInventoriesProvider {

    private var instance: XInventoriesAPI? = null

    /**
     * Gets the API instance.
     * @throws IllegalStateException if xInventories is not loaded
     */
    @JvmStatic
    fun get(): XInventoriesAPI {
        return instance ?: throw IllegalStateException(
            "xInventories API is not available. " +
            "Make sure xInventories is installed and your plugin depends on it."
        )
    }

    /**
     * Gets the API instance, or null if not available.
     */
    @JvmStatic
    fun getOrNull(): XInventoriesAPI? = instance

    /**
     * Checks if the API is available.
     */
    @JvmStatic
    fun isAvailable(): Boolean = instance != null

    /**
     * Registers the API instance. Internal use only.
     */
    @JvmSynthetic
    internal fun register(api: XInventoriesAPI) {
        check(instance == null) { "API already registered" }
        instance = api
    }

    /**
     * Unregisters the API instance. Internal use only.
     */
    @JvmSynthetic
    internal fun unregister() {
        instance = null
    }
}

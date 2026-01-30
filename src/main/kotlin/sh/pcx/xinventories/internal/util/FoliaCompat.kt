package sh.pcx.xinventories.internal.util

import org.bukkit.Bukkit

/**
 * Compatibility layer for detecting and working with Folia.
 *
 * Folia is a Paper fork that uses a regionalized multithreading approach,
 * which means tasks must be scheduled on the correct region thread for
 * the entity/location being accessed.
 *
 * This object provides runtime detection of Folia and helper methods
 * for working with its unique scheduling requirements.
 */
object FoliaCompat {

    /**
     * Whether the server is running Folia.
     * This is detected once at startup and cached.
     */
    val isFolia: Boolean by lazy {
        detectFolia()
    }

    /**
     * Whether region-based scheduling is required.
     * This is true on Folia and false on Paper/Spigot.
     */
    val requiresRegionScheduling: Boolean
        get() = isFolia

    /**
     * The name of the server implementation.
     */
    val serverImplementation: String by lazy {
        when {
            isFolia -> "Folia"
            isPaper -> "Paper"
            else -> "Spigot/Bukkit"
        }
    }

    /**
     * Whether the server is running Paper (or a Paper fork like Folia).
     */
    val isPaper: Boolean by lazy {
        detectPaper()
    }

    /**
     * Detects if the server is running Folia.
     *
     * Detection is done by checking for the presence of Folia-specific classes.
     */
    private fun detectFolia(): Boolean {
        return try {
            // Check for Folia's RegionizedServer class
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer")
            Logging.info("Folia detected - using region-aware scheduling")
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }

    /**
     * Detects if the server is running Paper (or a Paper fork).
     */
    private fun detectPaper(): Boolean {
        return try {
            // Check for Paper's AsyncChunkGenerator or other Paper-specific classes
            Class.forName("com.destroystokyo.paper.PaperConfig")
            true
        } catch (e: ClassNotFoundException) {
            try {
                // Newer Paper versions
                Class.forName("io.papermc.paper.configuration.Configuration")
                true
            } catch (e2: ClassNotFoundException) {
                false
            }
        }
    }

    /**
     * Checks if the current thread is the main server thread.
     *
     * On Folia, this check is less meaningful since there are multiple
     * region threads, but it still identifies the global region thread.
     */
    fun isPrimaryThread(): Boolean {
        return Bukkit.isPrimaryThread()
    }

    /**
     * Gets information about the current server implementation.
     *
     * @return A string describing the server (e.g., "Folia 1.20.4", "Paper 1.20.4")
     */
    fun getServerInfo(): String {
        val version = Bukkit.getVersion()
        return "$serverImplementation - $version"
    }

    /**
     * Checks if async operations are safe to perform.
     *
     * On Folia, many operations that would be async on Paper need to be
     * done on region threads instead.
     */
    fun isAsyncOperationSafe(): Boolean {
        return !isPrimaryThread()
    }

    /**
     * Gets the minimum tick delay for scheduled tasks.
     *
     * This may differ between server implementations.
     */
    fun getMinimumTickDelay(): Long {
        return if (isFolia) {
            // Folia may have different timing characteristics
            1L
        } else {
            1L
        }
    }
}

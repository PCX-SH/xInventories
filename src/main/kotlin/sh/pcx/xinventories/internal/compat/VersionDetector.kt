package sh.pcx.xinventories.internal.compat

import org.bukkit.Bukkit

/**
 * Utility object for detecting the Minecraft server version at runtime.
 *
 * This is used to handle API differences between Paper versions, particularly
 * around Kotlin bundling (Paper 1.21+ bundles Kotlin, 1.20.5-1.20.6 does not).
 *
 * The version is parsed lazily on first access to avoid issues during plugin
 * loading when Bukkit may not be fully initialized.
 */
object VersionDetector {

    /**
     * Cached parsed version components.
     */
    private val parsedVersion: ParsedVersion by lazy {
        parseVersion(getServerVersion())
    }

    /**
     * Gets the raw Minecraft version string from the server.
     *
     * Uses [Bukkit.getMinecraftVersion] which returns a clean version like "1.21.1"
     * or "1.20.6". Falls back to parsing [Bukkit.getBukkitVersion] if needed.
     *
     * @return The Minecraft version string (e.g., "1.20.6", "1.21.1")
     */
    fun getVersionString(): String = getServerVersion()

    /**
     * Gets the major version component.
     *
     * For Minecraft versions, this is always `1`.
     *
     * @return The major version (always 1 for Minecraft)
     */
    fun getMajorVersion(): Int = parsedVersion.major

    /**
     * Gets the minor version component.
     *
     * Examples:
     * - "1.20.6" returns 20
     * - "1.21.1" returns 21
     *
     * @return The minor version (e.g., 20, 21)
     */
    fun getMinorVersion(): Int = parsedVersion.minor

    /**
     * Gets the patch version component.
     *
     * Examples:
     * - "1.20.6" returns 6
     * - "1.21.1" returns 1
     * - "1.21" returns 0
     *
     * @return The patch version (e.g., 0, 1, 5, 6)
     */
    fun getPatchVersion(): Int = parsedVersion.patch

    /**
     * Checks if the server version is at least the specified version.
     *
     * Examples:
     * ```
     * // On 1.21.1 server:
     * isAtLeast(1, 20, 0)  // true
     * isAtLeast(1, 21, 0)  // true
     * isAtLeast(1, 21, 1)  // true
     * isAtLeast(1, 21, 2)  // false
     * isAtLeast(1, 22, 0)  // false
     * ```
     *
     * @param major The major version to compare against
     * @param minor The minor version to compare against
     * @param patch The patch version to compare against (default 0)
     * @return true if the server version is greater than or equal to the specified version
     */
    fun isAtLeast(major: Int, minor: Int, patch: Int = 0): Boolean {
        val current = parsedVersion
        return when {
            current.major > major -> true
            current.major < major -> false
            current.minor > minor -> true
            current.minor < minor -> false
            else -> current.patch >= patch
        }
    }

    /**
     * Checks if the server version is below the specified version.
     *
     * This is the inverse of [isAtLeast].
     *
     * @param major The major version to compare against
     * @param minor The minor version to compare against
     * @param patch The patch version to compare against (default 0)
     * @return true if the server version is less than the specified version
     */
    fun isBelow(major: Int, minor: Int, patch: Int = 0): Boolean = !isAtLeast(major, minor, patch)

    /**
     * Checks if Kotlin is bundled by the server.
     *
     * Paper 1.21+ bundles Kotlin runtime, while Paper 1.20.x does not.
     *
     * @return true if Kotlin is bundled by the server
     */
    fun isKotlinBundled(): Boolean = isAtLeast(1, 21, 0)

    /**
     * Gets the server version from Bukkit.
     *
     * Prefers [Bukkit.getMinecraftVersion] for clean version strings,
     * falls back to parsing [Bukkit.getBukkitVersion] for older implementations.
     */
    private fun getServerVersion(): String {
        return try {
            // Paper/modern servers have getMinecraftVersion()
            Bukkit.getMinecraftVersion()
        } catch (_: NoSuchMethodError) {
            // Fallback for older implementations
            // getBukkitVersion() returns something like "1.20.6-R0.1-SNAPSHOT"
            Bukkit.getBukkitVersion().substringBefore("-")
        }
    }

    /**
     * Parses a version string into its components.
     *
     * @param version The version string (e.g., "1.20.6", "1.21")
     * @return The parsed version components
     */
    internal fun parseVersion(version: String): ParsedVersion {
        val parts = version.split(".")
        return ParsedVersion(
            major = parts.getOrNull(0)?.toIntOrNull() ?: 1,
            minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
        )
    }

    /**
     * Data class holding parsed version components.
     *
     * @property major The major version (always 1 for Minecraft)
     * @property minor The minor version (e.g., 20, 21)
     * @property patch The patch version (e.g., 0, 1, 5, 6)
     */
    internal data class ParsedVersion(
        val major: Int,
        val minor: Int,
        val patch: Int
    )
}

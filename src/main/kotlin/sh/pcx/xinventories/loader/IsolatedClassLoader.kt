package sh.pcx.xinventories.loader

import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Path

/**
 * An isolated classloader for loading plugin dependencies.
 *
 * This classloader loads classes from downloaded dependency JARs while
 * delegating Bukkit API classes to the parent (plugin) classloader.
 * This allows the plugin to use its own versions of libraries without
 * conflicting with other plugins or the server.
 *
 * Class loading strategy:
 * 1. Bukkit/Spigot/Paper API classes -> parent classloader
 * 2. Plugin's own classes -> parent classloader
 * 3. Dependency classes -> this classloader (from downloaded JARs)
 * 4. Everything else -> parent classloader
 *
 * @param urls URLs to the dependency JARs
 * @param parent The parent classloader (typically the plugin's classloader)
 */
class IsolatedClassLoader(
    urls: Array<URL>,
    parent: ClassLoader
) : URLClassLoader(urls, parent) {

    /**
     * Creates an IsolatedClassLoader from a collection of JAR paths.
     *
     * @param jarPaths Paths to the dependency JAR files
     * @param parent The parent classloader
     */
    constructor(jarPaths: Collection<Path>, parent: ClassLoader) : this(
        jarPaths.map { it.toUri().toURL() }.toTypedArray(),
        parent
    )

    /**
     * Loads a class, using the appropriate classloader based on the package.
     *
     * For dependency packages (Kotlin, kotlinx, etc.), we load from this
     * classloader first. For everything else, we delegate to the parent.
     */
    override fun loadClass(name: String, resolve: Boolean): Class<*> {
        synchronized(getClassLoadingLock(name)) {
            // Check if already loaded
            var c = findLoadedClass(name)
            if (c != null) {
                if (resolve) resolveClass(c)
                return c
            }

            // Determine loading strategy based on package
            c = if (shouldLoadFromSelf(name)) {
                // Try to load from our JARs first
                try {
                    findClass(name)
                } catch (e: ClassNotFoundException) {
                    // Fall back to parent
                    parent.loadClass(name)
                }
            } else {
                // Delegate to parent first
                try {
                    parent.loadClass(name)
                } catch (e: ClassNotFoundException) {
                    // Try our JARs as fallback
                    findClass(name)
                }
            }

            if (resolve) resolveClass(c)
            return c
        }
    }

    /**
     * Determines if a class should be loaded from this classloader first.
     *
     * @param name Fully qualified class name
     * @return true if the class should be loaded from dependency JARs
     */
    private fun shouldLoadFromSelf(name: String): Boolean {
        // Load dependency packages from our JARs
        return SELF_LOAD_PACKAGES.any { name.startsWith(it) }
    }

    /**
     * Adds a JAR to this classloader at runtime.
     *
     * @param url URL to the JAR file
     */
    public override fun addURL(url: URL) {
        super.addURL(url)
    }

    /**
     * Adds a JAR path to this classloader at runtime.
     *
     * @param path Path to the JAR file
     */
    fun addJar(path: Path) {
        addURL(path.toUri().toURL())
    }

    companion object {
        /**
         * Packages that should be loaded from dependency JARs.
         * These are loaded from this classloader first, then fall back to parent.
         */
        private val SELF_LOAD_PACKAGES = listOf(
            // Kotlin runtime
            "kotlin.",
            "kotlinx.",

            // Caching
            "com.github.benmanes.caffeine.",

            // Database
            "com.zaxxer.hikari.",

            // Redis
            "redis.clients.",
            "org.apache.commons.pool2.",

            // JSON (for Jedis)
            "com.google.gson.",

            // SLF4J
            "org.slf4j."
        )

        /**
         * Creates an IsolatedClassLoader with the given dependencies.
         *
         * @param dependencies Paths to dependency JARs
         * @param parent Parent classloader
         * @return New IsolatedClassLoader instance
         */
        fun create(dependencies: Collection<Path>, parent: ClassLoader): IsolatedClassLoader {
            return IsolatedClassLoader(dependencies, parent)
        }
    }
}

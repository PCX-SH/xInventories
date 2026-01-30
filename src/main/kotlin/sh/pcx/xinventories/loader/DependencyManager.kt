package sh.pcx.xinventories.loader

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.logging.Logger

/**
 * Manages runtime dependency downloading and verification.
 *
 * Downloads dependencies from Maven Central (or configured mirrors) and verifies
 * SHA-256 checksums before loading. Dependencies are cached locally to avoid
 * re-downloading on every server start.
 *
 * @property librariesDir Directory to store downloaded dependencies
 * @property logger Logger for status messages
 * @property repositories List of Maven repository URLs to try (in order)
 */
class DependencyManager(
    private val librariesDir: Path,
    private val logger: Logger,
    private val repositories: List<String> = DEFAULT_REPOSITORIES
) {
    private val downloadedPaths = ConcurrentHashMap<Dependency, Path>()
    private val failedDependencies = mutableListOf<Dependency>()

    /**
     * Loads all specified dependencies, downloading if necessary.
     *
     * @param dependencies The dependencies to load
     * @param skipVerification If true, skip SHA-256 verification (for development only)
     * @return Map of dependencies to their local file paths
     * @throws DependencyException if any required dependency fails to load
     */
    fun loadDependencies(
        dependencies: List<Dependency>,
        skipVerification: Boolean = false
    ): Map<Dependency, Path> {
        // Ensure libraries directory exists
        Files.createDirectories(librariesDir)

        logger.info("Loading ${dependencies.size} dependencies...")

        for (dependency in dependencies) {
            try {
                val path = loadDependency(dependency, skipVerification)
                downloadedPaths[dependency] = path
                logger.fine("Loaded: ${dependency.fileName}")
            } catch (e: Exception) {
                logger.severe("Failed to load ${dependency.fileName}: ${e.message}")
                failedDependencies.add(dependency)
            }
        }

        if (failedDependencies.isNotEmpty()) {
            throw DependencyException(
                "Failed to load ${failedDependencies.size} dependencies: " +
                    failedDependencies.joinToString { it.fileName }
            )
        }

        logger.info("Successfully loaded ${downloadedPaths.size} dependencies")
        return downloadedPaths.toMap()
    }

    /**
     * Loads a single dependency, downloading if not cached or invalid.
     *
     * @param dependency The dependency to load
     * @param skipVerification If true, skip SHA-256 verification
     * @return Path to the downloaded JAR file
     */
    private fun loadDependency(dependency: Dependency, skipVerification: Boolean): Path {
        val targetPath = librariesDir.resolve(dependency.fileName)

        // Check if already cached and valid
        if (Files.exists(targetPath)) {
            if (skipVerification || verifyChecksum(targetPath, dependency.sha256)) {
                return targetPath
            }
            logger.warning("Checksum mismatch for ${dependency.fileName}, re-downloading...")
            Files.delete(targetPath)
        }

        // Download from repositories
        return downloadDependency(dependency, targetPath, skipVerification)
    }

    /**
     * Downloads a dependency from configured repositories.
     *
     * @param dependency The dependency to download
     * @param targetPath Where to save the downloaded file
     * @param skipVerification If true, skip SHA-256 verification
     * @return Path to the downloaded file
     * @throws DependencyException if download fails from all repositories
     */
    private fun downloadDependency(
        dependency: Dependency,
        targetPath: Path,
        skipVerification: Boolean
    ): Path {
        val errors = mutableListOf<String>()

        for (repository in repositories) {
            val url = "$repository/${dependency.mavenPath}"
            try {
                logger.info("Downloading ${dependency.fileName} from ${getRepositoryName(repository)}...")
                downloadFile(url, targetPath)

                // Verify checksum
                if (!skipVerification && !verifyChecksum(targetPath, dependency.sha256)) {
                    Files.deleteIfExists(targetPath)
                    errors.add("$repository: Checksum verification failed")
                    continue
                }

                return targetPath
            } catch (e: IOException) {
                errors.add("$repository: ${e.message}")
                Files.deleteIfExists(targetPath)
            }
        }

        throw DependencyException(
            "Failed to download ${dependency.fileName} from any repository:\n" +
                errors.joinToString("\n") { "  - $it" }
        )
    }

    /**
     * Downloads a file from a URL.
     *
     * @param url The URL to download from
     * @param targetPath Where to save the file
     */
    private fun downloadFile(url: String, targetPath: Path) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT
        connection.setRequestProperty("User-Agent", USER_AGENT)

        try {
            connection.connect()

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
            }

            val contentLength = connection.contentLengthLong
            var downloaded = 0L
            var lastProgress = 0

            connection.inputStream.use { input ->
                Files.newOutputStream(targetPath).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        downloaded += bytesRead

                        // Log progress for large files
                        if (contentLength > 0) {
                            val progress = ((downloaded * 100) / contentLength).toInt()
                            if (progress >= lastProgress + 25) {
                                logger.fine("  Progress: $progress%")
                                lastProgress = progress
                            }
                        }
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Verifies a file's SHA-256 checksum.
     *
     * @param path The file to verify
     * @param expectedChecksum Base64-encoded expected SHA-256 hash
     * @return true if checksum matches
     */
    private fun verifyChecksum(path: Path, expectedChecksum: String): Boolean {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        val actualChecksum = Base64.getEncoder().encodeToString(digest.digest())
        return actualChecksum == expectedChecksum
    }

    /**
     * Calculates the SHA-256 checksum of a file.
     *
     * @param path The file to hash
     * @return Base64-encoded SHA-256 hash
     */
    fun calculateChecksum(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return Base64.getEncoder().encodeToString(digest.digest())
    }

    /**
     * Gets all downloaded dependency paths.
     */
    fun getLoadedPaths(): Collection<Path> = downloadedPaths.values

    /**
     * Gets the path for a specific loaded dependency.
     */
    fun getPath(dependency: Dependency): Path? = downloadedPaths[dependency]

    /**
     * Clears the dependency cache, forcing re-download on next load.
     */
    fun clearCache() {
        if (Files.exists(librariesDir)) {
            Files.walk(librariesDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
        downloadedPaths.clear()
    }

    /**
     * Gets a friendly name for a repository URL.
     */
    private fun getRepositoryName(url: String): String = when {
        url.contains("repo1.maven.org") -> "Maven Central"
        url.contains("repo.maven.apache.org") -> "Maven Central (Apache)"
        url.contains("jcenter") -> "JCenter"
        else -> url.substringAfter("://").substringBefore("/")
    }

    companion object {
        /**
         * Default Maven repositories to try, in order of preference.
         */
        val DEFAULT_REPOSITORIES = listOf(
            "https://repo1.maven.org/maven2",
            "https://repo.maven.apache.org/maven2"
        )

        private const val CONNECT_TIMEOUT = 15_000 // 15 seconds
        private const val READ_TIMEOUT = 60_000 // 60 seconds
        private const val USER_AGENT = "xInventories-DependencyManager/1.0"
    }
}

/**
 * Exception thrown when dependency loading fails.
 */
class DependencyException(message: String, cause: Throwable? = null) : Exception(message, cause)

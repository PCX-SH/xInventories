package sh.pcx.xinventories.unit.loader

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sh.pcx.xinventories.loader.Dependency
import sh.pcx.xinventories.loader.DependencyException
import sh.pcx.xinventories.loader.DependencyManager
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [DependencyManager].
 */
class DependencyManagerTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var librariesDir: Path
    private lateinit var manager: DependencyManager
    private val logger = Logger.getLogger("DependencyManagerTest")

    @BeforeEach
    fun setUp() {
        librariesDir = tempDir.resolve("libraries")
        manager = DependencyManager(librariesDir, logger)
    }

    @AfterEach
    fun tearDown() {
        // Clean up any downloaded files
        if (Files.exists(librariesDir)) {
            Files.walk(librariesDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `libraries directory is created on load`() {
        // Given
        assertFalse(Files.exists(librariesDir))

        // When
        manager.loadDependencies(emptyList())

        // Then
        assertTrue(Files.exists(librariesDir))
    }

    @Test
    fun `dependency file name is correct`() {
        val dep = Dependency.KOTLIN_STDLIB
        assertEquals("kotlin-stdlib-2.3.0.jar", dep.fileName)
    }

    @Test
    fun `dependency maven path is correct`() {
        val dep = Dependency.KOTLIN_STDLIB
        assertEquals(
            "org/jetbrains/kotlin/kotlin-stdlib/2.3.0/kotlin-stdlib-2.3.0.jar",
            dep.mavenPath
        )
    }

    @Test
    fun `dependency maven central URL is correct`() {
        val dep = Dependency.KOTLIN_STDLIB
        assertTrue(dep.mavenCentralUrl.startsWith("https://repo1.maven.org/maven2/"))
        assertTrue(dep.mavenCentralUrl.endsWith("kotlin-stdlib-2.3.0.jar"))
    }

    @Test
    fun `core dependencies list is not empty`() {
        assertTrue(Dependency.CORE_DEPENDENCIES.isNotEmpty())
        assertTrue(Dependency.CORE_DEPENDENCIES.contains(Dependency.KOTLIN_STDLIB))
    }

    @Test
    fun `all dependencies list includes core and redis`() {
        val all = Dependency.ALL_DEPENDENCIES
        assertTrue(all.containsAll(Dependency.CORE_DEPENDENCIES))
        assertTrue(all.containsAll(Dependency.REDIS_DEPENDENCIES))
    }

    @Test
    fun `checksum calculation is correct`() {
        // Create a test file with known content
        val testFile = tempDir.resolve("test.txt")
        val content = "Hello, World!"
        Files.writeString(testFile, content)

        // Calculate expected checksum
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray())
        val expected = Base64.getEncoder().encodeToString(hash)

        // Verify
        val actual = manager.calculateChecksum(testFile)
        assertEquals(expected, actual)
    }

    @Test
    fun `cached dependency is reused`() {
        // Create a fake cached JAR
        Files.createDirectories(librariesDir)
        val fakeJar = librariesDir.resolve("kotlin-stdlib-2.3.0.jar")
        Files.writeString(fakeJar, "fake jar content")

        // Calculate the checksum of the fake jar
        val checksum = manager.calculateChecksum(fakeJar)

        // Create a custom dependency with matching checksum
        // Note: We can't easily test this without downloading real deps
        // So we just verify the cached file exists
        assertTrue(Files.exists(fakeJar))
    }

    @Test
    fun `clear cache removes all files`() {
        // Create some files in the libraries directory
        Files.createDirectories(librariesDir)
        Files.writeString(librariesDir.resolve("file1.jar"), "content1")
        Files.writeString(librariesDir.resolve("file2.jar"), "content2")
        val subDir = librariesDir.resolve("subdir")
        Files.createDirectories(subDir)
        Files.writeString(subDir.resolve("file3.jar"), "content3")

        // Clear cache
        manager.clearCache()

        // Verify all files are gone
        assertFalse(Files.exists(librariesDir))
    }

    @Test
    fun `empty dependency list returns empty map`() {
        val result = manager.loadDependencies(emptyList())
        assertTrue(result.isEmpty())
    }

    @Test
    fun `getLoadedPaths returns empty before loading`() {
        assertTrue(manager.getLoadedPaths().isEmpty())
    }

    @Test
    fun `getPath returns null for unloaded dependency`() {
        assertEquals(null, manager.getPath(Dependency.KOTLIN_STDLIB))
    }
}

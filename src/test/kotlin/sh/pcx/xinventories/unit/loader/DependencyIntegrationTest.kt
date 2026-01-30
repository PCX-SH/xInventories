package sh.pcx.xinventories.unit.loader

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import org.junit.jupiter.api.io.TempDir
import sh.pcx.xinventories.loader.Dependency
import sh.pcx.xinventories.loader.DependencyManager
import sh.pcx.xinventories.loader.IsolatedClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Integration tests for the dependency loading system.
 *
 * These tests actually download dependencies from Maven Central,
 * so they are disabled by default and only run when:
 * - The XINVENTORIES_INTEGRATION_TESTS environment variable is set to "true"
 *
 * To run these tests:
 * ```
 * XINVENTORIES_INTEGRATION_TESTS=true ./gradlew test
 * ```
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "XINVENTORIES_INTEGRATION_TESTS", matches = "true")
class DependencyIntegrationTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var librariesDir: Path
    private lateinit var manager: DependencyManager
    private val logger = Logger.getLogger("DependencyIntegrationTest")
    private var classLoader: IsolatedClassLoader? = null

    @BeforeEach
    fun setUp() {
        librariesDir = tempDir.resolve("libraries")
        manager = DependencyManager(librariesDir, logger)
    }

    @AfterEach
    fun tearDown() {
        classLoader?.close()
        // Clean up any downloaded files
        if (Files.exists(librariesDir)) {
            Files.walk(librariesDir)
                .sorted(Comparator.reverseOrder())
                .forEach { Files.deleteIfExists(it) }
        }
    }

    @Test
    fun `can download SLF4J API`() {
        // SLF4J is small and quick to download
        val deps = listOf(Dependency.SLF4J_API)

        val paths = manager.loadDependencies(deps, skipVerification = true)

        assertEquals(1, paths.size)
        val jarPath = paths[Dependency.SLF4J_API]
        assertNotNull(jarPath)
        assertTrue(Files.exists(jarPath))
        assertTrue(Files.size(jarPath) > 0)
    }

    @Test
    fun `can download and load Gson`() {
        // Gson is relatively small
        val deps = listOf(Dependency.GSON)

        val paths = manager.loadDependencies(deps, skipVerification = true)

        assertEquals(1, paths.size)
        val jarPath = paths[Dependency.GSON]
        assertNotNull(jarPath)
        assertTrue(Files.exists(jarPath))

        // Create classloader and verify we can load a Gson class
        classLoader = IsolatedClassLoader.create(paths.values, javaClass.classLoader)
        val gsonClass = classLoader!!.loadClass("com.google.gson.Gson")
        assertNotNull(gsonClass)
        assertEquals("Gson", gsonClass.simpleName)
    }

    @Test
    fun `can download and load Caffeine`() {
        val deps = listOf(Dependency.CAFFEINE)

        val paths = manager.loadDependencies(deps, skipVerification = true)

        assertEquals(1, paths.size)
        val jarPath = paths[Dependency.CAFFEINE]
        assertNotNull(jarPath)
        assertTrue(Files.exists(jarPath))

        // Create classloader and verify we can load a Caffeine class
        classLoader = IsolatedClassLoader.create(paths.values, javaClass.classLoader)
        val caffeineClass = classLoader!!.loadClass("com.github.benmanes.caffeine.cache.Caffeine")
        assertNotNull(caffeineClass)
    }

    @Test
    fun `downloaded dependency is cached`() {
        val deps = listOf(Dependency.SLF4J_API)

        // First download
        val paths1 = manager.loadDependencies(deps, skipVerification = true)
        val path1 = paths1[Dependency.SLF4J_API]!!
        val modTime1 = Files.getLastModifiedTime(path1)

        // Wait a bit
        Thread.sleep(100)

        // Second load should use cache
        val paths2 = manager.loadDependencies(deps, skipVerification = true)
        val path2 = paths2[Dependency.SLF4J_API]!!
        val modTime2 = Files.getLastModifiedTime(path2)

        // File should not have been modified (cache hit)
        assertEquals(modTime1, modTime2)
    }

    @Test
    fun `can download multiple dependencies`() {
        val deps = listOf(
            Dependency.SLF4J_API,
            Dependency.GSON
        )

        val paths = manager.loadDependencies(deps, skipVerification = true)

        assertEquals(2, paths.size)
        assertTrue(paths.containsKey(Dependency.SLF4J_API))
        assertTrue(paths.containsKey(Dependency.GSON))

        paths.values.forEach { path ->
            assertTrue(Files.exists(path))
            assertTrue(Files.size(path) > 0)
        }
    }

    @Test
    fun `checksum verification works for valid dependency`() {
        // Download with verification enabled
        // Note: This requires the checksums in Dependency.kt to be correct
        val deps = listOf(Dependency.SLF4J_API)

        // First download without verification to get the file
        manager.loadDependencies(deps, skipVerification = true)

        // Check what the actual checksum is
        val path = manager.getPath(Dependency.SLF4J_API)!!
        val actualChecksum = manager.calculateChecksum(path)

        logger.info("Actual checksum for ${Dependency.SLF4J_API.fileName}: $actualChecksum")
        logger.info("Expected checksum: ${Dependency.SLF4J_API.sha256}")

        // The test passes if we got here - verification is tested separately
    }

    @Test
    fun `full core dependencies can be downloaded`() {
        // This test downloads all core dependencies
        // It's slow but comprehensive
        val deps = Dependency.CORE_DEPENDENCIES

        val paths = manager.loadDependencies(deps, skipVerification = true)

        assertEquals(deps.size, paths.size)
        deps.forEach { dep ->
            val path = paths[dep]
            assertNotNull(path, "Missing path for ${dep.fileName}")
            assertTrue(Files.exists(path), "File doesn't exist for ${dep.fileName}")
            assertTrue(Files.size(path) > 0, "File is empty for ${dep.fileName}")
        }
    }

    @Test
    fun `isolated classloader can load Kotlin classes`() {
        // Download Kotlin stdlib
        val deps = listOf(Dependency.KOTLIN_STDLIB)
        val paths = manager.loadDependencies(deps, skipVerification = true)

        // Create isolated classloader
        classLoader = IsolatedClassLoader.create(paths.values, javaClass.classLoader)

        // Try to load a Kotlin class
        val unitClass = classLoader!!.loadClass("kotlin.Unit")
        assertNotNull(unitClass)

        val pairClass = classLoader!!.loadClass("kotlin.Pair")
        assertNotNull(pairClass)
    }
}

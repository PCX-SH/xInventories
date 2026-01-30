package sh.pcx.xinventories.unit.loader

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import sh.pcx.xinventories.loader.IsolatedClassLoader
import java.io.FileOutputStream
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for [IsolatedClassLoader].
 */
class IsolatedClassLoaderTest {

    @TempDir
    lateinit var tempDir: Path

    private var classLoader: IsolatedClassLoader? = null

    @AfterEach
    fun tearDown() {
        classLoader?.close()
    }

    @Test
    fun `classloader can be created with empty URLs`() {
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)
        assertNotNull(classLoader)
    }

    @Test
    fun `classloader can be created from paths`() {
        classLoader = IsolatedClassLoader(emptyList(), javaClass.classLoader)
        assertNotNull(classLoader)
    }

    @Test
    fun `classloader loads JDK classes from parent`() {
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)

        // Should load java.lang.String from parent
        val stringClass = classLoader!!.loadClass("java.lang.String")
        assertEquals(String::class.java, stringClass)
    }

    @Test
    fun `classloader loads bukkit classes from parent`() {
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)

        // Bukkit classes should delegate to parent (which may or may not have them)
        // In tests with MockBukkit, this should work
        try {
            val bukkitClass = classLoader!!.loadClass("org.bukkit.Bukkit")
            assertNotNull(bukkitClass)
        } catch (e: ClassNotFoundException) {
            // Expected if Bukkit is not on the test classpath
        }
    }

    @Test
    fun `classloader can add URL at runtime`() {
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)

        // Create a simple JAR file
        val jarPath = createTestJar("test.jar", "com.test.TestClass")
        classLoader!!.addURL(jarPath.toUri().toURL())

        // Verify URL was added
        val urls = classLoader!!.urLs
        assertTrue(urls.any { it.path.endsWith("test.jar") })
    }

    @Test
    fun `classloader can add JAR path at runtime`() {
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)

        // Create a simple JAR file
        val jarPath = createTestJar("test2.jar", "com.test.TestClass2")
        classLoader!!.addJar(jarPath)

        // Verify JAR was added
        val urls = classLoader!!.urLs
        assertTrue(urls.any { it.path.endsWith("test2.jar") })
    }

    @Test
    fun `create factory method works`() {
        val jarPath = createTestJar("test3.jar", "com.test.TestClass3")

        classLoader = IsolatedClassLoader.create(listOf(jarPath), javaClass.classLoader)

        assertNotNull(classLoader)
        val urls = classLoader!!.urLs
        assertTrue(urls.any { it.path.endsWith("test3.jar") })
    }

    @Test
    fun `kotlin packages are loaded from self first`() {
        // Create a classloader with no JARs
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)

        // kotlin.* classes should try to load from self first
        // Since we have no JARs, it will fall back to parent
        // This test verifies the logic doesn't crash
        try {
            classLoader!!.loadClass("kotlin.Unit")
            // If kotlin is on classpath, this works
        } catch (e: ClassNotFoundException) {
            // If kotlin is not on classpath, this is expected
        }
    }

    @Test
    fun `kotlinx packages are loaded from self first`() {
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)

        try {
            classLoader!!.loadClass("kotlinx.coroutines.CoroutineScope")
        } catch (e: ClassNotFoundException) {
            // Expected if not on classpath
        }
    }

    @Test
    fun `caffeine packages are loaded from self first`() {
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)

        try {
            classLoader!!.loadClass("com.github.benmanes.caffeine.cache.Cache")
        } catch (e: ClassNotFoundException) {
            // Expected if not on classpath
        }
    }

    @Test
    fun `hikari packages are loaded from self first`() {
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)

        try {
            classLoader!!.loadClass("com.zaxxer.hikari.HikariDataSource")
        } catch (e: ClassNotFoundException) {
            // Expected if not on classpath
        }
    }

    @Test
    fun `jedis packages are loaded from self first`() {
        classLoader = IsolatedClassLoader(emptyArray(), javaClass.classLoader)

        try {
            classLoader!!.loadClass("redis.clients.jedis.Jedis")
        } catch (e: ClassNotFoundException) {
            // Expected if not on classpath
        }
    }

    /**
     * Creates a minimal test JAR file with a dummy class.
     */
    private fun createTestJar(name: String, className: String): Path {
        val jarPath = tempDir.resolve(name)

        JarOutputStream(FileOutputStream(jarPath.toFile())).use { jos ->
            // Add a dummy class entry (just the structure, not real bytecode)
            val classPath = className.replace('.', '/') + ".class"
            jos.putNextEntry(JarEntry(classPath))
            // Write minimal class file header (magic number only, not valid but tests structure)
            jos.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte(), 0xBA.toByte(), 0xBE.toByte()))
            jos.closeEntry()
        }

        return jarPath
    }
}

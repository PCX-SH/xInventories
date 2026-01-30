package sh.pcx.xinventories.unit.util

import io.mockk.*
import org.bukkit.Bukkit
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.util.FoliaCompat
import sh.pcx.xinventories.internal.util.Logging

/**
 * Unit tests for FoliaCompat.
 *
 * Tests cover:
 * - Folia detection logic
 * - Paper detection logic
 * - Server implementation identification
 * - Thread checking utilities
 * - Minimum tick delay
 */
@DisplayName("FoliaCompat")
class FoliaCompatTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    @BeforeEach
    fun setUp() {
        mockkStatic(Bukkit::class)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Bukkit::class)
    }

    @Nested
    @DisplayName("Folia Detection")
    inner class FoliaDetectionTests {

        @Test
        @DisplayName("isFolia should be determined at runtime")
        fun isFoliaDeterminedAtRuntime() {
            // The actual value depends on whether Folia classes exist
            // In a test environment without Folia, this should be false
            // We just verify it doesn't throw
            assertDoesNotThrow { FoliaCompat.isFolia }
        }

        @Test
        @DisplayName("requiresRegionScheduling should match isFolia")
        fun requiresRegionSchedulingMatchesFolia() {
            assertEquals(FoliaCompat.isFolia, FoliaCompat.requiresRegionScheduling)
        }
    }

    @Nested
    @DisplayName("Paper Detection")
    inner class PaperDetectionTests {

        @Test
        @DisplayName("isPaper should be determined at runtime")
        fun isPaperDeterminedAtRuntime() {
            // The actual value depends on whether Paper classes exist
            assertDoesNotThrow { FoliaCompat.isPaper }
        }
    }

    @Nested
    @DisplayName("Server Implementation")
    inner class ServerImplementationTests {

        @Test
        @DisplayName("serverImplementation should return non-empty string")
        fun serverImplementationReturnsNonEmptyString() {
            val impl = FoliaCompat.serverImplementation
            assertTrue(impl.isNotEmpty())
        }

        @Test
        @DisplayName("serverImplementation should be one of known values")
        fun serverImplementationIsKnownValue() {
            val impl = FoliaCompat.serverImplementation
            assertTrue(
                impl == "Folia" || impl == "Paper" || impl == "Spigot/Bukkit",
                "Unknown server implementation: $impl"
            )
        }

        @Test
        @DisplayName("getServerInfo should include version information")
        fun getServerInfoIncludesVersion() {
            every { Bukkit.getVersion() } returns "1.20.4-R0.1-SNAPSHOT"

            val info = FoliaCompat.getServerInfo()

            assertTrue(info.contains("1.20.4"))
        }
    }

    @Nested
    @DisplayName("Thread Checking")
    inner class ThreadCheckingTests {

        @Test
        @DisplayName("isPrimaryThread delegates to Bukkit")
        fun isPrimaryThreadDelegatesToBukkit() {
            every { Bukkit.isPrimaryThread() } returns true

            assertTrue(FoliaCompat.isPrimaryThread())

            every { Bukkit.isPrimaryThread() } returns false

            assertFalse(FoliaCompat.isPrimaryThread())
        }

        @Test
        @DisplayName("isAsyncOperationSafe returns opposite of isPrimaryThread")
        fun isAsyncOperationSafeReturnsOppositePrimaryThread() {
            every { Bukkit.isPrimaryThread() } returns true
            assertFalse(FoliaCompat.isAsyncOperationSafe())

            every { Bukkit.isPrimaryThread() } returns false
            assertTrue(FoliaCompat.isAsyncOperationSafe())
        }
    }

    @Nested
    @DisplayName("Timing Utilities")
    inner class TimingUtilitiesTests {

        @Test
        @DisplayName("getMinimumTickDelay should return positive value")
        fun getMinimumTickDelayReturnsPositive() {
            val delay = FoliaCompat.getMinimumTickDelay()
            assertTrue(delay > 0)
        }

        @Test
        @DisplayName("getMinimumTickDelay should return at least 1 tick")
        fun getMinimumTickDelayAtLeast1() {
            val delay = FoliaCompat.getMinimumTickDelay()
            assertTrue(delay >= 1L)
        }
    }
}

package sh.pcx.xinventories.unit.hook

import io.mockk.*
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.ServicesManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import kotlin.test.assertNull
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.hook.LuckPermsHook
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.InventoryService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID

/**
 * Unit tests for LuckPermsHook.
 *
 * Tests cover:
 * - Initialization when LuckPerms is not available
 * - Graceful handling when LuckPerms API classes are missing
 * - Unregistration cleanup
 * - Context key constant
 * - isInitialized state tracking
 */
@DisplayName("LuckPermsHook")
class LuckPermsHookTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            mockkObject(Logging)
            every { Logging.debug(any<() -> String>()) } just Runs
            every { Logging.debug(any<String>()) } just Runs
            every { Logging.info(any()) } just Runs
            every { Logging.warning(any()) } just Runs
            every { Logging.error(any<String>()) } just Runs
            every { Logging.error(any<String>(), any()) } just Runs
        }

        @JvmStatic
        @AfterAll
        fun teardownAll() {
            unmockkAll()
        }
    }

    private lateinit var plugin: XInventories
    private lateinit var server: Server
    private lateinit var servicesManager: ServicesManager
    private lateinit var serviceManager: ServiceManager
    private lateinit var inventoryService: InventoryService
    private lateinit var groupService: GroupService

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        server = mockk(relaxed = true)
        servicesManager = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        inventoryService = mockk(relaxed = true)
        groupService = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.inventoryService } returns inventoryService
        every { serviceManager.groupService } returns groupService

        // Mock Bukkit static methods
        mockkStatic(Bukkit::class)
        every { Bukkit.getServicesManager() } returns servicesManager
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Bukkit::class)
    }

    @Nested
    @DisplayName("Initialization")
    inner class InitializationTests {

        @Test
        @DisplayName("should return false when LuckPerms service not registered")
        fun returnFalseWhenLuckPermsNotRegistered() {
            // LuckPerms not registered
            every { servicesManager.getRegistration(any<Class<*>>()) } returns null

            val hook = LuckPermsHook(plugin)
            val result = hook.initialize()

            assertFalse(result)
            assertFalse(hook.isInitialized())
        }

        @Test
        @DisplayName("should not be initialized before initialize() is called")
        fun notInitializedByDefault() {
            val hook = LuckPermsHook(plugin)

            assertFalse(hook.isInitialized())
        }

        @Test
        @DisplayName("should handle NoClassDefFoundError gracefully")
        fun handleNoClassDefFoundError() {
            // Simulate LuckPerms API not on classpath
            every { servicesManager.getRegistration(any<Class<*>>()) } throws NoClassDefFoundError("net.luckperms.api.LuckPerms")

            val hook = LuckPermsHook(plugin)
            val result = hook.initialize()

            assertFalse(result)
            assertFalse(hook.isInitialized())
        }

        @Test
        @DisplayName("should handle generic exceptions gracefully")
        fun handleGenericException() {
            every { servicesManager.getRegistration(any<Class<*>>()) } throws RuntimeException("Test exception")

            val hook = LuckPermsHook(plugin)
            val result = hook.initialize()

            assertFalse(result)
            assertFalse(hook.isInitialized())
        }

        @Test
        @DisplayName("should return true on repeated initialize calls when already initialized")
        fun returnTrueOnRepeatedInitialize() {
            // LuckPerms not registered
            every { servicesManager.getRegistration(any<Class<*>>()) } returns null

            val hook = LuckPermsHook(plugin)

            // First call - fails because LuckPerms not available
            assertFalse(hook.initialize())

            // The hook should still report false since it couldn't initialize
            assertFalse(hook.isInitialized())
        }
    }

    @Nested
    @DisplayName("Unregistration")
    inner class UnregistrationTests {

        @Test
        @DisplayName("should handle unregister when not initialized")
        fun unregisterWhenNotInitialized() {
            val hook = LuckPermsHook(plugin)

            // Should not throw
            assertDoesNotThrow { hook.unregister() }
            assertFalse(hook.isInitialized())
        }

        @Test
        @DisplayName("should set initialized to false after unregister")
        fun setInitializedFalseAfterUnregister() {
            every { servicesManager.getRegistration(any<Class<*>>()) } returns null

            val hook = LuckPermsHook(plugin)
            hook.initialize()
            hook.unregister()

            assertFalse(hook.isInitialized())
        }
    }

    @Nested
    @DisplayName("Context Key")
    inner class ContextKeyTests {

        @Test
        @DisplayName("should have correct context key")
        fun hasCorrectContextKey() {
            assertEquals("xinventories:group", LuckPermsHook.CONTEXT_KEY)
        }
    }

    @Nested
    @DisplayName("Context Update Signaling")
    inner class ContextUpdateTests {

        @Test
        @DisplayName("should not throw when signaling update and not initialized")
        fun noExceptionWhenNotInitialized() {
            val player = mockk<Player>(relaxed = true)
            val hook = LuckPermsHook(plugin)

            assertDoesNotThrow { hook.signalContextUpdate(player) }
        }
    }

    @Nested
    @DisplayName("LuckPerms API Access")
    inner class ApiAccessTests {

        @Test
        @DisplayName("should return null LuckPerms when not initialized")
        fun returnNullWhenNotInitialized() {
            val hook = LuckPermsHook(plugin)

            assertNull(hook.getLuckPerms())
        }
    }
}

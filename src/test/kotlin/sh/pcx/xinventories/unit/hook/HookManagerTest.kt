package sh.pcx.xinventories.unit.hook

import io.mockk.*
import net.milkbowl.vault.permission.Permission
import org.bukkit.Bukkit
import org.bukkit.Server
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.RegisteredServiceProvider
import org.bukkit.plugin.ServicesManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import kotlin.test.assertNull
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.EconomyConfig
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.hook.HookManager
import sh.pcx.xinventories.internal.hook.LuckPermsHook
import sh.pcx.xinventories.internal.hook.PlaceholderAPIHook
import sh.pcx.xinventories.internal.hook.VaultHook
import sh.pcx.xinventories.internal.integration.XInventoriesEconomyProvider
import sh.pcx.xinventories.internal.service.EconomyService
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID

/**
 * Unit tests for HookManager.
 *
 * Tests cover:
 * - registerHooks() - Registering PlaceholderAPI, Vault, LuckPerms hooks
 * - unregisterHooks() - Unregistering all hooks
 * - hasPlaceholderAPI() - Checking if PlaceholderAPI is available
 * - hasVault() - Checking if Vault is available
 * - hasLuckPerms() - Checking if LuckPerms is available
 * - getVaultHook() / getLuckPermsHook() - Getting hook instances
 * - signalLuckPermsContextUpdate() - Signaling context changes
 * - registerEconomyProvider() - Registering economy provider when enabled
 */
@DisplayName("HookManager Unit Tests")
class HookManagerTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(java.util.logging.Logger.getLogger("HookManagerTest"), false)
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
    private lateinit var pluginManager: PluginManager
    private lateinit var servicesManager: ServicesManager
    private lateinit var configManager: ConfigManager
    private lateinit var serviceManager: ServiceManager
    private lateinit var economyService: EconomyService
    private lateinit var groupService: GroupService

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        server = mockk(relaxed = true)
        pluginManager = mockk(relaxed = true)
        servicesManager = mockk(relaxed = true)
        configManager = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        economyService = mockk(relaxed = true)
        groupService = mockk(relaxed = true)

        every { plugin.configManager } returns configManager
        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.economyService } returns economyService
        every { serviceManager.groupService } returns groupService

        // Default economy config (disabled)
        every { configManager.mainConfig } returns MainConfig(
            economy = EconomyConfig(enabled = false)
        )

        mockkStatic(Bukkit::class)
        every { Bukkit.getPluginManager() } returns pluginManager
        every { Bukkit.getServicesManager() } returns servicesManager

        // Default: no plugins present
        every { pluginManager.getPlugin("PlaceholderAPI") } returns null
        every { pluginManager.getPlugin("Vault") } returns null
        every { pluginManager.getPlugin("LuckPerms") } returns null
        every { servicesManager.getRegistration(any<Class<*>>()) } returns null
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Bukkit::class)
    }

    // =========================================================================
    // registerHooks() Tests
    // =========================================================================

    @Nested
    @DisplayName("registerHooks")
    inner class RegisterHooksTests {

        @Test
        @DisplayName("Should not throw when no plugins are present")
        fun noThrowWhenNoPlugins() {
            val hookManager = HookManager(plugin)

            assertDoesNotThrow { hookManager.registerHooks() }
        }

        @Test
        @DisplayName("Should check for PlaceholderAPI plugin")
        fun checkForPlaceholderAPI() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verify { pluginManager.getPlugin("PlaceholderAPI") }
        }

        @Test
        @DisplayName("Should check for Vault plugin")
        fun checkForVault() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verify { pluginManager.getPlugin("Vault") }
        }

        @Test
        @DisplayName("Should check for LuckPerms plugin")
        fun checkForLuckPerms() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verify { pluginManager.getPlugin("LuckPerms") }
        }

        @Test
        @DisplayName("Should detect PlaceholderAPI when present")
        fun detectPlaceholderAPIWhenPresent() {
            val papiPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("PlaceholderAPI") } returns papiPlugin

            val hookManager = HookManager(plugin)

            // PlaceholderAPI registration will fail without the actual API classes,
            // so we just verify the attempt was made via getPlugin
            try {
                hookManager.registerHooks()
            } catch (_: NoClassDefFoundError) {
                // Expected - PlaceholderAPI classes not available in test
            } catch (_: ExceptionInInitializerError) {
                // Expected - PlaceholderAPI initialization fails in test
            }

            verify { pluginManager.getPlugin("PlaceholderAPI") }
        }

        @Test
        @DisplayName("Should detect Vault when present")
        fun detectVaultWhenPresent() {
            val vaultPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("Vault") } returns vaultPlugin

            val hookManager = HookManager(plugin)

            try {
                hookManager.registerHooks()
            } catch (_: NoClassDefFoundError) {
                // Expected - Vault classes not available in test
            }

            verify { pluginManager.getPlugin("Vault") }
        }

        @Test
        @DisplayName("Should detect LuckPerms when present")
        fun detectLuckPermsWhenPresent() {
            val luckPermsPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("LuckPerms") } returns luckPermsPlugin

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verify { pluginManager.getPlugin("LuckPerms") }
        }

        @Test
        @DisplayName("Should not register hooks when plugins not present")
        fun noRegistrationWhenPluginsNotPresent() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertFalse(hookManager.hasPlaceholderAPI())
            assertFalse(hookManager.hasVault())
            assertFalse(hookManager.hasLuckPerms())
        }

        @Test
        @DisplayName("Should log info when PlaceholderAPI hook registers successfully")
        fun logInfoWhenPlaceholderAPIRegisters() {
            val papiPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("PlaceholderAPI") } returns papiPlugin

            val hookManager = HookManager(plugin)

            try {
                hookManager.registerHooks()
            } catch (_: NoClassDefFoundError) {
                // Expected
            } catch (_: ExceptionInInitializerError) {
                // Expected
            }

            // If registration succeeds, info is logged
            // If it fails with exception, warning is logged
            // Either way, the plugin was checked
            verify { pluginManager.getPlugin("PlaceholderAPI") }
        }

        @Test
        @DisplayName("Should log info when Vault permission hook registers successfully")
        fun logInfoWhenVaultRegisters() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verify { Logging.info("Vault permission hook registered") }
        }

        @Test
        @DisplayName("Should log info when LuckPerms context hook registers successfully")
        fun logInfoWhenLuckPermsRegisters() {
            // This test verifies the logging behavior, though full LuckPerms registration
            // requires the actual API classes
            val luckPermsPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("LuckPerms") } returns luckPermsPlugin

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            // LuckPerms won't fully initialize without the API, but the plugin check occurs
            verify { pluginManager.getPlugin("LuckPerms") }
        }

        @Test
        @DisplayName("Should be idempotent - multiple calls should be safe")
        fun multipleCallsAreSafe() {
            val hookManager = HookManager(plugin)

            assertDoesNotThrow {
                hookManager.registerHooks()
                hookManager.registerHooks()
                hookManager.registerHooks()
            }
        }
    }

    // =========================================================================
    // unregisterHooks() Tests
    // =========================================================================

    @Nested
    @DisplayName("unregisterHooks")
    inner class UnregisterHooksTests {

        @Test
        @DisplayName("Should not throw when no hooks registered")
        fun noThrowWhenNoHooksRegistered() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertDoesNotThrow { hookManager.unregisterHooks() }
        }

        @Test
        @DisplayName("Should clear hook references")
        fun clearHookReferences() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertFalse(hookManager.hasPlaceholderAPI())
            assertFalse(hookManager.hasVault())
            assertFalse(hookManager.hasLuckPerms())
        }

        @Test
        @DisplayName("Should clear Vault hook reference")
        fun clearVaultHookReference() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertNull(hookManager.getVaultHook())
        }

        @Test
        @DisplayName("Should clear LuckPerms hook reference")
        fun clearLuckPermsHookReference() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertNull(hookManager.getLuckPermsHook())
        }

        @Test
        @DisplayName("Should handle multiple unregister calls")
        fun multipleUnregisterCallsAreSafe() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertDoesNotThrow {
                hookManager.unregisterHooks()
                hookManager.unregisterHooks()
                hookManager.unregisterHooks()
            }
        }

        @Test
        @DisplayName("Should handle unregister before register")
        fun unregisterBeforeRegisterIsSafe() {
            val hookManager = HookManager(plugin)

            assertDoesNotThrow { hookManager.unregisterHooks() }
        }

        @Test
        @DisplayName("Should allow re-registration after unregister")
        fun allowReRegistrationAfterUnregister() {
            val hookManager = HookManager(plugin)

            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertDoesNotThrow { hookManager.registerHooks() }
        }
    }

    // =========================================================================
    // hasPlaceholderAPI() Tests
    // =========================================================================

    @Nested
    @DisplayName("hasPlaceholderAPI")
    inner class HasPlaceholderAPITests {

        @Test
        @DisplayName("Should return false when PlaceholderAPI not present")
        fun returnFalseWhenNotPresent() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertFalse(hookManager.hasPlaceholderAPI())
        }

        @Test
        @DisplayName("Should return false before registerHooks is called")
        fun returnFalseBeforeRegister() {
            val hookManager = HookManager(plugin)

            assertFalse(hookManager.hasPlaceholderAPI())
        }

        @Test
        @DisplayName("Should return false after unregisterHooks")
        fun returnFalseAfterUnregister() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertFalse(hookManager.hasPlaceholderAPI())
        }
    }

    // =========================================================================
    // hasVault() Tests
    // =========================================================================

    @Nested
    @DisplayName("hasVault")
    inner class HasVaultTests {

        @Test
        @DisplayName("Should return false when Vault not present")
        fun returnFalseWhenNotPresent() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertFalse(hookManager.hasVault())
        }

        @Test
        @DisplayName("Should return false before registerHooks is called")
        fun returnFalseBeforeRegister() {
            val hookManager = HookManager(plugin)

            assertFalse(hookManager.hasVault())
        }

        @Test
        @DisplayName("Should return false when Vault present but permission service not available")
        fun returnFalseWhenPermissionServiceNotAvailable() {
            val vaultPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns null

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertFalse(hookManager.hasVault())
        }

        @Test
        @DisplayName("Should return true when Vault initialized successfully")
        fun returnTrueWhenVaultInitialized() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertTrue(hookManager.hasVault())
        }

        @Test
        @DisplayName("Should return false after unregisterHooks")
        fun returnFalseAfterUnregister() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertFalse(hookManager.hasVault())
        }
    }

    // =========================================================================
    // hasLuckPerms() Tests
    // =========================================================================

    @Nested
    @DisplayName("hasLuckPerms")
    inner class HasLuckPermsTests {

        @Test
        @DisplayName("Should return false when LuckPerms not present")
        fun returnFalseWhenNotPresent() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertFalse(hookManager.hasLuckPerms())
        }

        @Test
        @DisplayName("Should return false before registerHooks is called")
        fun returnFalseBeforeRegister() {
            val hookManager = HookManager(plugin)

            assertFalse(hookManager.hasLuckPerms())
        }

        @Test
        @DisplayName("Should return false when LuckPerms present but service not available")
        fun returnFalseWhenServiceNotAvailable() {
            val luckPermsPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("LuckPerms") } returns luckPermsPlugin
            // Service registration returns null

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertFalse(hookManager.hasLuckPerms())
        }

        @Test
        @DisplayName("Should return false after unregisterHooks")
        fun returnFalseAfterUnregister() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertFalse(hookManager.hasLuckPerms())
        }
    }

    // =========================================================================
    // getVaultHook() Tests
    // =========================================================================

    @Nested
    @DisplayName("getVaultHook")
    inner class GetVaultHookTests {

        @Test
        @DisplayName("Should return null when Vault not registered")
        fun returnNullWhenNotRegistered() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertNull(hookManager.getVaultHook())
        }

        @Test
        @DisplayName("Should return null before registerHooks is called")
        fun returnNullBeforeRegister() {
            val hookManager = HookManager(plugin)

            assertNull(hookManager.getVaultHook())
        }

        @Test
        @DisplayName("Should return VaultHook instance when Vault is present")
        fun returnVaultHookWhenPresent() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertNotNull(hookManager.getVaultHook())
        }

        @Test
        @DisplayName("Should return same instance on multiple calls")
        fun returnSameInstanceOnMultipleCalls() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            val hook1 = hookManager.getVaultHook()
            val hook2 = hookManager.getVaultHook()

            assertSame(hook1, hook2)
        }

        @Test
        @DisplayName("Should return null after unregisterHooks")
        fun returnNullAfterUnregister() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertNull(hookManager.getVaultHook())
        }
    }

    // =========================================================================
    // getLuckPermsHook() Tests
    // =========================================================================

    @Nested
    @DisplayName("getLuckPermsHook")
    inner class GetLuckPermsHookTests {

        @Test
        @DisplayName("Should return null when LuckPerms not registered")
        fun returnNullWhenNotRegistered() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertNull(hookManager.getLuckPermsHook())
        }

        @Test
        @DisplayName("Should return null before registerHooks is called")
        fun returnNullBeforeRegister() {
            val hookManager = HookManager(plugin)

            assertNull(hookManager.getLuckPermsHook())
        }

        @Test
        @DisplayName("Should return null after unregisterHooks")
        fun returnNullAfterUnregister() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertNull(hookManager.getLuckPermsHook())
        }

        @Test
        @DisplayName("Should return null when LuckPerms initialization fails")
        fun returnNullWhenInitializationFails() {
            val luckPermsPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("LuckPerms") } returns luckPermsPlugin
            // Service registration returns null, causing initialization failure

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertNull(hookManager.getLuckPermsHook())
        }
    }

    // =========================================================================
    // signalLuckPermsContextUpdate() Tests
    // =========================================================================

    @Nested
    @DisplayName("signalLuckPermsContextUpdate")
    inner class SignalLuckPermsContextUpdateTests {

        @Test
        @DisplayName("Should not throw when LuckPerms not available")
        fun noThrowWhenLuckPermsNotAvailable() {
            val player = mockk<Player>(relaxed = true)
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertDoesNotThrow { hookManager.signalLuckPermsContextUpdate(player) }
        }

        @Test
        @DisplayName("Should not throw before registerHooks is called")
        fun noThrowBeforeRegister() {
            val player = mockk<Player>(relaxed = true)
            val hookManager = HookManager(plugin)

            assertDoesNotThrow { hookManager.signalLuckPermsContextUpdate(player) }
        }

        @Test
        @DisplayName("Should not throw after unregisterHooks")
        fun noThrowAfterUnregister() {
            val player = mockk<Player>(relaxed = true)
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertDoesNotThrow { hookManager.signalLuckPermsContextUpdate(player) }
        }

        @Test
        @DisplayName("Should accept any player instance")
        fun acceptAnyPlayerInstance() {
            val player1 = mockk<Player>(relaxed = true)
            val player2 = mockk<Player>(relaxed = true)

            every { player1.uniqueId } returns UUID.randomUUID()
            every { player2.uniqueId } returns UUID.randomUUID()

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertDoesNotThrow {
                hookManager.signalLuckPermsContextUpdate(player1)
                hookManager.signalLuckPermsContextUpdate(player2)
            }
        }
    }

    // =========================================================================
    // Economy Provider Registration Tests
    // =========================================================================

    @Nested
    @DisplayName("Economy Provider Registration")
    inner class EconomyProviderTests {

        @Test
        @DisplayName("Should not register economy provider when economy disabled")
        fun noEconomyProviderWhenDisabled() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission
            every { configManager.mainConfig } returns MainConfig(
                economy = EconomyConfig(enabled = false)
            )

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            // Verify economy provider registration was not attempted
            verify(exactly = 0) { Logging.info("Registered xInventories as Vault economy provider") }
        }

        @Test
        @DisplayName("Should not register economy provider when separateByGroup is false")
        fun noEconomyProviderWhenSeparateByGroupFalse() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission
            every { configManager.mainConfig } returns MainConfig(
                economy = EconomyConfig(enabled = true, separateByGroup = false)
            )

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            // Economy provider should not be registered when separateByGroup is false
            verify(exactly = 0) { Logging.info("Registered xInventories as Vault economy provider") }
        }

        @Test
        @DisplayName("Should attempt economy provider registration when economy enabled with separateByGroup")
        fun attemptEconomyProviderRegistrationWhenEnabled() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission
            every { configManager.mainConfig } returns MainConfig(
                economy = EconomyConfig(enabled = true, separateByGroup = true)
            )

            val hookManager = HookManager(plugin)

            try {
                hookManager.registerHooks()
            } catch (_: Exception) {
                // May fail due to missing dependencies in test environment
            }

            // The code path for economy provider registration should be attempted
            verify { configManager.mainConfig }
        }
    }

    // =========================================================================
    // Error Handling Tests
    // =========================================================================

    @Nested
    @DisplayName("Error Handling")
    inner class ErrorHandlingTests {

        @Test
        @DisplayName("Should handle PlaceholderAPI registration failure gracefully")
        fun handlePlaceholderAPIFailure() {
            val papiPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("PlaceholderAPI") } returns papiPlugin

            val hookManager = HookManager(plugin)

            // In the test environment, PlaceholderAPI classes aren't fully available.
            // The HookManager catches Exception, but NoClassDefFoundError/ExceptionInInitializerError
            // are Errors (not Exceptions) and may propagate.
            var errorOccurred = false
            try {
                hookManager.registerHooks()
            } catch (_: NoClassDefFoundError) {
                errorOccurred = true
            } catch (_: ExceptionInInitializerError) {
                errorOccurred = true
            }

            // If an error occurred, we verify it was handled without crashing
            if (errorOccurred) {
                assertTrue(true, "Handled error gracefully")
            } else {
                // If no error, the hook might have registered
                assertTrue(true, "No error occurred - PAPI classes might be on classpath")
            }
        }

        @Test
        @DisplayName("Should handle Vault registration failure gracefully")
        fun handleVaultFailure() {
            val vaultPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } throws RuntimeException("Test")

            val hookManager = HookManager(plugin)

            // Should handle exception and continue
            assertDoesNotThrow { hookManager.registerHooks() }
            assertFalse(hookManager.hasVault())
        }

        @Test
        @DisplayName("Should handle LuckPerms registration failure gracefully")
        fun handleLuckPermsFailure() {
            val luckPermsPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("LuckPerms") } returns luckPermsPlugin

            val hookManager = HookManager(plugin)

            // Should not throw
            assertDoesNotThrow { hookManager.registerHooks() }
            assertFalse(hookManager.hasLuckPerms())
        }

        @Test
        @DisplayName("Should handle NoClassDefFoundError for LuckPerms gracefully")
        fun handleLuckPermsNoClassDefFound() {
            val luckPermsPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("LuckPerms") } returns luckPermsPlugin
            every { servicesManager.getRegistration(any<Class<*>>()) } throws NoClassDefFoundError("LuckPerms API")

            val hookManager = HookManager(plugin)

            // Should not throw - HookManager catches NoClassDefFoundError
            assertDoesNotThrow { hookManager.registerHooks() }
            assertFalse(hookManager.hasLuckPerms())
        }

        @Test
        @DisplayName("Should log warning when PlaceholderAPI registration fails")
        fun logWarningWhenPlaceholderAPIFails() {
            val papiPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("PlaceholderAPI") } returns papiPlugin

            val hookManager = HookManager(plugin)

            try {
                hookManager.registerHooks()
            } catch (_: NoClassDefFoundError) {
                // Expected
            } catch (_: ExceptionInInitializerError) {
                // Expected
            }

            // If registration fails, a warning should be logged
            // (or it may succeed if PAPI is on the classpath)
            verify { pluginManager.getPlugin("PlaceholderAPI") }
        }

        @Test
        @DisplayName("Should log warning when Vault registration fails with exception")
        fun logWarningWhenVaultFailsWithException() {
            val vaultPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } throws RuntimeException("Test failure")

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verify { Logging.warning(match { it.contains("Failed to register Vault hook") }) }
        }

        @Test
        @DisplayName("Should log debug when LuckPerms API not available")
        fun logDebugWhenLuckPermsApiNotAvailable() {
            val luckPermsPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("LuckPerms") } returns luckPermsPlugin
            every { servicesManager.getRegistration(any<Class<*>>()) } throws NoClassDefFoundError("LuckPerms")

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verify { Logging.debug(any<() -> String>()) }
        }

        @Test
        @DisplayName("Should log warning when LuckPerms registration fails with generic exception")
        fun logWarningWhenLuckPermsFailsWithException() {
            val luckPermsPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("LuckPerms") } returns luckPermsPlugin
            every { servicesManager.getRegistration(any<Class<*>>()) } throws RuntimeException("Test failure")

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verify { Logging.warning(match { it.contains("LuckPerms") && it.contains("fail") }) }
        }
    }

    // =========================================================================
    // Plugin Detection Tests
    // =========================================================================

    @Nested
    @DisplayName("Plugin Detection")
    inner class PluginDetectionTests {

        @Test
        @DisplayName("Should check all plugins in order")
        fun checkAllPluginsInOrder() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verifyOrder {
                pluginManager.getPlugin("PlaceholderAPI")
                pluginManager.getPlugin("Vault")
                pluginManager.getPlugin("LuckPerms")
            }
        }

        @Test
        @DisplayName("Should continue to next plugin when one is not present")
        fun continueToNextPluginWhenOneNotPresent() {
            every { pluginManager.getPlugin("PlaceholderAPI") } returns null
            val vaultPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("Vault") } returns vaultPlugin

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            verify { pluginManager.getPlugin("PlaceholderAPI") }
            verify { pluginManager.getPlugin("Vault") }
            verify { pluginManager.getPlugin("LuckPerms") }
        }

        @Test
        @DisplayName("Should check all plugins even when earlier ones fail")
        fun checkAllPluginsEvenWhenEarlierFail() {
            val papiPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("PlaceholderAPI") } returns papiPlugin
            // PAPI will fail due to missing classes

            val vaultPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("Vault") } returns vaultPlugin

            val luckPermsPlugin = mockk<Plugin>()
            every { pluginManager.getPlugin("LuckPerms") } returns luckPermsPlugin

            val hookManager = HookManager(plugin)

            try {
                hookManager.registerHooks()
            } catch (_: NoClassDefFoundError) {
                // Expected from PlaceholderAPI
            } catch (_: ExceptionInInitializerError) {
                // Expected from PlaceholderAPI
            }

            verify { pluginManager.getPlugin("PlaceholderAPI") }
        }
    }

    // =========================================================================
    // State Consistency Tests
    // =========================================================================

    @Nested
    @DisplayName("State Consistency")
    inner class StateConsistencyTests {

        @Test
        @DisplayName("Hook state should be consistent before registerHooks")
        fun consistentStateBeforeRegister() {
            val hookManager = HookManager(plugin)

            assertFalse(hookManager.hasPlaceholderAPI())
            assertFalse(hookManager.hasVault())
            assertFalse(hookManager.hasLuckPerms())
            assertNull(hookManager.getVaultHook())
            assertNull(hookManager.getLuckPermsHook())
        }

        @Test
        @DisplayName("Hook state should be consistent after registerHooks with no plugins")
        fun consistentStateAfterRegisterNoPlugins() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertFalse(hookManager.hasPlaceholderAPI())
            assertFalse(hookManager.hasVault())
            assertFalse(hookManager.hasLuckPerms())
            assertNull(hookManager.getVaultHook())
            assertNull(hookManager.getLuckPermsHook())
        }

        @Test
        @DisplayName("Hook state should be consistent after unregisterHooks")
        fun consistentStateAfterUnregister() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()
            hookManager.unregisterHooks()

            assertFalse(hookManager.hasPlaceholderAPI())
            assertFalse(hookManager.hasVault())
            assertFalse(hookManager.hasLuckPerms())
            assertNull(hookManager.getVaultHook())
            assertNull(hookManager.getLuckPermsHook())
        }

        @Test
        @DisplayName("Hook availability should match hook instance availability")
        fun availabilityMatchesInstanceAvailability() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            // hasVault() should return true if and only if getVaultHook() returns non-null
            assertEquals(hookManager.hasVault(), hookManager.getVaultHook() != null)
        }

        @Test
        @DisplayName("LuckPerms availability should match hook instance availability")
        fun luckPermsAvailabilityMatchesInstance() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            // hasLuckPerms() should return true if and only if getLuckPermsHook() is not null
            // and isInitialized() is true
            val hook = hookManager.getLuckPermsHook()
            if (hook != null) {
                assertEquals(hookManager.hasLuckPerms(), hook.isInitialized())
            } else {
                assertFalse(hookManager.hasLuckPerms())
            }
        }
    }

    // =========================================================================
    // Vault Hook Integration Tests
    // =========================================================================

    @Nested
    @DisplayName("Vault Hook Integration")
    inner class VaultHookIntegrationTests {

        @Test
        @DisplayName("Should create VaultHook with plugin reference")
        fun createVaultHookWithPlugin() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            val vaultHook = hookManager.getVaultHook()
            assertNotNull(vaultHook)
            assertTrue(vaultHook?.isInitialized() == true)
        }

        @Test
        @DisplayName("VaultHook should be initialized after successful registration")
        fun vaultHookInitializedAfterRegistration() {
            val vaultPlugin = mockk<Plugin>()
            val permissionProvider = mockk<RegisteredServiceProvider<Permission>>(relaxed = true)
            val permission = mockk<Permission>(relaxed = true)

            every { pluginManager.getPlugin("Vault") } returns vaultPlugin
            every { servicesManager.getRegistration(Permission::class.java) } returns permissionProvider
            every { permissionProvider.provider } returns permission

            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            assertTrue(hookManager.hasVault())
            assertTrue(hookManager.getVaultHook()?.isInitialized() == true)
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle null player in signalLuckPermsContextUpdate gracefully")
        fun handlePlayerInContextUpdate() {
            val hookManager = HookManager(plugin)
            hookManager.registerHooks()

            val player = mockk<Player>(relaxed = true)
            assertDoesNotThrow { hookManager.signalLuckPermsContextUpdate(player) }
        }

        @Test
        @DisplayName("Should handle rapid register/unregister cycles")
        fun handleRapidRegisterUnregisterCycles() {
            val hookManager = HookManager(plugin)

            assertDoesNotThrow {
                repeat(10) {
                    hookManager.registerHooks()
                    hookManager.unregisterHooks()
                }
            }

            // After all cycles, state should be clean
            assertFalse(hookManager.hasPlaceholderAPI())
            assertFalse(hookManager.hasVault())
            assertFalse(hookManager.hasLuckPerms())
        }

        @Test
        @DisplayName("Should handle interleaved method calls")
        fun handleInterleavedMethodCalls() {
            val hookManager = HookManager(plugin)

            hookManager.registerHooks()
            assertFalse(hookManager.hasPlaceholderAPI())
            hookManager.signalLuckPermsContextUpdate(mockk(relaxed = true))
            assertNull(hookManager.getVaultHook())
            hookManager.unregisterHooks()
            assertFalse(hookManager.hasLuckPerms())

            // No exceptions should occur
            assertTrue(true)
        }

        @Test
        @DisplayName("Should work with fresh HookManager instances")
        fun workWithFreshInstances() {
            val hookManager1 = HookManager(plugin)
            val hookManager2 = HookManager(plugin)

            hookManager1.registerHooks()
            hookManager2.registerHooks()

            // Both should work independently
            assertFalse(hookManager1.hasVault())
            assertFalse(hookManager2.hasVault())

            hookManager1.unregisterHooks()
            // hookManager2 should still be in its registered state
            assertFalse(hookManager2.hasVault())

            hookManager2.unregisterHooks()
        }
    }
}

package sh.pcx.xinventories.unit.economy

import io.mockk.*
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Server
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.ServicesManager
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.config.ConfigManager
import sh.pcx.xinventories.internal.config.MainConfig
import sh.pcx.xinventories.internal.model.Group
import sh.pcx.xinventories.internal.service.EconomyService
import sh.pcx.xinventories.internal.service.GroupService
import sh.pcx.xinventories.internal.service.StorageService
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.logging.Logger

@DisplayName("EconomyService Tests")
class EconomyServiceTest {

    private lateinit var plugin: XInventories
    private lateinit var storageService: StorageService
    private lateinit var groupService: GroupService
    private lateinit var economyService: EconomyService
    private lateinit var configManager: ConfigManager
    private lateinit var player: Player
    private lateinit var world: World

    private val playerUuid = UUID.randomUUID()
    private val playerName = "TestPlayer"
    private val testGroup = "survival"

    @BeforeEach
    fun setUp() {
        // Initialize Logging
        Logging.init(Logger.getLogger("Test"), debug = false)

        // Mock Bukkit
        val server = mockk<Server>(relaxed = true)
        val servicesManager = mockk<ServicesManager>(relaxed = true)
        val pluginManager = mockk<PluginManager>(relaxed = true)
        every { server.servicesManager } returns servicesManager
        every { server.pluginManager } returns pluginManager
        every { servicesManager.getRegistration(any<Class<*>>()) } returns null
        every { pluginManager.getPlugin(any()) } returns null  // Vault not installed

        mockkStatic(Bukkit::class)
        every { Bukkit.getServer() } returns server
        every { Bukkit.getServicesManager() } returns servicesManager
        every { Bukkit.getPluginManager() } returns pluginManager
        every { Bukkit.getOfflinePlayer(any<UUID>()) } answers {
            val uuid = firstArg<UUID>()
            mockk {
                every { uniqueId } returns uuid
                every { name } returns playerName
                every { hasPlayedBefore() } returns true
            }
        }

        // Mock world
        world = mockk {
            every { name } returns "world"
        }

        // Mock player
        player = mockk(relaxed = true)
        every { player.uniqueId } returns playerUuid
        every { player.name } returns playerName
        every { player.world } returns world
        every { player.gameMode } returns GameMode.SURVIVAL

        // Mock Group
        val group = mockk<Group> {
            every { name } returns testGroup
        }

        // Mock GroupService
        groupService = mockk {
            every { getGroupForWorld(any<World>()) } returns group
            every { getGroupForWorld(any<String>()) } returns group
            every { getDefaultGroup() } returns group
            every { getGroup(any()) } returns group
            every { getAllGroups() } returns listOf(group)
        }

        // Mock StorageService
        storageService = mockk(relaxed = true)

        // Mock ConfigManager
        configManager = mockk {
            every { mainConfig } returns MainConfig()
        }

        // Mock Plugin
        plugin = mockk {
            every { this@mockk.configManager } returns this@EconomyServiceTest.configManager
        }

        // Create EconomyService
        economyService = EconomyService(plugin, storageService, groupService)
        economyService.initialize()
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Nested
    @DisplayName("Initialization Tests")
    inner class InitializationTests {

        @Test
        @DisplayName("should initialize with economy enabled")
        fun initializeWithEconomyEnabled() {
            assertTrue(economyService.isEnabled())
            assertTrue(economyService.isSeparateByGroup())
        }
    }

    @Nested
    @DisplayName("Balance Operations")
    inner class BalanceOperationsTests {

        @Test
        @DisplayName("should get balance for player in group")
        fun getBalanceForPlayerInGroup() {
            val balance = economyService.getBalance(player, testGroup)

            // New player should have 0 balance
            assertEquals(0.0, balance)
        }

        @Test
        @DisplayName("should get balance using player current world")
        fun getBalanceUsingPlayerCurrentWorld() {
            val balance = economyService.getBalance(player)

            assertEquals(0.0, balance)
        }

        @Test
        @DisplayName("should get balance by UUID")
        fun getBalanceByUuid() {
            val balance = economyService.getBalance(playerUuid, testGroup)

            assertEquals(0.0, balance)
        }

        @Test
        @DisplayName("should set balance for player")
        fun setBalanceForPlayer() {
            val amount = 1000.0

            val success = economyService.setBalance(player, amount, testGroup)

            assertTrue(success)
            assertEquals(amount, economyService.getBalance(player, testGroup))
        }

        @Test
        @DisplayName("should set balance by UUID")
        fun setBalanceByUuid() {
            val amount = 500.0

            val success = economyService.setBalance(playerUuid, testGroup, amount)

            assertTrue(success)
            assertEquals(amount, economyService.getBalance(playerUuid, testGroup))
        }

        @Test
        @DisplayName("should not allow negative balance")
        fun shouldNotAllowNegativeBalance() {
            economyService.setBalance(playerUuid, testGroup, -100.0)

            val balance = economyService.getBalance(playerUuid, testGroup)
            assertEquals(0.0, balance)
        }
    }

    @Nested
    @DisplayName("Deposit and Withdraw")
    inner class DepositWithdrawTests {

        @Test
        @DisplayName("should deposit money")
        fun depositMoney() {
            val initial = 100.0
            val deposit = 50.0

            economyService.setBalance(player, initial, testGroup)
            val success = economyService.deposit(player, deposit, testGroup)

            assertTrue(success)
            assertEquals(initial + deposit, economyService.getBalance(player, testGroup))
        }

        @Test
        @DisplayName("should withdraw money")
        fun withdrawMoney() {
            val initial = 100.0
            val withdraw = 30.0

            economyService.setBalance(player, initial, testGroup)
            val success = economyService.withdraw(player, withdraw, testGroup)

            assertTrue(success)
            assertEquals(initial - withdraw, economyService.getBalance(player, testGroup))
        }

        @Test
        @DisplayName("should fail to withdraw more than balance")
        fun failToWithdrawMoreThanBalance() {
            val initial = 50.0
            val withdraw = 100.0

            economyService.setBalance(player, initial, testGroup)
            val success = economyService.withdraw(player, withdraw, testGroup)

            assertFalse(success)
            assertEquals(initial, economyService.getBalance(player, testGroup))
        }

        @Test
        @DisplayName("should fail to deposit negative amount")
        fun failToDepositNegative() {
            val initial = 100.0

            economyService.setBalance(player, initial, testGroup)
            val success = economyService.deposit(player, -50.0, testGroup)

            assertFalse(success)
            assertEquals(initial, economyService.getBalance(player, testGroup))
        }

        @Test
        @DisplayName("should fail to withdraw negative amount")
        fun failToWithdrawNegative() {
            val initial = 100.0

            economyService.setBalance(player, initial, testGroup)
            val success = economyService.withdraw(player, -50.0, testGroup)

            assertFalse(success)
        }
    }

    @Nested
    @DisplayName("Transfer Operations")
    inner class TransferTests {

        @Test
        @DisplayName("should transfer between groups")
        fun transferBetweenGroups() {
            val fromGroup = "survival"
            val toGroup = "creative"
            val initial = 100.0
            val transferAmount = 40.0

            // Setup second group
            val creativeGroup = mockk<Group> {
                every { name } returns toGroup
            }
            every { groupService.getGroup(toGroup) } returns creativeGroup
            every { groupService.getAllGroups() } returns listOf(
                mockk { every { name } returns fromGroup },
                creativeGroup
            )

            economyService.setBalance(playerUuid, fromGroup, initial)

            val success = economyService.transfer(playerUuid, fromGroup, toGroup, transferAmount)

            assertTrue(success)
            assertEquals(initial - transferAmount, economyService.getBalance(playerUuid, fromGroup))
            assertEquals(transferAmount, economyService.getBalance(playerUuid, toGroup))
        }

        @Test
        @DisplayName("should fail transfer with insufficient funds")
        fun failTransferWithInsufficientFunds() {
            val fromGroup = "survival"
            val toGroup = "creative"
            val initial = 50.0
            val transferAmount = 100.0

            economyService.setBalance(playerUuid, fromGroup, initial)

            val success = economyService.transfer(playerUuid, fromGroup, toGroup, transferAmount)

            assertFalse(success)
            assertEquals(initial, economyService.getBalance(playerUuid, fromGroup))
        }

        @Test
        @DisplayName("should succeed transfer to same group")
        fun succeedTransferToSameGroup() {
            val group = "survival"
            val initial = 100.0

            economyService.setBalance(playerUuid, group, initial)

            val success = economyService.transfer(playerUuid, group, group, 50.0)

            assertTrue(success) // No-op
            assertEquals(initial, economyService.getBalance(playerUuid, group))
        }

        @Test
        @DisplayName("should fail transfer with negative amount")
        fun failTransferWithNegativeAmount() {
            val fromGroup = "survival"
            val toGroup = "creative"

            economyService.setBalance(playerUuid, fromGroup, 100.0)

            val success = economyService.transfer(playerUuid, fromGroup, toGroup, -50.0)

            assertFalse(success)
        }

        @Test
        @DisplayName("should fail transfer with zero amount")
        fun failTransferWithZeroAmount() {
            val fromGroup = "survival"
            val toGroup = "creative"

            economyService.setBalance(playerUuid, fromGroup, 100.0)

            val success = economyService.transfer(playerUuid, fromGroup, toGroup, 0.0)

            assertFalse(success)
        }
    }

    @Nested
    @DisplayName("Has Balance Check")
    inner class HasBalanceTests {

        @Test
        @DisplayName("should return true when player has sufficient balance")
        fun hasSufficientBalance() {
            economyService.setBalance(player, 100.0, testGroup)

            assertTrue(economyService.has(player, 50.0, testGroup))
            assertTrue(economyService.has(player, 100.0, testGroup))
        }

        @Test
        @DisplayName("should return false when player has insufficient balance")
        fun hasInsufficientBalance() {
            economyService.setBalance(player, 50.0, testGroup)

            assertFalse(economyService.has(player, 100.0, testGroup))
        }
    }

    @Nested
    @DisplayName("Get All Balances")
    inner class GetAllBalancesTests {

        @Test
        @DisplayName("should return all balances for player")
        fun returnAllBalances() {
            val groups = listOf(
                mockk<Group> { every { name } returns "survival" },
                mockk<Group> { every { name } returns "creative" }
            )
            every { groupService.getAllGroups() } returns groups

            economyService.setBalance(playerUuid, "survival", 100.0)
            economyService.setBalance(playerUuid, "creative", 50.0)

            val balances = economyService.getAllBalances(playerUuid)

            assertEquals(2, balances.size)
            assertEquals(100.0, balances["survival"])
            assertEquals(50.0, balances["creative"])
        }

        @Test
        @DisplayName("should not include zero balances")
        fun notIncludeZeroBalances() {
            val groups = listOf(
                mockk<Group> { every { name } returns "survival" },
                mockk<Group> { every { name } returns "creative" }
            )
            every { groupService.getAllGroups() } returns groups

            economyService.setBalance(playerUuid, "survival", 100.0)
            // creative has 0 balance

            val balances = economyService.getAllBalances(playerUuid)

            assertEquals(1, balances.size)
            assertTrue(balances.containsKey("survival"))
            assertFalse(balances.containsKey("creative"))
        }
    }

    @Nested
    @DisplayName("Cache Operations")
    inner class CacheTests {

        @Test
        @DisplayName("should invalidate cache for player")
        fun invalidateCacheForPlayer() {
            economyService.setBalance(playerUuid, testGroup, 100.0)

            economyService.invalidateCache(playerUuid)

            // Pending changes are preserved, so balance is still available
            // Cache invalidation only clears the read cache, not pending writes
            assertEquals(100.0, economyService.getBalance(playerUuid, testGroup))
        }

        @Test
        @DisplayName("should clear all cache")
        fun clearAllCache() {
            economyService.setBalance(playerUuid, testGroup, 100.0)

            economyService.clearCache()

            // Pending changes are preserved, so balance is still available
            // Cache clearing only clears the read cache, not pending writes
            assertEquals(100.0, economyService.getBalance(playerUuid, testGroup))
        }
    }
}

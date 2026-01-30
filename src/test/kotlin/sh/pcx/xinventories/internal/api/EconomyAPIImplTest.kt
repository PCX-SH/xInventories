package sh.pcx.xinventories.internal.api

import io.mockk.*
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.service.EconomyService
import sh.pcx.xinventories.internal.service.ServiceManager
import java.util.*

@DisplayName("EconomyAPIImpl")
class EconomyAPIImplTest {

    private lateinit var plugin: XInventories
    private lateinit var api: EconomyAPIImpl
    private lateinit var serviceManager: ServiceManager
    private lateinit var economyService: EconomyService

    private val testUuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        economyService = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.economyService } returns economyService

        api = EconomyAPIImpl(plugin)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Nested
    @DisplayName("isEnabled")
    inner class IsEnabled {

        @Test
        @DisplayName("returns true when economy is enabled")
        fun returnsTrueWhenEconomyIsEnabled() {
            every { economyService.isEnabled() } returns true

            val result = api.isEnabled()

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when economy is disabled")
        fun returnsFalseWhenEconomyIsDisabled() {
            every { economyService.isEnabled() } returns false

            val result = api.isEnabled()

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("isSeparateByGroup")
    inner class IsSeparateByGroup {

        @Test
        @DisplayName("returns true when balances are separate")
        fun returnsTrueWhenBalancesAreSeparate() {
            every { economyService.isSeparateByGroup() } returns true

            val result = api.isSeparateByGroup()

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when balances are shared")
        fun returnsFalseWhenBalancesAreShared() {
            every { economyService.isSeparateByGroup() } returns false

            val result = api.isSeparateByGroup()

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("getBalance")
    inner class GetBalance {

        @Test
        @DisplayName("gets balance for player in current group")
        fun getsBalanceForPlayerInCurrentGroup() {
            val player = mockk<Player>()
            every { economyService.getBalance(player, null) } returns 1000.0

            val result = api.getBalance(player)

            assertEquals(1000.0, result)
        }

        @Test
        @DisplayName("gets balance for player in specific group")
        fun getsBalanceForPlayerInSpecificGroup() {
            val player = mockk<Player>()
            every { economyService.getBalance(player, "creative") } returns 500.0

            val result = api.getBalance(player, "creative")

            assertEquals(500.0, result)
        }

        @Test
        @DisplayName("gets balance by UUID")
        fun getsBalanceByUuid() {
            every { economyService.getBalance(testUuid, "survival") } returns 2500.0

            val result = api.getBalance(testUuid, "survival")

            assertEquals(2500.0, result)
        }
    }

    @Nested
    @DisplayName("getAllBalances")
    inner class GetAllBalances {

        @Test
        @DisplayName("returns all balances across groups")
        fun returnsAllBalancesAcrossGroups() {
            every { economyService.getAllBalances(testUuid) } returns mapOf(
                "survival" to 1000.0,
                "creative" to 500.0,
                "minigames" to 250.0
            )

            val result = api.getAllBalances(testUuid)

            assertEquals(3, result.size)
            assertEquals(1000.0, result["survival"])
            assertEquals(500.0, result["creative"])
            assertEquals(250.0, result["minigames"])
        }

        @Test
        @DisplayName("returns empty map when no balances")
        fun returnsEmptyMapWhenNoBalances() {
            every { economyService.getAllBalances(testUuid) } returns emptyMap()

            val result = api.getAllBalances(testUuid)

            assertTrue(result.isEmpty())
        }
    }

    @Nested
    @DisplayName("setBalance")
    inner class SetBalance {

        @Test
        @DisplayName("sets balance for player in current group")
        fun setsBalanceForPlayerInCurrentGroup() {
            val player = mockk<Player>()
            every { economyService.setBalance(player, 1500.0, null) } returns true

            val result = api.setBalance(player, 1500.0)

            assertTrue(result)
            verify { economyService.setBalance(player, 1500.0, null) }
        }

        @Test
        @DisplayName("sets balance for player in specific group")
        fun setsBalanceForPlayerInSpecificGroup() {
            val player = mockk<Player>()
            every { economyService.setBalance(player, 750.0, "creative") } returns true

            val result = api.setBalance(player, 750.0, "creative")

            assertTrue(result)
        }

        @Test
        @DisplayName("sets balance by UUID")
        fun setsBalanceByUuid() {
            every { economyService.setBalance(testUuid, "survival", 3000.0) } returns true

            val result = api.setBalance(testUuid, "survival", 3000.0)

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when set fails")
        fun returnsFalseWhenSetFails() {
            val player = mockk<Player>()
            every { economyService.setBalance(player, any(), any()) } returns false

            val result = api.setBalance(player, 100.0)

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("deposit")
    inner class Deposit {

        @Test
        @DisplayName("deposits to current group")
        fun depositsToCurrentGroup() {
            val player = mockk<Player>()
            every { economyService.deposit(player, 500.0, null) } returns true

            val result = api.deposit(player, 500.0)

            assertTrue(result)
            verify { economyService.deposit(player, 500.0, null) }
        }

        @Test
        @DisplayName("deposits to specific group")
        fun depositsToSpecificGroup() {
            val player = mockk<Player>()
            every { economyService.deposit(player, 250.0, "creative") } returns true

            val result = api.deposit(player, 250.0, "creative")

            assertTrue(result)
        }
    }

    @Nested
    @DisplayName("withdraw")
    inner class Withdraw {

        @Test
        @DisplayName("withdraws from current group")
        fun withdrawsFromCurrentGroup() {
            val player = mockk<Player>()
            every { economyService.withdraw(player, 200.0, null) } returns true

            val result = api.withdraw(player, 200.0)

            assertTrue(result)
            verify { economyService.withdraw(player, 200.0, null) }
        }

        @Test
        @DisplayName("returns false when insufficient funds")
        fun returnsFalseWhenInsufficientFunds() {
            val player = mockk<Player>()
            every { economyService.withdraw(player, any(), any()) } returns false

            val result = api.withdraw(player, 10000.0)

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("transfer")
    inner class Transfer {

        @Test
        @DisplayName("transfers between groups for player")
        fun transfersBetweenGroupsForPlayer() {
            val player = mockk<Player>()
            every { economyService.transfer(player, "survival", "creative", 500.0) } returns true

            val result = api.transfer(player, "survival", "creative", 500.0)

            assertTrue(result)
        }

        @Test
        @DisplayName("transfers between groups by UUID")
        fun transfersBetweenGroupsByUuid() {
            every { economyService.transfer(testUuid, "creative", "minigames", 250.0) } returns true

            val result = api.transfer(testUuid, "creative", "minigames", 250.0)

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when transfer fails")
        fun returnsFalseWhenTransferFails() {
            val player = mockk<Player>()
            every { economyService.transfer(player, any(), any(), any()) } returns false

            val result = api.transfer(player, "survival", "creative", 10000.0)

            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("has")
    inner class Has {

        @Test
        @DisplayName("returns true when player has sufficient funds")
        fun returnsTrueWhenPlayerHasSufficientFunds() {
            val player = mockk<Player>()
            every { economyService.has(player, 500.0, null) } returns true

            val result = api.has(player, 500.0)

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when player has insufficient funds")
        fun returnsFalseWhenPlayerHasInsufficientFunds() {
            val player = mockk<Player>()
            every { economyService.has(player, 10000.0, null) } returns false

            val result = api.has(player, 10000.0)

            assertFalse(result)
        }

        @Test
        @DisplayName("checks specific group balance")
        fun checksSpecificGroupBalance() {
            val player = mockk<Player>()
            every { economyService.has(player, 100.0, "creative") } returns true

            val result = api.has(player, 100.0, "creative")

            assertTrue(result)
            verify { economyService.has(player, 100.0, "creative") }
        }
    }

    @Nested
    @DisplayName("hasGroupEconomy")
    inner class HasGroupEconomy {

        @Test
        @DisplayName("returns true when group has separate economy")
        fun returnsTrueWhenGroupHasSeparateEconomy() {
            every { economyService.hasGroupEconomy("survival") } returns true

            val result = api.hasGroupEconomy("survival")

            assertTrue(result)
        }

        @Test
        @DisplayName("returns false when group does not have separate economy")
        fun returnsFalseWhenGroupDoesNotHaveSeparateEconomy() {
            every { economyService.hasGroupEconomy("minigames") } returns false

            val result = api.hasGroupEconomy("minigames")

            assertFalse(result)
        }
    }
}

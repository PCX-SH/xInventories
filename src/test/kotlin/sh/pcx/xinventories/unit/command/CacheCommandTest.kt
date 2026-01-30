package sh.pcx.xinventories.unit.command

import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.api.model.CacheStatistics
import sh.pcx.xinventories.internal.command.subcommand.CacheCommand
import sh.pcx.xinventories.internal.service.MessageService
import sh.pcx.xinventories.internal.service.ServiceManager
import sh.pcx.xinventories.internal.service.StorageService
import sh.pcx.xinventories.internal.util.Logging
import java.util.UUID
import java.util.logging.Logger

/**
 * Unit tests for CacheCommand.
 *
 * Tests cover:
 * - Subcommand properties (name, permission, usage, description)
 * - execute() with "stats" action - Displaying cache statistics
 * - execute() with "clear" action - Clearing all cache
 * - execute() with "clear [player]" - Clearing specific player's cache
 * - Syntax validation when no arguments
 * - Player not found error handling
 * - tabComplete() - Action completions and player names for clear
 */
@DisplayName("CacheCommand Unit Tests")
class CacheCommandTest {

    private lateinit var plugin: XInventories
    private lateinit var serviceManager: ServiceManager
    private lateinit var storageService: StorageService
    private lateinit var messageService: MessageService
    private lateinit var cacheCommand: CacheCommand

    companion object {
        @JvmStatic
        @BeforeAll
        fun setupAll() {
            Logging.init(Logger.getLogger("CacheCommandTest"), false)
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

    @BeforeEach
    fun setUp() {
        plugin = mockk(relaxed = true)
        serviceManager = mockk(relaxed = true)
        storageService = mockk(relaxed = true)
        messageService = mockk(relaxed = true)

        every { plugin.serviceManager } returns serviceManager
        every { serviceManager.storageService } returns storageService
        every { serviceManager.messageService } returns messageService

        // Mock Bukkit static methods
        mockkStatic(Bukkit::class)

        // Default mock for getOfflinePlayer(String) to return a player that hasn't played before
        // This prevents NoClassDefFoundError when the real Bukkit tries to resolve players
        every { Bukkit.getOfflinePlayer(any<String>()) } answers {
            val name = firstArg<String>()
            mockk<OfflinePlayer>(relaxed = true) {
                every { uniqueId } returns UUID.randomUUID()
                every { this@mockk.name } returns name
                every { hasPlayedBefore() } returns false
            }
        }

        // Default mock for getPlayer(String) to return null
        every { Bukkit.getPlayer(any<String>()) } returns null

        cacheCommand = CacheCommand()
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
        unmockkStatic(Bukkit::class)
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private fun createMockPlayer(
        uuid: UUID = UUID.randomUUID(),
        name: String = "TestPlayer"
    ): Player {
        return mockk<Player>(relaxed = true).apply {
            every { uniqueId } returns uuid
            every { this@apply.name } returns name
        }
    }

    private fun createMockOfflinePlayer(
        uuid: UUID = UUID.randomUUID(),
        name: String = "OfflinePlayer",
        hasPlayedBefore: Boolean = true
    ): OfflinePlayer {
        return mockk<OfflinePlayer>(relaxed = true).apply {
            every { uniqueId } returns uuid
            every { this@apply.name } returns name
            every { this@apply.hasPlayedBefore() } returns hasPlayedBefore
        }
    }

    private fun createMockCommandSender(name: String = "CONSOLE"): CommandSender {
        return mockk<CommandSender>(relaxed = true).apply {
            every { this@apply.name } returns name
        }
    }

    private fun createCacheStats(
        size: Int = 50,
        maxSize: Int = 1000,
        hitCount: Long = 100,
        missCount: Long = 20,
        loadCount: Long = 50,
        evictionCount: Long = 5
    ): CacheStatistics {
        return CacheStatistics(
            size = size,
            maxSize = maxSize,
            hitCount = hitCount,
            missCount = missCount,
            loadCount = loadCount,
            evictionCount = evictionCount
        )
    }

    // =========================================================================
    // Property Tests
    // =========================================================================

    @Nested
    @DisplayName("Subcommand Properties")
    inner class PropertyTests {

        @Test
        @DisplayName("Should have correct name")
        fun hasCorrectName() {
            assertEquals("cache", cacheCommand.name)
        }

        @Test
        @DisplayName("Should have correct permission")
        fun hasCorrectPermission() {
            assertEquals("xinventories.command.cache", cacheCommand.permission)
        }

        @Test
        @DisplayName("Should have correct usage")
        fun hasCorrectUsage() {
            assertEquals("/xinv cache <stats|clear> [player]", cacheCommand.usage)
        }

        @Test
        @DisplayName("Should have correct description")
        fun hasCorrectDescription() {
            assertEquals("Manage inventory cache", cacheCommand.description)
        }

        @Test
        @DisplayName("Should have empty aliases by default")
        fun hasEmptyAliases() {
            assertTrue(cacheCommand.aliases.isEmpty())
        }

        @Test
        @DisplayName("Should not be player-only command")
        fun notPlayerOnlyCommand() {
            assertFalse(cacheCommand.playerOnly)
        }
    }

    // =========================================================================
    // Execute - Stats Action Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Stats Action")
    inner class ExecuteStatsActionTests {

        @Test
        @DisplayName("Should show cache stats when 'stats' action specified")
        fun showCacheStatsWhenStatsActionSpecified() = runTest {
            val sender = createMockCommandSender()
            val stats = createCacheStats(
                size = 75,
                maxSize = 500,
                hitCount = 200,
                missCount = 50,
                loadCount = 100,
                evictionCount = 10
            )

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, sender, arrayOf("stats"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "cache-stats-header") }
            verify { messageService.sendRaw(sender, "cache-stats-size", "size" to "75", "max" to "500") }
            verify { messageService.sendRaw(sender, "cache-stats-hits", "hits" to "200", "rate" to "80.0") }
            verify { messageService.sendRaw(sender, "cache-stats-misses", "misses" to "50") }
            verify { messageService.sendRaw(sender, "cache-stats-evictions", "evictions" to "10") }
        }

        @Test
        @DisplayName("Should handle stats action case insensitively")
        fun handleStatsActionCaseInsensitively() = runTest {
            val sender = createMockCommandSender()
            val stats = createCacheStats()

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, sender, arrayOf("STATS"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "cache-stats-header") }
        }

        @Test
        @DisplayName("Should handle mixed case stats action")
        fun handleMixedCaseStatsAction() = runTest {
            val sender = createMockCommandSender()
            val stats = createCacheStats()

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, sender, arrayOf("StAtS"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "cache-stats-header") }
        }

        @Test
        @DisplayName("Should show zero hit rate when no hits or misses")
        fun showZeroHitRateWhenNoHitsOrMisses() = runTest {
            val sender = createMockCommandSender()
            val stats = createCacheStats(hitCount = 0, missCount = 0)

            every { storageService.getCacheStats() } returns stats

            cacheCommand.execute(plugin, sender, arrayOf("stats"))

            verify { messageService.sendRaw(sender, "cache-stats-hits", "hits" to "0", "rate" to "0.0") }
        }

        @Test
        @DisplayName("Should show 100% hit rate when all hits")
        fun show100PercentHitRateWhenAllHits() = runTest {
            val sender = createMockCommandSender()
            val stats = createCacheStats(hitCount = 100, missCount = 0)

            every { storageService.getCacheStats() } returns stats

            cacheCommand.execute(plugin, sender, arrayOf("stats"))

            verify { messageService.sendRaw(sender, "cache-stats-hits", "hits" to "100", "rate" to "100.0") }
        }

        @Test
        @DisplayName("Should format hit rate to one decimal place")
        fun formatHitRateToOneDecimalPlace() = runTest {
            val sender = createMockCommandSender()
            // 7 hits, 3 misses = 70% hit rate
            val stats = createCacheStats(hitCount = 7, missCount = 3)

            every { storageService.getCacheStats() } returns stats

            cacheCommand.execute(plugin, sender, arrayOf("stats"))

            verify { messageService.sendRaw(sender, "cache-stats-hits", "hits" to "7", "rate" to "70.0") }
        }

        @Test
        @DisplayName("Should show zero values in stats")
        fun showZeroValuesInStats() = runTest {
            val sender = createMockCommandSender()
            val stats = CacheStatistics(
                size = 0,
                maxSize = 0,
                hitCount = 0,
                missCount = 0,
                loadCount = 0,
                evictionCount = 0
            )

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, sender, arrayOf("stats"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "cache-stats-size", "size" to "0", "max" to "0") }
            verify { messageService.sendRaw(sender, "cache-stats-misses", "misses" to "0") }
            verify { messageService.sendRaw(sender, "cache-stats-evictions", "evictions" to "0") }
        }

        @Test
        @DisplayName("Should show large values in stats")
        fun showLargeValuesInStats() = runTest {
            val sender = createMockCommandSender()
            val stats = CacheStatistics(
                size = 999999,
                maxSize = 1000000,
                hitCount = 1000000000L,
                missCount = 500000000L,
                loadCount = 100000000L,
                evictionCount = 50000000L
            )

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, sender, arrayOf("stats"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "cache-stats-size", "size" to "999999", "max" to "1000000") }
            verify { messageService.sendRaw(sender, "cache-stats-evictions", "evictions" to "50000000") }
        }
    }

    // =========================================================================
    // Execute - Clear All Action Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Clear All Action")
    inner class ExecuteClearAllActionTests {

        @Test
        @DisplayName("Should clear all cache when 'clear' action with no player specified")
        fun clearAllCacheWhenClearActionWithNoPlayer() = runTest {
            val sender = createMockCommandSender()

            every { storageService.clearCache() } returns 50

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear"))

            assertTrue(result)
            verify { storageService.clearCache() }
            verify { messageService.send(sender, "cache-cleared", "count" to "50") }
        }

        @Test
        @DisplayName("Should handle clear action case insensitively")
        fun handleClearActionCaseInsensitively() = runTest {
            val sender = createMockCommandSender()

            every { storageService.clearCache() } returns 10

            val result = cacheCommand.execute(plugin, sender, arrayOf("CLEAR"))

            assertTrue(result)
            verify { storageService.clearCache() }
            verify { messageService.send(sender, "cache-cleared", "count" to "10") }
        }

        @Test
        @DisplayName("Should handle mixed case clear action")
        fun handleMixedCaseClearAction() = runTest {
            val sender = createMockCommandSender()

            every { storageService.clearCache() } returns 25

            val result = cacheCommand.execute(plugin, sender, arrayOf("ClEaR"))

            assertTrue(result)
            verify { storageService.clearCache() }
        }

        @Test
        @DisplayName("Should show zero when no entries cleared")
        fun showZeroWhenNoEntriesCleared() = runTest {
            val sender = createMockCommandSender()

            every { storageService.clearCache() } returns 0

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear"))

            assertTrue(result)
            verify { messageService.send(sender, "cache-cleared", "count" to "0") }
        }

        @Test
        @DisplayName("Should show large count when many entries cleared")
        fun showLargeCountWhenManyEntriesCleared() = runTest {
            val sender = createMockCommandSender()

            every { storageService.clearCache() } returns 100000

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear"))

            assertTrue(result)
            verify { messageService.send(sender, "cache-cleared", "count" to "100000") }
        }
    }

    // =========================================================================
    // Execute - Clear Player Action Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Clear Player Action")
    inner class ExecuteClearPlayerActionTests {

        @Test
        @DisplayName("Should clear specific online player's cache")
        fun clearSpecificOnlinePlayerCache() = runTest {
            val sender = createMockCommandSender()
            val targetUuid = UUID.randomUUID()
            val targetPlayer = createMockPlayer(uuid = targetUuid, name = "TargetPlayer")

            every { Bukkit.getPlayer("TargetPlayer") } returns targetPlayer

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", "TargetPlayer"))

            assertTrue(result)
            verify { storageService.invalidateCache(targetUuid) }
            verify { messageService.send(sender, "cache-player-cleared", "player" to "TargetPlayer") }
        }

        @Test
        @DisplayName("Should clear specific offline player's cache when player has played before")
        fun clearSpecificOfflinePlayerCacheWhenHasPlayedBefore() = runTest {
            val sender = createMockCommandSender()
            val targetUuid = UUID.randomUUID()
            val offlinePlayer = createMockOfflinePlayer(uuid = targetUuid, name = "OfflinePlayer", hasPlayedBefore = true)

            every { Bukkit.getPlayer("OfflinePlayer") } returns null
            every { Bukkit.getOfflinePlayer("OfflinePlayer") } returns offlinePlayer

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", "OfflinePlayer"))

            assertTrue(result)
            verify { storageService.invalidateCache(targetUuid) }
            verify { messageService.send(sender, "cache-player-cleared", "player" to "OfflinePlayer") }
        }

        @Test
        @DisplayName("Should show player not found error when player doesn't exist")
        fun showPlayerNotFoundErrorWhenPlayerDoesntExist() = runTest {
            val sender = createMockCommandSender()
            val offlinePlayer = createMockOfflinePlayer(hasPlayedBefore = false)

            every { Bukkit.getPlayer("NonExistent") } returns null
            every { Bukkit.getOfflinePlayer("NonExistent") } returns offlinePlayer

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", "NonExistent"))

            assertTrue(result)
            verify { messageService.send(sender, "player-not-found", "player" to "NonExistent") }
            verify(exactly = 0) { storageService.invalidateCache(any()) }
        }

        @Test
        @DisplayName("Should prefer online player over offline player")
        fun preferOnlinePlayerOverOfflinePlayer() = runTest {
            val sender = createMockCommandSender()
            val onlineUuid = UUID.randomUUID()
            val offlineUuid = UUID.randomUUID()
            val onlinePlayer = createMockPlayer(uuid = onlineUuid, name = "Player")
            val offlinePlayer = createMockOfflinePlayer(uuid = offlineUuid, hasPlayedBefore = true)

            every { Bukkit.getPlayer("Player") } returns onlinePlayer
            every { Bukkit.getOfflinePlayer("Player") } returns offlinePlayer

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", "Player"))

            assertTrue(result)
            verify { storageService.invalidateCache(onlineUuid) }
            verify(exactly = 0) { storageService.invalidateCache(offlineUuid) }
        }

        @Test
        @DisplayName("Should handle player name case sensitivity")
        fun handlePlayerNameCaseSensitivity() = runTest {
            val sender = createMockCommandSender()
            val targetUuid = UUID.randomUUID()
            val targetPlayer = createMockPlayer(uuid = targetUuid, name = "TestPlayer")

            // Bukkit.getPlayer is case-insensitive
            every { Bukkit.getPlayer("testplayer") } returns targetPlayer

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", "testplayer"))

            assertTrue(result)
            verify { storageService.invalidateCache(targetUuid) }
            verify { messageService.send(sender, "cache-player-cleared", "player" to "testplayer") }
        }

        @Test
        @DisplayName("Should handle player name with special characters")
        fun handlePlayerNameWithSpecialCharacters() = runTest {
            val sender = createMockCommandSender()
            val targetUuid = UUID.randomUUID()
            val targetPlayer = createMockPlayer(uuid = targetUuid, name = "Player_123")

            every { Bukkit.getPlayer("Player_123") } returns targetPlayer

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", "Player_123"))

            assertTrue(result)
            verify { storageService.invalidateCache(targetUuid) }
            verify { messageService.send(sender, "cache-player-cleared", "player" to "Player_123") }
        }

        @Test
        @DisplayName("Should not check offline player when online player found")
        fun notCheckOfflinePlayerWhenOnlinePlayerFound() = runTest {
            val sender = createMockCommandSender()
            val onlinePlayer = createMockPlayer(name = "OnlinePlayer")

            every { Bukkit.getPlayer("OnlinePlayer") } returns onlinePlayer

            cacheCommand.execute(plugin, sender, arrayOf("clear", "OnlinePlayer"))

            verify(exactly = 0) { Bukkit.getOfflinePlayer(any<String>()) }
        }
    }

    // =========================================================================
    // Execute - Syntax Validation Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Syntax Validation")
    inner class ExecuteSyntaxValidationTests {

        @Test
        @DisplayName("Should show invalid syntax when no arguments provided")
        fun showInvalidSyntaxWhenNoArguments() = runTest {
            val sender = createMockCommandSender()

            val result = cacheCommand.execute(plugin, sender, emptyArray())

            assertTrue(result)
            verify { messageService.send(sender, "invalid-syntax", "usage" to "/xinv cache <stats|clear> [player]") }
        }

        @Test
        @DisplayName("Should show invalid syntax for unknown action")
        fun showInvalidSyntaxForUnknownAction() = runTest {
            val sender = createMockCommandSender()

            val result = cacheCommand.execute(plugin, sender, arrayOf("unknown"))

            assertTrue(result)
            verify { messageService.send(sender, "invalid-syntax", "usage" to "/xinv cache <stats|clear> [player]") }
        }

        @Test
        @DisplayName("Should show invalid syntax for gibberish action")
        fun showInvalidSyntaxForGibberishAction() = runTest {
            val sender = createMockCommandSender()

            val result = cacheCommand.execute(plugin, sender, arrayOf("asdf1234"))

            assertTrue(result)
            verify { messageService.send(sender, "invalid-syntax", "usage" to "/xinv cache <stats|clear> [player]") }
        }

        @Test
        @DisplayName("Should show invalid syntax for empty string action")
        fun showInvalidSyntaxForEmptyStringAction() = runTest {
            val sender = createMockCommandSender()

            val result = cacheCommand.execute(plugin, sender, arrayOf(""))

            assertTrue(result)
            verify { messageService.send(sender, "invalid-syntax", "usage" to "/xinv cache <stats|clear> [player]") }
        }
    }

    // =========================================================================
    // Execute - Return Value Tests
    // =========================================================================

    @Nested
    @DisplayName("execute() - Return Values")
    inner class ExecuteReturnValueTests {

        @Test
        @DisplayName("Should always return true from execute")
        fun alwaysReturnTrueFromExecute() = runTest {
            val sender = createMockCommandSender()

            every { storageService.getCacheStats() } returns createCacheStats()
            every { storageService.clearCache() } returns 10

            val resultEmpty = cacheCommand.execute(plugin, sender, emptyArray())
            val resultStats = cacheCommand.execute(plugin, sender, arrayOf("stats"))
            val resultClear = cacheCommand.execute(plugin, sender, arrayOf("clear"))
            val resultUnknown = cacheCommand.execute(plugin, sender, arrayOf("unknown"))

            assertTrue(resultEmpty)
            assertTrue(resultStats)
            assertTrue(resultClear)
            assertTrue(resultUnknown)
        }

        @Test
        @DisplayName("Should return true when player not found")
        fun returnTrueWhenPlayerNotFound() = runTest {
            val sender = createMockCommandSender()
            val offlinePlayer = createMockOfflinePlayer(hasPlayedBefore = false)

            every { Bukkit.getPlayer("NonExistent") } returns null
            every { Bukkit.getOfflinePlayer("NonExistent") } returns offlinePlayer

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", "NonExistent"))

            assertTrue(result)
        }

        @Test
        @DisplayName("Should return true when player cache cleared")
        fun returnTrueWhenPlayerCacheCleared() = runTest {
            val sender = createMockCommandSender()
            val player = createMockPlayer()

            every { Bukkit.getPlayer("TestPlayer") } returns player

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", "TestPlayer"))

            assertTrue(result)
        }
    }

    // =========================================================================
    // Tab Complete Tests
    // =========================================================================

    @Nested
    @DisplayName("tabComplete()")
    inner class TabCompleteTests {

        @Test
        @DisplayName("Should return all actions for empty first argument")
        fun returnAllActionsForEmptyFirstArg() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf(""))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("stats"))
            assertTrue(completions.contains("clear"))
        }

        @Test
        @DisplayName("Should filter actions by 's' prefix")
        fun filterActionsBySPrefix() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("s"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("stats"))
        }

        @Test
        @DisplayName("Should filter actions by 'c' prefix")
        fun filterActionsByCPrefix() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("c"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("clear"))
        }

        @Test
        @DisplayName("Should filter actions by 'st' prefix")
        fun filterActionsByStPrefix() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("st"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("stats"))
        }

        @Test
        @DisplayName("Should filter actions by 'cl' prefix")
        fun filterActionsByClPrefix() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("cl"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("clear"))
        }

        @Test
        @DisplayName("Should return empty list for non-matching prefix")
        fun returnEmptyListForNonMatchingPrefix() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("x"))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should filter actions case insensitively")
        fun filterActionsCaseInsensitively() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("S"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("stats"))
        }

        @Test
        @DisplayName("Should return player names for 'clear' action")
        fun returnPlayerNamesForClearAction() {
            val sender = createMockCommandSender()
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Bob"),
                createMockPlayer(name = "Charlie")
            )

            every { Bukkit.getOnlinePlayers() } returns players

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("clear", ""))

            assertEquals(3, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Bob"))
            assertTrue(completions.contains("Charlie"))
        }

        @Test
        @DisplayName("Should filter player names by partial match")
        fun filterPlayerNamesByPartialMatch() {
            val sender = createMockCommandSender()
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Adam"),
                createMockPlayer(name = "Bob")
            )

            every { Bukkit.getOnlinePlayers() } returns players

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("clear", "A"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Adam"))
            assertFalse(completions.contains("Bob"))
        }

        @Test
        @DisplayName("Should filter player names case insensitively")
        fun filterPlayerNamesCaseInsensitively() {
            val sender = createMockCommandSender()
            val players = listOf(
                createMockPlayer(name = "Alice"),
                createMockPlayer(name = "Adam"),
                createMockPlayer(name = "Bob")
            )

            every { Bukkit.getOnlinePlayers() } returns players

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("clear", "a"))

            assertEquals(2, completions.size)
            assertTrue(completions.contains("Alice"))
            assertTrue(completions.contains("Adam"))
        }

        @Test
        @DisplayName("Should return empty list for stats action at second argument")
        fun returnEmptyListForStatsActionAtSecondArg() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("stats", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list for unknown action at second argument")
        fun returnEmptyListForUnknownActionAtSecondArg() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("unknown", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should return empty list for third argument and beyond")
        fun returnEmptyListForThirdArgumentAndBeyond() {
            val sender = createMockCommandSender()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("clear", "Alice", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should handle empty online players list")
        fun handleEmptyOnlinePlayersList() {
            val sender = createMockCommandSender()

            every { Bukkit.getOnlinePlayers() } returns emptyList()

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("clear", ""))

            assertTrue(completions.isEmpty())
        }

        @Test
        @DisplayName("Should handle clear action case insensitively for tab complete")
        fun handleClearActionCaseInsensitivelyForTabComplete() {
            val sender = createMockCommandSender()
            val players = listOf(createMockPlayer(name = "TestPlayer"))

            every { Bukkit.getOnlinePlayers() } returns players

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("CLEAR", ""))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("TestPlayer"))
        }

        @Test
        @DisplayName("Should handle special characters in player names for tab completion")
        fun handleSpecialCharactersInPlayerNamesForTabCompletion() {
            val sender = createMockCommandSender()
            val players = listOf(
                createMockPlayer(name = "Player_With_Underscore"),
                createMockPlayer(name = "Player123"),
                createMockPlayer(name = "xX_Pro_Xx")
            )

            every { Bukkit.getOnlinePlayers() } returns players

            val completions = cacheCommand.tabComplete(plugin, sender, arrayOf("clear", "x"))

            assertEquals(1, completions.size)
            assertTrue(completions.contains("xX_Pro_Xx"))
        }

        @Test
        @DisplayName("Should return empty list for empty args")
        fun returnEmptyListForEmptyArgs() {
            val sender = createMockCommandSender()

            // When args.size is 0 (no args at all), it doesn't match any case
            val completions = cacheCommand.tabComplete(plugin, sender, emptyArray())

            assertTrue(completions.isEmpty())
        }
    }

    // =========================================================================
    // Permission Tests
    // =========================================================================

    @Nested
    @DisplayName("Permission Checks")
    inner class PermissionTests {

        @Test
        @DisplayName("Should return true when sender has permission")
        fun returnTrueWhenHasPermission() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission("xinventories.command.cache") } returns true

            val result = cacheCommand.hasPermission(sender)

            assertTrue(result)
        }

        @Test
        @DisplayName("Should return false when sender lacks permission")
        fun returnFalseWhenLacksPermission() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission("xinventories.command.cache") } returns false

            val result = cacheCommand.hasPermission(sender)

            assertFalse(result)
        }

        @Test
        @DisplayName("Should check correct permission string")
        fun checkCorrectPermissionString() {
            val sender = mockk<CommandSender>(relaxed = true)
            every { sender.hasPermission(any<String>()) } returns true

            cacheCommand.hasPermission(sender)

            verify(exactly = 1) { sender.hasPermission("xinventories.command.cache") }
        }
    }

    // =========================================================================
    // Subcommand Interface Compliance Tests
    // =========================================================================

    @Nested
    @DisplayName("Subcommand Interface Compliance")
    inner class SubcommandInterfaceComplianceTests {

        @Test
        @DisplayName("Should implement all required properties")
        fun implementAllRequiredProperties() {
            assertTrue(cacheCommand.name.isNotEmpty())
            assertTrue(cacheCommand.permission.isNotEmpty())
            assertTrue(cacheCommand.usage.isNotEmpty())
            assertTrue(cacheCommand.description.isNotEmpty())
            assertTrue(cacheCommand.aliases.isEmpty() || cacheCommand.aliases.isNotEmpty())
            assertFalse(cacheCommand.playerOnly)
        }

        @Test
        @DisplayName("Should have non-blank name")
        fun haveNonBlankName() {
            assertTrue(cacheCommand.name.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank permission")
        fun haveNonBlankPermission() {
            assertTrue(cacheCommand.permission.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank usage")
        fun haveNonBlankUsage() {
            assertTrue(cacheCommand.usage.isNotBlank())
        }

        @Test
        @DisplayName("Should have non-blank description")
        fun haveNonBlankDescription() {
            assertTrue(cacheCommand.description.isNotBlank())
        }

        @Test
        @DisplayName("Should have permission starting with xinventories")
        fun havePermissionStartingWithXinventories() {
            assertTrue(cacheCommand.permission.startsWith("xinventories."))
        }

        @Test
        @DisplayName("Should have usage containing command name")
        fun haveUsageContainingCommandName() {
            assertTrue(cacheCommand.usage.contains(cacheCommand.name))
        }
    }

    // =========================================================================
    // Edge Cases Tests
    // =========================================================================

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCasesTests {

        @Test
        @DisplayName("Should handle extra arguments for stats action")
        fun handleExtraArgumentsForStatsAction() = runTest {
            val sender = createMockCommandSender()
            val stats = createCacheStats()

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, sender, arrayOf("stats", "extra", "args", "ignored"))

            assertTrue(result)
            verify { messageService.sendRaw(sender, "cache-stats-header") }
        }

        @Test
        @DisplayName("Should handle extra arguments after player name")
        fun handleExtraArgumentsAfterPlayerName() = runTest {
            val sender = createMockCommandSender()
            val player = createMockPlayer(name = "TestPlayer")

            every { Bukkit.getPlayer("TestPlayer") } returns player

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", "TestPlayer", "extra", "args"))

            assertTrue(result)
            // Only the first argument after "clear" should be used as player name
            verify { Bukkit.getPlayer("TestPlayer") }
            verify { storageService.invalidateCache(any()) }
        }

        @Test
        @DisplayName("Should work with Player sender")
        fun workWithPlayerSender() = runTest {
            val playerSender = createMockPlayer(name = "AdminPlayer")
            val stats = createCacheStats()

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, playerSender, arrayOf("stats"))

            assertTrue(result)
            verify { messageService.sendRaw(playerSender, "cache-stats-header") }
        }

        @Test
        @DisplayName("Should work with console sender")
        fun workWithConsoleSender() = runTest {
            val consoleSender = createMockCommandSender("CONSOLE")
            val stats = createCacheStats()

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, consoleSender, arrayOf("stats"))

            assertTrue(result)
            verify { messageService.sendRaw(consoleSender, "cache-stats-header") }
        }

        @Test
        @DisplayName("Should handle clearing own cache as player")
        fun handleClearingOwnCacheAsPlayer() = runTest {
            val playerUuid = UUID.randomUUID()
            val playerSender = createMockPlayer(uuid = playerUuid, name = "SelfPlayer")

            every { Bukkit.getPlayer("SelfPlayer") } returns playerSender

            val result = cacheCommand.execute(plugin, playerSender, arrayOf("clear", "SelfPlayer"))

            assertTrue(result)
            verify { storageService.invalidateCache(playerUuid) }
            verify { messageService.send(playerSender, "cache-player-cleared", "player" to "SelfPlayer") }
        }

        @Test
        @DisplayName("Should handle very long player name")
        fun handleVeryLongPlayerName() = runTest {
            val sender = createMockCommandSender()
            val longName = "A".repeat(100)
            val offlinePlayer = createMockOfflinePlayer(hasPlayedBefore = false)

            every { Bukkit.getPlayer(longName) } returns null
            every { Bukkit.getOfflinePlayer(longName) } returns offlinePlayer

            val result = cacheCommand.execute(plugin, sender, arrayOf("clear", longName))

            assertTrue(result)
            verify { messageService.send(sender, "player-not-found", "player" to longName) }
        }

        @Test
        @DisplayName("Should handle whitespace in action argument")
        fun handleWhitespaceInActionArgument() = runTest {
            val sender = createMockCommandSender()

            // " stats" or "stats " would be different from "stats"
            val result = cacheCommand.execute(plugin, sender, arrayOf(" stats"))

            assertTrue(result)
            // " stats".lowercase() != "stats" so it goes to else branch
            verify { messageService.send(sender, "invalid-syntax", "usage" to "/xinv cache <stats|clear> [player]") }
        }
    }

    // =========================================================================
    // Different Sender Type Tests
    // =========================================================================

    @Nested
    @DisplayName("Different Sender Types")
    inner class DifferentSenderTypeTests {

        @Test
        @DisplayName("Should work with console CommandSender for stats action")
        fun workWithConsoleForStatsAction() = runTest {
            val consoleSender = createMockCommandSender("CONSOLE")
            val stats = createCacheStats()

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, consoleSender, arrayOf("stats"))

            assertTrue(result)
            verify { messageService.sendRaw(consoleSender, "cache-stats-header") }
        }

        @Test
        @DisplayName("Should work with console CommandSender for clear action")
        fun workWithConsoleForClearAction() = runTest {
            val consoleSender = createMockCommandSender("CONSOLE")

            every { storageService.clearCache() } returns 50

            val result = cacheCommand.execute(plugin, consoleSender, arrayOf("clear"))

            assertTrue(result)
            verify { messageService.send(consoleSender, "cache-cleared", "count" to "50") }
        }

        @Test
        @DisplayName("Should work with console CommandSender for clear player action")
        fun workWithConsoleForClearPlayerAction() = runTest {
            val consoleSender = createMockCommandSender("CONSOLE")
            val targetPlayer = createMockPlayer(name = "Target")

            every { Bukkit.getPlayer("Target") } returns targetPlayer

            val result = cacheCommand.execute(plugin, consoleSender, arrayOf("clear", "Target"))

            assertTrue(result)
            verify { messageService.send(consoleSender, "cache-player-cleared", "player" to "Target") }
        }

        @Test
        @DisplayName("Should work with Player sender for stats action")
        fun workWithPlayerForStatsAction() = runTest {
            val player = createMockPlayer(name = "AdminPlayer")
            val stats = createCacheStats()

            every { storageService.getCacheStats() } returns stats

            val result = cacheCommand.execute(plugin, player, arrayOf("stats"))

            assertTrue(result)
            verify { messageService.sendRaw(player, "cache-stats-header") }
        }

        @Test
        @DisplayName("Should work with Player sender for clear action")
        fun workWithPlayerForClearAction() = runTest {
            val player = createMockPlayer(name = "AdminPlayer")

            every { storageService.clearCache() } returns 25

            val result = cacheCommand.execute(plugin, player, arrayOf("clear"))

            assertTrue(result)
            verify { messageService.send(player, "cache-cleared", "count" to "25") }
        }

        @Test
        @DisplayName("Should work with Player sender for clear player action")
        fun workWithPlayerForClearPlayerAction() = runTest {
            val adminPlayer = createMockPlayer(name = "AdminPlayer")
            val targetUuid = UUID.randomUUID()
            val targetPlayer = createMockPlayer(uuid = targetUuid, name = "Target")

            every { Bukkit.getPlayer("Target") } returns targetPlayer

            val result = cacheCommand.execute(plugin, adminPlayer, arrayOf("clear", "Target"))

            assertTrue(result)
            verify { storageService.invalidateCache(targetUuid) }
            verify { messageService.send(adminPlayer, "cache-player-cleared", "player" to "Target") }
        }
    }

    // =========================================================================
    // Message Service Interaction Tests
    // =========================================================================

    @Nested
    @DisplayName("MessageService Interactions")
    inner class MessageServiceInteractionTests {

        @Test
        @DisplayName("Should use sendRaw for stats messages")
        fun useSendRawForStatsMessages() = runTest {
            val sender = createMockCommandSender()
            val stats = createCacheStats()

            every { storageService.getCacheStats() } returns stats

            cacheCommand.execute(plugin, sender, arrayOf("stats"))

            verify { messageService.sendRaw(sender, "cache-stats-header") }
            verify { messageService.sendRaw(sender, "cache-stats-size", any(), any()) }
            verify { messageService.sendRaw(sender, "cache-stats-hits", any(), any()) }
            verify { messageService.sendRaw(sender, "cache-stats-misses", any()) }
            verify { messageService.sendRaw(sender, "cache-stats-evictions", any()) }
        }

        @Test
        @DisplayName("Should use send for cache cleared message")
        fun useSendForCacheClearedMessage() = runTest {
            val sender = createMockCommandSender()

            every { storageService.clearCache() } returns 10

            cacheCommand.execute(plugin, sender, arrayOf("clear"))

            verify { messageService.send(sender, "cache-cleared", "count" to "10") }
        }

        @Test
        @DisplayName("Should use send for player cache cleared message")
        fun useSendForPlayerCacheClearedMessage() = runTest {
            val sender = createMockCommandSender()
            val player = createMockPlayer(name = "Target")

            every { Bukkit.getPlayer("Target") } returns player

            cacheCommand.execute(plugin, sender, arrayOf("clear", "Target"))

            verify { messageService.send(sender, "cache-player-cleared", "player" to "Target") }
        }

        @Test
        @DisplayName("Should use send for player not found message")
        fun useSendForPlayerNotFoundMessage() = runTest {
            val sender = createMockCommandSender()
            val offlinePlayer = createMockOfflinePlayer(hasPlayedBefore = false)

            every { Bukkit.getPlayer("Unknown") } returns null
            every { Bukkit.getOfflinePlayer("Unknown") } returns offlinePlayer

            cacheCommand.execute(plugin, sender, arrayOf("clear", "Unknown"))

            verify { messageService.send(sender, "player-not-found", "player" to "Unknown") }
        }

        @Test
        @DisplayName("Should use send for invalid syntax message")
        fun useSendForInvalidSyntaxMessage() = runTest {
            val sender = createMockCommandSender()

            cacheCommand.execute(plugin, sender, emptyArray())

            verify { messageService.send(sender, "invalid-syntax", "usage" to "/xinv cache <stats|clear> [player]") }
        }
    }

    // =========================================================================
    // Storage Service Interaction Tests
    // =========================================================================

    @Nested
    @DisplayName("StorageService Interactions")
    inner class StorageServiceInteractionTests {

        @Test
        @DisplayName("Should call getCacheStats for stats action")
        fun callGetCacheStatsForStatsAction() = runTest {
            val sender = createMockCommandSender()
            val stats = createCacheStats()

            every { storageService.getCacheStats() } returns stats

            cacheCommand.execute(plugin, sender, arrayOf("stats"))

            verify(exactly = 1) { storageService.getCacheStats() }
        }

        @Test
        @DisplayName("Should call clearCache for clear action without player")
        fun callClearCacheForClearActionWithoutPlayer() = runTest {
            val sender = createMockCommandSender()

            every { storageService.clearCache() } returns 10

            cacheCommand.execute(plugin, sender, arrayOf("clear"))

            verify(exactly = 1) { storageService.clearCache() }
        }

        @Test
        @DisplayName("Should call invalidateCache for clear action with player")
        fun callInvalidateCacheForClearActionWithPlayer() = runTest {
            val sender = createMockCommandSender()
            val targetUuid = UUID.randomUUID()
            val targetPlayer = createMockPlayer(uuid = targetUuid)

            every { Bukkit.getPlayer("Target") } returns targetPlayer

            cacheCommand.execute(plugin, sender, arrayOf("clear", "Target"))

            verify(exactly = 1) { storageService.invalidateCache(targetUuid) }
            verify(exactly = 0) { storageService.clearCache() }
        }

        @Test
        @DisplayName("Should not call storage methods for invalid syntax")
        fun notCallStorageMethodsForInvalidSyntax() = runTest {
            val sender = createMockCommandSender()

            cacheCommand.execute(plugin, sender, emptyArray())

            verify(exactly = 0) { storageService.getCacheStats() }
            verify(exactly = 0) { storageService.clearCache() }
            verify(exactly = 0) { storageService.invalidateCache(any()) }
        }

        @Test
        @DisplayName("Should not call invalidateCache when player not found")
        fun notCallInvalidateCacheWhenPlayerNotFound() = runTest {
            val sender = createMockCommandSender()
            val offlinePlayer = createMockOfflinePlayer(hasPlayedBefore = false)

            every { Bukkit.getPlayer("Unknown") } returns null
            every { Bukkit.getOfflinePlayer("Unknown") } returns offlinePlayer

            cacheCommand.execute(plugin, sender, arrayOf("clear", "Unknown"))

            verify(exactly = 0) { storageService.invalidateCache(any()) }
        }
    }
}

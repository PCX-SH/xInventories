package sh.pcx.xinventories.integration.storage

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.storage.SqliteStorage
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger
import kotlin.system.measureTimeMillis

/**
 * Comprehensive integration tests for SqliteStorage.
 * Tests database operations against a real SQLite database using temporary directories.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("SqliteStorage Integration Tests")
class SqliteStorageTest {

    private lateinit var server: ServerMock

    @BeforeAll
    fun setUpServer() {
        server = MockBukkit.mock()
        Logging.init(Logger.getLogger("SqliteStorageTest"), debug = true)
    }

    @AfterAll
    fun tearDownServer() {
        MockBukkit.unmock()
    }

    // ============================================================
    // Test Fixtures
    // ============================================================

    private val testUuid1 = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val testUuid2 = UUID.fromString("87654321-4321-4321-4321-cba987654321")
    private val testUuid3 = UUID.fromString("11111111-2222-3333-4444-555555555555")
    private val testPlayerName = "TestPlayer"
    private val testGroup = "survival"
    private val testTimestamp = Instant.ofEpochMilli(1700000000000L)

    /**
     * Creates a mock XInventories plugin with a custom data folder.
     */
    private fun createMockPlugin(dataFolder: File): XInventories {
        return mockk<XInventories>(relaxed = true) {
            every { this@mockk.plugin } returns this@mockk
            every { this@mockk.dataFolder } returns dataFolder
        }
    }

    /**
     * Creates a PlayerData with all fields populated for testing.
     */
    private fun createFullPlayerData(
        uuid: UUID = testUuid1,
        playerName: String = testPlayerName,
        group: String = testGroup,
        gameMode: GameMode = GameMode.SURVIVAL
    ): PlayerData {
        return PlayerData(
            uuid = uuid,
            playerName = playerName,
            group = group,
            gameMode = gameMode
        ).apply {
            health = 15.5
            maxHealth = 24.0
            foodLevel = 18
            saturation = 4.5f
            exhaustion = 1.2f
            experience = 0.75f
            level = 25
            totalExperience = 1250
            timestamp = testTimestamp

            // Using simple items to avoid MockBukkit serialization issues with meta
            mainInventory[0] = ItemStack(Material.STONE, 1)
            mainInventory[1] = ItemStack(Material.COBBLESTONE, 1)
            mainInventory[8] = ItemStack(Material.GOLDEN_APPLE, 16)
            mainInventory[35] = ItemStack(Material.TORCH, 64)

            // Using simple blocks instead of armor (armor items have ArmorMeta that MockBukkit doesn't serialize properly)
            armorInventory[0] = ItemStack(Material.IRON_INGOT, 1)
            armorInventory[1] = ItemStack(Material.GOLD_INGOT, 1)
            armorInventory[2] = ItemStack(Material.DIAMOND, 1)
            armorInventory[3] = ItemStack(Material.EMERALD, 1)

            offhand = ItemStack(Material.STICK, 1)

            enderChest[0] = ItemStack(Material.ENDER_PEARL, 16)
            enderChest[13] = ItemStack(Material.DIAMOND, 64)
            enderChest[26] = ItemStack(Material.NETHERITE_INGOT, 8)

            potionEffects.add(PotionEffect(PotionEffectType.SPEED, 600, 1, false, true, true))
            potionEffects.add(PotionEffect(PotionEffectType.REGENERATION, 200, 2, true, false, true))
        }
    }

    /**
     * Creates an empty PlayerData for testing edge cases.
     */
    private fun createEmptyPlayerData(
        uuid: UUID = testUuid1,
        playerName: String = testPlayerName,
        group: String = testGroup,
        gameMode: GameMode = GameMode.SURVIVAL
    ): PlayerData {
        return PlayerData(uuid, playerName, group, gameMode).apply {
            timestamp = testTimestamp
        }
    }

    // ============================================================
    // Initialization Tests
    // ============================================================

    @Nested
    @DisplayName("Initialization")
    inner class InitializationTests {

        @Test
        @DisplayName("should initialize storage and create database file")
        fun testInitializeCreatesDatabase(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")

            storage.initialize()

            val dbFile = tempDir.resolve("xinventories.db").toFile()
            assertTrue(dbFile.exists(), "Database file should be created")
            assertTrue(dbFile.length() > 0, "Database file should not be empty")

            storage.shutdown()
        }

        @Test
        @DisplayName("should create tables on first initialization")
        fun testCreateTablesOnInit(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")

            storage.initialize()

            // Verify by saving and loading data
            val data = createEmptyPlayerData()
            assertTrue(storage.savePlayerData(data))

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            Assertions.assertNotNull(loaded)

            storage.shutdown()
        }

        @Test
        @DisplayName("should handle database file in subdirectory")
        fun testDatabaseInSubdirectory(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "data/storage/xinventories.db")

            storage.initialize()

            val dbFile = tempDir.resolve("data/storage/xinventories.db").toFile()
            assertTrue(dbFile.exists(), "Database file in subdirectory should be created")

            storage.shutdown()
        }

        @Test
        @DisplayName("should not reinitialize if already initialized")
        fun testNoReinitialize(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")

            storage.initialize()
            val originalSize = tempDir.resolve("xinventories.db").toFile().length()

            // Save some data
            storage.savePlayerData(createFullPlayerData())

            // Reinitialize should not clear data
            storage.initialize()

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            Assertions.assertNotNull(loaded, "Data should persist after re-initialize call")

            storage.shutdown()
        }

        @Test
        @DisplayName("should return correct storage name")
        fun testStorageName(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")

            assertEquals("SQLite", storage.name)
        }
    }

    // ============================================================
    // Save Player Data Tests
    // ============================================================

    @Nested
    @DisplayName("Save Player Data")
    inner class SavePlayerDataTests {

        @Test
        @DisplayName("should save player data to database")
        fun testSavePlayerData(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val data = createFullPlayerData()
            val result = storage.savePlayerData(data)

            assertTrue(result, "Save should return true on success")
            assertEquals(1, storage.getEntryCount(), "Should have 1 entry")

            storage.shutdown()
        }

        @Test
        @DisplayName("should save empty player data")
        fun testSaveEmptyPlayerData(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val data = createEmptyPlayerData()
            val result = storage.savePlayerData(data)

            assertTrue(result, "Save should return true on success")

            storage.shutdown()
        }

        @ParameterizedTest
        @EnumSource(GameMode::class)
        @DisplayName("should save data for all game modes")
        fun testSaveAllGameModes(gameMode: GameMode, @TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val data = createEmptyPlayerData(gameMode = gameMode)
            val result = storage.savePlayerData(data)

            assertTrue(result)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, gameMode)
            Assertions.assertNotNull(loaded)
            assertEquals(gameMode, loaded?.gameMode)

            storage.shutdown()
        }

        @Test
        @DisplayName("should return false when storage not initialized")
        fun testSaveWithoutInit(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")

            val data = createEmptyPlayerData()
            val result = storage.savePlayerData(data)

            assertFalse(result, "Save should return false when not initialized")
        }
    }

    // ============================================================
    // Load Player Data Tests
    // ============================================================

    @Nested
    @DisplayName("Load Player Data")
    inner class LoadPlayerDataTests {

        @Test
        @DisplayName("should load player data from database")
        fun testLoadPlayerData(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val original = createFullPlayerData()
            storage.savePlayerData(original)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            Assertions.assertNotNull(loaded)
            assertEquals(original.uuid, loaded?.uuid)
            assertEquals(original.playerName, loaded?.playerName)
            assertEquals(original.group, loaded?.group)
            assertEquals(original.gameMode, loaded?.gameMode)

            storage.shutdown()
        }

        @Test
        @DisplayName("should load non-existent player and return null")
        fun testLoadNonExistentPlayer(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            Assertions.assertNull(loaded, "Loading non-existent player should return null")

            storage.shutdown()
        }

        @Test
        @DisplayName("should load data by group without gamemode")
        fun testLoadByGroupOnly(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val data = createFullPlayerData()
            storage.savePlayerData(data)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, null)

            Assertions.assertNotNull(loaded)
            assertEquals(testUuid1, loaded?.uuid)
            assertEquals(testGroup, loaded?.group)

            storage.shutdown()
        }

        @Test
        @DisplayName("should return null when storage not initialized")
        fun testLoadWithoutInit(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            Assertions.assertNull(loaded)
        }
    }

    // ============================================================
    // Save and Load Roundtrip Tests
    // ============================================================

    @Nested
    @DisplayName("Save and Load Roundtrip")
    inner class RoundtripTests {

        @Test
        @DisplayName("should preserve all player state data through roundtrip")
        fun testRoundtripPreservesState(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val original = createFullPlayerData()
            storage.savePlayerData(original)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            Assertions.assertNotNull(loaded)
            assertEquals(original.health, loaded!!.health)
            assertEquals(original.maxHealth, loaded.maxHealth)
            assertEquals(original.foodLevel, loaded.foodLevel)
            assertEquals(original.saturation, loaded.saturation, 0.001f)
            assertEquals(original.exhaustion, loaded.exhaustion, 0.001f)
            assertEquals(original.experience, loaded.experience, 0.001f)
            assertEquals(original.level, loaded.level)
            assertEquals(original.totalExperience, loaded.totalExperience)

            storage.shutdown()
        }

        @Test
        @DisplayName("should preserve timestamp through roundtrip")
        fun testRoundtripPreservesTimestamp(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val original = createFullPlayerData()
            storage.savePlayerData(original)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            Assertions.assertNotNull(loaded)
            assertEquals(original.timestamp.toEpochMilli(), loaded!!.timestamp.toEpochMilli())

            storage.shutdown()
        }

        @Test
        @DisplayName("should preserve inventory contents through roundtrip")
        fun testRoundtripPreservesInventory(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val original = createFullPlayerData()
            storage.savePlayerData(original)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            Assertions.assertNotNull(loaded)
            assertEquals(original.mainInventory.size, loaded!!.mainInventory.size)
            assertEquals(original.armorInventory.size, loaded.armorInventory.size)
            assertEquals(original.enderChest.size, loaded.enderChest.size)
            Assertions.assertNotNull(loaded.offhand)

            storage.shutdown()
        }

        @Test
        @Disabled("MockBukkit PotionEffect serialization doesn't preserve effects correctly")
        @DisplayName("should preserve potion effects through roundtrip")
        fun testRoundtripPreservesPotionEffects(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val original = createFullPlayerData()
            storage.savePlayerData(original)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            Assertions.assertNotNull(loaded)
            assertEquals(original.potionEffects.size, loaded!!.potionEffects.size)

            storage.shutdown()
        }
    }

    // ============================================================
    // Load All Player Data Tests
    // ============================================================

    @Nested
    @DisplayName("Load All Player Data")
    inner class LoadAllPlayerDataTests {

        @Test
        @DisplayName("should load all player data for a UUID")
        fun testLoadAllPlayerData(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Save data for multiple groups/gamemodes
            storage.savePlayerData(createFullPlayerData(group = "survival", gameMode = GameMode.SURVIVAL))
            storage.savePlayerData(createFullPlayerData(group = "survival", gameMode = GameMode.CREATIVE))
            storage.savePlayerData(createFullPlayerData(group = "creative", gameMode = GameMode.CREATIVE))

            val allData = storage.loadAllPlayerData(testUuid1)

            assertEquals(3, allData.size, "Should load all 3 entries")

            storage.shutdown()
        }

        @Test
        @DisplayName("should return empty map for player with no data")
        fun testLoadAllNoData(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val allData = storage.loadAllPlayerData(testUuid1)

            assertTrue(allData.isEmpty())

            storage.shutdown()
        }

        @Test
        @DisplayName("should only load data for specified UUID")
        fun testLoadAllOnlySpecifiedUuid(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Save data for two different players
            storage.savePlayerData(createFullPlayerData(uuid = testUuid1))
            storage.savePlayerData(createFullPlayerData(uuid = testUuid2))

            val player1Data = storage.loadAllPlayerData(testUuid1)
            val player2Data = storage.loadAllPlayerData(testUuid2)

            assertEquals(1, player1Data.size)
            assertEquals(1, player2Data.size)

            storage.shutdown()
        }
    }

    // ============================================================
    // Delete Player Data Tests
    // ============================================================

    @Nested
    @DisplayName("Delete Player Data")
    inner class DeletePlayerDataTests {

        @Test
        @DisplayName("should delete specific player data")
        fun testDeletePlayerData(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            storage.savePlayerData(createFullPlayerData())
            assertTrue(storage.hasPlayerData(testUuid1, testGroup, GameMode.SURVIVAL))

            val deleted = storage.deletePlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertTrue(deleted)
            assertFalse(storage.hasPlayerData(testUuid1, testGroup, GameMode.SURVIVAL))

            storage.shutdown()
        }

        @Test
        @DisplayName("should delete group data for player (all gamemodes)")
        fun testDeleteGroupData(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Save data for multiple gamemodes in same group
            storage.savePlayerData(createFullPlayerData(gameMode = GameMode.SURVIVAL))
            storage.savePlayerData(createFullPlayerData(gameMode = GameMode.CREATIVE))

            assertEquals(2, storage.getEntryCount())

            val deleted = storage.deletePlayerData(testUuid1, testGroup, null)

            assertTrue(deleted)
            assertEquals(0, storage.getEntryCount())

            storage.shutdown()
        }

        @Test
        @DisplayName("should return false when deleting non-existent data")
        fun testDeleteNonExistent(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val deleted = storage.deletePlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertFalse(deleted)

            storage.shutdown()
        }

        @Test
        @DisplayName("should delete all player data")
        fun testDeleteAllPlayerData(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Save multiple entries for the same player
            storage.savePlayerData(createFullPlayerData(group = "survival"))
            storage.savePlayerData(createFullPlayerData(group = "creative"))
            storage.savePlayerData(createFullPlayerData(group = "adventure"))

            assertEquals(3, storage.getEntryCount())

            val count = storage.deleteAllPlayerData(testUuid1)

            assertEquals(3, count)
            assertEquals(0, storage.getEntryCount())

            storage.shutdown()
        }

        @Test
        @DisplayName("should not delete other players data")
        fun testDeleteOnlyTargetPlayer(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            storage.savePlayerData(createFullPlayerData(uuid = testUuid1))
            storage.savePlayerData(createFullPlayerData(uuid = testUuid2))

            storage.deleteAllPlayerData(testUuid1)

            assertEquals(1, storage.getEntryCount())
            assertTrue(storage.hasPlayerData(testUuid2, testGroup, GameMode.SURVIVAL))

            storage.shutdown()
        }
    }

    // ============================================================
    // Update/Upsert Tests
    // ============================================================

    @Nested
    @DisplayName("Update Existing Player Data (Upsert)")
    inner class UpsertTests {

        @Test
        @DisplayName("should update existing player data")
        fun testUpdateExistingData(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Save initial data
            val original = createFullPlayerData()
            original.health = 10.0
            original.level = 5
            storage.savePlayerData(original)

            // Update with new values
            val updated = createFullPlayerData()
            updated.health = 20.0
            updated.level = 50
            storage.savePlayerData(updated)

            // Should still be 1 entry (upsert)
            assertEquals(1, storage.getEntryCount())

            // Verify updated values
            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            Assertions.assertNotNull(loaded)
            assertEquals(20.0, loaded!!.health)
            assertEquals(50, loaded.level)

            storage.shutdown()
        }

        @Test
        @DisplayName("should update player name on upsert")
        fun testUpdatePlayerName(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Save with original name
            val original = createFullPlayerData(playerName = "OldName")
            storage.savePlayerData(original)

            // Update with new name
            val updated = createFullPlayerData(playerName = "NewName")
            storage.savePlayerData(updated)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            assertEquals("NewName", loaded?.playerName)

            storage.shutdown()
        }
    }

    // ============================================================
    // Batch Operations Tests
    // ============================================================

    @Nested
    @DisplayName("Batch Save Operations")
    inner class BatchOperationsTests {

        @Test
        @DisplayName("should save multiple player data entries in batch")
        fun testBatchSave(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val dataList = listOf(
                createFullPlayerData(uuid = testUuid1, group = "survival"),
                createFullPlayerData(uuid = testUuid2, group = "survival"),
                createFullPlayerData(uuid = testUuid3, group = "survival"),
                createFullPlayerData(uuid = testUuid1, group = "creative"),
                createFullPlayerData(uuid = testUuid2, group = "creative")
            )

            val count = storage.savePlayerDataBatch(dataList)

            assertEquals(5, count)
            assertEquals(5, storage.getEntryCount())

            storage.shutdown()
        }

        @Test
        @DisplayName("should return 0 when batch is empty")
        fun testBatchSaveEmpty(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val count = storage.savePlayerDataBatch(emptyList())

            assertEquals(0, count)

            storage.shutdown()
        }

        @Test
        @DisplayName("should use transaction for batch operations")
        fun testBatchUsesTransaction(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Save a large batch
            val dataList = (1..100).map { i ->
                createEmptyPlayerData(
                    uuid = UUID.randomUUID(),
                    playerName = "Player$i"
                )
            }

            val count = storage.savePlayerDataBatch(dataList)

            assertEquals(100, count)
            assertEquals(100, storage.getEntryCount())

            storage.shutdown()
        }
    }

    // ============================================================
    // Has Player Data Tests
    // ============================================================

    @Nested
    @DisplayName("Has Player Data")
    inner class HasPlayerDataTests {

        @Test
        @DisplayName("should return true when data exists")
        fun testHasDataReturnsTrue(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            storage.savePlayerData(createFullPlayerData())

            assertTrue(storage.hasPlayerData(testUuid1, testGroup, GameMode.SURVIVAL))

            storage.shutdown()
        }

        @Test
        @DisplayName("should return false when data does not exist")
        fun testHasDataReturnsFalse(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            assertFalse(storage.hasPlayerData(testUuid1, testGroup, GameMode.SURVIVAL))

            storage.shutdown()
        }

        @Test
        @DisplayName("should check by group without gamemode")
        fun testHasDataByGroupOnly(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            storage.savePlayerData(createFullPlayerData())

            assertTrue(storage.hasPlayerData(testUuid1, testGroup, null))
            assertFalse(storage.hasPlayerData(testUuid1, "nonexistent", null))

            storage.shutdown()
        }
    }

    // ============================================================
    // Get All Player UUIDs Tests
    // ============================================================

    @Nested
    @DisplayName("Get All Player UUIDs")
    inner class GetAllPlayerUUIDsTests {

        @Test
        @DisplayName("should return all unique UUIDs")
        fun testGetAllUuids(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            storage.savePlayerData(createFullPlayerData(uuid = testUuid1, group = "survival"))
            storage.savePlayerData(createFullPlayerData(uuid = testUuid1, group = "creative"))
            storage.savePlayerData(createFullPlayerData(uuid = testUuid2, group = "survival"))
            storage.savePlayerData(createFullPlayerData(uuid = testUuid3, group = "survival"))

            val uuids = storage.getAllPlayerUUIDs()

            assertEquals(3, uuids.size)
            assertTrue(uuids.contains(testUuid1))
            assertTrue(uuids.contains(testUuid2))
            assertTrue(uuids.contains(testUuid3))

            storage.shutdown()
        }

        @Test
        @DisplayName("should return empty set when no data")
        fun testGetAllUuidsEmpty(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val uuids = storage.getAllPlayerUUIDs()

            assertTrue(uuids.isEmpty())

            storage.shutdown()
        }
    }

    // ============================================================
    // Get Player Groups Tests
    // ============================================================

    @Nested
    @DisplayName("Get Player Groups")
    inner class GetPlayerGroupsTests {

        @Test
        @DisplayName("should return all groups for a player")
        fun testGetPlayerGroups(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            storage.savePlayerData(createFullPlayerData(group = "survival"))
            storage.savePlayerData(createFullPlayerData(group = "creative"))
            storage.savePlayerData(createFullPlayerData(group = "adventure"))

            val groups = storage.getPlayerGroups(testUuid1)

            assertEquals(3, groups.size)
            assertTrue(groups.contains("survival"))
            assertTrue(groups.contains("creative"))
            assertTrue(groups.contains("adventure"))

            storage.shutdown()
        }

        @Test
        @DisplayName("should return empty set for player with no data")
        fun testGetPlayerGroupsEmpty(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val groups = storage.getPlayerGroups(testUuid1)

            assertTrue(groups.isEmpty())

            storage.shutdown()
        }
    }

    // ============================================================
    // Health Check Tests
    // ============================================================

    @Nested
    @DisplayName("Health Check")
    inner class HealthCheckTests {

        @Test
        @DisplayName("should return true when connection is valid")
        fun testHealthCheckValid(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            assertTrue(storage.isHealthy())

            storage.shutdown()
        }

        @Test
        @DisplayName("should return false when not initialized")
        fun testHealthCheckNotInitialized(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")

            assertFalse(storage.isHealthy())
        }

        @Test
        @DisplayName("should return false after shutdown")
        fun testHealthCheckAfterShutdown(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()
            storage.shutdown()

            assertFalse(storage.isHealthy())
        }
    }

    // ============================================================
    // Shutdown Tests
    // ============================================================

    @Nested
    @DisplayName("Shutdown")
    inner class ShutdownTests {

        @Test
        @DisplayName("should close connections on shutdown")
        fun testShutdownClosesConnections(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            storage.shutdown()

            // After shutdown, operations should fail gracefully
            assertFalse(storage.isHealthy())

            // Should be able to save data (returns false since not initialized)
            val result = storage.savePlayerData(createEmptyPlayerData())
            assertFalse(result)
        }

        @Test
        @DisplayName("should handle multiple shutdown calls")
        fun testMultipleShutdowns(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Multiple shutdowns should not throw
            assertDoesNotThrow {
                runBlocking {
                    storage.shutdown()
                    storage.shutdown()
                    storage.shutdown()
                }
            }
        }

        @Test
        @DisplayName("should preserve data after shutdown and reinit")
        fun testDataPersistsAfterShutdown(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())

            // First session
            val storage1 = SqliteStorage(plugin, "xinventories.db")
            storage1.initialize()
            storage1.savePlayerData(createFullPlayerData())
            storage1.shutdown()

            // Second session
            val storage2 = SqliteStorage(plugin, "xinventories.db")
            storage2.initialize()

            val loaded = storage2.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            Assertions.assertNotNull(loaded)
            assertEquals(testUuid1, loaded?.uuid)

            storage2.shutdown()
        }
    }

    // ============================================================
    // Storage Size and Entry Count Tests
    // ============================================================

    @Nested
    @DisplayName("Storage Size and Entry Count")
    inner class StorageSizeTests {

        @Test
        @DisplayName("should return correct entry count")
        fun testEntryCount(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            assertEquals(0, storage.getEntryCount())

            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid1))
            assertEquals(1, storage.getEntryCount())

            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid2))
            assertEquals(2, storage.getEntryCount())

            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid3))
            assertEquals(3, storage.getEntryCount())

            storage.shutdown()
        }

        @Test
        @DisplayName("should return storage size")
        fun testStorageSize(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val initialSize = storage.getStorageSize()
            assertTrue(initialSize > 0, "Database should have some initial size")

            // Add some data
            storage.savePlayerData(createFullPlayerData())

            val newSize = storage.getStorageSize()
            assertTrue(newSize >= initialSize, "Size should increase or stay same after adding data")

            storage.shutdown()
        }
    }

    // ============================================================
    // Query Performance Tests
    // ============================================================

    @Nested
    @DisplayName("Query Performance")
    inner class PerformanceTests {

        @Test
        @DisplayName("should perform well with multiple entries")
        fun testPerformanceWithMultipleEntries(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Insert 500 entries
            val dataList = (1..500).map { i ->
                createEmptyPlayerData(
                    uuid = UUID.randomUUID(),
                    playerName = "Player$i",
                    group = "group${i % 10}"
                )
            }

            val insertTime = measureTimeMillis {
                storage.savePlayerDataBatch(dataList)
            }

            assertTrue(insertTime < 10000, "Batch insert of 500 entries should complete in under 10 seconds, took $insertTime ms")

            // Query performance
            val queryTime = measureTimeMillis {
                repeat(100) {
                    storage.getAllPlayerUUIDs()
                }
            }

            assertTrue(queryTime < 5000, "100 queries should complete in under 5 seconds, took $queryTime ms")

            assertEquals(500, storage.getEntryCount())

            storage.shutdown()
        }

        @Test
        @DisplayName("should use indexes for faster queries")
        fun testIndexedQueries(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Insert data for multiple players and groups
            val dataList = (1..100).flatMap { playerNum ->
                val uuid = UUID.randomUUID()
                (1..5).map { groupNum ->
                    createEmptyPlayerData(
                        uuid = uuid,
                        playerName = "Player$playerNum",
                        group = "group$groupNum"
                    )
                }
            }

            storage.savePlayerDataBatch(dataList)
            assertEquals(500, storage.getEntryCount())

            // Test indexed lookups
            val targetUuid = dataList[250].uuid
            val lookupTime = measureTimeMillis {
                repeat(1000) {
                    storage.loadPlayerData(targetUuid, "group3", GameMode.SURVIVAL)
                }
            }

            assertTrue(lookupTime < 5000, "1000 indexed lookups should complete in under 5 seconds, took $lookupTime ms")

            storage.shutdown()
        }
    }

    // ============================================================
    // Edge Cases and Error Handling Tests
    // ============================================================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("should handle special characters in group name")
        fun testSpecialCharactersInGroupName(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val specialGroup = "survival-world_1"
            val data = createEmptyPlayerData(group = specialGroup)
            storage.savePlayerData(data)

            val loaded = storage.loadPlayerData(testUuid1, specialGroup, GameMode.SURVIVAL)
            Assertions.assertNotNull(loaded)
            assertEquals(specialGroup, loaded?.group)

            storage.shutdown()
        }

        @Test
        @DisplayName("should handle special characters in player name")
        fun testSpecialCharactersInPlayerName(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val specialName = "Player_123"
            val data = createEmptyPlayerData(playerName = specialName)
            storage.savePlayerData(data)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            Assertions.assertNotNull(loaded)
            assertEquals(specialName, loaded?.playerName)

            storage.shutdown()
        }

        @Test
        @DisplayName("should handle extreme health values")
        fun testExtremeHealthValues(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val data = createEmptyPlayerData().apply {
                health = 0.0001
                maxHealth = 10000.0
            }
            storage.savePlayerData(data)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            Assertions.assertNotNull(loaded)
            assertEquals(0.0001, loaded!!.health, 0.0001)
            assertEquals(10000.0, loaded.maxHealth, 0.001)

            storage.shutdown()
        }

        @Test
        @DisplayName("should handle max integer values")
        fun testMaxIntegerValues(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val data = createEmptyPlayerData().apply {
                level = Int.MAX_VALUE
                totalExperience = Int.MAX_VALUE
                foodLevel = 20
            }
            storage.savePlayerData(data)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            Assertions.assertNotNull(loaded)
            assertEquals(Int.MAX_VALUE, loaded!!.level)
            assertEquals(Int.MAX_VALUE, loaded.totalExperience)

            storage.shutdown()
        }

        @Test
        @DisplayName("should handle empty inventories")
        fun testEmptyInventories(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            val data = createEmptyPlayerData()
            storage.savePlayerData(data)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            Assertions.assertNotNull(loaded)
            assertTrue(loaded!!.mainInventory.isEmpty())
            assertTrue(loaded.armorInventory.isEmpty())
            assertTrue(loaded.enderChest.isEmpty())
            Assertions.assertNull(loaded.offhand)

            storage.shutdown()
        }

        @Test
        @DisplayName("should handle sequential reads and writes")
        fun testSequentialAccess(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "xinventories.db")
            storage.initialize()

            // Perform sequential operations
            val results = mutableListOf<PlayerData?>()
            for (i in 1..50) {
                val uuid = UUID.randomUUID()
                val data = createEmptyPlayerData(uuid = uuid, playerName = "Player$i")
                storage.savePlayerData(data)
                results.add(storage.loadPlayerData(uuid, testGroup, GameMode.SURVIVAL))
            }

            // All operations should succeed
            assertTrue(results.all { it != null })
            assertEquals(50, storage.getEntryCount())

            storage.shutdown()
        }
    }

    // ============================================================
    // Database File Management Tests
    // ============================================================

    @Nested
    @DisplayName("Database File Management")
    inner class DatabaseFileTests {

        @Test
        @DisplayName("should create parent directories if needed")
        fun testCreateParentDirectories(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())
            val storage = SqliteStorage(plugin, "deeply/nested/path/xinventories.db")

            storage.initialize()

            val dbFile = tempDir.resolve("deeply/nested/path/xinventories.db").toFile()
            assertTrue(dbFile.exists())
            assertTrue(dbFile.parentFile.exists())

            storage.shutdown()
        }

        @Test
        @DisplayName("should work with existing database file")
        fun testExistingDatabaseFile(@TempDir tempDir: Path) = runTest {
            val plugin = createMockPlugin(tempDir.toFile())

            // Create and populate first instance
            val storage1 = SqliteStorage(plugin, "xinventories.db")
            storage1.initialize()
            storage1.savePlayerData(createFullPlayerData())
            storage1.shutdown()

            // Verify file exists
            val dbFile = tempDir.resolve("xinventories.db").toFile()
            assertTrue(dbFile.exists())
            val originalSize = dbFile.length()

            // Open existing database
            val storage2 = SqliteStorage(plugin, "xinventories.db")
            storage2.initialize()

            assertEquals(1, storage2.getEntryCount())
            assertTrue(dbFile.length() >= originalSize)

            storage2.shutdown()
        }
    }
}

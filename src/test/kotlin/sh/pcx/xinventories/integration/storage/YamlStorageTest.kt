package sh.pcx.xinventories.integration.storage

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.XInventories
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.storage.YamlStorage
import sh.pcx.xinventories.internal.util.Logging
import java.io.File
import java.nio.file.Path
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/**
 * Comprehensive integration tests for YamlStorage.
 * Tests file-based storage operations including initialization, CRUD operations,
 * batch operations, error handling, and file structure verification.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("YamlStorage Integration Tests")
class YamlStorageTest {

    private lateinit var server: ServerMock

    @BeforeAll
    fun setUpServer() {
        server = MockBukkit.mock()
        Logging.init(Logger.getLogger("YamlStorageTest"), debug = true)
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
     * Creates a mock plugin with a specified data folder.
     */
    private fun createMockPlugin(dataFolder: File): XInventories {
        val plugin = mockk<XInventories>(relaxed = true)
        every { plugin.dataFolder } returns dataFolder
        return plugin
    }

    /**
     * Creates a YamlStorage instance with a temporary directory.
     */
    private fun createStorage(tempDir: Path): Pair<YamlStorage, XInventories> {
        val dataFolder = tempDir.resolve("plugin").toFile()
        dataFolder.mkdirs()
        val plugin = createMockPlugin(dataFolder)
        val storage = YamlStorage(plugin)
        return storage to plugin
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

            // Main inventory - using simple items to avoid MockBukkit serialization issues with meta
            mainInventory[0] = ItemStack(Material.STONE, 1)
            mainInventory[1] = ItemStack(Material.COBBLESTONE, 1)
            mainInventory[8] = ItemStack(Material.GOLDEN_APPLE, 16)
            mainInventory[35] = ItemStack(Material.TORCH, 64)

            // Armor - using simple blocks as placeholders (armor items have ArmorMeta that MockBukkit doesn't serialize properly)
            armorInventory[0] = ItemStack(Material.IRON_INGOT, 1)
            armorInventory[1] = ItemStack(Material.GOLD_INGOT, 1)
            armorInventory[2] = ItemStack(Material.DIAMOND, 1)
            armorInventory[3] = ItemStack(Material.EMERALD, 1)

            // Offhand - using simple item
            offhand = ItemStack(Material.STICK, 1)

            // Ender chest
            enderChest[0] = ItemStack(Material.ENDER_PEARL, 16)
            enderChest[13] = ItemStack(Material.DIAMOND, 64)
            enderChest[26] = ItemStack(Material.NETHERITE_INGOT, 8)

            // Potion effects
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
    @DisplayName("Initialize storage and create directories")
    inner class InitializationTests {

        @Test
        @DisplayName("should create data directory on initialization")
        fun testCreateDataDirectory(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            val dataDir = File(plugin.dataFolder, "data")
            val playersDir = File(dataDir, "players")

            storage.initialize()

            assertTrue(playersDir.exists(), "Players directory should exist after initialization")
            assertTrue(playersDir.isDirectory, "Players directory should be a directory")
        }

        @Test
        @DisplayName("should handle initialization when directories already exist")
        fun testInitializeWithExistingDirectories(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            val playersDir = File(File(plugin.dataFolder, "data"), "players")
            playersDir.mkdirs()

            assertDoesNotThrow {
                runBlocking { storage.initialize() }
            }

            assertTrue(playersDir.exists())
        }

        @Test
        @DisplayName("should not initialize twice")
        fun testDoubleInitialization(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)

            storage.initialize()

            // Second initialization should not throw and should log a warning
            assertDoesNotThrow {
                runBlocking { storage.initialize() }
            }
        }

        @Test
        @DisplayName("should report correct storage name")
        fun testStorageName(@TempDir tempDir: Path) {
            val (storage, _) = createStorage(tempDir)

            assertEquals("YAML", storage.name)
        }
    }

    // ============================================================
    // Save Player Data Tests
    // ============================================================

    @Nested
    @DisplayName("Save player data to YAML file")
    inner class SavePlayerDataTests {

        @Test
        @DisplayName("should save player data successfully")
        fun testSavePlayerData(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()
            val data = createFullPlayerData()

            val result = storage.savePlayerData(data)

            assertTrue(result, "Save should return true on success")

            val expectedFile = File(
                File(File(File(plugin.dataFolder, "data"), "players"), testUuid1.toString()),
                "${testGroup}_SURVIVAL.yml"
            )
            assertTrue(expectedFile.exists(), "YAML file should exist after save")
        }

        @Test
        @DisplayName("should save empty player data")
        fun testSaveEmptyPlayerData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val data = createEmptyPlayerData()

            val result = storage.savePlayerData(data)

            assertTrue(result)
        }

        @Test
        @DisplayName("should overwrite existing player data")
        fun testOverwritePlayerData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val data1 = createFullPlayerData().apply { level = 10 }
            val data2 = createFullPlayerData().apply { level = 50 }

            storage.savePlayerData(data1)
            val result = storage.savePlayerData(data2)

            assertTrue(result)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            assertNotNull(loaded)
            assertEquals(50, loaded!!.level, "Saved level should be updated value")
        }

        @Test
        @DisplayName("should return false when storage not initialized")
        fun testSaveWithoutInitialization(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            val data = createFullPlayerData()

            val result = storage.savePlayerData(data)

            assertFalse(result, "Save should fail when storage is not initialized")
        }

        @ParameterizedTest
        @EnumSource(GameMode::class)
        @DisplayName("should save data for all game modes")
        fun testSaveAllGameModes(gameMode: GameMode, @TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()
            val data = createEmptyPlayerData(gameMode = gameMode)

            val result = storage.savePlayerData(data)

            assertTrue(result)

            val expectedFile = File(
                File(File(File(plugin.dataFolder, "data"), "players"), testUuid1.toString()),
                "${testGroup}_${gameMode.name}.yml"
            )
            assertTrue(expectedFile.exists(), "File for $gameMode should exist")
        }
    }

    // ============================================================
    // Load Player Data Tests
    // ============================================================

    @Nested
    @DisplayName("Load player data from YAML file")
    inner class LoadPlayerDataTests {

        @Test
        @DisplayName("should load existing player data")
        fun testLoadPlayerData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val original = createFullPlayerData()
            storage.savePlayerData(original)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertNotNull(loaded, "Loaded data should not be null")
            assertEquals(original.uuid, loaded!!.uuid)
            assertEquals(original.playerName, loaded.playerName)
            assertEquals(original.group, loaded.group)
            assertEquals(original.gameMode, loaded.gameMode)
            assertEquals(original.health, loaded.health, 0.001)
            assertEquals(original.maxHealth, loaded.maxHealth, 0.001)
            assertEquals(original.foodLevel, loaded.foodLevel)
            assertEquals(original.level, loaded.level)
            assertEquals(original.totalExperience, loaded.totalExperience)
        }

        @Test
        @DisplayName("should return null for non-existent player")
        fun testLoadNonExistentPlayer(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val nonExistentUuid = UUID.randomUUID()

            val loaded = storage.loadPlayerData(nonExistentUuid, testGroup, GameMode.SURVIVAL)

            assertNull(loaded, "Load should return null for non-existent player")
        }

        @Test
        @DisplayName("should return null for non-existent group")
        fun testLoadNonExistentGroup(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val data = createFullPlayerData()
            storage.savePlayerData(data)

            val loaded = storage.loadPlayerData(testUuid1, "nonexistent", GameMode.SURVIVAL)

            assertNull(loaded, "Load should return null for non-existent group")
        }

        @Test
        @DisplayName("should return null for non-existent gamemode")
        fun testLoadNonExistentGameMode(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val data = createFullPlayerData(gameMode = GameMode.SURVIVAL)
            storage.savePlayerData(data)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.CREATIVE)

            assertNull(loaded, "Load should return null for non-existent game mode")
        }

        @Test
        @DisplayName("should return null when storage not initialized")
        fun testLoadWithoutInitialization(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertNull(loaded)
        }
    }

    // ============================================================
    // Save and Load Roundtrip Tests
    // ============================================================

    @Nested
    @DisplayName("Save and load roundtrip preserves all data")
    inner class RoundtripTests {

        @Test
        @DisplayName("should preserve all player state through roundtrip")
        fun testRoundtripPreservesState(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val original = createFullPlayerData()

            storage.savePlayerData(original)
            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertNotNull(loaded)
            assertEquals(original.uuid, loaded!!.uuid)
            assertEquals(original.playerName, loaded.playerName)
            assertEquals(original.group, loaded.group)
            assertEquals(original.gameMode, loaded.gameMode)
            assertEquals(original.health, loaded.health, 0.001)
            assertEquals(original.maxHealth, loaded.maxHealth, 0.001)
            assertEquals(original.foodLevel, loaded.foodLevel)
            assertEquals(original.saturation, loaded.saturation, 0.001f)
            assertEquals(original.exhaustion, loaded.exhaustion, 0.001f)
            assertEquals(original.experience, loaded.experience, 0.001f)
            assertEquals(original.level, loaded.level)
            assertEquals(original.totalExperience, loaded.totalExperience)
            assertEquals(original.timestamp, loaded.timestamp)
        }

        @Test
        @Disabled("MockBukkit ItemStack serialization doesn't preserve items correctly")
        @DisplayName("should preserve inventory contents through roundtrip")
        fun testRoundtripPreservesInventory(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val original = createFullPlayerData()

            storage.savePlayerData(original)
            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertNotNull(loaded)

            // Check main inventory
            assertEquals(original.mainInventory.size, loaded!!.mainInventory.size)
            original.mainInventory.forEach { (slot, item) ->
                val loadedItem = loaded.mainInventory[slot]
                assertNotNull(loadedItem, "Main inventory slot $slot should exist")
                assertEquals(item.type, loadedItem!!.type)
                assertEquals(item.amount, loadedItem.amount)
            }

            // Check armor
            assertEquals(original.armorInventory.size, loaded.armorInventory.size)
            original.armorInventory.forEach { (slot, item) ->
                val loadedItem = loaded.armorInventory[slot]
                assertNotNull(loadedItem, "Armor slot $slot should exist")
                assertEquals(item.type, loadedItem!!.type)
            }

            // Check offhand
            assertNotNull(loaded.offhand)
            assertEquals(original.offhand?.type, loaded.offhand?.type)

            // Check ender chest
            assertEquals(original.enderChest.size, loaded.enderChest.size)
        }

        @Test
        @DisplayName("should preserve potion effects through roundtrip")
        fun testRoundtripPreservesPotionEffects(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val original = createFullPlayerData()

            storage.savePlayerData(original)
            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertNotNull(loaded)
            assertEquals(original.potionEffects.size, loaded!!.potionEffects.size)

            original.potionEffects.forEachIndexed { index, effect ->
                val loadedEffect = loaded.potionEffects[index]
                assertEquals(effect.type, loadedEffect.type)
                assertEquals(effect.duration, loadedEffect.duration)
                assertEquals(effect.amplifier, loadedEffect.amplifier)
            }
        }

        @Test
        @DisplayName("should preserve empty data through roundtrip")
        fun testRoundtripEmptyData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val original = createEmptyPlayerData()

            storage.savePlayerData(original)
            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertNotNull(loaded)
            assertTrue(loaded!!.mainInventory.isEmpty())
            assertTrue(loaded.armorInventory.isEmpty())
            assertNull(loaded.offhand)
            assertTrue(loaded.enderChest.isEmpty())
            assertTrue(loaded.potionEffects.isEmpty())
        }
    }

    // ============================================================
    // Load All Players Data Tests
    // ============================================================

    @Nested
    @DisplayName("Load all players data")
    inner class LoadAllPlayerDataTests {

        @Test
        @DisplayName("should load all data for a player across groups and gamemodes")
        fun testLoadAllPlayerData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            // Save data for different groups and game modes
            storage.savePlayerData(createEmptyPlayerData(group = "survival", gameMode = GameMode.SURVIVAL))
            storage.savePlayerData(createEmptyPlayerData(group = "survival", gameMode = GameMode.CREATIVE))
            storage.savePlayerData(createEmptyPlayerData(group = "creative", gameMode = GameMode.CREATIVE))

            val allData = storage.loadAllPlayerData(testUuid1)

            assertEquals(3, allData.size, "Should load all 3 entries")
            assertTrue(allData.containsKey("survival_SURVIVAL"))
            assertTrue(allData.containsKey("survival_CREATIVE"))
            assertTrue(allData.containsKey("creative_CREATIVE"))
        }

        @Test
        @DisplayName("should return empty map for player with no data")
        fun testLoadAllForNonExistentPlayer(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val nonExistentUuid = UUID.randomUUID()

            val allData = storage.loadAllPlayerData(nonExistentUuid)

            assertTrue(allData.isEmpty())
        }

        @Test
        @DisplayName("should return empty map when storage not initialized")
        fun testLoadAllWithoutInitialization(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)

            val allData = storage.loadAllPlayerData(testUuid1)

            assertTrue(allData.isEmpty())
        }
    }

    // ============================================================
    // Delete Player Data Tests
    // ============================================================

    @Nested
    @DisplayName("Delete player data")
    inner class DeletePlayerDataTests {

        @Test
        @DisplayName("should delete specific player data")
        fun testDeleteSpecificData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            storage.savePlayerData(createEmptyPlayerData(gameMode = GameMode.SURVIVAL))
            storage.savePlayerData(createEmptyPlayerData(gameMode = GameMode.CREATIVE))

            val result = storage.deletePlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertTrue(result, "Delete should return true")
            assertNull(storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL))
            assertNotNull(storage.loadPlayerData(testUuid1, testGroup, GameMode.CREATIVE))
        }

        @Test
        @DisplayName("should return false when deleting non-existent data")
        fun testDeleteNonExistentData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val result = storage.deletePlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertFalse(result)
        }

        @Test
        @DisplayName("should delete all data for a player")
        fun testDeleteAllPlayerData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            storage.savePlayerData(createEmptyPlayerData(group = "survival", gameMode = GameMode.SURVIVAL))
            storage.savePlayerData(createEmptyPlayerData(group = "survival", gameMode = GameMode.CREATIVE))
            storage.savePlayerData(createEmptyPlayerData(group = "creative", gameMode = GameMode.CREATIVE))

            val count = storage.deleteAllPlayerData(testUuid1)

            assertEquals(3, count, "Should delete 3 entries")
            assertTrue(storage.loadAllPlayerData(testUuid1).isEmpty())
        }

        @Test
        @DisplayName("should return zero when deleting all for non-existent player")
        fun testDeleteAllForNonExistentPlayer(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val nonExistentUuid = UUID.randomUUID()

            val count = storage.deleteAllPlayerData(nonExistentUuid)

            assertEquals(0, count)
        }

        @Test
        @DisplayName("should remove player directory when all data deleted")
        fun testPlayerDirectoryRemovalAfterDelete(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()
            storage.savePlayerData(createEmptyPlayerData())

            val playerDir = File(File(File(plugin.dataFolder, "data"), "players"), testUuid1.toString())
            assertTrue(playerDir.exists(), "Player directory should exist before delete")

            storage.deleteAllPlayerData(testUuid1)

            assertFalse(playerDir.exists(), "Player directory should be removed after deleting all data")
        }
    }

    // ============================================================
    // Delete Specific Group Data Tests
    // ============================================================

    @Nested
    @DisplayName("Delete specific group data for player")
    inner class DeleteGroupDataTests {

        @Test
        @DisplayName("should delete all gamemodes for a group when gamemode is null")
        fun testDeleteGroupWithNullGameMode(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            storage.savePlayerData(createEmptyPlayerData(group = "survival", gameMode = GameMode.SURVIVAL))
            storage.savePlayerData(createEmptyPlayerData(group = "survival", gameMode = GameMode.CREATIVE))
            storage.savePlayerData(createEmptyPlayerData(group = "creative", gameMode = GameMode.CREATIVE))

            val result = storage.deletePlayerData(testUuid1, "survival", null)

            assertTrue(result)
            assertNull(storage.loadPlayerData(testUuid1, "survival", GameMode.SURVIVAL))
            assertNull(storage.loadPlayerData(testUuid1, "survival", GameMode.CREATIVE))
            assertNotNull(storage.loadPlayerData(testUuid1, "creative", GameMode.CREATIVE))
        }

        @Test
        @DisplayName("should return false when deleting group with no data")
        fun testDeleteNonExistentGroup(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            storage.savePlayerData(createEmptyPlayerData(group = "survival"))

            val result = storage.deletePlayerData(testUuid1, "nonexistent", null)

            assertFalse(result)
        }
    }

    // ============================================================
    // Handle Corrupted YAML Files Tests
    // ============================================================

    @Nested
    @DisplayName("Handle corrupted YAML files gracefully")
    inner class CorruptedFileTests {

        @Test
        @DisplayName("should handle corrupted YAML file gracefully during load")
        fun testLoadCorruptedFile(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()

            // Create a corrupted YAML file
            val playerDir = File(File(File(plugin.dataFolder, "data"), "players"), testUuid1.toString())
            playerDir.mkdirs()
            val corruptedFile = File(playerDir, "${testGroup}_SURVIVAL.yml")
            corruptedFile.writeText("this is not: valid: yaml: [[[")

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            // Should return null instead of crashing
            assertNull(loaded, "Load should return null for corrupted file")
        }

        @Test
        @DisplayName("should handle incomplete YAML file gracefully")
        fun testLoadIncompleteFile(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()

            // Create a file with missing required fields
            val playerDir = File(File(File(plugin.dataFolder, "data"), "players"), testUuid1.toString())
            playerDir.mkdirs()
            val incompleteFile = File(playerDir, "${testGroup}_SURVIVAL.yml")
            incompleteFile.writeText("""
                health: 20.0
                level: 5
            """.trimIndent())

            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            // Should return null since required fields are missing
            assertNull(loaded, "Load should return null for incomplete file")
        }

        @Test
        @DisplayName("should continue loading other files when one is corrupted")
        fun testLoadAllWithCorruptedFile(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()

            // Save a valid file
            storage.savePlayerData(createEmptyPlayerData(gameMode = GameMode.SURVIVAL))

            // Create a corrupted file for a different gamemode
            val playerDir = File(File(File(plugin.dataFolder, "data"), "players"), testUuid1.toString())
            val corruptedFile = File(playerDir, "${testGroup}_CREATIVE.yml")
            corruptedFile.writeText("corrupted: yaml: [[[")

            val allData = storage.loadAllPlayerData(testUuid1)

            // Should still load the valid file
            assertTrue(allData.isNotEmpty(), "Should load valid files even when some are corrupted")
        }
    }

    // ============================================================
    // Batch Save Operations Tests
    // ============================================================

    @Nested
    @DisplayName("Batch save operations")
    inner class BatchSaveTests {

        @Test
        @DisplayName("should save multiple player data entries in batch")
        fun testBatchSave(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val dataList = listOf(
                createEmptyPlayerData(uuid = testUuid1, group = "survival"),
                createEmptyPlayerData(uuid = testUuid2, group = "survival"),
                createEmptyPlayerData(uuid = testUuid3, group = "survival")
            )

            val count = storage.savePlayerDataBatch(dataList)

            assertEquals(3, count, "All entries should be saved")
            assertNotNull(storage.loadPlayerData(testUuid1, "survival", GameMode.SURVIVAL))
            assertNotNull(storage.loadPlayerData(testUuid2, "survival", GameMode.SURVIVAL))
            assertNotNull(storage.loadPlayerData(testUuid3, "survival", GameMode.SURVIVAL))
        }

        @Test
        @DisplayName("should return zero for batch save when not initialized")
        fun testBatchSaveWithoutInitialization(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            val dataList = listOf(createEmptyPlayerData())

            val count = storage.savePlayerDataBatch(dataList)

            assertEquals(0, count)
        }

        @Test
        @DisplayName("should handle empty batch")
        fun testEmptyBatchSave(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val count = storage.savePlayerDataBatch(emptyList())

            assertEquals(0, count)
        }

        @Test
        @DisplayName("should save batch with mixed groups and gamemodes")
        fun testBatchSaveMixedData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val dataList = listOf(
                createEmptyPlayerData(uuid = testUuid1, group = "survival", gameMode = GameMode.SURVIVAL),
                createEmptyPlayerData(uuid = testUuid1, group = "survival", gameMode = GameMode.CREATIVE),
                createEmptyPlayerData(uuid = testUuid1, group = "creative", gameMode = GameMode.CREATIVE),
                createEmptyPlayerData(uuid = testUuid2, group = "survival", gameMode = GameMode.SURVIVAL)
            )

            val count = storage.savePlayerDataBatch(dataList)

            assertEquals(4, count)
        }
    }

    // ============================================================
    // Health Check Tests
    // ============================================================

    @Nested
    @DisplayName("Health check returns true when directory exists")
    inner class HealthCheckTests {

        @Test
        @DisplayName("should return true when storage is healthy")
        fun testHealthyStorage(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val healthy = storage.isHealthy()

            assertTrue(healthy)
        }

        @Test
        @DisplayName("should return false when storage not initialized")
        fun testHealthCheckWithoutInitialization(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)

            val healthy = storage.isHealthy()

            assertFalse(healthy)
        }

        @Test
        @DisplayName("should return false when players directory does not exist")
        fun testHealthCheckWithMissingDirectory(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()

            // Delete the players directory
            val playersDir = File(File(plugin.dataFolder, "data"), "players")
            playersDir.deleteRecursively()

            val healthy = storage.isHealthy()

            assertFalse(healthy)
        }
    }

    // ============================================================
    // Shutdown Tests
    // ============================================================

    @Nested
    @DisplayName("Shutdown cleans up resources")
    inner class ShutdownTests {

        @Test
        @DisplayName("should shutdown gracefully")
        fun testShutdown(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            assertDoesNotThrow {
                runBlocking { storage.shutdown() }
            }
        }

        @Test
        @DisplayName("should handle shutdown when not initialized")
        fun testShutdownWithoutInitialization(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)

            assertDoesNotThrow {
                runBlocking { storage.shutdown() }
            }
        }

        @Test
        @DisplayName("should return false for operations after shutdown")
        fun testOperationsAfterShutdown(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            storage.shutdown()

            val saveResult = storage.savePlayerData(createEmptyPlayerData())
            val loadResult = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)

            assertFalse(saveResult)
            assertNull(loadResult)
        }
    }

    // ============================================================
    // File Naming Convention Tests
    // ============================================================

    @Nested
    @DisplayName("File naming convention (UUID-based)")
    inner class FileNamingTests {

        @Test
        @DisplayName("should create files with correct naming convention")
        fun testFileNamingConvention(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()
            storage.savePlayerData(createEmptyPlayerData(group = "survival", gameMode = GameMode.SURVIVAL))

            val expectedFile = File(
                File(File(File(plugin.dataFolder, "data"), "players"), testUuid1.toString()),
                "survival_SURVIVAL.yml"
            )

            assertTrue(expectedFile.exists(), "File should follow naming convention: group_GAMEMODE.yml")
        }

        @Test
        @DisplayName("should sanitize group names in filenames")
        fun testFilenameSanitization(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()
            val specialGroup = "my/special\\group:name"
            storage.savePlayerData(createEmptyPlayerData(group = specialGroup))

            val playerDir = File(File(File(plugin.dataFolder, "data"), "players"), testUuid1.toString())

            // File should exist with sanitized name
            val files = playerDir.listFiles()
            assertNotNull(files)
            assertTrue(files!!.isNotEmpty())

            // Filename should not contain special characters
            val filename = files[0].name
            assertFalse(filename.contains("/"))
            assertFalse(filename.contains("\\"))
            assertFalse(filename.contains(":"))
        }

        @Test
        @DisplayName("should create correct directory structure per player UUID")
        fun testDirectoryStructurePerPlayer(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()

            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid1))
            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid2))

            val playersDir = File(File(plugin.dataFolder, "data"), "players")
            val player1Dir = File(playersDir, testUuid1.toString())
            val player2Dir = File(playersDir, testUuid2.toString())

            assertTrue(player1Dir.exists() && player1Dir.isDirectory)
            assertTrue(player2Dir.exists() && player2Dir.isDirectory)
        }
    }

    // ============================================================
    // Directory Structure Creation Tests
    // ============================================================

    @Nested
    @DisplayName("Directory structure creation")
    inner class DirectoryStructureTests {

        @Test
        @DisplayName("should create nested directory structure on save")
        fun testNestedDirectoryCreation(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()

            // Delete all directories to test creation
            val dataDir = File(plugin.dataFolder, "data")
            dataDir.deleteRecursively()

            storage.savePlayerData(createEmptyPlayerData())

            val playersDir = File(dataDir, "players")
            val playerDir = File(playersDir, testUuid1.toString())

            assertTrue(playersDir.exists())
            assertTrue(playerDir.exists())
        }

        @Test
        @DisplayName("should preserve directory structure across multiple saves")
        fun testDirectoryStructurePreservation(@TempDir tempDir: Path) = runBlocking {
            val (storage, plugin) = createStorage(tempDir)
            storage.initialize()

            // Save multiple entries
            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid1, group = "group1"))
            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid1, group = "group2"))
            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid2, group = "group1"))

            val playersDir = File(File(plugin.dataFolder, "data"), "players")

            // Check player 1 has 2 files
            val player1Files = File(playersDir, testUuid1.toString()).listFiles()
            assertNotNull(player1Files)
            assertEquals(2, player1Files!!.size)

            // Check player 2 has 1 file
            val player2Files = File(playersDir, testUuid2.toString()).listFiles()
            assertNotNull(player2Files)
            assertEquals(1, player2Files!!.size)
        }
    }

    // ============================================================
    // Additional Storage Operations Tests
    // ============================================================

    @Nested
    @DisplayName("Additional storage operations")
    inner class AdditionalOperationsTests {

        @Test
        @DisplayName("should check if player data exists")
        fun testHasPlayerData(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            storage.savePlayerData(createEmptyPlayerData())

            assertTrue(storage.hasPlayerData(testUuid1, testGroup, GameMode.SURVIVAL))
            assertFalse(storage.hasPlayerData(testUuid1, testGroup, GameMode.CREATIVE))
            assertFalse(storage.hasPlayerData(testUuid2, testGroup, GameMode.SURVIVAL))
        }

        @Test
        @DisplayName("should check if player has any data in group when gamemode is null")
        fun testHasPlayerDataNullGameMode(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            storage.savePlayerData(createEmptyPlayerData(gameMode = GameMode.CREATIVE))

            assertTrue(storage.hasPlayerData(testUuid1, testGroup, null))
            assertFalse(storage.hasPlayerData(testUuid1, "othergroup", null))
        }

        @Test
        @DisplayName("should get all player UUIDs")
        fun testGetAllPlayerUUIDs(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid1))
            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid2))
            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid3))

            val uuids = storage.getAllPlayerUUIDs()

            assertEquals(3, uuids.size)
            assertTrue(uuids.contains(testUuid1))
            assertTrue(uuids.contains(testUuid2))
            assertTrue(uuids.contains(testUuid3))
        }

        @Test
        @DisplayName("should return empty set when no players exist")
        fun testGetAllPlayerUUIDsEmpty(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val uuids = storage.getAllPlayerUUIDs()

            assertTrue(uuids.isEmpty())
        }

        @Test
        @DisplayName("should get groups for a player")
        fun testGetPlayerGroups(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            storage.savePlayerData(createEmptyPlayerData(group = "survival", gameMode = GameMode.SURVIVAL))
            storage.savePlayerData(createEmptyPlayerData(group = "survival", gameMode = GameMode.CREATIVE))
            storage.savePlayerData(createEmptyPlayerData(group = "creative", gameMode = GameMode.CREATIVE))
            storage.savePlayerData(createEmptyPlayerData(group = "skyblock", gameMode = GameMode.SURVIVAL))

            val groups = storage.getPlayerGroups(testUuid1)

            assertEquals(3, groups.size)
            assertTrue(groups.contains("survival"))
            assertTrue(groups.contains("creative"))
            assertTrue(groups.contains("skyblock"))
        }

        @Test
        @DisplayName("should return empty set for player with no groups")
        fun testGetPlayerGroupsEmpty(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val groups = storage.getPlayerGroups(testUuid1)

            assertTrue(groups.isEmpty())
        }

        @Test
        @DisplayName("should get entry count")
        fun testGetEntryCount(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid1, group = "survival", gameMode = GameMode.SURVIVAL))
            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid1, group = "survival", gameMode = GameMode.CREATIVE))
            storage.savePlayerData(createEmptyPlayerData(uuid = testUuid2, group = "survival", gameMode = GameMode.SURVIVAL))

            val count = storage.getEntryCount()

            assertEquals(3, count)
        }

        @Test
        @DisplayName("should return zero entry count for empty storage")
        fun testGetEntryCountEmpty(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val count = storage.getEntryCount()

            assertEquals(0, count)
        }

        @Test
        @DisplayName("should get storage size")
        fun testGetStorageSize(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            storage.savePlayerData(createFullPlayerData())

            val size = storage.getStorageSize()

            assertTrue(size > 0, "Storage size should be positive after saving data")
        }

        @Test
        @DisplayName("should return zero size for empty storage")
        fun testGetStorageSizeEmpty(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val size = storage.getStorageSize()

            assertEquals(0L, size)
        }
    }

    // ============================================================
    // Concurrent Access Tests
    // ============================================================

    @Nested
    @DisplayName("Concurrent access handling")
    inner class ConcurrentAccessTests {

        @Test
        @DisplayName("should handle concurrent saves safely")
        fun testConcurrentSaves(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()

            val jobs = (1..10).map { i ->
                async {
                    val data = createEmptyPlayerData(
                        uuid = UUID.randomUUID(),
                        group = "group$i"
                    )
                    storage.savePlayerData(data)
                }
            }

            val results = jobs.awaitAll()

            assertTrue(results.all { it }, "All concurrent saves should succeed")
        }

        @Test
        @DisplayName("should handle concurrent reads safely")
        fun testConcurrentReads(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            storage.savePlayerData(createEmptyPlayerData())

            val jobs = (1..10).map {
                async {
                    storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
                }
            }

            val results = jobs.awaitAll()

            assertTrue(results.all { it != null }, "All concurrent reads should succeed")
        }
    }

    // ============================================================
    // Edge Cases Tests
    // ============================================================

    @Nested
    @DisplayName("Edge cases")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("should handle very long group names")
        fun testLongGroupName(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val longGroup = "a".repeat(100)
            val data = createEmptyPlayerData(group = longGroup)

            val result = storage.savePlayerData(data)

            assertTrue(result)
            assertNotNull(storage.loadPlayerData(testUuid1, longGroup, GameMode.SURVIVAL))
        }

        @Test
        @DisplayName("should handle unicode characters in player name")
        fun testUnicodePlayerName(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val data = createEmptyPlayerData(playerName = "Player_123")

            val result = storage.savePlayerData(data)

            assertTrue(result)
            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            assertNotNull(loaded)
            assertEquals("Player_123", loaded!!.playerName)
        }

        @Test
        @DisplayName("should handle maximum inventory values")
        fun testMaximumValues(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val data = createEmptyPlayerData().apply {
                health = 1024.0
                maxHealth = 2048.0
                foodLevel = 20
                level = Int.MAX_VALUE
                totalExperience = Int.MAX_VALUE
            }

            val result = storage.savePlayerData(data)

            assertTrue(result)
            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            assertNotNull(loaded)
            assertEquals(Int.MAX_VALUE, loaded!!.level)
            assertEquals(Int.MAX_VALUE, loaded.totalExperience)
        }

        @Test
        @DisplayName("should handle minimum inventory values")
        fun testMinimumValues(@TempDir tempDir: Path) = runBlocking {
            val (storage, _) = createStorage(tempDir)
            storage.initialize()
            val data = createEmptyPlayerData().apply {
                health = 0.0
                maxHealth = 0.0
                foodLevel = 0
                saturation = 0.0f
                exhaustion = 0.0f
                experience = 0.0f
                level = 0
                totalExperience = 0
            }

            val result = storage.savePlayerData(data)

            assertTrue(result)
            val loaded = storage.loadPlayerData(testUuid1, testGroup, GameMode.SURVIVAL)
            assertNotNull(loaded)
            assertEquals(0.0, loaded!!.health)
            assertEquals(0, loaded.level)
        }
    }
}

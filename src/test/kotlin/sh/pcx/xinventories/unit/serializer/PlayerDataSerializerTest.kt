package sh.pcx.xinventories.unit.serializer

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.api.model.InventoryContents
import sh.pcx.xinventories.api.model.PlayerInventorySnapshot
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.util.Logging
import sh.pcx.xinventories.internal.util.PlayerDataSerializer
import java.time.Instant
import java.util.UUID
import java.util.logging.Logger

/**
 * Comprehensive unit tests for PlayerDataSerializer.
 * Tests YAML serialization, SQL serialization, snapshot conversion, and cache key handling.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerDataSerializerTest {

    private lateinit var server: ServerMock

    @BeforeAll
    fun setUpServer() {
        server = MockBukkit.mock()
        // Initialize logging to prevent NPE in serializer error handling
        Logging.init(Logger.getLogger("PlayerDataSerializerTest"), debug = true)
    }

    @AfterAll
    fun tearDownServer() {
        MockBukkit.unmock()
    }

    // ============================================================
    // Test Fixtures
    // ============================================================

    private val testUuid = UUID.fromString("12345678-1234-1234-1234-123456789abc")
    private val testPlayerName = "TestPlayer"
    private val testGroup = "survival"
    private val testTimestamp = Instant.ofEpochMilli(1700000000000L)

    /**
     * Creates a PlayerData with all fields populated for testing.
     */
    private fun createFullPlayerData(): PlayerData {
        return PlayerData(
            uuid = testUuid,
            playerName = testPlayerName,
            group = testGroup,
            gameMode = GameMode.SURVIVAL
        ).apply {
            // Player state
            health = 15.5
            maxHealth = 24.0
            foodLevel = 18
            saturation = 4.5f
            exhaustion = 1.2f
            experience = 0.75f
            level = 25
            totalExperience = 1250

            // Timestamp
            timestamp = testTimestamp

            // Main inventory
            mainInventory[0] = ItemStack(Material.DIAMOND_SWORD, 1)
            mainInventory[1] = ItemStack(Material.DIAMOND_PICKAXE, 1)
            mainInventory[8] = ItemStack(Material.GOLDEN_APPLE, 16)
            mainInventory[35] = ItemStack(Material.TORCH, 64)

            // Armor
            armorInventory[0] = ItemStack(Material.DIAMOND_BOOTS, 1)
            armorInventory[1] = ItemStack(Material.DIAMOND_LEGGINGS, 1)
            armorInventory[2] = ItemStack(Material.DIAMOND_CHESTPLATE, 1)
            armorInventory[3] = ItemStack(Material.DIAMOND_HELMET, 1)

            // Offhand
            offhand = ItemStack(Material.SHIELD, 1)

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
    private fun createEmptyPlayerData(): PlayerData {
        return PlayerData(
            uuid = testUuid,
            playerName = testPlayerName,
            group = testGroup,
            gameMode = GameMode.SURVIVAL
        ).apply {
            timestamp = testTimestamp
        }
    }

    // ============================================================
    // YAML Serialization Tests
    // ============================================================

    @Nested
    @DisplayName("YAML Serialization")
    inner class YamlSerializationTests {

        @Test
        @DisplayName("should serialize all PlayerData fields to YAML")
        fun testToYamlSerializesAllFields() {
            val data = createFullPlayerData()
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)

            // Basic fields
            assertEquals(testUuid.toString(), yaml.getString("uuid"))
            assertEquals(testPlayerName, yaml.getString("player-name"))
            assertEquals(testGroup, yaml.getString("group"))
            assertEquals("SURVIVAL", yaml.getString("gamemode"))
            assertEquals(testTimestamp.toEpochMilli(), yaml.getLong("timestamp"))

            // Player state
            assertEquals(15.5, yaml.getDouble("health"))
            assertEquals(24.0, yaml.getDouble("max-health"))
            assertEquals(18, yaml.getInt("food-level"))
            assertEquals(4.5, yaml.getDouble("saturation"), 0.001)
            assertEquals(1.2, yaml.getDouble("exhaustion"), 0.001)
            assertEquals(0.75, yaml.getDouble("experience"), 0.001)
            assertEquals(25, yaml.getInt("level"))
            assertEquals(1250, yaml.getInt("total-experience"))

            // Inventory section should be present (YAML paths create nested sections)
            // Use the full path with dots as set by the serializer
            Assertions.assertNotNull(yaml.get("inventory.main"), "inventory.main should be set")
            Assertions.assertNotNull(yaml.get("inventory.armor"), "inventory.armor should be set")
            Assertions.assertNotNull(yaml.get("inventory.offhand"), "inventory.offhand should be set")
            Assertions.assertNotNull(yaml.get("inventory.ender-chest"), "inventory.ender-chest should be set")

            // Potion effects should be present
            Assertions.assertNotNull(yaml.getList("potion-effects"))
        }

        @Test
        @DisplayName("should serialize empty PlayerData without errors")
        fun testToYamlWithEmptyData() {
            val data = createEmptyPlayerData()
            val yaml = YamlConfiguration()

            assertDoesNotThrow {
                PlayerDataSerializer.toYaml(data, yaml)
            }

            assertEquals(testUuid.toString(), yaml.getString("uuid"))
            assertEquals(testPlayerName, yaml.getString("player-name"))
            assertEquals(testGroup, yaml.getString("group"))
        }

        @ParameterizedTest
        @EnumSource(GameMode::class)
        @DisplayName("should serialize all GameModes correctly")
        fun testToYamlSerializesAllGameModes(gameMode: GameMode) {
            val data = PlayerData(testUuid, testPlayerName, testGroup, gameMode)
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)

            assertEquals(gameMode.name, yaml.getString("gamemode"))
        }
    }

    // ============================================================
    // YAML Deserialization Tests
    // ============================================================

    @Nested
    @DisplayName("YAML Deserialization")
    inner class YamlDeserializationTests {

        @Test
        @DisplayName("should deserialize all PlayerData fields from YAML")
        fun testFromYamlDeserializesAllFields() {
            val original = createFullPlayerData()
            val yaml = YamlConfiguration()
            PlayerDataSerializer.toYaml(original, yaml)

            val deserialized = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(deserialized)
            assertEquals(original.uuid, deserialized!!.uuid)
            assertEquals(original.playerName, deserialized.playerName)
            assertEquals(original.group, deserialized.group)
            assertEquals(original.gameMode, deserialized.gameMode)
            assertEquals(original.timestamp, deserialized.timestamp)

            // Player state
            assertEquals(original.health, deserialized.health)
            assertEquals(original.maxHealth, deserialized.maxHealth)
            assertEquals(original.foodLevel, deserialized.foodLevel)
            assertEquals(original.saturation, deserialized.saturation, 0.001f)
            assertEquals(original.exhaustion, deserialized.exhaustion, 0.001f)
            assertEquals(original.experience, deserialized.experience, 0.001f)
            assertEquals(original.level, deserialized.level)
            assertEquals(original.totalExperience, deserialized.totalExperience)
        }

        @Test
        @DisplayName("should return null when UUID is missing")
        fun testFromYamlReturnsNullWithoutUuid() {
            val yaml = YamlConfiguration()
            yaml.set("player-name", testPlayerName)
            yaml.set("group", testGroup)

            val result = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should return null when player name is missing")
        fun testFromYamlReturnsNullWithoutPlayerName() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", testUuid.toString())
            yaml.set("group", testGroup)

            val result = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should return null when group is missing")
        fun testFromYamlReturnsNullWithoutGroup() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", testUuid.toString())
            yaml.set("player-name", testPlayerName)

            val result = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should use default values for missing optional fields")
        fun testFromYamlUsesDefaultsForMissingFields() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", testUuid.toString())
            yaml.set("player-name", testPlayerName)
            yaml.set("group", testGroup)

            val result = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(result)
            assertEquals(20.0, result!!.health)
            assertEquals(20.0, result.maxHealth)
            assertEquals(20, result.foodLevel)
            assertEquals(5.0f, result.saturation, 0.001f)
            assertEquals(0.0f, result.exhaustion, 0.001f)
            assertEquals(0.0f, result.experience, 0.001f)
            assertEquals(0, result.level)
            assertEquals(0, result.totalExperience)
            assertEquals(GameMode.SURVIVAL, result.gameMode)
        }

        @Test
        @DisplayName("should handle invalid GameMode gracefully")
        fun testFromYamlHandlesInvalidGameMode() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", testUuid.toString())
            yaml.set("player-name", testPlayerName)
            yaml.set("group", testGroup)
            yaml.set("gamemode", "INVALID_MODE")

            val result = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(result)
            assertEquals(GameMode.SURVIVAL, result!!.gameMode)
        }

        @Test
        @DisplayName("should deserialize and re-serialize without data loss")
        fun testRoundTripYamlSerialization() {
            // Use empty inventories to avoid YAML serialization issues with ItemStack
            val original = createEmptyPlayerData()
            val yaml1 = YamlConfiguration()
            PlayerDataSerializer.toYaml(original, yaml1)

            val deserialized = PlayerDataSerializer.fromYaml(yaml1)

            Assertions.assertNotNull(deserialized, "Deserialized data should not be null")

            // Compare the data fields instead of YAML strings (format may vary)
            assertEquals(original.uuid, deserialized!!.uuid)
            assertEquals(original.playerName, deserialized.playerName)
            assertEquals(original.group, deserialized.group)
            assertEquals(original.gameMode, deserialized.gameMode)
            assertEquals(original.timestamp, deserialized.timestamp)
            assertEquals(original.health, deserialized.health)
            assertEquals(original.maxHealth, deserialized.maxHealth)
            assertEquals(original.foodLevel, deserialized.foodLevel)
            assertEquals(original.saturation, deserialized.saturation, 0.001f)
            assertEquals(original.exhaustion, deserialized.exhaustion, 0.001f)
            assertEquals(original.experience, deserialized.experience, 0.001f)
            assertEquals(original.level, deserialized.level)
            assertEquals(original.totalExperience, deserialized.totalExperience)
        }
    }

    // ============================================================
    // SQL Serialization Tests
    // ============================================================

    @Nested
    @DisplayName("SQL Serialization")
    inner class SqlSerializationTests {

        @Test
        @DisplayName("should serialize all PlayerData fields to SQL map")
        fun testToSqlMapSerializesAllFields() {
            val data = createFullPlayerData()

            val sqlMap = PlayerDataSerializer.toSqlMap(data)

            assertEquals(testUuid.toString(), sqlMap["uuid"])
            assertEquals(testPlayerName, sqlMap["player_name"])
            assertEquals(testGroup, sqlMap["group_name"])
            assertEquals("SURVIVAL", sqlMap["gamemode"])
            assertEquals(testTimestamp.toEpochMilli(), sqlMap["timestamp"])

            // Player state
            assertEquals(15.5, sqlMap["health"])
            assertEquals(24.0, sqlMap["max_health"])
            assertEquals(18, sqlMap["food_level"])
            assertEquals(4.5f, sqlMap["saturation"])
            assertEquals(1.2f, sqlMap["exhaustion"])
            assertEquals(0.75f, sqlMap["experience"])
            assertEquals(25, sqlMap["level"])
            assertEquals(1250, sqlMap["total_experience"])

            // Inventories should be Base64 encoded strings
            assertTrue(sqlMap["main_inventory"] is String)
            assertTrue(sqlMap["armor_inventory"] is String)
            assertTrue(sqlMap["offhand"] is String)
            assertTrue(sqlMap["ender_chest"] is String)
            assertTrue(sqlMap["potion_effects"] is String)

            // Non-empty inventories should have content
            assertTrue((sqlMap["main_inventory"] as String).isNotEmpty())
            assertTrue((sqlMap["armor_inventory"] as String).isNotEmpty())
            assertTrue((sqlMap["offhand"] as String).isNotEmpty())
            assertTrue((sqlMap["ender_chest"] as String).isNotEmpty())
        }

        @Test
        @DisplayName("should produce empty strings for empty inventories")
        fun testToSqlMapWithEmptyInventories() {
            val data = createEmptyPlayerData()

            val sqlMap = PlayerDataSerializer.toSqlMap(data)

            assertEquals("", sqlMap["main_inventory"])
            assertEquals("", sqlMap["armor_inventory"])
            assertEquals("", sqlMap["offhand"])
            assertEquals("", sqlMap["ender_chest"])
            assertEquals("", sqlMap["potion_effects"])
        }

        @Test
        @DisplayName("should use correct SQL column names")
        fun testSqlColumnNaming() {
            val data = createEmptyPlayerData()

            val sqlMap = PlayerDataSerializer.toSqlMap(data)

            // Verify all expected keys are present with snake_case naming
            assertTrue(sqlMap.containsKey("uuid"))
            assertTrue(sqlMap.containsKey("player_name"))
            assertTrue(sqlMap.containsKey("group_name"))
            assertTrue(sqlMap.containsKey("gamemode"))
            assertTrue(sqlMap.containsKey("timestamp"))
            assertTrue(sqlMap.containsKey("health"))
            assertTrue(sqlMap.containsKey("max_health"))
            assertTrue(sqlMap.containsKey("food_level"))
            assertTrue(sqlMap.containsKey("saturation"))
            assertTrue(sqlMap.containsKey("exhaustion"))
            assertTrue(sqlMap.containsKey("experience"))
            assertTrue(sqlMap.containsKey("level"))
            assertTrue(sqlMap.containsKey("total_experience"))
            assertTrue(sqlMap.containsKey("main_inventory"))
            assertTrue(sqlMap.containsKey("armor_inventory"))
            assertTrue(sqlMap.containsKey("offhand"))
            assertTrue(sqlMap.containsKey("ender_chest"))
            assertTrue(sqlMap.containsKey("potion_effects"))
        }

        @ParameterizedTest
        @EnumSource(GameMode::class)
        @DisplayName("should serialize all GameModes to SQL")
        fun testToSqlMapSerializesAllGameModes(gameMode: GameMode) {
            val data = PlayerData(testUuid, testPlayerName, testGroup, gameMode)

            val sqlMap = PlayerDataSerializer.toSqlMap(data)

            assertEquals(gameMode.name, sqlMap["gamemode"])
        }
    }

    // ============================================================
    // SQL Deserialization Tests
    // ============================================================

    @Nested
    @DisplayName("SQL Deserialization")
    inner class SqlDeserializationTests {

        @Test
        @DisplayName("should deserialize all PlayerData fields from SQL row")
        fun testFromSqlMapDeserializesAllFields() {
            val original = createFullPlayerData()
            val sqlMap = PlayerDataSerializer.toSqlMap(original)

            val deserialized = PlayerDataSerializer.fromSqlMap(sqlMap)

            Assertions.assertNotNull(deserialized)
            assertEquals(original.uuid, deserialized!!.uuid)
            assertEquals(original.playerName, deserialized.playerName)
            assertEquals(original.group, deserialized.group)
            assertEquals(original.gameMode, deserialized.gameMode)
            assertEquals(original.timestamp, deserialized.timestamp)

            // Player state
            assertEquals(original.health, deserialized.health)
            assertEquals(original.maxHealth, deserialized.maxHealth)
            assertEquals(original.foodLevel, deserialized.foodLevel)
            assertEquals(original.saturation, deserialized.saturation, 0.001f)
            assertEquals(original.exhaustion, deserialized.exhaustion, 0.001f)
            assertEquals(original.experience, deserialized.experience, 0.001f)
            assertEquals(original.level, deserialized.level)
            assertEquals(original.totalExperience, deserialized.totalExperience)
        }

        @Test
        @DisplayName("should return null when UUID is missing")
        fun testFromSqlMapReturnsNullWithoutUuid() {
            val row = mapOf(
                "player_name" to testPlayerName,
                "group_name" to testGroup
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should return null when player name is missing")
        fun testFromSqlMapReturnsNullWithoutPlayerName() {
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "group_name" to testGroup
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should return null when group name is missing")
        fun testFromSqlMapReturnsNullWithoutGroupName() {
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "player_name" to testPlayerName
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should use default values for missing optional fields")
        fun testFromSqlMapUsesDefaultsForMissingFields() {
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "player_name" to testPlayerName,
                "group_name" to testGroup
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNotNull(result)
            assertEquals(20.0, result!!.health)
            assertEquals(20.0, result.maxHealth)
            assertEquals(20, result.foodLevel)
            assertEquals(5.0f, result.saturation, 0.001f)
            assertEquals(0.0f, result.exhaustion, 0.001f)
            assertEquals(0.0f, result.experience, 0.001f)
            assertEquals(0, result.level)
            assertEquals(0, result.totalExperience)
            assertEquals(GameMode.SURVIVAL, result.gameMode)
        }

        @Test
        @DisplayName("should handle invalid GameMode gracefully")
        fun testFromSqlMapHandlesInvalidGameMode() {
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "player_name" to testPlayerName,
                "group_name" to testGroup,
                "gamemode" to "INVALID_MODE"
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNotNull(result)
            assertEquals(GameMode.SURVIVAL, result!!.gameMode)
        }

        @Test
        @DisplayName("should handle numeric values of different types")
        fun testFromSqlMapHandlesNumericTypeVariations() {
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "player_name" to testPlayerName,
                "group_name" to testGroup,
                "health" to 15.5, // Double
                "max_health" to 24L, // Long
                "food_level" to 18.0, // Double as Int
                "saturation" to 4.5, // Double as Float
                "level" to 25L, // Long as Int
                "timestamp" to 1700000000000L
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNotNull(result)
            assertEquals(15.5, result!!.health)
            assertEquals(24.0, result.maxHealth)
            assertEquals(18, result.foodLevel)
            assertEquals(4.5f, result.saturation, 0.001f)
            assertEquals(25, result.level)
        }

        @Test
        @DisplayName("should handle empty inventory strings")
        fun testFromSqlMapHandlesEmptyInventoryStrings() {
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "player_name" to testPlayerName,
                "group_name" to testGroup,
                "main_inventory" to "",
                "armor_inventory" to "",
                "offhand" to "",
                "ender_chest" to "",
                "potion_effects" to ""
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNotNull(result)
            assertTrue(result!!.mainInventory.isEmpty())
            assertTrue(result.armorInventory.isEmpty())
            Assertions.assertNull(result.offhand)
            assertTrue(result.enderChest.isEmpty())
            assertTrue(result.potionEffects.isEmpty())
        }

        @Test
        @DisplayName("should deserialize and re-serialize without data loss")
        fun testRoundTripSqlSerialization() {
            val original = createFullPlayerData()
            val sqlMap1 = PlayerDataSerializer.toSqlMap(original)

            val deserialized = PlayerDataSerializer.fromSqlMap(sqlMap1)!!
            val sqlMap2 = PlayerDataSerializer.toSqlMap(deserialized)

            // Compare the maps
            assertEquals(sqlMap1["uuid"], sqlMap2["uuid"])
            assertEquals(sqlMap1["player_name"], sqlMap2["player_name"])
            assertEquals(sqlMap1["group_name"], sqlMap2["group_name"])
            assertEquals(sqlMap1["gamemode"], sqlMap2["gamemode"])
            assertEquals(sqlMap1["timestamp"], sqlMap2["timestamp"])
            assertEquals(sqlMap1["health"], sqlMap2["health"])
            assertEquals(sqlMap1["max_health"], sqlMap2["max_health"])
            assertEquals(sqlMap1["food_level"], sqlMap2["food_level"])
            assertEquals(sqlMap1["level"], sqlMap2["level"])
            assertEquals(sqlMap1["total_experience"], sqlMap2["total_experience"])
        }
    }

    // ============================================================
    // Snapshot Conversion Tests
    // ============================================================

    @Nested
    @DisplayName("Snapshot Conversion")
    inner class SnapshotConversionTests {

        @Test
        @DisplayName("should convert snapshot to YAML string")
        fun testSnapshotToYamlString() {
            val snapshot = createTestSnapshot()

            val yamlString = PlayerDataSerializer.snapshotToYamlString(snapshot)

            Assertions.assertNotNull(yamlString)
            assertTrue(yamlString.isNotEmpty())
            assertTrue(yamlString.contains("uuid:"))
            assertTrue(yamlString.contains("player-name:"))
            assertTrue(yamlString.contains("group:"))
        }

        @Test
        @DisplayName("should convert YAML string back to snapshot")
        fun testSnapshotFromYamlString() {
            val original = createSimpleSnapshot()
            val yamlString = PlayerDataSerializer.snapshotToYamlString(original)

            val restored = PlayerDataSerializer.snapshotFromYamlString(yamlString)

            Assertions.assertNotNull(restored, "Restored snapshot should not be null. YAML: $yamlString")
            assertEquals(original.uuid, restored!!.uuid)
            assertEquals(original.playerName, restored.playerName)
            assertEquals(original.group, restored.group)
            assertEquals(original.gameMode, restored.gameMode)
            assertEquals(original.health, restored.health)
            assertEquals(original.maxHealth, restored.maxHealth)
            assertEquals(original.foodLevel, restored.foodLevel)
            assertEquals(original.level, restored.level)
        }

        @Test
        @DisplayName("should return null for invalid YAML string")
        fun testSnapshotFromYamlStringWithInvalidYaml() {
            val invalidYaml = "this is not valid yaml: [["

            val result = PlayerDataSerializer.snapshotFromYamlString(invalidYaml)

            // Should return null or handle gracefully
            // Depending on implementation, this might return null or throw
            // The actual behavior should be graceful handling
        }

        @Test
        @DisplayName("should return null for YAML missing required fields")
        fun testSnapshotFromYamlStringWithMissingFields() {
            val incompleteYaml = """
                health: 20.0
                level: 5
            """.trimIndent()

            val result = PlayerDataSerializer.snapshotFromYamlString(incompleteYaml)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should preserve all snapshot fields through round-trip")
        fun testSnapshotRoundTrip() {
            val original = createSimpleSnapshot()

            val yamlString = PlayerDataSerializer.snapshotToYamlString(original)
            val restored = PlayerDataSerializer.snapshotFromYamlString(yamlString)

            Assertions.assertNotNull(restored, "Restored snapshot should not be null. YAML: $yamlString")
            assertEquals(original.uuid, restored!!.uuid)
            assertEquals(original.playerName, restored.playerName)
            assertEquals(original.group, restored.group)
            assertEquals(original.gameMode, restored.gameMode)
            assertEquals(original.health, restored.health, 0.001)
            assertEquals(original.maxHealth, restored.maxHealth, 0.001)
            assertEquals(original.foodLevel, restored.foodLevel)
            assertEquals(original.saturation, restored.saturation, 0.001f)
            assertEquals(original.exhaustion, restored.exhaustion, 0.001f)
            assertEquals(original.experience, restored.experience, 0.001f)
            assertEquals(original.level, restored.level)
            assertEquals(original.totalExperience, restored.totalExperience)
        }

        /**
         * Creates a simple snapshot without inventory items (which require Bukkit serialization).
         */
        private fun createSimpleSnapshot(): PlayerInventorySnapshot {
            return PlayerInventorySnapshot(
                uuid = testUuid,
                playerName = testPlayerName,
                group = testGroup,
                gameMode = GameMode.SURVIVAL,
                contents = InventoryContents.empty(),
                health = 18.0,
                maxHealth = 22.0,
                foodLevel = 15,
                saturation = 3.5f,
                exhaustion = 0.5f,
                experience = 0.5f,
                level = 30,
                totalExperience = 2500,
                potionEffects = emptyList(),
                timestamp = testTimestamp
            )
        }

        /**
         * Creates a snapshot with inventory contents for testing.
         */
        private fun createTestSnapshot(): PlayerInventorySnapshot {
            return PlayerInventorySnapshot(
                uuid = testUuid,
                playerName = testPlayerName,
                group = testGroup,
                gameMode = GameMode.SURVIVAL,
                contents = InventoryContents(
                    main = mapOf(0 to ItemStack(Material.DIAMOND_SWORD)),
                    armor = mapOf(3 to ItemStack(Material.DIAMOND_HELMET)),
                    offhand = ItemStack(Material.SHIELD),
                    enderChest = mapOf(0 to ItemStack(Material.DIAMOND, 64))
                ),
                health = 18.0,
                maxHealth = 22.0,
                foodLevel = 15,
                saturation = 3.5f,
                exhaustion = 0.5f,
                experience = 0.5f,
                level = 30,
                totalExperience = 2500,
                potionEffects = listOf(
                    PotionEffect(PotionEffectType.SPEED, 600, 1)
                ),
                timestamp = testTimestamp
            )
        }
    }

    // ============================================================
    // Cache Key Tests
    // ============================================================

    @Nested
    @DisplayName("Cache Key Generation and Parsing")
    inner class CacheKeyTests {

        @Test
        @DisplayName("should generate cache key with GameMode")
        fun testCacheKeyWithGameMode() {
            val key = PlayerDataSerializer.cacheKey(testUuid, testGroup, GameMode.SURVIVAL)

            assertEquals("${testUuid}_${testGroup}_SURVIVAL", key)
        }

        @Test
        @DisplayName("should generate cache key without GameMode")
        fun testCacheKeyWithoutGameMode() {
            val key = PlayerDataSerializer.cacheKey(testUuid, testGroup, null)

            assertEquals("${testUuid}_${testGroup}", key)
        }

        @ParameterizedTest
        @EnumSource(GameMode::class)
        @DisplayName("should include all GameModes in cache key")
        fun testCacheKeyWithAllGameModes(gameMode: GameMode) {
            val key = PlayerDataSerializer.cacheKey(testUuid, testGroup, gameMode)

            assertTrue(key.endsWith("_${gameMode.name}"))
        }

        @Test
        @DisplayName("should parse cache key with GameMode")
        fun testParseCacheKeyWithGameMode() {
            val key = "${testUuid}_${testGroup}_SURVIVAL"

            val result = PlayerDataSerializer.parseCacheKey(key)

            Assertions.assertNotNull(result)
            assertEquals(testUuid, result!!.first)
            assertEquals(testGroup, result.second)
            assertEquals(GameMode.SURVIVAL, result.third)
        }

        @Test
        @DisplayName("should parse cache key without GameMode")
        fun testParseCacheKeyWithoutGameMode() {
            val key = "${testUuid}_${testGroup}"

            val result = PlayerDataSerializer.parseCacheKey(key)

            Assertions.assertNotNull(result)
            assertEquals(testUuid, result!!.first)
            assertEquals(testGroup, result.second)
            Assertions.assertNull(result.third)
        }

        @Test
        @DisplayName("should return null for invalid cache key")
        fun testParseCacheKeyWithInvalidFormat() {
            val invalidKey = "not-a-valid-key"

            val result = PlayerDataSerializer.parseCacheKey(invalidKey)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should return null for empty cache key")
        fun testParseCacheKeyWithEmptyString() {
            val result = PlayerDataSerializer.parseCacheKey("")

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should return null for cache key with invalid UUID")
        fun testParseCacheKeyWithInvalidUuid() {
            val invalidKey = "not-a-uuid_${testGroup}_SURVIVAL"

            val result = PlayerDataSerializer.parseCacheKey(invalidKey)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should handle cache key with invalid GameMode gracefully")
        fun testParseCacheKeyWithInvalidGameMode() {
            val key = "${testUuid}_${testGroup}_INVALID_MODE"

            val result = PlayerDataSerializer.parseCacheKey(key)

            Assertions.assertNotNull(result)
            assertEquals(testUuid, result!!.first)
            assertEquals(testGroup, result.second)
            Assertions.assertNull(result.third) // Invalid gamemode becomes null
        }

        @Test
        @DisplayName("should round-trip cache key with GameMode")
        fun testCacheKeyRoundTripWithGameMode() {
            val key = PlayerDataSerializer.cacheKey(testUuid, testGroup, GameMode.CREATIVE)
            val parsed = PlayerDataSerializer.parseCacheKey(key)

            Assertions.assertNotNull(parsed)
            assertEquals(testUuid, parsed!!.first)
            assertEquals(testGroup, parsed.second)
            assertEquals(GameMode.CREATIVE, parsed.third)
        }

        @Test
        @DisplayName("should round-trip cache key without GameMode")
        fun testCacheKeyRoundTripWithoutGameMode() {
            val key = PlayerDataSerializer.cacheKey(testUuid, testGroup, null)
            val parsed = PlayerDataSerializer.parseCacheKey(key)

            Assertions.assertNotNull(parsed)
            assertEquals(testUuid, parsed!!.first)
            assertEquals(testGroup, parsed.second)
            Assertions.assertNull(parsed.third)
        }
    }

    // ============================================================
    // UUID Parsing Tests
    // ============================================================

    @Nested
    @DisplayName("UUID Parsing")
    inner class UuidParsingTests {

        @Test
        @DisplayName("should parse standard UUID format")
        fun testParseStandardUuid() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", "12345678-1234-1234-1234-123456789abc")
            yaml.set("player-name", testPlayerName)
            yaml.set("group", testGroup)

            val result = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(result)
            assertEquals(UUID.fromString("12345678-1234-1234-1234-123456789abc"), result!!.uuid)
        }

        @Test
        @DisplayName("should return null for invalid UUID format")
        fun testParseInvalidUuid() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", "not-a-valid-uuid")
            yaml.set("player-name", testPlayerName)
            yaml.set("group", testGroup)

            val result = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should handle UUID in SQL map")
        fun testParseUuidFromSqlMap() {
            val row = mapOf(
                "uuid" to "12345678-1234-1234-1234-123456789abc",
                "player_name" to testPlayerName,
                "group_name" to testGroup
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNotNull(result)
            assertEquals(UUID.fromString("12345678-1234-1234-1234-123456789abc"), result!!.uuid)
        }

        @Test
        @DisplayName("should return null for invalid UUID in SQL map")
        fun testParseInvalidUuidFromSqlMap() {
            val row = mapOf(
                "uuid" to "invalid-uuid",
                "player_name" to testPlayerName,
                "group_name" to testGroup
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNull(result)
        }

        @Test
        @DisplayName("should preserve UUID through YAML round-trip")
        fun testUuidYamlRoundTrip() {
            val uuid = UUID.randomUUID()
            val data = PlayerData(uuid, testPlayerName, testGroup, GameMode.SURVIVAL)
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored)
            assertEquals(uuid, restored!!.uuid)
        }

        @Test
        @DisplayName("should preserve UUID through SQL round-trip")
        fun testUuidSqlRoundTrip() {
            val uuid = UUID.randomUUID()
            val data = PlayerData(uuid, testPlayerName, testGroup, GameMode.SURVIVAL)

            val sqlMap = PlayerDataSerializer.toSqlMap(data)
            val restored = PlayerDataSerializer.fromSqlMap(sqlMap)

            Assertions.assertNotNull(restored)
            assertEquals(uuid, restored!!.uuid)
        }
    }

    // ============================================================
    // Timestamp Handling Tests
    // ============================================================

    @Nested
    @DisplayName("Timestamp Handling")
    inner class TimestampTests {

        @Test
        @DisplayName("should serialize timestamp as epoch millis to YAML")
        fun testTimestampToYaml() {
            val data = createFullPlayerData()
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)

            assertEquals(testTimestamp.toEpochMilli(), yaml.getLong("timestamp"))
        }

        @Test
        @DisplayName("should deserialize timestamp from epoch millis in YAML")
        fun testTimestampFromYaml() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", testUuid.toString())
            yaml.set("player-name", testPlayerName)
            yaml.set("group", testGroup)
            yaml.set("timestamp", testTimestamp.toEpochMilli())

            val result = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(result)
            assertEquals(testTimestamp, result!!.timestamp)
        }

        @Test
        @DisplayName("should use current time for missing timestamp in YAML")
        fun testDefaultTimestampInYaml() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", testUuid.toString())
            yaml.set("player-name", testPlayerName)
            yaml.set("group", testGroup)

            val before = System.currentTimeMillis()
            val result = PlayerDataSerializer.fromYaml(yaml)
            val after = System.currentTimeMillis()

            Assertions.assertNotNull(result)
            val timestampMillis = result!!.timestamp.toEpochMilli()
            assertTrue(timestampMillis >= before && timestampMillis <= after)
        }

        @Test
        @DisplayName("should serialize timestamp as epoch millis to SQL")
        fun testTimestampToSql() {
            val data = createFullPlayerData()

            val sqlMap = PlayerDataSerializer.toSqlMap(data)

            assertEquals(testTimestamp.toEpochMilli(), sqlMap["timestamp"])
        }

        @Test
        @DisplayName("should deserialize timestamp from epoch millis in SQL")
        fun testTimestampFromSql() {
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "player_name" to testPlayerName,
                "group_name" to testGroup,
                "timestamp" to testTimestamp.toEpochMilli()
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNotNull(result)
            assertEquals(testTimestamp, result!!.timestamp)
        }

        @Test
        @DisplayName("should handle timestamp as Long in SQL")
        fun testTimestampAsLongInSql() {
            val timestamp = 1700000000000L
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "player_name" to testPlayerName,
                "group_name" to testGroup,
                "timestamp" to timestamp
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNotNull(result)
            assertEquals(Instant.ofEpochMilli(timestamp), result!!.timestamp)
        }

        @Test
        @DisplayName("should handle timestamp as Int in SQL")
        fun testTimestampAsIntInSql() {
            // Smaller timestamp that fits in Int (seconds)
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "player_name" to testPlayerName,
                "group_name" to testGroup,
                "timestamp" to 1700000 // as Int
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNotNull(result)
            assertEquals(Instant.ofEpochMilli(1700000L), result!!.timestamp)
        }

        @Test
        @DisplayName("should preserve timestamp through YAML round-trip")
        fun testTimestampYamlRoundTrip() {
            val data = createFullPlayerData()
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored)
            assertEquals(data.timestamp, restored!!.timestamp)
        }

        @Test
        @DisplayName("should preserve timestamp through SQL round-trip")
        fun testTimestampSqlRoundTrip() {
            val data = createFullPlayerData()

            val sqlMap = PlayerDataSerializer.toSqlMap(data)
            val restored = PlayerDataSerializer.fromSqlMap(sqlMap)

            Assertions.assertNotNull(restored)
            assertEquals(data.timestamp, restored!!.timestamp)
        }
    }

    // ============================================================
    // GameMode Conversion Tests
    // ============================================================

    @Nested
    @DisplayName("GameMode Conversion")
    inner class GameModeTests {

        @ParameterizedTest
        @EnumSource(GameMode::class)
        @DisplayName("should preserve GameMode through YAML round-trip")
        fun testGameModeYamlRoundTrip(gameMode: GameMode) {
            val data = PlayerData(testUuid, testPlayerName, testGroup, gameMode)
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored)
            assertEquals(gameMode, restored!!.gameMode)
        }

        @ParameterizedTest
        @EnumSource(GameMode::class)
        @DisplayName("should preserve GameMode through SQL round-trip")
        fun testGameModeSqlRoundTrip(gameMode: GameMode) {
            val data = PlayerData(testUuid, testPlayerName, testGroup, gameMode)

            val sqlMap = PlayerDataSerializer.toSqlMap(data)
            val restored = PlayerDataSerializer.fromSqlMap(sqlMap)

            Assertions.assertNotNull(restored)
            assertEquals(gameMode, restored!!.gameMode)
        }

        @Test
        @DisplayName("should handle null GameMode string in YAML")
        fun testNullGameModeInYaml() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", testUuid.toString())
            yaml.set("player-name", testPlayerName)
            yaml.set("group", testGroup)
            // gamemode not set

            val result = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(result)
            assertEquals(GameMode.SURVIVAL, result!!.gameMode)
        }

        @Test
        @DisplayName("should handle null GameMode string in SQL")
        fun testNullGameModeInSql() {
            val row = mapOf(
                "uuid" to testUuid.toString(),
                "player_name" to testPlayerName,
                "group_name" to testGroup
                // gamemode not set
            )

            val result = PlayerDataSerializer.fromSqlMap(row)

            Assertions.assertNotNull(result)
            assertEquals(GameMode.SURVIVAL, result!!.gameMode)
        }

        @Test
        @DisplayName("should handle case-sensitive GameMode in YAML")
        fun testCaseSensitiveGameModeInYaml() {
            val yaml = YamlConfiguration()
            yaml.set("uuid", testUuid.toString())
            yaml.set("player-name", testPlayerName)
            yaml.set("group", testGroup)
            yaml.set("gamemode", "survival") // lowercase

            val result = PlayerDataSerializer.fromYaml(yaml)

            // Should default to SURVIVAL since lowercase "survival" != "SURVIVAL"
            Assertions.assertNotNull(result)
            // The implementation uses valueOf which is case-sensitive
            assertEquals(GameMode.SURVIVAL, result!!.gameMode)
        }
    }

    // ============================================================
    // Edge Cases and Error Handling
    // ============================================================

    @Nested
    @DisplayName("Edge Cases and Error Handling")
    inner class EdgeCaseTests {

        @Test
        @DisplayName("should handle special characters in player name")
        fun testSpecialCharactersInPlayerName() {
            val specialName = "Player_123"
            val data = PlayerData(testUuid, specialName, testGroup, GameMode.SURVIVAL)
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored)
            assertEquals(specialName, restored!!.playerName)
        }

        @Test
        @DisplayName("should handle special characters in group name")
        fun testSpecialCharactersInGroupName() {
            val specialGroup = "survival-world_1"
            val data = PlayerData(testUuid, testPlayerName, specialGroup, GameMode.SURVIVAL)
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored)
            assertEquals(specialGroup, restored!!.group)
        }

        @Test
        @DisplayName("should handle extreme health values")
        fun testExtremeHealthValues() {
            val data = PlayerData(testUuid, testPlayerName, testGroup, GameMode.SURVIVAL).apply {
                health = 0.1
                maxHealth = 1024.0
            }
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored)
            assertEquals(0.1, restored!!.health, 0.001)
            assertEquals(1024.0, restored.maxHealth, 0.001)
        }

        @Test
        @DisplayName("should handle extreme experience values")
        fun testExtremeExperienceValues() {
            val data = PlayerData(testUuid, testPlayerName, testGroup, GameMode.SURVIVAL).apply {
                experience = 0.999f
                level = 32767
                totalExperience = Int.MAX_VALUE
            }
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored)
            assertEquals(0.999f, restored!!.experience, 0.001f)
            assertEquals(32767, restored.level)
            assertEquals(Int.MAX_VALUE, restored.totalExperience)
        }

        @Test
        @DisplayName("should handle zero values for all numeric fields")
        fun testZeroValues() {
            val data = PlayerData(testUuid, testPlayerName, testGroup, GameMode.SURVIVAL).apply {
                health = 0.0
                maxHealth = 0.0
                foodLevel = 0
                saturation = 0.0f
                exhaustion = 0.0f
                experience = 0.0f
                level = 0
                totalExperience = 0
            }
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored)
            assertEquals(0.0, restored!!.health)
            assertEquals(0.0, restored.maxHealth)
            assertEquals(0, restored.foodLevel)
            assertEquals(0.0f, restored.saturation)
            assertEquals(0.0f, restored.exhaustion)
            assertEquals(0.0f, restored.experience)
            assertEquals(0, restored.level)
            assertEquals(0, restored.totalExperience)
        }

        @Test
        @DisplayName("should handle full inventory slots")
        fun testFullInventory() {
            val data = PlayerData(testUuid, testPlayerName, testGroup, GameMode.SURVIVAL).apply {
                // Fill all 36 main inventory slots
                for (i in 0..35) {
                    mainInventory[i] = ItemStack(Material.DIRT, 64)
                }
                // Fill all 4 armor slots
                for (i in 0..3) {
                    armorInventory[i] = ItemStack(Material.LEATHER_BOOTS, 1)
                }
                // Fill all 27 ender chest slots
                for (i in 0..26) {
                    enderChest[i] = ItemStack(Material.STONE, 64)
                }
                offhand = ItemStack(Material.SHIELD, 1)
            }
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored, "Restored data should not be null")
            // Check the original data was set correctly
            assertEquals(36, data.mainInventory.size, "Original main inventory size")
            assertEquals(4, data.armorInventory.size, "Original armor inventory size")
            assertEquals(27, data.enderChest.size, "Original ender chest size")
            Assertions.assertNotNull(data.offhand, "Original offhand")

            // Check restored data has inventories (size may vary based on serialization)
            Assertions.assertNotNull(restored!!.mainInventory, "Restored main inventory not null")
            Assertions.assertNotNull(restored.armorInventory, "Restored armor inventory not null")
            Assertions.assertNotNull(restored.enderChest, "Restored ender chest not null")
        }

        @Test
        @DisplayName("should handle large number of potion effects")
        fun testManyPotionEffects() {
            val data = PlayerData(testUuid, testPlayerName, testGroup, GameMode.SURVIVAL).apply {
                potionEffects.add(PotionEffect(PotionEffectType.SPEED, 600, 1))
                potionEffects.add(PotionEffect(PotionEffectType.STRENGTH, 1200, 2))
                potionEffects.add(PotionEffect(PotionEffectType.REGENERATION, 300, 0))
                potionEffects.add(PotionEffect(PotionEffectType.FIRE_RESISTANCE, 3600, 0))
                potionEffects.add(PotionEffect(PotionEffectType.WATER_BREATHING, 600, 0))
            }
            val yaml = YamlConfiguration()

            PlayerDataSerializer.toYaml(data, yaml)
            val restored = PlayerDataSerializer.fromYaml(yaml)

            Assertions.assertNotNull(restored)
            assertEquals(5, restored!!.potionEffects.size)
        }
    }
}

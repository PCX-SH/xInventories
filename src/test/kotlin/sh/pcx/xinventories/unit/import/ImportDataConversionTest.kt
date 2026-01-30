package sh.pcx.xinventories.unit.import

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.internal.model.ImportGroup
import sh.pcx.xinventories.internal.model.ImportMapping
import sh.pcx.xinventories.internal.model.ImportOptions
import sh.pcx.xinventories.internal.model.ImportedPlayerData
import java.time.Instant
import java.util.UUID

@DisplayName("Import Data Conversion Tests")
class ImportDataConversionTest {

    private lateinit var server: ServerMock

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Nested
    @DisplayName("ImportGroup Tests")
    inner class ImportGroupTests {

        @Test
        @DisplayName("should create import group with worlds")
        fun createImportGroupWithWorlds() {
            val group = ImportGroup(
                name = "survival",
                worlds = setOf("world", "world_nether", "world_the_end"),
                isDefault = true
            )

            assertEquals("survival", group.name)
            assertEquals(3, group.worlds.size)
            assertTrue(group.isDefault)
            assertTrue(group.containsWorld("world"))
            assertTrue(group.containsWorld("world_nether"))
            assertFalse(group.containsWorld("creative"))
        }

        @Test
        @DisplayName("should generate display string")
        fun generateDisplayString() {
            val group = ImportGroup(
                name = "creative",
                worlds = setOf("creative_world"),
                isDefault = false
            )

            val display = group.toDisplayString()

            assertTrue(display.contains("creative"))
            assertTrue(display.contains("creative_world"))
            assertFalse(display.contains("(default)"))
        }

        @Test
        @DisplayName("should show default in display string")
        fun showDefaultInDisplayString() {
            val group = ImportGroup(
                name = "default",
                worlds = emptySet(),
                isDefault = true
            )

            val display = group.toDisplayString()

            assertTrue(display.contains("(default)"))
        }
    }

    @Nested
    @DisplayName("ImportMapping Tests")
    inner class ImportMappingTests {

        @Test
        @DisplayName("should create identity mapping")
        fun createIdentityMapping() {
            val groups = listOf(
                ImportGroup("survival", setOf("world")),
                ImportGroup("creative", setOf("creative"))
            )

            val mapping = ImportMapping.identity("pwi", groups)

            assertEquals("pwi", mapping.source)
            assertEquals("survival", mapping.getTargetGroup("survival"))
            assertEquals("creative", mapping.getTargetGroup("creative"))
        }

        @Test
        @DisplayName("should return source group for unmapped groups")
        fun returnSourceGroupForUnmapped() {
            val mapping = ImportMapping(
                source = "pwi",
                groupMappings = mapOf("survival" to "main")
            )

            assertEquals("main", mapping.getTargetGroup("survival"))
            assertEquals("unmapped", mapping.getTargetGroup("unmapped"))
        }

        @Test
        @DisplayName("should track mapped source groups")
        fun trackMappedSourceGroups() {
            val mapping = ImportMapping(
                source = "pwi",
                groupMappings = mapOf(
                    "survival" to "main",
                    "creative" to "build"
                )
            )

            assertTrue(mapping.hasMappingFor("survival"))
            assertTrue(mapping.hasMappingFor("creative"))
            assertFalse(mapping.hasMappingFor("minigames"))

            assertEquals(setOf("survival", "creative"), mapping.getMappedSourceGroups())
            assertEquals(setOf("main", "build"), mapping.getTargetGroups())
        }

        @Test
        @DisplayName("should create empty mapping")
        fun createEmptyMapping() {
            val mapping = ImportMapping.empty("mvi")

            assertEquals("mvi", mapping.source)
            assertTrue(mapping.groupMappings.isEmpty())
        }
    }

    @Nested
    @DisplayName("ImportOptions Tests")
    inner class ImportOptionsTests {

        @Test
        @DisplayName("should have sensible defaults")
        fun haveSensibleDefaults() {
            val options = ImportOptions()

            assertFalse(options.overwriteExisting)
            assertTrue(options.importBalances)
            assertFalse(options.deleteSourceAfter)
            assertTrue(options.useApiWhenAvailable)
            assertTrue(options.createMissingGroups)
            assertTrue(options.importOfflinePlayers)
            assertEquals(100, options.batchSize)
            assertFalse(options.dryRun)
        }

        @Test
        @DisplayName("should allow custom options")
        fun allowCustomOptions() {
            val options = ImportOptions(
                overwriteExisting = true,
                importBalances = false,
                batchSize = 50,
                dryRun = true
            )

            assertTrue(options.overwriteExisting)
            assertFalse(options.importBalances)
            assertEquals(50, options.batchSize)
            assertTrue(options.dryRun)
        }
    }

    @Nested
    @DisplayName("ImportedPlayerData Tests")
    inner class ImportedPlayerDataTests {

        private val testUuid = UUID.randomUUID()

        @Test
        @DisplayName("should create empty imported player data")
        fun createEmptyImportedPlayerData() {
            val data = ImportedPlayerData.empty(
                uuid = testUuid,
                playerName = "TestPlayer",
                sourceGroup = "survival",
                sourceId = "pwi",
                gameMode = GameMode.SURVIVAL
            )

            assertEquals(testUuid, data.uuid)
            assertEquals("TestPlayer", data.playerName)
            assertEquals("survival", data.sourceGroup)
            assertEquals("pwi", data.sourceId)
            assertEquals(GameMode.SURVIVAL, data.gameMode)
            assertTrue(data.mainInventory.isEmpty())
            assertTrue(data.armorInventory.isEmpty())
            Assertions.assertNull(data.offhand)
            assertEquals(20.0, data.health)
            assertEquals(20, data.foodLevel)
        }

        @Test
        @DisplayName("should convert to PlayerData")
        fun convertToPlayerData() {
            val data = ImportedPlayerData(
                uuid = testUuid,
                playerName = "TestPlayer",
                sourceGroup = "old_survival",
                gameMode = GameMode.SURVIVAL,
                mainInventory = mapOf(0 to ItemStack(Material.DIAMOND, 64)),
                armorInventory = mapOf(3 to ItemStack(Material.DIAMOND_HELMET)),
                offhand = ItemStack(Material.SHIELD),
                enderChest = mapOf(0 to ItemStack(Material.GOLD_INGOT, 32)),
                health = 15.0,
                maxHealth = 20.0,
                foodLevel = 18,
                saturation = 4.5f,
                exhaustion = 1.0f,
                experience = 0.5f,
                level = 30,
                totalExperience = 1395,
                potionEffects = listOf(PotionEffect(PotionEffectType.SPEED, 600, 1)),
                balance = 1000.0,
                sourceTimestamp = Instant.now(),
                sourceId = "pwi"
            )

            val playerData = data.toPlayerData("new_survival")

            assertEquals(testUuid, playerData.uuid)
            assertEquals("TestPlayer", playerData.playerName)
            assertEquals("new_survival", playerData.group)
            assertEquals(GameMode.SURVIVAL, playerData.gameMode)

            // Check inventory
            assertEquals(1, playerData.mainInventory.size)
            assertEquals(Material.DIAMOND, playerData.mainInventory[0]?.type)
            assertEquals(64, playerData.mainInventory[0]?.amount)

            // Check armor
            assertEquals(1, playerData.armorInventory.size)
            assertEquals(Material.DIAMOND_HELMET, playerData.armorInventory[3]?.type)

            // Check offhand
            assertEquals(Material.SHIELD, playerData.offhand?.type)

            // Check ender chest
            assertEquals(1, playerData.enderChest.size)
            assertEquals(Material.GOLD_INGOT, playerData.enderChest[0]?.type)

            // Check stats
            assertEquals(15.0, playerData.health)
            assertEquals(20.0, playerData.maxHealth)
            assertEquals(18, playerData.foodLevel)
            assertEquals(4.5f, playerData.saturation)
            assertEquals(1.0f, playerData.exhaustion)
            assertEquals(0.5f, playerData.experience)
            assertEquals(30, playerData.level)
            assertEquals(1395, playerData.totalExperience)

            // Check potion effects
            assertEquals(1, playerData.potionEffects.size)
            assertEquals(PotionEffectType.SPEED, playerData.potionEffects[0].type)
        }

        @Test
        @DisplayName("should clone items when converting")
        fun cloneItemsWhenConverting() {
            val originalItem = ItemStack(Material.DIAMOND, 64)
            val data = ImportedPlayerData(
                uuid = testUuid,
                playerName = "TestPlayer",
                sourceGroup = "survival",
                gameMode = GameMode.SURVIVAL,
                mainInventory = mapOf(0 to originalItem),
                armorInventory = emptyMap(),
                offhand = null,
                enderChest = emptyMap(),
                health = 20.0,
                maxHealth = 20.0,
                foodLevel = 20,
                saturation = 5.0f,
                exhaustion = 0.0f,
                experience = 0.0f,
                level = 0,
                totalExperience = 0,
                potionEffects = emptyList(),
                balance = null,
                sourceTimestamp = null,
                sourceId = "pwi"
            )

            val playerData = data.toPlayerData("survival")

            // Modify original
            originalItem.amount = 1

            // PlayerData should have original amount
            assertEquals(64, playerData.mainInventory[0]?.amount)
        }

        @Test
        @DisplayName("should generate inventory summary")
        fun generateInventorySummary() {
            val data = ImportedPlayerData(
                uuid = testUuid,
                playerName = "TestPlayer",
                sourceGroup = "survival",
                gameMode = GameMode.SURVIVAL,
                mainInventory = mapOf(0 to ItemStack(Material.DIAMOND)),
                armorInventory = mapOf(3 to ItemStack(Material.DIAMOND_HELMET)),
                offhand = ItemStack(Material.SHIELD),
                enderChest = mapOf(0 to ItemStack(Material.GOLD_INGOT)),
                health = 20.0,
                maxHealth = 20.0,
                foodLevel = 20,
                saturation = 5.0f,
                exhaustion = 0.0f,
                experience = 0.0f,
                level = 30,
                totalExperience = 0,
                potionEffects = emptyList(),
                balance = 1500.50,
                sourceTimestamp = null,
                sourceId = "pwi"
            )

            val summary = data.getInventorySummary()

            assertTrue(summary.contains("4 items"))
            assertTrue(summary.contains("1500.50"))
            assertTrue(summary.contains("level: 30"))
        }

        @Test
        @DisplayName("should handle null balance in summary")
        fun handleNullBalanceInSummary() {
            val data = ImportedPlayerData.empty(
                uuid = testUuid,
                playerName = "TestPlayer",
                sourceGroup = "survival",
                sourceId = "pwi"
            )

            val summary = data.getInventorySummary()

            assertFalse(summary.contains("balance"))
        }
    }

    @Nested
    @DisplayName("ImportedPlayerData Edge Cases")
    inner class ImportedPlayerDataEdgeCasesTests {

        @Test
        @DisplayName("should handle empty inventories")
        fun handleEmptyInventories() {
            val data = ImportedPlayerData.empty(
                uuid = UUID.randomUUID(),
                playerName = "Empty",
                sourceGroup = "survival",
                sourceId = "pwi"
            )

            val playerData = data.toPlayerData("survival")

            assertTrue(playerData.mainInventory.isEmpty())
            assertTrue(playerData.armorInventory.isEmpty())
            assertTrue(playerData.enderChest.isEmpty())
            Assertions.assertNull(playerData.offhand)
        }

        @Test
        @DisplayName("should preserve source timestamp")
        fun preserveSourceTimestamp() {
            val timestamp = Instant.now().minusSeconds(3600)
            val data = ImportedPlayerData(
                uuid = UUID.randomUUID(),
                playerName = "Test",
                sourceGroup = "survival",
                gameMode = GameMode.SURVIVAL,
                mainInventory = emptyMap(),
                armorInventory = emptyMap(),
                offhand = null,
                enderChest = emptyMap(),
                health = 20.0,
                maxHealth = 20.0,
                foodLevel = 20,
                saturation = 5.0f,
                exhaustion = 0.0f,
                experience = 0.0f,
                level = 0,
                totalExperience = 0,
                potionEffects = emptyList(),
                balance = null,
                sourceTimestamp = timestamp,
                sourceId = "pwi"
            )

            val playerData = data.toPlayerData("survival")

            assertEquals(timestamp, playerData.timestamp)
        }

        @Test
        @DisplayName("should set current timestamp when source is null")
        fun setCurrentTimestampWhenSourceIsNull() {
            val before = Instant.now()

            val data = ImportedPlayerData.empty(
                uuid = UUID.randomUUID(),
                playerName = "Test",
                sourceGroup = "survival",
                sourceId = "pwi"
            )

            val playerData = data.toPlayerData("survival")

            val after = Instant.now()
            assertTrue(playerData.timestamp >= before)
            assertTrue(playerData.timestamp <= after)
        }

        @Test
        @DisplayName("should use SURVIVAL when gameMode is null")
        fun useSurvivalWhenGameModeIsNull() {
            val data = ImportedPlayerData(
                uuid = UUID.randomUUID(),
                playerName = "Test",
                sourceGroup = "survival",
                gameMode = null,
                mainInventory = emptyMap(),
                armorInventory = emptyMap(),
                offhand = null,
                enderChest = emptyMap(),
                health = 20.0,
                maxHealth = 20.0,
                foodLevel = 20,
                saturation = 5.0f,
                exhaustion = 0.0f,
                experience = 0.0f,
                level = 0,
                totalExperience = 0,
                potionEffects = emptyList(),
                balance = null,
                sourceTimestamp = null,
                sourceId = "pwi"
            )

            val playerData = data.toPlayerData("survival")

            assertEquals(GameMode.SURVIVAL, playerData.gameMode)
        }
    }
}

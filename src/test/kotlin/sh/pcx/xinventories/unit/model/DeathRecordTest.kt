package sh.pcx.xinventories.unit.model

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.PlayerData
import java.time.Instant
import java.util.UUID

@DisplayName("DeathRecord Tests")
class DeathRecordTest {

    private lateinit var server: ServerMock
    private lateinit var playerData: PlayerData
    private val testUuid = UUID.randomUUID()
    private val killerUuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()

        playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)
        playerData.mainInventory[0] = ItemStack(Material.DIAMOND_SWORD)
        playerData.mainInventory[1] = ItemStack(Material.GOLDEN_APPLE, 5)
        playerData.armorInventory[3] = ItemStack(Material.DIAMOND_HELMET)
        playerData.health = 0.0 // They died!
        playerData.level = 30
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Nested
    @DisplayName("Death Record Creation")
    inner class DeathRecordCreationTests {

        @Test
        @DisplayName("should create death record with unique ID")
        fun createWithUniqueId() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 100.0, 64.0, 200.0)

            val record1 = DeathRecord.create(playerData, location, "FALL", null, null)
            val record2 = DeathRecord.create(playerData, location, "FALL", null, null)

            assertNotEquals(record1.id, record2.id)
            assertEquals(36, record1.id.length) // UUID format
        }

        @Test
        @DisplayName("should store player UUID")
        fun storePlayerUuid() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 100.0, 64.0, 200.0)

            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            assertEquals(testUuid, record.playerUuid)
        }

        @Test
        @DisplayName("should store death location")
        fun storeDeathLocation() {
            val world = server.addSimpleWorld("test_world")
            val location = Location(world, 123.5, 64.0, 456.7)

            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            assertEquals("test_world", record.world)
            assertEquals(123.5, record.x)
            assertEquals(64.0, record.y)
            assertEquals(456.7, record.z)
        }

        @Test
        @DisplayName("should store death cause")
        fun storeDeathCause() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "ENTITY_ATTACK", null, null)

            assertEquals("ENTITY_ATTACK", record.deathCause)
        }

        @Test
        @DisplayName("should store killer information")
        fun storeKillerInfo() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "ENTITY_ATTACK", "EvilPlayer", killerUuid)

            assertEquals("EvilPlayer", record.killerName)
            assertEquals(killerUuid, record.killerUuid)
        }

        @Test
        @DisplayName("should store null killer for environmental deaths")
        fun storeNullKiller() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            Assertions.assertNull(record.killerName)
            Assertions.assertNull(record.killerUuid)
        }

        @Test
        @DisplayName("should store group and game mode")
        fun storeGroupAndGameMode() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            assertEquals("survival", record.group)
            assertEquals(GameMode.SURVIVAL, record.gameMode)
        }

        @Test
        @DisplayName("should store inventory data")
        fun storeInventoryData() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            assertEquals(2, record.inventoryData.mainInventory.size)
            assertEquals(Material.DIAMOND_SWORD, record.inventoryData.mainInventory[0]?.type)
            assertEquals(30, record.inventoryData.level)
        }

        @Test
        @DisplayName("should set timestamp to now")
        fun setTimestampToNow() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val before = Instant.now()
            val record = DeathRecord.create(playerData, location, "FALL", null, null)
            val after = Instant.now()

            assertTrue(record.timestamp >= before)
            assertTrue(record.timestamp <= after)
        }
    }

    @Nested
    @DisplayName("Death Record from Storage")
    inner class FromStorageTests {

        @Test
        @DisplayName("should reconstruct death record from storage data")
        fun reconstructFromStorage() {
            val id = UUID.randomUUID().toString()
            val timestamp = Instant.now().minusSeconds(3600)

            val record = DeathRecord.fromStorage(
                id = id,
                playerUuid = testUuid,
                timestamp = timestamp,
                world = "nether",
                x = 50.0,
                y = 30.0,
                z = -100.0,
                deathCause = "LAVA",
                killerName = null,
                killerUuid = null,
                group = "nether_group",
                gameMode = GameMode.SURVIVAL,
                inventoryData = playerData
            )

            assertEquals(id, record.id)
            assertEquals(testUuid, record.playerUuid)
            assertEquals(timestamp, record.timestamp)
            assertEquals("nether", record.world)
            assertEquals(50.0, record.x)
            assertEquals(30.0, record.y)
            assertEquals(-100.0, record.z)
            assertEquals("LAVA", record.deathCause)
            Assertions.assertNull(record.killerName)
            assertEquals("nether_group", record.group)
        }
    }

    @Nested
    @DisplayName("Location String")
    inner class LocationStringTests {

        @Test
        @DisplayName("should format location string correctly")
        fun formatLocationString() {
            val world = server.addSimpleWorld("test_world")
            val location = Location(world, 123.7, 64.2, -456.9)

            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            assertEquals("test_world (123, 64, -456)", record.getLocationString())
        }
    }

    @Nested
    @DisplayName("Death Description")
    inner class DeathDescriptionTests {

        @Test
        @DisplayName("should describe player kill with UUID")
        fun describePlayerKill() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "ENTITY_ATTACK", "EvilPlayer", killerUuid)

            assertEquals("Killed by player EvilPlayer", record.getDeathDescription())
        }

        @Test
        @DisplayName("should describe entity kill without UUID")
        fun describeEntityKill() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "ENTITY_ATTACK", "Zombie", null)

            assertEquals("Killed by Zombie", record.getDeathDescription())
        }

        @Test
        @DisplayName("should format death cause nicely")
        fun formatDeathCause() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            assertEquals("Fall", record.getDeathDescription())
        }

        @Test
        @DisplayName("should handle multi-word death causes")
        fun handleMultiWordCause() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "BLOCK_EXPLOSION", null, null)

            assertEquals("Block explosion", record.getDeathDescription())
        }

        @Test
        @DisplayName("should return unknown for null cause")
        fun handleNullCause() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, null, null, null)

            assertEquals("Unknown cause", record.getDeathDescription())
        }
    }

    @Nested
    @DisplayName("Relative Time Description")
    inner class RelativeTimeDescriptionTests {

        @Test
        @DisplayName("should return 'just now' for recent deaths")
        fun justNow() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            assertEquals("just now", record.getRelativeTimeDescription())
        }

        @Test
        @DisplayName("should return minutes ago")
        fun minutesAgo() {
            val record = DeathRecord.fromStorage(
                id = UUID.randomUUID().toString(),
                playerUuid = testUuid,
                timestamp = Instant.now().minusSeconds(300), // 5 minutes
                world = "world",
                x = 0.0, y = 0.0, z = 0.0,
                deathCause = "FALL",
                killerName = null,
                killerUuid = null,
                group = "default",
                gameMode = GameMode.SURVIVAL,
                inventoryData = playerData
            )

            assertEquals("5 minutes ago", record.getRelativeTimeDescription())
        }
    }

    @Nested
    @DisplayName("Item Summary")
    inner class ItemSummaryTests {

        @Test
        @DisplayName("should return 'empty inventory' for no items")
        fun emptyInventory() {
            val emptyData = PlayerData.empty(testUuid, "Test", "default", GameMode.SURVIVAL)
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(emptyData, location, "FALL", null, null)

            assertEquals("empty inventory", record.getItemSummary())
        }

        @Test
        @DisplayName("should count items correctly")
        fun countItems() {
            val world = server.addSimpleWorld("world")
            val location = Location(world, 0.0, 0.0, 0.0)

            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            assertTrue(record.getItemSummary().contains("2 items"))
            assertTrue(record.getItemSummary().contains("1 armor"))
        }
    }
}

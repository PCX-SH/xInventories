package sh.pcx.xinventories.integration.storage

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
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@DisplayName("Death Storage Integration Tests")
class DeathStorageTest {

    private lateinit var server: ServerMock
    private lateinit var tempDir: File
    private val testUuid = UUID.randomUUID()
    private val killerUuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        tempDir = File(System.getProperty("java.io.tmpdir"), "xinv-death-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
        tempDir.deleteRecursively()
    }

    @Test
    @DisplayName("Test DeathRecord data model creation")
    fun testDeathRecordCreation() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)
        playerData.mainInventory[0] = ItemStack(Material.DIAMOND_SWORD)
        playerData.mainInventory[1] = ItemStack(Material.GOLDEN_APPLE, 5)
        playerData.level = 30

        val world = server.addSimpleWorld("test_world")
        val location = Location(world, 100.5, 64.0, 200.5)

        val deathRecord = DeathRecord.create(
            playerData,
            location,
            "ENTITY_ATTACK",
            "Zombie",
            null
        )

        Assertions.assertNotNull(deathRecord.id)
        assertEquals(testUuid, deathRecord.playerUuid)
        assertEquals("test_world", deathRecord.world)
        assertEquals(100.5, deathRecord.x)
        assertEquals(64.0, deathRecord.y)
        assertEquals(200.5, deathRecord.z)
        assertEquals("ENTITY_ATTACK", deathRecord.deathCause)
        assertEquals("Zombie", deathRecord.killerName)
        Assertions.assertNull(deathRecord.killerUuid)
        assertEquals("survival", deathRecord.group)
        assertEquals(GameMode.SURVIVAL, deathRecord.gameMode)
        assertEquals(2, deathRecord.inventoryData.mainInventory.size)
        assertEquals(30, deathRecord.inventoryData.level)
    }

    @Test
    @DisplayName("Test DeathRecord with player killer")
    fun testDeathRecordWithPlayerKiller() {
        val playerData = PlayerData.empty(testUuid, "Victim", "pvp", GameMode.SURVIVAL)
        val world = server.addSimpleWorld("pvp_arena")
        val location = Location(world, 50.0, 65.0, 50.0)

        val deathRecord = DeathRecord.create(
            playerData,
            location,
            "ENTITY_ATTACK",
            "EvilPlayer",
            killerUuid
        )

        assertEquals("EvilPlayer", deathRecord.killerName)
        assertEquals(killerUuid, deathRecord.killerUuid)
        assertEquals("Killed by player EvilPlayer", deathRecord.getDeathDescription())
    }

    @Test
    @DisplayName("Test DeathRecord serialization round-trip")
    fun testDeathRecordRoundTrip() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "creative", GameMode.CREATIVE)
        playerData.mainInventory[5] = ItemStack(Material.GOLDEN_APPLE, 32)
        playerData.armorInventory[3] = ItemStack(Material.DIAMOND_HELMET)
        playerData.offhand = ItemStack(Material.SHIELD)
        playerData.health = 0.0 // Dead
        playerData.level = 50

        val world = server.addSimpleWorld("nether")
        val location = Location(world, 10.0, 30.0, -100.0)

        val original = DeathRecord.create(playerData, location, "LAVA", null, null)

        // Simulate storage round-trip using fromStorage
        val reconstructed = DeathRecord.fromStorage(
            id = original.id,
            playerUuid = original.playerUuid,
            timestamp = original.timestamp,
            world = original.world,
            x = original.x,
            y = original.y,
            z = original.z,
            deathCause = original.deathCause,
            killerName = original.killerName,
            killerUuid = original.killerUuid,
            group = original.group,
            gameMode = original.gameMode,
            inventoryData = original.inventoryData
        )

        assertEquals(original.id, reconstructed.id)
        assertEquals(original.playerUuid, reconstructed.playerUuid)
        assertEquals(original.timestamp, reconstructed.timestamp)
        assertEquals(original.world, reconstructed.world)
        assertEquals(original.x, reconstructed.x)
        assertEquals(original.y, reconstructed.y)
        assertEquals(original.z, reconstructed.z)
        assertEquals(original.deathCause, reconstructed.deathCause)
        assertEquals(original.killerName, reconstructed.killerName)
        assertEquals(original.killerUuid, reconstructed.killerUuid)
        assertEquals(original.group, reconstructed.group)
        assertEquals(original.gameMode, reconstructed.gameMode)
        assertEquals(original.inventoryData.mainInventory.size, reconstructed.inventoryData.mainInventory.size)
    }

    @Test
    @DisplayName("Test death record timestamp for pruning")
    fun testDeathRecordTimestampForPruning() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)
        val world = server.addSimpleWorld("world")
        val location = Location(world, 0.0, 0.0, 0.0)

        // Create recent death
        val recentDeath = DeathRecord.create(playerData, location, "FALL", null, null)

        // Create old death
        val oldTimestamp = Instant.now().minus(5, ChronoUnit.DAYS)
        val oldDeath = DeathRecord.fromStorage(
            id = UUID.randomUUID().toString(),
            playerUuid = testUuid,
            timestamp = oldTimestamp,
            world = "world",
            x = 0.0, y = 0.0, z = 0.0,
            deathCause = "FALL",
            killerName = null,
            killerUuid = null,
            group = "survival",
            gameMode = GameMode.SURVIVAL,
            inventoryData = playerData
        )

        val pruneCutoff = Instant.now().minus(3, ChronoUnit.DAYS)

        // Recent death should NOT be pruned
        assertTrue(recentDeath.timestamp.isAfter(pruneCutoff))

        // Old death SHOULD be pruned
        assertTrue(oldDeath.timestamp.isBefore(pruneCutoff))
    }

    @Test
    @DisplayName("Test death record ordering by timestamp")
    fun testDeathRecordOrdering() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)

        val deaths = listOf(
            DeathRecord.fromStorage(
                UUID.randomUUID().toString(), testUuid,
                Instant.now().minus(1, ChronoUnit.HOURS),
                "world", 0.0, 0.0, 0.0, "FALL", null, null,
                "survival", GameMode.SURVIVAL, playerData
            ),
            DeathRecord.fromStorage(
                UUID.randomUUID().toString(), testUuid,
                Instant.now().minus(3, ChronoUnit.HOURS),
                "world", 0.0, 0.0, 0.0, "LAVA", null, null,
                "survival", GameMode.SURVIVAL, playerData
            ),
            DeathRecord.fromStorage(
                UUID.randomUUID().toString(), testUuid,
                Instant.now().minus(2, ChronoUnit.HOURS),
                "world", 0.0, 0.0, 0.0, "DROWNING", null, null,
                "survival", GameMode.SURVIVAL, playerData
            )
        )

        // Sort by timestamp descending (most recent first)
        val sorted = deaths.sortedByDescending { it.timestamp }

        assertTrue(sorted[0].timestamp.isAfter(sorted[1].timestamp))
        assertTrue(sorted[1].timestamp.isAfter(sorted[2].timestamp))

        // Verify death causes are in expected order
        assertEquals("FALL", sorted[0].deathCause) // 1 hour ago
        assertEquals("DROWNING", sorted[1].deathCause) // 2 hours ago
        assertEquals("LAVA", sorted[2].deathCause) // 3 hours ago
    }

    @Test
    @DisplayName("Test death cause formatting")
    fun testDeathCauseFormatting() {
        val testCases = mapOf(
            "FALL" to "Fall",
            "LAVA" to "Lava",
            "DROWNING" to "Drowning",
            "ENTITY_ATTACK" to "Entity attack",
            "BLOCK_EXPLOSION" to "Block explosion",
            "FIRE_TICK" to "Fire tick"
        )

        val playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)
        val world = server.addSimpleWorld("world")
        val location = Location(world, 0.0, 0.0, 0.0)

        testCases.forEach { (cause, expected) ->
            val death = DeathRecord.create(playerData, location, cause, null, null)
            assertEquals(expected, death.getDeathDescription())
        }
    }

    @Test
    @DisplayName("Test location string formatting")
    fun testLocationStringFormatting() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)
        val world = server.addSimpleWorld("test_world")

        val testCases = listOf(
            Triple(100.7, 64.2, 200.9) to "test_world (100, 64, 200)",
            Triple(-50.3, 30.8, -150.1) to "test_world (-50, 30, -150)",
            Triple(0.0, 0.0, 0.0) to "test_world (0, 0, 0)"
        )

        testCases.forEach { (coords, expected) ->
            val location = Location(world, coords.first, coords.second, coords.third)
            val death = DeathRecord.create(playerData, location, "FALL", null, null)
            assertEquals(expected, death.getLocationString())
        }
    }

    @Test
    @DisplayName("Test item summary")
    fun testItemSummary() {
        val world = server.addSimpleWorld("world")
        val location = Location(world, 0.0, 0.0, 0.0)

        // Empty inventory
        val emptyData = PlayerData.empty(testUuid, "Test", "survival", GameMode.SURVIVAL)
        val emptyDeath = DeathRecord.create(emptyData, location, "FALL", null, null)
        assertEquals("empty inventory", emptyDeath.getItemSummary())

        // Full inventory
        val fullData = PlayerData.empty(testUuid, "Test", "survival", GameMode.SURVIVAL)
        fullData.mainInventory[0] = ItemStack(Material.DIAMOND, 64)
        fullData.mainInventory[1] = ItemStack(Material.IRON_INGOT, 32)
        fullData.armorInventory[3] = ItemStack(Material.DIAMOND_HELMET)
        fullData.offhand = ItemStack(Material.SHIELD)
        fullData.enderChest[0] = ItemStack(Material.GOLD_INGOT)

        val fullDeath = DeathRecord.create(fullData, location, "FALL", null, null)
        val summary = fullDeath.getItemSummary()

        assertTrue(summary.contains("2 items"))
        assertTrue(summary.contains("1 armor"))
        assertTrue(summary.contains("offhand"))
        assertTrue(summary.contains("1 ender chest"))
    }
}

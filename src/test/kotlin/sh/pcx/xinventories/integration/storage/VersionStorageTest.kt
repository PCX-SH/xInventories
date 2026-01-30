package sh.pcx.xinventories.integration.storage

import kotlinx.coroutines.runBlocking
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.VersionTrigger
import sh.pcx.xinventories.internal.storage.SqliteStorage
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@DisplayName("Version Storage Integration Tests")
class VersionStorageTest {

    private lateinit var server: ServerMock
    private lateinit var tempDir: File
    private lateinit var storage: SqliteStorage
    private val testUuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
        tempDir = File(System.getProperty("java.io.tmpdir"), "xinv-test-${System.currentTimeMillis()}")
        tempDir.mkdirs()

        // Note: We can't easily test SqliteStorage without the full plugin context
        // This test serves as a template for when full integration tests are run
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
        tempDir.deleteRecursively()
    }

    @Test
    @DisplayName("Test InventoryVersion data model creation")
    fun testVersionCreation() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)
        playerData.mainInventory[0] = ItemStack(Material.DIAMOND, 64)
        playerData.level = 30

        val version = InventoryVersion.create(
            playerData,
            VersionTrigger.MANUAL,
            mapOf("world" to "test_world", "reason" to "test")
        )

        Assertions.assertNotNull(version.id)
        assertEquals(testUuid, version.playerUuid)
        assertEquals("survival", version.group)
        assertEquals(GameMode.SURVIVAL, version.gameMode)
        assertEquals(VersionTrigger.MANUAL, version.trigger)
        assertEquals(1, version.data.mainInventory.size)
        assertEquals(30, version.data.level)
        assertEquals("test_world", version.metadata["world"])
    }

    @Test
    @DisplayName("Test InventoryVersion serialization round-trip")
    fun testVersionRoundTrip() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "creative", GameMode.CREATIVE)
        playerData.mainInventory[5] = ItemStack(Material.GOLDEN_APPLE, 32)
        playerData.armorInventory[3] = ItemStack(Material.DIAMOND_HELMET)
        playerData.offhand = ItemStack(Material.SHIELD)
        playerData.health = 15.5
        playerData.level = 50

        val original = InventoryVersion.create(playerData, VersionTrigger.WORLD_CHANGE)

        // Simulate storage round-trip using fromStorage
        val reconstructed = InventoryVersion.fromStorage(
            id = original.id,
            playerUuid = original.playerUuid,
            group = original.group,
            gameMode = original.gameMode,
            timestamp = original.timestamp,
            trigger = original.trigger,
            data = original.data,
            metadata = original.metadata
        )

        assertEquals(original.id, reconstructed.id)
        assertEquals(original.playerUuid, reconstructed.playerUuid)
        assertEquals(original.group, reconstructed.group)
        assertEquals(original.gameMode, reconstructed.gameMode)
        assertEquals(original.timestamp, reconstructed.timestamp)
        assertEquals(original.trigger, reconstructed.trigger)
        assertEquals(original.data.mainInventory.size, reconstructed.data.mainInventory.size)
        assertEquals(original.data.level, reconstructed.data.level)
    }

    @Test
    @DisplayName("Test version timestamp for pruning")
    fun testVersionTimestampForPruning() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)

        // Create versions at different times
        val recentVersion = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

        val oldTimestamp = Instant.now().minus(10, ChronoUnit.DAYS)
        val oldVersion = InventoryVersion.fromStorage(
            id = UUID.randomUUID().toString(),
            playerUuid = testUuid,
            group = "survival",
            gameMode = GameMode.SURVIVAL,
            timestamp = oldTimestamp,
            trigger = VersionTrigger.MANUAL,
            data = playerData,
            metadata = emptyMap()
        )

        val pruneCutoff = Instant.now().minus(7, ChronoUnit.DAYS)

        // Recent version should NOT be pruned
        assertTrue(recentVersion.timestamp.isAfter(pruneCutoff))

        // Old version SHOULD be pruned
        assertTrue(oldVersion.timestamp.isBefore(pruneCutoff))
    }

    @Test
    @DisplayName("Test version ordering by timestamp")
    fun testVersionOrdering() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)

        val versions = listOf(
            InventoryVersion.fromStorage(
                UUID.randomUUID().toString(), testUuid, "survival", GameMode.SURVIVAL,
                Instant.now().minus(1, ChronoUnit.HOURS), VersionTrigger.MANUAL, playerData, emptyMap()
            ),
            InventoryVersion.fromStorage(
                UUID.randomUUID().toString(), testUuid, "survival", GameMode.SURVIVAL,
                Instant.now().minus(3, ChronoUnit.HOURS), VersionTrigger.MANUAL, playerData, emptyMap()
            ),
            InventoryVersion.fromStorage(
                UUID.randomUUID().toString(), testUuid, "survival", GameMode.SURVIVAL,
                Instant.now().minus(2, ChronoUnit.HOURS), VersionTrigger.MANUAL, playerData, emptyMap()
            )
        )

        // Sort by timestamp descending (most recent first)
        val sorted = versions.sortedByDescending { it.timestamp }

        assertTrue(sorted[0].timestamp.isAfter(sorted[1].timestamp))
        assertTrue(sorted[1].timestamp.isAfter(sorted[2].timestamp))
    }

    @Test
    @DisplayName("Test all trigger types")
    fun testAllTriggerTypes() {
        val playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)

        VersionTrigger.values().forEach { trigger ->
            val version = InventoryVersion.create(playerData, trigger)
            assertEquals(trigger, version.trigger)
        }
    }
}

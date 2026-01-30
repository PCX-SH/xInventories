package sh.pcx.xinventories.unit.model

import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import sh.pcx.xinventories.internal.model.InventoryVersion
import sh.pcx.xinventories.internal.model.PlayerData
import sh.pcx.xinventories.internal.model.VersionTrigger
import java.time.Instant
import java.util.UUID

@DisplayName("InventoryVersion Tests")
class InventoryVersionTest {

    private lateinit var playerData: PlayerData
    private val testUuid = UUID.randomUUID()

    @BeforeEach
    fun setUp() {
        playerData = PlayerData.empty(testUuid, "TestPlayer", "survival", GameMode.SURVIVAL)
        playerData.mainInventory[0] = ItemStack(Material.DIAMOND, 64)
        playerData.armorInventory[3] = ItemStack(Material.DIAMOND_HELMET)
        playerData.health = 15.0
        playerData.level = 30
    }

    @Nested
    @DisplayName("Version Creation")
    inner class VersionCreationTests {

        @Test
        @DisplayName("should create version with unique ID")
        fun createVersionWithUniqueId() {
            val version1 = InventoryVersion.create(playerData, VersionTrigger.MANUAL)
            val version2 = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertNotEquals(version1.id, version2.id)
            assertEquals(36, version1.id.length) // UUID format
        }

        @Test
        @DisplayName("should preserve player UUID")
        fun preservePlayerUuid() {
            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertEquals(testUuid, version.playerUuid)
        }

        @Test
        @DisplayName("should preserve group name")
        fun preserveGroupName() {
            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertEquals("survival", version.group)
        }

        @Test
        @DisplayName("should preserve game mode")
        fun preserveGameMode() {
            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertEquals(GameMode.SURVIVAL, version.gameMode)
        }

        @Test
        @DisplayName("should set timestamp to now")
        fun setTimestampToNow() {
            val before = Instant.now()
            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)
            val after = Instant.now()

            assertTrue(version.timestamp >= before)
            assertTrue(version.timestamp <= after)
        }

        @Test
        @DisplayName("should store trigger type")
        fun storeTriggerType() {
            val manualVersion = InventoryVersion.create(playerData, VersionTrigger.MANUAL)
            val worldChangeVersion = InventoryVersion.create(playerData, VersionTrigger.WORLD_CHANGE)
            val disconnectVersion = InventoryVersion.create(playerData, VersionTrigger.DISCONNECT)
            val deathVersion = InventoryVersion.create(playerData, VersionTrigger.DEATH)

            assertEquals(VersionTrigger.MANUAL, manualVersion.trigger)
            assertEquals(VersionTrigger.WORLD_CHANGE, worldChangeVersion.trigger)
            assertEquals(VersionTrigger.DISCONNECT, disconnectVersion.trigger)
            assertEquals(VersionTrigger.DEATH, deathVersion.trigger)
        }

        @Test
        @DisplayName("should store player data")
        fun storePlayerData() {
            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertEquals(1, version.data.mainInventory.size)
            assertEquals(Material.DIAMOND, version.data.mainInventory[0]?.type)
            assertEquals(15.0, version.data.health)
            assertEquals(30, version.data.level)
        }

        @Test
        @DisplayName("should store metadata")
        fun storeMetadata() {
            val metadata = mapOf("world" to "survival_world", "reason" to "test backup")
            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL, metadata)

            assertEquals("survival_world", version.metadata["world"])
            assertEquals("test backup", version.metadata["reason"])
        }

        @Test
        @DisplayName("should have empty metadata by default")
        fun emptyMetadataByDefault() {
            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertTrue(version.metadata.isEmpty())
        }
    }

    @Nested
    @DisplayName("Version from Storage")
    inner class FromStorageTests {

        @Test
        @DisplayName("should reconstruct version from storage data")
        fun reconstructFromStorage() {
            val id = UUID.randomUUID().toString()
            val timestamp = Instant.now().minusSeconds(3600)
            val metadata = mapOf("key" to "value")

            val version = InventoryVersion.fromStorage(
                id = id,
                playerUuid = testUuid,
                group = "creative",
                gameMode = GameMode.CREATIVE,
                timestamp = timestamp,
                trigger = VersionTrigger.WORLD_CHANGE,
                data = playerData,
                metadata = metadata
            )

            assertEquals(id, version.id)
            assertEquals(testUuid, version.playerUuid)
            assertEquals("creative", version.group)
            assertEquals(GameMode.CREATIVE, version.gameMode)
            assertEquals(timestamp, version.timestamp)
            assertEquals(VersionTrigger.WORLD_CHANGE, version.trigger)
            assertEquals("value", version.metadata["key"])
        }
    }

    @Nested
    @DisplayName("Relative Time Description")
    inner class RelativeTimeDescriptionTests {

        @Test
        @DisplayName("should return 'just now' for recent versions")
        fun justNow() {
            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)
            assertEquals("just now", version.getRelativeTimeDescription())
        }

        @Test
        @DisplayName("should return minutes ago")
        fun minutesAgo() {
            val version = InventoryVersion.fromStorage(
                id = UUID.randomUUID().toString(),
                playerUuid = testUuid,
                group = "default",
                gameMode = GameMode.SURVIVAL,
                timestamp = Instant.now().minusSeconds(300), // 5 minutes
                trigger = VersionTrigger.MANUAL,
                data = playerData,
                metadata = emptyMap()
            )

            assertEquals("5 minutes ago", version.getRelativeTimeDescription())
        }

        @Test
        @DisplayName("should return hours ago")
        fun hoursAgo() {
            val version = InventoryVersion.fromStorage(
                id = UUID.randomUUID().toString(),
                playerUuid = testUuid,
                group = "default",
                gameMode = GameMode.SURVIVAL,
                timestamp = Instant.now().minusSeconds(7200), // 2 hours
                trigger = VersionTrigger.MANUAL,
                data = playerData,
                metadata = emptyMap()
            )

            assertEquals("2 hours ago", version.getRelativeTimeDescription())
        }

        @Test
        @DisplayName("should return days ago")
        fun daysAgo() {
            val version = InventoryVersion.fromStorage(
                id = UUID.randomUUID().toString(),
                playerUuid = testUuid,
                group = "default",
                gameMode = GameMode.SURVIVAL,
                timestamp = Instant.now().minusSeconds(172800), // 2 days
                trigger = VersionTrigger.MANUAL,
                data = playerData,
                metadata = emptyMap()
            )

            assertEquals("2 days ago", version.getRelativeTimeDescription())
        }
    }

    @Nested
    @DisplayName("Item Summary")
    inner class ItemSummaryTests {

        @Test
        @DisplayName("should return 'empty' for empty inventory")
        fun emptyInventory() {
            val emptyData = PlayerData.empty(testUuid, "Test", "default", GameMode.SURVIVAL)
            val version = InventoryVersion.create(emptyData, VersionTrigger.MANUAL)

            assertEquals("empty", version.getItemSummary())
        }

        @Test
        @DisplayName("should count main inventory items")
        fun countMainInventory() {
            playerData.mainInventory.clear()
            playerData.armorInventory.clear()
            playerData.offhand = null
            playerData.mainInventory[0] = ItemStack(Material.DIAMOND, 1)
            playerData.mainInventory[1] = ItemStack(Material.IRON_INGOT, 1)
            playerData.mainInventory[2] = ItemStack(Material.GOLD_INGOT, 1)

            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertTrue(version.getItemSummary().contains("3 items"))
        }

        @Test
        @DisplayName("should include armor count")
        fun includeArmorCount() {
            playerData.mainInventory.clear()
            playerData.armorInventory.clear()
            playerData.offhand = null
            playerData.armorInventory[0] = ItemStack(Material.DIAMOND_BOOTS)
            playerData.armorInventory[3] = ItemStack(Material.DIAMOND_HELMET)

            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertTrue(version.getItemSummary().contains("2 armor"))
        }

        @Test
        @DisplayName("should include offhand")
        fun includeOffhand() {
            playerData.mainInventory.clear()
            playerData.armorInventory.clear()
            playerData.offhand = ItemStack(Material.SHIELD)

            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertTrue(version.getItemSummary().contains("offhand"))
        }

        @Test
        @DisplayName("should include ender chest count")
        fun includeEnderChest() {
            playerData.mainInventory.clear()
            playerData.armorInventory.clear()
            playerData.offhand = null
            playerData.enderChest[0] = ItemStack(Material.DIAMOND, 64)

            val version = InventoryVersion.create(playerData, VersionTrigger.MANUAL)

            assertTrue(version.getItemSummary().contains("1 ender chest"))
        }
    }
}

package sh.pcx.xinventories.unit.gui

import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.internal.model.DeathRecord
import sh.pcx.xinventories.internal.model.PlayerData
import java.time.Instant
import java.util.UUID

@DisplayName("DeathRecoveryGUI Logic")
class DeathRecoveryGUITest {

    private lateinit var server: ServerMock
    private val testUuid = UUID.randomUUID()
    private val testName = "TestPlayer"

    @BeforeEach
    fun setUp() {
        server = MockBukkit.mock()
    }

    @AfterEach
    fun tearDown() {
        MockBukkit.unmock()
    }

    @Nested
    @DisplayName("Death Record Display")
    inner class DeathRecordDisplayTests {

        @Test
        @DisplayName("should format death cause correctly")
        fun shouldFormatDeathCauseCorrectly() {
            assertEquals("Fall", formatDeathCause("FALL"))
            assertEquals("Lava", formatDeathCause("LAVA"))
            assertEquals("Entity attack", formatDeathCause("ENTITY_ATTACK"))
            assertEquals("Block explosion", formatDeathCause("BLOCK_EXPLOSION"))
        }

        @Test
        @DisplayName("should get death description for player kill")
        fun shouldGetDeathDescriptionForPlayerKill() {
            val killerUuid = UUID.randomUUID()
            val description = getDeathDescription("ENTITY_ATTACK", "EvilPlayer", killerUuid)

            assertEquals("Killed by player EvilPlayer", description)
        }

        @Test
        @DisplayName("should get death description for mob kill")
        fun shouldGetDeathDescriptionForMobKill() {
            val description = getDeathDescription("ENTITY_ATTACK", "Zombie", null)

            assertEquals("Killed by Zombie", description)
        }

        @Test
        @DisplayName("should get death description for environmental death")
        fun shouldGetDeathDescriptionForEnvironmentalDeath() {
            val description = getDeathDescription("FALL", null, null)

            assertEquals("Fall", description)
        }

        @Test
        @DisplayName("should get death description for unknown cause")
        fun shouldGetDeathDescriptionForUnknownCause() {
            val description = getDeathDescription(null, null, null)

            assertEquals("Unknown cause", description)
        }

        private fun formatDeathCause(cause: String): String {
            return cause.lowercase()
                .replace("_", " ")
                .replaceFirstChar { it.uppercase() }
        }

        private fun getDeathDescription(deathCause: String?, killerName: String?, killerUuid: UUID?): String {
            return when {
                killerName != null && killerUuid != null -> "Killed by player $killerName"
                killerName != null -> "Killed by $killerName"
                deathCause != null -> formatDeathCause(deathCause)
                else -> "Unknown cause"
            }
        }
    }

    @Nested
    @DisplayName("Death Location Display")
    inner class DeathLocationDisplayTests {

        @Test
        @DisplayName("should format location string correctly")
        fun shouldFormatLocationStringCorrectly() {
            val world = server.addSimpleWorld("test_world")
            val location = Location(world, 123.7, 64.2, -456.9)

            val playerData = PlayerData.empty(testUuid, testName, "survival", GameMode.SURVIVAL)
            val record = DeathRecord.create(playerData, location, "FALL", null, null)

            assertEquals("test_world (123, 64, -456)", record.getLocationString())
        }
    }

    @Nested
    @DisplayName("Death Material Mapping")
    inner class DeathMaterialMappingTests {

        @Test
        @DisplayName("should map PvP death to sword")
        fun shouldMapPvPDeathToSword() {
            assertEquals(Material.IRON_SWORD, getMaterialForDeath(null, UUID.randomUUID()))
        }

        @Test
        @DisplayName("should map mob death to zombie head")
        fun shouldMapMobDeathToZombieHead() {
            assertEquals(Material.ZOMBIE_HEAD, getMaterialForDeath("Zombie", null))
        }

        @Test
        @DisplayName("should map fall death to feather")
        fun shouldMapFallDeathToFeather() {
            assertEquals(Material.FEATHER, getMaterialForCause("FALL"))
        }

        @Test
        @DisplayName("should map lava death to lava bucket")
        fun shouldMapLavaDeathToLavaBucket() {
            assertEquals(Material.LAVA_BUCKET, getMaterialForCause("LAVA"))
        }

        @Test
        @DisplayName("should map fire death to blaze powder")
        fun shouldMapFireDeathToBlazePowder() {
            assertEquals(Material.BLAZE_POWDER, getMaterialForCause("FIRE"))
        }

        @Test
        @DisplayName("should map drown death to water bucket")
        fun shouldMapDrownDeathToWaterBucket() {
            assertEquals(Material.WATER_BUCKET, getMaterialForCause("DROWNING"))
        }

        @Test
        @DisplayName("should map void death to end portal frame")
        fun shouldMapVoidDeathToEndPortalFrame() {
            assertEquals(Material.END_PORTAL_FRAME, getMaterialForCause("VOID"))
        }

        @Test
        @DisplayName("should map explosion death to TNT")
        fun shouldMapExplosionDeathToTNT() {
            assertEquals(Material.TNT, getMaterialForCause("BLOCK_EXPLOSION"))
        }

        @Test
        @DisplayName("should map magic death to splash potion")
        fun shouldMapMagicDeathToSplashPotion() {
            assertEquals(Material.SPLASH_POTION, getMaterialForCause("MAGIC"))
        }

        @Test
        @DisplayName("should default to bone for unknown cause")
        fun shouldDefaultToBoneForUnknownCause() {
            assertEquals(Material.BONE, getMaterialForCause("UNKNOWN"))
            assertEquals(Material.BONE, getMaterialForCause(null))
        }

        private fun getMaterialForDeath(killerName: String?, killerUuid: UUID?): Material {
            return when {
                killerUuid != null -> Material.IRON_SWORD // PvP death
                killerName != null -> Material.ZOMBIE_HEAD // Mob kill
                else -> Material.BONE
            }
        }

        private fun getMaterialForCause(deathCause: String?): Material {
            return when {
                deathCause?.contains("FALL") == true -> Material.FEATHER
                deathCause?.contains("LAVA") == true -> Material.LAVA_BUCKET
                deathCause?.contains("FIRE") == true -> Material.BLAZE_POWDER
                deathCause?.contains("DROWN") == true -> Material.WATER_BUCKET
                deathCause?.contains("VOID") == true -> Material.END_PORTAL_FRAME
                deathCause?.contains("EXPLOSION") == true -> Material.TNT
                deathCause?.contains("MAGIC") == true -> Material.SPLASH_POTION
                deathCause?.contains("WITHER") == true -> Material.WITHER_SKELETON_SKULL
                else -> Material.BONE
            }
        }
    }

    @Nested
    @DisplayName("Pagination")
    inner class PaginationTests {

        @Test
        @DisplayName("should calculate correct max page for death records")
        fun shouldCalculateCorrectMaxPage() {
            val itemsPerPage = 28

            assertEquals(0, calculateMaxPage(0, itemsPerPage))
            assertEquals(0, calculateMaxPage(1, itemsPerPage))
            assertEquals(0, calculateMaxPage(28, itemsPerPage))
            assertEquals(1, calculateMaxPage(29, itemsPerPage))
            assertEquals(1, calculateMaxPage(56, itemsPerPage))
            assertEquals(2, calculateMaxPage(57, itemsPerPage))
        }

        @Test
        @DisplayName("should calculate death record slot positions")
        fun shouldCalculateDeathRecordSlotPositions() {
            // Grid: rows 1-4, columns 1-7
            val recordSlots = mutableListOf<Int>()
            for (row in 1..4) {
                for (col in 1..7) {
                    recordSlots.add(row * 9 + col)
                }
            }

            assertEquals(28, recordSlots.size)
            assertEquals(10, recordSlots.first()) // First slot: row 1, col 1
            assertEquals(43, recordSlots.last()) // Last slot: row 4, col 7
        }

        private fun calculateMaxPage(totalItems: Int, itemsPerPage: Int): Int {
            return if (totalItems == 0) 0 else (totalItems - 1) / itemsPerPage
        }
    }

    @Nested
    @DisplayName("Relative Time")
    inner class RelativeTimeTests {

        @Test
        @DisplayName("should return just now for recent deaths")
        fun shouldReturnJustNowForRecentDeaths() {
            val timestamp = Instant.now()
            assertEquals("just now", getRelativeTimeDescription(timestamp))
        }

        @Test
        @DisplayName("should return minutes ago")
        fun shouldReturnMinutesAgo() {
            val timestamp = Instant.now().minusSeconds(300) // 5 minutes
            assertEquals("5 minutes ago", getRelativeTimeDescription(timestamp))
        }

        @Test
        @DisplayName("should return hours ago")
        fun shouldReturnHoursAgo() {
            val timestamp = Instant.now().minusSeconds(7200) // 2 hours
            assertEquals("2 hours ago", getRelativeTimeDescription(timestamp))
        }

        @Test
        @DisplayName("should return days ago")
        fun shouldReturnDaysAgo() {
            val timestamp = Instant.now().minusSeconds(172800) // 2 days
            assertEquals("2 days ago", getRelativeTimeDescription(timestamp))
        }

        @Test
        @DisplayName("should return weeks ago")
        fun shouldReturnWeeksAgo() {
            val timestamp = Instant.now().minusSeconds(1209600) // 2 weeks
            assertEquals("2 weeks ago", getRelativeTimeDescription(timestamp))
        }

        private fun getRelativeTimeDescription(timestamp: Instant): String {
            val now = Instant.now()
            val diffSeconds = now.epochSecond - timestamp.epochSecond

            return when {
                diffSeconds < 60 -> "just now"
                diffSeconds < 3600 -> "${diffSeconds / 60} minutes ago"
                diffSeconds < 86400 -> "${diffSeconds / 3600} hours ago"
                diffSeconds < 604800 -> "${diffSeconds / 86400} days ago"
                else -> "${diffSeconds / 604800} weeks ago"
            }
        }
    }

    @Nested
    @DisplayName("Item Summary")
    inner class ItemSummaryTests {

        @Test
        @DisplayName("should show empty inventory for no items")
        fun shouldShowEmptyInventoryForNoItems() {
            val playerData = PlayerData.empty(testUuid, testName, "test", GameMode.SURVIVAL)

            assertEquals("empty inventory", getItemSummary(playerData))
        }

        @Test
        @DisplayName("should count main inventory items")
        fun shouldCountMainInventoryItems() {
            val playerData = PlayerData.empty(testUuid, testName, "test", GameMode.SURVIVAL)
            playerData.mainInventory[0] = ItemStack(Material.DIAMOND_SWORD)
            playerData.mainInventory[1] = ItemStack(Material.GOLDEN_APPLE, 5)

            val summary = getItemSummary(playerData)
            assertTrue(summary.contains("2 items"))
        }

        @Test
        @DisplayName("should count armor pieces")
        fun shouldCountArmorPieces() {
            val playerData = PlayerData.empty(testUuid, testName, "test", GameMode.SURVIVAL)
            playerData.armorInventory[0] = ItemStack(Material.DIAMOND_BOOTS)
            playerData.armorInventory[3] = ItemStack(Material.DIAMOND_HELMET)

            val summary = getItemSummary(playerData)
            assertTrue(summary.contains("2 armor"))
        }

        @Test
        @DisplayName("should indicate offhand")
        fun shouldIndicateOffhand() {
            val playerData = PlayerData.empty(testUuid, testName, "test", GameMode.SURVIVAL)
            playerData.offhand = ItemStack(Material.SHIELD)

            val summary = getItemSummary(playerData)
            assertTrue(summary.contains("offhand"))
        }

        @Test
        @DisplayName("should count ender chest items")
        fun shouldCountEnderChestItems() {
            val playerData = PlayerData.empty(testUuid, testName, "test", GameMode.SURVIVAL)
            playerData.enderChest[0] = ItemStack(Material.DIAMOND_BLOCK)
            playerData.enderChest[5] = ItemStack(Material.GOLD_BLOCK)
            playerData.enderChest[10] = ItemStack(Material.IRON_BLOCK)

            val summary = getItemSummary(playerData)
            assertTrue(summary.contains("3 ender chest"))
        }

        private fun getItemSummary(data: PlayerData): String {
            val mainCount = data.mainInventory.size
            val armorCount = data.armorInventory.size
            val hasOffhand = data.offhand != null
            val enderCount = data.enderChest.size

            val parts = mutableListOf<String>()
            if (mainCount > 0) parts.add("$mainCount items")
            if (armorCount > 0) parts.add("$armorCount armor")
            if (hasOffhand) parts.add("offhand")
            if (enderCount > 0) parts.add("$enderCount ender chest")

            return if (parts.isEmpty()) "empty inventory" else parts.joinToString(", ")
        }
    }
}

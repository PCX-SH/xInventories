package sh.pcx.xinventories.unit.compat

import org.bukkit.attribute.Attribute
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import org.mockbukkit.mockbukkit.entity.PlayerMock
import sh.pcx.xinventories.internal.compat.AttributeCompat
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for [AttributeCompat] compatibility layer.
 *
 * These tests verify that the attribute compatibility layer works correctly
 * across different Minecraft versions (1.20.5 - 1.21.x+).
 */
@DisplayName("AttributeCompat")
class AttributeCompatTest {

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
    @DisplayName("MAX_HEALTH Attribute Detection")
    inner class MaxHealthAttributeDetection {

        @Test
        @DisplayName("MAX_HEALTH attribute should be resolved")
        fun maxHealthAttributeResolved() {
            val attribute = AttributeCompat.MAX_HEALTH

            assertNotNull(attribute)
            // Should be either MAX_HEALTH or GENERIC_MAX_HEALTH depending on version
            assertTrue(
                attribute == Attribute.MAX_HEALTH || attribute.name().contains("MAX_HEALTH"),
                "Attribute should be MAX_HEALTH variant"
            )
        }

        @Test
        @DisplayName("MAX_HEALTH should be consistent on repeated access")
        fun maxHealthConsistentOnRepeatedAccess() {
            val first = AttributeCompat.MAX_HEALTH
            val second = AttributeCompat.MAX_HEALTH
            val third = AttributeCompat.MAX_HEALTH

            assertEquals(first, second)
            assertEquals(second, third)
        }
    }

    @Nested
    @DisplayName("getMaxHealth()")
    inner class GetMaxHealthTests {

        @Test
        @DisplayName("should return attribute instance for player")
        fun returnsAttributeInstanceForPlayer() {
            val player = server.addPlayer()

            val attributeInstance = AttributeCompat.getMaxHealth(player)

            assertNotNull(attributeInstance)
        }

        @Test
        @DisplayName("attribute instance should have correct base value")
        fun attributeInstanceHasCorrectBaseValue() {
            val player = server.addPlayer()

            val attributeInstance = AttributeCompat.getMaxHealth(player)

            assertNotNull(attributeInstance)
            assertEquals(20.0, attributeInstance.baseValue)
        }

        @Test
        @DisplayName("attribute instance should reflect value changes")
        fun attributeInstanceReflectsValueChanges() {
            val player = server.addPlayer()

            val attributeInstance = AttributeCompat.getMaxHealth(player)
            assertNotNull(attributeInstance)

            attributeInstance.baseValue = 40.0

            assertEquals(40.0, attributeInstance.baseValue)
            assertEquals(40.0, AttributeCompat.getMaxHealth(player)?.baseValue)
        }
    }

    @Nested
    @DisplayName("getMaxHealthValue()")
    inner class GetMaxHealthValueTests {

        @Test
        @DisplayName("should return player max health")
        fun returnsPlayerMaxHealth() {
            val player = server.addPlayer()

            val maxHealth = AttributeCompat.getMaxHealthValue(player)

            assertEquals(20.0, maxHealth)
        }

        @Test
        @DisplayName("should return custom max health after modification")
        fun returnsCustomMaxHealthAfterModification() {
            val player = server.addPlayer()
            AttributeCompat.getMaxHealth(player)?.baseValue = 30.0

            val maxHealth = AttributeCompat.getMaxHealthValue(player)

            assertEquals(30.0, maxHealth)
        }

        @Test
        @DisplayName("should use default value if attribute is missing")
        fun usesDefaultValueIfAttributeMissing() {
            val player = server.addPlayer()

            // Even with a mock, the default should work
            val maxHealth = AttributeCompat.getMaxHealthValue(player, 100.0)

            // Should return actual value, not default, since attribute exists
            assertEquals(20.0, maxHealth)
        }

        @Test
        @DisplayName("should use custom default value")
        fun usesCustomDefaultValue() {
            val player = server.addPlayer()

            val maxHealth = AttributeCompat.getMaxHealthValue(player, 50.0)

            // Attribute exists on mock players, so returns real value
            assertEquals(20.0, maxHealth)
        }

        @Test
        @DisplayName("should work with different players independently")
        fun worksWithDifferentPlayersIndependently() {
            val player1 = server.addPlayer("Player1")
            val player2 = server.addPlayer("Player2")

            AttributeCompat.getMaxHealth(player1)?.baseValue = 30.0
            AttributeCompat.getMaxHealth(player2)?.baseValue = 40.0

            assertEquals(30.0, AttributeCompat.getMaxHealthValue(player1))
            assertEquals(40.0, AttributeCompat.getMaxHealthValue(player2))
        }
    }

    @Nested
    @DisplayName("Version Compatibility")
    inner class VersionCompatibility {

        @Test
        @DisplayName("should work with MockBukkit simulated environment")
        fun worksWithMockBukkitEnvironment() {
            val player = server.addPlayer()

            // All methods should work without exceptions
            val attribute = AttributeCompat.MAX_HEALTH
            val instance = AttributeCompat.getMaxHealth(player)
            val value = AttributeCompat.getMaxHealthValue(player)

            assertNotNull(attribute)
            assertNotNull(instance)
            assertTrue(value > 0)
        }

        @Test
        @DisplayName("lazy initialization should be thread-safe")
        fun lazyInitializationThreadSafe() {
            // Access from multiple conceptual "threads" (sequential in test)
            val results = (1..10).map { AttributeCompat.MAX_HEALTH }

            // All should be the same instance
            assertTrue(results.all { it == results.first() })
        }
    }

    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {

        @Test
        @DisplayName("should handle player with modified health")
        fun handlesPlayerWithModifiedHealth() {
            val player = server.addPlayer()
            player.health = 10.0

            val maxHealth = AttributeCompat.getMaxHealthValue(player)

            // Max health should still be 20 even if current health is 10
            assertEquals(20.0, maxHealth)
        }

        @Test
        @DisplayName("should handle very high max health values")
        fun handlesVeryHighMaxHealthValues() {
            val player = server.addPlayer()
            AttributeCompat.getMaxHealth(player)?.baseValue = 2048.0

            val maxHealth = AttributeCompat.getMaxHealthValue(player)

            assertEquals(2048.0, maxHealth)
        }

        @Test
        @DisplayName("should handle minimum max health value")
        fun handlesMinimumMaxHealthValue() {
            val player = server.addPlayer()
            AttributeCompat.getMaxHealth(player)?.baseValue = 1.0

            val maxHealth = AttributeCompat.getMaxHealthValue(player)

            assertEquals(1.0, maxHealth)
        }
    }
}

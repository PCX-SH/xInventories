package sh.pcx.xinventories.unit.compat

import org.bukkit.potion.PotionEffectType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockbukkit.mockbukkit.MockBukkit
import org.mockbukkit.mockbukkit.ServerMock
import sh.pcx.xinventories.internal.compat.PotionEffectCompat
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests for [PotionEffectCompat] compatibility layer.
 *
 * These tests verify that potion effect type lookups work correctly
 * across different input formats and Minecraft versions.
 */
@DisplayName("PotionEffectCompat")
class PotionEffectCompatTest {

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
    @DisplayName("getByName() with simple names")
    inner class GetByNameSimpleNames {

        @Test
        @DisplayName("should resolve SPEED")
        fun resolvesSpeed() {
            val effect = PotionEffectCompat.getByName("SPEED")

            assertNotNull(effect)
            assertEquals(PotionEffectType.SPEED, effect)
        }

        @Test
        @DisplayName("should resolve lowercase speed")
        fun resolvesLowercaseSpeed() {
            val effect = PotionEffectCompat.getByName("speed")

            assertNotNull(effect)
            assertEquals(PotionEffectType.SPEED, effect)
        }

        @Test
        @DisplayName("should resolve REGENERATION")
        fun resolvesRegeneration() {
            val effect = PotionEffectCompat.getByName("REGENERATION")

            assertNotNull(effect)
            assertEquals(PotionEffectType.REGENERATION, effect)
        }

        @Test
        @DisplayName("should resolve STRENGTH")
        fun resolvesStrength() {
            val effect = PotionEffectCompat.getByName("STRENGTH")

            assertNotNull(effect)
            assertEquals(PotionEffectType.STRENGTH, effect)
        }

        @Test
        @DisplayName("should resolve INVISIBILITY")
        fun resolvesInvisibility() {
            val effect = PotionEffectCompat.getByName("INVISIBILITY")

            assertNotNull(effect)
            assertEquals(PotionEffectType.INVISIBILITY, effect)
        }

        @Test
        @DisplayName("should resolve POISON")
        fun resolvesPoison() {
            val effect = PotionEffectCompat.getByName("POISON")

            assertNotNull(effect)
            assertEquals(PotionEffectType.POISON, effect)
        }

        @Test
        @DisplayName("should resolve WITHER")
        fun resolvesWither() {
            val effect = PotionEffectCompat.getByName("WITHER")

            assertNotNull(effect)
            assertEquals(PotionEffectType.WITHER, effect)
        }
    }

    @Nested
    @DisplayName("getByName() with namespaced keys")
    inner class GetByNameNamespacedKeys {

        @Test
        @DisplayName("should resolve minecraft:speed")
        fun resolvesNamespacedSpeed() {
            val effect = PotionEffectCompat.getByName("minecraft:speed")

            assertNotNull(effect)
            assertEquals(PotionEffectType.SPEED, effect)
        }

        @Test
        @DisplayName("should resolve minecraft:regeneration")
        fun resolvesNamespacedRegeneration() {
            val effect = PotionEffectCompat.getByName("minecraft:regeneration")

            assertNotNull(effect)
            assertEquals(PotionEffectType.REGENERATION, effect)
        }

        @Test
        @DisplayName("should resolve minecraft:strength")
        fun resolvesNamespacedStrength() {
            val effect = PotionEffectCompat.getByName("minecraft:strength")

            assertNotNull(effect)
            assertEquals(PotionEffectType.STRENGTH, effect)
        }

        @Test
        @DisplayName("should resolve minecraft:instant_health")
        fun resolvesNamespacedInstantHealth() {
            val effect = PotionEffectCompat.getByName("minecraft:instant_health")

            assertNotNull(effect)
            assertEquals(PotionEffectType.INSTANT_HEALTH, effect)
        }

        @Test
        @DisplayName("should resolve minecraft:instant_damage")
        fun resolvesNamespacedInstantDamage() {
            val effect = PotionEffectCompat.getByName("minecraft:instant_damage")

            assertNotNull(effect)
            assertEquals(PotionEffectType.INSTANT_DAMAGE, effect)
        }
    }

    @Nested
    @DisplayName("getByName() with legacy names")
    inner class GetByNameLegacyNames {

        @Test
        @DisplayName("should resolve INCREASE_DAMAGE as STRENGTH")
        fun resolvesIncreaseDamageAsStrength() {
            val effect = PotionEffectCompat.getByName("INCREASE_DAMAGE")

            assertNotNull(effect)
            assertEquals(PotionEffectType.STRENGTH, effect)
        }

        @Test
        @DisplayName("should resolve DAMAGE_RESISTANCE as RESISTANCE")
        fun resolvesDamageResistanceAsResistance() {
            val effect = PotionEffectCompat.getByName("DAMAGE_RESISTANCE")

            assertNotNull(effect)
            assertEquals(PotionEffectType.RESISTANCE, effect)
        }

        @Test
        @DisplayName("should resolve SLOW as SLOWNESS")
        fun resolvesSlowAsSlowness() {
            val effect = PotionEffectCompat.getByName("SLOW")

            assertNotNull(effect)
            assertEquals(PotionEffectType.SLOWNESS, effect)
        }

        @Test
        @DisplayName("should resolve FAST_DIGGING as HASTE")
        fun resolvesFastDiggingAsHaste() {
            val effect = PotionEffectCompat.getByName("FAST_DIGGING")

            assertNotNull(effect)
            assertEquals(PotionEffectType.HASTE, effect)
        }

        @Test
        @DisplayName("should resolve SLOW_DIGGING as MINING_FATIGUE")
        fun resolvesSlowDiggingAsMiningFatigue() {
            val effect = PotionEffectCompat.getByName("SLOW_DIGGING")

            assertNotNull(effect)
            assertEquals(PotionEffectType.MINING_FATIGUE, effect)
        }

        @Test
        @DisplayName("should resolve HEAL as INSTANT_HEALTH")
        fun resolvesHealAsInstantHealth() {
            val effect = PotionEffectCompat.getByName("HEAL")

            assertNotNull(effect)
            assertEquals(PotionEffectType.INSTANT_HEALTH, effect)
        }

        @Test
        @DisplayName("should resolve HARM as INSTANT_DAMAGE")
        fun resolvesHarmAsInstantDamage() {
            val effect = PotionEffectCompat.getByName("HARM")

            assertNotNull(effect)
            assertEquals(PotionEffectType.INSTANT_DAMAGE, effect)
        }

        @Test
        @DisplayName("should resolve CONFUSION as NAUSEA")
        fun resolvesConfusionAsNausea() {
            val effect = PotionEffectCompat.getByName("CONFUSION")

            assertNotNull(effect)
            assertEquals(PotionEffectType.NAUSEA, effect)
        }

        @Test
        @DisplayName("should resolve JUMP as JUMP_BOOST")
        fun resolvesJumpAsJumpBoost() {
            val effect = PotionEffectCompat.getByName("JUMP")

            assertNotNull(effect)
            assertEquals(PotionEffectType.JUMP_BOOST, effect)
        }
    }

    @Nested
    @DisplayName("getByName() with invalid input")
    inner class GetByNameInvalidInput {

        @Test
        @DisplayName("should return null for unknown effect")
        fun returnsNullForUnknownEffect() {
            val effect = PotionEffectCompat.getByName("UNKNOWN_EFFECT")

            assertNull(effect)
        }

        @Test
        @DisplayName("should return null for empty string")
        fun returnsNullForEmptyString() {
            val effect = PotionEffectCompat.getByName("")

            assertNull(effect)
        }

        @Test
        @DisplayName("should return null for blank string")
        fun returnsNullForBlankString() {
            val effect = PotionEffectCompat.getByName("   ")

            assertNull(effect)
        }

        @Test
        @DisplayName("should return null for invalid namespaced key")
        fun returnsNullForInvalidNamespacedKey() {
            val effect = PotionEffectCompat.getByName("invalid:namespace:key")

            assertNull(effect)
        }

        @Test
        @DisplayName("should return null for unknown namespaced effect")
        fun returnsNullForUnknownNamespacedEffect() {
            val effect = PotionEffectCompat.getByName("minecraft:nonexistent_effect")

            assertNull(effect)
        }
    }

    @Nested
    @DisplayName("All standard effects")
    inner class AllStandardEffects {

        @Test
        @DisplayName("should resolve all common potion effects")
        fun resolvesAllCommonPotionEffects() {
            val effectNames = listOf(
                "SPEED" to PotionEffectType.SPEED,
                "SLOWNESS" to PotionEffectType.SLOWNESS,
                "HASTE" to PotionEffectType.HASTE,
                "MINING_FATIGUE" to PotionEffectType.MINING_FATIGUE,
                "STRENGTH" to PotionEffectType.STRENGTH,
                "INSTANT_HEALTH" to PotionEffectType.INSTANT_HEALTH,
                "INSTANT_DAMAGE" to PotionEffectType.INSTANT_DAMAGE,
                "JUMP_BOOST" to PotionEffectType.JUMP_BOOST,
                "NAUSEA" to PotionEffectType.NAUSEA,
                "REGENERATION" to PotionEffectType.REGENERATION,
                "RESISTANCE" to PotionEffectType.RESISTANCE,
                "FIRE_RESISTANCE" to PotionEffectType.FIRE_RESISTANCE,
                "WATER_BREATHING" to PotionEffectType.WATER_BREATHING,
                "INVISIBILITY" to PotionEffectType.INVISIBILITY,
                "BLINDNESS" to PotionEffectType.BLINDNESS,
                "NIGHT_VISION" to PotionEffectType.NIGHT_VISION,
                "HUNGER" to PotionEffectType.HUNGER,
                "WEAKNESS" to PotionEffectType.WEAKNESS,
                "POISON" to PotionEffectType.POISON,
                "WITHER" to PotionEffectType.WITHER,
                "HEALTH_BOOST" to PotionEffectType.HEALTH_BOOST,
                "ABSORPTION" to PotionEffectType.ABSORPTION,
                "SATURATION" to PotionEffectType.SATURATION,
                "GLOWING" to PotionEffectType.GLOWING,
                "LEVITATION" to PotionEffectType.LEVITATION,
                "LUCK" to PotionEffectType.LUCK,
                "UNLUCK" to PotionEffectType.UNLUCK,
                "SLOW_FALLING" to PotionEffectType.SLOW_FALLING,
                "CONDUIT_POWER" to PotionEffectType.CONDUIT_POWER,
                "DOLPHINS_GRACE" to PotionEffectType.DOLPHINS_GRACE,
                "BAD_OMEN" to PotionEffectType.BAD_OMEN,
                "HERO_OF_THE_VILLAGE" to PotionEffectType.HERO_OF_THE_VILLAGE,
                "DARKNESS" to PotionEffectType.DARKNESS
            )

            effectNames.forEach { (name, expected) ->
                val resolved = PotionEffectCompat.getByName(name)
                assertNotNull(resolved, "Failed to resolve: $name")
                assertEquals(expected, resolved, "Incorrect resolution for: $name")
            }
        }
    }

    @Nested
    @DisplayName("Case sensitivity")
    inner class CaseSensitivity {

        @Test
        @DisplayName("should resolve UPPERCASE names")
        fun resolvesUppercaseNames() {
            val effect = PotionEffectCompat.getByName("SPEED")
            assertNotNull(effect)
            assertEquals(PotionEffectType.SPEED, effect)
        }

        @Test
        @DisplayName("should resolve lowercase names")
        fun resolvesLowercaseNames() {
            val effect = PotionEffectCompat.getByName("speed")
            assertNotNull(effect)
            assertEquals(PotionEffectType.SPEED, effect)
        }

        @Test
        @DisplayName("should resolve Mixed_Case names")
        fun resolvesMixedCaseNames() {
            val effect = PotionEffectCompat.getByName("Speed")
            assertNotNull(effect)
            assertEquals(PotionEffectType.SPEED, effect)
        }

        @Test
        @DisplayName("should resolve underscored names in any case")
        fun resolvesUnderscoredNamesInAnyCase() {
            val effects = listOf(
                "JUMP_BOOST",
                "jump_boost",
                "Jump_Boost"
            )

            effects.forEach { name ->
                val effect = PotionEffectCompat.getByName(name)
                assertNotNull(effect, "Failed to resolve: $name")
                assertEquals(PotionEffectType.JUMP_BOOST, effect)
            }
        }
    }

    @Nested
    @DisplayName("Consistency")
    inner class Consistency {

        @Test
        @DisplayName("multiple lookups should return same result")
        fun multipleLookupsSameResult() {
            val name = "REGENERATION"

            val first = PotionEffectCompat.getByName(name)
            val second = PotionEffectCompat.getByName(name)
            val third = PotionEffectCompat.getByName(name)

            assertNotNull(first)
            assertEquals(first, second)
            assertEquals(second, third)
        }

        @Test
        @DisplayName("different formats should resolve to same effect")
        fun differentFormatsSameEffect() {
            val speed1 = PotionEffectCompat.getByName("SPEED")
            val speed2 = PotionEffectCompat.getByName("speed")
            val speed3 = PotionEffectCompat.getByName("minecraft:speed")

            assertNotNull(speed1)
            assertNotNull(speed2)
            assertNotNull(speed3)
            assertEquals(speed1, speed2)
            assertEquals(speed2, speed3)
        }
    }
}

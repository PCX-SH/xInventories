package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.text.NumberFormat
import java.util.Locale

@DisplayName("EconomyOverviewGUI Logic")
class EconomyOverviewGUITest {

    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale.US)

    @Nested
    @DisplayName("Currency Formatting")
    inner class CurrencyFormattingTests {

        @Test
        @DisplayName("formats small amounts correctly")
        fun formatsSmallAmountsCorrectly() {
            val formatted = currencyFormat.format(123.45)
            assertEquals("$123.45", formatted)
        }

        @Test
        @DisplayName("formats large amounts correctly")
        fun formatsLargeAmountsCorrectly() {
            val formatted = currencyFormat.format(1234567.89)
            assertEquals("$1,234,567.89", formatted)
        }

        @Test
        @DisplayName("formats zero correctly")
        fun formatsZeroCorrectly() {
            val formatted = currencyFormat.format(0.0)
            assertEquals("$0.00", formatted)
        }
    }

    @Nested
    @DisplayName("Gini Coefficient Calculation")
    inner class GiniCoefficientTests {

        @Test
        @DisplayName("perfectly equal distribution returns 0")
        fun perfectlyEqualDistributionReturnsZero() {
            val balances = listOf(100.0, 100.0, 100.0, 100.0)
            val gini = calculateGiniCoefficient(balances)
            assertEquals(0.0, gini, 0.01)
        }

        @Test
        @DisplayName("single player returns 0")
        fun singlePlayerReturnsZero() {
            val balances = listOf(1000.0)
            val gini = calculateGiniCoefficient(balances)
            assertEquals(0.0, gini, 0.01)
        }

        @Test
        @DisplayName("perfect inequality returns close to 1")
        fun perfectInequalityReturnsCloseToOne() {
            // One person has everything, others have nothing
            val balances = listOf(1000.0, 0.0, 0.0, 0.0)
            val gini = calculateGiniCoefficient(balances)
            assertTrue(gini > 0.7) // Should be close to 1
        }

        @Test
        @DisplayName("moderate inequality returns moderate value")
        fun moderateInequalityReturnsModerateValue() {
            val balances = listOf(100.0, 200.0, 300.0, 400.0)
            val gini = calculateGiniCoefficient(balances)
            assertTrue(gini > 0.1 && gini < 0.5)
        }

        @Test
        @DisplayName("empty list returns 0")
        fun emptyListReturnsZero() {
            val balances = emptyList<Double>()
            val gini = calculateGiniCoefficient(balances)
            assertEquals(0.0, gini, 0.01)
        }

        private fun calculateGiniCoefficient(balances: List<Double>): Double {
            if (balances.isEmpty()) return 0.0

            val sorted = balances.sorted()
            val n = sorted.size
            val mean = sorted.average()

            if (mean == 0.0) return 0.0

            var sumDifferences = 0.0
            for (i in sorted.indices) {
                for (j in sorted.indices) {
                    sumDifferences += kotlin.math.abs(sorted[i] - sorted[j])
                }
            }

            return sumDifferences / (2 * n * n * mean)
        }
    }

    @Nested
    @DisplayName("Economy Health Colors")
    inner class EconomyHealthColorTests {

        @Test
        @DisplayName("low total is white")
        fun lowTotalIsWhite() {
            assertEquals("WHITE", getEconomyHealthColor(500.0))
        }

        @Test
        @DisplayName("medium total is green")
        fun mediumTotalIsGreen() {
            assertEquals("GREEN", getEconomyHealthColor(50000.0))
        }

        @Test
        @DisplayName("high total is yellow")
        fun highTotalIsYellow() {
            assertEquals("YELLOW", getEconomyHealthColor(500000.0))
        }

        @Test
        @DisplayName("very high total is gold")
        fun veryHighTotalIsGold() {
            assertEquals("GOLD", getEconomyHealthColor(5000000.0))
        }

        private fun getEconomyHealthColor(total: Double): String {
            return when {
                total < 1000 -> "WHITE"
                total < 100000 -> "GREEN"
                total < 1000000 -> "YELLOW"
                else -> "GOLD"
            }
        }
    }

    @Nested
    @DisplayName("Health Indicator Text")
    inner class HealthIndicatorTextTests {

        @Test
        @DisplayName("low gini coefficient shows healthy")
        fun lowGiniShowsHealthy() {
            val text = getHealthText(0.2)
            assertEquals("Healthy (balanced)", text)
        }

        @Test
        @DisplayName("medium gini coefficient shows moderate")
        fun mediumGiniShowsModerate() {
            val text = getHealthText(0.45)
            assertEquals("Moderate concentration", text)
        }

        @Test
        @DisplayName("high gini coefficient shows high concentration")
        fun highGiniShowsHighConcentration() {
            val text = getHealthText(0.7)
            assertEquals("High concentration", text)
        }

        private fun getHealthText(giniCoefficient: Double): String {
            return when {
                giniCoefficient < 0.3 -> "Healthy (balanced)"
                giniCoefficient < 0.6 -> "Moderate concentration"
                else -> "High concentration"
            }
        }
    }

    @Nested
    @DisplayName("Group Material Selection")
    inner class GroupMaterialTests {

        @Test
        @DisplayName("survival group gets grass block")
        fun survivalGroupGetsGrassBlock() {
            assertEquals("GRASS_BLOCK", getGroupMaterialName("survival"))
            assertEquals("GRASS_BLOCK", getGroupMaterialName("Survival_World"))
        }

        @Test
        @DisplayName("creative group gets command block")
        fun creativeGroupGetsCommandBlock() {
            assertEquals("COMMAND_BLOCK", getGroupMaterialName("creative"))
            assertEquals("COMMAND_BLOCK", getGroupMaterialName("Creative_Build"))
        }

        @Test
        @DisplayName("skyblock group gets sand")
        fun skyblockGroupGetsSand() {
            assertEquals("SAND", getGroupMaterialName("skyblock"))
            assertEquals("SAND", getGroupMaterialName("SkyBlock_Main"))
        }

        @Test
        @DisplayName("unknown group gets chest")
        fun unknownGroupGetsChest() {
            assertEquals("CHEST", getGroupMaterialName("unknown"))
            assertEquals("CHEST", getGroupMaterialName("custom_group"))
        }

        private fun getGroupMaterialName(group: String): String {
            return when {
                group.contains("survival", ignoreCase = true) -> "GRASS_BLOCK"
                group.contains("creative", ignoreCase = true) -> "COMMAND_BLOCK"
                group.contains("skyblock", ignoreCase = true) -> "SAND"
                group.contains("prison", ignoreCase = true) -> "IRON_BARS"
                group.contains("minigame", ignoreCase = true) -> "FIREWORK_ROCKET"
                else -> "CHEST"
            }
        }
    }
}

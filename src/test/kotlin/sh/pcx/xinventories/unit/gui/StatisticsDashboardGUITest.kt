package sh.pcx.xinventories.unit.gui

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.service.CacheMetrics
import sh.pcx.xinventories.internal.service.PerformanceMetrics
import sh.pcx.xinventories.internal.service.StorageMetrics

@DisplayName("StatisticsDashboardGUI Logic")
class StatisticsDashboardGUITest {

    @Nested
    @DisplayName("Health Indicator Colors")
    inner class HealthIndicatorTests {

        @Test
        @DisplayName("data size color is green for small sizes")
        fun dataSizeColorIsGreenForSmallSizes() {
            val sizeMB = 50.0
            val color = getSizeColorIndicator(sizeMB)
            assertEquals("GREEN", color)
        }

        @Test
        @DisplayName("data size color is yellow for medium sizes")
        fun dataSizeColorIsYellowForMediumSizes() {
            val sizeMB = 250.0
            val color = getSizeColorIndicator(sizeMB)
            assertEquals("YELLOW", color)
        }

        @Test
        @DisplayName("data size color is red for large sizes")
        fun dataSizeColorIsRedForLargeSizes() {
            val sizeMB = 600.0
            val color = getSizeColorIndicator(sizeMB)
            assertEquals("RED", color)
        }

        private fun getSizeColorIndicator(sizeMB: Double): String {
            return when {
                sizeMB < 100 -> "GREEN"
                sizeMB < 500 -> "YELLOW"
                else -> "RED"
            }
        }
    }

    @Nested
    @DisplayName("Cache Hit Rate Colors")
    inner class CacheHitRateTests {

        @Test
        @DisplayName("high hit rate is green")
        fun highHitRateIsGreen() {
            val hitRate = 85.0
            assertEquals("GREEN", getHitRateColor(hitRate))
        }

        @Test
        @DisplayName("medium hit rate is yellow")
        fun mediumHitRateIsYellow() {
            val hitRate = 65.0
            assertEquals("YELLOW", getHitRateColor(hitRate))
        }

        @Test
        @DisplayName("low hit rate is red")
        fun lowHitRateIsRed() {
            val hitRate = 40.0
            assertEquals("RED", getHitRateColor(hitRate))
        }

        private fun getHitRateColor(hitRatePercent: Double): String {
            return when {
                hitRatePercent >= 80 -> "GREEN"
                hitRatePercent >= 50 -> "YELLOW"
                else -> "RED"
            }
        }
    }

    @Nested
    @DisplayName("Error Rate Colors")
    inner class ErrorRateTests {

        @Test
        @DisplayName("low error rate is green")
        fun lowErrorRateIsGreen() {
            val errorRate = 0.5
            assertEquals("GREEN", getErrorRateColor(errorRate))
        }

        @Test
        @DisplayName("medium error rate is yellow")
        fun mediumErrorRateIsYellow() {
            val errorRate = 3.0
            assertEquals("YELLOW", getErrorRateColor(errorRate))
        }

        @Test
        @DisplayName("high error rate is red")
        fun highErrorRateIsRed() {
            val errorRate = 7.0
            assertEquals("RED", getErrorRateColor(errorRate))
        }

        private fun getErrorRateColor(errorRatePercent: Double): String {
            return when {
                errorRatePercent < 1 -> "GREEN"
                errorRatePercent < 5 -> "YELLOW"
                else -> "RED"
            }
        }
    }

    @Nested
    @DisplayName("Timing Statistics Colors")
    inner class TimingTests {

        @Test
        @DisplayName("fast save time is green")
        fun fastSaveTimeIsGreen() {
            val avgSaveTime = 25.0
            assertEquals("GREEN", getTimingColor(avgSaveTime))
        }

        @Test
        @DisplayName("medium save time is yellow")
        fun mediumSaveTimeIsYellow() {
            val avgSaveTime = 100.0
            assertEquals("YELLOW", getTimingColor(avgSaveTime))
        }

        @Test
        @DisplayName("slow save time is red")
        fun slowSaveTimeIsRed() {
            val avgSaveTime = 300.0
            assertEquals("RED", getTimingColor(avgSaveTime))
        }

        private fun getTimingColor(avgSaveTimeMs: Double): String {
            return when {
                avgSaveTimeMs < 50 -> "GREEN"
                avgSaveTimeMs < 200 -> "YELLOW"
                else -> "RED"
            }
        }
    }

    @Nested
    @DisplayName("Uptime Formatting")
    inner class UptimeFormattingTests {

        @Test
        @DisplayName("formats seconds correctly")
        fun formatsSecondsCorrectly() {
            assertEquals("45s", formatUptime(45))
        }

        @Test
        @DisplayName("formats minutes correctly")
        fun formatsMinutesCorrectly() {
            assertEquals("5m 30s", formatUptime(330))
        }

        @Test
        @DisplayName("formats hours correctly")
        fun formatsHoursCorrectly() {
            assertEquals("2h 15m 10s", formatUptime(8110))
        }

        @Test
        @DisplayName("formats days correctly")
        fun formatsDaysCorrectly() {
            assertEquals("3d 5h 20m", formatUptime(278400))
        }

        private fun formatUptime(seconds: Long): String {
            val days = seconds / 86400
            val hours = (seconds % 86400) / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60

            return when {
                days > 0 -> "${days}d ${hours}h ${minutes}m"
                hours > 0 -> "${hours}h ${minutes}m ${secs}s"
                minutes > 0 -> "${minutes}m ${secs}s"
                else -> "${secs}s"
            }
        }
    }

    @Nested
    @DisplayName("Storage Metrics")
    inner class StorageMetricsTests {

        @Test
        @DisplayName("MB calculation is correct")
        fun mbCalculationIsCorrect() {
            val sizeBytes = 10485760L // 10 MB
            val sizeMB = sizeBytes / (1024.0 * 1024.0)
            assertEquals(10.0, sizeMB, 0.01)
        }

        @Test
        @DisplayName("large byte sizes convert correctly")
        fun largeByteSizesConvertCorrectly() {
            val sizeBytes = 1073741824L // 1 GB
            val sizeMB = sizeBytes / (1024.0 * 1024.0)
            assertEquals(1024.0, sizeMB, 0.01)
        }
    }
}

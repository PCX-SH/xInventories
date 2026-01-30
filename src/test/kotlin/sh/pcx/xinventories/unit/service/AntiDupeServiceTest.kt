package sh.pcx.xinventories.unit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.service.AntiDupeConfig
import sh.pcx.xinventories.internal.service.DupeDetectionType
import sh.pcx.xinventories.internal.service.DupeSensitivity
import sh.pcx.xinventories.internal.service.DupeSeverity

@DisplayName("AntiDupe Components")
class AntiDupeServiceTest {

    @Nested
    @DisplayName("DupeSensitivity")
    inner class DupeSensitivityTests {

        @Test
        @DisplayName("LOW has lenient thresholds")
        fun lowHasLenientThresholds() {
            val low = DupeSensitivity.LOW

            assertEquals(200, low.minSwitchIntervalMs)
            assertEquals(3.0, low.anomalyThreshold)
        }

        @Test
        @DisplayName("MEDIUM has balanced thresholds")
        fun mediumHasBalancedThresholds() {
            val medium = DupeSensitivity.MEDIUM

            assertEquals(500, medium.minSwitchIntervalMs)
            assertEquals(2.0, medium.anomalyThreshold)
        }

        @Test
        @DisplayName("HIGH has strict thresholds")
        fun highHasStrictThresholds() {
            val high = DupeSensitivity.HIGH

            assertEquals(1000, high.minSwitchIntervalMs)
            assertEquals(1.5, high.anomalyThreshold)
        }

        @Test
        @DisplayName("thresholds increase with sensitivity")
        fun thresholdsIncreaseWithSensitivity() {
            assertTrue(DupeSensitivity.LOW.minSwitchIntervalMs < DupeSensitivity.MEDIUM.minSwitchIntervalMs)
            assertTrue(DupeSensitivity.MEDIUM.minSwitchIntervalMs < DupeSensitivity.HIGH.minSwitchIntervalMs)

            assertTrue(DupeSensitivity.LOW.anomalyThreshold > DupeSensitivity.MEDIUM.anomalyThreshold)
            assertTrue(DupeSensitivity.MEDIUM.anomalyThreshold > DupeSensitivity.HIGH.anomalyThreshold)
        }
    }

    @Nested
    @DisplayName("AntiDupeConfig")
    inner class AntiDupeConfigTests {

        @Test
        @DisplayName("default config has sensible values")
        fun defaultConfigHasSensibleValues() {
            val config = AntiDupeConfig()

            assertTrue(config.enabled)
            assertEquals(DupeSensitivity.MEDIUM, config.sensitivity)
            assertEquals(500, config.minSwitchIntervalMs)
            assertFalse(config.freezeOnDetection)
            assertTrue(config.notifyAdmins)
            assertTrue(config.logDetections)
        }

        @Test
        @DisplayName("custom config preserves values")
        fun customConfigPreservesValues() {
            val config = AntiDupeConfig(
                enabled = false,
                sensitivity = DupeSensitivity.HIGH,
                minSwitchIntervalMs = 1000,
                freezeOnDetection = true,
                notifyAdmins = false,
                logDetections = false
            )

            assertFalse(config.enabled)
            assertEquals(DupeSensitivity.HIGH, config.sensitivity)
            assertEquals(1000, config.minSwitchIntervalMs)
            assertTrue(config.freezeOnDetection)
            assertFalse(config.notifyAdmins)
            assertFalse(config.logDetections)
        }
    }

    @Nested
    @DisplayName("DupeDetectionType")
    inner class DupeDetectionTypeTests {

        @Test
        @DisplayName("all detection types are defined")
        fun allDetectionTypesAreDefined() {
            assertEquals(3, DupeDetectionType.entries.size)
            assertNotNull(DupeDetectionType.RAPID_SWITCH)
            assertNotNull(DupeDetectionType.ITEM_ANOMALY)
            assertNotNull(DupeDetectionType.TIMING_ANOMALY)
        }
    }

    @Nested
    @DisplayName("DupeSeverity")
    inner class DupeSeverityTests {

        @Test
        @DisplayName("all severity levels are defined")
        fun allSeverityLevelsAreDefined() {
            assertEquals(3, DupeSeverity.entries.size)
            assertNotNull(DupeSeverity.LOW)
            assertNotNull(DupeSeverity.MEDIUM)
            assertNotNull(DupeSeverity.HIGH)
        }

        @Test
        @DisplayName("severity levels have logical ordering")
        fun severityLevelsHaveLogicalOrdering() {
            val ordinals = DupeSeverity.entries.map { it.ordinal }

            assertEquals(0, DupeSeverity.LOW.ordinal)
            assertEquals(1, DupeSeverity.MEDIUM.ordinal)
            assertEquals(2, DupeSeverity.HIGH.ordinal)
        }
    }
}

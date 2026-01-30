package sh.pcx.xinventories.unit.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import sh.pcx.xinventories.internal.service.TimingTracker

@DisplayName("MetricsService Components")
class MetricsServiceTest {

    @Nested
    @DisplayName("TimingTracker")
    inner class TimingTrackerTests {

        private lateinit var tracker: TimingTracker

        @BeforeEach
        fun setUp() {
            tracker = TimingTracker(windowSize = 10)
        }

        @Test
        @DisplayName("empty tracker returns zero for all metrics")
        fun emptyTrackerReturnsZeroForAllMetrics() {
            assertEquals(0.0, tracker.getAverage())
            assertEquals(0, tracker.getMin())
            assertEquals(0, tracker.getMax())
            assertEquals(0, tracker.getCount())
        }

        @Test
        @DisplayName("single recording updates all metrics")
        fun singleRecordingUpdatesAllMetrics() {
            tracker.record(100)

            assertEquals(100.0, tracker.getAverage())
            assertEquals(100, tracker.getMin())
            assertEquals(100, tracker.getMax())
            assertEquals(1, tracker.getCount())
        }

        @Test
        @DisplayName("multiple recordings calculate correct average")
        fun multipleRecordingsCalculateCorrectAverage() {
            tracker.record(10)
            tracker.record(20)
            tracker.record(30)

            assertEquals(20.0, tracker.getAverage())
            assertEquals(3, tracker.getCount())
        }

        @Test
        @DisplayName("min and max are tracked correctly")
        fun minAndMaxAreTrackedCorrectly() {
            tracker.record(50)
            tracker.record(10)
            tracker.record(100)
            tracker.record(30)

            assertEquals(10, tracker.getMin())
            assertEquals(100, tracker.getMax())
        }

        @Test
        @DisplayName("window size limits stored entries")
        fun windowSizeLimitsStoredEntries() {
            // Record 15 entries (window size is 10)
            for (i in 1..15) {
                tracker.record(i.toLong())
            }

            // Should only have last 10 entries (6-15)
            assertEquals(10, tracker.getCount())
            assertEquals(6, tracker.getMin())
            assertEquals(15, tracker.getMax())
            // Average of 6-15 = (6+7+8+9+10+11+12+13+14+15)/10 = 105/10 = 10.5
            assertEquals(10.5, tracker.getAverage())
        }

        @Test
        @DisplayName("window slides correctly")
        fun windowSlidesCorrectly() {
            // Fill window with 100s
            for (i in 1..10) {
                tracker.record(100)
            }

            assertEquals(100.0, tracker.getAverage())

            // Add a new value - oldest should be removed
            tracker.record(200)

            assertEquals(10, tracker.getCount())
            assertEquals(100, tracker.getMin())
            assertEquals(200, tracker.getMax())
            // Average should be (9 * 100 + 200) / 10 = 1100 / 10 = 110
            assertEquals(110.0, tracker.getAverage())
        }

        @Test
        @DisplayName("handles large values")
        fun handlesLargeValues() {
            tracker.record(Long.MAX_VALUE / 2)
            tracker.record(Long.MAX_VALUE / 2)

            assertEquals(Long.MAX_VALUE / 2, tracker.getMin())
            assertEquals(Long.MAX_VALUE / 2, tracker.getMax())
            assertEquals(2, tracker.getCount())
        }

        @Test
        @DisplayName("handles zero values")
        fun handlesZeroValues() {
            tracker.record(0)
            tracker.record(0)
            tracker.record(0)

            assertEquals(0.0, tracker.getAverage())
            assertEquals(0, tracker.getMin())
            assertEquals(0, tracker.getMax())
            assertEquals(3, tracker.getCount())
        }
    }
}

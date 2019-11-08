package no.nav.eessi.pensjon.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOError
import java.util.concurrent.TimeUnit

internal class MetricsHelperTest {

    lateinit var registry: MeterRegistry
    lateinit var metricsHelper: MetricsHelper
    lateinit var config: MetricsHelper.Configuration

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        config = MetricsHelper.Configuration()
        metricsHelper = MetricsHelper(registry)
    }

    @Test
    fun measure_counts_success_up_by_one_if_no_exception() {
        metricsHelper.measure (method = "dummy"){
            // the logic you want measured - no exception happening her today
        }

        assertEquals(
            1.0,
            registry.counter(
                    config.measureMeterName,
                    config.methodTag, "dummy",
                    config.typeTag, config.successTypeTagValue)
                .count(),
            0.0001)
    }

    @Test
    fun measure_counts_failure_up_by_one_if_exception_thrown() {
        try {
            metricsHelper.measure("dummy") {
                throw RuntimeException("boom!")
            }
        } catch (ex: RuntimeException) {
            // ignoring on purpose
        }

        assertEquals(
            1.0,
            registry.counter(
                    config.measureMeterName,
                    config.methodTag, "dummy",
                    config.typeTag, config.failureTypeTagValue)
                .count(),
            0.0001)
    }

    @Test
    fun measure_counts_failure_up_by_one_if_error_thrown() {
        try {
            metricsHelper.measure("dummy") {
                throw IOError(RuntimeException())
            }
        } catch (ex: IOError) {
            // ignoring on purpose
        }

        assertEquals(
            1.0,
            registry.counter(
                    config.measureMeterName,
                    config.methodTag, "dummy",
                    config.typeTag, config.failureTypeTagValue)
                .count(),
            0.0001)
    }

    @Test
    fun measure_registers_a_timer_too() {
        val mockClock = MockClock()
        val registry = SimpleMeterRegistry(SimpleConfig.DEFAULT, mockClock)
        val metricsHelper = MetricsHelper(registry)

        metricsHelper.measure("dummy") {
            // the logic you want counted - no exception happening her today
            mockClock.add(100, TimeUnit.MILLISECONDS)
        }

        val timer = registry.timer(
                "${config.measureMeterName}.${config.measureTimerSuffix}",
                config.methodTag, "dummy")
        assertEquals(1, timer.count())
        assertEquals(
                100.0,
                timer.totalTime(TimeUnit.MILLISECONDS),
                10.0)
    }

    @Test
    fun increment_increments_a_counter() {
        metricsHelper.increment(
                event = "myevent",
                eventType = "mottatt")

        assertEquals(1.0,
                registry.counter(
                        config.incrementMeterName,
                        config.eventTag, "myevent",
                        config.typeTag, "mottatt")
                        .count(),
                0.0001)

    }

    @Test
    fun increment_can_take_a_throwable() {
        metricsHelper.increment(
                event = "myevent",
                eventType = "failed",
                throwable = RuntimeException("BOOM!")
        )

        assertEquals(1.0,
                registry.counter(
                        config.incrementMeterName,
                        config.eventTag, "myevent",
                        config.typeTag, "failed")
                        .count(),
                0.0001)

    }
}

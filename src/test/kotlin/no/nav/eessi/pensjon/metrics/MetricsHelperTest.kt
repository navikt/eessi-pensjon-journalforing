package no.nav.eessi.pensjon.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.MockClock
import io.micrometer.core.instrument.simple.SimpleConfig
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper.Configuration.eventTag
import no.nav.eessi.pensjon.metrics.MetricsHelper.Configuration.failureTypeTagValue
import no.nav.eessi.pensjon.metrics.MetricsHelper.Configuration.incrementMeterName
import no.nav.eessi.pensjon.metrics.MetricsHelper.Configuration.measureMeterName
import no.nav.eessi.pensjon.metrics.MetricsHelper.Configuration.measureTimerSuffix
import no.nav.eessi.pensjon.metrics.MetricsHelper.Configuration.methodTag
import no.nav.eessi.pensjon.metrics.MetricsHelper.Configuration.successTypeTagValue
import no.nav.eessi.pensjon.metrics.MetricsHelper.Configuration.typeTag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOError
import java.util.concurrent.TimeUnit

internal class MetricsHelperTest {

    private lateinit var registry: MeterRegistry
    private lateinit var metricsHelper: MetricsHelper

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
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
                    measureMeterName,
                    methodTag, "dummy",
                    typeTag, successTypeTagValue)
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
                    measureMeterName,
                    methodTag, "dummy",
                    typeTag, failureTypeTagValue)
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
                    measureMeterName,
                    methodTag, "dummy",
                    typeTag, failureTypeTagValue)
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
                "$measureMeterName.$measureTimerSuffix",
                methodTag, "dummy")
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
                        incrementMeterName,
                        eventTag, "myevent",
                        typeTag, "mottatt")
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
                        incrementMeterName,
                        eventTag, "myevent",
                        typeTag, "failed")
                        .count(),
                0.0001)
    }
}

package no.nav.eessi.pensjon.journalforing.config

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

data class TimingObject(
        val timer: Timer,
        val start: Long
)

@Service
class TimingService(private val registry: MeterRegistry) {

    fun timedStart(tag: String): TimingObject {
        // you can keep a ref to this; ok to call multiple times, though
        val timer = Timer.builder("eessipensjon_journalforing").tag("tag", tag).register(registry)
        // manually do the timing calculation
        return TimingObject(timer, System.nanoTime())
    }

    fun timesStop(timerObject: TimingObject) {
        //get timer and starttime from Objet
        val timer = timerObject.timer
        val start = timerObject.start
        // manually do the timing calculation
        timer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS)
    }
}
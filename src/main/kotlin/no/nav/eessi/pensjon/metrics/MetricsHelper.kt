package no.nav.eessi.pensjon.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics

fun counter(name: String, type: String): Counter {
    return Metrics.counter(name, "type", type)
}

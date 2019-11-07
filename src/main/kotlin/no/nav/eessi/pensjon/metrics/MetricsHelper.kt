package no.nav.eessi.pensjon.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class MetricsHelper(val registry: MeterRegistry, @Autowired(required = false) val configuration: Configuration = Configuration()) {

    /**
     * Alle counters må legges inn i init listen slik at counteren med konkrete tagger blir initiert med 0.
     * Dette er nødvendig for at grafana alarmer skal fungere i alle tilfeller
     */
    @PostConstruct
    fun initCounters() {
        listOf("journalforOgOpprettOppgaveForSed",
                "consumeOutgoingSed",
                "consumeIncomingSed",
                "pdfConverter",
                "disoverSTS",
                "getSystemOidcToken",
                "aktoerregister",
                "hentSed",
                "hentpdf",
                "hentSeds",
                "hentSed",
                "hentYtelseKravtype",
                "opprettjournalpost",
                "hentArbeidsfordeling",
                "opprettoppgave",
                "hentperson"
                ).forEach {counterName ->
            Counter.builder(configuration.measureMeterName)
                    .tag(configuration.typeTag, configuration.successTypeTagValue)
                    .tag(configuration.methodTag, counterName)
                    .register(registry)

            Counter.builder(configuration.measureMeterName)
                    .tag(configuration.typeTag, configuration.failureTypeTagValue)
                    .tag(configuration.methodTag, counterName)
                    .register(registry)
        }
    }

    fun <R> measure(
            method: String,
            failure: String = configuration.failureTypeTagValue,
            success: String = configuration.successTypeTagValue,
            meterName: String = configuration.measureMeterName,
            eventType: String = configuration.callEventTypeTagValue,
            block: () -> R): R {

        var typeTag = success

        try {
            return Timer.builder("$meterName.${configuration.measureTimerSuffix}")
                    .tag(configuration.methodTag, method)
                    .register(registry)
                    .recordCallable {
                        block.invoke()
                    }
        } catch (throwable: Throwable) {
            typeTag = failure
            throw throwable
        } finally {
            try {
                Counter.builder(meterName)
                        .tag(configuration.methodTag, method)
                        .tag(configuration.typeTag, typeTag)
                        .register(registry)
                        .increment()
            } catch (e: Exception) {
                // ignoring on purpose
            }
        }
    }

    fun increment(
            event: String,
            eventType: String = configuration.eventTypeTagValue,
            throwable: Throwable? = null,
            meterName: String = configuration.incrementMeterName) {
        try {
            Counter.builder(meterName)
                    .tag(configuration.eventTag, event)
                    .tag(configuration.typeTag, eventType)
                    .register(registry)
                    .increment()
        } catch (t: Throwable) {
            // ignoring on purpose
        }
    }

    data class Configuration(
            val incrementMeterName: String = "event",
            val measureMeterName: String = "method",
            val measureTimerSuffix: String = "timer",

            val eventTag: String = "event",
            val methodTag: String = "method",
            val typeTag: String = "type",

            val successTypeTagValue: String = "successful",
            val failureTypeTagValue: String = "failed",

            val eventTypeTagValue: String = "occurred",

            val callEventTypeTagValue: String = "called"
    )
}
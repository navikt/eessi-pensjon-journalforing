package no.nav.eessi.pensjon.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class MetricsHelper(val registry: MeterRegistry) {

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
                "hentpdf",
                "hentSeds",
                "hentSed",
                "hentYtelseKravtype",
                "opprettjournalpost",
                "hentArbeidsfordeling",
                "opprettoppgave",
                "hentperson",
                "hentFodselsdatoFraBuc",
                "hentFnrFraBUC",
                "publiserOppgavemelding"
                ).forEach {counterName ->
            Counter.builder(measureMeterName)
                    .tag(typeTag, successTypeTagValue)
                    .tag(methodTag, counterName)
                    .register(registry)

            Counter.builder(measureMeterName)
                    .tag(typeTag, failureTypeTagValue)
                    .tag(methodTag, counterName)
                    .register(registry)
        }
    }

    fun <R> measure(
            method: String,
            failure: String = failureTypeTagValue,
            success: String = successTypeTagValue,
            meterName: String = measureMeterName,
            block: () -> R): R {

        var typeTagValue = success

        try {
            return Timer.builder("$meterName.$measureTimerSuffix")
                    .tag(methodTag, method)
                    .register(registry)
                    .recordCallable {
                        block.invoke()
                    }
        } catch (throwable: Throwable) {
            typeTagValue = failure
            throw throwable
        } finally {
            try {
                Counter.builder(meterName)
                        .tag(methodTag, method)
                        .tag(typeTag, typeTagValue)
                        .register(registry)
                        .increment()
            } catch (e: Exception) {
                // ignoring on purpose
            }
        }
    }

    fun increment(
            event: String,
            eventType: String,
            throwable: Throwable? = null,
            meterName: String = incrementMeterName) {
        try {
            Counter.builder(meterName)
                    .tag(eventTag, event)
                    .tag(typeTag, eventType)
                    .register(registry)
                    .increment()
        } catch (t: Throwable) {
            // ignoring on purpose
        }
    }

    companion object Configuration {
        const val incrementMeterName: String = "event"
        const val measureMeterName: String = "method"
        const val measureTimerSuffix: String = "timer"

        const val eventTag: String = "event"
        const val methodTag: String = "method"
        const val typeTag: String = "type"

        const val successTypeTagValue: String = "successful"
        const val failureTypeTagValue: String = "failed"
    }
}


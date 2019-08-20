package no.nav.eessi.pensjon.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class MetricsHelper(val registry: MeterRegistry, @Autowired(required = false) val configuration: Configuration = Configuration()) {

    fun <R> measure(
            method: String,
            failure: String = configuration.failureTypeTagValue,
            success: String = configuration.successTypeTagValue,
            meterName: String = configuration.measureMeterName,
            eventType: String = configuration.callEventTypeTagValue,
            extraTags: Array<String> = arrayOf(),
            description: String = "",
            block: () -> R): R {

        var throwableClass = configuration.noExceptionTypeTagValue
        var typeTag = success

        try {
            return Timer.builder("$meterName.${configuration.measureTimerSuffix}")
                    .description(if (description.isEmpty()) null else description)
                    .tag(configuration.methodTag, method)
                    .tags(*extraTags)
                    .register(registry)
                    .recordCallable {
                        block.invoke()
                    }
        } catch (throwable: Throwable) {
            throwableClass = throwable.javaClass.simpleName
            typeTag = failure
            throw throwable
        } finally {
            try {
                Counter.builder(meterName)
                        .description(if (description.isEmpty()) null else description)
                        .tags(*extraTags)
                        .tag(configuration.methodTag, method)
                        .tag(configuration.exceptionTag, throwableClass)
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
            extraTags: Array<String> = arrayOf(),
            throwable: Throwable? = null,
            meterName: String = configuration.incrementMeterName,
            description: String = "") {
        try {
            Counter.builder(meterName)
                    .description(if (description.isEmpty()) null else description)
                    .tag(configuration.eventTag, event)
                    .tags(*extraTags)
                    .tag(configuration.typeTag, eventType)
                    .tag(configuration.exceptionTag, if (throwable == null) configuration.noExceptionTypeTagValue else throwable.javaClass.simpleName)
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
            val exceptionTag: String = "exception",

            val successTypeTagValue: String = "successful",
            val failureTypeTagValue: String = "failed",

            val eventTypeTagValue: String = "occurred",

            val callEventTypeTagValue: String = "called",
            val noExceptionTypeTagValue: String = "none"
    )
}

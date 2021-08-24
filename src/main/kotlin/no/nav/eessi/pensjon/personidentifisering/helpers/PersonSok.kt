package no.nav.eessi.pensjon.personidentifisering.helpers

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class PersonSok(@Suppress("SpringJavaInjectionPointsAutowiringInspection") private val personService: PersonService,
                @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(PersonSok::class.java)
    private lateinit var sokPersonTellerTreff: Counter
    private lateinit var sokPersonTellerMiss: Counter

    @PostConstruct
    fun initMetrics() {
        sokPersonTellerTreff = Counter.builder("sokPersonTellerTreff")
            .tag(metricsHelper.configuration.typeTag, metricsHelper.configuration.successTypeTagValue)
            .tag(metricsHelper.configuration.alertTag, metricsHelper.configuration.toggleOffTagValue)
            .register(metricsHelper.registry)

        sokPersonTellerMiss = Counter.builder("sokPersonTellerMiss")
            .tag(metricsHelper.configuration.typeTag, metricsHelper.configuration.failureTypeTagValue)
            .tag(metricsHelper.configuration.alertTag, metricsHelper.configuration.toggleOffTagValue)
            .register(metricsHelper.registry)
    }


    fun sokEtterPerson(sokeKriterier: SokKriterier) : String? {
        logger.info("Utfører personsøk")
        logger.debug("SokKriterie: $sokeKriterier}")

        val sokPersonFnrTreff = sokeKriterier.let { personService.sokPerson(it) }
            .firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident
            .also { logger.info("Har gjort et treff på personsøk (${it!=null})") }
        if (sokPersonFnrTreff != null) {
            sokPersonTellerTreff.increment()
        } else {
            sokPersonTellerMiss.increment()
        }
        return sokPersonFnrTreff
    }
}
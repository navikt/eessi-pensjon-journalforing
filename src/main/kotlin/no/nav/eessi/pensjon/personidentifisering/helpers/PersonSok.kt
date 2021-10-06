package no.nav.eessi.pensjon.personidentifisering.helpers

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class PersonSok(
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val personService: PersonService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()) ) {

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

    fun sokPersonEtterFnr(personRelasjoner: List<SEDPersonRelasjon>, rinaDocumentId: String, bucType: BucType, sedType: SedType?, hendelsesType: HendelseType): SEDPersonRelasjon? {
        logger.info("PersonUtvelger *** SøkPerson *** ")

        val potensiellePersonRelasjoner = personRelasjoner.filter { relasjon -> relasjon.rinaDocumentId == rinaDocumentId }

        if (potensiellePersonRelasjoner.isEmpty()) {
            logger.info("Ingen gyldig sed for søkPerson")
            return null
        }
        logger.info("Har identifisert følgende personrelasjoner: ${potensiellePersonRelasjoner.map { it.relasjon }}, $sedType ")

        val personRelasjon = potensiellePersonRelasjoner.firstOrNull { it.relasjon == Relasjon.GJENLEVENDE }
            ?: potensiellePersonRelasjoner.firstOrNull { it.relasjon == Relasjon.FORSIKRET }

        logger.info("Personrelasjon sokekriterier: ${personRelasjon?.sokKriterier}, sedtype: ${personRelasjon?.sedType}, relasjon: ${personRelasjon?.relasjon}")

        personRelasjon?.sokKriterier?.let {
            pdlSokEtterFnr(it)?.let { fnr ->
                return personRelasjon.copy(fnr = Fodselsnummer.fra(fnr))
            }
        }

        return null
    }

    fun pdlSokEtterFnr(sokeKriterier: SokKriterier): String? {
        logger.info("Utfører personsøk etter fnr")

        val sokPersonFnrTreff = sokeKriterier.let { personService.sokPerson(it) }
            .firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident
        if (sokPersonFnrTreff != null) {
            logger.info("Har gjort et treff på personsøk")
            sokPersonTellerTreff.increment()
        } else {
            logger.info("IKKE treff på personsøk")
            sokPersonTellerMiss.increment()
        }
        return sokPersonFnrTreff
    }
}
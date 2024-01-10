package no.nav.eessi.pensjon.personidentifisering.helpers

import io.micrometer.core.instrument.Counter
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.FOLKEREGISTERIDENT
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.NPID
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PersonSok(
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val personService: PersonService,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest() ) {

    private val logger = LoggerFactory.getLogger(PersonSok::class.java)
    private lateinit var sokPersonTellerTreff: Counter
    private lateinit var sokPersonTellerMiss: Counter

    init {
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
        logger.info("PersonUtvelger *** SøkPerson *** rinadokumentId: $rinaDocumentId sedType: $sedType bucType: $bucType personrelasjoner: ${personRelasjoner.map { it.relasjon }} hendelsesType")

        val potensiellePersonRelasjoner = if (bucType == BucType.P_BUC_02 || sedType in listOf(H070, H120, H121)) personRelasjoner
        else personRelasjoner.filter { relasjon -> relasjon.rinaDocumentId == rinaDocumentId }

        if (potensiellePersonRelasjoner.isEmpty()) {
            logger.info("Ingen personrelasjoner funnet i sed (personSøk).")
            return null
        }
        logger.info("Har identifisert følgende personrelasjoner: ${potensiellePersonRelasjoner.map { it.relasjon }}, $sedType ")

        val personRelasjon = potensiellePersonRelasjoner.firstOrNull { it.relasjon == Relasjon.GJENLEVENDE }
            ?: potensiellePersonRelasjoner.firstOrNull { it.relasjon == Relasjon.FORSIKRET }

        logger.info("Personrelasjon: ${personRelasjon?.relasjon} (søkPerson).")
        logger.debug("Personrelasjon sokekriterier: ${personRelasjon?.sokKriterier}, sedtype: ${personRelasjon?.sedType}, relasjon: ${personRelasjon?.relasjon}")

        personRelasjon?.sokKriterier?.let {
            pdlSokEtterFnrEllerNpid(it)?.let { fnr ->
                return personRelasjon.copy(fnr = Fodselsnummer.fra(fnr))
            }
        }
        return null

    }

    fun pdlSokEtterFnrEllerNpid(sokeKriterier: SokKriterier): String? {
        logger.info("Utfører personsøk etter fnr eller npid")

        val sokPersonFnrTreff = sokeKriterier.let { personService.sokPerson(it) }
            .firstOrNull { it.gruppe == FOLKEREGISTERIDENT || it.gruppe == NPID }?.ident
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
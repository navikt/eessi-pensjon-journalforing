package no.nav.eessi.pensjon.personidentifisering.helpers

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDate
import javax.annotation.PostConstruct

@Component
class PersonSok(@Suppress("SpringJavaInjectionPointsAutowiringInspection") private val personService: PersonService,
                @Autowired private val fnrHelper: FnrHelper,
                @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val brukForikretPersonISed = listOf(SedType.H121, SedType.H120, SedType.H070)
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

    fun sokPersonUtvelger(
        navBruker: Fodselsnummer?,
        sedListe: List<Pair<String, SED>>,
        rinaDocumentId: String,
        bucType: BucType,
        sedType: SedType?,
        hendelsesType: HendelseType
    ): IdentifisertPerson? {
        logger.info("PersonUtvelger *** SøkPerson *** ")

        val sedFraHendelse = sedListe.firstOrNull { it.first == rinaDocumentId }
        if (sedFraHendelse == null) {
            logger.info("Ingen gyldig sed for søkPerson")
            return null
        }

        val potensiellePersonRelasjoner = fnrHelper.getPotensielleFnrFraSeder(listOf(sedFraHendelse), bucType)

        val identifisertePersoner = hentIdentifisertePersoner(
            sedListe,
            bucType,
            potensiellePersonRelasjoner,
            hendelsesType
        )
        return identifisertPersonUtvelger(identifisertePersoner, bucType, sedType, potensiellePersonRelasjoner)
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

    fun hentIdentifisertPerson(
        personRelasjon: SEDPersonRelasjon,
        alleSediBuc: List<Pair<String, SED>>,
        hendelsesType: HendelseType,
        bucType: BucType,
        benyttSokPerson: Boolean = false
    ): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${personRelasjon.toJson()}")

        return try {

            val valgtFnr = sokEtterPerson(personRelasjon.sokKriterier!!)

            if (valgtFnr == null) {
                logger.info("Ingen gyldig ident, går ut av hentIdentifisertPerson!")
                return null
            }

            logger.debug("Henter person med fnr. $valgtFnr fra PDL")
            personService.hentPerson(NorskIdent(valgtFnr))
                ?.let { person ->
                    populerIdentifisertPerson(
                        person,
                        alleSediBuc,
                        personRelasjon,
                        hendelsesType
                    )
                }
                ?.also {
                    logger.debug(
                        """IdentifisertPerson hentet fra PDL (aktoerId: ${it.aktoerId}, landkode: ${it.landkode}, 
                                    navn: ${it.personNavn}, sed: ${it.personRelasjon.sedType?.name})""".trimIndent()
                    )
                }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
            null
        }
    }

    private fun populerIdentifisertPerson(
        person: Person,
        alleSediBuc: List<Pair<String, SED>>,
        sedPersonRelasjon: SEDPersonRelasjon,
        hendelsesType: HendelseType
    ): IdentifisertPerson {
        logger.debug("Populerer IdentifisertPerson med data fra PDL hendelseType: $hendelsesType")

        val personNavn = person.navn?.run { "$fornavn $etternavn" }
        val aktorId = person.identer.firstOrNull { it.gruppe == IdentGruppe.AKTORID }?.ident ?: ""
        val personFnr = person.identer.first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }.ident
        val adressebeskyttet = finnesPersonMedAdressebeskyttelse(alleSediBuc)
        val geografiskTilknytning = person.geografiskTilknytning?.gtKommune
        val landkode = hentLandkode(person)
        val newPersonRelasjon = sedPersonRelasjon.copy(fnr = Fodselsnummer.fra(personFnr))

        return IdentifisertPerson(
            aktorId,
            personNavn,
            adressebeskyttet,
            landkode,
            geografiskTilknytning,
            newPersonRelasjon
        )
    }

    private fun finnesPersonMedAdressebeskyttelse(alleSediBuc: List<Pair<String, SED>>): Boolean {
        val alleSedTyper = alleSediBuc.map { it.second.type}.toJson()
        logger.info("Leter etter personer med adressebeskyttelse i : $alleSedTyper")
        val fnr = alleSediBuc.flatMap { SedFnrSok.finnAlleFnrDnrISed(it.second) }
        val gradering =
            listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND, AdressebeskyttelseGradering.STRENGT_FORTROLIG)

        return personService.harAdressebeskyttelse(fnr, gradering)
            .also { logger.debug("Finnes adressebeskyttet person: $it") }
    }

    private fun hentLandkode(person: Person): String {
        val landkodeOppholdKontakt = person.kontaktadresse?.utenlandskAdresseIFrittFormat?.landkode
        val landkodeUtlandsAdresse = person.kontaktadresse?.utenlandskAdresse?.landkode
        val landkodeOppholdsadresse = person.oppholdsadresse?.utenlandskAdresse?.landkode
        val landkodeBostedsadresse = person.bostedsadresse?.utenlandskAdresse?.landkode
        val geografiskLandkode = person.geografiskTilknytning?.gtLand
        val landkodeBostedNorge = person.bostedsadresse?.vegadresse
        val landkodeKontaktNorge = person.kontaktadresse?.postadresseIFrittFormat

        logger.debug("Landkode og person: ${person.toJson()}")

        return when {
            landkodeOppholdKontakt != null -> landkodeOppholdKontakt
            landkodeUtlandsAdresse != null -> landkodeUtlandsAdresse
            landkodeOppholdsadresse != null -> landkodeOppholdsadresse
            landkodeBostedsadresse != null -> landkodeBostedsadresse
            geografiskLandkode != null -> geografiskLandkode
            landkodeBostedNorge != null -> "NOR"
            landkodeKontaktNorge != null -> "NOR"
            else -> {
                logger.warn("Ingen landkode funnet settes til ''")
                ""
            }
        }
    }

    fun hentIdentifisertePersoner(
        alleSediBuc: List<Pair<String, SED>>,
        bucType: BucType,
        potensielleSEDPersonRelasjoner: List<SEDPersonRelasjon>,
        hendelsesType: HendelseType
    ): List<IdentifisertPerson> {
           return  potensielleSEDPersonRelasjoner
                .mapNotNull { relasjon ->
                    hentIdentifisertPerson(
                        relasjon,
                        alleSediBuc,
                        hendelsesType,
                        bucType,
                        true
                    )
                }
                .distinctBy { it.aktoerId }
    }

    /**
     * Forsøker å finne om identifisert person er en eller fler med avdød person
     */
    /**
     * Forsøker å finne om identifisert person er en eller fler med avdød person
     */
    fun identifisertPersonUtvelger(
        identifisertePersoner: List<IdentifisertPerson>,
        bucType: BucType,
        sedType: SedType?,
        potensielleSEDPersonRelasjoner: List<SEDPersonRelasjon>
    ): IdentifisertPerson? {
        logger.info("IdentifisertePersoner $bucType, SedType: ${sedType?.name}, antall identifisertePersoner : ${identifisertePersoner.size} ")

        val forsikretPerson = brukForsikretPerson(sedType, identifisertePersoner)
        if (forsikretPerson != null)
            return forsikretPerson

        return when {
            identifisertePersoner.isEmpty() -> null
            bucType == BucType.R_BUC_02 -> identifisertePersoner.first().apply { personListe = identifisertePersoner }
            bucType == BucType.P_BUC_02 -> identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }
            bucType == BucType.P_BUC_05 -> {
                val erGjenlevendeRelasjon = potensielleSEDPersonRelasjoner.any { it.relasjon == Relasjon.GJENLEVENDE }
                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeRelasjon)
            }
            bucType == BucType.P_BUC_10 -> {
                val erGjenlevendeYtelse = potensielleSEDPersonRelasjoner.any { it.saktype == Saktype.GJENLEV }

                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeYtelse)
            }
            identifisertePersoner.size == 1 -> identifisertePersoner.first()
            else -> {
                logger.debug("BucType: $bucType Personer: ${identifisertePersoner.toJson()}")
                throw RuntimeException("Stopper grunnet flere personer på bucType: $bucType")
            }
        }
    }

    //felles for P_BUC_05 og P_BUC_10
    private fun utvelgerPersonOgGjenlev(
        identifisertePersoner: List<IdentifisertPerson>,
        erGjenlevende: Boolean
    ): IdentifisertPerson? {
        identifisertePersoner.forEach {
            logger.debug(it.toJson())
        }
        val forsikretPerson = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
        val gjenlevendePerson = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }
        logger.info("personAktoerid: ${forsikretPerson?.aktoerId}, gjenlevAktoerid: ${gjenlevendePerson?.aktoerId}, harGjenlvRelasjon: $erGjenlevende")

        return when {
            gjenlevendePerson != null -> gjenlevendePerson
            erGjenlevende -> null
            else -> {
                forsikretPerson?.apply {
                    personListe = identifisertePersoner.filterNot { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
                }
            }
        }
    }

    /**
     * Noen Seder kan kun inneholde forsikret person i de tilfeller benyttes den forsikrede selv om andre Sed i Buc inneholder andre personer
     */
    /**
     * Noen Seder kan kun inneholde forsikret person i de tilfeller benyttes den forsikrede selv om andre Sed i Buc inneholder andre personer
     */
    private fun brukForsikretPerson(
        sedType: SedType?,
        identifisertePersoner: List<IdentifisertPerson>
    ): IdentifisertPerson? {
        if (sedType in brukForikretPersonISed) {
            logger.info("Henter ut forsikret person fra følgende SED $sedType")
            return identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
        }
        return null
    }

    //finne korrekt fdato fra SED på current rinadocid (sed på hendelsen)
    fun injectFdatoFraSedPersonNavBruker(alleSediBuc: List<Pair<String, SED>>, rinaDocumentId: String): LocalDate? {
        logger.debug("rinaDocumentId $rinaDocumentId")
        val currentSed = alleSediBuc.firstOrNull { it.first == rinaDocumentId }?.second
        logger.debug("CurrentSED type: ${currentSed?.type}")

        val fdato = currentSed?.let { FodselsdatoHelper.fdatoFraSedListe(listOf(it), emptyList()) }
        logger.debug("Navbruker fdato-fraSed: $fdato, sedtype: ${currentSed?.type}")
        return fdato
    }


}
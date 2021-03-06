package no.nav.eessi.pensjon.personidentifisering

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FodselsdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personidentifisering.helpers.SedFnrSok
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.LocalDate
import javax.annotation.PostConstruct

@Component
class PersonidentifiseringService(
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val personService: PersonService,
    private val fnrHelper: FnrHelper,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)
    private val brukForikretPersonISed = listOf(SedType.H121, SedType.H120, SedType.H070)
    private lateinit var sokPersonTellerTreff: Counter
    private lateinit var sokPersonTellerMiss: Counter

    @Value("\${namespace}")
    lateinit var nameSpace: String

    companion object {
        fun erFnrDnrFormat(id: String?): Boolean {
            return id != null && id.length == 11 && id.isNotBlank()
        }
    }

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

    fun validateIdentifisertPerson(identifisertPerson: IdentifisertPerson, hendelsesType: HendelseType, erNavCaseOwner: Boolean): IdentifisertPerson? {
        return if (hendelsesType == HendelseType.MOTTATT) {

            val check =  identifisertPerson.personRelasjon.validateFnrOgDato()
            if(check) {
                logger.info("valider: $check, fnr-dato: ${identifisertPerson.personRelasjon.fnr?.getBirthDate()}, sed-fdato: ${identifisertPerson.personRelasjon.fdato}, $hendelsesType, Nav CaseOwner: $erNavCaseOwner")
                validerCounter("successful")
                identifisertPerson
            } else {
                logger.warn("valider: $check, fnr-dato: ${identifisertPerson.personRelasjon.fnr?.getBirthDate()}, sed-fdato: ${identifisertPerson.personRelasjon.fdato}, $hendelsesType, Nav CaseOwner: $erNavCaseOwner")
                validerCounter("failed")
                null
            }

        } else {
            identifisertPerson
        }
    }

    private fun validerCounter(typeValue: String) {
        try {
            Metrics.counter("validerPerson", "hendelse", "MOTTATT", "type", "$typeValue").increment()
        } catch (ex: Exception) {
            logger.warn("Metrics feilet på validerPerson value: $typeValue")
        }
    }

    fun hentIdentifisertPerson(
        navBruker: Fodselsnummer?,
        sedListe: List<Pair<String, SED>>,
        bucType: BucType,
        sedType: SedType?,
        hendelsesType: HendelseType,
        rinaDocumentId: String,
        erNavCaseOwner: Boolean = false
    ): IdentifisertPerson? {

        val potensiellePersonRelasjoner = fnrHelper.getPotensielleFnrFraSeder(sedListe)
        val identifisertePersoner = hentIdentifisertePersoner(
            navBruker,
            sedListe,
            bucType,
            potensiellePersonRelasjoner,
            hendelsesType,
            rinaDocumentId = rinaDocumentId
        )

        val identifisertPerson = identifisertPersonUtvelger(identifisertePersoner, bucType, sedType, potensiellePersonRelasjoner)

        if (identifisertPerson != null) {
            return validateIdentifisertPerson(identifisertPerson, hendelsesType, erNavCaseOwner)
        }

        logger.warn("Klarte ikke å finne identifisertPerson, prøver søkPerson")
        return sokPersonUtvelger(navBruker, sedListe, rinaDocumentId, bucType, sedType, hendelsesType)
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

        val sokSedliste = sedListe.firstOrNull { it.first == rinaDocumentId }
        if (sokSedliste == null) {
            logger.info("Ingen gyldig sed for søkPerson")
            sokPersonTellerMiss.increment()
            return null
        }

        val potensiellePersonRelasjoner = fnrHelper.getPotensielleFnrFraSeder(listOf(sokSedliste))

        val identifisertePersoner = hentIdentifisertePersoner(
            null,
            sedListe,
            bucType,
            potensiellePersonRelasjoner,
            hendelsesType,
            true,
            rinaDocumentId
        )
        return identifisertPersonUtvelger(identifisertePersoner, bucType, sedType, potensiellePersonRelasjoner)
    }

    fun hentIdentifisertePersoner(
        navBruker: Fodselsnummer?,
        alleSediBuc: List<Pair<String, SED>>,
        bucType: BucType,
        potensielleSEDPersonRelasjoner: List<SEDPersonRelasjon>,
        hendelsesType: HendelseType,
        benyttSokPerson: Boolean = false,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {
        logger.info("Forsøker å identifisere personen")

        val personForNavBruker = when {
            bucType == BucType.P_BUC_02 -> null
            bucType == BucType.P_BUC_05 -> null
            bucType == BucType.P_BUC_06 -> null
            bucType == BucType.P_BUC_10 -> null
            navBruker != null -> {
                try {
                    personService.hentPerson(NorskIdent(navBruker.value))
                } catch (ex: Exception) {
                    logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
                    null
                }
            }
            else -> null
        }

        return if (personForNavBruker != null) {
            logger.info("*** Funnet identifisertPerson: personForNavBruker, fra hendelse: $hendelsesType, bucType: $bucType ***")
             val identifisertPerson = populerIdentifisertPerson(
                    personForNavBruker,
                    alleSediBuc,
                    SEDPersonRelasjon(navBruker!!, Relasjon.FORSIKRET, fdato = injectFdatoFraSedPersonNavBruker(alleSediBuc, rinaDocumentId)),
                    hendelsesType,
                    bucType
            )
            listOf(identifisertPerson)
        } else {
            // Leser inn fnr fra utvalgte seder
            logger.info("Forsøker å identifisere personer ut fra SEDer i BUC: $bucType")
            potensielleSEDPersonRelasjoner
                //.filterNot { it.fnr == null }
                .mapNotNull { relasjon ->
                    hentIdentifisertPerson(
                        relasjon,
                        alleSediBuc,
                        hendelsesType,
                        bucType,
                        benyttSokPerson
                    )
                }
                .distinctBy { it.aktoerId }
        }
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

    fun hentIdentifisertPerson(
        personRelasjon: SEDPersonRelasjon,
        alleSediBuc: List<Pair<String, SED>>,
        hendelsesType: HendelseType,
        bucType: BucType,
        benyttSokPerson: Boolean = false
    ): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${personRelasjon.toJson()}")

        return try {

            val valgtFnr = if (benyttSokPerson) {
                logger.info("Utfører personsøk")
                logger.debug("SokKriterie: ${personRelasjon.sokKriterier}")

                val sokPersonFnrTreff = personRelasjon.sokKriterier?.let { personService.sokPerson(it) }
                    ?.firstOrNull { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }?.ident
                    .also { logger.info("Har gjort et treff på personsøk (${it!=null})") }
                if (sokPersonFnrTreff != null) {
                    sokPersonTellerTreff.increment()
                } else {
                    sokPersonTellerMiss.increment()
                }
                sokPersonFnrTreff
            } else {
                personRelasjon.fnr?.value
            }

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
                        hendelsesType,
                        bucType
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
        hendelsesType: HendelseType,
        bucType: BucType
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
        val fnr = alleSediBuc.flatMap { SedFnrSok.finnAlleFnrDnrISed(it.second) }
        logger.info("Fant ${fnr.size} unike fnr i SEDer tilknyttet Buc")

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

    /**
     * Henter første treff på dato fra listen av SEDer
     */
    /**
     * Henter første treff på dato fra listen av SEDer
     */
    fun hentFodselsDato(
        identifisertPerson: IdentifisertPerson?,
        seder: List<SED>,
        kansellerteSeder: List<SED>
    ): LocalDate? {
        return identifisertPerson?.personRelasjon?.fnr?.getBirthDate()
            ?: FodselsdatoHelper.fdatoFraSedListe(seder, kansellerteSeder)

    }
}






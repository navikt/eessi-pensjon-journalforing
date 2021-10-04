package no.nav.eessi.pensjon.personidentifisering

//import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.sed.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.personidentifisering.helpers.FodselsdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.PersonSok
import no.nav.eessi.pensjon.personidentifisering.helpers.SedFnrSok
import no.nav.eessi.pensjon.personidentifisering.relasjoner.GenericRelasjon
import no.nav.eessi.pensjon.personidentifisering.relasjoner.P15000Relasjon
import no.nav.eessi.pensjon.personidentifisering.relasjoner.P2000Relasjon
import no.nav.eessi.pensjon.personidentifisering.relasjoner.P2100Relasjon
import no.nav.eessi.pensjon.personidentifisering.relasjoner.P6000Relasjon
import no.nav.eessi.pensjon.personidentifisering.relasjoner.P8000AndP10000Relasjon
import no.nav.eessi.pensjon.personidentifisering.relasjoner.R005Relasjon
import no.nav.eessi.pensjon.personidentifisering.relasjoner.T2000TurboRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PersonidentifiseringService(
    @Autowired private val personSok: PersonSok,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val personService: PersonService,
) {
    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)
    private val brukForikretPersonISed = listOf(SedType.H121, SedType.H120, SedType.H070)

    fun validateIdentifisertPerson(identifisertPerson: IdentifisertPerson, hendelsesType: HendelseType, erNavCaseOwner: Boolean): IdentifisertPerson? {
        return if (hendelsesType == HendelseType.MOTTATT) {
            val isFnrDnrFdatoLikSedFdato =  identifisertPerson.personRelasjon.isFnrDnrSinFdatoLikSedFdato()

            if(isFnrDnrFdatoLikSedFdato) {
                logger.info("valider: $isFnrDnrFdatoLikSedFdato, fnr-dato: ${identifisertPerson.personRelasjon.fnr?.getBirthDate()}, sed-fdato: ${identifisertPerson.personRelasjon.fdato}, $hendelsesType, Nav CaseOwner: $erNavCaseOwner")
                validerCounter("successful")
                identifisertPerson
            } else {
                logger.warn("valider: $isFnrDnrFdatoLikSedFdato, fnr-dato: ${identifisertPerson.personRelasjon.fnr?.getBirthDate()}, sed-fdato: ${identifisertPerson.personRelasjon.fdato}, $hendelsesType, Nav CaseOwner: $erNavCaseOwner")
                validerCounter("failed")
                null
            }
        } else {
            identifisertPerson
        }
    }

    private fun validerCounter(typeValue: String) {
        try {
            Metrics.counter("validerPerson", "hendelse", "MOTTATT", "type", typeValue).increment()
        } catch (ex: Exception) {
            logger.warn("Metrics feilet på validerPerson value: $typeValue")
        }
    }

    fun hentIdentifisertPerson(
        sedListe: List<Pair<String, SED>>,
        bucType: BucType,
        sedType: SedType?,
        hendelsesType: HendelseType,
        rinaDocumentId: String,
        erNavCaseOwner: Boolean = false
    ): IdentifisertPerson? {

        val potensiellePersonRelasjoner = hentRelasjoner(sedListe, bucType)

        val identifisertePersoner = hentIdentifisertePersoner(sedListe, bucType, potensiellePersonRelasjoner, hendelsesType, rinaDocumentId)

        val identifisertPerson = try {
            identifisertPersonUtvelger(identifisertePersoner, bucType, sedType, potensiellePersonRelasjoner)
        } catch (fppbe: FlerePersonPaaBucException) {
            logger.warn("Flere personer funnet i $bucType, returnerer null")
            //flere personer på buc return null for id og fordeling
            return null
        }

        if (identifisertPerson != null) {
            return validateIdentifisertPerson(identifisertPerson, hendelsesType, erNavCaseOwner)
        }

        logger.warn("Klarte ikke å finne identifisertPerson, prøver søkPerson")
        personSok.sokPersonEtterFnr(sedListe, rinaDocumentId, bucType, sedType, hendelsesType)
        ?.let { personRelasjon -> return hentIdentifisertPerson(personRelasjon, hendelsesType) }

        return null
    }

    private fun hentRelasjoner(seder: List<Pair<String, SED>>, bucType: BucType): List<SEDPersonRelasjon> {
            val fnrListe = mutableSetOf<SEDPersonRelasjon>()
            val sedMedForsikretPrioritet = listOf(SedType.H121, SedType.H120, SedType.H070)

            seder.forEach { (_,sed) ->
                try {
                    getRelasjonHandler(sed, bucType)?.let { handler ->
                        fnrListe.addAll(handler.hentRelasjoner())
                    }

                } catch (ex: Exception) {
                    logger.warn("Noe gikk galt under innlesing av fnr fra sed", ex)
                }
            }


        val resultat = fnrListe
            .filter { it.erGyldig() || it.sedType in sedMedForsikretPrioritet }
            .filterNot { it.filterUbrukeligeElemeterAvSedPersonRelasjon() }
            .sortedBy { it.relasjon }

        return resultat.ifEmpty { fnrListe.distinctBy { it.fnr } }

    }

    fun getRelasjonHandler(sed: SED, bucType: BucType): T2000TurboRelasjon? {

        if (sed.type.kanInneholdeIdentEllerFdato()) {
            return when (sed.type) {
                SedType.R005 -> R005Relasjon(sed,bucType)
                SedType.P2000 -> P2000Relasjon(sed, bucType)
                SedType.P2200 -> P2000Relasjon(sed, bucType)
                SedType.P2100 -> P2100Relasjon(sed, bucType)
                SedType.P8000, SedType.P10000 -> P8000AndP10000Relasjon(sed, bucType)
                SedType.P6000 -> P6000Relasjon(sed, bucType)
                SedType.P15000 -> P15000Relasjon(sed, bucType)

                SedType.H070, SedType.H120, SedType.H121 -> GenericRelasjon(sed, bucType)
                else -> GenericRelasjon(sed, bucType)
            }
        }
        return null
    }

    fun hentIdentifisertePersoner(
        alleSediBuc: List<Pair<String, SED>>,
        bucType: BucType,
        potensielleSEDPersonRelasjoner: List<SEDPersonRelasjon>,
        hendelsesType: HendelseType,
        rinaDocumentId: String
    ): List<IdentifisertPerson> {

        val distinctByPotensielleSEDPersonRelasjoner = potensielleSEDPersonRelasjoner.distinctBy { relasjon -> relasjon.fnr }
        logger.info("Forsøker å identifisere personer ut fra følgende SED: ${distinctByPotensielleSEDPersonRelasjoner.map { "${it.relasjon}, ${it.sedType}" }}, BUC: $bucType")

            return distinctByPotensielleSEDPersonRelasjoner
                .mapNotNull { relasjon ->
                    hentIdentifisertPerson(relasjon, hendelsesType)
                }
                .distinctBy { it.aktoerId }
    }

    fun hentIdentifisertPerson(
        personRelasjon: SEDPersonRelasjon, hendelsesType: HendelseType
    ): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${personRelasjon.toJson()}")

        return try {
            logger.info("Velger fnr med relasjon: ${personRelasjon.relasjon} i SED: ${personRelasjon.sedType}")
            val valgtFnr = personRelasjon.fnr?.value

            if (valgtFnr == null) {
                logger.info("Ingen gyldig ident, går ut av hentIdentifisertPerson!")
                return null
            }

            logger.debug("Henter person med fnr. $valgtFnr fra PDL")
            personService.hentPerson(NorskIdent(valgtFnr))
                ?.let { person ->
                    populerIdentifisertPerson(
                        person,
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
        sedPersonRelasjon: SEDPersonRelasjon,
        hendelsesType: HendelseType
    ): IdentifisertPerson {
        logger.debug("Populerer IdentifisertPerson med data fra PDL hendelseType: $hendelsesType")

        val personNavn = person.navn?.run { "$fornavn $etternavn" }
        val aktorId = person.identer.firstOrNull { it.gruppe == IdentGruppe.AKTORID }?.ident ?: ""
        val personFnr = person.identer.first { it.gruppe == IdentGruppe.FOLKEREGISTERIDENT }.ident
        val geografiskTilknytning = person.geografiskTilknytning?.gtKommune
        val landkode = hentLandkode(person)
        val newPersonRelasjon = sedPersonRelasjon.copy(fnr = Fodselsnummer.fra(personFnr))

        return IdentifisertPerson(
            aktorId,
            personNavn,
            landkode,
            geografiskTilknytning,
            newPersonRelasjon
        )
    }

    fun finnesPersonMedAdressebeskyttelseIBuc(alleSediBuc: List<Pair<String, SED>>): Boolean {
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
        logger.info("Antall identifisertePersoner : ${identifisertePersoner.size} ")

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
            //buc_01,buc_03 hvis flere enn en forsikret person så sendes til id_og_fordeling
            bucType == BucType.P_BUC_01 && (identifisertePersoner.size > 1) -> throw FlerePersonPaaBucException()
            bucType == BucType.P_BUC_03 && (identifisertePersoner.size > 1) -> throw FlerePersonPaaBucException()

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

class FlerePersonPaaBucException(): Exception()




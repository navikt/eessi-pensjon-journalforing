package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
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
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PersonidentifiseringService(
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val personService: PersonService,
    private val fnrHelper: FnrHelper) {

    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)
    private val brukForikretPersonISed = listOf(SedType.H121, SedType.H120, SedType.H070)

    companion object {
        fun erFnrDnrFormat(id: String?): Boolean {
            return id != null && id.length == 11 && id.isNotBlank()
        }
    }

    fun hentIdentifisertPerson(
        navBruker: Fodselsnummer?,
        sedListe: List<SED>,
        bucType: BucType,
        sedType: SedType?,
        hendelsesType: HendelseType
    ): IdentifisertPerson? {
        val potensiellePersonRelasjoner = fnrHelper.getPotensielleFnrFraSeder(sedListe)
        val identifisertePersoner = hentIdentifisertePersoner(
            navBruker,
            sedListe,
            bucType,
            potensiellePersonRelasjoner,
            hendelsesType
        )
        return identifisertPersonUtvelger(identifisertePersoner, bucType, sedType, potensiellePersonRelasjoner)
    }

    fun hentIdentifisertePersoner(
        navBruker: Fodselsnummer?,
        alleSediBuc: List<SED>,
        bucType: BucType,
        potensiellePersonRelasjoner: List<PersonRelasjon>,
        hendelsesType: HendelseType
    ): List<IdentifisertPerson> {
        logger.info("Forsøker å identifisere personen")

        val personForNavBruker = when {
            bucType == BucType.P_BUC_02 -> null
            bucType == BucType.P_BUC_05 -> null
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
            listOf(populerIdentifisertPerson(
                personForNavBruker,
                alleSediBuc,
                PersonRelasjon(navBruker!!, Relasjon.FORSIKRET),
                hendelsesType,
                bucType
            ))
        } else {
            // Leser inn fnr fra utvalgte seder
            logger.info("Forsøker å identifisere personer ut fra SEDer i BUC: $bucType")
            potensiellePersonRelasjoner
                .filterNot { it.fnr == null }
                .mapNotNull { relasjon -> hentIdentifisertPerson(relasjon, alleSediBuc, hendelsesType, bucType) }
                .distinctBy { it.aktoerId }
        }
    }

    fun hentIdentifisertPerson(
        relasjon: PersonRelasjon,
        alleSediBuc: List<SED>,
        hendelsesType: HendelseType,
        bucType: BucType
    ): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${relasjon.toJson()}")

        return try {
            val fnr = relasjon.fnr!!.value
            logger.debug("Henter person med fnr. $fnr fra PDL")

            personService.hentPerson(NorskIdent(fnr))
                    ?.let { person -> populerIdentifisertPerson(person, alleSediBuc, relasjon, hendelsesType, bucType) }
                    ?.also {
                        logger.debug("""IdentifisertPerson hentet fra PDL (aktoerId: ${it.aktoerId}, landkode: ${it.landkode}, 
                                    navn: ${it.personNavn}, sed: ${it.personRelasjon.sedType?.name})""".trimIndent())
                    }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
            null
        }
    }

    private fun populerIdentifisertPerson(
        person: Person,
        alleSediBuc: List<SED>,
        personRelasjon: PersonRelasjon,
        hendelsesType: HendelseType,
        bucType: BucType
    ): IdentifisertPerson {
        logger.debug("Populerer IdentifisertPerson med data fra PDL")

        val personNavn = person.navn?.run { "$fornavn $etternavn" }
        val aktorId = person.identer.firstOrNull { it.gruppe == IdentGruppe.AKTORID }?.ident ?: ""
        val adressebeskyttet = finnesPersonMedAdressebeskyttelse(alleSediBuc)
        val geografiskTilknytning = person.geografiskTilknytning?.gtKommune
        val landkode = hentLandkode(person, hendelsesType,bucType)

        return IdentifisertPerson(
                aktorId,
                personNavn,
                adressebeskyttet,
                landkode,
                geografiskTilknytning,
                personRelasjon
        )
    }

    private fun finnesPersonMedAdressebeskyttelse(alleSediBuc: List<SED>): Boolean {
        val fnr = alleSediBuc.flatMap { SedFnrSok.finnAlleFnrDnrISed(it) }
        logger.info("Fant ${fnr.size} unike fnr i SEDer tilknyttet Buc")

        val gradering = listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND, AdressebeskyttelseGradering.STRENGT_FORTROLIG)

        return personService.harAdressebeskyttelse(fnr, gradering)
            .also { logger.info("Finnes adressebeskyttet person: $it") }
    }

    private fun hentLandkode(person: Person, hendelsesType: HendelseType, bucType: BucType): String {
        val landkodeOppholdKontakt =  person.kontaktadresse?.utenlandskAdresseIFrittFormat?.landkode
        val landkodeUtlandsAdresse = person.kontaktadresse?.utenlandskAdresse?.landkode
        val landkodeOppholdsadresse = person.oppholdsadresse?.utenlandskAdresse?.landkode
        val landkodeBostedsadresse = person.bostedsadresse?.utenlandskAdresse?.landkode
        val geografiskLandkode = person.geografiskTilknytning?.gtLand
        val landkodeBostedNorge = person.bostedsadresse?.vegadresse
        val landkodeKontaktNorge = person.kontaktadresse?.postadresseIFrittFormat
        val erMottatt = hendelsesType == HendelseType.MOTTATT
        val erKravBuc = bucType in listOf(BucType.P_BUC_01, BucType.P_BUC_02, BucType.P_BUC_03)

        logger.debug("Landkode og person: ${person.toJson()}")

        return when {
            landkodeOppholdKontakt != null -> landkodeOppholdKontakt
            landkodeUtlandsAdresse != null -> landkodeUtlandsAdresse
            landkodeOppholdsadresse != null -> landkodeOppholdsadresse
            landkodeBostedsadresse != null -> landkodeBostedsadresse
            geografiskLandkode != null && !(erKravBuc && erMottatt) -> geografiskLandkode
            landkodeBostedNorge != null && !(erKravBuc && erMottatt) -> "NOR"
            landkodeKontaktNorge != null && !(erKravBuc && erMottatt)  -> "NOR"
            else -> {
                logger.warn("Ingen landkode funnet settes til ''")
                ""
            }
        }
    }

    /**
     * Forsøker å finne om identifisert person er en eller fler med avdød person
     */
    fun identifisertPersonUtvelger(identifisertePersoner: List<IdentifisertPerson>, bucType: BucType, sedType: SedType?, potensiellePersonRelasjoner: List<PersonRelasjon>): IdentifisertPerson? {
        logger.info("IdentifisertePersoner $bucType, SedType: ${sedType?.name}, antall identifisertePersoner : ${identifisertePersoner.size} ")

        val forsikretPerson = brukForsikretPerson(sedType, identifisertePersoner)
        if (forsikretPerson != null)
            return forsikretPerson

        return when {
            identifisertePersoner.isEmpty() -> null
            bucType == BucType.R_BUC_02 -> identifisertePersoner.first().apply { personListe = identifisertePersoner }
            bucType == BucType.P_BUC_02 -> identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }
            bucType == BucType.P_BUC_05 -> {
                val erGjenlevendeRelasjon = potensiellePersonRelasjoner.any { it.relasjon == Relasjon.GJENLEVENDE }
                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeRelasjon)
            }
            bucType == BucType.P_BUC_10 -> {
                val erGjenlevendeYtelse = potensiellePersonRelasjoner.any { it.saktype == Saktype.GJENLEV }

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
    private fun utvelgerPersonOgGjenlev(identifisertePersoner: List<IdentifisertPerson>, erGjenlevende: Boolean): IdentifisertPerson? {
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
    private fun brukForsikretPerson(sedType: SedType?, identifisertePersoner: List<IdentifisertPerson>): IdentifisertPerson? {
        if (sedType in brukForikretPersonISed) {
            logger.info("Henter ut forsikret person fra følgende SED $sedType")
            return identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
        }
        return null
    }

    /**
     * Henter første treff på dato fra listen av SEDer
     */
    fun hentFodselsDato(identifisertPerson: IdentifisertPerson?, seder: List<SED>, kansellerteSeder: List<SED>): LocalDate? {
        return identifisertPerson?.personRelasjon?.fnr?.getBirthDate()
                ?: FodselsdatoHelper.fraSedListe(seder, kansellerteSeder)
    }
}






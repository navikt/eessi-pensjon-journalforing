package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.NavFodselsnummer
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class PersonidentifiseringService(private val aktoerregisterService: AktoerregisterService,
                                  private val personV3Service: PersonV3Service,
                                  private val diskresjonService: DiskresjonkodeHelper,
                                  private val fnrHelper: FnrHelper,
                                  private val fdatoHelper: FdatoHelper) {

    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)

    companion object {
        fun trimFnrString(fnrAsString: String) = fnrAsString.replace("[^0-9]".toRegex(), "")

        fun erFnrDnrFormat(id: String?): Boolean {
            return id != null && id.length == 11 && id.isNotBlank()
        }
    }

    fun hentIdentifisertPerson(navBruker: String?, alleSediBuc: List<String?>, bucType: BucType): IdentifisertPerson? {
        val identifisertePersoner = hentIdentifisertePersoner(navBruker, alleSediBuc, bucType)
        return identifisertPersonUtvelger(identifisertePersoner, bucType)
    }

    fun hentIdentifisertePersoner(navBruker: String?, alleSediBuc: List<String?>, bucType: BucType?): List<IdentifisertPerson> {
        logger.info("Forsøker å identifisere personen")
        val trimmetNavBruker = navBruker?.let { trimFnrString(it) }
        val personForNavBruker = if (erFnrDnrFormat(trimmetNavBruker)) personV3Service.hentPerson(trimmetNavBruker!!) else null

        if (personForNavBruker != null) {
            return listOf(populerIdentifisertPerson(personForNavBruker, alleSediBuc, PersonRelasjon(trimmetNavBruker!!, Relasjon.FORSIKRET)))

        } else {
            //Prøve fnr
            logger.info("Forsøker å identifisere personer ut fra SEDer i BUC")
            val identifisertePersonRelasjoner = mutableListOf<IdentifisertPerson>()
            try {
                val potensiellePersonRelasjoner = fnrHelper.getPotensielleFnrFraSeder(alleSediBuc)
                potensiellePersonRelasjoner.forEach { personRelasjon ->
                    val personen = personV3Service.hentPerson(trimFnrString(personRelasjon.fnr))
                    if (personen != null) {
                        val identifisertPerson = populerIdentifisertPerson(
                                personen,
                                alleSediBuc,
                                personRelasjon)
                        identifisertePersonRelasjoner.add(identifisertPerson)
                    }
                }
            } catch (ex: Exception) {
                logger.warn("Feil ved henting av person / fødselsnummer fra SEDer, fortsetter uten", ex)
            }
            return identifisertePersonRelasjoner
        }
    }

    private fun populerIdentifisertPerson(person: Bruker, alleSediBuc: List<String?>, personRelasjon: PersonRelasjon): IdentifisertPerson {
        val personNavn = hentPersonNavn(person)
        val aktoerId = hentAktoerId(personRelasjon.fnr) ?: ""
        val diskresjonskode = diskresjonService.hentDiskresjonskode(alleSediBuc)?.name
        val landkode = hentLandkode(person)
        val geografiskTilknytning = hentGeografiskTilknytning(person)

        return IdentifisertPerson(aktoerId, personNavn, diskresjonskode, landkode, geografiskTilknytning, personRelasjon)
    }

    /**
     * Forsøker å finne om identifisert person er en eller fler med avdød person
     */
    fun identifisertPersonUtvelger(identifisertePersoner: List<IdentifisertPerson>, bucType: BucType): IdentifisertPerson? {
        return if (identifisertePersoner.isEmpty()) {
            null
        } else {
            logger.info("Antall identifisertePersoner : ${identifisertePersoner.size}")
            if (identifisertePersoner.size == 1) {
                identifisertePersoner.first()
            } else {
                logger.debug("BucType: $bucType Personer: ${identifisertePersoner.toJson()}")
                throw RuntimeException("Stopper grunnet flere personer på bucType: $bucType")
            }
        }
    }

    /**
     * Henter første treff på dato fra listen av SEDer
     */
    fun hentFodselsDato(identifisertPerson: IdentifisertPerson?, seder: List<String?>?): LocalDate {
        val fnr = identifisertPerson?.personRelasjon?.fnr
        val fdatoFraFnr = if (!erFnrDnrFormat(fnr)) null else fodselsDatoFra(fnr!!)
        if (fdatoFraFnr != null) {
            return fdatoFraFnr
        }
        if (!seder.isNullOrEmpty()) {
            logger.info("Henter fdato fra SEDer")
            return fdatoHelper.finnEnFdatoFraSEDer(seder)
        }
        throw RuntimeException("Kunne ikke finne fdato i listen over SEDer")
    }

    private fun fodselsDatoFra(fnr: String) =
            try {
                val trimmedFnr = trimFnrString(fnr)
                LocalDate.parse(NavFodselsnummer(trimmedFnr).getBirthDateAsISO(), DateTimeFormatter.ISO_DATE)
            } catch (ex: Exception) {
                logger.error("navBruker ikke gyldig for fdato", ex)
                null
            }

    private fun hentAktoerId(navBruker: String?): String? {
        if (!erFnrDnrFormat(navBruker)) return null
        return try {
            val aktoerId = aktoerregisterService.hentGjeldendeAktorIdForNorskIdent(navBruker!!)
            aktoerId
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting av aktørid: $ex")
            null
        }
    }
}

data class IdentifisertPerson(
        val aktoerId: String,
        val personNavn: String?,
        val diskresjonskode: String? = null,
        val landkode: String?,
        val geografiskTilknytning: String?,
        val personRelasjon: PersonRelasjon
)

data class PersonRelasjon(
        val fnr: String,
        val relasjon: Relasjon
)

enum class Relasjon {
    BARN,
    FORSIKRET,
    GJENLEVENDE,
    AVDOD,
    ANNET
}

fun hentLandkode(person: Person) =
        person.bostedsadresse?.strukturertAdresse?.landkode?.value

fun hentPersonNavn(person: Person) =
        person.personnavn?.sammensattNavn

fun hentGeografiskTilknytning(bruker: Bruker?) = bruker?.geografiskTilknytning?.geografiskTilknytning

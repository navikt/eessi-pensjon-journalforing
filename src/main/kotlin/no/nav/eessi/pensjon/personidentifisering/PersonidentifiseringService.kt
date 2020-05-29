package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.NavFodselsnummer
import no.nav.eessi.pensjon.personidentifisering.klienter.*
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class PersonidentifiseringService(private val aktoerregisterKlient: AktoerregisterKlient,
                                  private val personV3Klient: PersonV3Klient,
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

    fun identifiserPersoner(navBruker: String?, alleSediBuc: List<String?>, bucType: BucType?): List<IdentifisertPerson> {
        logger.info("Forsøker å identifisere personen")
        val trimmetNavBruker = navBruker?.let { trimFnrString(it) }
        val personForNavBruker = if (erFnrDnrFormat(trimmetNavBruker)) personV3Klient.hentPerson(trimmetNavBruker!!) else null

        if (personForNavBruker != null) {
            return listOf(populerIdentifisertPerson(personForNavBruker, alleSediBuc, PersonRelasjon(trimmetNavBruker!!, Relasjon.FORSIKRET)))

        } else {
            //Prøve fnr
            logger.info("Forsøker å identifisere personer ut fra SEDer i BUC")
            val identifisertePersonRelasjoner = mutableListOf<IdentifisertPerson>()
            try {
                val potensiellePersonRelasjoner = fnrHelper.getPotensielleFnrFraSeder(alleSediBuc)
                potensiellePersonRelasjoner.forEach { personRelasjon ->
                    val personen = personV3Klient.hentPerson(trimFnrString(personRelasjon.fnr))
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

        return IdentifisertPerson(aktoerId, personNavn!!, diskresjonskode, landkode!!, geografiskTilknytning!!, personRelasjon)
    }

    /**
     * Forsøker å finne om identifisert person er en eller fler med avdød person
     */
    fun identifisertPersonUtvelger(identifisertePersoner: List<IdentifisertPerson>, bucType: BucType): IdentifisertPerson? {
        if (identifisertePersoner.isEmpty()) {
            return null
        } else {
            logger.info("Antall identifisertePersoner : ${identifisertePersoner.size}")
            if (identifisertePersoner.size == 1) {
                return identifisertePersoner.first()
            } else {
                if (bucType == BucType.R_BUC_02) {
                    val identifisertAvdod = identifisertePersoner.filter { it.personRelasjon.relasjon == Relasjon.AVDOD }
                            .map { it }
                    if (identifisertAvdod.size == 1) {
                        return identifisertAvdod.first()
                    }
                }
                return null
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
            val aktoerId = aktoerregisterKlient.hentGjeldendeAktoerIdForNorskIdent(navBruker!!)
            aktoerId
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting av aktørid: $ex")
            null
        }
    }
}

data class IdentifisertPerson(
        val aktoerId: String,
        val personNavn: String,
        val diskresjonskode: String? = null,
        val landkode: String,
        val geografiskTilknytning: String,
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


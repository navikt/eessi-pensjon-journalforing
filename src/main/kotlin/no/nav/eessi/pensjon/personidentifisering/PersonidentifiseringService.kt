package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.NavFodselsnummer
import no.nav.eessi.pensjon.personidentifisering.klienter.*
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

    fun identifiserPerson(navBruker: String?, alleSediBuc: List<String?>): IdentifisertPerson {
        val trimmetNavBruker = navBruker?.let { trimFnrString(it) }

        val personForNavBruker = if (erFnrDnrFormat(trimmetNavBruker)) personV3Klient.hentPerson(trimmetNavBruker!!) else null

        var fnr: String? = null
        var fdato: LocalDate? = null

        var person = personForNavBruker

        if (person != null) {
            fnr = trimmetNavBruker!!
            fdato = hentFodselsDato(fnr, null)
        } else {
            //Prøve fnr
            try {
                val potensielleFnr = fnrHelper.getPotensielleFnrFraSeder(alleSediBuc)

                potensielleFnr.forEach {
                    person = personV3Klient.hentPerson(trimFnrString(it.fnr!!))
                    if (person != null) {
                        fnr = trimFnrString(it.fnr)
                        fdato = hentFodselsDato(fnr, null)
                        return@forEach
                    }
                }
            } catch (ex: Exception) {
                logger.info("Feil ved henting av person / fødselsnummer, fortsetter uten")
            }
            //Prøve fdato
            if (fdato == null) {
                try {
                    fdato = hentFodselsDato(fnr, alleSediBuc)
                    logger.debug("følgende fdato: $fdato")
                } catch (ex: Exception) {
                    logger.error("Feil ved henting av fdato på valgt sed", ex)
                }
            }
        }

        val personNavn = hentPersonNavn(person)
        var aktoerId: String? = null

        if (person != null) aktoerId = hentAktoerId(fnr)

        val diskresjonskode = diskresjonService.hentDiskresjonskode(alleSediBuc)
        val landkode = hentLandkode(person)
        val geografiskTilknytning = hentGeografiskTilknytning(person)

        if (fdato == null) throw NullPointerException("Unexpected null for fdato-variable")

        return IdentifisertPerson(aktoerId, fdato!!, personNavn, diskresjonskode?.name, landkode, geografiskTilknytning, PersonRelasjon(fnr,Relasjon.ANNET))
    }

    /**
     * Henter første treff på dato fra listen av SEDer
     */
    fun hentFodselsDato(fnr: String?, seder: List<String?>?): LocalDate {
        val fdatoFraFnr = if (!erFnrDnrFormat(fnr)) null else fodselsDatoFra(fnr!!)
        if (fdatoFraFnr != null) {
            return fdatoFraFnr
        }
        if (!seder.isNullOrEmpty()) {
            return fdatoHelper.finnEnFdatoFraSEDer(seder)
        }
        throw RuntimeException("Kunne ikke finne fdato i listen av SEDer")
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
        val aktoerId: String? = null,
        val fdato: LocalDate,
        val personNavn: String? = null,
        val diskresjonskode: String? = null,
        val landkode: String? = null,
        val geografiskTilknytning: String? = null,
        val personRelasjon: PersonRelasjon
)

data class PersonRelasjon(
        val fnr: String? = null,
        val relasjon: Relasjon
)

enum class Relasjon {
    BARN,
    FORSIKRET,
    GJENLEVENDE,
    AVDOD,
    ANNET
}


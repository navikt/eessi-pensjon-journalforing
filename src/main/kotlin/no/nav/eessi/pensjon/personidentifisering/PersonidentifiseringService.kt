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
                                  private val fdatoHelper: FdatoHelper)  {

    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)

    fun identifiserPerson(navBruker: String?, alleSediBuc: List<String?>) : IdentifisertPerson {
        val trimmetNavBruker = navBruker?.let{ FnrHelper.trimFnrString(it) }

        val personForNavBruker = if (isFnrValid(trimmetNavBruker)) personV3Klient.hentPerson(trimmetNavBruker!!) else null

        var fnr : String?
        var fdato: LocalDate? = null

        var person = personForNavBruker

        if(person != null) {
            fnr = trimmetNavBruker!!
            fdato = hentFodselsDato(fnr, null)
        } else {
            //Prøve fnr
            try {
                val personFnrPair = fnrHelper.getPersonOgFnrFraSeder(alleSediBuc)
                if (personFnrPair != null) {
                    person = personFnrPair.first
                    fnr = personFnrPair.second
                    logger.debug("hentet person: $person")
                } else {
                    logger.info("Ingen treff på person eller fødselsnummer, fortsetter uten")
                    person = null
                    fnr = null
                }
            } catch (ex: Exception) {
                logger.info("Feil ved henting av person / fødselsnummer, fortsetter uten")
                person = null
                fnr = null
            }
            //Prøve fdato
            try {
                fdato = hentFodselsDato(fnr, alleSediBuc)
                logger.debug("følgende fdato: $fdato")
            } catch (ex:  Exception) {
                logger.info("Feil ved henting av fdato på valgt sed")
            }
        }

        val personNavn = hentPersonNavn(person)
        var aktoerId: String? = null

        if (person != null) aktoerId = hentAktoerId(fnr)

        val diskresjonskode = diskresjonService.hentDiskresjonskode(alleSediBuc)
        val landkode = hentLandkode(person)
        val geografiskTilknytning = hentGeografiskTilknytning(person)

        if (fdato == null) throw NullPointerException("Unexpected null for fdato-variable")

        return IdentifisertPerson(fnr, aktoerId, fdato, personNavn, diskresjonskode?.name, landkode, geografiskTilknytning)
    }

    /**
     * Henter første treff på dato fra listen av SEDer
     */
    fun hentFodselsDato(fnr: String?, seder: List<String?>?): LocalDate {
        val fdatoFraFnr = fodselsDatoFra(fnr)
        if (fdatoFraFnr != null) {
            return fdatoFraFnr
        }
        if (!seder.isNullOrEmpty()) {
            return fdatoHelper.finnEnFdatoFraSEDer(seder)
        }
        throw RuntimeException("Kunne ikke finne fdato i listen av SEDer")
    }

    private fun fodselsDatoFra(fnr: String?) =
            try {
                LocalDate.parse(NavFodselsnummer(fnr!!).getBirthDateAsISO(), DateTimeFormatter.ISO_DATE)
            } catch (ex: Exception) {
                logger.error("navBruker ikke gyldig for fdato", ex)
                null
            }

    private fun hentAktoerId(navBruker: String?): String? {
        if (!isFnrValid(navBruker)) return null
        return try {
            val aktoerId = aktoerregisterKlient.hentGjeldendeAktoerIdForNorskIdent(navBruker!!)
            aktoerId
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting av aktørid: $ex")
            null
        }
    }

    fun isFnrValid(navBruker: String?) = navBruker != null && navBruker.length == 11
}

class IdentifisertPerson(val fnr : String? = null,
                              val aktoerId: String? = null,
                              val fdato: LocalDate,
                              val personNavn: String? = null,
                              val diskresjonskode: String? = null,
                              val landkode: String? = null,
                              val geografiskTilknytning: String? = null)

package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.NavFodselsnummer
import no.nav.eessi.pensjon.personidentifisering.klienter.*
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
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

    fun identifiserPerson(sedHendelse: SedHendelseModel, alleSediBuc: List<String?>) : IdentifisertPerson {
        val regex = "[^0-9.]".toRegex()
        val filtrertNavBruker = sedHendelse.navBruker?.replace(regex, "")

        var person = hentPerson(filtrertNavBruker)
        var fnr : String?
        var fdato: LocalDate? = null

        if(person != null) {
            fnr = filtrertNavBruker!!
            fdato = hentFodselsDato(fnr, null)
        } else {
            //Prøve fnr
            try {
                val personFnrPair = fnrHelper.getFodselsnrFraSeder(alleSediBuc)
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

        return IdentifisertPerson(fnr, aktoerId, fdato!!, personNavn, diskresjonskode?.name, landkode, geografiskTilknytning)
    }

    /**
     * Henter første treff på dato fra listen av SEDer
     */
    fun hentFodselsDato(fnr: String?, seder: List<String?>?): LocalDate {
        var fodselsDatoISO : String? = null

        if (isFnrValid(fnr)) {
            fodselsDatoISO = try {
                val navfnr = NavFodselsnummer(fnr!!)
                navfnr.getBirthDateAsISO()
            } catch (ex : Exception) {
                logger.error("navBruker ikke gyldig for fdato", ex)
                null
            }
        }

        if (fodselsDatoISO.isNullOrEmpty()) {
            fodselsDatoISO = seder?.let { fdatoHelper.finnFDatoFraSeder(it) }
            logger.debug("Fant følgende fdato: $fodselsDatoISO")
        }

        return if (fodselsDatoISO.isNullOrEmpty()) {
            throw(RuntimeException("Kunne ikke finne fdato i listen av SEDer"))
        } else {
            LocalDate.parse(fodselsDatoISO, DateTimeFormatter.ISO_DATE)
        }
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

    private fun hentPerson(navBruker: String?): Bruker? {
        if (!isFnrValid(navBruker)) return null
        return personV3Klient.hentPerson(navBruker!!)
    }

    fun isFnrValid(navBruker: String?): Boolean {
        if(navBruker == null) return false
        if(navBruker.length != 11) return false

        return true
    }
}

class IdentifisertPerson(val fnr : String? = null,
                              val aktoerId: String? = null,
                              val fdato: LocalDate,
                              val personNavn: String? = null,
                              val diskresjonskode: String? = null,
                              val landkode: String? = null,
                              val geografiskTilknytning: String? = null)
package no.nav.eessi.pensjon.personidentifisering

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.buc.BucHelper
import no.nav.eessi.pensjon.buc.FdatoHelper
import no.nav.eessi.pensjon.buc.FnrHelper
import no.nav.eessi.pensjon.journalforing.DiskresjonService
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.person.*
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class IdentifiserPersonHelper(private val aktoerregisterService: AktoerregisterService,
                              private val personV3Service: PersonV3Service,
                              private val diskresjonService: DiskresjonService,
                              private val fnrHelper: FnrHelper,
                              private val fdatoHelper: FdatoHelper,
                              private val fagmodulService: FagmodulService,
                              private val euxService: EuxService)  {

    private val logger = LoggerFactory.getLogger(IdentifiserPersonHelper::class.java)

    private val mapper = jacksonObjectMapper()

    fun identifiserPerson(sedHendelse: SedHendelseModel) : IdentifisertPerson {
        val regex = "[^0-9.]".toRegex()
        val filtrertNavBruker = sedHendelse.navBruker?.replace(regex, "")

        var person = hentPerson(filtrertNavBruker)
        var fnr : String?
        var fdato: LocalDate? = null

        if(person != null) {
            fnr = filtrertNavBruker!!
            fdato = hentFodselsDato(fnr, null)
        } else {
            try {
                val alleSediBuc = hentAlleSedIBuc(sedHendelse.rinaSakId)
                fnr = fnrHelper.getFodselsnrFraSeder(alleSediBuc)
                fdato = hentFodselsDato(fnr, alleSediBuc)
                person = hentPerson(fnr)
                if (person == null) {
                    logger.info("Ingen treff på fødselsnummer, fortsetter uten")
                    fnr = null
                }
            } catch (ex: Exception) {
                logger.info("Ingen treff på fødselsnummer, fortsetter uten")
                person = null
                fnr = null
            }
        }

        val personNavn = hentPersonNavn(person)
        var aktoerId: String? = null

        if (person != null) aktoerId = hentAktoerId(fnr)

        val diskresjonskode = diskresjonService.hentDiskresjonskode(sedHendelse)
        val landkode = hentLandkode(person)
        val geografiskTilknytning = hentGeografiskTilknytning(person)

        return IdentifisertPerson(fnr, aktoerId, fdato!!, personNavn, diskresjonskode?.name, landkode, geografiskTilknytning)
    }

    fun hentAlleSedIBuc(euxCaseId: String): List<String?> {
        val alleDokumenter = fagmodulService.hentAlleDokumenter(euxCaseId)
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)

        val gyldigeSeds = BucHelper.filterUtGyldigSedId(alleDokumenterJsonNode)

        return gyldigeSeds.map { pair ->
            val sedDocumentId = pair.first
            euxService.hentSed(euxCaseId, sedDocumentId)
        }
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
            val aktoerId = aktoerregisterService.hentGjeldendeAktoerIdForNorskIdent(navBruker!!)
            aktoerId
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting av aktørid: $ex")
            null
        }
    }

    private fun hentPerson(navBruker: String?): Bruker? {
        if (!isFnrValid(navBruker)) return null
        return try {
            personV3Service.hentPerson(navBruker!!)
        } catch (ex: Exception) {
            null
        }
    }

    fun isFnrValid(navBruker: String?): Boolean {
        if(navBruker == null) return false
        if(navBruker.length != 11) return false

        return true
    }
}


data class IdentifisertPerson(val fnr : String? = null,
                              val aktoerId: String? = null,
                              val fdato: LocalDate,
                              val personNavn: String? = null,
                              val diskresjonskode: String? = null,
                              val landkode: String? = null,
                              val geografiskTilknytning: String? = null)
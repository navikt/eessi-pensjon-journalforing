package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.buc.BucHelper
import no.nav.eessi.pensjon.buc.FdatoHelper
import no.nav.eessi.pensjon.buc.FnrHelper
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.JournalforingPerson
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.EuxDokument
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.services.person.*
import no.nav.eessi.pensjon.services.pesys.PenService
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class JournalforingService(private val euxService: EuxService,
                           private val journalpostService: JournalpostService,
                           private val aktoerregisterService: AktoerregisterService,
                           private val personV3Service: PersonV3Service,
                           private val fagmodulService: FagmodulService,
                           private val oppgaveRoutingService: OppgaveRoutingService,
                           private val pdfService: PDFService,
                           private val diskresjonService: DiskresjonService,
                           private val oppgaveHandler: OppgaveHandler,
                           private val penService: PenService,
                           private val fnrHelper: FnrHelper,
                           private val fdatoHelper: FdatoHelper,
                           @Value("\${NAIS_NAMESPACE}") private val namespace: String,
                           @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()))  {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)
    private val mapper = jacksonObjectMapper()

    fun journalfor(hendelseJson: String, hendelseType: HendelseType) {
        metricsHelper.measure("journalforOgOpprettOppgaveForSed") {
            try {
                val sedHendelse = SedHendelseModel.fromJson(hendelseJson)

                if (sedHendelse.sektorKode != "P") {
                    // Vi ignorerer alle hendelser som ikke har vår sektorkode
                    return@measure
                }

                logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")

                val person = identifiserPerson(sedHendelse)

                // TODO pin og ytelse skal gjøres om til å returnere kun ytelse
                val pinOgYtelse = hentYtelseKravType(sedHendelse)

                val sedDokumenterJSON = euxService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                        ?: throw RuntimeException("Failed to get documents from EUX, ${sedHendelse.rinaSakId}, ${sedHendelse.rinaDokumentId}")
                val (documents, uSupporterteVedlegg) = pdfService.parseJsonDocuments(sedDokumenterJSON, sedHendelse.sedType!!)

                val tildeltEnhet = hentTildeltEnhet(
                        sedHendelse.sedType,
                        sedHendelse.bucType!!,
                        pinOgYtelse,
                        person
                )

                logger.debug("tildeltEnhet: $tildeltEnhet")

                var sakId: String? = null
                if(person.aktoerId != null) {
                    if (hendelseType == HendelseType.SENDT && namespace == "q2") {
                        sakId = penService.hentSakId(person.aktoerId, sedHendelse.bucType)
                    }
                }
                var forsokFerdigstill = false
                sakId?.let { forsokFerdigstill = true }

                val journalPostResponse = journalpostService.opprettJournalpost(
                        rinaSakId = sedHendelse.rinaSakId,
                        fnr = person.fnr,
                        personNavn = person.personNavn,
                        bucType = sedHendelse.bucType.name,
                        sedType = sedHendelse.sedType.name,
                        sedHendelseType = hendelseType.name,
                        eksternReferanseId = null,// TODO what value to put here?,
                        kanal = "EESSI",
                        journalfoerendeEnhet = tildeltEnhet.enhetsNr,
                        arkivsaksnummer = sakId,
                        dokumenter = documents,
                        forsokFerdigstill = forsokFerdigstill,
                        avsenderLand = sedHendelse.avsenderLand,
                        avsenderNavn = sedHendelse.avsenderNavn
                )

                logger.debug("JournalPostID: ${journalPostResponse!!.journalpostId}")

                if(!journalPostResponse.journalpostferdigstilt) {
                    publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, journalPostResponse.journalpostId, tildeltEnhet, person.aktoerId, "JOURNALFORING", sedHendelse, hendelseType)

                    if (uSupporterteVedlegg.isNotEmpty()) {
                        publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, null, tildeltEnhet, person.aktoerId, "BEHANDLE_SED", sedHendelse, hendelseType, usupporterteFilnavn(uSupporterteVedlegg))

                    }
                }


            } catch (ex: MismatchedInputException) {
                logger.error("Det oppstod en feil ved deserialisering av hendelse", ex)
                throw ex
            } catch (ex: MissingKotlinParameterException) {
                logger.error("Det oppstod en feil ved deserialisering av hendelse", ex)
                throw ex
            } catch (ex: Exception) {
                logger.error("Det oppstod en uventet feil ved journalforing av hendelse", ex)
                throw ex
            }
        }
    }

    private fun publishOppgavemeldingPaaKafkaTopic(sedType: SedType, journalpostId: String?, tildeltEnhet: OppgaveRoutingModel.Enhet, aktoerId: String?, oppgaveType: String, sedHendelse: SedHendelseModel, hendelseType: HendelseType, filnavn: String? = null) {
        oppgaveHandler.opprettOppgaveMeldingPaaKafkaTopic(OppgaveMelding(
                sedType = sedType.name,
                journalpostId = journalpostId,
                tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                aktoerId = aktoerId,
                oppgaveType = oppgaveType,
                rinaSakId = sedHendelse.rinaSakId,
                hendelseType = hendelseType.name,
                filnavn = filnavn
        ))
    }

    private fun usupporterteFilnavn(uSupporterteVedlegg: List<EuxDokument>): String {
        var filnavn = ""
        uSupporterteVedlegg.forEach { vedlegg -> filnavn += vedlegg.filnavn + " " }
        return filnavn
    }

    private fun hentYtelseKravType(sedHendelse: SedHendelseModel): String? {
        if(sedHendelse.sedType == SedType.P2100 || sedHendelse.sedType == SedType.P15000) {
            return try{
                fagmodulService.hentYtelseKravType(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            } catch (ex: Exception) {
                null
            }
        }
        return null
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

    private fun hentTildeltEnhet(
            sedType: SedType,
            bucType: BucType,
            ytelseType: String?,
            person: JournalforingPerson
    ): OppgaveRoutingModel.Enhet {
        return if(sedType == SedType.P15000){
            oppgaveRoutingService.route(person, bucType, ytelseType)
        } else {
            oppgaveRoutingService.route(person, bucType)
        }
    }

    fun identifiserPerson(sedHendelse: SedHendelseModel) : JournalforingPerson {
        var person = hentPerson(sedHendelse.navBruker)
        var fnr : String?
        var fdato: LocalDate? = null

        if(person != null) {
            fnr = sedHendelse.navBruker!!
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

        return JournalforingPerson(fnr, aktoerId, fdato!!, personNavn, diskresjonskode?.name, landkode, geografiskTilknytning)
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

    fun isFnrValid(navBruker: String?): Boolean {
        if(navBruker == null) return false
        if(navBruker.length != 11) return false

        return true
    }
}

class Person(val fnr : String?,
             val aktoerId: String?,
             val fdato: LocalDate?,
             val personNavn: String?,
             val diskresjonskode: String?,
             val landkode: String?,
             val geografiskTilknytning: String?)
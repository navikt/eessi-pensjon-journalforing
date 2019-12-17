package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.buc.FdatoService
import no.nav.eessi.pensjon.buc.FnrService
import no.nav.eessi.pensjon.handler.OppgaveHandler
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.pdf.EuxDokument
import no.nav.eessi.pensjon.pdf.PDFService
import no.nav.eessi.pensjon.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.fagmodul.HentPinOgYtelseTypeResponse
import no.nav.eessi.pensjon.services.fagmodul.Krav
import no.nav.eessi.pensjon.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.services.norg2.Diskresjonskode
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import no.nav.eessi.pensjon.services.personv3.hentGeografiskTilknytning
import no.nav.eessi.pensjon.services.personv3.hentLandkode
import no.nav.eessi.pensjon.services.personv3.hentPersonNavn
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
                           private val begrensInnsynService: BegrensInnsynService,
                           private val oppgaveHandler: OppgaveHandler,
                           private val penService: PenService,
                           private val fnrService: FnrService,
                           private val fdatoService: FdatoService,
                           @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()))  {

    @Value("\${NAIS_NAMESPACE}")
    private lateinit var namespace: String

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    private val hentYtelseTypeMapper = HentYtelseTypeMapper()

    fun journalfor(hendelseJson: String, hendelseType: HendelseType) {
        metricsHelper.measure("journalforOgOpprettOppgaveForSed") {
            try {
                val sedHendelse = SedHendelseModel.fromJson(hendelseJson)

                if (sedHendelse.sektorKode != "P") {
                    // Vi ignorerer alle hendelser som ikke har vår sektorkode
                    return@measure
                }

                logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")

                var person = hentPerson(sedHendelse.navBruker)
                var fnr : String? = null

                if(person != null) {
                    fnr = sedHendelse.navBruker!!
                } else {
                   try {
                        fnr = fnrService.getFodselsnrFraSedPaaVagtBuc(sedHendelse.rinaSakId)
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

                // TODO pin og ytelse skal gjøres om til å returnere kun ytelse
                val pinOgYtelse = hentPinOgYtelse(sedHendelse)

                val fodselsDato = hentFodselsDato(sedHendelse, fnr)

                val landkode = hentLandkode(person)

                val sedDokumenterJSON = euxService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                        ?: throw RuntimeException("Failed to get documents from EUX, ${sedHendelse.rinaSakId}, ${sedHendelse.rinaDokumentId}")
                val (documents, uSupporterteVedlegg) = pdfService.parseJsonDocuments(sedDokumenterJSON, sedHendelse.sedType!!)

                //tps bruker gt
                val geografiskTilknytning = hentGeografiskTilknytning(person)

                //tps bruker diskresjon
                val diskresjonskode = begrensInnsynService.begrensInnsyn(sedHendelse)

                logger.debug("geografiskTilknytning: $geografiskTilknytning")

                val tildeltEnhet = hentTildeltEnhet(
                        sedHendelse.sedType,
                        sedHendelse.bucType!!,
                        pinOgYtelse,
                        fnr,
                        landkode,
                        fodselsDato,
                        geografiskTilknytning,
                        diskresjonskode
                )

                logger.debug("tildeltEnhet: $tildeltEnhet")

                var sakId: String? = null
                if(aktoerId != null) {
                    if (hendelseType == HendelseType.SENDT && namespace == "q2") {
                        sakId = penService.hentSakId(aktoerId, sedHendelse.bucType)
                    }
                }
                var forsokFerdigstill = false
                sakId?.let { forsokFerdigstill = true }

                val journalPostResponse = journalpostService.opprettJournalpost(
                        rinaSakId = sedHendelse.rinaSakId,
                        fnr = fnr,
                        personNavn = personNavn,
                        bucType = sedHendelse.bucType.name,
                        sedType = sedHendelse.sedType.name,
                        sedHendelseType = hendelseType.name,
                        eksternReferanseId = null,// TODO what value to put here?,
                        kanal = "EESSI",
                        journalfoerendeEnhet = tildeltEnhet.enhetsNr,
                        arkivsaksnummer = sakId,
                        dokumenter = documents,
                        forsokFerdigstill = forsokFerdigstill,
                        avsenderLand = sedHendelse.avsenderLand
                )

                logger.debug("JournalPostID: ${journalPostResponse!!.journalpostId}")

                if(!journalPostResponse.journalpostferdigstilt) {
                    publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, journalPostResponse.journalpostId, tildeltEnhet, aktoerId, "JOURNALFORING", sedHendelse, hendelseType)

                    if (uSupporterteVedlegg.isNotEmpty()) {
                        publishOppgavemeldingPaaKafkaTopic(sedHendelse.sedType, null, tildeltEnhet, aktoerId, "BEHANDLE_SED", sedHendelse, hendelseType, usupporterteFilnavn(uSupporterteVedlegg))

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

    private fun hentPinOgYtelse(sedHendelse: SedHendelseModel): HentPinOgYtelseTypeResponse? {
        if(sedHendelse.sedType == SedType.P2100 || sedHendelse.sedType == SedType.P15000) {
            return try{
                fagmodulService.hentPinOgYtelseType(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            } catch (ex: Exception) {
                null
            }
        }
        return null
    }

    /**
     * Henter fødselsdatoen fra den gjeldende SEDen som skal journalføres, dersom dette feltet er tomt
     * hentes fødselsdatoen fra første SED i samme BUC som har fødselsdato satt.
     */
    fun hentFodselsDato(sedHendelse: SedHendelseModel, fnr: String?): LocalDate? {
        var fodselsDatoISO : String? = null

        if (isFnrValid(fnr)) {
            try {
                val navfnr = NavFodselsnummer(fnr!!)
                fodselsDatoISO =  navfnr.getBirthDateAsISO()
            } catch (ex : Exception) {
                logger.error("navBruker ikke gyldig for fdato", ex)
                fodselsDatoISO = null
            }
        }

        if (fodselsDatoISO.isNullOrEmpty()) {
            logger.debug("provøer å hente inn fodselsDato fra følgende SED : ${sedHendelse.rinaSakId} / ${sedHendelse.rinaDokumentId}")
            fodselsDatoISO = euxService.hentFodselsDatoFraSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

            if (fodselsDatoISO.isNullOrEmpty()) {
                try {
                    logger.debug("provøer å hente inn fodselsDato fra første og beste SED i BUC : ${sedHendelse.rinaSakId}")
                    fodselsDatoISO = fdatoService.getFDatoFromSed(sedHendelse.rinaSakId)
                } catch (ex: Exception) {
                    logger.error("Ingen gyldige fdato funnet i BUC : ${sedHendelse.rinaSakId}", ex)
                }
            }
        }

        return if (fodselsDatoISO.isNullOrEmpty()) {
             null
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
            pinOgYtelseType: HentPinOgYtelseTypeResponse?,
            navBruker: String?,
            landkode: String?,
            fodselsDato: LocalDate? = null,
            geografiskTilknytning: String?,
            diskresjonskode: Diskresjonskode?
    ): OppgaveRoutingModel.Enhet {
        return if(sedType == SedType.P15000){
            val ytelseType = hentYtelseTypeMapper.map(pinOgYtelseType)
            oppgaveRoutingService.route(navBruker, bucType, landkode, fodselsDato, geografiskTilknytning, diskresjonskode, ytelseType)
        } else {
            oppgaveRoutingService.route(navBruker, bucType, landkode, fodselsDato, geografiskTilknytning, diskresjonskode)
        }
    }
}

fun isFnrValid(navBruker: String?): Boolean {
    if(navBruker == null) return false
    if(navBruker.length != 11) return false

    return true
}

private class HentYtelseTypeMapper {
    fun map(hentPinOgYtelseTypeResponse: HentPinOgYtelseTypeResponse?) : OppgaveRoutingModel.YtelseType? {
        return when(hentPinOgYtelseTypeResponse?.krav?.type) {
            Krav.YtelseType.AP -> OppgaveRoutingModel.YtelseType.AP
            Krav.YtelseType.GP -> OppgaveRoutingModel.YtelseType.GP
            Krav.YtelseType.UT -> OppgaveRoutingModel.YtelseType.UT
            null -> null
        }
    }
}
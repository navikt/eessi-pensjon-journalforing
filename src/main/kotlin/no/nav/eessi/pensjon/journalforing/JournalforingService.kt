package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
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
import no.nav.eessi.pensjon.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import no.nav.eessi.pensjon.services.personv3.hentGeografiskTilknytning
import no.nav.eessi.pensjon.services.personv3.hentLandkode
import no.nav.eessi.pensjon.services.personv3.hentPersonNavn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class JournalforingService(private val euxService: EuxService,
                           private val journalpostService: JournalpostService,
                           private val oppgaveService: OppgaveService,
                           private val aktoerregisterService: AktoerregisterService,
                           private val personV3Service: PersonV3Service,
                           private val fagmodulService: FagmodulService,
                           private val oppgaveRoutingService: OppgaveRoutingService,
                           private val pdfService: PDFService,
                           private val begrensInnsynService: BegrensInnsynService,
                           @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry()))  {

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
                        // TODO buctype fjernes som argument snart fordi fagmodulen bruker den ikke
                        fnr = fagmodulService.hentFnrFraBuc(sedHendelse.rinaSakId, "fjernes")!!
                        person = hentPerson(fnr)
                    } catch (ex: Exception) {
                        logger.info("Ingen treff på fødselsnummer, fortsetter uten")
                    }
                }

                val personNavn = hentPersonNavn(person)
                var aktoerId: String? = null

                // TODO pin og ytelse skal gjøres om til å returnere kun ytelse
                val pinOgYtelse = hentPinOgYtelse(sedHendelse)

                if (person != null) aktoerId = hentAktoerId(fnr)

                val fodselsDato = hentFodselsDato(sedHendelse)
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

                val journalpostId = journalpostService.opprettJournalpost(
                        rinaSakId = sedHendelse.rinaSakId,
                        navBruker = fnr,
                        personNavn = personNavn,
                        avsenderId = sedHendelse.avsenderId,
                        avsenderNavn = sedHendelse.avsenderNavn,
                        mottakerId = sedHendelse.mottakerId,
                        mottakerNavn = sedHendelse.mottakerNavn,
                        bucType = sedHendelse.bucType.name,
                        sedType = sedHendelse.sedType.name,
                        sedHendelseType = hendelseType.name,
                        eksternReferanseId = null,// TODO what value to put here?,
                        kanal = "EESSI",
                        journalfoerendeEnhet = tildeltEnhet.enhetsNr,
                        arkivsaksnummer = null, // TODO what value to put here?,
                        arkivsaksystem = null, // TODO what value to put here?,
                        dokumenter = documents,
                        forsokFerdigstill = false
                )

                logger.debug("JournalPostID: $journalpostId")

                oppgaveService.opprettOppgave(
                        sedType = sedHendelse.sedType,
                        journalpostId = journalpostId,
                        tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                        aktoerId = aktoerId,
                        oppgaveType = "JOURNALFORING",
                        rinaSakId = sedHendelse.rinaSakId,
                        filnavn = null,
                        hendelseType = hendelseType)

                if (uSupporterteVedlegg.isNotEmpty()) {
                    oppgaveService.opprettOppgave(
                            sedType = sedHendelse.sedType,
                            journalpostId = null,
                            tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                            aktoerId = aktoerId,
                            oppgaveType = "BEHANDLE_SED",
                            rinaSakId = sedHendelse.rinaSakId,
                            filnavn = usupporterteFilnavn(uSupporterteVedlegg),
                            hendelseType = hendelseType)
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
    private fun hentFodselsDato(sedHendelse: SedHendelseModel): LocalDate {
        var fodselsDatoISO = euxService.hentFodselsDatoFraSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

        if(fodselsDatoISO.isNullOrEmpty()) {
            fodselsDatoISO = fagmodulService.hentFodselsdatoFraBuc(sedHendelse.rinaSakId, sedHendelse.bucType!!.name)
        }
        return LocalDate.parse(fodselsDatoISO, DateTimeFormatter.ISO_DATE)
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
            fodselsDato: LocalDate,
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
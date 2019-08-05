package no.nav.eessi.pensjon.journalforing.journalforing

import com.fasterxml.jackson.databind.exc.MismatchedInputException
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException
import no.nav.eessi.pensjon.journalforing.models.BucType
import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.EuxService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.HentYtelseTypeResponse
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.eessi.pensjon.journalforing.models.HendelseType
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.pdf.*
import no.nav.eessi.pensjon.journalforing.services.fagmodul.Krav
import no.nav.eessi.pensjon.journalforing.services.journalpost.*
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import java.lang.RuntimeException

@Service
class JournalforingService(private val euxService: EuxService,
                           private val journalpostService: JournalpostService,
                           private val oppgaveService: OppgaveService,
                           private val aktoerregisterService: AktoerregisterService,
                           private val personV3Service: PersonV3Service,
                           private val fagmodulService: FagmodulService,
                           private val oppgaveRoutingService: OppgaveRoutingService,
                           private val pdfService: PDFService)  {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    private val hentYtelseTypeMapper = HentYtelseTypeMapper()

    fun journalfor(hendelseJson: String, hendelseType: HendelseType) {
        try {
            val sedHendelse = SedHendelseModel.fromJson(hendelseJson)

            if (sedHendelse.sektorKode != "P") {
                // Vi ignorerer alle hendelser som ikke har vår sektorkode
                return
            }

            logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")

            val person = hentPerson(sedHendelse.navBruker)
            val personNavn = hentPersonNavn(person)
            val aktoerId = hentAktoerId(sedHendelse.navBruker)
            val fodselsDato = hentFodselsDato(sedHendelse)
            val landkode = hentLandkode(person)

            val sedDokumenterJSON = euxService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId) ?: throw RuntimeException("Failed to get documents from EUX, ${sedHendelse.rinaSakId}, ${sedHendelse.rinaDokumentId}")
            val (documents, uSupporterteVedlegg) = pdfService.parseJsonDocuments(sedDokumenterJSON, sedHendelse.sedId)


            val journalpostId = journalpostService.opprettJournalpost(
                    navBruker= sedHendelse.navBruker,
                    personNavn= personNavn,
                    avsenderId= sedHendelse.avsenderId,
                    avsenderNavn= sedHendelse.avsenderNavn,
                    mottakerId= sedHendelse.mottakerId,
                    mottakerNavn= sedHendelse.mottakerNavn,
                    bucType= sedHendelse.bucType?.name ?: throw RuntimeException("Buctype er null: $hendelseJson"),
                    sedType= sedHendelse.sedType?.toString() ?: throw RuntimeException("sedType er null: $hendelseJson"),
                    sedHendelseType= hendelseType.name,
                    eksternReferanseId= null,// TODO what value to put here?,
                    kanal= null, // TODO what value to put here?,
                    journalfoerendeEnhet= null, // TODO what value to put here?,
                    arkivsaksnummer= null, // TODO what value to put here?,
                    arkivsaksystem= null, // TODO what value to put here?,
                    dokumenter= documents,
                    forsokFerdigstill= false
            )

            val tildeltEnhet = hentTildeltEnhet(
                    sedHendelse.bucType,
                    sedHendelse.rinaSakId,
                    sedHendelse.rinaDokumentId,
                    sedHendelse.navBruker,
                    landkode,
                    fodselsDato
            )

            oppgaveService.opprettOppgave(
                    sedType = sedHendelse.sedType.toString(),
                    journalpostId = journalpostId,
                    tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                    aktoerId = aktoerId,
                    oppgaveType = "JOURNALFORING",
                    rinaSakId = null,
                    filnavn = null)

            if (uSupporterteVedlegg != null) {
                oppgaveService.opprettOppgave(
                        sedType = sedHendelse.sedType.toString(),
                        journalpostId = null,
                        tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                        aktoerId = aktoerId,
                        oppgaveType = "BEHANDLE_SED",
                        rinaSakId = sedHendelse.rinaSakId,
                        filnavn = uSupporterteVedlegg)
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

    private fun hentLandkode(person: Person?) =
            person?.bostedsadresse?.strukturertAdresse?.landkode?.value

    private fun hentPersonNavn(person: Person?) =
            person?.personnavn?.sammensattNavn

    private fun hentFodselsDato(sedHendelse: SedHendelseModel): String {
        val fodselsDatoISO = euxService.hentFodselsDato(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
        val isoDato = LocalDate.parse(fodselsDatoISO, DateTimeFormatter.ISO_DATE)
        return isoDato.format(DateTimeFormatter.ofPattern("ddMMyy"))
    }

    private fun hentAktoerId(navBruker: String?): String? {
        if(navBruker == null) return null
        return try {
            val aktoerId = aktoerregisterService.hentGjeldendeAktoerIdForNorskIdent(navBruker)
            aktoerId
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting av aktørid: $ex")
            null
        }
    }

    private fun hentPerson(navBruker: String?): Person? {
        if (navBruker == null) return null
        return try {
            personV3Service.hentPerson(navBruker)
        } catch (ex: Exception) {
            logger.error("Det oppstod en feil ved henting av person: $ex")
            null
        }
    }

    private fun hentTildeltEnhet(
            bucType: BucType?,
            rinaSakId: String,
            rinaDokumentId: String,
            navBruker: String?,
            landkode: String?,
            fodselsDato: String
    ): OppgaveRoutingModel.Enhet {
        return if(bucType == BucType.P_BUC_10){
            val ytelseType = hentYtelseTypeMapper.map(fagmodulService.hentYtelseTypeForPBuc10(rinaSakId, rinaDokumentId))
            oppgaveRoutingService.route(navBruker, bucType, landkode, fodselsDato, ytelseType)
        } else {
            oppgaveRoutingService.route(navBruker, bucType, landkode, fodselsDato)
        }
    }
}

private class HentYtelseTypeMapper {
    fun map(hentYtelseTypeResponse: HentYtelseTypeResponse) : OppgaveRoutingModel.YtelseType? {
        return when(hentYtelseTypeResponse.krav?.type) {
            Krav.YtelseType.AP -> OppgaveRoutingModel.YtelseType.AP
            Krav.YtelseType.GP -> OppgaveRoutingModel.YtelseType.GP
            Krav.YtelseType.UT -> OppgaveRoutingModel.YtelseType.UT
            null -> null
        }
    }
}


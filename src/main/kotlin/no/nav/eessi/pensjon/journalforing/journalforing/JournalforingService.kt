package no.nav.eessi.pensjon.journalforing.journalforing

import no.nav.eessi.pensjon.journalforing.models.BucType
import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.EuxService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.HentYtelseTypeResponse
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingModel
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OpprettOppgaveModel
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import no.nav.eessi.pensjon.journalforing.models.HendelseType
import no.nav.eessi.pensjon.journalforing.oppgaverouting.OppgaveRoutingService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.Krav
import no.nav.eessi.pensjon.journalforing.services.journalpost.*
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person

@Service
class JournalforingService(private val euxService: EuxService,
                           private val journalpostService: JournalpostService,
                           private val oppgaveService: OppgaveService,
                           private val aktoerregisterService: AktoerregisterService,
                           private val personV3Service: PersonV3Service,
                           private val fagmodulService: FagmodulService,
                           private val oppgaveRoutingService: OppgaveRoutingService)  {

    private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

    private val hentYtelseTypeMapper = HentYtelseTypeMapper()

    fun journalfor(hendelseJson: String, hendelseType: HendelseType){
        val sedHendelse = deserialiserHendelse(hendelseJson)

        if (sedHendelse.sektorKode != "P") {
            // Vi ignorerer alle hendelser som ikke har vår sektorkode
            return
        }

        logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")

        val person = hentPerson(sedHendelse.navBruker)
        val aktoerId = hentAktoerId(sedHendelse.navBruker)
        val personNavn = person?.personnavn?.sammensattNavn
        val landkode = person?.bostedsadresse?.strukturertAdresse?.landkode?.value

        val sedDokumenter = euxService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
        val fodselsDatoISO = euxService.hentFodselsDato(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
        val isoDato = LocalDate.parse(fodselsDatoISO, DateTimeFormatter.ISO_DATE)
        val fodselsDato = isoDato.format(DateTimeFormatter.ofPattern("ddMMyy"))

        val requestBody = JournalpostModel.from(sedHendelse, hendelseType, sedDokumenter, personNavn)
        val journalPostResponse = journalpostService.opprettJournalpost(requestBody.journalpostRequest, hendelseType,false)

        val ytelseType: OppgaveRoutingModel.YtelseType? =
                if(sedHendelse.bucType == BucType.P_BUC_10){
                    hentYtelseTypeMapper.map(fagmodulService.hentYtelseTypeForPBuc10(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId))
                } else null

        val tildeltEnhet = oppgaveRoutingService.route(sedHendelse.navBruker, sedHendelse.bucType, landkode, fodselsDato, ytelseType)

        oppgaveService.opprettOppgave(OpprettOppgaveModel(
                sedType = sedHendelse.sedType.toString(),
                journalpostId = journalPostResponse.journalpostId,
                tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                aktoerId = aktoerId,
                oppgaveType = OpprettOppgaveModel.OppgaveType.JOURNALFORING,
                rinaSakId = null,
                filnavn = null))

        if(requestBody.uSupporterteVedlegg.isNotEmpty()) {
            oppgaveService.opprettOppgave(OpprettOppgaveModel(
                    sedType = sedHendelse.sedType.toString(),
                    journalpostId = null,
                    tildeltEnhetsnr = tildeltEnhet.enhetsNr,
                    aktoerId = aktoerId,
                    oppgaveType = OpprettOppgaveModel.OppgaveType.BEHANDLE_SED,
                    rinaSakId = sedHendelse.rinaSakId,
                    filnavn = requestBody.uSupporterteVedlegg))
        }
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

    private fun deserialiserHendelse(hendelse: String): SedHendelseModel {
        try {
            return SedHendelseModel.fromJson(hendelse)
        } catch (ex: Exception){
            logger.error("Det oppstod en feil ved deserialisering av hendelse: $ex")
            throw ex
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


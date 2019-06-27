package no.nav.eessi.pensjon.journalforing.services

import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.EuxService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.HentYtelseTypeMapper
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel
import no.nav.eessi.pensjon.journalforing.services.kafka.sedMapper
import no.nav.eessi.pensjon.journalforing.services.oppgave.Oppgave
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OpprettOppgaveModel
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val logger = LoggerFactory.getLogger(JournalforingService::class.java)

@Service
class JournalforingService(val euxService: EuxService,
                           val journalpostService: JournalpostService,
                           val oppgaveService: OppgaveService,
                           val aktoerregisterService: AktoerregisterService,
                           val personV3Service: PersonV3Service,
                           val fagmodulService: FagmodulService)  {

    private val hentYtelseTypeMapper = HentYtelseTypeMapper()

    fun journalfor(hendelse: String){
        val sedHendelse = sedMapper.readValue(hendelse, SedHendelseModel::class.java)

        if (sedHendelse.sektorKode == "P") {
            logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId} rinasakID: ${sedHendelse.rinaSakId}")

            val landkode = hentLandkode(sedHendelse)
            val aktoerId = hentAktoerId(sedHendelse)
            val sedDokumenter = euxService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

            val fodselsDatoISO = euxService.hentFodselsDato(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            val isoDato = LocalDate.parse(fodselsDatoISO, DateTimeFormatter.ISO_DATE)
            val fodselsDato = isoDato.format(DateTimeFormatter.ofPattern("ddMMyy"))

            val requestBody = journalpostService.byggJournalPostRequest(sedHendelseModel = sedHendelse, sedDokumenter = sedDokumenter)
            val journalPostResponse = journalpostService.opprettJournalpost(requestBody.journalpostRequest,false)

            var ytelseType: OppgaveRoutingModel.YtelseType? = null

            if(sedHendelse.bucType == SedHendelseModel.BucType.P_BUC_10) {
                ytelseType = hentYtelseTypeMapper.map(fagmodulService.hentYtelseTypeForPBuc10(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId))
            }

            oppgaveService.opprettOppgave(OpprettOppgaveModel(sedHendelse,
                    journalPostResponse,
                    aktoerId,
                    landkode,
                    fodselsDato,
                    ytelseType,
                    Oppgave.OppgaveType.JOURNALFORING,
                    null,
                    null))

            if(requestBody.uSupporterteVedlegg.isNotEmpty()) {
                oppgaveService.opprettOppgave(OpprettOppgaveModel(sedHendelse,
                        null,
                        aktoerId,
                        landkode,
                        fodselsDato,
                        ytelseType,
                        Oppgave.OppgaveType.BEHANDLE_SED,
                        sedHendelse.rinaSakId,
                        requestBody.uSupporterteVedlegg))
            }
        }
    }

    private fun hentAktoerId(sedHendelse: SedHendelseModel): String? {
        return if(sedHendelse.navBruker == null) {
            null
        } else {
            try {
                val aktoerId = aktoerregisterService.hentGjeldendeAktoerIdForNorskIdent(sedHendelse.navBruker!!)
                logger.info("Aktørid: $aktoerId")
                aktoerId
            } catch (ex: Exception) {
                logger.error("Det oppstod en feil ved henting av aktørid: $ex")
                null
            }
        }
    }

    fun hentLandkode(sedHendelse: SedHendelseModel): String?{
        return if (sedHendelse.navBruker == null) {
            null
        } else {
            return try {
                val person = personV3Service.hentPerson(sedHendelse.navBruker)
                personV3Service.hentLandKode(person)
            } catch (ex: Exception) {
                logger.error("Det oppstod en feil ved henting av landkode: $ex")
                null
            }
        }
    }
}
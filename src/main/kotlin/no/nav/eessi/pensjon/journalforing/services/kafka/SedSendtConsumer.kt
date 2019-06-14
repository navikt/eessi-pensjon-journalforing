package no.nav.eessi.pensjon.journalforing.services.kafka

import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.EuxService
import no.nav.eessi.pensjon.journalforing.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.services.oppgave.Oppgave
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OpprettOppgaveModel
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import no.nav.eessi.pensjon.journalforing.utils.counter
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.CountDownLatch

@Service
class SedSendtConsumer(val euxService: EuxService,
                       val journalpostService: JournalpostService,
                       val oppgaveService: OppgaveService,
                       val aktoerregisterService: AktoerregisterService,
                       val personV3Service: PersonV3Service,
                       val fagmodulService: FagmodulService) {

    private val logger = LoggerFactory.getLogger(SedSendtConsumer::class.java)
    private val latch = CountDownLatch(4)

    private final val consumeSedMessageNavn = "eessipensjon_journalforing.consumeOutgoingSed"
    private val consumeSedMessageVellykkede = counter(consumeSedMessageNavn, "vellykkede")
    private val consumeSedMessageFeilede = counter(consumeSedMessageNavn, "feilede")

    fun getLatch(): CountDownLatch {
        return latch
    }

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet hendelse")
        try {
            val sedHendelse = sedMapper.readValue(hendelse, SedHendelseModel::class.java)

            if (sedHendelse.sektorKode == "P") {
                logger.info("Gjelder pensjon: ${sedHendelse.sektorKode}")
                logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId}")
                logger.info("rinasakID: ${sedHendelse.rinaSakId}")

                val landkode = hentLandkode(sedHendelse)
                val aktoerId = hentAktoerId(sedHendelse)
                val sedDokumenter = euxService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

                val fodselsDatoISO = euxService.hentFodselsDato(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                val isoDato = LocalDate.parse(fodselsDatoISO, DateTimeFormatter.ISO_DATE)
                val fodselsDato = isoDato.format(DateTimeFormatter.ofPattern("ddMMyy"))

                val requestBody = journalpostService.byggJournalPostRequest(sedHendelseModel = sedHendelse, sedDokumenter = sedDokumenter)
                val journalPostResponse = journalpostService.opprettJournalpost(requestBody.journalpostRequest,false)

                var ytelseType: OppgaveRoutingModel.YtelseType? = null
                fagmodulService.hentYtelseTypeForPBuc10(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

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

            consumeSedMessageVellykkede.increment()
            acknowledgment.acknowledge()

        } catch(ex: Exception){
            consumeSedMessageFeilede.increment()
            logger.error(
                    "Noe gikk galt under behandling av SED-hendelse:\n $hendelse \n" +
                    "${ex.message}",
                    ex
            )
            throw RuntimeException(ex.message)
        }
        latch.countDown()
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
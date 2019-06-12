package no.nav.eessi.pensjon.journalforing.services.kafka

import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.EuxService
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalPostResponse
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.services.oppgave.Oppgave
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OpprettOppgaveModel
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import no.nav.eessi.pensjon.journalforing.utils.counter
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch

@Service
class SedSendtConsumer(val euxService: EuxService,
                       val journalpostService: JournalpostService,
                       val oppgaveService: OppgaveService,
                       val aktoerregisterService: AktoerregisterService,
                       val personV3Service: PersonV3Service) {

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

            if (sedHendelse.sektorKode.equals("P")) {
                logger.info("Gjelder pensjon: ${sedHendelse.sektorKode}")
                logger.info("rinadokumentID: ${sedHendelse.rinaDokumentId}")
                logger.info("rinasakID: ${sedHendelse.rinaSakId}")
                var aktoerId: String? = null
                val person: Person?
                var landkode: String? = null

                if (sedHendelse.navBruker != null) {
                    aktoerId = aktoerregisterService.hentGjeldendeAktoerIdForNorskIdent(sedHendelse.navBruker)
                    logger.info("Aktørid: $aktoerId")
                    person = personV3Service.hentPerson(sedHendelse.navBruker)
                    landkode = personV3Service.hentLandKode(person)
                }

                val sedDokumenter = euxService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                val journalPostResponse: JournalPostResponse

                try  {
                    journalPostResponse = journalpostService.opprettJournalpost(sedHendelseModel = sedHendelse,
                            sedDokumenter = sedDokumenter,
                            forsokFerdigstill = false)

                    oppgaveService.opprettOppgave(OpprettOppgaveModel(sedHendelse,
                            journalPostResponse,
                            aktoerId,
                            landkode,
                            "010184",
                            OppgaveRoutingModel.YtelseType.AP,
                            Oppgave.OppgaveType.JOURNALFORING,
                            null,
                            null))

                } catch (ex: JournalpostService.UnsupportedFiletypeException) {
                    oppgaveService.opprettOppgave(OpprettOppgaveModel(sedHendelse,
                            null,
                            aktoerId,
                            landkode,
                            "010184",
                            OppgaveRoutingModel.YtelseType.AP,
                            Oppgave.OppgaveType.BEHANDLE_SED,
                            ex.rinaSakId,
                            ex.filNavn)
                    )
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
}
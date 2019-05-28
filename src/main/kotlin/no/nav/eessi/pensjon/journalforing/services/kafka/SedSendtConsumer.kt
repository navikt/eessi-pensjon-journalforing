package no.nav.eessi.pensjon.journalforing.services.kafka

import no.nav.eessi.pensjon.journalforing.services.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.journalforing.services.eux.PdfService
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.util.concurrent.CountDownLatch

@Service
class SedSendtConsumer(val pdfService: PdfService,
                  val journalpostService: JournalpostService,
                  val oppgaveService: OppgaveService,
                  val aktoerregisterService: AktoerregisterService,
                  val personV3Service: PersonV3Service) {

    private val logger = LoggerFactory.getLogger(SedSendtConsumer::class.java)
    private val latch = CountDownLatch(4)

    fun getLatch(): CountDownLatch {
        return latch
    }

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet hendelse")
        val sedHendelse = sedMapper.readValue(hendelse, SedHendelse::class.java)

        if(sedHendelse.sektorKode.equals("P")){
            logger.info("Gjelder pensjon: ${sedHendelse.sektorKode}")
            val sedDokumenter = pdfService.hentSedDokumenter(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

            val journalPostResponse = journalpostService.opprettJournalpost(sedHendelse = sedHendelse,
                    sedDokumenter = sedDokumenter,
                    forsokFerdigstill = false)

            var aktoerId: String? = null
            var person: Person?
            var landkode: String? = null

            if(sedHendelse.navBruker != null) {
                aktoerId = aktoerregisterService.hentGjeldendeAktoerIdForNorskIdent(sedHendelse.navBruker)
                person = personV3Service.hentPerson(sedHendelse.navBruker)
                landkode = personV3Service.hentLandKode(person)
            }
            oppgaveService.opprettOppgave(sedHendelse, journalPostResponse, aktoerId)

            // acknowledgment.acknowledge()
        }
        latch.countDown()
    }
}
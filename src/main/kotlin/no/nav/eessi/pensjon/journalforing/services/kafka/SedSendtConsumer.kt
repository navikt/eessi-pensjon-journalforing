package no.nav.eessi.pensjon.journalforing.services.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.eux.PdfService
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import no.nav.eessi.pensjon.journalforing.services.oppgave.Oppgave
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
class SedConsumer(val pdfService: PdfService,
                  val journalpostService: JournalpostService,
                  val oppgaveService: OppgaveService) {

    private val logger = LoggerFactory.getLogger(SedConsumer::class.java)
    private val mapper = jacksonObjectMapper()

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet hendelse")
        val sedHendelse = mapper.readValue(hendelse, SedHendelse::class.java)


        if(sedHendelse.sektorKode.equals("P")){
            logger.info("Gjelder pensjon: ${sedHendelse.sektorKode}")
            val pdfBody = pdfService.hentPdf(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            journalpostService.opprettJournalpost(sedHendelse = sedHendelse, pdfBody= pdfBody!!, forsokFerdigstill = false)
            oppgaveService.opprettOppgave(populerOppgaveFelter(sedHendelse))
            // acknowledgment.acknowledge()
        }
    }

    fun populerOppgaveFelter(sedHendelse: SedHendelse) : Oppgave {
        val oppgave = Oppgave()
        oppgave.oppgavetype = Oppgave.OppgaveType.JOURNALFORING.name

        if(sedHendelse.bucType.equals("P_BUC_03")) {
            oppgave.tema = Oppgave.Tema.UFORETRYGD.name
            oppgave.behandlingstema = Oppgave.Behandlingstema.UFORE_UTLAND.name
            oppgave.temagruppe = Oppgave.Temagruppe.UFORETRYDG.name

        } else {
           oppgave.tema = Oppgave.Tema.PENSJON.name
           oppgave.behandlingstema = Oppgave.Behandlingstema.UTLAND.name
           oppgave.temagruppe = Oppgave.Temagruppe.PENSJON.name

        }
        oppgave.prioritet = Oppgave.Prioritet.NORM.name
        //oppgave.aktoerId = sedHendelse.
        oppgave.aktivDato = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        oppgave.behandlingstype = Oppgave.Behandlingstype.UTLAND.name
        // oppgave.journalpostId = hent fra midlertidig journalføring når den er klar
        oppgave.opprettetAvEnhetsnr = "9999"


        return oppgave
    }
}
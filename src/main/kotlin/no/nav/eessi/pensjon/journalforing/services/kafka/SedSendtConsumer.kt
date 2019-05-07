package no.nav.eessi.pensjon.journalforing.services.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.eux.PdfService
import no.nav.eessi.pensjon.journalforing.services.journalpost.JournalpostService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class SedConsumer(val pdfService: PdfService, val journalpostService: JournalpostService) {

    private val logger = LoggerFactory.getLogger(SedConsumer::class.java)
    private val mapper = jacksonObjectMapper()

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet hendelse")
        val sedHendelse = mapper.readValue(hendelse, SedHendelse::class.java)


        if(sedHendelse.sektorKode.equals("P")){

            val pdfBody: String = pdfService.hentPdf(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
                    ?: throw RuntimeException("Noe gikk galt under henting av pdf")

            journalpostService.opprettJournalpost(sedHendelse = sedHendelse, pdfBody= pdfBody, forsokFerdigstill = false)
            logger.info("Gjelder pensjon: ${sedHendelse.sektorKode}")
            val pdfBody = pdfService.hentPdf(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            journalpostService.opprettJournalpost(sedHendelse = sedHendelse, pdfBody= pdfBody!!, forsokFerdigstill = false)
            // acknowledgment.acknowledge()
        }
    }
}
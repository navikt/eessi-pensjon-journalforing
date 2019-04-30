package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.eessi.pensjon.journalforing.services.eux.PdfService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class SedConsumer(val pdfService: PdfService) {

    private val logger = LoggerFactory.getLogger(SedConsumer::class.java)
    private val mapper = ObjectMapper()

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet hendelse.")
        val sedHendelse = mapper.readValue(hendelse, SedHendelse::class.java)

        pdfService.hentPdf(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

        if(sedHendelse.sektorKode.equals("P")){
            logger.info("Gjelder pensjon: ${sedHendelse.sektorKode}")
           // acknowledgment.acknowledge()
        }
    }
}
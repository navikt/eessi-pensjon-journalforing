package no.nav.eessi.pensjon.journalforing.services.kafka

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.services.eux.PdfService
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Service

@Service
class SedSendtConsumer(val pdfService: PdfService) {

    private val logger = LoggerFactory.getLogger(SedSendtConsumer::class.java)
    private val mapper = jacksonObjectMapper()

    @KafkaListener(topics = ["\${kafka.sedSendt.topic}"], groupId = "\${kafka.sedSendt.groupid}")
    fun consume(hendelse: String, acknowledgment: Acknowledgment) {
        logger.info("Innkommet hendelse")
        val sedHendelse = mapper.readValue(hendelse, SedHendelse::class.java)

        if(sedHendelse.sektorKode.equals("P")){
            logger.info("Gjelder pensjon: ${sedHendelse.sektorKode}")
            pdfService.hentPdf(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            // acknowledgment.acknowledge()
        }
    }
}
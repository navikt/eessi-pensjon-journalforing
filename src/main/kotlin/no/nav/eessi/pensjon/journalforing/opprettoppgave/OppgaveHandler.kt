package no.nav.eessi.pensjon.journalforing.opprettoppgave

import no.nav.eessi.pensjon.journalforing.etterlatte.EtterlatteService
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

@Service
class OppgaveHandler(private val oppgaveKafkaTemplate: KafkaTemplate<String, String>,
                     private val etterlatteService: EtterlatteService,
                     @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest() ) {

    private val logger = LoggerFactory.getLogger(OppgaveHandler::class.java)
    private val X_REQUEST_ID = "x_request_id"

    private lateinit var publiserOppgavemelding: MetricsHelper.Metric

    init {
        publiserOppgavemelding = metricsHelper.init("publiserOppgavemelding")
    }

    fun opprettOppgaveMeldingPaaKafkaTopic(melding: OppgaveMelding) {
        val key = MDC.get(X_REQUEST_ID)

        if (melding.tema in listOf(Tema.EYBARNEP, Tema.OMSTILLING) && melding.hendelseType == HendelseType.SENDT) {
            if (etterlatteService.opprettGjennyOppgave(melding).isFailure) {
                logger.error("Kunne ikke opprette oppgave i Gjenny for sakId: ${melding.rinaSakId}")
                throw IllegalArgumentException("Noe gikk ikke bra ved opprettelse av gjennyoppgave for sak med sakid${melding.rinaSakId}")
            }
            logger.info("Oppgave opprettet i Gjenny for sakId: ${melding.rinaSakId}")
        } else {
            val payload = melding.toJson()

            publiserOppgavemelding.measure {
                logger.info("Opprette ${melding.oppgaveType}-oppgave melding på kafka: ${oppgaveKafkaTemplate.defaultTopic}  melding: $melding")
                oppgaveKafkaTemplate.sendDefault(key, payload).get()
            }
        }
    }
}

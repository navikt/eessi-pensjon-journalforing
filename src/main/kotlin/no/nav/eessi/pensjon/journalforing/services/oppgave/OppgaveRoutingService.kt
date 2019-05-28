package no.nav.eessi.pensjon.journalforing.services.oppgave

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import no.nav.eessi.pensjon.journalforing.services.oppgave.OppgaveRoutingModel.Bosatt

private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

@Service
class OppgaveRoutingService {

    fun route(oppgaverouting: OppgaveRoutingModel): OppgaveRoutingModel.Enhet {
        return if (oppgaverouting.fnr == null) {
            logger.info("Fnr mangler, oppgave routes til: ${oppgaverouting.bucType.manglerFnrEnhet}")
            oppgaverouting.bucType.manglerFnrEnhet
        } else {
            if (hentBosatt(oppgaverouting.fnr) == Bosatt.NORGE) {
                logger.info("Person bosatt i Norge, oppgave routes til: ${oppgaverouting.bucType.bosattNorgeEnhet}")
                oppgaverouting.bucType.bosattNorgeEnhet
            } else {
                logger.info("Person bosatt i utlandet, oppgave routes til: ${oppgaverouting.bucType.bosattUtlandEnhet}")
                oppgaverouting.bucType.bosattUtlandEnhet
            }
        }
    }

    fun hentBosatt(fnr: String?) : Bosatt {
        // Byttes ut med PersonV3 service kall når den er tilgjengelig
        return Bosatt.NORGE
    }
}

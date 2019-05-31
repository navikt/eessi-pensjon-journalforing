package no.nav.eessi.pensjon.journalforing.services.oppgave

import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

@Service
class OppgaveRoutingService {

    val mapper = OppgaveRoutingMapper()

    fun route(sedHendelse: SedHendelseModel,
              landkode: String?,
              fodselsDato: String,
              gjelder: OppgaveRoutingModel.YtelseType?): OppgaveRoutingModel.Enhet {

        return mapper.map(sedHendelse, landkode, fodselsDato, gjelder)
    }
}

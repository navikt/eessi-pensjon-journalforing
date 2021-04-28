package no.nav.eessi.pensjon.oppgaverouting

import no.nav.eessi.pensjon.klienter.norg2.Norg2Service
import no.nav.eessi.pensjon.klienter.norg2.NorgKlientRequest
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OppgaveRoutingService(private val norg2Service: Norg2Service) {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        if (routingRequest.aktorId == null) {
            logger.info("AktørID mangler. Bruker enhet ID_OG_FORDELING.")
            return Enhet.ID_OG_FORDELING
        }

        val tildeltEnhet = tildelEnhet(routingRequest)

        logger.info(
            "Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}) for:" +
                    "Buc: ${routingRequest.bucType}, " +
                    "Landkode: ${routingRequest.landkode}, " +
                    "Fødselsdato: ${routingRequest.fdato}, " +
                    "Geografisk Tilknytning: ${routingRequest.geografiskTilknytning}, " +
                    "saktype: ${routingRequest.saktype}"
        )

        return tildeltEnhet
    }

    private fun tildelEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        val enhet = BucTilEnhetHandlerCreator.getHandler(routingRequest.bucType).hentEnhet(routingRequest)

        if (enhet == Enhet.AUTOMATISK_JOURNALFORING)
            return enhet

        logger.debug("enhet: $enhet")

        if (routingRequest.bucType == BucType.P_BUC_01) {
            val norgKlientRequest = NorgKlientRequest(
                routingRequest.harAdressebeskyttelse,
                routingRequest.landkode,
                routingRequest.geografiskTilknytning,
                routingRequest.saktype
            )
            return norg2Service.hentArbeidsfordelingEnhet(norgKlientRequest) ?: enhet
        }
        if (routingRequest.bucType == BucType.P_BUC_10 && routingRequest.hendelseType == HendelseType.MOTTATT) {
            val personRelasjon = routingRequest.identifisertPerson?.personRelasjon
            val norgKlientRequest = NorgKlientRequest(
                routingRequest.harAdressebeskyttelse,
                routingRequest.landkode,
                routingRequest.geografiskTilknytning,
                routingRequest.saktype,
                personRelasjon
            )
            return norg2Service.hentArbeidsfordelingEnhet(norgKlientRequest) ?: enhet
        }
        return enhet
    }

}

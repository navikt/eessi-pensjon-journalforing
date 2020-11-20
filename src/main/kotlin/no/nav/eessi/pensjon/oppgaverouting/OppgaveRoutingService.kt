package no.nav.eessi.pensjon.oppgaverouting

import com.google.common.annotations.VisibleForTesting
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class OppgaveRoutingService(private val norg2Klient: Norg2Klient) {

    private val logger = LoggerFactory.getLogger(OppgaveRoutingService::class.java)

    fun route(routingRequest: OppgaveRoutingRequest): Enhet {
        logger.debug("personfdato: ${routingRequest.fdato},  bucType: ${routingRequest.bucType}, ytelseType: ${routingRequest.ytelseType}")

        if (routingRequest.fnr == null) return Enhet.ID_OG_FORDELING
        val norgKlientRequest = NorgKlientRequest(routingRequest.diskresjonskode?.name, routingRequest.landkode, routingRequest.geografiskTilknytning)

        val tildeltEnhet = hentNorg2Enhet(norgKlientRequest, routingRequest.bucType)
                ?: bestemEnhet(routingRequest)

        logger.info("Router oppgave til $tildeltEnhet (${tildeltEnhet.enhetsNr}) for:" +
                "Buc: ${routingRequest.bucType}, " +
                "Landkode: ${routingRequest.landkode}, " +
                "Fødselsdato: ${routingRequest.fdato}, " +
                "Geografisk Tilknytning: ${routingRequest.geografiskTilknytning}, " +
                "Ytelsetype: ${routingRequest.ytelseType}")

        return tildeltEnhet
    }

    private fun bestemEnhet(routingRequest: OppgaveRoutingRequest): Enhet {
        return if (routingRequest.diskresjonskode != null && routingRequest.diskresjonskode == Diskresjonskode.SPSF)
            Enhet.DISKRESJONSKODE
        else
            BucTilEnhetHandlerCreator.getHandler(routingRequest.bucType).hentEnhet(routingRequest)
    }


    @VisibleForTesting
    fun hentNorg2Enhet(person: NorgKlientRequest, bucType: BucType?): Enhet? {
        return if (bucType == BucType.P_BUC_01)
            try {
                val enhetVerdi = norg2Klient.hentArbeidsfordelingEnhet(person)
                logger.info("Norg2tildeltEnhet: $enhetVerdi")
                enhetVerdi?.let { Enhet.getEnhet(it) }
            } catch (ex: Exception) {
                logger.error("Ukjent feil oppstod; ${ex.message}")
                null
            }
        else null
    }
}
